package com.supernutrition.core.config

/** Immutable image-boundary values shared by the Android and iOS normalizers. */
object PhotoNormalizationDefaults {
    const val MAX_DIMENSION = 4096
    const val JPEG_QUALITY = 92
    const val COLOR_SPACE = "sRGB"
    val METADATA_KEYS: Set<String> = emptySet()
}
