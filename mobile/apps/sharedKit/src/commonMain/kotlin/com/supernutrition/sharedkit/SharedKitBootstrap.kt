package com.supernutrition.sharedkit

import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

private data class IosBootstrapMarker(val name: String = "ios-bootstrap")

/**
 * The only public iOS composition/bootstrap entry point.
 *
 * The thin Swift host calls this once. Repeated calls are deliberately safe and
 * do not create another Koin application or duplicate bindings.
 */
public object SharedKitBootstrap {
    private var started: Boolean = false

    /** Returns true only for the first successful bootstrap call. */
    public fun start(): Boolean {
        if (started) return false

        startKoin {
            modules(module { single { IosBootstrapMarker() } })
        }
        started = true
        return true
    }

    /** Resets only the in-process test fixture; never exposed to the Swift API. */
    internal fun resetForTests() {
        if (!started) return
        stopKoin()
        started = false
    }
}
