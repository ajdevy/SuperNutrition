package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.profile_edit
import org.jetbrains.compose.resources.painterResource

private const val REGIONAL_INDICATOR_FIRST = 0x1F1E6
private const val REGIONAL_INDICATOR_LAST = 0x1F1FF
private const val EMOJI_MODIFIER_FIRST = 0x1F3FB
private const val EMOJI_MODIFIER_LAST = 0x1F3FF
private const val EMOJI_TAG_FIRST = 0xE0020
private const val EMOJI_TAG_LAST = 0xE007F
private const val VARIATION_SELECTOR_FIRST = 0xE0100
private const val VARIATION_SELECTOR_LAST = 0xE01EF
private const val ZERO_WIDTH_JOINER = 0x200D
private const val MAX_BMP_CODE_POINT = 0xFFFF
private const val SUPPLEMENTARY_CODE_POINT_BASE = 0x10000
private const val HIGH_SURROGATE_BASE = 0xD800
private const val LOW_SURROGATE_BASE = 0xDC00
private const val SURROGATE_PAYLOAD_BITS = 10

@Composable
internal fun ProfileIdentity(
    displayName: String,
    copy: ProfileScreenCopy,
    onEditRequested: () -> Unit,
    editor: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(80.dp).shadow(1.dp, CircleShape).background(Color(0xFFE1F3DA), CircleShape)
                .border(1.dp, Color(0xFFD8EED2), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                profileInitial(displayName),
                color = ProfileInk,
                fontFamily = frauncesFont(),
                fontSize = 36.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (editor != null) {
            Box(Modifier.weight(1f)) { editor() }
        } else {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    displayName.trim().ifEmpty { copy.unknownName },
                    color = ProfileInk,
                    fontFamily = frauncesFont(),
                    fontSize = 26.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false).automationId(UiAutomationIds.PROFILE_NAME),
                )
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = copy.editName, onClick = onEditRequested)
                        .semantics { contentDescription = copy.editName }
                        .automationId(UiAutomationIds.PROFILE_EDIT_NAME),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Image(painterResource(Res.drawable.profile_edit), null, Modifier.padding(start = 2.dp).size(20.dp))
                }
            }
        }
    }
}

internal fun profileInitial(displayName: String): String {
    val name = displayName.trim()
    if (name.isEmpty()) return "?"
    return name.take(name.profileInitialEnd()).uppercase()
}

/** Preserves BMP marks, variation selectors and common emoji sequences; not full Unicode segmentation. */
private fun String.profileInitialEnd(): Int {
    var end = profileCodePointEnd(0)
    if (profileCodePointAt(0) in REGIONAL_INDICATOR_FIRST..REGIONAL_INDICATOR_LAST && end < length &&
        profileCodePointAt(end) in REGIONAL_INDICATOR_FIRST..REGIONAL_INDICATOR_LAST
    ) {
        end = profileCodePointEnd(end)
    }
    while (end < length) {
        val codePoint = profileCodePointAt(end)
        val next = profileCodePointEnd(end)
        end = when {
            profileInitialExtension(codePoint) -> next
            codePoint == ZERO_WIDTH_JOINER && next < length -> profileCodePointEnd(next)
            else -> return end
        }
    }
    return end
}

private fun String.profileCodePointEnd(index: Int): Int =
    index + if (this[index].isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) 2 else 1

private fun String.profileCodePointAt(index: Int): Int = if (profileCodePointEnd(index) - index == 2) {
    SUPPLEMENTARY_CODE_POINT_BASE + ((this[index].code - HIGH_SURROGATE_BASE) shl SURROGATE_PAYLOAD_BITS) +
        this[index + 1].code - LOW_SURROGATE_BASE
} else {
    this[index].code
}

private fun profileInitialExtension(codePoint: Int): Boolean = when {
    codePoint in EMOJI_MODIFIER_FIRST..EMOJI_MODIFIER_LAST || codePoint in EMOJI_TAG_FIRST..EMOJI_TAG_LAST ||
        codePoint in VARIATION_SELECTOR_FIRST..VARIATION_SELECTOR_LAST -> true

    codePoint > MAX_BMP_CODE_POINT -> false

    else -> when (codePoint.toChar().category) {
        CharCategory.NON_SPACING_MARK, CharCategory.COMBINING_SPACING_MARK, CharCategory.ENCLOSING_MARK -> true
        else -> false
    }
}
