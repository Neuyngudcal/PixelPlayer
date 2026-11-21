package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Trace
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.createBitmap
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.theveloper.pixelplay.data.database.AlbumArtThemeDao
import com.theveloper.pixelplay.data.database.AlbumArtThemeEntity
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import com.theveloper.pixelplay.presentation.components.albumArtMemoryCacheKey
import com.theveloper.pixelplay.ui.theme.GOOGLE_EXTRACTION_MAX_DIMENSION
import com.theveloper.pixelplay.ui.theme.extractSeedColorArgb
import com.theveloper.pixelplay.ui.theme.generateColorSchemeFromSeed
import com.theveloper.pixelplay.ui.theme.quantizeContrastLevel
import com.theveloper.pixelplay.utils.LocalArtworkUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Two-tier Google Material Color Utilities processor for album and artist artwork.
 *
 * - **Tier 1 (Seed Cache — style & contrast independent):**
 *   In-memory LRU (`uri -> seedArgb`) backed by Room `album_art_themes` (3 columns).
 *   Concurrent requests for the same URI coalesce onto a single bitmap decode + Celebi quantization.
 * - **Tier 2 (Scheme Cache — fast RAM LRU):**
 *   In-memory LRU (`uri|style|contrast -> ColorSchemePair`). Generating a 48-role light+dark
 *   [ColorSchemePair] from a cached seed takes < 0.15ms, so changing palette style or system
 *   contrast never re-decodes bitmaps.
 */
@Singleton
class ColorSchemeProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val albumArtThemeDao: AlbumArtThemeDao
) {
    private val seedMemoryCache = SynchronizedLruCache<String, Int>(512)
    private val schemeMemoryCache = SynchronizedLruCache<String, ColorSchemePair>(256)
    private val seedExtractionMutex = Mutex()
    private val extractionSemaphore = Semaphore(permits = 2)
    private val inFlightSeedRequests = mutableMapOf<String, CompletableDeferred<Int?>>()

    val requestChannel = Channel<String>(capacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * Pure O(1) RAM lookup for the fully-synthesized [ColorSchemePair].
     * Never synthesizes on the calling thread, making it safe for scrolling item composition.
     */
    fun getCachedSchemeOnlyFromMemory(
        albumArtUri: String,
        paletteStyle: AlbumArtPaletteStyle,
        contrastLevel: Double = 0.0
    ): ColorSchemePair? {
        if (albumArtUri.isBlank()) return null
        val schemeKey = buildSchemeCacheKey(albumArtUri, paletteStyle, contrastLevel)
        return schemeMemoryCache.get(schemeKey)
    }

    /**
     * Fast synchronous RAM lookup for hot paths (preloaded track skips, carousel swipes).
     */
    fun getCachedColorSchemeFromMemory(
        albumArtUri: String,
        paletteStyle: AlbumArtPaletteStyle,
        contrastLevel: Double = 0.0
    ): ColorSchemePair? {
        val schemeKey = buildSchemeCacheKey(albumArtUri, paletteStyle, contrastLevel)
        schemeMemoryCache.get(schemeKey)?.let { return it }

        // If seed is already resident in RAM, synthesize the 48-role scheme immediately (< 0.15ms).
        val cachedSeed = seedMemoryCache.get(albumArtUri) ?: return null
        return generateColorSchemeFromSeed(
            seedColor = Color(cachedSeed),
            paletteStyle = paletteStyle,
            contrastLevel = contrastLevel
        ).also { pair ->
            schemeMemoryCache.put(schemeKey, pair)
        }
    }

    /**
     * Returns the extracted Google MCU seed color ARGB for [albumArtUri], checking RAM LRU,
     * coalescing in-flight work, checking Room seed cache, and finally decoding & quantizing.
     */
    suspend fun getOrExtractSeedColor(
        albumArtUri: String,
        forceRefresh: Boolean = false,
        persistToDatabase: Boolean = true
    ): Int? {
        if (albumArtUri.isBlank()) return null

        Trace.beginSection("ColorSchemeProcessor.getOrExtractSeed")
        try {
            if (!forceRefresh) {
                seedMemoryCache.get(albumArtUri)?.let { return it }
            }

            if (forceRefresh) {
                return extractAndCacheSeedColor(
                    albumArtUri = albumArtUri,
                    forceRefresh = true,
                    persistToDatabase = persistToDatabase
                )
            }

            val (deferred, isOwner) = seedExtractionMutex.withLock {
                val existing = inFlightSeedRequests[albumArtUri]
                if (existing != null) {
                    existing to false
                } else {
                    CompletableDeferred<Int?>().also { created ->
                        inFlightSeedRequests[albumArtUri] = created
                    } to true
                }
            }

            if (!isOwner) {
                return deferred.await()
            }

            return try {
                val seed = loadCachedSeedColor(albumArtUri)
                    ?: extractAndCacheSeedColor(
                        albumArtUri = albumArtUri,
                        forceRefresh = false,
                        persistToDatabase = persistToDatabase
                    )
                deferred.complete(seed)
                seed
            } catch (t: Throwable) {
                deferred.complete(null)
                throw t
            } finally {
                seedExtractionMutex.withLock {
                    if (inFlightSeedRequests[albumArtUri] === deferred) {
                        inFlightSeedRequests.remove(albumArtUri)
                    }
                }
            }
        } finally {
            Trace.endSection()
        }
    }

    /**
     * Gets or generates a 48-role light+dark [ColorSchemePair] for [albumArtUri] using Google's
     * Material Color Utilities pipeline and the requested [paletteStyle] & [contrastLevel].
     */
    suspend fun getOrGenerateColorScheme(
        albumArtUri: String,
        paletteStyle: AlbumArtPaletteStyle,
        contrastLevel: Double = 0.0,
        forceRefresh: Boolean = false
    ): ColorSchemePair? {
        if (albumArtUri.isBlank()) return null

        Trace.beginSection("ColorSchemeProcessor.getOrGenerate")
        try {
            val schemeKey = buildSchemeCacheKey(albumArtUri, paletteStyle, contrastLevel)
            if (!forceRefresh) {
                schemeMemoryCache.get(schemeKey)?.let { return it }
            }

            val seedArgb = getOrExtractSeedColor(
                albumArtUri = albumArtUri,
                forceRefresh = forceRefresh,
                persistToDatabase = true
            ) ?: return null

            val schemePair = withContext(Dispatchers.Default) {
                generateColorSchemeFromSeed(
                    seedColor = Color(seedArgb),
                    paletteStyle = paletteStyle,
                    contrastLevel = contrastLevel
                )
            }
            schemeMemoryCache.put(schemeKey, schemePair)
            return schemePair
        } finally {
            Trace.endSection()
        }
    }

    /**
     * Hydrates dynamic [ColorSchemePair]s for a batch of visible/upcoming artwork URIs
     * (e.g. after scroll settles), using RAM cache -> batch Room seed query -> semaphore-bounded
     * bitmap extraction with cooperative cancellation.
     */
    suspend fun getOrGenerateColorSchemesBatch(
        albumArtUris: List<String>,
        paletteStyle: AlbumArtPaletteStyle,
        contrastLevel: Double = 0.0,
        onSchemeReady: (String, ColorSchemePair) -> Unit
    ) {
        val uniqueUris = albumArtUris.asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        if (uniqueUris.isEmpty()) return

        val missingSchemes = mutableListOf<String>()
        for (uri in uniqueUris) {
            val cachedScheme = getCachedSchemeOnlyFromMemory(uri, paletteStyle, contrastLevel)
            if (cachedScheme != null) {
                onSchemeReady(uri, cachedScheme)
            } else {
                missingSchemes.add(uri)
            }
        }
        if (missingSchemes.isEmpty()) return

        val seedHits = mutableListOf<Pair<String, Int>>()
        val missingSeeds = mutableListOf<String>()
        for (uri in missingSchemes) {
            val seed = seedMemoryCache.get(uri)
            if (seed != null) {
                seedHits.add(uri to seed)
            } else {
                missingSeeds.add(uri)
            }
        }

        if (seedHits.isNotEmpty()) {
            val generatedPairs = withContext(Dispatchers.Default) {
                seedHits.map { (uri, seed) ->
                    uri to generateColorSchemeFromSeed(
                        seedColor = Color(seed),
                        paletteStyle = paletteStyle,
                        contrastLevel = contrastLevel
                    )
                }
            }
            generatedPairs.forEach { (uri, pair) ->
                schemeMemoryCache.put(buildSchemeCacheKey(uri, paletteStyle, contrastLevel), pair)
                onSchemeReady(uri, pair)
            }
        }
        if (missingSeeds.isEmpty()) return

        currentCoroutineContext().ensureActive()

        val dbEntities = withContext(Dispatchers.IO) {
            missingSeeds.chunked(200).flatMap { chunk ->
                albumArtThemeDao.getSeedsByUris(chunk, SEED_ALGORITHM_VERSION)
            }
        }
        if (dbEntities.isNotEmpty()) {
            dbEntities.forEach { entity ->
                seedMemoryCache.put(entity.albumArtUriString, entity.seedColorArgb)
            }
            val dbPairs = withContext(Dispatchers.Default) {
                dbEntities.map { entity ->
                    entity.albumArtUriString to generateColorSchemeFromSeed(
                        seedColor = Color(entity.seedColorArgb),
                        paletteStyle = paletteStyle,
                        contrastLevel = contrastLevel
                    )
                }
            }
            dbPairs.forEach { (uri, pair) ->
                schemeMemoryCache.put(buildSchemeCacheKey(uri, paletteStyle, contrastLevel), pair)
                onSchemeReady(uri, pair)
            }
        }

        val dbUriSet = dbEntities.mapTo(HashSet(dbEntities.size)) { it.albumArtUriString }
        val uncachedUris = missingSeeds.filter { it !in dbUriSet }
        for (uri in uncachedUris) {
            currentCoroutineContext().ensureActive()
            val pair = extractionSemaphore.withPermit {
                getOrGenerateColorScheme(
                    albumArtUri = uri,
                    paletteStyle = paletteStyle,
                    contrastLevel = contrastLevel
                )
            }
            if (pair != null) {
                onSchemeReady(uri, pair)
            }
        }
    }

    /**
     * Fast preview generator for settings screens: reuses the cached seed without writing new
     * uncached preview rows if the caller only needs an ephemeral preview.
     */
    suspend fun getPreviewColorScheme(
        albumArtUri: String,
        paletteStyle: AlbumArtPaletteStyle,
        contrastLevel: Double = 0.0
    ): ColorSchemePair? {
        if (albumArtUri.isBlank()) return null

        val schemeKey = buildSchemeCacheKey(albumArtUri, paletteStyle, contrastLevel)
        schemeMemoryCache.get(schemeKey)?.let { return it }

        val seedArgb = getOrExtractSeedColor(
            albumArtUri = albumArtUri,
            forceRefresh = false,
            persistToDatabase = false
        ) ?: return null

        return withContext(Dispatchers.Default) {
            generateColorSchemeFromSeed(
                seedColor = Color(seedArgb),
                paletteStyle = paletteStyle,
                contrastLevel = contrastLevel
            )
        }.also { pair ->
            schemeMemoryCache.put(schemeKey, pair)
        }
    }

    private data class ExtractedBitmapHolder(
        val bitmap: Bitmap,
        val ownsBitmap: Boolean
    )

    private suspend fun loadCachedSeedColor(albumArtUri: String): Int? {
        seedMemoryCache.get(albumArtUri)?.let { return it }

        val persistedSeed = withContext(Dispatchers.IO) {
            albumArtThemeDao.getSeedByUri(albumArtUri, SEED_ALGORITHM_VERSION)
        } ?: return null

        seedMemoryCache.put(albumArtUri, persistedSeed)
        return persistedSeed
    }

    private suspend fun extractAndCacheSeedColor(
        albumArtUri: String,
        forceRefresh: Boolean,
        persistToDatabase: Boolean
    ): Int? {
        Trace.beginSection("ColorSchemeProcessor.extractSeed")
        try {
            val bitmapHolder = withContext(Dispatchers.IO) {
                loadBitmapForColorExtraction(albumArtUri, skipCache = forceRefresh)
            } ?: return null

            val seedArgb = withContext(Dispatchers.Default) {
                try {
                    extractSeedColorArgb(bitmapHolder.bitmap)
                } finally {
                    // Only recycle temporary rasterized bitmaps; never recycle Coil MemoryCache bitmaps.
                    if (bitmapHolder.ownsBitmap && !bitmapHolder.bitmap.isRecycled) {
                        bitmapHolder.bitmap.recycle()
                    }
                }
            }

            if (forceRefresh) {
                removeUriFromSchemeCache(albumArtUri)
            }
            seedMemoryCache.put(albumArtUri, seedArgb)

            if (persistToDatabase) {
                withContext(Dispatchers.IO) {
                    albumArtThemeDao.insertSeed(
                        AlbumArtThemeEntity(
                            albumArtUriString = albumArtUri,
                            seedColorArgb = seedArgb,
                            algorithmVersion = SEED_ALGORITHM_VERSION
                        )
                    )
                }
            }

            return seedArgb
        } catch (_: Exception) {
            return null
        } finally {
            Trace.endSection()
        }
    }

    /**
     * Loads a compact 112x112 software bitmap matching Android Framework `WallpaperColors`
     * extraction bounds. Reuses Coil's decoded BitmapDrawable directly when readable.
     */
    private suspend fun loadBitmapForColorExtraction(uri: String, skipCache: Boolean): ExtractedBitmapHolder? {
        return try {
            if (!skipCache) {
                val memoryCache = context.imageLoader.memoryCache
                if (memoryCache != null) {
                    val candidateKeys = listOfNotNull(
                        albumArtMemoryCacheKey(uri, Size(256, 256)),
                        albumArtMemoryCacheKey(uri, Size(300, 300)),
                        albumArtMemoryCacheKey(uri, Size(512, 512)),
                        uri
                    )
                    for (cacheKey in candidateKeys) {
                        val cachedBitmap = memoryCache[MemoryCache.Key(cacheKey)]?.bitmap
                        if (cachedBitmap != null && !cachedBitmap.isRecycled && cachedBitmap.width > 0 && cachedBitmap.height > 0) {
                            return ExtractedBitmapHolder(bitmap = cachedBitmap, ownsBitmap = false)
                        }
                    }
                }
            }

            val cachePolicy = if (skipCache) CachePolicy.DISABLED else CachePolicy.ENABLED
            val diskCachePolicy = if (LocalArtworkUri.isLocalArtworkUri(uri)) CachePolicy.DISABLED else cachePolicy

            val request = ImageRequest.Builder(context)
                .data(uri)
                .allowHardware(false)
                .size(Size(GOOGLE_EXTRACTION_MAX_DIMENSION, GOOGLE_EXTRACTION_MAX_DIMENSION))
                .bitmapConfig(Bitmap.Config.ARGB_8888)
                .memoryCachePolicy(cachePolicy)
                .diskCachePolicy(diskCachePolicy)
                .build()

            val drawable = context.imageLoader.execute(request).drawable ?: return null
            if (drawable is BitmapDrawable) {
                val existingBitmap = drawable.bitmap
                if (existingBitmap != null && !existingBitmap.isRecycled && existingBitmap.config != Bitmap.Config.HARDWARE) {
                    return ExtractedBitmapHolder(bitmap = existingBitmap, ownsBitmap = false)
                }
            }

            val rasterized = createBitmap(
                drawable.intrinsicWidth.coerceIn(1, GOOGLE_EXTRACTION_MAX_DIMENSION),
                drawable.intrinsicHeight.coerceIn(1, GOOGLE_EXTRACTION_MAX_DIMENSION)
            ).also { bmp ->
                Canvas(bmp).let { canvas ->
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                }
            }
            ExtractedBitmapHolder(bitmap = rasterized, ownsBitmap = true)
        } catch (_: Exception) {
            null
        }
    }

    fun clearMemoryCache() {
        schemeMemoryCache.evictAll()
        seedMemoryCache.evictAll()
    }

    fun evictFromCache(uri: String) {
        seedMemoryCache.remove(uri)
        removeUriFromSchemeCache(uri)
    }

    suspend fun invalidateScheme(uri: String) {
        evictFromCache(uri)
        withContext(Dispatchers.IO) {
            albumArtThemeDao.deleteThemesByUris(listOf(uri))
        }
    }

    private fun removeUriFromSchemeCache(uri: String) {
        val prefix = "$uri$CACHE_KEY_SEPARATOR"
        schemeMemoryCache.keysSnapshot()
            .filter { key -> key == uri || key.startsWith(prefix) }
            .forEach { key -> schemeMemoryCache.remove(key) }
    }

    private class SynchronizedLruCache<K, V>(
        private val maxSize: Int
    ) {
        private val lock = Any()
        private val map = object : LinkedHashMap<K, V>(maxSize + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
                return size > maxSize
            }
        }

        fun get(key: K): V? = synchronized(lock) { map[key] }

        fun put(key: K, value: V) {
            synchronized(lock) {
                map[key] = value
            }
        }

        fun remove(key: K): V? = synchronized(lock) { map.remove(key) }

        fun evictAll() {
            synchronized(lock) {
                map.clear()
            }
        }

        fun keysSnapshot(): List<K> = synchronized(lock) { map.keys.toList() }
    }

    private fun buildSchemeCacheKey(
        uri: String,
        paletteStyle: AlbumArtPaletteStyle,
        contrastLevel: Double
    ): String {
        val contrastBucket = quantizeContrastLevel(contrastLevel)
        return "$uri$CACHE_KEY_SEPARATOR${paletteStyle.storageKey}$CACHE_KEY_SEPARATOR$contrastBucket"
    }

    companion object {
        private const val CACHE_KEY_SEPARATOR = "|"
        internal const val SEED_ALGORITHM_VERSION = 1
    }
}
