package com.supernutrition.sharedkit

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Executable Native-side bridge contract for the Swift host boundary. */
class SharedKitBootstrapBridgeTest {
    @Test
    fun exportedBootstrapRemainsIdempotentOnNative() {
        SharedKitBootstrap.resetForTests()
        assertTrue(SharedKitBootstrap.start())
        assertFalse(SharedKitBootstrap.start())
    }
}
