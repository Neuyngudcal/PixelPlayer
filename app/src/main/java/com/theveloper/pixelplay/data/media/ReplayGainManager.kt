package com.theveloper.pixelplay.data.media

import android.os.ParcelFileDescriptor
import androidx.annotation.VisibleForTesting
import com.kyant.taglib.TagLib
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * Reads ReplayGain metadata from audio files and computes volume multipliers.
 *
 * Supports standard tags:
 * - REPLAYGAIN_TRACK_GAIN (e.g. "-6.54 dB")
 * - REPLAYGAIN_ALBUM_GAIN (e.g. "-8.20 dB")
 * - R128_TRACK_GAIN / R128_ALBUM_GAIN (RFC 7845 Q7.8 fixed-point `1/256 dB`, aligned by `+5.0 dB`)
 *
 * The gain is converted to a linear volume multiplier: 10^(gainDb / 20)
 * and clamped to [0.0, 1.0] to avoid clipping and match `Player.volume` bounds.
 */
@Singleton
class ReplayGainManager @Inject constructor() {

    private data class CacheEntry(
        val values: ReplayGainValues?,
        val lastModifiedMs: Long,
    )

    // LRU cache: filePath -> CacheEntry (supports both positive and negative cache hits)
    // Avoids re-reading tags on repeat, resume, or rapid track changes.
    private val cache = object : LinkedHashMap<String, CacheEntry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, CacheEntry>) = size > 200
    }

    @VisibleForTesting
    internal var propertyMapReader: (File) -> Map<String, Array<String>>? = { file ->
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            TagLib.getMetadata(fd.detachFd(), readPictures = false)?.propertyMap
        }
    }

    companion object {
        private const val TAG = "ReplayGainManager"

        // Pre-amp to apply when no ReplayGain tag is found (dB)
        // 0.0 means no change
        const val DEFAULT_PRE_AMP_DB = 0.0f
    }

    data class ReplayGainValues(
        val trackGainDb: Float? = null,
        val albumGainDb: Float? = null
    )

    /**
     * Invalidates any cached ReplayGain entry (positive or negative) for [filePath].
     */
    fun invalidateCache(filePath: String) {
        if (filePath.isBlank()) return
        synchronized(cache) {
            cache.remove(filePath)
        }
    }

    /**
     * Returns the cached ReplayGain values for the given path without triggering an IO read.
     * Returns null if the file has not been read yet, was modified since caching, or has no tags.
     */
    fun getCachedReplayGain(filePath: String): ReplayGainValues? {
        if (filePath.isBlank()) return null
        val file = File(filePath)
        val currentModified = if (file.exists()) file.lastModified() else return null
        return synchronized(cache) {
            val entry = cache[filePath] ?: return@synchronized null
            if (entry.lastModifiedMs != currentModified) {
                cache.remove(filePath)
                null
            } else {
                entry.values
            }
        }
    }

    /**
     * Reads ReplayGain tags from the audio file at the given path.
     * Returns null if the file can't be read or no RG tags are found.
     * Uses both positive and negative caching keyed by file modification timestamp.
     */
    fun readReplayGain(filePath: String): ReplayGainValues? {
        if (filePath.isBlank()) return null

        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return null
        val currentModified = file.lastModified()

        // Return cached entry (including cached negative null hit) when modification time matches.
        val cachedEntry = synchronized(cache) {
            val entry = cache[filePath]
            if (entry != null && entry.lastModifiedMs == currentModified) {
                entry
            } else {
                if (entry != null) cache.remove(filePath)
                null
            }
        }
        if (cachedEntry != null) {
            Timber.tag(TAG).d("Cache hit (hasTags=%s) for %s", cachedEntry.values != null, file.name)
            return cachedEntry.values
        }

        return try {
            val propertyMap = propertyMapReader(file)
            if (propertyMap == null) {
                synchronized(cache) {
                    cache[filePath] = CacheEntry(values = null, lastModifiedMs = currentModified)
                }
                return null
            }

            val trackGain = extractGainValue(propertyMap, ReplayGainTagParser.TRACK_GAIN_KEYS)
            val albumGain = extractGainValue(propertyMap, ReplayGainTagParser.ALBUM_GAIN_KEYS)

            if (trackGain == null && albumGain == null) {
                synchronized(cache) {
                    cache[filePath] = CacheEntry(values = null, lastModifiedMs = currentModified)
                }
                return null
            }

            ReplayGainValues(trackGainDb = trackGain, albumGainDb = albumGain).also { values ->
                Timber.tag(TAG).d("ReplayGain for ${file.name}: track=${values.trackGainDb}dB, album=${values.albumGainDb}dB")
                synchronized(cache) {
                    cache[filePath] = CacheEntry(values = values, lastModifiedMs = currentModified)
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to read ReplayGain from: $filePath")
            null
        }
    }

    /**
     * Converts a dB gain value to a linear volume multiplier, clamped to `[0.0, 1.0]`.
     * Negative gain values reduce volume (which is the common case for loud tracks).
     * Positive gain values cannot exceed `1.0` on [androidx.media3.common.Player.setVolume]
     * without external amplification, so clamping here keeps `expectedVolume` and crossfade
     * headroom consistent and avoids clipping.
     */
    fun gainDbToVolume(gainDb: Float, preAmpDb: Float = DEFAULT_PRE_AMP_DB): Float {
        if (!gainDb.isFinite() || !preAmpDb.isFinite()) return 1f
        val totalGainDb = gainDb + preAmpDb
        val linear = 10f.pow(totalGainDb / 20f)
        return if (linear.isFinite()) linear.coerceIn(0f, 1f) else 1f
    }

    /**
     * Returns the volume multiplier for a track given its ReplayGain values and the selected mode.
     * Falls back to: track -> album -> 1.0 (no adjustment)
     */
    fun getVolumeMultiplier(
        values: ReplayGainValues?,
        useAlbumGain: Boolean = false,
        preAmpDb: Float = DEFAULT_PRE_AMP_DB
    ): Float {
        if (values == null) return 1f

        val gainDb = if (useAlbumGain) {
            values.albumGainDb ?: values.trackGainDb
        } else {
            values.trackGainDb ?: values.albumGainDb
        }

        return if (gainDb != null) {
            gainDbToVolume(gainDb, preAmpDb)
        } else {
            1f
        }
    }

    private fun extractGainValue(propertyMap: Map<String, Array<String>>, keys: List<String>): Float? =
        ReplayGainTagParser.extractGainDb(propertyMap, keys)
}
