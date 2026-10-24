package com.supernutrition.feature.photoanalysis

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MealCatalogSessionTest {
    @Test
    fun `default sessions are isolated unavailable and close before start is final`() {
        val first = MealCatalogSession()
        val second = MealCatalogSession()
        first.retry()
        first.start(CoroutineScope(ImmediateCatalogDispatcher()), ImmediateCatalogDispatcher())
        assertEquals(MealCatalogState.Unavailable, first.state.value)
        first.close()
        first.start(CoroutineScope(ImmediateCatalogDispatcher()), ImmediateCatalogDispatcher())
        first.retry()
        assertNull(first.catalog)
        assertEquals(MealCatalogState.Unavailable, second.state.value)
        second.close()
        second.start(CoroutineScope(ImmediateCatalogDispatcher()), ImmediateCatalogDispatcher())
        assertEquals(MealCatalogState.Unavailable, second.state.value)
    }

    @Test
    fun `opening is explicit background work and duplicate starts retries and closes are idempotent`() {
        val main = QueuedCatalogDispatcher()
        val background = QueuedCatalogDispatcher()
        val handle = FeatureCatalogHandle(FeatureCatalogFixture())
        var calls = 0
        val session = MealCatalogSession {
            assertTrue(background.running)
            assertFalse(main.running)
            calls++
            handle
        }
        assertEquals(0, calls)
        session.retry()
        session.start(CoroutineScope(main), background)
        session.start(CoroutineScope(main), background)
        session.retry()
        assertEquals(MealCatalogState.Loading, session.state.value)
        assertEquals(0, calls)
        main.runNext()
        background.runNext()
        assertEquals(MealCatalogState.Loading, session.state.value)
        main.runAll()
        assertSame(handle.catalog, assertIs<MealCatalogState.Ready>(session.state.value).catalog)
        assertSame(handle.catalog, session.catalog)
        assertEquals(1, calls)
        session.close()
        session.close()
        session.retry()
        assertNull(session.catalog)
        assertEquals(1, handle.closes)
    }

    @Test
    fun `null and ordinary opening failure are visible and explicit retry can succeed`() {
        val handle = FeatureCatalogHandle(FeatureCatalogFixture())
        var calls = 0
        val session = MealCatalogSession {
            when (++calls) {
                1 -> null
                2 -> error("unavailable package")
                else -> handle
            }
        }
        session.start(CoroutineScope(ImmediateCatalogDispatcher()), ImmediateCatalogDispatcher())
        assertEquals(MealCatalogState.Unavailable, session.state.value)
        session.retry()
        assertEquals(MealCatalogState.Unavailable, session.state.value)
        session.retry()
        assertIs<MealCatalogState.Ready>(session.state.value)
        session.close()
        assertEquals(1, handle.closes)
    }

    @Test
    fun `prompt cancellation inside factory captures and closes its returned handle despite cleanup failure`() {
        val main = QueuedCatalogDispatcher()
        val background = QueuedCatalogDispatcher()
        val owner = SupervisorJob()
        val cancelled = CancellationException("owner disposed during open")
        val handle = FeatureCatalogHandle(FeatureCatalogFixture()).also { it.closeFailure = errorForCleanup() }
        val session = MealCatalogSession {
            owner.cancel(cancelled)
            handle
        }
        session.start(CoroutineScope(owner + main), background)
        main.runNext()
        var completion: Throwable? = null
        owner.children.single().invokeOnCompletion { completion = it }
        background.runNext()
        main.runAll()
        assertEquals(cancelled.message, assertIs<CancellationException>(completion).message)
        assertEquals(1, handle.closes)
        assertEquals(MealCatalogState.Unavailable, session.state.value)
        session.close()
        assertEquals(1, handle.closes)
    }

    @Test
    fun `candidate produced before main publication is closed when owner is disposed`() {
        val main = QueuedCatalogDispatcher()
        val background = QueuedCatalogDispatcher()
        val handle = FeatureCatalogHandle(FeatureCatalogFixture())
        val session = MealCatalogSession { handle }
        session.start(CoroutineScope(main), background)
        main.runNext()
        background.runNext()
        session.close()
        main.runAll()
        assertEquals(1, handle.closes)
        assertEquals(MealCatalogState.Unavailable, session.state.value)
    }

    @Test
    fun `closing before startup work or using an inactive owner never opens a source`() {
        val main = QueuedCatalogDispatcher()
        var calls = 0
        val session = MealCatalogSession {
            calls++
            null
        }
        session.start(CoroutineScope(main), main)
        session.close()
        main.runAll()
        val inactive = MealCatalogSession {
            calls++
            null
        }
        inactive.start(CoroutineScope(Job().also { it.cancel() } + main), main)
        inactive.retry()
        assertEquals(0, calls)
        assertEquals(MealCatalogState.Unavailable, inactive.state.value)
        inactive.close()
    }

    @Test
    fun `direct factory cancellation propagates instead of being converted to normal failure`() {
        val main = QueuedCatalogDispatcher()
        val background = QueuedCatalogDispatcher()
        val owner = SupervisorJob()
        val cancelled = CancellationException("source cancelled")
        val session = MealCatalogSession { throw cancelled }
        session.start(CoroutineScope(owner + main), background)
        main.runNext()
        var completion: Throwable? = null
        owner.children.single().invokeOnCompletion { completion = it }
        background.runNext()
        main.runAll()
        assertEquals(cancelled.message, assertIs<CancellationException>(completion).message)
        assertEquals(MealCatalogState.Unavailable, session.state.value)
        session.close()
    }

    @Test
    fun `synchronous disposal during opening cannot publish a ready source`() {
        val dispatcher = ImmediateCatalogDispatcher()
        val handle = FeatureCatalogHandle(FeatureCatalogFixture())
        lateinit var session: MealCatalogSession
        session = MealCatalogSession {
            session.close()
            handle
        }
        session.start(CoroutineScope(dispatcher), dispatcher)
        assertEquals(1, handle.closes)
        assertEquals(MealCatalogState.Unavailable, session.state.value)
    }

    private fun errorForCleanup() = IllegalStateException("cleanup failed")
}
