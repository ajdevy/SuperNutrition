package com.supernutrition.inference.localios

import com.supernutrition.domain.inference.AttemptId
import com.supernutrition.domain.inference.InferenceV2Capability
import com.supernutrition.domain.inference.InferenceV2Engine
import com.supernutrition.domain.inference.InferenceV2Request
import com.supernutrition.domain.inference.InferenceV2Result
import com.supernutrition.domain.photoanalysis.PhotoLeaseStorePort
import com.supernutrition.domain.photoanalysis.PhotoSessionStorePort
import com.supernutrition.inference.localshared.LocalComponentInferenceAdapter
import com.supernutrition.inference.localshared.LocalComponentInferenceRuntime

const val LOCAL_IOS_COMPONENT_ENGINE_ID = "local-ios-component"

/** iOS component engine identity wrapper; the shared adapter owns all behavior. */
class LocalIosComponentInferenceEngine(
    photos: PhotoSessionStorePort,
    leases: PhotoLeaseStorePort,
    runtime: LocalComponentInferenceRuntime,
    digest: (ByteArray) -> String,
) : InferenceV2Engine() {
    private val delegate = LocalComponentInferenceAdapter(
        photos = photos,
        leases = leases,
        runtime = runtime,
        digest = digest,
        engineId = LOCAL_IOS_COMPONENT_ENGINE_ID,
    )

    override val contractVersion: String
        get() = delegate.contractVersion

    override suspend fun capability(): InferenceV2Capability = delegate.capability()

    public override suspend fun analyzeUnvalidated(request: InferenceV2Request): InferenceV2Result =
        delegate.analyzeUnvalidated(request)

    suspend fun cancel(attemptId: AttemptId) {
        delegate.cancel(attemptId)
    }
}
