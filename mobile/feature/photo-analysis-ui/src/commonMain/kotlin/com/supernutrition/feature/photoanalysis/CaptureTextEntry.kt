package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.refresh_review_pencil
import org.jetbrains.compose.resources.painterResource

/** The typed fallback remains available while camera access is missing or denied. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CaptureTextEntry(onSave: (MealDraft) -> Boolean, copy: MealFormCopy, controlsVisible: Boolean = true) {
    var visible by remember { mutableStateOf(false) }
    var hintVisible by remember { mutableStateOf(true) }
    val open = {
        hintVisible = false
        visible = true
    }
    if (controlsVisible) CaptureTextEntryControls(open, hintVisible, copy)
    if (visible) {
        ModalBottomSheet(
            onDismissRequest = { visible = false },
            modifier = Modifier.automationRoot(),
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = ProfileBackground,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        ) {
            MealForm(
                config = MealFormConfig(title = copy.addTitle),
                actions = MealFormActions(
                    onSave = { draft -> onSave(draft).also { saved -> if (saved) visible = false } },
                    onCancel = { visible = false },
                ),
                copy = copy,
            )
        }
    }
}

@Composable
private fun CaptureTextEntryControls(onOpen: () -> Unit, hintVisible: Boolean, copy: MealFormCopy) {
    Box(Modifier.fillMaxSize().navigationBarsPadding()) {
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 84.dp).size(51.3.dp)
                .clip(CircleShape).background(Color.White.copy(alpha = .2f))
                .clickable(role = Role.Button, onClick = onOpen).automationId(UiAutomationIds.CAPTURE_TEXT)
                .semantics { contentDescription = copy.addTitle },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(Res.drawable.refresh_review_pencil),
                null,
                Modifier.size(26.dp),
                colorFilter = ColorFilter.tint(Color.White),
            )
        }
        if (hintVisible) {
            Text(
                copy.cameraHint,
                color = ProfileBody,
                fontFamily = nunitoFont(),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 148.dp)
                    .widthIn(max = 250.dp).background(Color.White, RoundedCornerShape(22.dp))
                    .clickable(role = Role.Button, onClick = onOpen).padding(12.dp)
                    .automationId(UiAutomationIds.CAPTURE_TEXT_HINT),
            )
        }
    }
}
