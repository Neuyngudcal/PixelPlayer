package com.theveloper.pixelplay.data.service.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.io.File
import java.net.URI
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Bounded, thread-safe resolved URI cache with single-flight coalescing, TTL eviction,
 * local loopback proxy port invalidation, and local file existence validation.
 */
internal class CloudUriResolverCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val timeSourceMs: () -> Long = { System.currentTimeMillis() },
    private val fileExists: (String) -> Boolean = { path -> File(path).exists() }
) {
    private data class CachedUriEntry(
        val resolvedUri: String,
        val scheme: String,
        val cachedAtMs: Long
    )

    private val cacheLock = Any()
    private val entries = object : LinkedHashMap<String, CachedUriEntry>(maxEntries + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedUriEntry>?): Boolean {
            return size > maxEntries
        }
    }

    private val latestProxyPortByScheme = ConcurrentHashMap<String, Int>()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<String?>>()

    fun peekValid(scheme: String?, originalUri: String): String? {
        val entry = synchronized(cacheLock) { entries[originalUri] } ?: return null
        if (!isEntryValid(entry, expectedScheme = scheme)) {
            synchronized(cacheLock) {
                if (entries[originalUri] === entry) {
                    entries.remove(originalUri)
                }
            }
            return null
        }
        return entry.resolvedUri
    }

    fun putResolved(scheme: String, originalUri: String, resolvedUri: String) {
        if (resolvedUri == originalUri) return
        recordLoopbackProxyPort(scheme, resolvedUri)
        val entry = CachedUriEntry(
            resolvedUri = resolvedUri,
            scheme = scheme,
            cachedAtMs = timeSourceMs()
        )
        synchronized(cacheLock) {
            entries[originalUri] = entry
        }
    }

    suspend fun resolveWithCoalescing(
        scheme: String,
        originalUri: String,
        resolver: suspend () -> String?
    ): String? {
        peekValid(scheme, originalUri)?.let { return it }

        while (true) {
            inFlight[originalUri]?.let { existing ->
                try {
                    return existing.await()
                } catch (ce: CancellationException) {
                    if (!currentCoroutineContext().isActive) throw ce
                    // Previous leader was cancelled; retry with a fresh flight below.
                }
            }

            val created = CompletableDeferred<String?>()
            val raced = inFlight.putIfAbsent(originalUri, created)
            if (raced != null) {
                try {
                    return raced.await()
                } catch (ce: CancellationException) {
                    if (!currentCoroutineContext().isActive) throw ce
                    continue
                }
            }

            try {
                // Re-check cache after winning the flight slot in case another caller just finished.
                val resolved = peekValid(scheme, originalUri) ?: resolver()?.also { freshUri ->
                    putResolved(scheme, originalUri, freshUri)
                }
                created.complete(resolved)
                return resolved
            } catch (ce: CancellationException) {
                created.cancel(ce)
                throw ce
            } catch (t: Throwable) {
                created.completeExceptionally(t)
                throw t
            } finally {
                inFlight.remove(originalUri, created)
            }
        }
    }

    fun invalidateScheme(scheme: String) {
        latestProxyPortByScheme.remove(scheme)
        synchronized(cacheLock) {
            val iterator = entries.entries.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().value.scheme == scheme) {
                    iterator.remove()
                }
            }
        }
    }

    fun clear() {
        latestProxyPortByScheme.clear()
        synchronized(cacheLock) {
            entries.clear()
        }
    }

    private fun isEntryValid(entry: CachedUriEntry, expectedScheme: String?): Boolean {
        if (expectedScheme != null && entry.scheme != expectedScheme) {
            return false
        }
        if (timeSourceMs() - entry.cachedAtMs > ttlMs) {
            return false
        }
        val parsed = runCatching { URI(entry.resolvedUri) }.getOrNull() ?: return true
        when (parsed.scheme?.lowercase()) {
            "file" -> {
                val path = parsed.path
                if (path.isNullOrBlank() || !fileExists(path)) {
                    return false
                }
            }
            "http", "https" -> {
                val host = parsed.host
                val port = parsed.port
                if ((host == "127.0.0.1" || host == "localhost") && port > 0) {
                    val currentPort = latestProxyPortByScheme[entry.scheme]
                    if (currentPort != null && port != currentPort) {
                        return false
                    }
                }
            }
        }
        return true
    }

    private fun recordLoopbackProxyPort(scheme: String, resolvedUri: String) {
        val parsed = runCatching { URI(resolvedUri) }.getOrNull() ?: return
        val httpScheme = parsed.scheme?.lowercase()
        if ((httpScheme == "http" || httpScheme == "https") &&
            (parsed.host == "127.0.0.1" || parsed.host == "localhost") &&
            parsed.port > 0
        ) {
            latestProxyPortByScheme[scheme] = parsed.port
        }
    }

    companion object {
        internal const val DEFAULT_MAX_ENTRIES = 100
        internal const val DEFAULT_TTL_MS = 10 * 60_000L
    }
}
