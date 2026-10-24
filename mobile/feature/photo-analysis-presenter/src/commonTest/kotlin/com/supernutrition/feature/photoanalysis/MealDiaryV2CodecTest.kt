package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.CatalogMealBinding
import com.supernutrition.domain.nutrition.NutritionCatalogIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MealDiaryV2CodecTest {
    @Test
    fun `version two restores an explicit product document using the existing decode API`() {
        val fields = listOf(
            "one", "20020", "12:34", "Rice, brown", "150.0", "TEXT", "", "0", "1",
            "usda-sr-legacy", "2018-04", "US", "a".repeat(64), "food:usda-srl-20037",
        )
        val encoded = "SNMD2:1:" + fields.joinToString("") { "${it.length}:$it" }
        val restored = assertIs<MealDiaryDecodeResult.Success>(MealDiaryCodec.decode(encoded)).meals.single()
        assertEquals("Rice, brown", restored.name)
        assertEquals(150.0, restored.portionGrams)
        assertEquals(emptyList(), restored.ingredients)
        assertEquals(presenterCatalogMeal().catalogBinding, restored.catalogBinding)
    }

    @Test
    fun `version two roundtrip preserves binding identities Unicode and delimiter-bearing content`() {
        val original = presenterCatalogMeal().copy(
            name = "Блюдо, 🥗 | : café",
            note = "first:\nsecond|,🙂",
            catalogBinding = CatalogMealBinding(
                presenterCatalogProduct().snapshot.copy(identity = NutritionCatalogIdentity("usda:🙂", "old|:version")),
                "food:unavailable:🙂|1",
            ),
        )
        val encoded = MealDiaryCodec.encode(listOf(original, original.copy(id = "plain", catalogBinding = null)))
        assertTrue(encoded.startsWith("SNMD2:2:"))
        assertEquals(listOf(original, original.copy(id = "plain", catalogBinding = null)), success(encoded))
        assertEquals(encoded, MealDiaryCodec.encode(success(encoded)))
    }

    @Test
    fun `version one restores an unbound historical record and upgrades only when explicitly encoded`() {
        val fields = listOf("old", "20020", "12:34", "Rice, white", "", "PHOTO", "Note", "1", "Rice, white")
        val legacy = "SNMD1:1:" + encodedFields(fields)
        val restored = success(legacy).single()
        assertNull(restored.catalogBinding)
        assertNull(restored.portionGrams)
        assertEquals(listOf("Rice, white"), restored.ingredients)
        assertEquals(emptyList(), success("SNMD1:0:"))
        assertEquals(emptyList(), success("SNMD2:0:"))
        assertTrue(MealDiaryCodec.encode(listOf(restored)).startsWith("SNMD2:1:"))
    }

    @Test
    fun `invalid binding tags fields and constructor inputs reject the entire document`() {
        listOf("", "2", "true", "00").forEach { flag ->
            assertFailure(rawRecord(listOf(flag)), MealDiaryCodecError.INVALID_RECORD)
        }
        val binding = bindingFields()
        val invalid = listOf(
            binding.changed(1, ""),
            binding.changed(2, " "),
            binding.changed(3, ""),
            binding.changed(4, "a".repeat(63)),
            binding.changed(4, "A".repeat(64)),
            binding.changed(4, "z".repeat(64)),
            binding.changed(5, " "),
            binding.changed(1, "x".repeat(257)),
            binding.changed(2, "x".repeat(257)),
            binding.changed(3, "x".repeat(33)),
            binding.changed(5, "x".repeat(257)),
        )
        invalid.forEach { fields ->
            assertFailure(rawRecord(fields), MealDiaryCodecError.INVALID_RECORD)
            val goodBody = rawRecord(bindingFields()).substringAfter("SNMD2:1:")
            val badBody = rawRecord(fields, id = "bad").substringAfter("SNMD2:1:")
            assertFailure("SNMD2:2:$goodBody$badBody", MealDiaryCodecError.INVALID_RECORD)
        }
    }

    @Test
    fun `truncated and trailing binding documents fail without partial restoration`() {
        val valid = rawRecord(bindingFields())
        assertFailure(valid.dropLast(1), MealDiaryCodecError.MALFORMED)
        assertFailure(valid + "trailing", MealDiaryCodecError.MALFORMED)
        assertFailure(rawRecord(emptyList()), MealDiaryCodecError.MALFORMED)
        assertFailure(rawRecord(listOf("0", "unexpected")), MealDiaryCodecError.MALFORMED)
        assertFailure("SNMD0:0:", MealDiaryCodecError.UNSUPPORTED_VERSION)
        assertFailure("SNMD3:0:", MealDiaryCodecError.UNSUPPORTED_VERSION)
    }

    @Test
    fun `bound records require a whole product with explicit positive grams`() {
        assertFailure(rawRecord(bindingFields(), portion = ""), MealDiaryCodecError.INVALID_RECORD)
        assertFailure(rawRecord(bindingFields(), ingredients = listOf("Onion")), MealDiaryCodecError.INVALID_RECORD)
        val meal = presenterCatalogMeal()
        val selected = assertNotNull(meal.catalogBinding)
        assertFailsWith<IllegalArgumentException> { MealDiaryCodec.encode(listOf(meal.copy(portionGrams = null))) }
        assertFailsWith<IllegalArgumentException> {
            MealDiaryCodec.encode(listOf(meal.copy(ingredients = listOf("Onion"))))
        }
        val oversized = listOf(
            selected.copy(snapshot = selected.snapshot.copy(identity = NutritionCatalogIdentity("x".repeat(257), "v"))),
            selected.copy(
                snapshot = selected.snapshot.copy(identity = NutritionCatalogIdentity("id", "x".repeat(257))),
            ),
            selected.copy(snapshot = selected.snapshot.copy(catalogCountry = "x".repeat(33))),
            selected.copy(canonicalItemId = "x".repeat(257)),
        )
        oversized.forEach { binding ->
            assertFailsWith<IllegalArgumentException> {
                MealDiaryCodec.encode(listOf(meal.copy(catalogBinding = binding)))
            }
        }
    }

    @Test
    fun `exact bounded historical identifiers roundtrip even when no current source owns them`() {
        val original = presenterCatalogMeal()
        val binding = CatalogMealBinding(
            assertNotNull(original.catalogBinding).snapshot.copy(
                identity = NutritionCatalogIdentity("c".repeat(256), "v".repeat(256)),
                catalogCountry = "u".repeat(32),
            ),
            "i".repeat(256),
        )
        val meal = original.copy(catalogBinding = binding)
        assertEquals(listOf(meal), success(MealDiaryCodec.encode(listOf(meal))))
    }

    private fun bindingFields(): List<String> = listOf(
        "1",
        "usda-sr-legacy",
        "2018-04",
        "US",
        "a".repeat(64),
        "food:usda-srl-20037",
    )

    private fun rawRecord(
        binding: List<String>,
        id: String = "one",
        portion: String = "150.0",
        ingredients: List<String> = emptyList(),
    ): String = "SNMD2:1:" + encodedFields(
        listOf(id, "20020", "12:34", "Rice, brown", portion, "TEXT", "", ingredients.size.toString()) +
            ingredients + binding,
    )

    private fun encodedFields(fields: List<String>): String = fields.joinToString("") { "${it.length}:$it" }

    private fun List<String>.changed(index: Int, value: String): List<String> = mapIndexed { position, field ->
        if (position == index) value else field
    }

    private fun success(encoded: String): List<SavedMeal> =
        assertIs<MealDiaryDecodeResult.Success>(MealDiaryCodec.decode(encoded)).meals

    private fun assertFailure(encoded: String, reason: MealDiaryCodecError) {
        assertEquals(reason, assertIs<MealDiaryDecodeResult.Failure>(MealDiaryCodec.decode(encoded)).reason)
    }
}
