# KẾ HOẠCH SỬA LỖI CONNECT DEVICE (CAST & BLUETOOTH AUDIO OUTPUT) THEO CHUẨN M3 EXPRESSIVE

Bạn là một Senior Android Jetpack Compose Engineer chuyên về **Material 3 Expressive (`androidx.compose.material3:material3:1.5.0-alpha22`)**.
Hãy thực hiện chính xác **8 bước sửa lỗi Logic, UI/UX và chuẩn hóa Material 3 Expressive** cho tính năng **Connect Device (`CastBottomSheet`, `CastSheetState`, `UnifiedPlayerOverlaysLayer`, `ConnectivityStateHolder`)** dưới đây.

## NGUYÊN TẮC BẮT BUỘC:
1. Chỉ sửa đúng các đoạn mã được chỉ định trong từng file, giữ nguyên các logic không liên quan.
2. Tuân thủ quy chuẩn Material 3 Expressive (`MotionScheme.expressive()`):
   - Sử dụng `fastSpatialSpec()` / `defaultSpatialSpec()` cho kích thước, vị trí, tỷ lệ (`scale`), độ bo góc (`cornerRadius`).
   - Sử dụng `fastEffectsSpec()` / `defaultEffectsSpec()` (`dampingRatio = 1.0f`) cho màu sắc (`Color`) và độ mờ (`alpha`).
   - Sử dụng `LinearWavyProgressIndicator` và `CircularWavyProgressIndicator` thay cho indicator phẳng của Material 2.
   - Không đặt `.padding(...)` sau `.size(...)` làm méo khung chứa hình học.

---

## BƯỚC 1: Bổ sung các chuỗi đa ngôn ngữ còn thiếu vào `strings_player.xml`
**File:** `app/src/main/res/values/strings_player.xml`

Tìm đoạn (dòng 55–60):
```xml
    <string name="cast_connecting_ellipsis">Connecting...</string>
    <string name="cast_searching">Searching for devices…</string>
    <string name="cast_searching_hint">Make sure your TV or speaker is on and sharing the same Wi‑Fi network.</string>
    <string name="cast_tab_controls">Controls</string>
    <string name="cast_tab_devices">Devices</string>
```

Thay bằng:
```xml
    <string name="cast_connecting_ellipsis">Connecting...</string>
    <string name="cast_searching">Searching for devices…</string>
    <string name="cast_searching_hint">Make sure your TV or speaker is on and sharing the same Wi‑Fi network.</string>
    <string name="cast_tab_controls">Controls</string>
    <string name="cast_tab_devices">Devices</string>
    <string name="cast_manage_radios_rescan">Manage active radios and rescan</string>
    <string name="cast_tap_to_connect">Tap to connect</string>
    <string name="cast_no_devices_yet">No devices yet</string>
```

---

## BƯỚC 2: Sửa lỗi rò rỉ `AudioDeviceCallback` (Memory Leak) trong `ConnectivityStateHolder.kt`
**File:** `app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/ConnectivityStateHolder.kt`

Tìm hàm `onCleared()` (dòng 571–590):
```kotlin
    fun onCleared() {
        networkCallback?.let { 
            runCatching { connectivityManager.unregisterNetworkCallback(it) }
        }
        wifiStateReceiver?.let { 
            runCatching { context.unregisterReceiver(it) }
        }
        bluetoothStateReceiver?.let { 
            runCatching { context.unregisterReceiver(it) }
        }
        if (hasBluetoothScanPermission()) {
```

Thay bằng (bổ sung hủy đăng ký `audioDeviceCallback`):
```kotlin
    fun onCleared() {
        networkCallback?.let { 
            runCatching { connectivityManager.unregisterNetworkCallback(it) }
        }
        wifiStateReceiver?.let { 
            runCatching { context.unregisterReceiver(it) }
        }
        bluetoothStateReceiver?.let { 
            runCatching { context.unregisterReceiver(it) }
        }
        audioDeviceCallback?.let {
            runCatching { audioManager.unregisterAudioDeviceCallback(it) }
        }
        audioDeviceCallback = null
        if (hasBluetoothScanPermission()) {
```

---

## BƯỚC 3: Chuẩn hóa `MotionScheme.expressive()` cho `CastSheetState.kt` và `UnifiedPlayerOverlaysLayer.kt`

### 3A. File: `app/src/main/java/com/theveloper/pixelplay/presentation/components/scoped/CastSheetState.kt`
Thay thế toàn bộ nội dung file bằng:
```kotlin
package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal data class CastSheetState(
    val showCastSheet: Boolean,
    val castSheetOpenFraction: Float,
    val openCastSheet: () -> Unit,
    val dismissCastSheet: () -> Unit,
    val onCastExpansionChanged: (Float) -> Unit
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberCastSheetState(): CastSheetState {
    var showCastSheet by remember { mutableStateOf(false) }
    val motionScheme = MaterialTheme.motionScheme
    val castSheetOpenFraction by animateFloatAsState(
        targetValue = if (showCastSheet) 1f else 0f,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "castSheetOpenFraction"
    )

    val openCastSheet = remember {
        { showCastSheet = true }
    }
    val dismissCastSheet = remember {
        { showCastSheet = false }
    }
    val onCastExpansionChanged = remember {
        { _: Float -> Unit }
    }

    return CastSheetState(
        showCastSheet = showCastSheet,
        castSheetOpenFraction = castSheetOpenFraction,
        openCastSheet = openCastSheet,
        dismissCastSheet = dismissCastSheet,
        onCastExpansionChanged = onCastExpansionChanged
    )
}
```

### 3B. File: `app/src/main/java/com/theveloper/pixelplay/presentation/components/UnifiedPlayerOverlaysLayer.kt`
Tìm hàm `UnifiedPlayerCastLayer` (dòng 476–503):
```kotlin
@OptIn(UnstableApi::class)
@Composable
internal fun UnifiedPlayerCastLayer(
    showCastSheet: Boolean,
    internalIsKeyboardVisible: Boolean,
    albumColorScheme: ColorScheme,
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    onExpansionChanged: (Float) -> Unit
) {
    if (!showCastSheet || internalIsKeyboardVisible) return

    CompositionLocalProvider(
        LocalMaterialTheme provides albumColorScheme
    ) {
        MaterialTheme(
            colorScheme = LocalMaterialTheme.current,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes
        ) {
            CastBottomSheet(
                playerViewModel = playerViewModel,
                onDismiss = onDismiss,
                onExpansionChanged = onExpansionChanged
            )
        }
    }
}
```

Thay bằng (bổ sung `motionScheme = MotionScheme.expressive()`):
```kotlin
@OptIn(UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun UnifiedPlayerCastLayer(
    showCastSheet: Boolean,
    internalIsKeyboardVisible: Boolean,
    albumColorScheme: ColorScheme,
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    onExpansionChanged: (Float) -> Unit
) {
    if (!showCastSheet || internalIsKeyboardVisible) return

    CompositionLocalProvider(
        LocalMaterialTheme provides albumColorScheme
    ) {
        MaterialTheme(
            colorScheme = LocalMaterialTheme.current,
            motionScheme = androidx.compose.material3.MotionScheme.expressive(),
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes
        ) {
            CastBottomSheet(
                playerViewModel = playerViewModel,
                onDismiss = onDismiss,
                onExpansionChanged = onExpansionChanged
            )
        }
    }
}
```

---

## BƯỚC 4: Tự động quét thiết bị Cast khi mở Sheet & Sửa logic kết nối Bluetooth + Quyền trong `CastBottomSheet.kt`
**File:** `app/src/main/java/com/theveloper/pixelplay/presentation/components/CastBottomSheet.kt`

1. Tại phần import (dòng 73–79), thêm import `CircularWavyProgressIndicator` và `LinearWavyProgressIndicator`:
```kotlin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
```

2. Tìm đoạn `permissionLauncher`, `LaunchedEffect(Unit)` và xử lý danh sách `bluetoothDevices` (dòng 201–270):
```kotlin
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        missingPermissions = missingCastPermissions(context, requiredPermissions)
        if (missingPermissions.isEmpty()) {
            playerViewModel.refreshLocalConnectionInfo(refreshBluetoothDevices = true)
        }
    }

    LaunchedEffect(Unit) {
        missingPermissions = missingCastPermissions(context, requiredPermissions)
        if (missingPermissions.isEmpty()) {
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
            activeName.isNotEmpty() && bluetoothDevices.any { it.name == activeName }
        }
```
và dòng 265–270:
```kotlin
        if (isBluetoothEnabled) {
            bluetoothDevices.forEach { bluetoothDevice ->
                val isConnected = bluetoothDevice.name == activeBluetoothName
```

Thay thế bằng (tự động gọi `playerViewModel.refreshCastRoutes()` khi mở Sheet và dùng `bluetoothDevice.isConnected`):
```kotlin
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
```
và tại dòng `bluetoothDevices.forEach`:
```kotlin
        if (isBluetoothEnabled) {
            bluetoothDevices.forEach { bluetoothDevice ->
                val isConnected = bluetoothDevice.isConnected ||
                    bluetoothDevice.name.equals(activeBluetoothName, ignoreCase = true)
```

3. Trong `CastPermissionStep` (dòng 491–501):
```kotlin
                PermissionHighlight(
                    icon = Icons.Rounded.Bluetooth,
                    title = stringResource(R.string.cast_perm_nearby_title),
                    description = stringResource(R.string.cast_perm_nearby_desc)
                )
                PermissionHighlight(
                    icon = Icons.Rounded.Wifi,
                    title = stringResource(R.string.cast_perm_location_title),
                    description = stringResource(R.string.cast_perm_location_desc)
                )
```

Thay bằng (chỉ hiện giải thích quyền Location trên Android 11 trở xuống khi thực sự cần Location):
```kotlin
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
```

---

## BƯỚC 5: Chuẩn hóa M3E Motion, Đa ngôn ngữ & `LinearWavyProgressIndicator` trong `CastSheetContent`, `CastControlsTabContent`, `CastDevicesTabContent` và `DeviceSectionHeader`
**File:** `app/src/main/java/com/theveloper/pixelplay/presentation/components/CastBottomSheet.kt`

Tìm từ đầu hàm `CastSheetContent` đến hết `DeviceSectionHeader` (bao gồm cả việc xóa bỏ 2 hàm dead code `CastSheetContainer` và `CollapsibleCastTopBar` ở giữa, từ dòng 567 đến dòng 1270) và thay bằng:
```kotlin
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
    startWithControls: Boolean = true
) {
    val allConnectivityOff = !state.wifiEnabled && !state.isBluetoothEnabled
    val configuration = LocalConfiguration.current
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val maxPagerHeight = (
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

    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        heightAnimationEnabled = true
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.background(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = CircleShape
                )
            ) {
                Text(
                    modifier = Modifier.padding(start = 6.dp, end = 8.dp),
                    text = stringResource(R.string.cast_title_connect),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

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
                        onTurnOnWifi = onTurnOnWifi,
                        onOpenBluetoothSettings = onOpenBluetoothSettings,
                        onRefresh = onRefresh,
                        bottomSpacing = 20.dp,
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
                    )
                }
            }
        }

        PrimaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
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
    }
}

@Composable
private fun CastControlsTabContent(
    state: CastSheetUiState,
    allConnectivityOff: Boolean,
    onDisconnect: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onTurnOnWifi: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRefresh: () -> Unit,
    bottomSpacing: Dp,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ActiveDeviceHero(
            device = state.activeDevice,
            onDisconnect = onDisconnect,
            onVolumeChange = onVolumeChange
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cast_cd_refresh_connections))
                }
            }
            QuickSettingsRow(
                wifiOn = state.wifiRadioOn,
                wifiConnected = state.wifiEnabled,
                wifiSsid = state.wifiSsid,
                onWifiClick = onTurnOnWifi,
                bluetoothEnabled = state.isBluetoothEnabled,
                bluetoothName = state.bluetoothName,
                onBluetoothClick = onOpenBluetoothSettings
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
) {
    val colors = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxContentHeight)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        if (!allConnectivityOff) {
            item(key = "deviceSectionHeader") {
                DeviceSectionHeader(
                    modifier = Modifier.fillMaxWidth(),
                    hasDevices = state.devices.isNotEmpty(),
                    onRefresh = onRefresh
                )
            }

            item(key = "refreshIndicator") {
                AnimatedVisibility(
                    visible = state.isRefreshing,
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

        if (state.isScanning && state.devices.isEmpty()) {
            item(key = "scanningPlaceholder") {
                ScanningPlaceholderList()
            }
        } else if (state.devices.isEmpty()) {
            item(key = "emptyDevices") {
                EmptyDeviceState()
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
    onRefresh: () -> Unit
) {
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
                text = if (hasDevices) stringResource(R.string.cast_tap_to_connect) else stringResource(R.string.cast_no_devices_yet),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(
            onClick = onRefresh,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.clip(RoundedCornerShape(16.dp))
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cast_cd_refresh_devices))
        }
    }
}
```

---

## BƯỚC 6: Sửa Thanh trượt Âm lượng bị giật, Phân biệt màu Local/Remote, Cặp màu Disconnect & `CircularWavyProgressIndicator` trong `ActiveDeviceHero`
**File:** `app/src/main/java/com/theveloper/pixelplay/presentation/components/CastBottomSheet.kt`

Tìm hàm `ActiveDeviceHero` và thay thế toàn bộ hàm đó bằng:
```kotlin
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActiveDeviceHero(
    device: ActiveDeviceUi,
    onDisconnect: () -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    var sliderValue by remember(device.id) { mutableFloatStateOf(device.volume) }
    var isDraggingSlider by remember(device.id) { mutableStateOf(false) }
    LaunchedEffect(device.volume, isDraggingSlider) {
        if (!isDraggingSlider) {
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        .width(62.dp),
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
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = heroContentColor,
                        maxLines = 2
                    )

                    val statusText = buildString {
                        append(device.subtitle)
                        append(" • ")
                        append(device.connectionLabel)
                    }

                    Text(
                        text = if (device.isConnecting && device.isRemote) stringResource(R.string.cast_connecting_ellipsis) else statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = heroContentColor,
                        maxLines = 1
                    )
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
                        text = if (device.isRemote) stringResource(R.string.cast_volume_device) else stringResource(R.string.cast_volume_phone),
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
```

---

## BƯỚC 7: Sửa lỗi méo ngôi sao xoay (`RoundedStarShape`) & Thêm State Morphing cho `CastDeviceRow`
**File:** `app/src/main/java/com/theveloper/pixelplay/presentation/components/CastBottomSheet.kt`

Tìm hàm `CastDeviceRow` và thay thế bằng:
```kotlin
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

    val infiniteRotation = rememberInfiniteTransition(label = "activeDeviceRotation")
    val rotation by infiniteRotation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "deviceRotation"
    )
    val backgroundScale by animateFloatAsState(
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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .clickable(
                enabled = true,
                onClick = when {
                    device.isBluetooth -> onSelect
                    device.isSelected &&
                        device.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED -> onDisconnect
                    device.isSelected &&
                        device.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING -> ({})
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
                            rotationZ = if (isActiveDevice) rotation else 0f
                            scaleX = backgroundScale
                            scaleY = backgroundScale
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
```

---

## BƯỚC 8: Sửa lỗi Giật góc (Corner Popping) trên `QuickSettingTile` & Xóa hàm thừa `ScanningIndicator`
**File:** `app/src/main/java/com/theveloper/pixelplay/presentation/components/CastBottomSheet.kt`

1. Tìm hàm `QuickSettingTile` và thay bằng (dùng `animateDpAsState` với `fastSpatialSpec()` và `defaultEffectsSpec()`):
```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuickSettingTile(
    label: String,
    subtitle: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
            .height(72.dp)
            .clip(tileShape)
            .clickable(onClick = onClick),
        shape = tileShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconBoxColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
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
```

2. Xóa hàm `ScanningIndicator(isActive: Boolean)` không còn sử dụng (nằm ngay dưới `ScanningPlaceholderList()`).
