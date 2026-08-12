package com.supernutrition.domain.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InferenceContractV3Test {
    private val attemptId = AttemptId("attempt-1")
    private val context = InferenceAttemptContext(
        attemptId = attemptId,
        requestId = "request-1",
        configurationVersion = "config-1",
        configurationDigest = "a".repeat(64),
    )

    @Test
    fun `a photo request reports the photo modality`() {
        val request = InferenceRequestV3.photo(PhotoRef.issue(attemptId, "photo-ref-1"), context)

        assertEquals(InferenceModality.PHOTO, request.modality)
    }

    @Test
    fun `a text request reports the text modality and keeps its input locale`() {
        val request = InferenceRequestV3.text(
            description = "two boiled eggs and rye toast",
            inputLocale = "ru-RU",
            attemptContext = context,
        )

        assertEquals(InferenceModality.TEXT, request.modality)
        // AD-12: the language a description was typed in is its own value, not
        // something derived from the catalog country.
        assertEquals("ru-RU", (request as InferenceRequestV3.Text).inputLocale)
    }

    @Test
    fun `a blank description is rejected rather than sent to an engine`() {
        assertFailsWith<IllegalArgumentException> {
            InferenceRequestV3.text("   ", "en-US", context)
        }
    }

    @Test
    fun `a blank input locale is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            InferenceRequestV3.text("rye toast", "  ", context)
        }
    }

    @Test
    fun `a photo request must belong to the same attempt as its context`() {
        val foreignPhoto = PhotoRef.issue(AttemptId("attempt-other"), "photo-ref-1")

        assertFailsWith<IllegalArgumentException> {
            InferenceRequestV3.photo(foreignPhoto, context)
        }
    }

    @Test
    fun `a text request never renders its description in toString`() {
        // User-authored free text can carry more identifying detail than a food
        // photo. A crash report or log line interpolating the request must not
        // leak what someone ate or where they ate it.
        val request = InferenceRequestV3.text(
            "leftover curry from the place on Baker Street",
            "en-GB",
            context,
        )

        assertTrue("Baker Street" !in request.toString(), "description must not appear in toString")
        assertTrue("redacted" in request.toString().lowercase())
    }

    @Test
    fun `the contract version names the modality-neutral v3 release`() {
        assertEquals("inference-evidence/v3", INFERENCE_CONTRACT_V3)
    }

    @Test
    fun `copy cannot route a photo to an attempt it does not belong to`() {
        // The invariant lived only in the factory, so the generated copy() went
        // around it: a request whose PhotoRef and attempt context named
        // different attempts was constructible from any caller. Moving the check
        // into the type closes that whichever way the instance is made.
        val request = InferenceRequestV3.photo(PhotoRef.issue(attemptId, "photo-ref-1"), context)
        val foreign = PhotoRef.issue(AttemptId("attempt-2"), "photo-ref-2")

        val failure = assertFailsWith<IllegalArgumentException> {
            (request as InferenceRequestV3.Photo).copy(photoRef = foreign)
        }

        assertTrue("same attempt" in failure.message.orEmpty())
    }

    @Test
    fun `a text request enforces its own invariants, not only the factory's`() {
        // Same reasoning from the other side: constructing directly must not be
        // a way to obtain a request the factory would have refused.
        assertFailsWith<IllegalArgumentException> {
            InferenceRequestV3.Text(description = "", inputLocale = "en-US", attemptContext = context)
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceRequestV3.Text(description = "rye toast", inputLocale = " ", attemptContext = context)
        }
    }
}
