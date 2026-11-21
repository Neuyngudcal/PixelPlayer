package com.theveloper.pixelplay.data.service.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DualPlayerEngineQueueAndReadyTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun computeRestoredQueueAndIndex_preservesFull250SongQueueBeyond200Window() {
        val fullQueue = List(250) { idx ->
            MediaItem.Builder().setMediaId("song-$idx").build()
        }
        val windowedItems = fullQueue.subList(50, 250)

        val restored = computeRestoredQueueAndIndex(
            snapshot = fullQueue,
            playerWindowItems = windowedItems,
            currentMediaId = "song-230",
            fallbackAbsoluteIndex = 230,
            fallbackPlayerIndex = 180
        )

        assertThat(restored.mediaItems).hasSize(250)
        assertThat(restored.currentIndex).isEqualTo(230)
        assertThat(restored.mediaItems[230].mediaId).isEqualTo("song-230")
    }

    @Test
    fun shouldPreserveWindowedSnapshotOnTimelineChanged_preservesWindowButAllowsNewPlaylist() {
        val snapshotIds = List(300) { idx -> "song-$idx" }
        val activeWindowSlice = snapshotIds.subList(50, 250)

        // Internal playlist timeline update with matching 200-song slice preserves full snapshot.
        assertThat(
            shouldPreserveWindowedSnapshotOnTimelineChanged(
                activePlayerUsesWindowedQueue = true,
                snapshotMediaIds = snapshotIds,
                windowStartIndex = 50,
                playerMediaIds = activeWindowSlice,
                reason = Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
                maxAuxiliaryItems = 200
            )
        ).isTrue()

        // Playing a new short album replaces the snapshot cleanly.
        assertThat(
            shouldPreserveWindowedSnapshotOnTimelineChanged(
                activePlayerUsesWindowedQueue = true,
                snapshotMediaIds = snapshotIds,
                windowStartIndex = 50,
                playerMediaIds = listOf("album-a-1", "album-a-2", "album-a-3"),
                reason = Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
                maxAuxiliaryItems = 200
            )
        ).isFalse()
    }

    @Test
    fun awaitPlayerReadyOnPlayer_returnsImmediatelyWhenAlreadyReady() = runTest(testDispatcher) {
        val player = mockk<Player>(relaxed = true)
        every { player.playbackState } returns Player.STATE_READY

        val ready = awaitPlayerReadyOnPlayer(
            player = player,
            timeoutMs = 5_000L,
            mainDispatcher = testDispatcher
        )

        assertThat(ready).isTrue()
        verify(exactly = 0) { player.addListener(any()) }
    }

    @Test
    fun awaitPlayerReadyOnPlayer_handlesStateFlipRightBeforeListenerCheck() = runTest(testDispatcher) {
        val player = mockk<Player>(relaxed = true)
        val listenerRef = mutableListOf<Player.Listener>()
        every { player.playbackState } returnsMany listOf(Player.STATE_BUFFERING, Player.STATE_READY)
        every { player.addListener(capture(listenerRef)) } answers { }

        val ready = awaitPlayerReadyOnPlayer(
            player = player,
            timeoutMs = 5_000L,
            mainDispatcher = testDispatcher
        )

        assertThat(ready).isTrue()
        assertThat(listenerRef).hasSize(1)
        verify(exactly = 1) { player.removeListener(listenerRef.first()) }
    }

    @Test
    fun awaitPlayerReadyOnPlayer_waitsForReadyCallbackAndCleansUpListener() = runTest(testDispatcher) {
        val player = mockk<Player>(relaxed = true)
        val listenerRef = mutableListOf<Player.Listener>()
        every { player.playbackState } returns Player.STATE_BUFFERING
        every { player.addListener(capture(listenerRef)) } answers { }

        val deferred = async {
            awaitPlayerReadyOnPlayer(
                player = player,
                timeoutMs = 5_000L,
                mainDispatcher = testDispatcher
            )
        }
        runCurrent()

        assertThat(listenerRef).hasSize(1)
        listenerRef.first().onPlaybackStateChanged(Player.STATE_READY)

        assertThat(deferred.await()).isTrue()
        verify(atLeast = 1) { player.removeListener(listenerRef.first()) }
    }
}
