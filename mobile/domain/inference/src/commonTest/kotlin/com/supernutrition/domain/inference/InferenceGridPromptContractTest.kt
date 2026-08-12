package com.supernutrition.domain.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InferenceGridPromptContractTest {
    @Test
    fun `request context carries an explicit valid grid cell`() {
        val context = InferenceAttemptContext(
            attemptId = AttemptId("grid-contract-attempt"),
            requestId = "request-grid",
            configurationVersion = "config-1",
            configurationDigest = "a".repeat(64),
            gridCellIndex = 4,
        )

        assertEquals(4, context.gridCellIndex)
    }

    @Test
    fun `request context rejects a grid cell outside the 3x3 contract`() {
        assertFailsWith<IllegalArgumentException> {
            InferenceAttemptContext(
                attemptId = AttemptId("grid-contract-attempt"),
                requestId = "request-grid-invalid",
                configurationVersion = "config-1",
                configurationDigest = "a".repeat(64),
                gridCellIndex = 9,
            )
        }
    }
}
