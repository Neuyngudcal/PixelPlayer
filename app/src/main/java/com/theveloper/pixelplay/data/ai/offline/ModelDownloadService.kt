package com.theveloper.pixelplay.data.ai.offline

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.theveloper.pixelplay.data.ai.AiNotificationManager
import com.theveloper.pixelplay.di.AppScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class ModelDownloadState {
    object Idle : ModelDownloadState()

    data class Downloading(
        val modelId: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val progressPercent: Int,
        val speedBytesPerSec: Long
    ) : ModelDownloadState()

    data class Paused(
        val modelId: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val progressPercent: Int
    ) : ModelDownloadState()

    data class Completed(
        val modelId: String,
        val filePath: String
    ) : ModelDownloadState()

    data class Failed(
        val modelId: String,
        val errorMessage: String,
        val canResume: Boolean = true
    ) : ModelDownloadState()
}

@Singleton
class OfflineModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hardwareInspector: DeviceHardwareInspector,
    private val notificationManager: AiNotificationManager,
    baseHttpClient: OkHttpClient,
    @AppScope private val appScope: CoroutineScope
) {
    private val httpClient: OkHttpClient by lazy {
        baseHttpClient.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val _downloadStates = MutableStateFlow<Map<String, ModelDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, ModelDownloadState>> = _downloadStates.asStateFlow()

    init {
        appScope.launch(Dispatchers.IO) {
            refreshExistingDownloads()
        }
    }

    fun refreshExistingDownloads() {
        val modelsDir = OfflineModelCatalog.getModelsDir(context.filesDir)
        val updated = mutableMapOf<String, ModelDownloadState>()

        OfflineModelCatalog.recommendedModels.forEach { model ->
            val targetFile = File(modelsDir, model.fileName)
            val partFile = File(modelsDir, "${model.fileName}.part")
            val currentState = _downloadStates.value[model.id]

            when {
                currentState is ModelDownloadState.Downloading -> {
                    updated[model.id] = currentState
                }
                targetFile.exists() && targetFile.length() > 1024L * 1024L -> {
                    updated[model.id] = ModelDownloadState.Completed(
                        modelId = model.id,
                        filePath = targetFile.absolutePath
                    )
                }
                partFile.exists() && partFile.length() > 0L -> {
                    val downloaded = partFile.length()
                    val total = model.sizeBytes.coerceAtLeast(downloaded)
                    val pct = ((downloaded * 100L) / total).toInt().coerceIn(0, 99)
                    updated[model.id] = ModelDownloadState.Paused(
                        modelId = model.id,
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        progressPercent = pct
                    )
                }
                else -> {
                    updated[model.id] = ModelDownloadState.Idle
                }
            }
        }
        _downloadStates.value = updated
    }

    fun startOrResumeDownload(model: OfflineModelInfo) {
        if (activeJobs[model.id]?.isActive == true) return

        // Pre-check storage space
        val eval = hardwareInspector.evaluateForModel(model, requireDownloadStorage = true)
        if (eval is StaticHardwareEvalResult.Blocked && eval.isStorageError) {
            _downloadStates.update { map ->
                map + (model.id to ModelDownloadState.Failed(
                    modelId = model.id,
                    errorMessage = eval.reason,
                    canResume = false
                ))
            }
            return
        }

        // Start foreground service to keep process alive while downloading
        runCatching {
            ModelDownloadService.start(context, model.id)
        }

        val job = appScope.launch(Dispatchers.IO) {
            executeDownload(model)
        }
        activeJobs[model.id] = job
    }

    fun pauseDownload(modelId: String) {
        val job = activeJobs.remove(modelId)
        job?.cancel()

        val modelsDir = OfflineModelCatalog.getModelsDir(context.filesDir)
        val model = OfflineModelCatalog.recommendedModels.find { it.id == modelId } ?: return
        val partFile = File(modelsDir, "${model.fileName}.part")
        val downloaded = if (partFile.exists()) partFile.length() else 0L
        val total = model.sizeBytes.coerceAtLeast(downloaded).coerceAtLeast(1L)
        val pct = ((downloaded * 100L) / total).toInt().coerceIn(0, 99)

        _downloadStates.update { map ->
            map + (modelId to ModelDownloadState.Paused(
                modelId = modelId,
                downloadedBytes = downloaded,
                totalBytes = total,
                progressPercent = pct
            ))
        }
        notificationManager.hideProgress()
        stopServiceIfNoActiveDownloads()
    }

    fun cancelAndDeleteModel(model: OfflineModelInfo) {
        activeJobs.remove(model.id)?.cancel()
        val modelsDir = OfflineModelCatalog.getModelsDir(context.filesDir)
        val targetFile = model.localFilePath?.let(::File) ?: File(modelsDir, model.fileName)
        val partFile = File(modelsDir, "${model.fileName}.part")
        runCatching { if (targetFile.exists()) targetFile.delete() }
        runCatching { if (partFile.exists()) partFile.delete() }

        _downloadStates.update { map ->
            map + (model.id to ModelDownloadState.Idle)
        }
        notificationManager.hideProgress()
        stopServiceIfNoActiveDownloads()
    }

    fun getDownloadedFile(model: OfflineModelInfo): File? {
        val modelsDir = OfflineModelCatalog.getModelsDir(context.filesDir)
        val file = File(modelsDir, model.fileName)
        return file.takeIf { it.exists() && it.length() > 1024L * 1024L }
    }

    private suspend fun executeDownload(model: OfflineModelInfo) {
        val modelsDir = OfflineModelCatalog.getModelsDir(context.filesDir)
        val targetFile = File(modelsDir, model.fileName)
        val partFile = File(modelsDir, "${model.fileName}.part")

        var existingBytes = if (partFile.exists()) partFile.length() else 0L
        var totalBytes = model.sizeBytes.coerceAtLeast(1L)

        _downloadStates.update { map ->
            map + (model.id to ModelDownloadState.Downloading(
                modelId = model.id,
                downloadedBytes = existingBytes,
                totalBytes = totalBytes,
                progressPercent = ((existingBytes * 100L) / totalBytes).toInt().coerceIn(0, 99),
                speedBytesPerSec = 0L
            ))
        }

        try {
            val requestBuilder = Request.Builder()
                .url(model.downloadUrl)
                .header("User-Agent", "PixelPlayer-Android/1.0")
            if (existingBytes > 0L) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
            }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    // If server rejected Range (e.g. 416), reset partFile
                    if (response.code == 416 && partFile.exists()) {
                        partFile.delete()
                        existingBytes = 0L
                    }
                    throw IllegalStateException("HTTP ${response.code}: Không thể tải mô hình từ máy chủ.")
                }

                val isResumed = response.code == 206 && existingBytes > 0L
                if (!isResumed) {
                    existingBytes = 0L
                }

                val contentLength = response.body.contentLength()
                if (contentLength > 0L) {
                    totalBytes = existingBytes + contentLength
                }

                val bodyStream = response.body.byteStream()

                RandomAccessFile(partFile, "rw").use { raf ->
                    if (isResumed) {
                        raf.seek(existingBytes)
                    } else {
                        raf.setLength(0L)
                        raf.seek(0L)
                    }

                    val buffer = ByteArray(64 * 1024)
                    var downloaded = existingBytes
                    var lastEmitTimeMs = System.currentTimeMillis()
                    var bytesSinceLastEmit = 0L

                    bodyStream.use { input ->
                        while (kotlin.coroutines.coroutineContext.isActive) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            raf.write(buffer, 0, read)
                            downloaded += read
                            bytesSinceLastEmit += read

                            val now = System.currentTimeMillis()
                            val elapsed = now - lastEmitTimeMs
                            if (elapsed >= 400L) {
                                val speed = (bytesSinceLastEmit * 1000L) / elapsed.coerceAtLeast(1L)
                                val pct = ((downloaded * 100L) / totalBytes.coerceAtLeast(1L))
                                    .toInt()
                                    .coerceIn(0, 99)

                                _downloadStates.update { map ->
                                    map + (model.id to ModelDownloadState.Downloading(
                                        modelId = model.id,
                                        downloadedBytes = downloaded,
                                        totalBytes = totalBytes,
                                        progressPercent = pct,
                                        speedBytesPerSec = speed
                                    ))
                                }

                                notificationManager.showProgress(
                                    title = "Đang tải ${model.displayName}",
                                    message = "$pct% (${downloaded / OfflineModelCatalog.MB_BYTES} MB / ${totalBytes / OfflineModelCatalog.MB_BYTES} MB)",
                                    progress = pct,
                                    max = 100
                                )

                                lastEmitTimeMs = now
                                bytesSinceLastEmit = 0L
                            }
                        }
                    }
                }
            }

            // Validate GGUF header before finalizing
            val headerInfo = LlamaContext.inspectGgufFile(partFile)
            if (!headerInfo.isValid) {
                partFile.delete()
                throw IllegalStateException(headerInfo.errorReason ?: "File tải về không đúng định dạng GGUF.")
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            val renamed = partFile.renameTo(targetFile)
            if (!renamed) {
                partFile.copyTo(targetFile, overwrite = true)
                partFile.delete()
            }

            _downloadStates.update { map ->
                map + (model.id to ModelDownloadState.Completed(
                    modelId = model.id,
                    filePath = targetFile.absolutePath
                ))
            }
            notificationManager.showCompletion(
                title = "Đã tải xong ${model.displayName}",
                message = "Mở Quản lý Mô hình AI Offline để chạy Kiểm tra phần cứng & kích hoạt."
            )
        } catch (ce: CancellationException) {
            Timber.tag("ModelDownload").i("Download paused/cancelled for %s", model.id)
        } catch (e: Exception) {
            Timber.tag("ModelDownload").e(e, "Failed downloading %s", model.id)
            _downloadStates.update { map ->
                map + (model.id to ModelDownloadState.Failed(
                    modelId = model.id,
                    errorMessage = e.message ?: "Mất kết nối mạng khi đang tải. Bấm Tiếp tục để tải tiếp.",
                    canResume = true
                ))
            }
            notificationManager.showCompletion(
                title = "Tải mô hình gián đoạn",
                message = "Bạn có thể bấm Tiếp tục (Resume) trong ứng dụng."
            )
        } finally {
            activeJobs.remove(model.id)
            stopServiceIfNoActiveDownloads()
        }
    }

    fun hasActiveDownloads(): Boolean = activeJobs.values.any { it.isActive }

    private fun stopServiceIfNoActiveDownloads() {
        if (!hasActiveDownloads()) {
            runCatching {
                ModelDownloadService.stop(context)
            }
        }
    }
}

/**
 * Foreground Service keeping GGUF model downloads alive when the user switches apps.
 */
@AndroidEntryPoint
class ModelDownloadService : Service() {

    @Inject
    lateinit var downloadManager: OfflineModelDownloadManager

    companion object {
        private const val SERVICE_NOTIFICATION_ID = 1099
        private const val EXTRA_MODEL_ID = "extra_model_id"

        fun start(context: Context, modelId: String) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                putExtra(EXTRA_MODEL_ID, modelId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ModelDownloadService::class.java))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, AiNotificationManager.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Đang tải mô hình AI Offline")
            .setContentText("Quá trình tải hỗ trợ tiếp tục (Resume) nếu gián đoạn.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    SERVICE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(SERVICE_NOTIFICATION_ID, notification)
            }
        }

        if (!downloadManager.hasActiveDownloads()) {
            stopSelf()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
