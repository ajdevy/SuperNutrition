package com.supernutrition.sharedkit

import com.supernutrition.feature.photoanalysis.PhotoAnalysisAnalysisLauncher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P1-7 — iOS could take a photo and never analyse it.
 *
 * analysisLauncher was hardcoded to PhotoAnalysisAnalysisLauncher.None, so
 * photoReturned() normalised the image, dispatched PreparationSucceeded, reached
 * Ready, and then called a launcher that does nothing. `None` is documented as
 * the choice for a host that deliberately binds no analysis runtime; iOS is not
 * that host, it had simply never been given one.
 *
 * These run on the iOS target, so they are evidence about the Apple binary
 * rather than about a JVM stand-in.
 */
class SharedKitAnalysisLauncherTest {

    private class SpyLauncher : PhotoAnalysisAnalysisLauncher {
        val calls = mutableListOf<String>()
        override fun start() {
            calls += "start"
        }
        override fun cancel() {
            calls += "cancel"
        }
    }

    @Test
    fun `the composition root can bind a real launcher`() {
        // The defect was structural: there was no way to supply one at all.
        val spy = SpyLauncher()
        val session = SharedKitPhotoAnalysisSession("att-launcher-bound", spy)

        session.scanAgain()

        assertTrue(spy.calls.isNotEmpty(), "the injected launcher was never called")
    }

    @Test
    fun `scanning again cancels the run in flight`() {
        val spy = SpyLauncher()
        val session = SharedKitPhotoAnalysisSession("att-launcher-cancel", spy)

        session.scanAgain()

        assertEquals(listOf("cancel"), spy.calls)
    }

    @Test
    fun `cancelling a capture cancels analysis too`() {
        val spy = SpyLauncher()
        val session = SharedKitPhotoAnalysisSession("att-launcher-capture-cancel", spy)

        session.captureCancelled()

        assertEquals(listOf("cancel"), spy.calls)
    }

    @Test
    fun `a host that binds nothing still works`() {
        // The default remains, for the preview and screenshot paths that
        // genuinely do not analyse — but it is now a caller's decision rather
        // than something baked into the type.
        val session = SharedKitPhotoAnalysisSession("att-launcher-default")

        session.scanAgain()
    }
}
