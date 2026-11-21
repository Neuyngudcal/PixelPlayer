package com.theveloper.pixelplay.data.service.cast

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BoundedLruCacheTest {

    @Test
    fun evictsLeastRecentlyUsedWhenCapacityExceeded() {
        val cache = BoundedLruCache<String, String>(maxEntries = 3)
        cache["a"] = "audio/mp4"
        cache["b"] = "audio/flac"
        cache["c"] = "audio/alac"

        // Touch "a" so "b" becomes least recently used.
        assertThat(cache["a"]).isEqualTo("audio/mp4")

        cache["d"] = "audio/mpeg"

        assertThat(cache.size()).isEqualTo(3)
        assertThat(cache.getCached("b")).isEqualTo(BoundedLruCache.LookupResult.Miss)
        assertThat(cache.getCached("a")).isEqualTo(BoundedLruCache.LookupResult.Hit("audio/mp4"))
        assertThat(cache.getCached("c")).isEqualTo(BoundedLruCache.LookupResult.Hit("audio/alac"))
        assertThat(cache.getCached("d")).isEqualTo(BoundedLruCache.LookupResult.Hit("audio/mpeg"))
    }

    @Test
    fun preservesExplicitNullNegativeCacheEntries() {
        val cache = BoundedLruCache<String, String?>(maxEntries = 10)
        assertThat(cache.getCached("missing")).isEqualTo(BoundedLruCache.LookupResult.Miss)

        cache["missing"] = null
        assertThat(cache.getCached("missing")).isEqualTo(BoundedLruCache.LookupResult.Hit(null))
    }

    @Test
    fun concurrentReadsAndWritesRemainBoundedAndThreadSafe() {
        val cache = BoundedLruCache<Int, String?>(maxEntries = 64)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val futures = (0 until 4).map { worker ->
                pool.submit {
                    for (i in 0 until 250) {
                        val key = (worker * 100 + i) % 128
                        cache[key] = if (key % 5 == 0) null else "mime-$key"
                        cache.getCached(key)
                    }
                }
            }
            futures.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
        assertThat(cache.size()).isAtMost(64)
    }
}
