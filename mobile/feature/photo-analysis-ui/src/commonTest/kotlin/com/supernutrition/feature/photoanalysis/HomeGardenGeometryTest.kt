package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeGardenGeometryTest {
    @Test
    fun `ordinary tall wide and enlarged text slots have floor clearance without uncovered canvas`() {
        listOf(
            Size(360f, 780f) to Rect(16f, 205f, 344f, 348f),
            Size(412f, 915f) to Rect(16f, 208f, 396f, 370.5f),
            Size(600f, 800f) to Rect(85f, 230f, 515f, 392.5f),
            Size(320f, 568f) to Rect(16f, 252f, 304f, 414.5f),
            Size(360f, 780f) to Rect(16f, 380f, 344f, 542.5f),
        ).forEach { (viewport, mascot) ->
            val scene = homeGardenImageBounds(viewport, mascot)
            assertCovered(viewport, scene)
            val contact = mascot.top + mascot.height * 319f / 325f
            val floor = scene.top + scene.height * 455f / 1024f
            assertEquals(contact - mascot.height * 0.36f, floor, 0.01f)
            assertTrue(floor < contact - 32f, "The paws remain too close to the horizon: $viewport $scene")
        }
    }

    @Test
    fun `missing or invalid mascot bounds fall back to a centered covered garden`() {
        val viewport = Size(600f, 800f)
        val fallback = homeGardenImageBounds(viewport, null)
        assertCovered(viewport, fallback)
        assertEquals(viewport.height / 2f, fallback.center.y, 0.01f)
        listOf(
            Rect.Zero,
            Rect(100f, 100f, 0f, 200f),
            Rect(0f, 200f, 100f, 100f),
            Rect(Float.NaN, 0f, 100f, 100f),
            Rect(0f, Float.NaN, 100f, 100f),
            Rect(0f, 0f, Float.POSITIVE_INFINITY, 100f),
            Rect(0f, 0f, 100f, Float.POSITIVE_INFINITY),
        ).forEach { assertEquals(fallback, homeGardenImageBounds(viewport, it)) }
    }

    @Test
    fun `offscreen mascot contacts clamp before scaling instead of exposing canvas`() {
        val viewport = Size(360f, 780f)
        listOf(-1_000_000f, -500f, 900f, 1_000_000f).forEach { top ->
            val scene = homeGardenImageBounds(viewport, Rect(16f, top, 344f, top + 162.5f))
            assertCovered(viewport, scene)
            val floor = scene.top + scene.height * 455f / 1024f
            assertTrue(floor in -0.01f..viewport.height)
            val maximumHeight = maxOf(viewport.height / (455f / 1024f), viewport.height / (1f - 455f / 1024f))
            assertTrue(scene.height <= maximumHeight + 1f)
        }
    }

    @Test
    fun `invalid canvas sizes produce an empty finite scene`() {
        listOf(
            Size.Zero,
            Size(-1f, 780f),
            Size(360f, -1f),
            Size(Float.NaN, 780f),
            Size(360f, Float.NaN),
            Size(Float.POSITIVE_INFINITY, 780f),
            Size(360f, Float.POSITIVE_INFINITY),
        ).forEach { assertEquals(Rect.Zero, homeGardenImageBounds(it, null)) }
    }

    private fun assertCovered(viewport: Size, scene: Rect) {
        assertTrue(listOf(scene.left, scene.top, scene.right, scene.bottom).all { it.isFinite() })
        assertTrue(scene.left <= 0.01f && scene.top <= 0.01f, "Uncovered top/left: $viewport $scene")
        assertTrue(scene.right >= viewport.width - 0.01f && scene.bottom >= viewport.height - 0.01f)
        assertEquals(473f / 1024f, scene.width / scene.height, 0.0001f, "The garden's aspect ratio changed")
        assertEquals(viewport.width / 2f, scene.center.x, 0.01f)
    }
}
