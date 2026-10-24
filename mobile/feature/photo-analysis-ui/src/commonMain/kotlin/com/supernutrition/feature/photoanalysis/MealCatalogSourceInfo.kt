package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.domain.nutrition.CatalogAttribution
import com.supernutrition.domain.nutrition.NutritionCatalogProvenance
import com.supernutrition.domain.nutrition.NutritionCatalogSnapshot

internal fun catalogSnapshotLabel(snapshot: NutritionCatalogSnapshot): String =
    "${snapshot.identity.catalogId} · ${snapshot.identity.version} · ${snapshot.catalogCountry}"

@Composable
internal fun MealCatalogSourceButton(provenance: NutritionCatalogProvenance, context: MealCatalogContext) {
    var visible by remember { mutableStateOf(false) }
    DiaryButton(context.copy.info, UiAutomationIds.MEAL_CATALOG_INFO, { visible = true }, primary = false)
    if (visible) MealCatalogSourceInfo(provenance, context) { visible = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealCatalogSourceInfo(
    provenance: NutritionCatalogProvenance,
    context: MealCatalogContext,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val onLink = context.onOpenLink ?: { url: String -> uriHandler.openUri(url) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.automationRoot(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ProfileBackground,
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(.9f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DiaryButton(context.copy.closeInfo, UiAutomationIds.MEAL_CATALOG_INFO_CLOSE, onDismiss, primary = false)
            DiaryHeading(provenance.sourceName)
            DiaryBody(provenance.coverageDescription)
            CatalogSourceLink(
                CatalogLink(
                    provenance.sourceName,
                    context.copy.source,
                    provenance.sourceUrl,
                    UiAutomationIds.MEAL_CATALOG_SOURCE,
                ),
                onLink,
                context.copy.linkOpenFailed,
            )
            CatalogSourceLink(
                CatalogLink(
                    provenance.licenceName,
                    context.copy.licence,
                    provenance.licenceUrl,
                    UiAutomationIds.MEAL_CATALOG_LICENCE,
                ),
                onLink,
                context.copy.linkOpenFailed,
            )
            provenance.attributions.forEach { CatalogAttributionLinks(it, context.copy, onLink) }
        }
    }
}

@Composable
private fun CatalogAttributionLinks(attribution: CatalogAttribution, copy: MealCatalogCopy, onLink: (String) -> Unit) {
    DiaryHeading(attribution.name)
    DiaryBody(attribution.notice)
    CatalogSourceLink(CatalogLink(attribution.name, copy.source, attribution.sourceUrl), onLink, copy.linkOpenFailed)
    CatalogSourceLink(CatalogLink(attribution.name, copy.licence, attribution.licenceUrl), onLink, copy.linkOpenFailed)
}
