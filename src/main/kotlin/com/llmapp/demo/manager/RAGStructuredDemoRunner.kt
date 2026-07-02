package com.llmapp.demo.manager

import com.llmapp.api.ClientFactory
import com.llmapp.model.ChatMessage
import com.llmapp.model.RouterRequest
import com.llmapp.rag.RAGEnhancer
import com.llmapp.rag.domain.RerankerConfig
import com.llmapp.rag.domain.RerankerType
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

private data class StructuredTestQuestion(
    val id: Int,
    val question: String,
    val expectedKeywords: List<String>,
    val expectedBehavior: ExpectedBehavior,
)

enum class ExpectedBehavior {
    SHOULD_ANSWER,      // есть релевантный контент, score > 0.85
    SHOULD_SAY_IDONTKNOW, // score < 0.85 (низкая релевантность)
    NO_CONTEXT,         // совсем не по теме, контекста нет
}

private val testQuestions = listOf(
    // === 1. ВОПРОСЫ, ГДЕ ДОЛЖНО РАБОТАТЬ (SHOULD_ANSWER) ===
    StructuredTestQuestion(
        id = 1,
        question = "Кто выиграл мужской финал чемпионата мира 2022 года?",
        expectedKeywords = listOf("Аргентина", "Месси", "2022"),
        expectedBehavior = ExpectedBehavior.SHOULD_ANSWER,
    ),
    StructuredTestQuestion(
        id = 2,
        question = "Какие технологии (VAR, SAOT, Al Rihla) использовались на ЧМ-2022?",
        expectedKeywords = listOf("VAR", "SAOT", "Al Rihla"),
        expectedBehavior = ExpectedBehavior.SHOULD_ANSWER,
    ),
    StructuredTestQuestion(
        id = 3,
        question = "Сколько заработала ФИФА на цикле 2018-2022?",
        expectedKeywords = listOf("7.6", "млрд", "ФИФА"),
        expectedBehavior = ExpectedBehavior.SHOULD_ANSWER,
    ),
    StructuredTestQuestion(
        id = 4,
        question = "Кто забил больше всего голов в истории чемпионатов мира?",
        expectedKeywords = listOf("Клозе", "16", "Рекордсмен"),
        expectedBehavior = ExpectedBehavior.SHOULD_ANSWER,
    ),

    // === 2. ВОПРОСЫ С НИЗКОЙ РЕЛЕВАНТНОСТЬЮ (score < 0.85) — SHOULD_SAY_IDONTKNOW ===
    // Очень специфические детали, которых нет в базе
    StructuredTestQuestion(
        id = 5,
        question = "Какой был точный вес мяча Al Rihla в граммах?",
        expectedKeywords = listOf("вес", "грамм", "Al Rihla"),
        expectedBehavior = ExpectedBehavior.SHOULD_SAY_IDONTKNOW,
    ),
    StructuredTestQuestion(
        id = 6,
        question = "Сколько именно минут длилась перерыв между первым и вторым таймом финала 2022?",
        expectedKeywords = listOf("перерыв", "минут", "финал", "2022"),
        expectedBehavior = ExpectedBehavior.SHOULD_SAY_IDONTKNOW,
    ),

    // === 3. ВОПРОСЫ БЕЗ КОНТЕКСТА ВООБЩЕ (NO_CONTEXT) — совсем не по теме ===
    StructuredTestQuestion(
        id = 7,
        question = "Как приготовить борщ по старинному рецепту?",
        expectedKeywords = listOf("борщ", "рецепт", "свекласса", "свекла"),
        expectedBehavior = ExpectedBehavior.NO_CONTEXT,
    ),
    StructuredTestQuestion(
        id = 8,
        question = "Какая столица Австралии?",
        expectedKeywords = listOf("Канберра", "Австралия", "столица"),
        expectedBehavior = ExpectedBehavior.NO_CONTEXT,
    ),
)

data class QuestionResult(
    val questionId: Int,
    val question: String,
    val answer: String,
    val sources: List<String>,
    val quotes: List<String>,
    val isUnknown: Boolean,
    val unknownReason: String?,
    val topScore: Float?,
    val chunksFound: Int,
    val responseTimeMs: Long,
    val passed: Boolean,
    val expectedBehavior: ExpectedBehavior,
)

class RAGStructuredDemoRunner(
    onMessageAdded: (ChatMessageUI) -> Unit,
    onTypingStateChanged: (Boolean) -> Unit,
) : BaseDemoRunner(onMessageAdded, onTypingStateChanged) {

    private val apiKey = com.llmapp.api.ApiConfig.getApiKey()
    private val systemPrompt = "Ты полезный ассистент. Отвечай кратко и по делу на русском языке."

    private val modelsByPower = listOf(
        "openai/gpt-oss-20b:free",
        "cognitivecomputations/dolphin-mistral-24b-venice-edition:free",
        "google/gemma-4-26b-a4b-it:free",
        "meta-llama/llama-3.3-70b-instruct:free",
        "nvidia/nemotron-3-super-120b-a12b:free",
    )

    private var currentModel: String? = null
    private var modelFallbackIndex = 0
    private val threshold = 0.85f
    private val topKBefore = 30
    private val topKAfter = 5

    private fun buildStructuredPrompt(
        userQuery: String,
        ragAnswer: com.llmapp.rag.domain.RagAnswer
    ): String {
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
            appendLine("4. Если в контексте есть ответ:")
            appendLine("   — Сначала напиши краткий ответ с inline-ссылками на источники [1], [2]...")
            appendLine("   — Затем на новой строке напиши «**Источники:**»")
            appendLine("   — После этого для каждого фрагмента, который ты использовал, добавь:")
            appendLine("     «> **Фрагмент N** (название источника / раздел): текст цитаты»")
            appendLine("   — Используй ТОЛЬКО цитаты из раздела ЦИТАТЫ выше, не придумывай свои")
            appendLine("5. Если в контексте НЕТ ответа на вопрос — напиши ровно: \"В предоставленном контексте нет информации об этом. Уточните вопрос или задайте другой.\"")
            appendLine("6. НЕ пиши вводные фразы вроде \"Получил контекст, чем могу помочь\" — отвечай сразу по существу")
            appendLine()
            appendLine("Вопрос пользователя: $userQuery")
            appendLine()
            appendLine("Твой ответ (ссылки на источники [1], [2]... обязательны):")
        }
    }

    private suspend fun findWorkingModel(): String {
        currentModel?.let { return it }
        for (i in modelFallbackIndex until modelsByPower.size) {
            val model = modelsByPower[i]
            println("📊 ДЕМО RAG Structured:   ⏳ Проверяю модель $model...")
            try {
                val client = ClientFactory.create(apiKey)
                val request = RouterRequest(
                    model = model,
                    messages = listOf(
                        ChatMessage("system", systemPrompt),
                        ChatMessage("user", "Ответь: 2+2=?")
                    ),
                    maxTokens = 50,
                )
                val response = client.sendRequest(request)
                response.error?.let { throw Exception(it.message) }
                currentModel = model
                modelFallbackIndex = i
                println("📊 ДЕМО RAG Structured:   ✅ Модель $model работает")
                return model
            } catch (e: Exception) {
                println("📊 ДЕМО RAG Structured:   ⚠️ Модель $model недоступна: ${e.message}")
            }
        }
        throw RuntimeException("Ни одна модель из списка не работает")
    }

    override suspend fun run() {
        val startTime = System.currentTimeMillis()

        println("═══════════════════════════════════════")
        println("📊 ДЕМО RAG Structured: Обязательные источники и цитаты + режим 'не знаю'")
        println("📊 ДЕМО RAG Structured: Поиск рабочей модели...")
        val testModel = findWorkingModel()
        println("📊 ДЕМО RAG Structured: Используем модель: $testModel")
        println("📊 ДЕМО RAG Structured: Всего вопросов: ${testQuestions.size}")
        println("═══════════════════════════════════════")

        addMessage("assistant", buildString {
            appendLine("🔬 **Демонстрация: Структурированный RAG с обязательными источниками и цитатами**")
            appendLine()
            appendLine("Проверяем **8 вопросов** по базе знаний ЧМ с требованием:")
            appendLine("1️⃣ Обязательные источники (source + section + chunk_id)")
            appendLine("2️⃣ Обязательные цитаты (фрагменты из найденных чанков)")
            appendLine("3️⃣ Режим 'не знаю' при релевантности ниже порога ($threshold)")
            appendLine()
            appendLine("Модель: **$testModel**")
            appendLine("Порог релевантности: **${"%.2f".format(threshold)}**")
            appendLine("Режим поиска: **Rewrite + Эвристика**, Top-5")
        }, metadata = "ДЕМОНСТРАЦИЯ RAG СТРУКТУРИРОВАННЫЙ")
        delay(6.seconds)

        val enhancer = RAGEnhancer(
            topK = topKBefore,
            rerankerConfig = RerankerConfig(
                type = RerankerType.HEURISTIC,
                similarityThreshold = threshold,
                topKBefore = topKBefore,
                topKAfter = topKAfter,
            ),
            mode = com.llmapp.rag.RagMode.REWRITE_FILTER,
        )

        try {
            enhancer.ensureIndexLoaded()
            addMessage(
                "assistant",
                "✅ RAG-индекс загружен. Начинаю проверку 8 вопросов...",
                metadata = "Загрузка"
            )
        } catch (e: Exception) {
            addMessage(
                "assistant",
                "⚠️ Ошибка загрузки индекса: ${e.message}. Проверь, что индекс построен.",
                metadata = "Ошибка"
            )
            return
        }
        delay(4.seconds)

        val results = mutableListOf<QuestionResult>()

        for (question in testQuestions) {
            checkCancelled()
            println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            println("📊 ДЕМО RAG Structured: Вопрос ${question.id}/${testQuestions.size}: ${question.question}")
            println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            addMessage("assistant", buildString {
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("**Вопрос ${question.id}/${testQuestions.size}:** ${question.question}")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            }, metadata = "Вопрос ${question.id}")
            delay(3.seconds)

            val ragAnswer = enhancer.searchWithStructuredContext(question.question, threshold)

            // Ask LLM to generate answer from RAG context
            val structuredPrompt = buildStructuredPrompt(question.question, ragAnswer)
            val modelsToTry = modelsByPower.drop(modelsByPower.indexOf(testModel))

            var llmAnswer = ""
            var responseTimeMs = 0L

            for (model in modelsToTry) {
                val client = ClientFactory.create(apiKey)
                val request = RouterRequest(
                    model = model,
                    messages = listOf(
                        ChatMessage("system", systemPrompt),
                        ChatMessage("user", structuredPrompt),
                    ),
                    maxTokens = 2048,
                    temperature = 0.3,
                )

                val responseStart = System.currentTimeMillis()
                try {
                    val response = withTimeout(120.seconds) {
                        client.sendRequest(request)
                    }
                    responseTimeMs = System.currentTimeMillis() - responseStart

                    llmAnswer = response.choices?.firstOrNull()?.message?.content?.trim()
                        ?: "Ошибка: пустой ответ"
                    response.error?.let { llmAnswer = "Ошибка API: ${it.message}" }
                } catch (e: Exception) {
                    responseTimeMs = System.currentTimeMillis() - responseStart
                    llmAnswer = "Ошибка: ${e.message}"
                }

                if (!isGarbageResponse(llmAnswer)) {
                    if (model != currentModel) {
                        currentModel = model
                        modelFallbackIndex = modelsByPower.indexOf(model).coerceAtLeast(0)
                    }
                    break
                }
                println("📊 ДЕМО RAG Structured:   ⚠️ Модель $model вернула мусор, пробую следующую...")
            }

            // Validate: check if answer contains sources [1], [2] and has quotes
            val hasSourceRefs = Regex("\\[\\d+]").containsMatchIn(llmAnswer)
            val hasKeywords = question.expectedKeywords.any { keyword ->
                llmAnswer.contains(keyword, ignoreCase = true)
            }

            // Detect if LLM said "don't know" — no source refs and a refusal phrase
            val isIDontKnow = !hasSourceRefs && (
                    llmAnswer.contains("не знаю", ignoreCase = true)
                            || llmAnswer.contains("нет информации", ignoreCase = true)
                            || llmAnswer.contains("не может", ignoreCase = true)
                    )

            val sourceStrings = ragAnswer.sources.map {
                "[${ragAnswer.sources.indexOf(it) + 1}] ${it.title} — ${it.section} (score: ${
                    "%.3f".format(it.score)
                })"
            }
            val quoteStrings = ragAnswer.quotes.map { "> ${it.text}" }

            // Extract which sources were actually used by the model
            val usedSourceIndices = Regex("\\[(\\d+)]").findAll(llmAnswer)
                .map { it.groupValues[1].toInt() - 1 }
                .distinct()
                .filter { it in ragAnswer.sources.indices }
                .toList()

            val usedSources = usedSourceIndices.map { sourceStrings[it] }
            val usedQuotes = usedSourceIndices.map { quoteStrings[it] }

            // Определяем passed на основе expectedBehavior
            val passed = when (question.expectedBehavior) {
                ExpectedBehavior.SHOULD_ANSWER -> hasSourceRefs && hasKeywords
                ExpectedBehavior.SHOULD_SAY_IDONTKNOW -> isIDontKnow
                ExpectedBehavior.NO_CONTEXT -> isIDontKnow
            }

            val result = QuestionResult(
                questionId = question.id,
                question = question.question,
                answer = llmAnswer,
                sources = usedSources,  // Only sources actually used
                quotes = usedQuotes,    // Only quotes actually used
                isUnknown = false,
                unknownReason = null,
                topScore = ragAnswer.topScore,
                chunksFound = ragAnswer.totalChunks,
                responseTimeMs = responseTimeMs,
                passed = passed,
                expectedBehavior = question.expectedBehavior,
            )
            results.add(result)

            addMessage(
                "assistant",
                buildString {
                    appendLine("📝 **Ответ LLM:**")
                    appendLine(llmAnswer)
                    appendLine()
                    appendLine(
                        "**Ожидаемое поведение:** ${
                            when (question.expectedBehavior) {
                                ExpectedBehavior.SHOULD_ANSWER -> "Должен ответить с источниками"
                                ExpectedBehavior.SHOULD_SAY_IDONTKNOW -> "Должен был сказать 'не знаю' (низкая релевантность)"
                                ExpectedBehavior.NO_CONTEXT -> "Должен был сказать 'не знаю' (нет контекста)"
                            }
                        }"
                    )
                    appendLine()
                    appendLine("**Проверка качества:**")
                    if (question.expectedBehavior == ExpectedBehavior.SHOULD_ANSWER) {
                        appendLine("• Ссылки на источники [1], [2]...: ${if (hasSourceRefs) "✅ ЕСТЬ" else "❌ НЕТ"}")
                        appendLine(
                            "• Ожидаемые ключевые слова: ${if (hasKeywords) "✅ ЕСТЬ" else "❌ НЕТ"} (${
                                question.expectedKeywords.joinToString(", ")
                            })"
                        )
                    } else {
                        appendLine("• Сказал 'не знаю': ${if (isIDontKnow) "✅ ДА" else "❌ НЕТ, ответил без контекста"}")
                    }
                    appendLine("• Топ score: ${"%.3f".format(result.topScore ?: 0f)}")
                    val afterFilter = ragAnswer.chunks.size
                    val beforeFilter = ragAnswer.totalChunks
                    if (afterFilter < beforeFilter) {
                        appendLine("• Чанков найдено: $beforeFilter всего, $afterFilter после фильтрации")
                    } else {
                        appendLine("• Чанков найдено: $beforeFilter")
                    }
                    appendLine("• Время ответа: ${result.responseTimeMs}мс")
                    appendLine("• **Итог: ${if (result.passed) "✅ ПРОЙДЕН" else "❌ НЕ ПРОЙДЕН"}**")
                },
                metadata = "Вопрос ${question.id} — ${if (result.passed) "ПРОЙДЕН" else "НЕ ПРОЙДЕН"}"
            )
            delay(5.seconds)
        }

        val elapsed = (System.currentTimeMillis() - startTime) / 1000
        printSummary(results, testModel, elapsed)
    }

    private suspend fun printSummary(
        results: List<QuestionResult>,
        model: String,
        elapsedSec: Long,
    ) {
        val total = results.size
        val passed = results.count { it.passed }
        val avgTime = results.map { it.responseTimeMs }.average().toLong()
        val avgScore = results.map { it.topScore?.toDouble() ?: 0.0 }.average()

        val shouldAnswer = results.filter { it.expectedBehavior == ExpectedBehavior.SHOULD_ANSWER }
        val shouldSayIdontknow =
            results.filter { it.expectedBehavior == ExpectedBehavior.SHOULD_SAY_IDONTKNOW }
        val noContext = results.filter { it.expectedBehavior == ExpectedBehavior.NO_CONTEXT }

        val shouldAnswerPassed = shouldAnswer.count { it.passed }
        val shouldSayIdontknowCorrect = shouldSayIdontknow.count { it.passed }
        val noContextCorrect = noContext.count { it.passed }

        addMessage("assistant", buildString {
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("✅ **Демонстрация завершена за ${elapsedSec}с!**")
            appendLine()
            appendLine("**Итоговая статистика по $total вопросам:**")
            appendLine()
            appendLine("| Метрика | Значение |")
            appendLine("|---|---|")
            appendLine("| Всего вопросов | $total |")
            appendLine("| Пройдено (всего) | **$passed** (${"%.0f".format(passed.toDouble() / total * 100)}%) |")
            appendLine("| Средний топ score | ${"%.3f".format(avgScore)} |")
            appendLine("| Среднее время ответа | ${avgTime}мс |")
            appendLine("| Модель | $model |")
            appendLine("| Порог релевантности | ${"%.2f".format(threshold)} |")
            appendLine()
            appendLine("**Разбор по категориям:**")
            appendLine()
            appendLine("| Категория | Всего | Корректно | % |")
            appendLine("|---|---|---|---|")
            appendLine(
                "| ✅ Должен ответить (SHOULD_ANSWER) | ${shouldAnswer.size} | $shouldAnswerPassed | ${
                    if (shouldAnswer.isNotEmpty()) "%.0f".format(
                        shouldAnswerPassed.toDouble() / shouldAnswer.size * 100
                    ) else "—"
                }% |"
            )
            appendLine(
                "| ⚠️ Должен сказать \"не знаю\" (SHOULD_SAY_IDONTKNOW) | ${shouldSayIdontknow.size} | $shouldSayIdontknowCorrect | ${
                    if (shouldSayIdontknow.isNotEmpty()) "%.0f".format(
                        shouldSayIdontknowCorrect.toDouble() / shouldSayIdontknow.size * 100
                    ) else "—"
                }% |"
            )
            appendLine(
                "| 🚫 Нет контекста (NO_CONTEXT) | ${noContext.size} | $noContextCorrect | ${
                    if (noContext.isNotEmpty()) "%.0f".format(
                        noContextCorrect.toDouble() / noContext.size * 100
                    ) else "—"
                }% |"
            )
            appendLine()
            appendLine("**Детализация по вопросам:**")
            appendLine()
            appendLine("| # | Вопрос | Статус | Score | Категория |")
            appendLine("|---|---|---|---|---|")
            results.forEach { r ->
                val shortQ = if (r.question.length > 50) r.question.take(50) + "..." else r.question
                val catIcon = when (r.expectedBehavior) {
                    ExpectedBehavior.SHOULD_ANSWER -> "✅"
                    ExpectedBehavior.SHOULD_SAY_IDONTKNOW -> "⚠️"
                    ExpectedBehavior.NO_CONTEXT -> "🚫"
                }
                appendLine(
                    "| ${r.questionId} | $shortQ | ${if (r.passed) "✅" else "❌"} | ${
                        "%.3f".format(r.topScore ?: 0f)
                    } | $catIcon |"
                )
            }
            appendLine()
            appendLine("**Выводы:**")
            appendLine()
            if (passed.toDouble() / total >= 0.8) {
                appendLine("✅ **Система работает отлично** — ${"%.0f".format(passed.toDouble() / total * 100)}% вопросов обработаны корректно")
            } else if (passed.toDouble() / total >= 0.6) {
                appendLine("⚠️ **Система работает удовлетворительно** — ${"%.0f".format(passed.toDouble() / total * 100)}% прошли, есть над чем поработать")
            } else {
                appendLine("❌ **Система требует доработки** — только ${"%.0f".format(passed.toDouble() / total * 100)}% прошли проверку")
            }
        }, metadata = "ИТОГИ")
    }

    private fun isGarbageResponse(response: String): Boolean {
        val trimmed = response.trim()
        if (trimmed.isEmpty()) return true
        if (trimmed.startsWith("Ошибка")) return true
        if (trimmed.contains("<unk>")) return true

        // Binary garbage: too many control characters
        val controlChars = trimmed.count { it.code in 1..31 || it.code in 127..159 }
        if (controlChars > 5) return true

        // Garbled text: less than 30% alphabetic characters
        val letterCount = trimmed.count { it.isLetter() }
        if (letterCount < trimmed.length * 0.3) return true

        return false
    }
}