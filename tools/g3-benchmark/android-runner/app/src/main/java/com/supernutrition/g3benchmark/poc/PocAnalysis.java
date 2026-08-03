package com.supernutrition.g3benchmark.poc;

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
    /** Full dish-prior and detector-candidate list for research debugging. */
    public final List<IngredientSuggestion> ingredientSuggestions;
    public final String status;
    public final List<String> warnings;

    PocAnalysis(
            List<DishCandidate> dishCandidates,
            List<Region> regions,
            List<IngredientSuggestion> ingredientSuggestions,
            String status,
            List<String> warnings
    ) {
        this.dishCandidates = immutableCopy(dishCandidates);
        this.regions = immutableCopy(regions);
        this.ingredientSuggestions = immutableCopy(ingredientSuggestions);
        this.status = status;
        this.warnings = immutableCopy(warnings);
    }

    public List<DishCandidate> getDishCandidates() {
        return dishCandidates;
    }

    public List<Region> getRegions() {
        return regions;
    }

    public List<IngredientSuggestion> getIngredientSuggestions() {
        return ingredientSuggestions;
    }

    public String getStatus() {
        return status;
    }

    public List<String> getWarnings() {
        return warnings;
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

    /**
     * Debug-only ingredient candidate. A dish prior is a plausibility hint,
     * not visual evidence; detector confidence is -1 when that label was not
     * emitted by the current detector.
     */
    public static final class IngredientSuggestion {
        public final String label;
        public final float detectorConfidence;
        public final boolean fromDishPrior;
        public final boolean acceptedForRegions;
        public final float compositionSupport;

        IngredientSuggestion(
                String label,
                float detectorConfidence,
                boolean fromDishPrior,
                boolean acceptedForRegions,
                float compositionSupport
        ) {
            this.label = label;
            this.detectorConfidence = detectorConfidence;
            this.fromDishPrior = fromDishPrior;
            this.acceptedForRegions = acceptedForRegions;
            this.compositionSupport = compositionSupport;
        }

        public String getLabel() {
            return label;
        }

        public float getDetectorConfidence() {
            return detectorConfidence;
        }

        public boolean isFromDishPrior() {
            return fromDishPrior;
        }

        public boolean isAcceptedForRegions() {
            return acceptedForRegions;
        }

        public float getCompositionSupport() {
            return compositionSupport;
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
