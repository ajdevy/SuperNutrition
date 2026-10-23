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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.supernutrition.core.ui.CameraPermissionState
import com.supernutrition.domain.nutrition.ConfirmedConsumedFood
import com.supernutrition.domain.nutrition.RewardId
import com.supernutrition.domain.photoanalysis.AttemptId
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
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
    private var cameraPermissionState by mutableStateOf(CameraPermissionState.NOT_DETERMINED)
    private var cameraPermissionRequestInFlight by mutableStateOf(false)
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
            review = MealReviewCopy(
                title = getString(R.string.capture_review_title),
                subtitle = getString(R.string.capture_review_subtitle),
                customMeal = getString(R.string.capture_review_custom_meal),
                anythingElse = getString(R.string.capture_review_anything_else),
                retake = getString(R.string.capture_review_retake),
                addToMeal = getString(R.string.capture_review_add_to_meal),
                pantryIngredientLabels = pantryIngredientLabels(),
            ),
        )
    }

    private val rewardsCopy: RewardsPresenterCopy by lazy {
        RewardsPresenterCopy(
            title = getString(R.string.rewards_title),
            earnedLabel = getString(R.string.rewards_earned),
            readyLabel = getString(R.string.rewards_ready),
            progressTemplate = getString(R.string.rewards_progress_template),
            levelTitle = getString(R.string.rewards_level_title),
            levelSummary = getString(R.string.rewards_level_summary),
            earnedSectionTitle = getString(R.string.rewards_earned_section),
            earnedSeeAllLabel = getString(R.string.rewards_earned_see_all),
            inProgressSectionTitle = getString(R.string.rewards_in_progress_section),
            backLabel = getString(R.string.rewards_back),
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
            visualCards = listOf(
                RewardsVisualCardCopy(
                    rewardId = RewardId.FIBER_PRESENT,
                    title = getString(R.string.reward_visual_fiber_friend_title),
                    subtitle = getString(R.string.reward_visual_fiber_friend_subtitle),
                ),
                RewardsVisualCardCopy(
                    rewardId = RewardId.PROTEIN_ALL_DAY,
                    title = getString(R.string.reward_visual_protein_pal_title),
                    subtitle = getString(R.string.reward_visual_protein_pal_subtitle),
                ),
                RewardsVisualCardCopy(
                    rewardId = RewardId.SEVEN_DAYS_RECORDED,
                    title = getString(R.string.reward_visual_rhythm_keeper_title),
                    subtitle = getString(R.string.reward_visual_rhythm_keeper_subtitle),
                ),
                RewardsVisualCardCopy(
                    rewardId = RewardId.FATS_PRESENT,
                    title = getString(R.string.reward_visual_good_fats_title),
                    subtitle = getString(R.string.reward_visual_good_fats_subtitle),
                ),
                RewardsVisualCardCopy(
                    rewardId = RewardId.FERMENTED_THREE_DAYS,
                    title = getString(R.string.reward_visual_culture_keeper_title),
                    subtitle = getString(R.string.reward_visual_culture_keeper_subtitle),
                ),
                RewardsVisualCardCopy(
                    rewardId = RewardId.THIRTY_PRODUCTS,
                    title = getString(R.string.reward_visual_veggie_explorer_title),
                    subtitle = getString(R.string.reward_visual_veggie_explorer_subtitle),
                ),
                RewardsVisualCardCopy(
                    rewardId = RewardId.COLORS_OF_DAY,
                    title = getString(R.string.reward_visual_rainbow_plate_title),
                    subtitle = getString(R.string.reward_visual_rainbow_plate_subtitle),
                ),
                RewardsVisualCardCopy(
                    rewardId = RewardId.PROTEIN_ALL_DAY,
                    title = getString(R.string.reward_visual_protein_pro_title),
                    subtitle = getString(R.string.reward_visual_protein_pro_subtitle),
                ),
                RewardsVisualCardCopy(
                    rewardId = RewardId.SEVEN_DAYS_RECORDED,
                    title = getString(R.string.reward_visual_rhythm_master_title),
                    subtitle = getString(R.string.reward_visual_rhythm_master_subtitle),
                ),
                RewardsVisualCardCopy(
                    rewardId = RewardId.FIBER_PRESENT,
                    title = getString(R.string.reward_visual_fiber_legend_title),
                    subtitle = getString(R.string.reward_visual_fiber_legend_subtitle),
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
        host.onPermissionResult(granted)
        cameraPermissionRequestInFlight = false
        cameraPermissionState = host.refreshCameraPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Compose consumes IME insets; system panning would move Close off screen.
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        updateSystemBarAppearance(darkIcons = false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        restoreActivityState(savedInstanceState)
        val photoBridge = AndroidPhotoBridge(
            context = this,
            permissionRequestInFlight = retainedState.captureOperation.permissionRequestInFlight,
        )
        host = createHost(photoBridge).also { it.initialize() }
        cameraPermissionState = host.refreshCameraPermission()
        cameraPermissionRequestInFlight = retainedState.captureOperation.permissionRequestInFlight
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
        // Legacy Bundles may carry a shutter token whose Activity-owned camera
        // controller no longer exists. Never restore that native effect.
        if (savedInstanceState?.getString(PENDING_OPERATION_ID_KEY) != null) {
            retainedState.captureOperation.pendingOperationId = null
        }
        // Keep the live request guard across configuration changes. A new
        // ViewModel starts with a cleared guard after process death; this
        // transient value is deliberately not restored from the Bundle.
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
            requestCameraPermission = {
                cameraPermissionRequestInFlight = true
                permissionLauncher.launch(Manifest.permission.CAMERA)
            },
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

    private fun tabShell(rewardsModel: RewardsPresenterModel, confirmedFoods: List<ConfirmedConsumedFood>) =
        PhotoAnalysisTabShell(
            homeModel = homeScreenModel(),
            rewardsModel = rewardsModel,
            rewardsScreenCopy = rewardsScreenCopy(),
            homeLabel = getString(R.string.capture_tab),
            rewardsLabel = getString(R.string.rewards_tab),
            cameraLabel = getString(R.string.capture_shutter),
            confirmedFoods = confirmedFoods,
            profileName = retainedState.profileDisplayName,
            onProfileNameChanged = { retainedState.profileDisplayName = it },
            onSystemBarAppearanceChanged = ::updateSystemBarAppearance,
        )

    private fun renderContent() {
        setContent {
            val preview by retainedState.previewStore.preview.collectAsState()
            val manualIngredientState by feature.manualIngredientStateFlow.collectAsState()
            val rewardsSnapshot by retainedState.rewardsFeature.snapshot.collectAsState()
            val confirmedFoods by retainedState.rewardsFeature.confirmedFoods.collectAsState()
            val openedProfileId = profileId
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { testTagsAsResourceId = true },
            ) {
                if (openedProfileId != null) {
                    SideEffect { updateSystemBarAppearance(darkIcons = true) }
                    LinkedProfileScreen(
                        profileId = openedProfileId,
                        onCloseRequested = { profileId = null },
                    )
                } else {
                    PhotoAnalysisTabs(
                        shell = tabShell(RewardsPresenterModel.from(rewardsSnapshot, rewardsCopy), confirmedFoods),
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
                                    cameraAccess = PhotoAnalysisCameraAccess(
                                        permissionState = cameraPermissionState,
                                        requestInFlight = cameraPermissionRequestInFlight,
                                        onAccessRequested = {
                                            cameraPermissionState = host.requestCameraPreviewAccess()
                                        },
                                    ),
                                    onCameraPreviewEntered = {
                                        cameraPermissionState = host.onCameraPreviewEntered()
                                    },
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

    private fun homeScreenModel() = HomeScreenModel(
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
        fiber = homeMetricModel(
            R.string.home_fiber_label,
            R.string.home_fiber_value,
            R.integer.home_fiber_amount,
            R.integer.home_fiber_target,
        ),
        water = HomeMetricModel(
            label = getString(R.string.home_water_label),
            value = getString(R.string.home_water_value),
        ),
        protein = homeMetricModel(
            R.string.home_protein_label,
            R.string.home_protein_value,
            R.integer.home_protein_amount,
            R.integer.home_protein_target,
        ),
        movement = HomeMetricModel(
            label = getString(R.string.home_movement_label),
            value = getString(R.string.home_movement_value),
        ),
        colors = HomeMetricModel(
            label = getString(R.string.home_colors_label),
            value = getString(R.string.home_metric_unavailable),
        ),
        fats = HomeMetricModel(
            label = getString(R.string.home_fats_label),
            value = getString(R.string.home_metric_unavailable),
        ),
        nextStepTitle = getString(R.string.home_next_step_title),
        nextStepDescription = getString(R.string.home_next_step_description),
        nextStepAction = getString(R.string.home_next_step_action),
        viewGoalsLabel = getString(R.string.home_view_goals),
        goalsTitle = getString(R.string.home_goals_title),
        goalsDescription = getString(
            R.string.home_goals_description,
            resources.getInteger(R.integer.home_fiber_target),
            resources.getInteger(R.integer.home_protein_target),
        ),
        goalsDoneLabel = getString(R.string.home_goals_done),
        profileActionLabel = getString(R.string.home_profile_action),
        mascotDescription = getString(R.string.home_mascot_description),
        saladDescription = getString(R.string.home_salad_description),
    )

    private fun homeMetricModel(labelRes: Int, valueRes: Int, amountRes: Int, targetRes: Int): HomeMetricModel {
        val amount = resources.getInteger(amountRes)
        val target = resources.getInteger(targetRes)
        return HomeMetricModel(getString(labelRes), getString(valueRes, amount, target), amount.toFloat() / target)
    }

    private fun rewardsScreenCopy() = RewardsScreenCopy(
        daily = getString(R.string.rewards_daily),
        weekly = getString(R.string.rewards_weekly),
        countTemplate = getString(R.string.rewards_count_template),
        todaySummaryTemplate = getString(R.string.rewards_today_summary),
        weekSummaryTemplate = getString(R.string.rewards_week_summary),
    )

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putAll(PhotoAnalysisSnapshotBundleCodec.encode(feature.snapshotForPersistence(), attemptId))
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

    override fun onResume() {
        super.onResume()
        cameraPermissionState = host.refreshCameraPermission()
        cameraPermissionRequestInFlight = retainedState.captureOperation.permissionRequestInFlight
    }

    private fun updateSystemBarAppearance(darkIcons: Boolean) {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = darkIcons
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
        internal var profileDisplayName by mutableStateOf("")
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
private fun LinkedProfileScreen(profileId: String, onCloseRequested: () -> Unit) {
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
private fun HomeProfileDestination(shell: PhotoAnalysisTabShell, onBackRequested: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val profileFlow = rememberProfileFlowState()
    BackHandler(enabled = profileFlow.historyVisible, onBack = profileFlow::showProfile)
    BackHandler(enabled = profileFlow.nameEditing, onBack = profileFlow::cancelNameEdit)
    ProfileFlow(
        state = profileFlow,
        displayName = shell.profileName,
        confirmedFoods = shell.confirmedFoods,
        actions = ProfileScreenActions(
            onNameChanged = shell.onProfileNameChanged,
            onBackRequested = onBackRequested,
            onPrivacyPolicyRequested = { uriHandler.openUri(ProfileLegalLinks.PRIVACY_POLICY) },
            onTermsRequested = { uriHandler.openUri(ProfileLegalLinks.TERMS) },
        ),
        modifier = Modifier.safeDrawingPadding(),
    )
}

@Composable
private fun PhotoAnalysisTabs(
    shell: PhotoAnalysisTabShell,
    captureContent: @Composable (onCloseRequested: () -> Unit) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var captureVisible by rememberSaveable { mutableStateOf(false) }
    var captureReturnTab by rememberSaveable { mutableIntStateOf(0) }
    var profileVisible by rememberSaveable { mutableStateOf(false) }
    val mainState = rememberSaveableStateHolder()
    val closeCapture: () -> Unit = {
        captureVisible = false
        selectedTab = captureReturnTab
    }
    val showCapture: () -> Unit = {
        captureReturnTab = selectedTab
        captureVisible = true
    }
    val darkSystemBarIcons = profileVisible || (!captureVisible && selectedTab != 0)
    SideEffect { shell.onSystemBarAppearanceChanged(darkSystemBarIcons) }
    BackHandler(enabled = profileVisible, onBack = { profileVisible = false })
    BackHandler(enabled = captureVisible, onBack = closeCapture)
    Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
        if (profileVisible) {
            HomeProfileDestination(shell) { profileVisible = false }
        } else if (captureVisible) {
            captureContent(closeCapture)
        } else {
            mainState.SaveableStateProvider("main-tabs") {
                PhotoAnalysisMainLayout(
                    state = PhotoAnalysisMainBottomBarState(
                        selectedTab = selectedTab,
                        homeLabel = shell.homeLabel,
                        rewardsLabel = shell.rewardsLabel,
                        cameraLabel = shell.cameraLabel,
                    ),
                    actions = PhotoAnalysisMainBottomBarActions(
                        onHomeSelected = { selectedTab = 0 },
                        onRewardsSelected = { selectedTab = 1 },
                        onCameraRequested = showCapture,
                    ),
                    contentPadding = WindowInsets.safeDrawing.asPaddingValues(),
                ) {
                    PhotoAnalysisTabContent(
                        shell = shell,
                        selectedTab = selectedTab,
                        onAddFood = showCapture,
                        onRewardsBack = { selectedTab = 0 },
                        onProfileRequested = { profileVisible = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoAnalysisTabContent(
    shell: PhotoAnalysisTabShell,
    selectedTab: Int,
    onAddFood: () -> Unit,
    onRewardsBack: () -> Unit,
    onProfileRequested: () -> Unit,
) {
    if (selectedTab == 0) {
        HomeScreen(
            model = shell.homeModel,
            onAddFoodRequested = onAddFood,
            onProfileRequested = onProfileRequested,
        )
    } else {
        RewardsScreen(shell.rewardsModel, onBackRequested = onRewardsBack, copy = shell.rewardsScreenCopy)
    }
}

private data class PhotoAnalysisTabShell(
    val homeModel: HomeScreenModel,
    val rewardsModel: RewardsPresenterModel,
    val rewardsScreenCopy: RewardsScreenCopy,
    val homeLabel: String,
    val rewardsLabel: String,
    val cameraLabel: String,
    val confirmedFoods: List<ConfirmedConsumedFood>,
    val profileName: String,
    val onProfileNameChanged: (String) -> Unit,
    val onSystemBarAppearanceChanged: (darkIcons: Boolean) -> Unit,
)

@Composable
private fun PhotoAnalysisCaptureTab(state: PhotoAnalysisCaptureTabState) {
    val feature = state.feature
    val mealReviewSelection by feature.mealReviewSelection.collectAsState()
    val workflowState by feature.stateFlow.collectAsState()
    val host = state.host
    val presentationCopy = state.presentationCopy
    val preview = state.preview
    val manualIngredientState = state.manualIngredientState
    val gridHintsEnabled = state.gridHintsEnabled
    val needsLivePreview = workflowState == PhotoAnalysisState.Capture && preview == null
    LaunchedEffect(host, needsLivePreview) {
        if (needsLivePreview) state.onCameraPreviewEntered()
    }
    PhotoAnalysisCaptureContent(
        stateFlow = feature.stateFlow,
        onAction = host::handleAction,
        surface = PhotoAnalysisCaptureSurface(
            presenterCopy = presentationCopy,
            closeLabel = state.closeLabel,
            onCloseRequested = state.onCloseRequested,
            liveCamera = captureLiveCamera(state, workflowState),
            retainedPhoto = preview?.let { current -> { AndroidRetainedPhotoBackdrop(current) } },
            retainedPhotoAspectRatio = preview?.bitmap?.let { bitmap ->
                if (bitmap.width > 0 && bitmap.height > 0) bitmap.width.toFloat() / bitmap.height else null
            },
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
            mealReviewSelection = mealReviewSelection,
            onMealReviewSelectionChanged = { feature.updateMealReviewSelection(it) },
            onMealReviewConfirmed = { confirmation ->
                if (feature.confirmMealReview(confirmation)) state.onCloseRequested()
            },
        ),
    )
}

private fun captureLiveCamera(
    state: PhotoAnalysisCaptureTabState,
    workflowState: PhotoAnalysisState,
): (@Composable () -> Unit)? {
    if (state.preview != null) return null
    return {
        PhotoAnalysisLiveCameraPreview(
            cameraAccess = state.cameraAccess.copy(
                actionVisible = workflowState == PhotoAnalysisState.Capture,
            ),
            permissionHint = state.presentationCopy.recovery.cameraPermissionRequired,
            unavailableHint = state.presentationCopy.recovery.cameraUnavailable,
            cameraController = state.inlineCameraController,
        )
    }
}

private data class PhotoAnalysisCaptureTabState(
    val feature: PhotoAnalysisFeature,
    val host: PhotoAnalysisAndroidHostController,
    val presentationCopy: PhotoAnalysisPresenterCopy,
    val closeLabel: String,
    val onCloseRequested: () -> Unit,
    val preview: AndroidRetainedPhotoPreview?,
    val manualIngredientState: PhotoAnalysisManualIngredientState,
    val cameraAccess: PhotoAnalysisCameraAccess,
    val onCameraPreviewEntered: () -> Unit,
    val inlineCameraController: PhotoAnalysisInlineCameraController,
    val gridHintsEnabled: Boolean,
)
