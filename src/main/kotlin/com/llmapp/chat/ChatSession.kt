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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
    private val conversationHistory = mutableListOf<Pair<String, String>>()

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

        val mcpConnected = mcpIntegration?.isConnected() == true

        val effectivePrompt = if (mcpConnected) {
            "$userPrompt\n\n${mcpIntegration!!.getToolDescriptions()}"
        } else userPrompt

        val savedControl = if (mcpConnected && responseControl.enabled && (responseControl.maxTokens
                ?: 0) in 1..500
        ) {
            responseControl
        } else null
        if (savedControl != null) {
            setResponseControl(savedControl.copy(maxTokens = 1536, formatDescription = null))
        }

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
            conversationHistory.add(userPrompt to finalContent)
            if (conversationHistory.size > 10) {
                conversationHistory.removeAt(0)
            }
            return response.copy(content = finalContent)
        } catch (e: Exception) {
            throw Exception("Ошибка при обращении к LLM: ${e.message}", e)
        } finally {
            if (savedControl != null) {
                setResponseControl(savedControl)
            }
        }
    }

    private suspend fun handleMcpToolCalls(
        initialContent: String,
        originalUserPrompt: String
    ): String {
        println("\n═══════════════════════════════════════")
        println("🔍 MCP: Сырой ответ LLM:")
        println(initialContent.take(2000))
        println("═══════════════════════════════════════")
        var content = initialContent
        val allResults = mutableListOf<String>()
        run loop@{
            repeat(5) {
                val json = extractMcpCallJson(content)
                if (json == null) {
                    val hasMarker =
                        listOf("[MCP_CALL]", "[MCPCALL]", "[mcp_call]", "[mcpcall]").any {
                            content.contains(it, ignoreCase = true)
                        }
                    if (!hasMarker) {
                        val cleaned = stripToolCallArtifacts(content)
                        if (cleaned.isEmpty()) {
                            return@loop
                        }
                        if (allResults.isEmpty()) {
                            content = cleaned
                            return@loop
                        }
                        println("📦 MCP: Нет [MCP_CALL], но есть ${allResults.size} результатов — синтезирую финальный ответ")
                        content = cleaned
                        return@loop
                    }
                    println("⚠️ MCP: JSON обрезан, но [MCP_CALL] есть — отправляю на исправление")
                    content = buildString {
                        append(formatConversationHistory())
                        appendLine("Original user question: $originalUserPrompt")
                        appendLine()
                        appendLine("Your JSON was broken/incomplete. Make sure EVERY quote and brace is closed.")
                        appendLine("Here is what you sent (the broken part):")
                        appendLine(content.take(500))
                        appendLine()
                        appendLine("Fix the JSON and call the tool again using exactly this format:")
                        appendLine("[MCP_CALL]")
                        appendLine("""{"tool": "tool_name", "arguments": {"param": "value"}}""")
                        appendLine()
                        appendLine("For get_group, the parameter name is EXACTLY 'name' (not 'group', not 'letter').")
                    }
                    content = askMcpFollowUpSafe(content)
                    // fall through to next iteration
                    return@repeat
                }
                println("✅ MCP: Извлечён JSON: $json")
                val lastToolName = try {
                    Json.parseToJsonElement(json).jsonObject["tool"]?.jsonPrimitive?.content
                } catch (_: Exception) {
                    null
                }
                var result: String
                var errorMsg: String? = null
                try {
                    val r = mcpIntegration?.executeToolCall(json)
                    if (r == null) {
                        println("❌ MCP: executeToolCall вернул null")
                        val cleaned = stripToolCallArtifacts(content)
                        return cleaned.ifEmpty { content }
                    }
                    println("✅ MCP: Результат (${r.take(500)})")
                    if (r.startsWith("Error:") || r.startsWith("MCP tool error:")) {
                        println("⚠️ MCP: Результат содержит ошибку")
                        errorMsg = r
                    }
                    result = r
                } catch (e: Exception) {
                    println("❌ MCP: Ошибка выполнения: ${e.message}")
                    errorMsg = e.message
                    result = "MCP tool error: ${e.message}"
                }
                allResults.add(result)
                println("📋 MCP: Всего результатов: ${allResults.size}")

                if (errorMsg != null) {
                    println("🔄 MCP: Отправляю запрос на исправление ошибки")
                    val fixPrompt = buildString {
                        append(formatConversationHistory())
                        appendLine("Original user question: $originalUserPrompt")
                        appendLine()
                        appendLine("Error calling MCP tool: $errorMsg")
                        appendLine()
                        appendLine("The JSON you sent was: ${json.take(200)}")
                        appendLine()
                        appendLine("The correct format is:")
                        appendLine("[MCP_CALL]")
                        appendLine("""{"tool": "tool_name", "arguments": {"param": "value"}}""")
                        appendLine()
                        appendLine("Fix the JSON using the correct format above and call the tool ONE MORE TIME.")
                        appendLine("Do NOT change the parameter names — use exactly 'tool' and 'arguments'.")
                        appendLine("IMPORTANT: Do NOT call the same tool with the same arguments twice. Check what you already got from previous results.")
                    }
                    content = askMcpFollowUpSafe(fixPrompt)
                } else {
                    println("🔄 MCP: Отправляю follow-up запрос агенту (итерация ${allResults.size})")
                    val followUp = buildString {
                        append(formatConversationHistory())
                        appendLine("Original user question: $originalUserPrompt")
                        appendLine()
                        appendLine("=== DATA COLLECTED SO FAR ===")
                        for ((i, prev) in allResults.withIndex()) {
                            appendLine("--- Result ${i + 1} ---")
                            appendLine(
                                if (prev.length > 1000) prev.take(15000) else filterGamesForDisplay(
                                    prev,
                                    allResults
                                ).take(500)
                            )
                            appendLine()
                        }
                        appendLine("--- Latest result ---")
                        appendLine(
                            if (result.length > 1000) result.take(15000) else filterGamesForDisplay(
                                result,
                                allResults
                            ).take(500)
                        )
                        appendLine("=== END ===")
                        appendLine()
                        appendLine(getPipelineNextStep(lastToolName))
                        appendLine("Do NOT call the same tool with the same arguments twice — you already have its data above.")
                        appendLine(getMcpHints())
                    }
                    content = askMcpFollowUpSafe(followUp)
                }
            }
        }
        return synthesizeFinalAnswer(allResults, originalUserPrompt, content)
    }

    private fun getPipelineNextStep(lastToolName: String?): String {
        val names = mcpIntegration?.getToolNames()
            ?: return "Review ALL data above. If you still need more data, call [MCP_CALL]{\"tool\": \"tool_name\", \"arguments\": {...}}[/MCP_CALL]"
        val pipelineTools = setOf("search_data", "summarize_data", "save_data")
        val hasPipeline = names.toSet().containsAll(pipelineTools)
        if (!hasPipeline) return "Review ALL data above. If you still need more data, call [MCP_CALL]{\"tool\": \"tool_name\", \"arguments\": {...}}[/MCP_CALL]"

        val sb = StringBuilder()
        when (lastToolName) {
            "search_data" -> {
                sb.appendLine("You just called search_data. Now you MUST continue the pipeline.")
                sb.appendLine("IMPORTANT: Do NOT pass the search_data result as raw_data argument.")
                sb.appendLine("Just call summarize_data with EMPTY arguments — it will fetch data automatically.")
                sb.appendLine("Call format EXACTLY: [MCP_CALL]{\"tool\": \"summarize_data\", \"arguments\": {}}[/MCP_CALL]")
                sb.appendLine("CRITICAL: Do NOT answer the user yet. You MUST call summarize_data next.")
            }

            "summarize_data" -> {
                sb.appendLine("You just called summarize_data. Now you MUST continue the pipeline.")
                sb.appendLine("IMPORTANT: Do NOT pass the summarize_data result as summary_data argument.")
                sb.appendLine("Just call save_data with only the format argument — it will generate the summary automatically.")
                sb.appendLine("Call format EXACTLY: [MCP_CALL]{\"tool\": \"save_data\", \"arguments\": {\"format\": \"json\"}}[/MCP_CALL]")
                sb.appendLine("CRITICAL: Do NOT answer the user yet. You MUST call save_data next.")
            }

            "save_data" -> {
                sb.appendLine("Pipeline complete! You have called all three tools: search_data → summarize_data → save_data.")
                sb.appendLine("Now provide the final answer to the user in Russian. Show results of each step.")
                sb.appendLine("Do NOT call any more tools. Just answer using the data you have collected.")
            }

            else -> {
                sb.appendLine("Review ALL data above. If you still need more data, call [MCP_CALL]{\"tool\": \"tool_name\", \"arguments\": {...}}[/MCP_CALL]")
            }
        }
        return sb.toString()
    }

    private fun getMcpHints(): String {
        val names = mcpIntegration?.getToolNames() ?: return ""
        val hasPipeline = mcpIntegration?.hasPipelineTools() == true

        val hints = mutableListOf<String>()
        if (hasPipeline) {
            hints.add("PIPELINE: use search_data → summarize_data → save_data in order.")
            hints.add("  Pass the FULL output of search_data as raw_data to summarize_data.")
            hints.add("  Pass the FULL output of summarize_data as summary_data to save_data.")
        }
        if ("get_team" in names && "get_group" in names) {
            hints.add("Team → group standings: call get_team(to find group) → get_group(to find position).")
        }
        if ("get_games" in names) {
            hints.add("Goals/scorers/match results: call get_games.")
        }
        if ("get_stadiums" in names) {
            hints.add("Stadiums/capacity: call get_stadiums.")
        }
        return hints.joinToString("\n").ifEmpty { "HINT: call a tool that answers the question." }
    }

    private fun formatConversationHistory(): String {
        if (conversationHistory.size <= 1) return ""
        val recent = conversationHistory.dropLast(1).takeLast(3)
        return buildString {
            appendLine("### PREVIOUS CONVERSATION HISTORY ###")
            for ((q, a) in recent) {
                appendLine("User: $q")
                val stripped = a.replace(Regex("<[^>]*>"), "").take(300)
                appendLine("Assistant: $stripped")
                appendLine()
            }
            appendLine("### END OF HISTORY ###")
            appendLine()
        }
    }

    private fun stripToolCallArtifacts(text: String): String {
        val markerRegex = Regex("\\[MCP[\\w_]*CALL].*?(\\{.*?\\})?", setOf(RegexOption.IGNORE_CASE))
        return text.replace(markerRegex, "").trim()
    }

    private suspend fun synthesizeFinalAnswer(
        allResults: List<String>,
        originalUserPrompt: String,
        fallback: String
    ): String {
        if (allResults.isEmpty()) {
            println("⚠️ MCP: Нет результатов тулов. Возвращаю очищенный ответ LLM (было ${fallback.length} символов)")
            val cleaned = stripToolCallArtifacts(fallback)
            println("📄 MCP: Очищенный ответ (${cleaned.length} символов): ${cleaned.take(300)}")
            if (cleaned.length < fallback.length * 0.8) {
                return cleaned.ifEmpty { "Ошибка: LLM не смог корректно вызвать MCP инструмент. Попробуйте ещё раз." }
            }
            return fallback
        }
        println("📊 MCP: Собираю финальный ответ из ${allResults.size} результатов тулов")
        val hasPipeline = mcpIntegration?.hasPipelineTools() == true
        val prompt = buildString {
            append(formatConversationHistory())
            appendLine("Answer the user's question using ONLY the data below. Do NOT use your own knowledge.")
            appendLine()
            appendLine("User question: $originalUserPrompt")
            appendLine()
            appendLine("MCP tool results (use these, ignore your own knowledge):")
            if (hasPipeline && allResults.size >= 3) {
                val rawResult = allResults[0]
                val summaryResult = allResults[1]
                val saveResult = allResults[2]
                appendLine("--- Summary (summarize_data) ---")
                appendLine(summaryResult)
                appendLine()
                appendLine("--- Saved File (save_data) ---")
                appendLine(saveResult)
                appendLine()
                appendLine("--- Raw Data Loaded (search_data — summary only) ---")
                val gameCount = try {
                    Json.parseToJsonElement(rawResult).jsonObject["games"]?.jsonArray?.size ?: "?"
                } catch (_: Exception) {
                    "?"
                }
                appendLine("Total games loaded: $gameCount")
                appendLine()
            } else {
                allResults.forEachIndexed { i, r ->
                    appendLine("--- Result ${i + 1} ---")
                    appendLine(
                        if (r.length > 1000) r.take(25000) else filterGamesForDisplay(
                            r,
                            allResults
                        )
                    )
                }
            }
            appendLine()
            appendLine("Now provide the final answer in Russian based ONLY on the data above.")
            appendLine("CRITICAL: Do NOT output [MCP_CALL] markers. Do NOT call any tools. You already have all the data you need.")
            append("Do NOT guess or use your training data.")
        }
        val response = askMcpFollowUpSafe(prompt)
        if (response == "NO_MCP_CALL") {
            println("⚠️ MCP: LLM недоступен для синтеза, возвращаю сырые данные")
            val teamResult = allResults.firstOrNull()
            return teamResult ?: fallback
        }
        return response
    }

    private fun filterGamesForDisplay(rawResult: String, allResults: List<String>): String {
        val json = Json { ignoreUnknownKeys = true }
        val root = try {
            json.parseToJsonElement(rawResult).jsonObject
        } catch (_: Exception) {
            null
        }
        if (root == null) return rawResult.take(3000)

        val relevantGroups = mutableSetOf<String>()
        for (prev in allResults.dropLast(1)) {
            val prevJson = try {
                json.parseToJsonElement(prev).jsonObject
            } catch (_: Exception) {
                null
            }
            val groupName = prevJson?.get("group")?.jsonObject?.get("name")?.jsonPrimitive?.content
            val groupsField =
                prevJson?.get("team")?.jsonObject?.get("groups")?.jsonPrimitive?.content
            if (groupName != null) relevantGroups.add(groupName)
            if (groupsField != null) relevantGroups.add(groupsField)
        }

        if (relevantGroups.isEmpty()) return rawResult.take(3000)

        val gamesObj = root["games"]?.jsonArray
        if (gamesObj != null) {
            val filtered = gamesObj.filter { game ->
                val obj = game.jsonObject
                val g = obj["group"]?.jsonPrimitive?.content
                g in relevantGroups
            }
            if (filtered.isEmpty()) return rawResult.take(3000)
            fun j(v: JsonElement?): String = v?.jsonPrimitive?.content ?: "null"
            val compact = buildString {
                appendLine("{ \"games\": [")
                filtered.forEachIndexed { i, game ->
                    val o = game.jsonObject
                    append(
                        "  { \"id\": ${j(o["id"])}, \"home_team_name_en\": \"${j(o["home_team_name_en"])}\", \"away_team_name_en\": \"${
                            j(
                                o["away_team_name_en"]
                            )
                        }\", \"home_score\": ${j(o["home_score"])}, \"away_score\": ${j(o["away_score"])}, \"home_scorers\": ${
                            j(
                                o["home_scorers"]
                            )
                        }, \"away_scorers\": ${j(o["away_scorers"])}, \"group\": \"${j(o["group"])}\", \"matchday\": ${
                            j(
                                o["matchday"]
                            )
                        }, \"local_date\": \"${j(o["local_date"])}\", \"finished\": \"${j(o["finished"])}\" }"
                    )
                    if (i < filtered.lastIndex) append(",")
                    appendLine()
                }
                append("]}")
            }
            return compact
        }

        val groupsObj = root["groups"]?.jsonArray
        if (groupsObj != null) {
            val filtered = groupsObj.filter { g ->
                g.jsonObject["name"]?.jsonPrimitive?.content in relevantGroups
            }
            if (filtered.isEmpty()) return rawResult.take(3000)
            val compact = buildString {
                appendLine("{ \"groups\": [")
                filtered.forEachIndexed { i, g ->
                    append("  $g")
                    if (i < filtered.lastIndex) append(",")
                    appendLine()
                }
                append("]}")
            }
            return compact
        }

        return rawResult.take(3000)
    }

    private fun extractMcpCallJson(text: String): String? {
        val json = Json { ignoreUnknownKeys = true }
        val markers = listOf("[MCP_CALL]", "[MCPCALL]", "[mcp_call]", "[mcpcall]")
        for (marker in markers) {
            val idx = text.indexOf(marker, ignoreCase = true)
            if (idx == -1) continue
            println("🔎 MCP: Найден маркер '$marker' на позиции $idx")
            val after = text.substring(idx + marker.length).trim()
            println("🔎 MCP: Текст после маркера (первые 200): ${after.take(200)}")
            var searchFrom = 0
            while (true) {
                val braceStart = after.indexOf('{', searchFrom)
                if (braceStart == -1) {
                    println("⚠️ MCP: '{' не найден после маркера")
                    break
                }
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
                                    println("✅ MCP: Валидный JSON найден: ${candidate.take(100)}")
                                    return candidate
                                } catch (_: Exception) {
                                    println(
                                        "⚠️ MCP: JSON невалиден, ищу дальше: ${
                                            candidate.take(
                                                100
                                            )
                                        }"
                                    )
                                    searchFrom = braceStart + 1
                                    break
                                }
                            }
                        }
                    }
                }
                if (depth > 0) {
                    // JSON обрезан (нет закрывающих скобок). Игнорируем и ищем следующий {.
                    println("⚠️ MCP: JSON обрезан (depth=$depth), пропускаю и ищу следующий {")
                    searchFrom = braceStart + 1
                    continue
                }
            }
        }
        println("❌ MCP: Ни одного валидного JSON не найдено")
        return null
    }

    private suspend fun askMcpFollowUpSafe(prompt: String): String {
        return try {
            askMcpFollowUp(prompt)
        } catch (e: Exception) {
            println("⚠️ MCP: Follow-up не удался (${e.message?.take(200)}), синтезирую из имеющихся данных")
            "NO_MCP_CALL"
        }
    }

    private suspend fun askMcpFollowUp(prompt: String): String {
        println("🔄 LLM: Отправляю follow-up запрос (${prompt.length} символов)")
        println("📝 LLM: Промт (первые 500): ${prompt.take(500)}")
        val client = OpenRouterClient(currentApiKey)
        val request = OpenRouterRequest(
            model = currentModel,
            messages = listOf(
                ChatMessage("system", systemPrompt),
                ChatMessage("user", prompt)
            ),
            maxTokens = 1536,
            skipContextOptimization = true
        )
        val response = client.sendRequest(request)
        response.error?.let {
            println("❌ LLM: Ошибка follow-up: ${it.message}")
            throw Exception(it.message)
        }
        val content = response.choices?.firstOrNull()?.message?.content ?: "No response"
        println("✅ LLM: Follow-up ответ (${content.length} символов): ${content.take(300)}")
        return content
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
        conversationHistory.clear()
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
