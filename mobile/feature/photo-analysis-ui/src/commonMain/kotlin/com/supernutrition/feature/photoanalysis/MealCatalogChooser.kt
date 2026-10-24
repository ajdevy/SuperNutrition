package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.domain.nutrition.CatalogProductSearchResult

@Composable
internal fun MealCatalogChooser(draft: MealDraft, context: MealCatalogContext, onChanged: (MealDraft) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val search = remember { MealCatalogSearchState() }
    val copy = context.copy
    val binding = draft.catalogBinding
    if (binding != null) {
        DiaryCard(Modifier.automationId(UiAutomationIds.MEAL_CATALOG_SELECTED)) {
            DiaryBody(copy.selected, secondary = true)
            DiaryHeading(draft.name)
            DiaryBody(catalogSnapshotLabel(binding.snapshot), secondary = true)
            DiaryBody(copy.gramsRequired, secondary = true)
            DiaryButton(copy.remove, UiAutomationIds.MEAL_CATALOG_REMOVE, {
                onChanged(draft.copy(catalogBinding = null))
            }, primary = false)
        }
    }
    if (open) {
        MealCatalogSearch(search, context) {
            onChanged(draft.withCatalogProduct(it.product))
            open = false
        }
    } else {
        DiaryButton(copy.open, UiAutomationIds.MEAL_CATALOG_OPEN, { open = true }, primary = false)
    }
}

@Composable
private fun MealCatalogSearch(
    search: MealCatalogSearchState,
    context: MealCatalogContext,
    onSelected: (CatalogProductSearchResult) -> Unit,
) {
    val result = remember(context.state, search.query, search.attempt) {
        searchMealCatalog(context.state, search.query)
    }
    DiaryCard {
        DiaryBody(context.copy.description, secondary = true)
        context.state.catalogOrNull?.let { catalog ->
            DiaryHeading(catalog.provenance.sourceName)
            DiaryBody(catalogSnapshotLabel(catalog.snapshot), secondary = true)
            DiaryBody(catalog.provenance.coverageDescription, secondary = true)
            MealCatalogSourceButton(catalog.provenance, context)
        }
        MealFormInput(
            context.copy.query,
            search.query,
            UiAutomationIds.MEAL_CATALOG_QUERY,
            onChanged = search::updateQuery,
        )
        MealCatalogSearchStatus(result, context, search)
        if (result is MealCatalogSearchResult.Matches && result.entries.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().automationId(UiAutomationIds.MEAL_CATALOG_RESULTS),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                result.entries.take(search.visibleCount).forEachIndexed { index, entry ->
                    MealCatalogResultRow(entry, index, context.copy) { onSelected(entry) }
                }
                if (search.visibleCount < result.entries.size) {
                    DiaryButton(context.copy.more, UiAutomationIds.MEAL_CATALOG_MORE, {
                        search.showMore(result.entries.size)
                    }, primary = false)
                }
            }
        }
    }
}

@Composable
private fun MealCatalogSearchStatus(
    result: MealCatalogSearchResult,
    context: MealCatalogContext,
    search: MealCatalogSearchState,
) {
    val message = when (result) {
        MealCatalogSearchResult.Loading -> context.copy.loading
        MealCatalogSearchResult.Unavailable -> context.copy.unavailable
        MealCatalogSearchResult.Prompt -> context.copy.prompt
        is MealCatalogSearchResult.Matches -> if (result.entries.isEmpty()) context.copy.noResults else null
    }
    message?.let { DiaryBody(it, Modifier.automationId(UiAutomationIds.MEAL_CATALOG_STATUS), secondary = true) }
    if (result == MealCatalogSearchResult.Unavailable) {
        DiaryButton(context.copy.retry, UiAutomationIds.MEAL_CATALOG_OPEN, {
            context.onRetryRequested()
            search.retry()
        }, primary = false)
    }
}

@Composable
private fun MealCatalogResultRow(
    entry: CatalogProductSearchResult,
    index: Int,
    copy: MealCatalogCopy,
    onClick: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick)
            .automationId(UiAutomationIds.mealSearchRow(index)).padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DiaryBody(entry.product.displayName, Modifier.automationId(UiAutomationIds.mealSearchRowTitle(index)))
        if (entry.unmatchedPreparation.isNotEmpty()) {
            DiaryBody(
                copy.preparationWarning.replace("{words}", entry.unmatchedPreparation.joinToString(", ")),
                Modifier.automationId(UiAutomationIds.mealSearchRowDetail(index)),
                secondary = true,
            )
        }
    }
}
