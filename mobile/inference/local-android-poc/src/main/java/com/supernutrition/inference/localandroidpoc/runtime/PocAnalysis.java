package com.supernutrition.inference.localandroidpoc.runtime;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable research-only output. Food-101 candidates are dish-level labels;
 * COCO region labels are limited proposals and are never ingredient claims.
 * No mass or gram field is intentionally present in this contract.
 */
public final class PocAnalysis implements AutoCloseable {
    public final List<DishCandidate> dishCandidates;
    public final List<Region> regions;
    public final String status;
    public final List<String> warnings;
    public final List<String> failedStages;

    PocAnalysis(
            List<DishCandidate> dishCandidates,
            List<Region> regions,
            String status,
            List<String> warnings,
            List<String> failedStages
    ) {
        this.dishCandidates = immutableCopy(dishCandidates);
        this.regions = immutableCopy(regions);
        this.status = status;
        this.warnings = immutableCopy(warnings);
        this.failedStages = immutableCopy(failedStages);
    }

    public List<DishCandidate> getDishCandidates() {
        return dishCandidates;
    }

    public List<Region> getRegions() {
        return regions;
    }

    public String getStatus() {
        return status;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<String> getFailedStages() {
        return failedStages;
    }

    /**
     * Recycles masks returned in this result. Calling this is optional but
     * recommended after the caller has finished consuming the masks.
     */
    @Override
    public void close() {
        for (Region region : regions) {
            region.closeMask();
        }
    }

    public static final class DishCandidate {
        public final String label;
        public final float confidence;

        DishCandidate(String label, float confidence) {
            this.label = label;
            this.confidence = confidence;
        }

        public String getLabel() {
            return label;
        }

        public float getConfidence() {
            return confidence;
        }
    }

    public static final class Region implements AutoCloseable {
        public final String label;
        public final float confidence;
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;
        /** Full-image ARGB alpha mask at a bounded, aspect-preserving size. */
        public final Bitmap mask;
        /** Zero-based row-major index in a stable 3x3 image grid. */
        public final int gridCellIndex;

        Region(
                String label,
                float confidence,
                float left,
                float top,
                float right,
                float bottom,
                Bitmap mask,
                int gridCellIndex
        ) {
            this.label = label;
            this.confidence = confidence;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.mask = mask;
            this.gridCellIndex = gridCellIndex;
        }

        public String getLabel() {
            return label;
        }

        public float getConfidence() {
            return confidence;
        }

        public float getLeft() {
            return left;
        }

        public float getTop() {
            return top;
        }

        public float getRight() {
            return right;
        }

        public float getBottom() {
            return bottom;
        }

        public Bitmap getMask() {
            return mask;
        }

        public int getGridCellIndex() {
            return gridCellIndex;
        }

        @Override
        public void close() {
            closeMask();
        }

        private void closeMask() {
            if (mask != null && !mask.isRecycled()) {
                mask.recycle();
            }
        }
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
