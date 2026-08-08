package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.inference.InferenceAttemptContext
import com.supernutrition.domain.inference.InferenceV2Engine
import com.supernutrition.domain.inference.PhotoInferenceEngine
import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
import com.supernutrition.domain.photoanalysis.RequestId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Host-owned trigger for one request-bound local analysis attempt. */
interface PhotoAnalysisAnalysisLauncher {
    fun start(gridCellIndex: Int? = null)

    fun cancel()

    /** No-op default for hosts that intentionally do not bind an analysis runtime. */
    object None : PhotoAnalysisAnalysisLauncher {
        override fun start(gridCellIndex: Int?) = Unit

        override fun cancel() = Unit
    }
}

data class PhotoAnalysisAnalysisConfiguration(
    val requestPrefix: String,
    val configurationVersion: String,
    val configurationDigest: String,
) {
    init {
        require(requestPrefix.isNotBlank()) { "Analysis request prefix must not be blank" }
        require(configurationVersion.isNotBlank()) { "Analysis configuration version must not be blank" }
        require(configurationDigest.length == 64 && configurationDigest.all { it in '0'..'9' || it in 'a'..'f' }) {
            "Analysis configuration digest must be a lowercase SHA-256 digest"
        }
    }
}

/**
 * Binds the common coordinator to a host lifecycle and injected local engines.
 * The engines remain adapter-owned; this class never selects or activates a
 * model manifest.
 */
@Suppress("LongParameterList")
class CoordinatedPhotoAnalysisAnalysisLauncher(
    private val feature: PhotoAnalysisFeature,
    private val dishEngine: PhotoInferenceEngine,
    private val componentEngine: InferenceV2Engine,
    private val scope: CoroutineScope,
    private val configuration: PhotoAnalysisAnalysisConfiguration,
    private val onStageChanged: (com.supernutrition.domain.photoanalysis.LocalAnalysisStage) -> Unit = {},
    private val onFailure: (Throwable) -> Unit = {},
) : PhotoAnalysisAnalysisLauncher {
    private var requestSequence = 0L
    private var job: Job? = null

    override fun start(gridCellIndex: Int?) {
        require(gridCellIndex == null || gridCellIndex in 0..8) {
            "Grid cell index must be null or between 0 and 8"
        }
        val ready = feature.state as? PhotoAnalysisState.Ready ?: return
        cancel()
        val requestId = RequestId("${configuration.requestPrefix}-${++requestSequence}")
        val attemptContext = InferenceAttemptContext(
            attemptId = ready.input.ref.attemptId,
            requestId = requestId.value,
            configurationVersion = configuration.configurationVersion,
            configurationDigest = configuration.configurationDigest,
            gridCellIndex = gridCellIndex,
        )
        val coordinator = PhotoAnalysisAnalysisCoordinator(
            feature = feature,
            dishEngine = dishEngine,
            componentEngine = componentEngine,
            onStageChanged = onStageChanged,
            onFailure = onFailure,
        )
        job = scope.launch {
            coordinator.run(requestId, attemptContext)
        }
    }

    override fun cancel() {
        job?.cancel()
        job = null
        if (feature.state is PhotoAnalysisState.Analyzing) {
            feature.dispatch(PhotoAnalysisEvent.Cancel)
        }
    }
}

object PhotoAnalysisLauncherFactory {
    @Suppress("LongParameterList")
    fun create(
        feature: PhotoAnalysisFeature,
        dishEngine: PhotoInferenceEngine,
        componentEngine: InferenceV2Engine,
        scope: CoroutineScope,
        configuration: PhotoAnalysisAnalysisConfiguration,
        onFailure: (Throwable) -> Unit = {},
    ): PhotoAnalysisAnalysisLauncher = CoordinatedPhotoAnalysisAnalysisLauncher(
        feature = feature,
        dishEngine = dishEngine,
        componentEngine = componentEngine,
        scope = scope,
        configuration = configuration,
        onFailure = onFailure,
    )
}
