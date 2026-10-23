package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ReviewProfileRefreshTest {
    @Test
    fun `review heading follows the current twenty sp type hierarchy`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val copy = PhotoAnalysisPresenterCopyFixtures.localized()
        setContent {
            PhotoAnalysisCaptureScreen(
                cameraReviewFixture(),
                {},
                PhotoAnalysisCaptureSurface(presenterCopy = copy),
            )
        }
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText(copy.review.title, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(20.sp, layouts.single().layoutInput.style.fontSize)
    }

    @Test
    fun `profile name edits in place without an overlaid edit dialog`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val copy = ProfileScreenCopy()
        setContent { ProfileScreen("Alex", emptyList(), ProfileScreenActions({}, {})) }
        onNodeWithTag(UiAutomationIds.PROFILE_EDIT_NAME.value).performClick()
        onNode(hasSetTextAction()).assertExists()
        onNodeWithText(copy.editName, useUnmergedTree = true).assertDoesNotExist()
    }
}
