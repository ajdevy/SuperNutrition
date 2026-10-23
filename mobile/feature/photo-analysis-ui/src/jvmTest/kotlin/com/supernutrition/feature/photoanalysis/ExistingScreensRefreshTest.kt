package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ExistingScreensRefreshTest {
    @Test
    fun `Home never relabels water or movement as new nutrition metrics`() = runSkikoComposeUiTest(
        size = Size(375f, 812f),
        density = Density(1f),
    ) {
        val model = layoutHomeModel()
        setContent { HomeScreen(model) }
        onNodeWithText(model.water.label, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText(model.movement.label, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText(model.nextStepTitle, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `Rewards keeps actual card titles instead of decorative duplicate rewards`() = runSkikoComposeUiTest(
        size = Size(375f, 1000f),
        density = Density(1f),
    ) {
        val model = layoutRewardsModel()
        setContent { RewardsScreen(model) }
        onNodeWithText("Fiber Friend", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("Level 4 • Garden Guide", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText(model.cards.first().title, useUnmergedTree = true).assertExists()
    }
}
