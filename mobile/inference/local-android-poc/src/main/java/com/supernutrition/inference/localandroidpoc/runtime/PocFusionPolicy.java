package com.supernutrition.inference.localandroidpoc.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Research-only bounded late-fusion policy for the Android PoC. */
final class PocFusionPolicy {
    static final float PRIOR_WEIGHT = 0.20f;

    private PocFusionPolicy() {
    }

    static Map<String, Float> compositionSupport(List<PocAnalysis.DishCandidate> dishes) {
        Map<String, Float> support = new LinkedHashMap<>();
        int limit = Math.min(3, dishes.size());
        for (int index = 0; index < limit; index++) {
            PocAnalysis.DishCandidate dish = dishes.get(index);
            for (String ingredient : PocDishIngredientPriors.forDish(dish.label)) {
                Float current = support.get(ingredient);
                support.put(ingredient, (current == null ? 0.0f : current) + dish.confidence);
            }
        }
        return support;
    }

    static List<String> compositionLabels(List<PocAnalysis.DishCandidate> dishes) {
        return new ArrayList<>(compositionSupport(dishes).keySet());
    }

    static float normalizedSupport(Map<String, Float> support, String label) {
        Float value = support.get(label);
        if (value == null || value <= 0.0f) {
            return 0.0f;
        }
        float maximum = 0.0f;
        for (Float candidate : support.values()) {
            if (candidate != null) {
                maximum = Math.max(maximum, candidate);
            }
        }
        return maximum <= 0.0f ? 0.0f : value / maximum;
    }

    static float rankScore(float visualConfidence, float compositionSupport) {
        return (1.0f - PRIOR_WEIGHT) * visualConfidence + PRIOR_WEIGHT * compositionSupport;
    }
}
