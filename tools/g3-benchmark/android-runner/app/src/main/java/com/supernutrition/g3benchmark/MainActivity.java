package com.supernutrition.g3benchmark;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.BatteryManager;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String TAG = "SuperNutritionG3";
    private static final String MODEL_ASSET = "swin-finetuned-food101-matmul-int8.onnx";
    private static final String LABELS_ASSET = "food101-labels.txt";
    private static final String MODEL_SHA256 =
            "9070f8097cda88923f77774d93d9fead7cee344486521c7fc5142806642061df";
    private static final String MEAL_MANIFEST_SHA256 =
            "d312e9c7408aa474df38ff823d9fd3fc6c8a3a86cffc94df7ce53391c6975292";
    private static final String[] FIXTURES = {
            "steak-side-plate.jpg",
            "fish-side-plate.jpg",
            "chopped-salad.jpg",
            "buddha-bowl.jpg",
            "salmon-grain-plate.jpg",
            "steak.jpg",
            "greek-salad.jpg",
            "salmon-on-grill.jpg",
            "sushi.jpg",
            "waffles.jpg"
    };
    private static final String[] EXPECTED_LABELS = {
            null,
            null,
            null,
            null,
            null,
            "steak",
            "greek_salad",
            "grilled_salmon",
            "sushi",
            "waffles"
    };
    private static final float SEMANTIC_MIN_CONFIDENCE = 0.55f;
    private static final int IMAGE_SIZE = 224;
    private static final int WARMUP_RUNS = 5;
    private static final int MEASURED_RUNS = 50;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        status = new TextView(this);
        status.setText("G-3 benchmark running…");
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
            new File(getFilesDir(), "result.json").delete();
            List<String> labels = readLabels();
            if (labels.size() != 101) {
                throw new IOException("Food101 label map must contain 101 labels");
            }
            File modelFile = copyAsset(MODEL_ASSET);
            long processStartPssKb = Debug.getPss();
            BatteryManager batteryManager = (BatteryManager) getSystemService(BATTERY_SERVICE);
            long batteryCurrentBeforeUa = batteryManager == null
                    ? Long.MIN_VALUE
                    : batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            int thermalBefore = thermalStatus();
            long sessionStartNs = System.nanoTime();
            OrtEnvironment environment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.EXTENDED_OPT);
            options.setIntraOpNumThreads(Math.max(1, Runtime.getRuntime().availableProcessors()));
            OrtSession session = environment.createSession(modelFile.getAbsolutePath(), options);
            long sessionInitMs = elapsedMs(sessionStartNs);
            String inputName = session.getInputNames().iterator().next();

            List<float[]> inputs = new ArrayList<>();
            for (String fixture : FIXTURES) {
                inputs.add(preprocess(fixture));
            }
            for (int i = 0; i < WARMUP_RUNS; i++) {
                runOnce(environment, session, inputName, inputs.get(i % inputs.size()));
            }

            List<Long> inferenceMs = new ArrayList<>();
            List<InferenceResult> firstResults = new ArrayList<>(inputs.size());
            for (int i = 0; i < MEASURED_RUNS; i++) {
                long started = System.nanoTime();
                InferenceResult result = runOnce(
                        environment, session, inputName, inputs.get(i % inputs.size()));
                inferenceMs.add(elapsedMs(started));
                if (i < inputs.size()) {
                    firstResults.add(result);
                }
            }

            long processEndPssKb = Debug.getPss();
            long batteryCurrentAfterUa = batteryManager == null
                    ? Long.MIN_VALUE
                    : batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            int thermalAfter = thermalStatus();
            String result = resultJson(
                    sessionInitMs,
                    inferenceMs,
                    processStartPssKb,
                    processEndPssKb,
                    batteryCurrentBeforeUa,
                    batteryCurrentAfterUa,
                    thermalBefore,
                    thermalAfter,
                    labels,
                    firstResults
            );
            writeResult(result);
            Log.i(TAG, result);
            runOnUiThread(() -> status.setText("G-3 benchmark complete"));
        } catch (Throwable error) {
            String failure = "{\"status\":\"error\",\"message\":\""
                    + jsonEscape(error.toString()) + "\"}";
            try {
                writeResult(failure);
            } catch (IOException ignored) {
                // The log line remains the failure evidence if the file cannot be written.
            }
            Log.e(TAG, failure, error);
            runOnUiThread(() -> status.setText("G-3 benchmark failed"));
        }
    }

    private InferenceResult runOnce(
            OrtEnvironment environment,
            OrtSession session,
            String inputName,
            float[] input
    ) throws Exception {
        try (OnnxTensor tensor = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(input),
                new long[]{1, 3, IMAGE_SIZE, IMAGE_SIZE}
        ); OrtSession.Result result = session.run(Collections.singletonMap(inputName, tensor))) {
            OnnxValue output = result.get(0);
            float[][] scores = (float[][]) output.getValue();
            if (scores.length != 1 || scores[0].length != 101) {
                throw new IOException("Expected Food101 output shape [1,101]");
            }
            return toInferenceResult(scores[0]);
        }
    }

    private static InferenceResult toInferenceResult(float[] logits) {
        float max = logits[0];
        for (int i = 1; i < logits.length; i++) {
            max = Math.max(max, logits[i]);
        }
        double sum = 0.0;
        float[] probabilities = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            probabilities[i] = (float) Math.exp(logits[i] - max);
            sum += probabilities[i];
        }
        for (int i = 0; i < probabilities.length; i++) {
            probabilities[i] /= (float) sum;
        }

        int topCount = Math.min(5, probabilities.length);
        int[] topIndexes = new int[topCount];
        float[] topProbabilities = new float[topCount];
        boolean[] used = new boolean[probabilities.length];
        for (int rank = 0; rank < topCount; rank++) {
            int best = -1;
            for (int index = 0; index < probabilities.length; index++) {
                if (!used[index] && (best < 0 || probabilities[index] > probabilities[best])) {
                    best = index;
                }
            }
            used[best] = true;
            topIndexes[rank] = best;
            topProbabilities[rank] = probabilities[best];
        }
        return new InferenceResult(topIndexes, topProbabilities);
    }

    private float[] preprocess(String fixture) throws IOException {
        try (InputStream input = getAssets().open("fixtures/" + fixture)) {
            Bitmap source = BitmapFactory.decodeStream(input);
            if (source == null) {
                throw new IOException("Unable to decode " + fixture);
            }
            int side = Math.min(source.getWidth(), source.getHeight());
            int left = (source.getWidth() - side) / 2;
            int top = (source.getHeight() - side) / 2;
            Bitmap cropped = Bitmap.createBitmap(source, left, top, side, side);
            Bitmap resized = Bitmap.createScaledBitmap(cropped, IMAGE_SIZE, IMAGE_SIZE, true);
            float[] output = new float[3 * IMAGE_SIZE * IMAGE_SIZE];
            for (int y = 0; y < IMAGE_SIZE; y++) {
                for (int x = 0; x < IMAGE_SIZE; x++) {
                    int pixel = resized.getPixel(x, y);
                    int offset = y * IMAGE_SIZE + x;
                    output[offset] = normalize((pixel >> 16) & 0xff, 0.485f, 0.229f);
                    output[IMAGE_SIZE * IMAGE_SIZE + offset] = normalize(
                            (pixel >> 8) & 0xff, 0.456f, 0.224f);
                    output[2 * IMAGE_SIZE * IMAGE_SIZE + offset] = normalize(
                            pixel & 0xff, 0.406f, 0.225f);
                }
            }
            resized.recycle();
            cropped.recycle();
            source.recycle();
            return output;
        }
    }

    private List<String> readLabels() throws IOException {
        List<String> labels = new ArrayList<>();
        try (InputStream input = getAssets().open(LABELS_ASSET);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    labels.add(line.trim());
                }
            }
        }
        return labels;
    }

    private static float normalize(int value, float mean, float standardDeviation) {
        return (value / 255.0f - mean) / standardDeviation;
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
        try (FileOutputStream output = new FileOutputStream(new File(getFilesDir(), "result.json"))) {
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

    private static String resultJson(
            long sessionInitMs,
            List<Long> inferenceMs,
            long pssBeforeKb,
            long pssAfterKb,
            long batteryBeforeUa,
            long batteryAfterUa,
            int thermalBefore,
            int thermalAfter,
            List<String> labels,
            List<InferenceResult> results
    ) {
        long[] values = new long[inferenceMs.size()];
        for (int i = 0; i < inferenceMs.size(); i++) {
            values[i] = inferenceMs.get(i);
        }
        java.util.Arrays.sort(values);
        int expectedCount = 0;
        int correctCount = 0;
        for (int i = 0; i < results.size(); i++) {
            String expected = EXPECTED_LABELS[i];
            if (expected == null) {
                continue;
            }
            expectedCount++;
            InferenceResult result = results.get(i);
            boolean correct = expected.equals(labels.get(result.topIndexes[0]))
                    && result.topProbabilities[0] >= SEMANTIC_MIN_CONFIDENCE;
            if (correct) {
                correctCount++;
            }
        }
        boolean semanticPass = expectedCount > 0 && correctCount == expectedCount;
        StringBuilder json = new StringBuilder();
        json.append("{\"status\":\"success\",\"runtime\":\"onnxruntime-android\",");
        json.append("\"runtime_version\":\"1.28.0\",\"model\":\"")
                .append(MODEL_ASSET).append("\",\"model_sha256\":\"")
                .append(MODEL_SHA256).append("\",\"meal_benchmark_manifest_sha256\":\"")
                .append(MEAL_MANIFEST_SHA256).append("\",\"meal_benchmark_fixture_count\":5,");
        json.append("\"fixture_count\":").append(results.size())
                .append(",\"semantic_expected_count\":").append(expectedCount)
                .append(",\"semantic_correct_count\":").append(correctCount)
                .append(",\"semantic_accuracy\":")
                .append(formatFloat(expectedCount == 0 ? 0.0f : (float) correctCount / expectedCount))
                .append(",\"semantic_min_confidence\":")
                .append(formatFloat(SEMANTIC_MIN_CONFIDENCE))
                .append(",\"semantic_pass\":").append(semanticPass).append(",")
                .append("\"ingredient_benchmark\":{")
                .append("\"status\":\"unsupported\",")
                .append("\"scope\":\"visible_ingredient_set\",")
                .append("\"ground_truth_case_count\":5,")
                .append("\"predicted_case_count\":0,")
                .append("\"reason\":\"Food-101 runner emits one dish label and no ingredient candidates, boxes, or masks.\"},")
                .append("\"weight_benchmark\":{")
                .append("\"status\":\"not-evaluable\",")
                .append("\"scope\":\"plated_mass_g\",")
                .append("\"ground_truth_case_count\":0,")
                .append("\"predicted_case_count\":0,")
                .append("\"reason\":\"Fixtures have no calibrated scale ground truth and the current model emits no mass estimate.\"},");
        json.append("\"warmup_runs\":").append(WARMUP_RUNS)
                .append(",\"measured_runs\":").append(MEASURED_RUNS)
                .append(",\"session_init_ms\":").append(sessionInitMs)
                .append(",\"inference_p50_ms\":").append(percentile(values, 0.50))
                .append(",\"inference_p95_ms\":").append(percentile(values, 0.95))
                .append(",\"inference_max_ms\":").append(values[values.length - 1])
                .append(",\"pss_before_kb\":").append(pssBeforeKb)
                .append(",\"pss_after_kb\":").append(pssAfterKb)
                .append(",\"battery_current_before_ua\":").append(batteryBeforeUa)
                .append(",\"battery_current_after_ua\":").append(batteryAfterUa)
                .append(",\"thermal_before\":").append(thermalBefore)
                .append(",\"thermal_after\":").append(thermalAfter)
                .append(",\"fixture_results\":[");
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            InferenceResult result = results.get(i);
            String expected = EXPECTED_LABELS[i];
            boolean correct = expected != null
                    && expected.equals(labels.get(result.topIndexes[0]))
                    && result.topProbabilities[0] >= SEMANTIC_MIN_CONFIDENCE;
            json.append("{\"fixture\":\"").append(jsonEscape(FIXTURES[i]))
                    .append("\",\"expected_label\":");
            if (expected == null) {
                json.append("null");
            } else {
                json.append('"').append(jsonEscape(expected)).append('"');
            }
            json.append(",\"top1_label\":\"")
                    .append(jsonEscape(labels.get(result.topIndexes[0])))
                    .append("\",\"top1_confidence\":")
                    .append(formatFloat(result.topProbabilities[0]))
                    .append(",\"correct\":").append(correct)
                    .append(",\"top5\":[");
            for (int rank = 0; rank < result.topIndexes.length; rank++) {
                if (rank > 0) {
                    json.append(',');
                }
                json.append("{\"label\":\"")
                        .append(jsonEscape(labels.get(result.topIndexes[rank])))
                        .append("\",\"confidence\":")
                        .append(formatFloat(result.topProbabilities[rank]))
                        .append('}');
            }
            json.append("]}");
        }
        json.append("]}");
        return json.toString();
    }

    private static long percentile(long[] values, double percentile) {
        int index = (int) Math.ceil(percentile * values.length) - 1;
        return values[Math.max(0, Math.min(index, values.length - 1))];
    }

    private static String formatFloat(float value) {
        return String.format(Locale.US, "%.6f", value);
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class InferenceResult {
        private final int[] topIndexes;
        private final float[] topProbabilities;

        private InferenceResult(int[] topIndexes, float[] topProbabilities) {
            this.topIndexes = topIndexes;
            this.topProbabilities = topProbabilities;
        }
    }
}
