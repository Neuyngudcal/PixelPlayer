package com.theveloper.pixelplay.data.ai.offline

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Feature
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

enum class AiCoreAvailabilityStatus {
    CHECKING,
    AVAILABLE,
    DOWNLOADABLE,
    DOWNLOADING,
    UNAVAILABLE,
    ERROR
}

data class AiCoreModelInfo(
    val availability: AiCoreAvailabilityStatus = AiCoreAvailabilityStatus.CHECKING,
    val isSystemServiceInstalled: Boolean = false,
    val aicorePackageVersion: String = "Đang kiểm tra...",
    val baseModelName: String = "",
    val tokenLimit: Int = 0,
    val supportsSystemPrompt: Boolean = false,
    val supportedFeatures: List<String> = emptyList(),
    val isWarmedUpInNpu: Boolean = false,
    val socHardware: String = "",
    val deviceModel: String = "",
    val androidVersion: String = "",
    val statusMessageVi: String = "Đang kiểm tra hỗ trợ Android AICore (NPU)...",
    val downloadBytesTransferred: Long = 0L,
    val downloadTotalBytes: Long = 0L,
    val lastInferenceDurationMs: Long = 0L,
    val lastCheckedTimestampMs: Long = 0L
) {
    val isSupportedOnDevice: Boolean
        get() = availability == AiCoreAvailabilityStatus.AVAILABLE ||
            availability == AiCoreAvailabilityStatus.DOWNLOADABLE ||
            availability == AiCoreAvailabilityStatus.DOWNLOADING
}

/**
 * Runtime engine wrapping Android AICore (Gemini Nano on-device NPU) via ML Kit GenAI Prompt API.
 */
@Singleton
class AndroidAiCoreEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepo: AiPreferencesRepository
) {
    companion object {
        private const val TAG = "AndroidAiCoreEngine"
        const val AICORE_PACKAGE_NAME = "com.google.android.aicore"
        private const val FALLBACK_BASE_MODEL_NAME = "Gemini Nano (Android AICore)"
    }

    private val engineMutex = Mutex()
    private val generativeModel: GenerativeModel? by lazy {
        runCatching { Generation.getClient() }
            .onFailure { Timber.tag(TAG).w(it, "Failed to obtain ML Kit Prompt GenerativeModel client") }
            .getOrNull()
    }

    private val _modelInfo = MutableStateFlow(createInitialInfo())
    val modelInfo: StateFlow<AiCoreModelInfo> = _modelInfo.asStateFlow()

    private fun createInitialInfo(): AiCoreModelInfo {
        val (installed, pkgVersion) = readAiCorePackageInfo()
        val soc = readSocHardware()
        return AiCoreModelInfo(
            availability = AiCoreAvailabilityStatus.CHECKING,
            isSystemServiceInstalled = installed,
            aicorePackageVersion = pkgVersion,
            socHardware = soc,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        )
    }

    /**
     * Checks whether this device supports Android AICore and retrieves active Gemini Nano model info.
     */
    suspend fun checkDeviceSupportAndModelInfo(): AiCoreModelInfo = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            val base = _modelInfo.value
            _modelInfo.value = base.copy(
                availability = AiCoreAvailabilityStatus.CHECKING,
                statusMessageVi = "Đang kiểm tra dịch vụ hệ thống Android AICore & phần cứng NPU..."
            )

            val (isPkgInstalled, pkgVersion) = readAiCorePackageInfo()
            val soc = readSocHardware()
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            val androidVer = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

            val client = generativeModel
            if (client == null) {
                val info = AiCoreModelInfo(
                    availability = AiCoreAvailabilityStatus.UNAVAILABLE,
                    isSystemServiceInstalled = isPkgInstalled,
                    aicorePackageVersion = pkgVersion,
                    socHardware = soc,
                    deviceModel = deviceName,
                    androidVersion = androidVer,
                    statusMessageVi = "Thiết bị không thể khởi tạo ML Kit GenAI Prompt API (AICore không khả dụng).",
                    lastCheckedTimestampMs = System.currentTimeMillis()
                )
                _modelInfo.value = info
                preferencesRepo.setAiCoreStatus(supported = false, baseModelName = "")
                return@withLock info
            }

            val updated = runCatching {
                val featureStatus = client.checkStatus()
                val mappedStatus = when (featureStatus) {
                    FeatureStatus.AVAILABLE -> AiCoreAvailabilityStatus.AVAILABLE
                    FeatureStatus.DOWNLOADABLE -> AiCoreAvailabilityStatus.DOWNLOADABLE
                    FeatureStatus.DOWNLOADING -> AiCoreAvailabilityStatus.DOWNLOADING
                    else -> AiCoreAvailabilityStatus.UNAVAILABLE
                }

                var detectedModelName = ""
                var tokenLimit = 0
                var supportsSysPrompt = false
                var featureLabels = emptyList<String>()

                if (mappedStatus == AiCoreAvailabilityStatus.AVAILABLE) {
                    val capabilities = inspectModelCapabilities(client)
                    detectedModelName = capabilities.baseModelName
                    tokenLimit = capabilities.tokenLimit
                    supportsSysPrompt = capabilities.supportsSystemPrompt
                    featureLabels = capabilities.featureLabels
                }

                val statusMsg = when (mappedStatus) {
                    AiCoreAvailabilityStatus.AVAILABLE ->
                        "Thiết bị hỗ trợ Android AICore — Gemini Nano đã sẵn sàng trên NPU."
                    AiCoreAvailabilityStatus.DOWNLOADABLE ->
                        "Thiết bị có NPU hỗ trợ AICore nhưng hệ điều hành chưa tải gói Gemini Nano."
                    AiCoreAvailabilityStatus.DOWNLOADING ->
                        "Hệ điều hành đang tải gói Gemini Nano cho Android AICore..."
                    AiCoreAvailabilityStatus.UNAVAILABLE -> if (!isPkgInstalled) {
                        "Thiết bị chưa cài dịch vụ hệ thống Google Android AICore (com.google.android.aicore)."
                    } else {
                        "Thiết bị/SoC này ($soc) chưa được Google/OEM kích hoạt Gemini Nano trên Android AICore."
                    }
                    else -> "Không xác định được trạng thái AICore."
                }

                AiCoreModelInfo(
                    availability = mappedStatus,
                    isSystemServiceInstalled = isPkgInstalled,
                    aicorePackageVersion = pkgVersion,
                    baseModelName = detectedModelName,
                    tokenLimit = tokenLimit,
                    supportsSystemPrompt = supportsSysPrompt,
                    supportedFeatures = featureLabels,
                    isWarmedUpInNpu = if (mappedStatus == AiCoreAvailabilityStatus.AVAILABLE) base.isWarmedUpInNpu else false,
                    socHardware = soc,
                    deviceModel = deviceName,
                    androidVersion = androidVer,
                    statusMessageVi = statusMsg,
                    lastInferenceDurationMs = base.lastInferenceDurationMs,
                    lastCheckedTimestampMs = System.currentTimeMillis()
                )
            }.getOrElse { error ->
                Timber.tag(TAG).w(error, "checkStatus() failed for Android AICore")
                AiCoreModelInfo(
                    availability = if (isPkgInstalled) AiCoreAvailabilityStatus.ERROR else AiCoreAvailabilityStatus.UNAVAILABLE,
                    isSystemServiceInstalled = isPkgInstalled,
                    aicorePackageVersion = pkgVersion,
                    socHardware = soc,
                    deviceModel = deviceName,
                    androidVersion = androidVer,
                    statusMessageVi = "Không thể truy vấn AICore: ${error.localizedMessage ?: "Thiết bị không hỗ trợ."}",
                    lastCheckedTimestampMs = System.currentTimeMillis()
                )
            }

            _modelInfo.value = updated
            preferencesRepo.setAiCoreStatus(
                supported = updated.isSupportedOnDevice,
                baseModelName = updated.baseModelName
            )
            updated
        }
    }

    /**
     * Loads Gemini Nano into NPU runtime via warmup() and refreshes exact running base model info.
     */
    suspend fun warmupAndInspectModel(): Result<AiCoreModelInfo> = withContext(Dispatchers.IO) {
        val currentCheck = checkDeviceSupportAndModelInfo()
        if (currentCheck.availability != AiCoreAvailabilityStatus.AVAILABLE) {
            return@withContext Result.failure(IllegalStateException(currentCheck.statusMessageVi))
        }
        val client = generativeModel ?: return@withContext Result.failure(
            IllegalStateException("Không thể khởi tạo client Android AICore.")
        )

        engineMutex.withLock {
            runCatching {
                client.warmup()
                val capabilities = inspectModelCapabilities(
                    client = client,
                    fallbackBaseName = currentCheck.baseModelName
                )

                val updated = _modelInfo.value.copy(
                    availability = AiCoreAvailabilityStatus.AVAILABLE,
                    baseModelName = capabilities.baseModelName,
                    tokenLimit = if (capabilities.tokenLimit > 0) capabilities.tokenLimit else currentCheck.tokenLimit,
                    supportsSystemPrompt = capabilities.supportsSystemPrompt,
                    supportedFeatures = capabilities.featureLabels.ifEmpty { currentCheck.supportedFeatures },
                    isWarmedUpInNpu = true,
                    statusMessageVi = "Model ${capabilities.baseModelName} đã nạp sẵn trong NPU hệ thống (0 MB RAM ứng dụng).",
                    lastCheckedTimestampMs = System.currentTimeMillis()
                )
                _modelInfo.value = updated
                preferencesRepo.setAiCoreStatus(supported = true, baseModelName = capabilities.baseModelName)
                updated
            }
        }
    }

    /**
     * Triggers system-level Gemini Nano download inside Android AICore when status is DOWNLOADABLE.
     */
    fun downloadSystemModel(): Flow<AiCoreModelInfo> = flow {
        val client = generativeModel
        if (client == null) {
            val failed = _modelInfo.value.copy(
                availability = AiCoreAvailabilityStatus.UNAVAILABLE,
                statusMessageVi = "Thiết bị không hỗ trợ ML Kit GenAI Prompt API."
            )
            _modelInfo.value = failed
            emit(failed)
            return@flow
        }

        try {
            client.download().collect { status ->
                val current = _modelInfo.value
                val updated = when (status) {
                    is DownloadStatus.DownloadStarted -> current.copy(
                        availability = AiCoreAvailabilityStatus.DOWNLOADING,
                        downloadTotalBytes = status.bytesToDownload,
                        downloadBytesTransferred = 0L,
                        statusMessageVi = "Hệ thống Android AICore đang bắt đầu tải Gemini Nano..."
                    )
                    is DownloadStatus.DownloadProgress -> current.copy(
                        availability = AiCoreAvailabilityStatus.DOWNLOADING,
                        downloadBytesTransferred = status.totalBytesDownloaded,
                        statusMessageVi = "Đang tải gói Gemini Nano hệ thống (${status.totalBytesDownloaded / (1024 * 1024)} MB)..."
                    )
                    DownloadStatus.DownloadCompleted -> {
                        val refreshed = runCatching {
                            val name = client.getBaseModelName().trim().ifBlank { FALLBACK_BASE_MODEL_NAME }
                            val limit = runCatching { client.getTokenLimit() }.getOrDefault(0)
                            name to limit
                        }.getOrNull()
                        val modelName = refreshed?.first ?: FALLBACK_BASE_MODEL_NAME
                        preferencesRepo.setAiCoreStatus(supported = true, baseModelName = modelName)
                        current.copy(
                            availability = AiCoreAvailabilityStatus.AVAILABLE,
                            baseModelName = modelName,
                            tokenLimit = refreshed?.second ?: current.tokenLimit,
                            downloadBytesTransferred = current.downloadTotalBytes,
                            statusMessageVi = "Đã tải xong Gemini Nano! Sẵn sàng chạy trên NPU.",
                            lastCheckedTimestampMs = System.currentTimeMillis()
                        )
                    }
                    is DownloadStatus.DownloadFailed -> current.copy(
                        availability = AiCoreAvailabilityStatus.ERROR,
                        statusMessageVi = "Tải Gemini Nano thất bại: ${status.e.localizedMessage ?: "Lỗi AICore (${status.e.errorCode})"}"
                    )
                }
                _modelInfo.value = updated
                emit(updated)
            }
        } catch (e: Exception) {
            val failed = _modelInfo.value.copy(
                availability = AiCoreAvailabilityStatus.ERROR,
                statusMessageVi = "Lỗi khi tải Gemini Nano qua AICore: ${e.localizedMessage ?: "Không rõ"}"
            )
            _modelInfo.value = failed
            emit(failed)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Runs on-device NPU inference via Android AICore.
     */
    suspend fun generateContent(
        systemPrompt: String,
        userPrompt: String,
        temperature: Float = 0.2f,
        topK: Int = 32,
        maxTokens: Int = 512
    ): String = withContext(Dispatchers.IO) {
        val currentInfo = checkDeviceSupportAndModelInfo()
        if (currentInfo.availability != AiCoreAvailabilityStatus.AVAILABLE) {
            throw IllegalStateException(currentInfo.statusMessageVi)
        }
        val client = generativeModel ?: throw IllegalStateException("Android AICore client chưa sẵn sàng.")

        engineMutex.withLock {
            val request = buildRequest(
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                temperature = temperature,
                topK = topK,
                maxTokens = maxTokens,
                supportsSystemPrompt = currentInfo.supportsSystemPrompt
            )

            var responseText = ""
            val elapsedMs = measureTimeMillis {
                val response = client.generateContent(request)
                responseText = response.candidates
                    .firstOrNull { !it.text.isNullOrBlank() }
                    ?.text
                    ?.trim()
                    .orEmpty()
            }

            val resolvedModelName = currentInfo.baseModelName.ifBlank {
                runCatching { client.getBaseModelName() }.getOrNull()?.trim().orEmpty()
                    .ifBlank { FALLBACK_BASE_MODEL_NAME }
            }

            _modelInfo.value = _modelInfo.value.copy(
                availability = AiCoreAvailabilityStatus.AVAILABLE,
                baseModelName = resolvedModelName,
                isWarmedUpInNpu = true,
                lastInferenceDurationMs = elapsedMs,
                lastCheckedTimestampMs = System.currentTimeMillis()
            )
            preferencesRepo.setAiCoreStatus(supported = true, baseModelName = resolvedModelName)

            responseText
        }
    }

    suspend fun countTokens(systemPrompt: String, userPrompt: String): Int = withContext(Dispatchers.IO) {
        val client = generativeModel
        val combined = if (systemPrompt.isNotBlank()) "$systemPrompt\n\n$userPrompt" else userPrompt
        if (client != null && _modelInfo.value.availability == AiCoreAvailabilityStatus.AVAILABLE) {
            runCatching {
                val req = generateContentRequest(TextPart(combined)) {}
                client.countTokens(req).totalTokens
            }.getOrElse {
                (combined.length / 4).coerceAtLeast(1)
            }
        } else {
            (combined.length / 4).coerceAtLeast(1)
        }
    }

    private fun buildRequest(
        systemPrompt: String,
        userPrompt: String,
        temperature: Float,
        topK: Int,
        maxTokens: Int,
        supportsSystemPrompt: Boolean
    ): GenerateContentRequest {
        val safeTemp = temperature.coerceIn(0.0f, 1.0f)
        val safeTopK = topK.coerceIn(1, 64)
        val safeMaxTokens = maxTokens.coerceIn(64, 1024)

        val cleanSystem = systemPrompt.trim()
        val cleanUser = userPrompt.trim()

        return if (cleanSystem.isNotEmpty() && supportsSystemPrompt) {
            generateContentRequest(SystemInstruction(cleanSystem), TextPart(cleanUser)) {
                this.temperature = safeTemp
                this.topK = safeTopK
                this.maxOutputTokens = safeMaxTokens
            }
        } else {
            val combinedPrompt = if (cleanSystem.isNotEmpty()) "$cleanSystem\n\n$cleanUser" else cleanUser
            generateContentRequest(TextPart(combinedPrompt)) {
                this.temperature = safeTemp
                this.topK = safeTopK
                this.maxOutputTokens = safeMaxTokens
            }
        }
    }

    private fun readAiCorePackageInfo(): Pair<Boolean, String> {
        return runCatching {
            val pm = context.packageManager
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(AICORE_PACKAGE_NAME, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(AICORE_PACKAGE_NAME, 0)
            }
            val versionName = pkgInfo.versionName?.takeIf { it.isNotBlank() } ?: "unknown"
            val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)
            true to "v$versionName ($versionCode)"
        }.getOrElse {
            false to "Chưa cài đặt"
        }
    }

    private data class ModelCapabilities(
        val baseModelName: String,
        val tokenLimit: Int,
        val supportsSystemPrompt: Boolean,
        val featureLabels: List<String>
    )

    private suspend fun inspectModelCapabilities(
        client: GenerativeModel,
        fallbackBaseName: String = ""
    ): ModelCapabilities {
        val baseName = runCatching { client.getBaseModelName() }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: fallbackBaseName.ifBlank { FALLBACK_BASE_MODEL_NAME }
        val tokenLimit = runCatching { client.getTokenLimit() }.getOrDefault(0)
        val features = runCatching { client.getAvailableFeatures() }.getOrDefault(emptyList())
        val supportsSystemPrompt = Feature.SYSTEM_PROMPT in features
        val labels = features.mapNotNull { feature ->
            when (feature) {
                Feature.SYSTEM_PROMPT -> "System Prompt"
                Feature.THINKING_MODE -> "Thinking Mode"
                Feature.PREFIX_CACHING -> "Prefix Caching"
                Feature.STRUCTURED_OUTPUT -> "Structured Output"
                Feature.FUNCTION_CALLING -> "Function Calling"
                else -> null
            }
        }
        return ModelCapabilities(
            baseModelName = baseName,
            tokenLimit = tokenLimit,
            supportsSystemPrompt = supportsSystemPrompt,
            featureLabels = labels
        )
    }

    private fun readSocHardware(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN } ?: Build.HARDWARE
        } else {
            Build.HARDWARE
        }
    }
}
