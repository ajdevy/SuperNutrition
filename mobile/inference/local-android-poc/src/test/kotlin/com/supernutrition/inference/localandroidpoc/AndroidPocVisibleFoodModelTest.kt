package com.supernutrition.inference.localandroidpoc

import com.supernutrition.domain.inference.ComponentModelActivation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidPocVisibleFoodModelTest {
    @Test
    fun `local visible-food manifest pins the selected model without mass-production placeholders`() {
        val manifest = AndroidPocVisibleFoodModel.manifest

        assertEquals("poc-yolo-food-components-v1", manifest.modelVersion)
        assertEquals("d27ac81640ca470cc7e42edd638b6626ac6b4f4dcce74aabb6e09cd02a13c84f", manifest.artifactDigest)
        assertEquals("supernutrition-visible-food-v1", manifest.taxonomyVersion)
        assertEquals("e8e6ee82fa7ae0de0a3f1fe8b0b219913db81dfd6413ceacbe38c48564f99605", manifest.taxonomyDigest)
        assertEquals(ComponentModelActivation.STAGED, manifest.activation)
        assertNull(manifest.rollbackTargetVersion)
        assertNull(manifest.productApprovalRef)
        assertNull(manifest.engineeringApprovalRef)
        assertNull(manifest.legalApprovalRef)
        assertNull(manifest.corpusRef)
        assertNull(manifest.benchmarkEvidenceRef)
        assertNull(manifest.rollbackEvidenceRef)
    }
}
