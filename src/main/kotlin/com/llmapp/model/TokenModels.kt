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
    private val PRICES = mapOf(
        "nvidia/nemotron-3-super-120b-a12b:free" to Pricing(0.5e-6, 0.5e-6),
        "nvidia/nemotron-3-ultra-550b-a55b:free" to Pricing(1.0e-6, 1.0e-6),
        "nvidia/nemotron-3-nano-30b-a3b:free" to Pricing(0.2e-6, 0.2e-6),
        "openai/gpt-oss-120b:free" to Pricing(0.8e-6, 0.8e-6),
        "google/gemma-4-31b-it:free" to Pricing(0.6e-6, 0.6e-6),
        "poolside/laguna-m.1:free" to Pricing(0.4e-6, 0.4e-6)
    )

    private val DEFAULT_PRICE = Pricing(0.3e-6, 0.3e-6)

    fun getPricing(modelId: String): Pricing {
        return PRICES[modelId] ?: DEFAULT_PRICE
    }
}

data class Pricing(
    val perPromptToken: Double,
    val perCompletionToken: Double
)
