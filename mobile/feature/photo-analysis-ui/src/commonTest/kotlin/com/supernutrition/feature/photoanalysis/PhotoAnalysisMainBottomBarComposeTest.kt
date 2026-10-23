package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PhotoAnalysisMainBottomBarComposeTest {
    @Test
    fun `bottom bar exposes both tabs and routes central camera action`() = runComposeUiTest {
        val events = mutableListOf<String>()

        setContent {
            Box(Modifier.requiredSize(375.dp, 812.dp)) {
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
        }

        onNodeWithTag(UiAutomationIds.BOTTOM_BAR_PILL.value, useUnmergedTree = true)
            .assertWidthIsEqualTo(343.dp)
            .assertHeightIsEqualTo(64.dp)
        onNodeWithTag(UiAutomationIds.CAPTURE_TAB.value)
            .assertIsDisplayed()
            .assertIsSelected()
            .performClick()
        onNodeWithTag(UiAutomationIds.REWARDS_TAB.value)
            .assertIsDisplayed()
            .assertIsNotSelected()
            .performClick()
        onNodeWithTag(UiAutomationIds.CAMERA_ACTION.value).assertIsDisplayed().performClick()

        assertEquals(listOf("home", "rewards", "camera"), events)
    }
}
