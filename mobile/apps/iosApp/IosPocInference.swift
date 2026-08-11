import CoreGraphics
import CryptoKit
import Foundation
import UIKit

struct IosPocDishCandidate {
    let label: String
    let confidence: Double
}

struct IosPocRegionProposal {
    let label: String
    let confidence: Double
    let left: Double
    let top: Double
    let right: Double
    let bottom: Double
}

struct IosPocAnalysisResult {
    let dishes: [IosPocDishCandidate]
    let regions: [IosPocRegionProposal]
}

/// Native iOS counterpart of the bounded Android PoC runtime.
///
/// It intentionally runs the same two assets and preprocessing contract as
/// Android: Food-101 Swin for dish proposals and YOLO-World restricted to the
/// five visible COCO food labels used by the PoC. MobileSAM is not included
/// because the Android app path also disables mask refinement.
final class IosPocInferenceEngine {
    private static let dishAsset = "swin-finetuned-food101-matmul-int8"
    private static let regionAsset = "yolov8s-worldv2-coco"
    private static let labelsAsset = "food101-labels"
    private static let dishSha256 = "9070f8097cda88923f77774d93d9fead7cee344486521c7fc5142806642061df"
    private static let regionSha256 = "d27ac81640ca470cc7e42edd638b6626ac6b4f4dcce74aabb6e09cd02a13c84f"
    private static let labelsSha256 = "eb6b396455a281445a698d8af22b2142be9a6eb9401520ee702746545f1c8820"
    private static let artifactSha256 = "e17ea5185554e6b2d3462c14f1559d8fe8a2f1bf9ed69051d555aab7d4c8f39a"
    private static let dishSize = 224
    private static let regionSize = 640
    private static let maximumRegions = 16
    private static let regionConfidence = Float(0.10)
    private static let regionNmsIou = Float(0.70)
    private static let cocoNames = [
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic_light",
        "fire_hydrant", "stop_sign", "parking_meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
        "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
        "skis", "snowboard", "sports_ball", "kite", "baseball_bat", "baseball_glove", "skateboard", "surfboard", "tennis_racket", "bottle",
        "wine_glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
        "broccoli", "carrot", "hot_dog", "pizza", "donut", "cake", "chair", "couch", "potted_plant", "bed",
        "dining_table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell_phone", "microwave", "oven",
        "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy_bear", "hair_drier", "toothbrush",
    ]
    private static let componentLabels: Set<String> = ["banana", "apple", "orange", "broccoli", "carrot"]

    private var environment: ORTEnv?
    private var dishSession: ORTSession?
    private var regionSession: ORTSession?
    private var labels: [String]?
    private var assetsVerified = false

    func analyze(_ data: Data) throws -> IosPocAnalysisResult {
        guard let image = UIImage(data: data) else { throw IosPocInferenceError.invalidImage }
        try verifyAssets()
        let dishes = (try? runDish(image)) ?? []
        let regions = (try? runRegions(image)) ?? []
        guard !dishes.isEmpty || !regions.isEmpty else {
            throw IosPocInferenceError.runtime("PoC inference produced no valid stage output")
        }
        return IosPocAnalysisResult(dishes: dishes, regions: regions)
    }

    private func verifyAssets() throws {
        if assetsVerified { return }
        let dish = try resourceURL(Self.dishAsset, extension: "onnx")
        let region = try resourceURL(Self.regionAsset, extension: "onnx")
        let labels = try resourceURL(Self.labelsAsset, extension: "txt")
        let dishData = try Data(contentsOf: dish)
        let regionData = try Data(contentsOf: region)
        let labelsData = try Data(contentsOf: labels)
        guard sha256(dishData) == Self.dishSha256,
              sha256(regionData) == Self.regionSha256,
              sha256(labelsData) == Self.labelsSha256,
              sha256(Data("\(Self.dishSha256):\(Self.regionSha256):\(Self.labelsSha256)".utf8)) == Self.artifactSha256
        else { throw IosPocInferenceError.assetIntegrity }
        let values = try String(contentsOf: labels, encoding: .utf8)
            .split(whereSeparator: \.isNewline)
            .map(String.init)
        guard values.count == 101 else { throw IosPocInferenceError.invalidLabels }
        self.labels = values
        assetsVerified = true
    }

    private func runDish(_ image: UIImage) throws -> [IosPocDishCandidate] {
        let session = try dish()
        let input = try makeTensor(
            try rasterize(image, side: Self.dishSize, cropToSquare: true, normalization: .dish),
            shape: [1, 3, Self.dishSize, Self.dishSize],
        )
        let output = try run(session, input: input)
        let scores = try floats(output.value)
        guard scores.count >= 5, let labels else { throw IosPocInferenceError.invalidDishOutput }
        let probabilities = try softmax(scores)
        return probabilities.enumerated()
            .sorted { $0.element > $1.element }
            .prefix(5)
            .map { IosPocDishCandidate(label: labels[$0.offset], confidence: Double($0.element)) }
    }

    private func runRegions(_ image: UIImage) throws -> [IosPocRegionProposal] {
        let session = try regions()
        let input = try makeTensor(
            try rasterize(image, side: Self.regionSize, cropToSquare: false, normalization: .rgb),
            shape: [1, 3, Self.regionSize, Self.regionSize],
        )
        let output = try run(session, input: input)
        return try parseRegions(values: floats(output.value), shape: output.shape)
    }

    private func dish() throws -> ORTSession {
        if let dishSession { return dishSession }
        let session = try makeSession(Self.dishAsset)
        dishSession = session
        return session
    }

    private func regions() throws -> ORTSession {
        if let regionSession { return regionSession }
        let session = try makeSession(Self.regionAsset)
        regionSession = session
        return session
    }

    private func makeSession(_ name: String) throws -> ORTSession {
        guard let environment = environment ?? makeEnvironment() else {
            throw IosPocInferenceError.runtime("ONNX Runtime environment unavailable")
        }
        self.environment = environment
        let model = try resourceURL(name, extension: "onnx")
        let options: ORTSessionOptions
        do {
            options = try ORTSessionOptions()
            try options.setGraphOptimizationLevel(.extended)
            try options.setIntraOpNumThreads(2)
        } catch {
            throw error
        }
        guard let session = try? ORTSession(
            env: environment,
            modelPath: model.path,
            sessionOptions: options,
        ) else {
            throw IosPocInferenceError.runtime("ONNX Runtime session unavailable")
        }
        guard try session.inputNames().count == 1 else { throw IosPocInferenceError.runtime("PoC model must have one input") }
        return session
    }

    private func makeEnvironment() -> ORTEnv? {
        try? ORTEnv(loggingLevel: .warning)
    }

    private func run(_ session: ORTSession, input: ORTValue) throws -> (value: ORTValue, shape: [Int]) {
        do {
            let inputName = try session.inputNames().first
            let outputName = try session.outputNames().first
            guard let inputName, let outputName else {
                throw IosPocInferenceError.runtime("PoC model input or output unavailable")
            }
            let outputs = try session.run(
                withInputs: [inputName: input],
                outputNames: [outputName],
                runOptions: nil,
            )
            guard let value = outputs[outputName] else {
                throw IosPocInferenceError.runtime("ONNX Runtime model execution failed")
            }
            let info = try value.tensorTypeAndShapeInfo()
            return (value, info.shape.map { $0.intValue })
        } catch {
            throw error
        }
    }

    private func makeTensor(_ values: [Float], shape: [Int]) throws -> ORTValue {
        let data = values.withUnsafeBytes { bytes in
            NSMutableData(bytes: bytes.baseAddress, length: bytes.count)
        }
        return try ORTValue(
            tensorData: data,
            elementType: .float,
            shape: shape.map { NSNumber(value: $0) },
        )
    }

    private func floats(_ value: ORTValue) throws -> [Float] {
        let info = try value.tensorTypeAndShapeInfo()
        guard info.elementType == .float else { throw IosPocInferenceError.invalidTensorType }
        let data = try value.tensorData()
        let count = data.length / MemoryLayout<Float>.size
        let pointer = data.bytes.bindMemory(to: Float.self, capacity: count)
        return Array(UnsafeBufferPointer(start: pointer, count: count))
    }

    private func parseRegions(values: [Float], shape: [Int]) throws -> [IosPocRegionProposal] {
        guard shape.count == 3, shape[0] == 1, shape[1] > 0, shape[2] > 0 else {
            throw IosPocInferenceError.invalidRegionOutput
        }
        let featureCount = 4 + Self.cocoNames.count
        let channelsFirst = shape[1] == featureCount
        let rowsFirst = shape[2] == featureCount
        guard channelsFirst != rowsFirst else { throw IosPocInferenceError.invalidRegionOutput }
        let predictionCount = channelsFirst ? shape[2] : shape[1]
        var candidates: [IosPocRegionProposal] = []
        for prediction in 0..<predictionCount {
            func at(_ feature: Int) -> Float {
                channelsFirst ? values[feature * predictionCount + prediction] : values[prediction * featureCount + feature]
            }
            var classIndex = -1
            var confidence: Float = 0
            for candidate in 0..<Self.cocoNames.count {
                let score = at(4 + candidate)
                guard score.isFinite, score >= 0, score <= 1 else {
                    throw IosPocInferenceError.invalidRegionOutput
                }
                if score > confidence {
                    confidence = score
                    classIndex = candidate
                }
            }
            guard classIndex >= 0,
                  confidence >= Self.regionConfidence,
                  Self.componentLabels.contains(Self.cocoNames[classIndex]) else { continue }
            let centerX = at(0)
            let centerY = at(1)
            let width = at(2)
            let height = at(3)
            guard centerX.isFinite, centerY.isFinite,
                  width.isFinite, height.isFinite,
                  width > 0, height > 0 else {
                throw IosPocInferenceError.invalidRegionOutput
            }
            // The checked YOLO export pins box coordinates to regionSize pixels.
            let scale: Float = 1 / Float(Self.regionSize)
            let left = clamp((centerX - width * 0.5) * scale)
            let top = clamp((centerY - height * 0.5) * scale)
            let right = clamp((centerX + width * 0.5) * scale)
            let bottom = clamp((centerY + height * 0.5) * scale)
            guard right > left, bottom > top else { continue }
            candidates.append(IosPocRegionProposal(
                label: Self.cocoNames[classIndex],
                confidence: Double(confidence),
                left: Double(left),
                top: Double(top),
                right: Double(right),
                bottom: Double(bottom),
            ))
        }
        candidates.sort { $0.confidence > $1.confidence }
        var kept: [IosPocRegionProposal] = []
        for candidate in candidates {
            if kept.contains(where: { $0.label == candidate.label && iou($0, candidate) > Self.regionNmsIou }) {
                continue
            }
            kept.append(candidate)
            if kept.count == Self.maximumRegions { break }
        }
        return kept
    }

    private enum Normalization { case dish, rgb }

    private func rasterize(
        _ image: UIImage,
        side: Int,
        cropToSquare: Bool,
        normalization: Normalization,
    ) throws -> [Float] {
        let target = CGSize(width: side, height: side)
        let renderer = UIGraphicsImageRenderer(size: target)
        let rendered = renderer.image { _ in
            if cropToSquare {
                let sourceSize = image.size
                let scale = max(target.width / sourceSize.width, target.height / sourceSize.height)
                let drawSize = CGSize(width: sourceSize.width * scale, height: sourceSize.height * scale)
                let origin = CGPoint(
                    x: (target.width - drawSize.width) * 0.5,
                    y: (target.height - drawSize.height) * 0.5,
                )
                image.draw(in: CGRect(origin: origin, size: drawSize))
            } else {
                image.draw(in: CGRect(origin: .zero, size: target))
            }
        }
        guard let cgImage = rendered.cgImage,
              let providerData = cgImage.dataProvider?.data,
              let bytes = CFDataGetBytePtr(providerData) else {
            throw IosPocInferenceError.invalidRaster
        }
        let bytesPerPixel = cgImage.bitsPerPixel / 8
        let bytesPerRow = cgImage.bytesPerRow
        let plane = side * side
        var output = [Float](repeating: 0, count: plane * 3)
        for y in 0..<side {
            for x in 0..<side {
                let pixel = bytes + y * bytesPerRow + x * bytesPerPixel
                let red = Float(pixel[0]) / 255
                let green = Float(pixel[1]) / 255
                let blue = Float(pixel[2]) / 255
                let offset = y * side + x
                if normalization == .dish {
                    output[offset] = (red - 0.485) / 0.229
                    output[plane + offset] = (green - 0.456) / 0.224
                    output[plane * 2 + offset] = (blue - 0.406) / 0.225
                } else {
                    output[offset] = red
                    output[plane + offset] = green
                    output[plane * 2 + offset] = blue
                }
            }
        }
        return output
    }

    private func resourceURL(_ name: String, extension: String) throws -> URL {
        guard let url = Bundle.main.url(forResource: name, withExtension: `extension`) else {
            throw IosPocInferenceError.assetMissing(name)
        }
        return url
    }

    private func sha256(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    private func softmax(_ values: [Float]) throws -> [Float] {
        guard !values.isEmpty, values.allSatisfy(\.isFinite) else {
            throw IosPocInferenceError.invalidDishOutput
        }
        let maximum = values.max() ?? 0
        let exponentials = values.map { exp($0 - maximum) }
        let total = exponentials.reduce(0, +)
        guard total.isFinite, total > 0 else { throw IosPocInferenceError.invalidDishOutput }
        let result = exponentials.map { $0 / total }
        guard result.allSatisfy(\.isFinite) else { throw IosPocInferenceError.invalidDishOutput }
        return result
    }

    private func clamp(_ value: Float) -> Float { min(1, max(0, value)) }

    private func iou(_ lhs: IosPocRegionProposal, _ rhs: IosPocRegionProposal) -> Float {
        let left = max(lhs.left, rhs.left)
        let top = max(lhs.top, rhs.top)
        let right = min(lhs.right, rhs.right)
        let bottom = min(lhs.bottom, rhs.bottom)
        let intersection = max(0, right - left) * max(0, bottom - top)
        let lhsArea = (lhs.right - lhs.left) * (lhs.bottom - lhs.top)
        let rhsArea = (rhs.right - rhs.left) * (rhs.bottom - rhs.top)
        let union = lhsArea + rhsArea - intersection
        return union == 0 ? 0 : Float(intersection / union)
    }
}

private enum IosPocInferenceError: Error {
    case invalidImage
    case assetMissing(String)
    case assetIntegrity
    case invalidLabels
    case invalidDishOutput
    case invalidTensorType
    case invalidRegionOutput
    case invalidRaster
    case runtime(String)
}
