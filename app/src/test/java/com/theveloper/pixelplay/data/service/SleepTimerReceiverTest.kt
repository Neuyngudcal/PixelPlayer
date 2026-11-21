package com.theveloper.pixelplay.data.service

import android.content.Context
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SleepTimerReceiverTest {

    @Test
    fun `onReceive clears stale timer state without starting service when MusicService is inactive`() {
        val context = mockk<Context>(relaxed = true)
        every { context.packageName } returns "com.theveloper.pixelplay"
        every { context.getSystemService(Context.ALARM_SERVICE) } returns null
        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns SleepTimerCoordinator.ACTION_VIEWMODEL_SLEEP_TIMER_EXPIRED

        val coordinator = SleepTimerCoordinator(dispatchToMain = { action -> action() })
        var stateCleanupCount = 0
        coordinator.registerStateCleanupHandler { stateCleanupCount++ }

        val receiver = SleepTimerReceiver().apply {
            coordinatorOverride = coordinator
        }

        receiver.onReceive(context, intent)

        assertFalse(coordinator.isServiceActive())
        assertEquals(1, stateCleanupCount)
        verify(exactly = 0) { context.startService(any()) }
        verify(exactly = 0) { context.startForegroundService(any()) }
    }

    @Test
    fun `onReceive dispatches in-process expiration handler without starting service when MusicService is active`() {
        val context = mockk<Context>(relaxed = true)
        every { context.packageName } returns "com.theveloper.pixelplay"
        every { context.getSystemService(Context.ALARM_SERVICE) } returns null
        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns SleepTimerCoordinator.ACTION_SERVICE_SLEEP_TIMER_EXPIRED

        val coordinator = SleepTimerCoordinator(dispatchToMain = { action -> action() })
        var stateCleanupCount = 0
        var serviceExpiredCount = 0
        coordinator.registerStateCleanupHandler { stateCleanupCount++ }
        val serviceHandler: () -> Unit = { serviceExpiredCount++ }
        coordinator.registerServiceExpirationHandler(serviceHandler)

        val receiver = SleepTimerReceiver().apply {
            coordinatorOverride = coordinator
        }

        receiver.onReceive(context, intent)

        assertTrue(coordinator.isServiceActive())
        assertEquals(1, stateCleanupCount)
        assertEquals(1, serviceExpiredCount)
        verify(exactly = 0) { context.startService(any()) }
        verify(exactly = 0) { context.startForegroundService(any()) }
    }
}
