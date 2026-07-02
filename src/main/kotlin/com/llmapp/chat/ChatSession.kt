package com.llmapp.chat

import com.llmapp.agent.CompressedLLMAgent
import com.llmapp.agent.LLMAgent
import com.llmapp.agent.StrategicLLMAgent
import com.llmapp.agent.TokenSnapshot
import com.llmapp.api.ClientFactory
import com.llmapp.api.RouterClient
import com.llmapp.mcp.McpIntegration
import com.llmapp.mcp.McpIntegration.McpToolInfo
import com.llmapp.model.ChatMessage
import com.llmapp.model.ResponseControl
import com.llmapp.model.RouterRequest
import com.llmapp.model.TokenStats
import com.llmapp.rag.RAGEnhancer
import com.llmapp.rag.domain.RagAnswer
import kotlinx.serialization.json.Json
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
    private val summarizeEvery: Int = 6,
    val synthesisRetryModel: String? = "nvidia/nemotron-3-super-120b-a12b:free"
) {
    var logListener: ((String) -> Unit)? = null
    private fun log(msg: String) {
        println(msg)
        logListener?.invoke(msg)
    }

    private var strategicAgent: StrategicLLMAgent? = null
    private var useStrategicAgent = true
    private var currentApiKey = apiKey
    private val conversationHistory = mutableListOf<Pair<String, String>>()

    var dataIntegration: McpIntegration? = null
    var pipelineIntegration: McpIntegration? = null

    var ragEnabled: Boolean = false
    val ragEnhancer: RAGEnhancer by lazy { RAGEnhancer() }

    fun configureRag(
        enabled: Boolean,
        mode: com.llmapp.rag.RagMode = com.llmapp.rag.RagMode.BASIC,
        rerankerType: com.llmapp.rag.domain.RerankerType = com.llmapp.rag.domain.RerankerType.SIMILARITY_THRESHOLD,
        threshold: Float = 0.3f,
        topK: Int = 5,
        topKBefore: Int = 20,
        topKAfter: Int = 5,
    ) {
        ragEnabled = enabled
        ragEnhancer.mode = mode
        ragEnhancer.topK = topK
        ragEnhancer.rerankerConfig = com.llmapp.rag.domain.RerankerConfig(
            type = rerankerType,
            similarityThreshold = threshold,
            topKBefore = topKBefore,
            topKAfter = topKAfter,
        )
    }

    private val dataToolNames = setOf(
        "get_groups", "get_group", "get_teams", "get_team",
        "get_games", "get_game", "get_stadiums", "get_stadium",
        "search_all", "search_games", "search_teams", "search_groups"
    )

    private fun isAnyMcpConnected(): Boolean =
        dataIntegration?.isConnected() == true || pipelineIntegration?.isConnected() == true

    private fun getToolNames(): Set<String> {
        val names = mutableSetOf<String>()
        dataIntegration?.getToolNames()?.let { names.addAll(it) }
        pipelineIntegration?.getToolNames()?.let { names.addAll(it) }
        return names
    }

    private fun hasPipelineTools(): Boolean =
        pipelineIntegration?.hasPipelineTools() == true

    private fun getCombinedToolDescriptions(): String {
        val allTools = mutableListOf<McpToolInfo>()
        dataIntegration?.getTools()?.let { allTools.addAll(it) }
        pipelineIntegration?.getTools()?.let { allTools.addAll(it) }
        if (allTools.isEmpty()) return ""

        val hasSpecific = allTools.any { it.name in setOf("get_groups", "get_teams", "get_games") }
        val visibleTools = allTools.filter { tool ->
            !(hasSpecific && tool.name in setOf(
                "search_all",
                "search_games",
                "search_teams",
                "search_groups"
            ))
        }
        if (visibleTools.isEmpty()) return ""

        return buildString {
            appendLine("ВАЖНО: Имена параметров должны быть ТОЧНО как указано ниже. Не угадывай.")
            appendLine()
            appendLine("=== ДОСТУПНЫЕ ИНСТРУМЕНТЫ ===")
            appendLine()
            for (tool in visibleTools) {
                val req = tool.parameters.filter { it.name in (tool.requiredParams ?: emptyList()) }
                val opt =
                    tool.parameters.filter { it.name !in (tool.requiredParams ?: emptyList()) }
                appendLine("--- ${tool.name} ---")
                if (req.isNotEmpty()) {
                    val args = req.joinToString(", ") { "\"${it.name}\": \"...\"" }
                    appendLine("  Пример: {\"tool\": \"${tool.name}\", \"arguments\": {$args}}")
                } else {
                    appendLine("  Пример: {\"tool\": \"${tool.name}\", \"arguments\": {}}")
                }
                for (p in req) {
                    appendLine("  Параметр \"${p.name}\" (обязательный): ${p.description}")
                }
                for (p in opt) {
                    appendLine("  Параметр \"${p.name}\" (опциональный): ${p.description}")
                }
                if (tool.name == "get_group") {
                    appendLine("  ВНИМАНИЕ: Параметр называется \"name\", а НЕ \"group\". Передавай {\"name\": \"A\"}")
                }
                appendLine()
            }
            appendLine("=== ПРАВИЛА ===")
            appendLine("- Первое сообщение = [MCP_CALL]...[/MCP_CALL]")
            appendLine("- Один инструмент за раз.")
            appendLine("- Читай описания тулов и решай сам, какой вызвать.")
            appendLine("- Когда данных достаточно — ответь на русском.")
        }
    }


    private fun getIntegrationForTool(toolName: String): McpIntegration? = when {
        toolName in dataToolNames -> dataIntegration
        pipelineIntegration?.getToolNames()?.contains(toolName) == true -> pipelineIntegration
        else -> dataIntegration?.takeIf { it.getToolNames().contains(toolName) }
            ?: pipelineIntegration?.takeIf { it.getToolNames().contains(toolName) }
    }

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

        val mcpConnected = isAnyMcpConnected()

        if (mcpConnected) {
            dataIntegration?.refreshTools()
            pipelineIntegration?.refreshTools()
        }

        val mcpSystemPrompt = if (mcpConnected) {
            buildString {
                appendLine("ТЫ В РЕЖИМЕ ВЫЗОВА MCP ИНСТРУМЕНТОВ.")
                appendLine("ОТВЕЧАЙ ТОЛЬКО НА РУССКОМ ЯЗЫКЕ. НИ СЛОВА ПО-АНГЛИЙСКИ.")
                appendLine("НЕ ПИШИ НИЧЕГО, КРОМЕ [MCP_CALL] БЛОКА ИЛИ РУССКОГО ОТВЕТА.")
                appendLine("НЕ ДУМАЙ ВСЛУХ. НЕ ОБЪЯСНЯЙ. НЕ ПЛАНИРУЙ.")
                appendLine("ПРОСТО ВЫЗОВИ ИНСТРУМЕНТ ИЛИ ОТВЕТЬ ПО-РУССКИ.")
                appendLine("ДЛЯ ПОИСКА ДАННЫХ ИСПОЛЬЗУЙ search_all ИЛИ get_groups/get_games.")
                appendLine()
                append(getCombinedToolDescriptions())
            }
        } else null

        mcpSystemPrompt.let { prompt ->
            if (prompt != null) {
                strategicAgent?.updateSystemPrompt(prompt)
                compressedAgent?.updateSystemPrompt(prompt)
                regularAgent?.updateSystemPrompt(prompt)
            }
        }

        var augmentedUserPrompt = userPrompt
        if (ragEnabled && !mcpConnected) {
            try {
                ragEnhancer.ensureIndexLoaded()
                val ragAnswer: RagAnswer = ragEnhancer.searchWithStructuredContext(userPrompt)
                if (ragAnswer.chunks.isNotEmpty()) {
                    log("📚 RAG: Найдено ${ragAnswer.chunks.size} релевантных чанков")
                    augmentedUserPrompt = buildStructuredRagPrompt(userPrompt, ragAnswer)
                } else if (ragAnswer.shouldSayIdontKnow) {
                    log("📚 RAG: Релевантность ниже порога — возвращаю 'не знаю'")
                    return ChatResponse(
                        content = ragAnswer.iDontKnowMessage
                            ?: "Я не нашёл релевантной информации в базе знаний. Уточните запрос или задайте другой вопрос.",
                        promptTokens = 0,
                        completionTokens = 0,
                        totalTokens = 0,
                        finishReason = "rag_low_relevance",
                        responseTimeMs = 0
                    )
                }
            } catch (e: Exception) {
                log("⚠️ RAG: Ошибка обогащения: ${e.message}")
            }
        }

        val effectivePrompt = if (mcpConnected) {
            "START WITH [MCP_CALL] IMMEDIATELY. NO TEXT BEFORE IT.\n\n$augmentedUserPrompt"
        } else augmentedUserPrompt

        val savedControl = if (mcpConnected && responseControl.enabled) {
            responseControl
        } else null
        if (savedControl != null) {
            setResponseControl(
                savedControl.copy(
                    maxTokens = 2048,
                    formatDescription = getToolFormatReminder(),
                    stopSequences = listOf("[/MCP_CALL]"),
                    temperature = 0.0
                )
            )
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
            if (mcpSystemPrompt != null) {
                strategicAgent?.updateSystemPrompt(systemPrompt)
                compressedAgent?.updateSystemPrompt(systemPrompt)
                regularAgent?.updateSystemPrompt(systemPrompt)
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
        val calledTools = mutableSetOf<String>()
        val errorCounts = mutableMapOf<String, Int>()
        run loop@{
            repeat(8) {
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
                        val hasRussianAnswer = content.contains("---ОТВЕТ---")
                        if (hasRussianAnswer && cleaned.length > 100) {
                            return stripThinkingPrefix(content)
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
                        // Detect intended tool from malformed call
                        val afterMcpCall = content.substringAfter("[MCP_CALL]", "")
                            .substringAfter("[mcp_call]", "")
                            .trim()
                        val knownTools = getToolNames()
                        // Extract first balanced JSON object from the text
                        val firstJsonBlock = run {
                            val start = afterMcpCall.indexOf('{')
                            if (start < 0) null
                            else {
                                var depth = 0
                                var inStr = false
                                var esc = false
                                var result: String? = null
                                for (i in start until afterMcpCall.length) {
                                    val c = afterMcpCall[i]
                                    when {
                                        esc -> esc = false
                                        c == '\\' -> esc = true
                                        c == '"' -> inStr = !inStr
                                        !inStr -> {
                                            if (c == '{') depth++
                                            else if (c == '}') {
                                                depth--
                                                if (depth == 0) {
                                                    result = afterMcpCall.substring(start, i + 1)
                                                    break
                                                }
                                            }
                                        }
                                    }
                                }
                                result
                            }
                        }
                        // Check if JSON uses "name" instead of "tool"
                        val json = Json { ignoreUnknownKeys = true; isLenient = true }
                        val maybeJson = if (firstJsonBlock != null) {
                            try {
                                val el = json.parseToJsonElement(firstJsonBlock).jsonObject
                                if (el.containsKey("name") && !el.containsKey("tool")) {
                                    el["name"]?.jsonPrimitive?.content
                                } else null
                            } catch (_: Exception) {
                                null
                            }
                        } else null
                        if (maybeJson != null && maybeJson in knownTools) {
                            appendLine("You used \"name\" instead of \"tool\". The key MUST be \"tool\", not \"name\".")
                            appendLine("Use EXACTLY this:")
                            appendLine("[MCP_CALL]")
                            appendLine("""{"tool": "$maybeJson", "arguments": {}}""")
                            appendLine("[/MCP_CALL]")
                            appendLine("Do NOT change the key name. Copy exactly.")
                        } else if (maybeJson != null) {
                            val matchedTool = knownTools.firstOrNull {
                                it.contains(maybeJson, ignoreCase = true) ||
                                        maybeJson.contains(it, ignoreCase = true) ||
                                        // Common prefix heuristic: if 60%+ of either string matches
                                        run {
                                            val minLen = minOf(it.length, maybeJson.length)
                                            val prefixLen = it.zip(maybeJson).takeWhile { (a, b) ->
                                                a.equals(
                                                    b,
                                                    ignoreCase = true
                                                )
                                            }.count()
                                            prefixLen >= minLen * 0.6 || prefixLen >= 5
                                        }
                            }
                            appendLine("You tried to call '$maybeJson' which does not exist.")
                            if (matchedTool != null) {
                                appendLine("Did you mean '$matchedTool'?")
                                appendLine("Use EXACTLY this:")
                                appendLine("[MCP_CALL]")
                                appendLine("""{"tool": "$matchedTool", "arguments": {}}""")
                                appendLine("[/MCP_CALL]")
                            } else {
                                appendLine(
                                    "Available tools: ${
                                        knownTools.sorted().joinToString(", ")
                                    }"
                                )
                                appendLine("Use EXACTLY this format:")
                                appendLine("[MCP_CALL]")
                                appendLine("""{"tool": "tool_name", "arguments": {}}""")
                                appendLine("[/MCP_CALL]")
                            }
                        } else {
                            val intendedTool =
                                afterMcpCall.substringBefore(" ").substringBefore("{").trim()
                                    .ifEmpty { null }
                            if (intendedTool != null && intendedTool !in knownTools) {
                                val matchedTool = knownTools.firstOrNull {
                                    it.contains(
                                        intendedTool,
                                        ignoreCase = true
                                    )
                                }
                                if (matchedTool != null) {
                                    appendLine("You tried to call '$intendedTool'. Did you mean '$matchedTool'?")
                                    appendLine("Use EXACTLY this:")
                                    appendLine("[MCP_CALL]")
                                    appendLine("""{"tool": "$matchedTool", "arguments": {}}""")
                                    appendLine("[/MCP_CALL]")
                                } else {
                                    appendLine("Use EXACTLY this format for the tool call:")
                                    appendLine("[MCP_CALL]")
                                    appendLine("""{"tool": "tool_name", "arguments": {"param": "value"}}""")
                                    appendLine("[/MCP_CALL]")
                                }
                            } else if (intendedTool != null) {
                                appendLine("You wrote '$intendedTool' after [MCP_CALL] but it must be JSON. Use EXACTLY this:")
                                appendLine("[MCP_CALL]")
                                appendLine("""{"tool": "$intendedTool", "arguments": {}}""")
                                appendLine("[/MCP_CALL]")
                                appendLine("Copy this exact line. Do NOT change anything.")
                            } else {
                                appendLine("Use EXACTLY this format for the tool call:")
                                appendLine("[MCP_CALL]")
                                appendLine("""{"tool": "tool_name", "arguments": {"param": "value"}}""")
                                appendLine("[/MCP_CALL]")
                            }
                        }
                    }
                    content = askMcpFollowUpSafe(content)
                    // fall through to next iteration
                    return@repeat
                }
                println("✅ MCP: Извлечён JSON: $json")
                var result: String
                var errorMsg: String? = null
                var r: String? = null
                val toolName = try {
                    Json.parseToJsonElement(json).jsonObject["tool"]?.jsonPrimitive?.content
                } catch (_: Exception) {
                    null
                }
                try {
                    val integration =
                        if (toolName != null) getIntegrationForTool(toolName) else null
                    if (integration == null && toolName != null) {
                        println("❌ MCP: Неизвестный инструмент '$toolName'")
                        val fixPrompt = buildString {
                            appendLine("Original user question: $originalUserPrompt")
                            appendLine()
                            appendLine(
                                "Tool '$toolName' does not exist. Available tools: ${
                                    getToolNames().sorted().joinToString(", ")
                                }"
                            )
                            appendLine("Call one of the available tools instead.")
                            appendLine("If you already have all the data you need, just answer in Russian without [MCP_CALL].")
                        }
                        content = askMcpFollowUpSafe(fixPrompt)
                        return@repeat
                    }
                    r = integration?.executeToolCall(json)
                    if (r == null) {
                        println("❌ MCP: executeToolCall вернул null")
                        val cleaned = stripToolCallArtifacts(content)
                        return cleaned.ifEmpty { content }
                    }
                    log("✅ MCP: Результат (${r.take(500)})")
                    if (r.startsWith("Error:") || r.startsWith("MCP tool error:")) {
                        println("⚠️ MCP: Результат содержит ошибку")
                        errorMsg = r
                    }
                    result = r
                } catch (e: Exception) {
                    log("❌ MCP: Ошибка выполнения: ${e.message}")
                    errorMsg = e.message
                    result = "MCP tool error: ${e.message}"
                }
                allResults.add(result)
                log("📋 MCP: Всего результатов: ${allResults.size}")
                val beforeWasAlreadyCalled = toolName != null && toolName in calledTools
                if (toolName != null && r != null && !r.startsWith("Error:") && !r.startsWith("MCP tool error:")) {
                    calledTools.add(toolName)
                    log("📋 MCP: Вызванные инструменты: $calledTools")
                }
                val errorCount =
                    if (toolName != null) errorCounts.getOrPut(toolName) { 0 } + if (errorMsg != null) 1 else 0 else 0
                if (errorMsg != null && toolName != null) errorCounts[toolName] = errorCount

                if (errorMsg != null) {
                    println("🔄 MCP: Отправляю запрос на исправление ошибки")
                    if (errorCount >= 3) {
                        println("⚠️ MCP: Слишком много ошибок для '$toolName', предлагаю альтернативу")
                        content = buildString {
                            appendLine("Original user question: $originalUserPrompt")
                            appendLine()
                            appendLine("Tool '$toolName' keeps failing. Try a DIFFERENT approach:")
                            if (toolName == "summarize_data") {
                                appendLine("- Save your data directly as JSON with save_data (format: \"json\")")
                                appendLine("- Or answer the user in Russian with the data you have.")
                            } else {
                                appendLine("- Use get_groups (returns ALL groups at once) instead of get_group")
                                appendLine("- Use get_games to get all matches")
                            }
                            appendLine("- If you already have all data, answer the user in Russian.")
                        }
                        return@repeat
                    }
                    val fixPrompt = buildString {
                        append(formatConversationHistory())
                        appendLine("Original user question: $originalUserPrompt")
                        appendLine()
                        appendLine("Error calling MCP tool: $errorMsg")
                        appendLine()
                        appendLine("The JSON you sent was: ${json.take(200)}")
                        appendLine()
                        when (toolName) {
                            "get_group" -> {
                                appendLine("CRITICAL: For get_group, the parameter name is EXACTLY \"name\", NOT \"group\".")
                                appendLine("Example: {\"tool\": \"get_group\", \"arguments\": {\"name\": \"A\"}}")
                                appendLine("Do NOT change 'name' to 'group' or any other name.")
                            }

                            "summarize_data" -> {
                                val rawDataMissing =
                                    errorMsg.contains("raw_data", ignoreCase = true)
                                val fieldsMissing =
                                    errorMsg.contains("required for type", ignoreCase = true)
                                when {
                                    rawDataMissing && !fieldsMissing -> {
                                        appendLine("CRITICAL: The parameter name is EXACTLY \"raw_data\".")
                                        appendLine("Example: {\"tool\": \"summarize_data\", \"arguments\": {\"raw_data\": \"{\\\"groups\\\": [...]}\"}}")
                                    }

                                    fieldsMissing -> {
                                        appendLine("ERROR: Your raw_data JSON is missing required fields. Each team object MUST have ALL 10 fields:")
                                        appendLine("  team_id, mp, w, l, d, pts, gf, ga, gd, _id")
                                        appendLine("Copy the EXACT team JSON from the get_groups output. Do NOT omit any field.")
                                    }

                                    else -> {
                                        appendLine("summarize_data requires \"raw_data\" — a JSON string with 'games' and/or 'groups' arrays.")
                                        appendLine("All team objects must have ALL 10 fields: team_id, mp, w, l, d, pts, gf, ga, gd, _id.")
                                    }
                                }
                            }

                            else -> {
                                appendLine("The correct format is:")
                                appendLine("[MCP_CALL]")
                                appendLine("""{"tool": "tool_name", "arguments": {"param": "value"}}""")
                                appendLine("[/MCP_CALL]")
                                appendLine()
                                appendLine("Fix the JSON using the correct format above and call the tool ONE MORE TIME.")
                                appendLine("Do NOT change the parameter names — use exactly 'tool' and 'arguments'.")
                            }
                        }
                        appendLine("IMPORTANT: Do NOT call the same tool with the same arguments twice. Check what you already got from previous results.")
                    }
                    content = askMcpFollowUpSafe(fixPrompt)
                } else {
                    log("🔄 MCP: Отправляю follow-up запрос агенту (итерация ${allResults.size})")
                    if (beforeWasAlreadyCalled && allResults.size > 1) {
                        log("⚠️ MCP: Инструмент '$toolName' уже вызывался ранее")
                        val hasSave = pipelineIntegration?.getToolNames()
                            ?.contains("save_data") == true
                        val hasSummarize = pipelineIntegration?.getToolNames()
                            ?.contains("summarize_data") == true
                        val failedTooMany = (errorCounts["summarize_data"] ?: 0) >= 2
                        val needPipeline =
                            hasSummarize && "summarize_data" !in calledTools && !failedTooMany
                        val needSave = hasSave && "save_data" !in calledTools
                        val dataToolNames = dataIntegration?.getToolNames()?.toSet() ?: emptySet()
                        val uncalledDataTools = dataToolNames - calledTools
                        val hasCoreData = "get_groups" in calledTools && "get_teams" in calledTools
                        val suggestedTools = if (hasCoreData) emptySet() else uncalledDataTools
                        val estimatedSize =
                            allResults.sumOf { it.length.coerceAtMost(25000) } + 2000
                        val forceSynthesis = estimatedSize > 25000
                        val repeatPrompt = buildString {
                            appendLine("Вопрос: $originalUserPrompt")
                            appendLine()
                            appendLine("=== РЕЗУЛЬТАТЫ MCP ===")
                            for ((i, prev) in allResults.withIndex()) {
                                appendLine("--- Результат ${i + 1} ---")
                                appendLine(prev.take(if (forceSynthesis) 10000 else 25000))
                                appendLine()
                            }
                            appendLine("=== КОНЕЦ ===")
                            appendLine()
                            if (forceSynthesis) {
                                appendLine("У ТЕБЯ УЖЕ ЕСТЬ ВСЕ ДАННЫЕ. НЕ ВЫЗЫВАЙ ИНСТРУМЕНТЫ.")
                                appendLine("---ОТВЕТ---")
                                appendLine("ОТВЕЧАЙ НА РУССКОМ. НИ СЛОВА АНГЛИЙСКОГО. НЕ ДУМАЙ. ПРОСТО ТАБЛИЦЫ.")
                            } else if (suggestedTools.isNotEmpty()) {
                                appendLine(
                                    "Инструмент '$toolName' уже вызывался. Тебе ещё нужно вызвать: ${
                                        suggestedTools.sorted().joinToString(", ")
                                    }."
                                )
                                appendLine("Вызови их через [MCP_CALL], затем проанализируй и сохрани результат save_data.")
                            } else if (needPipeline) {
                                appendLine("Инструмент '$toolName' уже вызывался. Посмотри на описание других доступных инструментов и реши, что вызвать следующим.")
                            } else if (needSave) {
                                appendLine("У ТЕБЯ УЖЕ ВСЕ ДАННЫЕ. Вызови save_data для сохранения аналитической сводки (format: \"md\").")
                                appendLine("Если не хочешь сохранять — ответь на русском.")
                            } else {
                                appendLine("У ТЕБЯ УЖЕ ЕСТЬ ВСЕ ДАННЫЕ. НЕ ВЫЗЫВАЙ ИНСТРУМЕНТЫ.")
                                appendLine("---ОТВЕТ---")
                                appendLine("ОТВЕЧАЙ НА РУССКОМ. НИ СЛОВА АНГЛИЙСКОГО. НЕ ДУМАЙ. ПРОСТО ТАБЛИЦЫ.")
                            }
                        }
                        content = askMcpFollowUpSafe(repeatPrompt, isFinalSynthesis = true)
                        if (!forceSynthesis && (suggestedTools.isNotEmpty() || needPipeline || needSave) && Regex(
                                "\\[MCP[_A-Z]*CALL]",
                                setOf(RegexOption.IGNORE_CASE)
                            ).containsMatchIn(content)
                        ) {
                            return@loop
                        }
                        val hasRussianAnswer = content.contains("---ОТВЕТ---")
                        val hasCyrillic = Regex("[а-яёА-ЯЁ]").containsMatchIn(content)
                        val hasMcpCall = Regex(
                            "\\[MCP[_A-Z]*CALL]",
                            setOf(RegexOption.IGNORE_CASE)
                        ).containsMatchIn(content)
                        val cleaned = stripToolCallArtifacts(content)
                        val isValidAnswer =
                            hasRussianAnswer || (!hasMcpCall && hasCyrillic && cleaned.length > 200)
                        if (isValidAnswer) {
                            return stripThinkingPrefix(content)
                        }
                        // Bad greeting or English - retry once with stronger prompt
                        log("⚠️ MCP: Ответ без ---ОТВЕТ--- (${content.take(100)}), повторный запрос")
                        val retryPrompt = buildString {
                            appendLine("Вопрос: $originalUserPrompt")
                            appendLine()
                            appendLine("=== РЕЗУЛЬТАТЫ MCP ===")
                            for ((i, prev) in allResults.withIndex()) {
                                appendLine("--- Результат ${i + 1} ---")
                                val short = prev.take(25000)
                                appendLine(short)
                                appendLine()
                            }
                            appendLine("=== КОНЕЦ ===")
                            appendLine()
                            if (suggestedTools.isNotEmpty()) {
                                appendLine(
                                    "НЕ ПИШИ АНГЛИЙСКИЙ. Вызови нужные инструменты из списка: ${
                                        suggestedTools.sorted().joinToString(", ")
                                    }."
                                )
                                appendLine("После получения всех данных ответь на русском.")
                                appendLine("Формат: [MCP_CALL]{\"tool\": \"имя\", \"arguments\": {}}[/MCP_CALL]")
                            } else {
                                appendLine("НЕ ВЫЗЫВАЙ ИНСТРУМЕНТЫ. У ТЕБЯ УЖЕ ВСЕ ДАННЫЕ.")
                                appendLine("ТЫ ДОЛЖЕН ОТВЕТИТЬ НА РУССКОМ ЯЗЫКЕ. НЕ ПИШИ АНГЛИЙСКИЙ.")
                                appendLine("НЕ ЗДОРОВАЙСЯ. НЕ СПРАШИВАЙ. НЕ ПИШИ [MCP_CALL]. ПРОСТО ВЫВЕДИ ТАБЛИЦЫ.")
                                appendLine("Используй НАЗВАНИЯ КОМАНД (из games данных), а не team_id.")
                            }
                            appendLine("---ОТВЕТ---")
                            appendLine("Сразу после ---ОТВЕТ--- напиши таблицы групп и анализ квалификации.")
                            appendLine("Используй ТОЛЬКО данные из результатов MCP выше. НЕ ВЫЗЫВАЙ ИНСТРУМЕНТЫ.")
                        }
                        content = askMcpFollowUpSafe(retryPrompt, isFinalSynthesis = true)
                        val hasCyrillic2 = Regex("[а-яёА-ЯЁ]").containsMatchIn(content)
                        val hasMcpCall2 = Regex(
                            "\\[MCP[_A-Z]*CALL]",
                            setOf(RegexOption.IGNORE_CASE)
                        ).containsMatchIn(content)
                        if ((content.contains("---ОТВЕТ---") || (!hasMcpCall2 && hasCyrillic2)) && stripToolCallArtifacts(
                                content
                            ).length > 100
                        ) {
                            return stripThinkingPrefix(content)
                        }
                        return@loop
                    }
                    val followUp = buildString {
                        append(formatConversationHistory())
                        appendLine("Вопрос: $originalUserPrompt")
                        appendLine()
                        appendLine("=== СОБРАННЫЕ ДАННЫЕ ===")
                        val totalLen =
                            allResults.sumOf { it.length } + (allResults.size * 50) + 5000
                        for ((i, prev) in allResults.withIndex()) {
                            appendLine("--- Результат ${i + 1} ---")
                            val limit = when {
                                totalLen > 35000 -> 5000
                                totalLen > 20000 -> 12000
                                else -> 25000
                            }
                            appendLine(
                                if (prev.length > 1000) prev.take(limit) else compactData(prev).take(
                                    500
                                )
                            )
                            appendLine()
                        }
                        appendLine("--- Последний результат ---")
                        val limit = when {
                            totalLen > 35000 -> 5000
                            totalLen > 20000 -> 12000
                            else -> 25000
                        }
                        appendLine(
                            if (result.length > 1000) result.take(limit) else compactData(result).take(
                                500
                            )
                        )
                        appendLine("=== КОНЕЦ ===")
                        appendLine()
                        appendLine(getPipelineNextStep())
                        appendLine(getMcpHints())
                        appendLine("---ОТВЕТ---")
                        appendLine("Если нужно вызвать инструмент, напиши [MCP_CALL]. Иначе ---ОТВЕТ--- и ответ на РУССКОМ.")
                    }
                    content = askMcpFollowUpSafe(followUp)
                }
            }
        }
        return synthesizeFinalAnswer(allResults, originalUserPrompt, content)
    }

    private fun getPipelineNextStep(): String {
        val names = getToolNames()
        if (names.isEmpty()) return "Проверь ВСЕ данные выше. Если нужно больше данных, вызывай [MCP_CALL]{\"tool\": \"tool_name\", \"arguments\": {...}}[/MCP_CALL]"
        return "Решай сам, какой инструмент вызвать следующим, основываясь на их описаниях."
    }

    private fun getToolFormatReminder(): String {
        val tools = getCombinedToolDescriptions().ifEmpty { "Нет доступных инструментов." }
        return buildString {
            appendLine(tools)
            appendLine()
            appendLine("Формат вызова: [MCP_CALL]{\"tool\": \"tool_name\", \"arguments\": {...}}[/MCP_CALL]")
            appendLine()
            appendLine("ВАЖНО:")
            appendLine("- Начинай ОТВЕТ СРАЗУ С [MCP_CALL]. Никакого текста до.")
            appendLine("- Не думай вслух. Не объясняй. Не планируй.")
            appendLine("- Просто вызови инструмент. Не пиши ничего кроме [MCP_CALL] блока.")
            appendLine("- После получения данных ответь пользователю на русском языке.")
        }
    }

    private fun getMcpHints(): String {
        val names = getToolNames()
        if (names.isEmpty()) return ""

        val hints = mutableListOf<String>()
        if ("get_team" in names) {
            hints.add("get_team: параметр \"team_id\" (имя сервера, НЕ \"id\"). Пример: {\"team_id\": \"1\"}")
        }
        if ("get_group" in names) {
            hints.add("get_group: параметр \"name\" (НЕ \"group\", НЕ \"letter\"). Пример: {\"name\": \"A\"}")
        }
        if ("get_teams" in names) {
            hints.add("get_teams: аргументы не требуются.")
        }
        if ("get_games" in names) {
            hints.add("get_games: аргументы не требуются.")
        }
        return hints.joinToString("\n").ifEmpty { "" }
    }

    private fun formatConversationHistory(): String {
        if (conversationHistory.size <= 1) return ""
        val recent = conversationHistory.dropLast(1).takeLast(3)
        return buildString {
            appendLine("### ПРЕДЫДУЩАЯ ИСТОРИЯ ДИАЛОГА ###")
            for ((q, a) in recent) {
                appendLine("Пользователь: $q")
                val stripped = a.replace(Regex("<[^>]*>"), "").take(300)
                appendLine("Ассистент: $stripped")
                appendLine()
            }
            appendLine("### КОНЕЦ ИСТОРИИ ###")
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
            log("⚠️ MCP: Нет результатов тулов. Возвращаю очищенный ответ LLM (было ${fallback.length} символов)")
            val cleaned = stripToolCallArtifacts(fallback)
            log("📄 MCP: Очищенный ответ (${cleaned.length} символов): ${cleaned.take(300)}")
            if (cleaned.length < fallback.length * 0.8) {
                return cleaned.ifEmpty { "Ошибка: LLM не смог корректно вызвать MCP инструмент. Попробуйте ещё раз." }
            }
            return fallback
        }
        log("📊 MCP: Собираю финальный ответ из ${allResults.size} результатов тулов")

        // Build team ID → name mapping from games or teams data
        fun buildTeamMap(): Map<String, String> {
            val map = mutableMapOf<String, String>()
            val json = Json { ignoreUnknownKeys = true }
            for (r in allResults) {
                try {
                    val root = json.parseToJsonElement(r).jsonObject
                    val teams = root["teams"]?.jsonArray
                    if (teams != null) {
                        for (t in teams) {
                            val o = t.jsonObject
                            val id = o["id"]?.jsonPrimitive?.content ?: ""
                            val name = o["name_en"]?.jsonPrimitive?.content ?: ""
                            if (id.isNotEmpty() && name.isNotEmpty()) map[id] = name
                        }
                    }
                    val games = root["games"]?.jsonArray
                    if (games != null) {
                        for (g in games) {
                            val o = g.jsonObject
                            val hId = o["home_team_id"]?.jsonPrimitive?.content ?: ""
                            val aId = o["away_team_id"]?.jsonPrimitive?.content ?: ""
                            val hName = o["home_team_name_en"]?.jsonPrimitive?.content ?: ""
                            val aName = o["away_team_name_en"]?.jsonPrimitive?.content ?: ""
                            if (hId.isNotEmpty() && hName.isNotEmpty()) map[hId] = hName
                            if (aId.isNotEmpty() && aName.isNotEmpty()) map[aId] = aName
                        }
                    }
                } catch (_: Exception) {
                }
            }
            return map
        }

        val teamMap = buildTeamMap()

        // Format groups with team names
        fun formatGroupsWithNames(rawResult: String): String {
            val json = Json { ignoreUnknownKeys = true }
            val root = try {
                json.parseToJsonElement(rawResult).jsonObject
            } catch (_: Exception) {
                return ""
            }
            val groupsArr = root["groups"]?.jsonArray ?: return ""
            val sb = StringBuilder()
            sb.appendLine("ГРУППЫ:")
            groupsArr.forEach { g ->
                val o = g.jsonObject
                val name = o["name"]?.jsonPrimitive?.content ?: "?"
                sb.append("  $name: ")
                val teams = o["teams"]?.jsonArray
                if (teams != null) {
                    val teamStrs = teams.map { t ->
                        val to = t.jsonObject
                        val tid = to["team_id"]?.jsonPrimitive?.content ?: "?"
                        val pts = to["pts"]?.jsonPrimitive?.content ?: "?"
                        val gd = to["gd"]?.jsonPrimitive?.content ?: "0"
                        val teamName = teamMap[tid] ?: "ID$tid"
                        "$teamName(${pts}pts,${gd}gd)"
                    }
                    sb.appendLine(teamStrs.joinToString(", "))
                } else {
                    sb.appendLine("нет команд")
                }
            }
            return sb.toString()
        }

        val groupsWithNames =
            allResults.map { formatGroupsWithNames(it) }.firstOrNull { it.isNotEmpty() } ?: ""

        // Format games compact
        fun formatGamesCompact(rawResult: String): String {
            val json = Json { ignoreUnknownKeys = true }
            val root = try {
                json.parseToJsonElement(rawResult).jsonObject
            } catch (_: Exception) {
                return ""
            }
            val gamesArr = root["games"]?.jsonArray ?: return ""
            val sb = StringBuilder()
            sb.appendLine("МАТЧИ:")
            gamesArr.forEach { g ->
                val o = g.jsonObject
                val hId = o["home_team_id"]?.jsonPrimitive?.content ?: "?"
                val aId = o["away_team_id"]?.jsonPrimitive?.content ?: "?"
                val hName = o["home_team_name_en"]?.jsonPrimitive?.content ?: "?"
                val aName = o["away_team_name_en"]?.jsonPrimitive?.content ?: "?"
                val hs = o["home_score"]?.jsonPrimitive?.content ?: "?"
                val asScore = o["away_score"]?.jsonPrimitive?.content ?: "?"
                val grp = o["group"]?.jsonPrimitive?.content ?: "?"
                val fin = o["finished"]?.jsonPrimitive?.content ?: "?"
                sb.appendLine("  $grp: $hName($hId) $hs-$asScore $aName($aId) finished=$fin")
            }
            return sb.toString()
        }

        val gamesCompact =
            allResults.map { formatGamesCompact(it) }.firstOrNull { it.isNotEmpty() } ?: ""

        // Build mapping section
        val mappingSection = if (teamMap.isNotEmpty()) {
            "СООТВЕТСТВИЕ ID → НАЗВАНИЕ КОМАНДЫ:\n" + teamMap.entries.joinToString("\n") { "  ID${it.key} = ${it.value}" }
        } else ""

        // Check if save_data was already called
        val existingSaveResult = allResults.firstOrNull { r ->
            r.contains("saved", ignoreCase = true) || r.contains(
                "path",
                ignoreCase = true
            ) || r.startsWith("{\"status\"")
        }
        val needSave = existingSaveResult == null

        val prompt = buildString {
            appendLine("Вопрос пользователя: $originalUserPrompt")
            appendLine()
            if (groupsWithNames.isNotEmpty()) {
                appendLine(groupsWithNames)
                appendLine()
            }
            if (gamesCompact.isNotEmpty()) {
                appendLine(gamesCompact)
                appendLine()
            }
            if (mappingSection.isNotEmpty()) {
                appendLine(mappingSection)
                appendLine()
            }
            if (existingSaveResult != null) {
                appendLine("--- save_data результат ---")
                appendLine(existingSaveResult.take(500))
                appendLine()
            } else {
                appendLine("Аналитика ещё не сохранена. Если хочешь сохранить — начни с [MCP_CALL]{\"tool\": \"save_data\", \"arguments\": {\"content\": \"...\", \"format\": \"md\"}}[/MCP_CALL], потом ответь.")
            }
            appendLine()
            appendLine("---ОТВЕТ---")
            appendLine("ТОЛЬКО РУССКИЙ. НИ СЛОВА АНГЛИЙСКОГО.")
            appendLine("Выведи таблицы ВСЕХ 12 ГРУПП (A-L), аналитику и статус квалификации.")
            appendLine("ИСПОЛЬЗУЙ ТОЛЬКО ДАННЫЕ ВЫШЕ.")
        }
        var response = askMcpFollowUpSafe(prompt, isFinalSynthesis = true)
        if (response == "NO_MCP_CALL") {
            log("⚠️ MCP: LLM недоступен для синтеза, возвращаю сырые данные")
            val teamResult = allResults.firstOrNull()
            return teamResult ?: fallback
        }
        // Handle MCP_CALL in the response — loop until no more tool calls
        repeat(5) {
            val nextJson = extractMcpCallJson(response) ?: return@repeat
            try {
                val obj = Json.parseToJsonElement(nextJson).jsonObject
                val toolName = obj["tool"]?.jsonPrimitive?.content
                if (toolName == "save_data" && needSave) {
                    log("📦 MCP: Выполняю save_data из синтеза")
                    val integration = getIntegrationForTool("save_data")
                    val saveResult = integration?.executeToolCall(nextJson)
                    if (saveResult != null) {
                        log("✅ MCP: save_data выполнен: ${saveResult.take(100)}")
                        response = buildString {
                            appendLine("Вопрос пользователя: $originalUserPrompt")
                            appendLine()
                            if (groupsWithNames.isNotEmpty()) {
                                appendLine(groupsWithNames)
                                appendLine()
                            }
                            if (gamesCompact.isNotEmpty()) {
                                appendLine(gamesCompact)
                                appendLine()
                            }
                            if (mappingSection.isNotEmpty()) {
                                appendLine(mappingSection)
                                appendLine()
                            }
                            appendLine("--- save_data результат ---")
                            appendLine(saveResult.take(500))
                            appendLine()
                            appendLine()
                            appendLine("---ОТВЕТ---")
                            appendLine("Данные сохранены. ТЕПЕРЬ напиши ответ пользователю на русском.")
                            appendLine("Выведи таблицы ВСЕХ 12 ГРУПП (A-L), аналитику и статус квалификации.")
                            appendLine("НЕ ВЫЗЫВАЙ БОЛЬШЕ ИНСТРУМЕНТЫ. Просто напиши ответ.")
                        }
                        response = askMcpFollowUpSafe(response, isFinalSynthesis = true)
                        if (response == "NO_MCP_CALL") {
                            return allResults.firstOrNull() ?: fallback
                        }
                    }
                } else if (toolName != null) {
                    val integration = getIntegrationForTool(toolName)
                    if (integration != null) {
                        log("📦 MCP: Выполняю $toolName из синтеза")
                        val toolResult = integration.executeToolCall(nextJson)
                        log("✅ MCP: $toolName выполнен: ${toolResult.take(100)}")
                        val updatedResults = allResults + toolResult
                        fun buildTeamMapInner(): Map<String, String> {
                            val map = mutableMapOf<String, String>()
                            val json = Json { ignoreUnknownKeys = true }
                            for (r in updatedResults) {
                                try {
                                    val root = json.parseToJsonElement(r).jsonObject
                                    val teams = root["teams"]?.jsonArray
                                    if (teams != null) {
                                        for (t in teams) {
                                            val o = t.jsonObject
                                            val id = o["id"]?.jsonPrimitive?.content ?: ""
                                            val name = o["name_en"]?.jsonPrimitive?.content ?: ""
                                            if (id.isNotEmpty() && name.isNotEmpty()) map[id] = name
                                        }
                                    }
                                    val games = root["games"]?.jsonArray
                                    if (games != null) {
                                        for (g in games) {
                                            val o = g.jsonObject
                                            val hId =
                                                o["home_team_id"]?.jsonPrimitive?.content ?: ""
                                            val aId =
                                                o["away_team_id"]?.jsonPrimitive?.content ?: ""
                                            val hName =
                                                o["home_team_name_en"]?.jsonPrimitive?.content ?: ""
                                            val aName =
                                                o["away_team_name_en"]?.jsonPrimitive?.content ?: ""
                                            if (hId.isNotEmpty() && hName.isNotEmpty()) map[hId] =
                                                hName
                                            if (aId.isNotEmpty() && aName.isNotEmpty()) map[aId] =
                                                aName
                                        }
                                    }
                                } catch (_: Exception) {
                                }
                            }
                            return map
                        }

                        val updatedTeamMap = buildTeamMapInner()
                        fun formatGroupsInner(rawResult: String): String {
                            val json = Json { ignoreUnknownKeys = true }
                            val root = try {
                                json.parseToJsonElement(rawResult).jsonObject
                            } catch (_: Exception) {
                                return ""
                            }
                            val groupsArr = root["groups"]?.jsonArray ?: return ""
                            val sb = StringBuilder()
                            sb.appendLine("ГРУППЫ:")
                            groupsArr.forEach { g ->
                                val o = g.jsonObject
                                val name = o["name"]?.jsonPrimitive?.content ?: "?"
                                sb.append("  $name: ")
                                val teams = o["teams"]?.jsonArray
                                if (teams != null) {
                                    val teamStrs = teams.map { t ->
                                        val to = t.jsonObject
                                        val tid = to["team_id"]?.jsonPrimitive?.content ?: "?"
                                        val pts = to["pts"]?.jsonPrimitive?.content ?: "?"
                                        val gd = to["gd"]?.jsonPrimitive?.content ?: "0"
                                        val teamName = updatedTeamMap[tid] ?: "ID$tid"
                                        "$teamName(${pts}pts,${gd}gd)"
                                    }
                                    sb.appendLine(teamStrs.joinToString(", "))
                                } else {
                                    sb.appendLine("нет команд")
                                }
                            }
                            return sb.toString()
                        }

                        val updatedGroups =
                            updatedResults.map { formatGroupsInner(it) }
                                .firstOrNull { it.isNotEmpty() } ?: ""
                        response = buildString {
                            appendLine("Вопрос пользователя: $originalUserPrompt")
                            appendLine()
                            if (updatedGroups.isNotEmpty()) {
                                appendLine(updatedGroups)
                                appendLine()
                            }
                            if (updatedTeamMap.isNotEmpty()) {
                                appendLine("СООТВЕТСТВИЕ ID → НАЗВАНИЕ КОМАНДЫ:")
                                updatedTeamMap.entries.sortedBy { it.key.toIntOrNull() ?: 0 }
                                    .forEach { (id, name) ->
                                        appendLine("  $id → $name")
                                    }
                                appendLine()
                            }
                            appendLine("---ОТВЕТ---")
                            appendLine("ТЕПЕРЬ напиши ответ пользователю на русском. НЕ ВЫЗЫВАЙ БОЛЬШЕ ИНСТРУМЕНТЫ.")
                            appendLine("Выведи таблицы ВСЕХ 12 ГРУПП с названиями команд и очками, затем аналитику и статус квалификации.")
                        }
                        response = askMcpFollowUpSafe(response, isFinalSynthesis = true)
                    }
                }
            } catch (_: Exception) {
            }
        }
        val stripped = stripThinkingPrefix(response)
        val russianCount =
            stripped.count { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }
        val englishCount = stripped.count { it in 'a'..'z' || it in 'A'..'Z' }
        val hasEnglishContent = englishCount > russianCount * 2 + 20
        if (hasEnglishContent && response.length > 50) {
            log("⚠️ MCP: Синтез на английском, повторная попытка")
            val retryPrompt = buildString {
                appendLine("Вопрос пользователя: $originalUserPrompt")
                appendLine()
                appendLine("Данные уже собраны. НЕ ВЫЗЫВАЙ ИНСТРУМЕНТЫ. У ТЕБЯ УЖЕ ЕСТЬ ВСЕ ДАННЫЕ.")
                appendLine("Просто напиши ответ на русском языке. Без [MCP_CALL]. Без английского.")
                appendLine()
                if (groupsWithNames.isNotEmpty()) {
                    appendLine(groupsWithNames)
                    appendLine()
                }
                if (gamesCompact.isNotEmpty()) {
                    appendLine(gamesCompact)
                    appendLine()
                }
                if (mappingSection.isNotEmpty()) {
                    appendLine(mappingSection)
                    appendLine()
                }
                if (teamMap.isEmpty()) {
                    appendLine("ПРИМЕЧАНИЕ: Названия команд неизвестны. Используй ID1, ID2 и т.д. в таблицах.")
                    appendLine()
                }
                appendLine("---ОТВЕТ---")
                appendLine("ТОЛЬКО РУССКИЙ. НЕ ПИШИ [MCP_CALL]. НЕ ПИШИ АНГЛИЙСКИЙ.")
                appendLine("Выведи таблицы ВСЕХ 12 ГРУПП с названиями команд и очками, затем аналитику.")
            }
            val savedModel = currentModel
            if (synthesisRetryModel != null) currentModel = synthesisRetryModel
            try {
                val retryResult =
                    stripThinkingPrefix(askMcpFollowUpSafe(retryPrompt, isFinalSynthesis = true))
                return retryResult
            } finally {
                currentModel = savedModel
            }
        }
        return stripped
    }

    private fun stripThinkingPrefix(text: String): String {
        val marker = "---ОТВЕТ---"
        val firstIdx = text.indexOf(marker)
        val lastIdx = text.lastIndexOf(marker)
        if (lastIdx >= 0 && lastIdx != firstIdx) {
            val beforeLast = text.substring(firstIdx + marker.length, lastIdx).trim()
            if (beforeLast.isNotEmpty()) return beforeLast
        }
        if (firstIdx >= 0) {
            val after = text.substring(firstIdx + marker.length).trim()
            if (after.isNotEmpty()) return after
        }
        val noMcpCall = text.replace(
            Regex(
                "\\[MCP[_A-Z]*CALL].*?(\\[/MCP[_A-Z]*CALL])?",
                setOf(RegexOption.IGNORE_CASE)
            ), ""
        ).trim()
        var cleaned =
            if (noMcpCall.isNotEmpty() && noMcpCall.length < text.length * 0.8) noMcpCall.trimStart() else text.trimStart()
        for (prefix in listOf(
            "We need to answer",
            "We need to produce",
            "Need to produce",
            "Need to answer",
            "We have",
            "We must",
            "Let me",
            "Let's",
            "I need to",
            "Based on",
            "Here",
            "Answer the user",
            "Answering the user",
            "So we need",
            "So we can",
            "Simplify",
            "Also we have",
            "Also we",
            "First, let",
            "First, we",
            "First we",
            "Provide",
            "Now I",
            "I will",
            "I can",
            "To answer",
            "We'll",
            "We will",
            "Alright",
            "Okay",
            "So here",
            "We can provide",
            "We can answer",
            "We can now",
            "Now we need",
            "Now we can",
            "Now let",
            "Let's create",
            "Let's provide",
            "Let's answer",
            "Let's write",
            "Let's start",
            "Let me provide",
            "Let me answer",
            "Let me create",
            "Let me write",
            "Great,",
            "Good,",
        )) {
            val pattern = Regex("^$prefix[^.]*\\.?\\s*", RegexOption.IGNORE_CASE)
            cleaned = cleaned.replaceFirst(pattern, "")
        }
        // If still starts with Latin text, strip first sentence aggressively
        if (cleaned.length > 3 && cleaned.first()
                .isUpperCase() && Regex("^[A-Z][a-z]+ ").containsMatchIn(cleaned)
        ) {
            val firstSentence = Regex("^[^.]+\\.").find(cleaned)
            if (firstSentence != null) {
                cleaned = cleaned.removePrefix(firstSentence.value).trim()
            }
        }
        // Strip if starts with lowercase English continuation after stripping
        if (cleaned.length > 3 && cleaned.first()
                .isLowerCase() && cleaned[0] !in 'а'..'я' && cleaned[0] !in 'А'..'Я'
        ) {
            val firstSentence = Regex("^[^.]+[.!?]?").find(cleaned)
            if (firstSentence != null) {
                cleaned = cleaned.removePrefix(firstSentence.value).trim()
            }
        }
        return cleaned.ifEmpty { text }
    }

    private fun compactData(rawResult: String): String {
        val json = Json { ignoreUnknownKeys = true }
        val root = try {
            json.parseToJsonElement(rawResult).jsonObject
        } catch (_: Exception) {
            return rawResult.take(5000)
        }

        val gamesArr = root["games"]?.jsonArray
        if (gamesArr != null) {
            val sb = StringBuilder()
            sb.appendLine("МАТЧИ:")
            gamesArr.forEach { g ->
                val o = g.jsonObject
                val hId = o["home_team_id"]?.jsonPrimitive?.content ?: "?"
                val aId = o["away_team_id"]?.jsonPrimitive?.content ?: "?"
                val hName = o["home_team_name_en"]?.jsonPrimitive?.content ?: "?"
                val aName = o["away_team_name_en"]?.jsonPrimitive?.content ?: "?"
                val hs = o["home_score"]?.jsonPrimitive?.content ?: "?"
                val asScore = o["away_score"]?.jsonPrimitive?.content ?: "?"
                val grp = o["group"]?.jsonPrimitive?.content ?: "?"
                val fin = o["finished"]?.jsonPrimitive?.content ?: "?"
                sb.appendLine("  $grp: $hName($hId) $hs-$asScore $aName($aId) finished=$fin")
            }
            return sb.toString()
        }

        val groupsArr = root["groups"]?.jsonArray
        if (groupsArr != null) {
            val sb = StringBuilder()
            sb.appendLine("ГРУППЫ:")
            groupsArr.forEach { g ->
                val o = g.jsonObject
                val name = o["name"]?.jsonPrimitive?.content ?: "?"
                sb.append("  $name: ")
                val teams = o["teams"]?.jsonArray
                if (teams != null) {
                    val teamStrs = teams.map { t ->
                        val to = t.jsonObject
                        val tid = to["team_id"]?.jsonPrimitive?.content ?: "?"
                        val pts = to["pts"]?.jsonPrimitive?.content ?: "?"
                        val gd = to["gd"]?.jsonPrimitive?.content ?: "0"
                        "ID$tid(${pts}pts,${gd}gd)"
                    }
                    sb.appendLine(teamStrs.joinToString(", "))
                } else {
                    sb.appendLine("нет команд")
                }
            }
            return sb.toString()
        }

        return rawResult.take(5000)
    }

    private fun extractMcpCallJson(text: String): String? {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val markers = listOf("[MCP_CALL]", "[MCPCALL]", "[mcp_call]", "[mcpcall]")
        for (marker in markers) {
            val idx = text.indexOf(marker, ignoreCase = true)
            if (idx == -1) continue
            println("🔎 MCP: Найден маркер '$marker' на позиции $idx")
            val after = text.substring(idx + marker.length).trim()
            println("🔎 MCP: Текст после маркера (первые 200): ${after.take(200)}")

            // Find [/MCP_CALL] if present
            val closeMarkers = listOf("[/MCP_CALL]", "[/MCPCALL]", "[/mcp_call]", "[/mcpcall]")
            for (closeMarker in closeMarkers) {
                val closeIdx = after.indexOf(closeMarker, ignoreCase = true)
                if (closeIdx > 0) {
                    val between = after.substring(0, closeIdx).trim()
                    if (between.isNotEmpty()) {
                        try {
                            val obj = json.parseToJsonElement(between).jsonObject
                            if (obj.containsKey("tool")) {
                                println("✅ MCP: Валидный JSON между маркерами")
                                return between
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }

            // Fallback: brace-balancing, only the first '{'
            val braceStart = after.indexOf('{')
            if (braceStart >= 0) {
                var depth = 0
                var insideString = false
                var escaped = false
                for (i in braceStart until after.length) {
                    val c = after[i]
                    when {
                        escaped -> escaped = false
                        c == '\\' -> escaped = true
                        c == '"' -> insideString = !insideString
                        !insideString -> {
                            when (c) {
                                '{' -> depth++
                                '}' -> {
                                    depth--
                                    if (depth == 0) {
                                        val candidate = after.substring(braceStart, i + 1)
                                        try {
                                            val obj = json.parseToJsonElement(candidate).jsonObject
                                            if (obj.containsKey("tool")) {
                                                println("✅ MCP: Валидный JSON (brace-balanced)")
                                                return candidate
                                            }
                                        } catch (_: Exception) {
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        println("❌ MCP: Ни одного валидного JSON не найдено")
        return null
    }

    private suspend fun askMcpFollowUpSafe(
        prompt: String,
        isFinalSynthesis: Boolean = false
    ): String {
        return try {
            askMcpFollowUp(prompt, isFinalSynthesis)
        } catch (e: Exception) {
            log("⚠️ MCP: Follow-up не удался (${e.message?.take(200)}), синтезирую из имеющихся данных")
            "NO_MCP_CALL"
        }
    }

    private suspend fun askMcpFollowUp(prompt: String, isFinalSynthesis: Boolean = false): String {
        println("🔄 LLM: Отправляю follow-up запрос (${prompt.length} символов)")
        println("📝 LLM: Промт (первые 500): ${prompt.take(500)}")
        val client: RouterClient = ClientFactory.create(currentApiKey)
        val effectiveSystemPrompt = if (isAnyMcpConnected()) {
            dataIntegration?.refreshTools()
            pipelineIntegration?.refreshTools()
            buildString {
                append("ТЫ В РЕЖИМЕ ВЫЗОВА MCP ИНСТРУМЕНТОВ. НЕ ПИШИ НИЧЕГО КРОМЕ [MCP_CALL]. НЕ ДУМАЙ ВСЛУХ. ПРОСТО ВЫЗОВИ ИНСТРУМЕНТ.")
                appendLine()
                append(getCombinedToolDescriptions())
            }
        } else {
            systemPrompt
        }
        val request = RouterRequest(
            model = currentModel,
            messages = listOf(
                ChatMessage("system", effectiveSystemPrompt),
                ChatMessage("user", prompt)
            ),
            maxTokens = if (isFinalSynthesis) 8192 else if (hasPipelineTools()) 8192 else 2048,
            stop = null // don't strip [/MCP_CALL] — extractMcpCallJson needs it to find the boundary
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

    private fun buildStructuredRagPrompt(userQuery: String, ragAnswer: RagAnswer): String {
        val sourcesText = ragAnswer.sources
            .mapIndexed { i, s -> "[${i + 1}] ${s.title} — ${s.section} (score: ${"%.3f".format(s.score)})" }
            .joinToString("\n")

        val quotesText = ragAnswer.quotes
            .mapIndexed { i, q -> "> **Цитата ${i + 1}** (${q.source.title} / ${q.source.section}):\n> ${q.text}" }
            .joinToString("\n\n")

        return buildString {
            appendLine("Ты — ассистент с доступом к базе знаний. Твоя задача — ответить на вопрос пользователя, используя ТОЛЬКО предоставленный контекст.")
            appendLine()
            appendLine("=== КОНТЕКСТ ИЗ БАЗЫ ЗНАНИЙ ===")
            appendLine(ragAnswer.answer)
            appendLine("=== КОНЕЦ КОНТЕКСТА ===")
            appendLine()
            appendLine("=== ИСТОЧНИКИ (обязательно ссылайся на них в формате [1], [2] и т.д.) ===")
            appendLine(sourcesText)
            appendLine()
            appendLine("=== ЦИТАТЫ (используй их для подтверждения фактов) ===")
            appendLine(quotesText)
            appendLine()
            appendLine("=== СТРОГИЕ ИНСТРУКЦИИ ===")
            appendLine("1. Отвечай ТОЛЬКО на русском языке")
            appendLine("2. Используй ТОЛЬКО факты из контекста выше — НЕ придумывай ничего")
            appendLine("3. ОБЯЗАТЕЛЬНО указывай источники в тексте ответа в формате [1], [2], [3]...")
            appendLine("4. Используй цитаты из раздела ЦИТАТЫ для подтверждения важных фактов")
            appendLine("5. Если в контексте НЕТ ответа на вопрос — напиши ровно: \"В предоставленном контексте нет информации об этом. Уточните вопрос или задайте другой.\"")
            appendLine("6. НЕ пиши вводные фразы вроде \"Получил контекст, чем могу помочь\" — отвечай сразу по существу")
            appendLine()
            appendLine("Вопрос пользователя: $userQuery")
            appendLine()
            appendLine("Твой ответ (ссылки на источники [1], [2]... обязательны):")
        }
    }
}
