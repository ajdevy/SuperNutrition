package com.supernutrition.inference.localandroidpoc.runtime;

import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class PocInferenceEngineTest {
    private static final int FEATURE_COUNT = 84;
    private static final int BANANA_CLASS = 46;
    private static final int CARROT_CLASS = 51;

    @Test
    public void parsesChannelsFirstYoloOutput() throws Exception {
        float[][][] tensor = new float[1][FEATURE_COUNT][2];
        tensor[0][0][0] = 320.0f;
        tensor[0][1][0] = 320.0f;
        tensor[0][2][0] = 128.0f;
        tensor[0][3][0] = 128.0f;
        tensor[0][4 + BANANA_CLASS][0] = 0.9f;

        List<PocInferenceEngine.RawRegion> regions = PocInferenceEngine.parseYolo(tensor);

        assertEquals(1, regions.size());
        assertEquals("banana", regions.get(0).label);
        assertEquals(0.4f, regions.get(0).left, 0.0001f);
        assertEquals(0.6f, regions.get(0).right, 0.0001f);
    }

    @Test
    public void parsesRowsFirstYoloOutput() throws Exception {
        float[][][] tensor = new float[1][2][FEATURE_COUNT];
        tensor[0][0][0] = 160.0f;
        tensor[0][0][1] = 160.0f;
        tensor[0][0][2] = 64.0f;
        tensor[0][0][3] = 64.0f;
        tensor[0][0][4 + CARROT_CLASS] = 0.8f;

        List<PocInferenceEngine.RawRegion> regions = PocInferenceEngine.parseYolo(tensor);

        assertEquals(1, regions.size());
        assertEquals("carrot", regions.get(0).label);
        assertEquals(0.2f, regions.get(0).top, 0.0001f);
        assertEquals(0.3f, regions.get(0).bottom, 0.0001f);
    }

    @Test(expected = IOException.class)
    public void rejectsAmbiguousOrUnexpectedFeatureShape() throws Exception {
        PocInferenceEngine.parseYolo(new float[1][FEATURE_COUNT][FEATURE_COUNT]);
    }

    @Test
    public void filtersUnsupportedLabelsAndConfidenceBeforeNms() throws Exception {
        float[][][] tensor = new float[1][FEATURE_COUNT][3];
        setCandidate(tensor, 0, 100.0f, 100.0f, 60.0f, 60.0f, BANANA_CLASS, 0.9f);
        setCandidate(tensor, 1, 100.0f, 100.0f, 60.0f, 60.0f, BANANA_CLASS, 0.8f);
        setCandidate(tensor, 2, 100.0f, 100.0f, 60.0f, 60.0f, 55, 0.99f);

        List<PocInferenceEngine.RawRegion> regions = PocInferenceEngine.parseYolo(tensor);

        assertEquals(1, regions.size());
        assertTrue(regions.get(0).confidence >= 0.9f);
    }

    private static void setCandidate(
            float[][][] tensor,
            int prediction,
            float centerX,
            float centerY,
            float width,
            float height,
            int classIndex,
            float confidence
    ) {
        tensor[0][0][prediction] = centerX;
        tensor[0][1][prediction] = centerY;
        tensor[0][2][prediction] = width;
        tensor[0][3][prediction] = height;
        tensor[0][4 + classIndex][prediction] = confidence;
    }
}
