package com.theveloper.pixelplay.presentation.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.theveloper.pixelplay.data.ai.offline.CustomImportState
import com.theveloper.pixelplay.data.ai.offline.DeviceHardwareSnapshot
import com.theveloper.pixelplay.data.ai.offline.ModelDownloadState
import com.theveloper.pixelplay.data.ai.offline.OfflineModelCatalog
import com.theveloper.pixelplay.data.ai.offline.OfflineModelInfo
import com.theveloper.pixelplay.data.ai.offline.StaticHardwareEvalResult
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.HardwareCheckDialog
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.viewmodel.OfflineModelHubViewModel
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun OfflineModelHubScreen(
    navController: NavController,
    viewModel: OfflineModelHubViewModel = hiltViewModel()
) {
    val hardwareSnapshot by viewModel.hardwareSnapshot.collectAsStateWithLifecycle()
    val recommendedModels by viewModel.recommendedModels.collectAsStateWithLifecycle()
    val customModels by viewModel.customModels.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val customImportState by viewModel.customImportState.collectAsStateWithLifecycle()
    val activeModelId by viewModel.activeOfflineModelId.collectAsStateWithLifecycle()
    val activeModelName by viewModel.activeOfflineModelName.collectAsStateWithLifecycle()
    val hardwarePassed by viewModel.offlineHardwarePassed.collectAsStateWithLifecycle()
    val lastBenchmarkSpeed by viewModel.offlineLastBenchmarkSpeed.collectAsStateWithLifecycle()
    val threadsCount by viewModel.offlineThreadsCount.collectAsStateWithLifecycle()
    val useGpu by viewModel.offlineUseGpu.collectAsStateWithLifecycle()
    val isModelLoadedInRam by viewModel.isModelLoadedInRam.collectAsStateWithLifecycle()
    val selectedModelForCheck by viewModel.selectedModelForCheck.collectAsStateWithLifecycle()
    val benchmarkState by viewModel.benchmarkState.collectAsStateWithLifecycle()

    val ggufFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importCustomGguf(uri)
        }
    }

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 180.dp
    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(topBarHeight.value) {
        collapseFraction = 1f - ((topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx))
            .coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0
                if (!isScrollingDown && (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)) {
                    return Offset.Zero
                }
                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight
                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch { topBarHeight.snapTo(newHeight) }
                }
                val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand = lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
            val targetValue = if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx
            if (topBarHeight.value != targetValue) {
                coroutineScope.launch { topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium)) }
            }
        }
    }

    selectedModelForCheck?.let { modelToCheck ->
        HardwareCheckDialog(
            model = modelToCheck,
            benchmarkState = benchmarkState,
            onConfirmRiskAndRun = { viewModel.confirmRiskAndRunDryRun() },
            onActivateModel = { model, speed -> viewModel.activateVerifiedModel(model, speed) },
            onRetry = { viewModel.openHardwareCheckDialog(modelToCheck) },
            onDismiss = { viewModel.dismissHardwareDialog() }
        )
    }

    Box(
        modifier = Modifier
            .nestedScroll(nestedScrollConnection)
            .fillMaxSize()
    ) {
        val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }

        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(
                top = currentTopBarHeightDp + 8.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. [THIẾT BỊ CỦA BẠN]
            item(key = "device_hardware_card") {
                DeviceHardwareSummaryCard(
                    snapshot = hardwareSnapshot,
                    activeModelName = activeModelName,
                    hardwarePassed = hardwarePassed,
                    lastBenchmarkSpeed = lastBenchmarkSpeed,
                    isModelLoadedInRam = isModelLoadedInRam,
                    onRefresh = { viewModel.refreshAll() },
                    onUnloadRam = { viewModel.unloadModelFromRamNow() }
                )
            }

            // 2. [DANH SÁCH MÔ HÌNH ĐỀ XUẤT]
            item(key = "recommended_header") {
                SectionHeaderTitle(
                    title = "DANH SÁCH MÔ HÌNH ĐỀ XUẤT",
                    subtitle = "Tải trực tiếp vào bộ nhớ ứng dụng, hỗ trợ Tạm dừng / Tiếp tục."
                )
            }

            items(recommendedModels, key = { it.id }) { model ->
                val isCurrentActive = activeModelId == model.id && hardwarePassed
                val staticEval = remember(model, hardwareSnapshot) {
                    viewModel.evaluateStaticModelStatus(model)
                }
                OfflineModelItemCard(
                    model = model,
                    downloadState = downloadStates[model.id] ?: ModelDownloadState.Idle,
                    staticEval = staticEval,
                    isActiveModel = isCurrentActive,
                    onStartOrResumeDownload = { viewModel.startOrResumeDownload(model) },
                    onPauseDownload = { viewModel.pauseDownload(model.id) },
                    onCheckAndActivate = { viewModel.openHardwareCheckDialog(model) },
                    onDeleteModel = { viewModel.deleteModel(model) }
                )
            }

            // 3. [TÙY CHỌN RIÊNG - SAF CUSTOM FILE PICKER]
            item(key = "custom_header") {
                SectionHeaderTitle(
                    title = "TÙY CHỌN RIÊNG (.GGUF TỰ CHỌN)",
                    subtitle = "Chọn file .gguf bất kỳ từ bộ nhớ máy qua SAF."
                )
            }

            item(key = "custom_picker_card") {
                CustomModelPickerCard(
                    importState = customImportState,
                    onPickFile = {
                        viewModel.clearImportState()
                        ggufFilePickerLauncher.launch(arrayOf("*/*"))
                    },
                    onDismissError = { viewModel.clearImportState() }
                )
            }

            if (customModels.isNotEmpty()) {
                items(customModels, key = { it.id }) { customModel ->
                    val isCurrentActive = activeModelId == customModel.id && hardwarePassed
                    val staticEval = remember(customModel, hardwareSnapshot) {
                        viewModel.evaluateStaticModelStatus(customModel)
                    }
                    OfflineModelItemCard(
                        model = customModel,
                        downloadState = ModelDownloadState.Completed(
                            modelId = customModel.id,
                            filePath = customModel.localFilePath.orEmpty()
                        ),
                        staticEval = staticEval,
                        isActiveModel = isCurrentActive,
                        onStartOrResumeDownload = {},
                        onPauseDownload = {},
                        onCheckAndActivate = { viewModel.openHardwareCheckDialog(customModel) },
                        onDeleteModel = { viewModel.deleteModel(customModel) }
                    )
                }
            }

            // 4. [CẤU HÌNH RUNTIME OFFLINE]
            item(key = "runtime_config_card") {
                OfflineRuntimeConfigCard(
                    threadsCount = threadsCount,
                    useGpu = useGpu,
                    onThreadsChange = { viewModel.setThreadsCount(it) },
                    onUseGpuChange = { viewModel.setUseGpu(it) }
                )
            }
        }

        CollapsibleCommonTopBar(
            title = "Quản lý Mô hình AI Offline",
            collapseFraction = collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = { navController.popBackStack() },
            expandedTitleStartPadding = 20.dp,
            collapsedTitleStartPadding = 68.dp,
            maxLines = 2
        )
    }
}

@Composable
private fun SectionHeaderTitle(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeviceHardwareSummaryCard(
    snapshot: DeviceHardwareSnapshot?,
    activeModelName: String,
    hardwarePassed: Boolean,
    lastBenchmarkSpeed: Float,
    isModelLoadedInRam: Boolean,
    onRefresh: () -> Unit,
    onUnloadRam: () -> Unit
) {
    val isCapable = snapshot?.isGenerallyCapable ?: true
    val containerColor = if (isCapable) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (isCapable) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AbsoluteSmoothCornerShape(28.dp, 60),
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = null,
                        tint = contentColor
                    )
                    Text(
                        text = "THIẾT BỊ CỦA BẠN",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Làm mới thông số",
                        tint = contentColor
                    )
                }
            }

            if (snapshot != null) {
                val totalRamStr = String.format(Locale.US, "%.1f GB", snapshot.totalRamGb)
                val availRamStr = String.format(Locale.US, "%.1f GB", snapshot.availableRamGb)
                val archStr = if (snapshot.is64Bit) "64-bit (${snapshot.primaryAbi})" else "32-bit (${snapshot.primaryAbi})"
                val freeRomStr = String.format(Locale.US, "%.1f GB", snapshot.freeStorageGb)
                val statusLabel = if (snapshot.isGenerallyCapable) "Đủ điều kiện sử dụng" else "Không đạt chuẩn tối thiểu"

                Text(
                    text = "RAM: $totalRamStr (Khả dụng: $availRamStr)  |  CPU: ${snapshot.cpuCores} nhân $archStr",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Text(
                    text = "Bộ nhớ trống: $freeRomStr ($statusLabel)  •  SoC: ${snapshot.socHardware}",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.9f)
                )
            } else {
                Text(
                    text = "Đang đọc thông số phần cứng thiết bị...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
            }

            if (activeModelName.isNotBlank() && hardwarePassed) {
                HorizontalDivider(color = contentColor.copy(alpha = 0.2f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Đang kích hoạt: $activeModelName",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                        val speedHint = if (lastBenchmarkSpeed > 0f) {
                            String.format(Locale.US, " • %.1f tokens/s", lastBenchmarkSpeed)
                        } else ""
                        val ramStateText = if (isModelLoadedInRam) {
                            "Đang nạp trong RAM (Auto-unload sau 3 phút nghỉ)"
                        } else {
                            "Đã giải phóng khỏi RAM (Tiết kiệm pin & bộ nhớ)"
                        }
                        Text(
                            text = "$ramStateText$speedHint",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.85f)
                        )
                    }
                    if (isModelLoadedInRam) {
                        OutlinedButton(onClick = onUnloadRam) {
                            Icon(
                                imageVector = Icons.Rounded.CleaningServices,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Xả RAM")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineModelItemCard(
    model: OfflineModelInfo,
    downloadState: ModelDownloadState,
    staticEval: StaticHardwareEvalResult,
    isActiveModel: Boolean,
    onStartOrResumeDownload: () -> Unit,
    onPauseDownload: () -> Unit,
    onCheckAndActivate: () -> Unit,
    onDeleteModel: () -> Unit
) {
    val isDownloaded = model.isDownloaded || downloadState is ModelDownloadState.Completed
    val sizeStr = if (model.sizeBytes >= OfflineModelCatalog.GB_BYTES) {
        String.format(Locale.US, "%.2f GB", model.sizeBytes.toFloat() / OfflineModelCatalog.GB_BYTES)
    } else {
        "${model.sizeBytes / OfflineModelCatalog.MB_BYTES} MB"
    }
    val runtimeRamStr = String.format(Locale.US, "~%.1f GB", model.estimatedRuntimeRamGb)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AbsoluteSmoothCornerShape(24.dp, 60),
        color = if (isActiveModel) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isActiveModel) {
                            Icons.Rounded.RadioButtonChecked
                        } else {
                            Icons.Rounded.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        tint = if (isActiveModel) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Column {
                        Text(
                            text = model.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = model.tier.labelVi,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Right status badge
                ModelStatusBadge(
                    isDownloaded = isDownloaded,
                    isActiveModel = isActiveModel,
                    staticEval = staticEval
                )
            }

            Text(
                text = model.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "• Dung lượng: $sizeStr  |  RAM cần: $runtimeRamStr (Máy >= ${String.format(Locale.US, "%.1f", model.minRamGb)} GB RAM)",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )

            // Hardware status line
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when {
                    isActiveModel -> {
                        Icon(
                            imageVector = Icons.Rounded.Verified,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Trạng thái phần cứng: [✓ ĐÃ KIỂM TRA ĐẠT CHUẨN]",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    staticEval is StaticHardwareEvalResult.Blocked -> {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = staticEval.reason,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    staticEval is StaticHardwareEvalResult.Warning -> {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "Yêu cầu khuyến nghị >= ${String.format(Locale.US, "%.0f", model.recommendedRamGb)} GB RAM (Cần chạy thử Dry-Run)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Tương thích phần cứng (${model.minChipsetHint})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Download progress UI if currently downloading or paused or failed
            when (downloadState) {
                is ModelDownloadState.Downloading -> {
                    val speedMb = String.format(
                        Locale.US,
                        "%.1f MB/s",
                        downloadState.speedBytesPerSec.toFloat() / OfflineModelCatalog.MB_BYTES
                    )
                    val dlMb = downloadState.downloadedBytes / OfflineModelCatalog.MB_BYTES
                    val totMb = downloadState.totalBytes / OfflineModelCatalog.MB_BYTES

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Đang tải: ${downloadState.progressPercent}% ($dlMb / $totMb MB)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = speedMb,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (downloadState.progressPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onPauseDownload,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Tạm dừng")
                            }
                            OutlinedButton(
                                onClick = onDeleteModel,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Hủy tải")
                            }
                        }
                    }
                }

                is ModelDownloadState.Paused -> {
                    val dlMb = downloadState.downloadedBytes / OfflineModelCatalog.MB_BYTES
                    val totMb = downloadState.totalBytes / OfflineModelCatalog.MB_BYTES
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Đã tạm dừng ở ${downloadState.progressPercent}% ($dlMb / $totMb MB)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        LinearProgressIndicator(
                            progress = { (downloadState.progressPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onStartOrResumeDownload,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Tiếp tục tải")
                            }
                            OutlinedButton(
                                onClick = onDeleteModel,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Xóa")
                            }
                        }
                    }
                }

                is ModelDownloadState.Failed -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = downloadState.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onStartOrResumeDownload,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Thử tải lại / Tiếp tục")
                        }
                        OutlinedButton(onClick = onDeleteModel) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                else -> {
                    if (isDownloaded) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isActiveModel) {
                                FilledTonalButton(
                                    onClick = onCheckAndActivate,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("ĐANG SỬ DỤNG (Test lại)")
                                }
                            } else {
                                Button(
                                    onClick = onCheckAndActivate,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Kiểm tra & Kích hoạt")
                                }
                            }

                            OutlinedButton(onClick = onDeleteModel) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Xóa")
                            }
                        }
                    } else {
                        Button(
                            onClick = onStartOrResumeDownload,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Tải về ($sizeStr)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelStatusBadge(
    isDownloaded: Boolean,
    isActiveModel: Boolean,
    staticEval: StaticHardwareEvalResult
) {
    val (label, bg, fg) = when {
        isActiveModel -> Triple(
            "ĐANG SỬ DỤNG",
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary
        )
        isDownloaded -> Triple(
            "ĐÃ TẢI",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        staticEval is StaticHardwareEvalResult.Blocked || staticEval is StaticHardwareEvalResult.Warning -> Triple(
            "CẢNH BÁO",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        else -> Triple(
            "TẢI VỀ",
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CustomModelPickerCard(
    importState: CustomImportState,
    onPickFile: () -> Unit,
    onDismissError: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AbsoluteSmoothCornerShape(24.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onPickFile,
                enabled = importState !is CustomImportState.Copying && importState !is CustomImportState.Validating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("+ Chọn file .gguf từ bộ nhớ máy")
            }

            when (importState) {
                is CustomImportState.Validating -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Đang kiểm tra chữ ký GGUF của '${importState.fileName}'...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                is CustomImportState.Copying -> {
                    val copiedMb = importState.copiedBytes / OfflineModelCatalog.MB_BYTES
                    val totalMb = importState.totalBytes / OfflineModelCatalog.MB_BYTES
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Đang sao chép vào vùng nhớ ứng dụng: ${importState.progressPercent}% ($copiedMb / $totalMb MB)",
                            style = MaterialTheme.typography.labelMedium
                        )
                        LinearProgressIndicator(
                            progress = { (importState.progressPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
                is CustomImportState.Failed -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = importState.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onDismissError, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Đóng",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
                else -> {}
            }

            Text(
                text = "* File tự chọn phải vượt qua bài kiểm tra RAM & chạy thử (Dry-run) trước khi kích hoạt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OfflineRuntimeConfigCard(
    threadsCount: Int,
    useGpu: Boolean,
    onThreadsChange: (Int) -> Unit,
    onUseGpuChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AbsoluteSmoothCornerShape(24.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Science,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Cấu hình Lõi Suy Luận (Llama.cpp)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Số luồng CPU (Threads)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$threadsCount luồng",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = threadsCount.toFloat(),
                    onValueChange = { onThreadsChange(it.roundToInt().coerceIn(1, 8)) },
                    valueRange = 1f..8f,
                    steps = 6
                )
                Text(
                    text = "Khuyên dùng 4 luồng để cân bằng tốc độ sinh Playlist và nhiệt độ máy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tăng tốc GPU (OpenCL / Vulkan)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Tận dụng GPU Adreno/Mali để giảm tải cho CPU khi suy luận.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = useGpu,
                    onCheckedChange = onUseGpuChange
                )
            }
        }
    }
}
