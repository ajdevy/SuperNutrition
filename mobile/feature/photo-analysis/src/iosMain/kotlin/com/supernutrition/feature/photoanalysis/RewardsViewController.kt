package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewController

@Suppress("FunctionNaming")
@OptIn(ExperimentalForeignApi::class)
fun RewardsViewController(feature: RewardsFeature, copy: RewardsPresenterCopy): UIViewController =
    ComposeUIViewController {
        val snapshot = feature.snapshot.collectAsState().value
        RewardsScreen(RewardsPresenterModel.from(snapshot, copy))
    }
