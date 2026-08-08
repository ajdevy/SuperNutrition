package com.supernutrition.feature.photoanalysis

import android.content.Context
import com.supernutrition.domain.photoanalysis.PhotoLeaseStorePort
import com.supernutrition.domain.photoanalysis.PhotoSessionStorePort
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

object PhotoAnalysisAndroidRuntimeRegistry {
    @Volatile
    private var factory: PhotoAnalysisAndroidRuntimeFactory? = null

    @JvmStatic
    fun install(runtimeFactory: PhotoAnalysisAndroidRuntimeFactory) {
        factory = runtimeFactory
    }

    @JvmStatic
    fun current(): PhotoAnalysisAndroidRuntimeFactory? = factory
}
