package com.supernutrition.domain.inference

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * Shared lifecycle for local inference adapters.
 *
 * The native token remains platform-owned, but generation supersession,
 * cancellation, and exactly-once cleanup follow one contract on every host.
 */
class InferenceGenerationController<Token>(
    private val cancelToken: (Token) -> Unit,
    private val releaseToken: (Token) -> Unit,
) {
    private var nextGeneration = 0L
    private val stateMutex = Mutex()
    private val activeTokens = mutableMapOf<AttemptId, Token>()

    suspend fun <Outcome> run(
        attemptId: AttemptId,
        tokenFactory: (generation: Long) -> Token,
        block: suspend (Token) -> Outcome,
    ): Outcome {
        val (token, previousToken) = stateMutex.withLock {
            val token = tokenFactory(++nextGeneration)
            val previousToken = activeTokens.put(attemptId, token)
            token to previousToken
        }
        previousToken?.let(cancelToken)

        return try {
            val outcome = block(token)
            currentCoroutineContext().ensureActive()
            if (!stateMutex.withLock { activeTokens[attemptId] == token }) {
                throw CancellationException("Inference result was superseded")
            }
            outcome
        } catch (cancellation: CancellationException) {
            cancelToken(token)
            throw cancellation
        } finally {
            stateMutex.withLock {
                if (activeTokens[attemptId] == token) {
                    activeTokens.remove(attemptId)
                }
            }
            releaseToken(token)
        }
    }

    suspend fun cancel(attemptId: AttemptId) {
        val token = stateMutex.withLock {
            activeTokens.remove(attemptId)
        }
        token?.let(cancelToken)
    }
}
