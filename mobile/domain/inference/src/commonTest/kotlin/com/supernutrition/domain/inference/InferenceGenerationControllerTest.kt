package com.supernutrition.domain.inference

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class InferenceGenerationControllerTest {
    @Test
    fun `completed active generation returns its outcome releases its token and leaves no active token`() =
        runBlocking {
            val cancelled = mutableListOf<String>()
            val released = mutableListOf<String>()
            val controller = InferenceGenerationController(cancelled::add, released::add)
            val attempt = AttemptId("active-generation")

            val outcome = controller.run(
                attemptId = attempt,
                tokenFactory = { generation -> "token-$generation" },
                block = { token -> "$token-result" },
            )

            assertEquals("token-1-result", outcome)
            assertEquals(emptyList<String?>(), cancelled)
            assertEquals(listOf("token-1"), released)

            controller.cancel(attempt)
            assertEquals(emptyList(), cancelled)
        }

    @Test
    fun `newer generation supersedes an in-flight result and both tokens are released`() = runBlocking {
        val cancelled = mutableListOf<String>()
        val released = mutableListOf<String>()
        val controller = InferenceGenerationController(cancelled::add, released::add)
        val attempt = AttemptId("superseded-generation")
        val firstStarted = CompletableDeferred<Unit>()
        val allowFirstCompletion = CompletableDeferred<Unit>()
        val first = async {
            controller.run(
                attemptId = attempt,
                tokenFactory = { generation -> "token-$generation" },
                block = { token ->
                    firstStarted.complete(Unit)
                    allowFirstCompletion.await()
                    "$token-result"
                },
            )
        }

        firstStarted.await()
        val newest = controller.run(
            attemptId = attempt,
            tokenFactory = { generation -> "token-$generation" },
            block = { token -> "$token-result" },
        )
        allowFirstCompletion.complete(Unit)

        assertEquals("token-2-result", newest)
        assertCancelled { first.await() }
        assertEquals(listOf("token-1", "token-1"), cancelled)
        assertEquals(listOf("token-2", "token-1"), released)
    }

    @Test
    fun `explicit cancellation invalidates an in-flight generation before it can publish`() = runBlocking {
        val cancelled = mutableListOf<String>()
        val released = mutableListOf<String>()
        val controller = InferenceGenerationController(cancelled::add, released::add)
        val attempt = AttemptId("cancelled-generation")
        val started = CompletableDeferred<Unit>()
        val allowCompletion = CompletableDeferred<Unit>()
        val pending = async {
            controller.run(
                attemptId = attempt,
                tokenFactory = { generation -> "token-$generation" },
                block = { token ->
                    started.complete(Unit)
                    allowCompletion.await()
                    "$token-result"
                },
            )
        }

        started.await()
        controller.cancel(attempt)
        allowCompletion.complete(Unit)

        assertCancelled { pending.await() }
        assertEquals(listOf("token-1", "token-1"), cancelled)
        assertEquals(listOf("token-1"), released)
    }

    @Test
    fun `nullable platform token is released after a completed active generation`() = runBlocking {
        val cancelled = mutableListOf<String?>()
        val released = mutableListOf<String?>()
        val controller = InferenceGenerationController<String?>(cancelled::add, released::add)

        val outcome = controller.run(
            attemptId = AttemptId("nullable-token"),
            tokenFactory = { null },
            block = { token -> token ?: "no-native-token" },
        )

        assertEquals("no-native-token", outcome)
        assertEquals(emptyList<String?>(), cancelled)
        assertEquals(listOf<String?>(null), released)
    }

    @Test
    fun `cancellation from an active block cancels removes and releases its token`() = runBlocking {
        val cancelled = mutableListOf<String>()
        val released = mutableListOf<String>()
        val controller = InferenceGenerationController(cancelled::add, released::add)

        assertCancelled {
            controller.run(
                attemptId = AttemptId("block-cancellation"),
                tokenFactory = { generation -> "token-$generation" },
                block = { throw CancellationException("native inference cancelled") },
            )
        }

        assertEquals(listOf("token-1"), cancelled)
        assertEquals(listOf("token-1"), released)
    }

    private suspend fun assertCancelled(action: suspend () -> Any?) {
        try {
            action()
            fail("Expected a superseded generation cancellation")
        } catch (_: CancellationException) {
        }
    }
}
