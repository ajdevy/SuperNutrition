package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.nutrition.ApprovedNutritionCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class MealCatalogReplacementTest {
    @Test
    fun `explicit retry after ready source failure opens a replacement and closes old only after success`() {
        val failedSource = FeatureCatalogFixture()
        val first = FeatureCatalogHandle(failedSource)
        val replacement = FeatureCatalogHandle(FeatureCatalogFixture())
        var attempts = 0
        val session = MealCatalogSession { if (++attempts == 1) first else replacement }
        session.start(CoroutineScope(ImmediateCatalogDispatcher()), ImmediateCatalogDispatcher())
        assertIs<MealCatalogState.Ready>(session.state.value)
        failedSource.failure = IllegalStateException("database search failed")
        assertFailsWith<IllegalStateException> { session.catalog?.search("cheese") }
        session.retry()
        assertEquals(2, attempts)
        assertSame(replacement.catalog, session.catalog)
        assertEquals(1, first.closes)
        assertEquals(0, replacement.closes)
        session.close()
        assertEquals(1, replacement.closes)
    }

    @Test
    fun `failed null and getter replacement retain previous ownership until later success`() {
        val first = FeatureCatalogHandle(FeatureCatalogFixture())
        val replacement = FeatureCatalogHandle(FeatureCatalogFixture())
        var brokenCloses = 0
        val broken = object : MealCatalogHandle {
            override val catalog: ApprovedNutritionCatalog get() = error("invalid replacement")

            override fun close() {
                brokenCloses++
            }
        }
        var attempts = 0
        val session = MealCatalogSession {
            when (++attempts) {
                1 -> first
                2 -> null
                3 -> error("replacement unavailable")
                4 -> broken
                else -> replacement
            }
        }
        session.start(CoroutineScope(ImmediateCatalogDispatcher()), ImmediateCatalogDispatcher())
        repeat(3) {
            session.retry()
            assertEquals(MealCatalogState.Unavailable, session.state.value)
            assertEquals(0, first.closes)
        }
        assertEquals(1, brokenCloses)
        first.closeFailure = IllegalStateException("old cleanup failed")
        session.retry()
        assertSame(replacement.catalog, session.catalog)
        assertEquals(1, first.closes)
        session.close()
        assertEquals(1, replacement.closes)
    }

    @Test
    fun `cancelled replacement leaves unavailable state retains old handle and allows explicit retry`() {
        val main = QueuedCatalogDispatcher()
        val background = QueuedCatalogDispatcher()
        val owner = SupervisorJob()
        val first = FeatureCatalogHandle(FeatureCatalogFixture())
        val replacement = FeatureCatalogHandle(FeatureCatalogFixture())
        var attempts = 0
        val session = MealCatalogSession {
            when (++attempts) {
                1 -> first
                2 -> throw CancellationException("replacement cancelled")
                else -> replacement
            }
        }
        session.start(CoroutineScope(owner + main), background)
        main.runNext()
        background.runNext()
        main.runAll()
        session.retry()
        main.runNext()
        var completion: Throwable? = null
        owner.children.single().invokeOnCompletion { completion = it }
        background.runNext()
        main.runAll()
        assertEquals("replacement cancelled", assertIs<CancellationException>(completion).message)
        assertEquals(MealCatalogState.Unavailable, session.state.value)
        assertEquals(0, first.closes)
        session.retry()
        main.runNext()
        background.runNext()
        assertEquals(0, first.closes)
        main.runAll()
        assertSame(replacement.catalog, session.catalog)
        assertEquals(1, first.closes)
        session.close()
        assertEquals(1, replacement.closes)
    }

    @Test
    fun `disposing after replacement is produced closes old and late candidate once`() {
        val main = QueuedCatalogDispatcher()
        val background = QueuedCatalogDispatcher()
        val first = FeatureCatalogHandle(FeatureCatalogFixture())
        val replacement = FeatureCatalogHandle(FeatureCatalogFixture())
        var attempts = 0
        val session = MealCatalogSession { if (++attempts == 1) first else replacement }
        session.start(CoroutineScope(main), background)
        main.runNext()
        background.runNext()
        main.runAll()
        session.retry()
        main.runNext()
        background.runNext()
        session.close()
        main.runAll()
        session.close()
        assertEquals(MealCatalogState.Unavailable, session.state.value)
        assertEquals(1, first.closes)
        assertEquals(1, replacement.closes)
    }
}
