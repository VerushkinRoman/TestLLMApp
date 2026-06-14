package com.llmapp.agent

import com.llmapp.api.OpenRouterClient
import com.llmapp.model.ChatMessage
import com.llmapp.model.OpenRouterRequest
import com.llmapp.model.ResponseControl
import com.llmapp.model.TokenUsage
import com.llmapp.strategy.BranchingStrategy
import com.llmapp.strategy.ContextStrategy
import com.llmapp.strategy.SlidingWindowStrategy
import com.llmapp.strategy.StickyFactsStrategy
import com.llmapp.strategy.StrategyStats

data class StrategicResponse(
    val content: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val finishReason: String?,
    val responseTimeMs: Long,
    val strategyUsed: String,
    val strategyStats: StrategyStats
)

enum class ContextStrategyType {
    SLIDING_WINDOW,
    STICKY_FACTS,
    BRANCHING
}

class StrategicLLMAgent(
    apiKey: String,
    private var model: String,
    systemPrompt: String,
    private var responseControl: ResponseControl = ResponseControl()
) {
    private val apiClient = OpenRouterClient(apiKey)
    private val tokenTracker = TokenTracker()
    private var requestCounter = 0

    private var currentStrategyType: ContextStrategyType = ContextStrategyType.SLIDING_WINDOW
    private lateinit var strategy: ContextStrategy
    private val branchingStrategy: BranchingStrategy
    private val originalSystemPrompt: String = systemPrompt

    init {
        tokenTracker.updateModel(model)
        branchingStrategy = BranchingStrategy(systemPrompt)
        initStrategy(currentStrategyType, systemPrompt)
    }

    private fun initStrategy(type: ContextStrategyType, systemPrompt: String) {
        strategy = when (type) {
            ContextStrategyType.SLIDING_WINDOW -> SlidingWindowStrategy(20, systemPrompt)
            ContextStrategyType.STICKY_FACTS -> StickyFactsStrategy(10, systemPrompt)
            ContextStrategyType.BRANCHING -> branchingStrategy
        }
    }

    fun setStrategy(type: ContextStrategyType) {
        if (currentStrategyType == type) return

        val oldHistory = strategy.getFullHistory()

        currentStrategyType = type
        initStrategy(type, oldHistory.firstOrNull()?.content ?: originalSystemPrompt)

        oldHistory.drop(1).forEach { message ->
            strategy.addMessage(message)
        }
    }

    fun getCurrentStrategyName(): String = strategy.getName()

    fun createCheckpoint(name: String): String? {
        return if (currentStrategyType == ContextStrategyType.BRANCHING) {
            branchingStrategy.createCheckpoint(name)
        } else null
    }

    fun createBranch(checkpointId: String, branchName: String): String? {
        return if (currentStrategyType == ContextStrategyType.BRANCHING) {
            branchingStrategy.createBranch(checkpointId, branchName)
        } else null
    }

    fun switchToBranch(branchId: String): Boolean {
        return if (currentStrategyType == ContextStrategyType.BRANCHING) {
            branchingStrategy.switchToBranch(branchId)
        } else false
    }

    fun getAllBranches(): List<BranchingStrategy.BranchInfo> {
        return if (currentStrategyType == ContextStrategyType.BRANCHING) {
            branchingStrategy.getAllBranches()
        } else emptyList()
    }

    suspend fun processRequest(userInput: String): StrategicResponse {
        try {
            val enhancedPrompt = enhancePrompt(userInput)
            strategy.addMessage(ChatMessage(role = "user", content = enhancedPrompt))

            val (response, responseTime) = sendToLLM()

            if (response.error != null) {
                throw Exception("API Error: ${response.error.message}")
            }

            val answer = response.choices?.firstOrNull()?.message?.content
                ?: throw Exception("Empty API response")

            strategy.addMessage(ChatMessage(role = "assistant", content = answer))
            requestCounter++

            response.usage?.let { usage ->
                val tokenUsage = TokenUsage(
                    promptTokens = usage.promptTokens ?: 0,
                    completionTokens = usage.completionTokens ?: 0,
                    totalTokens = usage.totalTokens ?: 0
                )

                tokenTracker.trackRequest(tokenUsage, requestCounter)
            }

            return StrategicResponse(
                content = answer,
                promptTokens = response.usage?.promptTokens,
                completionTokens = response.usage?.completionTokens,
                totalTokens = response.usage?.totalTokens,
                finishReason = response.choices.firstOrNull()?.finishReason,
                responseTimeMs = responseTime,
                strategyUsed = strategy.getName(),
                strategyStats = strategy.getStats()
            )
        } catch (e: Exception) {
            println("❌ Ошибка в StrategicLLMAgent: ${e.message}")
            throw e
        }
    }

    private fun enhancePrompt(userInput: String): String {
        return if (responseControl.enabled && responseControl.formatDescription != null) {
            "$userInput\n\n${responseControl.formatDescription}"
        } else {
            userInput
        }
    }

    private suspend fun sendToLLM(): Pair<com.llmapp.model.OpenRouterResponse, Long> {
        val messages = strategy.getContextForRequest()

        println("📊 [${strategy.getName()}] Отправляю запрос с ${messages.size} сообщениями")

        val request = OpenRouterRequest(
            model = model,
            messages = messages,
            maxTokens = if (responseControl.enabled) responseControl.maxTokens else null,
            stop = if (responseControl.enabled) responseControl.stopSequences else null,
            temperature = if (responseControl.enabled) responseControl.temperature else null,
            skipContextOptimization = true
        )

        val startTime = System.currentTimeMillis()
        val response = apiClient.sendRequest(request)
        val endTime = System.currentTimeMillis()

        return Pair(response, endTime - startTime)
    }

    fun changeModel(newModel: String) {
        model = newModel
        tokenTracker.updateModel(newModel)
    }

    fun updateResponseControl(control: ResponseControl) {
        responseControl = control
    }

    fun clearHistory() {
        strategy.clear()
        tokenTracker.reset()
        requestCounter = 0
    }

    fun clearTokenStats() {
        tokenTracker.reset()
        requestCounter = 0
    }

    fun getTokenStats() = tokenTracker.stats.value
    fun getStrategyStats(): StrategyStats = strategy.getStats()

    fun getFacts(): Map<String, String> {
        return if (currentStrategyType == ContextStrategyType.STICKY_FACTS) {
            (strategy as StickyFactsStrategy).getFacts()
        } else emptyMap()
    }

    fun getTokenHistory(): List<TokenSnapshot> {
        return tokenTracker.historySnapshots.value
    }
}
