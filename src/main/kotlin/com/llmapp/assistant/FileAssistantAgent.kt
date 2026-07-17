package com.llmapp.assistant

import com.llmapp.api.ClientFactory
import com.llmapp.api.RouterClient
import com.llmapp.model.ChatMessage
import com.llmapp.model.RouterRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Интерактивный AI-ассистент для работы с файлами проекта.
 *
 * Пользователь задаёт задачу на уровне цели (например, "найди все использования
 * SettingsScreen"), а ассистент сам решает какие файлы читать, искать, изменять.
 *
 * Поддерживаемые операции:
 * - list_files: список файлов проекта
 * - read_file: чтение содержимого файла
 * - search: поиск по содержимому файлов
 * - find_usages: поиск использований класса/функции
 * - write_file: создание/запись файла
 * - move_file: перемещение/переименование файла или директории
 * - delete_file: удаление файла или директории
 * - project_stats: статистика проекта
 * - arch_overview: обзор архитектуры
 */

class FileAssistantAgent(
    projectPath: String,
    private val onProgress: ((String) -> Unit)? = null,
) {
    private val tools = LocalFileTools(projectPath)
    private val index = ProjectIndex(projectPath)
    private val apiClient: RouterClient = ClientFactory.create()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val pendingReadFiles = mutableListOf<String>()

    private val toolSchemas = """
        Ты — AI-ассистент для работы с файлами проекта.
        
        ╔══════════════════════════════════════════════════════════════════╗
        ║  ИНВАРИАНТЫ (нарушение = немедленный отказ)                     ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║  И1: Каждое действие с файлом — ТОЛЬКО через [TOOL_CALL].      ║
        ║  И2: Запись файла — ТОЛЬКО через op=write_file.                ║
        ║  И3: Никаких bash/python/shell скриптов. Никогда.              ║
        ║  И4: Никаких echo/cat/printf/tee/mkdir команд. Никогда.        ║
        ║  И5: Имена классов — ТОЛЬКО из read_file, НЕ из docs/         ║
        ║  И6: Минимум 3 вызова тулзов перед финальным ответом.          ║
        ║  И7: Для каждого класса в ответе — вызов read_file.            ║
        ║  И8: НЕ фабрикуй цифры (N методов, N мест) без read_file!      ║
        ║  И9: Docs/ — только для навигации, НЕ для фактов о коде.       ║
        ║  И10: НЕ утверждай ничего о непрочитанных файлах!              ║
        ║       Если не вызвал read_file — пропусти в отчёте.            ║
        ╚══════════════════════════════════════════════════════════════════╝

        ФОРМАТ ВЫЗОВА (единственно верный):
        [TOOL_CALL]
        {"op": "operation_name", "param": "value"}
        [/TOOL_CALL]

        НЕСКОЛЬКО ВЫЗОВОВ В ОДНОМ ОТВЕТЕ:
        [TOOL_CALL]
        {"op": "search_rag", "query": "ViewModel"}
        [/TOOL_CALL]
        [TOOL_CALL]
        {"op": "read_file", "path": "src/.../MyViewModel.kt"}
        [/TOOL_CALL]

        ДОСТУПНЫЕ ОПЕРАЦИИ:

        [TOOL_CALL]
        {"op": "list_files", "pattern": "ViewModel", "extensions": ["kt"]}
        [/TOOL_CALL]
        extensions ОБЯЗАТЕЛЕН для list_files! Без extensions вернёт 0 файлов.
        Паттерн — подстрока в пути (не glob). Примеры:
        - list_files.extensions=["kt"] — все .kt файлы
        - list_files.pattern="ViewModel".extensions=["kt"] — ViewModel-файлы
        - list_files.pattern="Repository".extensions=["kt"] — Repository-файлы
        НЕ ИСПОЛЬЗУЙ list_files для поиска конкретных файлов (build.gradle, libs.versions.toml)!
        Для поиска по имени файла используй search или search_rag.

        [TOOL_CALL]
        {"op": "search_rag", "query": "поисковый_запрос", "max_results": 10}
        [/TOOL_CALL]

        [TOOL_CALL]
        {"op": "search_code", "query": "поисковый_запрос", "max_results": 10}
        [/TOOL_CALL]

        [TOOL_CALL]
        {"op": "search", "query": "regex_паттерн", "extensions": ["kt"], "max_results": 50}
        [/TOOL_CALL]

        [TOOL_CALL]
        {"op": "search_docs", "query": "поисковый_запрос", "max_results": 10}
        [/TOOL_CALL]

        [TOOL_CALL]
        {"op": "read_file", "path": "src/commonMain/kotlin/.../File.kt"}
        [/TOOL_CALL]

        [TOOL_CALL]
        {"op": "write_file", "path": "docs/README.md", "content": "# Заголовок\\n\\nТекст"}
        [/TOOL_CALL]

        [TOOL_CALL]
        {"op": "move_file", "source": "old/File.kt", "dest": "new/File.kt"}
        [/TOOL_CALL]

        [TOOL_CALL]
        {"op": "delete_file", "path": "path/to/delete"}
        [/TOOL_CALL]

        [TOOL_CALL]
        {"op": "sync_docs", "symbol": "ClassName"}
        [/TOOL_CALL]

        [TOOL_CALL]
        {"op": "reindex", "path": "docs/file.md"}
        [/TOOL_CALL]

        [TOOL_CALL]
        {"op": "project_stats"}
        [/TOOL_CALL]

        ═══════════════════════════════════════════════════════════════
        ПРОЦЕДУРА (строго следуй):
        ═══════════════════════════════════════════════════════════════
        1. Пойми задачу.
        2. Вызови search_code для навигации — найди КАКИЕ .kt файлы релевантны.
        3. Для подсчёта количества (N методов, N мест) — вызови search с regex-паттерном.
        4. Из РЕЗУЛЬТАТОВ search_code/search возьми ТОЧНЫЕ ПУТИ К ФАЙЛАМ (строка 📄).
        5. Для КАЖДОГО найденного .kt файла вызови read_file с ТОЧНЫМ ПУТЕМ.
        6. ТОЛЬКО после read_file ты можешь утверждать факты о коде.
        7. Если задача требует сохранения — вызови write_file ДО финального ответа.
        8. Дай итоговый ответ.

        ВАЖНО:
        - НЕ ПРИДУМЫВАЙ ПУТИ К ФАЙЛАМ! Бери ТОЛЬКО из results search/search_code.
        - search/search_code возвращает 📄 path/to/File.kt — используй этот путь.
        - Используй search (grep) для ПОДСЧЁТА: вызови search с regex-паттерном и посчитай совпадения.
        - Используй search_code для ПОИСКА классов по содержанию.
        - Docs/ (.md) файлы — только для контекста, НЕ для фактов о коде.
        - Числа (N методов, N вызовов) — ТОЛЬКО из search + read_file, никогда из docs.
        - Если search_code/search не находит — используй list_files.

        ПРАВИЛА:
        - write_file: content — строка с \\n для переносов строк.
        - После write_file вызови reindex для этого файла.
        - Если задача содержит "сохрани/запиши/save" — ОБЯЗАТЕЛЬНО вызови write_file ДО финального ответа.
        - НИКОГДА не выводи длинный текст в ответе — если нужно сохранить файл, используй write_file.
        - list_files + read_file: после list_files ОБЯЗАТЕЛЬНО вызови read_file для КАЖДОГО найденного .kt файла.
          Не пропускай файлы! Если list_files вернул 10 файлов — вызови read_file 10 раз.
        - Для поиска по СОДЕРЖИМОМУ файлов (grep) — используй search.
        - Для поиска по ИМЕНИ/ПУТИ файлов — используй list_files с extensions.
        - Для чтения конкретного файла (settings.gradle.kts, libs.versions.toml) — используй read_file напрямую.
        - Для README: прочитай read_file("settings.gradle.kts"), read_file("gradle/libs.versions.toml"),
          list_files(extensions=["kt"]).take(30), search_code для ключевых классов.
        - Пиши на русском языке.
        - НЕ спрашивай разрешения.

        КОНТЕКСТ: $projectPath
        Индекс: ${index.stats()["totalFiles"]} файлов, ${index.stats()["totalChunks"]} чанков.
    """.trimIndent()

    /**
     * Выполнить задачу пользователя.
     * Возвращает финальный ответ ассистента.
     */
    suspend fun executeTask(userTask: String): String {
        onProgress?.invoke("Планирую выполнение задачи...")
        val allToolResults = mutableListOf<String>()
        val calledOps = mutableSetOf<String>()
        val allFoundNames = mutableSetOf<String>() // все имена классов/файлов из результатов тулзов
        val allLlmResponses = mutableListOf<String>() // все ответы LLM для forced-write
        var currentPrompt = "$toolSchemas\n\nЗАДАЧА ПОЛЬЗОВАТЕЛЯ: $userTask"

        repeat(15) { step ->
            debug("STEP", "═══════════════════════════════════════════")
            debug(
                "STEP",
                "Шаг ${step + 1}/10 | Тулзов вызвано: ${allToolResults.size} | Имен найдено: ${allFoundNames.size}"
            )
            onProgress?.invoke("Шаг ${step + 1}: отправляю запрос LLM...")

            debug("PROMPT", "Длина промпта: ${currentPrompt.length} символов")
            debug("PROMPT", "Последние 200 символов промпта:\n...${currentPrompt.takeLast(200)}")

            val llmResponse = callLlm(currentPrompt)
            val content = llmResponse.content
            allLlmResponses.add(content)

            debug("LLM_RAW", "═══ СЫРОЙ ОТВЕТ LLM ═══")
            debug("LLM_RAW", content)
            debug("LLM_RAW", "═══ КОНЕЦ ОТВЕТА LLM ═══")
            debug(
                "LLM_RAW",
                "Токены: prompt=${llmResponse.promptTokens} completion=${llmResponse.completionTokens} | Время: ${llmResponse.responseTimeMs}ms"
            )

            val toolCalls = extractAllToolCalls(content)
            debug("PARSED", "Извлечено тулзов: ${toolCalls.size}")
            for ((i, tc) in toolCalls.withIndex()) {
                debug("PARSED", "  [$i] op=${tc.op} args=${tc.args}")
            }

            // Проверяем что агент не написал bash-скрипт вместо тулзов
            if (toolCalls.isEmpty() && looksLikeBashScript(content)) {
                debug("REJECT", "НАРУШЕНИЕ И3/И4: обнаружен bash-скрипт")
                onProgress?.invoke("Нарушен И3/И4: bash-скрипт вместо тулзов")
                currentPrompt = buildRejectPrompt(
                    userTask,
                    "НАРУШЕНИЕ ИНВАРИАНТОВ И3/И4: Ты написал bash-скрипт!\n" +
                            "ЕДИНСТВЕННЫЙ СПОСОБ записать файл — [TOOL_CALL] с op=write_file.",
                    allToolResults,
                )
                return@repeat
            }

            if (toolCalls.isEmpty()) {
                // Проверяем: задача требует сохранения, но write_file не вызван
                val needsWrite = userTask.contains("сохрани", ignoreCase = true) ||
                        userTask.contains("запиши", ignoreCase = true) ||
                        userTask.contains("save", ignoreCase = true)
                val hasWritten = "write_file" in calledOps

                if (needsWrite && !hasWritten) {
                    // Принудительное сохранение после 5+ тулзов
                    if (allToolResults.size >= 5) {
                        val forcedWritePath = extractForceWritePath(userTask)
                        // Ищем лучший контент среди ВСЕХ ответов LLM
                        // Приоритет: markdown-код-блоки (полный README) > ответы с реальными именами > длина
                        val forcedContent = allLlmResponses
                            .map { response ->
                                val markdownInCodeBlock = Regex(
                                    "```(?:markdown)?\\s*\\n(.*?)\\n```",
                                    RegexOption.DOT_MATCHES_ALL
                                )
                                    .find(response)?.groupValues?.get(1)?.trim()
                                val markdownFromHeading = extractMarkdownFromResponse(response)
                                val bestContent =
                                    if (markdownInCodeBlock != null && markdownInCodeBlock.length > 200)
                                        markdownInCodeBlock else markdownFromHeading
                                val hasRealNames = allFoundNames.any { name ->
                                    (bestContent ?: "").contains(
                                        name,
                                        ignoreCase = true
                                    ) && name.length > 3
                                }
                                bestContent to (bestContent?.length
                                    ?: 0) + if (hasRealNames) 15000 else 0
                            }
                            .filter { it.first != null && it.second > 100 }
                            .maxByOrNull { it.second }
                            ?.first
                        if (forcedWritePath != null && forcedContent != null && forcedContent.length > 100) {
                            debug(
                                "FORCE_WRITE",
                                "Извлечён контент из ответа LLM (${forcedContent.length} символов)"
                            )
                            debug("FORCE_WRITE", "Путь: $forcedWritePath")
                            tools.writeFile(forcedWritePath, forcedContent)
                            index.reindexFile(forcedWritePath)
                            onProgress?.invoke("Файл сохранён принудительно: $forcedWritePath")
                            debug(
                                "ACCEPT",
                                "Финальный ответ принят (forced write, ${allToolResults.size} тулзов)"
                            )
                            return content
                        }
                    }
                    // Ещё не хватает тулзов или не удалось извлечь контент — отправляем обратно
                    val minToolCalls = 5
                    if (allToolResults.size < minToolCalls) {
                        debug(
                            "REJECT",
                            "НАРУШЕНИЕ И6: ${allToolResults.size}/${minToolCalls} тулзов для задачи с сохранением"
                        )
                        onProgress?.invoke("Нужно больше тулзов (${allToolResults.size}/${minToolCalls}) — читай файлы!")
                        currentPrompt = buildRejectPrompt(
                            userTask,
                            "НАРУШЕНИЕ: задача требует сохранения, но ты вызвал ${allToolResults.size} тулзов (нужно $minToolCalls).\n" +
                                    "ОБЯЗАТЕЛЬНО прочитай: 1) settings.gradle.kts, 2) build.gradle.kts, " +
                                    "3) gradle/libs.versions.toml, 4) list_files(extensions=[\"kt\"]), " +
                                    "5) read_file для ключевых классов.\n" +
                                    "Потом СОХРАНИ результат через write_file!",
                            allToolResults,
                        )
                        return@repeat
                    }
                    // Достаточно тулзов, но write_file не вызван — принудительно сохраняем
                    val forcedWritePath = extractForceWritePath(userTask)
                    val forcedContent = allLlmResponses
                        .map { response ->
                            val markdownInCodeBlock = Regex(
                                "```(?:markdown)?\\s*\\n(.*?)\\n```",
                                RegexOption.DOT_MATCHES_ALL
                            )
                                .find(response)?.groupValues?.get(1)?.trim()
                            val markdownFromHeading = extractMarkdownFromResponse(response)
                            val bestContent =
                                if (markdownInCodeBlock != null && markdownInCodeBlock.length > 200)
                                    markdownInCodeBlock else markdownFromHeading
                            val hasRealNames = allFoundNames.any { name ->
                                (bestContent ?: "").contains(
                                    name,
                                    ignoreCase = true
                                ) && name.length > 3
                            }
                            bestContent to (bestContent?.length
                                ?: 0) + if (hasRealNames) 15000 else 0
                        }
                        .filter { it.first != null && it.second > 100 }
                        .maxByOrNull { it.second }
                        ?.first
                    if (forcedWritePath != null && forcedContent != null && forcedContent.length > 100) {
                        debug(
                            "FORCE_WRITE",
                            "Извлечён контент из ответа LLM (${forcedContent.length} символов)"
                        )
                        debug("FORCE_WRITE", "Путь: $forcedWritePath")
                        tools.writeFile(forcedWritePath, forcedContent)
                        index.reindexFile(forcedWritePath)
                        onProgress?.invoke("Файл сохранён принудительно: $forcedWritePath")
                        debug(
                            "ACCEPT",
                            "Финальный ответ принят (forced write, ${allToolResults.size} тулзов)"
                        )
                        return content
                    }
                    debug("REJECT", "НАРУШЕНИЕ: задача требует сохранения, но write_file не вызван")
                    onProgress?.invoke("Задача требует сохранения — вызови write_file!")
                    currentPrompt = buildRejectPrompt(
                        userTask,
                        "НАРУШЕНИЕ: задача содержит 'сохрани/запиши/save', но ты НЕ вызвал write_file!\n" +
                                "НЕ ВЫВОДИ ТЕКСТ В ОТВЕТЕ! Вместо этого — вставь текст в параметр content tool call:\n" +
                                "[TOOL_CALL]\n" +
                                "{\"op\": \"write_file\", \"path\": \"docs/GENERATED_README.md\", \"content\": \"твой_текст_сюда\"}\n" +
                                "[/TOOL_CALL]\n" +
                                "Параметр content — это строка с \\n для переносов строк. НЕ пиши markdown-код блоки!",
                        allToolResults,
                    )
                    return@repeat
                }

                // Ответ без тулзов — принимаем ТОЛЬКО если было достаточно предыдущих вызовов
                if (allToolResults.size < 3) {
                    debug("REJECT", "НАРУШЕНИЕ И6: только ${allToolResults.size} тулзов, нужно ≥3")
                    onProgress?.invoke("Нарушен И6 (${allToolResults.size} тулзов) — отправляю обратно...")
                    currentPrompt = buildRejectPrompt(
                        userTask,
                        "НАРУШЕНИЕ ИНВАРИАНТА И6: вызвано ${allToolResults.size} тулз(ов), нужно минимум 3.\n" +
                                "ОБЯЗАТЕЛЬНО вызови read_file для КАЖДОГО класса перед ответом.",
                        allToolResults,
                    )
                    return@repeat
                }

                debug(
                    "ACCEPT",
                    "Финальный ответ принят (${allToolResults.size} тулзов, ${llmResponse.responseTimeMs}ms)"
                )
                onProgress?.invoke("Задача выполнена за ${step + 1} шагов (${allToolResults.size} тулзов)")
                return content
            }

            // Выполняем ВСЕ tool calls из одного ответа
            for (toolCall in toolCalls) {
                debug("EXEC", "Выполняю: ${toolCall.op}(${toolCall.args})")
                onProgress?.invoke("Выполняю: ${toolCall.op}...")
                val result = executeToolCall(toolCall, userTask)
                debug("EXEC_RESULT", "${toolCall.op} → ${result.length} символов")
                debug("EXEC_RESULT", result.take(500))
                allToolResults.add(result)
                calledOps.add(toolCall.op)
                // Извлекаем имена классов/файлов из результата
                allFoundNames.addAll(extractNamesFromResult(result))
            }

            // Автоматически читаем ViewModel файлы из очереди
            while (pendingReadFiles.isNotEmpty()) {
                val vmFile = pendingReadFiles.removeFirst()
                debug("AUTO_READ", "Читаю ViewModel файл: $vmFile")
                onProgress?.invoke("Читаю: ${vmFile.substringAfterLast("/")}")
                try {
                    val content = tools.readFile(vmFile)
                    val result = "=== $vmFile ===\n$content"
                    allToolResults.add(result)
                    calledOps.add("read_file")
                    allFoundNames.addAll(extractNamesFromResult(result))
                    debug("AUTO_READ", "Прочитан: $vmFile (${content.length} символов)")
                } catch (e: Exception) {
                    debug("AUTO_READ", "Ошибка чтения $vmFile: ${e.message}")
                }
            }
            debug("STATE", "Всего тулзов: ${allToolResults.size} | Имен: ${allFoundNames.size}")

            currentPrompt = buildString {
                appendLine("ЗАДАЧА ПОЛЬЗОВАТЕЛЯ: $userTask")
                appendLine()
                appendLine("=== РЕЗУЛЬТАТЫ ПРЕДЫДУЩИХ ОПЕРАЦИЙ ===")
                for ((i, prev) in allToolResults.withIndex()) {
                    appendLine("--- Операция ${i + 1} ---")
                    appendLine(prev.take(8000))
                    appendLine()
                }
                appendLine("=== КОНЕЦ ===")
                appendLine()
                appendLine("Продолжай выполнять задачу. Если данных מספיק — дай финальный ответ.")
            }
        }

        onProgress?.invoke("Достигнут лимит итераций")
        return "Задача не была завершена за 10 шагов. Результаты: ${
            allToolResults.lastOrNull()?.take(500)
        }"
    }

    /**
     * Извлекает имена классов/файлов из результата тулза.
     */
    private fun extractNamesFromResult(result: String): Set<String> {
        val names = mutableSetOf<String>()
        // Имена файлов из путей
        Regex("/([A-Z][A-Za-z0-9_]+)\\.kt").findAll(result).forEach {
            names.add(it.groupValues[1])
        }
        // class/interface/object имена
        Regex("(?:class|interface|object|enum class|data class)\\s+(\\w+)").findAll(result)
            .forEach {
                names.add(it.groupValues[1])
            }
        // Имена из заголовков результатов search_rag
        Regex("📄\\s+\\S*?/([A-Za-z0-9_]+)\\.kt").findAll(result).forEach {
            names.add(it.groupValues[1])
        }
        // ViewModel имена
        Regex("(\\w+ViewModel)\\b").findAll(result).forEach {
            names.add(it.groupValues[1])
        }
        return names
    }

    private fun extractAllToolCalls(content: String): List<ToolCall> {
        val results = mutableListOf<ToolCall>()
        val markerPattern = Regex(
            "\\[(TOOL_CALL|tool_call)]\\s*",
            RegexOption.DOT_MATCHES_ALL
        )
        val closePattern = Regex(
            "\\[/(TOOL_CALL|tool_call)]",
            RegexOption.DOT_MATCHES_ALL
        )

        val openMatches = markerPattern.findAll(content).toList()
        val closeMatches = closePattern.findAll(content).toList()

        for ((i, openMatch) in openMatches.withIndex()) {
            val openEnd = openMatch.range.last + 1
            val closeMatch = closeMatches.getOrNull(i) ?: break
            val closeStart = closeMatch.range.first
            if (closeStart <= openEnd) continue

            val block = content.substring(openEnd, closeStart).trim()
            try {
                val obj = json.parseToJsonElement(block).jsonObject
                val op = obj["op"]?.jsonPrimitive?.content ?: continue
                val args = mutableMapOf<String, String>()
                for ((key, value) in obj) {
                    if (key != "op") {
                        args[key] = when (value) {
                            is JsonObject -> value.toString()
                            is kotlinx.serialization.json.JsonArray -> value.toString()
                            else -> value.jsonPrimitive.content
                        }
                    }
                }
                results.add(ToolCall(op, args))
            } catch (_: Exception) {
                // пропускаем битые JSON
            }
        }
        return results
    }

    private fun executeToolCall(call: ToolCall, userTask: String = ""): String {
        return try {
            when (call.op) {
                "search_rag" -> {
                    val query = call.args["query"] ?: return "Ошибка: не указан запрос"
                    val maxResults = call.args["max_results"]?.toIntOrNull() ?: 10
                    val results = index.search(query, maxResults = maxResults)
                    if (results.isEmpty()) {
                        "Ничего не найдено по запросу: $query"
                    } else {
                        buildString {
                            appendLine("Найдено ${results.size} релевантных чанков по запросу \"$query\":")
                            for (result in results) {
                                appendLine(
                                    "\n📄 ${result.chunk.filePath} (стр. ${result.chunk.startLine}-${result.chunk.endLine}, score: ${
                                        "%.2f".format(
                                            result.score
                                        )
                                    })"
                                )
                                appendLine("Совпавшие термины: ${result.matchedTerms.joinToString(", ")}")
                                appendLine("```kotlin")
                                appendLine(result.chunk.content.take(1500))
                                if (result.chunk.content.length > 1500) appendLine("// ... [обрезано]")
                                appendLine("```")
                            }
                        }
                    }
                }

                "search_code" -> {
                    val query = call.args["query"] ?: return "Ошибка: не указан запрос"
                    val maxResults = call.args["max_results"]?.toIntOrNull() ?: 10
                    val results = index.searchCode(query, maxResults = maxResults)
                    if (results.isEmpty()) {
                        "Ничего не найдено в коде по запросу: $query"
                    } else {
                        buildString {
                            appendLine("Найдено ${results.size} релевантных чанков кода (.kt) по запросу \"$query\":")
                            for (result in results) {
                                appendLine(
                                    "\n📄 ${result.chunk.filePath} (стр. ${result.chunk.startLine}-${result.chunk.endLine}, score: ${
                                        "%.2f".format(
                                            result.score
                                        )
                                    })"
                                )
                                appendLine("Совпавшие термины: ${result.matchedTerms.joinToString(", ")}")
                                appendLine("```kotlin")
                                appendLine(result.chunk.content.take(1500))
                                if (result.chunk.content.length > 1500) appendLine("// ... [обрезано]")
                                appendLine("```")
                            }
                        }
                    }
                }

                "search_docs" -> {
                    val query = call.args["query"] ?: return "Ошибка: не указан запрос"
                    val maxResults = call.args["max_results"]?.toIntOrNull() ?: 10
                    val results = index.searchDocs(query, maxResults = maxResults)
                    if (results.isEmpty()) {
                        "Ничего не найдено в документации по запросу: $query"
                    } else {
                        buildString {
                            appendLine("Найдено ${results.size} релевантных блоков документации по запросу \"$query\":")
                            for (result in results) {
                                appendLine(
                                    "\n📝 ${result.chunk.filePath} (стр. ${result.chunk.startLine}-${result.chunk.endLine}, score: ${
                                        "%.2f".format(
                                            result.score
                                        )
                                    })"
                                )
                                appendLine("Совпавшие термины: ${result.matchedTerms.joinToString(", ")}")
                                appendLine("```markdown")
                                appendLine(result.chunk.content.take(2000))
                                if (result.chunk.content.length > 2000) appendLine("<!-- ... [обрезано] -->")
                                appendLine("```")
                            }
                        }
                    }
                }

                "sync_docs" -> {
                    val symbol = call.args["symbol"] ?: return "Ошибка: не указан символ"
                    val staleRefs = index.findStaleDocReferences(symbol)
                    if (staleRefs.isEmpty()) {
                        "Документы не содержат упоминаний \"$symbol\" — обновление не требуется"
                    } else {
                        val grouped = staleRefs.groupBy { it.chunk.filePath }
                        buildString {
                            appendLine("Найдено ${staleRefs.size} упоминаний \"$symbol\" в ${grouped.size} документах:")
                            grouped.forEach { (file, matches) ->
                                appendLine("\n📝 $file")
                                matches.take(3).forEach { m ->
                                    appendLine(
                                        "  стр. ${m.chunk.startLine}: ${
                                            m.chunk.content.take(
                                                200
                                            )
                                        }"
                                    )
                                }
                            }
                            appendLine()
                            appendLine("ВНИМАНИЕ: Обновите эти файлы через write_file, если символ был переименован/удалён!")
                        }
                    }
                }

                "reindex" -> {
                    val path = call.args["path"] ?: return "Ошибка: не указан путь"
                    index.reindexFile(path)
                    "Файл переиндексирован: $path"
                }

                "list_files" -> {
                    val extensions = call.args["extensions"]
                        ?.removeSurrounding("[", "]")
                        ?.split(",")
                        ?.map { it.trim().removeSurrounding("\"") }
                        ?.toSet()
                    val pattern = call.args["pattern"]
                    val files = tools.listFiles(pattern = pattern, extensions = extensions)
                    val result =
                        "Найдено ${files.size} файлов:\n${files.take(100).joinToString("\n")}"

                    // Автоматическая очередь read_file для ViewModel файлов
                    if (pattern != null && pattern.contains("ViewModel", ignoreCase = true)) {
                        val vmFiles = files.filter { it.endsWith(".kt") && !it.contains("Module") }
                        if (vmFiles.isNotEmpty()) {
                            pendingReadFiles.addAll(vmFiles)
                            debug(
                                "AUTO_QUEUE",
                                "Добавлено ${vmFiles.size} ViewModel файлов в очередь read_file"
                            )
                        }
                    }
                    result
                }

                "read_file" -> {
                    val path = call.args["path"] ?: return "Ошибка: не указан путь"
                    try {
                        val content = tools.readFile(path)
                        "=== $path ===\n$content"
                    } catch (_: Exception) {
                        "Файл не найден: $path\n" +
                                "Используй search_rag для поиска нужного файла, " +
                                "или list_files с паттерном для поиска по имени."
                    }
                }

                "search" -> {
                    val query = call.args["query"] ?: return "Ошибка: не указан запрос"
                    val extensions = call.args["extensions"]
                        ?.removeSurrounding("[", "]")
                        ?.split(",")
                        ?.map { it.trim().removeSurrounding("\"") }
                        ?.toSet()
                    val matches =
                        tools.searchContent(query, extensions = extensions, maxResults = 50)
                    if (matches.isEmpty()) {
                        "Ничего не найдено по запросу: $query"
                    } else {
                        buildString {
                            appendLine("Найдено ${matches.size} совпадений:")
                            matches.groupBy { it.filePath }.forEach { (file, fileMatches) ->
                                appendLine("\n📁 $file (${fileMatches.size})")
                                fileMatches.take(5).forEach { m ->
                                    appendLine("  :${m.lineNumber}: ${m.lineContent.take(100)}")
                                }
                                if (fileMatches.size > 5) appendLine("  ... и ещё ${fileMatches.size - 5}")
                            }
                        }
                    }
                }

                "find_usages" -> {
                    val symbol = call.args["symbol"] ?: return "Ошибка: не указан символ"
                    val usages = tools.findUsages(symbol)
                    if (usages.isEmpty()) {
                        "Использования '$symbol' не найдены"
                    } else {
                        val grouped = usages.groupBy { it.filePath }
                        buildString {
                            appendLine("Найдено ${usages.size} использований '$symbol' в ${grouped.size} файлах:")
                            grouped.forEach { (file, matches) ->
                                appendLine("\n📁 $file (${matches.size} упоминаний)")
                                matches.take(5).forEach { m ->
                                    appendLine("  :${m.lineNumber}: ${m.lineContent.take(100)}")
                                }
                                if (matches.size > 5) appendLine("  ... и ещё ${matches.size - 5}")
                            }
                        }
                    }
                }

                "write_file" -> {
                    val path = call.args["path"] ?: return "Ошибка: не указан путь"
                    val content = call.args["content"] ?: return "Ошибка: не указано содержимое"
                    val unescaped = content
                        .replace("\\n", "\n")
                        .replace("\\t", "\t")
                        .replace("\\\"", "\"")
                    debug("WRITE", "═══ ЗАПИСЬ ФАЙЛА ═══")
                    debug("WRITE", "Путь: $path")
                    debug(
                        "WRITE",
                        "Содержимое (${unescaped.length} символов, ${unescaped.lines().size} строк):"
                    )
                    debug("WRITE", unescaped.take(1000))
                    if (unescaped.length > 1000) debug(
                        "WRITE",
                        "... [обрезано, всего ${unescaped.length}]"
                    )
                    debug("WRITE", "═══ КОНЕЦ ЗАПИСИ ═══")
                    // Валидация пути: если задача содержит конкретный путь — проверяем
                    val expectedPath = extractForceWritePath(userTask)
                    if (expectedPath != null && !path.equals(expectedPath, ignoreCase = true)) {
                        debug(
                            "WRITE_REJECT",
                            "Путь '$path' не совпадает с ожидаемым '$expectedPath'"
                        )
                        "ОШИБКА: ты записал в '$path', но задача требует путь '$expectedPath'. " +
                                "Вызови write_file с правильным путём: $expectedPath"
                    } else {
                        tools.writeFile(path, unescaped)
                        index.reindexFile(path)
                        "Файл создан/обновлён: $path (${unescaped.lines().size} строк)"
                    }
                }

                "project_stats" -> {
                    val stats = tools.projectStats()
                    buildString {
                        appendLine("Статистика проекта:")
                        stats.entries.sortedByDescending { it.value.second }
                            .forEach { (ext, pair) ->
                                appendLine("  .$ext: ${pair.first} файлов, ${formatBytes(pair.second)}")
                            }
                    }
                }

                "arch_overview" -> {
                    val overview = tools.architecturalOverview()
                    buildString {
                        appendLine("Архитектурный обзор проекта:")
                        overview.entries.sortedBy { it.key }.forEach { (pkg, classes) ->
                            appendLine("\n📦 $pkg (${classes.size})")
                            classes.sorted().forEach { appendLine("  - $it") }
                        }
                    }
                }

                "move_file" -> {
                    val source = call.args["source"] ?: return "Ошибка: не указан source"
                    val dest = call.args["dest"] ?: return "Ошибка: не указан dest"
                    try {
                        tools.moveFile(source, dest)
                        index.reindexFile(dest)
                        "Файл перемещён: $source → $dest"
                    } catch (e: Exception) {
                        "Ошибка перемещения: ${e.message}\nИспользуй list_files/search_rag для поиска исходного файла."
                    }
                }

                "delete_file" -> {
                    val path = call.args["path"] ?: return "Ошибка: не указан путь"
                    try {
                        tools.deleteFile(path)
                        index.reindexFile(path)
                        "Файл удалён: $path"
                    } catch (e: Exception) {
                        "Ошибка удаления: ${e.message}\nИспользуй list_files/search_rag для поиска файла."
                    }
                }

                else -> "Неизвестная операция: ${call.op}"
            }
        } catch (e: Exception) {
            "Ошибка выполнения ${call.op}: ${e.message}"
        }
    }

    /**
     * Собирает промпт для reject с сохранением предыдущих результатов тулзов.
     * Не теряет контекст — агент не будет перезапрашивать то же самое.
     */
    private fun buildRejectPrompt(
        userTask: String,
        violation: String,
        previousResults: List<String>,
    ): String {
        return buildString {
            appendLine(toolSchemas)
            appendLine()
            appendLine(violation)
            appendLine()
            if (previousResults.isNotEmpty()) {
                appendLine("=== УЖЕ ПОЛУЧЕННЫЕ ДАННЫЕ (НЕ ПОВТОРЯЙ ЭТИ ЗАПРОСЫ) ===")
                for ((i, prev) in previousResults.withIndex()) {
                    appendLine("--- Результат ${i + 1} (${prev.length} символов) ---")
                    appendLine(prev.take(4000))
                    appendLine()
                }
                appendLine("=== КОНЕЦ ДАННЫХ ===")
                appendLine()
                appendLine("Проанализируй УЖЕ ПОЛУЧЕННЫЕ данные и дай финальный ответ.")
                appendLine("НЕ ВЫЗЫВАЙ ПОВТОРНО те же search_rag/search_docs запросы!")
            } else {
                appendLine("ЗАДАЧА: $userTask")
            }
        }
    }

    /**
     * Извлекает путь для принудительного сохранения из задачи пользователя.
     * Ищет паттерн "в файл path/to/file" или "в docs/file.md".
     */
    private fun extractForceWritePath(userTask: String): String? {
        // Ищем "в файл path" или "в path/file.md"
        val patterns = listOf(
            Regex("в\\s+файл\\s+(\\S+\\.md)", RegexOption.IGNORE_CASE),
            Regex("в\\s+(docs/\\S+\\.md)", RegexOption.IGNORE_CASE),
            Regex("save\\s+to\\s+(\\S+\\.md)", RegexOption.IGNORE_CASE),
            Regex("сохрани\\s+в\\s+(\\S+\\.md)", RegexOption.IGNORE_CASE),
        )
        for (pattern in patterns) {
            val match = pattern.find(userTask)
            if (match != null) return match.groupValues[1]
        }
        // Дефолтный путь для README
        if (userTask.contains("readme", ignoreCase = true)) {
            return "docs/GENERATED_README.md"
        }
        return null
    }

    /**
     * Извлекает markdown-контент из ответа LLM.
     * Ищет ```markdown ... ``` блоки, ``` ... ``` блоки, или纯 текст с заголовком #.
     */
    private fun extractMarkdownFromResponse(content: String): String? {
        // 1. Ищем markdown code block (```markdown ... ```)
        val markdownPattern =
            Regex("```(?:markdown)?\\s*\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL)
        val match = markdownPattern.find(content)
        if (match != null) {
            val extracted = match.groupValues[1].trim()
            if (extracted.length > 100) return extracted
        }
        // 2. Ищем заголовок # в тексте и берём всё после него
        val hashPatterns = listOf("# ", "## ", "### ")
        for (hashPattern in hashPatterns) {
            val hashIndex = content.indexOf(hashPattern)
            if (hashIndex >= 0) {
                var extracted = content.substring(hashIndex).trim()
                // Убираем trailing tool calls ([TOOL_CALL]...[/TOOL_CALL])
                val toolCallIdx = extracted.indexOf("[TOOL_CALL]")
                if (toolCallIdx > 0) {
                    extracted = extracted.substring(0, toolCallIdx).trimEnd()
                }
                if (extracted.length > 100) return extracted
            }
        }
        // 3. Если текст начинается с #, берём ВСЁ (README обычно начинается с #)
        if (content.trimStart().startsWith("# ")) {
            var extracted = content.trim()
            val toolCallIdx = extracted.indexOf("[TOOL_CALL]")
            if (toolCallIdx > 0) {
                extracted = extracted.substring(0, toolCallIdx).trimEnd()
            }
            if (extracted.length > 100) return extracted
        }
        // 4. Если текст содержит несколько заголовков ## — это README, берём всё
        val h2Count = content.split("\n## ").size - 1
        if (h2Count >= 2) {
            var extracted = content.trim()
            val toolCallIdx = extracted.indexOf("[TOOL_CALL]")
            if (toolCallIdx > 0) {
                extracted = extracted.substring(0, toolCallIdx).trimEnd()
            }
            if (extracted.length > 100) return extracted
        }
        // 5. Ищем "Вот README" или "сгенерирую README" и берём всё после
        val readmePatterns = listOf("README", "Календарь", "CalendarKMP")
        for (pattern in readmePatterns) {
            val idx = content.indexOf(pattern, ignoreCase = true)
            if (idx in 0..<500) {
                val afterPattern = content.substring(idx)
                val headerIdx = afterPattern.indexOf("# ")
                if (headerIdx >= 0) {
                    var extracted = afterPattern.substring(headerIdx).trim()
                    val toolCallIdx = extracted.indexOf("[TOOL_CALL]")
                    if (toolCallIdx > 0) {
                        extracted = extracted.substring(0, toolCallIdx).trimEnd()
                    }
                    if (extracted.length > 100) return extracted
                }
            }
        }
        return null
    }

    private suspend fun callLlm(prompt: String): LlmResult {
        debug(
            "API",
            "→ Отправка запроса: model=mistral/mistral-large-latest temp=0.0 maxTokens=8192"
        )
        val request = RouterRequest(
            model = "mistral/mistral-large-latest",
            messages = listOf(
                ChatMessage(role = "system", content = prompt),
            ),
            maxTokens = 8192,
            temperature = 0.0,
            stop = listOf("```bash", "```sh", "```shell", "```python", "#!/bin/"),
        )
        val startTime = System.currentTimeMillis()
        val response = apiClient.sendRequest(request)
        val elapsed = System.currentTimeMillis() - startTime

        val content = response.choices?.firstOrNull()?.message?.content ?: ""
        debug(
            "API",
            "← Ответ получен за ${elapsed}ms | Токены: prompt=${response.usage?.promptTokens} completion=${response.usage?.completionTokens}"
        )

        if (response.error != null) {
            debug("API_ERROR", "code=${response.error.code} message=${response.error.message}")
        }

        return LlmResult(
            content = content,
            promptTokens = response.usage?.promptTokens,
            completionTokens = response.usage?.completionTokens,
            responseTimeMs = elapsed,
        )
    }

    /**
     * Определяет содержит ли ответ bash-скрипты/команды вместо тулзов.
     */
    private fun looksLikeBashScript(text: String): Boolean {
        val bashPatterns = listOf(
            Regex(
                "^\\s*(echo|cat|printf|tee|mkdir|cp|mv|rm|touch|sed|awk|grep)\\s+",
                RegexOption.MULTILINE
            ),
            Regex("^\\s*#!/bin/(ba)?sh", RegexOption.MULTILINE),
            Regex("\\becho\\s+[\"']?.*?>\\s*\\S+\\.md"),
            Regex("\\bcat\\s+<<\\s*EOF"),
            Regex("\\bcat\\s*>>?\\s+\\S+\\.md"),
            Regex("\\bprintf\\s+.+>\\s+\\S+\\.md"),
            Regex("\\btee\\s+\\S+\\.md"),
            Regex("```bash"),
            Regex("```sh"),
            Regex("```shell"),
            Regex("```python"),
            Regex("^\\s*cd\\s+\\S+", RegexOption.MULTILINE),
            Regex("^\\s*mkdir\\s+-p\\s+", RegexOption.MULTILINE),
        )
        val matches = bashPatterns.count { it.containsMatchIn(text) }
        return matches >= 2
    }

    private fun debug(tag: String, msg: String) {
        println("[FILE_AGENT][$tag] $msg")
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> "${"%.1f".format(bytes / 1024.0 / 1024.0)}MB"
    }

    private data class ToolCall(val op: String, val args: Map<String, String>)

    private data class LlmResult(
        val content: String,
        val promptTokens: Int?,
        val completionTokens: Int?,
        val responseTimeMs: Long,
    )
}
