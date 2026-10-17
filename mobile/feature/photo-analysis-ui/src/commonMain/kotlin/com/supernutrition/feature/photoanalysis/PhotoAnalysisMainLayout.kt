package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal val LocalMainBackdropProvided = staticCompositionLocalOf { false }
internal val LocalMainNavigationOverlap = staticCompositionLocalOf { 0.dp }

// Existing inset consumers use the same composition local.
internal val LocalMainBottomBarInset = LocalMainNavigationOverlap

/** Applies host insets once and overlays floating navigation on Home and Rewards. */
@Composable
fun PhotoAnalysisMainLayout(
    state: PhotoAnalysisMainBottomBarState,
    actions: PhotoAnalysisMainBottomBarActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        PhotoAnalysisMainBackground(state.selectedTab)
        Box(Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(contentPadding)) {
            val overlaysNavigation = state.selectedTab == 0 || state.selectedTab == 1
            CompositionLocalProvider(
                LocalMainBackdropProvided provides true,
                LocalMainNavigationOverlap provides if (overlaysNavigation) MainBottomBarCanvasHeight else 0.dp,
            ) {
                if (overlaysNavigation) {
                    content()
                    PhotoAnalysisMainBottomBar(
                        state = state,
                        actions = actions,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().weight(1f)) { content() }
                        PhotoAnalysisMainBottomBar(state = state, actions = actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoAnalysisMainBackground(selectedTab: Int) {
    if (selectedTab == 0) {
        HomeScreenBackground()
    } else {
        val background = if (selectedTab == 1) RewardsPaper else MaterialTheme.colorScheme.background
        Box(Modifier.fillMaxSize().background(background))
    }
}
