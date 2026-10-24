package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.UIKit.UIViewController

/** Production and acceptance hosts share the designed main navigation and its actual diary. */
@OptIn(ExperimentalForeignApi::class)
fun mainViewController(
    feature: PhotoAnalysisFeature,
    rewards: RewardsFeature,
    copy: RewardsPresenterCopy,
    onCaptureRequested: () -> Unit,
    onProfileRequested: () -> Unit,
): UIViewController = ComposeUIViewController {
    AppleMainContent(feature, rewards, copy, onCaptureRequested, onProfileRequested)
}

@Composable
private fun AppleMainContent(
    feature: PhotoAnalysisFeature,
    rewards: RewardsFeature,
    copy: RewardsPresenterCopy,
    onCaptureRequested: () -> Unit,
    onProfileRequested: () -> Unit,
) {
    val meals by feature.mealDiary.meals.collectAsState()
    val foods by rewards.confirmedFoods.collectAsState()
    val snapshot by rewards.snapshot.collectAsState()
    val catalogState by feature.mealDiary.catalogSession.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val rewardFlow = rememberRewardsFlowState()
    val clock = rememberAppleMainClock()
    LaunchedEffect(meals, clock.day) { rewards.updateRecordedMeals(meals) }
    val home = feature.mealDiary.homeScreenModel(
        clock.label,
        clock.day,
        foods,
        meals,
        catalogState,
    )
    val rewardsModel = RewardsPresenterModel.from(snapshot, copy)
    val details = RewardsDetailsContext(
        flowState = rewardFlow,
        confirmedFoods = foods,
        referenceDay = clock.day,
        savedMeals = meals,
    )
    if (selectedTab == 1 && rewardFlow.canGoBack) {
        RewardsScreen(
            rewardsModel,
            modifier = Modifier.safeDrawingPadding(),
            details = details,
        )
    } else {
        PhotoAnalysisMainLayout(
            state = PhotoAnalysisMainBottomBarState(
                selectedTab,
                home.title,
                rewardsModel.title,
                home.addFoodLabel,
            ),
            actions = PhotoAnalysisMainBottomBarActions(
                onHomeSelected = { selectedTab = 0 },
                onRewardsSelected = { selectedTab = 1 },
                onCameraRequested = onCaptureRequested,
            ),
            contentPadding = WindowInsets.safeDrawing.asPaddingValues(),
        ) {
            if (selectedTab == 0) {
                HomeScreen(home, onAddFoodRequested = onCaptureRequested, onProfileRequested = onProfileRequested)
            } else {
                RewardsScreen(
                    rewardsModel,
                    details = details,
                    onBackRequested = { selectedTab = 0 },
                )
            }
        }
    }
}

@Composable
private fun rememberAppleMainClock(): AppleMainClock {
    var clock by remember { mutableStateOf(appleMainClock()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            clock = appleMainClock()
            delay(MAIN_CLOCK_REFRESH_MILLIS)
        }
    }
    return clock
}

private data class AppleMainClock(val day: Int, val label: String)

private fun appleMainClock(): AppleMainClock {
    val formatter = NSDateFormatter().apply { dateFormat = "EEEE, MMM d" }
    return AppleMainClock(currentMealRecordContext().localDay, formatter.stringFromDate(NSDate()))
}

private const val MAIN_CLOCK_REFRESH_MILLIS = 60_000L
