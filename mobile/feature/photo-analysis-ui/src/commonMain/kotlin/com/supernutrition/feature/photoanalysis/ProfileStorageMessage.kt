package com.supernutrition.feature.photoanalysis

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp

@Composable
internal fun ProfileStorageMessage(message: String) {
    Text(
        message,
        color = ProfileBody,
        fontFamily = nunitoFont(),
        fontSize = 14.sp,
        lineHeight = 20.sp,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}
