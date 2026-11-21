package com.theveloper.pixelplay.data.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class CoroutinesFutureBridgeTest {

    @Test
    fun `cancelling ListenableFuture cancels underlying coroutine job`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val started = CompletableDeferred<Unit>()
        val cancelledLatch = CountDownLatch(1)

        val future = scope.launchListenableFuture(Dispatchers.Unconfined) {
            started.complete(Unit)
            try {
                CompletableDeferred<Unit>().await()
            } finally {
                cancelledLatch.countDown()
            }
        }

        assertTrue(started.isCompleted)
        assertTrue(future.cancel(true))
        assertTrue(future.isCancelled)
        assertTrue(cancelledLatch.await(1, TimeUnit.SECONDS))
        scope.cancel()
    }

    @Test
    fun `cancelling coroutine scope cancels future without wrapping CancellationException`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val future = scope.launchListenableFuture(Dispatchers.Unconfined) {
            CompletableDeferred<String>().await()
        }

        scope.cancel()

        assertTrue(future.isCancelled)
        assertThrows(CancellationException::class.java) {
            future.get(1, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `successful result and non-cancellation errors propagate cleanly`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        val okFuture = scope.launchListenableFuture(Dispatchers.Unconfined) { 42 }
        assertFalse(okFuture.isCancelled)
        assertEquals(42, okFuture.get(1, TimeUnit.SECONDS))

        val errorFuture = scope.launchListenableFuture<Int>(Dispatchers.Unconfined) {
            throw IllegalStateException("boom")
        }
        val executionException = assertThrows(ExecutionException::class.java) {
            errorFuture.get(1, TimeUnit.SECONDS)
        }
        assertTrue(executionException.cause is IllegalStateException)
        scope.cancel()
    }
}
