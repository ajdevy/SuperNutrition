package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.inference.CalibratedConfidence
import com.supernutrition.domain.inference.ComponentAmbiguity
import com.supernutrition.domain.inference.ComponentVisibility
import com.supernutrition.domain.inference.Confidence
import com.supernutrition.domain.inference.ConfidenceBand
import com.supernutrition.domain.inference.GramRange
import com.supernutrition.domain.inference.INFERENCE_EVIDENCE_CONTRACT_V2
import com.supernutrition.domain.inference.InferenceCandidate
import com.supernutrition.domain.inference.InferenceProvenance
import com.supernutrition.domain.inference.InferenceRoute
import com.supernutrition.domain.inference.InferenceV2Provenance
import com.supernutrition.domain.inference.LocalizedCandidateIdentity
import com.supernutrition.domain.inference.MassAbstentionReason
import com.supernutrition.domain.inference.MassCalibrationAssumption
import com.supernutrition.domain.inference.MassEvidence
import com.supernutrition.domain.inference.MassNotEvaluableReason
import com.supernutrition.domain.inference.PHOTO_INFERENCE_CONTRACT_V1
import com.supernutrition.domain.inference.VisibleComponent
import com.supernutrition.domain.photoanalysis.AttemptId
import com.supernutrition.domain.photoanalysis.CandidateReviewDraft
import com.supernutrition.domain.photoanalysis.DishIngredientHint
import com.supernutrition.domain.photoanalysis.LocalAnalysisStage
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
import com.supernutrition.domain.photoanalysis.PhotoRef
import com.supernutrition.domain.photoanalysis.RequestId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PhotoAnalysisPresenterReviewTest {
    private val copy = PhotoAnalysisPresenterCopyFixtures.localized()
    private val ref = PhotoRef.issue(AttemptId("presenter-review-attempt"), "presenter-review-photo")
    private val requestId = RequestId("presenter-review-request")

    @Test
    fun `dish confidence bands survive identical localized confidence labels`() {
        val bands = ConfidenceBand.entries
        val proposals = bands.mapIndexed { index, band ->
            dish("dish-${band.name}", "Dish ${index + 1}", index + 1).copy(
                confidence = Confidence(
                    score = when (band) {
                        ConfidenceBand.LOW -> 0.1
                        ConfidenceBand.MEDIUM -> 0.6
                        ConfidenceBand.HIGH -> 0.9
                    },
                    band = band,
                ),
            )
        }
        val localized = copy.copy(
            evidence = copy.evidence.copy(
                lowConfidence = "Localized confidence",
                mediumConfidence = "Localized confidence",
                highConfidence = "Localized confidence",
            ),
        )
        val model = PhotoAnalysisPresenterModel.from(
            PhotoAnalysisState.CandidateReview(
                ref,
                requestId,
                CandidateReviewDraft(mainDishProposals = proposals, photoEvidence = emptyList()),
            ),
            localized,
        )
        val projected = assertNotNull(model.review).mainDishProposals
        assertEquals(bands, projected.map { it.confidenceBand })
        assertEquals(setOf("Localized confidence"), projected.map { it.confidenceLabel }.toSet())
    }

    @Test
    fun `presenter maps every factual local stage without a time estimate`() {
        val expectedLabels = mapOf(
            LocalAnalysisStage.FINDING_VISIBLE_FOODS to copy.status.findingVisibleFoods,
            LocalAnalysisStage.IDENTIFYING_DISH to copy.status.identifyingDish,
            LocalAnalysisStage.ESTIMATING_PORTIONS to copy.status.estimatingPortions,
            LocalAnalysisStage.CHECKING_RESULTS to copy.status.checkingResults,
        )

        expectedLabels.forEach { (stage, label) ->
            val model = PhotoAnalysisPresenterModel.from(
                PhotoAnalysisState.Analyzing(ref, requestId, stage),
                copy,
            )

            assertEquals(label, model.statusLabel)
            assertEquals(PhotoAnalysisPresenterState.ANALYZING, model.state)
            assertEquals(
                listOf(
                    PhotoAnalysisPresenterAction.ScanAgain,
                    PhotoAnalysisPresenterAction.ImportPhoto,
                    PhotoAnalysisPresenterAction.Cancel,
                ),
                model.actions,
            )
        }
    }

    @Test
    fun `presenter keeps local visible-food evidence separate without ingredient or mass claims`() {
        val model = PhotoAnalysisPresenterModel.from(
            PhotoAnalysisState.CandidateReview(
                ref = ref,
                requestId = requestId,
                draft = CandidateReviewDraft(
                    mainDishProposals = listOf(dish("dish-salmon", "Salmon with rice", 1)),
                    photoEvidence = listOf(component("component-rice", "ingredient:rice", "Rice")),
                    compositionHints = listOf(
                        DishIngredientHint(
                            ingredientId = "ingredient:tomato",
                            label = "Tomato",
                            support = 1.0,
                            sourceDishCandidateIds = listOf("dish-salmon"),
                        ),
                    ),
                ),
            ),
            copy,
        )

        val review = assertNotNull(model.review)
        assertEquals(
            listOf(
                PhotoAnalysisPresenterAction.ScanAgain,
                PhotoAnalysisPresenterAction.ImportPhoto,
                PhotoAnalysisPresenterAction.Cancel,
            ),
            model.actions,
        )
        assertEquals(listOf("Salmon with rice"), review.mainDishProposals.map { it.label })
        assertEquals(listOf("Rice"), review.visibleComponents.map { it.label })
        assertEquals(copy.evidence.fromThisPhoto, review.visibleComponents.single().sourceLabel)
        assertNull(review.visibleComponents.single().mass)
        assertEquals(copy.evidence.localProvenance, review.visibleComponents.single().provenanceLabel)
        assertFalse(review.visibleComponents.single().isSelected)
        assertEquals(copy.evidence.notSelected, review.visibleComponents.single().selectionLabel)
        assertEquals(emptyList(), review.compositionHints)
    }

    @Test
    fun `presenter retains ingredient and mass evidence for remote results`() {
        val model = PhotoAnalysisPresenterModel.from(
            PhotoAnalysisState.CandidateReview(
                ref = ref,
                requestId = requestId,
                draft = CandidateReviewDraft(
                    mainDishProposals = listOf(dish("dish-remote", "Remote dish", 1, InferenceRoute.REMOTE)),
                    photoEvidence = listOf(
                        component(
                            id = "component-remote",
                            candidateId = "ingredient:beans",
                            label = "Beans",
                            evidence = ComponentEvidence(route = InferenceRoute.REMOTE),
                        ),
                    ),
                    compositionHints = listOf(
                        DishIngredientHint(
                            ingredientId = "ingredient:tomato",
                            label = "Tomato",
                            support = 1.0,
                            sourceDishCandidateIds = listOf("dish-remote"),
                        ),
                    ),
                ),
            ),
            copy,
        )

        val review = assertNotNull(model.review)
        assertEquals("Estimated: 120–150 g", assertNotNull(review.visibleComponents.single().mass).label)
        assertEquals(listOf("Tomato"), review.compositionHints.map { it.label })
    }

    @Test
    fun `presenter displays each ingredient label once across evidence lanes`() {
        val model = PhotoAnalysisPresenterModel.from(
            PhotoAnalysisState.CandidateReview(
                ref = ref,
                requestId = requestId,
                draft = CandidateReviewDraft(
                    mainDishProposals = listOf(dish("dish-salad", "Salad", 1, InferenceRoute.REMOTE)),
                    photoEvidence = listOf(
                        component(
                            "component-carrot-1",
                            "ingredient:carrot",
                            "Carrot",
                            ComponentEvidence(route = InferenceRoute.REMOTE),
                        ),
                        component(
                            "component-carrot-2",
                            "coco:carrot",
                            "carrot",
                            ComponentEvidence(route = InferenceRoute.REMOTE),
                        ),
                    ),
                    compositionHints = listOf(
                        DishIngredientHint(
                            ingredientId = "ingredient:carrot",
                            label = "CARROT",
                            support = 1.0,
                            sourceDishCandidateIds = listOf("dish-salad"),
                        ),
                        DishIngredientHint(
                            ingredientId = "ingredient:tomato",
                            label = "Tomato",
                            support = 0.9,
                            sourceDishCandidateIds = listOf("dish-salad"),
                        ),
                    ),
                ),
            ),
            copy,
        )

        val review = assertNotNull(model.review)
        assertEquals(listOf("Carrot", "carrot"), review.visibleComponents.map { it.label })
        assertEquals(listOf("CARROT", "Tomato"), review.compositionHints.map { it.label })
    }

    @Test
    fun `presenter preserves factual evidence variants without inventing a mass`() {
        val review = factualVariantReview()

        assertEquals(copy.evidence.remoteProvenance, review.mainDishProposals.single().provenanceLabel)
        assertEquals(
            listOf(
                PhotoAnalysisMassStatus.ESTIMATED,
                PhotoAnalysisMassStatus.NOT_EVALUABLE,
                PhotoAnalysisMassStatus.ABSTAINED,
            ),
            review.visibleComponents.map { assertNotNull(it.mass).status },
        )
        assertEquals("Estimated: 20.5–30.25 g", assertNotNull(review.visibleComponents[0].mass).label)
        assertEquals(copy.evidence.identityAmbiguous, review.visibleComponents[0].ambiguityLabel)
        assertEquals(copy.evidence.partiallyOccluded, review.visibleComponents[0].visibilityLabel)
        assertEquals(copy.evidence.regionUnavailable, review.visibleComponents[0].regionLabel)
        assertEquals(copy.evidence.mixedContent, review.visibleComponents[1].ambiguityLabel)
        assertEquals(copy.evidence.massNotEvaluable, assertNotNull(review.visibleComponents[1].mass).label)
        assertEquals(copy.evidence.massAbstained, assertNotNull(review.visibleComponents[2].mass).label)
    }

    private fun factualVariantReview(): PhotoAnalysisReviewModel = assertNotNull(
        PhotoAnalysisPresenterModel.from(
            PhotoAnalysisState.CandidateReview(
                ref = ref,
                requestId = requestId,
                draft = CandidateReviewDraft(
                    mainDishProposals = listOf(dish("dish-remote", "Dish", 1, InferenceRoute.REMOTE)),
                    photoEvidence = listOf(
                        component(
                            id = "component-estimated",
                            candidateId = "ingredient:beans",
                            label = "Beans",
                            evidence = ComponentEvidence(
                                confidence = CalibratedConfidence(0.6, ConfidenceBand.MEDIUM),
                                visibility = ComponentVisibility.PARTIALLY_OCCLUDED,
                                ambiguity = ComponentAmbiguity.IDENTITY_AMBIGUOUS,
                                mass = MassEvidence.Estimated(
                                    GramRange(20.5, 30.25),
                                    listOf(MassCalibrationAssumption.PARTIAL_OCCLUSION),
                                ),
                                route = InferenceRoute.REMOTE,
                            ),
                        ),
                        component(
                            id = "component-not-evaluable",
                            candidateId = "ingredient:sauce",
                            label = "Sauce",
                            evidence = ComponentEvidence(
                                confidence = CalibratedConfidence(0.3, ConfidenceBand.LOW),
                                ambiguity = ComponentAmbiguity.MIXED_CONTENT,
                                mass = MassEvidence.NotEvaluable(
                                    MassNotEvaluableReason.MIXED_OR_OCCLUDED,
                                    "mass-not-evaluable",
                                ),
                                route = InferenceRoute.REMOTE,
                            ),
                        ),
                        component(
                            id = "component-abstained",
                            candidateId = "ingredient:herbs",
                            label = "Herbs",
                            evidence = ComponentEvidence(
                                mass = MassEvidence.Abstained(
                                    MassAbstentionReason.IDENTITY_AMBIGUOUS,
                                    "mass-abstained",
                                ),
                                route = InferenceRoute.REMOTE,
                            ),
                        ),
                    ),
                ),
            ),
            copy,
        ).review,
    )

    @Test
    fun `evidence copy requires an estimated-mass template`() {
        assertFailsWith<IllegalArgumentException> {
            copy.copy(evidence = copy.evidence.copy(estimatedMass = "Estimated mass"))
        }
    }

    private fun dish(
        id: String,
        label: String,
        rank: Int,
        route: InferenceRoute = InferenceRoute.LOCAL,
    ): InferenceCandidate = InferenceCandidate(
        candidateId = id,
        label = label,
        rank = rank,
        confidence = Confidence(0.9, ConfidenceBand.HIGH),
        unresolvedAssumptions = emptyList(),
        provenance = InferenceProvenance(
            route = route,
            engineId = "fixture-engine",
            modelVersion = "fixture-model",
            calibrationVersion = "fixture-calibration",
            contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
        ),
    )

    private data class ComponentEvidence(
        val confidence: CalibratedConfidence = CalibratedConfidence(0.9, ConfidenceBand.HIGH),
        val visibility: ComponentVisibility = ComponentVisibility.VISIBLE,
        val ambiguity: ComponentAmbiguity = ComponentAmbiguity.NONE,
        val mass: MassEvidence = MassEvidence.Estimated(
            range = GramRange(120.0, 150.0),
            calibrationAssumptions = listOf(MassCalibrationAssumption.SINGLE_VIEW_APPROXIMATION),
        ),
        val route: InferenceRoute = InferenceRoute.LOCAL,
    )

    private fun component(
        id: String,
        candidateId: String,
        label: String,
        evidence: ComponentEvidence = ComponentEvidence(),
    ): VisibleComponent = VisibleComponent(
        componentId = id,
        candidate = LocalizedCandidateIdentity(candidateId, label, "en"),
        confidence = evidence.confidence,
        visibility = evidence.visibility,
        ambiguity = evidence.ambiguity,
        mass = evidence.mass,
        provenance = InferenceV2Provenance(
            route = evidence.route,
            engineId = "fixture-engine",
            modelVersion = "fixture-model",
            taxonomyVersion = "fixture-taxonomy",
            calibrationVersion = "fixture-calibration",
            contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
        ),
    )
}
