package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RowScope.CustomNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    selectedIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    contentDescription: String? = null,
    alwaysShowLabel: Boolean = true,
    selectedIconColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    unselectedIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedTextColor: Color = MaterialTheme.colorScheme.onSurface,
    unselectedTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    indicatorColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    // Animated tokens keyed by selection state using Material 3 Expressive motion specs.
    val motionScheme = MaterialTheme.motionScheme
    val iconColor by animateColorAsState(
        targetValue = if (selected) selectedIconColor else unselectedIconColor,
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "iconColor"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) selectedTextColor else unselectedTextColor,
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "textColor"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "iconScale"
    )

    val showLabel = label != null && (alwaysShowLabel || selected)
    val indicatorWidth = 64.dp
    val indicatorHeight = 32.dp
    val iconWidth = 48.dp
    val iconHeight = 24.dp
    val indicatorPadding = 4.dp
    val indicatorShape = RoundedCornerShape(16.dp)
    val iconShape = RoundedCornerShape(12.dp)
    val itemTouchShape = RoundedCornerShape(20.dp)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(itemTouchShape)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                if (!showLabel && contentDescription != null) {
                    this.contentDescription = contentDescription
                }
            }
            .clickable(
                onClick = onClick,
                enabled = enabled,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = ripple(bounded = true)
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(indicatorWidth, indicatorHeight)
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = selected,
                enter = fadeIn(animationSpec = motionScheme.fastEffectsSpec()) +
                    scaleIn(animationSpec = motionScheme.fastSpatialSpec(), initialScale = 0.8f),
                exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                    scaleOut(animationSpec = motionScheme.fastSpatialSpec(), targetScale = 0.8f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = indicatorPadding)
                        .background(
                            color = indicatorColor,
                            shape = indicatorShape
                        )
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(iconWidth, iconHeight)
                    .clip(iconShape)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            ) {
                CompositionLocalProvider(LocalContentColor provides iconColor) {
                    Box(modifier = Modifier.clearAndSetSemantics {}) {
                        if (selected) selectedIcon() else icon()
                    }
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showLabel,
            enter = fadeIn(animationSpec = motionScheme.fastEffectsSpec()),
            exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec())
        ) {
            Box(
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
            ) {
                ProvideTextStyle(
                    value = MaterialTheme.typography.labelMedium.copy(
                        color = textColor,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                ) {
                    label?.invoke()
                }
            }
        }
    }
}
