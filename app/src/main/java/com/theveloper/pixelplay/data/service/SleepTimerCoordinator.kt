package com.theveloper.pixelplay.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-process coordinator for sleep timer expiration events.
 *
 * Prevents [SleepTimerReceiver] from calling `Context.startService()` or
 * `Context.startForegroundService()` while the app is backgrounded on Android 12+
 * when [MusicService] is stopped or playback is paused.
 */
@Singleton
class SleepTimerCoordinator @VisibleForTesting internal constructor(
    private val dispatchToMain: (() -> Unit) -> Unit,
) {
    @Inject
    constructor() : this(
        dispatchToMain = { action ->
            val mainLooper = runCatching { Looper.getMainLooper() }.getOrNull()
            if (mainLooper != null && mainLooper != Looper.myLooper()) {
                Handler(mainLooper).post(action)
            } else {
                action()
            }
        }
    )

    private val activeServiceHandler = AtomicReference<(() -> Unit)?>(null)
    private val activeStateCleanupHandler = AtomicReference<(() -> Unit)?>(null)

    fun registerServiceExpirationHandler(handler: () -> Unit) {
        activeServiceHandler.set(handler)
    }

    fun unregisterServiceExpirationHandler(handler: () -> Unit) {
        activeServiceHandler.compareAndSet(handler, null)
    }

    fun registerStateCleanupHandler(handler: () -> Unit) {
        activeStateCleanupHandler.set(handler)
    }

    fun isServiceActive(): Boolean = activeServiceHandler.get() != null

    /**
     * Handles sleep timer expiration safely in-process without starting a service.
     *
     * @return `true` if an active [MusicService] handler was notified; `false` if the service
     * is not running and only stale alarm/state cleanup was performed.
     */
    fun onTimerExpired(context: Context): Boolean {
        val serviceHandler = activeServiceHandler.get()
        val stateCleanup = activeStateCleanupHandler.get()

        cancelPendingAlarms(context)

        if (serviceHandler == null) {
            Timber.tag(TAG).d("Sleep timer expired while MusicService is inactive; clearing stale state")
            stateCleanup?.let { cleanup ->
                dispatchToMain { cleanup() }
            }
            return false
        }

        dispatchToMain {
            stateCleanup?.invoke()
            serviceHandler.invoke()
        }
        return true
    }

    /**
     * Cancels any scheduled sleep timer alarms registered for [SleepTimerReceiver].
     */
    fun cancelPendingAlarms(context: Context) {
        val alarmManager = runCatching {
            context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        }.getOrNull() ?: return

        KNOWN_SLEEP_TIMER_ACTIONS.forEach { action ->
            val intent = Intent(context, SleepTimerReceiver::class.java).apply {
                this.action = action
                setPackage(context.packageName)
            }
            val pendingIntent = runCatching {
                PendingIntent.getBroadcast(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                )
            }.getOrNull()
            if (pendingIntent != null) {
                runCatching {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
            }
        }
    }

    companion object {
        private const val TAG = "SleepTimerCoordinator"
        const val ACTION_SERVICE_SLEEP_TIMER_EXPIRED = "com.theveloper.pixelplay.ACTION_SLEEP_TIMER_EXPIRED"
        const val ACTION_VIEWMODEL_SLEEP_TIMER_EXPIRED = "com.theveloper.pixelplay.action.SLEEP_TIMER_EXPIRED"

        private val KNOWN_SLEEP_TIMER_ACTIONS = listOf(
            ACTION_SERVICE_SLEEP_TIMER_EXPIRED,
            ACTION_VIEWMODEL_SLEEP_TIMER_EXPIRED,
        )
    }
}
