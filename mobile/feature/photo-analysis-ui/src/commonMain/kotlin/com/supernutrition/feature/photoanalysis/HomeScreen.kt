package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.capybara
import com.supernutrition.feature.photoanalysis.resources.home_background
import com.supernutrition.feature.photoanalysis.resources.home_bg_arch
import org.jetbrains.compose.resources.painterResource

// The original 182.8pt mascot overlaps the rhythm panel by 16.2pt.
private const val MASCOT_PANEL_OVERLAP_FRACTION = 16.2f / 182.8f
private const val MASCOT_WIDTH_FRACTION = 0.56f

/** The Home design with naturally measured foreground content and a decorative backdrop. */
@Composable
fun HomeScreen(
    model: HomeScreenModel,
    modifier: Modifier = Modifier,
    onAddFoodRequested: () -> Unit = {},
    onViewGoalsRequested: () -> Unit = {},
    onProfileRequested: () -> Unit = {},
) {
    var goalsVisible by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().automationId(UiAutomationIds.HOME_TITLE)
            .semantics { contentDescription = model.title },
    ) {
        if (!LocalMainBackdropProvided.current) HomeScreenBackground()
        val compact = maxHeight - LocalMainNavigationOverlap.current < 700.dp && LocalDensity.current.fontScale <= 1.2f
        val mascotWidthFraction = if (compact) 0.53f else MASCOT_WIDTH_FRACTION
        val mascotHeight = ((minOf(maxWidth, MainContentMaxWidth) - MainScreenGutter * 2) * mascotWidthFraction)
            .coerceIn(160.dp, 182.dp)
        HomeContent(
            model = model,
            mascotHeight = mascotHeight,
            compact = compact,
            actions = HomeActions(
                onAddFoodRequested = onAddFoodRequested,
                onViewGoalsRequested = {
                    goalsVisible = true
                    onViewGoalsRequested()
                },
                onProfileRequested = onProfileRequested,
            ),
        )
    }
    if (goalsVisible) HomeGoalsDialog(model) { goalsVisible = false }
}

@Composable
fun HomeScreenBackground(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(HomeBackgroundColor)) {
        Image(
            painter = painterResource(Res.drawable.home_bg_arch),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(Res.drawable.home_background),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun HomeContent(model: HomeScreenModel, mascotHeight: Dp, compact: Boolean, actions: HomeActions) {
    val verticalPadding = if (compact) 8.dp else 16.dp
    Box(Modifier.fillMaxSize().automationId(UiAutomationIds.HOME_SCREEN)) {
        Column(
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = MainContentMaxWidth)
                .fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState())
                .padding(
                    start = MainScreenGutter,
                    top = verticalPadding,
                    end = MainScreenGutter,
                    bottom = verticalPadding + LocalMainNavigationOverlap.current,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        ) {
            HomeHeader(model, actions.onProfileRequested)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HomeCarePrompt(model, actions.onAddFoodRequested, actions.onViewGoalsRequested, compact)
                HomeMascotAndRhythm(model, mascotHeight, compact)
            }
            HomeNextStep(model, actions.onAddFoodRequested)
        }
    }
}

@Composable
private fun HomeMascotAndRhythm(model: HomeScreenModel, mascotHeight: Dp, compact: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(-mascotHeight * MASCOT_PANEL_OVERLAP_FRACTION),
    ) {
        Image(
            painter = painterResource(Res.drawable.capybara),
            contentDescription = model.mascotDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(mascotHeight).zIndex(1f),
        )
        HomeRhythmCard(model, compact)
    }
}

@Composable
private fun HomeGoalsDialog(model: HomeScreenModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(model.goalsTitle, fontFamily = frauncesFont()) },
        text = { Text(model.goalsDescription, color = HomeBody, fontFamily = nunitoFont()) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(model.goalsDoneLabel, fontFamily = nunitoFont()) }
        },
    )
}

private data class HomeActions(
    val onAddFoodRequested: () -> Unit,
    val onViewGoalsRequested: () -> Unit,
    val onProfileRequested: () -> Unit,
)
