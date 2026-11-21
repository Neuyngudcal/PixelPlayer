package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.presentation.components.LocalMaterialTheme
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun ToggleSegmentButton(
    modifier: Modifier,
    active: Boolean,
    enabled: Boolean = true,
    activeColor: Color,
    inactiveColor: Color = Color.Gray,
    activeContentColor: Color = LocalMaterialTheme.current.onPrimary,
    inactiveContentColor: Color = LocalMaterialTheme.current.onSurfaceVariant,
    activeCornerRadius: Dp = 8.dp,
    onClick: () -> Unit,
    iconId: Int,
    contentDesc: String
) {
    ToggleSegmentButtonContainer(
        modifier = modifier,
        active = active,
        enabled = enabled,
        activeColor = activeColor,
        inactiveColor = inactiveColor,
        activeCornerRadius = activeCornerRadius,
        onClick = onClick
    ) {
        Icon(
            painter = painterResource(iconId),
            contentDescription = contentDesc,
            tint = if (active) activeContentColor else inactiveContentColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun ToggleSegmentButton(
    modifier: Modifier,
    active: Boolean,
    enabled: Boolean = true,
    activeColor: Color,
    inactiveColor: Color = Color.Gray,
    activeContentColor: Color = LocalMaterialTheme.current.onPrimary,
    inactiveContentColor: Color = LocalMaterialTheme.current.onSurfaceVariant,
    activeCornerRadius: Dp = 8.dp,
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDesc: String
) {
    ToggleSegmentButtonContainer(
        modifier = modifier,
        active = active,
        enabled = enabled,
        activeColor = activeColor,
        inactiveColor = inactiveColor,
        activeCornerRadius = activeCornerRadius,
        onClick = onClick
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDesc,
            tint = if (active) activeContentColor else inactiveContentColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun ToggleSegmentButton(
    modifier: Modifier,
    active: Boolean,
    enabled: Boolean = true,
    activeColor: Color,
    inactiveColor: Color = Color.Gray,
    activeContentColor: Color = LocalMaterialTheme.current.onPrimary,
    inactiveContentColor: Color = LocalMaterialTheme.current.primary,
    activeCornerRadius: Dp = 8.dp,
    onClick: () -> Unit,
    text: String,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign = TextAlign.Center
) {
    ToggleSegmentButtonContainer(
        modifier = modifier,
        active = active,
        enabled = enabled,
        activeColor = activeColor,
        inactiveColor = inactiveColor,
        activeCornerRadius = activeCornerRadius,
        onClick = onClick
    ) {
        Text(
            text = text,
            color = if (active) activeContentColor else inactiveContentColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = maxLines,
            overflow = overflow,
            textAlign = textAlign
        )
    }
}

@Composable
fun ToggleSegmentButton(
    modifier: Modifier,
    active: Boolean,
    enabled: Boolean = true,
    activeColor: Color,
    inactiveColor: Color = Color.Gray,
    activeContentColor: Color = LocalMaterialTheme.current.onPrimary,
    inactiveContentColor: Color = LocalMaterialTheme.current.onSurfaceVariant,
    activeCornerRadius: Dp = 8.dp,
    onClick: () -> Unit,
    text: String,
    imageVector: ImageVector,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign = TextAlign.Center
) {
    ToggleSegmentButtonContainer(
        modifier = modifier,
        active = active,
        enabled = enabled,
        activeColor = activeColor,
        inactiveColor = inactiveColor,
        activeCornerRadius = activeCornerRadius,
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = if (active) activeContentColor else inactiveContentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = if (active) activeContentColor else inactiveContentColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = maxLines,
                overflow = overflow,
                textAlign = textAlign
            )
        }
    }
}


@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToggleSegmentButtonContainer(
    modifier: Modifier,
    active: Boolean,
    enabled: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    activeCornerRadius: Dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val motionScheme = androidx.compose.material3.MaterialTheme.motionScheme
    val activeFractionState = animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "segmentActiveFraction"
    )
    val cornerRadiusState = animateDpAsState(
        targetValue = if (active) activeCornerRadius else 14.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "segmentCornerRadius"
    )
    val contentScaleState = animateFloatAsState(
        targetValue = if (active) 1f else 0.93f,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "segmentContentScale"
    )
    val latestActiveColor = rememberUpdatedState(activeColor)
    val latestInactiveColor = rememberUpdatedState(inactiveColor)
    val latestEnabled = rememberUpdatedState(enabled)

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                shape = RoundedCornerShape(cornerRadiusState.value.toPx())
                clip = true
            }
            .drawBehind {
                val baseColor = lerp(
                    latestInactiveColor.value,
                    latestActiveColor.value,
                    activeFractionState.value
                )
                drawRect(color = if (latestEnabled.value) baseColor else baseColor.copy(alpha = 0.5f))
            }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    alpha = if (latestEnabled.value) 1f else 0.38f
                    val scale = contentScaleState.value
                    scaleX = scale
                    scaleY = scale
                }
                .padding(horizontal = 10.dp)
        ) {
            content()
        }
    }
}
