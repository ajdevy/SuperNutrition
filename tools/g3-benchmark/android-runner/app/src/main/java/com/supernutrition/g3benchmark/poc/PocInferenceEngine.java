package com.supernutrition.g3benchmark.poc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Standalone, research-only Android PoC inference pipeline.
 *
 * <p>The pipeline is deliberately not production mobile code. Food-101 is
 * used only for dish candidates, YOLO-World is restricted to a small COCO
 * food-label subset for region proposals, and MobileSAM is class-agnostic
 * mask refinement. No ingredient decomposition, nutrition binding, or mass
 * estimation is performed.</p>
 */
public final class PocInferenceEngine implements AutoCloseable {
    private static final String DISH_MODEL = "swin-finetuned-food101-matmul-int8.onnx";
    private static final String DISH_LABELS = "food101-labels.txt";
    private static final String REGION_MODEL = "yolov8s-worldv2-coco.onnx";
    private static final String SAM_ENCODER = "mobile-sam-encoder.onnx";
    private static final String SAM_DECODER = "mobile-sam-decoder.onnx";
    private static final int DISH_SIZE = 224;
    private static final int REGION_SIZE = 640;
    private static final int SAM_SIZE = 1024;
    private static final int MAX_REGIONS = 16;
    private static final float REGION_CONFIDENCE = 0.10f;
    private static final float REGION_NMS_IOU = 0.70f;
    private static final int MASK_MAX_SIDE = 512;
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
    /**
     * Only labels that can plausibly be shown as visible component hints are
     * retained. Composite dish labels are deliberately excluded: they are
     * not ingredients and produced misleading results such as "pizza" on a
     * soup photo in the first device PoC.
     */
    private static final Set<String> COCO_COMPONENT_LABELS = new HashSet<>(Arrays.asList(
            "banana", "apple", "orange", "broccoli", "carrot"
    ));

    private final Context context;
    private final OrtEnvironment environment;
    private OrtSession dishSession;
    private OrtSession regionSession;
    private OrtSession samEncoderSession;
    private OrtSession samDecoderSession;
    private String dishInput;
    private String regionInput;
    private String samEncoderInput;
    private boolean closed;

    /** Creates an engine; model sessions are loaded lazily by {@link #analyze(Bitmap)}. */
    public PocInferenceEngine(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        this.context = context.getApplicationContext() == null ? context : context.getApplicationContext();
        this.environment = OrtEnvironment.getEnvironment();
    }

    /**
     * Runs all available research stages. A model/runtime shape mismatch is
     * represented as a warning and an empty stage result instead of escaping
     * from this method; invalid input and use-after-close remain programming
     * errors and are reported through the declared Exception API.
     */
    public synchronized PocAnalysis analyze(Bitmap bitmap) throws Exception {
        return analyze(bitmap, -1);
    }

    /**
     * Runs the pipeline with an optional user-selected 3x3 grid prompt.
     * The prompt creates a class-agnostic region named {@code grid_prompt};
     * it is never presented as an ingredient prediction.
     */
    public synchronized PocAnalysis analyze(Bitmap bitmap, int gridCellIndex) throws Exception {
        ensureOpen();
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            throw new IllegalArgumentException("bitmap must be non-null, live, and non-empty");
        }
        if (gridCellIndex < -1 || gridCellIndex >= 9) {
            throw new IllegalArgumentException("grid cell must be -1 or in [0, 8]");
        }

        List<PocAnalysis.DishCandidate> dishes = new ArrayList<>();
        List<RawRegion> detectorProposals = new ArrayList<>();
        List<PocAnalysis.Region> regions = new ArrayList<>();
        List<PocAnalysis.IngredientSuggestion> ingredientSuggestions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        warnings.add("research-only output: dish labels and limited COCO proposals are not full ingredients; grams are never estimated");

        try {
            dishes.addAll(runDish(bitmap));
        } catch (Exception error) {
            warnings.add("dish pipeline unavailable: " + reason(error));
        }

        try {
            detectorProposals.addAll(runRegionProposals(bitmap));
        } catch (Exception error) {
            warnings.add("COCO region proposal pipeline unavailable: " + reason(error));
        }

        ingredientSuggestions.addAll(buildIngredientSuggestions(dishes, detectorProposals));
        List<RawRegion> proposals = applySuggestionFirstFusion(dishes, detectorProposals, warnings);

        if (gridCellIndex >= 0) {
            float[] bounds = PocInferenceMath.gridCellBounds(gridCellIndex);
            proposals.add(0, new RawRegion(
                    "grid_prompt", 0.0f, bounds[0], bounds[1], bounds[2], bounds[3]));
            if (proposals.size() > MAX_REGIONS) {
                proposals.remove(proposals.size() - 1);
            }
            warnings.add("grid prompt is class-agnostic; its mask does not identify an ingredient");
        }

        if (!proposals.isEmpty()) {
            boolean samAvailable = true;
            try {
                ensureSamSessions();
            } catch (Exception error) {
                samAvailable = false;
                warnings.add("MobileSAM mask refinement unavailable: " + reason(error));
            }
            for (RawRegion proposal : proposals) {
                Bitmap mask = null;
                if (samAvailable) {
                    try {
                        mask = refineMask(bitmap, proposal);
                    } catch (Exception error) {
                        warnings.add("mask refinement unavailable for " + proposal.label + ": " + reason(error));
                    }
                }
                regions.add(new PocAnalysis.Region(
                        proposal.label,
                        proposal.confidence,
                        proposal.left,
                        proposal.top,
                        proposal.right,
                        proposal.bottom,
                        mask,
                        PocInferenceMath.gridCellIndex(
                                proposal.left, proposal.top, proposal.right, proposal.bottom
                        )
                ));
            }
        } else {
            warnings.add("no valid COCO food region proposals were produced");
        }

        String status = dishes.isEmpty() && regions.isEmpty()
                ? "fallback"
                : (dishes.isEmpty() || regions.isEmpty() ? "partial" : "success");
        return new PocAnalysis(dishes, regions, ingredientSuggestions, status, warnings);
    }

    /** Closes all owned ONNX sessions. It is safe to call more than once. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        closeSession(samDecoderSession);
        closeSession(samEncoderSession);
        closeSession(regionSession);
        closeSession(dishSession);
        samDecoderSession = null;
        samEncoderSession = null;
        regionSession = null;
        dishSession = null;
    }

    private List<PocAnalysis.DishCandidate> runDish(Bitmap source) throws Exception {
        ensureDishSession();
        Bitmap resized = null;
        try {
            resized = squareResize(source, DISH_SIZE);
            float[] pixels = toDishInput(resized);
            try (OnnxTensor input = OnnxTensor.createTensor(
                    environment, FloatBuffer.wrap(pixels), new long[]{1, 3, DISH_SIZE, DISH_SIZE}
            ); OrtSession.Result result = dishSession.run(Collections.singletonMap(dishInput, input))) {
                Object raw = result.get(0).getValue();
                float[] logits = flattenFloats(raw);
                if (logits.length < 5) {
                    throw new IOException("Food-101 output contains fewer than five scores");
                }
                List<String> labels = readLabels();
                if (labels.size() != logits.length) {
                    throw new IOException("Food-101 label count " + labels.size()
                            + " does not match output count " + logits.length);
                }
                float[] probabilities = PocInferenceMath.softmax(logits);
                int[] top = PocInferenceMath.topK(probabilities, 5);
                List<PocAnalysis.DishCandidate> candidates = new ArrayList<>(top.length);
                for (int index : top) {
                    if (index >= 0) {
                        candidates.add(new PocAnalysis.DishCandidate(labels.get(index), probabilities[index]));
                    }
                }
                return candidates;
            }
        } finally {
            recycleIfOwned(resized, source);
        }
    }

    private List<RawRegion> runRegionProposals(Bitmap source) throws Exception {
        ensureRegionSession();
        Bitmap resized = null;
        try {
            resized = resize(source, REGION_SIZE, REGION_SIZE);
            float[] pixels = toRgbInput(resized, REGION_SIZE);
            try (OnnxTensor input = OnnxTensor.createTensor(
                    environment, FloatBuffer.wrap(pixels), new long[]{1, 3, REGION_SIZE, REGION_SIZE}
            ); OrtSession.Result result = regionSession.run(Collections.singletonMap(regionInput, input))) {
                Object raw = result.get(0).getValue();
                if (!(raw instanceof float[][][])) {
                    throw new IOException("YOLO output is not a rank-3 float tensor: " + className(raw));
                }
                return parseYolo((float[][][]) raw);
            }
        } finally {
            recycleIfOwned(resized, source);
        }
    }

    private void ensureDishSession() throws Exception {
        if (dishSession != null) {
            return;
        }
        File model = copyAsset(DISH_MODEL);
        OrtSession.SessionOptions options = newSessionOptions();
        try {
            dishSession = environment.createSession(model.getAbsolutePath(), options);
            dishInput = requireOnlyInput(dishSession, "Food-101");
        } finally {
            options.close();
        }
    }

    private void ensureRegionSession() throws Exception {
        if (regionSession != null) {
            return;
        }
        File model = copyAsset(REGION_MODEL);
        OrtSession.SessionOptions options = newSessionOptions();
        try {
            regionSession = environment.createSession(model.getAbsolutePath(), options);
            regionInput = requireOnlyInput(regionSession, "YOLO-World");
        } finally {
            options.close();
        }
    }

    private void ensureSamSessions() throws Exception {
        if (samEncoderSession != null && samDecoderSession != null) {
            return;
        }
        File encoder = copyAsset(SAM_ENCODER);
        File decoder = copyAsset(SAM_DECODER);
        OrtSession.SessionOptions options = newSessionOptions();
        try {
            samEncoderSession = environment.createSession(encoder.getAbsolutePath(), options);
            samDecoderSession = environment.createSession(decoder.getAbsolutePath(), options);
            samEncoderInput = requireOnlyInput(samEncoderSession, "MobileSAM encoder");
            requireDecoderInputs(samDecoderSession);
        } catch (Exception error) {
            closeSession(samDecoderSession);
            closeSession(samEncoderSession);
            samDecoderSession = null;
            samEncoderSession = null;
            throw error;
        } finally {
            options.close();
        }
    }

    private Bitmap refineMask(Bitmap source, RawRegion proposal) throws Exception {
        Bitmap resized = null;
        try {
            resized = resize(source, SAM_SIZE, SAM_SIZE);
            float[] pixels = toSamInput(resized);
            try (OnnxTensor image = OnnxTensor.createTensor(
                    environment,
                    FloatBuffer.wrap(pixels),
                    new long[]{SAM_SIZE, SAM_SIZE, 3}
            ); OrtSession.Result encoderResult = samEncoderSession.run(
                    Collections.singletonMap(samEncoderInput, image))) {
                OnnxValue embeddingValue = encoderResult.get(0);
                if (!(embeddingValue instanceof OnnxTensor)) {
                    throw new IOException("MobileSAM encoder did not return a tensor embedding");
                }
                return decodeMask(source, proposal, (OnnxTensor) embeddingValue);
            }
        } finally {
            recycleIfOwned(resized, source);
        }
    }

    private Bitmap decodeMask(Bitmap source, RawRegion proposal, OnnxTensor embedding) throws Exception {
        float centerX = (proposal.left + proposal.right) * 0.5f;
        float centerY = (proposal.top + proposal.bottom) * 0.5f;
        float padding = Math.max(0.02f, Math.min(0.15f, Math.max(
                proposal.right - proposal.left, proposal.bottom - proposal.top
        ) * 0.25f));
        float negativeX = proposal.left - padding >= 0.0f
                ? proposal.left - padding : Math.min(1.0f, proposal.right + padding);
        float negativeY = proposal.top - padding >= 0.0f
                ? proposal.top - padding : Math.min(1.0f, proposal.bottom + padding);
        try (OnnxTensor pointCoords = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(new float[]{
                        centerX * SAM_SIZE, centerY * SAM_SIZE,
                        negativeX * SAM_SIZE, negativeY * SAM_SIZE
                }),
                new long[]{1, 2, 2}
        ); OnnxTensor pointLabels = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(new float[]{1.0f, -1.0f}),
                new long[]{1, 2}
        ); OnnxTensor maskInput = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(new float[256 * 256]),
                new long[]{1, 1, 256, 256}
        ); OnnxTensor hasMaskInput = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(new float[]{0.0f}),
                new long[]{1}
        ); OnnxTensor originalImageSize = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(new float[]{SAM_SIZE, SAM_SIZE}),
                new long[]{2}
        )) {
            Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
            inputs.put("image_embeddings", embedding);
            inputs.put("point_coords", pointCoords);
            inputs.put("point_labels", pointLabels);
            inputs.put("mask_input", maskInput);
            inputs.put("has_mask_input", hasMaskInput);
            inputs.put("orig_im_size", originalImageSize);
            try (OrtSession.Result result = samDecoderSession.run(inputs)) {
                Object raw = result.get(0).getValue();
                float[][] mask = firstMask(raw);
                return toArgbMask(mask, source.getWidth(), source.getHeight());
            }
        }
    }

    private static List<RawRegion> parseYolo(float[][][] tensor) throws IOException {
        if (tensor.length != 1 || tensor[0].length == 0) {
            throw new IOException("Unexpected YOLO output batch/shape");
        }
        float[][] data = tensor[0];
        List<RawRegion> candidates = new ArrayList<>();
        if (data.length >= 4 + COCO_NAMES.length) {
            int predictionCount = data[0].length;
            for (int prediction = 0; prediction < predictionCount; prediction++) {
                addYoloCandidate(candidates, data[0][prediction], data[1][prediction],
                        data[2][prediction], data[3][prediction], data, prediction);
            }
        } else if (data.length > 0 && data[0].length >= 4 + COCO_NAMES.length) {
            for (float[] row : data) {
                addYoloCandidate(candidates, row);
            }
        } else {
            throw new IOException("Expected YOLO [1,84,N] or [1,N,84], got [1,"
                    + data.length + "," + data[0].length + "]");
        }
        Collections.sort(candidates, Comparator.comparingDouble((RawRegion item) -> item.confidence).reversed());
        List<RawRegion> kept = new ArrayList<>();
        for (RawRegion candidate : candidates) {
            boolean overlaps = false;
            for (RawRegion existing : kept) {
                if (existing.label.equals(candidate.label)
                        && PocInferenceMath.iou(existing.left, existing.top, existing.right, existing.bottom,
                        candidate.left, candidate.top, candidate.right, candidate.bottom) > REGION_NMS_IOU) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                kept.add(candidate);
                if (kept.size() == MAX_REGIONS) {
                    break;
                }
            }
        }
        return kept;
    }

    private static List<RawRegion> applySuggestionFirstFusion(
            List<PocAnalysis.DishCandidate> dishes,
            List<RawRegion> detectorProposals,
            List<String> warnings
    ) {
        Map<String, Float> support = PocFusionPolicy.compositionSupport(dishes);
        List<RawRegion> ranked = new ArrayList<>(detectorProposals);
        Collections.sort(ranked, (left, right) -> Float.compare(
                PocFusionPolicy.rankScore(
                        right.confidence,
                        PocFusionPolicy.normalizedSupport(support, right.label)),
                PocFusionPolicy.rankScore(
                        left.confidence,
                        PocFusionPolicy.normalizedSupport(support, left.label))
        ));
        if (!ranked.isEmpty()) {
            warnings.add("bounded late fusion applied; raw visual proposals retained and reranked");
        }
        warnings.add("dish-prior-only suggestions are composition hints; they never create visual overlays");
        return ranked;
    }

    private static List<PocAnalysis.IngredientSuggestion> buildIngredientSuggestions(
            List<PocAnalysis.DishCandidate> dishes,
            List<RawRegion> detectorProposals
    ) {
        Map<String, Float> compositionSupport = PocFusionPolicy.compositionSupport(dishes);
        List<PocAnalysis.IngredientSuggestion> suggestions = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String label : PocFusionPolicy.compositionLabels(dishes)) {
            float detectorConfidence = maxDetectorConfidence(detectorProposals, label);
            suggestions.add(new PocAnalysis.IngredientSuggestion(
                    label,
                    detectorConfidence,
                    true,
                    detectorConfidence >= 0.0f,
                    PocFusionPolicy.normalizedSupport(compositionSupport, label)
            ));
            seen.add(label);
        }
        for (RawRegion proposal : detectorProposals) {
            if (seen.add(proposal.label)) {
                suggestions.add(new PocAnalysis.IngredientSuggestion(
                        proposal.label,
                        proposal.confidence,
                        false,
                        true,
                        PocFusionPolicy.normalizedSupport(compositionSupport, proposal.label)
                ));
            }
        }
        return suggestions;
    }

    private static float maxDetectorConfidence(List<RawRegion> proposals, String label) {
        float confidence = -1.0f;
        for (RawRegion proposal : proposals) {
            if (label.equals(proposal.label)) {
                confidence = Math.max(confidence, proposal.confidence);
            }
        }
        return confidence;
    }

    private static void addYoloCandidate(
            List<RawRegion> candidates,
            float centerX,
            float centerY,
            float width,
            float height,
            float[][] data,
            int prediction
    ) {
        int classIndex = -1;
        float confidence = 0.0f;
        for (int candidateClass = 0; candidateClass < COCO_NAMES.length; candidateClass++) {
            float score = data[4 + candidateClass][prediction];
            if (score > confidence) {
                confidence = score;
                classIndex = candidateClass;
            }
        }
        if (classIndex < 0 || confidence < REGION_CONFIDENCE
                || !COCO_COMPONENT_LABELS.contains(COCO_NAMES[classIndex])) {
            return;
        }
        float scale = Math.max(Math.max(Math.abs(centerX), Math.abs(centerY)),
                Math.max(Math.abs(width), Math.abs(height))) <= 2.0f ? 1.0f : 1.0f / REGION_SIZE;
        float left = PocInferenceMath.clamp01((centerX - width * 0.5f) * scale);
        float top = PocInferenceMath.clamp01((centerY - height * 0.5f) * scale);
        float right = PocInferenceMath.clamp01((centerX + width * 0.5f) * scale);
        float bottom = PocInferenceMath.clamp01((centerY + height * 0.5f) * scale);
        if (right <= left || bottom <= top) {
            return;
        }
        candidates.add(new RawRegion(COCO_NAMES[classIndex], confidence, left, top, right, bottom));
    }

    private static void addYoloCandidate(List<RawRegion> candidates, float[] row) {
        int classIndex = -1;
        float confidence = 0.0f;
        for (int candidateClass = 0; candidateClass < COCO_NAMES.length; candidateClass++) {
            float score = row[4 + candidateClass];
            if (score > confidence) {
                confidence = score;
                classIndex = candidateClass;
            }
        }
        if (classIndex < 0 || confidence < REGION_CONFIDENCE
                || !COCO_COMPONENT_LABELS.contains(COCO_NAMES[classIndex])) {
            return;
        }
        float centerX = row[0];
        float centerY = row[1];
        float width = row[2];
        float height = row[3];
        float scale = Math.max(Math.max(Math.abs(centerX), Math.abs(centerY)),
                Math.max(Math.abs(width), Math.abs(height))) <= 2.0f ? 1.0f : 1.0f / REGION_SIZE;
        float left = PocInferenceMath.clamp01((centerX - width * 0.5f) * scale);
        float top = PocInferenceMath.clamp01((centerY - height * 0.5f) * scale);
        float right = PocInferenceMath.clamp01((centerX + width * 0.5f) * scale);
        float bottom = PocInferenceMath.clamp01((centerY + height * 0.5f) * scale);
        if (right > left && bottom > top) {
            candidates.add(new RawRegion(COCO_NAMES[classIndex], confidence, left, top, right, bottom));
        }
    }

    private static float[][] firstMask(Object raw) throws IOException {
        if (!(raw instanceof float[][][][])) {
            throw new IOException("MobileSAM output is not a rank-4 float mask: " + className(raw));
        }
        float[][][][] masks = (float[][][][]) raw;
        if (masks.length != 1 || masks[0].length == 0 || masks[0][0].length == 0
                || masks[0][0][0].length == 0) {
            throw new IOException("Unexpected MobileSAM mask shape");
        }
        return masks[0][0];
    }

    private static Bitmap toArgbMask(float[][] mask, int sourceWidth, int sourceHeight) {
        float scale = Math.min(1.0f, MASK_MAX_SIDE / (float) Math.max(sourceWidth, sourceHeight));
        int width = Math.max(1, Math.round(sourceWidth * scale));
        int height = Math.max(1, Math.round(sourceHeight * scale));
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int maskHeight = mask.length;
        int maskWidth = mask[0].length;
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int maskY = Math.min(maskHeight - 1, y * maskHeight / height);
            for (int x = 0; x < width; x++) {
                int maskX = Math.min(maskWidth - 1, x * maskWidth / width);
                float value = mask[maskY][maskX];
                float threshold = value >= 0.0f && value <= 1.0f ? 0.5f : 0.0f;
                pixels[y * width + x] = value > threshold ? Color.WHITE : Color.TRANSPARENT;
            }
        }
        result.setPixels(pixels, 0, width, 0, 0, width, height);
        return result;
    }

    private static float[] flattenFloats(Object raw) throws IOException {
        List<Float> values = new ArrayList<>();
        flattenFloats(raw, values);
        if (values.isEmpty()) {
            throw new IOException("ONNX output contains no float scores");
        }
        float[] result = new float[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index);
        }
        return result;
    }

    private static void flattenFloats(Object raw, List<Float> values) throws IOException {
        if (raw instanceof float[]) {
            for (float value : (float[]) raw) {
                values.add(value);
            }
        } else if (raw instanceof float[][]) {
            for (float[] row : (float[][]) raw) {
                flattenFloats(row, values);
            }
        } else if (raw instanceof float[][][]) {
            for (float[][] row : (float[][][]) raw) {
                flattenFloats(row, values);
            }
        } else if (raw instanceof float[][][][]) {
            for (float[][][] row : (float[][][][]) raw) {
                flattenFloats(row, values);
            }
        } else {
            throw new IOException("ONNX output is not a supported float tensor: " + className(raw));
        }
    }

    private List<String> readLabels() throws IOException {
        List<String> labels = new ArrayList<>();
        try (InputStream input = context.getAssets().open(DISH_LABELS);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    labels.add(line.trim());
                }
            }
        }
        return labels;
    }

    private File copyAsset(String name) throws IOException {
        File target = new File(context.getCacheDir(), name);
        if (target.isFile() && target.length() > 0L) {
            return target;
        }
        try (InputStream source = context.getAssets().open(name);
             FileOutputStream destination = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = source.read(buffer)) != -1) {
                destination.write(buffer, 0, count);
            }
        }
        return target;
    }

    private static OrtSession.SessionOptions newSessionOptions() throws Exception {
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.EXTENDED_OPT);
        options.setIntraOpNumThreads(Math.max(1, Runtime.getRuntime().availableProcessors()));
        return options;
    }

    private static String requireOnlyInput(OrtSession session, String role) throws IOException {
        if (session.getInputNames().size() != 1) {
            throw new IOException("Expected one " + role + " input, got " + session.getInputNames());
        }
        return session.getInputNames().iterator().next();
    }

    private static void requireDecoderInputs(OrtSession session) throws IOException {
        String[] expected = {"image_embeddings", "point_coords", "point_labels", "mask_input", "has_mask_input", "orig_im_size"};
        if (!session.getInputNames().containsAll(Arrays.asList(expected))) {
            throw new IOException("Unexpected MobileSAM decoder inputs " + session.getInputNames());
        }
    }

    private static Bitmap squareResize(Bitmap source, int side) {
        int crop = Math.min(source.getWidth(), source.getHeight());
        int left = (source.getWidth() - crop) / 2;
        int top = (source.getHeight() - crop) / 2;
        Bitmap cropped = Bitmap.createBitmap(source, left, top, crop, crop);
        Bitmap resized = resize(cropped, side, side);
        recycleIfOwned(cropped, resized);
        return resized;
    }

    private static Bitmap resize(Bitmap source, int width, int height) {
        return Bitmap.createScaledBitmap(source, width, height, true);
    }

    private static float[] toDishInput(Bitmap bitmap) {
        int plane = DISH_SIZE * DISH_SIZE;
        float[] output = new float[3 * plane];
        for (int y = 0; y < DISH_SIZE; y++) {
            for (int x = 0; x < DISH_SIZE; x++) {
                int pixel = bitmap.getPixel(x, y);
                int offset = y * DISH_SIZE + x;
                output[offset] = normalize((pixel >> 16) & 0xff, 0.485f, 0.229f);
                output[plane + offset] = normalize((pixel >> 8) & 0xff, 0.456f, 0.224f);
                output[2 * plane + offset] = normalize(pixel & 0xff, 0.406f, 0.225f);
            }
        }
        return output;
    }

    private static float[] toRgbInput(Bitmap bitmap, int side) {
        int plane = side * side;
        float[] output = new float[3 * plane];
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                int pixel = bitmap.getPixel(x, y);
                int offset = y * side + x;
                output[offset] = ((pixel >> 16) & 0xff) / 255.0f;
                output[plane + offset] = ((pixel >> 8) & 0xff) / 255.0f;
                output[2 * plane + offset] = (pixel & 0xff) / 255.0f;
            }
        }
        return output;
    }

    private static float[] toSamInput(Bitmap bitmap) {
        float[] output = new float[SAM_SIZE * SAM_SIZE * 3];
        for (int y = 0; y < SAM_SIZE; y++) {
            for (int x = 0; x < SAM_SIZE; x++) {
                int pixel = bitmap.getPixel(x, y);
                int offset = (y * SAM_SIZE + x) * 3;
                output[offset] = (((pixel >> 16) & 0xff) - 123.675f) / 58.395f;
                output[offset + 1] = (((pixel >> 8) & 0xff) - 116.28f) / 57.12f;
                output[offset + 2] = ((pixel & 0xff) - 103.53f) / 57.375f;
            }
        }
        return output;
    }

    private static float normalize(int value, float mean, float standardDeviation) {
        return (value / 255.0f - mean) / standardDeviation;
    }

    private static void recycleIfOwned(Bitmap bitmap, Bitmap source) {
        if (bitmap != null && bitmap != source && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("PocInferenceEngine is closed");
        }
    }

    private static void closeSession(OrtSession session) {
        if (session != null) {
            try {
                session.close();
            } catch (Exception ignored) {
                // close() is best effort during a terminal resource transition.
            }
        }
    }

    private static String reason(Exception error) {
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null || message.isEmpty() ? "" : ": " + message);
    }

    private static String className(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }

    private static final class RawRegion {
        private final String label;
        private final float confidence;
        private final float left;
        private final float top;
        private final float right;
        private final float bottom;

        private RawRegion(String label, float confidence, float left, float top, float right, float bottom) {
            this.label = label;
            this.confidence = confidence;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }
}
