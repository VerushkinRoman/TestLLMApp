package com.llmapp.agent

import com.llmapp.api.OpenRouterClient
import com.llmapp.model.OpenRouterRequest
import com.llmapp.model.OpenRouterResponse
import com.llmapp.model.ResponseControl
import com.llmapp.model.TokenUsage

data class LLMResponseWithCompression(
    val content: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val finishReason: String?,
    val responseTimeMs: Long,
    val compressionStats: CompressedChatHistory.CompressionStats? = null,
    val usedCompression: Boolean = false
)

class CompressedLLMAgent(
    apiKey: String,
    private var model: String,
    systemPrompt: String,
    private var responseControl: ResponseControl = ResponseControl(),
    maxHistorySize: Int = 50,
    keepLastMessages: Int = 8,
    summarizeEvery: Int = 6
) {
    private val apiClient = OpenRouterClient(apiKey)
    private val history = CompressedChatHistory(
        apiClient = apiClient,
        systemPrompt = systemPrompt,
        keepLastMessages = keepLastMessages,
        summarizeEvery = summarizeEvery,
        maxHistorySize = maxHistorySize
    )
    private val tokenTracker = TokenTracker()
    private var requestCounter = 0

    var compressionEnabled = true
        set(value) {
            field = value
            history.compressionEnabled = value
        }

    init {
        tokenTracker.updateModel(model)
    }

    suspend fun processRequest(userInput: String): LLMResponseWithCompression {
        try {
            val enhancedPrompt = enhancePrompt(userInput)
            history.addUserMessage(enhancedPrompt)

            if (compressionEnabled && shouldGenerateSummary()) {
                history.generateSummary()
            }

            val (response, responseTime) = sendToLLM()

            if (response.error != null) {
                throw Exception("API Error: ${response.error.message}")
            }

            val answer = response.choices?.firstOrNull()?.message?.content
                ?: throw Exception("Empty API response")

            history.addAssistantMessage(answer)
            requestCounter++

            response.usage?.let { usage ->
                val tokenUsage = TokenUsage(
                    promptTokens = usage.promptTokens ?: 0,
                    completionTokens = usage.completionTokens ?: 0,
                    totalTokens = usage.totalTokens ?: 0
                )
                tokenTracker.trackRequest(tokenUsage, requestCounter)
            }

            return LLMResponseWithCompression(
                content = answer,
                promptTokens = response.usage?.promptTokens,
                completionTokens = response.usage?.completionTokens,
                totalTokens = response.usage?.totalTokens,
                finishReason = response.choices.firstOrNull()?.finishReason,
                responseTimeMs = responseTime,
                compressionStats = if (compressionEnabled) history.getCompressionStats() else null,
                usedCompression = compressionEnabled
            )
        } catch (e: Exception) {
            println("❌ Ошибка в CompressedLLMAgent: ${e.message}")
            throw e
        }
    }

    private fun shouldGenerateSummary(): Boolean {
        val stats = history.getCompressionStats()
        return stats.pendingForSummaryCount >= 4
    }

    private fun enhancePrompt(userInput: String): String {
        return if (responseControl.enabled && responseControl.formatDescription != null) {
            "$userInput\n\n${responseControl.formatDescription}"
        } else {
            userInput
        }
    }

    private suspend fun sendToLLM(): Pair<OpenRouterResponse, Long> {
        val messages = history.getMessagesForRequest(compressionEnabled)

        println("📊 Отправляю запрос с ${messages.size} сообщениями (компрессия: ${if (compressionEnabled) "вкл" else "выкл"})")

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
        history.clear()
        tokenTracker.reset()
        requestCounter = 0
    }

    fun rebuildHistory(uiMessages: List<Pair<String, String>>) {
        clearHistory()
        for ((role, content) in uiMessages) {
            when (role) {
                "user" -> history.addUserMessage(content)
                "assistant" -> history.addAssistantMessage(content)
            }
        }
    }

    fun getHistorySize(): Int = history.size()

    fun getTokenStats() = tokenTracker.stats.value

    fun getTokenHistory() = tokenTracker.historySnapshots.value

    fun getContextWarning() = tokenTracker.getWarningMessage()

    fun getCompressionStats() = history.getCompressionStats()

    fun clearTokenStats() {
        tokenTracker.reset()
        requestCounter = 0
    }
}
