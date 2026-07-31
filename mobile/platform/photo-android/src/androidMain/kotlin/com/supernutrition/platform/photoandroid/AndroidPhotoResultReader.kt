package com.supernutrition.platform.photoandroid

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import com.supernutrition.core.config.PhotoNormalizationDefaults
import java.io.ByteArrayOutputStream
import java.io.File

/** Separates one-shot token, selected URI, and legacy thumbnail extraction. */
internal class AndroidPhotoResultReader(private val context: Context) {
    fun read(result: Intent?): ByteArray? = readToken(result) ?: readUri(result) ?: readThumbnail(result)

    private fun readToken(result: Intent?): ByteArray? = result
        ?.getStringExtra(AndroidPhotoCaptureFiles.EXTRA_CAPTURE_TOKEN)
        ?.let(AndroidPhotoCaptureResultStore::take)

    private fun readUri(result: Intent?): ByteArray? {
        val uri = result?.data ?: result?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri ?: return null
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream(INITIAL_CAPACITY)
                val buffer = ByteArray(BUFFER_SIZE)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_RESULT_BYTES) return@use null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } catch (_: Exception) {
            null
        } finally {
            if (AndroidPhotoCaptureFiles.isOwnedUri(context, uri)) {
                uri.path?.let(::File)?.delete()
            }
        }
    }

    private fun readThumbnail(result: Intent?): ByteArray? {
        val extras = result?.extras ?: return null
        val thumbnail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(AndroidPhotoResultKeys.LEGACY_THUMBNAIL, Bitmap::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(AndroidPhotoResultKeys.LEGACY_THUMBNAIL) as? Bitmap
        } ?: return null
        return ByteArrayOutputStream().use { output ->
            if (!thumbnail.compress(Bitmap.CompressFormat.JPEG, PhotoNormalizationDefaults.JPEG_QUALITY, output)) {
                null
            } else if (output.size() > MAX_RESULT_BYTES) {
                null
            } else {
                output.toByteArray()
            }
        }
    }

    private companion object {
        const val BUFFER_SIZE = 16 * 1024
        const val INITIAL_CAPACITY = 256 * 1024
        const val MAX_RESULT_BYTES = 10 * 1024 * 1024
    }
}
