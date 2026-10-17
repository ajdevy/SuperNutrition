package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A host can refresh data and callbacks without recreating the open screen. */
@OptIn(ExperimentalTestApi::class)
class MainScreenRefreshTest {
    @Test
    fun `Home refreshes metrics and dialog copy and uses the current action callbacks`() = runSkikoComposeUiTest(
        size = Size(600f, 932f),
        density = Density(1f),
    ) {
        val model = mutableStateOf(layoutHomeModel())
        val size = mutableStateOf(Size(375f, 900f))
        val fontScale = mutableStateOf(1f)
        val events = mutableListOf<String>()
        val add = mutableStateOf<() -> Unit>({ events.add("old add") })
        val goals = mutableStateOf<() -> Unit>({ events.add("old goals") })
        val profile = mutableStateOf<() -> Unit>({ events.add("old profile") })
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale.value)) {
                Box(Modifier.requiredSize(size.value.width.dp, size.value.height.dp)) {
                    HomeScreen(
                        model.value,
                        onAddFoodRequested = add.value,
                        onViewGoalsRequested = goals.value,
                        onProfileRequested = profile.value,
                    )
                }
            }
        }
        onNodeWithTag(UiAutomationIds.HOME_ADD_FOOD.value).performClick()
        onNodeWithTag(UiAutomationIds.HOME_VIEW_GOALS.value).performClick()
        onNodeWithText("Your goals").assertIsDisplayed()
        runOnIdle { model.value = updatedHomeModel(model.value) }
        onNodeWithText("Updated goals").assertIsDisplayed()
        onNodeWithText("Water 2 L").assertIsDisplayed()
        onNodeWithText("Close goals").performClick()
        onNodeWithText("Friday, Aug 8").assertIsDisplayed()
        onNodeWithText("18 g").assertIsDisplayed()
        onNodeWithText("2 / 2 L").assertIsDisplayed()
        runOnIdle {
            add.value = { events.add("new add") }
            goals.value = { events.add("new goals") }
            profile.value = { events.add("new profile") }
        }
        onNodeWithTag(UiAutomationIds.HOME_ADD_FOOD.value).performClick()
        onNodeWithTag(UiAutomationIds.HOME_PROFILE.value).performClick()
        onNodeWithTag(UiAutomationIds.HOME_VIEW_GOALS.value).performClick()
        onNodeWithText("Close goals").performClick()
        onNodeWithText("Add water").performScrollTo().performClick()
        runOnIdle { size.value = Size(320f, 536f) }
        onNodeWithTag(UiAutomationIds.HOME_ADD_FOOD.value).performScrollTo().assertIsDisplayed()
        runOnIdle { fontScale.value = 1.5f }
        onNodeWithTag(UiAutomationIds.HOME_NEXT_STEP.value).performScrollTo().assertIsDisplayed()
        onNodeWithText("Add water").performClick()
        assertEquals(expectedHomeActions(), events)
    }

    @Test
    fun `Rewards updates earned state and remains reachable after live text and size changes`() = runSkikoComposeUiTest(
        size = Size(600f, 932f),
        density = Density(1f),
    ) {
        val model = mutableStateOf(layoutRewardsModel().copy(cards = emptyList()))
        val width = mutableStateOf(375.dp)
        val fontScale = mutableStateOf(1f)
        val inset = mutableStateOf(0.dp)
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, fontScale.value),
                LocalMainBottomBarInset provides inset.value,
            ) {
                RewardsScreen(model.value, Modifier.requiredSize(width.value, 812.dp))
            }
        }
        onNodeWithTag(UiAutomationIds.REWARDS_TITLE.value).assertIsDisplayed()
        runOnIdle { model.value = layoutRewardsModel() }
        onNodeWithTag(UiAutomationIds.rewardsRowProgress(1).value).assertTextEquals("0 of 1")
        runOnIdle {
            model.value = model.value.copy(
                title = "Your rewards",
                cards = model.value.cards.mapIndexed { index, card ->
                    if (index == 1) {
                        card.copy(
                            isEarned = true,
                            progressLabel = "1 of 1",
                            stateLabel = "Earned today",
                        )
                    } else {
                        card
                    }
                },
            )
        }
        onNodeWithText("Your rewards").assertIsDisplayed()
        onNodeWithTag(UiAutomationIds.rewardsRowProgress(1).value).assertTextEquals("1 of 1")
        onNodeWithTag(UiAutomationIds.rewardsRowState(1).value).assertTextEquals("Earned today")
        runOnIdle { inset.value = MainBottomBarHeight }
        runOnIdle { width.value = 300.dp }
        onNodeWithTag(UiAutomationIds.REWARDS_LIST.value).performScrollToIndex(7)
        val final = onNodeWithTag(
            UiAutomationIds.rewardsRow(6).value,
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(final.bottom <= 812f - MainBottomBarHeight.value)
        runOnIdle { fontScale.value = 1.5f }
        onNodeWithTag(UiAutomationIds.REWARDS_LIST.value).performScrollToIndex(7)
        onNodeWithTag(UiAutomationIds.rewardsRowState(6).value).assertIsDisplayed()
    }

    @Test
    fun `tab navigation and host insets update in place without stale labels or callbacks`() = runSkikoComposeUiTest(
        size = Size(375f, 932f),
        density = Density(1f),
    ) {
        val state = mutableStateOf(layoutBottomBarState())
        val padding = mutableStateOf(PaddingValues())
        val modifier = mutableStateOf<Modifier>(Modifier)
        val events = mutableListOf<String>()
        val actions = mutableStateOf(
            PhotoAnalysisMainBottomBarActions(
                { state.value = state.value.copy(selectedTab = 0) },
                { state.value = state.value.copy(selectedTab = 1) },
                { events.add("old camera") },
            ),
        )
        setContent {
            PhotoAnalysisMainLayout(
                state.value,
                actions.value,
                modifier = modifier.value,
                contentPadding = padding.value,
            ) {
                if (state.value.selectedTab == 0) HomeScreen(layoutHomeModel()) else RewardsScreen(layoutRewardsModel())
            }
        }
        onNodeWithTag(UiAutomationIds.CAMERA_ACTION.value).performClick()
        onNodeWithTag(UiAutomationIds.REWARDS_TAB.value).performClick().assertIsSelected()
        runOnIdle { padding.value = PaddingValues(top = 24.dp, bottom = 20.dp) }
        assertEquals(912f, onNodeWithTag(UiAutomationIds.REWARDS_LIST.value).fetchSemanticsNode().boundsInRoot.bottom)
        runOnIdle {
            state.value = state.value.copy(
                homeLabel = "Today",
                rewardsLabel = "Badges",
                cameraLabel = "Add photo",
            )
        }
        onNodeWithContentDescription("Badges").assertIsSelected()
        onNodeWithContentDescription("Add photo").assertIsDisplayed()
        runOnIdle {
            actions.value = actions.value.copy(
                onHomeSelected = {
                    events.add("new home")
                    state.value = state.value.copy(selectedTab = 0)
                },
                onCameraRequested = { events.add("new camera") },
            )
            modifier.value = Modifier.requiredSize(320.dp, 800.dp)
        }
        onNodeWithTag(UiAutomationIds.HOME_TAB.value).performClick().assertIsSelected()
        onNodeWithTag(UiAutomationIds.CAMERA_ACTION.value).performClick()
        onNodeWithText("Super Nutrition").assertIsDisplayed()
        assertEquals(listOf("old camera", "new home", "new camera"), events)
    }

    private fun updatedHomeModel(model: HomeScreenModel) = model.copy(
        dateLabel = "Friday, Aug 8", careQuestion = "Ready for lunch?",
        fiber = HomeMetricModel("Fiber", "18 g"), water = HomeMetricModel("Water", "2 / 2 L"),
        goalsTitle = "Updated goals", goalsDescription = "Water 2 L", goalsDoneLabel = "Close goals",
        nextStepDescription = "Add a glass of water", nextStepAction = "Add water",
    )
    private fun expectedHomeActions() = listOf(
        "old add",
        "old goals",
        "new add",
        "new profile",
        "new goals",
        "new add",
        "new add",
    )
}
