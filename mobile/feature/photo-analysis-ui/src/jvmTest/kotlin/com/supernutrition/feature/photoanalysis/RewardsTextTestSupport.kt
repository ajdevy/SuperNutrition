package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Checks the measured, rendered text rather than the row's accessible description. */
internal fun SemanticsNodeInteraction.assertCompleteRewardsText(expected: String) {
    val layouts = mutableListOf<TextLayoutResult>()
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    val layout = layouts.single()
    val diagnostics = rewardsTextDiagnostics(layout, fetchSemanticsNode().boundsInRoot)
    assertEquals(expected, layout.layoutInput.text.text, "Rewards renders different text")
    assertTrue(layout.lineCount > 0, "Rewards text has no measured lines: $expected")
    assertEquals(
        expected.length,
        layout.getLineEnd(layout.lineCount - 1, visibleEnd = true),
        "Rewards does not display every character: $expected; $diagnostics",
    )
    assertTrue(
        frameContainsNaturalText(layout, fetchSemanticsNode().boundsInRoot),
        "Rewards natural text frame is clipped: $expected; $diagnostics",
    )
    repeat(layout.lineCount) { line ->
        assertFalse(layout.isLineEllipsized(line), "Rewards line $line is ellipsized: $expected; $diagnostics")
        val glyphs = rewardsGlyphBounds(layout, line)
        if (glyphs != null) {
            assertTrue(
                glyphs.top >= -1f && glyphs.bottom <= layout.size.height + 1f,
                "Rewards line $line glyphs are vertically clipped: $expected; $diagnostics",
            )
            assertTrue(
                glyphs.left >= -1f && glyphs.right <= layout.size.width + 1f,
                "Rewards line $line glyphs are horizontally clipped: $expected; $diagnostics",
            )
        }
        assertTrue(
            layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1f,
            "Rewards line $line exceeds its natural frame: $expected; $diagnostics",
        )
    }
}

private fun frameContainsNaturalText(layout: TextLayoutResult, frame: Rect) =
    frame.width + 1f >= layout.size.width && frame.height + 1f >= layout.size.height

private fun rewardsGlyphBounds(layout: TextLayoutResult, line: Int): Rect? {
    val glyphs = (layout.getLineStart(line) until layout.getLineEnd(line, visibleEnd = true))
        .filterNot { layout.layoutInput.text[it].isWhitespace() }.map(layout::getBoundingBox)
    return if (glyphs.isEmpty()) {
        null
    } else {
        Rect(
            glyphs.minOf { it.left },
            glyphs.minOf { it.top },
            glyphs.maxOf { it.right },
            glyphs.maxOf { it.bottom },
        )
    }
}

private fun rewardsTextDiagnostics(layout: TextLayoutResult, frame: Rect) = buildString {
    append("size=${layout.size}; frame=$frame; constraints=${layout.layoutInput.constraints}; ")
    append("density=${layout.layoutInput.density.density}/${layout.layoutInput.density.fontScale}; ")
    append("font=${layout.layoutInput.style.fontSize}; ")
    append("lineHeight=${layout.layoutInput.style.lineHeight}; overflow=${layout.hasVisualOverflow}; ")
    repeat(layout.lineCount) { line ->
        val start = layout.getLineStart(line)
        val end = layout.getLineEnd(line, visibleEnd = true)
        val span = rewardsGlyphBounds(layout, line)
        append(
            "line$line[$start..$end/${layout.getLineEnd(line)}; ellipsis=${layout.isLineEllipsized(line)}; " +
                "left=${layout.getLineLeft(line)}; right=${layout.getLineRight(line)}; " +
                "top=${layout.getLineTop(line)}; bottom=${layout.getLineBottom(line)}; glyphs=$span]; ",
        )
    }
}
