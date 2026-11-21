package com.theveloper.pixelplay.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import com.theveloper.pixelplay.presentation.viewmodel.ColorSchemePair

val LocalPixelPlayDarkTheme = staticCompositionLocalOf { false }
val LocalShowScrollbar = staticCompositionLocalOf { true }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Suppress("DEPRECATION")
@Composable
fun PixelPlayStatusBarStyle(
    color: Color,
    useDarkIcons: Boolean = ColorUtils.calculateLuminance(color.toArgb()) > 0.55,
    navigationColor: Color? = null,
    useDarkNavigationIcons: Boolean = navigationColor
        ?.let { ColorUtils.calculateLuminance(it.toArgb()) > 0.55 }
        ?: useDarkIcons
) {
    val view = LocalView.current
    if (view.isInEditMode) return

    val updateNavigationBar = navigationColor != null
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
        }

        WindowCompat.getInsetsController(window, view).run {
            isAppearanceLightStatusBars = useDarkIcons

            if (updateNavigationBar) {
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                isAppearanceLightNavigationBars = useDarkNavigationIcons
            }
        }
    }
}

/**
 * Full 48-role fallback brand color schemes generated through Google's Material Color Utilities
 * ([com.google.android.material.color.utilities.SchemeTonalSpot]) from [PixelPlayBrandSeed].
 */
val DefaultBrandColorSchemePair: ColorSchemePair by lazy {
    generateColorSchemeFromSeed(
        seedColor = PixelPlayBrandSeed,
        paletteStyle = AlbumArtPaletteStyle.TONAL_SPOT,
        contrastLevel = 0.0
    )
}

val DarkColorScheme: ColorScheme
    get() = DefaultBrandColorSchemePair.dark

val LightColorScheme: ColorScheme
    get() = DefaultBrandColorSchemePair.light

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PixelPlayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorSchemePairOverride: ColorSchemePair? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val finalColorScheme = when {
        colorSchemePairOverride == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            try {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } catch (_: Exception) {
                if (darkTheme) DarkColorScheme else LightColorScheme
            }
        }
        colorSchemePairOverride != null -> {
            if (darkTheme) colorSchemePairOverride.dark else colorSchemePairOverride.light
        }
        else -> {
            val systemContrast = remember(context) {
                SystemContrastObserver.readSystemContrast(context)
            }
            val brandSchemePair = remember(systemContrast) {
                if (quantizeContrastLevel(systemContrast) == 0) {
                    DefaultBrandColorSchemePair
                } else {
                    generateColorSchemeFromSeed(
                        seedColor = PixelPlayBrandSeed,
                        paletteStyle = AlbumArtPaletteStyle.TONAL_SPOT,
                        contrastLevel = systemContrast
                    )
                }
            }
            if (darkTheme) brandSchemePair.dark else brandSchemePair.light
        }
    }

    PixelPlayStatusBarStyle(
        color = finalColorScheme.background,
        navigationColor = finalColorScheme.background
    )

    val windowInfo = rememberPixelPlayWindowInfo()
    val spacing = remember(windowInfo.widthTier) { spacingForWidthTier(windowInfo.widthTier) }

    CompositionLocalProvider(
        LocalPixelPlayDarkTheme provides darkTheme,
        LocalPixelPlayWindowInfo provides windowInfo,
        LocalSpacing provides spacing
    ) {
        MaterialTheme(
            colorScheme = finalColorScheme,
            motionScheme = androidx.compose.material3.MotionScheme.expressive(),
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
