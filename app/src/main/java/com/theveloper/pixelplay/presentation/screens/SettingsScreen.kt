package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.theveloper.pixelplay.presentation.model.SettingsCategory
import com.theveloper.pixelplay.presentation.screens.settings.SettingsAdaptiveScreen
import com.theveloper.pixelplay.presentation.screens.settings.rememberCategoryColors
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import com.theveloper.pixelplay.ui.theme.LocalSpacing
import com.theveloper.pixelplay.ui.theme.PixelPlayGroupShapes

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    onNavigationIconClick: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    SettingsAdaptiveScreen(
        navController = navController,
        playerViewModel = playerViewModel,
        onNavigationIconClick = onNavigationIconClick,
        settingsViewModel = settingsViewModel
    )
}

@Composable
fun ExpressiveNavigationItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    colors: Pair<Color, Color>,
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(PixelPlayGroupShapes.GroupOuterRadius)
) {
    val spacing = LocalSpacing.current
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = spacing.listRowHorizontal, vertical = spacing.listRowVertical)
                .fillMaxSize()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.first)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.second,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(spacing.iconTextGap))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun ExpressiveCategoryItem(
    category: SettingsCategory,
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(PixelPlayGroupShapes.GroupOuterRadius),
    customColors: Pair<Color, Color>? = null
) {
    val spacing = LocalSpacing.current
    val resolvedColors = customColors ?: rememberCategoryColors(category).let {
        it.containerColor to it.iconColor
    }
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = spacing.listRowHorizontal, vertical = spacing.listRowVertical)
                .fillMaxSize()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(resolvedColors.first)
            ) {
                if (category.icon != null) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = resolvedColors.second,
                        modifier = Modifier.size(22.dp)
                    )
                } else if (category.iconRes != null) {
                    Icon(
                        painter = painterResource(id = category.iconRes),
                        contentDescription = null,
                        tint = resolvedColors.second,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(spacing.iconTextGap))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(category.titleRes),
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = stringResource(category.subtitleRes),
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun ExpressiveSettingsGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PixelPlayGroupShapes.GroupOuterRadius))
            .background(Color.Transparent)
    ) {
        content()
    }
}
