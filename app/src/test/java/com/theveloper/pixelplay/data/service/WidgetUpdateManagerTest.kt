package com.theveloper.pixelplay.data.service

import android.content.Context
import com.theveloper.pixelplay.data.model.PlayerInfo
import com.theveloper.pixelplay.data.service.wear.WearStatePublisher
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetUpdateManagerTest {

    @Test
    fun `requestFullUpdate debounces forced update while flushImmediateUpdate executes synchronously without delay`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val managerScope = TestScope(testDispatcher)
            val context = mockk<Context>(relaxed = true)
            val wearStatePublisher = mockk<WearStatePublisher>(relaxed = true)

            var currentInfo = PlayerInfo(songTitle = "Track A", isPlaying = true)
            val updatedWidgets = mutableListOf<PlayerInfo>()

            val manager = WidgetUpdateManager(
                context = context,
                scope = managerScope,
                wearStatePublisher = wearStatePublisher,
                buildPlayerInfo = { currentInfo },
                resolveCurrentMediaIdForWear = { "song-1" },
                widgetUpdater = { info -> updatedWidgets.add(info) },
            )

            // Queue a forced debounced update (250ms delay). At t=0 it should not have executed yet.
            manager.requestFullUpdate(force = true)
            managerScope.runCurrent()
            assertEquals(0, updatedWidgets.size)

            // Flip playback state as stopPlaybackAndUnload() does and flush immediately.
            currentInfo = PlayerInfo(songTitle = "Track A", isPlaying = false)
            manager.flushImmediateUpdate()

            assertEquals(1, updatedWidgets.size)
            assertEquals(false, updatedWidgets.single().isPlaying)
            verify(exactly = 1) {
                wearStatePublisher.publishState("song-1", currentInfo)
            }

            // Advancing past the old 250ms debounce must NOT re-trigger the cancelled debounced job.
            managerScope.advanceTimeBy(500L)
            managerScope.runCurrent()
            assertEquals(1, updatedWidgets.size)
        }
}
