package com.llmapp.chat

import com.llmapp.agent.CompressedLLMAgent
import com.llmapp.agent.LLMAgent
import com.llmapp.agent.TokenSnapshot
import com.llmapp.model.ResponseControl
import com.llmapp.model.TokenStats

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
    maxHistorySize: Int = 50,
    private val compressionEnabled: Boolean = true,
    private val keepLastMessages: Int = 8,
    private val summarizeEvery: Int = 6
) {
    private val compressedAgent: CompressedLLMAgent? = if (compressionEnabled) {
        CompressedLLMAgent(
            apiKey = apiKey,
            model = currentModel,
            systemPrompt = systemPrompt,
            responseControl = ResponseControl(),
            maxHistorySize = maxHistorySize,
            keepLastMessages = keepLastMessages,
            summarizeEvery = summarizeEvery
        ).also { it.compressionEnabled = true }
    } else null

    private val regularAgent: LLMAgent? = if (!compressionEnabled) {
        LLMAgent(
            apiKey = apiKey,
            model = currentModel,
            systemPrompt = systemPrompt,
            responseControl = ResponseControl(),
            maxHistorySize = maxHistorySize
        )
    } else null

    private var responseControl = ResponseControl()

    fun isCompressionEnabled(): Boolean = compressionEnabled

    fun getCompressionStats() = compressedAgent?.getCompressionStats()

    fun changeModel(newModel: String) {
        currentModel = newModel
        compressedAgent?.changeModel(newModel)
        regularAgent?.changeModel(newModel)
    }

    fun getCurrentModel(): String = currentModel

    fun setResponseControl(control: ResponseControl) {
        responseControl = control
        compressedAgent?.updateResponseControl(control)
        regularAgent?.updateResponseControl(control)
    }

    fun getResponseControl(): ResponseControl = responseControl

    suspend fun ask(userPrompt: String, isRegeneration: Boolean = false): ChatResponse {
        try {
            val response = when {
                compressedAgent != null -> {
                    if (isRegeneration) {
                        println("⚠️ Регенерация для сжатого агента пока не поддерживается")
                    }
                    val resp = compressedAgent.processRequest(userPrompt)
                    ChatResponse(
                        content = resp.content,
                        promptTokens = resp.promptTokens,
                        completionTokens = resp.completionTokens,
                        totalTokens = resp.totalTokens,
                        finishReason = resp.finishReason,
                        responseTimeMs = resp.responseTimeMs
                    )
                }

                regularAgent != null -> {
                    val resp = if (isRegeneration) {
                        regularAgent.regenerateLastResponse(userPrompt)
                    } else {
                        regularAgent.processRequest(userPrompt)
                    }
                    ChatResponse(
                        content = resp.content,
                        promptTokens = resp.promptTokens,
                        completionTokens = resp.completionTokens,
                        totalTokens = resp.totalTokens,
                        finishReason = resp.finishReason,
                        responseTimeMs = resp.responseTimeMs
                    )
                }

                else -> error("No agent available")
            }

            printMetadata(
                finishReason = response.finishReason,
                promptTokens = response.promptTokens,
                completionTokens = response.completionTokens,
                totalTokens = response.totalTokens,
                responseTimeMs = response.responseTimeMs
            )

            return response
        } catch (e: Exception) {
            throw Exception("Ошибка при обращении к LLM: ${e.message}", e)
        }
    }

    fun rebuildHistoryFromUiMessages(uiMessages: List<Pair<String, String>>) {
        compressedAgent?.rebuildHistory(uiMessages)
        regularAgent?.rebuildHistory(uiMessages)
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
        compressedAgent?.clearHistory()
        regularAgent?.clearHistory()
    }

    fun getHistorySize(): Int =
        compressedAgent?.getHistorySize() ?: regularAgent?.getHistorySize() ?: 0

    fun getTokenStats(): TokenStats =
        compressedAgent?.getTokenStats() ?: regularAgent?.getTokenStats() ?: TokenStats()

    fun getTokenHistory(): List<TokenSnapshot> =
        compressedAgent?.getTokenHistory() ?: regularAgent?.getTokenHistory() ?: emptyList()

    fun getContextWarning(): String =
        compressedAgent?.getContextWarning() ?: regularAgent?.getContextWarning() ?: ""

    fun clearTokenStats() {
        compressedAgent?.clearTokenStats()
        regularAgent?.clearTokenStats()
    }
}
