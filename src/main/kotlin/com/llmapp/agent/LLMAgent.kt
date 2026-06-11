package com.llmapp.agent

import com.llmapp.api.OpenRouterClient
import com.llmapp.chat.ChatHistory
import com.llmapp.model.OpenRouterRequest
import com.llmapp.model.ResponseControl
import com.llmapp.model.TokenUsage

data class LLMResponse(
    val content: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val finishReason: String?,
    val responseTimeMs: Long
)

class LLMAgent(
    apiKey: String,
    private var model: String,
    systemPrompt: String,
    private var responseControl: ResponseControl = ResponseControl(),
    maxHistorySize: Int = 50
) {
    private val apiClient = OpenRouterClient(apiKey)
    private val history = ChatHistory(systemPrompt, maxHistorySize)
    private val tokenTracker = TokenTracker()
    private var requestCounter = 0

    init {
        tokenTracker.updateModel(model)
    }

    suspend fun processRequest(userInput: String): LLMResponse {
        try {
            val enhancedPrompt = enhancePrompt(userInput)
            history.addUserMessage(enhancedPrompt)

            val (response, responseTime) = sendToLLM()

            if (response.error != null) {
                val errorMsg = response.error.message
                println("❌ API вернул ошибку: $errorMsg")
                throw Exception("API Error: $errorMsg")
            }

            val answer = response.choices?.firstOrNull()?.message?.content
            if (answer.isNullOrBlank()) {
                println("❌ API вернул пустой ответ")
                throw Exception("Empty API response")
            }

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

            return LLMResponse(
                content = answer,
                promptTokens = response.usage?.promptTokens,
                completionTokens = response.usage?.completionTokens,
                totalTokens = response.usage?.totalTokens,
                finishReason = response.choices.firstOrNull()?.finishReason,
                responseTimeMs = responseTime
            )
        } catch (e: Exception) {
            println("❌ Ошибка в LLMAgent: ${e.message}")
            throw e
        }
    }

    suspend fun regenerateLastResponse(originalUserMessage: String? = null): LLMResponse {
        history.removeLastMessage()

        if (originalUserMessage != null) {
            history.removeLastMessage()
            val enhancedPrompt = enhancePrompt(originalUserMessage)
            history.addUserMessage(enhancedPrompt)
        }

        val (response, responseTime) = sendToLLM()

        response.error?.let {
            throw Exception("API Error: ${it.message}")
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

        val usage = response.usage

        return LLMResponse(
            content = answer,
            promptTokens = usage?.promptTokens,
            completionTokens = usage?.completionTokens,
            totalTokens = usage?.totalTokens,
            finishReason = response.choices.firstOrNull()?.finishReason,
            responseTimeMs = responseTime
        )
    }

    private fun enhancePrompt(userInput: String): String {
        return if (responseControl.enabled && responseControl.formatDescription != null) {
            "$userInput\n\n${responseControl.formatDescription}"
        } else {
            userInput
        }
    }

    private suspend fun sendToLLM(): Pair<com.llmapp.model.OpenRouterResponse, Long> {
        val request = OpenRouterRequest(
            model = model,
            messages = history.getMessages(),
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

    fun getHistorySize(): Int = history.size()

    fun rebuildHistory(uiMessages: List<Pair<String, String>>) {
        clearHistory()
        for ((role, content) in uiMessages) {
            when (role) {
                "user" -> history.addUserMessage(content)
                "assistant" -> history.addAssistantMessage(content)
            }
        }
    }

    fun getTokenStats() = tokenTracker.stats.value
    fun getTokenHistory() = tokenTracker.historySnapshots.value
    fun getContextStatus() = tokenTracker.checkContextLimit()
    fun getContextWarning() = tokenTracker.getWarningMessage()

    fun clearTokenStats() {
        tokenTracker.reset()
        requestCounter = 0
    }
}
