package com.theveloper.pixelplay.data.ai.offline

import java.io.File
import kotlin.math.max

enum class OfflineModelTier(val labelVi: String) {
    LITE("Siêu nhẹ (Lite)"),
    STANDARD("Khuyên dùng (Standard)"),
    PRO("Chất lượng cao (Pro)"),
    CUSTOM("Tự chọn (.gguf)")
}

data class OfflineModelInfo(
    val id: String,
    val displayName: String,
    val description: String,
    val tier: OfflineModelTier,
    val downloadUrl: String,
    val fileName: String,
    val sizeBytes: Long,
    val minRamGb: Float,
    val recommendedRamGb: Float,
    val requiredFreeStorageBytes: Long,
    val minChipsetHint: String,
    val isCustom: Boolean = false,
    val localFilePath: String? = null,
    val isDownloaded: Boolean = false
) {
    val estimatedRuntimeRamGb: Float
        get() = ((sizeBytes.toDouble() / OfflineModelCatalog.GB_BYTES.toDouble()) * 1.4).toFloat()
            .coerceAtLeast(1.0f)
}

object OfflineModelCatalog {
    const val GB_BYTES = 1024L * 1024L * 1024L
    const val MB_BYTES = 1024L * 1024L
    const val MODELS_DIR_NAME = "ai_models"
    const val CUSTOM_MODELS_SUBDIR = "custom"

    val recommendedModels: List<OfflineModelInfo> = listOf(
        OfflineModelInfo(
            id = "qwen2.5-1.5b-instruct-q4_k_m",
            displayName = "Qwen 2.5 1.5B (Khuyên dùng)",
            description = "Hiểu sâu tiếng Việt & V-Pop, xuất JSON chuẩn xác, chạy mượt trên máy từ 4GB - 6GB RAM.",
            tier = OfflineModelTier.STANDARD,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            fileName = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
            sizeBytes = 1_120L * MB_BYTES,
            minRamGb = 4.0f,
            recommendedRamGb = 6.0f,
            requiredFreeStorageBytes = (3.0 * GB_BYTES).toLong(),
            minChipsetHint = "Snapdragon 695 / Dimensity 6080"
        ),
        OfflineModelInfo(
            id = "llama-3.2-1b-instruct-q4_k_m",
            displayName = "Llama 3.2 1B (Bản siêu nhẹ)",
            description = "Dung lượng nhỏ gọn (~750 MB), tốc độ sinh playlist cực nhanh cho máy cấu hình thấp.",
            tier = OfflineModelTier.LITE,
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            sizeBytes = 770L * MB_BYTES,
            minRamGb = 3.5f,
            recommendedRamGb = 4.0f,
            requiredFreeStorageBytes = (2.5 * GB_BYTES).toLong(),
            minChipsetHint = "8 cores (Snapdragon 680 / Helio G88)"
        ),
        OfflineModelInfo(
            id = "qwen2.5-3b-instruct-q4_k_m",
            displayName = "Qwen 2.5 3B (Chất lượng cao)",
            description = "Cảm thụ tâm trạng và gu âm nhạc sâu sắc hơn, yêu cầu máy từ 6GB - 8GB RAM trở lên.",
            tier = OfflineModelTier.PRO,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
            fileName = "Qwen2.5-3B-Instruct-Q4_K_M.gguf",
            sizeBytes = 2_100L * MB_BYTES,
            minRamGb = 6.0f,
            recommendedRamGb = 8.0f,
            requiredFreeStorageBytes = (4.5 * GB_BYTES).toLong(),
            minChipsetHint = "Snapdragon 778G / Dimensity 7050+"
        )
    )

    /**
     * Builds hardware criteria dynamically for a user-supplied .gguf file:
     * - Min RAM = FileSize * 1.6 (at least 3.5 GB)
     * - Recommended RAM = FileSize * 2.2 (at least 4.0 GB)
     * - Free Storage = max(FileSize * 1.5, FileSize + 1.5 GB)
     */
    fun createCustomModelInfo(
        file: File,
        customDisplayName: String? = null
    ): OfflineModelInfo {
        val fileSizeBytes = file.length().coerceAtLeast(1L)
        val fileSizeGb = fileSizeBytes.toFloat() / GB_BYTES.toFloat()
        val minRamGb = max(3.5f, fileSizeGb * 1.6f)
        val recommendedRamGb = max(4.0f, fileSizeGb * 2.2f)
        val requiredFreeStorageBytes = max(
            (fileSizeBytes * 1.5).toLong(),
            fileSizeBytes + (1.5 * GB_BYTES).toLong()
        )
        val cleanName = customDisplayName ?: file.nameWithoutExtension
            .replace('-', ' ')
            .replace('_', ' ')

        return OfflineModelInfo(
            id = "custom_${file.name.lowercase()}",
            displayName = cleanName,
            description = "Mô hình GGUF do người dùng tự chọn (${file.name}).",
            tier = OfflineModelTier.CUSTOM,
            downloadUrl = "",
            fileName = file.name,
            sizeBytes = fileSizeBytes,
            minRamGb = minRamGb,
            recommendedRamGb = recommendedRamGb,
            requiredFreeStorageBytes = requiredFreeStorageBytes,
            minChipsetHint = if (fileSizeGb > 2.5f) "Snapdragon 8 Gen 1+ / >= 8GB RAM" else "64-bit Octa-core CPU",
            isCustom = true,
            localFilePath = file.absolutePath,
            isDownloaded = file.exists() && file.length() > 0L
        )
    }

    fun getModelsDir(filesDir: File): File {
        return File(filesDir, MODELS_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    fun getCustomModelsDir(filesDir: File): File {
        return File(getModelsDir(filesDir), CUSTOM_MODELS_SUBDIR).apply {
            if (!exists()) mkdirs()
        }
    }
}
