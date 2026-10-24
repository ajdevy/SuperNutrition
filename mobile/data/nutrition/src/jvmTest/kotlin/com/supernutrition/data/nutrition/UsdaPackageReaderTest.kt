package com.supernutrition.data.nutrition

import com.supernutrition.domain.nutrition.NutritionCatalogIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UsdaPackageReaderTest {
    private val row = "food:usda-srl-01227\tCheese, dry white, queso seco\t325\t24.51\t24.35\t2.04\t\t0.55\t1808\n"

    @Test
    fun `valid pinned package maps USDA column order without filling missing cells`() {
        val product = readVerifiedUsdaPackage(candidate(row), ::sha256).single()
        assertEquals(2.04, product.nutrientsPer100Grams.carbohydrateGrams)
        assertEquals(24.35, product.nutrientsPer100Grams.fatGrams)
    }

    @Test
    fun `unsupported identity country and declared size are rejected`() {
        val valid = candidate(row)
        reject(valid.copy(snapshot = valid.snapshot.copy(identity = NutritionCatalogIdentity("other", "2018-04"))))
        reject(valid.copy(snapshot = valid.snapshot.copy(catalogCountry = "LV")))
        reject(valid.copy(foodCount = 0))
        reject(valid.copy(foodCount = 10001))
        reject(valid.copy(foodCount = 2))
    }

    @Test
    fun `checksum canonical line endings oversize and duplicate rows are rejected`() {
        reject(candidate(row).copy(payload = row + "tampered"))
        reject(candidate(row.dropLast(1)))
        reject(candidate(row.replace("\n", "\r\n")))
        reject(candidate("x".repeat(2 * 1024 * 1024 + 1)))
        reject(candidate(row + row, count = 2))
    }

    @Test
    fun `malformed columns identities and labels are rejected`() {
        for (invalid in listOf(
            row.replace("\t325", ""), row.replace("food:usda-srl-01227", "food:unknown-01227"),
            row.replace("01227", "0122"), row.replace("01227", "01x27"), row.replace("01227", "01-27"),
            row.replace("01227", "01١27"), row.replace("Cheese, dry white, queso seco", " "),
            row.replace("Cheese, dry white, queso seco", "x".repeat(161)),
            row.replace("Cheese, dry white, queso seco", "Cheese\u0000"),
        )) {
            reject(candidate(invalid))
        }
    }

    @Test
    fun `invalid finite and nonfinite nutrient values fail closed`() {
        for (invalid in listOf("not-a-number", "NaN", "Infinity", "-1")) {
            reject(candidate(row.replace("\t325\t", "\t$invalid\t")))
        }
    }

    private fun candidate(payload: String, count: Int = 1) = UsdaPackagePayload(
        usdaCatalogSnapshot(sha256(payload.encodeToByteArray())),
        payload,
        count,
    )

    private fun reject(candidate: UsdaPackagePayload) {
        assertFailsWith<IllegalArgumentException> { readVerifiedUsdaPackage(candidate, ::sha256) }
    }
}
