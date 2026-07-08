package com.llmapp.demo.manager

import com.llmapp.api.ClientFactory
import com.llmapp.demo.evaluation.DemoEvaluator
import com.llmapp.demo.evaluation.TestCase
import com.llmapp.model.ChatMessage
import com.llmapp.model.RouterRequest
import com.llmapp.rag.RAGEnhancer
import com.llmapp.rag.RagMode
import com.llmapp.rag.domain.LLMQueryRewriter
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private data class RagBenchmarkQuestion(
    val id: Int,
    val question: String,
    val expectedAnswer: String,
)

private val benchmarkQuestions = listOf(
    RagBenchmarkQuestion(
        id = 1,
        question = "Кто выиграл финал чемпионата мира 2022 года и с каким счётом?",
        expectedAnswer = "Аргентина выиграла у Франции в серии пенальти (4:2) после счёта 3:3 в основное время",
    ),
    RagBenchmarkQuestion(
        id = 2,
        question = "Сколько голов забил Мирослав Клозе на чемпионатах мира?",
        expectedAnswer = "16 голов — рекорд в истории чемпионатов мира",
    ),
    RagBenchmarkQuestion(
        id = 3,
        question = "Какая африканская сборная первой вышла в полуфинал чемпионата мира?",
        expectedAnswer = "Марокко в 2022 году",
    ),
    RagBenchmarkQuestion(
        id = 4,
        question = "Кто забил «гол столетия» и в каком году?",
        expectedAnswer = "Диего Марадона в 1986 году в четвертьфинале против Англии",
    ),
    RagBenchmarkQuestion(
        id = 5,
        question = "Когда впервые применили VAR на чемпионатах мира?",
        expectedAnswer = "Впервые VAR применили на ЧМ-2018 в России",
    ),
)

data class RagModelAnswer(
    val questionId: Int,
    val question: String,
    val answer: String,
    val timeMs: Long,
    val tokenCount: Int?,
    val error: String? = null,
)

class LocalRAGComparisonDemoRunner(
    onMessageAdded: (ChatMessageUI) -> Unit,
    onTypingStateChanged: (Boolean) -> Unit,
    private val onStatsUpdated: ((List<TestCase>) -> Unit)? = null,
) : BaseDemoRunner(onMessageAdded, onTypingStateChanged) {

    private val localModelName = "gemma4:26b"
    private val cloudModelName = "openai/gpt-oss-20b:free"
    private val evaluatorModel = "meta-llama/llama-3.3-70b-instruct:free"
    private val evaluator = DemoEvaluator(model = evaluatorModel)

    override suspend fun run() {
        val overallStart = System.currentTimeMillis()

        addMessage("assistant", buildString {
            appendLine("🧪 **Демонстрация: Локальный RAG vs Облачный RAG**")
            appendLine()
            appendLine("Сценарий: **полностью локальная RAG-система**")
            appendLine()
            appendLine("**Компоненты:**")
            appendLine("• 🗂️ **Индекс:** JSON-файл с эмбеддингами (HuggingFace granite-embedding)")
            appendLine("• 🔍 **Retrieval:** косинусное сходство (выполняется локально)")
            appendLine("• 🤖 **Локальная модель:** Ollama — **$localModelName**")
            appendLine("• ☁️ **Облачная модель:** KodikRouter — **$cloudModelName**")
            appendLine()
            appendLine("**План:**")
            appendLine("1. Загрузить RAG-индекс")
            appendLine("2. Для каждого вопроса — retrieval + ответ от ОБЕИХ моделей")
            appendLine("3. Сравнить: качество, скорость, стабильность")
            appendLine("4. Отправить результаты облачной модели для экспертной оценки")
            appendLine()
            appendLine("Всего вопросов: **${benchmarkQuestions.size}**")
            appendLine("⏳ Загрузка RAG-индекса...")
        }, metadata = "ДЕМОНСТРАЦИЯ ЛОКАЛЬНЫЙ RAG")
        delay(5.seconds)

        val enhancer = RAGEnhancer(
            mode = RagMode.REWRITE_FILTER,
            queryRewriter = LLMQueryRewriter(
                model = cloudModelName,
                useLocal = false,
            ),
        )
        try {
            enhancer.ensureIndexLoaded()
            addMessage(
                "assistant",
                "✅ RAG-индекс загружен. Поиск будет по top-${enhancer.topK} релевантным фрагментам.",
                metadata = "Загрузка"
            )
        } catch (e: Exception) {
            addMessage(
                "assistant",
                "⚠️ Ошибка загрузки индекса: ${e.message}. Проверь, что индекс построен на вкладке Index.",
                metadata = "Ошибка"
            )
            println("❌ ДЕМО LOCAL RAG: Ошибка загрузки индекса: ${e.message}")
            delay(3.seconds)
            return
        }
        delay(3.seconds)

        val localAnswers = mutableListOf<RagModelAnswer>()
        val cloudAnswers = mutableListOf<RagModelAnswer>()
        val totalQuestions = benchmarkQuestions.size

        for ((qIndex, question) in benchmarkQuestions.withIndex()) {
            checkCancelled()

            println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            println("📊 ДЕМО LOCAL RAG: Вопрос ${question.id}/$totalQuestions")
            println("📊 ДЕМО LOCAL RAG: ${question.question}")
            println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            addMessage("assistant", buildString {
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("**Вопрос ${question.id}/${totalQuestions}:** ${question.question}")
                appendLine("📌 Ожидаемый ответ: ${question.expectedAnswer}")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            }, metadata = "Вопрос ${question.id}")
            delay(3.seconds)

            val ragContext = runRetrieval(enhancer, question)

            val localAnswer = runLocalModel(question, ragContext)
            localAnswers.add(localAnswer)
            delay(2.seconds)

            val cloudAnswer = runCloudModel(question, ragContext)
            cloudAnswers.add(cloudAnswer)
            delay(2.seconds)

            showComparison(question, localAnswer, cloudAnswer)
            delay(5.seconds)

            if (qIndex < totalQuestions - 1) {
                val remaining = totalQuestions - qIndex - 1
                addMessage(
                    "assistant",
                    "🔄 Прогресс: **${qIndex + 1}/$totalQuestions** (осталось $remaining вопросов)",
                    metadata = "Прогресс"
                )
                delay(3.seconds)
            }
        }

        val totalTimeMs = System.currentTimeMillis() - overallStart

        addMessage("assistant", buildString {
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("📊 **Сводная статистика**")
            appendLine()
            appendLine("Общее время: **${formatTime(totalTimeMs)}**")
            appendLine()
            appendLine("**Локальная модель ($localModelName):**")
            val localAvgTime = localAnswers.filter { it.error == null }.map { it.timeMs }.average()
            val localErrors = localAnswers.count { it.error != null }
            appendLine("• Среднее время ответа: **${formatTime(localAvgTime.toLong())}**")
            appendLine("• Ошибок: **$localErrors / ${localAnswers.size}**")
            appendLine("• Стабильность: **${if (localErrors == 0) "✅ Отлично" else "⚠️ " + localErrors + " ошибок"}**")
            appendLine()
            appendLine("**Облачная модель ($cloudModelName):**")
            val cloudAvgTime = cloudAnswers.filter { it.error == null }.map { it.timeMs }.average()
            val cloudErrors = cloudAnswers.count { it.error != null }
            appendLine("• Среднее время ответа: **${formatTime(cloudAvgTime.toLong())}**")
            appendLine("• Ошибок: **$cloudErrors / ${cloudAnswers.size}**")
            appendLine("• Стабильность: **${if (cloudErrors == 0) "✅ Отлично" else "⚠️ " + cloudErrors + " ошибок"}**")
            appendLine()
            val speedDiff = if (localAvgTime > 0 && cloudAvgTime > 0) {
                val ratio = cloudAvgTime / localAvgTime
                if (ratio > 1.1) "☁️ Облачная медленнее локальной в **${"%.1f".format(ratio)}×**"
                else if (ratio < 0.9) "☁️ Облачная быстрее локальной в **${"%.1f".format(1 / ratio)}×**"
                else "⚖️ Скорость примерно **одинаковая**"
            } else "⚖️ Недостаточно данных"
            appendLine(speedDiff)
            appendLine()
            appendLine("⏳ Отправляю результаты на экспертную оценку облачной модели **$evaluatorModel**...")
        }, metadata = "СВОДНАЯ СТАТИСТИКА")
        delay(3.seconds)

        runEvaluation(localAnswers, cloudAnswers)
        delay(2.seconds)

        addMessage("assistant", buildString {
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("✅ **Демонстрация завершена за ${formatTime(totalTimeMs)}!**")
            appendLine()
            appendLine("**Итог:**")
            appendLine("• 🗂️ **RAG-индекс** — загружен локально")
            appendLine("• 🔍 **Retrieval** — выполнен локально (косинусное сходство)")
            appendLine("• 🤖 **Локальная генерация** — через Ollama ($localModelName)")
            appendLine("• ☁️ **Облачная генерация** — через KodikRouter ($cloudModelName)")
            appendLine()
            appendLine("**Результаты сравнения выше.** Прокрути вверх для деталей.")
        }, metadata = "ИТОГИ")
    }

    private suspend fun runRetrieval(enhancer: RAGEnhancer, question: RagBenchmarkQuestion): String {
        addMessage(
            "assistant",
            "🔍 Выполняю retrieval для вопроса **${question.id}**...",
            metadata = "Retrieval Q${question.id}"
        )
        val ragContext = enhancer.search(question.question)

        addMessage("assistant", buildString {
            appendLine("🔍 **Retrieval завершён за ${ragContext.searchTimeMs}мс**")
            if (ragContext.rewrittenQuery != null && ragContext.rewrittenQuery != question.question) {
                appendLine("📝 Запрос расширен LLM: «${question.question}» → «${ragContext.rewrittenQuery}»")
            }
            appendLine("Найдено **${ragContext.chunks.size}** релевантных фрагментов:")
            if (ragContext.chunks.isNotEmpty()) {
                for ((i, r) in ragContext.chunks.withIndex()) {
                    appendLine("  ${i + 1}. «${r.chunk.title}» — ${r.chunk.section} (score: ${"%.3f".format(r.score)})")
                }
            } else {
                appendLine("  ⚠️ Релевантных фрагментов не найдено")
            }
        }, metadata = "Retrieval Q${question.id}")
        println("📊 ДЕМО LOCAL RAG: Retrieval за ${ragContext.searchTimeMs}мс, ${ragContext.chunks.size} чанков")
        delay(2.seconds)
        return ragContext.combinedContext
    }

    private suspend fun runLocalModel(
        question: RagBenchmarkQuestion,
        ragContext: String,
    ): RagModelAnswer {
        addMessage(
            "assistant",
            "🤖 **Локальная модель ($localModelName):** отправляю запрос...",
            metadata = "Локальная Q${question.id}"
        )

        val prompt = buildString {
            appendLine("Ответь на вопрос пользователя, используя информацию из предоставленного контекста.")
            appendLine("Если в контексте нет ответа — ответь на основе своих знаний, но укажи это.")
            appendLine()
            append(ragContext)
            appendLine("=== КОНЕЦ КОНТЕКСТА ===")
            appendLine()
            appendLine("Вопрос: ${question.question}")
            appendLine()
            appendLine("Ответь на русском языке, используя факты из контекста. Укажи источник информации.")
        }

        val answer = runModel(prompt, useLocal = true)
        return answer.also { result ->
            if (result.error != null) {
                addMessage("assistant",
                    "❌ **Локальная модель:** ${result.error}",
                    metadata = "Локальная Q${question.id} (ошибка)")
            } else {
                addMessage("assistant", buildString {
                    appendLine("🤖 **Локальная модель ($localModelName, ${formatTime(result.timeMs)}):**")
                    appendLine()
                    appendLine(result.answer)
                    appendLine()
                    appendLine("---")
                    appendLine("⏱ ${formatTime(result.timeMs)} | 🪙 Токенов: ${result.tokenCount ?: "N/A"}")
                }, metadata = "Локальная Q${question.id}",
                    totalTokens = result.tokenCount,
                    responseTimeMs = result.timeMs)
            }
        }
    }

    private suspend fun runCloudModel(
        question: RagBenchmarkQuestion,
        ragContext: String,
    ): RagModelAnswer {
        addMessage(
            "assistant",
            "☁️ **Облачная модель ($cloudModelName):** отправляю запрос...",
            metadata = "Облачная Q${question.id}"
        )

        val prompt = buildString {
            appendLine("Ответь на вопрос пользователя, используя информацию из предоставленного контекста.")
            appendLine("Если в контексте нет ответа — ответь на основе своих знаний, но укажи это.")
            appendLine()
            append(ragContext)
            appendLine("=== КОНЕЦ КОНТЕКСТА ===")
            appendLine()
            appendLine("Вопрос: ${question.question}")
            appendLine()
            appendLine("Ответь на русском языке, используя факты из контекста. Укажи источник информации.")
        }

        val answer = runModel(prompt, useLocal = false)
        return answer.also { result ->
            if (result.error != null) {
                addMessage("assistant",
                    "❌ **Облачная модель:** ${result.error}",
                    metadata = "Облачная Q${question.id} (ошибка)")
            } else {
                addMessage("assistant", buildString {
                    appendLine("☁️ **Облачная модель ($cloudModelName, ${formatTime(result.timeMs)}):**")
                    appendLine()
                    appendLine(result.answer)
                    appendLine()
                    appendLine("---")
                    appendLine("⏱ ${formatTime(result.timeMs)} | 🪙 Токенов: ${result.tokenCount ?: "N/A"}")
                }, metadata = "Облачная Q${question.id}",
                    totalTokens = result.tokenCount,
                    responseTimeMs = result.timeMs)
            }
        }
    }

    private suspend fun runModel(prompt: String, useLocal: Boolean): RagModelAnswer {
        val modelName = if (useLocal) localModelName else cloudModelName
        println("📊 ДЕМО LOCAL RAG:   ⏳ ${if (useLocal) "Локальная" else "Облачная"} модель $modelName...")

        val savedLocal = ClientFactory.create()
        val wasLocalBefore = (savedLocal as? com.llmapp.api.SwitchingClient)?.useLocal ?: false
        ClientFactory.setUseLocal(useLocal)

        val start = System.currentTimeMillis()
        try {
            val client = ClientFactory.create()
            val request = RouterRequest(
                model = modelName,
                messages = listOf(
                    ChatMessage("system", "Ты полезный ассистент. Отвечай кратко и по делу на русском языке."),
                    ChatMessage("user", prompt),
                ),
                maxTokens = 2048,
            )
            val response = client.sendRequest(request)
            response.error?.let { throw Exception(it.message) }

            val elapsed = System.currentTimeMillis() - start
            val content = response.choices?.firstOrNull()?.message?.content ?: "Нет ответа"
            val tokens = response.usage?.totalTokens

            println("📊 ДЕМО LOCAL RAG:   ✅ Ответ за ${elapsed}мс, ${tokens ?: "?"} токенов")
            println("📊 ДЕМО LOCAL RAG:   📝 ${content.take(200)}...")

            // Restore state
            ClientFactory.setUseLocal(wasLocalBefore)

            return RagModelAnswer(
                questionId = 0,
                question = "",
                answer = content,
                timeMs = elapsed,
                tokenCount = tokens,
            )
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            println("📊 ДЕМО LOCAL RAG:   ❌ Ошибка за ${elapsed}мс: ${e.message}")

            // Restore state
            ClientFactory.setUseLocal(wasLocalBefore)

            return RagModelAnswer(
                questionId = 0,
                question = "",
                answer = "",
                timeMs = elapsed,
                tokenCount = null,
                error = e.message,
            )
        }
    }

    private suspend fun showComparison(
        question: RagBenchmarkQuestion,
        local: RagModelAnswer,
        cloud: RagModelAnswer,
    ) {
        val localOk = local.error == null
        val cloudOk = cloud.error == null
        val localTime = if (localOk) local.timeMs else -1L
        val cloudTime = if (cloudOk) cloud.timeMs else -1L

        val speedComparison = when {
            !localOk || !cloudOk -> "Н/Д (одна из моделей не ответила)"
            cloudTime < localTime * 0.8 -> "☁️ Облачная быстрее в ${"%.1f".format(localTime.toDouble() / cloudTime)}×"
            localTime < cloudTime * 0.8 -> "🤖 Локальная быстрее в ${"%.1f".format(cloudTime.toDouble() / localTime)}×"
            else -> "⚖️ Примерно одинаково"
        }

        val qualityComparison = when {
            !localOk && !cloudOk -> "Обе модели не ответили"
            !localOk -> "☁️ Облачная ответила, локальная — нет"
            !cloudOk -> "🤖 Локальная ответила, облачная — нет"
            else -> "Сравни ответы выше по точности фактов"
        }

        addMessage("assistant", buildString {
            appendLine("**📊 Сравнение по вопросу ${question.id}:**")
            appendLine()
            appendLine("| Параметр | 🤖 Локальная ($localModelName) | ☁️ Облачная ($cloudModelName) |")
            appendLine("|---|---|---|")
            appendLine("| ⏱ Время | ${if (localOk) formatTime(localTime) else "❌ Ошибка"} | ${if (cloudOk) formatTime(cloudTime) else "❌ Ошибка"} |")
            appendLine("| 🪙 Токены | ${local.tokenCount?.toString() ?: "N/A"} | ${cloud.tokenCount?.toString() ?: "N/A"} |")
            appendLine("| ✅ Статус | ${if (localOk) "✅" else "❌"} | ${if (cloudOk) "✅" else "❌"} |")
            appendLine()
            appendLine("**⚡ Скорость:** $speedComparison")
            appendLine("**📋 Качество:** $qualityComparison")
        }, metadata = "Сравнение Q${question.id}")
    }

    private suspend fun runEvaluation(
        localAnswers: List<RagModelAnswer>,
        cloudAnswers: List<RagModelAnswer>,
    ) {
        val testCases = benchmarkQuestions.mapIndexed { index, q ->
            val local = localAnswers.getOrNull(index)
            val cloud = cloudAnswers.getOrNull(index)
            TestCase(
                id = q.id,
                description = "${q.question}\n(Ожидалось: ${q.expectedAnswer})",
                expectedBehavior = "Ответ должен содержать правильные факты из базы знаний ЧМ",
                actualResponse = buildString {
                    appendLine("=== ЛОКАЛЬНАЯ МОДЕЛЬ ($localModelName) ===")
                    appendLine(if (local?.error != null) "[Ошибка: ${local.error}]" else (local?.answer ?: "Нет ответа"))
                    appendLine()
                    appendLine("=== ОБЛАЧНАЯ МОДЕЛЬ ($cloudModelName) ===")
                    appendLine(if (cloud?.error != null) "[Ошибка: ${cloud.error}]" else (cloud?.answer ?: "Нет ответа"))
                },
                metrics = mapOf(
                    "Время локальной" to (local?.timeMs?.let { formatTime(it) } ?: "N/A"),
                    "Время облачной" to (cloud?.timeMs?.let { formatTime(it) } ?: "N/A"),
                    "Токены локальной" to (local?.tokenCount?.toString() ?: "N/A"),
                    "Токены облачной" to (cloud?.tokenCount?.toString() ?: "N/A"),
                    "Ошибка локальной" to (if (local?.error != null) "❌ Да" else "✅ Нет"),
                    "Ошибка облачной" to (if (cloud?.error != null) "❌ Да" else "✅ Нет"),
                ),
            )
        }

        onStatsUpdated?.invoke(testCases)

        addMessage(
            "assistant",
            "⏳ Отправляю **${testCases.size}** кейсов на экспертную оценку модели **$evaluatorModel**...",
            metadata = "Оценка"
        )
        println("📊 ДЕМО LOCAL RAG: Запуск оценки через $evaluatorModel...")
        delay(3.seconds)

        try {
            val evalResult = evaluator.evaluate(
                demoName = "Локальный RAG vs Облачный RAG",
                testCases = testCases,
                additionalContext = buildString {
                    appendLine("Это сравнение двух подходов к RAG-генерации:")
                    appendLine()
                    appendLine("1. **Локальная RAG-система** (полностью на локальной машине):")
                    appendLine("   - Индекс: JSON с эмбеддингами (HuggingFace granite-embedding-97m-multilingual-r2)")
                    appendLine("   - Retrieval: косинусное сходство (выполняется локально)")
                    appendLine("   - Генерация: Ollama ($localModelName)")
                    appendLine()
                    appendLine("2. **Облачная RAG-система**:")
                    appendLine("   - Индекс: тот же JSON (локальный)")
                    appendLine("   - Retrieval: косинусное сходство (локально)")
                    appendLine("   - Генерация: KodikRouter ($cloudModelName)")
                    appendLine()
                    appendLine("Обе модели получают **одинаковый RAG-контекст** для каждого вопроса.")
                    appendLine("Разница только в модели генерации.")
                    appendLine()
                    appendLine("Оцени:")
                    appendLine("1. **Качество ответов** — какая модель точнее использует контекст?")
                    appendLine("2. **Скорость** — какая модель быстрее?")
                    appendLine("3. **Стабильность** — какая модель реже ошибается?")
                    appendLine("4. **Общий вердикт** — какая система лучше для RAG?")
                },
            )
            addMessage("assistant", buildString {
                appendLine("📋 **Экспертная оценка от $evaluatorModel**")
                appendLine()
                appendLine(evalResult)
            }, metadata = "ЭКСПЕРТНАЯ ОЦЕНКА")
            println("📊 ДЕМО LOCAL RAG: ✅ Оценка получена (${evalResult.length} символов)")
        } catch (e: Exception) {
            addMessage("assistant",
                "❌ **Ошибка при получении экспертной оценки:** ${e.message}",
                metadata = "Оценка (ошибка)")
            println("📊 ДЕМО LOCAL RAG: ❌ Ошибка оценки: ${e.message}")
        }
    }
}
