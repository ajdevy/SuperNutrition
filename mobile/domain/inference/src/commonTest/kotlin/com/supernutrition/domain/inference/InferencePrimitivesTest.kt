package com.supernutrition.domain.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The shared validators every contract type is built from.
 *
 * They are public API of the inference binding and had no direct tests: each was
 * exercised only incidentally, through whichever type happened to call it with
 * whichever value that type's tests happened to use. `isSha256Digest` decides
 * whether a release digest is well formed, which is the check standing between a
 * pinned contract and a typo, and nothing asserted what it accepts.
 */
class InferencePrimitivesTest {
    private val valid = "b63de2850e1213815c8949fdfec82acb54903ca1b149b8bd6bdc748718b64179"

    @Test
    fun `a well-formed digest is exactly sixty-four lowercase hex characters`() {
        assertTrue(isSha256Digest(valid))
        assertEquals(64, valid.length)
    }

    @Test
    fun `a digest of the wrong length is rejected whatever it contains`() {
        assertFalse(isSha256Digest(valid.dropLast(1)), "63 characters is not a SHA-256 digest")
        assertFalse(isSha256Digest(valid + "0"), "65 characters is not one either")
        assertFalse(isSha256Digest(""), "and neither is nothing")
    }

    @Test
    fun `uppercase hex is rejected rather than quietly accepted`() {
        // The same digest in the other case is the same number and a different
        // string. Accepting both would make a pinned digest comparable two ways,
        // and only one of them is what the emitting script writes.
        assertFalse(isSha256Digest(valid.uppercase()))
    }

    @Test
    fun `a non-hex character anywhere in the string is rejected`() {
        // Checked at both ends and in the middle, because a range test is the
        // kind of thing that is right for the first character and wrong for the
        // last.
        assertFalse(isSha256Digest("g" + valid.drop(1)))
        assertFalse(isSha256Digest(valid.dropLast(1) + "z"))
        assertFalse(isSha256Digest(valid.take(32) + "-" + valid.drop(33)))
    }

    @Test
    fun `requireSha256Digest returns the value it accepted and raises with the caller's message`() {
        assertEquals(valid, requireSha256Digest(valid, "unused"))

        val failure = assertFailsWith<IllegalArgumentException> {
            requireSha256Digest("not-a-digest", "release digest is malformed")
        }
        assertEquals("release digest is malformed", failure.message)
    }

    @Test
    fun `requireNonBlankValue treats whitespace as blank, not as a value`() {
        // The distinction that matters: an engine id of " " is absent, and a
        // check for emptiness alone would let it through and leave a capability
        // attributed to nothing.
        assertEquals("fixture-engine", requireNonBlankValue("fixture-engine", "unused"))
        assertFailsWith<IllegalArgumentException> { requireNonBlankValue("", "engine id must not be blank") }
        assertFailsWith<IllegalArgumentException> { requireNonBlankValue("   ", "engine id must not be blank") }
        assertFailsWith<IllegalArgumentException> { requireNonBlankValue("\t\n", "engine id must not be blank") }
    }
}
