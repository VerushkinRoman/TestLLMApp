package com.llmapp.model

import kotlinx.serialization.Serializable

@Serializable
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

data class TokenStats(
    val totalPromptTokens: Int = 0,
    val totalCompletionTokens: Int = 0,
    val totalTokens: Int = 0,
    val estimatedCostUsd: Double = 0.0,
    val requestCount: Int = 0
) {
    fun addUsage(
        usage: TokenUsage,
        costPerPromptToken: Double,
        costPerCompletionToken: Double
    ): TokenStats {
        val newPromptTokens = totalPromptTokens + usage.promptTokens
        val newCompletionTokens = totalCompletionTokens + usage.completionTokens
        val newCost =
            (newPromptTokens * costPerPromptToken) + (newCompletionTokens * costPerCompletionToken)

        return copy(
            totalPromptTokens = newPromptTokens,
            totalCompletionTokens = newCompletionTokens,
            totalTokens = newPromptTokens + newCompletionTokens,
            estimatedCostUsd = newCost,
            requestCount = requestCount + 1
        )
    }

    fun getFormattedCost(): String = "$${"%.6f".format(estimatedCostUsd)}"
    fun getFormattedTokens(): String =
        "Prompt: $totalPromptTokens | Completion: $totalCompletionTokens | Total: $totalTokens"
}

object ModelPricing {
    fun getPricing(modelId: String): Pricing {
        return ModelList.pricing[modelId] ?: Pricing(0.0, 0.0)
    }
}

data class Pricing(
    val perPromptToken: Double,
    val perCompletionToken: Double
)
