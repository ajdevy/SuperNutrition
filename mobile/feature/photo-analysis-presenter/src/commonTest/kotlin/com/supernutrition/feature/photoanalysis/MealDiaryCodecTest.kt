package com.supernutrition.feature.photoanalysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class MealDiaryCodecTest {
    @Test
    fun `roundtrip keeps Unicode delimiters multiline values source and optional portion`() {
        val meals = listOf(
            meal().copy(
                id = "review:🌱|1",
                name = "Борщ: 🥣 | 1,2",
                ingredients = listOf("свёкла|: ,", "café\n豆腐"),
                note = "two:\nlines|comma,🙂",
            ),
            meal().copy(id = "text2", source = SavedMealSource.TEXT, portionGrams = null, ingredients = emptyList()),
        )
        val serializedBeforeRestart = MealDiaryCodec.encode(meals)
        assertEquals(meals, success(serializedBeforeRestart))
        assertEquals(serializedBeforeRestart, MealDiaryCodec.encode(success(serializedBeforeRestart)))
        assertEquals(emptyList(), success(MealDiaryCodec.encode(emptyList())))
    }

    @Test
    fun `rejects unsupported versions and malformed headers`() {
        assertFailure("SNMD3:0:", MealDiaryCodecError.UNSUPPORTED_VERSION)
        listOf("", "foo", "SNMD", "SNMD1:", "SNMD1:-1:", "SNMD1:1.0:", "SNMD1:01:", "SNMD1:999999999999:")
            .forEach { assertFailure(it, MealDiaryCodecError.MALFORMED) }
    }

    @Test
    fun `rejects truncated trailing or invalid length payloads without partial meals`() {
        val encoded = MealDiaryCodec.encode(listOf(meal(), meal().copy(id = "two")))
        assertFailure(encoded.dropLast(1), MealDiaryCodecError.MALFORMED)
        assertFailure(encoded + "trailing", MealDiaryCodecError.MALFORMED)
        assertFailure("SNMD1:1:-1:x", MealDiaryCodecError.MALFORMED)
        assertFailure("SNMD1:1:999999999999:x", MealDiaryCodecError.MALFORMED)
        assertFailure("SNMD1:1:01:x", MealDiaryCodecError.MALFORMED)
    }

    @Test
    fun `rejects invalid typed record fields source and nonfinite portions`() {
        listOf("NaN", "Infinity", "-Infinity", "0.0", "-3.0", "oops").forEach { portion ->
            assertFailure(rawRecord(RawMealRecord(portion = portion)), MealDiaryCodecError.INVALID_RECORD)
        }
        assertFailure(rawRecord(RawMealRecord(id = "")), MealDiaryCodecError.INVALID_RECORD)
        assertFailure(rawRecord(RawMealRecord(name = " ")), MealDiaryCodecError.INVALID_RECORD)
        assertFailure(rawRecord(RawMealRecord(day = "-1")), MealDiaryCodecError.INVALID_RECORD)
        assertFailure(rawRecord(RawMealRecord(day = "2147483648")), MealDiaryCodecError.INVALID_RECORD)
        assertFailure(rawRecord(RawMealRecord(time = "")), MealDiaryCodecError.INVALID_RECORD)
        assertFailure(rawRecord(RawMealRecord(source = "UNKNOWN")), MealDiaryCodecError.INVALID_RECORD)
        assertFailure(rawRecord(RawMealRecord(ingredient = "")), MealDiaryCodecError.INVALID_RECORD)
    }

    @Test
    fun `rejects duplicate IDs and bounded inventories`() {
        val body = rawRecord().substringAfter("SNMD1:1:")
        assertFailure("SNMD1:2:$body$body", MealDiaryCodecError.DUPLICATE_ID)
        assertFailure("SNMD1:${MealDiaryLimits.MEALS_MAX_COUNT + 1}:", MealDiaryCodecError.TOO_LARGE)
        assertFailure("x".repeat(MealDiaryLimits.ENCODED_MAX_LENGTH + 1), MealDiaryCodecError.TOO_LARGE)
        assertFailsWith<IllegalArgumentException> { MealDiaryCodec.encode(listOf(meal(), meal())) }
        assertFailsWith<IllegalArgumentException> {
            MealDiaryCodec.encode(
                listOf(meal().copy(portionGrams = Double.NaN)),
            )
        }
        assertFailsWith<IllegalArgumentException> { MealDiaryCodec.encode(listOf(meal().copy(name = ""))) }
    }

    @Test
    fun `encoding rejects each invalid stored field without truncation or partial replacement`() {
        val invalid = listOf(
            meal().copy(id = " "),
            meal().copy(id = "i".repeat(MealDiaryLimits.ID_MAX_LENGTH + 1)),
            meal().copy(localDay = -1),
            meal().copy(timeLabel = " "),
            meal().copy(timeLabel = "t".repeat(MealDiaryLimits.TIME_MAX_LENGTH + 1)),
            meal().copy(name = "n".repeat(MealDraftLimits.NAME_MAX_LENGTH + 1)),
            meal().copy(portionGrams = 0.0),
            meal().copy(portionGrams = Double.POSITIVE_INFINITY),
            meal().copy(note = "n".repeat(MealDraftLimits.NOTE_MAX_LENGTH + 1)),
            meal().copy(ingredients = listOf(" ")),
            meal().copy(ingredients = listOf("i".repeat(MealDraftLimits.INGREDIENT_MAX_LENGTH + 1))),
            meal().copy(ingredients = List(MealDraftLimits.INGREDIENTS_MAX_COUNT + 1) { "pea" }),
            meal().copy(
                ingredients = List(MealDraftLimits.INGREDIENTS_MAX_COUNT) {
                    "i".repeat(MealDraftLimits.INGREDIENT_MAX_LENGTH)
                },
            ),
        )
        invalid.forEach { record ->
            assertFailsWith<IllegalArgumentException> { MealDiaryCodec.encode(listOf(record)) }
        }
    }

    @Test
    fun `decoded ingredient count is a bounded integer and truncated fields never become records`() {
        listOf("oops", "-1", "51").forEach { count ->
            val fields = listOf("one", "20020", "12:34", "Soup", "150.0", "PHOTO", "", count, "Lentils")
            val encoded = "SNMD1:1:" + fields.joinToString("") { "${it.length}:$it" }
            assertFailure(encoded, MealDiaryCodecError.INVALID_RECORD)
        }
        assertFailure("SNMD1:1:1:", MealDiaryCodecError.MALFORMED)
        assertFailure("SNMD1:1:0:", MealDiaryCodecError.MALFORMED)
    }

    @Test
    fun `encoding enforces both document inventory and serialized size limits`() {
        assertFailsWith<IllegalArgumentException> {
            MealDiaryCodec.encode(List(MealDiaryLimits.MEALS_MAX_COUNT + 1) { meal() })
        }
        val large = meal().copy(
            ingredients = maximumIngredientContent(),
            note = "n".repeat(MealDraftLimits.NOTE_MAX_LENGTH),
        )
        assertFailsWith<IllegalArgumentException> {
            MealDiaryCodec.encode(List(600) { index -> large.copy(id = "large-$index") })
        }
    }

    @Test
    fun `exact field and content limits roundtrip with first and final integer dates`() {
        val boundary = meal().copy(
            id = "i".repeat(MealDiaryLimits.ID_MAX_LENGTH),
            localDay = 0,
            timeLabel = "t".repeat(MealDiaryLimits.TIME_MAX_LENGTH),
            name = "n".repeat(MealDraftLimits.NAME_MAX_LENGTH),
            portionGrams = Double.MIN_VALUE,
            ingredients = maximumIngredientContent(),
            note = "n".repeat(MealDraftLimits.NOTE_MAX_LENGTH),
        )
        val records = listOf(boundary, boundary.copy(id = "last", localDay = Int.MAX_VALUE))
        assertEquals(records, success(MealDiaryCodec.encode(records)))
    }

    private fun maximumIngredientContent(): List<String> =
        List(MealDraftLimits.INGREDIENTS_MAX_COUNT - 1) { "i".repeat(MealDraftLimits.INGREDIENT_MAX_LENGTH) } +
            "i".repeat(
                MealDraftLimits.INGREDIENTS_MAX_LENGTH -
                    (MealDraftLimits.INGREDIENTS_MAX_COUNT - 1) * (MealDraftLimits.INGREDIENT_MAX_LENGTH + 1),
            )

    private fun success(encoded: String): List<SavedMeal> =
        assertIs<MealDiaryDecodeResult.Success>(MealDiaryCodec.decode(encoded)).meals

    private fun assertFailure(encoded: String, reason: MealDiaryCodecError) {
        assertEquals(reason, assertIs<MealDiaryDecodeResult.Failure>(MealDiaryCodec.decode(encoded)).reason)
    }

    private fun meal() = SavedMeal(
        id = "one",
        localDay = 20_020,
        timeLabel = "12:34",
        name = "Lentil soup",
        portionGrams = 150.0,
        ingredients = listOf("Lentils"),
        source = SavedMealSource.PHOTO,
    )

    private fun rawRecord(record: RawMealRecord = RawMealRecord()): String {
        val fields = with(record) { listOf(id, day, time, name, portion, source, "", "1", ingredient) }
        return "SNMD1:1:" + fields.joinToString("") { "${it.length}:$it" }
    }

    private data class RawMealRecord(
        val id: String = "one",
        val day: String = "20020",
        val time: String = "12:34",
        val name: String = "Soup",
        val portion: String = "150.0",
        val source: String = "PHOTO",
        val ingredient: String = "Lentils",
    )
}
