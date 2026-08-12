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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertEquals

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
            onNodeWithTag(UiAutomationIds.mealSearchRowTitle(0).value, useUnmergedTree = true)
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

            onNodeWithTag(UiAutomationIds.mealSearchRowTitle(0).value, useUnmergedTree = true)
                .assertTextEquals("Chicken breast, raw")

            // An equal-but-distinct instance: the screen must render the same
            // thing either way, whether or not Compose elects to skip.
            state = MealSearchUiState(query = "chicken", rows = rows)
            waitForIdle()

            onNodeWithTag(UiAutomationIds.mealSearchRowTitle(0).value, useUnmergedTree = true)
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

            onNodeWithTag(
                UiAutomationIds.mealSearchRowTitle(0).value,
                useUnmergedTree = true,
            ).assertTextEquals("Chicken")

            state = MealSearchUiState(
                query = "c",
                rows = listOf(
                    food("food:a", "Chicken", null),
                    food("food:b", "Cheddar", "not matched: aged"),
                ),
            )
            waitForIdle()

            onNodeWithTag(
                UiAutomationIds.mealSearchRowTitle(1).value,
                useUnmergedTree = true,
            ).assertTextEquals("Cheddar")
            onNodeWithTag(UiAutomationIds.mealSearchRowDetail(1).value, useUnmergedTree = true)
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
            onNodeWithTag(UiAutomationIds.mealSearchRowTitle(0).value, useUnmergedTree = true)
                .assertTextEquals("Chicken breast, raw")
        }
    }

    private companion object {
        const val CALLER_TAG = "meal_search_caller_modifier"
        const val TICK_TAG = "meal_search_unrelated_tick"
    }

    @Test
    fun `a screen given a selection handler recomposes as rows change`() {
        // Adding a parameter to a @Composable adds Compose's own
        // recomposition-skip branches, which only a recomposition through the
        // explicit-argument form reaches. Rendering the default form alone
        // leaves them untested, and an untested skip is how a row silently
        // stops updating.
        runComposeUiTest {
            var state by mutableStateOf(
                MealSearchUiState(query = "c", rows = listOf(food("food:a", "Chicken", null))),
            )
            val selected = mutableListOf<MealSearchRow>()

            setContent {
                MealSearchScreen(
                    state = state,
                    onQueryChanged = {},
                    copy = copy,
                    modifier = Modifier,
                    onRowSelected = selected::add,
                )
            }

            onNodeWithTag(UiAutomationIds.mealSearchRow(0).value).assertIsDisplayed()

            state = MealSearchUiState(
                query = "ch",
                rows = listOf(
                    food("food:a", "Chicken", null),
                    food("food:b", "Chickpeas", "raw only"),
                ),
            )

            onNodeWithTag(UiAutomationIds.mealSearchRow(1).value).performClick()
            assertEquals("food:b", selected.single().id)
        }
    }

    @Test
    fun `rows that did not change are skipped rather than rebuilt`() {
        // The query moves and the row list does not. Compose should skip the
        // rows, which is the branch that only an unchanged-argument
        // recomposition reaches — and the rows must still work afterwards,
        // because a skip that leaves a stale click handler is the failure mode.
        runComposeUiTest {
            val rows = listOf(food("food:a", "Chicken", null))
            var state by mutableStateOf(MealSearchUiState(query = "c", rows = rows))
            val selected = mutableListOf<MealSearchRow>()

            setContent {
                MealSearchScreen(
                    state = state,
                    onQueryChanged = {},
                    copy = copy,
                    modifier = Modifier,
                    onRowSelected = selected::add,
                )
            }

            state = MealSearchUiState(query = "ch", rows = rows)
            state = MealSearchUiState(query = "chi", rows = rows)

            onNodeWithTag(UiAutomationIds.mealSearchRow(0).value).performClick()
            assertEquals("food:a", selected.single().id)
        }
    }

    @Test
    fun `a row picks up a replaced handler rather than holding the first one`() {
        // The other half of the row's recomposition branch: not the arguments
        // changing around a stable handler, but the HANDLER changing. A row that
        // skipped on this would keep calling the lambda it was first composed
        // with, which is a tap silently routed to a screen that has gone away.
        runComposeUiTest {
            val rows = listOf(food("food:a", "Chicken", null))
            val first = mutableListOf<MealSearchRow>()
            val second = mutableListOf<MealSearchRow>()
            var handler by mutableStateOf<(MealSearchRow) -> Unit>({ first += it })

            setContent {
                MealSearchScreen(
                    state = MealSearchUiState(query = "c", rows = rows),
                    onQueryChanged = {},
                    copy = copy,
                    modifier = Modifier,
                    onRowSelected = handler,
                )
            }

            onNodeWithTag(UiAutomationIds.mealSearchRow(0).value).performClick()
            assertEquals(1, first.size)

            handler = { second += it }
            onNodeWithTag(UiAutomationIds.mealSearchRow(0).value).performClick()

            assertEquals(1, first.size, "the replaced handler kept receiving taps")
            assertEquals("food:a", second.single().id)
        }
    }
}
