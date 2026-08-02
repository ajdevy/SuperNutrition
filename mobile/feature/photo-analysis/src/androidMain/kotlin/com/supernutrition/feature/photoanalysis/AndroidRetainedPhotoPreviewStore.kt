package com.supernutrition.feature.photoanalysis

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
import com.supernutrition.domain.photoanalysis.PhotoRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android-only, process-ephemeral display copy of a normalized photo. It never
 * stores a URI or raw source bytes and clears when the reducer no longer owns
 * the same [PhotoRef]. All calls come from the Android main host.
 */
internal class AndroidRetainedPhotoPreviewStore {
    private val mutablePreview = MutableStateFlow<AndroidRetainedPhotoPreview?>(null)

    val preview = mutablePreview.asStateFlow()

    fun show(input: NormalizedPhotoInput) {
        val next = decodePreview(input) ?: run {
            clear()
            return
        }
        val previous = mutablePreview.value
        mutablePreview.value = next
        previous?.release()
    }

    fun retainFor(state: PhotoAnalysisState) {
        if (state is PhotoAnalysisState.Backgrounded) return
        if (mutablePreview.value?.ref != state.previewPhotoRef()) clear()
    }

    fun clear() {
        val previous = mutablePreview.value
        mutablePreview.value = null
        previous?.release()
    }

    private fun decodePreview(input: NormalizedPhotoInput): AndroidRetainedPhotoPreview? {
        val bytes = input.bytes
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val bitmap = BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = previewSampleSize(bounds.outWidth, bounds.outHeight)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            ) ?: return null
            AndroidRetainedPhotoPreview(input.ref, bitmap, hasLightBackdrop(bitmap))
        } finally {
            bytes.fill(0)
        }
    }

    private fun previewSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width, height) / sample > MAX_PREVIEW_DIMENSION) sample *= 2
        return sample
    }

    private fun hasLightBackdrop(bitmap: Bitmap): Boolean {
        val horizontalStep = maxOf(1, bitmap.width / LUMINANCE_SAMPLE_AXIS)
        val verticalStep = maxOf(1, bitmap.height / LUMINANCE_SAMPLE_AXIS)
        var luminance = 0.0
        var sampleCount = 0
        for (y in 0 until bitmap.height step verticalStep) {
            for (x in 0 until bitmap.width step horizontalStep) {
                val color = bitmap.getPixel(x, y)
                luminance += 0.2126 * ((color shr 16) and COLOR_MASK) +
                    0.7152 * ((color shr 8) and COLOR_MASK) +
                    0.0722 * (color and COLOR_MASK)
                sampleCount += 1
            }
        }
        return sampleCount > 0 && luminance / sampleCount >= LIGHT_BACKDROP_LUMINANCE
    }

    private companion object {
        const val MAX_PREVIEW_DIMENSION = 1080
        const val LUMINANCE_SAMPLE_AXIS = 16
        const val COLOR_MASK = 0xff
        const val LIGHT_BACKDROP_LUMINANCE = 145.0
    }
}

internal class AndroidRetainedPhotoPreview(val ref: PhotoRef, val bitmap: Bitmap, val hasLightBackdrop: Boolean) {
    fun release() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

private fun PhotoAnalysisState.previewPhotoRef(): PhotoRef? = when (this) {
    PhotoAnalysisState.Capture -> null
    is PhotoAnalysisState.Preparing -> ref
    is PhotoAnalysisState.Ready -> input.ref
    is PhotoAnalysisState.Analyzing -> ref
    is PhotoAnalysisState.CandidateReview -> ref
    is PhotoAnalysisState.Recovery -> ref
    is PhotoAnalysisState.Backgrounded -> null
}
