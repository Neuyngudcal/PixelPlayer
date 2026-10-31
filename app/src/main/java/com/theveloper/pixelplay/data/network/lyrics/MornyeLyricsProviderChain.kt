package com.theveloper.pixelplay.data.network.lyrics

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.preferences.dataStore
import com.theveloper.pixelplay.utils.LyricsUtils
import com.theveloper.pixelplay.utils.TtmlLyricsParser
import java.io.IOException
import java.text.Normalizer
import java.util.Base64
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

enum class LyricsProviderType(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val websiteUri: String
) {
    ZARZ_LRC(
        id = "zarz_lrc",
        displayName = "Zarz-LRC",
        subtitle = "Apple Music v1 Syllable, Backing Vocals, Translation & Romaji",
        websiteUri = "https://api.zarz.moe"
    ),
    APPLE_MUSIC(
        id = "apple_music",
        displayName = "Apple Music",
        subtitle = "Word-by-word ELRC, Multi-singer & Supplements",
        websiteUri = "https://music.apple.com"
    ),
    LRCRED(
        id = "lrcred",
        displayName = "lrc.red",
        subtitle = "ISRC-verified Apple TTML word-synced lyrics",
        websiteUri = "https://lrc.red"
    ),
    LYRICSPLUS(
        id = "lyricsplus",
        displayName = "LyricsPlus",
        subtitle = "KPOE v2 Word-by-word & Backing Vocals",
        websiteUri = "https://lyricsplus.prjktla.my.id"
    ),
    BETTERLYRICS(
        id = "betterlyrics",
        displayName = "BetterLyrics",
        subtitle = "Cached Apple TTML word-synced lyrics",
        websiteUri = "https://lyrics-api.boidu.dev"
    ),
    LRCLIB(
        id = "lrclib",
        displayName = "LRCLIB",
        subtitle = "Community synced LRC database",
        websiteUri = "https://lrclib.net"
    ),
    NETEASE(
        id = "netease",
        displayName = "NetEase",
        subtitle = "Synced LRC + Translation & Romanization",
        websiteUri = "https://music.163.com"
    ),
    KUGOU(
        id = "kugou",
        displayName = "Kugou",
        subtitle = "Direct LRC & Karaoke lyrics",
        websiteUri = "https://www.kugou.com"
    ),
    QQMUSIC(
        id = "qqmusic",
        displayName = "QQ Music",
        subtitle = "Direct CJK & Asian music lyrics",
        websiteUri = "https://y.qq.com"
    );

    companion object {
        val DEFAULT_ORDER: List<LyricsProviderType> = listOf(
            ZARZ_LRC,
            APPLE_MUSIC,
            LRCRED,
            LYRICSPLUS,
            BETTERLYRICS,
            LRCLIB,
            NETEASE,
            KUGOU,
            QQMUSIC
        )

        fun fromId(id: String): LyricsProviderType? =
            entries.find { it.id.equals(id.trim(), ignoreCase = true) }
                ?: if (id.equals("mornye_lyrics", ignoreCase = true) || id.equals("spotiflac_lyrics", ignoreCase = true)) {
                    ZARZ_LRC
                } else {
                    null
                }

        fun resolveWebsiteUri(providerName: String?): String {
            if (providerName.isNullOrBlank()) return LRCLIB.websiteUri
            val normalized = providerName.trim()
            if (normalized.equals("AMLLDB", ignoreCase = true)) {
                return "https://github.com/Steve-xmh/amll-ttml-db"
            }
            return entries.find {
                it.displayName.equals(normalized, ignoreCase = true) ||
                    it.id.equals(normalized, ignoreCase = true)
            }?.websiteUri ?: LRCLIB.websiteUri
        }
    }
}

data class LyricsProviderConfig(
    val provider: LyricsProviderType,
    val enabled: Boolean
)

data class ProviderChainLyricsHit(
    val provider: LyricsProviderType,
    val rawLrc: String,
    val parsedLyrics: Lyrics
)

object LyricsProviderPreferences {
    private val KEY_PROVIDER_CHAIN = stringPreferencesKey("mornye_lyrics_provider_chain_v1")

    fun observeProviderConfigs(context: Context): Flow<List<LyricsProviderConfig>> {
        return context.dataStore.data.map { prefs ->
            decodeChain(prefs[KEY_PROVIDER_CHAIN])
        }
    }

    suspend fun getProviderConfigs(context: Context): List<LyricsProviderConfig> {
        return runCatching {
            val raw = context.dataStore.data.first()[KEY_PROVIDER_CHAIN]
            decodeChain(raw)
        }.getOrElse { defaultConfigs() }
    }

    suspend fun saveProviderConfigs(context: Context, configs: List<LyricsProviderConfig>) {
        val safeConfigs = if (configs.none { it.enabled }) {
            configs.mapIndexed { index, item -> if (index == 0) item.copy(enabled = true) else item }
        } else {
            configs
        }
        val serialized = safeConfigs.joinToString(",") { "${it.provider.id}:${if (it.enabled) "1" else "0"}" }
        context.dataStore.edit { prefs ->
            prefs[KEY_PROVIDER_CHAIN] = serialized
        }
    }

    fun defaultConfigs(): List<LyricsProviderConfig> =
        LyricsProviderType.DEFAULT_ORDER.map { LyricsProviderConfig(it, enabled = true) }

    private fun decodeChain(raw: String?): List<LyricsProviderConfig> {
        if (raw.isNullOrBlank()) return defaultConfigs()
        val seen = mutableSetOf<LyricsProviderType>()
        val parsed = mutableListOf<LyricsProviderConfig>()
        raw.split(",").forEach { token ->
            val parts = token.split(":")
            val id = parts.getOrNull(0)?.trim().orEmpty()
            val enabled = parts.getOrNull(1)?.trim() != "0"
            val type = LyricsProviderType.fromId(id) ?: return@forEach
            if (seen.add(type)) {
                parsed += LyricsProviderConfig(type, enabled)
            }
        }
        LyricsProviderType.DEFAULT_ORDER.forEach { type ->
            if (seen.add(type)) {
                parsed += LyricsProviderConfig(type, enabled = true)
            }
        }
        if (parsed.none { it.enabled } && parsed.isNotEmpty()) {
            parsed[0] = parsed[0].copy(enabled = true)
        }
        return parsed
    }
}

/**
 * Port of Mornye's multi-provider lyrics engine (`BuiltinLyricsClient` + `LyricsService`):
 * - Supports Zarz-LRC (`api.zarz.moe`), Apple Music (`lyrics.paxsenix.org`), lrc.red, LyricsPlus (KPOE),
 *   BetterLyrics, LRCLIB, NetEase, Kugou, and QQ Music.
 * - Executes the user's reorderable chain with up to 3 concurrent workers and a 5-second priority
 *   grace window so higher-priority providers win without blocking fast fallbacks indefinitely.
 */
class MornyeLyricsProviderChain(
    private val baseOkHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "MornyeLyricsChain"
        private const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
        private const val UNAVAILABLE_COOLDOWN_MS = 10 * 60 * 1000L // 10 minutes
        private const val PRIORITY_GRACE_MS = 5_000L // 5 seconds
        private const val APPLE_TOKEN_TTL_MS = 30 * 60 * 1000L // 30 minutes

        private val APPLE_INDEX_SCRIPT_REGEX = Regex("""/assets/index~[^"' <]+\.js""")
        private val APPLE_JWT_TOKEN_REGEX = Regex("""eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
        private val ISRC_REGEX = Regex("""^[A-Z0-9]{12}$""")
    }

    private val standardClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val zarzClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(65, TimeUnit.SECONDS)
            .build()
    }

    private val appleTokenMutex = Mutex()
    @Volatile
    private var cachedAppleToken: String? = null
    @Volatile
    private var cachedAppleTokenTimeMs: Long = 0L

    private val providerUnavailableUntil = ConcurrentHashMap<LyricsProviderType, Long>()

    private fun isProviderHealthy(provider: LyricsProviderType): Boolean {
        val until = providerUnavailableUntil[provider] ?: return true
        if (System.currentTimeMillis() >= until) {
            providerUnavailableUntil.remove(provider)
            return true
        }
        return false
    }

    private fun markProviderUnavailable(provider: LyricsProviderType) {
        providerUnavailableUntil[provider] = System.currentTimeMillis() + UNAVAILABLE_COOLDOWN_MS
    }

    /**
     * Searches the user's ordered provider chain using Mornye's bounded concurrency (max 3 workers)
     * + 5-second priority grace window.
     */
    suspend fun searchOrderedChain(
        context: Context,
        title: String,
        artist: String,
        durationMs: Long,
        lrclibFetcher: suspend () -> Pair<Lyrics, String>?
    ): ProviderChainLyricsHit? = withContext(Dispatchers.IO) {
        val configs = LyricsProviderPreferences.getProviderConfigs(context)
        val healthyProviders = configs
            .filter { it.enabled && isProviderHealthy(it.provider) }
            .map { it.provider }
            .ifEmpty { listOf(LyricsProviderType.ZARZ_LRC, LyricsProviderType.LRCLIB) }

        val durationSec = if (durationMs > 0L) durationMs / 1000.0 else 0.0
        val semaphore = Semaphore(3)

        coroutineScope {
            // Launch tasks bounded by semaphore(3) in priority order
            val deferreds = healthyProviders.mapIndexed { index, provider ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            fetchFromSingleProvider(
                                provider = provider,
                                title = title,
                                artist = artist,
                                durationSec = durationSec,
                                lrclibFetcher = lrclibFetcher
                            )
                        }.getOrNull()?.let { index to it }
                    }
                }
            }

            // Priority resolution with 5s grace window for earlier providers
            for (i in deferreds.indices) {
                val currentDeferred = deferreds[i]
                // Check if any lower-index (higher priority) is i itself, wait for it or check if a later one finished
                val firstCompletedLater = deferreds.drop(i + 1).firstOrNull { it.isCompleted && it.getCompleted() != null }
                val resultAtI = if (firstCompletedLater != null && !currentDeferred.isCompleted) {
                    // A lower-priority provider already succeeded! Give this higher-priority provider up to 5s grace
                    withTimeoutOrNull(PRIORITY_GRACE_MS) { currentDeferred.await() }
                } else {
                    currentDeferred.await()
                }

                if (resultAtI != null) {
                    deferreds.forEach { if (it.isActive) it.cancel() }
                    return@coroutineScope resultAtI.second
                }
            }
            null
        }
    }

    /**
     * Queries a single specific provider (used both by the chain and by manual provider picker).
     */
    suspend fun fetchFromSingleProvider(
        provider: LyricsProviderType,
        title: String,
        artist: String,
        durationSec: Double,
        lrclibFetcher: (suspend () -> Pair<Lyrics, String>?)? = null
    ): ProviderChainLyricsHit? = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        if (cleanTitle.isEmpty()) return@withContext null

        try {
            val rawLrc = when (provider) {
                LyricsProviderType.ZARZ_LRC -> fetchZarzLrc(cleanTitle, cleanArtist, durationSec)
                LyricsProviderType.APPLE_MUSIC -> fetchAppleMusicPaxsenix(cleanTitle, cleanArtist, durationSec)
                LyricsProviderType.LRCRED -> fetchLrcRed(cleanTitle, cleanArtist, durationSec)
                LyricsProviderType.LYRICSPLUS -> fetchLyricsPlus(cleanTitle, cleanArtist, durationSec)
                LyricsProviderType.BETTERLYRICS -> fetchBetterLyrics(cleanTitle, cleanArtist, durationSec)
                LyricsProviderType.LRCLIB -> {
                    val pair = lrclibFetcher?.invoke() ?: return@withContext null
                    val taggedRaw = if (pair.second.contains("[source:", ignoreCase = true)) {
                        pair.second
                    } else {
                        "[source:${provider.displayName}]\n${pair.second}"
                    }
                    return@withContext ProviderChainLyricsHit(
                        provider = provider,
                        rawLrc = taggedRaw,
                        parsedLyrics = pair.first.copy(
                            areFromRemote = true,
                            provider = provider.displayName
                        )
                    )
                }
                LyricsProviderType.NETEASE -> fetchNetease(cleanTitle, cleanArtist, durationSec)
                LyricsProviderType.KUGOU -> fetchKugou(cleanTitle, cleanArtist, durationSec)
                LyricsProviderType.QQMUSIC -> fetchQqMusic(cleanTitle, cleanArtist, durationSec)
            } ?: return@withContext null

            val taggedRaw = if (rawLrc.contains("[source:", ignoreCase = true)) {
                rawLrc
            } else {
                "[source:${provider.displayName}]\n$rawLrc"
            }
            val parsed = LyricsUtils.parseLyrics(taggedRaw).copy(
                areFromRemote = true,
                provider = provider.displayName
            )
            if (parsed.synced.isNullOrEmpty() && parsed.plain.isNullOrEmpty()) {
                return@withContext null
            }
            Log.d(TAG, "Successfully fetched lyrics from ${provider.displayName} for $cleanArtist - $cleanTitle")
            ProviderChainLyricsHit(
                provider = provider,
                rawLrc = taggedRaw,
                parsedLyrics = parsed
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: ProviderUnavailableException) {
            Log.w(TAG, "Provider ${provider.displayName} unavailable, cooling down: ${e.message}")
            markProviderUnavailable(provider)
            null
        } catch (e: Exception) {
            Log.d(TAG, "Provider ${provider.displayName} miss for $cleanArtist - $cleanTitle: ${e.message}")
            null
        }
    }

    // ─── 1. Apple Music Catalog & Token + Zarz-LRC + Apple Music (Paxsenix) ────

    private suspend fun getAppleMusicBearerToken(forceRefresh: Boolean = false): String? {
        return appleTokenMutex.withLock {
            val now = System.currentTimeMillis()
            if (!forceRefresh && !cachedAppleToken.isNullOrEmpty() && (now - cachedAppleTokenTimeMs) < APPLE_TOKEN_TTL_MS) {
                return@withLock cachedAppleToken
            }

            val homeReq = Request.Builder()
                .url("https://beta.music.apple.com")
                .header("User-Agent", BROWSER_UA)
                .get()
                .build()

            val html = standardClient.newCall(homeReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withLock null
                resp.body.string()
            }

            val scriptPath = APPLE_INDEX_SCRIPT_REGEX.find(html)?.value ?: return@withLock null
            val scriptReq = Request.Builder()
                .url("https://beta.music.apple.com$scriptPath")
                .header("User-Agent", BROWSER_UA)
                .get()
                .build()

            val js = standardClient.newCall(scriptReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withLock null
                resp.body.string()
            }

            val token = APPLE_JWT_TOKEN_REGEX.find(js)?.value ?: return@withLock null
            cachedAppleToken = token
            cachedAppleTokenTimeMs = System.currentTimeMillis()
            token
        }
    }

    private suspend fun resolveAppleCatalogSongId(
        title: String,
        artist: String,
        durationSec: Double
    ): String? {
        val query = "$title $artist".trim()
        if (query.isEmpty()) return null

        var token = getAppleMusicBearerToken(forceRefresh = false) ?: return null
        var searchJson = executeAppleCatalogSearch(token, query)
        if (searchJson == null) {
            token = getAppleMusicBearerToken(forceRefresh = true) ?: return null
            searchJson = executeAppleCatalogSearch(token, query) ?: return null
        }

        val songsOrder = searchJson.getAsJsonObject("results")
            ?.getAsJsonObject("songs")
            ?.getAsJsonArray("data")
            ?: return null
        val resourcesSongs = searchJson.getAsJsonObject("resources")
            ?.getAsJsonObject("songs")
            ?: return null

        var bestId: String? = null
        var bestScore = -1

        for (item in songsOrder) {
            val id = item.asJsonObjectOrNull()?.getStringOrNull("id") ?: continue
            val attrs = resourcesSongs.getAsJsonObject(id)?.getAsJsonObject("attributes") ?: continue
            val candName = attrs.getStringOrNull("name") ?: continue
            val candArtist = attrs.getStringOrNull("artistName").orEmpty()
            val candDurSec = (attrs.getLongOrNull("durationInMillis") ?: 0L) / 1000.0

            val score = scoreTrackCandidate(
                candTitle = candName,
                candArtist = candArtist,
                candDurationSec = candDurSec,
                reqTitle = title,
                reqArtist = artist,
                reqDurationSec = durationSec
            ) ?: continue

            if (score > bestScore) {
                bestScore = score
                bestId = id
            }
        }

        return bestId
    }

    private fun executeAppleCatalogSearch(token: String, query: String): JsonObject? {
        val url = Uri.parse("https://amp-api.music.apple.com/v1/catalog/us/search")
            .buildUpon()
            .appendQueryParameter("term", query)
            .appendQueryParameter("types", "songs")
            .appendQueryParameter("limit", "25")
            .appendQueryParameter("l", "en-US")
            .appendQueryParameter("platform", "web")
            .appendQueryParameter("format[resources]", "map")
            .appendQueryParameter("include[songs]", "artists")
            .appendQueryParameter("extend", "artistUrl")
            .build()
            .toString()

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Origin", "https://music.apple.com")
            .header("Referer", "https://music.apple.com/")
            .header("User-Agent", BROWSER_UA)
            .header("Accept", "application/json")
            .header("Accept-Language", "en-US,en;q=0.5")
            .header("x-apple-renewal", "true")
            .get()
            .build()

        return standardClient.newCall(req).execute().use { resp ->
            when {
                resp.code == 401 -> null
                !resp.isSuccessful -> throw ProviderUnavailableException("Apple catalog HTTP ${resp.code}")
                else -> JsonParser.parseString(resp.body.string()).asJsonObjectOrNull()
            }
        }
    }

    private suspend fun fetchZarzLrc(title: String, artist: String, durationSec: Double): String? {
        val songId = resolveAppleCatalogSongId(title, artist, durationSec) ?: return null
        val url = Uri.parse("https://api.zarz.moe/apple-music/lyrics")
            .buildUpon()
            .appendQueryParameter("id", songId)
            .appendQueryParameter("v", "1")
            .build()
            .toString()

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_UA)
            .header("Accept", "application/json")
            .get()
            .build()

        val raw = zarzClient.newCall(req).execute().use { resp ->
            when {
                resp.code == 404 -> return null
                resp.code == 429 || resp.code >= 500 -> throw ProviderUnavailableException("Zarz-LRC HTTP ${resp.code}")
                !resp.isSuccessful -> return null
                else -> resp.body.string().trim()
            }
        }
        if (raw.isEmpty() || raw.startsWith("<")) {
            throw ProviderUnavailableException("Zarz-LRC returned non-JSON payload")
        }
        return parseAppleJsonPayloadToEnhancedLrc(raw)
    }

    private suspend fun fetchAppleMusicPaxsenix(title: String, artist: String, durationSec: Double): String? {
        val songId = resolveAppleCatalogSongId(title, artist, durationSec) ?: return null
        val url = Uri.parse("https://lyrics.paxsenix.org/apple-music/lyrics")
            .buildUpon()
            .appendQueryParameter("id", songId)
            .build()
            .toString()

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_UA)
            .header("Accept", "application/json")
            .get()
            .build()

        val raw = standardClient.newCall(req).execute().use { resp ->
            when {
                resp.code == 404 -> return null
                resp.code == 429 || resp.code >= 500 -> throw ProviderUnavailableException("Apple Music proxy HTTP ${resp.code}")
                !resp.isSuccessful -> return null
                else -> resp.body.string().trim()
            }
        }
        if (raw.isEmpty() || raw.startsWith("<")) {
            throw ProviderUnavailableException("Apple Music proxy returned invalid payload")
        }
        return parseAppleJsonPayloadToEnhancedLrc(raw)
    }

    /**
     * Parses Zarz-LRC / Apple Music v1 JSON (`type = Syllable | Line | Unsynced`, `content[]`,
     * `backgroundLines[]`, `elrcMultiPerson`, `elrc`, and `metadata.translations` / `transliterations`)
     * into Canonical Enhanced LRC.
     */
    internal fun parseAppleJsonPayloadToEnhancedLrc(raw: String): String? {
        val element = runCatching { JsonParser.parseString(raw) }.getOrNull() ?: return raw.takeIf { it.startsWith("[") }
        if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            return element.asString.trim().takeIf { it.isNotEmpty() }
        }
        val obj = element.asJsonObjectOrNull() ?: return null

        val type = obj.getStringOrNull("type")?.lowercase(Locale.ROOT).orEmpty()
        val contentArray = obj.getAsJsonArray("content")
        val lineBuilder = mutableListOf<String>()
        val lineStartTimes = mutableListOf<Int>()

        if (contentArray != null && contentArray.size() > 0) {
            val isSyllable = type == "syllable" || type.isEmpty()
            for (lineEl in contentArray) {
                val lineObj = lineEl.asJsonObjectOrNull() ?: continue
                val lineStartMs = lineObj.getIntOrNull("timestamp") ?: continue
                val lineEndMs = lineObj.getIntOrNull("endtime")
                val oppositeTurn = lineObj.getBooleanOrNull("oppositeTurn") == true ||
                    lineObj.getStringOrNull("agent")?.equals("v2", ignoreCase = true) == true
                val voicePrefix = if (oppositeTurn) "v2:" else "v1:"

                val textArray = lineObj.getAsJsonArray("text")
                val formattedBody = formatAppleSyllableArray(textArray, isSyllable, lineEndMs)
                if (formattedBody.isNotBlank()) {
                    lineStartTimes += lineStartMs
                    lineBuilder += "[${formatLrcMs(lineStartMs)}]$voicePrefix$formattedBody"
                }

                // Background lines (Zarz-LRC `backgroundLines` or Paxsenix `backgroundText`)
                val bgLines = lineObj.getAsJsonArray("backgroundLines")
                if (bgLines != null && bgLines.size() > 0) {
                    for (bgEl in bgLines) {
                        val bgObj = bgEl.asJsonObjectOrNull() ?: continue
                        val bgStart = bgObj.getIntOrNull("timestamp") ?: lineStartMs
                        val bgEnd = bgObj.getIntOrNull("endtime") ?: lineEndMs
                        val bgBody = formatAppleSyllableArray(bgObj.getAsJsonArray("text"), isSyllable, bgEnd)
                        if (bgBody.isNotBlank()) {
                            lineBuilder += "[${formatLrcMs(bgStart)}][bg:$bgBody]"
                        }
                    }
                } else if (lineObj.getBooleanOrNull("background") == true) {
                    val bgText = lineObj.getAsJsonArray("backgroundText")
                    val bgBody = formatAppleSyllableArray(bgText, isSyllable, lineEndMs)
                    if (bgBody.isNotBlank()) {
                        lineBuilder += "[${formatLrcMs(lineStartMs)}][bg:$bgBody]"
                    }
                }
            }
        } else {
            val elrcMulti = obj.getStringOrNull("elrcMultiPerson")?.trim()
            val elrc = obj.getStringOrNull("elrc")?.trim()
            val ttml = obj.getStringOrNull("ttmlContent")?.trim()
            val plain = obj.getStringOrNull("plain")?.trim()
            when {
                !elrcMulti.isNullOrEmpty() -> lineBuilder += elrcMulti
                !elrc.isNullOrEmpty() -> lineBuilder += elrc
                !ttml.isNullOrEmpty() -> TtmlLyricsParser.parseToEnhancedLrc(ttml)?.let { lineBuilder += it }
                !plain.isNullOrEmpty() -> return plain
            }
        }

        if (lineBuilder.isEmpty()) return null

        // Extract metadata translations & transliterations (`apple_supplements`)
        val metadataObj = obj.getAsJsonObject("metadata")
        val headers = mutableListOf<String>()
        if (metadataObj != null) {
            val deviceLang = Locale.getDefault().language.lowercase(Locale.ROOT)
            extractAppleMetadataSupplement(
                groups = metadataObj.getAsJsonArray("transliterations"),
                languageRanker = { if (it.contains("latn", ignoreCase = true)) 2 else 0 }
            ).forEach { (ts, text) ->
                val b64 = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
                headers += "[x-romaji:$ts:$b64]"
            }
            extractAppleMetadataSupplement(
                groups = metadataObj.getAsJsonArray("translations"),
                languageRanker = { lang ->
                    val normalized = lang.trim().lowercase(Locale.ROOT)
                    when {
                        deviceLang.isNotEmpty() && normalized.startsWith(deviceLang) -> 2
                        normalized.startsWith("en") -> 1
                        else -> 0
                    }
                }
            ).forEach { (ts, text) ->
                val b64 = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
                headers += "[x-translation:$ts:$b64]"
            }
        }

        return if (headers.isEmpty()) {
            lineBuilder.joinToString("\n")
        } else {
            headers.joinToString("\n") + "\n" + lineBuilder.joinToString("\n")
        }
    }

    private fun formatAppleSyllableArray(
        details: com.google.gson.JsonArray?,
        wordTiming: Boolean,
        fallbackLineEndMs: Int?
    ): String {
        if (details == null || details.size() == 0) return ""
        val sb = StringBuilder()
        var lastTimestampTag = ""
        for (i in 0 until details.size()) {
            val detail = details[i].asJsonObjectOrNull() ?: continue
            val text = detail.getStringOrNull("text") ?: continue
            if (text.trim().isEmpty()) continue
            val part = detail.getBooleanOrNull("part") == true
            val startMs = detail.getIntOrNull("timestamp")
            val endMs = detail.getIntOrNull("endtime") ?: if (i == details.size() - 1) fallbackLineEndMs else null

            if (wordTiming && startMs != null) {
                val startTag = "<${formatLrcMs(startMs)}>"
                if (startTag != lastTimestampTag) {
                    sb.append(startTag)
                    lastTimestampTag = startTag
                }
            }
            sb.append(text)
            if (!part) {
                sb.append(' ')
            }
            if (wordTiming && endMs != null) {
                val endTag = "<${formatLrcMs(endMs)}>"
                sb.append(endTag)
                lastTimestampTag = endTag
            }
        }
        return sb.toString().trim()
    }

    private fun extractAppleMetadataSupplement(
        groups: com.google.gson.JsonArray?,
        languageRanker: (String) -> Int
    ): Map<Int, String> {
        if (groups == null || groups.size() == 0) return emptyMap()
        var bestMap = emptyMap<Int, String>()
        var bestRank = -1

        for (groupEl in groups) {
            val groupObj = groupEl.asJsonObjectOrNull() ?: continue
            val lang = groupObj.getStringOrNull("lang").orEmpty()
            val rank = languageRanker(lang)
            val linesArr = groupObj.getAsJsonArray("lines") ?: continue
            val map = mutableMapOf<Int, String>()
            for (lineEl in linesArr) {
                val lineObj = lineEl.asJsonObjectOrNull() ?: continue
                val ts = lineObj.getIntOrNull("timestamp") ?: continue
                val txt = lineObj.getStringOrNull("text")?.trim() ?: continue
                if (ts >= 0 && txt.isNotEmpty()) {
                    map[ts] = txt
                }
            }
            if (map.isNotEmpty()) {
                if (bestMap.isEmpty() || (rank > bestRank) || (rank == bestRank && map.size > bestMap.size)) {
                    bestMap = map
                    bestRank = rank
                }
            }
        }
        return bestMap
    }

    // ─── 2. lrc.red (ISRC-Verified Apple TTML) ─────────────────────────────────

    private fun fetchLrcRed(title: String, artist: String, durationSec: Double): String? {
        val query = "$title $artist".trim()
        val searchUrl = Uri.parse("https://lrc.red/search.json")
            .buildUpon()
            .appendQueryParameter("q", query)
            .build()
            .toString()

        val searchReq = Request.Builder()
            .url(searchUrl)
            .header("User-Agent", BROWSER_UA)
            .header("Accept", "application/json")
            .get()
            .build()

        val searchJson = standardClient.newCall(searchReq).execute().use { resp ->
            when {
                resp.code == 429 || resp.code >= 500 -> throw ProviderUnavailableException("lrc.red HTTP ${resp.code}")
                !resp.isSuccessful -> return null
                else -> JsonParser.parseString(resp.body.string()).asJsonObjectOrNull()
            }
        } ?: return null

        val hits = searchJson.getAsJsonArray("hits") ?: return null
        var bestIsrc: String? = null
        var bestScore = -1

        for (hitEl in hits) {
            val hit = hitEl.asJsonObjectOrNull() ?: continue
            val rawIsrc = hit.getStringOrNull("isrc")?.trim()?.uppercase(Locale.ROOT).orEmpty()
            if (!ISRC_REGEX.matches(rawIsrc)) continue
            val hitTitle = hit.getStringOrNull("title").orEmpty()
            val hitArtist = hit.getStringOrNull("artist").orEmpty()
            val hitDuration = hit.getDoubleOrNull("duration") ?: 0.0

            val score = scoreTrackCandidate(
                candTitle = hitTitle,
                candArtist = hitArtist,
                candDurationSec = hitDuration,
                reqTitle = title,
                reqArtist = artist,
                reqDurationSec = durationSec
            ) ?: continue

            if (score > bestScore) {
                bestScore = score
                bestIsrc = rawIsrc
            }
        }

        val isrc = bestIsrc ?: return null
        val ttmlReq = Request.Builder()
            .url("https://lrc.red/s/$isrc.ttml")
            .header("Accept", "application/ttml+xml, application/xml")
            .header("User-Agent", BROWSER_UA)
            .get()
            .build()

        val ttml = standardClient.newCall(ttmlReq).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body.string()
        }
        return TtmlLyricsParser.parseToEnhancedLrc(ttml)
    }

    // ─── 3. LyricsPlus (KPOE v2 Word-Synced Servers) ───────────────────────────

    private fun fetchLyricsPlus(title: String, artist: String, durationSec: Double): String? {
        val endpoints = listOf(
            "https://lyricsplus.prjktla.my.id/v2/lyrics/get",
            "https://lyricsplus.binimum.org/v2/lyrics/get"
        )
        for (endpoint in endpoints) {
            val res = runCatching { fetchLyricsPlusFromServer(endpoint, title, artist, durationSec) }.getOrNull()
            if (!res.isNullOrBlank()) return res
        }
        return null
    }

    private fun fetchLyricsPlusFromServer(
        endpoint: String,
        title: String,
        artist: String,
        durationSec: Double
    ): String? {
        val urlBuilder = Uri.parse(endpoint).buildUpon()
            .appendQueryParameter("title", title)
            .appendQueryParameter("artist", artist)
        if (durationSec > 0.0) {
            urlBuilder.appendQueryParameter("duration", String.format(Locale.US, "%.3f", durationSec))
        }

        val req = Request.Builder()
            .url(urlBuilder.build().toString())
            .header("User-Agent", BROWSER_UA)
            .header("Accept", "application/json")
            .get()
            .build()

        val json = standardClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            JsonParser.parseString(resp.body.string()).asJsonObjectOrNull()
        } ?: return null

        val kind = json.getStringOrNull("type")?.lowercase(Locale.ROOT).orEmpty()
        val isWordTimed = kind == "word" || kind == "syllable"
        val lyricsArr = json.getAsJsonArray("lyrics") ?: return null
        if (lyricsArr.size() == 0) return null

        val outLines = mutableListOf<String>()
        for (lineEl in lyricsArr) {
            val lineObj = lineEl.asJsonObjectOrNull() ?: continue
            val lineTimeMs = (lineObj.getDoubleOrNull("time") ?: continue).roundToLong().toInt()
            val lineDurMs = (lineObj.getDoubleOrNull("duration") ?: 0.0).roundToLong().toInt()
            val syllabus = lineObj.getAsJsonArray("syllabus")

            if (isWordTimed && syllabus != null && syllabus.size() > 0) {
                val leadSyllables = mutableListOf<JsonObject>()
                val bgSyllables = mutableListOf<JsonObject>()
                for (sylEl in syllabus) {
                    val sylObj = sylEl.asJsonObjectOrNull() ?: continue
                    if (sylObj.getBooleanOrNull("isBackground") == true) {
                        bgSyllables += sylObj
                    } else {
                        leadSyllables += sylObj
                    }
                }
                val mainList = if (leadSyllables.isEmpty()) bgSyllables else leadSyllables
                val mainFormatted = formatKpoeSyllables(mainList)
                if (mainFormatted.isNotBlank()) {
                    outLines += "[${formatLrcMs(lineTimeMs)}]$mainFormatted"
                }
                if (leadSyllables.isNotEmpty() && bgSyllables.isNotEmpty()) {
                    val bgStart = (bgSyllables.first().getDoubleOrNull("time") ?: lineTimeMs.toDouble()).roundToLong().toInt()
                    val bgFormatted = formatKpoeSyllables(bgSyllables)
                    if (bgFormatted.isNotBlank()) {
                        outLines += "[${formatLrcMs(bgStart)}][bg:$bgFormatted]"
                    }
                }
            } else {
                val text = lineObj.getStringOrNull("text")?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    val endSuffix = if (lineDurMs > 0) "<${formatLrcMs(lineTimeMs + lineDurMs)}>" else ""
                    outLines += "[${formatLrcMs(lineTimeMs)}]$text$endSuffix"
                }
            }
        }
        return outLines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private fun formatKpoeSyllables(syllables: List<JsonObject>): String {
        val sb = StringBuilder()
        var lastTag = ""
        for (syl in syllables) {
            val rawText = syl.getStringOrNull("text") ?: continue
            if (rawText.isEmpty()) continue
            val startMs = (syl.getDoubleOrNull("time") ?: continue).roundToLong().toInt()
            val durMs = (syl.getDoubleOrNull("duration") ?: 0.0).roundToLong().toInt()
            val startTag = "<${formatLrcMs(startMs)}>"
            if (startTag != lastTag) {
                sb.append(startTag)
                lastTag = startTag
            }
            sb.append(rawText)
            if (durMs > 0) {
                val endTag = "<${formatLrcMs(startMs + durMs)}>"
                sb.append(endTag)
                lastTag = endTag
            }
        }
        return sb.toString().trim()
    }

    // ─── 4. BetterLyrics (TTML) ────────────────────────────────────────────────

    private fun fetchBetterLyrics(title: String, artist: String, durationSec: Double): String? {
        val urlBuilder = Uri.parse("https://lyrics-api.boidu.dev/getLyrics").buildUpon()
            .appendQueryParameter("s", title)
            .appendQueryParameter("a", artist)
        if (durationSec > 0.0) {
            urlBuilder.appendQueryParameter("d", durationSec.roundToLong().toString())
        }

        val req = Request.Builder()
            .url(urlBuilder.build().toString())
            .header("User-Agent", BROWSER_UA)
            .header("Accept", "application/json")
            .get()
            .build()

        val json = standardClient.newCall(req).execute().use { resp ->
            when {
                resp.code == 401 || resp.code == 404 -> return null
                resp.code == 429 || resp.code >= 500 -> throw ProviderUnavailableException("BetterLyrics HTTP ${resp.code}")
                !resp.isSuccessful -> return null
                else -> JsonParser.parseString(resp.body.string()).asJsonObjectOrNull()
            }
        } ?: return null

        val ttml = json.getStringOrNull("ttml")?.trim().orEmpty()
        if (ttml.isEmpty()) return null
        return TtmlLyricsParser.parseToEnhancedLrc(ttml)
    }

    // ─── 5. NetEase (LRC + Translation + Romanization) ─────────────────────────

    private fun fetchNetease(title: String, artist: String, durationSec: Double): String? {
        val query = "$title $artist".trim()
        val searchUrl = Uri.parse("https://lyrics.paxsenix.org/netease/search")
            .buildUpon()
            .appendQueryParameter("q", query)
            .build()
            .toString()

        val searchReq = Request.Builder()
            .url(searchUrl)
            .header("Accept", "application/json")
            .header("User-Agent", BROWSER_UA)
            .get()
            .build()

        val searchJson = standardClient.newCall(searchReq).execute().use { resp ->
            when {
                resp.code == 429 || resp.code >= 500 -> throw ProviderUnavailableException("NetEase HTTP ${resp.code}")
                !resp.isSuccessful -> return null
                else -> JsonParser.parseString(resp.body.string()).asJsonObjectOrNull()
            }
        } ?: return null

        val songs = searchJson.getAsJsonObject("result")?.getAsJsonArray("songs") ?: return null
        var bestSongId: Long? = null
        var bestScore = -1

        for (songEl in songs) {
            val songObj = songEl.asJsonObjectOrNull() ?: continue
            val id = songObj.getLongOrNull("id") ?: continue
            val name = songObj.getStringOrNull("name") ?: continue
            val artistsArr = songObj.getAsJsonArray("artists")
            val artistJoined = buildList {
                if (artistsArr != null) {
                    for (a in artistsArr) {
                        a.asJsonObjectOrNull()?.getStringOrNull("name")?.let { add(it) }
                    }
                }
            }.joinToString(", ")

            val score = scoreTrackCandidate(
                candTitle = name,
                candArtist = artistJoined,
                candDurationSec = 0.0,
                reqTitle = title,
                reqArtist = artist,
                reqDurationSec = 0.0
            ) ?: continue

            if (score > bestScore) {
                bestScore = score
                bestSongId = id
            }
        }

        val songId = bestSongId ?: return null
        val lyricsUrl = Uri.parse("https://lyrics.paxsenix.org/netease/lyrics")
            .buildUpon()
            .appendQueryParameter("id", songId.toString())
            .build()
            .toString()

        val lyricsReq = Request.Builder()
            .url(lyricsUrl)
            .header("Accept", "application/json")
            .header("User-Agent", BROWSER_UA)
            .get()
            .build()

        val lyricsJson = standardClient.newCall(lyricsReq).execute().use { resp ->
            if (!resp.isSuccessful) return null
            JsonParser.parseString(resp.body.string()).asJsonObjectOrNull()
        } ?: return null

        val mainLrc = lyricsJson.getAsJsonObject("lrc")?.getStringOrNull("lyric")?.trim().orEmpty()
        if (mainLrc.isEmpty()) return null

        val tlyric = lyricsJson.getAsJsonObject("tlyric")?.getStringOrNull("lyric")?.trim().orEmpty()
        return buildString {
            append(mainLrc)
            if (tlyric.isNotEmpty()) {
                append("\n\n")
                append(tlyric)
            }
        }
    }

    // ─── 6. Kugou (Direct LRC) ─────────────────────────────────────────────────

    private fun fetchKugou(title: String, artist: String, durationSec: Double): String? {
        val keyword = if (artist.isNotBlank()) "$artist - $title" else title
        val durationMs = if (durationSec > 0.0) (durationSec * 1000.0).roundToLong() else 0L

        val searchUrl = Uri.parse("https://lyrics.kugou.com/search")
            .buildUpon()
            .appendQueryParameter("ver", "1")
            .appendQueryParameter("man", "yes")
            .appendQueryParameter("client", "pc")
            .appendQueryParameter("keyword", keyword)
            .appendQueryParameter("duration", durationMs.toString())
            .appendQueryParameter("hash", "")
            .build()
            .toString()

        val searchReq = Request.Builder()
            .url(searchUrl)
            .header("User-Agent", BROWSER_UA)
            .get()
            .build()

        val searchJson = standardClient.newCall(searchReq).execute().use { resp ->
            if (!resp.isSuccessful) return null
            JsonParser.parseString(resp.body.string()).asJsonObjectOrNull()
        } ?: return null

        val candidates = searchJson.getAsJsonArray("candidates") ?: return null
        var bestId: String? = null
        var bestAccessKey: String? = null
        var bestScore = -1

        for (candEl in candidates) {
            val cand = candEl.asJsonObjectOrNull() ?: continue
            val id = cand.getStringOrNull("id")?.trim().orEmpty()
            val accessKey = cand.getStringOrNull("accesskey")?.trim().orEmpty()
            if (id.isEmpty() || accessKey.isEmpty()) continue
            val candSong = cand.getStringOrNull("song").orEmpty()
            val candSinger = cand.getStringOrNull("singer").orEmpty()
            val candDurSec = (cand.getDoubleOrNull("duration") ?: 0.0) / 1000.0

            val score = scoreTrackCandidate(
                candTitle = candSong,
                candArtist = candSinger,
                candDurationSec = candDurSec,
                reqTitle = title,
                reqArtist = artist,
                reqDurationSec = durationSec
            ) ?: continue

            if (score > bestScore) {
                bestScore = score
                bestId = id
                bestAccessKey = accessKey
            }
        }

        val id = bestId ?: return null
        val accessKey = bestAccessKey ?: return null
        val dlUrl = Uri.parse("https://lyrics.kugou.com/download")
            .buildUpon()
            .appendQueryParameter("ver", "1")
            .appendQueryParameter("client", "pc")
            .appendQueryParameter("id", id)
            .appendQueryParameter("accesskey", accessKey)
            .appendQueryParameter("fmt", "lrc")
            .appendQueryParameter("charset", "utf8")
            .build()
            .toString()

        val dlReq = Request.Builder()
            .url(dlUrl)
            .header("User-Agent", BROWSER_UA)
            .get()
            .build()

        val dlJson = standardClient.newCall(dlReq).execute().use { resp ->
            if (!resp.isSuccessful) return null
            JsonParser.parseString(resp.body.string()).asJsonObjectOrNull()
        } ?: return null

        val contentB64 = dlJson.getStringOrNull("content")?.trim().orEmpty()
        if (contentB64.isEmpty()) return null
        return decodeBase64Lyrics(contentB64)
    }

    // ─── 7. QQ Music (Direct LRC) ──────────────────────────────────────────────

    private fun fetchQqMusic(title: String, artist: String, durationSec: Double): String? {
        val query = "$title $artist".trim()
        val searchUrl = Uri.parse("https://c.y.qq.com/splcloud/fcgi-bin/smartbox_new.fcg")
            .buildUpon()
            .appendQueryParameter("format", "json")
            .appendQueryParameter("key", query)
            .build()
            .toString()

        val searchReq = Request.Builder()
            .url(searchUrl)
            .header("Referer", "https://y.qq.com/")
            .header("User-Agent", BROWSER_UA)
            .get()
            .build()

        val searchJson = standardClient.newCall(searchReq).execute().use { resp ->
            if (!resp.isSuccessful) return null
            JsonParser.parseString(resp.body.string()).asJsonObjectOrNull()
        } ?: return null

        val itemList = searchJson.getAsJsonObject("data")
            ?.getAsJsonObject("song")
            ?.getAsJsonArray("itemlist")
            ?: return null

        var bestMid: String? = null
        var bestId: String? = null
        var bestScore = -1

        for (itemEl in itemList) {
            val item = itemEl.asJsonObjectOrNull() ?: continue
            val mid = item.getStringOrNull("mid")?.trim().orEmpty()
            val id = item.getStringOrNull("id")?.trim().orEmpty()
            if (mid.isEmpty()) continue
            val candName = item.getStringOrNull("name").orEmpty()
            val candSinger = item.getStringOrNull("singer").orEmpty()

            val score = scoreTrackCandidate(
                candTitle = candName,
                candArtist = candSinger,
                candDurationSec = 0.0,
                reqTitle = title,
                reqArtist = artist,
                reqDurationSec = 0.0
            ) ?: continue

            if (score > bestScore) {
                bestScore = score
                bestMid = mid
                bestId = id
            }
        }

        val songMid = bestMid ?: return null
        val lyricUrl = Uri.parse("https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg")
            .buildUpon()
            .appendQueryParameter("format", "json")
            .appendQueryParameter("inCharset", "utf8")
            .appendQueryParameter("outCharset", "utf-8")
            .appendQueryParameter("notice", "0")
            .appendQueryParameter("platform", "yqq.json")
            .appendQueryParameter("needNewCode", "0")
            .appendQueryParameter("songmid", songMid)
            .apply {
                if (!bestId.isNullOrEmpty()) appendQueryParameter("songid", bestId)
            }
            .build()
            .toString()

        val lyricReq = Request.Builder()
            .url(lyricUrl)
            .header("Referer", "https://y.qq.com/")
            .header("User-Agent", BROWSER_UA)
            .get()
            .build()

        val lyricJson = standardClient.newCall(lyricReq).execute().use { resp ->
            if (!resp.isSuccessful) return null
            JsonParser.parseString(resp.body.string()).asJsonObjectOrNull()
        } ?: return null

        val rawLyric = lyricJson.getStringOrNull("lyric")?.trim().orEmpty()
        if (rawLyric.isEmpty()) return null
        val mainLrc = if (rawLyric.startsWith("[")) rawLyric else decodeBase64Lyrics(rawLyric) ?: return null
        val rawTrans = lyricJson.getStringOrNull("trans")?.trim().orEmpty()
        val transLrc = if (rawTrans.isNotEmpty()) {
            if (rawTrans.startsWith("[")) rawTrans else decodeBase64Lyrics(rawTrans)
        } else null

        return if (!transLrc.isNullOrBlank()) "$mainLrc\n\n$transLrc" else mainLrc
    }

    // ─── Helpers & Matching ────────────────────────────────────────────────────

    private fun scoreTrackCandidate(
        candTitle: String,
        candArtist: String,
        candDurationSec: Double,
        reqTitle: String,
        reqArtist: String,
        reqDurationSec: Double
    ): Int? {
        val normCandTitle = normalizeMatchText(candTitle)
        val normReqTitle = normalizeMatchText(reqTitle)
        if (normCandTitle.isEmpty() || normReqTitle.isEmpty()) return null

        val titleScore = when {
            normCandTitle == normReqTitle -> 70
            normCandTitle.contains(normReqTitle) || normReqTitle.contains(normCandTitle) -> 55
            else -> {
                val candTokens = normCandTitle.split(" ").filter { it.isNotBlank() }.toSet()
                val reqTokens = normReqTitle.split(" ").filter { it.isNotBlank() }.toSet()
                val overlap = candTokens.intersect(reqTokens).size
                if (reqTokens.isNotEmpty() && overlap.toDouble() / reqTokens.size >= 0.75) 45 else return null
            }
        }

        val normReqArtist = normalizeMatchText(reqArtist)
        val normCandArtist = normalizeMatchText(candArtist)
        val artistScore = if (normReqArtist.isEmpty() || normReqArtist == "unknown" || normReqArtist == "unknown artist") {
            10
        } else if (normCandArtist == normReqArtist) {
            30
        } else if (normCandArtist.contains(normReqArtist) || normReqArtist.contains(normCandArtist)) {
            22
        } else {
            val candArtTokens = normCandArtist.split(" ").filter { it.length > 1 }.toSet()
            val reqArtTokens = normReqArtist.split(" ").filter { it.length > 1 }.toSet()
            if (candArtTokens.intersect(reqArtTokens).isNotEmpty()) 15 else return null
        }

        if (reqDurationSec > 0.0 && candDurationSec > 0.0) {
            val diff = abs(candDurationSec - reqDurationSec)
            if (diff > 8.0) return null
            val durBonus = (8.0 - diff).toInt().coerceAtLeast(0)
            return titleScore + artistScore + durBonus
        }

        return titleScore + artistScore
    }

    private fun normalizeMatchText(value: String): String {
        val strippedBrackets = value.replace(Regex("""[\(\[\{][^)\]\}]*[\)\]\}]"""), " ")
        val deaccented = Normalizer.normalize(strippedBrackets.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
        return deaccented
            .replace("&", " and ")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
    }

    private fun decodeBase64Lyrics(raw: String): String? {
        val cleaned = raw.filterNot { it == '\r' || it == '\n' || it == ' ' }
        return runCatching {
            String(Base64.getDecoder().decode(cleaned), Charsets.UTF_8)
        }.getOrElse {
            runCatching {
                String(Base64.getMimeDecoder().decode(raw), Charsets.UTF_8)
            }.getOrNull()
        }
    }

    private fun formatLrcMs(timeMs: Int): String {
        val clamped = timeMs.coerceAtLeast(0)
        val totalSeconds = clamped / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hundredths = (clamped % 1000) / 10
        return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, hundredths)
    }

    private class ProviderUnavailableException(message: String) : IOException(message)
}

private fun JsonElement?.asJsonObjectOrNull(): JsonObject? =
    if (this != null && this.isJsonObject) this.asJsonObject else null

private fun JsonObject.getStringOrNull(key: String): String? {
    val el = this.get(key) ?: return null
    return if (el.isJsonPrimitive) runCatching { el.asString }.getOrNull() else null
}

private fun JsonObject.getIntOrNull(key: String): Int? {
    val el = this.get(key) ?: return null
    return if (el.isJsonPrimitive) runCatching { el.asInt }.getOrNull() else null
}

private fun JsonObject.getLongOrNull(key: String): Long? {
    val el = this.get(key) ?: return null
    return if (el.isJsonPrimitive) runCatching { el.asLong }.getOrNull() else null
}

private fun JsonObject.getDoubleOrNull(key: String): Double? {
    val el = this.get(key) ?: return null
    return if (el.isJsonPrimitive) runCatching { el.asDouble }.getOrNull() else null
}

private fun JsonObject.getBooleanOrNull(key: String): Boolean? {
    val el = this.get(key) ?: return null
    return if (el.isJsonPrimitive) runCatching { el.asBoolean }.getOrNull() else null
}
