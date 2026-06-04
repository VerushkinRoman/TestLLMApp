package com.llmapp.chat

import com.llmapp.api.OpenRouterClient
import com.llmapp.model.OpenRouterRequest
import com.llmapp.model.ResponseControl
import com.llmapp.model.Usage

data class ChatResponse(
    val content: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val finishReason: String?
)

class ChatSession(
    apiKey: String,
    private var currentModel: String = "openrouter/owl-alpha",
    systemPrompt: String = """Ты полезный ассистент. Отвечай кратко и по делу на русском языке.
        Форматирование ответов:
        - Используй **жирный** текст для важной информации
        - Используй *курсив* для выделения
        - Для кода используй тройные обратные кавычки с указанием языка:
        ```kotlin
        fun example() {
            println("Hello")
        }
        Для списков используй - или * в начале строки
        
        Для заголовков используй #, ##, ###
        
        Для цитат используй > в начале строки
        
        Ссылки оформляй как текст""",
    maxHistorySize: Int = 20
) {
    private val history = ChatHistory(systemPrompt, maxHistorySize)
    private val apiClient = OpenRouterClient(apiKey)
    private var responseControl = ResponseControl()

    fun changeModel(newModel: String) {
        currentModel = newModel
    }

    fun getCurrentModel(): String = currentModel

    fun setResponseControl(control: ResponseControl) {
        responseControl = control
    }

    fun getResponseControl(): ResponseControl = responseControl

    suspend fun ask(userPrompt: String): ChatResponse {
        val enhancedPrompt =
            if (responseControl.enabled && responseControl.formatDescription != null) {
                "$userPrompt\n\n${responseControl.formatDescription}"
            } else {
                userPrompt
            }

        history.addUserMessage(enhancedPrompt)

        val request = OpenRouterRequest(
            model = currentModel,
            messages = history.getMessages(),
            maxTokens = if (responseControl.enabled) responseControl.maxTokens else null,
            stop = if (responseControl.enabled) responseControl.stopSequences else null,
            temperature = if (responseControl.enabled) responseControl.temperature else null
        )

        val response = apiClient.sendRequest(request)

        response.error?.let {
            throw Exception("API Error: ${it.message}")
        }

        val answer = response.choices?.firstOrNull()?.message?.content
            ?: throw Exception("Empty API response")

        val finishReason = response.choices.firstOrNull()?.finishReason
        val usage = response.usage

        printMetadata(finishReason, usage)

        history.addAssistantMessage(answer)

        return ChatResponse(
            content = answer,
            promptTokens = usage?.promptTokens,
            completionTokens = usage?.completionTokens,
            totalTokens = usage?.totalTokens,
            finishReason = finishReason
        )
    }

    private fun printMetadata(finishReason: String?, usage: Usage?) {
        val metadata = buildString {
            if (finishReason != null) {
                append("🏁 Завершено: $finishReason")
            }
            if (usage != null) {
                if (isNotEmpty()) append(" | ")
                append("📊 Токены: ${usage.totalTokens ?: "?"} (запрос: ${usage.promptTokens ?: "?"}, ответ: ${usage.completionTokens ?: "?"})")
            }
        }

        if (metadata.isNotEmpty()) {
            println("📈 $metadata")
        }
    }

    fun clearHistory() {
        history.clear()
    }

    fun getHistorySize(): Int = history.size()
}
