package com.theveloper.pixelplay.data.service.cast

import java.util.LinkedHashMap

/**
 * Thread-safe bounded access-order LRU cache that preserves explicit `null` values
 * so negative probe results remain distinguishable from cache misses.
 */
internal class BoundedLruCache<K, V>(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    sealed class LookupResult<out V> {
        data object Miss : LookupResult<Nothing>()
        data class Hit<out V>(val value: V) : LookupResult<V>()
    }

    private val lock = Any()
    private val map = object : LinkedHashMap<K, Any?>(maxEntries + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Any?>?): Boolean {
            return size > maxEntries
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun getCached(key: K): LookupResult<V> = synchronized(lock) {
        if (!map.containsKey(key)) {
            LookupResult.Miss
        } else {
            LookupResult.Hit(map[key] as V)
        }
    }

    operator fun get(key: K): V? = synchronized(lock) {
        @Suppress("UNCHECKED_CAST")
        map[key] as V?
    }

    operator fun set(key: K, value: V) {
        synchronized(lock) {
            map[key] = value
        }
    }

    fun clear() {
        synchronized(lock) {
            map.clear()
        }
    }

    fun size(): Int = synchronized(lock) { map.size }

    companion object {
        internal const val DEFAULT_MAX_ENTRIES = 500
    }
}
