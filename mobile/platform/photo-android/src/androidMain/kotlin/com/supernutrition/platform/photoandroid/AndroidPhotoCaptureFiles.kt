package com.supernutrition.platform.photoandroid

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/** Retains a defensive sweep for legacy/interrupted capture files. */
object AndroidPhotoCaptureFiles {
    const val CAPTURE_FILE_PREFIX = "supernutrition-camera-"
    const val EXTRA_ATTEMPT_ID = "com.supernutrition.platform.photoandroid.ATTEMPT_ID"
    const val EXTRA_CAPTURE_TOKEN = "com.supernutrition.platform.photoandroid.CAPTURE_TOKEN"
    const val EXTRA_CAPTURE_FAILURE = "com.supernutrition.platform.photoandroid.CAPTURE_FAILURE"
    const val EXTRA_CAPTURE_FAILURE_REASON = "com.supernutrition.platform.photoandroid.CAPTURE_FAILURE_REASON"

    fun create(context: Context, attemptId: String): File {
        val safeAttemptId = attemptId
            .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .take(48)
            .ifBlank { "unknown-attempt" }
        return File(
            context.cacheDir,
            "$CAPTURE_FILE_PREFIX$safeAttemptId-${UUID.randomUUID()}.jpg",
        )
    }

    fun isOwned(context: Context, file: File): Boolean = runCatching {
        file.canonicalFile.parentFile == context.cacheDir.canonicalFile &&
            file.name.startsWith(CAPTURE_FILE_PREFIX)
    }.getOrDefault(false)

    fun isOwnedUri(context: Context, uri: Uri): Boolean = uri.scheme == "file" &&
        uri.path?.let { isOwned(context, File(it)) } == true

    fun cleanupStale(context: Context): Int {
        var deleted = 0
        context.cacheDir.listFiles().orEmpty().forEach { file ->
            if (isOwned(context, file) && file.delete()) deleted++
        }
        return deleted
    }
}
