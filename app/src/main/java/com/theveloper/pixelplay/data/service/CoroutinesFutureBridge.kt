package com.theveloper.pixelplay.data.service

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Bridges a suspending [block] in this [CoroutineScope] to a Guava [ListenableFuture] with
 * bidirectional cancellation:
 *  - Cancelling the returned [ListenableFuture] cancels the underlying coroutine [kotlinx.coroutines.Job].
 *  - Cancelling the coroutine marks the future as cancelled via [SettableFuture.cancel] without
 *    propagating [CancellationException] as a failure.
 */
internal fun <T> CoroutineScope.launchListenableFuture(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    block: suspend () -> T,
): ListenableFuture<T> {
    val future = SettableFuture.create<T>()
    val job = launch(dispatcher) {
        try {
            val result = block()
            future.set(result)
        } catch (ce: CancellationException) {
            future.cancel(false)
            throw ce
        } catch (t: Throwable) {
            future.setException(t)
        }
    }
    future.addListener(
        {
            if (future.isCancelled && !job.isCancelled) {
                job.cancel()
            }
        },
        MoreExecutors.directExecutor(),
    )
    return future
}
