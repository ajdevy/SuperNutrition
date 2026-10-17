package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.logo_sprout_vector
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

private const val BRAND_NAME = "Super Nutrition"
private const val SPROUT_GLYPH_INDEX = 10

/** The sprout replaces the first i's dot and follows its measured position, including wrapped text. */
@Composable
internal fun HomeBrand(modifier: Modifier) {
    val density = LocalDensity.current
    var sproutGlyph by remember { mutableStateOf<Rect?>(null) }
    val wordmark = remember {
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic)) { append("Super ") }
            withStyle(SpanStyle(fontWeight = FontWeight.Normal, fontStyle = FontStyle.Italic)) {
                // U+0131 retains the i stem while the sprout supplies its dot.
                append("Nutr\u0131tion")
            }
        }
    }
    Box(modifier.semantics { contentDescription = BRAND_NAME }, contentAlignment = AbsoluteAlignment.TopLeft) {
        Text(
            text = wordmark,
            color = Color(0xFF173A00),
            fontFamily = frauncesFont(),
            fontSize = 23.5.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.5056).sp,
            textAlign = TextAlign.Center,
            onTextLayout = { sproutGlyph = it.getBoundingBox(SPROUT_GLYPH_INDEX) },
            modifier = Modifier.fillMaxWidth().semantics { text = AnnotatedString(BRAND_NAME) },
        )
        sproutGlyph?.let { glyph ->
            Image(
                painter = painterResource(Res.drawable.logo_sprout_vector),
                contentDescription = null,
                modifier = Modifier.absoluteOffset {
                    with(density) {
                        // Italic stems lean right of their advance-box center.
                        IntOffset((glyph.center.x - 2.sp.toPx()).roundToInt(), (glyph.top + 7.sp.toPx()).roundToInt())
                    }
                }.size(with(density) { 6.sp.toDp() }, with(density) { 5.sp.toDp() }),
            )
        }
    }
}
