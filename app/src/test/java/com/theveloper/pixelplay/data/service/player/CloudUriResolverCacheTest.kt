package com.theveloper.pixelplay.data.service.player

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class CloudUriResolverCacheTest {

    @Test
    fun resolveWithCoalescing_concurrentCallersInvokeUpstreamOnce() = runTest {
        val cache = CloudUriResolverCache()
        val gate = CompletableDeferred<Unit>()
        val upstreamCalls = AtomicInteger(0)

        val jobs = List(8) {
            async {
                cache.resolveWithCoalescing("netease", "netease://song/42") {
                    upstreamCalls.incrementAndGet()
                    gate.await()
                    "http://127.0.0.1:41001/netease/42.mp3"
                }
            }
        }

        testScheduler.runCurrent()
        assertThat(upstreamCalls.get()).isEqualTo(1)

        gate.complete(Unit)
        val results = jobs.awaitAll()
        assertThat(results.distinct()).containsExactly("http://127.0.0.1:41001/netease/42.mp3")
        assertThat(upstreamCalls.get()).isEqualTo(1)
    }

    @Test
    fun resolveWithCoalescing_cancelledLeaderAllowsWaitingCallerToRetryCleanly() = runTest {
        val cache = CloudUriResolverCache()
        val firstAttemptStarted = CompletableDeferred<Unit>()
        val upstreamCalls = AtomicInteger(0)

        val leader = launch {
            cache.resolveWithCoalescing("telegram", "telegram://99") {
                upstreamCalls.incrementAndGet()
                firstAttemptStarted.complete(Unit)
                CompletableDeferred<String?>().await()
            }
        }

        firstAttemptStarted.await()
        val joiner = async {
            cache.resolveWithCoalescing("telegram", "telegram://99") {
                upstreamCalls.incrementAndGet()
                "http://127.0.0.1:41010/telegram/99"
            }
        }

        testScheduler.runCurrent()
        leader.cancelAndJoin()

        val resolved = joiner.await()
        assertThat(resolved).isEqualTo("http://127.0.0.1:41010/telegram/99")
        assertThat(upstreamCalls.get()).isEqualTo(2)
    }

    @Test
    fun peekValid_invalidatesStaleLoopbackProxyPortOnProxyRestart() {
        val cache = CloudUriResolverCache()
        cache.putResolved("qqmusic", "qqmusic://song/1", "http://127.0.0.1:41001/song/1")
        assertThat(cache.peekValid("qqmusic", "qqmusic://song/1"))
            .isEqualTo("http://127.0.0.1:41001/song/1")

        // New resolution for same scheme reveals a new local proxy port after proxy restart.
        cache.putResolved("qqmusic", "qqmusic://song/2", "http://127.0.0.1:42002/song/2")

        assertThat(cache.peekValid("qqmusic", "qqmusic://song/1")).isNull()
        assertThat(cache.peekValid("qqmusic", "qqmusic://song/2"))
            .isEqualTo("http://127.0.0.1:42002/song/2")
    }

    @Test
    fun peekValid_evictsExpiredEntriesAndDeletedLocalFiles() {
        var nowMs = 1_000L
        var filePresent = true
        val cache = CloudUriResolverCache(
            ttlMs = 60_000L,
            timeSourceMs = { nowMs },
            fileExists = { filePresent }
        )

        cache.putResolved("telegram", "telegram://1", "file:///cache/tg_1.flac")
        assertThat(cache.peekValid("telegram", "telegram://1")).isEqualTo("file:///cache/tg_1.flac")

        // Deleting local cached file makes entry stale immediately.
        filePresent = false
        assertThat(cache.peekValid("telegram", "telegram://1")).isNull()

        filePresent = true
        cache.putResolved("telegram", "telegram://2", "file:///cache/tg_2.flac")
        nowMs += 60_001L
        assertThat(cache.peekValid("telegram", "telegram://2")).isNull()
    }
}
