package com.supernutrition.domain.inference

import kotlinx.serialization.json.Json

/**
 * One JSON configuration for every inference release.
 *
 * The discriminator, null handling and unknown-field policy are contract-level
 * decisions that must not differ between releases. The two codecs are separate
 * objects because a caller should have to say which release it is speaking, but
 * they share this instance: two `Json` builders would be two places for those
 * decisions to drift, and a payload that round-trips on one release and not the
 * other is the kind of difference nothing would notice until a migration.
 */
internal val INFERENCE_JSON: Json = Json {
    classDiscriminator = "outcomeType"
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}
