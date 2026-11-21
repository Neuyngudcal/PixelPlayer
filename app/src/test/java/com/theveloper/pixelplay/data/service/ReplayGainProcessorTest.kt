package com.theveloper.pixelplay.data.service

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import android.os.Bundle
import com.theveloper.pixelplay.data.media.ReplayGainManager
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import com.theveloper.pixelplay.utils.MediaItemBuilder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReplayGainProcessorTest {

    @Test
    fun `apply updates player volume respecting userSelectedVolume and ignores stale request tokens`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val masterPlayer = mockk<Player>(relaxed = true)
            val engine = mockk<DualPlayerEngine>(relaxed = true) {
                every { this@mockk.masterPlayer } returns masterPlayer
                every { isTransitionRunning() } returns false
            }
            val replayGainManager = mockk<ReplayGainManager>()
            every { replayGainManager.readReplayGain("/music/a.flac") } returns
                ReplayGainManager.ReplayGainValues(trackGainDb = -6.0f)
            every { replayGainManager.getVolumeMultiplier(any(), any(), any()) } returns 0.5f

            val extrasA = mockk<Bundle> {
                every { getString(MediaItemBuilder.EXTERNAL_EXTRA_FILE_PATH) } returns "/music/a.flac"
            }
            val itemA = MediaItem.Builder()
                .setMediaId("a")
                .setMediaMetadata(MediaMetadata.Builder().setExtras(extrasA).build())
                .build()

            var currentItem: MediaItem? = itemA
            val processor = ReplayGainProcessor(
                engine = engine,
                replayGainManager = replayGainManager,
                scope = this,
                currentSessionMediaItem = { currentItem },
                mainDispatcher = testDispatcher,
                ioDispatcher = testDispatcher,
            )

            processor.captureUserVolume(0.8f)
            processor.setEnabled(true)
            processor.apply(itemA)
            advanceUntilIdle()

            // Effective volume = userSelectedVolume (0.8) * rgMultiplier (0.5) = 0.4
            verify(atLeast = 1) { masterPlayer.volume = 0.4f }

            // Disabling ReplayGain restores userSelectedVolume
            processor.setEnabled(false)
            verify(atLeast = 1) { masterPlayer.volume = 0.8f }
        }

    @Test
    fun `prepareForTransition and onTransitionFinished synchronize pending volume and lastMediaId`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val masterPlayer = mockk<Player>(relaxed = true)
            val engine = mockk<DualPlayerEngine>(relaxed = true) {
                every { this@mockk.masterPlayer } returns masterPlayer
                every { isTransitionRunning() } returns true
            }
            val extrasB = mockk<Bundle> {
                every { getString(MediaItemBuilder.EXTERNAL_EXTRA_FILE_PATH) } returns "/music/b.flac"
            }
            val itemB = MediaItem.Builder()
                .setMediaId("b")
                .setMediaMetadata(MediaMetadata.Builder().setExtras(extrasB).build())
                .build()
            every { masterPlayer.currentMediaItem } returns itemB

            val replayGainManager = mockk<ReplayGainManager>()
            every { replayGainManager.getCachedReplayGain("/music/b.flac") } returns
                ReplayGainManager.ReplayGainValues(trackGainDb = -6.0f)
            every { replayGainManager.readReplayGain("/music/b.flac") } returns
                ReplayGainManager.ReplayGainValues(trackGainDb = -6.0f)
            every { replayGainManager.getVolumeMultiplier(any(), any(), any()) } returns 0.5f

            var currentItem: MediaItem? = itemB
            val processor = ReplayGainProcessor(
                engine = engine,
                replayGainManager = replayGainManager,
                scope = this,
                currentSessionMediaItem = { currentItem },
                mainDispatcher = testDispatcher,
                ioDispatcher = testDispatcher,
            )

            processor.setEnabled(true)
            processor.prepareForTransition(masterPlayer)
            advanceUntilIdle()

            // Transition finishes -> applies pending volume cleanly and syncs lastMediaId
            every { engine.isTransitionRunning() } returns false
            processor.onTransitionFinished()
            verify(atLeast = 1) { masterPlayer.volume = 0.5f }

            // Subsequent metadata event for the same item re-applies without triggering IO read
            processor.onMediaMetadataChanged(itemB)
            verify(exactly = 1) { replayGainManager.readReplayGain("/music/b.flac") }
        }
}
