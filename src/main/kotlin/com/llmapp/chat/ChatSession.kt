package com.llmapp.chat

import com.llmapp.agent.CompressedLLMAgent
import com.llmapp.agent.LLMAgent
import com.llmapp.agent.StrategicLLMAgent
import com.llmapp.agent.TokenSnapshot
import com.llmapp.api.OpenRouterClient
import com.llmapp.mcp.McpIntegration
import com.llmapp.model.ChatMessage
import com.llmapp.model.OpenRouterRequest
import com.llmapp.model.ResponseControl
import com.llmapp.model.TokenStats
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

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
    private val systemPrompt: String = """Ты полезный ассистент. Отвечай кратко и по делу на русском языке.
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
    private var strategicAgent: StrategicLLMAgent? = null
    private var useStrategicAgent = true
    private var currentApiKey = apiKey

    var mcpIntegration: McpIntegration? = null

    private val compressedAgent: CompressedLLMAgent? = if (compressionEnabled) {
        CompressedLLMAgent(
            apiKey = currentApiKey,
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
            apiKey = currentApiKey,
            model = currentModel,
            systemPrompt = systemPrompt,
            responseControl = ResponseControl(),
            maxHistorySize = maxHistorySize
        )
    } else null

    private var responseControl = ResponseControl()

    init {
        strategicAgent = StrategicLLMAgent(
            apiKey = currentApiKey,
            model = currentModel,
            systemPrompt = systemPrompt,
            responseControl = ResponseControl()
        )
    }

    fun refreshApiKeys() {
        val newApiKey = com.llmapp.api.ApiConfig.getApiKey()
        if (newApiKey == currentApiKey) return

        currentApiKey = newApiKey
        println("🔄 ChatSession: Обновляем API ключ для всех агентов")

        compressedAgent?.refreshApiKey(newApiKey)
        regularAgent?.refreshApiKey(newApiKey)
        strategicAgent?.refreshApiKey(newApiKey)
    }

    fun isCompressionEnabled(): Boolean = compressionEnabled
    fun getCompressionStats() = compressedAgent?.getCompressionStats()

    fun changeModel(newModel: String) {
        currentModel = newModel
        compressedAgent?.changeModel(newModel)
        regularAgent?.changeModel(newModel)
        strategicAgent?.changeModel(newModel)
    }

    fun getCurrentModel(): String = currentModel

    fun setResponseControl(control: ResponseControl) {
        responseControl = control
        compressedAgent?.updateResponseControl(control)
        regularAgent?.updateResponseControl(control)
        strategicAgent?.updateResponseControl(control)
    }

    suspend fun ask(userPrompt: String, isRegeneration: Boolean = false): ChatResponse {
        refreshApiKeys()

        val effectivePrompt = if (mcpIntegration?.isConnected() == true) {
            "$userPrompt\n\n${mcpIntegration!!.getToolDescriptions()}"
        } else userPrompt

        try {
            val response = run {
                if (useStrategicAgent && strategicAgent != null) {
                    try {
                        val resp = strategicAgent!!.processRequest(effectivePrompt)
                        printMetadata(
                            finishReason = resp.finishReason,
                            promptTokens = resp.promptTokens,
                            completionTokens = resp.completionTokens,
                            totalTokens = resp.totalTokens,
                            responseTimeMs = resp.responseTimeMs
                        )
                        println("📊 Стратегия: ${resp.strategyUsed}")
                        ChatResponse(
                            content = resp.content,
                            promptTokens = resp.promptTokens,
                            completionTokens = resp.completionTokens,
                            totalTokens = resp.totalTokens,
                            finishReason = resp.finishReason,
                            responseTimeMs = resp.responseTimeMs
                        )
                    } catch (_: Exception) {
                        println("⚠️ Стратегический агент не работает, переключаюсь на обычный")
                        useStrategicAgent = false
                        null
                    }
                } else null
            } ?: when {
                compressedAgent != null -> {
                    if (isRegeneration) {
                        println("⚠️ Регенерация для сжатого агента пока не поддерживается")
                    }
                    val resp = compressedAgent.processRequest(effectivePrompt)
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
                        regularAgent.regenerateLastResponse(effectivePrompt)
                    } else {
                        regularAgent.processRequest(effectivePrompt)
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

            val finalContent = handleMcpToolCalls(response.content, userPrompt)
            return response.copy(content = finalContent)
        } catch (e: Exception) {
            throw Exception("Ошибка при обращении к LLM: ${e.message}", e)
        }
    }

    private suspend fun handleMcpToolCalls(
        initialContent: String,
        originalUserPrompt: String
    ): String {
        var content = initialContent
        val allResults = mutableListOf<String>()
        repeat(5) {
            val json = extractMcpCallJson(content) ?: return synthesizeFinalAnswer(
                allResults,
                originalUserPrompt,
                content
            )
            val result = try {
                mcpIntegration?.executeToolCall(json) ?: return synthesizeFinalAnswer(
                    allResults,
                    originalUserPrompt,
                    content
                )
            } catch (e: Exception) {
                return synthesizeFinalAnswer(
                    allResults,
                    originalUserPrompt,
                    "MCP tool error: ${e.message}"
                )
            }
            allResults.add(result)
            val followUp = buildString {
                appendLine("Original user question: $originalUserPrompt")
                appendLine()
                appendLine("MCP tool returned this data:")
                appendLine(result)
                appendLine()
                appendLine("If you need more data, call another MCP tool using the same [MCP_CALL] format.")
                appendLine("If you have all the data needed, provide the FINAL answer in Russian.")
                appendLine("Do NOT output [MCP_CALL] or [MCPCALL] markers if you are ready to answer.")
            }
            content = askMcpFollowUp(followUp)
        }
        return synthesizeFinalAnswer(allResults, originalUserPrompt, content)
    }

    private suspend fun synthesizeFinalAnswer(
        allResults: List<String>,
        originalUserPrompt: String,
        fallback: String
    ): String {
        if (allResults.isEmpty()) return fallback
        val prompt = buildString {
            appendLine("Answer the user's question using ONLY the data below. Do NOT use your own knowledge.")
            appendLine()
            appendLine("User question: $originalUserPrompt")
            appendLine()
            appendLine("MCP tool results (use these, ignore your own knowledge):")
            allResults.forEachIndexed { i, r ->
                appendLine("--- Result ${i + 1} ---")
                appendLine(r)
            }
            appendLine()
            appendLine("Now provide the final answer in Russian based ONLY on the data above.")
            appendLine("If the data shows a group letter, position, or points — use those exact values.")
            append("Do NOT guess or use your training data.")
        }
        return askMcpFollowUp(prompt)
    }

    private fun extractMcpCallJson(text: String): String? {
        val json = Json { ignoreUnknownKeys = true }
        val markers = listOf("[MCP_CALL]", "[MCPCALL]", "[mcp_call]", "[mcpcall]")
        for (marker in markers) {
            val idx = text.indexOf(marker, ignoreCase = true)
            if (idx == -1) continue
            val after = text.substring(idx + marker.length).trim()
            var searchFrom = 0
            while (true) {
                val braceStart = after.indexOf('{', searchFrom)
                if (braceStart == -1) break
                var depth = 0
                for (i in braceStart until after.length) {
                    when (after[i]) {
                        '{' -> depth++
                        '}' -> {
                            depth--
                            if (depth == 0) {
                                val candidate = after.substring(braceStart, i + 1)
                                try {
                                    json.parseToJsonElement(candidate).jsonObject
                                    return candidate
                                } catch (_: Exception) {
                                    searchFrom = braceStart + 1
                                    break
                                }
                            }
                        }
                    }
                }
                if (depth > 0) break
            }
        }
        return null
    }

    private suspend fun askMcpFollowUp(prompt: String): String {
        val client = OpenRouterClient(currentApiKey)
        val request = OpenRouterRequest(
            model = currentModel,
            messages = listOf(
                ChatMessage("system", systemPrompt),
                ChatMessage("user", prompt)
            ),
            skipContextOptimization = true
        )
        val response = client.sendRequest(request)
        response.error?.let { throw Exception(it.message) }
        return response.choices?.firstOrNull()?.message?.content ?: "No response"
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
            if (finishReason != null) append("🏁 Завершено: $finishReason")
            if (promptTokens != null || completionTokens != null || totalTokens != null) {
                if (isNotEmpty()) append(" | ")
                append("📊 Токены: ${totalTokens ?: "?"} (↑${promptTokens ?: "?"}/↓${completionTokens ?: "?"})")
            }
            if (responseTimeMs > 0) {
                if (isNotEmpty()) append(" | ")
                val timeStr = when {
                    responseTimeMs < 1000 -> "${responseTimeMs}мс"
                    responseTimeMs < 60000 -> "${responseTimeMs / 1000}.${(responseTimeMs % 1000) / 100}с"
                    else -> "${responseTimeMs / 60000}м ${(responseTimeMs % 60000) / 1000}с"
                }
                append("⏱️ $timeStr")
            }
        }
        if (metadata.isNotEmpty()) println("📈 $metadata")
    }

    fun clearHistory() {
        compressedAgent?.clearHistory()
        regularAgent?.clearHistory()
        strategicAgent?.clearHistory()
    }

    fun getHistorySize(): Int =
        compressedAgent?.getHistorySize() ?: regularAgent?.getHistorySize() ?: 0

    fun getTokenStats(): TokenStats {
        if (useStrategicAgent && strategicAgent != null) {
            try {
                val stats = strategicAgent!!.getTokenStats()
                if (stats.totalTokens > 0 || stats.requestCount > 0) {
                    return stats
                }
            } catch (_: Exception) {
            }
        }

        val stats =
            compressedAgent?.getTokenStats() ?: regularAgent?.getTokenStats() ?: TokenStats()

        return stats
    }

    fun getTokenHistory(): List<TokenSnapshot> {
        if (useStrategicAgent && strategicAgent != null) {
            try {
                return strategicAgent!!.getTokenHistory()
            } catch (_: Exception) {
            }
        }
        return compressedAgent?.getTokenHistory() ?: regularAgent?.getTokenHistory() ?: emptyList()
    }

    fun getContextWarning(): String {
        if (useStrategicAgent && strategicAgent != null) {
            try {
                val stats = strategicAgent!!.getTokenStats()
                val contextWindowSize = 131072
                val contextPercent = (stats.totalTokens.toDouble() / contextWindowSize) * 100
                return when {
                    contextPercent > 90 -> "🔴 КРИТИЧЕСКИ: ${stats.totalTokens}/131072 (${
                        "%.1f".format(contextPercent)
                    }%)"

                    contextPercent > 70 -> "⚠️ ВНИМАНИЕ: ${stats.totalTokens}/131072 (${
                        "%.1f".format(contextPercent)
                    }%)"

                    else -> "✅ Контекст в порядке: ${stats.totalTokens}/131072 (${
                        "%.1f".format(contextPercent)
                    }%)"
                }
            } catch (_: Exception) {
            }
        }
        return compressedAgent?.getContextWarning() ?: regularAgent?.getContextWarning() ?: ""
    }

    fun clearTokenStats() {
        compressedAgent?.clearTokenStats()
        regularAgent?.clearTokenStats()
        strategicAgent?.clearTokenStats()
    }
}
