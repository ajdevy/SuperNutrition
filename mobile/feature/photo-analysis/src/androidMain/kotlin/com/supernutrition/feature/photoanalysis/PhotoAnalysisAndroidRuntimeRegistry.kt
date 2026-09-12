package com.supernutrition.feature.photoanalysis

import android.content.Context
import com.supernutrition.domain.photoanalysis.PhotoLeaseStorePort
import com.supernutrition.domain.photoanalysis.PhotoSessionStorePort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/** Explicit app-to-feature composition seam for the local model-backed app host. */
fun interface PhotoAnalysisAndroidRuntimeFactory {
    fun create(
        context: Context,
        feature: PhotoAnalysisFeature,
        leases: PhotoLeaseStorePort,
        sessions: PhotoSessionStorePort,
        scope: CoroutineScope,
    ): PhotoAnalysisAndroidRuntimeBinding
}

/**
 * Composition-root provider. The host owns one factory for its process instead
 * of installing mutable global runtime state that can leak between tests or
 * activities.
 */
interface PhotoAnalysisAndroidRuntimeProvider {
    val photoAnalysisRuntimeFactory: PhotoAnalysisAndroidRuntimeFactory
    val photoAnalysisBackgroundDispatcher: CoroutineDispatcher
}

fun interface PhotoAnalysisAndroidRuntimeCloseAction {
    fun close()
}

class PhotoAnalysisAndroidRuntimeBinding(
    val analysisLauncher: PhotoAnalysisAnalysisLauncher,
    private val closeAction: PhotoAnalysisAndroidRuntimeCloseAction,
) : AutoCloseable {
    override fun close() {
        analysisLauncher.cancel()
        closeAction.close()
    }
}
