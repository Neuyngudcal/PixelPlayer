package com.theveloper.pixelplay.data.ai.offline

import android.net.Uri
import android.os.ParcelFileDescriptor
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

data class LlamaParams(
    val contextSize: Int = 2048,
    val batchSize: Int = 512,
    val threadsCount: Int = 4,
    val useGpu: Boolean = false,
    val useMmap: Boolean = true,
    val useMlock: Boolean = false
)

data class GgufHeaderInfo(
    val isValid: Boolean,
    val version: Int = 0,
    val tensorCount: Long = 0L,
    val metadataKvCount: Long = 0L,
    val errorReason: String? = null
)

/**
 * JNI Wrapper over Llama.cpp Android runtime (libllama / librnllama).
 * Supports GGUF v2/v3 models with ARM64 NEON/FP16/DotProd and optional GPU offload.
 */
class LlamaContext {

    companion object {
        private val contextIdGenerator = AtomicInteger(1)
        private val GGUF_MAGIC = byteArrayOf(0x47, 0x47, 0x55, 0x46) // "GGUF"

        /**
         * Reads and validates the binary GGUF header (magic "GGUF" + version 2 or 3 + tensor count).
         */
        fun inspectGgufFile(file: File): GgufHeaderInfo {
            if (!file.exists() || !file.isFile) {
                return GgufHeaderInfo(isValid = false, errorReason = "File không tồn tại: ${file.name}")
            }
            if (file.length() < 24L) {
                return GgufHeaderInfo(isValid = false, errorReason = "File quá nhỏ, không phải định dạng GGUF hợp lệ.")
            }

            return runCatching {
                FileInputStream(file).use { fis ->
                    val headerBytes = ByteArray(24)
                    val read = fis.read(headerBytes)
                    if (read < 24) {
                        return GgufHeaderInfo(isValid = false, errorReason = "Không thể đọc header của file GGUF.")
                    }
                    val magic = headerBytes.sliceArray(0..3)
                    if (!magic.contentEquals(GGUF_MAGIC)) {
                        return GgufHeaderInfo(
                            isValid = false,
                            errorReason = "Sai chữ ký tệp (Magic Bytes không phải 'GGUF')."
                        )
                    }
                    val buffer = ByteBuffer.wrap(headerBytes, 4, 20).order(ByteOrder.LITTLE_ENDIAN)
                    val version = buffer.int
                    val tensorCount = buffer.long
                    val kvCount = buffer.long

                    if (version !in 1..3) {
                        return GgufHeaderInfo(
                            isValid = false,
                            version = version,
                            errorReason = "Phiên bản GGUF v$version không được hỗ trợ (Yêu cầu GGUF v2 hoặc v3)."
                        )
                    }

                    GgufHeaderInfo(
                        isValid = true,
                        version = version,
                        tensorCount = tensorCount,
                        metadataKvCount = kvCount
                    )
                }
            }.getOrElse { error ->
                GgufHeaderInfo(isValid = false, errorReason = "Lỗi đọc file GGUF: ${error.message}")
            }
        }
    }

    private var nativeDelegate: org.nehuatl.llamacpp.LlamaContext? = null
    private var contextPointer: Long = 0L
    private var loadedModelPath: String? = null
    private var activeParams: LlamaParams = LlamaParams()

    val isLoaded: Boolean
        get() = contextPointer != 0L && nativeDelegate != null

    val currentModelPath: String?
        get() = loadedModelPath

    /**
     * Loads a GGUF model into native memory and returns the native context pointer.
     */
    fun loadModel(modelPath: String, params: LlamaParams = LlamaParams()): Long {
        unloadModel()

        val modelFile = File(modelPath)
        val header = inspectGgufFile(modelFile)
        if (!header.isValid) {
            throw IllegalArgumentException(header.errorReason ?: "File GGUF không hợp lệ")
        }

        val pfd = ParcelFileDescriptor.open(modelFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val detachedFd = pfd.detachFd()

        val id = contextIdGenerator.getAndIncrement()
        val config = mapOf<String, Any>(
            "model" to Uri.fromFile(modelFile).toString(),
            "model_fd" to detachedFd,
            "n_ctx" to params.contextSize,
            "n_batch" to params.batchSize,
            "n_threads" to params.threadsCount.coerceIn(1, 8),
            "n_gpu_layers" to if (params.useGpu) 16 else 0,
            "use_mmap" to params.useMmap,
            "use_mlock" to params.useMlock,
            "embedding" to false,
            "vocab_only" to false,
            "lora" to "",
            "lora_scaled" to 1.0,
            "rope_freq_base" to 0.0,
            "rope_freq_scale" to 0.0
        )

        val delegate = org.nehuatl.llamacpp.LlamaContext(id, config)
        if (delegate.context == 0L) {
            throw IllegalStateException("Không thể khởi tạo bộ nhớ Llama.cpp cho mô hình ${modelFile.name}")
        }

        nativeDelegate = delegate
        contextPointer = delegate.context
        loadedModelPath = modelPath
        activeParams = params
        Timber.tag("LlamaContext").i(
            "Loaded GGUF v%d model '%s' (ptr=%d, ctx=%d, threads=%d, gpu=%b)",
            header.version,
            modelFile.name,
            contextPointer,
            params.contextSize,
            params.threadsCount,
            params.useGpu
        )
        return contextPointer
    }

    /**
     * Runs text completion using the loaded GGUF model.
     */
    fun completion(
        prompt: String,
        maxTokens: Int = 512,
        temp: Float = 0.2f,
        topP: Float = 0.85f,
        topK: Int = 40,
        presencePenalty: Float = 0.0f,
        frequencyPenalty: Float = 0.0f,
        jsonGrammar: String? = null
    ): String {
        val delegate = nativeDelegate
            ?: throw IllegalStateException("Mô hình AI Offline chưa được nạp vào bộ nhớ.")

        val streamedBuilder = StringBuilder()
        delegate.setTokenCallback { token ->
            streamedBuilder.append(token)
        }

        val completionParams = mutableMapOf<String, Any>(
            "prompt" to prompt,
            "temperature" to temp.toDouble(),
            "n_threads" to activeParams.threadsCount.coerceIn(1, 8),
            "n_predict" to maxTokens.coerceIn(1, activeParams.contextSize),
            "top_k" to topK,
            "top_p" to topP.toDouble(),
            "penalty_present" to presencePenalty.toDouble(),
            "penalty_freq" to frequencyPenalty.toDouble(),
            "emit_partial_completion" to true,
            "stop" to listOf(
                "<|im_end|>",
                "<|endoftext|>",
                "<|eot_id|>",
                "<end_of_turn>",
                "</s>"
            )
        )
        if (!jsonGrammar.isNullOrBlank()) {
            completionParams["grammar"] = jsonGrammar
        }

        val resultMap = delegate.completion(completionParams)
        val mapText = (resultMap["text"] as? String)
            ?: (resultMap["content"] as? String)
            ?: ""

        return if (mapText.isNotBlank()) mapText else streamedBuilder.toString()
    }

    /**
     * Runs a short dry-run completion (default 16 tokens on "Hello") and returns tokens/second.
     */
    fun benchmark(testTokens: Int = 16): Float {
        val delegate = nativeDelegate
            ?: throw IllegalStateException("Mô hình chưa được nạp để chạy benchmark.")

        val targetTokens = testTokens.coerceIn(8, 32)
        var generatedTokensCount = 0
        delegate.setTokenCallback {
            generatedTokensCount++
        }

        val elapsedMs = measureTimeMillis {
            val params = mapOf<String, Any>(
                "prompt" to "Hello",
                "temperature" to 0.1,
                "n_threads" to activeParams.threadsCount.coerceIn(1, 8),
                "n_predict" to targetTokens,
                "top_k" to 20,
                "top_p" to 0.8,
                "emit_partial_completion" to true
            )
            val result = delegate.completion(params)
            if (generatedTokensCount == 0) {
                val tokensPredicted = (result["tokens_predicted"] as? Number)?.toInt()
                    ?: (result["n_predicted"] as? Number)?.toInt()
                    ?: ((result["text"] as? String)?.length?.div(4)?.coerceAtLeast(1) ?: targetTokens)
                generatedTokensCount = tokensPredicted.coerceAtLeast(1)
            }
        }.coerceAtLeast(1L)

        val actualTokens = generatedTokensCount.coerceAtLeast(1)
        val tokensPerSec = (actualTokens * 1000f) / elapsedMs.toFloat()
        Timber.tag("LlamaContext").i(
            "Dry-run benchmark generated %d tokens in %d ms (%.2f tokens/s)",
            actualTokens,
            elapsedMs,
            tokensPerSec
        )
        return tokensPerSec
    }

    fun countTokens(text: String): Int {
        val delegate = nativeDelegate ?: return (text.length / 4).coerceAtLeast(1)
        return runCatching {
            delegate.tokenize(text).size
        }.getOrElse { (text.length / 4).coerceAtLeast(1) }
    }

    /**
     * Releases all native memory held by Llama.cpp context.
     */
    fun unloadModel() {
        val delegate = nativeDelegate
        nativeDelegate = null
        contextPointer = 0L
        val oldPath = loadedModelPath
        loadedModelPath = null
        if (delegate != null) {
            runCatching {
                delegate.stopCompletion()
            }
            runCatching {
                delegate.release()
                Timber.tag("LlamaContext").i("Unloaded offline GGUF model: %s", oldPath)
            }.onFailure { error ->
                Timber.tag("LlamaContext").w(error, "Error while releasing native llama context")
            }
        }
    }
}
