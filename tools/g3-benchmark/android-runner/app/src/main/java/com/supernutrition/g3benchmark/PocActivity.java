package com.supernutrition.g3benchmark;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.supernutrition.g3benchmark.poc.PocDishIngredientPriors;
import com.supernutrition.inference.localandroidpoc.runtime.PocAnalysis;
import com.supernutrition.inference.localandroidpoc.runtime.PocInferenceEngine;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Research-only UI/camera slice. It intentionally does not belong to
 * production mobile/** and does not promise ingredient or weight accuracy.
 */
public final class PocActivity extends Activity {
    private static final int REQUEST_CAMERA_PERMISSION = 4101;
    private static final int REQUEST_CAPTURE = 4102;
    private static final int REQUEST_GALLERY = 4103;
    private static final String RESEARCH_WARNING =
            "Research PoC / ingredient labels limited to model vocabulary / grams unavailable";
    private static final String EXTRA_RESEARCH_IMAGE_PATH = "research_image_path";

    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private PocOverlayView overlayView;
    private TextView statusView;
    private TextView resultView;
    private Bitmap selectedBitmap;
    private PocInferenceEngine engine;
    private PocAnalysis latestAnalysis;
    private int selectedCellIndex = -1;
    private int analysisRequestId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("SuperNutrition G-3 Research PoC");
        setContentView(createContentView());
        String researchImagePath = getIntent().getStringExtra(EXTRA_RESEARCH_IMAGE_PATH);
        if (researchImagePath != null && !researchImagePath.isBlank()) {
            loadResearchImage(researchImagePath);
        }
    }

    @Override
    protected void onDestroy() {
        analysisExecutor.shutdownNow();
        closeAnalysis(latestAnalysis);
        latestAnalysis = null;
        closeEngine();
        if (selectedBitmap != null && !selectedBitmap.isRecycled()) {
            selectedBitmap.recycle();
            selectedBitmap = null;
        }
        super.onDestroy();
    }

    private View createContentView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        content.setPadding(padding, padding, padding, padding);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text("G-3 visible-component research PoC", 22);
        title.setContentDescription("Заголовок исследовательского PoC");
        content.addView(title, fullWidthParams(8));

        TextView warning = text(RESEARCH_WARNING, 14);
        warning.setTextColor(0xff8a3b12);
        warning.setContentDescription(RESEARCH_WARNING);
        content.addView(warning, fullWidthParams(12));

        LinearLayout captureRow = new LinearLayout(this);
        captureRow.setOrientation(LinearLayout.HORIZONTAL);
        captureRow.setGravity(Gravity.CENTER_VERTICAL);
        Button captureButton = button("Сделать фото", "Сделать фото камерой");
        captureButton.setTag("poc_capture");
        captureButton.setOnClickListener(view -> requestCameraOrFallback());
        captureRow.addView(captureButton, weightParams(1, 0));
        Button galleryButton = button("Выбрать фото", "Выбрать фото из галереи");
        galleryButton.setTag("poc_gallery");
        galleryButton.setOnClickListener(view -> openGallery());
        captureRow.addView(galleryButton, weightParams(1, 8));
        content.addView(captureRow, fullWidthParams(8));

        overlayView = new PocOverlayView(this);
        overlayView.setVisibility(View.GONE);
        overlayView.setTag("poc_photo_overlay");
        overlayView.setOnGridCellClickListener(this::selectGridCell);
        content.addView(overlayView, fullWidthParams(8));

        TextView gridTitle = text("Нажимаемые grid hints", 16);
        gridTitle.setContentDescription("Нажимаемые подсказки ячеек сетки");
        content.addView(gridTitle, fullWidthParams(4));
        GridLayout gridHints = new GridLayout(this);
        gridHints.setColumnCount(3);
        gridHints.setRowCount(3);
        for (int index = 0; index < 9; index++) {
            final int cellIndex = index;
            Button cellButton = button(String.valueOf(index + 1),
                    "Выбрать grid cell " + (index + 1));
            cellButton.setTag("poc_grid_cell_" + (index + 1));
            cellButton.setOnClickListener(view -> selectGridCell(cellIndex));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dp(48);
            params.columnSpec = GridLayout.spec(index % 3, 1f);
            params.rowSpec = GridLayout.spec(index / 3);
            params.setMargins(index % 3 == 0 ? 0 : dp(4), dp(2), dp(4), dp(2));
            gridHints.addView(cellButton, params);
        }
        content.addView(gridHints, fullWidthParams(8));

        statusView = text("Сделайте фото или выберите безопасный gallery fallback.", 15);
        statusView.setTag("poc_status");
        content.addView(statusView, fullWidthParams(8));

        resultView = text("Top-3 блюда и области модели появятся после анализа.", 16);
        resultView.setTag("poc_results");
        content.addView(resultView, fullWidthParams(8));
        return scrollView;
    }

    private void requestCameraOrFallback() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        launchCamera();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_CAMERA_PERMISSION) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            statusView.setText("Доступ к камере не выдан. Доступен безопасный gallery fallback.");
            openGallery();
        }
    }

    private void launchCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) == null) {
            statusView.setText("Камера недоступна. Открываю безопасный gallery fallback.");
            openGallery();
            return;
        }
        // No FileProvider dependency: use the camera thumbnail contract as the PoC photo.
        startActivityForResult(intent, REQUEST_CAPTURE);
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQUEST_GALLERY);
        } catch (RuntimeException error) {
            statusView.setText("Gallery fallback недоступен: " + error.getClass().getSimpleName());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            statusView.setText("Фото не выбрано.");
            return;
        }
        Bitmap bitmap = requestCode == REQUEST_CAPTURE
                ? bitmapFromCapture(data)
                : bitmapFromUri(data == null ? null : data.getData());
        if (bitmap == null) {
            statusView.setText("Не удалось получить Bitmap. Попробуйте gallery fallback.");
            return;
        }
        setSelectedBitmap(bitmap);
    }

    private Bitmap bitmapFromCapture(Intent data) {
        if (data == null || data.getExtras() == null) {
            return null;
        }
        Object thumbnail = data.getExtras().get("data");
        return thumbnail instanceof Bitmap ? (Bitmap) thumbnail : null;
    }

    private Bitmap bitmapFromUri(Uri uri) {
        if (uri == null) {
            return null;
        }
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            return stream == null ? null : BitmapFactory.decodeStream(stream);
        } catch (Exception error) {
            return null;
        }
    }

    private void loadResearchImage(String path) {
        Bitmap bitmap = BitmapFactory.decodeFile(path);
        if (bitmap == null) {
            statusView.setText("Research image недоступно: " + path);
            return;
        }
        setSelectedBitmap(bitmap);
    }

    private void setSelectedBitmap(Bitmap bitmap) {
        analysisRequestId++;
        if (selectedBitmap != null && selectedBitmap != bitmap && !selectedBitmap.isRecycled()) {
            selectedBitmap.recycle();
        }
        selectedBitmap = bitmap;
        closeAnalysis(latestAnalysis);
        latestAnalysis = null;
        overlayView.setBitmap(bitmap);
        overlayView.setAnalyzing(false);
        overlayView.setRegions(Collections.<PocOverlayView.RegionOverlay>emptyList());
        overlayView.setSelectedCell(-1);
        selectedCellIndex = -1;
        overlayView.setVisibility(View.VISIBLE);
        resultView.setText("Автоматический анализ блюда и видимых областей…");
        statusView.setText("Фото получено; запускаю автоматический research PoC анализ…");
        analyzeSelectedBitmap();
    }

    private void selectGridCell(int cellIndex) {
        selectedCellIndex = cellIndex;
        overlayView.setSelectedCell(cellIndex);
        if (selectedBitmap == null || selectedBitmap.isRecycled()) {
            statusView.setText("Выбрана grid cell " + (cellIndex + 1)
                    + " — сначала выберите фото.");
            return;
        }
        statusView.setText("Выбрана grid cell " + (cellIndex + 1)
                + "; повторяю автоматический анализ с подсказкой…");
        analyzeSelectedBitmap();
    }

    private void analyzeSelectedBitmap() {
        if (selectedBitmap == null || selectedBitmap.isRecycled()) {
            statusView.setText("Сначала выберите фото.");
            return;
        }
        statusView.setText("Локальный research PoC анализирует Bitmap…");
        overlayView.setAnalyzing(true);
        final Bitmap bitmap = selectedBitmap;
        final int gridCell = selectedCellIndex;
        final int requestId = ++analysisRequestId;
        analysisExecutor.execute(() -> {
            try {
                if (engine == null) {
                    engine = createEngine();
                }
                PocAnalysis analysis = engine.analyze(bitmap, gridCell);
                PocUiResult uiResult = readAnalysis(analysis);
                runOnUiThread(() -> {
                    if (requestId != analysisRequestId || bitmap != selectedBitmap) {
                        closeAnalysis(analysis);
                        return;
                    }
                    closeAnalysis(latestAnalysis);
                    latestAnalysis = analysis;
                    overlayView.setAnalyzing(false);
                    overlayView.setRegions(uiResult.regions);
                    resultView.setText(uiResult.summary);
                    statusView.setText(uiResult.status);
                });
            } catch (Throwable error) {
                final String message = "PoC analysis unavailable: " + rootMessage(error);
                runOnUiThread(() -> {
                    if (requestId != analysisRequestId || bitmap != selectedBitmap) {
                        return;
                    }
                    overlayView.setAnalyzing(false);
                    statusView.setText(message);
                    resultView.setText(RESEARCH_WARNING);
                });
            }
        });
    }

    private PocInferenceEngine createEngine() {
        return new PocInferenceEngine(
                getApplicationContext(),
                true,
                PocDishIngredientPriors::forDish
        );
    }

    private void closeEngine() {
        if (engine == null) {
            return;
        }
        engine.close();
        engine = null;
    }

    private static void closeAnalysis(PocAnalysis analysis) {
        if (analysis == null) {
            return;
        }
        analysis.close();
    }

    private PocUiResult readAnalysis(PocAnalysis analysis) {
        if (analysis == null) {
            return new PocUiResult("PoC returned no analysis.", RESEARCH_WARNING, Collections.<PocOverlayView.RegionOverlay>emptyList());
        }
        List<String> dishes = readCandidates(analysis.dishCandidates);
        List<PocOverlayView.RegionOverlay> regions = readRegions(analysis.regions);
        List<String> warnings = analysis.warnings;
        String status = analysis.status == null ? "" : analysis.status.trim();
        if (status.length() == 0) {
            status = "Анализ завершён.";
        }

        StringBuilder summary = new StringBuilder("Top-3 dish candidates:\n");
        if (dishes.isEmpty()) {
            summary.append("нет кандидатов\n");
        } else {
            for (String dish : dishes) {
                summary.append("• ").append(dish).append('\n');
            }
        }
        summary.append("\nRegions: ").append(regions.size()).append("\n");
        if (!regions.isEmpty()) {
            for (PocOverlayView.RegionOverlay region : regions) {
                if (region == null) {
                    continue;
                }
                if ("grid_prompt".equals(region.label)) {
                    summary.append("\n• grid prompt");
                } else {
                    summary.append("\n• ").append(region.label)
                            .append(" — ").append(percent(region.confidence));
                }
            }
            summary.append('\n');
        }
        summary.append("\nDebug ingredient suggestions (full):");
        if (analysis.ingredientSuggestions.isEmpty()) {
            summary.append("\n• none");
        } else {
            for (PocAnalysis.IngredientSuggestion suggestion : analysis.ingredientSuggestions) {
                summary.append("\n• ").append(suggestion.label);
                if (suggestion.fromDishPrior) {
                    summary.append(" — dish prior");
                    summary.append(", composition support ").append(percent(suggestion.compositionSupport));
                    if (suggestion.detectorConfidence >= 0.0f) {
                        summary.append(", detected ").append(percent(suggestion.detectorConfidence));
                    } else {
                        summary.append(", not detected");
                    }
                } else {
                    summary.append(" — detector ").append(percent(suggestion.detectorConfidence));
                    summary.append(", retained for visual review");
                }
            }
        }
        summary.append('\n').append(RESEARCH_WARNING);
        for (String warning : warnings) {
            summary.append("\n⚠ ").append(warning);
        }
        return new PocUiResult(status, summary.toString(), regions);
    }

    private static List<String> readCandidates(List<PocAnalysis.DishCandidate> candidates) {
        List<String> result = new ArrayList<>();
        for (PocAnalysis.DishCandidate candidate : candidates) {
            if (candidate != null && candidate.label != null && !candidate.label.trim().isEmpty()) {
                result.add(candidate.label.trim() + " — " + percent(candidate.confidence));
            }
            if (result.size() == 3) {
                break;
            }
        }
        return result;
    }

    private static List<PocOverlayView.RegionOverlay> readRegions(List<PocAnalysis.Region> source) {
        List<PocOverlayView.RegionOverlay> result = new ArrayList<>();
        for (PocAnalysis.Region region : source) {
            if (region == null) {
                continue;
            }
            RectF rect = new RectF(region.left, region.top, region.right, region.bottom);
            result.add(new PocOverlayView.RegionOverlay(
                    region.label, region.confidence, rect, region.mask, region.gridCellIndex));
        }
        return result;
    }

    private static String percent(float confidence) {
        return String.format(Locale.US, "%.1f%%", confidence * 100f);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.trim().isEmpty()
                ? current.getClass().getSimpleName()
                : message;
    }

    private TextView text(String value, int sizeSp) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(0xff202124);
        return view;
    }

    private Button button(String value, String contentDescription) {
        Button button = new Button(this);
        button.setText(value);
        button.setContentDescription(contentDescription);
        return button;
    }

    private LinearLayout.LayoutParams fullWidthParams(int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(bottomMargin);
        return params;
    }

    private LinearLayout.LayoutParams weightParams(float weight, int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        params.leftMargin = dp(leftMargin);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class PocUiResult {
        private final String status;
        private final String summary;
        private final List<PocOverlayView.RegionOverlay> regions;

        private PocUiResult(String status, String summary, List<PocOverlayView.RegionOverlay> regions) {
            this.status = status;
            this.summary = summary;
            this.regions = regions;
        }
    }
}
