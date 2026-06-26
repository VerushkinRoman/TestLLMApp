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
        "nvidia/nemotron-3-super-120b-a12b:free" to Pricing(0.0, 0.0),
        "cognitivecomputations/dolphin-mistral-24b-venice-edition:free" to Pricing(0.0, 0.0),
        "meta-llama/llama-3.2-3b-instruct:free" to Pricing(0.0, 0.0),
        "meta-llama/llama-3.3-70b-instruct:free" to Pricing(0.0, 0.0),
        "google/gemma-4-31b-it:free" to Pricing(0.0, 0.0),
        "qwen/qwen3-coder:free" to Pricing(0.0, 0.0),
        "openai/gpt-oss-120b:free" to Pricing(0.0, 0.0),
        "liquid/lfm-2.5-1.2b-thinking:free" to Pricing(0.0, 0.0),
        "moonshotai/kimi-k2.6:free" to Pricing(0.0, 0.0),
        "nvidia/nemotron-3-nano-30b-a3b:free" to Pricing(0.0, 0.0),
        "poolside/laguna-m.1:free" to Pricing(0.0, 0.0),
        "nousresearch/hermes-3-llama-3.1-405b:free" to Pricing(0.0, 0.0),
        "openai/gpt-oss-20b:free" to Pricing(0.0, 0.0),
        "poolside/laguna-xs.2:free" to Pricing(0.0, 0.0),
        "google/gemma-4-26b-a4b-it:free" to Pricing(0.0, 0.0),
        "z-ai/glm-4.5-air:free" to Pricing(0.0, 0.0),
        "liquid/lfm-2.5-1.2b-instruct:free" to Pricing(0.0, 0.0),
        "tencent/hy3-preview:free" to Pricing(0.0, 0.0),
        "minimax/minimax-m2.5:free" to Pricing(0.0, 0.0),
        "arcee-ai/trinity-large-thinking:free" to Pricing(0.0, 0.0),
        "deepseek/deepseek-v4-flash:free" to Pricing(0.0, 0.0),
        "qwen/qwen3-next-80b-a3b-instruct:free" to Pricing(0.0, 0.0)
    )

    private val DEFAULT_PRICE = Pricing(0.0, 0.0)

    fun getPricing(modelId: String): Pricing {
        return PRICES[modelId] ?: DEFAULT_PRICE
    }
}

data class Pricing(
    val perPromptToken: Double,
    val perCompletionToken: Double
)
