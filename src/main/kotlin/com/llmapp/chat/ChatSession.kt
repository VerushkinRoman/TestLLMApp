package com.llmapp.chat

import com.llmapp.agent.LLMAgent
import com.llmapp.agent.LLMResponse
import com.llmapp.model.ResponseControl

data class ChatResponse(
    val content: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val finishReason: String?,
    val responseTimeMs: Long
)

class ChatSession(
    apiKey: String,
    private var currentModel: String = "nvidia/nemotron-3-super-120b-a12b:free",
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
    maxHistorySize: Int = 50
) {
    private val llmAgent = LLMAgent(
        apiKey = apiKey,
        model = currentModel,
        systemPrompt = systemPrompt,
        responseControl = ResponseControl(),
        maxHistorySize = maxHistorySize
    )

    private var responseControl = ResponseControl()

    fun changeModel(newModel: String) {
        currentModel = newModel
        llmAgent.changeModel(newModel)
    }

    fun getCurrentModel(): String = currentModel

    fun setResponseControl(control: ResponseControl) {
        responseControl = control
        llmAgent.updateResponseControl(control)
    }

    fun getResponseControl(): ResponseControl = responseControl

    suspend fun ask(userPrompt: String, isRegeneration: Boolean = false): ChatResponse {
        try {
            val response: LLMResponse = if (isRegeneration) {
                llmAgent.regenerateLastResponse(userPrompt)
            } else {
                llmAgent.processRequest(userPrompt)
            }

            printMetadata(
                finishReason = response.finishReason,
                promptTokens = response.promptTokens,
                completionTokens = response.completionTokens,
                totalTokens = response.totalTokens,
                responseTimeMs = response.responseTimeMs
            )

            return ChatResponse(
                content = response.content,
                promptTokens = response.promptTokens,
                completionTokens = response.completionTokens,
                totalTokens = response.totalTokens,
                finishReason = response.finishReason,
                responseTimeMs = response.responseTimeMs
            )
        } catch (e: Exception) {
            throw Exception("Ошибка при обращении к LLM: ${e.message}", e)
        }
    }

    fun rebuildHistoryFromUiMessages(uiMessages: List<Pair<String, String>>) {
        llmAgent.rebuildHistory(uiMessages)
    }

    private fun printMetadata(
        finishReason: String?,
        promptTokens: Int?,
        completionTokens: Int?,
        totalTokens: Int?,
        responseTimeMs: Long
    ) {
        val metadata = buildString {
            if (finishReason != null) {
                append("🏁 Завершено: $finishReason")
            }
            if (promptTokens != null || completionTokens != null || totalTokens != null) {
                if (isNotEmpty()) append(" | ")
                append("📊 Токены: ${totalTokens ?: "?"} (запрос: ${promptTokens ?: "?"} ответ:${completionTokens ?: "?"})")
            }

            if (responseTimeMs > 0) {
                if (isNotEmpty()) append(" | ")
                val timeStr = when {
                    responseTimeMs < 1000 -> "${responseTimeMs}мс"
                    responseTimeMs < 60000 -> "${responseTimeMs / 1000}.${(responseTimeMs % 1000) / 100}с"
                    else -> "${responseTimeMs / 60000}м ${(responseTimeMs % 60000) / 1000}с"
                }
                append("⏱️ Время: $timeStr")
            }
        }

        if (metadata.isNotEmpty()) {
            println("📈 $metadata")
        }
    }

    fun clearHistory() {
        llmAgent.clearHistory()
    }

    fun getHistorySize(): Int = llmAgent.getHistorySize()
}
