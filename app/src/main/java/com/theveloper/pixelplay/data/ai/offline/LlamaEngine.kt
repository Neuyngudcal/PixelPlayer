package com.theveloper.pixelplay.data.ai.offline

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import com.theveloper.pixelplay.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlamaEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepo: AiPreferencesRepository,
    @AppScope private val appScope: CoroutineScope
) : ComponentCallbacks2 {

    companion object {
        const val AUTO_UNLOAD_DELAY_MS = 3 * 60 * 1000L // 3 minutes as specified in the plan
        const val DEFAULT_CONTEXT_SIZE = 2048

        /**
         * GBNF grammar forcing output to be a valid JSON array of string IDs: ["id1", "id2", ...]
         */
        val PLAYLIST_JSON_ARRAY_GRAMMAR = """
            root ::= "[" ws string (ws "," ws string)* ws "]"
            string ::= "\"" ([^"\\] | "\\" .)* "\""
            ws ::= [ \t\n\r]*
        """.trimIndent()
    }

    private val engineMutex = Mutex()
    private val llamaContext by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { LlamaContext() }
    private var autoUnloadJob: Job? = null
    @Volatile
    private var isGeneratingNow: Boolean = false
    @Volatile
    private var isCallbacksRegistered: Boolean = false

    private val _isModelLoadedInRam = MutableStateFlow(false)
    val isModelLoadedInRam: StateFlow<Boolean> = _isModelLoadedInRam.asStateFlow()

    private val _loadedModelPath = MutableStateFlow<String?>(null)
    val loadedModelPath: StateFlow<String?> = _loadedModelPath.asStateFlow()

    private fun ensureComponentCallbacksRegistered() {
        if (!isCallbacksRegistered) {
            runCatching {
                context.applicationContext.registerComponentCallbacks(this)
                isCallbacksRegistered = true
            }
        }
    }

    /**
     * Ensures the active offline model is loaded into RAM, then runs generation on Dispatchers.IO.
     * Automatically schedules the 3-minute auto-unload timer after completion.
     */
    suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        temperature: Float = 0.2f,
        topP: Float = 0.85f,
        topK: Int = 40,
        maxTokens: Int = 512,
        presencePenalty: Float = 0.0f,
        frequencyPenalty: Float = 0.0f,
        explicitModelPath: String? = null
    ): String = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            autoUnloadJob?.cancel()
            isGeneratingNow = true
            try {
                val activePath = explicitModelPath?.takeIf { it.isNotBlank() }
                    ?: preferencesRepo.activeOfflineModelPath.first()
                val hardwarePassed = preferencesRepo.offlineHardwarePassed.first()

                if (activePath.isBlank()) {
                    throw IllegalStateException(
                        "Chưa chọn mô hình AI Offline. Vui lòng vào Cài đặt AI → Quản lý Mô hình AI Offline để tải hoặc chọn file .gguf."
                    )
                }
                if (!hardwarePassed) {
                    throw IllegalStateException(
                        "Mô hình AI Offline hiện tại chưa vượt qua bước Kiểm tra yêu cầu phần cứng (Dry-run). Vui lòng kiểm tra phần cứng trước khi sử dụng."
                    )
                }

                val modelFile = File(activePath)
                if (!modelFile.exists() || !modelFile.isFile) {
                    throw IllegalStateException(
                        "Không tìm thấy file mô hình GGUF tại: ${modelFile.name}. Vui lòng tải lại hoặc chọn lại mô hình."
                    )
                }

                val threads = preferencesRepo.offlineThreadsCount.first().coerceIn(1, 8)
                val useGpu = preferencesRepo.offlineUseGpu.first()

                if (!_isModelLoadedInRam.value || !llamaContext.isLoaded || llamaContext.currentModelPath != modelFile.absolutePath) {
                    ensureComponentCallbacksRegistered()
                    val params = LlamaParams(
                        contextSize = DEFAULT_CONTEXT_SIZE,
                        batchSize = 512,
                        threadsCount = threads,
                        useGpu = useGpu,
                        useMmap = true,
                        useMlock = false
                    )
                    llamaContext.loadModel(modelFile.absolutePath, params)
                    _isModelLoadedInRam.value = true
                    _loadedModelPath.value = modelFile.absolutePath
                }

                val formattedPrompt = formatChatPrompt(
                    modelName = modelFile.name,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt
                )

                val isPlaylistRequest = userPrompt.contains("<candidate_pool>", ignoreCase = true) ||
                    systemPrompt.contains("JSON array", ignoreCase = true)

                val effectiveMaxTokens = if (isPlaylistRequest) {
                    maxTokens.coerceIn(128, 512)
                } else {
                    maxTokens.coerceIn(128, 1024)
                }

                val rawOutput = llamaContext.completion(
                    prompt = formattedPrompt,
                    maxTokens = effectiveMaxTokens,
                    temp = temperature.coerceIn(0.05f, 1.2f),
                    topP = topP.coerceIn(0.1f, 1.0f),
                    topK = topK.coerceIn(1, 100),
                    presencePenalty = presencePenalty,
                    frequencyPenalty = frequencyPenalty,
                    jsonGrammar = if (isPlaylistRequest) PLAYLIST_JSON_ARRAY_GRAMMAR else null
                )

                rawOutput.trim()
            } finally {
                isGeneratingNow = false
                scheduleAutoUnloadLocked()
            }
        }
    }

    suspend fun countTokens(systemPrompt: String, userPrompt: String): Int = withContext(Dispatchers.IO) {
        val combined = "$systemPrompt\n$userPrompt"
        if (_isModelLoadedInRam.value && llamaContext.isLoaded) {
            llamaContext.countTokens(combined)
        } else {
            (combined.length / 4).coerceAtLeast(1)
        }
    }

    /**
     * Schedules automatic unloading of the model after [delayMs] (default 3 minutes)
     * to return ~1.5GB RAM to the OS and music player.
     */
    fun scheduleAutoUnload(delayMs: Long = AUTO_UNLOAD_DELAY_MS) {
        scheduleAutoUnloadLocked(delayMs)
    }

    private fun scheduleAutoUnloadLocked(delayMs: Long = AUTO_UNLOAD_DELAY_MS) {
        autoUnloadJob?.cancel()
        autoUnloadJob = appScope.launch(Dispatchers.IO) {
            delay(delayMs)
            unload()
        }
    }

    /**
     * Immediately unloads the GGUF model from native memory and triggers GC cleanup.
     */
    suspend fun unload() = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            autoUnloadJob?.cancel()
            autoUnloadJob = null
            if (_isModelLoadedInRam.value && llamaContext.isLoaded) {
                llamaContext.unloadModel()
                _isModelLoadedInRam.value = false
                _loadedModelPath.value = null
                System.gc()
                Timber.tag("LlamaEngine").i("Offline LLM unloaded from RAM to free memory for playback.")
            }
        }
    }

    /**
     * Formats system + user prompt according to the model architecture (ChatML for Qwen, Llama-3 template, or Gemma-2 template).
     */
    internal fun formatChatPrompt(
        modelName: String,
        systemPrompt: String,
        userPrompt: String
    ): String {
        val lower = modelName.lowercase()
        return when {
            lower.contains("llama-3") || lower.contains("llama3") -> {
                buildString {
                    append("<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n")
                    append(systemPrompt.trim())
                    append("<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n")
                    append(userPrompt.trim())
                    append("<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n")
                }
            }
            lower.contains("gemma") -> {
                buildString {
                    append("<bos><start_of_turn>user\n")
                    append(systemPrompt.trim())
                    append("\n\n")
                    append(userPrompt.trim())
                    append("<end_of_turn>\n<start_of_turn>model\n")
                }
            }
            else -> {
                // Default to ChatML (Qwen 2.5, DeepSeek, SmolLM, etc.)
                buildString {
                    append("<|im_start|>system\n")
                    append(systemPrompt.trim())
                    append("<|im_end|>\n<|im_start|>user\n")
                    append(userPrompt.trim())
                    append("<|im_end|>\n<|im_start|>assistant\n")
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        val isCritical = level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        if (isCritical && !isGeneratingNow && _isModelLoadedInRam.value) {
            Timber.tag("LlamaEngine").w("System memory pressure detected (level=%d). Auto-unloading offline LLM.", level)
            appScope.launch(Dispatchers.IO) {
                unload()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        if (!isGeneratingNow && _isModelLoadedInRam.value) {
            appScope.launch(Dispatchers.IO) {
                unload()
            }
        }
    }
}
