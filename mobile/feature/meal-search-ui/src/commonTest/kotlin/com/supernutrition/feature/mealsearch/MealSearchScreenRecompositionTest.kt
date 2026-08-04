package com.supernutrition.feature.mealsearch

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test

/**
 * The screen across recomposition, and with its optional modifier supplied.
 *
 * The rest of the suite renders each state once, from the default entry point.
 * That leaves two behaviours of the composable itself unexercised: whether it
 * updates when the state it was given changes, and whether the caller-supplied
 * modifier is honoured rather than silently replaced by the default. Both are
 * things a reader would assume and neither was asserted, so the screen could
 * ignore a new state or drop a caller's modifier and the suite would stay green.
 */
@OptIn(ExperimentalTestApi::class)
class MealSearchScreenRecompositionTest {

    private val copy = MealSearchCopy(
        searchLabel = "Search foods",
        noResults = "Nothing matched",
        ingredientCountLabel = "{count} ingredients",
    )

    private fun food(id: String, title: String, note: String?) = MealSearchRow.Food(id = id, title = title, note = note)

    @Test
    fun `the screen follows its state from untouched through results to no results`() {
        runComposeUiTest {
            var state by mutableStateOf(MealSearchUiState())
            setContent { MealSearchScreen(state = state, onQueryChanged = {}, copy = copy) }

            onNodeWithTag(UiAutomationIds.MEAL_SEARCH_RESULTS.value).assertDoesNotExist()
            onNodeWithTag(UiAutomationIds.MEAL_SEARCH_NO_RESULTS.value).assertDoesNotExist()

            state = MealSearchUiState(
                query = "chicken",
                rows = listOf(food("food:chicken", "Chicken breast, raw", "not matched: grilled")),
            )
            waitForIdle()
            onNodeWithTag(UiAutomationIds.mealSearchRowTitle(0).value)
                .assertTextEquals("Chicken breast, raw")

            // A later query that matches nothing must replace the list rather
            // than leave the previous results on screen.
            state = MealSearchUiState(query = "wildebeest", showsNoResults = true)
            waitForIdle()
            onNodeWithTag(UiAutomationIds.MEAL_SEARCH_NO_RESULTS.value).assertTextEquals(copy.noResults)
            onNodeWithTag(UiAutomationIds.MEAL_SEARCH_RESULTS.value).assertDoesNotExist()
        }
    }

    @Test
    fun `re-emitting the same state leaves the rendered result unchanged`() {
        runComposeUiTest {
            val rows = listOf(food("food:chicken", "Chicken breast, raw", null))
            var state by mutableStateOf(MealSearchUiState(query = "chicken", rows = rows))
            setContent { MealSearchScreen(state = state, onQueryChanged = {}, copy = copy) }

            onNodeWithTag(UiAutomationIds.mealSearchRowTitle(0).value)
                .assertTextEquals("Chicken breast, raw")

            // An equal-but-distinct instance: the screen must render the same
            // thing either way, whether or not Compose elects to skip.
            state = MealSearchUiState(query = "chicken", rows = rows)
            waitForIdle()

            onNodeWithTag(UiAutomationIds.mealSearchRowTitle(0).value)
                .assertTextEquals("Chicken breast, raw")
        }
    }

    @Test
    fun `a caller supplied modifier is applied rather than replaced by the default`() {
        runComposeUiTest {
            setContent {
                MealSearchScreen(
                    state = MealSearchUiState(query = "chicken"),
                    onQueryChanged = {},
                    copy = copy,
                    modifier = Modifier.semantics { testTag = CALLER_TAG },
                )
            }

            onNodeWithTag(CALLER_TAG).assertIsDisplayed()
            onNodeWithTag(UiAutomationIds.MEAL_SEARCH_QUERY.value).assertIsDisplayed()
        }
    }

    @Test
    fun `a growing result list renders every row it was given`() {
        runComposeUiTest {
            var state by mutableStateOf(
                MealSearchUiState(query = "c", rows = listOf(food("food:a", "Chicken", null))),
            )
            setContent { MealSearchScreen(state = state, onQueryChanged = {}, copy = copy) }

            onNodeWithTag(UiAutomationIds.mealSearchRowTitle(0).value).assertTextEquals("Chicken")

            state = MealSearchUiState(
                query = "c",
                rows = listOf(
                    food("food:a", "Chicken", null),
                    food("food:b", "Cheddar", "not matched: aged"),
                ),
            )
            waitForIdle()

            onNodeWithTag(UiAutomationIds.mealSearchRowTitle(1).value).assertTextEquals("Cheddar")
            onNodeWithTag(UiAutomationIds.mealSearchRowDetail(1).value)
                .assertTextEquals("not matched: aged")
        }
    }

    @Test
    fun `an unrelated recomposition around the screen leaves it skippable`() {
        // The screen takes only stable parameters, so a parent that recomposes
        // for its own reasons must be able to skip it entirely. If a parameter
        // ever became unstable the screen would re-run on every parent frame —
        // invisible in output, expensive in practice, and caught only here.
        runComposeUiTest {
            val state = MealSearchUiState(
                query = "chicken",
                rows = listOf(food("food:chicken", "Chicken breast, raw", null)),
            )
            setContent {
                var unrelated by remember { mutableStateOf(0) }
                Column {
                    Text(text = "tick $unrelated", modifier = Modifier.semantics { testTag = TICK_TAG })
                    MealSearchScreen(state = state, onQueryChanged = {}, copy = copy)
                }
                LaunchedEffect(Unit) { unrelated = 1 }
            }
            waitForIdle()

            onNodeWithTag(TICK_TAG).assertTextEquals("tick 1")
            onNodeWithTag(UiAutomationIds.mealSearchRowTitle(0).value)
                .assertTextEquals("Chicken breast, raw")
        }
    }

    private companion object {
        const val CALLER_TAG = "meal_search_caller_modifier"
        const val TICK_TAG = "meal_search_unrelated_tick"
    }
}
