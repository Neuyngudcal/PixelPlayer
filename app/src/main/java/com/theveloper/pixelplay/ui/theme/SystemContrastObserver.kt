package com.theveloper.pixelplay.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes Android 14+ (API 34) system color contrast level via [UiModeManager.getContrast]
 * and [UiModeManager.ContrastChangeListener], mirroring Google's `DynamicColors.getSystemContrast`.
 * Falls back cleanly to `0.0` (standard contrast) on API 30-33.
 */
@Singleton
class SystemContrastObserver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _systemContrastFlow = MutableStateFlow(readSystemContrast(context))
    val systemContrastFlow: StateFlow<Double> = _systemContrastFlow.asStateFlow()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching {
                val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
                uiModeManager?.addContrastChangeListener(context.mainExecutor) { contrast ->
                    _systemContrastFlow.value = contrast.toDouble().coerceIn(-1.0, 1.0)
                }
            }
        }
    }

    fun currentContrast(): Double {
        val latest = readSystemContrast(context)
        if (_systemContrastFlow.value != latest) {
            _systemContrastFlow.value = latest
        }
        return latest
    }

    companion object {
        fun readSystemContrast(context: Context): Double {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return 0.0
            return runCatching {
                val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
                uiModeManager?.contrast?.toDouble()?.coerceIn(-1.0, 1.0) ?: 0.0
            }.getOrDefault(0.0)
        }
    }
}
