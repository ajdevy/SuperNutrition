package com.supernutrition.inference.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The consent-window parser, on its own.
 *
 * It exists because this module has no date library, and the alternative to a
 * strict parser is trusting a client-supplied timestamp we cannot read. Every
 * shape it cannot read must answer null, because null is what the caller turns
 * into a refusal — a parser that guessed would make a malformed receipt the most
 * permissive one there is.
 */
class Rfc3339ParsingTest {

    @Test
    fun `reads the contract's instant shape`() {
        // Cross-checked against a known epoch: 2026-08-03T10:00:00Z.
        assertEquals(1_785_751_200_000L, parseRfc3339Utc("2026-08-03T10:00:00Z"))
    }

    @Test
    fun `reads fractional seconds by discarding them`() {
        assertEquals(1_785_751_200_000L, parseRfc3339Utc("2026-08-03T10:00:00.123Z"))
    }

    @Test
    fun `tolerates surrounding whitespace`() {
        assertEquals(1_785_751_200_000L, parseRfc3339Utc("  2026-08-03T10:00:00Z  "))
    }

    @Test
    fun `handles the leap-year boundary`() {
        // 2028 is a leap year, so the 29th exists; the civil-days algorithm is
        // the part most likely to be subtly wrong.
        assertEquals(1_835_395_200_000L, parseRfc3339Utc("2028-02-29T00:00:00Z"))
    }

    @Test
    fun `handles a century non-leap year`() {
        // 2100 is NOT a leap year, which the /100 and /400 terms exist for.
        assertEquals(4_107_542_400_000L, parseRfc3339Utc("2100-03-01T00:00:00Z"))
    }

    @Test
    fun `accepts a leap second`() {
        assertEquals(1_785_751_260_000L, parseRfc3339Utc("2026-08-03T10:00:60Z"))
    }

    @Test
    fun `refuses everything it cannot read`() {
        val unreadable = listOf(
            "" to "empty",
            "2026-08-03" to "date only",
            "2026-08-03T10:00:00" to "no zone",
            "2026-08-03T10:00:00+01:00" to "a non-UTC offset it must not silently treat as UTC",
            "2026-08-03 10:00:00Z" to "space instead of T",
            "2026-08-03T10:00Z" to "no seconds",
            "not-a-timestamp-at-all" to "prose",
            "20260803T100000Z" to "no separators",
            "2026-8-3T10:00:00Zxx" to "trailing junk",
            "yyyy-mm-ddThh:mm:ssZ" to "non-numeric",
        )

        for ((value, why) in unreadable) {
            assertNull(parseRfc3339Utc(value), "parsed $why: $value")
        }
    }

    @Test
    fun `refuses out-of-range fields rather than rolling them over`() {
        // Rolling 13 months into next January would silently move a consent
        // window, which is the opposite of failing closed.
        val outOfRange = listOf(
            "2026-13-03T10:00:00Z",
            "2026-00-03T10:00:00Z",
            "2026-08-32T10:00:00Z",
            "2026-08-00T10:00:00Z",
            "2026-08-03T24:00:00Z",
            "2026-08-03T10:60:00Z",
            "2026-08-03T10:00:61Z",
        )

        for (value in outOfRange) {
            assertNull(parseRfc3339Utc(value), "accepted an impossible field: $value")
        }
    }
}
