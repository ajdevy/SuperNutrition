package com.supernutrition.feature.photoanalysis

import android.content.Intent

/** Parses the public profile-link contract without trusting arbitrary intents. */
internal object ProfileDeepLink {
    private const val SCHEME = "https"
    private const val HOST = "supernutrition.app"
    private const val PROFILE_SEGMENT = "profile"
    private val PROFILE_ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")

    fun profileIdFrom(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        if (!uri.scheme.equals(SCHEME, ignoreCase = true) ||
            !uri.host.equals(HOST, ignoreCase = true) ||
            uri.port != -1
        ) {
            return null
        }
        val pathSegments = uri.pathSegments
        if (pathSegments.size != 2 || pathSegments[0] != PROFILE_SEGMENT) return null
        return pathSegments[1].takeIf(PROFILE_ID::matches)
    }
}
