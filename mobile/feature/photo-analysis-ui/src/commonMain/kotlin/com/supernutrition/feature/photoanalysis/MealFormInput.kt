package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationId

@Composable
internal fun MealFormInput(
    label: String,
    value: String,
    id: UiAutomationId,
    options: MealFormInputOptions = MealFormInputOptions(),
    onChanged: (String) -> Unit,
) {
    val shape = RoundedCornerShape(if (options.multiline) 20.dp else 28.dp)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontFamily = nunitoFont(), color = ProfileSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        BasicTextField(
            value = value,
            onValueChange = onChanged,
            singleLine = !options.multiline,
            textStyle = TextStyle(
                fontFamily = nunitoFont(),
                color = ProfileBody,
                fontSize = 15.sp,
                lineHeight = 21.sp,
            ),
            cursorBrush = SolidColor(Color(0xFF518841)),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (options.decimal) KeyboardType.Decimal else KeyboardType.Text,
                imeAction = if (options.multiline) ImeAction.Default else ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = if (options.multiline) 84.dp else 48.dp)
                .background(Color.White, shape).border(1.dp, Color(0xFFD8DED4), shape).padding(16.dp)
                .semantics { contentDescription = label }.automationId(id),
        )
    }
}
