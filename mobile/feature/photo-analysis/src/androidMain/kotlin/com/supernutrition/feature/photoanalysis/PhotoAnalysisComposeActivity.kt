package com.supernutrition.feature.photoanalysis

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.supernutrition.domain.photoanalysis.AttemptId
import com.supernutrition.domain.photoanalysis.PhotoLeaseStore
import com.supernutrition.domain.photoanalysis.PhotoSessionStore
import com.supernutrition.platform.photoandroid.AndroidImageNormalizer
import com.supernutrition.platform.photoandroid.AndroidPhotoAttemptIds
import com.supernutrition.platform.photoandroid.AndroidPhotoBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Android composition root: lifecycle, result launchers, and feature wiring only. */
@Suppress("TooManyFunctions")
class PhotoAnalysisComposeActivity : ComponentActivity() {
    private val attemptId = AttemptId(AndroidPhotoAttemptIds.CAPTURE)
    private val retainedState: RetainedState by lazy {
        ViewModelProvider(this)[RetainedState::class.java]
    }
    private val feature: PhotoAnalysisFeature get() = retainedState.feature
    private lateinit var host: PhotoAnalysisAndroidHostController
    private var analysisLauncher: PhotoAnalysisAnalysisLauncher? = null
    private var runtimeBinding: PhotoAnalysisAndroidRuntimeBinding? = null
    private var runtimeReady by mutableStateOf(false)
    private val inlineCameraController = PhotoAnalysisInlineCameraController()
    private var cameraPermissionGranted by mutableStateOf(false)
    private var permissionRationale: android.app.AlertDialog? = null
    private var wasBackgrounded = false
    private var profileId by mutableStateOf<String?>(null)

    private val presentationCopy: PhotoAnalysisPresenterCopy by lazy {
        PhotoAnalysisPresenterCopy(
            capture = CaptureCopy(
                title = getString(R.string.capture_title),
                takeMealPhoto = getString(R.string.capture_shutter),
                choosePhoto = getString(R.string.capture_import),
                openSettings = getString(R.string.capture_open_settings),
                cancel = getString(R.string.capture_cancel),
                researchWarning = getString(R.string.capture_research_warning),
                gridHintsTitle = getString(R.string.capture_grid_hints_title),
                gridOverlayDescription = getString(R.string.capture_grid_overlay_description),
                gridCellDescription = getString(R.string.capture_grid_cell_description),
                selectedState = getString(R.string.capture_selected),
                notSelectedState = getString(R.string.capture_not_selected),
                manualIngredientLabel = getString(R.string.capture_manual_ingredient_label),
                manualIngredientAdd = getString(R.string.capture_manual_ingredient_add),
                resultPlaceholder = getString(R.string.capture_result_placeholder),
                loadingLabel = getString(R.string.capture_loading),
            ),
            status = StatusCopy(
                readyToCapture = getString(R.string.capture_status_ready),
                preparingPhoto = getString(R.string.capture_status_preparing),
                photoReady = getString(R.string.capture_status_ready_photo),
                analyzingPhoto = getString(R.string.capture_status_analyzing),
                findingVisibleFoods = getString(R.string.capture_status_finding_visible_foods),
                identifyingDish = getString(R.string.capture_status_identifying_dish),
                estimatingPortions = getString(R.string.capture_status_estimating_portions),
                checkingResults = getString(R.string.capture_status_checking_results),
                reviewCandidate = getString(R.string.capture_status_review),
                restoringPhotoAnalysis = getString(R.string.capture_status_restoring),
            ),
            evidence = EvidenceCopy(
                fromThisPhoto = getString(R.string.capture_evidence_from_photo),
                highConfidence = getString(R.string.capture_evidence_high_confidence),
                mediumConfidence = getString(R.string.capture_evidence_medium_confidence),
                lowConfidence = getString(R.string.capture_evidence_low_confidence),
                identityClear = getString(R.string.capture_evidence_identity_clear),
                identityAmbiguous = getString(R.string.capture_evidence_identity_ambiguous),
                mixedContent = getString(R.string.capture_evidence_mixed_content),
                fullyVisible = getString(R.string.capture_evidence_fully_visible),
                partiallyOccluded = getString(R.string.capture_evidence_partially_occluded),
                regionAvailable = getString(R.string.capture_evidence_region_available),
                regionUnavailable = getString(R.string.capture_evidence_region_unavailable),
                estimatedMass = getString(R.string.capture_evidence_estimated_mass),
                massNotEvaluable = getString(R.string.capture_evidence_mass_not_evaluable),
                massAbstained = getString(R.string.capture_evidence_mass_abstained),
                localProvenance = getString(R.string.capture_evidence_local_provenance),
                remoteProvenance = getString(R.string.capture_evidence_remote_provenance),
                selected = getString(R.string.capture_evidence_selected),
                notSelected = getString(R.string.capture_evidence_not_selected),
                dishCandidatesTitle = getString(R.string.capture_evidence_dish_candidates),
                visibleComponentsTitle = getString(R.string.capture_evidence_visible_components),
                visibleRegionCount = getString(R.string.capture_evidence_visible_region_count),
                compositionHintsTitle = getString(R.string.capture_evidence_composition_hints),
                compositionHintSource = getString(R.string.capture_evidence_composition_hint_source),
                manualIngredientSource = getString(R.string.capture_evidence_manual_ingredient_source),
                partialDegradation = getString(R.string.capture_evidence_partial_degradation),
            ),
            recovery = RecoveryCopy(
                scanAgain = getString(R.string.capture_scan_again),
                cameraPermissionRequired = getString(R.string.capture_recovery_permission),
                cameraUnavailable = getString(R.string.capture_recovery_unavailable),
                photoCaptureFailed = getString(R.string.capture_recovery_failed),
                photoQualityTooLow = getString(R.string.capture_recovery_quality),
                noFoodFound = getString(R.string.capture_recovery_no_food),
                analysisFailed = getString(R.string.capture_recovery_analysis_failed),
                analysisOffline = getString(R.string.capture_recovery_offline),
                analysisCancelled = getString(R.string.capture_recovery_cancelled),
                reviewRestoreUnavailable = getString(R.string.capture_recovery_review_restore_unavailable),
            ),
        )
    }

    private val rewardsCopy: RewardsPresenterCopy by lazy {
        RewardsPresenterCopy(
            title = getString(R.string.rewards_title),
            earnedLabel = getString(R.string.rewards_earned),
            readyLabel = getString(R.string.rewards_ready),
            progressTemplate = getString(R.string.rewards_progress_template),
            cards = mapOf(
                com.supernutrition.domain.nutrition.RewardId.PROTEIN_ALL_DAY to RewardsCardCopy(
                    title = getString(R.string.reward_protein_title),
                    description = getString(R.string.reward_protein_description),
                ),
                com.supernutrition.domain.nutrition.RewardId.FATS_PRESENT to RewardsCardCopy(
                    title = getString(R.string.reward_fats_title),
                    description = getString(R.string.reward_fats_description),
                ),
                com.supernutrition.domain.nutrition.RewardId.FIBER_PRESENT to RewardsCardCopy(
                    title = getString(R.string.reward_fiber_title),
                    description = getString(R.string.reward_fiber_description),
                ),
                com.supernutrition.domain.nutrition.RewardId.COLORS_OF_DAY to RewardsCardCopy(
                    title = getString(R.string.reward_colors_title),
                    description = getString(R.string.reward_colors_description),
                ),
                com.supernutrition.domain.nutrition.RewardId.THIRTY_PRODUCTS to RewardsCardCopy(
                    title = getString(R.string.reward_products_title),
                    description = getString(R.string.reward_products_description),
                ),
                com.supernutrition.domain.nutrition.RewardId.FERMENTED_THREE_DAYS to RewardsCardCopy(
                    title = getString(R.string.reward_fermented_title),
                    description = getString(R.string.reward_fermented_description),
                ),
                com.supernutrition.domain.nutrition.RewardId.SEVEN_DAYS_RECORDED to RewardsCardCopy(
                    title = getString(R.string.reward_seven_days_title),
                    description = getString(R.string.reward_seven_days_description),
                ),
            ),
        )
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        host.onActivityResult(result.resultCode, result.data, isCamera = false)
    }
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraPermissionGranted = granted
        host.onPermissionResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        restoreActivityState(savedInstanceState)
        cameraPermissionGranted = checkSelfPermission(Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        val photoBridge = AndroidPhotoBridge(
            context = this,
            permissionRequestInFlight = retainedState.captureOperation.permissionRequestInFlight,
        )
        host = createHost(photoBridge).also { it.initialize() }
        inlineCameraController.onCaptureResult = { operationId, result ->
            host.onInlinePhotoResult(operationId, result)
        }
        restoreFeatureSnapshot(savedInstanceState)
        observeRetainedPhotoLifecycle()
        handleIntent(intent)
        renderContent()
        bindAnalysisRuntimeAsync()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        profileId = ProfileDeepLink.profileIdFrom(intent)
    }

    private fun restoreActivityState(savedInstanceState: Bundle?) {
        wasBackgrounded = retainedState.wasBackgrounded
        savedInstanceState?.getString(PENDING_OPERATION_ID_KEY)?.let {
            retainedState.captureOperation.pendingOperationId = it
        }
        // Android does not replay an Activity Result permission callback after
        // process death. Restoring this transient guard would prevent a retry.
        retainedState.captureOperation.permissionRequestInFlight = false
    }

    private fun bindAnalysisRuntimeAsync() {
        val runtimeProvider = application as? PhotoAnalysisAndroidRuntimeProvider
            ?: return
        val factory = runtimeProvider.photoAnalysisRuntimeFactory
        lifecycleScope.launch(runtimeProvider.photoAnalysisBackgroundDispatcher) {
            val binding = runCatching {
                factory.create(
                    context = this@PhotoAnalysisComposeActivity,
                    feature = feature,
                    leases = retainedState.leases,
                    sessions = retainedState.sessions,
                    scope = lifecycleScope,
                )
            }.getOrNull() ?: return@launch
            if (!isActive) {
                binding.close()
                return@launch
            }
            withContext(Dispatchers.Main.immediate) {
                if (isFinishing || isDestroyed) {
                    binding.close()
                } else {
                    runtimeBinding = binding
                    analysisLauncher = binding.analysisLauncher
                    host.setAnalysisLauncher(binding.analysisLauncher)
                    runtimeReady = true
                }
            }
        }
    }

    private fun createHost(photoBridge: AndroidPhotoBridge): PhotoAnalysisAndroidHostController =
        PhotoAnalysisAndroidHostController(
            feature = feature,
            photoBridge = photoBridge,
            normalizer = AndroidImageNormalizer(),
            requestCameraPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            showCameraPermissionRationale = { onContinue ->
                permissionRationale?.dismiss()
                if (!isFinishing && !isDestroyed) {
                    permissionRationale = android.app.AlertDialog.Builder(this)
                        .setMessage(R.string.capture_permission_rationale)
                        .setNegativeButton(R.string.capture_cancel, null)
                        .setPositiveButton(R.string.capture_permission_continue) { _, _ -> onContinue() }
                        .create()
                        .also { it.show() }
                }
            },
            capturePhoto = inlineCameraController::requestCapture,
            launchImport = { importLauncher.launch(photoBridge.photoPickerIntent()) },
            launchSettings = { startActivity(photoBridge.settingsIntent()) },
            captureOperation = retainedState.captureOperation,
            onRetainedPhotoReady = retainedState.previewStore::show,
            onRetainedPhotoInvalidated = retainedState.previewStore::clear,
            analysisLauncher = analysisLauncher,
        )

    private fun restoreFeatureSnapshot(savedInstanceState: Bundle?) {
        PhotoAnalysisSnapshotBundleCodec.decode(savedInstanceState, attemptId)?.let { snapshot ->
            feature.dispatch(com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent.RestoreFromSnapshot(snapshot))
        }
    }

    private fun renderContent() {
        setContent {
            val preview by retainedState.previewStore.preview.collectAsState()
            val manualIngredientState by feature.manualIngredientStateFlow.collectAsState()
            val rewardsSnapshot by retainedState.rewardsFeature.snapshot.collectAsState()
            val openedProfileId = profileId
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { testTagsAsResourceId = true },
            ) {
                if (openedProfileId != null) {
                    ProfileScreen(
                        profileId = openedProfileId,
                        onCloseRequested = { profileId = null },
                    )
                } else {
                    PhotoAnalysisTabs(
                        shell = PhotoAnalysisTabShell(
                            homeModel = HomeScreenModel(
                                title = getString(R.string.home_title),
                                greeting = getString(R.string.home_greeting),
                                dailySummaryTitle = getString(R.string.home_summary_title),
                                dailySummaryValue = getString(R.string.home_summary_value),
                                dailySummaryDescription = getString(R.string.home_summary_description),
                                emptyTitle = getString(R.string.home_empty_title),
                                emptyDescription = getString(R.string.home_empty_description),
                                addFoodLabel = getString(R.string.home_add_food),
                                dateLabel = getString(R.string.home_date_label),
                                careQuestion = getString(R.string.home_care_question),
                                rhythmTitle = getString(R.string.home_rhythm_title),
                                rhythmSubtitle = getString(R.string.home_rhythm_subtitle),
                                rhythmBadgeLabel = getString(R.string.home_rhythm_badge),
                                fiber = HomeMetricModel(
                                    label = getString(R.string.home_fiber_label),
                                    value = getString(R.string.home_fiber_value),
                                ),
                                water = HomeMetricModel(
                                    label = getString(R.string.home_water_label),
                                    value = getString(R.string.home_water_value),
                                ),
                                protein = HomeMetricModel(
                                    label = getString(R.string.home_protein_label),
                                    value = getString(R.string.home_protein_value),
                                ),
                                movement = HomeMetricModel(
                                    label = getString(R.string.home_movement_label),
                                    value = getString(R.string.home_movement_value),
                                ),
                                nextStepTitle = getString(R.string.home_next_step_title),
                                nextStepDescription = getString(R.string.home_next_step_description),
                                nextStepAction = getString(R.string.home_next_step_action),
                                viewGoalsLabel = getString(R.string.home_view_goals),
                                goalsTitle = getString(R.string.home_goals_title),
                                goalsDescription = getString(R.string.home_goals_description),
                                goalsDoneLabel = getString(R.string.home_goals_done),
                                profileActionLabel = getString(R.string.home_profile_action),
                                mascotDescription = getString(R.string.home_mascot_description),
                                saladDescription = getString(R.string.home_salad_description),
                            ),
                            rewardsModel = RewardsPresenterModel.from(rewardsSnapshot, rewardsCopy),
                            homeLabel = getString(R.string.capture_tab),
                            rewardsLabel = getString(R.string.rewards_tab),
                            cameraLabel = getString(R.string.capture_shutter),
                            onProfileRequested = { profileId = "me" },
                        ),
                        captureContent = { onCloseRequested ->
                            PhotoAnalysisCaptureTab(
                                state = PhotoAnalysisCaptureTabState(
                                    feature = feature,
                                    host = host,
                                    presentationCopy = presentationCopy,
                                    closeLabel = getString(R.string.capture_close),
                                    onCloseRequested = onCloseRequested,
                                    preview = preview,
                                    manualIngredientState = manualIngredientState,
                                    cameraPermissionGranted = cameraPermissionGranted,
                                    inlineCameraController = inlineCameraController,
                                    gridHintsEnabled = runtimeReady,
                                ),
                            )
                        },
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putAll(PhotoAnalysisSnapshotBundleCodec.encode(feature.snapshotForPersistence(), attemptId))
        retainedState.captureOperation.pendingOperationId?.let {
            outState.putString(PENDING_OPERATION_ID_KEY, it)
        }
        outState.putBoolean(
            PERMISSION_REQUEST_IN_FLIGHT_KEY,
            retainedState.captureOperation.permissionRequestInFlight,
        )
    }

    override fun onStart() {
        super.onStart()
        if (wasBackgrounded) {
            feature.dispatch(com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent.Restored)
            wasBackgrounded = false
            retainedState.wasBackgrounded = false
        }
    }

    override fun onStop() {
        permissionRationale?.dismiss()
        permissionRationale = null
        feature.dispatch(com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent.Backgrounded)
        wasBackgrounded = true
        retainedState.wasBackgrounded = true
        super.onStop()
    }

    override fun onDestroy() {
        inlineCameraController.clear()
        runtimeBinding?.close()
        runtimeBinding = null
        super.onDestroy()
    }

    private fun observeRetainedPhotoLifecycle() {
        lifecycleScope.launch {
            feature.stateFlow.collect(retainedState.previewStore::retainFor)
        }
    }

    class RetainedState : ViewModel() {
        internal val leases = PhotoLeaseStore()
        internal val sessions = PhotoSessionStore()
        internal val previewStore = AndroidRetainedPhotoPreviewStore()
        val feature = PhotoAnalysisFeature(
            attemptId = AttemptId(AndroidPhotoAttemptIds.CAPTURE),
            leases = leases,
            sessions = sessions,
        )
        val rewardsFeature = RewardsFeature(
            localDay = { java.time.LocalDate.now().toEpochDay().toInt() },
        )
        val captureOperation = CaptureOperationState()
        var wasBackgrounded: Boolean = false

        override fun onCleared() {
            previewStore.clear()
            super.onCleared()
        }
    }

    private companion object {
        const val PENDING_OPERATION_ID_KEY = "photo_analysis.pending_operation_id"
        const val PERMISSION_REQUEST_IN_FLIGHT_KEY = "photo_analysis.permission_request_in_flight"
    }
}

@Composable
private fun ProfileScreen(
    profileId: String,
    onCloseRequested: () -> Unit,
) {
    BackHandler(onBack = onCloseRequested)
    Scaffold { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .semantics { testTagsAsResourceId = true },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.profile_title),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .testTag(com.supernutrition.core.ui.UiAutomationIds.PROFILE_TITLE.value),
                )
                TextButton(
                    onClick = onCloseRequested,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .testTag(com.supernutrition.core.ui.UiAutomationIds.PROFILE_CLOSE.value),
                ) {
                    Text(text = stringResource(R.string.profile_close))
                }
            }
            Text(text = stringResource(R.string.profile_opened_from_link))
            Text(text = stringResource(R.string.profile_id_label))
            Text(
                text = profileId,
                modifier = Modifier.testTag(com.supernutrition.core.ui.UiAutomationIds.PROFILE_ID.value),
            )
        }
    }
}

@Composable
private fun PhotoAnalysisTabs(
    shell: PhotoAnalysisTabShell,
    captureContent: @Composable (onCloseRequested: () -> Unit) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var captureVisible by rememberSaveable { mutableStateOf(false) }
    var captureReturnTab by rememberSaveable { mutableIntStateOf(0) }
    val closeCapture: () -> Unit = {
        captureVisible = false
        selectedTab = captureReturnTab
    }
    BackHandler(enabled = captureVisible, onBack = closeCapture)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true },
    ) {
        when {
            captureVisible -> captureContent(closeCapture)
            selectedTab == 0 -> HomeScreen(
                model = shell.homeModel,
                        onAddFoodRequested = {
                            captureReturnTab = 0
                            captureVisible = true
                        },
                onProfileRequested = shell.onProfileRequested,
            )
            else -> RewardsScreen(shell.rewardsModel, Modifier.statusBarsPadding())
        }
        if (!captureVisible) {
            PhotoAnalysisMainBottomBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                state = PhotoAnalysisMainBottomBarState(
                    selectedTab = selectedTab,
                    homeLabel = shell.homeLabel,
                    rewardsLabel = shell.rewardsLabel,
                    cameraLabel = shell.cameraLabel,
                ),
                actions = PhotoAnalysisMainBottomBarActions(
                    onHomeSelected = {
                        selectedTab = 0
                        captureVisible = false
                    },
                    onRewardsSelected = {
                        selectedTab = 1
                        captureVisible = false
                    },
                    onCameraRequested = {
                        captureReturnTab = selectedTab
                        captureVisible = true
                    },
                ),
            )
        }
    }
}

private data class PhotoAnalysisTabShell(
    val homeModel: HomeScreenModel,
    val rewardsModel: RewardsPresenterModel,
    val homeLabel: String,
    val rewardsLabel: String,
    val cameraLabel: String,
    val onProfileRequested: () -> Unit,
)

@Composable
private fun PhotoAnalysisCaptureTab(state: PhotoAnalysisCaptureTabState) {
    val feature = state.feature
    val host = state.host
    val presentationCopy = state.presentationCopy
    val preview = state.preview
    val manualIngredientState = state.manualIngredientState
    val cameraPermissionGranted = state.cameraPermissionGranted
    val inlineCameraController = state.inlineCameraController
    val gridHintsEnabled = state.gridHintsEnabled
    PhotoAnalysisCaptureContent(
        stateFlow = feature.stateFlow,
        onAction = host::handleAction,
        modifier = Modifier.statusBarsPadding(),
        surface = PhotoAnalysisCaptureSurface(
            presenterCopy = presentationCopy,
            closeLabel = state.closeLabel,
            onCloseRequested = state.onCloseRequested,
            liveCamera = if (preview == null) {
                {
                    PhotoAnalysisLiveCameraPreview(
                        cameraPermissionGranted = cameraPermissionGranted,
                        permissionHint = presentationCopy.recovery.cameraPermissionRequired,
                        unavailableHint = presentationCopy.recovery.cameraUnavailable,
                        cameraController = inlineCameraController,
                    )
                }
            } else {
                null
            },
            retainedPhoto = preview?.let { current -> { AndroidRetainedPhotoBackdrop(current) } },
            photoTextContrast = preview?.let { current ->
                if (current.hasLightBackdrop) {
                    PhotoAnalysisPhotoTextContrast.DARK_TEXT
                } else {
                    PhotoAnalysisPhotoTextContrast.LIGHT_TEXT
                }
            },
            gridHintsEnabled = gridHintsEnabled,
            onGridCellSelected = { index -> host.handleGridCellSelected(index) },
            manualIngredientInput = manualIngredientState.query,
            manualIngredients = manualIngredientState.ingredients.map { ingredient ->
                PhotoAnalysisManualIngredientModel(
                    canonicalItemId = ingredient.canonicalItemId,
                    label = ingredient.displayName,
                    sourceDishName = ingredient.sourceDishName,
                )
            },
            manualIngredientUnresolved = manualIngredientState.unresolvedFragments,
            onManualIngredientInputChanged = feature::updateManualIngredientQuery,
            onManualIngredientAdded = feature::addManualIngredients,
            // Keep camera and captured photo in exactly the same slot.
            retainedPhotoAspectRatio = 4f / 3f,
        ),
    )
}

private data class PhotoAnalysisCaptureTabState(
    val feature: PhotoAnalysisFeature,
    val host: PhotoAnalysisAndroidHostController,
    val presentationCopy: PhotoAnalysisPresenterCopy,
    val closeLabel: String,
    val onCloseRequested: () -> Unit,
    val preview: AndroidRetainedPhotoPreview?,
    val manualIngredientState: PhotoAnalysisManualIngredientState,
    val cameraPermissionGranted: Boolean,
    val inlineCameraController: PhotoAnalysisInlineCameraController,
    val gridHintsEnabled: Boolean,
)
