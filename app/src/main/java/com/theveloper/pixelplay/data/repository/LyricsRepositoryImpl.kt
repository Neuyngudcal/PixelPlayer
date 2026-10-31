package com.theveloper.pixelplay.data.repository

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.util.LruCache
import androidx.core.net.toUri
import com.google.gson.Gson
import com.kyant.taglib.TagLib
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.network.lyrics.LrcLibApiService
import com.theveloper.pixelplay.data.network.lyrics.LrcLibResponse
import com.theveloper.pixelplay.data.network.lyrics.LyricsProviderPreferences
import com.theveloper.pixelplay.data.network.lyrics.LyricsProviderType
import com.theveloper.pixelplay.data.network.lyrics.MornyeLyricsProviderChain
import com.theveloper.pixelplay.utils.LyricsImportSecurity
import com.theveloper.pixelplay.utils.LyricsImportValidationResult
import com.theveloper.pixelplay.utils.LogUtils
import com.theveloper.pixelplay.utils.LyricsUtils
import com.theveloper.pixelplay.utils.MultiLangRomanizer
import com.theveloper.pixelplay.utils.NetworkRetryUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import okhttp3.OkHttpClient
import okhttp3.Request

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private val EMBEDDED_LYRICS_KEYS = listOf("LYRICS", "SYNCEDLYRICS", "TTML", "UNSYNCEDLYRICS")

private fun Lyrics.isValid(): Boolean = !synced.isNullOrEmpty() || !plain.isNullOrEmpty()

internal fun parseBestEmbeddedLyricsField(propertyMap: Map<String, Array<String>>?): Lyrics? {
    var firstPlainLyrics: Lyrics? = null

    EMBEDDED_LYRICS_KEYS.forEach { key ->
        propertyMap?.get(key).orEmpty().forEach { field ->
            if (field.isBlank()) return@forEach

            val parsedLyrics = LyricsUtils.parseLyrics(field)
            if (!parsedLyrics.isValid()) return@forEach

            val localLyrics = parsedLyrics.copy(areFromRemote = false)
            if (!localLyrics.synced.isNullOrEmpty()) return localLyrics
            if (firstPlainLyrics == null) firstPlainLyrics = localLyrics
        }
    }

    return firstPlainLyrics
}

/**
 * LyricsData for JSON disk cache (matches Rhythm's format)
 */
private data class LyricsData(
    val plainLyrics: String?,
    val syncedLyrics: String?,
    val wordByWordLyrics: String? = null
) {
    fun hasLyrics(): Boolean =
        !plainLyrics.isNullOrBlank() ||
            !syncedLyrics.isNullOrBlank() ||
            !wordByWordLyrics.isNullOrBlank()
}

private data class RemoteSearchStrategy(
    val name: String,
    val request: suspend () -> Array<LrcLibResponse>?
)

private data class RemoteSearchBatch(
    val strategyName: String,
    val responses: List<LrcLibResponse>
)

private enum class RemoteLyricsMatchMode {
    AUTOMATIC,
    CANDIDATE
}

private data class RemoteLyricsMatch(
    val response: LrcLibResponse,
    val score: Int
)

@Singleton
class LyricsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lrcLibApiService: LrcLibApiService,
    private val lyricsDao: com.theveloper.pixelplay.data.database.LyricsDao,
    private val okHttpClient: OkHttpClient,
    private val neteaseRepository: dagger.Lazy<com.theveloper.pixelplay.data.netease.NeteaseRepository>? = null,
    private val jellyfinRepository: dagger.Lazy<com.theveloper.pixelplay.data.jellyfin.JellyfinRepository>? = null,
    private val navidromeRepository: dagger.Lazy<com.theveloper.pixelplay.data.navidrome.NavidromeRepository>? = null
) : LyricsRepository {


    companion object {
        private const val TAG = "LyricsRepository"
        
        // Cache sizes (matching Rhythm)
        private const val MAX_LYRICS_CACHE_SIZE = 150
        
        // API rate limiting constants (matching Rhythm)
        private const val LRCLIB_MIN_DELAY = 100L
        private const val MAX_CALLS_PER_MINUTE = 30
        private const val AMLLDB_NCM_LYRICS_BASE_URL = "https://amlldb.bikonoo.com/lyrics/ncm-lyrics/"
        private const val NETWORK_RETRY_ATTEMPTS = 3
        private const val NETWORK_RETRY_INITIAL_DELAY_MS = 500L

        private val BRACKETED_QUALIFIER_REGEX = Regex("""[\(\[\{\uFF08\uFF3B\uFF5B\u3010\u300E\u300C\u3014\u3008\u300A]([^)\]\}\uFF09\uFF3D\uFF5D\u3011\u300F\u300D\u3015\u3009\u300B]*)[\)\]\}\uFF09\uFF3D\uFF5D\u3011\u300F\u300D\u3015\u3009\u300B]""")
        private val FEATURE_QUALIFIER_REGEX = Regex("""\b(feat(?:uring)?|ft)\.?\b""", RegexOption.IGNORE_CASE)
        private val TITLE_SEPARATOR_REGEX = Regex("""\s*[-\u2013\u2014:\uFF0D\u00B7\u30FB]\s*""")
        private val TIMING_VARIANT_KEYWORDS = setOf(
            "remix",
            "mix",
            "mashup",
            "bootleg",
            "edit",
            "extended",
            "radio",
            "club",
            "vip",
            "dub",
            "live",
            "acoustic",
            "unplugged",
            "sped",
            "slowed",
            "nightcore",
            "instrumental",
            "karaoke",
            "cover",
            "demo",
            "version",
            "rework",
            "flip",
            "refix",
            "opening",
            "ending",
            "op",
            "ed",
            "theme",
            "tv",
            "size",
            "ver",
            "full",
            "movie",
            "ost",
            "soundtrack",
            "background",
            "bgm",
            "short",
            "long",
            "reprise",
            "intro",
            "outro",
            "medley",
            "bonus"
        )
        private val TITLE_DROP_QUALIFIERS = setOf(
            "explicit",
            "clean",
            "mono",
            "stereo",
            "official audio",
            "official video",
            "official music video",
            "official lyric video",
            "lyric video",
            "lyrics video",
            "lyrics",
            "mv",
            "audio",
            "video",
            "hd",
            "hq",
            "hi-res",
            "high-res",
            "mqa"
        )
        private val UNKNOWN_ARTISTS = setOf(
            "",
            "<unknown>",
            "unknown",
            "unknown artist",
            "various artists",
            "various",
            "nhaccuatui",
            "zing mp3",
            "zingmp3",
            "chiasenhac",
            "youtube",
            "soundcloud",
            "spotify",
            "tiktok",
            "keeng",
            "nhacvn"
        )
        private val ARTIST_CONNECTOR_TOKENS = setOf(
            "feat",
            "featuring",
            "ft",
            "and",
            "with",
            "x",
            "vs",
            "the",
            "prod"
        )
        private val SPACED_HYPHEN_SPLIT_REGEX = Regex("""\s+[-\u2013\u2014\uFF0D]\s+""")
        private val LRC_TI_HEADER_REGEX = Regex("""(?im)^\s*\[ti:([^\]]+)\]""")
        private val LRC_AR_HEADER_REGEX = Regex("""(?im)^\s*\[ar:([^\]]+)\]""")
    }

    // Repository scope for background tasks
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Thread-safe LRU cache to avoid race conditions across concurrent lyrics requests.
    private val lyricsCache = LruCache<String, Lyrics>(MAX_LYRICS_CACHE_SIZE)

    // Thread-safe rate limiting state.
    private val lastApiCalls = ConcurrentHashMap<String, Long>()
    private val apiCallCounts = ConcurrentHashMap<String, RateLimitWindow>()

    // Gson for JSON cache
    private val gson = Gson()

    // Mornye reorderable multi-provider lyrics chain (Zarz-LRC, Apple Music, lrc.red, LyricsPlus, BetterLyrics, LRCLIB, NetEase, Kugou, QQ Music)
    private val mornyeProviderChain by lazy { MornyeLyricsProviderChain(okHttpClient) }

    /**
     * Executes multiple remote search strategies in parallel and returns the first non-empty result set.
     * This keeps search responsive while preserving the existing "first useful strategy wins" behavior.
     */
    private suspend fun runSearchStrategiesFast(
        strategies: List<RemoteSearchStrategy>
    ): List<LrcLibResponse> = coroutineScope {
        if (strategies.isEmpty()) return@coroutineScope emptyList()

        val channel = Channel<RemoteSearchBatch>(capacity = strategies.size)
        val jobs = strategies.map { strategy ->
            launch {
                val responses = runCatching {
                    withNetworkRetry(operationName = "lrclib_strategy:${strategy.name}") {
                        strategy.request()
                    }
                }.getOrElse { error ->
                    Log.d(
                        TAG,
                        "Strategy ${strategy.name} failed after retries: ${error.message}"
                    )
                    null
                }
                    ?.toList()
                    .orEmpty()
                channel.trySend(
                    RemoteSearchBatch(
                        strategyName = strategy.name,
                        responses = responses
                    )
                )
            }
        }

        repeat(strategies.size) {
            val batch = channel.receive()
            if (batch.responses.isNotEmpty()) {
                Log.d(TAG, "Fast search hit from strategy: ${batch.strategyName} (${batch.responses.size} results)")
                jobs.forEach { it.cancel() }
                channel.close()
                return@coroutineScope batch.responses.distinctBy { it.id }
            }
        }

        channel.close()
        emptyList()
    }

    /**
     * Executes a group of search strategies in parallel and merges all non-empty responses
     * without letting a fast unspecific response cancel a slightly slower artist-qualified response.
     */
    private suspend fun runSearchStrategiesMerged(
        strategies: List<RemoteSearchStrategy>
    ): List<LrcLibResponse> = coroutineScope {
        if (strategies.isEmpty()) return@coroutineScope emptyList()

        val batches = strategies.map { strategy ->
            async {
                runCatching {
                    withNetworkRetry(operationName = "lrclib_strategy:${strategy.name}") {
                        strategy.request()
                    }
                }.getOrElse { error ->
                    Log.d(
                        TAG,
                        "Strategy ${strategy.name} failed after retries: ${error.message}"
                    )
                    null
                }
                    ?.toList()
                    .orEmpty()
            }
        }.awaitAll()

        batches.flatten().distinctBy { it.id }
    }

    private suspend fun <T> withNetworkRetry(
        operationName: String,
        maxAttempts: Int = NETWORK_RETRY_ATTEMPTS,
        initialDelayMs: Long = NETWORK_RETRY_INITIAL_DELAY_MS,
        shouldRetry: (Throwable) -> Boolean = { it is IOException || (it is HttpException && (it.code() == 429 || it.code() >= 500)) },
        block: suspend () -> T
    ): T {
        return NetworkRetryUtils.withNetworkRetry(
            operationName = operationName,
            maxAttempts = maxAttempts,
            initialDelayMs = initialDelayMs,
            shouldRetry = shouldRetry,
            onRetry = { attempt, attempts, throwable ->
                Log.d(
                    TAG,
                    "Retrying $operationName after failure ($attempt/$attempts): ${throwable.message}"
                )
            },
            block = block
        )
    }

    private fun Int.isRetryableHttpStatusCode(): Boolean {
        return this == 429 || this in 500..599
    }

    /**
     * Calculate delay needed before next API call (matching Rhythm)
     */
    private data class RateLimitWindow(
        val windowStartMillis: Long,
        val count: Int
    )

    private fun calculateApiDelay(apiName: String, currentTime: Long): Long {
        val lastCall = lastApiCalls[apiName] ?: 0L
        val minDelay = when (apiName.lowercase()) {
            "lrclib" -> LRCLIB_MIN_DELAY
            else -> 250L
        }

        val timeSinceLastCall = currentTime - lastCall
        if (timeSinceLastCall < minDelay) {
            return minDelay - timeSinceLastCall
        }

        // Check if we're making too many calls in the current fixed 60s window
        val window = apiCallCounts[apiName]
        val callsInLastMinute = if (window != null && (currentTime - window.windowStartMillis) < 60000L) {
            window.count
        } else {
            0
        }
        if (callsInLastMinute >= MAX_CALLS_PER_MINUTE) {
            // Exponential backoff
            return minDelay * 2
        }

        return 0L
    }

    /**
     * Update last API call timestamp (matching Rhythm)
     */
    private fun updateLastApiCall(apiName: String, timestamp: Long) {
        lastApiCalls[apiName] = timestamp

        val currentWindow = apiCallCounts[apiName]
        val updatedWindow = if (currentWindow == null || (timestamp - currentWindow.windowStartMillis) >= 60000L) {
            RateLimitWindow(
                windowStartMillis = timestamp,
                count = 1
            )
        } else {
            currentWindow.copy(count = currentWindow.count + 1)
        }
        apiCallCounts[apiName] = updatedWindow
    }

    /**
     * Main lyrics fetching method with source preference support (matching Rhythm)
     */
    override suspend fun getLyrics(
        song: Song,
        sourcePreference: LyricsSourcePreference,
        forceRefresh: Boolean
    ): Lyrics? = withContext(Dispatchers.IO) {
        val cacheKey = generateCacheKey(song.id)
        val isNeteaseTrack = isNeteaseSong(song)
        
        Log.d(TAG, "===== FETCH LYRICS START: ${song.displayArtist} - ${song.title} (forceRefresh=$forceRefresh, source=$sourcePreference) =====")

        // Check in-memory cache unless force refresh (early return - matching Rhythm)
        if (!forceRefresh && !isNeteaseTrack) {
            lyricsCache.get(cacheKey)?.let { cached ->
                Log.d(TAG, "===== RETURNING IN-MEMORY CACHED LYRICS =====")
                return@withContext cached
            }
            Log.d(TAG, "===== NO IN-MEMORY CACHE HIT, proceeding to fetch =====")
        } else if (!forceRefresh && isNeteaseTrack) {
            Log.d(TAG, "===== BYPASSING IN-MEMORY CACHE FOR NETEASE TRACK =====")
        } else {
            Log.d(TAG, "===== FORCE REFRESH - BYPASSING IN-MEMORY CACHE =====")
        }

        if (!forceRefresh) {
            // Always honor explicit song.lyrics or manually assigned lyrics immediately.
            // For auto-cached sources ("remote", "local_file"), only short-circuit when they match
            // the primary source in sourcePreference so switching sourcePreference takes effect.
            val manualOrMatchingStored = loadStoredLyrics(
                song = song,
                cacheKey = cacheKey,
                includeMemoryCache = false,
                onlyManualOrMatchingPreference = sourcePreference
            )
            if (manualOrMatchingStored != null) {
                lyricsCache.put(cacheKey, manualOrMatchingStored.first)
                Log.d(TAG, "===== RETURNING STORED LYRICS WITHOUT REMOTE FETCH =====")
                return@withContext manualOrMatchingStored.first
            }
        }

        // Define source fetchers (matching Rhythm pattern)
        val fetchFromLocal: suspend () -> Lyrics? = {
            findLocalLyricsFile(song)
        }

        val fetchFromEmbedded: suspend () -> Lyrics? = {
            loadEmbeddedLyricsFromMetadata(song)
        }

        val fetchFromAPI: suspend () -> Lyrics? = {
            fetchLyricsFromAPI(song)
        }

        // Try sources in order based on preference, with fallback (matching Rhythm)
        val sourceFetchers = when (sourcePreference) {
            LyricsSourcePreference.API_FIRST -> listOf(fetchFromAPI, fetchFromEmbedded, fetchFromLocal)
            LyricsSourcePreference.EMBEDDED_FIRST -> listOf(fetchFromEmbedded, fetchFromAPI, fetchFromLocal)
            LyricsSourcePreference.LOCAL_FIRST -> listOf(fetchFromLocal, fetchFromEmbedded, fetchFromAPI)
        }

        // Try each source in order until we find lyrics (early return on success)
        for ((index, fetcher) in sourceFetchers.withIndex()) {
            try {
                val lyrics = fetcher()
                if (lyrics != null && lyrics.isValid()) {
                    val sourceName = when (index) {
                        0 -> when (sourcePreference) {
                            LyricsSourcePreference.API_FIRST -> "API"
                            LyricsSourcePreference.EMBEDDED_FIRST -> "Embedded"
                            LyricsSourcePreference.LOCAL_FIRST -> "Local"
                        }
                        1 -> when (sourcePreference) {
                            LyricsSourcePreference.API_FIRST -> "Embedded"
                            LyricsSourcePreference.EMBEDDED_FIRST -> "API"
                            LyricsSourcePreference.LOCAL_FIRST -> "Embedded"
                        }
                        else -> when (sourcePreference) {
                            LyricsSourcePreference.API_FIRST -> "Local"
                            LyricsSourcePreference.EMBEDDED_FIRST -> "Local"
                            LyricsSourcePreference.LOCAL_FIRST -> "API"
                        }
                    }
                    Log.d(TAG, "Found lyrics from $sourceName for: ${song.displayArtist} - ${song.title}")
                    
                    // Cache the result
                    lyricsCache.put(cacheKey, lyrics)
                    
                    // Save to JSON disk cache if from API
                    if (sourceName == "API") {
                        saveLocalLyricsJson(song, lyrics)
                    }
                    
                    return@withContext lyrics
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching from source ${index + 1}: ${e.message}")
                // Continue to next source
            }
        }

        // No lyrics found from any source
        Log.d(TAG, "No lyrics found from any source for: ${song.displayArtist} - ${song.title}")
        return@withContext null
    }

    override suspend fun getStoredLyrics(song: Song): Pair<Lyrics, String>? = withContext(Dispatchers.IO) {
        val cacheKey = generateCacheKey(song.id)
        loadStoredLyrics(song, cacheKey, includeMemoryCache = true)?.also { stored ->
            lyricsCache.put(cacheKey, stored.first)
        }
    }

    private suspend fun fetchCloudNativeLyrics(song: Song): Pair<Lyrics, String>? = withContext(Dispatchers.IO) {
        if (isNeteaseSong(song)) {
            val amlLyrics = fetchFromAmlldb(song)
            if (amlLyrics != null) {
                val raw = lyricsToRawContent(amlLyrics).orEmpty()
                return@withContext amlLyrics to raw
            }
            val neteaseId = resolveNeteaseSongId(song)
            if (neteaseId != null) {
                val rawNetease = runCatching { neteaseRepository?.get()?.getLyrics(neteaseId)?.getOrNull() }.getOrNull()
                if (!rawNetease.isNullOrBlank()) {
                    val parsed = LyricsUtils.parseLyrics(rawNetease).copy(areFromRemote = true, provider = "NetEase")
                    if (parsed.isValid()) {
                        return@withContext parsed to rawNetease
                    }
                }
            }
        }

        val jellyfinId = song.jellyfinId?.takeIf { it.isNotBlank() }
            ?: song.contentUriString.takeIf { it.startsWith("jellyfin://") }?.removePrefix("jellyfin://")?.substringBefore('/')
        if (!jellyfinId.isNullOrBlank()) {
            val rawJellyfin = runCatching { jellyfinRepository?.get()?.getLyrics(jellyfinId)?.getOrNull() }.getOrNull()
            if (!rawJellyfin.isNullOrBlank()) {
                val parsed = LyricsUtils.parseLyrics(rawJellyfin).copy(areFromRemote = true, provider = "Jellyfin")
                if (parsed.isValid()) {
                    return@withContext parsed to rawJellyfin
                }
            }
        }

        val navidromeId = song.navidromeId?.takeIf { it.isNotBlank() }
            ?: song.contentUriString.takeIf { it.startsWith("navidrome://") }?.removePrefix("navidrome://")?.substringBefore('/')
        if (!navidromeId.isNullOrBlank()) {
            val rawNavidrome = runCatching { navidromeRepository?.get()?.getLyrics(navidromeId)?.getOrNull() }.getOrNull()
            if (!rawNavidrome.isNullOrBlank()) {
                val parsed = LyricsUtils.parseLyrics(rawNavidrome).copy(areFromRemote = true, provider = "Navidrome")
                if (parsed.isValid()) {
                    return@withContext parsed to rawNavidrome
                }
            }
        }

        null
    }

    /**
     * Fetches lyrics from the reorderable Mornye provider chain (including Zarz-LRC, Apple Music,
     * lrc.red, LyricsPlus, BetterLyrics, LRCLIB, NetEase, Kugou, QQ Music).
     */
    private suspend fun fetchLyricsFromAPI(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        fetchCloudNativeLyrics(song)?.let { (cloudLyrics, raw) ->
            Log.d(TAG, "===== LOADED CLOUD NATIVE LYRICS (${cloudLyrics.provider}) =====")
            song.id.toLongOrNull()?.let { id ->
                if (raw.isNotBlank()) {
                    runCatching {
                        lyricsDao.insert(
                            com.theveloper.pixelplay.data.database.LyricsEntity(
                                songId = id,
                                content = raw,
                                isSynced = !cloudLyrics.synced.isNullOrEmpty(),
                                source = "remote"
                            )
                        )
                    }
                }
            }
            return@withContext cloudLyrics
        }

        // Check stored remote DB entry or JSON disk cache first
        song.id.toLongOrNull()?.let { numericSongId ->
            val entity = lyricsDao.getLyrics(numericSongId)
            val rawLyrics = entity?.content?.trim()?.takeIf { it.isNotBlank() }
            if (rawLyrics != null && entity.source == "remote") {
                val parsed = parseStoredLyrics(rawLyrics, isRemote = true)
                if (parsed != null && isStoredRemoteLyricsValidForSong(song, parsed, rawLyrics)) {
                    Log.d(TAG, "===== LOADED REMOTE LYRICS FROM DB CACHE =====")
                    return@withContext parsed
                }
            }
        }

        val cachedJson = loadLocalLyricsJson(song)
        if (cachedJson != null) {
            Log.d(TAG, "===== LOADED LYRICS FROM JSON DISK CACHE =====")
            return@withContext cachedJson
        }

        val cleanArtist = song.displayArtist.trim().replace(BRACKETED_QUALIFIER_REGEX, "").trim()
        val cleanTitle = song.title.trim().replace(BRACKETED_QUALIFIER_REGEX, "").trim()

        var lrclibInvoked = false
        val chainHit = runCatching {
            mornyeProviderChain.searchOrderedChain(
                context = context,
                title = cleanTitle.ifEmpty { song.title },
                artist = cleanArtist,
                durationMs = song.duration,
                lrclibFetcher = {
                    lrclibInvoked = true
                    fetchFromLrcLibPair(song)
                }
            )
        }.getOrNull()

        if (chainHit != null && chainHit.parsedLyrics.isValid()) {
            try {
                lyricsDao.insert(
                    com.theveloper.pixelplay.data.database.LyricsEntity(
                        songId = song.id.toLong(),
                        content = chainHit.rawLrc,
                        isSynced = !chainHit.parsedLyrics.synced.isNullOrEmpty(),
                        source = "remote"
                    )
                )
            } catch (e: NumberFormatException) {
                Log.w(TAG, "Skipping database save for non-numeric song ID: ${song.id}")
            }
            return@withContext chainHit.parsedLyrics
        }

        return@withContext if (!lrclibInvoked) {
            fetchFromLrcLibPair(song)?.first
        } else {
            null
        }
    }

    private suspend fun fetchFromLrcLibPair(song: Song): Pair<Lyrics, String>? = withContext(Dispatchers.IO) {
        // Apply rate limiting
        val currentTime = System.currentTimeMillis()
        val delayNeeded = calculateApiDelay("lrclib", currentTime)
        if (delayNeeded > 0) {
            Log.d(TAG, "Rate limiting: waiting ${delayNeeded}ms before API call")
            delay(delayNeeded)
        }
        updateLastApiCall("lrclib", System.currentTimeMillis())

        try {
            val cleanArtist = song.displayArtist.trim().replace(BRACKETED_QUALIFIER_REGEX, "").trim()
            val cleanTitle = song.title.trim().replace(BRACKETED_QUALIFIER_REGEX, "").trim()
            val simplifiedArtist = cleanArtist.split(" feat.", " ft.", " featuring", " & ", " , ").first().trim()
            val simplifiedTitle = cleanTitle.split(" feat.", " ft.", " featuring", " (").first().trim()
            val useSimplifiedStrategy =
                simplifiedArtist != cleanArtist || simplifiedTitle != cleanTitle
            val extractedPairs = extractTitleArtistCandidates(song)

            val primaryStrategies = buildList {
                if (!isUnknownArtist(cleanArtist)) {
                    add(
                        RemoteSearchStrategy("track+artist") {
                            lrcLibApiService.searchLyrics(trackName = cleanTitle, artistName = cleanArtist)
                        }
                    )
                    add(
                        RemoteSearchStrategy("combined_query") {
                            lrcLibApiService.searchLyrics(query = "$cleanArtist $cleanTitle")
                        }
                    )
                    if (useSimplifiedStrategy) {
                        add(
                            RemoteSearchStrategy("simplified_track+artist") {
                                lrcLibApiService.searchLyrics(trackName = simplifiedTitle, artistName = simplifiedArtist)
                            }
                        )
                    }
                    // Romanized search strategy for non-Latin titles
                    if (MultiLangRomanizer.isScriptThatNeedsRomanization(cleanTitle)) {
                        val romanTitle = romanizeForMatch(cleanTitle)
                        if (romanTitle != cleanTitle) {
                            add(RemoteSearchStrategy("romanized_track") {
                                lrcLibApiService.searchLyrics(trackName = romanTitle, artistName = cleanArtist)
                            })
                        }
                    }
                } else {
                    add(
                        RemoteSearchStrategy("combined_query_unknown_artist") {
                            lrcLibApiService.searchLyrics(query = cleanTitle)
                        }
                    )
                }

                // Add strategies for "Artist - Title" / "Title - Artist" extracted from title or filename
                extractedPairs.drop(1).take(4).forEachIndexed { idx, (candTitle, candArtist) ->
                    if (candTitle.isNotBlank() && !isUnknownArtist(candArtist)) {
                        add(
                            RemoteSearchStrategy("extracted_pair_$idx") {
                                lrcLibApiService.searchLyrics(trackName = candTitle, artistName = candArtist)
                            }
                        )
                    }
                }
            }

            var results = runSearchStrategiesMerged(primaryStrategies)
            var bestMatch = rankRemoteLyricsMatches(
                song = song,
                responses = results,
                mode = RemoteLyricsMatchMode.AUTOMATIC
            ).firstOrNull()?.response

            // Fallback strategies (title-only) only when artist-qualified strategies did not find a match
            if (bestMatch == null) {
                val fallbackStrategies = buildList {
                    val smartTitle = cleanTitleSmart(cleanTitle)
                    if (smartTitle != cleanTitle && smartTitle.isNotBlank()) {
                        Log.d(TAG, "Adding smart search strategy for: '$smartTitle' (orig: '$cleanTitle')")
                        add(RemoteSearchStrategy("smart_track_only") {
                            lrcLibApiService.searchLyrics(trackName = smartTitle)
                        })
                    }
                    val separators = charArrayOf('-', ',', '(', ')', ':', '\uFF0D', '\u00B7', '\u30FB')
                    val index = cleanTitle.indexOfAny(separators)
                    if (index != -1) {
                        val superCleanTitle = cleanTitle.substring(0, index).trim()
                        if (superCleanTitle.isNotEmpty() && superCleanTitle != smartTitle) {
                            Log.d(TAG, "Strategy 4: Searching with super simplified title: '$superCleanTitle' (no artist)")
                            add(RemoteSearchStrategy("super_clean_track_only") {
                                lrcLibApiService.searchLyrics(trackName = superCleanTitle)
                            })
                        }
                    }
                }

                if (fallbackStrategies.isNotEmpty()) {
                    val fallbackResults = runSearchStrategiesMerged(fallbackStrategies)
                    if (fallbackResults.isNotEmpty()) {
                        results = (results + fallbackResults).distinctBy { it.id }
                        bestMatch = rankRemoteLyricsMatches(
                            song = song,
                            responses = results,
                            mode = RemoteLyricsMatchMode.AUTOMATIC
                        ).firstOrNull()?.response
                    }
                }
            }

            if (bestMatch == null) {
                val exactResponse = runCatching {
                    withNetworkRetry(operationName = "lrclib_get_lyrics") {
                        lrcLibApiService.getLyrics(
                            trackName = song.title,
                            artistName = song.displayArtist,
                            albumName = song.album,
                            duration = (song.duration / 1000).toInt()
                        )
                    }
                }.getOrNull()

                if (exactResponse != null) {
                    bestMatch = rankRemoteLyricsMatches(
                        song = song,
                        responses = listOf(exactResponse),
                        mode = RemoteLyricsMatchMode.AUTOMATIC
                    ).firstOrNull()?.response
                }
            }

            if (results.isEmpty() && bestMatch == null) {
                Log.d(TAG, "No results from LRCLIB API")
                return@withContext null
            }

            if (bestMatch != null) {
                val rawLyrics = bestMatch.syncedLyrics ?: bestMatch.plainLyrics
                if (!rawLyrics.isNullOrBlank()) {
                    val parsedLyrics = LyricsUtils.parseLyrics(rawLyrics).copy(
                        areFromRemote = true,
                        provider = "LRCLIB"
                    )
                    if (parsedLyrics.isValid()) {
                        Log.d(TAG, "LRCLIB lyrics found - Synced: ${!bestMatch.syncedLyrics.isNullOrBlank()}, Plain: ${!bestMatch.plainLyrics.isNullOrBlank()}")
                        return@withContext parsedLyrics to rawLyrics
                    }
                }
            }

            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "LRCLIB lyrics fetch failed: ${e.message}", e)
            return@withContext null
        }
    }

    private fun hasLyrics(response: LrcLibResponse): Boolean =
        !response.plainLyrics.isNullOrBlank() || !response.syncedLyrics.isNullOrBlank()

    private fun hasSyncedLyrics(response: LrcLibResponse): Boolean =
        !response.syncedLyrics.isNullOrBlank()

    private fun extractTitleArtistCandidates(song: Song): List<Pair<String, String>> {
        val candidates = mutableListOf<Pair<String, String>>()
        val primaryTitle = song.title.trim()
        val primaryArtist = song.displayArtist.trim()
        candidates.add(primaryTitle to primaryArtist)

        val rawSources = listOf(primaryTitle, songFileName(song))
        for (source in rawSources) {
            if (source.isBlank()) continue
            val withoutTrackNum = source.replace(Regex("""^\s*\d{1,3}\s*[\._-]\s+"""), "").trim()
            val parts = SPACED_HYPHEN_SPLIT_REGEX.split(withoutTrackNum)
                .map { it.replace(BRACKETED_QUALIFIER_REGEX, "").trim() }
                .filter { it.isNotBlank() && !shouldDropTitleQualifier(it) }

            if (parts.size == 2) {
                val partA = parts[0]
                val partB = parts[1]
                // Both "Artist - Title" and "Title - Artist" are common in downloaded MP3s
                candidates.add(partB to partA)
                candidates.add(partA to partB)
                if (!isUnknownArtist(primaryArtist)) {
                    candidates.add(partB to primaryArtist)
                    candidates.add(partA to primaryArtist)
                }
            }
        }
        return candidates.distinct()
    }

    private fun rankRemoteLyricsMatches(
        song: Song,
        responses: List<LrcLibResponse>,
        mode: RemoteLyricsMatchMode
    ): List<RemoteLyricsMatch> {
        val songDurationSeconds = song.duration / 1000.0
        if (songDurationSeconds <= 0.0) return emptyList()

        return responses
            .mapNotNull { response ->
                val score = remoteLyricsMatchScore(
                    song = song,
                    response = response,
                    mode = mode,
                    songDurationSeconds = songDurationSeconds
                ) ?: return@mapNotNull null
                RemoteLyricsMatch(response, score)
            }
            .sortedWith(
                compareByDescending<RemoteLyricsMatch> { it.score }
                    .thenByDescending { hasSyncedLyrics(it.response) }
                    .thenBy { abs(it.response.duration - songDurationSeconds) }
            )
    }

    private fun remoteLyricsMatchScore(
        song: Song,
        response: LrcLibResponse,
        mode: RemoteLyricsMatchMode,
        songDurationSeconds: Double
    ): Int? {
        if (!hasLyrics(response) || response.duration <= 0.0) return null
        if (!variantDescriptorsCompatible(song, response)) return null

        val hasSynced = hasSyncedLyrics(response)
        val durationTolerance = remoteDurationToleranceSeconds(songDurationSeconds, hasSynced, mode)
        val durationDiff = abs(response.duration - songDurationSeconds)
        if (durationDiff > durationTolerance) return null

        if (hasSynced && !isSyncedResponseTimelineValid(song, response.syncedLyrics, mode)) {
            return null
        }

        val candidates = extractTitleArtistCandidates(song)
        var bestCombinedPairScore: Int? = null

        for ((candidateTitle, candidateArtist) in candidates) {
            val titleScore = titleMatchScore(candidateTitle, response.name, mode) ?: continue
            val unknownArtist = isUnknownArtist(candidateArtist)
            val artistScore = artistMatchScore(candidateArtist, response.artistName, mode)
            if (!unknownArtist && artistScore == null) continue

            // When artist is unknown in AUTOMATIC mode, require a high-confidence title match
            // and tight duration match so we don't pick a random song with a vaguely similar title.
            if (unknownArtist && mode == RemoteLyricsMatchMode.AUTOMATIC) {
                if (titleScore < 65 || durationDiff > 4.5) continue
            }

            val pairScore = titleScore + (artistScore ?: 0)
            if (bestCombinedPairScore == null || pairScore > bestCombinedPairScore) {
                bestCombinedPairScore = pairScore
            }
        }

        val matchedPairScore = bestCombinedPairScore ?: return null
        val durationScore = (durationTolerance - durationDiff).coerceAtLeast(0.0).toInt()
        val syncedScore = if (hasSynced) 10 else 0
        return matchedPairScore + durationScore + syncedScore
    }

    private fun isSyncedResponseTimelineValid(
        song: Song,
        rawSyncedLyrics: String?,
        mode: RemoteLyricsMatchMode
    ): Boolean {
        if (rawSyncedLyrics.isNullOrBlank()) return true
        if (!isLrcMetadataHeaderCompatible(song, rawSyncedLyrics)) return false
        if (song.duration <= 0L) return true

        val parsed = LyricsUtils.parseLyrics(rawSyncedLyrics)
        val synced = parsed.synced ?: return true
        if (synced.isEmpty()) return true

        val maxTimeMs = synced.maxOf { it.time }.toLong()
        val maxAllowedOverhangMs = if (mode == RemoteLyricsMatchMode.AUTOMATIC) 8_000L else 15_000L
        if (maxTimeMs > song.duration + maxAllowedOverhangMs) {
            return false
        }
        if (mode == RemoteLyricsMatchMode.AUTOMATIC && synced.size >= 8 && song.duration >= 60_000L) {
            if (maxTimeMs < (song.duration * 0.35).toLong()) {
                return false
            }
        }
        return true
    }

    private fun isLrcMetadataHeaderCompatible(song: Song, rawLyrics: String): Boolean {
        val tiHeader = LRC_TI_HEADER_REGEX.find(rawLyrics)?.groupValues?.getOrNull(1)?.trim()
        if (!tiHeader.isNullOrBlank()) {
            val headerBase = baseTitleForMatching(tiHeader)
            val headerTokens = matchTokens(headerBase)
            if (headerTokens.isNotEmpty()) {
                val candidateTitles = extractTitleArtistCandidates(song).map { it.first } + songFileName(song)
                val anyTitleOverlap = candidateTitles.any { cand ->
                    val candBase = baseTitleForMatching(cand)
                    val candTokens = matchTokens(candBase)
                    candBase == headerBase ||
                        candTokens.intersect(headerTokens).isNotEmpty() ||
                        (candBase.isNotBlank() && (candBase.contains(headerBase) || headerBase.contains(candBase)))
                }
                if (!anyTitleOverlap) {
                    return false
                }
            }
        }
        return true
    }

    private fun remoteDurationToleranceSeconds(
        songDurationSeconds: Double,
        hasSyncedLyrics: Boolean,
        mode: RemoteLyricsMatchMode
    ): Double {
        return when (mode) {
            RemoteLyricsMatchMode.AUTOMATIC -> {
                if (hasSyncedLyrics) {
                    (songDurationSeconds * 0.02).coerceIn(4.0, 6.5)
                } else {
                    (songDurationSeconds * 0.04).coerceIn(8.0, 12.0)
                }
            }
            RemoteLyricsMatchMode.CANDIDATE -> 15.0
        }
    }

    private fun titleMatchScore(songTitle: String, responseTitle: String, mode: RemoteLyricsMatchMode): Int? {
        val songBase = baseTitleForMatching(songTitle)
        val responseBase = baseTitleForMatching(responseTitle)
        if (songBase.isBlank() || responseBase.isBlank()) return null

        if (songBase == responseBase) return 70

        // Attempt Romanized match for non-Latin scripts
        if (MultiLangRomanizer.isScriptThatNeedsRomanization(songBase) || 
            MultiLangRomanizer.isScriptThatNeedsRomanization(responseBase)) {
            val songRoman = normalizeForMatch(romanizeForMatch(songBase))
            val responseRoman = normalizeForMatch(romanizeForMatch(responseBase))
            if (songRoman == responseRoman && songRoman.isNotBlank()) return 65
        }

        val songTokens = matchTokens(songBase)
        val responseTokens = matchTokens(responseBase)
        if (songTokens.isEmpty() || responseTokens.isEmpty()) return null

        if (songTokens.size == 1 || responseTokens.size == 1) {
            if (songTokens == responseTokens) return 60
            
            // Fuzzy match for single token CJK: if one contains the other
            if (MultiLangRomanizer.isScriptThatNeedsRomanization(songBase) ||
                MultiLangRomanizer.isScriptThatNeedsRomanization(responseBase)) {
                val s1 = songBase.replace(" ", "")
                val s2 = responseBase.replace(" ", "")
                if (s1.isNotBlank() && s2.isNotBlank()) {
                    if (s1.contains(s2) || s2.contains(s1)) return 55
                }
            }
            
            return null
        }

        val overlap = songTokens.intersect(responseTokens).size
        val songCoverage = overlap.toDouble() / songTokens.size
        val responseCoverage = overlap.toDouble() / responseTokens.size

        if (containsWholePhrase(responseBase, songBase) || containsWholePhrase(songBase, responseBase)) {
            val minCoverage = minOf(songCoverage, responseCoverage)
            if (mode == RemoteLyricsMatchMode.CANDIDATE || minCoverage >= 0.65) {
                return if (mode == RemoteLyricsMatchMode.AUTOMATIC) 58 else 54
            }
        }

        val requiredSongCoverage = if (mode == RemoteLyricsMatchMode.AUTOMATIC) 0.85 else 0.75
        val requiredResponseCoverage = if (mode == RemoteLyricsMatchMode.AUTOMATIC) 0.75 else 0.55

        return if (songCoverage >= requiredSongCoverage && responseCoverage >= requiredResponseCoverage) {
            45
        } else {
            null
        }
    }

    private fun artistMatchScore(
        songArtist: String,
        responseArtist: String,
        mode: RemoteLyricsMatchMode = RemoteLyricsMatchMode.AUTOMATIC
    ): Int? {
        if (isUnknownArtist(songArtist)) return 0

        val songBase = normalizeForMatch(songArtist)
        val responseBase = normalizeForMatch(responseArtist)
        if (songBase.isBlank() || responseBase.isBlank()) return null

        if (songBase == responseBase) return 30
        
        // Attempt Romanized match
        if (MultiLangRomanizer.isScriptThatNeedsRomanization(songBase) || 
            MultiLangRomanizer.isScriptThatNeedsRomanization(responseBase)) {
            val songRoman = normalizeForMatch(romanizeForMatch(songBase))
            val responseRoman = normalizeForMatch(romanizeForMatch(responseBase))
            if (songRoman == responseRoman && songRoman.isNotBlank()) return 28
        }

        if (containsWholePhrase(responseBase, songBase) || containsWholePhrase(songBase, responseBase)) {
            return 22
        }

        val songTokens = artistTokens(songBase)
        val responseTokens = artistTokens(responseBase)
        if (songTokens.isEmpty() || responseTokens.isEmpty()) return null

        val overlap = songTokens.intersect(responseTokens).size
        val minTokens = minOf(songTokens.size, responseTokens.size)
        val smallerArtistCoverage = overlap.toDouble() / minTokens
        val requiredCoverage = when {
            mode == RemoteLyricsMatchMode.CANDIDATE -> 0.5
            minTokens <= 2 -> 1.0
            else -> 0.66
        }
        return if (smallerArtistCoverage >= requiredCoverage) 12 else null
    }

    private fun variantDescriptorsCompatible(song: Song, response: LrcLibResponse): Boolean {
        val songVariants = timingVariantTokens(song.title) + timingVariantTokensFromFileName(song)
        val responseVariants = timingVariantTokens(response.name)

        if (songVariants.isEmpty()) {
            return responseVariants.isEmpty()
        }

        return responseVariants == songVariants
    }

    private fun baseTitleForMatching(title: String): String {
        var base = title.replace(Regex("""^\s*\d{1,3}\s*[\._-]\s+"""), "")

        base = BRACKETED_QUALIFIER_REGEX.replace(base) { match ->
            val qualifier = match.groupValues.getOrNull(1).orEmpty()
            if (shouldDropTitleQualifier(qualifier)) " " else " $qualifier "
        }

        var parts = TITLE_SEPARATOR_REGEX.split(base)
        while (parts.size > 1 && shouldDropTitleQualifier(parts.last())) {
            parts = parts.dropLast(1)
        }

        return normalizeForMatch(parts.joinToString(" "))
    }

    private fun shouldDropTitleQualifier(value: String): Boolean {
        val normalized = normalizeForMatch(value)
        if (normalized.isBlank()) return true
        return FEATURE_QUALIFIER_REGEX.containsMatchIn(value) ||
            timingVariantTokens(value).isNotEmpty() ||
            normalized in TITLE_DROP_QUALIFIERS
    }

    private fun timingVariantTokens(value: String): Set<String> {
        val normalized = normalizeForMatch(value)
        if (normalized.isBlank()) return emptySet()

        val tokens = matchTokens(normalized)
        val variants = tokens
            .filter { it in TIMING_VARIANT_KEYWORDS }
            .toMutableSet()

        if (Regex("""\bmash\s+up\b""").containsMatchIn(normalized)) {
            variants += "mashup"
        }
        if ("versus" in tokens || "vs" in tokens) {
            variants += "mashup"
        }

        return variants
    }

    private fun timingVariantTokensFromFileName(song: Song): Set<String> {
        val fileName = songFileName(song)
        if (fileName.isBlank()) return emptySet()

        val variants = BRACKETED_QUALIFIER_REGEX
            .findAll(fileName)
            .flatMap { match -> timingVariantTokens(match.groupValues.getOrNull(1).orEmpty()) }
            .toMutableSet()

        val titleBase = baseTitleForMatching(song.title)
        if (titleBase.isBlank()) return variants

        TITLE_SEPARATOR_REGEX.split(fileName).forEach { part ->
            val normalizedPart = normalizeForMatch(part)
            if (normalizedPart.startsWith("$titleBase ")) {
                variants += timingVariantTokens(normalizedPart.removePrefix(titleBase).trim())
            }
        }

        return variants
    }

    private fun songFileName(song: Song): String {
        if (song.path.isBlank()) return ""
        return runCatching { File(song.path).nameWithoutExtension }.getOrDefault("")
    }

    private fun artistTokens(normalizedArtist: String): Set<String> =
        matchTokens(normalizedArtist)
            .filterNot { it in ARTIST_CONNECTOR_TOKENS }
            .toSet()

    private fun matchTokens(normalizedValue: String): Set<String> =
        normalizedValue
            .split(' ')
            .filter { it.isNotBlank() }
            .toSet()

    private fun containsWholePhrase(haystack: String, needle: String): Boolean {
        if (needle.isBlank()) return false
        return Regex("""(?:^|\s)${Regex.escape(needle)}(?:\s|$)""").containsMatchIn(haystack)
    }

    private fun normalizeForMatch(value: String): String {
        val withoutDiacritics = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")

        return withoutDiacritics
            .replace("&", " and ")
            .replace(Regex("""[\u2019'`]"""), "")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
    }

    private fun isUnknownArtist(value: String): Boolean =
        normalizeForMatch(value) in UNKNOWN_ARTISTS

    private fun isNeteaseSong(song: Song): Boolean =
        song.neteaseId != null || song.contentUriString.startsWith("netease://")

    private fun resolveNeteaseSongId(song: Song): Long? {
        song.neteaseId?.let { return it }
        if (!song.contentUriString.startsWith("netease://")) return null
        return Uri.parse(song.contentUriString).host?.toLongOrNull()
    }

    private suspend fun fetchFromAmlldb(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        val neteaseSongId = resolveNeteaseSongId(song) ?: return@withContext null
        val request = Request.Builder()
            .url("$AMLLDB_NCM_LYRICS_BASE_URL$neteaseSongId")
            .get()
            .build()

        try {
            val ttml = withNetworkRetry(
                operationName = "amlldb_fetch:$neteaseSongId",
                shouldRetry = { throwable -> throwable is IOException }
            ) {
                okHttpClient.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> response.body.string()
                        response.code.isRetryableHttpStatusCode() ->
                            throw IOException("AMLLDB HTTP ${response.code} for songId=$neteaseSongId")
                        else -> ""
                    }
                }
            }

            if (ttml.isBlank() || ttml.contains("歌词不存在")) return@withContext null
            val lrc = com.theveloper.pixelplay.utils.TtmlLyricsParser.parseToEnhancedLrc(ttml)
                ?: convertAmlTtmlToLrc(ttml)
                ?: return@withContext null
            val parsed = LyricsUtils.parseLyrics(lrc)
            if (!parsed.isValid()) return@withContext null
            return@withContext parsed.copy(areFromRemote = true, provider = "AMLLDB")
        } catch (e: Exception) {
            Log.w(TAG, "AMLLDB fetch failed for $neteaseSongId: ${e.message}")
            return@withContext null
        }
    }

    private fun convertAmlTtmlToLrc(ttml: String): String? {
        val lineRegex = Regex(
            "<p\\b[^>]*\\bbegin=\"([^\"]+)\"[^>]*>(.*?)</p>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val spanRegex = Regex(
            "<span\\b([^>]*)>(.*?)</span>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val beginAttrRegex = Regex("\\bbegin=\"([^\"]+)\"")
        val roleAttrRegex = Regex("\\bttm:role=\"([^\"]+)\"")

        val lrcLines = mutableListOf<String>()
        lineRegex.findAll(ttml).forEach { lineMatch ->
            val lineStartMs = parseTtmlTimeToMs(lineMatch.groupValues[1]) ?: return@forEach
            var inner = lineMatch.groupValues[2]
            val markerRegex = Regex("§§TS\\(([^)]+)\\)§§")

            inner = spanRegex.replace(inner) { spanMatch ->
                val attributes = spanMatch.groupValues[1]
                val role = roleAttrRegex.find(attributes)?.groupValues?.getOrNull(1)?.lowercase()
                if (role == "x-roman") {
                    return@replace ""
                }
                val wordStartMs = beginAttrRegex
                    .find(attributes)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let(::parseTtmlTimeToMs)
                val text = decodeXmlEntities(spanMatch.groupValues[2])

                if (wordStartMs == null) {
                    // Keep visible text (e.g. translation) but do not inject word timing.
                    return@replace text
                }

                "§§TS(${formatTimestamp(wordStartMs.toInt())})§§$text"
            }

            val withoutXmlTags = decodeXmlEntities(inner.replace(Regex("<[^>]+>"), ""))
            val lrcInlineTagged = markerRegex.replace(withoutXmlTags, "<$1>")
            if (lrcInlineTagged.isBlank()) return@forEach

            lrcLines += "[${formatTimestamp(lineStartMs.toInt())}]$lrcInlineTagged"
        }

        return lrcLines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private fun parseTtmlTimeToMs(value: String): Long? {
        val raw = value.trim()
        if (raw.isEmpty()) return null

        if (raw.endsWith("s")) {
            val seconds = raw.removeSuffix("s").toDoubleOrNull() ?: return null
            return (seconds * 1000.0).toLong()
        }

        val parts = raw.split(":")
        val secondsPart = parts.lastOrNull()?.toDoubleOrNull() ?: return null
        return when (parts.size) {
            3 -> {
                val hours = parts[0].toLongOrNull() ?: return null
                val minutes = parts[1].toLongOrNull() ?: return null
                (hours * 3_600_000L) + (minutes * 60_000L) + (secondsPart * 1000.0).toLong()
            }
            2 -> {
                val minutes = parts[0].toLongOrNull() ?: return null
                (minutes * 60_000L) + (secondsPart * 1000.0).toLong()
            }
            1 -> (secondsPart * 1000.0).toLong()
            else -> null
        }
    }

    private fun decodeXmlEntities(value: String): String =
        value
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")

    /**
     * Find local .lrc file next to the music file (matching Rhythm)
     */
    private suspend fun findLocalLyricsFile(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        try {
            val songFile = File(song.path)
            val directory = songFile.parentFile ?: return@withContext null
            val songNameWithoutExt = songFile.nameWithoutExtension

            if (directory.exists()) {
                for (extension in LyricsImportSecurity.supportedFileExtensions()) {
                    val lyricsFile = File(directory, "$songNameWithoutExt.$extension")
                    if (!lyricsFile.exists() || !lyricsFile.canRead()) continue

                    val validated = readValidatedLocalLyrics(lyricsFile)
                    if (validated != null && isLocalLyricsFileCompatibleWithSong(song, validated.parsedLyrics, validated.sanitizedContent)) {
                        Log.d(TAG, "===== FOUND LOCAL LYRICS FILE: ${lyricsFile.name} =====")
                        return@withContext validated.parsedLyrics
                    } else if (validated != null) {
                        Log.w(TAG, "Skipping incompatible local lyrics file: ${lyricsFile.name} for song ${song.title}")
                    }
                }

                val cleanArtist = song.displayArtist.replace(Regex("[^a-zA-Z0-9]"), "_")
                val cleanTitle = song.title.replace(Regex("[^a-zA-Z0-9]"), "_")

                for (extension in LyricsImportSecurity.supportedFileExtensions()) {
                    val alternativeLyricsFile = File(directory, "${cleanArtist}_${cleanTitle}.$extension")
                    if (!alternativeLyricsFile.exists() || !alternativeLyricsFile.canRead()) continue

                    val validated = readValidatedLocalLyrics(alternativeLyricsFile)
                    if (validated != null && isLocalLyricsFileCompatibleWithSong(song, validated.parsedLyrics, validated.sanitizedContent)) {
                        Log.d(TAG, "===== FOUND LOCAL LYRICS FILE (alt pattern): ${alternativeLyricsFile.name} =====")
                        return@withContext validated.parsedLyrics
                    } else if (validated != null) {
                        Log.w(TAG, "Skipping incompatible local lyrics file (alt): ${alternativeLyricsFile.name} for song ${song.title}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching for local lyrics file", e)
        }
        return@withContext null
    }

    private fun isLocalLyricsFileCompatibleWithSong(
        song: Song,
        parsedLyrics: Lyrics,
        rawLyrics: String
    ): Boolean {
        if (!isLrcMetadataHeaderCompatible(song, rawLyrics)) {
            return false
        }
        val synced = parsedLyrics.synced
        if (!synced.isNullOrEmpty() && song.duration > 30_000L) {
            val maxTimeMs = synced.maxOf { it.time }.toLong()
            if (maxTimeMs > song.duration + 15_000L) {
                return false
            }
        }
        return true
    }

    private fun readValidatedLocalLyrics(file: File): com.theveloper.pixelplay.utils.ValidatedLyricsImport? {
        return when (val validation = LyricsImportSecurity.validateLocalLyricsFile(file)) {
            is LyricsImportValidationResult.Valid -> validation.value
            is LyricsImportValidationResult.Invalid -> null
        }
    }

    /**
     * Save lyrics to JSON disk cache (matching Rhythm)
     */
    private fun saveLocalLyricsJson(song: Song, lyrics: Lyrics) {
        saveLocalLyricsJsonById(song.id, lyrics)
    }

    private fun saveLocalLyricsJsonById(songId: String, lyrics: Lyrics) {
        try {
            val fileName = "$songId.json"
            val lyricsDir = File(context.filesDir, "lyrics")
            lyricsDir.mkdirs()

            val headerPrefix = buildLyricsHeaderPrefix(lyrics)
            val wordByWordLyrics = lyrics.synced
                ?.takeIf { lines -> lines.any { !it.words.isNullOrEmpty() || it.translation != null || it.romanization != null || it.isBackground || it.voice != null } }
                ?.let { headerPrefix + toWordByWordLrc(it) }

            val syncedLyricsText = lyrics.synced?.let { lines ->
                headerPrefix + lines.joinToString("\n") { line ->
                    val prefix = "[${formatTimestamp(line.time)}]"
                    val voicePrefix = line.voice?.let { "$it:" } ?: ""
                    val body = if (line.isBackground) "[bg:${line.line}]" else "$voicePrefix${line.line}"
                    prefix + body
                }
            }

            val lyricsData = LyricsData(
                plainLyrics = lyrics.plain?.joinToString("\n"),
                syncedLyrics = syncedLyricsText,
                wordByWordLyrics = wordByWordLyrics
            )

            val file = File(lyricsDir, fileName)
            val json = gson.toJson(lyricsData)
            file.writeText(json)
            Log.d(TAG, "Saved lyrics to JSON cache: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving lyrics to JSON cache: ${e.message}", e)
        }
    }

    private fun buildLyricsHeaderPrefix(lyrics: Lyrics): String {
        val headers = mutableListOf<String>()
        lyrics.provider?.takeIf { it.isNotBlank() }?.let {
            headers.add("[source:$it]")
        }
        lyrics.synced?.forEach { line ->
            line.translation?.takeIf { it.isNotBlank() }?.let { trans ->
                val b64 = android.util.Base64.encodeToString(
                    trans.toByteArray(Charsets.UTF_8),
                    android.util.Base64.NO_WRAP
                )
                headers.add("[x-translation:${line.time}:$b64]")
            }
            line.romanization?.takeIf { it.isNotBlank() }?.let { rom ->
                val b64 = android.util.Base64.encodeToString(
                    rom.toByteArray(Charsets.UTF_8),
                    android.util.Base64.NO_WRAP
                )
                headers.add("[x-romaji:${line.time}:$b64]")
            }
        }
        return if (headers.isEmpty()) "" else headers.joinToString("\n") + "\n"
    }

    /**
     * Load lyrics from JSON disk cache (matching Rhythm)
     */
    private suspend fun loadLocalLyricsJson(song: Song): Lyrics? {
        try {
            val data = readLyricsJsonCache(song) ?: return null
            if (data.hasLyrics()) {
                val rawLyrics = data.wordByWordLyrics ?: data.syncedLyrics ?: data.plainLyrics ?: return null
                val parsed = LyricsUtils.parseLyrics(rawLyrics)
                if (parsed.isValid()) {
                    if (!isStoredRemoteLyricsValidForSong(song, parsed, rawLyrics)) {
                        Log.w(TAG, "Auto-healing corrupted/mismatched JSON cached lyrics for song: ${song.title}")
                        deleteLyricsJsonCache(song)
                        return null
                    }
                    val hasWordTimestamps = parsed.synced?.any { !it.words.isNullOrEmpty() } == true
                    if (!hasWordTimestamps && data.wordByWordLyrics.isNullOrBlank()) {
                        // Legacy cache may have flattened word-by-word lines.
                        // Recover richer raw lyrics from DB when available.
                        val persistedContent = song.id.toLongOrNull()
                            ?.let { lyricsDao.getLyrics(it)?.content }
                            ?.takeIf { it.isNotBlank() }
                        if (persistedContent != null) {
                            val recovered = LyricsUtils.parseLyrics(persistedContent)
                            val recoveredHasWords = recovered.synced?.any { !it.words.isNullOrEmpty() } == true
                            if (recovered.isValid() && recoveredHasWords && isStoredRemoteLyricsValidForSong(song, recovered, persistedContent)) {
                                saveLocalLyricsJson(song, recovered)
                                return recovered
                            }
                        }

                        if (looksLikeFlattenedWordByWordCache(parsed)) {
                            // Force a remote re-fetch instead of serving degraded cache.
                            return null
                        }
                    }
                    return parsed
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading JSON cache: ${e.message}", e)
        }
        return null
    }

    private fun formatTimestamp(timeMs: Int): String {
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hundredths = (timeMs % 1000) / 10
        return String.format("%02d:%02d.%02d", minutes, seconds, hundredths)
    }

    private fun toWordByWordLrc(lines: List<SyncedLine>): String {
        return lines.joinToString("\n") { line ->
            val linePrefix = "[${formatTimestamp(line.time)}]"
            val voicePrefix = line.voice?.let { "$it:" } ?: ""
            val words = line.words
            val body = if (words.isNullOrEmpty()) {
                if (line.isBackground) "[bg:${line.line}]" else "$voicePrefix${line.line}"
            } else {
                val wordsPart = words.mapIndexed { index, word ->
                    val separator = if (index > 0 && word.startsNewWord) " " else ""
                    val endTag = word.endTime?.let { "<${formatTimestamp(it)}>" } ?: ""
                    "$separator<${formatTimestamp(word.time)}>${word.word}$endTag"
                }.joinToString("")
                if (line.isBackground) "[bg:$wordsPart]" else "$voicePrefix$wordsPart"
            }
            linePrefix + body
        }
    }

    private fun looksLikeFlattenedWordByWordCache(lyrics: Lyrics): Boolean {
        val synced = lyrics.synced ?: return false
        var suspiciousLines = 0

        for (line in synced) {
            val text = line.line
            if (text.isBlank() || text.any { it.isWhitespace() }) continue

            val hasLongLatinRun = Regex("[A-Za-z]{10,}").containsMatchIn(text)
            if (hasLongLatinRun) {
                suspiciousLines += 1
                if (suspiciousLines >= 2) return true
            }
        }

        return false
    }

    /**
     * Load embedded lyrics from audio file metadata without copying the full audio file to cacheDir
     * whenever a direct ParcelFileDescriptor is available.
     */
    private suspend fun loadEmbeddedLyricsFromMetadata(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        // Skip embedded lyrics for Telegram/Cloud songs
        if (song.contentUriString.startsWith("telegram://") ||
            song.contentUriString.startsWith("netease://") ||
            song.contentUriString.startsWith("qqmusic://") ||
            song.contentUriString.startsWith("jellyfin://") ||
            song.contentUriString.startsWith("navidrome://") ||
            (song.contentUriString.isEmpty() && song.path.isEmpty())
        ) {
            return@withContext null
        }

        return@withContext try {
            val directFile = song.path.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() && it.canRead() }
            val pfd: ParcelFileDescriptor? = when {
                directFile != null -> runCatching {
                    ParcelFileDescriptor.open(directFile, ParcelFileDescriptor.MODE_READ_ONLY)
                }.getOrNull()
                song.contentUriString.isNotBlank() -> runCatching {
                    context.contentResolver.openFileDescriptor(song.contentUriString.toUri(), "r")
                }.getOrNull()
                else -> null
            }

            if (pfd != null) {
                pfd.use { fd ->
                    val metadata = TagLib.getMetadata(fd.detachFd())
                    val parsedLyrics = parseBestEmbeddedLyricsField(metadata?.propertyMap)
                    if (parsedLyrics != null) {
                        Log.d(TAG, "===== FOUND EMBEDDED LYRICS (direct FD) =====")
                        return@withContext parsedLyrics
                    }
                    return@withContext null
                }
            }

            // Fallback to temp file only if neither direct path nor contentResolver FD could be opened
            if (song.contentUriString.isBlank()) return@withContext null
            val uri = song.contentUriString.toUri()
            val tempFile = createTempFileFromUri(uri)
            if (tempFile == null) {
                LogUtils.w(this@LyricsRepositoryImpl, "Could not create temp file from URI: ${song.contentUriString}")
                return@withContext null
            }

            try {
                ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    val metadata = TagLib.getMetadata(fd.detachFd())
                    val propertyMap = metadata?.propertyMap
                    val parsedLyrics = parseBestEmbeddedLyricsField(propertyMap)

                    if (parsedLyrics != null) {
                        Log.d(TAG, "===== FOUND EMBEDDED LYRICS =====")
                        parsedLyrics
                    } else {
                        null
                    }
                }
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            LogUtils.e(this@LyricsRepositoryImpl, e, "Error reading lyrics from file metadata")
            null
        }
    }

    private suspend fun loadStoredLyrics(
        song: Song,
        cacheKey: String,
        includeMemoryCache: Boolean,
        onlyManualOrMatchingPreference: LyricsSourcePreference? = null
    ): Pair<Lyrics, String>? = withContext(Dispatchers.IO) {
        song.lyrics
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { rawLyrics ->
                parseStoredLyrics(rawLyrics, isRemote = false)?.let { return@withContext it to rawLyrics }
            }

        song.id.toLongOrNull()?.let { numericSongId ->
            val entity = lyricsDao.getLyrics(numericSongId)
            val rawLyrics = entity?.content?.trim()?.takeIf { it.isNotBlank() }
            if (rawLyrics != null) {
                val isRemoteSource = entity.source == "remote"
                val isLocalFileSource = entity.source == "local_file"
                val isAutoCachedSource = isRemoteSource || isLocalFileSource
                val shouldReturnForPreference = when (onlyManualOrMatchingPreference) {
                    null -> true
                    LyricsSourcePreference.API_FIRST -> !isLocalFileSource
                    LyricsSourcePreference.LOCAL_FIRST -> !isRemoteSource
                    LyricsSourcePreference.EMBEDDED_FIRST -> !isAutoCachedSource
                }
                if (shouldReturnForPreference) {
                    val parsed = parseStoredLyrics(
                        rawLyrics = rawLyrics,
                        isRemote = isRemoteSource && onlyManualOrMatchingPreference != null
                    )
                    if (parsed != null) {
                        if (!isAutoCachedSource || isStoredRemoteLyricsValidForSong(song, parsed, rawLyrics)) {
                            return@withContext parsed to rawLyrics
                        } else {
                            Log.w(TAG, "Auto-healing mismatched stored lyrics (source=${entity.source}) for song: ${song.title}")
                            runCatching { lyricsDao.deleteLyrics(numericSongId) }
                            deleteLyricsJsonCache(song)
                        }
                    }
                }
            }
        }

        if (onlyManualOrMatchingPreference == null || onlyManualOrMatchingPreference == LyricsSourcePreference.API_FIRST) {
            readLyricsJsonCache(song)
                ?.takeIf { it.hasLyrics() }
                ?.let { data ->
                    val rawLyrics = data.wordByWordLyrics ?: data.syncedLyrics ?: data.plainLyrics
                    if (!rawLyrics.isNullOrBlank()) {
                        val parsed = parseStoredLyrics(
                            rawLyrics = rawLyrics,
                            isRemote = onlyManualOrMatchingPreference != null
                        )
                        if (parsed != null) {
                            if (isStoredRemoteLyricsValidForSong(song, parsed, rawLyrics)) {
                                return@withContext parsed to rawLyrics
                            } else {
                                Log.w(TAG, "Auto-healing mismatched JSON cached lyrics for song: ${song.title}")
                                deleteLyricsJsonCache(song)
                            }
                        }
                    }
                }
        }

        if (includeMemoryCache) {
            lyricsCache.get(cacheKey)?.let { cached ->
                lyricsToRawContent(cached)?.let { rawLyrics ->
                    return@withContext cached to rawLyrics
                }
            }
        }

        null
    }

    /**
     * Self-healing check for previously auto-fetched/cached lyrics:
     * Rejects cached lyrics if their LRC [ti:] header conflicts with the song title
     * or if their synced timestamps extend significantly past the song's duration.
     */
    private fun isStoredRemoteLyricsValidForSong(
        song: Song,
        parsedLyrics: Lyrics,
        rawLyrics: String
    ): Boolean {
        if (!isLrcMetadataHeaderCompatible(song, rawLyrics)) {
            return false
        }
        val synced = parsedLyrics.synced
        if (!synced.isNullOrEmpty() && song.duration > 30_000L) {
            val maxTimeMs = synced.maxOf { it.time }.toLong()
            if (maxTimeMs > song.duration + 10_000L) {
                return false
            }
            if (synced.size >= 8 && song.duration >= 60_000L && maxTimeMs < (song.duration * 0.35).toLong()) {
                return false
            }
        }
        return true
    }

    private fun deleteLyricsJsonCache(song: Song) {
        runCatching {
            val file = File(context.filesDir, "lyrics/${song.id}.json")
            if (file.exists()) file.delete()
        }
    }

    private fun parseStoredLyrics(rawLyrics: String, isRemote: Boolean = false): Lyrics? {
        val parsedLyrics = LyricsUtils.parseLyrics(rawLyrics)
        return parsedLyrics
            .takeIf { it.isValid() }
            ?.copy(areFromRemote = isRemote)
    }

    private fun lyricsToRawContent(lyrics: Lyrics): String? {
        val syncedLyrics = lyrics.synced
        if (!syncedLyrics.isNullOrEmpty()) {
            val headerPrefix = buildLyricsHeaderPrefix(lyrics)
            val hasWordTimestamps = syncedLyrics.any { !it.words.isNullOrEmpty() || it.isBackground || it.voice != null }
            return if (hasWordTimestamps) {
                headerPrefix + toWordByWordLrc(syncedLyrics)
            } else {
                headerPrefix + syncedLyrics.joinToString("\n") { line ->
                    "[${formatTimestamp(line.time)}]${line.line}"
                }
            }
        }

        return lyrics.plain
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString("\n")
            ?.takeIf { it.isNotBlank() }
    }

    private fun readLyricsJsonCache(song: Song): LyricsData? {
        val fileName = "${song.id}.json"
        val file = File(context.filesDir, "lyrics/$fileName")
        if (!file.exists()) return null

        val json = file.readText()
        return gson.fromJson(json, LyricsData::class.java)
    }

    // ========== Original methods (kept for backward compatibility) ==========

    override suspend fun fetchFromRemote(song: Song): Result<Pair<Lyrics, String>> = withContext(Dispatchers.IO) {
        try {
            LogUtils.d(this@LyricsRepositoryImpl, "Fetching lyrics from remote for: ${song.title}")

            val cacheKey = generateCacheKey(song.id)
            loadStoredLyrics(song, cacheKey, includeMemoryCache = true)?.let { stored ->
                lyricsCache.put(cacheKey, stored.first)
                LogUtils.d(
                    this@LyricsRepositoryImpl,
                    "Skipping remote lyrics fetch because stored lyrics already exist for: ${song.title}"
                )
                return@withContext Result.success(stored)
            }

            fetchCloudNativeLyrics(song)?.let { (cloudLyrics, rawCloudLrc) ->
                val rawToSave = rawCloudLrc.ifBlank { lyricsToRawContent(cloudLyrics).orEmpty() }
                if (rawToSave.isNotBlank()) {
                    try {
                        lyricsDao.insert(
                            com.theveloper.pixelplay.data.database.LyricsEntity(
                                songId = song.id.toLong(),
                                content = rawToSave,
                                isSynced = !cloudLyrics.synced.isNullOrEmpty(),
                                source = "remote"
                            )
                        )
                    } catch (e: NumberFormatException) {
                        Log.w(TAG, "Skipping DB update for non-numeric cloud ID: ${song.id}")
                    }
                    lyricsCache.put(cacheKey, cloudLyrics)
                    saveLocalLyricsJson(song, cloudLyrics)
                    return@withContext Result.success(Pair(cloudLyrics, rawToSave))
                }
            }

            // First try the reorderable Mornye provider chain (Zarz-LRC, Apple Music, lrc.red, LyricsPlus, BetterLyrics, LRCLIB, NetEase, Kugou, QQ Music)
            var lrclibInvoked = false
            val chainHit = mornyeProviderChain.searchOrderedChain(
                context = context,
                title = song.title,
                artist = song.displayArtist,
                durationMs = song.duration,
                lrclibFetcher = {
                    lrclibInvoked = true
                    fetchFromLrcLibPair(song)
                }
            )
            if (chainHit != null) {
                val parsedLyrics = chainHit.parsedLyrics
                val rawLyricsToSave = chainHit.rawLrc
                if (parsedLyrics.isValid()) {
                    try {
                        lyricsDao.insert(
                            com.theveloper.pixelplay.data.database.LyricsEntity(
                                songId = song.id.toLong(),
                                content = rawLyricsToSave,
                                isSynced = !parsedLyrics.synced.isNullOrEmpty(),
                                source = "remote"
                            )
                        )
                    } catch (e: NumberFormatException) {
                        Log.w(TAG, "Skipping DB update for non-numeric ID: ${song.id}")
                    }
                    lyricsCache.put(cacheKey, parsedLyrics)
                    saveLocalLyricsJson(song, parsedLyrics)
                    LogUtils.d(this@LyricsRepositoryImpl, "Fetched and cached remote lyrics via provider chain (${parsedLyrics.provider}) for: ${song.title}")
                    return@withContext Result.success(Pair(parsedLyrics, rawLyricsToSave))
                }
            }

            if (lrclibInvoked) {
                LogUtils.d(this@LyricsRepositoryImpl, "No lyrics found remotely via provider chain (including LRCLIB) for: ${song.title}")
                return@withContext Result.failure(NoLyricsFoundException())
            }

            // Next, run LRCLIB search strategies and rank with strict AUTOMATIC mode (never CANDIDATE mode)
            val searchResult = searchRemoteWithMode(song, RemoteLyricsMatchMode.AUTOMATIC)
            if (searchResult.isSuccess) {
                val (_, results) = searchResult.getOrThrow()
                if (results.isNotEmpty()) {
                    val best = results.first()
                    val rawLyricsToSave = best.rawLyrics

                    try {
                        lyricsDao.insert(
                             com.theveloper.pixelplay.data.database.LyricsEntity(
                                 songId = song.id.toLong(),
                                 content = rawLyricsToSave,
                                 isSynced = !best.lyrics.synced.isNullOrEmpty(),
                                 source = "remote"
                             )
                        )
                    } catch (e: NumberFormatException) {
                        Log.w(TAG, "Skipping DB update for non-numeric ID: ${song.id}")
                    }

                    lyricsCache.put(cacheKey, best.lyrics)
                    saveLocalLyricsJson(song, best.lyrics)
                    LogUtils.d(this@LyricsRepositoryImpl, "Fetched and cached remote lyrics for: ${song.title}")

                    return@withContext Result.success(Pair(best.lyrics, rawLyricsToSave))
                }
            }

            // Fallback: Try the exact match API (less likely to succeed, but worth a shot)
            val response = withNetworkRetry(operationName = "lrclib_get_lyrics") {
                lrcLibApiService.getLyrics(
                    trackName = song.title,
                    artistName = song.displayArtist,
                    albumName = song.album,
                    duration = (song.duration / 1000).toInt()
                )
            }

            val exactMatch = response
                ?.let { rankRemoteLyricsMatches(song, listOf(it), RemoteLyricsMatchMode.AUTOMATIC).firstOrNull()?.response }

            if (exactMatch != null) {
                val rawLyricsToSave = exactMatch.syncedLyrics ?: exactMatch.plainLyrics
                    ?: return@withContext Result.failure(NoLyricsFoundException())

                val parsedLyrics = LyricsUtils.parseLyrics(rawLyricsToSave).copy(areFromRemote = true)
                if (!parsedLyrics.isValid()) {
                    return@withContext Result.failure(LyricsException("Parsed lyrics are empty"))
                }

                try {
                    lyricsDao.insert(
                        com.theveloper.pixelplay.data.database.LyricsEntity(
                            songId = song.id.toLong(),
                            content = rawLyricsToSave,
                            isSynced = !parsedLyrics.synced.isNullOrEmpty(),
                            source = "remote"
                        )
                    )
                } catch (e: NumberFormatException) {
                    Log.w(TAG, "Skipping DB update for non-numeric ID in fallback: ${song.id}")
                }

                lyricsCache.put(cacheKey, parsedLyrics)
                saveLocalLyricsJson(song, parsedLyrics)
                LogUtils.d(this@LyricsRepositoryImpl, "Fetched and cached remote lyrics (exact match) for: ${song.title}")

                Result.success(Pair(parsedLyrics, rawLyricsToSave))
            } else {
                LogUtils.d(this@LyricsRepositoryImpl, "No lyrics found remotely for: ${song.title}")
                Result.failure(NoLyricsFoundException())
            }
        } catch (e: Exception) {
            LogUtils.e(this@LyricsRepositoryImpl, e, "Error fetching lyrics from remote")
            when {
                e is HttpException && e.code() == 404 -> Result.failure(NoLyricsFoundException())
                e is SocketTimeoutException -> Result.failure(LyricsException(context.getString(R.string.lyrics_fetch_timeout), e))
                e is UnknownHostException -> Result.failure(LyricsException(context.getString(R.string.lyrics_network_error), e))
                e is IOException -> Result.failure(LyricsException(context.getString(R.string.lyrics_network_error), e))
                e is HttpException -> Result.failure(LyricsException(context.getString(R.string.lyrics_server_error, e.code()), e))
                else -> Result.failure(LyricsException(context.getString(R.string.lyrics_failed_to_fetch_from_remote), e))
            }
        }
    }

    override suspend fun searchRemote(song: Song): Result<Pair<String, List<LyricsSearchResult>>> =
        searchRemoteWithMode(song, RemoteLyricsMatchMode.CANDIDATE)

    private suspend fun searchChainProvidersAsResults(
        title: String,
        artist: String?,
        album: String?,
        durationSec: Int?,
        specificProvider: LyricsProviderType? = null
    ): List<LyricsSearchResult> = coroutineScope {
        val providersToQuery = if (specificProvider != null) {
            listOf(specificProvider)
        } else {
            LyricsProviderPreferences.getProviderConfigs(context)
                .filter { it.enabled && it.provider != LyricsProviderType.LRCLIB }
                .map { it.provider }
                .take(5)
        }
        providersToQuery.mapIndexed { idx, provider ->
            async(Dispatchers.IO) {
                runCatching {
                    val hit = mornyeProviderChain.fetchFromSingleProvider(
                        provider = provider,
                        title = title,
                        artist = artist.orEmpty(),
                        durationSec = (durationSec ?: 0).toDouble(),
                        lrclibFetcher = null
                    ) ?: return@async null
                    val lyrics = hit.parsedLyrics
                    val rawLyrics = hit.rawLrc
                    if (!lyrics.isValid()) return@async null
                    val hasWordTimings = lyrics.synced?.any { !it.words.isNullOrEmpty() } == true
                    val badge = buildString {
                        append("[${provider.displayName}]")
                        if (hasWordTimings) append(" • Word-Sync")
                        if (lyrics.synced?.any { !it.translation.isNullOrBlank() } == true) append(" • Trans")
                        if (lyrics.synced?.any { !it.romanization.isNullOrBlank() } == true) append(" • Romaji")
                        if (!album.isNullOrBlank()) append(" • $album")
                    }
                    val syntheticRecord = LrcLibResponse(
                        id = -(1000 + idx),
                        name = title,
                        artistName = artist ?: "",
                        albumName = badge,
                        duration = (durationSec ?: 0).toDouble(),
                        plainLyrics = lyrics.plain?.joinToString("\n"),
                        syncedLyrics = if (!lyrics.synced.isNullOrEmpty()) rawLyrics else null
                    )
                    LyricsSearchResult(
                        record = syntheticRecord,
                        lyrics = lyrics.copy(areFromRemote = true, provider = provider.displayName),
                        rawLyrics = rawLyrics
                    )
                }.getOrNull()
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun searchRemoteWithMode(
        song: Song,
        mode: RemoteLyricsMatchMode
    ): Result<Pair<String, List<LyricsSearchResult>>> = withContext(Dispatchers.IO) {
        try {
            LogUtils.d(this@LyricsRepositoryImpl, "Searching remote for lyrics for: ${song.title} by ${song.displayArtist} (mode=$mode)")

            val combinedQuery = "${song.title} ${song.displayArtist}"
            val cleanTitle = song.title.trim()
            val cleanArtist = song.displayArtist.trim()
            val extractedPairs = extractTitleArtistCandidates(song)

            val chainCandidatesDeferred = if (mode == RemoteLyricsMatchMode.CANDIDATE) {
                async {
                    searchChainProvidersAsResults(
                        title = cleanTitle,
                        artist = cleanArtist.takeIf { !isUnknownArtist(it) },
                        album = song.album,
                        durationSec = (song.duration / 1000).toInt().takeIf { it > 0 }
                    )
                }
            } else null

            val primaryStrategies = buildList {
                if (!isUnknownArtist(cleanArtist)) {
                    add(RemoteSearchStrategy("query+artist") {
                        lrcLibApiService.searchLyrics(query = combinedQuery, artistName = cleanArtist)
                    })
                    add(RemoteSearchStrategy("track+artist") {
                        lrcLibApiService.searchLyrics(trackName = cleanTitle, artistName = cleanArtist)
                    })
                }
                extractedPairs.drop(1).take(4).forEachIndexed { idx, (candTitle, candArtist) ->
                    if (candTitle.isNotBlank() && !isUnknownArtist(candArtist)) {
                        add(RemoteSearchStrategy("extracted_pair_$idx") {
                            lrcLibApiService.searchLyrics(trackName = candTitle, artistName = candArtist)
                        })
                    }
                }
            }

            val fallbackStrategies = buildList {
                val smartTitle = cleanTitleSmart(cleanTitle)
                if (smartTitle != cleanTitle && smartTitle.isNotBlank()) {
                    LogUtils.d(this@LyricsRepositoryImpl, "Adding smart search strategy for: '$smartTitle' (orig: '$cleanTitle')")
                    add(RemoteSearchStrategy("smart_track_only") {
                        lrcLibApiService.searchLyrics(trackName = smartTitle)
                    })
                }
                add(RemoteSearchStrategy("track_only") {
                    lrcLibApiService.searchLyrics(trackName = cleanTitle)
                })
                add(RemoteSearchStrategy("query_title_only") {
                    lrcLibApiService.searchLyrics(query = cleanTitle)
                })
            }

            var uniqueResults = runSearchStrategiesMerged(primaryStrategies)
            var rankedMatches = if (uniqueResults.isNotEmpty()) {
                rankRemoteLyricsMatches(song = song, responses = uniqueResults, mode = mode)
            } else {
                emptyList()
            }

            if (rankedMatches.isEmpty() || mode == RemoteLyricsMatchMode.CANDIDATE) {
                val fallbackResults = runSearchStrategiesFast(fallbackStrategies)
                if (fallbackResults.isNotEmpty()) {
                    uniqueResults = (uniqueResults + fallbackResults).distinctBy { it.id }
                    rankedMatches = rankRemoteLyricsMatches(
                        song = song,
                        responses = uniqueResults,
                        mode = mode
                    )
                }
            }

            val lrcLibResults = if (uniqueResults.isNotEmpty()) {
                rankedMatches.mapNotNull { match ->
                    val response = match.response
                    val rawLyrics = response.syncedLyrics ?: response.plainLyrics ?: return@mapNotNull null
                    val parsedLyrics = LyricsUtils.parseLyrics(rawLyrics).copy(areFromRemote = true, provider = "LRCLIB")
                    if (!parsedLyrics.isValid()) {
                        LogUtils.w(this@LyricsRepositoryImpl, "Parsed lyrics are empty for: ${song.title}")
                        return@mapNotNull null
                    }
                    val hasSynced = !response.syncedLyrics.isNullOrEmpty()
                    LogUtils.d(this@LyricsRepositoryImpl, "  Found: ${response.name} by ${response.artistName} (synced: $hasSynced)")
                    LyricsSearchResult(response, parsedLyrics, "[source:LRCLIB]\n$rawLyrics")
                }
            } else {
                emptyList()
            }

            val chainResults = chainCandidatesDeferred?.await().orEmpty()
            val mergedResults = chainResults + lrcLibResults

            if (mergedResults.isNotEmpty()) {
                val syncedCount = mergedResults.count { !it.record.syncedLyrics.isNullOrEmpty() }
                LogUtils.d(this@LyricsRepositoryImpl, "Found ${mergedResults.size} lyrics for: ${song.title} ($syncedCount with synced)")
                Result.success(Pair(combinedQuery, mergedResults))
            } else {
                LogUtils.d(this@LyricsRepositoryImpl, "No matching lyrics found for: ${song.title}")
                Result.failure(NoLyricsFoundException(combinedQuery))
            }
        } catch (e: Exception) {
            LogUtils.e(this@LyricsRepositoryImpl, e, "Error searching remote for lyrics")
            when {
                e is SocketTimeoutException -> Result.failure(LyricsException(context.getString(R.string.lyrics_fetch_timeout), e))
                e is UnknownHostException -> Result.failure(LyricsException(context.getString(R.string.lyrics_network_error), e))
                e is IOException -> Result.failure(LyricsException(context.getString(R.string.lyrics_network_error), e))
                e is HttpException -> Result.failure(LyricsException(context.getString(R.string.lyrics_server_error, e.code()), e))
                else -> Result.failure(LyricsException(context.getString(R.string.lyrics_failed_to_search), e))
            }
        }
    }

    override suspend fun searchRemoteByQuery(title: String, artist: String?): Result<Pair<String, List<LyricsSearchResult>>> = withContext(Dispatchers.IO) {
        try {
            val providerPrefixRegex = Regex("^\\[provider:([a-z0-9_]+)]\\s*(.*)$", RegexOption.IGNORE_CASE)
            val prefixMatch = providerPrefixRegex.find(title.trim())
            val requestedProvider = prefixMatch?.groupValues?.getOrNull(1)?.let { LyricsProviderType.fromId(it) }
            val cleanTitle = (prefixMatch?.groupValues?.getOrNull(2) ?: title).trim()
            val cleanArtist = artist?.trim()?.takeIf { it.isNotBlank() }
            val query = listOfNotNull(
                cleanTitle.takeIf { it.isNotBlank() },
                cleanArtist
            ).joinToString(" ")

            LogUtils.d(this@LyricsRepositoryImpl, "Manual lyrics search: title=$cleanTitle, artist=$cleanArtist, provider=${requestedProvider?.id ?: "all"}")

            if (requestedProvider != null && requestedProvider != LyricsProviderType.LRCLIB) {
                val singleProviderResults = searchChainProvidersAsResults(
                    title = cleanTitle,
                    artist = cleanArtist,
                    album = null,
                    durationSec = null,
                    specificProvider = requestedProvider
                )
                return@withContext if (singleProviderResults.isNotEmpty()) {
                    Result.success(Pair("${requestedProvider.displayName}: $query", singleProviderResults))
                } else {
                    Result.failure(NoLyricsFoundException("${requestedProvider.displayName}: $query"))
                }
            }

            val chainDeferred = if (requestedProvider == null) {
                async {
                    searchChainProvidersAsResults(
                        title = cleanTitle,
                        artist = cleanArtist,
                        album = null,
                        durationSec = null
                    )
                }
            } else null

            val strategies = buildList {
                add(RemoteSearchStrategy("manual_query") { lrcLibApiService.searchLyrics(query = query) })
                if (!cleanArtist.isNullOrBlank()) {
                    add(
                        RemoteSearchStrategy("manual_track+artist") {
                            lrcLibApiService.searchLyrics(trackName = cleanTitle, artistName = cleanArtist)
                        }
                    )
                }
            }

            // Run both in parallel and take the first non-empty result set.
            val responses = runSearchStrategiesFast(strategies)
            val lrcLibResults = responses.mapNotNull { response ->
                val rawLyrics = response.syncedLyrics ?: response.plainLyrics ?: return@mapNotNull null
                val parsed = LyricsUtils.parseLyrics(rawLyrics).copy(areFromRemote = true, provider = "LRCLIB")
                if (!parsed.isValid()) return@mapNotNull null

                LyricsSearchResult(response, parsed, "[source:LRCLIB]\n$rawLyrics")
            }.sortedByDescending { !it.record.syncedLyrics.isNullOrEmpty() }

            val chainResults = chainDeferred?.await().orEmpty()
            val allResults = chainResults + lrcLibResults

            if (allResults.isEmpty()) {
                Result.failure(NoLyricsFoundException(query))
            } else {
                Result.success(Pair(query, allResults))
            }
        } catch (e: Exception) {
            LogUtils.e(this@LyricsRepositoryImpl, e, "Manual search failed")
            Result.failure(LyricsException(context.getString(R.string.lyrics_failed_to_search), e)
            )
        }
    }

    override suspend fun updateLyrics(songId: Long, lyricsContent: String): Unit = withContext(Dispatchers.IO) {
        LogUtils.d(this@LyricsRepositoryImpl, "Updating lyrics for songId: $songId")

        val parsedLyrics = LyricsUtils.parseLyrics(lyricsContent)
        if (!parsedLyrics.isValid()) {
            LogUtils.w(this@LyricsRepositoryImpl, "Attempted to save empty lyrics for songId: $songId")
            return@withContext
        }

        lyricsDao.insert(
             com.theveloper.pixelplay.data.database.LyricsEntity(
                 songId = songId,
                 content = lyricsContent,
                 isSynced = parsedLyrics.synced?.isNotEmpty() == true,
                 source = "manual"
             )
        )

        val cacheKey = generateCacheKey(songId.toString())
        lyricsCache.put(cacheKey, parsedLyrics)
        saveLocalLyricsJsonById(songId.toString(), parsedLyrics)
        LogUtils.d(this@LyricsRepositoryImpl, "Updated and cached lyrics for songId: $songId")
    }

    override suspend fun resetLyrics(songId: Long): Unit = withContext(Dispatchers.IO) {
        LogUtils.d(this, "Resetting lyrics for songId: $songId")
        val cacheKey = generateCacheKey(songId.toString())
        lyricsCache.remove(cacheKey)
        try {
            lyricsDao.deleteLyrics(songId)
        } catch (e: Exception) {
            Log.w(TAG, "Error removing lyrics from DB for ID: $songId", e)
        }
        
        // Also remove JSON cache
        try {
            val file = File(context.filesDir, "lyrics/${songId}.json")
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting JSON cache: ${e.message}")
        }
    }

    override suspend fun resetAllLyrics(): Unit = withContext(Dispatchers.IO) {
        LogUtils.d(this, "Resetting all lyrics")
        lyricsCache.evictAll()
        lyricsDao.deleteAll()
        
        // Also clear JSON cache directory
        try {
            val lyricsDir = File(context.filesDir, "lyrics")
            if (lyricsDir.exists()) {
                lyricsDir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing JSON cache: ${e.message}")
        }
    }

    override suspend fun scanAndAssignLocalLrcFiles(
        songs: List<Song>,
        onProgress: suspend (current: Int, total: Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        LogUtils.d(this@LyricsRepositoryImpl, "Starting bulk scan for .lrc files for ${songs.size} songs")
        val updatedCount = AtomicInteger(0)
        val processedCount = AtomicInteger(0)
        val total = songs.size

        val idsWithPersistedLyrics = songs
            .mapNotNull { it.id.toLongOrNull() }
            .chunked(900)
            .flatMap { chunk -> lyricsDao.getSongIdsWithLyrics(chunk) }
            .toHashSet()

        // Only scan songs that don't already have persisted lyrics.
        val songsToScan = songs.filter { song ->
            val songId = song.id.toLongOrNull()
            song.lyrics.isNullOrBlank() && (songId == null || songId !in idsWithPersistedLyrics)
        }
        val skippedCount = total - songsToScan.size
        processedCount.addAndGet(skippedCount)
        
        LogUtils.d(this@LyricsRepositoryImpl, "Skipping $skippedCount songs that already have lyrics. Scanning ${songsToScan.size} songs.")
        
        onProgress(processedCount.get(), total)
        
        if (songsToScan.isEmpty()) {
            return@withContext 0
        }

        val semaphore = Semaphore(8) // Limit concurrency

        coroutineScope {
            songsToScan.map { song ->
                async {
                    semaphore.withPermit {
                        try {
                            // Find lyrics file
                            val songFile = File(song.path)
                            val directory = songFile.parentFile
                            
                            if (directory != null && directory.exists()) {
                                var foundFile: File? = null
                                
                                // Strategy 1: Exact match name
                                for (extension in LyricsImportSecurity.supportedFileExtensions()) {
                                    val exactMatch = File(directory, "${songFile.nameWithoutExtension}.$extension")
                                    if (exactMatch.exists() && exactMatch.canRead()) {
                                        foundFile = exactMatch
                                        break
                                    }
                                }
                                
                                // Strategy 2: Artist - Title
                                if (foundFile == null) {
                                    val cleanArtist = song.displayArtist.replace(Regex("[^a-zA-Z0-9]"), "_")
                                    val cleanTitle = song.title.replace(Regex("[^a-zA-Z0-9]"), "_")
                                    for (extension in LyricsImportSecurity.supportedFileExtensions()) {
                                        val altMatch = File(directory, "${cleanArtist}_${cleanTitle}.$extension")
                                        if (altMatch.exists() && altMatch.canRead()) {
                                            foundFile = altMatch
                                            break
                                        }
                                    }
                                }
                                
                                if (foundFile != null) {
                                    val validated = readValidatedLocalLyrics(foundFile)
                                    if (validated != null) {
                                        try {
                                            lyricsDao.insert(
                                                 com.theveloper.pixelplay.data.database.LyricsEntity(
                                                     songId = song.id.toLong(),
                                                     content = validated.sanitizedContent,
                                                     isSynced = validated.parsedLyrics.synced?.isNotEmpty() == true,
                                                     source = "local_file"
                                                 )
                                            )
                                            updatedCount.incrementAndGet()
                                            LogUtils.d(this@LyricsRepositoryImpl, "Auto-assigned lyrics from ${foundFile.name}")
                                        } catch (e: Exception) {
                                            Log.w(TAG, "Skipping DB update for ID in scanner: ${song.id}", e)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error scanning lyrics for ${song.title}: ${e.message}")
                        }
                        
                        val current = processedCount.incrementAndGet()
                        if (current % 20 == 0 || current == total) {
                            onProgress(current, total)
                        }
                    }
                }
            }.awaitAll()
        }
        
        LogUtils.d(this@LyricsRepositoryImpl, "Bulk scan complete. Updated ${updatedCount.get()} songs.")
        return@withContext updatedCount.get()
    }

    override fun clearCache() {
        LogUtils.d(this, "Clearing lyrics in-memory cache")
        lyricsCache.evictAll()
    }

    private fun generateCacheKey(songId: String): String = songId

    private fun createTempFileFromUri(uri: Uri): File? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) cursor.getString(nameIndex) else "temp_audio"
                    } else "temp_audio"
                } ?: "temp_audio"

                val tempFile = File.createTempFile("lyrics_", "_$fileName", context.cacheDir)
                FileOutputStream(tempFile).use { output ->
                    inputStream.copyTo(output)
                }
                tempFile
            }
        } catch (e: Exception) {
            LogUtils.e(this, e, "Error creating temp file from URI")
            null
        }
    }

    private fun cleanTitleSmart(title: String): String {
        // 1. Remove leading digits/spaces/dots/hyphens (e.g., "01 ", "01. ", "01 - ")
        // \uFF0D is the fullwidth hyphen-minus character used in fullwidth/CJK text.
        var cleaned = title.replace(Regex("^[\\d\\s.\\-\\uFF0D]+"), "")
        
        // 2. Truncate at first spaced hyphen separator or opening bracket (preserving hyphenated names like M-TP, Jay-Z)
        val bracketSplitRegex = Regex("""(?:\s+[-\u2013\u2014\uFF0D]\s+)|[\(\[\{\uFF08\uFF3B\uFF5B\u3010\u300E\u300C\u3014\u3008\u300A]""")
        cleaned = cleaned.split(bracketSplitRegex).firstOrNull() ?: cleaned
        
        // 3. Trim whitespace
        return cleaned.trim()
    }

    private fun romanizeForMatch(text: String): String {
        return when {
            MultiLangRomanizer.isJapanese(text) -> MultiLangRomanizer.romanizeJapanese(text) ?: text
            MultiLangRomanizer.isChinese(text) -> MultiLangRomanizer.romanizeChinese(text) ?: text
            MultiLangRomanizer.isKorean(text) -> MultiLangRomanizer.romanizeKorean(text)
            else -> text
        }
    }
}

data class LyricsSearchResult(val record: LrcLibResponse, val lyrics: Lyrics, val rawLyrics: String)

data class NoLyricsFoundException(val query: String? = null) : Exception()

class LyricsException(message: String, cause: Throwable? = null) : Exception(message, cause)
