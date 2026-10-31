package com.theveloper.pixelplay.data.ai.offline

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

data class DryRunBenchmarkMetrics(
    val tokensPerSecond: Float,
    val ramBeforeAvailableBytes: Long,
    val ramAfterAvailableBytes: Long,
    val ramDeltaBytes: Long,
    val triggeredCriticalTrim: Boolean,
    val ggufVersion: Int,
    val tensorCount: Long,
    val passed: Boolean,
    val isSlowWarning: Boolean,
    val summaryMessage: String
) {
    val ramDeltaMb: Int
        get() = (ramDeltaBytes / OfflineModelCatalog.MB_BYTES).toInt()
}

sealed class BenchmarkStepState {
    object Idle : BenchmarkStepState()
    data class RunningStep(
        val stepIndex: Int,
        val totalSteps: Int,
        val progressFraction: Float,
        val statusText: String
    ) : BenchmarkStepState()

    data class RequireUserRiskConfirmation(
        val warningResult: StaticHardwareEvalResult.Warning
    ) : BenchmarkStepState()

    data class Completed(
        val staticSnapshot: DeviceHardwareSnapshot,
        val metrics: DryRunBenchmarkMetrics
    ) : BenchmarkStepState()

    data class BlockedOrFailed(
        val reason: String,
        val suggestion: String,
        val metrics: DryRunBenchmarkMetrics? = null
    ) : BenchmarkStepState()
}

@Singleton
class ModelBenchmarkRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hardwareInspector: DeviceHardwareInspector,
    private val llamaEngine: LlamaEngine
) {

    /**
     * Executes the full Hardware Gatekeeper + Dry-Run Benchmark pipeline:
     * 1. Static Hardware & ABI & Storage evaluation.
     * 2. GGUF binary header verification.
     * 3. Unloads any active model in LlamaEngine to ensure clean RAM baseline.
     * 4. Loads target GGUF model with short context (512 tokens).
     * 5. Generates 16 tokens from sample prompt "Hello" while monitoring RAM delta and TRIM_MEMORY_RUNNING_CRITICAL.
     * 6. Evaluates speed (> 3 tokens/s optimal, < 1 token/s blocked) and unloads test context.
     */
    fun runHardwareCheckAndBenchmark(
        model: OfflineModelInfo,
        userAcceptedWarningRisk: Boolean = false,
        threadsCount: Int = 4,
        useGpu: Boolean = false
    ): Flow<BenchmarkStepState> = flow {
        emit(
            BenchmarkStepState.RunningStep(
                stepIndex = 1,
                totalSteps = 4,
                progressFraction = 0.15f,
                statusText = "Bước 1/4: Kiểm tra kiến trúc CPU 64-bit, RAM và bộ nhớ trống..."
            )
        )

        val staticResult = hardwareInspector.evaluateForModel(
            model = model,
            requireDownloadStorage = false
        )

        val snapshot = when (staticResult) {
            is StaticHardwareEvalResult.Blocked -> {
                emit(
                    BenchmarkStepState.BlockedOrFailed(
                        reason = staticResult.reason,
                        suggestion = if (staticResult.isStorageError) {
                            "Hãy dọn dẹp bộ nhớ thiết bị và thử lại."
                        } else {
                            "Thiết bị không đạt mức phần cứng tối thiểu để chạy mô hình LLM cục bộ."
                        }
                    )
                )
                return@flow
            }
            is StaticHardwareEvalResult.Warning -> {
                if (!userAcceptedWarningRisk) {
                    emit(BenchmarkStepState.RequireUserRiskConfirmation(staticResult))
                    return@flow
                }
                staticResult.snapshot
            }
            is StaticHardwareEvalResult.Passed -> staticResult.snapshot
        }

        val modelPath = model.localFilePath
        if (modelPath.isNullOrBlank() || !File(modelPath).exists()) {
            emit(
                BenchmarkStepState.BlockedOrFailed(
                    reason = "Chưa tìm thấy file mô hình GGUF trên bộ nhớ máy.",
                    suggestion = "Vui lòng tải xuống mô hình hoặc chọn lại file .gguf hợp lệ trước khi kiểm tra."
                )
            )
            return@flow
        }

        val modelFile = File(modelPath)

        emit(
            BenchmarkStepState.RunningStep(
                stepIndex = 2,
                totalSteps = 4,
                progressFraction = 0.35f,
                statusText = "Bước 2/4: Xác thực cấu trúc tệp nhị phân GGUF (${modelFile.name})..."
            )
        )

        val ggufHeader = LlamaContext.inspectGgufFile(modelFile)
        if (!ggufHeader.isValid) {
            emit(
                BenchmarkStepState.BlockedOrFailed(
                    reason = ggufHeader.errorReason ?: "File .gguf bị hỏng hoặc sai định dạng.",
                    suggestion = "Hãy tải lại mô hình hoặc chọn một file GGUF v2/v3 hợp lệ."
                )
            )
            return@flow
        }

        // Unload any currently loaded model before dry-run to get accurate RAM measurement
        llamaEngine.unload()
        System.gc()

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memBefore = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }

        val criticalTrimTriggered = java.util.concurrent.atomic.AtomicBoolean(false)
        val trimCallback = object : ComponentCallbacks2 {
            @Suppress("DEPRECATION")
            override fun onTrimMemory(level: Int) {
                if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL || memBefore.lowMemory) {
                    criticalTrimTriggered.set(true)
                }
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() {
                criticalTrimTriggered.set(true)
            }
        }

        context.registerComponentCallbacks(trimCallback)
        val dryRunContext = LlamaContext()

        try {
            emit(
                BenchmarkStepState.RunningStep(
                    stepIndex = 3,
                    totalSteps = 4,
                    progressFraction = 0.60f,
                    statusText = "Bước 3/4: Nạp thử nghiệm mô hình với Context ngắn (512 tokens)..."
                )
            )

            dryRunContext.loadModel(
                modelPath = modelFile.absolutePath,
                params = LlamaParams(
                    contextSize = 512,
                    batchSize = 256,
                    threadsCount = threadsCount.coerceIn(1, 8),
                    useGpu = useGpu,
                    useMmap = true,
                    useMlock = false
                )
            )

            emit(
                BenchmarkStepState.RunningStep(
                    stepIndex = 4,
                    totalSteps = 4,
                    progressFraction = 0.85f,
                    statusText = "Bước 4/4: Chạy Dry-Run sinh 16 tokens ('Hello') & đo tốc độ suy luận..."
                )
            )

            val tokensPerSecond = dryRunContext.benchmark(testTokens = 16)
            val memAfter = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
            val rawRamDelta = max(0L, memBefore.availMem - memAfter.availMem)
            val estimatedRamDelta = if (rawRamDelta > 32L * OfflineModelCatalog.MB_BYTES) {
                rawRamDelta
            } else {
                // If mmap page cache didn't decrease availMem directly, report actual mapped footprint estimate
                (modelFile.length() * 6L / 10L).coerceAtLeast(128L * OfflineModelCatalog.MB_BYTES)
            }

            val isCriticalOomRisk = criticalTrimTriggered.get() || memAfter.lowMemory
            val isTooSlow = tokensPerSecond < 1.0f
            val isSlowWarning = tokensPerSecond in 1.0f..2.99f
            val passed = !isCriticalOomRisk && !isTooSlow

            val summaryMsg = when {
                isCriticalOomRisk ->
                    "Hệ điều hành phát tín hiệu thiếu hụt RAM nghiêm trọng (TRIM_MEMORY_RUNNING_CRITICAL). Không an toàn để chạy cùng trình phát nhạc."
                isTooSlow ->
                    "Tốc độ suy luận quá chậm (${String.format("%.1f", tokensPerSecond)} tokens/s < 1.0 token/s). Vui lòng chọn mô hình nhẹ hơn (như Llama 3.2 1B)."
                isSlowWarning ->
                    "Đạt chuẩn phần cứng (Tốc độ ${String.format("%.1f", tokensPerSecond)} tokens/s - hơi chậm nhưng ổn định, không tràn RAM)."
                else ->
                    "Vượt qua bài kiểm tra xuất sắc! Tốc độ đạt ${String.format("%.1f", tokensPerSecond)} tokens/s, RAM ổn định."
            }

            val metrics = DryRunBenchmarkMetrics(
                tokensPerSecond = tokensPerSecond,
                ramBeforeAvailableBytes = memBefore.availMem,
                ramAfterAvailableBytes = memAfter.availMem,
                ramDeltaBytes = estimatedRamDelta,
                triggeredCriticalTrim = isCriticalOomRisk,
                ggufVersion = ggufHeader.version,
                tensorCount = ggufHeader.tensorCount,
                passed = passed,
                isSlowWarning = isSlowWarning,
                summaryMessage = summaryMsg
            )

            if (passed) {
                emit(BenchmarkStepState.Completed(staticSnapshot = snapshot, metrics = metrics))
            } else {
                emit(
                    BenchmarkStepState.BlockedOrFailed(
                        reason = summaryMsg,
                        suggestion = "Khuyên chuyển sang mô hình nhẹ hơn (Llama 3.2 1B hoặc Qwen 2.5 1.5B) và đóng bớt các ứng dụng chạy nền.",
                        metrics = metrics
                    )
                )
            }
        } catch (oom: OutOfMemoryError) {
            Timber.tag("ModelBenchmarkRunner").e(oom, "OOM during dry-run benchmark")
            emit(
                BenchmarkStepState.BlockedOrFailed(
                    reason = "Tràn bộ nhớ (OutOfMemoryError) khi nạp mô hình ${model.displayName}.",
                    suggestion = "Thiết bị không đủ RAM trống cho mô hình này. Hãy chuyển sang mô hình nhẹ hơn."
                )
            )
        } catch (e: Throwable) {
            Timber.tag("ModelBenchmarkRunner").e(e, "Dry-run benchmark failed")
            emit(
                BenchmarkStepState.BlockedOrFailed(
                    reason = "Không thể chạy thử mô hình: ${e.message ?: "Lỗi không xác định"}",
                    suggestion = "Kiểm tra lại file .gguf hoặc chọn mô hình đề xuất từ danh mục."
                )
            )
        } finally {
            runCatching { context.unregisterComponentCallbacks(trimCallback) }
            runCatching { dryRunContext.unloadModel() }
        }
    }.flowOn(Dispatchers.IO)
}
