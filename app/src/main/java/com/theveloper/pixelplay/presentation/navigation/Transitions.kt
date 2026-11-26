package com.theveloper.pixelplay.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

// Curva Emphasized oficial de Material 3 (la que usan los Pixel contemporáneos)
val M3EmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

// MotionTokens.EasingEmphasizedAccelerateCubicBezier: arranca lento y acelera al salir. Durante el
// gesto de predictive back la pantalla apenas se desplaza y, al confirmarse, sale por completo.
val M3EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

// MotionTokens.EasingEmphasizedDecelerateCubicBezier: enters at peak velocity, settles gently.
val M3EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
const val AOSP_TRANSITION_DURATION = 350

// M3 "Top level" transition pattern (fade through, MDC MaterialFadeThrough). The outgoing screen
// fades out completely during the first 35 % before the incoming one fades in, so no cross-faded
// frames are drawn (M3 "clean fades"). 35 % is also the fade-through threshold of Android's
// predictive back spec, so a back gesture between top-level destinations scrubs the same timeline.
// M3 transitions still use easing + duration (not springs) and must not bounce.
const val TOP_LEVEL_TRANSITION_DURATION = 300 // MotionTokens.DurationMedium2
private const val TOP_LEVEL_FADE_OUT_DURATION = TOP_LEVEL_TRANSITION_DURATION * 35 / 100
private const val TOP_LEVEL_FADE_IN_DURATION = TOP_LEVEL_TRANSITION_DURATION - TOP_LEVEL_FADE_OUT_DURATION
private const val TOP_LEVEL_ENTER_INITIAL_SCALE = 0.92f

fun topLevelFadeThroughEnter(): EnterTransition {
    return fadeIn(
        animationSpec = tween(
            durationMillis = TOP_LEVEL_FADE_IN_DURATION,
            delayMillis = TOP_LEVEL_FADE_OUT_DURATION,
            easing = M3EmphasizedDecelerateEasing
        )
    ) + scaleIn(
        initialScale = TOP_LEVEL_ENTER_INITIAL_SCALE,
        animationSpec = tween(
            durationMillis = TOP_LEVEL_FADE_IN_DURATION,
            delayMillis = TOP_LEVEL_FADE_OUT_DURATION,
            easing = M3EmphasizedDecelerateEasing
        )
    )
}

fun topLevelFadeThroughExit(): ExitTransition {
    return fadeOut(
        animationSpec = tween(
            durationMillis = TOP_LEVEL_FADE_OUT_DURATION,
            easing = M3EmphasizedAccelerateEasing
        )
    )
}

// Equivale a sud_slide_next_in
fun aospSharedAxisEnter(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { it / 3 }, // Arranca desde 1/3 de la pantalla hacia la derecha
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    ) + fadeIn(
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    )
}

// Equivale a sud_slide_next_out
fun aospSharedAxisExit(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { -it / 3 }, // Se desplaza 1/3 de la pantalla hacia la izquierda
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    ) + scaleOut(
        targetScale = 0.92f, // Escala hacia abajo para dar efecto de profundidad sin desaparecer por completo
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    )
}

// Equivale a sud_slide_back_in (cuando volvés atrás, la pantalla previa reaparece desde la izquierda)
fun aospSharedAxisPopEnter(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { -it / 3 }, // Reaparece desde 1/3 de la izquierda
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    ) + fadeIn(
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    )
}

// Equivale a sud_slide_back_out (la pantalla secundaria se destruye deslizándose a la derecha)
fun aospSharedAxisPopExit(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { it }, // Se desliza completamente fuera de la pantalla
        animationSpec = tween(
            durationMillis = AOSP_TRANSITION_DURATION,
            easing = M3EmphasizedAccelerateEasing // Durante el arrastre se desplaza muy poco, y al completarse se desliza por completo
        )
    ) + scaleOut(
        targetScale = 0.85f, // Se achica más (efecto de profundidad más pronunciado)
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    )
}

// M3 "Container transform" (hero image variant: Library album card -> AlbumDetail header).
// The artwork + header morph in the shared-transition overlay (SharedHeroTransitions.kt); around it
// the old screen fades out completely in the first 35 % and the new one fades in afterwards
// (M3 "clean fades"). Emphasized easing + 500 ms is M3's pair for motion that begins and ends on
// screen. Never a spring: common transitions must not bounce.
const val CONTAINER_TRANSFORM_DURATION = 500 // MotionTokens.DurationLong2
private const val CONTAINER_TRANSFORM_FADE_OUT_DURATION = CONTAINER_TRANSFORM_DURATION * 35 / 100

fun containerTransformEnter(): EnterTransition {
    return fadeIn(
        animationSpec = tween(
            durationMillis = CONTAINER_TRANSFORM_DURATION - CONTAINER_TRANSFORM_FADE_OUT_DURATION,
            delayMillis = CONTAINER_TRANSFORM_FADE_OUT_DURATION,
            easing = M3EmphasizedDecelerateEasing
        )
    )
}

fun containerTransformExit(): ExitTransition {
    return fadeOut(
        animationSpec = tween(
            durationMillis = CONTAINER_TRANSFORM_FADE_OUT_DURATION,
            easing = M3EmphasizedAccelerateEasing
        )
    )
}
