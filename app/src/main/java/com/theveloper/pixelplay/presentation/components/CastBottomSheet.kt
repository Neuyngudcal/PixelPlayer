package com.theveloper.pixelplay.presentation.components

import android.Manifest
import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import android.content.Intent
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import com.theveloper.pixelplay.presentation.components.player.PlayerCardOverlayDialog
import com.theveloper.pixelplay.presentation.components.player.PlayerCardOverlaySheet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaRouter
import com.theveloper.pixelplay.presentation.screens.TabAnimation
import com.theveloper.pixelplay.presentation.viewmodel.BluetoothAudioDeviceState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import android.content.pm.PackageManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastBottomSheet(
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    onExpansionChanged: (Float) -> Unit = {},
    isContainedInCard: Boolean = false
) {
    val routes by playerViewModel.castRoutes.collectAsStateWithLifecycle()
    val selectedRoute by playerViewModel.selectedRoute.collectAsStateWithLifecycle()
    val routeVolume by playerViewModel.routeVolume.collectAsStateWithLifecycle()
    val isRefreshing by playerViewModel.isRefreshingRoutes.collectAsStateWithLifecycle()
    val isWifiEnabled by playerViewModel.isWifiEnabled.collectAsStateWithLifecycle()
    val isWifiRadioOn by playerViewModel.isWifiRadioOn.collectAsStateWithLifecycle()
    val wifiName by playerViewModel.wifiName.collectAsStateWithLifecycle()
    val isBluetoothEnabled by playerViewModel.isBluetoothEnabled.collectAsStateWithLifecycle()
    val bluetoothName by playerViewModel.bluetoothName.collectAsStateWithLifecycle()
    val bluetoothAudioDeviceStates by playerViewModel.bluetoothAudioDeviceStates.collectAsStateWithLifecycle()
    val connectingBluetoothAddress by playerViewModel.connectingBluetoothAddress.collectAsStateWithLifecycle()
    val isRemotePlaybackActive by playerViewModel.isRemotePlaybackActive.collectAsStateWithLifecycle()
    val isCastConnecting by playerViewModel.isCastConnecting.collectAsStateWithLifecycle()
    val localDeviceVolume by playerViewModel.localDeviceVolume.collectAsStateWithLifecycle()
    val isPlaying = playerViewModel.stablePlayerState.collectAsStateWithLifecycle().value.isPlaying
    val context = LocalContext.current

    val requiredPermissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }
    var missingPermissions by remember { mutableStateOf(missingCastPermissions(context, requiredPermissions)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        missingPermissions = missingCastPermissions(context, requiredPermissions)
        if (missingPermissions.isEmpty()) {
            playerViewModel.refreshCastRoutes()
            playerViewModel.refreshLocalConnectionInfo(refreshBluetoothDevices = true)
        }
    }

    LaunchedEffect(Unit) {
        missingPermissions = missingCastPermissions(context, requiredPermissions)
        if (missingPermissions.isEmpty()) {
            playerViewModel.refreshCastRoutes()
            playerViewModel.refreshLocalConnectionInfo(refreshBluetoothDevices = true)
        }
    }

    val activeRoute = selectedRoute?.takeUnless { it.isDefault }
    val isRemoteSession = (isRemotePlaybackActive || isCastConnecting) && activeRoute != null

    val availableRoutes = if (isWifiEnabled) {
        routes.filterNot { it.isDefault }
    } else {
        emptyList()
    }
    val bluetoothDevices = bluetoothAudioDeviceStates
        .map { state -> state.copy(name = state.name.trim()) }
        .filter { it.name.isNotEmpty() }
        .distinctBy { it.stableId() }
    val activeBluetoothName = bluetoothName
        ?.trim()
        ?.takeIf { activeName ->
            activeName.isNotEmpty() && bluetoothDevices.any { it.name.equals(activeName, ignoreCase = true) }
        }
        ?: bluetoothDevices.firstOrNull { it.isConnected }?.name

    val devices = buildList {
        if (isWifiEnabled) {
            addAll(
                availableRoutes.map { route ->
                    val isRouteActive = activeRoute?.id == route.id
                    val normalizedConnectionState = when {
                        isRouteActive && isCastConnecting ->
                            MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING
                        isRouteActive && isRemoteSession ->
                            MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED
                        route.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED ->
                            MediaRouter.RouteInfo.CONNECTION_STATE_DISCONNECTED
                        else -> route.connectionState
                    }

                    CastDeviceUi(
                        id = route.id,
                        name = route.name,
                        deviceType = route.deviceType,
                        playbackType = route.playbackType,
                        connectionState = normalizedConnectionState,
                        volumeHandling = route.volumeHandling,
                        volume = route.volume,
                        volumeMax = route.volumeMax,
                        isSelected = isRouteActive
                    )
                }
            )
        }

        if (isBluetoothEnabled) {
            bluetoothDevices.forEach { bluetoothDevice ->
                val isConnected = bluetoothDevice.isConnected ||
                    bluetoothDevice.name.equals(activeBluetoothName, ignoreCase = true)
                val isConnectingBt = !isConnected &&
                    bluetoothDevice.address != null &&
                    bluetoothDevice.address.equals(connectingBluetoothAddress, ignoreCase = true)
                add(
                    CastDeviceUi(
                        id = "bluetooth_${bluetoothDevice.stableId()}",
                        name = bluetoothDevice.name,
                        deviceType = MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH_A2DP,
                        playbackType = MediaRouter.RouteInfo.PLAYBACK_TYPE_LOCAL,
                        connectionState = when {
                            isConnected -> MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED
                            isConnectingBt -> MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING
                            else -> MediaRouter.RouteInfo.CONNECTION_STATE_DISCONNECTED
                        },
                        volumeHandling = MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE,
                        volume = if (isConnected) (localDeviceVolume * 100).roundToInt() else 0,
                        volumeMax = 100,
                        isSelected = isConnected && !isRemoteSession,
                        batteryPercent = bluetoothDevice.batteryPercent,
                        isBluetooth = true,
                        bluetoothAddress = bluetoothDevice.address
                    )
                )
            }
        }
    }

    val activeDevice = if (isRemoteSession) {
        val remoteRoute = checkNotNull(activeRoute)
        ActiveDeviceUi(
            id = remoteRoute.id,
            title = remoteRoute.name,
            subtitle = stringResource(R.string.cast_subtitle_session),
            isRemote = true,
            icon = when (remoteRoute.deviceType) {
                MediaRouter.RouteInfo.DEVICE_TYPE_TV -> Icons.Rounded.Tv
                MediaRouter.RouteInfo.DEVICE_TYPE_REMOTE_SPEAKER, MediaRouter.RouteInfo.DEVICE_TYPE_BUILTIN_SPEAKER -> Icons.Rounded.Speaker
                MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH_A2DP -> Icons.Rounded.Bluetooth
                else -> Icons.Filled.Cast
            },
            isConnecting = isCastConnecting,
            volume = routeVolume.toFloat().coerceAtLeast(0f),
            volumeRange = 0f..remoteRoute.volumeMax.toFloat().coerceAtLeast(1f),
            connectionLabel = if (isCastConnecting) stringResource(R.string.cast_connecting) else stringResource(R.string.cast_connected)
        )
    } else {
        val isBluetoothAudio = isBluetoothEnabled && !activeBluetoothName.isNullOrEmpty()
        val activeBluetoothDevice = if (isBluetoothAudio) {
            bluetoothDevices.firstOrNull {
                it.isConnected || it.name.equals(activeBluetoothName, ignoreCase = true)
            }
        } else {
            null
        }
        ActiveDeviceUi(
            id = "phone",
            title = if (isBluetoothAudio) activeBluetoothName else stringResource(R.string.cast_this_phone),
            subtitle = if (isBluetoothAudio) stringResource(R.string.cast_bluetooth_audio) else stringResource(R.string.cast_local_playback),
            isRemote = false,
            icon = if (isBluetoothAudio) Icons.Rounded.Bluetooth else Icons.Rounded.Headphones,
            isConnecting = false,
            volume = localDeviceVolume,
            volumeRange = 0f..1f,
            connectionLabel = if (isPlaying) stringResource(R.string.cast_playing) else stringResource(R.string.cast_paused),
            isBluetooth = isBluetoothAudio,
            batteryPercent = activeBluetoothDevice?.batteryPercent
        )
    }

    val uiState = CastSheetUiState(
        wifiRadioOn = isWifiRadioOn,
        wifiEnabled = isWifiEnabled,
        wifiSsid = wifiName,
        isScanning = isRefreshing && devices.isEmpty() && (isWifiEnabled || isBluetoothEnabled),
        isRefreshing = isRefreshing,
        devices = devices,
        activeDevice = activeDevice,
        isBluetoothEnabled = isBluetoothEnabled,
        bluetoothName = activeBluetoothName
    )

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )

    DisposableEffect(Unit) {
        onExpansionChanged(1f)
        onDispose { onExpansionChanged(0f) }
    }

    var showCastSecurityDialog by remember { mutableStateOf(false) }
    var onConfirmCastSecurityScan by remember { mutableStateOf<() -> Unit>({}) }

    val sheetBody: @Composable () -> Unit = {
        CompositionLocalProvider(
            LocalOverscrollFactory provides null
        ) {
            Box(
                modifier = Modifier
                    .padding(bottom = if (isContainedInCard) 6.dp else 18.dp)
            ) {
                if (missingPermissions.isNotEmpty()) {
                    CastPermissionStep(
                        missingPermissions = missingPermissions,
                        onRequestPermissions = {
                            permissionLauncher.launch(missingPermissions.toTypedArray())
                        },
                        isContainedInCard = isContainedInCard
                    )
                } else {
                    CastSheetContent(
                        state = uiState,
                        onSelectDevice = { id ->
                            when {
                                id.startsWith("bluetooth_") -> {
                                    val targetDevice = devices.firstOrNull { it.id == id }
                                    val isAlreadyConnected =
                                        targetDevice?.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED
                                    val isConnecting =
                                        targetDevice?.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING
                                    when {
                                        isAlreadyConnected && isRemoteSession -> {
                                            playerViewModel.disconnect()
                                        }
                                        isAlreadyConnected || isConnecting -> Unit
                                        targetDevice?.bluetoothAddress != null -> {
                                            val started = playerViewModel.connectToBluetoothDevice(targetDevice.bluetoothAddress)
                                            if (started) {
                                                playerViewModel.refreshLocalConnectionInfo(refreshBluetoothDevices = false)
                                            } else {
                                                launchBluetoothEnableOrSettings(context, isBluetoothEnabled)
                                            }
                                        }
                                        else -> launchBluetoothEnableOrSettings(context, isBluetoothEnabled)
                                    }
                                }
                                else -> routes.firstOrNull { it.id == id }?.let { playerViewModel.selectRoute(it) }
                            }
                        },
                        onDisconnect = {
                            playerViewModel.disconnect()
                            onDismiss()
                        },
                        onVolumeChange = { value ->
                            if (uiState.activeDevice.isRemote) {
                                playerViewModel.setRouteVolume(value.roundToInt())
                            } else {
                                playerViewModel.setLocalDeviceVolume(value)
                            }
                        },
                        onTurnOnWifi = {
                            launchWifiPanelOrSettings(context)
                        },
                        onOpenBluetoothSettings = {
                            launchBluetoothEnableOrSettings(context, isBluetoothEnabled)
                        },
                        onRefresh = {
                            playerViewModel.refreshCastRoutes()
                            playerViewModel.refreshLocalConnectionInfo(refreshBluetoothDevices = true)
                        },
                        startWithControls = isRemoteSession,
                        isContainedInCard = isContainedInCard,
                        onClose = if (isContainedInCard) onDismiss else null,
                        onRequestSecurityWarning = { onConfirmScan ->
                            onConfirmCastSecurityScan = onConfirmScan
                            showCastSecurityDialog = true
                        }
                    )
                }
            }
        }
    }

    if (isContainedInCard) {
        Box(modifier = Modifier.fillMaxSize()) {
            PlayerCardOverlaySheet(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 12.dp,
                maxHeightFraction = 0.90f
            ) {
                sheetBody()
            }

            if (showCastSecurityDialog) {
                CastSecurityWarningDialog(
                    wifiEnabled = uiState.wifiEnabled,
                    wifiSsid = uiState.wifiSsid,
                    onConfirmScan = {
                        showCastSecurityDialog = false
                        onConfirmCastSecurityScan()
                    },
                    onOpenWifiSettings = {
                        showCastSecurityDialog = false
                        launchWifiPanelOrSettings(context)
                    },
                    onDismiss = {
                        showCastSecurityDialog = false
                    },
                    isContainedInCard = true
                )
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 12.dp
        ) {
            sheetBody()
        }

        if (showCastSecurityDialog) {
            CastSecurityWarningDialog(
                wifiEnabled = uiState.wifiEnabled,
                wifiSsid = uiState.wifiSsid,
                onConfirmScan = {
                    showCastSecurityDialog = false
                    onConfirmCastSecurityScan()
                },
                onOpenWifiSettings = {
                    showCastSecurityDialog = false
                    launchWifiPanelOrSettings(context)
                },
                onDismiss = {
                    showCastSecurityDialog = false
                },
                isContainedInCard = false
            )
        }
    }
}

private data class CastDeviceUi(
    val id: String,
    val name: String,
    val deviceType: Int,
    val playbackType: Int,
    val connectionState: Int,
    val volumeHandling: Int,
    val volume: Int,
    val volumeMax: Int,
    val isSelected: Boolean,
    val batteryPercent: Int? = null,
    val isBluetooth: Boolean = false,
    val bluetoothAddress: String? = null
)

private data class ActiveDeviceUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val isRemote: Boolean,
    val icon: ImageVector,
    val isConnecting: Boolean,
    val volume: Float,
    val volumeRange: ClosedFloatingPointRange<Float>,
    val connectionLabel: String,
    val isBluetooth: Boolean = false,
    val batteryPercent: Int? = null
)

private data class CastSheetUiState(
    val wifiRadioOn: Boolean,
    val wifiEnabled: Boolean,
    val wifiSsid: String? = null,
    val isScanning: Boolean,
    val isRefreshing: Boolean,
    val devices: List<CastDeviceUi>,
    val activeDevice: ActiveDeviceUi,
    val isBluetoothEnabled: Boolean,
    val bluetoothName: String? = null
)

private fun BluetoothAudioDeviceState.stableId(): String {
    return address ?: name.lowercase()
}

@Composable
private fun CastPermissionStep(
    missingPermissions: List<String>,
    onRequestPermissions: () -> Unit,
    isContainedInCard: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isContainedInCard) Modifier.verticalScroll(rememberScrollState()) else Modifier.navigationBarsPadding())
            .padding(
                horizontal = if (isContainedInCard) 16.dp else 20.dp,
                vertical = if (isContainedInCard) 18.dp else 24.dp
            ),
        verticalArrangement = Arrangement.spacedBy(if (isContainedInCard) 14.dp else 18.dp)
    ) {
        Text(
            text = stringResource(R.string.cast_perm_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = stringResource(R.string.cast_perm_body),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )

        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PermissionHighlight(
                    icon = Icons.Rounded.Bluetooth,
                    title = stringResource(R.string.cast_perm_nearby_title),
                    description = stringResource(R.string.cast_perm_nearby_desc)
                )
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                    PermissionHighlight(
                        icon = Icons.Rounded.Wifi,
                        title = stringResource(R.string.cast_perm_location_title),
                        description = stringResource(R.string.cast_perm_location_desc)
                    )
                }
            }
        }

        Button(
            onClick = onRequestPermissions,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50)
        ) {
            Text(text = stringResource(R.string.cast_allow_access), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        if (missingPermissions.isNotEmpty()) {
            Text(
                text = stringResource(R.string.cast_perm_footer),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionHighlight(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun missingCastPermissions(context: Context, permissions: List<String>): List<String> {
    return permissions.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }
}

private fun Context.findActivity(): android.app.Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun launchWifiPanelOrSettings(context: Context) {
    val activity = context.findActivity()
    val panelIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Intent(Settings.Panel.ACTION_WIFI)
    } else {
        Intent(Settings.ACTION_WIFI_SETTINGS)
    }
    runCatching {
        if (activity != null) {
            activity.startActivity(panelIntent)
        } else {
            panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(panelIntent)
        }
    }.onFailure {
        val fallback = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { (activity ?: context).startActivity(fallback) }
    }
}

private fun launchBluetoothEnableOrSettings(
    context: Context,
    isBluetoothEnabled: Boolean
) {
    val activity = context.findActivity()
    if (!isBluetoothEnabled) {
        val enableIntent = Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
            if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { (activity ?: context).startActivity(enableIntent) }.isSuccess) {
            return
        }
    }
    // Try System Media Output Slice / Dialog on Android 11+ if Bluetooth is already ON, else Bluetooth Settings
    val btIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
        if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { (activity ?: context).startActivity(btIntent) }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CastSheetContent(
    state: CastSheetUiState,
    onSelectDevice: (String) -> Unit,
    onDisconnect: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onTurnOnWifi: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRefresh: () -> Unit,
    startWithControls: Boolean = true,
    isContainedInCard: Boolean = false,
    onClose: (() -> Unit)? = null,
    onRequestSecurityWarning: ((onConfirmScan: () -> Unit) -> Unit)? = null
) {
    val allConnectivityOff = !state.wifiEnabled && !state.isBluetoothEnabled
    val configuration = LocalConfiguration.current
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val fullscreenMaxPagerHeight = (
        configuration.screenHeightDp.dp -
            safeInsets.calculateTopPadding() -
            safeInsets.calculateBottomPadding() -
            212.dp
        ).coerceAtLeast(280.dp)
    val pagerState = rememberPagerState(
        initialPage = if (startWithControls) 0 else 1,
        pageCount = { 2 }
    )
    val scope = rememberCoroutineScope()
    val motionScheme = MaterialTheme.motionScheme
    var heightAnimationEnabled by remember { mutableStateOf(false) }
    var showCastSecurityDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        heightAnimationEnabled = true
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val maxPagerHeight = if (isContainedInCard) {
            if (constraints.hasBoundedHeight) {
                (maxHeight - 156.dp).coerceIn(200.dp, 520.dp)
            } else {
                440.dp
            }
        } else {
            fullscreenMaxPagerHeight
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isContainedInCard) 16.dp else 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = CircleShape
                        )
                ) {
                    Text(
                        modifier = Modifier.padding(start = 4.dp, end = 8.dp),
                        text = stringResource(R.string.cast_title_connect),
                        style = if (isContainedInCard) {
                            MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                        } else {
                            MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnimatedVisibility(
                        visible = state.isScanning,
                        enter = fadeIn(animationSpec = motionScheme.fastEffectsSpec()),
                        exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()),
                        label = "tabScanningIndicator"
                    ) {
                        BadgeChip(
                            text = stringResource(R.string.cast_scanning_nearby),
                            iconVector = Icons.Filled.Refresh,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (isContainedInCard && onClose != null) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable(onClick = onClose),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.player_panel_close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxPagerHeight)
                    .animateContentSize(
                        animationSpec = if (heightAnimationEnabled) {
                            motionScheme.defaultSpatialSpec()
                        } else {
                            snap()
                        },
                        alignment = Alignment.TopCenter
                    )
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .wrapContentHeight()
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) { page ->
                    when (page) {
                        0 -> CastControlsTabContent(
                            state = state,
                            allConnectivityOff = allConnectivityOff,
                            onDisconnect = onDisconnect,
                            onVolumeChange = onVolumeChange,
                            onCastClick = {
                                val confirmScan = {
                                    onRefresh()
                                    scope.launch {
                                        pagerState.animateScrollToPage(1)
                                    }
                                    Unit
                                }
                                if (onRequestSecurityWarning != null) {
                                    onRequestSecurityWarning(confirmScan)
                                } else {
                                    showCastSecurityDialog = true
                                }
                            },
                            onOpenBluetoothSettings = {
                                if (state.isBluetoothEnabled) {
                                    onRefresh()
                                    scope.launch { pagerState.animateScrollToPage(1) }
                                } else {
                                    onOpenBluetoothSettings()
                                }
                            },
                            onRefresh = onRefresh,
                            bottomSpacing = if (isContainedInCard) 8.dp else 20.dp,
                            isContainedInCard = isContainedInCard
                        )
                        1 -> CastDevicesTabContent(
                            state = state,
                            allConnectivityOff = allConnectivityOff,
                            onSelectDevice = onSelectDevice,
                            onDisconnect = onDisconnect,
                            onTurnOnWifi = onTurnOnWifi,
                            onOpenBluetoothSettings = onOpenBluetoothSettings,
                            onRefresh = onRefresh,
                            maxContentHeight = maxPagerHeight,
                            isContainedInCard = isContainedInCard
                        )
                    }
                }
            }

            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isContainedInCard) Modifier else Modifier.navigationBarsPadding())
                    .padding(
                        horizontal = if (isContainedInCard) 14.dp else 16.dp,
                        vertical = 8.dp
                    )
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(5.dp),
                containerColor = Color.Transparent,
                divider = {},
                indicator = {}
            ) {
                val controlsLabel = stringResource(R.string.cast_tab_controls)
                TabAnimation(
                    index = 0,
                    title = controlsLabel,
                    selectedIndex = pagerState.currentPage,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(0)
                        }
                    },
                    transformOrigin = TransformOrigin(0f, 0.5f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Speaker,
                            contentDescription = controlsLabel,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            controlsLabel.uppercase(),
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                val devicesLabel = stringResource(R.string.cast_tab_devices)
                TabAnimation(
                    index = 1,
                    title = devicesLabel,
                    selectedIndex = pagerState.currentPage,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(1)
                        }
                    },
                    transformOrigin = TransformOrigin(1f, 0.5f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Devices,
                            contentDescription = devicesLabel,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            devicesLabel.uppercase(),
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (showCastSecurityDialog && onRequestSecurityWarning == null) {
                CastSecurityWarningDialog(
                    wifiEnabled = state.wifiEnabled,
                    wifiSsid = state.wifiSsid,
                    onConfirmScan = {
                        showCastSecurityDialog = false
                        onRefresh()
                        scope.launch {
                            pagerState.animateScrollToPage(1)
                        }
                    },
                    onOpenWifiSettings = {
                        showCastSecurityDialog = false
                        onTurnOnWifi()
                    },
                    onDismiss = {
                        showCastSecurityDialog = false
                    },
                    isContainedInCard = isContainedInCard
                )
            }
        }
    }
}

@Composable
private fun CastControlsTabContent(
    state: CastSheetUiState,
    allConnectivityOff: Boolean,
    onDisconnect: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onCastClick: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRefresh: () -> Unit,
    bottomSpacing: Dp,
    isContainedInCard: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isContainedInCard) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = if (isContainedInCard) 16.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(if (isContainedInCard) 12.dp else 16.dp),
    ) {
        ActiveDeviceHero(
            device = state.activeDevice,
            onDisconnect = onDisconnect,
            onVolumeChange = onVolumeChange,
            isContainedInCard = isContainedInCard
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.cast_connectivity),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (allConnectivityOff) {
                            stringResource(R.string.cast_turn_on_wifi_bt)
                        } else {
                            stringResource(R.string.cast_manage_radios_rescan)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cast_cd_refresh_connections))
                }
            }
            QuickSettingsRow(
                isCasting = state.activeDevice.isRemote,
                activeCastDeviceName = state.activeDevice.title.takeIf { state.activeDevice.isRemote },
                wifiConnected = state.wifiEnabled,
                onCastClick = onCastClick,
                bluetoothEnabled = state.isBluetoothEnabled,
                bluetoothName = state.bluetoothName,
                onBluetoothClick = onOpenBluetoothSettings,
                isContainedInCard = isContainedInCard
            )
        }
        Spacer(modifier = Modifier.height(bottomSpacing))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CastDevicesTabContent(
    state: CastSheetUiState,
    allConnectivityOff: Boolean,
    onSelectDevice: (String) -> Unit,
    onDisconnect: () -> Unit,
    onTurnOnWifi: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRefresh: () -> Unit,
    maxContentHeight: Dp,
    isContainedInCard: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxContentHeight)
            .padding(horizontal = if (isContainedInCard) 16.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(if (isContainedInCard) 12.dp else 16.dp),
        contentPadding = PaddingValues(bottom = if (isContainedInCard) 12.dp else 20.dp)
    ) {
        item(key = "deviceSectionHeader") {
            DeviceSectionHeader(
                modifier = Modifier.fillMaxWidth(),
                hasDevices = state.devices.isNotEmpty(),
                isRefreshing = state.isRefreshing && !allConnectivityOff,
                onRefresh = onRefresh
            )
        }

        if (!allConnectivityOff) {
            item(key = "refreshIndicator") {
                AnimatedVisibility(
                    visible = state.isRefreshing && state.devices.isNotEmpty(),
                    enter = fadeIn(animationSpec = motionScheme.fastEffectsSpec()),
                    exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()),
                    label = "refreshIndicator"
                ) {
                    LinearWavyProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp)),
                        color = colors.primary,
                        trackColor = colors.primary.copy(alpha = 0.12f)
                    )
                }
            }
        }

        if (state.devices.isEmpty()) {
            item(key = "emptyOrScanningDevices") {
                EmptyDeviceState(
                    isScanning = state.isRefreshing && !allConnectivityOff,
                    allConnectivityOff = allConnectivityOff,
                    onTurnOnWifi = onTurnOnWifi,
                    onOpenBluetoothSettings = onOpenBluetoothSettings
                )
            }
        } else {
            items(state.devices, key = { it.id }) { device ->
                CastDeviceRow(
                    device = device,
                    onSelect = { onSelectDevice(device.id) },
                    onDisconnect = onDisconnect
                )
            }
        }
    }
}

@Composable
private fun DeviceSectionHeader(
    modifier: Modifier = Modifier,
    hasDevices: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    val colors = LocalMaterialTheme.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.padding(start = 4.dp, end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.cast_nearby_devices),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = when {
                    hasDevices -> stringResource(R.string.cast_tap_to_connect)
                    isRefreshing -> stringResource(R.string.cast_searching)
                    else -> stringResource(R.string.cast_no_devices_yet)
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }

        IconButton(
            onClick = onRefresh,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = colors.surfaceContainerHigh,
                contentColor = colors.onSurface
            ),
            modifier = Modifier.clip(RoundedCornerShape(16.dp))
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cast_cd_refresh_devices))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActiveDeviceHero(
    device: ActiveDeviceUi,
    onDisconnect: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    isContainedInCard: Boolean = false
) {
    var sliderValue by remember(device.id) { mutableFloatStateOf(device.volume) }
    var isDraggingSlider by remember(device.id) { mutableStateOf(false) }
    var justFinishedDrag by remember(device.id) { mutableStateOf(false) }
    LaunchedEffect(device.volume, isDraggingSlider) {
        if (!isDraggingSlider) {
            if (justFinishedDrag) {
                delay(300)
                justFinishedDrag = false
            }
            sliderValue = device.volume
        }
    }
    val haptics = LocalHapticFeedback.current

    var lastStep by remember(device.id) { mutableIntStateOf(-1) }

    val heroShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 42.dp,
            smoothnessAsPercentTL = 60,
            cornerRadiusTR = 20.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusBL = 20.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBR = 42.dp,
            smoothnessAsPercentBR = 60
        )
    }

    val heroContainerColor = if (device.isRemote) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val heroContentColor = if (device.isRemote) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Card(
        shape = heroShape,
        colors = CardDefaults.cardColors(
            containerColor = heroContainerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isContainedInCard) 16.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (isContainedInCard) 12.dp else 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(if (isContainedInCard) 52.dp else 62.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                color = heroContentColor.copy(
                                    alpha = if (device.isConnecting) 0.18f else 0.12f
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (device.isConnecting) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(34.dp),
                                color = heroContentColor,
                                trackColor = heroContentColor.copy(alpha = 0.2f)
                            )
                        } else {
                            Icon(
                                imageVector = device.icon,
                                contentDescription = null,
                                tint = heroContentColor
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = device.title,
                        style = if (isContainedInCard) {
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        } else {
                            MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        },
                        color = heroContentColor,
                        maxLines = 2
                    )

                    val statusText = buildString {
                        append(device.subtitle)
                        append(" • ")
                        append(device.connectionLabel)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (device.isConnecting && device.isRemote) stringResource(R.string.cast_connecting_ellipsis) else statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = heroContentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (device.batteryPercent != null) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(heroContentColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.BatteryFull,
                                    contentDescription = stringResource(R.string.cast_cd_battery),
                                    tint = heroContentColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = stringResource(R.string.common_percentage_text, device.batteryPercent),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = heroContentColor,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    if (device.isRemote) {
                        Button(
                            onClick = onDisconnect,
                            enabled = !device.isConnecting,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .height(46.dp)
                        ) {
                            Icon(
                                modifier = Modifier.size(22.dp),
                                painter = painterResource(R.drawable.rounded_mimo_disconnect_24),
                                contentDescription = stringResource(R.string.cast_disconnect),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.cast_disconnect))
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (device.isRemote || device.isBluetooth) {
                            stringResource(R.string.cast_volume_device)
                        } else {
                            stringResource(R.string.cast_volume_phone)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = heroContentColor
                    )
                    Text(
                        text = buildVolumeLabel(sliderValue, device.volumeRange.endInclusive),
                        style = MaterialTheme.typography.labelMedium,
                        color = heroContentColor
                    )
                }
                val interactionSource = remember { MutableInteractionSource() }
                Slider(
                    value = sliderValue.coerceIn(device.volumeRange.start, device.volumeRange.endInclusive),
                    onValueChange = { newValue ->
                        isDraggingSlider = true
                        sliderValue = newValue
                        val quantized = if (device.volumeRange.endInclusive <= 1f) {
                            (newValue * 20).toInt()
                        } else {
                            newValue.toInt()
                        }
                        if (quantized != lastStep) {
                            lastStep = quantized
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onVolumeChange(newValue)
                    },
                    onValueChangeFinished = {
                        justFinishedDrag = true
                        isDraggingSlider = false
                    },
                    valueRange = device.volumeRange,
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            modifier = Modifier
                                .height(30.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            colors = SliderDefaults.colors(
                                activeTrackColor = heroContentColor,
                                inactiveTrackColor = heroContentColor.copy(alpha = 0.2f),
                                thumbColor = heroContentColor
                            )
                        )
                    },
                    thumb = { sliderState ->
                        SliderDefaults.Thumb(
                            modifier = Modifier.height(36.dp),
                            interactionSource = interactionSource,
                            sliderState = sliderState,
                            colors = SliderDefaults.colors(thumbColor = heroContentColor)
                        )
                    },
                    colors = SliderDefaults.colors(
                        activeTrackColor = heroContentColor,
                        inactiveTrackColor = heroContentColor.copy(alpha = 0.2f),
                        thumbColor = heroContentColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun buildVolumeLabel(value: Float, max: Float): String {
    return if (max <= 1f) {
        "${(value * 100).toInt()}%"
    } else {
        "${value.toInt()} / ${max.toInt()}"
    }
}

private enum class EmptyDeviceStateMode {
    CONNECTIVITY_OFF,
    SCANNING,
    IDLE
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyDeviceState(
    isScanning: Boolean = false,
    allConnectivityOff: Boolean = false,
    onTurnOnWifi: () -> Unit = {},
    onOpenBluetoothSettings: () -> Unit = {}
) {
    val motionScheme = MaterialTheme.motionScheme
    val effectiveScanning = isScanning && !allConnectivityOff
    val mode = when {
        allConnectivityOff -> EmptyDeviceStateMode.CONNECTIVITY_OFF
        effectiveScanning -> EmptyDeviceStateMode.SCANNING
        else -> EmptyDeviceStateMode.IDLE
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 148.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(22.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) togetherWith
                        fadeOut(animationSpec = motionScheme.defaultEffectsSpec())
                },
                contentAlignment = Alignment.Center,
                label = "emptyDeviceScanningTransition"
            ) { currentMode ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (currentMode) {
                        EmptyDeviceStateMode.CONNECTIVITY_OFF -> {
                            Icon(
                                imageVector = Icons.Rounded.WifiOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = stringResource(R.string.cast_turn_on_wifi_bt),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = onTurnOnWifi,
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Wifi,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.cast_wifi),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Button(
                                    onClick = onOpenBluetoothSettings,
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.filledTonalButtonColors()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bluetooth,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.cast_bluetooth),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        EmptyDeviceStateMode.SCANNING -> {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                            Text(
                                text = stringResource(R.string.cast_searching),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.cast_searching_hint),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        EmptyDeviceStateMode.IDLE -> {
                            Icon(
                                imageVector = Icons.Filled.Devices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = stringResource(R.string.cast_no_devices_yet),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.cast_searching_hint),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CastDeviceRow(
    device: CastDeviceUi,
    onSelect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val motionScheme = MaterialTheme.motionScheme
    val (containerColor, onContainer) = when {
        device.isSelected -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        device.isBluetooth -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
    }

    val isActiveDevice = device.isSelected && device.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED
    val scallopShape = remember { RoundedStarShape(sides = 8, curve = 0.10, rotation = 0f) }

    val rotationState = if (isActiveDevice) {
        val infiniteRotation = rememberInfiniteTransition(label = "activeDeviceRotation")
        infiniteRotation.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 9000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "deviceRotation"
        )
    } else {
        null
    }
    val backgroundScaleState = animateFloatAsState(
        targetValue = if (isActiveDevice) 1.16f else 1f,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "activeDeviceScale"
    )
    val itemCornerRadius by animateDpAsState(
        targetValue = if (device.isSelected) 50.dp else 22.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "deviceRowCornerRadius"
    )
    val rowShape = RoundedCornerShape(itemCornerRadius)

    val deviceIcon = when (device.deviceType) {
        MediaRouter.RouteInfo.DEVICE_TYPE_TV -> Icons.Rounded.Tv
        MediaRouter.RouteInfo.DEVICE_TYPE_REMOTE_SPEAKER, MediaRouter.RouteInfo.DEVICE_TYPE_BUILTIN_SPEAKER -> Icons.Rounded.Speaker
        MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH_A2DP -> Icons.Rounded.Bluetooth
        else -> Icons.Filled.Cast
    }

    val isConnecting = device.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .clickable(
                enabled = !isConnecting,
                onClick = when {
                    isConnecting -> ({})
                    device.isBluetooth -> onSelect
                    device.isSelected &&
                        device.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED -> onDisconnect
                    else -> onSelect
                }
            ),
        shape = rowShape,
        color = containerColor,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Đặt padding(start = 4.dp) TRƯỚC size(52.dp) để khung chứa luôn vuông 52x52dp, không làm méo ngôi sao khi xoay
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(52.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            val scale = backgroundScaleState.value
                            rotationZ = rotationState?.value ?: 0f
                            scaleX = scale
                            scaleY = scale
                        }
                        .background(
                            color = onContainer.copy(alpha = 0.12f),
                            shape = if (isActiveDevice) scallopShape else CircleShape
                        )
                )

                Icon(
                    imageVector = deviceIcon,
                    contentDescription = null,
                    tint = onContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                val statusText = when {
                    device.isBluetooth &&
                        device.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED -> stringResource(R.string.cast_status_connected)
                    device.isBluetooth &&
                        device.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING -> stringResource(R.string.cast_status_connecting)
                    device.isBluetooth -> stringResource(R.string.cast_status_available_connect)
                    device.isSelected && device.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED -> stringResource(R.string.cast_status_connected)
                    device.isSelected && device.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING -> stringResource(R.string.cast_status_connecting)
                    else -> stringResource(R.string.cast_status_available)
                }

                val statusIcon = if (device.isBluetooth) R.drawable.rounded_bluetooth_24 else R.drawable.rounded_wifi_24

                BadgeChip(
                    text = statusText,
                    icon = statusIcon,
                    contentColor = onContainer
                )
            }

            when {
                device.isBluetooth && device.isSelected -> {
                    BluetoothMetricIndicator(
                        device = device,
                        contentColor = onContainer,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                device.volumeHandling == MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE && device.isSelected -> {
                    Text(
                        text = stringResource(R.string.cast_volume_levels, device.volume, device.volumeMax),
                        style = MaterialTheme.typography.labelSmall,
                        color = onContainer,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BluetoothMetricIndicator(
    device: CastDeviceUi,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val batteryPercent = device.batteryPercent
    val value = batteryPercent ?: buildBluetoothVolumePercent(device)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (batteryPercent != null) {
            Icon(
                imageVector = Icons.Rounded.BatteryFull,
                contentDescription = stringResource(R.string.cast_cd_battery),
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.rounded_volume_up_24),
                contentDescription = stringResource(R.string.cast_cd_volume_level),
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
        }

        Text(
            text = stringResource(R.string.common_percentage_text, value),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}

private fun buildBluetoothVolumePercent(device: CastDeviceUi): Int {
    return when {
        device.volumeMax > 0 -> ((device.volume.toFloat() / device.volumeMax) * 100).roundToInt()
        else -> device.volume
    }.coerceIn(0, 100)
}

@Composable
private fun BadgeChip(
    text: String,
    icon: Int? = null,
    iconVector: ImageVector? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(contentColor.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when {
            icon != null -> Icon(painterResource(id = icon), contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
            iconVector != null -> Icon(iconVector, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
        }
        Text(
            text = text,
            maxLines = 1,
            style = MaterialTheme.typography.labelMedium,
            overflow = TextOverflow.Ellipsis,
            color = contentColor
        )
    }
}

@Composable
private fun QuickSettingsRow(
    isCasting: Boolean,
    activeCastDeviceName: String?,
    wifiConnected: Boolean,
    onCastClick: () -> Unit,
    bluetoothEnabled: Boolean,
    bluetoothName: String?,
    onBluetoothClick: () -> Unit,
    isContainedInCard: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (isContainedInCard) 8.dp else 12.dp)
    ) {
        QuickSettingTile(
            label = if (isCasting && !activeCastDeviceName.isNullOrEmpty()) {
                activeCastDeviceName
            } else {
                stringResource(R.string.cast_tile_title)
            },
            subtitle = when {
                isCasting -> stringResource(R.string.cast_connected)
                wifiConnected -> stringResource(R.string.cast_tile_subtitle_ready)
                else -> stringResource(R.string.cast_tile_subtitle_wifi_needed)
            },
            icon = Icons.Filled.Cast,
            isActive = isCasting || wifiConnected,
            onClick = onCastClick,
            modifier = Modifier.weight(1f),
            isContainedInCard = isContainedInCard
        )
        QuickSettingTile(
            label = if (bluetoothEnabled && !bluetoothName.isNullOrEmpty()) {
                bluetoothName
            } else {
                stringResource(R.string.cast_bluetooth)
            },
            subtitle = if (bluetoothEnabled) {
                if (!bluetoothName.isNullOrEmpty()) {
                    stringResource(R.string.cast_bt_connected)
                } else {
                    stringResource(R.string.cast_bt_on)
                }
            } else {
                stringResource(R.string.cast_bt_off)
            },
            icon = if (bluetoothEnabled) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothDisabled,
            isActive = bluetoothEnabled,
            onClick = onBluetoothClick,
            modifier = Modifier.weight(1f),
            isContainedInCard = isContainedInCard
        )
    }
}

@Composable
private fun CastSecurityWarningDialog(
    wifiEnabled: Boolean,
    wifiSsid: String?,
    onConfirmScan: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onDismiss: () -> Unit,
    isContainedInCard: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    if (isContainedInCard) {
        PlayerCardOverlayDialog(
            onDismissRequest = onDismiss,
            containerColor = colors.surfaceContainerHigh
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Cast,
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.cast_security_dialog_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.cast_security_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                if (!wifiEnabled) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.errorContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WifiOff,
                                contentDescription = null,
                                tint = colors.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.cast_security_dialog_wifi_off_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onErrorContainer
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = stringResource(R.string.cast_security_dialog_dismiss),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = if (wifiEnabled) onConfirmScan else onOpenWifiSettings,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (wifiEnabled) {
                            stringResource(R.string.cast_security_dialog_confirm)
                        } else {
                            stringResource(R.string.cast_security_dialog_open_wifi)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    } else {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(28.dp),
            containerColor = colors.surfaceContainerHigh,
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Cast,
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.cast_security_dialog_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.cast_security_dialog_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                    if (!wifiEnabled) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colors.errorContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WifiOff,
                                    contentDescription = null,
                                    tint = colors.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = stringResource(R.string.cast_security_dialog_wifi_off_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onErrorContainer
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = if (wifiEnabled) onConfirmScan else onOpenWifiSettings,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (wifiEnabled) {
                            stringResource(R.string.cast_security_dialog_confirm)
                        } else {
                            stringResource(R.string.cast_security_dialog_open_wifi)
                        }
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(text = stringResource(R.string.cast_security_dialog_dismiss))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuickSettingTile(
    label: String,
    subtitle: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isContainedInCard: Boolean = false
) {
    val motionScheme = MaterialTheme.motionScheme
    val cornerRadius by animateDpAsState(
        targetValue = if (isActive) 18.dp else 36.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "tileCornerRadius"
    )
    val tileShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = cornerRadius,
        smoothnessAsPercentTL = 60,
        cornerRadiusTR = cornerRadius,
        smoothnessAsPercentTR = 60,
        cornerRadiusBL = cornerRadius,
        smoothnessAsPercentBL = 60,
        cornerRadiusBR = cornerRadius,
        smoothnessAsPercentBR = 60
    )

    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = MaterialTheme.colorScheme.onSurface

    val iconBoxColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.1f),
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "iconBoxColor"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.onPrimary else contentColor,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "iconTint"
    )

    Surface(
        modifier = modifier
            .height(if (isContainedInCard) 64.dp else 72.dp)
            .clip(tileShape)
            .clickable(onClick = onClick),
        shape = tileShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (isContainedInCard) 12.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isContainedInCard) 10.dp else 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (isContainedInCard) 36.dp else 40.dp)
                    .clip(CircleShape)
                    .background(iconBoxColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (isContainedInCard) 18.dp else 20.dp),
                    tint = iconTint
                )
            }

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}


@Composable
@Preview(showBackground = true)
private fun CastSheetScanningPreview() {
    val state = CastSheetUiState(
        wifiRadioOn = true,
        wifiEnabled = true,
        wifiSsid = "Home Wi-Fi",
        isScanning = true,
        isRefreshing = true,
        devices = emptyList(),
        activeDevice = ActiveDeviceUi(
            id = "phone",
            title = "This phone",
            subtitle = "Local playback",
            isRemote = false,
            icon = Icons.Rounded.Headphones,
            isConnecting = false,
            volume = 0.4f,
            volumeRange = 0f..1f,
            connectionLabel = "Playing"
        ),
        isBluetoothEnabled = true,
        bluetoothName = "Headphones"
    )
    CastSheetContent(
        state = state,
        onSelectDevice = {},
        onDisconnect = {},
        onVolumeChange = {},
        onTurnOnWifi = {},
        onOpenBluetoothSettings = {},
        onRefresh = {}
    )
}

@Composable
@Preview(showBackground = true)
private fun CastSheetDevicesPreview() {
    val devices = listOf(
        CastDeviceUi(
            id = "1",
            name = "Living room TV",
            deviceType = MediaRouter.RouteInfo.DEVICE_TYPE_TV,
            playbackType = MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE,
            connectionState = MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING,
            volumeHandling = MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE,
            volume = 8,
            volumeMax = 15,
            isSelected = false
        ),
        CastDeviceUi(
            id = "2",
            name = "Pixel Buds Pro",
            deviceType = MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH_A2DP,
            playbackType = MediaRouter.RouteInfo.PLAYBACK_TYPE_LOCAL,
            connectionState = MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED,
            volumeHandling = MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE,
            volume = 12,
            volumeMax = 25,
            isSelected = true
        )
    )
    val state = CastSheetUiState(
        wifiRadioOn = true,
        wifiEnabled = true,
        wifiSsid = "Office 5G",
        isScanning = false,
        isRefreshing = false,
        devices = devices,
        activeDevice = ActiveDeviceUi(
            id = "2",
            title = "Pixel Buds Pro",
            subtitle = "Connected via Bluetooth",
            isRemote = true,
            icon = Icons.Rounded.Bluetooth,
            isConnecting = false,
            volume = 12f,
            volumeRange = 0f..25f,
            connectionLabel = "Connected"
        ),
        isBluetoothEnabled = true,
        bluetoothName = "Pixel Buds Pro"
    )
    CastSheetContent(
        state = state,
        onSelectDevice = {},
        onDisconnect = {},
        onVolumeChange = {},
        onTurnOnWifi = {},
        onOpenBluetoothSettings = {},
        onRefresh = {}
    )
}

@Composable
@Preview(showBackground = true)
private fun CastSheetWifiOffPreview() {
    val state = CastSheetUiState(
        wifiRadioOn = false,
        wifiEnabled = false,
        isScanning = false,
        isRefreshing = false,
        devices = emptyList(),
        activeDevice = ActiveDeviceUi(
            id = "phone",
            title = "This phone",
            subtitle = "Local playback",
            isRemote = false,
            icon = Icons.Rounded.Headphones,
            isConnecting = false,
            volume = 0.5f,
            volumeRange = 0f..1f,
            connectionLabel = "Paused"
        ),
        isBluetoothEnabled = false
    )
    CastSheetContent(
        state = state,
        onSelectDevice = {},
        onDisconnect = {},
        onVolumeChange = {},
        onTurnOnWifi = {},
        onOpenBluetoothSettings = {},
        onRefresh = {}
    )
}
