package com.supernutrition.domain.nutrition

/**
 * At most two fractional decimal places, HALF_UP on the value's canonical decimal representation.
 * This never changes source/calculation values and avoids overflow when a finite Double is very large.
 */
fun nutrientDisplayNumber(value: Double?): String {
    if (value == null || !value.isFinite() || value < 0.0) return "—"
    val decimal = expandedNutrientDecimal(value.toString())
    val parts = decimal.split('.', limit = 2)
    val fraction = parts.getOrElse(1) { "" }.padEnd(3, '0')
    val cents = parts[0] + fraction.take(2)
    val rounded = if (fraction[2] >= '5') incrementDecimalDigits(cents) else cents
    val whole = rounded.dropLast(2).removePrefix("-")
    val remainder = rounded.takeLast(2).trimEnd('0')
    return if (remainder.isEmpty()) whole else "$whole.$remainder"
}

/** JVM and Native may choose different scientific notation; expansion keeps rounding identical. */
internal fun expandedNutrientDecimal(text: String): String {
    val exponentIndex = text.indexOf('E', ignoreCase = true)
    if (exponentIndex < 0) return text
    val mantissa = text.substring(0, exponentIndex)
    val exponent = text.substring(exponentIndex + 1).toInt()
    val pointIndex = mantissa.indexOf('.').let { if (it < 0) mantissa.length else it }
    val digits = mantissa.replace(".", "")
    val point = pointIndex + exponent
    return when {
        point <= 0 -> "0." + "0".repeat(-point) + digits
        point >= digits.length -> digits + "0".repeat(point - digits.length)
        else -> digits.substring(0, point) + "." + digits.substring(point)
    }
}

private fun incrementDecimalDigits(text: String): String {
    val digits = text.toCharArray()
    var position = digits.lastIndex
    while (position >= 0 && digits[position] == '9') {
        digits[position] = '0'
        position--
    }
    return if (position < 0) {
        "1" + digits.concatToString()
    } else {
        digits[position] = digits[position] + 1
        digits.concatToString()
    }
}
