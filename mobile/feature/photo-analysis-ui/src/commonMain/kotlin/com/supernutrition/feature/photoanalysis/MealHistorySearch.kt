package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.history_search
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun MealHistorySearch(query: String, onQueryChanged: (String) -> Unit, copy: MealHistoryCopy) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChanged,
        singleLine = true,
        textStyle = TextStyle(
            color = ProfileBody,
            fontFamily = nunitoFont(),
            fontSize = 15.sp,
            lineHeight = 20.sp,
        ),
        cursorBrush = SolidColor(ProfileInk),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .shadow(1.dp, RoundedCornerShape(999.dp), ambientColor = Color(0x0A1C241E), spotColor = Color(0x0A1C241E))
            .background(Color.White, RoundedCornerShape(999.dp))
            .semantics { contentDescription = copy.search }.automationId(UiAutomationIds.MEAL_HISTORY_SEARCH),
        decorationBox = { input ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(painterResource(Res.drawable.history_search), null, Modifier.size(20.dp))
                Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
                    if (query.isEmpty()) {
                        Text(
                            copy.search,
                            color = HistoryMuted,
                            fontFamily = nunitoFont(),
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                        )
                    }
                    input()
                }
                if (query.isNotEmpty()) {
                    TextButton(
                        onClick = { onQueryChanged("") },
                    ) { Text(copy.clearSearch, fontFamily = nunitoFont()) }
                }
            }
        },
    )
}
