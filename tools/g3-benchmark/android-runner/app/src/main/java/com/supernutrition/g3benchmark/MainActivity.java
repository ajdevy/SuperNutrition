package com.supernutrition.g3benchmark;

import android.app.Activity;
import android.app.ActivityManager;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String TAG = "SuperNutritionG3";
    private static final String MODEL_ASSET = "mobilenet_v2_food101.onnx";
    private static final String[] FIXTURES = {
            "steak-side-plate.jpg",
            "fish-side-plate.jpg",
            "chopped-salad.jpg",
            "buddha-bowl.jpg",
            "salmon-grain-plate.jpg"
    };
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
            int firstTop1 = -1;
            for (int i = 0; i < MEASURED_RUNS; i++) {
                long started = System.nanoTime();
                int top1 = runOnce(environment, session, inputName, inputs.get(i % inputs.size()));
                inferenceMs.add(elapsedMs(started));
                if (i == 0) {
                    firstTop1 = top1;
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
                    firstTop1
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

    private int runOnce(
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
            int top1 = 0;
            for (int i = 1; i < scores[0].length; i++) {
                if (scores[0][i] > scores[0][top1]) {
                    top1 = i;
                }
            }
            return top1;
        }
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
                    output[IMAGE_SIZE * IMAGE_SIZE + offset] = normalize((pixel >> 8) & 0xff, 0.456f, 0.224f);
                    output[2 * IMAGE_SIZE * IMAGE_SIZE + offset] = normalize(pixel & 0xff, 0.406f, 0.225f);
                }
            }
            resized.recycle();
            cropped.recycle();
            source.recycle();
            return output;
        }
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
            output.write(result.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
            int firstTop1
    ) {
        long[] values = new long[inferenceMs.size()];
        for (int i = 0; i < inferenceMs.size(); i++) {
            values[i] = inferenceMs.get(i);
        }
        Arrays.sort(values);
        return String.format(Locale.US,
                "{\"status\":\"success\",\"runtime\":\"onnxruntime-android\","
                        + "\"runtime_version\":\"1.28.0\",\"model\":\"mobilenet_v2_food101.onnx\","
                        + "\"fixture_count\":%d,\"warmup_runs\":%d,\"measured_runs\":%d,"
                        + "\"session_init_ms\":%d,\"inference_p50_ms\":%d,\"inference_p95_ms\":%d,"
                        + "\"inference_max_ms\":%d,\"pss_before_kb\":%d,\"pss_after_kb\":%d,"
                        + "\"battery_current_before_ua\":%d,\"battery_current_after_ua\":%d,"
                        + "\"thermal_before\":%d,\"thermal_after\":%d,\"first_top1_index\":%d}",
                FIXTURES.length,
                WARMUP_RUNS,
                MEASURED_RUNS,
                sessionInitMs,
                percentile(values, 0.50),
                percentile(values, 0.95),
                values[values.length - 1],
                pssBeforeKb,
                pssAfterKb,
                batteryBeforeUa,
                batteryAfterUa,
                thermalBefore,
                thermalAfter,
                firstTop1
        );
    }

    private static long percentile(long[] values, double percentile) {
        int index = (int) Math.ceil(percentile * values.length) - 1;
        return values[Math.max(0, Math.min(index, values.length - 1))];
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
