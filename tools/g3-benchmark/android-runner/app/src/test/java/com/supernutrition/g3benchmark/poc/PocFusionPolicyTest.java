package com.supernutrition.inference.localandroidpoc.runtime;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class PocFusionPolicyTest {
    @Test
    public void compositionSupportUsesTopThreeDishCandidates() {
        List<PocAnalysis.DishCandidate> dishes = Arrays.asList(
                new PocAnalysis.DishCandidate("pork_chop", 0.7f),
                new PocAnalysis.DishCandidate("garden_salad", 0.2f),
                new PocAnalysis.DishCandidate("grilled_salmon", 0.1f),
                new PocAnalysis.DishCandidate("pizza", 0.9f));

        Map<String, Float> support = PocFusionPolicy.compositionSupport(dishes, PocFusionPolicyTest::priorsForDish);

        assertEquals(0.2f, support.get("tomato"), 0.0001f);
        assertEquals(0.1f, support.get("broccoli"), 0.0001f);
        assertTrue(!support.containsKey("dough"));
    }

    @Test
    public void normalizedSupportIsBoundedAndMissingLabelsAreZero() {
        List<PocAnalysis.DishCandidate> dishes = Arrays.asList(
                new PocAnalysis.DishCandidate("pork_chop", 0.8f),
                new PocAnalysis.DishCandidate("garden_salad", 0.2f));
        Map<String, Float> support = PocFusionPolicy.compositionSupport(dishes, PocFusionPolicyTest::priorsForDish);

        assertEquals(1.0f, PocFusionPolicy.normalizedSupport(support, "carrot"), 0.0001f);
        assertEquals(0.0f, PocFusionPolicy.normalizedSupport(support, "broccoli"), 0.0001f);
    }

    @Test
    public void lateFusionOnlyBoundsVisualRankAndNeverDeletesEvidence() {
        float visual = PocFusionPolicy.rankScore(0.8f, 0.0f);
        float supported = PocFusionPolicy.rankScore(0.8f, 1.0f);

        assertTrue(supported > visual);
        assertTrue(visual > 0.0f);
        assertTrue(supported <= 1.0f);
    }

    private static List<String> priorsForDish(String dishLabel) {
        if ("pork_chop".equals(dishLabel)) {
            return Arrays.asList("pork", "carrot");
        }
        if ("garden_salad".equals(dishLabel)) {
            return Arrays.asList("tomato", "carrot");
        }
        if ("grilled_salmon".equals(dishLabel)) {
            return Arrays.asList("broccoli");
        }
        return Collections.emptyList();
    }
}
