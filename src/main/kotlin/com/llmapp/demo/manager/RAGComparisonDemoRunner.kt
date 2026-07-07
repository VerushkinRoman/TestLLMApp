package com.llmapp.demo.manager

import com.llmapp.api.ClientFactory
import com.llmapp.model.ChatMessage
import com.llmapp.model.RouterRequest
import com.llmapp.rag.RAGEnhancer
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private data class ComparisonTestQuestion(
    val id: Int,
    val question: String,
    val expectedAnswer: String,
    val expectedSources: List<String>,
)

private val testQuestions = listOf(
    ComparisonTestQuestion(
        id = 1,
        question = "Кто выиграл финал чемпионата мира 2022 года и с каким счётом?",
        expectedAnswer = "Аргентина выиграла у Франции в серии пенальти (4:2) после счёта 3:3 в основное время",
        expectedSources = listOf(
            "Триумф Аргентины на Чемпионате мира 2022",
            "Легендарные моменты чемпионатов мира"
        ),
    ),
    ComparisonTestQuestion(
        id = 2,
        question = "Какие страны будут принимать чемпионат мира 2026 года?",
        expectedAnswer = "США, Канада и Мексика — три страны-хозяйки",
        expectedSources = listOf("Чемпионат мира 2026: Города, формат и превью"),
    ),
    ComparisonTestQuestion(
        id = 3,
        question = "Сколько голов забил Мирослав Клозе на чемпионатах мира?",
        expectedAnswer = "16 голов — рекорд в истории чемпионатов мира",
        expectedSources = listOf("Рекорды и статистика чемпионатов мира"),
    ),
    ComparisonTestQuestion(
        id = 4,
        question = "Что такое «Мараканасо»?",
        expectedAnswer = "Поражение Бразилии от Уругвая 1:2 на домашнем стадионе Маракана в финале ЧМ-1950",
        expectedSources = listOf("Легендарные моменты чемпионатов мира"),
    ),
    ComparisonTestQuestion(
        id = 5,
        question = "Кто забил «гол столетия» и в каком году?",
        expectedAnswer = "Диего Марадона в 1986 году в четвертьфинале против Англии",
        expectedSources = listOf(
            "Легендарные моменты чемпионатов мира",
            "Величайшие игроки в истории чемпионатов мира"
        ),
    ),
    ComparisonTestQuestion(
        id = 6,
        question = "Какая африканская сборная первой вышла в полуфинал чемпионата мира?",
        expectedAnswer = "Марокко в 2022 году",
        expectedSources = listOf("Достижения африканских сборных на чемпионатах мира"),
    ),
    ComparisonTestQuestion(
        id = 7,
        question = "Какой формат будет у чемпионата мира 2026 года?",
        expectedAnswer = "48 команд, 16 групп по 3 команды, 104 матча",
        expectedSources = listOf("Чемпионат мира 2026: Города, формат и превью"),
    ),
    ComparisonTestQuestion(
        id = 8,
        question = "Кто является рекордсменом по количеству матчей на чемпионатах мира?",
        expectedAnswer = "Лионель Месси — 26 матчей на пяти турнирах",
        expectedSources = listOf(
            "Рекорды и статистика чемпионатов мира",
            "Величайшие игроки в истории чемпионатов мира"
        ),
    ),
    ComparisonTestQuestion(
        id = 9,
        question = "Какая команда выиграла женский чемпионат мира в 2023 году?",
        expectedAnswer = "Испания, победив Англию 1:0 в финале",
        expectedSources = listOf("Женский чемпионат мира по футболу"),
    ),
    ComparisonTestQuestion(
        id = 10,
        question = "Когда впервые применили VAR на чемпионатах мира и на сколько повысилась точность решений?",
        expectedAnswer = "Впервые VAR применили на ЧМ-2018 в России, точность решений выросла с 95% до 99,3%",
        expectedSources = listOf("Технологии на чемпионатах мира: VAR, датчики и ИИ"),
    ),
)

class RAGComparisonDemoRunner(
    onMessageAdded: (ChatMessageUI) -> Unit,
    onTypingStateChanged: (Boolean) -> Unit,
) : BaseDemoRunner(onMessageAdded, onTypingStateChanged) {

    private val systemPrompt = "Ты полезный ассистент. Отвечай кратко и по делу на русском языке."

    // Модели от самой слабой к самой сильной
    private val modelsByPower = listOf(
        "openai/gpt-oss-20b:free",
        "cognitivecomputations/dolphin-mistral-24b-venice-edition:free",
        "poolside/laguna-xs.2:free",
        "google/gemma-4-26b-a4b-it:free",
        "google/gemma-4-31b-it:free",
        "meta-llama/llama-3.3-70b-instruct:free",
        "openai/gpt-oss-120b:free",
        "nvidia/nemotron-3-super-120b-a12b:free",
    )

    private var currentModel: String? = null
    private var modelFallbackIndex = 0

    private suspend fun findWorkingModel(): String {
        currentModel?.let { return it }
        for (i in modelFallbackIndex until modelsByPower.size) {
            val model = modelsByPower[i]
            println("📊 ДЕМО RAG:   ⏳ Проверяю модель $model...")
            try {
                val client = ClientFactory.create()
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
                println("📊 ДЕМО RAG:   ✅ Модель $model работает, буду использовать её")
                return model
            } catch (e: Exception) {
                println("📊 ДЕМО RAG:   ⚠️ Модель $model недоступна: ${e.message}")
            }
        }
        throw RuntimeException("Ни одна модель из списка не работает")
    }

    override suspend fun run() {
        val startTime = System.currentTimeMillis()

        println("═══════════════════════════════════════")
        println("📊 ДЕМО RAG: Поиск рабочей слабой модели...")
        val testModel = findWorkingModel()
        println("📊 ДЕМО RAG: Используем модель: $testModel")
        println("📊 ДЕМО RAG: Всего вопросов: ${testQuestions.size}")
        println("═══════════════════════════════════════")

        addMessage("assistant", buildString {
            appendLine("📊 **Демонстрация: Сравнение ответов с RAG и без RAG**")
            appendLine()
            appendLine("Будет задано **10 вопросов** по базе знаний чемпионатов мира.")
            appendLine("Для каждого вопроса:")
            appendLine("1. ❌ Ответ **без RAG** — только знания модели")
            appendLine("2. ✅ Ответ **с RAG** — с контекстом из базы знаний")
            appendLine()
            appendLine("Модель: **$testModel** (одинаковая для обоих ответов)")
            appendLine("База знаний: **25 статей** о ЧМ по футболу")
            appendLine()
            appendLine("⏳ Загрузка RAG-индекса...")
        }, metadata = "ДЕМОНСТРАЦИЯ RAG СРАВНЕНИЕ")
        delay(5.seconds)

        val enhancer = RAGEnhancer()
        try {
            enhancer.ensureIndexLoaded()
            addMessage(
                "assistant",
                "✅ RAG-индекс загружен. Поиск будет по ${enhancer.topK} релевантным фрагментам.",
                metadata = "Загрузка"
            )
        } catch (e: Exception) {
            addMessage(
                "assistant",
                "⚠️ Ошибка загрузки индекса: ${e.message}. Проверь, что индекс построен на вкладке Index.",
                metadata = "Ошибка"
            )
            println("📊 ДЕМО RAG: Ошибка загрузки индекса: ${e.message}")
            delay(4.seconds)
            addMessage("assistant", "⚠️ Продолжаю без RAG для сравнения...", metadata = "Ошибка")
        }
        delay(4.seconds)

        val totalQuestions = testQuestions.size

        for ((qIndex, question) in testQuestions.withIndex()) {
            checkCancelled()

            println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            println("📊 ДЕМО RAG: Вопрос ${question.id}/$totalQuestions")
            println("📊 ДЕМО RAG: ${question.question}")
            println("📊 ДЕМО RAG: Ожидаемый ответ: ${question.expectedAnswer}")
            println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            addMessage("assistant", buildString {
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("**Вопрос ${question.id}/${totalQuestions}:** ${question.question}")
                appendLine()
                appendLine("📌 **Ожидаемый ответ:** ${question.expectedAnswer}")
                appendLine("📚 **Источники в базе:** ${question.expectedSources.joinToString(", ")}")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            }, metadata = "Вопрос ${question.id}")
            delay(5.seconds)

            println("📊 ДЕМО RAG: → Без RAG (модель $testModel)")
            runWithoutRag(question, testModel)
            delay(4.seconds)

            println("📊 ДЕМО RAG: → С RAG (модель $testModel + контекст)")
            runWithRag(question, enhancer, testModel)
            delay(4.seconds)

            addMessage("assistant", buildString {
                appendLine("**Наблюдение для вопроса ${question.id}:**")
                appendLine("Сравни ответы выше. RAG-контекст должен добавить точных фактов из базы знаний.")
                appendLine("Обе модели — одна и та же ($testModel), разница только в наличии контекста.")
            }, metadata = "Оценка ${question.id}")
            println("📊 ДЕМО RAG: Оценка вопроса ${question.id}")
            delay(6.seconds)

            if (qIndex < totalQuestions - 1) {
                val remaining = totalQuestions - qIndex - 1
                addMessage(
                    "assistant",
                    "🔄 Прогресс: **${qIndex + 1}/$totalQuestions** (осталось $remaining вопросов)",
                    metadata = "Прогресс"
                )
                println("📊 ДЕМО RAG: Прогресс ${qIndex + 1}/$totalQuestions, осталось $remaining")
                delay(4.seconds)
            }
        }

        val elapsed = (System.currentTimeMillis() - startTime) / 1000
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("📊 ДЕМО RAG: Завершено за ${elapsed}с")
        println("📊 ДЕМО RAG: Модель: $testModel")
        println("📊 ДЕМО RAG: Всего вопросов: $totalQuestions")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        addMessage("assistant", buildString {
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("✅ **Демонстрация завершена за ${elapsed}с!**")
            appendLine()
            appendLine("**Итоги сравнения:**")
            appendLine()
            appendLine("📊 Опрошено **$totalQuestions** вопросов")
            appendLine("📊 Модель: **$testModel**")
            appendLine()
            appendLine("**Выводы:**")
            appendLine("• Ответы **без RAG** — модель опиралась только на свои знания")
            appendLine("• Ответы **с RAG** — к запросу добавлялся контекст из базы знаний (top-5 чанков)")
            appendLine()
            appendLine("Прокрути вверх и сравни пары ответов для каждого вопроса.")
            appendLine("Обрати внимание на точность фактов, дат и имён.")
        }, metadata = "ИТОГИ")
        delay(2.seconds)
    }

    private suspend fun runWithoutRag(question: ComparisonTestQuestion, testModel: String) {
        val start = System.currentTimeMillis()
        addMessage(
            "assistant",
            "🤖 **Без RAG:** Отправляю запрос модели $testModel...",
            metadata = "Без RAG Q${question.id}"
        )
        println("📊 ДЕМО RAG:   ⏳ Запрос к $testModel...")
        delay(2.seconds)

        try {
            val response = queryLLM(question.question, testModel)
            val elapsed = System.currentTimeMillis() - start
            println("📊 ДЕМО RAG:   ✅ Ответ получен за ${elapsed}мс")
            println("📊 ДЕМО RAG:   📝 Ответ (первые 200): ${response.take(200)}")
            addMessage("assistant", buildString {
                appendLine("🤖 **Ответ без RAG (вопрос ${question.id}, ${elapsed}мс):**")
                appendLine()
                appendLine(response)
                appendLine()
                appendLine("---")
                appendLine("_Только знания модели, без контекста_")
            }, metadata = "❌ Без RAG Q${question.id}")
        } catch (e: Exception) {
            println("📊 ДЕМО RAG:   ❌ Ошибка: ${e.message}")
            addMessage(
                "assistant",
                "🤖 **Без RAG:** Ошибка: ${e.message}",
                metadata = "❌ Без RAG Q${question.id}"
            )
        }
    }

    private suspend fun runWithRag(
        question: ComparisonTestQuestion,
        enhancer: RAGEnhancer,
        testModel: String
    ) {
        val start = System.currentTimeMillis()
        addMessage(
            "assistant",
            "🏛️ **С RAG:** Ищу контекст в базе знаний...",
            metadata = "С RAG Q${question.id}"
        )
        println("📊 ДЕМО RAG:   ⏳ Поиск контекста в RAG-индексе...")
        delay(2.seconds)

        try {
            val ragContext = enhancer.search(question.question)
            println("📊 ДЕМО RAG:   🔍 Найдено ${ragContext.chunks.size} фрагментов за ${ragContext.searchTimeMs}мс")

            if (ragContext.chunks.isEmpty()) {
                println("📊 ДЕМО RAG:   ⚠️ Контекст не найден, отправляю обычный запрос")
                addMessage(
                    "assistant",
                    "🏛️ **С RAG:** Не найдено релевантных чанков в индексе. Отправляю обычный запрос.",
                    metadata = "С RAG Q${question.id}"
                )
                val response = queryLLM(question.question, testModel)
                addMessage("assistant", buildString {
                    appendLine("🤖 **Ответ (без контекста):**")
                    appendLine()
                    appendLine(response)
                }, metadata = "С RAG Q${question.id} (без контекста)")
                return
            }

            addMessage("assistant", buildString {
                appendLine("🏛️ **С RAG: Найдено ${ragContext.chunks.size} релевантных фрагментов**")
                appendLine("⏱ Поиск: ${ragContext.searchTimeMs}мс")
                appendLine()
                for (r in ragContext.chunks) {
                    appendLine(
                        "• «${r.chunk.section}» (${r.chunk.title}) — score **${
                            "%.3f".format(
                                r.score
                            )
                        }**"
                    )
                }
            }, metadata = "С RAG Q${question.id} (контекст)")
            delay(3.seconds)

            val augmentedPrompt = buildString {
                appendLine("Ответь на вопрос пользователя, используя информацию из предоставленного контекста.")
                appendLine("Если в контексте нет ответа — ответь на основе своих знаний, но укажи это.")
                appendLine()
                append(ragContext.combinedContext)
                appendLine("=== КОНЕЦ КОНТЕКСТА ===")
                appendLine()
                appendLine("Вопрос: ${question.question}")
                appendLine()
                appendLine("Ответь на русском языке, используя факты из контекста. Укажи источник информации.")
            }

            addMessage(
                "assistant",
                "🏛️ **С RAG:** Отправляю запрос $testModel с контекстом (${augmentedPrompt.length} символов)...",
                metadata = "С RAG Q${question.id}"
            )
            println("📊 ДЕМО RAG:   ⏳ Запрос к $testModel с контекстом (${augmentedPrompt.length} символов)...")
            delay(2.seconds)

            val response = queryLLM(augmentedPrompt, testModel)
            val elapsed = System.currentTimeMillis() - start
            println("📊 ДЕМО RAG:   ✅ Ответ получен за ${elapsed}мс")
            println("📊 ДЕМО RAG:   📝 Ответ (первые 200): ${response.take(200)}")
            addMessage("assistant", buildString {
                appendLine("🏛️ **Ответ с RAG (вопрос ${question.id}, ${elapsed}мс):**")
                appendLine()
                appendLine(response)
                appendLine()
                appendLine("---")
                appendLine("_Модель отвечала с контекстом из базы знаний_")
                appendLine(
                    "_Источники: ${
                        ragContext.chunks.take(3).joinToString(", ") { it.chunk.title }
                    }_"
                )
            }, metadata = "✅ С RAG Q${question.id}")
        } catch (e: Exception) {
            println("📊 ДЕМО RAG:   ❌ Ошибка: ${e.message}")
            addMessage(
                "assistant",
                "🏛️ **С RAG:** Ошибка: ${e.message}",
                metadata = "✅ С RAG Q${question.id} (ошибка)"
            )
        }
    }

    private suspend fun queryLLM(prompt: String, model: String): String {
        val client = ClientFactory.create()
        val request = RouterRequest(
            model = model,
            messages = listOf(
                ChatMessage("system", systemPrompt),
                ChatMessage("user", prompt),
            ),
            maxTokens = 2048,
        )
        val response = client.sendRequest(request)
        response.error?.let { throw Exception(it.message) }
        return response.choices?.firstOrNull()?.message?.content ?: "Нет ответа"
    }
}
