package com.supernutrition.g3benchmark;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.Collections;
import java.util.List;

/** Research-only photo, grid, region, and optional mask overlay. */
public final class PocOverlayView extends View {
    public static final class RegionOverlay {
        public final String label;
        public final float confidence;
        public final RectF normalizedRect;
        public final Bitmap mask;
        public final int gridCellIndex;

        public RegionOverlay(
                String label,
                float confidence,
                RectF normalizedRect,
                Bitmap mask,
                int gridCellIndex
        ) {
            this.label = label;
            this.confidence = confidence;
            this.normalizedRect = normalizedRect;
            this.mask = mask;
            this.gridCellIndex = gridCellIndex;
        }
    }

    public interface OnGridCellClickListener {
        void onGridCellClick(int cellIndex);
    }

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint regionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint loadingScrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spinnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap bitmap;
    private List<RegionOverlay> regions = Collections.emptyList();
    private RectF imageRect = new RectF();
    private int selectedCellIndex = -1;
    private int pressedCellIndex = -1;
    private boolean analyzing;
    private OnGridCellClickListener listener;

    public PocOverlayView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(20, 24, 28));
        setClickable(true);
        setFocusable(true);
        setContentDescription("Фотография блюда с сеткой 3 на 3 и областями модели");

        gridPaint.setColor(Color.WHITE);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(2));
        selectedGridPaint.setColor(Color.argb(75, 30, 180, 100));
        selectedGridPaint.setStyle(Paint.Style.FILL);
        regionPaint.setColor(Color.rgb(255, 193, 7));
        regionPaint.setStyle(Paint.Style.STROKE);
        regionPaint.setStrokeWidth(dp(2));
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(dp(14));
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        textPaint.setShadowLayer(dp(3), 0, dp(1), Color.BLACK);
        loadingScrimPaint.setColor(Color.argb(145, 0, 0, 0));
        spinnerPaint.setColor(Color.WHITE);
        spinnerPaint.setStyle(Paint.Style.STROKE);
        spinnerPaint.setStrokeWidth(dp(5));
        spinnerPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        requestLayout();
        invalidate();
    }

    public void setRegions(List<RegionOverlay> regions) {
        this.regions = regions == null ? Collections.<RegionOverlay>emptyList() : regions;
        invalidate();
    }

    public void setAnalyzing(boolean analyzing) {
        this.analyzing = analyzing;
        setContentDescription(analyzing
                ? "Фотография блюда анализируется"
                : "Фотография блюда с сеткой 3 на 3 и областями модели");
        invalidate();
    }

    public void setSelectedCell(int cellIndex) {
        selectedCellIndex = cellIndex >= 0 && cellIndex < 9 ? cellIndex : -1;
        invalidate();
    }

    public void setOnGridCellClickListener(OnGridCellClickListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0) {
            width = dp(320);
        }
        int height;
        if (bitmap != null && bitmap.getWidth() > 0) {
            height = Math.round(width * bitmap.getHeight() / (float) bitmap.getWidth());
        } else {
            height = dp(240);
        }
        height = Math.max(dp(220), Math.min(height, dp(520)));
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null || bitmap.getWidth() == 0 || bitmap.getHeight() == 0) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Фото пока не выбрано", getWidth() / 2f, getHeight() / 2f, textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
            return;
        }

        float scale = Math.min(
                getWidth() / (float) bitmap.getWidth(),
                getHeight() / (float) bitmap.getHeight()
        );
        float drawnWidth = bitmap.getWidth() * scale;
        float drawnHeight = bitmap.getHeight() * scale;
        imageRect.set(
                (getWidth() - drawnWidth) / 2f,
                (getHeight() - drawnHeight) / 2f,
                (getWidth() + drawnWidth) / 2f,
                (getHeight() + drawnHeight) / 2f
        );
        canvas.drawBitmap(bitmap, null, imageRect, bitmapPaint);

        float cellWidth = imageRect.width() / 3f;
        float cellHeight = imageRect.height() / 3f;
        if (selectedCellIndex >= 0) {
            int row = selectedCellIndex / 3;
            int column = selectedCellIndex % 3;
            canvas.drawRect(
                    imageRect.left + column * cellWidth,
                    imageRect.top + row * cellHeight,
                    imageRect.left + (column + 1) * cellWidth,
                    imageRect.top + (row + 1) * cellHeight,
                    selectedGridPaint
            );
        }

        for (int index = 1; index < 3; index++) {
            float x = imageRect.left + index * cellWidth;
            float y = imageRect.top + index * cellHeight;
            canvas.drawLine(x, imageRect.top, x, imageRect.bottom, gridPaint);
            canvas.drawLine(imageRect.left, y, imageRect.right, y, gridPaint);
        }
        canvas.drawRect(imageRect, gridPaint);
        for (int index = 0; index < 9; index++) {
            int row = index / 3;
            int column = index % 3;
            canvas.drawText(
                    String.valueOf(index + 1),
                    imageRect.left + column * cellWidth + dp(7),
                    imageRect.top + row * cellHeight + dp(20),
                    textPaint
            );
        }

        for (RegionOverlay region : regions) {
            if (region == null || region.normalizedRect == null) {
                continue;
            }
            RectF rect = normalizedToImage(region.normalizedRect);
            canvas.drawRect(rect, regionPaint);
            String label = region.label == null || region.label.trim().isEmpty()
                    ? "region"
                    : region.label;
            String caption = "grid_prompt".equals(label)
                    ? "grid prompt"
                    : label + " " + Math.round(region.confidence * 100f) + "%";
            float captionY = Math.max(imageRect.top + dp(18), rect.top - dp(4));
            canvas.drawText(caption, rect.left + dp(3), captionY, textPaint);
        }

        if (analyzing) {
            canvas.drawRect(imageRect, loadingScrimPaint);
            float diameter = Math.max(dp(48), Math.min(dp(96), imageRect.width() * 0.14f));
            float centerX = imageRect.centerX();
            float centerY = imageRect.centerY();
            RectF spinnerRect = new RectF(
                    centerX - diameter / 2f,
                    centerY - diameter / 2f,
                    centerX + diameter / 2f,
                    centerY + diameter / 2f
            );
            float startAngle = (SystemClock.uptimeMillis() % 1200L) / 1200f * 360f;
            canvas.drawArc(spinnerRect, startAngle, 270f, false, spinnerPaint);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Анализ…", centerX, centerY + diameter / 2f + dp(28), textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
            postInvalidateOnAnimation();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (bitmap == null) {
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            pressedCellIndex = cellAt(event.getX(), event.getY());
            return pressedCellIndex >= 0;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int releasedCellIndex = cellAt(event.getX(), event.getY());
            boolean clicked = pressedCellIndex >= 0 && pressedCellIndex == releasedCellIndex;
            pressedCellIndex = -1;
            if (clicked) {
                setSelectedCell(releasedCellIndex);
                performClick();
                if (listener != null) {
                    listener.onGridCellClick(releasedCellIndex);
                }
            }
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            pressedCellIndex = -1;
            return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private int cellAt(float x, float y) {
        if (!imageRect.contains(x, y) || imageRect.width() <= 0 || imageRect.height() <= 0) {
            return -1;
        }
        int column = Math.min(2, (int) ((x - imageRect.left) / (imageRect.width() / 3f)));
        int row = Math.min(2, (int) ((y - imageRect.top) / (imageRect.height() / 3f)));
        return row * 3 + column;
    }

    private RectF normalizedToImage(RectF normalized) {
        float left = clamp(normalized.left, 0f, 1f);
        float top = clamp(normalized.top, 0f, 1f);
        float right = clamp(normalized.right, 0f, 1f);
        float bottom = clamp(normalized.bottom, 0f, 1f);
        return new RectF(
                imageRect.left + left * imageRect.width(),
                imageRect.top + top * imageRect.height(),
                imageRect.left + right * imageRect.width(),
                imageRect.top + bottom * imageRect.height()
        );
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private int dp(int value) {
        return Math.round(dp((float) value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
