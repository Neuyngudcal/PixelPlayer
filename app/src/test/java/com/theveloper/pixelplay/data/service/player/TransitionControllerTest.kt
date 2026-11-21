package com.theveloper.pixelplay.data.service.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.model.TransitionMode
import com.theveloper.pixelplay.data.model.TransitionResolution
import com.theveloper.pixelplay.data.model.TransitionSettings
import com.theveloper.pixelplay.data.model.TransitionSource
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.TransitionRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransitionControllerTest {

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
    fun initializeAfterRelease_reusesSingletonScopeAndSchedulesTransitions() = runTest(testDispatcher) {
        val player = mockk<Player>(relaxed = true)
        val engine = mockk<DualPlayerEngine>(relaxed = true)
        val transitionRepository = mockk<TransitionRepository>()
        val userPreferencesRepository = mockk<UserPreferencesRepository>()

        val attachedListeners = mutableListOf<Player.Listener>()
        val swapListeners = mutableListOf<(Player) -> Unit>()

        every { engine.masterPlayer } returns player
        every { engine.isTransitionRunning() } returns false
        every { engine.addPlayerSwapListener(capture(swapListeners)) } just runs
        every { engine.removePlayerSwapListener(any()) } answers {
            swapListeners.remove(firstArg())
        }
        every { player.addListener(capture(attachedListeners)) } just runs
        every { player.removeListener(any()) } answers {
            attachedListeners.remove(firstArg())
        }
        every { player.isPlaying } returns true
        every { player.playWhenReady } returns true
        every { player.currentPosition } returns 0L
        every { player.duration } returns 30_000L
        every { player.repeatMode } returns Player.REPEAT_MODE_OFF

        val settings = TransitionSettings(
            mode = TransitionMode.OVERLAP,
            durationMs = 2_000
        )
        every { transitionRepository.getGlobalSettings() } returns flowOf(settings)
        every { transitionRepository.resolveTransitionSettings(any(), any(), any()) } returns flowOf(
            TransitionResolution(settings = settings, source = TransitionSource.GLOBAL_DEFAULT)
        )
        every { userPreferencesRepository.isCrossfadeEnabledFlow } returns MutableStateFlow(true)
        coEvery { engine.prepareNext(any<DualPlayerEngine.TransitionTarget>(), any()) } just runs

        val currentItem = MediaItem.Builder().setMediaId("track-1").build()
        val nextItem = MediaItem.Builder().setMediaId("track-2").build()
        every { player.currentMediaItem } returns currentItem
        every { engine.getNextTransitionTarget(currentItem, Player.REPEAT_MODE_OFF) } returns
            DualPlayerEngine.TransitionTarget(mediaItem = nextItem, absoluteIndex = 1, queueSize = 2)

        val controller = TransitionController(
            engine = engine,
            transitionRepository = transitionRepository,
            userPreferencesRepository = userPreferencesRepository
        )

        // Cycle 1: initialize -> release
        controller.initialize()
        assertThat(attachedListeners).hasSize(1)
        assertThat(swapListeners).hasSize(1)

        attachedListeners.last().onMediaItemTransition(currentItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        runCurrent()

        controller.release()
        assertThat(attachedListeners).isEmpty()
        assertThat(swapListeners).isEmpty()

        // Cycle 2: re-initialize after release must still have an active coroutine scope.
        controller.initialize()
        assertThat(attachedListeners).hasSize(1)
        assertThat(swapListeners).hasSize(1)

        attachedListeners.last().onMediaItemTransition(currentItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        advanceTimeBy(2_000L)
        runCurrent()

        verify(atLeast = 1) { engine.getNextTransitionTarget(currentItem, Player.REPEAT_MODE_OFF) }

        controller.release()
        advanceUntilIdle()
    }
}
