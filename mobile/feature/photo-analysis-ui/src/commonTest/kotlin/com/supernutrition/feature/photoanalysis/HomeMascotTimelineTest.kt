package com.supernutrition.feature.photoanalysis

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeMascotTimelineTest {
    @Test
    fun `sprite advances twelve frames per second and loops after seventy one frames`() {
        assertEquals(0, homeMascotFrame(0, 1f))
        assertEquals(1, homeMascotFrame(83_333_334L, 1f))
        assertEquals(70, homeMascotFrame(5_833_333_334L, 1f))
        assertEquals(0, homeMascotFrame(5_916_666_667L, 1f))
        assertEquals(1, homeMascotFrame(6_000_000_001L, 1f))
        assertEquals(1, homeMascotFrame(166_666_667L, 2f))
    }

    @Test
    fun `reduced motion and invalid timeline inputs hold the first frame`() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { scale ->
            assertEquals(0, homeMascotFrame(5_000_000_000L, scale))
        }
        assertEquals(0, homeMascotFrame(-100, 1f))
    }
}
