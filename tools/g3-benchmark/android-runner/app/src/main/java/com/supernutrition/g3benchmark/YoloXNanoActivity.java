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

/** Research-only physical-device screen for the official YOLOX-Nano COCO ONNX release. */
public final class YoloXNanoActivity extends Activity {
    private static final String TAG = "SuperNutritionYoloXNano";
    private static final String MODEL_ASSET = "yolox_nano.onnx";
    private static final String MODEL_SHA256 = "c789161ed43c8269fcd4e67c67eeeb4e80c622da2eb296a20bc6007bd18a0b7d";
    private static final String RESULT_FILE = "yolox-nano-result.json";
    private static final int IMAGE_SIZE = 416;
    private static final float CONFIDENCE_FLOOR = 0.10f;
    private static final float NMS_IOU = 0.45f;
    private static final int[] STRIDES = {8, 16, 32};

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        status = new TextView(this);
        status.setText("YOLOX-Nano research benchmark running…");
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
            long pssBeforeKb = Debug.getPss();
            int thermalBefore = thermalStatus();
            OrtEnvironment environment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.EXTENDED_OPT);
            options.setIntraOpNumThreads(Math.max(1, Runtime.getRuntime().availableProcessors()));
            long started = System.nanoTime();
            try (OrtSession session = environment.createSession(copyAsset(MODEL_ASSET).getAbsolutePath(), options)) {
                long sessionInitMs = elapsedMs(started);
                String inputName = requireOnlyInput(session);
                runOnce(environment, session, inputName, CocoFoodBenchmark.FIXTURES[0], 0);
                List<Long> elapsed = new ArrayList<>();
                List<FixtureRun> runs = new ArrayList<>();
                for (int index = 0; index < CocoFoodBenchmark.FIXTURES.length; index++) {
                    long inferenceStarted = System.nanoTime();
                    FixtureRun run = runOnce(environment, session, inputName, CocoFoodBenchmark.FIXTURES[index], index);
                    elapsed.add(elapsedMs(inferenceStarted));
                    runs.add(run);
                }
                String result = resultJson(sessionInitMs, elapsed, runs, pssBeforeKb, Debug.getPss(), thermalBefore, thermalStatus());
                writeResult(result);
                Log.i(TAG, result);
                runOnUiThread(() -> status.setText("YOLOX-Nano research benchmark complete"));
            }
        } catch (Throwable error) {
            String failure = "{\"status\":\"error\",\"candidate\":\"yolox-nano-coco\",\"message\":\"" + jsonEscape(error.toString()) + "\"}";
            try {
                writeResult(failure);
            } catch (IOException ignored) {
                // Logcat remains the failure record if app-private storage fails.
            }
            Log.e(TAG, failure, error);
            runOnUiThread(() -> status.setText("YOLOX-Nano research benchmark failed"));
        }
    }

    private FixtureRun runOnce(OrtEnvironment environment, OrtSession session, String inputName, String fixture, int fixtureIndex) throws Exception {
        try (OnnxTensor input = OnnxTensor.createTensor(environment, FloatBuffer.wrap(preprocess(fixture)), new long[]{1, 3, IMAGE_SIZE, IMAGE_SIZE});
             OrtSession.Result result = session.run(Collections.singletonMap(inputName, input))) {
            OnnxValue value = result.get(0);
            Object raw = value.getValue();
            if (!(raw instanceof float[][][])) {
                throw new IOException("Expected YOLOX output float[1][cells][85], got " + raw.getClass());
            }
            float[][][] output = (float[][][]) raw;
            if (output.length != 1) {
                throw new IOException("Unexpected YOLOX output batch " + output.length);
            }
            return new FixtureRun(fixture, CocoFoodBenchmark.EXPECTED_VISIBLE_COMPONENTS[fixtureIndex], decodeAndSuppress(output[0]));
        }
    }

    private static List<Detection> decodeAndSuppress(float[][] output) throws IOException {
        int expectedCells = 0;
        for (int stride : STRIDES) {
            int cellsPerSide = IMAGE_SIZE / stride;
            expectedCells += cellsPerSide * cellsPerSide;
        }
        if (output.length != expectedCells) {
            throw new IOException("Expected " + expectedCells + " YOLOX cells, got " + output.length);
        }
        List<Detection> candidates = new ArrayList<>();
        int offset = 0;
        for (int stride : STRIDES) {
            int cellsPerSide = IMAGE_SIZE / stride;
            for (int y = 0; y < cellsPerSide; y++) {
                for (int x = 0; x < cellsPerSide; x++, offset++) {
                    float[] prediction = output[offset];
                    if (prediction.length != 85) {
                        throw new IOException("Expected 85 YOLOX values, got " + prediction.length);
                    }
                    int classIndex = 0;
                    float classScore = prediction[5];
                    for (int candidateClass = 1; candidateClass < CocoFoodBenchmark.COCO_NAMES.length; candidateClass++) {
                        float score = prediction[5 + candidateClass];
                        if (score > classScore) {
                            classScore = score;
                            classIndex = candidateClass;
                        }
                    }
                    float confidence = prediction[4] * classScore;
                    if (confidence < CONFIDENCE_FLOOR) {
                        continue;
                    }
                    float centerX = (prediction[0] + x) * stride;
                    float centerY = (prediction[1] + y) * stride;
                    float width = (float) Math.exp(prediction[2]) * stride;
                    float height = (float) Math.exp(prediction[3]) * stride;
                    candidates.add(new Detection(classIndex, confidence, centerX - width / 2.0f, centerY - height / 2.0f, centerX + width / 2.0f, centerY + height / 2.0f));
                }
            }
        }
        Collections.sort(candidates, Comparator.comparingDouble((Detection item) -> item.confidence).reversed());
        List<Detection> kept = new ArrayList<>();
        for (Detection candidate : candidates) {
            boolean overlaps = false;
            for (Detection existing : kept) {
                if (iou(existing, candidate) > NMS_IOU) {
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
            float ratio = Math.min((float) IMAGE_SIZE / source.getWidth(), (float) IMAGE_SIZE / source.getHeight());
            int resizedWidth = (int) (source.getWidth() * ratio);
            int resizedHeight = (int) (source.getHeight() * ratio);
            Bitmap resized = Bitmap.createScaledBitmap(source, resizedWidth, resizedHeight, true);
            float[] output = new float[3 * IMAGE_SIZE * IMAGE_SIZE];
            Arrays.fill(output, 114.0f);
            int plane = IMAGE_SIZE * IMAGE_SIZE;
            for (int y = 0; y < resizedHeight; y++) {
                for (int x = 0; x < resizedWidth; x++) {
                    int pixel = resized.getPixel(x, y);
                    int index = y * IMAGE_SIZE + x;
                    output[index] = pixel & 0xff;
                    output[plane + index] = (pixel >> 8) & 0xff;
                    output[2 * plane + index] = (pixel >> 16) & 0xff;
                }
            }
            resized.recycle();
            source.recycle();
            return output;
        }
    }

    private static String requireOnlyInput(OrtSession session) throws IOException {
        if (session.getInputNames().size() != 1) {
            throw new IOException("Expected one YOLOX input, got " + session.getInputNames());
        }
        return session.getInputNames().iterator().next();
    }

    private File copyAsset(String name) throws IOException {
        File target = new File(getFilesDir(), name);
        if (target.isFile()) {
            return target;
        }
        try (InputStream source = getAssets().open(name); FileOutputStream destination = new FileOutputStream(target)) {
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

    private static String resultJson(long sessionInitMs, List<Long> elapsed, List<FixtureRun> runs, long pssBeforeKb, long pssAfterKb, int thermalBefore, int thermalAfter) {
        long[] timings = new long[elapsed.size()];
        float macroF1 = 0.0f;
        for (int index = 0; index < elapsed.size(); index++) {
            timings[index] = elapsed.get(index);
            macroF1 += runs.get(index).f1();
        }
        Arrays.sort(timings);
        macroF1 /= runs.size();
        StringBuilder json = new StringBuilder();
        json.append("{\"status\":\"success\",\"candidate\":\"yolox-nano-coco\",")
                .append("\"runtime\":\"onnxruntime-android\",\"runtime_version\":\"1.28.0\",")
                .append("\"model\":\"").append(MODEL_ASSET).append("\",\"model_sha256\":\"").append(MODEL_SHA256).append("\",")
                .append("\"input_contract\":\"416px BGR letterbox padded with 114; decoded YOLOX COCO output\",")
                .append("\"vocabulary\":\"COCO-80 only; no custom ingredient prompts\",")
                .append("\"ingredient_benchmark\":{\"status\":\"limited\",\"scope\":\"exact labels in COCO food subset only\",")
                .append("\"macro_f1\":").append(formatFloat(macroF1)).append(",\"fixture_count\":").append(runs.size())
                .append("},\"weight_benchmark\":{\"status\":\"not-evaluable\",\"reason\":\"YOLOX-Nano has no mass output and fixtures have no scale ground truth.\"},")
                .append("\"session_init_ms\":").append(sessionInitMs).append(",\"inference_p50_ms\":").append(percentile(timings, 0.50)).append(",")
                .append("\"inference_p95_ms\":").append(percentile(timings, 0.95)).append(",\"inference_max_ms\":").append(timings[timings.length - 1]).append(",")
                .append("\"pss_before_kb\":").append(pssBeforeKb).append(",\"pss_after_kb\":").append(pssAfterKb).append(",")
                .append("\"thermal_before\":").append(thermalBefore).append(",\"thermal_after\":").append(thermalAfter).append(",\"fixture_results\":[");
        for (int index = 0; index < runs.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            FixtureRun run = runs.get(index);
            json.append("{\"fixture\":\"").append(run.fixture).append("\",\"food_detections\":").append(run.foodDetectionsJson())
                    .append(",\"exact_visible_component_f1\":").append(formatFloat(run.f1())).append("}");
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
                json.append("{\"label\":\"").append(label).append("\",\"confidence\":").append(formatFloat(detection.confidence)).append("}");
            }
            return json.append(']').toString();
        }
    }
}
