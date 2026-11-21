package com.theveloper.pixelplay.data.service.wear
 
import android.app.Application
import android.content.Context
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.net.Uri
import androidx.core.graphics.ColorUtils
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.material.color.utilities.Hct
import com.theveloper.pixelplay.data.preferences.dataStore
import com.theveloper.pixelplay.data.model.PlayerInfo
import com.theveloper.pixelplay.shared.WearDataPaths
import com.theveloper.pixelplay.shared.WearLyrics
import com.theveloper.pixelplay.shared.WearPlayerState
import com.theveloper.pixelplay.shared.WearSyncedLyricLine
import com.theveloper.pixelplay.shared.WearThemePalette
import com.theveloper.pixelplay.utils.AlbumArtUtils
import com.theveloper.pixelplay.utils.ArtworkTransportSanitizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes player state to the Wear Data Layer so the watch app can display it.
 *
 * Album art is sent as a bounded-size JPEG Asset for full-screen quality on watch.
 */
@Singleton
class WearStatePublisher @Inject constructor(
    private val application: Application,
) {
    private val dataClient by lazy { Wearable.getDataClient(application) }
    private val audioManager by lazy {
        application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val artworkCacheLock = Any()
    private var lastArtworkUri: String? = null
    private var lastRawBitmapData: ByteArray? = null
    private var cachedWearArtAsset: Asset? = null

    /**
     * Clear the cached artwork assets.
     */
    fun clearCache() {
        synchronized(artworkCacheLock) {
            lastArtworkUri = null
            lastRawBitmapData = null
            cachedWearArtAsset = null
        }
        Timber.tag(TAG).d("Cleared Wear artwork cache")
    }

    companion object {
        private const val TAG = "WearStatePublisher"
        private const val MAX_WEAR_LYRIC_LINES = 180
    }

    /**
     * Wear OS support has been removed; state publishing is now a no-op.
     */
    @Suppress("UNUSED_PARAMETER")
    fun publishState(songId: String?, playerInfo: PlayerInfo) {
        // Wear OS integration disabled.
    }

    /**
     * Wear OS support has been removed; clearing state is now a no-op.
     */
    fun clearState() {
        clearCache()
    }

    private suspend fun publishStateInternal(songId: String?, playerInfo: PlayerInfo) {
        // Read lyrics display preferences from DataStore so the watch respects
        // the same translation/romanization visibility as the phone UI.
        val prefs = application.dataStore.data.first()
        val showLyricsTranslation = prefs[booleanPreferencesKey("show_lyrics_translation")] ?: true
        val showLyricsRomanization = prefs[booleanPreferencesKey("show_lyrics_romanization")] ?: true
        val lyricsSyncOffsetMs = if (!songId.isNullOrBlank()) {
            prefs[stringPreferencesKey("lyrics_sync_offsets_json")]
                ?.let { raw -> runCatching { json.decodeFromString<Map<String, Int>>(raw)[songId] }.getOrNull() }
                ?: 0
        } else 0

        val volumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volumeMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        val wearLyrics = playerInfo.lyrics?.toWearLyrics(
            showTranslation = showLyricsTranslation,
            showRomanization = showLyricsRomanization,
            lyricsSyncOffsetMs = lyricsSyncOffsetMs,
        )

        val wearState = WearPlayerState(
            songId = songId.orEmpty(),
            songTitle = playerInfo.songTitle,
            artistName = playerInfo.artistName,
            albumName = "", // Album name not in PlayerInfo; will be enriched in future phases
            isPlaying = playerInfo.isPlaying,
            currentPositionMs = playerInfo.currentPositionMs,
            totalDurationMs = playerInfo.totalDurationMs,
            isFavorite = playerInfo.isFavorite,
            isShuffleEnabled = playerInfo.isShuffleEnabled,
            repeatMode = playerInfo.repeatMode,
            volumeLevel = volumeLevel,
            volumeMax = volumeMax,
            themePalette = buildWearThemePalette(playerInfo),
            queueRevision = playerInfo.wearQueueRevision,
            lyrics = wearLyrics,
        )

        val stateJson = json.encodeToString(wearState)

        val request = PutDataMapRequest.create(WearDataPaths.PLAYER_STATE).apply {
            dataMap.putString(WearDataPaths.KEY_STATE_JSON, stateJson)
            dataMap.putLong(WearDataPaths.KEY_TIMESTAMP, System.currentTimeMillis())

            // Attach album art as Asset if available (cached to prevent duplicate processing)
            val artAsset = getOrCreateWearArtworkAsset(playerInfo)
            if (artAsset != null) {
                dataMap.putAsset(WearDataPaths.KEY_ALBUM_ART, artAsset)
            } else {
                dataMap.remove(WearDataPaths.KEY_ALBUM_ART)
            }
        }.asPutDataRequest().setUrgent()

        dataClient.putDataItem(request)
        Timber.tag(TAG).d("Published state to Wear: ${wearState.songTitle} (playing=${wearState.isPlaying})")
    }

    private fun getOrCreateWearArtworkAsset(playerInfo: PlayerInfo): Asset? {
        val uriString = playerInfo.albumArtUri
        val rawBitmapData = playerInfo.albumArtBitmapData

        synchronized(artworkCacheLock) {
            val uriMatches = uriString == lastArtworkUri
            val dataMatches = if (rawBitmapData == null && lastRawBitmapData == null) {
                true
            } else if (rawBitmapData != null && lastRawBitmapData != null) {
                rawBitmapData === lastRawBitmapData || rawBitmapData.contentEquals(lastRawBitmapData)
            } else {
                false
            }
            if (uriMatches && dataMatches) {
                return cachedWearArtAsset
            }
        }

        val sanitizedBytes = resolveArtworkBytesForWear(playerInfo)
        val asset = if (sanitizedBytes != null) {
            try {
                Asset.createFromBytes(sanitizedBytes)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to create album art asset")
                null
            }
        } else {
            null
        }

        synchronized(artworkCacheLock) {
            lastArtworkUri = uriString
            lastRawBitmapData = rawBitmapData
            cachedWearArtAsset = asset
        }

        return asset
    }

    private fun resolveArtworkBytesForWear(playerInfo: PlayerInfo): ByteArray? {
        val uriString = playerInfo.albumArtUri
        if (!uriString.isNullOrBlank()) {
            val uri = Uri.parse(uriString)
            val scheme = uri.scheme?.lowercase()
            when {
                com.theveloper.pixelplay.utils.LocalArtworkUri.isLocalArtworkUri(uriString) ||
                    scheme == "content" ||
                    scheme == "file" ||
                    scheme == "android.resource" -> {
                    AlbumArtUtils.openArtworkInputStream(application, uri)?.use { input ->
                        readBytesCapped(input, ArtworkTransportSanitizer.WEAR_CONFIG.sourceBytesLimit)
                            ?.let { bytes ->
                                ArtworkTransportSanitizer.sanitizeEncodedBytes(
                                    data = bytes,
                                    config = ArtworkTransportSanitizer.WEAR_CONFIG,
                                )
                            }
                    }?.let { return it }
                }
                scheme == "http" || scheme == "https" -> {
                    downloadAndSanitizeRemoteArtwork(uriString)?.let { return it }
                }
            }
        }
        return ArtworkTransportSanitizer.sanitizeEncodedBytes(
            data = playerInfo.albumArtBitmapData,
            config = ArtworkTransportSanitizer.WEAR_CONFIG,
        )
    }

    private fun downloadAndSanitizeRemoteArtwork(uriString: String): ByteArray? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(uriString).openConnection() as? HttpURLConnection)
                ?: return null
            connection.connectTimeout = 4_000
            connection.readTimeout = 6_000
            connection.instanceFollowRedirects = true
            connection.doInput = true
            connection.inputStream.use { input ->
                readBytesCapped(input, ArtworkTransportSanitizer.WEAR_CONFIG.sourceBytesLimit)
                    ?.let { bytes ->
                        ArtworkTransportSanitizer.sanitizeEncodedBytes(
                            data = bytes,
                            config = ArtworkTransportSanitizer.WEAR_CONFIG,
                        )
                    }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to download remote artwork for Wear: %s", uriString)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun readBytesCapped(input: java.io.InputStream, maxBytes: Int): ByteArray? {
        val buffer = ByteArray(16 * 1024)
        val output = java.io.ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > maxBytes) {
                Timber.tag(TAG).d("Artwork source exceeded hard limit (%d bytes)", total)
                return null
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray().takeIf { it.isNotEmpty() }
    }

    /**
     * Builds a watch-oriented palette from phone-side theme colors already computed by MusicService.
     * This avoids re-extracting colors from album art on watch and keeps both UIs aligned.
     */
    private fun buildWearThemePalette(playerInfo: PlayerInfo): WearThemePalette? {
        playerInfo.wearThemePalette?.let { return it }

        val colors = playerInfo.themeColors ?: return null

        val surfaceContainer = colors.darkSurfaceContainer
        val surfaceContainerLowest = colors.darkSurfaceContainerLowest.takeIf { it != 0 }
            ?: ColorUtils.blendARGB(surfaceContainer, AndroidColor.BLACK, 0.24f)
        val surfaceContainerLow = colors.darkSurfaceContainerLow.takeIf { it != 0 }
            ?: ColorUtils.blendARGB(surfaceContainer, AndroidColor.WHITE, 0.08f)
        val surfaceContainerHigh = colors.darkSurfaceContainerHigh.takeIf { it != 0 }
            ?: ColorUtils.blendARGB(surfaceContainer, AndroidColor.WHITE, 0.16f)
        val surfaceContainerHighest = colors.darkSurfaceContainerHighest.takeIf { it != 0 }
            ?: ColorUtils.blendARGB(surfaceContainer, AndroidColor.WHITE, 0.24f)
        val title = colors.darkTitle
        val artist = colors.darkArtist
        val playContainer = colors.darkPlayPauseBackground
        val playContent = colors.darkPlayPauseIcon
        val secondaryContainer = colors.darkPrevNextBackground
        val secondaryContent = colors.darkPrevNextIcon

        val primaryHct = Hct.fromInt(playContainer)
        val surfaceHct = Hct.fromInt(surfaceContainer)
        val accentChroma = primaryHct.chroma.coerceAtMost(36.0)
        val gradientTop = Hct.from(primaryHct.hue, (accentChroma * 0.65).coerceAtMost(28.0), 20.0).toInt()
        val gradientMiddle = Hct.from(surfaceHct.hue, (accentChroma * 0.35).coerceAtMost(16.0), 10.0).toInt()
        val gradientBottom = Hct.from(surfaceHct.hue, (accentChroma * 0.20).coerceAtMost(10.0), 4.0).toInt()

        val disabledContainer = surfaceContainerHighest
        val chipContainer = ColorUtils.blendARGB(secondaryContainer, surfaceContainerLow, 0.36f)

        return WearThemePalette(
            gradientTopArgb = gradientTop,
            gradientMiddleArgb = gradientMiddle,
            gradientBottomArgb = gradientBottom,
            surfaceContainerLowestArgb = surfaceContainerLowest,
            surfaceContainerLowArgb = surfaceContainerLow,
            surfaceContainerArgb = surfaceContainer,
            surfaceContainerHighArgb = surfaceContainerHigh,
            surfaceContainerHighestArgb = surfaceContainerHighest,
            textPrimaryArgb = ensureWearReadable(preferredColor = title, backgroundColor = gradientMiddle),
            textSecondaryArgb = ensureWearReadable(preferredColor = artist, backgroundColor = gradientBottom),
            textErrorArgb = harmonizeWearAccent(0xFFFFB4AB.toInt(), playContainer, gradientBottom),
            controlContainerArgb = playContainer,
            controlContentArgb = ensureWearReadable(preferredColor = playContent, backgroundColor = playContainer),
            controlDisabledContainerArgb = disabledContainer,
            controlDisabledContentArgb = ensureWearReadable(
                preferredColor = artist,
                backgroundColor = disabledContainer
            ),
            transportContainerArgb = secondaryContainer,
            transportContentArgb = ensureWearReadable(
                preferredColor = secondaryContent,
                backgroundColor = secondaryContainer,
            ),
            chipContainerArgb = chipContainer,
            chipContentArgb = ensureWearReadable(preferredColor = secondaryContent, backgroundColor = chipContainer),
            favoriteActiveArgb = harmonizeWearAccent(0xFFFF5C8A.toInt(), playContainer, surfaceContainer),
            shuffleActiveArgb = harmonizeWearAccent(0xFF44CDC4.toInt(), playContainer, surfaceContainer),
            repeatActiveArgb = harmonizeWearAccent(0xFF70A6FF.toInt(), playContainer, surfaceContainer),
        )
    }

    private fun com.theveloper.pixelplay.data.model.Lyrics.toWearLyrics(
        showTranslation: Boolean = true,
        showRomanization: Boolean = true,
        lyricsSyncOffsetMs: Int = 0,
    ): WearLyrics? {
        val syncedLines = synced
            ?.asSequence()
            ?.filter { it.line.isNotBlank() || !it.translation.isNullOrBlank() || !it.romanization.isNullOrBlank() }
            ?.take(MAX_WEAR_LYRIC_LINES)
            ?.map { line ->
                WearSyncedLyricLine(
                    timeMs = (line.time - lyricsSyncOffsetMs).coerceAtLeast(0),
                    line = line.line,
                    translation = if (showTranslation) line.translation else null,
                    romanization = if (showRomanization) line.romanization else null,
                )
            }
            ?.toList()
            .orEmpty()

        if (syncedLines.isNotEmpty()) {
            return WearLyrics(synced = syncedLines)
        }

        val plainLines = plain
            ?.asSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.take(MAX_WEAR_LYRIC_LINES)
            ?.toList()
            .orEmpty()

        return WearLyrics(plain = plainLines).takeIf { it.hasLyrics }
    }
}
