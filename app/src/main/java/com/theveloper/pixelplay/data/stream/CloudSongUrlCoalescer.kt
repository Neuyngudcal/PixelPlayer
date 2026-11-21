package com.theveloper.pixelplay.data.stream

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Coalesces concurrent song URL resolution requests per key while rate-limiting
 * rapid back-to-back retry attempts after upstream failures.
 *
 * Ordering guarantee:
 * 1. Join any active in-flight [CompletableDeferred] BEFORE checking failure cooldown,
 *    so concurrent proxy/warmup callers never fail with "throttled" while a resolution
 *    is already underway.
 * 2. Enforce cooldown only when no in-flight request exists and the prior resolution failed.
 * 3. Always remove the deferred and complete/cancel it in `finally` on cancellation or failure.
 */
internal class CloudSongUrlCoalescer<K : Any>(
    private val cooldownMs: Long = 1500L,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val throttledMessage: String = "Song URL request throttled"
) {
    private val inFlightRequests = ConcurrentHashMap<K, CompletableDeferred<Result<String>>>()
    private val lastFailedAttemptAtMs = ConcurrentHashMap<K, Long>()

    suspend fun resolve(
        key: K,
        block: suspend () -> Result<String>
    ): Result<String> {
        while (true) {
            inFlightRequests[key]?.let { existing ->
                return existing.await()
            }

            val now = clock()
            val lastFailureAt = lastFailedAttemptAtMs[key]
            if (lastFailureAt != null && now - lastFailureAt < cooldownMs) {
                Timber.d("Skip song URL retry due to failure cooldown: key=$key")
                return Result.failure(IllegalStateException(throttledMessage))
            }

            val requestDeferred = CompletableDeferred<Result<String>>()
            val racer = inFlightRequests.putIfAbsent(key, requestDeferred)
            if (racer != null) {
                return racer.await()
            }

            var resolvedResult: Result<String>? = null
            var cancellationCause: CancellationException? = null
            try {
                val result = block()
                if (result.isSuccess) {
                    lastFailedAttemptAtMs.remove(key)
                } else {
                    lastFailedAttemptAtMs[key] = clock()
                }
                resolvedResult = result
                return result
            } catch (ce: CancellationException) {
                cancellationCause = ce
                throw ce
            } catch (t: Throwable) {
                lastFailedAttemptAtMs[key] = clock()
                val failure = Result.failure<String>(t)
                resolvedResult = failure
                return failure
            } finally {
                inFlightRequests.remove(key, requestDeferred)
                if (!requestDeferred.isCompleted) {
                    when {
                        resolvedResult != null -> requestDeferred.complete(resolvedResult)
                        cancellationCause != null -> requestDeferred.cancel(cancellationCause)
                        else -> requestDeferred.complete(
                            Result.failure(IllegalStateException("Song URL resolution aborted"))
                        )
                    }
                }
            }
        }
    }

    internal fun clear() {
        val entries = inFlightRequests.values.toList()
        inFlightRequests.clear()
        lastFailedAttemptAtMs.clear()
        entries.forEach { deferred ->
            if (!deferred.isCompleted) {
                deferred.cancel()
            }
        }
    }

    internal fun inFlightCount(): Int = inFlightRequests.size
}
