package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Image as SkiaImage

@OptIn(ExperimentalTestApi::class)
class ProfileLayoutTest {
    @Test
    fun `profile follows the design geometry at 375 by 812`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        setContent {
            Box(Modifier.fillMaxSize().background(ProfileBackground).padding(top = 44.dp, bottom = 34.dp)) {
                ProfileScreen("", emptyList(), ProfileScreenActions({}, {}))
            }
        }
        val history = onNodeWithTag(UiAutomationIds.PROFILE_HISTORY.value).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue(abs(history.top - 212f) < 1f, "History starts at ${history.top}")
        assertTrue(abs(history.height - 74f) < 1f, "History is ${history.height} high")
        assertTrue(abs(history.left - 16f) < 1f && abs(history.right - 359f) < 1f)
        onNodeWithTag(UiAutomationIds.PROFILE_CLOSE.value).assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.PROFILE_CRASH_REPORTS.value).assertIsDisplayed()
        writeProfileScreenshot("profile-375x812", captureToImage().asSkiaBitmap())
    }

    @Test
    fun `large profile text scrolls and every action remains reachable`() = runSkikoComposeUiTest(
        size = Size(360f, 780f),
        density = Density(1f, fontScale = 2f),
    ) {
        val copy = ProfileScreenCopy()
        setContent {
            Box(Modifier.fillMaxSize().background(ProfileBackground).padding(top = 24.dp, bottom = 24.dp)) {
                ProfileScreen("Alexandria Example", emptyList(), ProfileScreenActions({}, {}))
            }
        }
        onNodeWithTag(UiAutomationIds.PROFILE_EDIT_NAME.value).assertIsDisplayed()
        writeProfileScreenshot("profile-360x780-200-top", captureToImage().asSkiaBitmap())
        listOf(
            copy.title,
            "Alexandria Example",
            copy.history,
            "0 meals saved",
            copy.privacy,
            copy.terms,
            copy.crashReports,
            copy.crashReportsDescription,
        ).forEach { text ->
            onNodeWithText(text, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
                .assertReadableHomeText(2f)
        }
        listOf(UiAutomationIds.PROFILE_HISTORY, UiAutomationIds.PROFILE_PRIVACY, UiAutomationIds.PROFILE_TERMS)
            .forEach { id -> onNodeWithTag(id.value).performScrollTo().assertIsDisplayed() }
        val diagnostics = onNodeWithTag(UiAutomationIds.PROFILE_CRASH_REPORTS.value)
            .performScrollTo().assertIsDisplayed()
        val diagnosticsBounds = diagnostics.fetchSemanticsNode().boundsInRoot
        val termsBounds = onNodeWithTag(UiAutomationIds.PROFILE_TERMS.value).fetchSemanticsNode().boundsInRoot
        assertTrue(diagnosticsBounds.top >= termsBounds.bottom, "Profile settings rows overlap")
        assertTrue(diagnosticsBounds.height >= 48f)
        writeProfileScreenshot("profile-360x780-200-bottom", captureToImage().asSkiaBitmap())
        diagnostics.performClick()
        onNodeWithText(copy.crashReportsUnavailable).assertIsDisplayed()
    }

    private fun writeProfileScreenshot(name: String, bitmap: org.jetbrains.skia.Bitmap) {
        val destination = File("build/reports/profile-design/$name.png")
        destination.parentFile.mkdirs()
        SkiaImage.makeFromBitmap(bitmap).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)!!.use { data -> destination.writeBytes(data.bytes) }
        }
    }
}
