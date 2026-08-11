package com.supernutrition.feature.mealsearch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationId
import com.supernutrition.core.ui.UiAutomationIds

/**
 * The typed meal search surface: a query box and its results.
 *
 * Stateless. It takes a [MealSearchUiState] and hands keystrokes back, so the
 * screen holds nothing the presenter could disagree with. That also makes every
 * state below reachable in a test by construction rather than by driving the
 * search engine to produce it.
 */
@Composable
fun MealSearchScreen(
    state: MealSearchUiState,
    onQueryChanged: (String) -> Unit,
    copy: MealSearchCopy,
    modifier: Modifier = Modifier,
    /**
     * P1-12 — rows were plain Columns with no callback, so the presenter copy and
     * the row detail both described a tap that could not happen: the detail line
     * exists to tell a user what tapping will add, and nothing was listening.
     *
     * The canonical catalog id is what travels, not the row index or the title.
     * An index is a position in a list that reorders as results arrive and a
     * title is display text; the id is what the next catalog-grounding step can
     * actually resolve.
     */
    onRowSelected: (MealSearchRow) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChanged,
            label = { Text(copy.searchLabel) },
            modifier = Modifier
                .fillMaxWidth()
                .automationId(UiAutomationIds.MEAL_SEARCH_QUERY),
        )

        // Three states, not two. An untouched box shows neither the results list
        // nor "no results", because greeting someone who has typed nothing with
        // "no results" is the collapse `showsNoResults` exists to prevent.
        if (state.showsNoResults) {
            Text(
                text = copy.noResults,
                modifier = Modifier.automationId(UiAutomationIds.MEAL_SEARCH_NO_RESULTS),
            )
        } else if (state.rows.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .automationId(UiAutomationIds.MEAL_SEARCH_RESULTS),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(state.rows) { index, row ->
                    MealSearchResultRow(index = index, row = row, copy = copy, onSelected = onRowSelected)
                }
            }
        }
    }
}

@Composable
private fun MealSearchResultRow(
    index: Int,
    row: MealSearchRow,
    copy: MealSearchCopy,
    onSelected: (MealSearchRow) -> Unit,
) {
    Column(
        modifier = Modifier
            .automationId(UiAutomationIds.mealSearchRow(index))
            .fillMaxWidth()
            // clickable rather than a Button: the whole row is the target, which
            // is both the larger tap area and what the detail line implies.
            .clickable { onSelected(row) },
    ) {
        Text(
            text = row.title,
            modifier = Modifier.automationId(UiAutomationIds.mealSearchRowTitle(index)),
        )
        // The detail line is what makes the row honest before the tap: an
        // unmatched preparation on a food, and how many items a dish will add.
        // A food with nothing unmatched has no detail, and drawing an empty one
        // would give every row a blank line rather than a meaningful gap.
        val detail = when (row) {
            is MealSearchRow.Food -> row.note

            is MealSearchRow.Dish -> copy.ingredientCountLabel.replace(
                COUNT_PLACEHOLDER,
                row.ingredientCount.toString(),
            )
        }
        if (detail != null) {
            Text(
                text = detail,
                modifier = Modifier.automationId(UiAutomationIds.mealSearchRowDetail(index)),
            )
        }
    }
}

private const val COUNT_PLACEHOLDER = "{count}"

private fun Modifier.automationId(id: UiAutomationId): Modifier = testTag(id.value)
