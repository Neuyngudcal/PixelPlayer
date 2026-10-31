package com.theveloper.pixelplay.data.ai.offline

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

sealed class CustomImportState {
    object Idle : CustomImportState()
    data class Validating(val fileName: String) : CustomImportState()
    data class Copying(
        val fileName: String,
        val copiedBytes: Long,
        val totalBytes: Long,
        val progressPercent: Int
    ) : CustomImportState()
    data class Completed(val model: OfflineModelInfo) : CustomImportState()
    data class Failed(val errorMessage: String) : CustomImportState()
}

@Singleton
class CustomModelImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hardwareInspector: DeviceHardwareInspector
) {

    /**
     * Lists all custom .gguf files imported into context.filesDir/ai_models/custom/
     */
    suspend fun listCustomModels(): List<OfflineModelInfo> = withContext(Dispatchers.IO) {
        val customDir = OfflineModelCatalog.getCustomModelsDir(context.filesDir)
        customDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("gguf", ignoreCase = true) && it.length() > 24L }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file -> OfflineModelCatalog.createCustomModelInfo(file) }
            .orEmpty()
    }

    suspend fun deleteCustomModel(model: OfflineModelInfo): Boolean = withContext(Dispatchers.IO) {
        val path = model.localFilePath ?: return@withContext false
        val file = File(path)
        runCatching {
            if (file.exists()) file.delete() else false
        }.getOrDefault(false)
    }

    /**
     * Validates a SAF Uri for .gguf extension, GGUF magic header, and free storage,
     * then copies it safely to context.filesDir/ai_models/custom/ so Llama.cpp gets a POSIX path.
     */
    fun importGgufFromUri(uri: Uri): Flow<CustomImportState> = flow {
        val meta = resolveUriMetadata(uri)
        val rawFileName = meta.first.ifBlank { "custom_model.gguf" }
        val estimatedSize = meta.second

        emit(CustomImportState.Validating(rawFileName))

        if (!rawFileName.lowercase().endsWith(".gguf")) {
            emit(
                CustomImportState.Failed(
                    "Tệp đã chọn ('$rawFileName') không có phần mở rộng .gguf. Vui lòng chọn tệp mô hình định dạng .gguf."
                )
            )
            return@flow
        }

        // Verify GGUF magic header directly from the SAF input stream before copying gigabytes of data
        val headerValid = validateStreamGgufHeader(uri)
        if (!headerValid.first) {
            emit(CustomImportState.Failed(headerValid.second ?: "Tệp không phải định dạng GGUF hợp lệ."))
            return@flow
        }

        // Check available device storage (requires FileSize + 1.5 GB free)
        val snapshot = hardwareInspector.inspect()
        val requiredFreeBytes = if (estimatedSize > 0L) {
            estimatedSize + (1.5 * OfflineModelCatalog.GB_BYTES).toLong()
        } else {
            (2.0 * OfflineModelCatalog.GB_BYTES).toLong()
        }

        if (snapshot.freeStorageBytes < requiredFreeBytes) {
            val reqGb = String.format("%.1f", requiredFreeBytes.toFloat() / OfflineModelCatalog.GB_BYTES)
            val freeGb = String.format("%.1f", snapshot.freeStorageGb)
            emit(
                CustomImportState.Failed(
                    "Không đủ bộ nhớ trống để sao chép mô hình: Cần tối thiểu $reqGb GB trống (Hiện còn $freeGb GB)."
                )
            )
            return@flow
        }

        val customDir = OfflineModelCatalog.getCustomModelsDir(context.filesDir)
        val sanitizedName = rawFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val destFile = File(customDir, sanitizedName)
        val tempFile = File(customDir, "$sanitizedName.tmp")

        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Không thể mở quyền đọc tệp từ bộ nhớ máy.")

            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var copied = 0L
                    var lastEmitMs = System.currentTimeMillis()
                    val total = estimatedSize.coerceAtLeast(1L)

                    while (kotlin.coroutines.coroutineContext.isActive) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        copied += read

                        val now = System.currentTimeMillis()
                        if (now - lastEmitMs >= 250L) {
                            val pct = if (estimatedSize > 0L) {
                                ((copied * 100L) / total).toInt().coerceIn(0, 99)
                            } else {
                                50
                            }
                            emit(
                                CustomImportState.Copying(
                                    fileName = rawFileName,
                                    copiedBytes = copied,
                                    totalBytes = estimatedSize.coerceAtLeast(copied),
                                    progressPercent = pct
                                )
                            )
                            lastEmitMs = now
                        }
                    }
                    output.flush()
                }
            }

            val finalHeader = LlamaContext.inspectGgufFile(tempFile)
            if (!finalHeader.isValid) {
                tempFile.delete()
                emit(CustomImportState.Failed(finalHeader.errorReason ?: "File GGUF sau khi sao chép không hợp lệ."))
                return@flow
            }

            if (destFile.exists()) {
                destFile.delete()
            }
            if (!tempFile.renameTo(destFile)) {
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
            }

            val modelInfo = OfflineModelCatalog.createCustomModelInfo(destFile)
            emit(CustomImportState.Completed(modelInfo))
        } catch (e: Exception) {
            Timber.tag("CustomModelImporter").e(e, "Failed importing custom GGUF from %s", uri)
            runCatching { if (tempFile.exists()) tempFile.delete() }
            emit(
                CustomImportState.Failed(
                    "Lỗi sao chép file GGUF: ${e.message ?: "Không xác định"}"
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    private fun resolveUriMetadata(uri: Uri): Pair<String, Long> {
        var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "custom_model.gguf"
        var sizeBytes = 0L
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                        displayName = cursor.getString(nameIdx)
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }
        }
        return displayName to sizeBytes
    }

    private fun validateStreamGgufHeader(uri: Uri): Pair<Boolean, String?> {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(8)
                val read = stream.read(header)
                if (read < 8) {
                    return false to "Tệp quá nhỏ hoặc rỗng."
                }
                val isGguf = header[0] == 0x47.toByte() &&
                    header[1] == 0x47.toByte() &&
                    header[2] == 0x55.toByte() &&
                    header[3] == 0x46.toByte()
                if (!isGguf) {
                    return false to "Tệp không có chữ ký nhị phân 'GGUF' ở đầu file."
                }
                val version = ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                if (version !in 1..3) {
                    return false to "Phiên bản GGUF v$version không tương thích (Chỉ hỗ trợ GGUF v2/v3)."
                }
                true to null
            } ?: (false to "Không thể mở tệp đã chọn.")
        }.getOrElse { e ->
            false to "Lỗi kiểm tra header GGUF: ${e.message}"
        }
    }
}
