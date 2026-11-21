package com.theveloper.pixelplay.data.stream

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class CloudSongUrlCoalescerTest {

    @Test
    fun `concurrent callers within cooldown join active request instead of throttling`() = runBlocking {
        val nowMs = AtomicLong(1_000L)
        val coalescer = CloudSongUrlCoalescer<Long>(
            cooldownMs = 1500L,
            clock = { nowMs.get() },
            throttledMessage = "Request throttled"
        )
        val calls = AtomicInteger(0)

        val first = async {
            coalescer.resolve(42L) {
                calls.incrementAndGet()
                delay(120L)
                Result.success("https://cdn.example.com/track.flac")
            }
        }
        // Second caller arrives 20ms later (well inside 1500ms cooldown)
        delay(20L)
        nowMs.addAndGet(20L)
        val second = async {
            coalescer.resolve(42L) {
                calls.incrementAndGet()
                Result.success("https://should-not-execute.example.com")
            }
        }

        assertEquals("https://cdn.example.com/track.flac", first.await().getOrThrow())
        assertEquals("https://cdn.example.com/track.flac", second.await().getOrThrow())
        assertEquals(1, calls.get())
        assertEquals(0, coalescer.inFlightCount())
    }

    @Test
    fun `sequential retry after failure is throttled within cooldown and allowed afterwards`() = runBlocking {
        val nowMs = AtomicLong(10_000L)
        val coalescer = CloudSongUrlCoalescer<String>(
            cooldownMs = 1500L,
            clock = { nowMs.get() },
            throttledMessage = "QQ song URL request throttled"
        )
        val calls = AtomicInteger(0)

        val firstFailure = coalescer.resolve("songMid_1") {
            calls.incrementAndGet()
            Result.failure(IllegalStateException("upstream error"))
        }
        assertTrue(firstFailure.isFailure)
        assertEquals(1, calls.get())

        // Retry 500ms later -> throttled without invoking upstream block
        nowMs.addAndGet(500L)
        val throttled = coalescer.resolve("songMid_1") {
            calls.incrementAndGet()
            Result.success("https://cdn.example.com/recovered.mp3")
        }
        assertTrue(throttled.isFailure)
        assertTrue(throttled.exceptionOrNull()?.message?.contains("throttled") == true)
        assertEquals(1, calls.get())

        // Retry after cooldown expires -> succeeds
        nowMs.addAndGet(1_100L)
        val recovered = coalescer.resolve("songMid_1") {
            calls.incrementAndGet()
            Result.success("https://cdn.example.com/recovered.mp3")
        }
        assertEquals("https://cdn.example.com/recovered.mp3", recovered.getOrThrow())
        assertEquals(2, calls.get())
    }

    @Test
    fun `cancellation removes in-flight entry in finally and never leaks`() = runBlocking {
        val coalescer = CloudSongUrlCoalescer<Long>(cooldownMs = 1500L)
        val firstStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val allowFinish = kotlinx.coroutines.CompletableDeferred<Unit>()

        val job = async {
            coalescer.resolve(77L) {
                firstStarted.complete(Unit)
                allowFinish.await()
                Result.success("https://cdn.example.com/never.mp3")
            }
        }
        firstStarted.await()
        assertEquals(1, coalescer.inFlightCount())

        job.cancel(CancellationException("caller cancelled"))
        runCatching { job.await() }

        assertEquals(0, coalescer.inFlightCount())

        // Subsequent request proceeds normally without stale in-flight lock or failure cooldown
        val next = coalescer.resolve(77L) {
            Result.success("https://cdn.example.com/fresh.mp3")
        }
        assertEquals("https://cdn.example.com/fresh.mp3", next.getOrThrow())
        assertEquals(0, coalescer.inFlightCount())
    }
}
