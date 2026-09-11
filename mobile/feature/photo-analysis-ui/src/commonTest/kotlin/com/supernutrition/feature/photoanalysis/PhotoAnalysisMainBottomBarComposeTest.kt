package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PhotoAnalysisMainBottomBarComposeTest {
    @Test
    fun `bottom bar exposes both tabs and routes central camera action`() = runComposeUiTest {
        val events = mutableListOf<String>()

        setContent {
            PhotoAnalysisMainBottomBar(
                state = PhotoAnalysisMainBottomBarState(
                    selectedTab = 0,
                    homeLabel = "Home",
                    rewardsLabel = "Rewards",
                    cameraLabel = "Take photo",
                ),
                actions = PhotoAnalysisMainBottomBarActions(
                    onHomeSelected = { events += "home" },
                    onRewardsSelected = { events += "rewards" },
                    onCameraRequested = { events += "camera" },
                ),
            )
        }

        onNodeWithTag(UiAutomationIds.CAPTURE_TAB.value).assertIsDisplayed().performClick()
        onNodeWithTag(UiAutomationIds.REWARDS_TAB.value).assertIsDisplayed().performClick()
        onNodeWithTag(UiAutomationIds.CAMERA_ACTION.value).assertIsDisplayed().performClick()

        assertEquals(listOf("home", "rewards", "camera"), events)
    }
}
