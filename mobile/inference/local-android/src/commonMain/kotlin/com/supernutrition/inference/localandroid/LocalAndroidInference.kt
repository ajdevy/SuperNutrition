package com.supernutrition.inference.localandroid

import com.supernutrition.domain.inference.AttemptId
import com.supernutrition.domain.inference.EngineCapability
import com.supernutrition.domain.inference.PhotoInferenceEngine
import com.supernutrition.domain.inference.PhotoInferenceRequest
import com.supernutrition.domain.inference.PhotoInferenceResult
import com.supernutrition.domain.photoanalysis.PhotoLeaseStorePort
import com.supernutrition.domain.photoanalysis.PhotoSessionStorePort
import com.supernutrition.inference.localshared.LocalPhotoInferenceAdapter
import com.supernutrition.inference.localshared.LocalPhotoInferenceRuntime

const val LOCAL_ANDROID_ENGINE_ID = "local-android"

/** Android engine identity wrapper; the shared adapter owns all behavior. */
class LocalAndroidInferenceEngine(
    photos: PhotoSessionStorePort,
    leases: PhotoLeaseStorePort,
    runtime: LocalPhotoInferenceRuntime,
    digest: (ByteArray) -> String,
) : PhotoInferenceEngine {
    private val delegate = LocalPhotoInferenceAdapter(photos, leases, runtime, digest, LOCAL_ANDROID_ENGINE_ID)

    override val contractVersion: String
        get() = delegate.contractVersion

    override suspend fun capability(): EngineCapability = delegate.capability()

    override suspend fun analyze(request: PhotoInferenceRequest): PhotoInferenceResult = delegate.analyze(request)

    suspend fun cancel(attemptId: AttemptId) {
        delegate.cancel(attemptId)
    }
}
