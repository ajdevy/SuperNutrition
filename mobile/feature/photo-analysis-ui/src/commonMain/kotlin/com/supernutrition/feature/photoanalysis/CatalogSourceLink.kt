package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun CatalogSourceLink(link: CatalogLink, onLink: (String) -> Unit, failureMessage: String) {
    var failed by remember(link.url) { mutableStateOf(false) }
    val identity = if (link.id == null) Modifier else Modifier.automationId(link.id)
    Column(Modifier.fillMaxWidth()) {
        TextButton(
            onClick = {
                try {
                    onLink(link.url)
                    failed = false
                } catch (_: IllegalArgumentException) {
                    // Native URL handlers throw when no application can open the link.
                    failed = true
                }
            },
            modifier = Modifier.fillMaxWidth().then(identity)
                .semantics { contentDescription = "${link.name}: ${link.label}" },
        ) { Text("${link.label} · ${link.name}", fontFamily = nunitoFont(), color = ProfileInk) }
        if (failed) {
            DiaryBody(
                failureMessage,
                Modifier.padding(horizontal = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
