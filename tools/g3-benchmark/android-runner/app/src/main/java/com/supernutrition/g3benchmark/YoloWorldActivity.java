package com.supernutrition.g3benchmark;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.PowerManager;
import android.util.Log;
import android.widget.TextView;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Research-only Android execution of an exported YOLO-World v2 with its
 * embedded COCO vocabulary. It deliberately does not present COCO labels as
 * a complete ingredient vocabulary or a grams estimator.
 */
public final class YoloWorldActivity extends Activity {
    private static final String TAG = "SuperNutritionYoloWorld";
    private static final String MODEL_ASSET = "yolov8s-worldv2-coco.onnx";
    private static final String MODEL_SHA256 =
            "d27ac81640ca470cc7e42edd638b6626ac6b4f4dcce74aabb6e09cd02a13c84f";
    private static final String RESULT_FILE = "yolo-world-v2-result.json";
    private static final int IMAGE_SIZE = 640;
    private static final float CONFIDENCE_FLOOR = 0.10f;
    private static final float NMS_IOU = 0.70f;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        status = new TextView(this);
        status.setText("YOLO-World v2 research benchmark running…");
        status.setTextSize(20);
        setContentView(status);
        executor.execute(this::runBenchmark);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void runBenchmark() {
        try {
            new File(getFilesDir(), RESULT_FILE).delete();
            File modelFile = copyAsset(MODEL_ASSET);
            long pssBeforeKb = Debug.getPss();
            int thermalBefore = thermalStatus();
            OrtEnvironment environment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.EXTENDED_OPT);
            options.setIntraOpNumThreads(Math.max(1, Runtime.getRuntime().availableProcessors()));
            long initializationStartNs = System.nanoTime();
            try (OrtSession session = environment.createSession(modelFile.getAbsolutePath(), options)) {
                long sessionInitMs = elapsedMs(initializationStartNs);
                String inputName = requireOnlyInput(session);
                runOnce(environment, session, inputName, CocoFoodBenchmark.FIXTURES[0], 0);
                List<Long> elapsed = new ArrayList<>();
                List<FixtureRun> runs = new ArrayList<>();
                for (int index = 0; index < CocoFoodBenchmark.FIXTURES.length; index++) {
                    long startNs = System.nanoTime();
                    FixtureRun run = runOnce(environment, session, inputName, CocoFoodBenchmark.FIXTURES[index], index);
                    elapsed.add(elapsedMs(startNs));
                    runs.add(run);
                }
                String result = resultJson(
                        sessionInitMs,
                        elapsed,
                        runs,
                        pssBeforeKb,
                        Debug.getPss(),
                        thermalBefore,
                        thermalStatus()
                );
                writeResult(result);
                Log.i(TAG, result);
                runOnUiThread(() -> status.setText("YOLO-World v2 research benchmark complete"));
            }
        } catch (Throwable error) {
            String failure = "{\"status\":\"error\",\"candidate\":\"yolo-world-v2\",\"message\":\""
                    + jsonEscape(error.toString()) + "\"}";
            try {
                writeResult(failure);
            } catch (IOException ignored) {
                // Logcat remains the failure record if app-private storage fails.
            }
            Log.e(TAG, failure, error);
            runOnUiThread(() -> status.setText("YOLO-World v2 research benchmark failed"));
        }
    }

    private FixtureRun runOnce(
            OrtEnvironment environment,
            OrtSession session,
            String inputName,
            String fixture,
            int fixtureIndex
    ) throws Exception {
        try (OnnxTensor input = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(preprocess(fixture)),
                new long[]{1, 3, IMAGE_SIZE, IMAGE_SIZE}
        ); OrtSession.Result result = session.run(Collections.singletonMap(inputName, input))) {
            OnnxValue value = result.get(0);
            Object raw = value.getValue();
            if (!(raw instanceof float[][][])) {
                throw new IOException("Expected YOLO-World output float[1][84][8400], got " + raw.getClass());
            }
            float[][][] output = (float[][][]) raw;
            if (output.length != 1 || output[0].length != 84) {
                throw new IOException("Unexpected YOLO-World output shape");
            }
            List<Detection> detections = nonMaximumSuppress(output[0]);
            return new FixtureRun(fixture, CocoFoodBenchmark.EXPECTED_VISIBLE_COMPONENTS[fixtureIndex], detections);
        }
    }

    private List<Detection> nonMaximumSuppress(float[][] output) throws IOException {
        int predictionCount = output[0].length;
        if (predictionCount < 1) {
            throw new IOException("YOLO-World output has no predictions");
        }
        List<Detection> candidates = new ArrayList<>();
        for (int prediction = 0; prediction < predictionCount; prediction++) {
            int classIndex = 0;
            float confidence = 0.0f;
            for (int candidateClass = 0; candidateClass < CocoFoodBenchmark.COCO_NAMES.length; candidateClass++) {
                float score = output[4 + candidateClass][prediction];
                if (score > confidence) {
                    confidence = score;
                    classIndex = candidateClass;
                }
            }
            if (confidence < CONFIDENCE_FLOOR) {
                continue;
            }
            float centerX = output[0][prediction];
            float centerY = output[1][prediction];
            float width = output[2][prediction];
            float height = output[3][prediction];
            candidates.add(new Detection(
                    classIndex,
                    confidence,
                    centerX - width / 2.0f,
                    centerY - height / 2.0f,
                    centerX + width / 2.0f,
                    centerY + height / 2.0f
            ));
        }
        Collections.sort(candidates, Comparator.comparingDouble((Detection item) -> item.confidence).reversed());
        List<Detection> kept = new ArrayList<>();
        for (Detection candidate : candidates) {
            boolean overlaps = false;
            for (Detection existing : kept) {
                if (existing.classIndex == candidate.classIndex && iou(existing, candidate) > NMS_IOU) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                kept.add(candidate);
            }
        }
        return kept;
    }

    private float[] preprocess(String fixture) throws IOException {
        try (InputStream input = getAssets().open("fixtures/" + fixture)) {
            Bitmap source = BitmapFactory.decodeStream(input);
            if (source == null) {
                throw new IOException("Unable to decode " + fixture);
            }
            Bitmap resized = Bitmap.createScaledBitmap(source, IMAGE_SIZE, IMAGE_SIZE, true);
            float[] output = new float[3 * IMAGE_SIZE * IMAGE_SIZE];
            for (int y = 0; y < IMAGE_SIZE; y++) {
                for (int x = 0; x < IMAGE_SIZE; x++) {
                    int pixel = resized.getPixel(x, y);
                    int offset = y * IMAGE_SIZE + x;
                    output[offset] = ((pixel >> 16) & 0xff) / 255.0f;
                    output[IMAGE_SIZE * IMAGE_SIZE + offset] = ((pixel >> 8) & 0xff) / 255.0f;
                    output[2 * IMAGE_SIZE * IMAGE_SIZE + offset] = (pixel & 0xff) / 255.0f;
                }
            }
            resized.recycle();
            source.recycle();
            return output;
        }
    }

    private static String requireOnlyInput(OrtSession session) throws IOException {
        if (session.getInputNames().size() != 1) {
            throw new IOException("Expected one YOLO-World input, got " + session.getInputNames());
        }
        return session.getInputNames().iterator().next();
    }

    private File copyAsset(String name) throws IOException {
        File target = new File(getFilesDir(), name);
        if (target.isFile()) {
            return target;
        }
        try (InputStream source = getAssets().open(name);
             FileOutputStream destination = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = source.read(buffer)) != -1) {
                destination.write(buffer, 0, count);
            }
        }
        return target;
    }

    private void writeResult(String result) throws IOException {
        try (FileOutputStream output = new FileOutputStream(new File(getFilesDir(), RESULT_FILE))) {
            output.write(result.getBytes(StandardCharsets.UTF_8));
        }
    }

    private int thermalStatus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return -1;
        }
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        return powerManager == null ? -1 : powerManager.getCurrentThermalStatus();
    }

    private static long elapsedMs(long startedNs) {
        return (System.nanoTime() - startedNs) / 1_000_000L;
    }

    private static float iou(Detection first, Detection second) {
        float left = Math.max(first.left, second.left);
        float top = Math.max(first.top, second.top);
        float right = Math.min(first.right, second.right);
        float bottom = Math.min(first.bottom, second.bottom);
        float intersection = Math.max(0.0f, right - left) * Math.max(0.0f, bottom - top);
        float firstArea = Math.max(0.0f, first.right - first.left) * Math.max(0.0f, first.bottom - first.top);
        float secondArea = Math.max(0.0f, second.right - second.left) * Math.max(0.0f, second.bottom - second.top);
        float union = firstArea + secondArea - intersection;
        return union <= 0.0f ? 0.0f : intersection / union;
    }

    private static String resultJson(
            long sessionInitMs,
            List<Long> elapsed,
            List<FixtureRun> runs,
            long pssBeforeKb,
            long pssAfterKb,
            int thermalBefore,
            int thermalAfter
    ) {
        long[] timings = new long[elapsed.size()];
        float macroF1 = 0.0f;
        for (int index = 0; index < elapsed.size(); index++) {
            timings[index] = elapsed.get(index);
            macroF1 += runs.get(index).f1();
        }
        Arrays.sort(timings);
        macroF1 /= runs.size();
        StringBuilder json = new StringBuilder();
        json.append("{\"status\":\"success\",\"candidate\":\"yolo-world-v2\",")
                .append("\"runtime\":\"onnxruntime-android\",\"runtime_version\":\"1.28.0\",")
                .append("\"model\":\"").append(MODEL_ASSET).append("\",\"model_sha256\":\"")
                .append(MODEL_SHA256).append("\",")
                .append("\"vocabulary\":\"embedded COCO-80 only; no custom ingredient prompts\",")
                .append("\"ingredient_benchmark\":{\"status\":\"limited\",\"scope\":\"exact labels in COCO food subset only\",")
                .append("\"macro_f1\":").append(formatFloat(macroF1)).append(",\"fixture_count\":").append(runs.size())
                .append("},\"weight_benchmark\":{\"status\":\"not-evaluable\",\"reason\":\"YOLO-World v2 has no mass output and fixtures have no scale ground truth.\"},")
                .append("\"session_init_ms\":").append(sessionInitMs).append(",")
                .append("\"inference_p50_ms\":").append(percentile(timings, 0.50)).append(",")
                .append("\"inference_p95_ms\":").append(percentile(timings, 0.95)).append(",")
                .append("\"inference_max_ms\":").append(timings[timings.length - 1]).append(",")
                .append("\"pss_before_kb\":").append(pssBeforeKb).append(",")
                .append("\"pss_after_kb\":").append(pssAfterKb).append(",")
                .append("\"thermal_before\":").append(thermalBefore).append(",")
                .append("\"thermal_after\":").append(thermalAfter).append(",")
                .append("\"fixture_results\":[");
        for (int index = 0; index < runs.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            FixtureRun run = runs.get(index);
            json.append("{\"fixture\":\"").append(run.fixture).append("\",")
                    .append("\"food_detections\":").append(run.foodDetectionsJson()).append(",")
                    .append("\"exact_visible_component_f1\":").append(formatFloat(run.f1())).append("}");
        }
        return json.append("]}").toString();
    }

    private static long percentile(long[] values, double percentile) {
        int index = (int) Math.ceil(percentile * values.length) - 1;
        return values[Math.max(0, Math.min(index, values.length - 1))];
    }

    private static String formatFloat(float value) {
        return String.format(Locale.US, "%.4f", value);
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class Detection {
        private final int classIndex;
        private final float confidence;
        private final float left;
        private final float top;
        private final float right;
        private final float bottom;

        private Detection(int classIndex, float confidence, float left, float top, float right, float bottom) {
            this.classIndex = classIndex;
            this.confidence = confidence;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    private static final class FixtureRun {
        private final String fixture;
        private final Set<String> expected;
        private final List<Detection> detections;

        private FixtureRun(String fixture, String[] expected, List<Detection> detections) {
            this.fixture = fixture;
            this.expected = new HashSet<>(Arrays.asList(expected));
            this.detections = detections;
        }

        private Set<String> predictedFoodLabels() {
            Set<String> labels = new LinkedHashSet<>();
            for (Detection detection : detections) {
                String label = CocoFoodBenchmark.COCO_NAMES[detection.classIndex];
                if (CocoFoodBenchmark.COCO_FOOD_LABELS.contains(label)) {
                    labels.add(label);
                }
            }
            return labels;
        }

        private float f1() {
            Set<String> predicted = predictedFoodLabels();
            int truePositive = 0;
            for (String label : predicted) {
                if (expected.contains(label)) {
                    truePositive++;
                }
            }
            int falsePositive = predicted.size() - truePositive;
            int falseNegative = expected.size() - truePositive;
            int denominator = 2 * truePositive + falsePositive + falseNegative;
            return denominator == 0 ? 0.0f : (2.0f * truePositive) / denominator;
        }

        private String foodDetectionsJson() {
            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            Set<String> seen = new HashSet<>();
            for (Detection detection : detections) {
                String label = CocoFoodBenchmark.COCO_NAMES[detection.classIndex];
                if (!CocoFoodBenchmark.COCO_FOOD_LABELS.contains(label) || !seen.add(label)) {
                    continue;
                }
                if (!first) {
                    json.append(',');
                }
                first = false;
                json.append("{\"label\":\"").append(label).append("\",\"confidence\":")
                        .append(formatFloat(detection.confidence)).append("}");
            }
            return json.append(']').toString();
        }
    }
}
