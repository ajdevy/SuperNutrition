package com.supernutrition.g3benchmark.poc;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class PocInferenceMathTest {
    @Test
    public void softmaxNormalizesAndTopKIsStable() {
        float[] probabilities = PocInferenceMath.softmax(new float[]{1.0f, 3.0f, 2.0f});

        assertEquals(1.0f, probabilities[0] + probabilities[1] + probabilities[2], 0.0001f);
        assertArrayEquals(new int[]{1, 2, 0}, PocInferenceMath.topK(probabilities, 3));
        assertTrue(probabilities[1] > probabilities[2]);
    }

    @Test
    public void gridCellUsesClampedRegionCenter() {
        assertEquals(0, PocInferenceMath.gridCellIndex(-1.0f, -1.0f, 0.1f, 0.1f));
        assertEquals(4, PocInferenceMath.gridCellIndex(0.34f, 0.34f, 0.66f, 0.66f));
        assertEquals(8, PocInferenceMath.gridCellIndex(0.9f, 0.9f, 2.0f, 2.0f));
    }

    @Test
    public void gridCellBoundsCoverTheSelectedCell() {
        assertArrayEquals(new float[]{0.0f, 0.0f, 1.0f / 3.0f, 1.0f / 3.0f},
                PocInferenceMath.gridCellBounds(0), 0.0001f);
        assertArrayEquals(new float[]{2.0f / 3.0f, 2.0f / 3.0f, 1.0f, 1.0f},
                PocInferenceMath.gridCellBounds(8), 0.0001f);
    }

    @Test
    public void iouRejectsDisjointRegions() {
        assertEquals(0.0f, PocInferenceMath.iou(0.0f, 0.0f, 0.2f, 0.2f,
                0.8f, 0.8f, 1.0f, 1.0f), 0.0001f);
        assertEquals(1.0f, PocInferenceMath.iou(0.0f, 0.0f, 1.0f, 1.0f,
                0.0f, 0.0f, 1.0f, 1.0f), 0.0001f);
    }
}
