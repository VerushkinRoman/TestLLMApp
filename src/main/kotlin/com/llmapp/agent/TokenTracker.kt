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

    private var contextWindowSize: Int = 128_000
    private var currentModel: String = "mistral/mistral-large-latest"

    fun updateModel(modelId: String) {
        currentModel = modelId
        contextWindowSize = 128_000
    }

    fun trackRequest(usage: TokenUsage, requestNumber: Int): TokenStats {
        val inputCost =
            (usage.promptTokens.toDouble() / 1_000_000) * ModelPricing.getInputPrice(currentModel)
        val outputCost =
            (usage.completionTokens.toDouble() / 1_000_000) * ModelPricing.getOutputPrice(
                currentModel
            )
        val requestCost = inputCost + outputCost
        val newStats = _stats.value.addUsageWithCost(usage, requestCost)
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
)
