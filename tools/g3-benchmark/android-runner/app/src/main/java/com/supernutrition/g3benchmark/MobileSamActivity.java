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
import ai.onnxruntime.TensorInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Research-only physical-device smoke for an externally prepared MobileSAM
 * encoder and decoder. A fixed centre-point prompt tests segmentation plumbing;
 * it never identifies ingredients or estimates mass.
 */
public final class MobileSamActivity extends Activity {
    private static final String TAG = "SuperNutritionMobileSAM";
    private static final String ENCODER_ASSET = "mobile-sam-encoder.onnx";
    private static final String DECODER_ASSET = "mobile-sam-decoder.onnx";
    private static final String RESULT_FILE = "mobile-sam-result.json";
    private static final String[] FIXTURES = {
            "steak-side-plate.jpg",
            "fish-side-plate.jpg",
            "chopped-salad.jpg",
            "buddha-bowl.jpg",
            "salmon-grain-plate.jpg"
    };
    private static final int IMAGE_SIZE = 1024;
    private static final int WARMUP_RUNS = 1;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        status = new TextView(this);
        status.setText("MobileSAM research benchmark running…");
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
            File encoderFile = copyAsset(ENCODER_ASSET);
            File decoderFile = copyAsset(DECODER_ASSET);
            long pssBeforeKb = Debug.getPss();
            int thermalBefore = thermalStatus();
            OrtEnvironment environment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.EXTENDED_OPT);
            options.setIntraOpNumThreads(Math.max(1, Runtime.getRuntime().availableProcessors()));
            long initializationStartNs = System.nanoTime();
            try (OrtSession encoder = environment.createSession(encoderFile.getAbsolutePath(), options);
                 OrtSession decoder = environment.createSession(decoderFile.getAbsolutePath(), options)) {
                long sessionInitMs = elapsedMs(initializationStartNs);
                String encoderInputName = requireOnlyInput(encoder, "encoder");
                requireDecoderInputs(decoder);

                for (int i = 0; i < WARMUP_RUNS; i++) {
                    runOnce(environment, encoder, decoder, encoderInputName, FIXTURES[i]);
                }

                List<Long> elapsed = new ArrayList<>();
                List<MaskRun> runs = new ArrayList<>();
                for (String fixture : FIXTURES) {
                    long startedNs = System.nanoTime();
                    MaskRun run = runOnce(environment, encoder, decoder, encoderInputName, fixture);
                    elapsed.add(elapsedMs(startedNs));
                    runs.add(run);
                }
                long pssAfterKb = Debug.getPss();
                int thermalAfter = thermalStatus();
                String result = resultJson(sessionInitMs, elapsed, runs, pssBeforeKb, pssAfterKb, thermalBefore, thermalAfter);
                writeResult(result);
                Log.i(TAG, result);
                runOnUiThread(() -> status.setText("MobileSAM research benchmark complete"));
            }
        } catch (Throwable error) {
            String failure = "{\"status\":\"error\",\"candidate\":\"mobile-sam\",\"message\":\""
                    + jsonEscape(error.toString()) + "\"}";
            try {
                writeResult(failure);
            } catch (IOException ignored) {
                // Logcat remains the failure record if app-private storage fails.
            }
            Log.e(TAG, failure, error);
            runOnUiThread(() -> status.setText("MobileSAM research benchmark failed"));
        }
    }

    private MaskRun runOnce(
            OrtEnvironment environment,
            OrtSession encoder,
            OrtSession decoder,
            String encoderInputName,
            String fixture
    ) throws Exception {
        float[] pixels = preprocess(fixture);
        try (OnnxTensor image = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(pixels),
                // Acly's exported MobileSAM encoder has an unbatched HWC input.
                // The decoder retains the embedding batch dimension internally.
                new long[]{IMAGE_SIZE, IMAGE_SIZE, 3}
        ); OrtSession.Result encoderResult = encoder.run(singleton(encoderInputName, image))) {
            OnnxValue embedding = encoderResult.get(0);
            if (!(embedding instanceof OnnxTensor)) {
                throw new IOException("MobileSAM encoder output is not a tensor");
            }
            try (OnnxTensor pointCoords = OnnxTensor.createTensor(
                    environment,
                    FloatBuffer.wrap(new float[]{IMAGE_SIZE / 2.0f, IMAGE_SIZE / 2.0f, 0.0f, 0.0f}),
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
                    FloatBuffer.wrap(new float[]{IMAGE_SIZE, IMAGE_SIZE}),
                    new long[]{2}
            )) {
                Map<String, OnnxTensor> decoderInputs = new LinkedHashMap<>();
                decoderInputs.put("image_embeddings", (OnnxTensor) embedding);
                decoderInputs.put("point_coords", pointCoords);
                decoderInputs.put("point_labels", pointLabels);
                decoderInputs.put("mask_input", maskInput);
                decoderInputs.put("has_mask_input", hasMaskInput);
                decoderInputs.put("orig_im_size", originalImageSize);
                try (OrtSession.Result decoderResult = decoder.run(decoderInputs)) {
                    OnnxValue masks = decoderResult.get(0);
                    if (!(masks.getInfo() instanceof TensorInfo)) {
                        throw new IOException("MobileSAM decoder mask output is not a tensor");
                    }
                    long[] maskShape = ((TensorInfo) masks.getInfo()).getShape();
                    if (maskShape.length != 4 || maskShape[0] != 1 || maskShape[1] < 1) {
                        throw new IOException("Unexpected MobileSAM mask shape " + Arrays.toString(maskShape));
                    }
                    return new MaskRun(fixture, maskShape);
                }
            }
        }
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
                    int offset = (y * IMAGE_SIZE + x) * 3;
                    output[offset] = (((pixel >> 16) & 0xff) - 123.675f) / 58.395f;
                    output[offset + 1] = (((pixel >> 8) & 0xff) - 116.28f) / 57.12f;
                    output[offset + 2] = ((pixel & 0xff) - 103.53f) / 57.375f;
                }
            }
            resized.recycle();
            source.recycle();
            return output;
        }
    }

    private static String requireOnlyInput(OrtSession session, String role) throws IOException {
        if (session.getInputNames().size() != 1) {
            throw new IOException("Expected one MobileSAM " + role + " input, got " + session.getInputNames());
        }
        return session.getInputNames().iterator().next();
    }

    private static void requireDecoderInputs(OrtSession session) throws IOException {
        List<String> expected = Arrays.asList(
                "image_embeddings", "point_coords", "point_labels", "mask_input", "has_mask_input", "orig_im_size"
        );
        if (!session.getInputNames().containsAll(expected)) {
            throw new IOException("Unexpected MobileSAM decoder inputs " + session.getInputNames());
        }
    }

    private static Map<String, OnnxTensor> singleton(String inputName, OnnxTensor input) {
        Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
        inputs.put(inputName, input);
        return inputs;
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

    private static String resultJson(
            long sessionInitMs,
            List<Long> elapsed,
            List<MaskRun> runs,
            long pssBeforeKb,
            long pssAfterKb,
            int thermalBefore,
            int thermalAfter
    ) {
        long[] timings = new long[elapsed.size()];
        for (int index = 0; index < elapsed.size(); index++) {
            timings[index] = elapsed.get(index);
        }
        Arrays.sort(timings);
        StringBuilder json = new StringBuilder();
        json.append("{\"status\":\"success\",\"candidate\":\"mobile-sam\",")
                .append("\"runtime\":\"onnxruntime-android\",\"runtime_version\":\"1.28.0\",")
                .append("\"encoder\":\"").append(ENCODER_ASSET).append("\",")
                .append("\"decoder\":\"").append(DECODER_ASSET).append("\",")
                .append("\"fixture_count\":").append(runs.size()).append(",")
                .append("\"prompt\":\"one centre positive point plus one padding negative point; not ingredient recognition\",")
                .append("\"ingredient_benchmark\":{\"status\":\"unsupported\",\"reason\":\"MobileSAM emits prompted class-agnostic masks only.\"},")
                .append("\"weight_benchmark\":{\"status\":\"not-evaluable\",\"reason\":\"MobileSAM has no mass output and the fixtures have no scale ground truth.\"},")
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
            MaskRun run = runs.get(index);
            json.append("{\"fixture\":\"").append(jsonEscape(run.fixture))
                    .append("\",\"mask_shape\":\"").append(jsonEscape(Arrays.toString(run.maskShape)))
                    .append("\"}");
        }
        json.append("]}");
        return json.toString();
    }

    private static long percentile(long[] values, double percentile) {
        int index = (int) Math.ceil(percentile * values.length) - 1;
        return values[Math.max(0, Math.min(index, values.length - 1))];
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class MaskRun {
        private final String fixture;
        private final long[] maskShape;

        private MaskRun(String fixture, long[] maskShape) {
            this.fixture = fixture;
            this.maskShape = maskShape;
        }
    }
}
