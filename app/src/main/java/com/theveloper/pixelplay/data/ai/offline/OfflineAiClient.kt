package com.theveloper.pixelplay.data.ai.offline

import com.theveloper.pixelplay.data.ai.provider.AiClient
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineAiClient @Inject constructor(
    private val llamaEngine: LlamaEngine,
    private val preferencesRepo: AiPreferencesRepository
) : AiClient {

    override suspend fun generateContent(
        model: String,
        systemPrompt: String,
        prompt: String,
        temperature: Float,
        topP: Float,
        topK: Int,
        maxTokens: Int,
        presencePenalty: Float,
        frequencyPenalty: Float
    ): String {
        val isStructuredJsonTask = prompt.contains("<candidate_pool>", ignoreCase = true) ||
            systemPrompt.contains("JSON", ignoreCase = true)
        val effectiveTemperature = if (isStructuredJsonTask) {
            temperature.coerceIn(0.1f, 0.3f)
        } else {
            temperature.coerceIn(0.1f, 0.8f)
        }

        return llamaEngine.generate(
            systemPrompt = systemPrompt,
            userPrompt = prompt,
            temperature = effectiveTemperature,
            topP = topP.coerceIn(0.1f, 0.95f),
            topK = topK.coerceIn(10, 64),
            maxTokens = maxTokens.coerceIn(128, 1024),
            presencePenalty = presencePenalty,
            frequencyPenalty = frequencyPenalty
        )
    }

    override suspend fun countTokens(model: String, systemPrompt: String, prompt: String): Int {
        return llamaEngine.countTokens(systemPrompt, prompt)
    }

    override suspend fun getAvailableModels(apiKey: String): List<String> {
        val activeName = preferencesRepo.activeOfflineModelName.first()
        val activePath = preferencesRepo.activeOfflineModelPath.first()
        return buildList {
            if (activeName.isNotBlank() && activePath.isNotBlank() && File(activePath).exists()) {
                add(activeName)
            }
            OfflineModelCatalog.recommendedModels.forEach { info ->
                if (info.displayName !in this) {
                    add(info.displayName)
                }
            }
        }
    }

    override suspend fun validateApiKey(apiKey: String): Boolean {
        val path = preferencesRepo.activeOfflineModelPath.first()
        val passed = preferencesRepo.offlineHardwarePassed.first()
        return passed && path.isNotBlank() && File(path).exists()
    }

    override fun getDefaultModel(): String {
        return "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
    }
}
