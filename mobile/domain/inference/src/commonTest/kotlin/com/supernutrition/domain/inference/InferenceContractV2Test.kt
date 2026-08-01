package com.supernutrition.domain.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InferenceContractV2Test {
    private val attemptId = AttemptId("attempt-1")
    private val context = InferenceAttemptContext(
        attemptId = attemptId,
        requestId = "request-1",
        configurationVersion = "config-1",
        configurationDigest = "a".repeat(64),
    )

    @Test
    fun `a photo request reports the photo modality`() {
        val request = InferenceRequestV2.photo(PhotoRef.issue(attemptId, "photo-ref-1"), context)

        assertEquals(InferenceModality.PHOTO, request.modality)
    }

    @Test
    fun `a text request reports the text modality and keeps its input locale`() {
        val request = InferenceRequestV2.text(
            description = "two boiled eggs and rye toast",
            inputLocale = "ru-RU",
            attemptContext = context,
        )

        assertEquals(InferenceModality.TEXT, request.modality)
        // AD-12: the language a description was typed in is its own value, not
        // something derived from the catalog country.
        assertEquals("ru-RU", (request as InferenceRequestV2.Text).inputLocale)
    }

    @Test
    fun `a blank description is rejected rather than sent to an engine`() {
        assertFailsWith<IllegalArgumentException> {
            InferenceRequestV2.text("   ", "en-US", context)
        }
    }

    @Test
    fun `a blank input locale is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            InferenceRequestV2.text("rye toast", "  ", context)
        }
    }

    @Test
    fun `a photo request must belong to the same attempt as its context`() {
        val foreignPhoto = PhotoRef.issue(AttemptId("attempt-other"), "photo-ref-1")

        assertFailsWith<IllegalArgumentException> {
            InferenceRequestV2.photo(foreignPhoto, context)
        }
    }

    @Test
    fun `a text request never renders its description in toString`() {
        // User-authored free text can carry more identifying detail than a food
        // photo. A crash report or log line interpolating the request must not
        // leak what someone ate or where they ate it.
        val request = InferenceRequestV2.text(
            "leftover curry from the place on Baker Street",
            "en-GB",
            context,
        )

        assertTrue("Baker Street" !in request.toString(), "description must not appear in toString")
        assertTrue("redacted" in request.toString().lowercase())
    }

    @Test
    fun `the contract version names the modality-neutral v2 release`() {
        assertEquals("inference-engine/v2", INFERENCE_CONTRACT_V2)
    }
}
