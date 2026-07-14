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
import com.llmapp.rag.data.SourceCodeProvider
import com.llmapp.rag.domain.RagAnswer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class RagSource(
    val title: String,
    val section: String,
    val score: Float,
)

data class ChatResponse(
    val content: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val finishReason: String?,
    val responseTimeMs: Long,
    val ragSources: List<RagSource>? = null,
    val compressionNotification: String? = null,
)

class ChatSession(
    private var currentModel: String = "mistral/mistral-large-latest",
    private val systemPrompt: String = """Ты полезный ассистент для проекта CalendarKMP. Отвечай кратко и по делу на русском языке.
        
CalendarKMP — это Kotlin Multiplatform приложение для отслеживания饮酒 календаря, построенное на Clean Architecture + MVI.

Проект включает:
- Мой календарь (отслеживание饮酒 дней)
- Календарь друзей (просмотр календаря друзей)
- Настройки (профиль, язык, тема, шаринг)
- Firebase Firestore для хранения данных
- Kodein DI для внедрения зависимостей
- JetBrains Navigation3 для навигации

**Git-операции:**
Ты можешь выполнять Git-операции через GitHub API:
- Читать файлы из репозитория
- Создавать коммиты (обновлять файлы)
- Создавать ветки
- Создавать Pull Request'ы

Для коммитов и PR нужен GITHUB_PERSONAL_ACCESS_TOKEN (env переменная).
Репозиторий: github.com/VerushkinRoman/CalendarKMP

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
    maxHistorySize: Int = 150,
    private val compressionEnabled: Boolean = true,
    private val keepLastMessages: Int = 15,
    private val compressAfterTokens: Int = 24000,
) {
    var logListener: ((String) -> Unit)? = null
    var progressListener: ((String) -> Unit)? = null
    private fun log(msg: String) {
        println(msg)
        logListener?.invoke(msg)
    }

    private fun progress(msg: String) {
        progressListener?.invoke(msg)
    }

    private var strategicAgent: StrategicLLMAgent? = null
    private var useStrategicAgent = true
    private val conversationHistory = mutableListOf<Pair<String, String>>()

    var mcpIntegration: McpIntegration? = null

    var ragEnabled: Boolean = false
    val ragEnhancer: RAGEnhancer by lazy { RAGEnhancer() }
    var taskMemorySummary: String = ""
        set(value) {
            field = value
            val systemWithMemory = buildSystemPromptWithMemory()
            strategicAgent?.updateSystemPrompt(systemWithMemory)
            compressedAgent?.updateSystemPrompt(systemWithMemory)
            compressedAgent?.updateTaskMemory(value)
            regularAgent?.updateSystemPrompt(systemWithMemory)
        }

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

    private fun isMcpAvailable(): Boolean =
        mcpIntegration?.isConnected() == true

    private fun getToolNames(): Set<String> {
        val names = mutableSetOf<String>()
        mcpIntegration?.getToolNames()?.let { names.addAll(it) }
        return names
    }

    private fun getCombinedToolDescriptions(): String {
        val allTools = mutableListOf<McpToolInfo>()
        mcpIntegration?.getTools()?.let { allTools.addAll(it) }
        if (allTools.isEmpty()) return ""

        return buildString {
            appendLine("ВАЖНО: Имена параметров должны быть ТОЧНО как указано ниже. Не угадывай.")
            appendLine()
            appendLine("=== ДОСТУПНЫЕ ИНСТРУМЕНТЫ ===")
            appendLine()
            for (tool in allTools) {
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
                appendLine()
            }
            appendLine("=== ПРАВИЛА ===")
            appendLine("- Первое сообщение = [MCP_CALL]...[/MCP_CALL]")
            appendLine("- Один инструмент за раз.")
            appendLine("- Читай описания тулов и решай сам, какой вызвать.")
            appendLine("- Когда данных достаточно — ответь на русском.")
        }
    }


    private fun getIntegrationForTool(toolName: String): McpIntegration? =
        mcpIntegration?.takeIf { it.getToolNames().contains(toolName) }

    private var pendingCompressionNotification: String? = null

    private val compressedAgent: CompressedLLMAgent? = if (compressionEnabled) {
        CompressedLLMAgent(

            model = currentModel,
            systemPrompt = systemPrompt,
            responseControl = ResponseControl(),
            maxHistorySize = maxHistorySize,
            keepLastMessages = keepLastMessages,
            compressAfterTokens = compressAfterTokens
        ).also {
            it.compressionEnabled = true
            it.onSummaryGenerated = { summary, _ ->
                pendingCompressionNotification = buildString {
                    appendLine("⚡ **Контекст сжат** — детальная сводка диалога:")
                    appendLine()
                    append(summary.trim())
                }
            }
        }
    } else null

    private val regularAgent: LLMAgent? = if (!compressionEnabled) {
        LLMAgent(

            model = currentModel,
            systemPrompt = systemPrompt,
            responseControl = ResponseControl(),
            maxHistorySize = maxHistorySize
        )
    } else null

    private var responseControl = ResponseControl()

    init {
        strategicAgent = StrategicLLMAgent(

            model = currentModel,
            systemPrompt = systemPrompt,
            responseControl = ResponseControl()
        )
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

    fun switchLocalMode(useLocal: Boolean) {
        val newModel = if (useLocal) "gemma4:26b" else "mistral/mistral-large-latest"
        currentModel = newModel
        ClientFactory.setUseLocal(useLocal)
        changeModel(newModel)
    }

    fun switchPrivateMode(usePrivate: Boolean) {
        val newModel = if (usePrivate) "local" else "mistral/mistral-large-latest"
        currentModel = newModel
        ClientFactory.setUsePrivate(usePrivate)
        changeModel(newModel)
    }

    fun setResponseControl(control: ResponseControl) {
        responseControl = control
        compressedAgent?.updateResponseControl(control)
        regularAgent?.updateResponseControl(control)
        strategicAgent?.updateResponseControl(control)
    }

    suspend fun ask(
        userPrompt: String,
        isRegeneration: Boolean = false,
        saveToHistory: Boolean = true
    ): ChatResponse {
        progress("Поиск в базе знаний...")
        val useMcpMode = isMcpAvailable()

        val mcpSystemPrompt = if (useMcpMode) {
            buildString {
                appendLine("ТЫ В РЕЖИМЕ ВЫЗОВА ИНСТРУМЕНТОВ (GitHub).")
                appendLine("ОТВЕЧАЙ ТОЛЬКО НА РУССКОМ ЯЗЫКЕ. НИ СЛОВА ПО-АНГЛИЙСКИ.")
                appendLine("НЕ ПИШИ НИЧЕГО, КРОМЕ [MCP_CALL] БЛОКА ИЛИ РУССКОГО ОТВЕТА.")
                appendLine("НЕ ДУМАЙ ВСЛУХ. НЕ ОБЪЯСНЯЙ. НЕ ПЛАНИРУЙ.")
                appendLine("ПРОСТО ВЫЗОВИ ИНСТРУМЕНТ ИЛИ ОТВЕТЬ ПО-РУССКИ.")
                appendLine("ДЛЯ ЧТЕНИЯ ДОКУМЕНТАЦИИ И КОДА ИСПОЛЬЗУЙ github_read_file, github_list_dir, github_search_source.")
                appendLine("ДЛЯ ПОИСКА КОДА ПО КЛЮЧЕВОМУ СЛОВУ ИСПОЛЬЗУЙ github_search_source.")
                appendLine("ДЛЯ ПРОСМОТРА СПИСКА ФАЙЛОВ ИСПОЛЬЗУЙ github_list_docs ИЛИ github_list_source_tree.")
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
        var lastRagSources: List<RagSource>? = null
        if (ragEnabled && !useMcpMode) {
            try {
                ragEnhancer.ensureIndexLoaded()
                val ragAnswer: RagAnswer = ragEnhancer.searchWithStructuredContext(userPrompt)
                lastRagSources = ragAnswer.sources.map { src ->
                    RagSource(
                        title = src.title,
                        section = src.section,
                        score = src.score,
                    )
                }
                if (ragAnswer.chunks.isNotEmpty()) {
                    log("📚 RAG: Найдено ${ragAnswer.chunks.size} релевантных чанков")
                    val sourceFiles = if (isSourceCodeNeeded(userPrompt)) {
                        try {
                            SourceCodeProvider.findRelevantFiles(userPrompt, ragAnswer)
                        } catch (e: Exception) {
                            log("⚠️ SourceCode: Ошибка загрузки файлов: ${e.message}")
                            emptyList()
                        }.also {
                            if (it.isNotEmpty()) log("📂 SourceCode: Загружено ${it.size} файлов из репозитория")
                        }
                    } else {
                        log("📚 SourceCode: Пропускаю — вопрос о документации, исходники не нужны")
                        emptyList()
                    }
                    augmentedUserPrompt =
                        buildStructuredRagPrompt(userPrompt, ragAnswer, sourceFiles)
                } else if (ragAnswer.shouldSayIdontKnow) {
                    log("📚 RAG: Релевантность ниже порога — возвращаю 'не знаю'")
                    val fallbackMessage = buildString {
                        appendLine("В базе знаний CalendarKMP нет информации по вашему запросу.")
                        appendLine()
                        appendLine("**Доступные темы:**")
                        appendLine("- Архитектура (Clean Architecture + MVI)")
                        appendLine("- Навигация (JetBrains Navigation3)")
                        appendLine("- Модели данных (DayData, MonthData, DrinkType)")
                        appendLine("- Use Cases (GetStartCalendarData, CalculateStatistic, CalculateMonthIndex, GetContacts, FilterContacts)")
                        appendLine("- DataSource (Firebase Firestore, OfflineDataSource)")
                        appendLine("- Фичи (Мой календарь, Друзья, Настройки)")
                        appendLine()
                        appendLine("Попробуйте переформулировать вопрос или уточните тему.")
                    }
                    return ChatResponse(
                        content = fallbackMessage,
                        promptTokens = 0,
                        completionTokens = 0,
                        totalTokens = 0,
                        finishReason = "rag_low_relevance",
                        responseTimeMs = 0,
                        ragSources = lastRagSources,
                    )
                }
            } catch (e: Exception) {
                log("⚠️ RAG: Ошибка обогащения: ${e.message}")
            }
        }

        val effectivePrompt = if (useMcpMode) {
            "START WITH [MCP_CALL] IMMEDIATELY. NO TEXT BEFORE IT.\n\n$augmentedUserPrompt"
        } else augmentedUserPrompt

        val savedControl = if (useMcpMode && responseControl.enabled) {
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

        val markerReminder = if (taskMemorySummary.isNotBlank()) {
            """
            
            ### ФОРМАТ ОТВЕТА ###
            В конце ответа добавь ТОЛЬКО НОВЫЕ элементы (не повторяй уже известные из памяти задачи):
            [CONSTRAINT]новое ограничение 1[/CONSTRAINT]
            [CONSTRAINT]новое ограничение 2[/CONSTRAINT]
            [PROGRESS_DONE]сделанная задача[/PROGRESS_DONE]
            [PROGRESS_IN_PROGRESS]текущая задача[/PROGRESS_IN_PROGRESS]
            [PROGRESS_BLOCKED]заблокированная задача[/PROGRESS_BLOCKED]
            [DECISION]новое решение[/DECISION]
            [CONTEXT]новый контекст — описание[/CONTEXT]
            Можно несколько [CONSTRAINT], [PROGRESS_DONE], [PROGRESS_IN_PROGRESS], [PROGRESS_BLOCKED], [DECISION], [CONTEXT] — каждый в отдельном теге.
            ⚠️ [GOAL] НЕ пиши — цель уже установлена.
            """.trimIndent()
        } else {
            """
            
            ### ФОРМАТ ОТВЕТА ###
            ЗАКОНЧИ ответ этими строками. Может быть НЕСКОЛЬКО ограничений, статусов задач, решений и контекстов — каждый в отдельном теге:
            [GOAL]цель пользователя одной фразой[/GOAL]
            [CONSTRAINT]ограничение 1[/CONSTRAINT]
            [CONSTRAINT]ограничение 2[/CONSTRAINT]
            [PROGRESS_DONE]сделанная задача[/PROGRESS_DONE]
            [PROGRESS_IN_PROGRESS]текущая задача[/PROGRESS_IN_PROGRESS]
            [PROGRESS_BLOCKED]заблокированная задача[/PROGRESS_BLOCKED]
            [DECISION]решение 1[/DECISION]
            [DECISION]решение 2[/DECISION]
            [CONTEXT]контекст 1 — описание[/CONTEXT]
            [CONTEXT]контекст 2 — описание[/CONTEXT]
            Если каких-то элементов нет — просто не пиши соответствующий тег. НО [GOAL] — всегда!
            """.trimIndent()
        }
        val promptWithMarkerReminder = "$effectivePrompt$markerReminder"

        progress("Генерация ответа...")
        try {
            val response = run {
                if (useStrategicAgent && strategicAgent != null) {
                    try {
                        val resp = strategicAgent!!.processRequest(promptWithMarkerReminder)
                        printMetadata(
                            finishReason = resp.finishReason,
                            promptTokens = resp.promptTokens,
                            completionTokens = resp.completionTokens,
                            totalTokens = resp.totalTokens,
                            responseTimeMs = resp.responseTimeMs
                        )
                        println("📊 Стратегия: ${resp.strategyUsed}")
                        var notification: String? = null
                        if (compressedAgent != null) {
                            compressedAgent.addUserMessage(effectivePrompt)
                            compressedAgent.addAssistantMessage(resp.content)
                            compressedAgent.compressNow()
                            notification = pendingCompressionNotification
                            pendingCompressionNotification = null
                        }
                        ChatResponse(
                            content = resp.content,
                            promptTokens = resp.promptTokens,
                            completionTokens = resp.completionTokens,
                            totalTokens = resp.totalTokens,
                            finishReason = resp.finishReason,
                            responseTimeMs = resp.responseTimeMs,
                            compressionNotification = notification
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
                    val resp = compressedAgent.processRequest(promptWithMarkerReminder)
                    val notification = pendingCompressionNotification
                    pendingCompressionNotification = null
                    ChatResponse(
                        content = resp.content,
                        promptTokens = resp.promptTokens,
                        completionTokens = resp.completionTokens,
                        totalTokens = resp.totalTokens,
                        finishReason = resp.finishReason,
                        responseTimeMs = resp.responseTimeMs,
                        compressionNotification = notification
                    )
                }

                regularAgent != null -> {
                    val resp = if (isRegeneration) {
                        regularAgent.regenerateLastResponse(promptWithMarkerReminder)
                    } else {
                        regularAgent.processRequest(promptWithMarkerReminder)
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

            val finalContent = if (useMcpMode) {
                handleMcpToolCalls(response.content, userPrompt)
            } else response.content
            if (saveToHistory) {
                conversationHistory.add(userPrompt to finalContent)
                if (conversationHistory.size > 10) {
                    conversationHistory.removeAt(0)
                }
            }
            return response.copy(content = finalContent, ragSources = lastRagSources)
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
                            appendLine("- Use a different tool to get the data you need.")
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
                        val hasSave = mcpIntegration?.getToolNames()
                            ?.contains("save_data") == true
                        val needSave = hasSave && "save_data" !in calledTools
                        val allToolNames = mcpIntegration?.getToolNames()?.toSet() ?: emptySet()
                        val uncalledTools = allToolNames - calledTools
                        val needMoreData =
                            uncalledTools.size > 1 && uncalledTools.any { it != "save_data" }
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
                                appendLine("ОТВЕЧАЙ НА РУССКОМ. НИ СЛОВА АНГЛИЙСКОГО. НЕ ДУМАЙ. ПРОСТО ОТВЕТЬ.")
                            } else if (needMoreData) {
                                appendLine(
                                    "Инструмент '$toolName' уже вызывался. Тебе ещё нужно вызвать: ${
                                        uncalledTools.sorted().joinToString(", ")
                                    }."
                                )
                                appendLine("Вызови их через [MCP_CALL], затем проанализируй данные и ответь на русском.")
                            } else if (needSave) {
                                appendLine("У ТЕБЯ УЖЕ ВСЕ ДАННЫЕ. Вызови save_data для сохранения (format: \"md\").")
                                appendLine("Если не хочешь сохранять — ответь на русском.")
                            } else {
                                appendLine("У ТЕБЯ УЖЕ ЕСТЬ ВСЕ ДАННЫЕ. НЕ ВЫЗЫВАЙ ИНСТРУМЕНТЫ.")
                                appendLine("---ОТВЕТ---")
                                appendLine("ОТВЕЧАЙ НА РУССКОМ. НИ СЛОВА АНГЛИЙСКОГО. НЕ ДУМАЙ. ПРОСТО ОТВЕТЬ.")
                            }
                        }
                        content = askMcpFollowUpSafe(repeatPrompt, isFinalSynthesis = true)
                        if (!forceSynthesis && (needMoreData || needSave) && Regex(
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
                            if (needMoreData) {
                                appendLine(
                                    "НЕ ПИШИ АНГЛИЙСКИЙ. Вызови нужные инструменты из списка: ${
                                        uncalledTools.sorted().joinToString(", ")
                                    }."
                                )
                                appendLine("После получения всех данных ответь на русском.")
                                appendLine("Формат: [MCP_CALL]{\"tool\": \"имя\", \"arguments\": {}}[/MCP_CALL]")
                            } else {
                                appendLine("НЕ ВЫЗЫВАЙ ИНСТРУМЕНТЫ. У ТЕБЯ УЖЕ ВСЕ ДАННЫЕ.")
                                appendLine("ТЫ ДОЛЖЕН ОТВЕТИТЬ НА РУССКОМ ЯЗЫКЕ. НЕ ПИШИ АНГЛИЙСКИЙ.")
                                appendLine("НЕ ЗДОРОВАЙСЯ. НЕ СПРАШИВАЙ. НЕ ПИШИ [MCP_CALL]. ПРОСТО ОТВЕТЬ.")
                            }
                            appendLine("---ОТВЕТ---")
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
                                if (prev.length > 1000) prev.take(limit) else prev.take(500)
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
                            if (result.length > 1000) result.take(limit) else result.take(500)
                        )
                        appendLine("=== КОНЕЦ ===")
                        appendLine()
                        appendLine(getPipelineNextStep())
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

        val prompt = buildString {
            appendLine("Вопрос пользователя: $originalUserPrompt")
            appendLine()
            appendLine("=== РЕЗУЛЬТАТЫ ИНСТРУМЕНТОВ ===")
            for ((i, r) in allResults.withIndex()) {
                appendLine("--- Результат ${i + 1} ---")
                appendLine(r.take(25000))
                appendLine()
            }
            appendLine("=== КОНЕЦ ===")
            appendLine()
            appendLine("---ОТВЕТ---")
            appendLine("Напиши ответ пользователю на русском языке на основе данных выше.")
            appendLine("ИСПОЛЬЗУЙ ТОЛЬКО ДАННЫЕ ВЫШЕ.")
        }
        var response = askMcpFollowUpSafe(prompt, isFinalSynthesis = true)
        if (response == "NO_MCP_CALL") {
            log("⚠️ MCP: LLM недоступен для синтеза, возвращаю сырые данные")
            return allResults.firstOrNull() ?: fallback
        }
        // Handle MCP_CALL in the response — loop until no more tool calls
        repeat(5) {
            val nextJson = extractMcpCallJson(response) ?: return@repeat
            try {
                val obj = Json.parseToJsonElement(nextJson).jsonObject
                val toolName = obj["tool"]?.jsonPrimitive?.content
                if (toolName != null) {
                    val integration = getIntegrationForTool(toolName)
                    if (integration != null) {
                        log("📦 MCP: Выполняю $toolName из синтеза")
                        val toolResult = integration.executeToolCall(nextJson)
                        log("✅ MCP: $toolName выполнен: ${toolResult.take(100)}")
                        val updatedResults = allResults + toolResult
                        response = buildString {
                            appendLine("Вопрос пользователя: $originalUserPrompt")
                            appendLine()
                            appendLine("=== РЕЗУЛЬТАТЫ ИНСТРУМЕНТОВ ===")
                            for ((i, r) in updatedResults.withIndex()) {
                                appendLine("--- Результат ${i + 1} ---")
                                appendLine(r.take(25000))
                                appendLine()
                            }
                            appendLine("=== КОНЕЦ ===")
                            appendLine()
                            appendLine("---ОТВЕТ---")
                            appendLine("ТЕПЕРЬ напиши ответ пользователю на русском. НЕ ВЫЗЫВАЙ БОЛЬШЕ ИНСТРУМЕНТЫ.")
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
                for ((i, r) in allResults.withIndex()) {
                    appendLine("--- Результат ${i + 1} ---")
                    appendLine(r.take(25000))
                    appendLine()
                }
                appendLine("---ОТВЕТ---")
                appendLine("ТОЛЬКО РУССКИЙ. НЕ ПИШИ [MCP_CALL]. НЕ ПИШИ АНГЛИЙСКИЙ.")
            }
            try {
                val retryResult =
                    stripThinkingPrefix(askMcpFollowUpSafe(retryPrompt, isFinalSynthesis = true))
                return retryResult
            } catch (_: Exception) {
                // fall through to original content
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
        val client: RouterClient = ClientFactory.create()
        val effectiveSystemPrompt = if (isMcpAvailable()) {
            buildString {
                append("ТЫ В РЕЖИМЕ ВЫЗОВА ИНСТРУМЕНТОВ. НЕ ПИШИ НИЧЕГО КРОМЕ [MCP_CALL]. НЕ ДУМАЙ ВСЛУХ. ПРОСТО ВЫЗОВИ ИНСТРУМЕНТ.")
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
            maxTokens = if (isFinalSynthesis) 8192 else 4096,
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
        val contextWindowSize = 131072
        val currentTokens = try {
            if (useStrategicAgent && strategicAgent != null) {
                val stats = strategicAgent!!.getStrategyStats()
                stats.contextSizeTokens
            } else compressedAgent?.getTokenStats()?.totalTokens
                ?: (regularAgent?.getTokenStats()?.totalTokens ?: 0)
        } catch (_: Exception) {
            0
        }
        if (currentTokens <= 0) return ""
        val percent = currentTokens.toDouble() / contextWindowSize * 100
        return when {
            percent > 90 -> "🔴 КРИТИЧЕСКИ: ${currentTokens}/${contextWindowSize} (${
                "%.1f".format(
                    percent
                )
            }%)"

            percent > 70 -> "⚠️ ВНИМАНИЕ: ${currentTokens}/${contextWindowSize} (${
                "%.1f".format(
                    percent
                )
            }%)"

            else -> "✅ Контекст в порядке: ${currentTokens}/${contextWindowSize} (${
                "%.1f".format(
                    percent
                )
            }%)"
        }
    }

    fun clearTokenStats() {
        compressedAgent?.clearTokenStats()
        regularAgent?.clearTokenStats()
        strategicAgent?.clearTokenStats()
    }

    private fun buildSystemPromptWithMemory(): String {
        val footer = if (taskMemorySummary.isNotBlank()) {
            """
            
            ### ФОРМАТ ОТВЕТА ###
            В конце ответа добавь ТОЛЬКО НОВЫЕ элементы (не повторяй уже известные из памяти задачи):
            [CONSTRAINT]новое ограничение 1[/CONSTRAINT]
            [CONSTRAINT]новое ограничение 2[/CONSTRAINT]
            [PROGRESS_DONE]сделанная задача[/PROGRESS_DONE]
            [PROGRESS_IN_PROGRESS]текущая задача[/PROGRESS_IN_PROGRESS]
            [PROGRESS_BLOCKED]заблокированная задача[/PROGRESS_BLOCKED]
            [DECISION]новое решение[/DECISION]
            [CONTEXT]новый контекст — описание[/CONTEXT]
            Можно несколько [CONSTRAINT], [PROGRESS_DONE], [PROGRESS_IN_PROGRESS], [PROGRESS_BLOCKED], [DECISION], [CONTEXT] — каждый в отдельном теге.
            ⚠️ [GOAL] НЕ пиши — цель уже установлена.
            """.trimIndent()
        } else {
            """
            
            ### ФОРМАТ ОТВЕТА ###
            Твой ответ ДОЛЖЕН заканчиваться тегами. Может быть НЕСКОЛЬКО ограничений, статусов задач, решений и контекстов — каждый в отдельном теге:
            [GOAL]цель пользователя одной фразой[/GOAL]
            [CONSTRAINT]ограничение 1[/CONSTRAINT]
            [CONSTRAINT]ограничение 2[/CONSTRAINT]
            [PROGRESS_DONE]сделанная задача[/PROGRESS_DONE]
            [PROGRESS_IN_PROGRESS]текущая задача[/PROGRESS_IN_PROGRESS]
            [PROGRESS_BLOCKED]заблокированная задача[/PROGRESS_BLOCKED]
            [DECISION]решение 1[/DECISION]
            [DECISION]решение 2[/DECISION]
            [CONTEXT]контекст 1 — описание[/CONTEXT]
            [CONTEXT]контекст 2 — описание[/CONTEXT]
            Если каких-то элементов нет — не пиши соответствующий тег. [GOAL] — всегда и обязательно!
            """.trimIndent()
        }
        return if (taskMemorySummary.isNotBlank()) {
            buildString {
                appendLine(systemPrompt)
                appendLine()
                appendLine("### ПАМЯТЬ ЗАДАЧИ (цель и контекст диалога) ###")
                appendLine(taskMemorySummary)
                appendLine("### КОНЕЦ ПАМЯТИ ЗАДАЧИ ###")
                append(footer)
            }
        } else {
            systemPrompt + footer
        }
    }

    private fun isSourceCodeNeeded(question: String): Boolean {
        val lower = question.lowercase()
        val docOnlyPatterns = listOf(
            "что такое", "что это", "описание", "архитектура", "структура проекта",
            "какие фичи", "какие модули", "что входит", "список фич",
            "общее описание", "чем отличается", "для чего нужен", "зачем нужен",
            "какой стек", "какие библиотеки", "как использовать",
            "навигац", "как работает навигац", "экран", "menu", "tab",
            "use case", "бизнес-логика", "сценарий", " workflow",
            "tutorial", "guide", "getting started", "how to use",
            "покажи все", "список всех", "какие сущности", "какие модели",
            "какие классы", "какие репозитории", "какие datasource",
        )
        if (docOnlyPatterns.any { lower.contains(it) }) return false
        val codePatterns = listOf(
            "реализац", "класс", "метод", "функци", "интерфейс", "код",
            "как работает", "как реализован", "покажи код", "пример кода",
            "имплементац", "конструктор", "сигнатура", "типы", "свойства",
            "viewModel", "repository", "datasource", "usecase", "component",
            "покажи исходник", "покажи реализац", "как написан",
        )
        return codePatterns.any { lower.contains(it) }
    }

    private fun buildStructuredRagPrompt(
        userQuery: String,
        ragAnswer: RagAnswer,
        sourceFiles: List<Pair<String, String>> = emptyList()
    ): String {
        val sourcesText = ragAnswer.sources.take(5)
            .mapIndexed { i, s -> "[${i + 1}] ${s.title} — ${s.section}" }
            .joinToString("\n")

        val quotesText = ragAnswer.quotes.take(3)
            .mapIndexed { i, q -> "> [${i + 1}] ${q.text.take(300)}" }
            .joinToString("\n\n")

        @Suppress("LocalVariableName")
        val MAX_TOTAL_SOURCE_BYTES = 8000
        val sourceCodeText = if (sourceFiles.isNotEmpty()) {
            var totalBytes = 0
            buildString {
                appendLine("=== ИСХОДНЫЙ КОД ===")
                for ((path, content) in sourceFiles) {
                    val contentBytes = content.length
                    if (totalBytes + contentBytes > MAX_TOTAL_SOURCE_BYTES) {
                        appendLine("// ... пропущено")
                        break
                    }
                    appendLine("### $path")
                    appendLine("```kotlin")
                    appendLine(content)
                    appendLine("```")
                    appendLine()
                    totalBytes += contentBytes
                }
                appendLine("=== КОНЕЦ КОДА ===")
                appendLine()
            }
        } else ""

        return buildString {
            appendLine("Ответь на вопрос по проекту CalendarKMP на основе контекста ниже.")
            appendLine("Если контекст действительно не содержит информации по вопросу — напиши: \"Нет данных в контексте.\"")
            appendLine("Но сначала убедись, что ты внимательно прочитал все фрагменты — ответ может быть в любом из них.")
            appendLine()
            appendLine("--- КОНТЕКСТ ---")
            appendLine(ragAnswer.answer)
            if (sourceCodeText.isNotEmpty()) {
                appendLine(sourceCodeText)
            }
            appendLine("--- ИСТОЧНИКИ ---")
            appendLine(sourcesText)
            if (quotesText.isNotEmpty()) {
                appendLine()
                appendLine("--- ЦИТАТЫ ---")
                appendLine(quotesText)
            }
            appendLine()
            appendLine("Ссылайся на источники [1], [2]... Отвечай на русском, по существу.")
            appendLine()
            appendLine("Вопрос: $userQuery")
        }
    }
}
