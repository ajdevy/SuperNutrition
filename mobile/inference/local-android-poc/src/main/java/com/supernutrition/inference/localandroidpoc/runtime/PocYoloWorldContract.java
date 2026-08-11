package com.supernutrition.inference.localandroidpoc.runtime;

import android.graphics.Bitmap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Canonical Android contract for the selected YOLO-World asset.
 *
 * <p>The shipped local runtime and the physical-device benchmark use this
 * class for RGB normalization, COCO-food filtering, coordinate normalization,
 * and class-aware NMS. It intentionally returns visible food labels only;
 * it does not infer recipe ingredients or mass.</p>
 */
public final class PocYoloWorldContract {
    public static final String MODEL_ASSET = "yolov8s-worldv2-coco.onnx";
    public static final String MODEL_SHA256 =
            "d27ac81640ca470cc7e42edd638b6626ac6b4f4dcce74aabb6e09cd02a13c84f";
    public static final int IMAGE_SIZE = 640;
    public static final float CONFIDENCE_FLOOR = 0.10f;
    public static final float NMS_IOU = 0.70f;
    public static final int MAX_DETECTIONS = 16;
    private static final String[] COCO_NAMES = {
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic_light",
            "fire_hydrant", "stop_sign", "parking_meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
            "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
            "skis", "snowboard", "sports_ball", "kite", "baseball_bat", "baseball_glove", "skateboard", "surfboard", "tennis_racket", "bottle",
            "wine_glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
            "broccoli", "carrot", "hot_dog", "pizza", "donut", "cake", "chair", "couch", "potted_plant", "bed",
            "dining_table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell_phone", "microwave", "oven",
            "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy_bear", "hair_drier", "toothbrush"
    };
    private static final Set<String> VISIBLE_FOOD_LABELS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList("apple", "banana", "broccoli", "carrot", "orange"))
    );

    private PocYoloWorldContract() {
    }

    /** RGB, channels-first, 0..1 normalization for the selected 640px export. */
    public static float[] preprocess(Bitmap source) throws IOException {
        if (source == null || source.isRecycled() || source.getWidth() <= 0 || source.getHeight() <= 0) {
            throw new IOException("YOLO-World source bitmap must be non-empty and live");
        }
        Bitmap resized = Bitmap.createScaledBitmap(source, IMAGE_SIZE, IMAGE_SIZE, true);
        try {
            int plane = IMAGE_SIZE * IMAGE_SIZE;
            float[] output = new float[3 * plane];
            for (int y = 0; y < IMAGE_SIZE; y++) {
                for (int x = 0; x < IMAGE_SIZE; x++) {
                    int pixel = resized.getPixel(x, y);
                    int offset = y * IMAGE_SIZE + x;
                    output[offset] = ((pixel >> 16) & 0xff) / 255.0f;
                    output[plane + offset] = ((pixel >> 8) & 0xff) / 255.0f;
                    output[2 * plane + offset] = (pixel & 0xff) / 255.0f;
                }
            }
            return output;
        } finally {
            if (!resized.isRecycled()) {
                resized.recycle();
            }
        }
    }

    /** Decodes either supported YOLO-World tensor layout into normalized visible-food boxes. */
    public static List<Detection> parse(float[][][] tensor) throws IOException {
        if (tensor.length != 1 || tensor[0].length == 0) {
            throw new IOException("Unexpected YOLO-World output batch/shape");
        }
        float[][] data = tensor[0];
        int featureCount = 4 + COCO_NAMES.length;
        boolean channelsFirst = data.length == featureCount;
        boolean rowsFirst = data[0].length == featureCount;
        if (channelsFirst == rowsFirst) {
            throw new IOException("Expected exactly one YOLO-World feature dimension of " + featureCount
                    + ", got [1," + data.length + "," + data[0].length + "]");
        }

        List<Detection> candidates = new ArrayList<>();
        if (channelsFirst) {
            int predictionCount = data[0].length;
            for (int prediction = 0; prediction < predictionCount; prediction++) {
                addCandidate(
                        candidates,
                        data[0][prediction],
                        data[1][prediction],
                        data[2][prediction],
                        data[3][prediction],
                        data,
                        prediction
                );
            }
        } else {
            for (float[] row : data) {
                addCandidate(candidates, row);
            }
        }

        Collections.sort(candidates, Comparator.comparingDouble(Detection::confidence).reversed());
        List<Detection> kept = new ArrayList<>();
        for (Detection candidate : candidates) {
            boolean overlaps = false;
            for (Detection existing : kept) {
                if (existing.label.equals(candidate.label)
                        && PocInferenceMath.iou(
                        existing.left,
                        existing.top,
                        existing.right,
                        existing.bottom,
                        candidate.left,
                        candidate.top,
                        candidate.right,
                        candidate.bottom
                ) > NMS_IOU) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                kept.add(candidate);
                if (kept.size() == MAX_DETECTIONS) {
                    break;
                }
            }
        }
        return kept;
    }

    public static Set<String> visibleFoodLabels() {
        return VISIBLE_FOOD_LABELS;
    }

    private static void addCandidate(
            List<Detection> candidates,
            float centerX,
            float centerY,
            float width,
            float height,
            float[][] data,
            int prediction
    ) throws IOException {
        int classIndex = -1;
        float confidence = 0.0f;
        for (int candidateClass = 0; candidateClass < COCO_NAMES.length; candidateClass++) {
            float score = data[4 + candidateClass][prediction];
            if (!Float.isFinite(score) || score < 0.0f || score > 1.0f) {
                throw new IOException("YOLO-World class score is not finite and bounded");
            }
            if (score > confidence) {
                confidence = score;
                classIndex = candidateClass;
            }
        }
        addCandidate(candidates, centerX, centerY, width, height, classIndex, confidence);
    }

    private static void addCandidate(List<Detection> candidates, float[] row) throws IOException {
        if (row.length != 4 + COCO_NAMES.length) {
            throw new IOException("Unexpected YOLO-World row feature count " + row.length);
        }
        int classIndex = -1;
        float confidence = 0.0f;
        for (int candidateClass = 0; candidateClass < COCO_NAMES.length; candidateClass++) {
            float score = row[4 + candidateClass];
            if (!Float.isFinite(score) || score < 0.0f || score > 1.0f) {
                throw new IOException("YOLO-World class score is not finite and bounded");
            }
            if (score > confidence) {
                confidence = score;
                classIndex = candidateClass;
            }
        }
        addCandidate(candidates, row[0], row[1], row[2], row[3], classIndex, confidence);
    }

    private static void addCandidate(
            List<Detection> candidates,
            float centerX,
            float centerY,
            float width,
            float height,
            int classIndex,
            float confidence
    ) throws IOException {
        if (classIndex < 0 || confidence < CONFIDENCE_FLOOR || !VISIBLE_FOOD_LABELS.contains(COCO_NAMES[classIndex])) {
            return;
        }
        if (!Float.isFinite(centerX) || !Float.isFinite(centerY)
                || !Float.isFinite(width) || !Float.isFinite(height)
                || width <= 0.0f || height <= 0.0f) {
            throw new IOException("YOLO-World box coordinates are not finite and positive");
        }
        float scale = 1.0f / IMAGE_SIZE;
        float left = PocInferenceMath.clamp01((centerX - width * 0.5f) * scale);
        float top = PocInferenceMath.clamp01((centerY - height * 0.5f) * scale);
        float right = PocInferenceMath.clamp01((centerX + width * 0.5f) * scale);
        float bottom = PocInferenceMath.clamp01((centerY + height * 0.5f) * scale);
        if (right > left && bottom > top) {
            candidates.add(new Detection(COCO_NAMES[classIndex], confidence, left, top, right, bottom));
        }
    }

    public static final class Detection {
        private final String label;
        private final float confidence;
        private final float left;
        private final float top;
        private final float right;
        private final float bottom;

        private Detection(String label, float confidence, float left, float top, float right, float bottom) {
            this.label = label;
            this.confidence = confidence;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public String label() {
            return label;
        }

        public float confidence() {
            return confidence;
        }

        public float left() {
            return left;
        }

        public float top() {
            return top;
        }

        public float right() {
            return right;
        }

        public float bottom() {
            return bottom;
        }
    }
}
