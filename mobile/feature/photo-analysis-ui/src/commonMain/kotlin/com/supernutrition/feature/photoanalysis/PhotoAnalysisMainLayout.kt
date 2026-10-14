package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal val LocalMainBackdropProvided = staticCompositionLocalOf { false }
internal val LocalMainBottomBarInset = staticCompositionLocalOf { 0.dp }

/** Reserves Home navigation and floats the Rewards pill while preserving host-owned system insets. */
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
        val insetModifier = Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(contentPadding)
        val floatingBar = state.selectedTab == 1
        CompositionLocalProvider(
            LocalMainBackdropProvided provides true,
            LocalMainBottomBarInset provides if (floatingBar) MainBottomBarHeight else 0.dp,
        ) {
            if (floatingBar) {
                Box(insetModifier) {
                    content()
                    PhotoAnalysisMainBottomBar(state, actions, Modifier.align(Alignment.BottomCenter))
                }
            } else {
                Column(insetModifier) {
                    Box(Modifier.fillMaxWidth().weight(1f)) { content() }
                    PhotoAnalysisMainBottomBar(state = state, actions = actions)
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
        Box(Modifier.fillMaxSize().background(RewardsPaper))
    }
}
