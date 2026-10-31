package com.theveloper.pixelplay.data.ai.offline

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceHardwareSnapshot(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val isLowMemory: Boolean,
    val cpuCores: Int,
    val primaryAbi: String,
    val supportedAbis: List<String>,
    val is64Bit: Boolean,
    val hasArm64V8a: Boolean,
    val cpuFeatures: String,
    val hasNeonOrAsimd: Boolean,
    val hasFp16: Boolean,
    val hasDotProd: Boolean,
    val freeStorageBytes: Long,
    val totalStorageBytes: Long,
    val socHardware: String,
    val deviceModel: String
) {
    val totalRamGb: Float
        get() = totalRamBytes.toFloat() / OfflineModelCatalog.GB_BYTES.toFloat()

    val availableRamGb: Float
        get() = availableRamBytes.toFloat() / OfflineModelCatalog.GB_BYTES.toFloat()

    val freeStorageGb: Float
        get() = freeStorageBytes.toFloat() / OfflineModelCatalog.GB_BYTES.toFloat()

    val totalStorageGb: Float
        get() = totalStorageBytes.toFloat() / OfflineModelCatalog.GB_BYTES.toFloat()

    val isGenerallyCapable: Boolean
        get() = is64Bit && totalRamGb >= 3.3f && freeStorageGb >= 2.0f
}

sealed class StaticHardwareEvalResult {
    data class Passed(
        val snapshot: DeviceHardwareSnapshot,
        val message: String
    ) : StaticHardwareEvalResult()

    data class Warning(
        val snapshot: DeviceHardwareSnapshot,
        val reason: String,
        val recommendation: String
    ) : StaticHardwareEvalResult()

    data class Blocked(
        val snapshot: DeviceHardwareSnapshot,
        val reason: String,
        val isStorageError: Boolean = false
    ) : StaticHardwareEvalResult()
}

@Singleton
class DeviceHardwareInspector @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun inspect(): DeviceHardwareSnapshot {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val statFs = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
        val freeStorage = statFs?.availableBytes ?: 0L
        val totalStorage = statFs?.totalBytes ?: 0L

        val abis = Build.SUPPORTED_ABIS?.toList().orEmpty()
        val abis64 = Build.SUPPORTED_64_BIT_ABIS?.toList().orEmpty()
        val primaryAbi = abis.firstOrNull() ?: "unknown"
        val hasArm64 = abis.contains("arm64-v8a")
        val is64Bit = abis64.isNotEmpty() || hasArm64 || abis.contains("x86_64")

        val cpuFeatures = readCpuFeatures()
        val hasNeon = cpuFeatures.contains("asimd") || cpuFeatures.contains("neon") || is64Bit
        val hasFp16 = cpuFeatures.contains("fphp") || cpuFeatures.contains("asimdhp") || cpuFeatures.contains("fp16")
        val hasDotProd = cpuFeatures.contains("asimddp") || cpuFeatures.contains("dotprod")

        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN } ?: Build.HARDWARE
        } else {
            Build.HARDWARE
        }

        return DeviceHardwareSnapshot(
            totalRamBytes = memoryInfo.totalMem,
            availableRamBytes = memoryInfo.availMem,
            isLowMemory = memoryInfo.lowMemory,
            cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            primaryAbi = primaryAbi,
            supportedAbis = abis,
            is64Bit = is64Bit,
            hasArm64V8a = hasArm64,
            cpuFeatures = cpuFeatures,
            hasNeonOrAsimd = hasNeon,
            hasFp16 = hasFp16,
            hasDotProd = hasDotProd,
            freeStorageBytes = freeStorage,
            totalStorageBytes = totalStorage,
            socHardware = soc,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        )
    }

    /**
     * Evaluates device hardware against the state machine in Phase 2 of the Offline AI Plan:
     * 1. CheckStorage -> StorageFail if FreeStorage < (ModelSize + 1.5GB) when downloading/importing
     * 2. CheckABI -> Blocked if not 64-bit
     * 3. CheckRAM ->
     *    - Blocked if Total RAM < 3.5GB (~3.35GB kernel-reported)
     *    - Warning if 3.5GB <= Total RAM < Model Requirement (or Available RAM is tight)
     *    - Passed if Total RAM >= Recommended
     */
    fun evaluateForModel(
        model: OfflineModelInfo,
        requireDownloadStorage: Boolean = !model.isDownloaded
    ): StaticHardwareEvalResult {
        val snapshot = inspect()

        // 1. Check 64-bit ABI
        if (!snapshot.is64Bit) {
            return StaticHardwareEvalResult.Blocked(
                snapshot = snapshot,
                reason = "Thiết bị chạy kiến trúc 32-bit (${snapshot.primaryAbi}). Mô hình GGUF yêu cầu CPU 64-bit (arm64-v8a) để cấp phát không gian địa chỉ bộ nhớ."
            )
        }

        // 2. Check Storage: FreeStorage < (ModelSize + 1.5GB) for download/import, or at least 512MB runtime scratch if already downloaded
        val minRequiredStorageBytes = if (requireDownloadStorage) {
            maxOf(
                model.requiredFreeStorageBytes,
                model.sizeBytes + (1.5 * OfflineModelCatalog.GB_BYTES).toLong()
            )
        } else {
            512L * OfflineModelCatalog.MB_BYTES
        }

        if (snapshot.freeStorageBytes < minRequiredStorageBytes) {
            val requiredGb = String.format("%.1f", minRequiredStorageBytes.toFloat() / OfflineModelCatalog.GB_BYTES)
            val freeGb = String.format("%.1f", snapshot.freeStorageGb)
            return StaticHardwareEvalResult.Blocked(
                snapshot = snapshot,
                reason = "Không đủ bộ nhớ trống: Cần tối thiểu $requiredGb GB trống (Hiện còn $freeGb GB).",
                isStorageError = true
            )
        }

        // 3. Check Total RAM (< 3.5GB -> Blocked; note kernel reserves ~0.15-0.25GB so 3.35GB reported = 3.5GB physical)
        if (snapshot.totalRamGb < 3.35f) {
            val ramStr = String.format("%.1f", snapshot.totalRamGb)
            return StaticHardwareEvalResult.Blocked(
                snapshot = snapshot,
                reason = "Tổng RAM thiết bị ($ramStr GB) thấp hơn mức tối thiểu 3.5 GB. Không thể chạy LLM cục bộ mà không gây sập trình phát nhạc."
            )
        }

        // 4. Check RAM Warning vs Passed
        // Physical 4GB device usually reports ~3.6 - 3.85 GB in ActivityManager.totalMem,
        // Physical 6GB reports ~5.5 - 5.8 GB, Physical 8GB reports ~7.3 - 7.8 GB.
        val effectivePhysicalRamGb = snapshot.totalRamGb + 0.35f
        if (effectivePhysicalRamGb < model.minRamGb) {
            return StaticHardwareEvalResult.Warning(
                snapshot = snapshot,
                reason = "Tổng RAM máy (${String.format("%.1f", snapshot.totalRamGb)} GB) thấp hơn mức tối thiểu của ${model.displayName} (${model.minRamGb} GB).",
                recommendation = "Nguy cơ cao bị hệ điều hành đóng ứng dụng (OOM) khi đang phát nhạc. Khuyên dùng mô hình nhẹ hơn (Llama 3.2 1B hoặc Qwen 2.5 1.5B)."
            )
        }

        if (effectivePhysicalRamGb < model.recommendedRamGb || snapshot.availableRamGb < model.estimatedRuntimeRamGb) {
            return StaticHardwareEvalResult.Warning(
                snapshot = snapshot,
                reason = "RAM khả dụng hiện tại (${String.format("%.1f", snapshot.availableRamGb)} GB / Tổng ${String.format("%.1f", snapshot.totalRamGb)} GB) thấp hơn mức khuyến nghị (${model.recommendedRamGb} GB).",
                recommendation = "Bạn vẫn có thể chạy thử nghiệm (Dry-Run), nhưng nên đóng bớt ứng dụng chạy nền trước khi tạo playlist."
            )
        }

        return StaticHardwareEvalResult.Passed(
            snapshot = snapshot,
            message = "Thiết bị đạt chuẩn phần cứng khuyến nghị (${String.format("%.1f", snapshot.totalRamGb)} GB RAM, ${snapshot.cpuCores} nhân 64-bit)."
        )
    }

    private fun readCpuFeatures(): String {
        val file = File("/proc/cpuinfo")
        if (!file.exists()) return ""
        return runCatching {
            BufferedReader(FileReader(file)).use { reader ->
                buildString {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val lower = line!!.lowercase()
                        if (lower.startsWith("features") || lower.startsWith("flags")) {
                            append(lower).append(" ")
                        }
                    }
                }
            }
        }.getOrDefault("")
    }
}
