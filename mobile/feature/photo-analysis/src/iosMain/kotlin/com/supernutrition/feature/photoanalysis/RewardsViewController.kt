package com.supernutrition.feature.photoanalysis

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSHashTable
import platform.UIKit.UIViewController
import platform.UIKit.navigationController

/** Swift-facing factory; its published name and two arguments are retained for compatibility. */
@Suppress("FunctionNaming")
@OptIn(ExperimentalForeignApi::class)
fun RewardsViewController(feature: RewardsFeature, copy: RewardsPresenterCopy): UIViewController {
    // Native weak storage avoids retaining the controller through its own composition callback.
    val hostControllers = NSHashTable.weakObjectsHashTable()
    val controller = ComposeUIViewController {
        val snapshot = feature.snapshot.collectAsState().value
        RewardsScreen(
            model = RewardsPresenterModel.from(snapshot, copy),
            onBackRequested = { (hostControllers.anyObject as? UIViewController)?.closeRewards() },
        )
    }
    hostControllers.addObject(controller)
    return controller
}

@OptIn(ExperimentalForeignApi::class)
private fun UIViewController.closeRewards() {
    val navigation = navigationController
    if (navigation != null) {
        if (navigation.topViewController != this) return
        if (navigation.viewControllers.size > 1) {
            navigation.popViewControllerAnimated(true)
        } else if (navigation.presentingViewController != null) {
            navigation.dismissViewControllerAnimated(true, completion = null)
        }
    } else if (presentingViewController != null) {
        dismissViewControllerAnimated(true, completion = null)
    }
    // A nonpresented root has no previous native screen; the host keeps its current root.
}
