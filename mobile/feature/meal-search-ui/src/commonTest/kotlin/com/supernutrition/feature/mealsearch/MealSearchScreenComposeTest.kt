package com.supernutrition.feature.mealsearch

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The shared production screen, exercised through the Compose UI Test v2 API.
 *
 * The screen is stateless, so every state below is reached by constructing the
 * one the presenter would have produced rather than by driving a search engine
 * to produce it. The presenter's own tests already cover which state a query
 * yields; these cover what the screen does with each.
 */
@OptIn(ExperimentalTestApi::class)
class MealSearchScreenComposeTest {
    private val copy = MealSearchCopy(
        searchLabel = "Search foods",
        noResults = "Nothing matched",
        ingredientCountLabel = "{count} ingredients",
    )

    @Test
    fun `an untouched search box shows neither results nor a no-results message`() {
        // The distinction the presenter insists on. A screen that drew
        // "no results" from `rows.isEmpty()` would greet someone who has typed
        // nothing with a failure message, and would still pass every other test
        // in this file.
        runComposeUiTest {
            setContent { MealSearchScreen(state = MealSearchUiState(), onQueryChanged = {}, copy = copy) }

            onNodeWithTag(UiAutomationIds.MEAL_SEARCH_QUERY.value).assertIsDisplayed()
            onNodeWithTag(UiAutomationIds.MEAL_SEARCH_NO_RESULTS.value).assertDoesNotExist()
            onNodeWithTag(UiAutomationIds.MEAL_SEARCH_RESULTS.value).assertDoesNotExist()
        }
    }

    @Test
    fun `a query that found nothing says so`() {
        runComposeUiTest {
            val state = MealSearchUiState(query = "wildebeest", showsNoResults = true)
            setContent { MealSearchScreen(state = state, onQueryChanged = {}, copy = copy) }

            onNodeWithTag(UiAutomationIds.MEAL_SEARCH_NO_RESULTS.value).assertTextEquals(copy.noResults)
            onNodeWithTag(UiAutomationIds.MEAL_SEARCH_RESULTS.value).assertDoesNotExist()
        }
    }

    @Test
    fun `a food row shows its unmatched preparation before the tap, not after`() {
        // The reason the note is on the row at all: someone choosing between
        // results needs to know the catalog could not honour "grilled" while
        // they are choosing.
        runComposeUiTest {
            val state = MealSearchUiState(
                query = "grilled chicken",
                rows = listOf(
                    MealSearchRow.Food(
                        id = "food:chicken-breast-raw",
                        title = "Chicken breast, raw",
                        note = "not matched: grilled",
                    ),
                ),
            )
            setContent { MealSearchScreen(state = state, onQueryChanged = {}, copy = copy) }

            onNodeWithTag(UiAutomationIds.mealSearchRowTitle(0).value).assertTextEquals("Chicken breast, raw")
            onNodeWithTag(UiAutomationIds.mealSearchRowDetail(0).value).assertTextEquals("not matched: grilled")
        }
    }

    @Test
    fun `a food with nothing unmatched draws no detail line at all`() {
        // Not an empty one. A blank line under every ordinary result would make
        // the unmatched-preparation case invisible by being indistinguishable.
        runComposeUiTest {
            val state = MealSearchUiState(
                query = "egg",
                rows = listOf(MealSearchRow.Food(id = "food:egg-boiled", title = "Egg, boiled", note = null)),
            )
            setContent { MealSearchScreen(state = state, onQueryChanged = {}, copy = copy) }

            onNodeWithTag(UiAutomationIds.mealSearchRowTitle(0).value).assertTextEquals("Egg, boiled")
            onNodeWithTag(UiAutomationIds.mealSearchRowDetail(0).value).assertDoesNotExist()
        }
    }

    @Test
    fun `a dish row says how many items tapping it will add`() {
        runComposeUiTest {
            val state = MealSearchUiState(
                query = "bean bowl",
                rows = listOf(MealSearchRow.Dish(id = "dish:bean-bowl", title = "Bean bowl", ingredientCount = 3)),
            )
            setContent { MealSearchScreen(state = state, onQueryChanged = {}, copy = copy) }

            onNodeWithTag(UiAutomationIds.mealSearchRowDetail(0).value).assertTextEquals("3 ingredients")
        }
    }

    @Test
    fun `rows keep their positions so two rows for one food stay distinguishable`() {
        // "rye toast and rye bread" produces two rows with the same canonical
        // id. Addressing rows by id would make them one; position is what tells
        // them apart, which is the same identity rule the segmentation review
        // reached.
        runComposeUiTest {
            val state = MealSearchUiState(
                query = "rye",
                rows = listOf(
                    MealSearchRow.Food(id = "food:bread-rye", title = "Bread, rye", note = null),
                    MealSearchRow.Food(id = "food:bread-rye", title = "Bread, rye", note = "not matched: toasted"),
                ),
            )
            setContent { MealSearchScreen(state = state, onQueryChanged = {}, copy = copy) }

            onNodeWithTag(UiAutomationIds.mealSearchRow(0).value).assertIsDisplayed()
            onNodeWithTag(UiAutomationIds.mealSearchRow(1).value).assertIsDisplayed()
            onNodeWithTag(UiAutomationIds.mealSearchRowDetail(0).value).assertDoesNotExist()
            onNodeWithTag(UiAutomationIds.mealSearchRowDetail(1).value).assertTextEquals("not matched: toasted")
        }
    }

    @Test
    fun `typing reaches the caller, one change at a time`() {
        runComposeUiTest {
            val typed = mutableListOf<String>()
            setContent {
                MealSearchScreen(state = MealSearchUiState(), onQueryChanged = { typed += it }, copy = copy)
            }

            onNodeWithTag(UiAutomationIds.MEAL_SEARCH_QUERY.value).performTextInput("egg")

            assertEquals(listOf("egg"), typed)
        }
    }
}
