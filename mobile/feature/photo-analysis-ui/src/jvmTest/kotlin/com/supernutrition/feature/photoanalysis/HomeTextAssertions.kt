package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Checks rendered lines rather than mistaking the intrinsic paragraph canvas for clipped glyphs. */
@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.assertReadableHomeText(fontScale: Float): TextLayoutResult {
    val layouts = mutableListOf<TextLayoutResult>()
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    val layout = layouts.single()
    val measurement = layout.homeTextMeasurement()
    if (layout.hasVisualOverflow) println("HOME_TEXT_MEASUREMENT: $measurement")
    // Centered text can retain the available paragraph canvas but report an intrinsic ink-sized frame.
    // Compare actual line spans with the integer frame; the canvas alone does not prove clipping.
    assertTrue(layout.multiParagraph.height <= layout.size.height + 1f, "Home text exceeds its height: $measurement")
    assertFalse(layout.multiParagraph.didExceedMaxLines, "Home text loses lines: $measurement")
    assertEquals(fontScale, layout.layoutInput.density.fontScale, "Home overrides the system text scale")
    repeat(layout.lineCount) { line ->
        assertFalse(layout.isLineEllipsized(line), "Home text is truncated: $measurement")
        val lineWidth = layout.getLineRight(line) - layout.getLineLeft(line)
        assertTrue(lineWidth <= layout.size.width + 1f, "Home text exceeds its line width: $measurement")
        assertTrue(
            layout.getLineBottom(line) <= layout.size.height + 1f,
            "Home text crosses its bottom edge: $measurement",
        )
    }
    assertEquals(
        layout.layoutInput.text.length,
        layout.getLineEnd(layout.lineCount - 1),
        "Home drops part of its text: $measurement",
    )
    return layout
}

private fun TextLayoutResult.homeTextMeasurement(): String {
    val lineMeasurements = (0 until lineCount).joinToString { line ->
        "$line[left=${getLineLeft(line)}, right=${getLineRight(line)}, bottom=${getLineBottom(line)}, " +
            "end=${getLineEnd(line)}, ellipsized=${isLineEllipsized(line)}]"
    }
    return "text='${layoutInput.text}', scale=${layoutInput.density.fontScale}, " +
        "overflowHeight=$didOverflowHeight, overflowWidth=$didOverflowWidth, hasVisualOverflow=$hasVisualOverflow, " +
        "size=$size, paragraphWidth=${multiParagraph.width}, paragraphHeight=${multiParagraph.height}, " +
        "didExceedMaxLines=${multiParagraph.didExceedMaxLines}, lineCount=$lineCount, lines=[$lineMeasurements], " +
        "constraints=${layoutInput.constraints}, font=${layoutInput.style.fontSize}, " +
        "lineHeight=${layoutInput.style.lineHeight}"
}
