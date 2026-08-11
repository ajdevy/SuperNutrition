package com.supernutrition.inference.localandroidpoc.runtime;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Locks the parser used by both the shipped local runtime and the device benchmark. */
public final class PocYoloWorldContractTest {
    private static final int FEATURE_COUNT = 84;
    private static final int BANANA_CLASS = 46;
    private static final int CARROT_CLASS = 51;

    @Test
    public void parsesBothExportLayoutsAndKeepsOnlyVisibleFoodLabels() throws Exception {
        float[][][] channelsFirst = new float[1][FEATURE_COUNT][2];
        putChannelsFirst(channelsFirst, 0, 320.0f, 320.0f, 128.0f, 128.0f, BANANA_CLASS, 0.9f);
        putChannelsFirst(channelsFirst, 1, 160.0f, 160.0f, 64.0f, 64.0f, 55, 0.99f);

        float[][][] rowsFirst = new float[1][1][FEATURE_COUNT];
        rowsFirst[0][0][0] = 160.0f;
        rowsFirst[0][0][1] = 160.0f;
        rowsFirst[0][0][2] = 64.0f;
        rowsFirst[0][0][3] = 64.0f;
        rowsFirst[0][0][4 + CARROT_CLASS] = 0.8f;

        List<PocYoloWorldContract.Detection> first = PocYoloWorldContract.parse(channelsFirst);
        List<PocYoloWorldContract.Detection> second = PocYoloWorldContract.parse(rowsFirst);

        assertEquals(1, first.size());
        assertEquals("banana", first.get(0).label());
        assertEquals(0.4f, first.get(0).left(), 0.0001f);
        assertEquals(0.6f, first.get(0).right(), 0.0001f);
        assertEquals(1, second.size());
        assertEquals("carrot", second.get(0).label());
        assertTrue(PocYoloWorldContract.visibleFoodLabels().contains("carrot"));
    }

    private static void putChannelsFirst(
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
