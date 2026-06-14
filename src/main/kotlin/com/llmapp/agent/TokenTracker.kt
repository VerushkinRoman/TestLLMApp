package com.llmapp.agent

import com.llmapp.model.ModelPricing
import com.llmapp.model.TokenStats
import com.llmapp.model.TokenUsage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TokenTracker {
    private val _stats = MutableStateFlow(TokenStats())
    val stats: StateFlow<TokenStats> = _stats.asStateFlow()

    private val _historySnapshots = MutableStateFlow<List<TokenSnapshot>>(emptyList())
    val historySnapshots: StateFlow<List<TokenSnapshot>> = _historySnapshots.asStateFlow()

    private var currentModelId: String = ""
    private var contextWindowSize: Int = getContextWindowForModel(currentModelId)

    private fun getContextWindowForModel(modelId: String): Int {
        return when {
            // NVIDIA Nemotron серия - 1M контекста
            modelId.contains("nemotron-3-super") -> 1_000_000
            modelId.contains("nemotron-3-ultra") -> 1_000_000
            modelId.contains("nemotron-3-nano-30b") -> 256_000
            modelId.contains("nemotron-3-nano-omni") -> 300_000
            modelId.contains("nemotron-nano-9b") -> 128_000
            modelId.contains("nemotron-nano-12b") -> 128_000

            // OpenAI GPT-OSS серия - 131K контекста
            modelId.contains("gpt-oss") -> 131_072

            // Google Gemma серия - 256K контекста
            modelId.contains("gemma-4") -> 256_000

            // Poolside серия - 128K контекста
            modelId.contains("laguna") -> 128_000

            // Owl Alpha - 1.05M контекста
            modelId.contains("owl-alpha") -> 1_050_000

            // GLM 4.5 Air - 131K контекста
            modelId.contains("glm-4.5") -> 131_072

            // Kimi K2.6 - 128K контекста
            modelId.contains("kimi") -> 128_000

            // По умолчанию - 128K
            else -> 128_000
        }
    }

    fun updateModel(modelId: String) {
        currentModelId = modelId
        contextWindowSize = getContextWindowForModel(modelId)
    }

    fun trackRequest(usage: TokenUsage, requestNumber: Int): TokenStats {
        val pricing = ModelPricing.getPricing(currentModelId)
        val newStats = _stats.value.addUsage(
            usage,
            pricing.perPromptToken,
            pricing.perCompletionToken
        )
        _stats.value = newStats

        val snapshot = TokenSnapshot(
            requestNumber = requestNumber,
            promptTokens = usage.promptTokens,
            completionTokens = usage.completionTokens,
            totalTokens = usage.totalTokens,
            cumulativeTokens = newStats.totalTokens,
            cumulativeCost = newStats.estimatedCostUsd,
            contextUsagePercent = (newStats.totalTokens.toDouble() / contextWindowSize) * 100,
            timestamp = System.currentTimeMillis(),
            contextWindowSize = contextWindowSize
        )

        _historySnapshots.value += snapshot

        return newStats
    }

    fun checkContextLimit(): ContextStatus {
        val currentTokens = _stats.value.totalTokens
        return when {
            currentTokens >= contextWindowSize -> ContextStatus.OVERFLOW
            currentTokens > contextWindowSize * 0.9 -> ContextStatus.CRITICAL
            currentTokens > contextWindowSize * 0.7 -> ContextStatus.WARNING
            else -> ContextStatus.SAFE
        }
    }

    fun getWarningMessage(): String {
        val status = checkContextLimit()
        val currentTokens = _stats.value.totalTokens

        return when (status) {
            ContextStatus.SAFE -> "✅ Контекст в порядке: ${currentTokens}/${contextWindowSize} токенов (${
                "%.1f".format(currentTokens.toDouble() / contextWindowSize * 100)
            }%)"

            ContextStatus.WARNING -> "⚠️ ВНИМАНИЕ: Контекст заполнен на 70%+ (${currentTokens}/${contextWindowSize} = ${
                "%.1f".format(currentTokens.toDouble() / contextWindowSize * 100)
            }%)"

            ContextStatus.CRITICAL -> "🔴 КРИТИЧЕСКИ: Контекст почти полон! (${currentTokens}/${contextWindowSize} = ${
                "%.1f".format(currentTokens.toDouble() / contextWindowSize * 100)
            }%)"

            ContextStatus.OVERFLOW -> "💥 ПЕРЕПОЛНЕНИЕ! Контекст превышен на ${currentTokens - contextWindowSize} токенов (${currentTokens}/${contextWindowSize})"
        }
    }

    fun reset() {
        _stats.value = TokenStats()
        _historySnapshots.value = emptyList()
    }
}

enum class ContextStatus {
    SAFE, WARNING, CRITICAL, OVERFLOW
}

data class TokenSnapshot(
    val requestNumber: Int,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val cumulativeTokens: Int,
    val cumulativeCost: Double,
    val contextUsagePercent: Double,
    val timestamp: Long,
    val contextWindowSize: Int
) {
    fun getFormattedCost(): String = "$${"%.6f".format(cumulativeCost)}"
}
