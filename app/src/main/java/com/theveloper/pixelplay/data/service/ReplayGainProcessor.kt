package com.theveloper.pixelplay.data.service

import androidx.annotation.MainThread
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.theveloper.pixelplay.data.media.ReplayGainManager
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import com.theveloper.pixelplay.utils.MediaItemBuilder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.abs

/**
 * Owns all ReplayGain volume-normalization state and logic, extracted from
 * [MusicService] during the Pass 5 service decomposition.
 *
 * The processor encapsulates the RG enable/album-gain flags, the in-flight IO
 * read job, the "expected vs. user-selected" volume bookkeeping that keeps a
 * programmatic RG volume change from being mistaken for a user gesture, and the
 * crossfade-aware application of the computed gain. It reaches the players only
 * through the injected [DualPlayerEngine]; the service supplies the current
 * session media item via [currentSessionMediaItem] so stale IO results can be
 * discarded.
 *
 * All public methods are main-thread affine (the IO tag read is dispatched
 * internally), matching the original in-service behaviour.
 */
class ReplayGainProcessor(
    private val engine: DualPlayerEngine,
    private val replayGainManager: ReplayGainManager,
    private val scope: CoroutineScope,
    private val currentSessionMediaItem: () -> MediaItem?,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private companion object {
        private const val TAG = "MusicService_PixelPlay"
    }

    private var enabled = false
    private var useAlbumGain = false
    private var job: Job? = null
    private var requestToken = 0L

    // Volume the user last chose by hand; restored whenever RG is disabled.
    private var userSelectedVolume = 1f
    // Volume we just wrote programmatically — used to ignore the echoed onVolumeChanged.
    private var expectedVolume: Float? = null
    // RG volume computed mid-crossfade, applied once the transition finishes.
    private var pendingVolume: Float? = null
    private var pendingMediaId: String? = null
    // Last successfully applied RG volume — avoids a full-volume spike during the
    // IO read for the next track (Repeat/Shuffle/Queue changes).
    private var lastAppliedVolume: Float? = null
    // MediaId for which lastAppliedVolume was computed.
    private var lastMediaId: String? = null

    @MainThread
    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (!value) {
            job?.cancel()
            requestToken += 1
            pendingVolume = null
            pendingMediaId = null
            lastAppliedVolume = null
            lastMediaId = null
            engine.incomingTrackReplayGainVolume = null
            if (!engine.isTransitionRunning()) {
                setPlayerVolume(engine.masterPlayer, userSelectedVolume)
            }
        }
    }

    @MainThread
    fun setUseAlbumGain(value: Boolean) {
        useAlbumGain = value
    }

    /** Seeds the user-selected volume from the player's current volume on startup. */
    @MainThread
    fun captureUserVolume(volume: Float) {
        userSelectedVolume = volume.coerceIn(0f, 1f)
    }

    @MainThread
    fun cancel() {
        job?.cancel()
        requestToken += 1
    }

    /**
     * Mirrors [Player.Listener.onVolumeChanged]: distinguishes a programmatic RG
     * volume change (which we ignore) from a genuine user gesture (which updates
     * [userSelectedVolume]).
     */
    @MainThread
    fun onPlayerVolumeChanged(volume: Float) {
        if (engine.isTransitionRunning()) return
        val expected = expectedVolume
        if (expected != null && abs(expected - volume) < 0.001f) {
            expectedVolume = null
            return
        }
        expectedVolume = null
        userSelectedVolume = volume.coerceIn(0f, 1f)
    }

    private fun setPlayerVolume(player: Player, volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        expectedVolume = clampedVolume
        player.volume = clampedVolume
    }

    private fun resolveEffectiveTargetVolume(replayGainMultiplier: Float): Float {
        return (userSelectedVolume * replayGainMultiplier).coerceIn(0f, 1f)
    }

    /**
     * Re-applies the last computed RG volume immediately (no IO) unless a
     * crossfade is running, preventing a brief full-volume spike on resume,
     * same-track seeks, and queue edits.
     */
    @MainThread
    fun reapplyLastAppliedVolume(player: Player) {
        if (!enabled || engine.isTransitionRunning()) return
        lastAppliedVolume?.let { setPlayerVolume(player, it) }
    }

    /**
     * Pre-computes ReplayGain for the incoming crossfade track. Seeds
     * [DualPlayerEngine.incomingTrackReplayGainVolume] from cache when available so
     * the fade loop ends at the correct volume, then kicks off [apply] which (since
     * the transition is running) stores the result as a pending volume.
     */
    @MainThread
    fun prepareForTransition(player: Player) {
        val incomingItem = player.currentMediaItem
        pendingVolume = null
        pendingMediaId = null
        cachedVolumeFor(incomingItem)?.let { cachedTargetVolume ->
            pendingVolume = cachedTargetVolume
            pendingMediaId = incomingItem?.mediaId
            engine.incomingTrackReplayGainVolume = cachedTargetVolume
        }
        apply(incomingItem)
    }

    /**
     * Applies ReplayGain volume normalization to [mediaItem]. Reads RG tags from
     * the file on an IO thread and adjusts the master player's volume accordingly.
     */
    @MainThread
    fun apply(mediaItem: MediaItem?) {
        job?.cancel()
        requestToken += 1
        val currentRequestToken = requestToken

        if (mediaItem == null) {
            pendingVolume = null
            pendingMediaId = null
            return
        }

        if (!enabled) {
            pendingVolume = null
            pendingMediaId = null
            engine.incomingTrackReplayGainVolume = null
            if (!engine.isTransitionRunning()) {
                setPlayerVolume(engine.masterPlayer, userSelectedVolume)
            }
            return
        }

        val mediaId = mediaItem.mediaId
        val filePath = mediaItem.mediaMetadata.extras
            ?.getString(MediaItemBuilder.EXTERNAL_EXTRA_FILE_PATH)

        if (filePath.isNullOrBlank()) {
            Timber.tag(TAG).d("ReplayGain: No file path for track, keeping user-selected volume")
            val fallbackVolume = userSelectedVolume.coerceIn(0f, 1f)
            if (engine.isTransitionRunning()) {
                pendingVolume = fallbackVolume
                pendingMediaId = mediaId
                engine.incomingTrackReplayGainVolume = fallbackVolume
            } else {
                pendingVolume = null
                pendingMediaId = null
                engine.incomingTrackReplayGainVolume = null
                lastAppliedVolume = fallbackVolume
                lastMediaId = mediaId
                setPlayerVolume(engine.masterPlayer, fallbackVolume)
            }
            return
        }

        val resolvedUseAlbumGain = useAlbumGain

        // Apply the last known RG volume immediately so there is no full-volume spike
        // while the IO coroutine reads the tags for the new track.
        if (!engine.isTransitionRunning()) {
            lastAppliedVolume?.let { setPlayerVolume(engine.masterPlayer, it) }
        }

        // Read ReplayGain tags on IO thread and synchronize all state/volume mutations on Main
        job = scope.launch(mainDispatcher) {
            val rgValues = withContext(ioDispatcher) {
                replayGainManager.readReplayGain(filePath)
            }

            if (currentRequestToken != requestToken) {
                return@launch
            }

            if (!enabled) {
                return@launch
            }

            val currentMediaId = currentSessionMediaItem()?.mediaId
            if (currentMediaId != mediaId) {
                Timber.tag(TAG).d("ReplayGain: Ignoring stale result for mediaId=%s", mediaId)
                return@launch
            }

            val rgMultiplier = replayGainManager.getVolumeMultiplier(
                rgValues,
                useAlbumGain = resolvedUseAlbumGain
            )
            val targetVolume = resolveEffectiveTargetVolume(rgMultiplier)

            if (engine.isTransitionRunning()) {
                // Store for application after transition completes.
                // Also pass to engine so the crossfade loop ends at the correct RG
                // volume instead of hard-coding 1f, preventing the audible jump.
                pendingVolume = targetVolume
                pendingMediaId = mediaId
                engine.incomingTrackReplayGainVolume = targetVolume
                Timber.tag(TAG).d(
                    "ReplayGain: Stored pending volume=%.2f for %s (transition running)",
                    targetVolume,
                    mediaItem.mediaMetadata.title
                )
            } else {
                pendingVolume = null
                pendingMediaId = null
                engine.incomingTrackReplayGainVolume = null
                lastAppliedVolume = targetVolume
                lastMediaId = mediaId
                setPlayerVolume(engine.masterPlayer, targetVolume)
                Timber.tag(TAG).d(
                    "ReplayGain: Applied volume=%.2f for %s",
                    targetVolume,
                    mediaItem.mediaMetadata.title
                )
            }
        }
    }

    /**
     * Returns the cached ReplayGain target volume for a media item if already computed, or null.
     * Does NOT trigger an IO read — only reads from the in-memory cache.
     */
    private fun cachedVolumeFor(mediaItem: MediaItem?): Float? {
        if (!enabled || mediaItem == null) return null
        val filePath = mediaItem.mediaMetadata.extras
            ?.getString(MediaItemBuilder.EXTERNAL_EXTRA_FILE_PATH) ?: return null
        if (filePath.isBlank()) return null
        val cached = replayGainManager.getCachedReplayGain(filePath) ?: return null
        val rgMultiplier = replayGainManager.getVolumeMultiplier(cached, useAlbumGain = useAlbumGain)
        return resolveEffectiveTargetVolume(rgMultiplier)
    }

    /**
     * Pre-fetches ReplayGain tags for a media item into the cache without applying the volume.
     * Called on queue changes and track transitions so the cache is warm by the time
     * [apply] runs, avoiding the 1-2s JNI read delay on playback start.
     */
    @MainThread
    fun prefetch(mediaItem: MediaItem?) {
        if (!enabled || mediaItem == null) return
        val filePath = mediaItem.mediaMetadata.extras
            ?.getString(MediaItemBuilder.EXTERNAL_EXTRA_FILE_PATH) ?: return
        if (filePath.isBlank()) return
        scope.launch(ioDispatcher) {
            replayGainManager.readReplayGain(filePath)
        }
    }

    /**
     * Applies the volume that was held back while a crossfade was running, or
     * triggers a fresh computation if none was pending.
     */
    @MainThread
    fun onTransitionFinished() {
        val player = engine.masterPlayer
        val pending = pendingVolume
        val targetMediaId = pendingMediaId
        pendingVolume = null
        pendingMediaId = null
        engine.incomingTrackReplayGainVolume = null

        if (!enabled) {
            setPlayerVolume(player, userSelectedVolume)
            Timber.tag(TAG).d(
                "ReplayGain: Transition finished, RG disabled — restored userSelectedVolume=%.2f",
                userSelectedVolume
            )
            return
        }

        val currentItem = currentSessionMediaItem()
        if (pending != null && targetMediaId != null && currentItem?.mediaId == targetMediaId) {
            // Invalidate any in-flight IO request from before transition finished so it can't overwrite.
            job?.cancel()
            requestToken += 1
            // The crossfade loop ramps to this value; apply it now as the stable post-fade volume.
            // Also update lastAppliedVolume & lastMediaId so any subsequent onPositionDiscontinuity
            // / onMediaMetadataChanged uses this value immediately without a redundant IO read.
            lastAppliedVolume = pending
            lastMediaId = targetMediaId
            setPlayerVolume(player, pending)
            Timber.tag(TAG).d("ReplayGain: Transition finished, applied pending volume=%.2f", pending)
        } else {
            // No matching pending volume for the current track; trigger full recomputation.
            apply(currentItem)
            Timber.tag(TAG).d("ReplayGain: Transition finished, triggering recomputation for current track")
        }
    }

    /**
     * Recomputes RG only when the track actually changed; otherwise re-applies the
     * last known volume. [onMediaMetadataChanged] also fires on queue edits without a
     * track change, which would otherwise launch a redundant IO read and cause a spike.
     */
    @MainThread
    fun onMediaMetadataChanged(currentItem: MediaItem?) {
        val currentMediaId = currentItem?.mediaId ?: return
        if (currentMediaId != lastMediaId) {
            apply(currentItem)
        } else {
            reapplyLastAppliedVolume(engine.masterPlayer)
        }
    }
}
