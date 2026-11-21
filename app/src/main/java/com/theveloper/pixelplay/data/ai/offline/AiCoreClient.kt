package com.theveloper.pixelplay.data.ai.offline

import com.theveloper.pixelplay.data.ai.provider.AiClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AiClient] implementation backed by Google Android AICore (Gemini Nano running on NPU/TPU).
 */
@Singleton
class AiCoreClient @Inject constructor(
    private val aiCoreEngine: AndroidAiCoreEngine
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

        return aiCoreEngine.generateContent(
            systemPrompt = systemPrompt,
            userPrompt = prompt,
            temperature = effectiveTemperature,
            topK = topK.coerceIn(1, 64),
            maxTokens = maxTokens.coerceIn(128, 1024)
        )
    }

    override suspend fun countTokens(model: String, systemPrompt: String, prompt: String): Int {
        return aiCoreEngine.countTokens(systemPrompt, prompt)
    }

    override suspend fun getAvailableModels(apiKey: String): List<String> {
        val currentName = aiCoreEngine.modelInfo.value.baseModelName.trim()
        return listOf(currentName.ifBlank { getDefaultModel() })
    }

    override suspend fun validateApiKey(apiKey: String): Boolean {
        val info = aiCoreEngine.checkDeviceSupportAndModelInfo()
        return info.availability == AiCoreAvailabilityStatus.AVAILABLE
    }

    override fun getDefaultModel(): String {
        return aiCoreEngine.modelInfo.value.baseModelName.ifBlank { "Gemini Nano (Android AICore)" }
    }
}
