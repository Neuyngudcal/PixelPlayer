package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AutoScrollingTextOnDemand(
    text: String,
    style: TextStyle,
    gradientEdgeColor: Color,
    expansionFractionProvider: () -> Float,
    modifier: Modifier = Modifier,
    canScroll: Boolean = true
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val layoutKey = remember(
        text,
        style.fontSize,
        style.fontWeight,
        style.fontStyle,
        style.fontFamily,
        style.letterSpacing,
        configuration.screenWidthDp,
        configuration.orientation
    ) {
        listOf(
            text,
            style.fontSize,
            style.fontWeight,
            style.fontStyle,
            style.fontFamily,
            style.letterSpacing,
            configuration.screenWidthDp,
            configuration.orientation
        )
    }
    var overflow by remember(layoutKey) { mutableStateOf(false) }
    val canStart by remember(layoutKey) { derivedStateOf { expansionFractionProvider() > 0.99f && overflow } }


// Usamos un Text "medidor" sólo la primera composición para detectar overflow.
    if (!overflow) {
        Text(
            text = text,
            style = style,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { res: TextLayoutResult -> overflow = res.hasVisualOverflow },
            modifier = modifier
        )
    } else {
        AutoScrollingText(
            text = text,
            style = style,
            textAlign = TextAlign.Start,
            gradientEdgeColor = gradientEdgeColor,
            modifier = modifier,
            canScroll = canScroll && canStart
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AutoScrollingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    textAlign: TextAlign? = null,
    gradientEdgeColor: Color,
    gradientWidth: Dp = 24.dp,
    canScroll: Boolean = true
) {
    val configuration = LocalConfiguration.current
    var containerWidthPx by remember { mutableIntStateOf(0) }
    val layoutKey = remember(
        text,
        style.fontSize,
        style.fontWeight,
        style.fontStyle,
        style.fontFamily,
        style.letterSpacing,
        configuration.screenWidthDp,
        configuration.orientation,
        containerWidthPx
    ) {
        listOf(
            text,
            style.fontSize,
            style.fontWeight,
            style.fontStyle,
            style.fontFamily,
            style.letterSpacing,
            configuration.screenWidthDp,
            configuration.orientation,
            containerWidthPx
        )
    }
    var isOverflowing by remember(layoutKey) { mutableStateOf(false) }

    val initialDelayMillis = 2000
    val shouldScroll = isOverflowing && canScroll

    var isScrolling by remember(text, shouldScroll) { mutableStateOf(false) }
    LaunchedEffect(text, shouldScroll) {
        if (!shouldScroll) {
            isScrolling = false
            return@LaunchedEffect
        }
        isScrolling = false
        kotlinx.coroutines.delay(initialDelayMillis.toLong())
        isScrolling = true
    }

    val leftEdgeAlphaState = animateFloatAsState(
        targetValue = if (shouldScroll && isScrolling) 0f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "LeftGradientStartAlpha"
    )

    val containerModifier = modifier
        .clipToBounds()
        .onSizeChanged { containerSize ->
            if (containerSize.width != containerWidthPx) {
                containerWidthPx = containerSize.width
            }
        }
        .let { base ->
            if (isOverflowing) {
                base
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithCache {
                        val gradientWidthPx = gradientWidth.toPx().coerceAtMost(size.width / 2f)
                        val leftFullFadeBrush = if (shouldScroll && gradientWidthPx > 0f) {
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startX = 0f,
                                endX = gradientWidthPx
                            )
                        } else {
                            null
                        }
                        val rightBrush = if (gradientWidthPx > 0f) {
                            Brush.horizontalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startX = (size.width - gradientWidthPx).coerceAtLeast(0f),
                                endX = size.width
                            )
                        } else {
                            null
                        }

                        onDrawWithContent {
                            drawContent()
                            if (leftFullFadeBrush != null) {
                                val startAlpha = leftEdgeAlphaState.value
                                when {
                                    startAlpha <= 0.001f -> {
                                        drawRect(
                                            brush = leftFullFadeBrush,
                                            blendMode = BlendMode.DstIn
                                        )
                                    }
                                    startAlpha < 0.999f -> {
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(Color.Black.copy(alpha = startAlpha), Color.Black),
                                                startX = 0f,
                                                endX = gradientWidthPx
                                            ),
                                            blendMode = BlendMode.DstIn
                                        )
                                    }
                                }
                            }
                            if (rightBrush != null) {
                                drawRect(
                                    brush = rightBrush,
                                    blendMode = BlendMode.DstIn
                                )
                            }
                        }
                    }
            } else {
                base
            }
        }

    Box(
        modifier = containerModifier,
        propagateMinConstraints = true
    ) {
        val textModifier = if (shouldScroll) {
            Modifier.basicMarquee(
                iterations = Int.MAX_VALUE,
                spacing = MarqueeSpacing(gradientWidth + 6.dp),
                velocity = 25.dp,
                initialDelayMillis = initialDelayMillis
            )
        } else {
            Modifier
        }

        Text(
            text = text,
            style = style,
            textAlign = textAlign,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { result: TextLayoutResult ->
                // basicMarquee measures text with unbounded width so hasVisualOverflow becomes false;
                // only record overflow while static measurement is active.
                if (!shouldScroll) {
                    isOverflowing = result.hasVisualOverflow
                }
            },
            modifier = textModifier
        )
    }
}