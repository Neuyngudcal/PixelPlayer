package com.theveloper.pixelplay.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import timber.log.Timber

class SleepTimerReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SleepTimerEntryPoint {
        fun sleepTimerCoordinator(): SleepTimerCoordinator
    }

    @VisibleForTesting
    internal var coordinatorOverride: SleepTimerCoordinator? = null

    override fun onReceive(context: Context, intent: Intent) {
        val coordinator = coordinatorOverride ?: runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                SleepTimerEntryPoint::class.java,
            ).sleepTimerCoordinator()
        }.getOrNull()

        if (coordinator == null) {
            Timber.tag(TAG).w("SleepTimerCoordinator unavailable; skipping sleep timer broadcast")
            return
        }

        val handledByActiveService = coordinator.onTimerExpired(context)
        Timber.tag(TAG).d(
            "Sleep timer expired (action=%s, handledByActiveService=%s)",
            intent.action,
            handledByActiveService,
        )
    }

    private companion object {
        const val TAG = "SleepTimerReceiver"
    }
}
