package com.llmapp.model

import kotlinx.serialization.Serializable

@Serializable
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

object ModelPricing {
    data class PricePair(val input: Double, val output: Double)

    private val priceMap = mapOf(
        "mistral/mistral-large-latest" to PricePair(2.0, 6.0),
        "mistral/mistral-medium-latest" to PricePair(2.7, 8.1),
        "mistral/mistral-small-latest" to PricePair(1.0, 3.0),
        "mistral/codestral-latest" to PricePair(1.0, 3.0),
        "mistral/open-mistral-nemo" to PricePair(0.3, 0.9),
        "mistral/ministral-3b-latest" to PricePair(0.04, 0.04),
    )

    fun getInputPrice(modelId: String): Double =
        priceMap.entries.firstOrNull { modelId.contains(it.key) }?.value?.input ?: 1.0

    fun getOutputPrice(modelId: String): Double =
        priceMap.entries.firstOrNull { modelId.contains(it.key) }?.value?.output ?: 3.0
}

data class TokenStats(
    val totalPromptTokens: Int = 0,
    val totalCompletionTokens: Int = 0,
    val totalTokens: Int = 0,
    val estimatedCostUsd: Double = 0.0,
    val requestCount: Int = 0
) {
    fun addUsage(usage: TokenUsage): TokenStats {
        return copy(
            totalPromptTokens = totalPromptTokens + usage.promptTokens,
            totalCompletionTokens = totalCompletionTokens + usage.completionTokens,
            totalTokens = totalTokens + usage.totalTokens,
            requestCount = requestCount + 1
        )
    }

    fun addUsageWithCost(usage: TokenUsage, cost: Double): TokenStats {
        return copy(
            totalPromptTokens = totalPromptTokens + usage.promptTokens,
            totalCompletionTokens = totalCompletionTokens + usage.completionTokens,
            totalTokens = totalTokens + usage.totalTokens,
            estimatedCostUsd = estimatedCostUsd + cost,
            requestCount = requestCount + 1
        )
    }

    fun getFormattedCost(): String = "$${ "%.6f".format(estimatedCostUsd) }"
    fun getFormattedTokens(): String =
        "Prompt: $totalPromptTokens | Completion: $totalCompletionTokens | Total: $totalTokens"
}
