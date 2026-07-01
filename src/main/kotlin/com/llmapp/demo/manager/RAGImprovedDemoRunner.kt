package com.llmapp.demo.manager

import com.llmapp.api.ClientFactory
import com.llmapp.model.ChatMessage
import com.llmapp.model.RouterRequest
import com.llmapp.rag.RAGEnhancer
import com.llmapp.rag.domain.RerankerConfig
import com.llmapp.rag.domain.RerankerType
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private val testQuestions = listOf(
    TestQuestion(
        id = 1,
        question = "Как внедрение VAR и SAOT повлияло на количество ошибок на ЧМ? Какие ещё технологии (Al Rihla) используются для сбора данных?",
        expectedAnswer = "VAR повысил точность с 95% до 99,3%, SAOT использует 12 камер и 29 точек тела (25 сек), мяч Al Rihla собирает 500 точек/сек",
        expectedSources = listOf(
            "Технологии на чемпионатах мира: VAR, датчики и ИИ",
            "Судьи и судейские скандалы на чемпионатах мира"
        ),
    ),
    TestQuestion(
        id = 2,
        question = "Сколько заработала ФИФА на ЧМ-2022 и какие расходы понесли организаторы? Назови конкретные цифры.",
        expectedAnswer = "ФИФА заработала $7,6 млрд (2018-2022), Катар потратил $220 млрд, Бразилия $15 млрд, Россия $14 млрд",
        expectedSources = listOf("Экономика чемпионатов мира"),
    ),
    TestQuestion(
        id = 3,
        question = "Кого можно назвать GOAT в футболе? Какие рекорды Пеле, Месси и Роналдо на ЧМ остаются непревзойдёнными?",
        expectedAnswer = "Пеле (3 титула, дебют в 17 лет), Клозе (16 голов), Месси (26 матчей, 11 MOTM), Дзенга (517 мин без голов), Поццо (2 титула), Михелс (тотальный футбол)",
        expectedSources = listOf(
            "Величайшие игроки в истории чемпионатов мира",
            "Рекорды и статистика чемпионатов мира",
            "Легендарные тренеры чемпионатов мира"
        ),
    ),
    TestQuestion(
        id = 4,
        question = "Какие стадионы ЧМ поражают размерами и стоимостью? Какие технологии ETFE и солнечные панели использовались при строительстве?",
        expectedAnswer = "Маракана (200 тыс зрителей 1950), Ацтека (3 финала), Lusail (88 966 мест, Катар $220 млрд), Stadium 974 (модульный), Mercedes-Benz (солнечные панели)",
        expectedSources = listOf(
            "Стадионы и архитектура чемпионатов мира",
            "Экономика чемпионатов мира"
        ),
    ),
    TestQuestion(
        id = 5,
        question = "Какие аутсайдеры (Корея, Марокко, Хорватия) добивались невероятных высот на ЧМ? Как это повлияло на развитие футбола в их регионах?",
        expectedAnswer = "Марокко (первый африканский полуфинал 2022), Хорватия (финал с 4 млн), Камерун (четвертьфинал 1990), Сенегал (победа над Францией 2002), Южная Корея (полуфинал 2002)",
        expectedSources = listOf(
            "Сборные-сенсации на чемпионатах мира",
            "Достижения африканских сборных на чемпионатах мира",
            "Азиатский футбол на чемпионатах мира"
        ),
    ),
    TestQuestion(
        id = 6,
        question = "Как менялись схемы и формации на ЧМ? Кто из тренеров (Поццо, Михелс) считается новатором и почему?",
        expectedAnswer = "2-3-5 (Пирамида), Методо Поццо, 4-2-4 Бразилия, тотальный футбол Михелса (1974), катеначчо, тики-така Испании (2010)",
        expectedSources = listOf(
            "Эволюция футбольной тактики на чемпионатах мира",
            "Легендарные тренеры чемпионатов мира"
        ),
    ),
    TestQuestion(
        id = 7,
        question = "Какие драматичные моменты (Мараканасо, Берн-1954, Рука Бога, Зидан-2006, 7-1) случались на ЧМ? Опиши самые громкие эпизоды.",
        expectedAnswer = "Рука Бога (1986), Бернское чудо (1954), Мараканасо (1950), Зидан-2006, Бразилия 1-7 Германия (2014), скандалы Корея-2002, гол Лэмпарда (2010)",
        expectedSources = listOf(
            "Легендарные моменты чемпионатов мира",
            "Судьи и судейские скандалы на чемпионатах мира"
        ),
    ),
    TestQuestion(
        id = 8,
        question = "Какие музыкальные хиты (Waka Waka) и традиции болельщиков (мексиканская ола) стали символами ЧМ?",
        expectedAnswer = "Waka Waka (10 млн копий), El Rock del Mundial, Muchachos Аргентины, Three Lions Англии, мексиканская ола, бразильские барабаны, африканские джембе, FIFA Fan Fest",
        expectedSources = listOf(
            "Музыка и гимны чемпионатов мира",
            "Футбольные болельщики и культура фанатов на ЧМ"
        ),
    ),
)

private data class ModeStats(
    val modeName: String,
    val totalChunksFound: Int,
    val totalTimeMs: Long,
    val avgScore: Double,
    val questionsWithNoResults: Int,
    val chunksPerQuestion: Double,
)

class RAGImprovedDemoRunner(
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
    private val threshold = 0.6f
    private val topKBefore = 30
    private val topKAfter = 5

    private suspend fun findWorkingModel(): String {
        currentModel?.let { return it }
        for (i in modelFallbackIndex until modelsByPower.size) {
            val model = modelsByPower[i]
            println("📊 ДЕМО RAG+:   ⏳ Проверяю модель $model...")
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
                println("📊 ДЕМО RAG+:   ✅ Модель $model работает")
                return model
            } catch (e: Exception) {
                println("📊 ДЕМО RAG+:   ⚠️ Модель $model недоступна: ${e.message}")
            }
        }
        throw RuntimeException("Ни одна модель из списка не работает")
    }

    override suspend fun run() {
        val startTime = System.currentTimeMillis()

        println("═══════════════════════════════════════")
        println("📊 ДЕМО RAG+: Улучшенный RAG — сравнение режимов")
        println("📊 ДЕМО RAG+: Поиск рабочей модели...")
        val testModel = findWorkingModel()
        println("📊 ДЕМО RAG+: Используем модель: $testModel")
        println("📊 ДЕМО RAG+: Всего вопросов: ${testQuestions.size}")
        println("═══════════════════════════════════════")

        addMessage("assistant", buildString {
            appendLine("🔬 **Демонстрация: Улучшенный RAG — сравнение режимов**")
            appendLine()
            appendLine("Сравниваются **3 режима** обработки запросов:")
            appendLine()
            appendLine("1️⃣ **Базовый** (top-$topKBefore) — все чанки без фильтрации, чем больше — тем ниже средний score")
            appendLine("2️⃣ **Эвристический фильтр** — порог **${"%.2f".format(threshold)}** + ранжирование по пересечению ключевых слов, top-$topKBefore→top-$topKAfter")
            appendLine("3️⃣ **Rewrite + Эвристика** — расширение запроса + эвристический фильтр")
            appendLine()
            appendLine("Будет опрошено **${testQuestions.size}** вопросов по базе знаний ЧМ.")
            appendLine("Для каждого покажу, сколько чанков нашёл каждый режим и средний score.")
            appendLine()
            appendLine("Модель: **$testModel**")
        }, metadata = "ДЕМОНСТРАЦИЯ RAG УЛУЧШЕННЫЙ")
        delay(6.seconds)

        val enhancer = RAGEnhancer(
            topK = topKBefore,
            rerankerConfig = RerankerConfig(
                type = RerankerType.HEURISTIC,
                similarityThreshold = threshold,
                topKBefore = topKBefore,
                topKAfter = topKAfter,
            ),
        )

        try {
            enhancer.ensureIndexLoaded()
            addMessage(
                "assistant",
                "✅ RAG-индекс загружен. Начинаю сравнение...",
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

        val allBasicStats = mutableListOf<ModeStats>()
        val allFilteredStats = mutableListOf<ModeStats>()
        val allRewriteStats = mutableListOf<ModeStats>()

        for (question in testQuestions) {
            checkCancelled()
            println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            println("📊 ДЕМО RAG+: Вопрос ${question.id}/${testQuestions.size}: ${question.question}")
            println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            addMessage("assistant", buildString {
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("**Вопрос ${question.id}/${testQuestions.size}:** ${question.question}")
                appendLine("📌 Ожидаемый ответ: ${question.expectedAnswer}")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            }, metadata = "Вопрос ${question.id}")
            delay(4.seconds)

            val multi = enhancer.compareModes(question.question)

            val basicStats = showModeResults("1️⃣ Базовый", multi.basic, question)
            delay(7.seconds)
            val filteredStats =
                showModeResults("2️⃣ Эвристика", requireNotNull(multi.filtered), question)
            delay(7.seconds)
            val rewriteStats = showModeResults(
                "3️⃣ Rewrite+Эвристика",
                requireNotNull(multi.rewriteFilter),
                question
            )

            allBasicStats.add(basicStats)
            allFilteredStats.add(filteredStats)
            allRewriteStats.add(rewriteStats)

            delay(3.seconds)
            addMessage("assistant", buildString {
                appendLine("**Итог по вопросу ${question.id}:**")
                appendLine()
                fun avgScore(chunks: List<com.llmapp.rag.domain.SearchResult>): Double =
                    chunks.map { it.score.toDouble() }.average()
                appendLine("| Режим | Чанков | Score | Время |")
                appendLine("|---|---|---|---|")
                appendLine("| Базовый | ${multi.basic.chunks.size} | ${"%.3f".format(avgScore(multi.basic.chunks))} | ${multi.basic.searchTimeMs}мс |")
                appendLine(
                    "| Эвристика | ${multi.filtered.chunks.size} | ${
                        "%.3f".format(
                            avgScore(
                                multi.filtered.chunks
                            )
                        )
                    } | ${multi.filtered.searchTimeMs}мс |"
                )
                appendLine(
                    "| Rewrite+Эвристика | ${multi.rewriteFilter.chunks.size} | ${
                        "%.3f".format(
                            avgScore(multi.rewriteFilter.chunks)
                        )
                    } | ${multi.rewriteFilter.searchTimeMs}мс |"
                )
            }, metadata = "Итог вопроса ${question.id}")
            delay(5.seconds)
        }

        val elapsed = (System.currentTimeMillis() - startTime) / 1000
        printSummary(allBasicStats, allFilteredStats, allRewriteStats, testModel, elapsed)
    }

    private suspend fun showModeResults(
        modeLabel: String,
        ragCtx: com.llmapp.rag.RagContext,
        question: TestQuestion,
    ): ModeStats {
        val chunks = ragCtx.chunks
        val avgScore =
            if (chunks.isNotEmpty()) chunks.map { it.score.toDouble() }.average() else 0.0

        addMessage("assistant", buildString {
            appendLine("$modeLabel (${ragCtx.mode.label}):")
            appendLine("• Найдено чанков: **${chunks.size}** за ${ragCtx.searchTimeMs}мс")
            if (ragCtx.originalQuery != ragCtx.rewrittenQuery && ragCtx.rewrittenQuery != null) {
                appendLine("• Запрос: «${ragCtx.originalQuery}» → «${ragCtx.rewrittenQuery}»")
            }
            if (ragCtx.rerankerResult != null) {
                val removed = ragCtx.rerankerResult.removedCount
                val totalBefore = ragCtx.rerankerResult.originalResults.size
                if (removed > 0) {
                    appendLine(
                        "• До фильтра: **$totalBefore** чанков, отсеяно **$removed** (порог ${
                            "%.2f".format(
                                ragCtx.rerankerResult.config.similarityThreshold
                            )
                        })"
                    )
                }
            }
            appendLine()
            if (chunks.isNotEmpty()) {
                appendLine("**Результаты:**")
                chunks.take(3).forEachIndexed { i, r ->
                    appendLine("  **#${i + 1}** score **${"%.3f".format(r.score)}** — ${r.chunk.title} / ${r.chunk.section}")
                }
                if (chunks.size > 3) {
                    appendLine("  ... и ещё ${chunks.size - 3}")
                }
                appendLine()
                appendLine("Средний score: **${"%.4f".format(avgScore)}**")
            } else {
                appendLine("⚠️ **Не найдено релевантных чанков** (все отсеяны по порогу)")
            }
        }, metadata = "$modeLabel Q${question.id}")

        return ModeStats(
            modeName = modeLabel,
            totalChunksFound = chunks.size,
            totalTimeMs = ragCtx.searchTimeMs,
            avgScore = avgScore,
            questionsWithNoResults = if (chunks.isEmpty()) 1 else 0,
            chunksPerQuestion = chunks.size.toDouble(),
        )
    }

    private suspend fun printSummary(
        basic: List<ModeStats>,
        filtered: List<ModeStats>,
        rewrite: List<ModeStats>,
        model: String,
        elapsedSec: Long,
    ) {
        val totalQuestions = testQuestions.size

        fun aggregate(stats: List<ModeStats>) = object {
            val totalChunks = stats.sumOf { it.totalChunksFound }
            val avgTime = stats.map { it.totalTimeMs }.average().toLong()
            val avgScore =
                if (totalChunks > 0) stats.filter { it.totalChunksFound > 0 }.map { it.avgScore }
                    .average() else 0.0
            val emptyQuestions = stats.sumOf { it.questionsWithNoResults }
            val avgChunks = stats.map { it.chunksPerQuestion }.average()
        }

        val basicAgg = aggregate(basic)
        val filteredAgg = aggregate(filtered)
        val rewriteAgg = aggregate(rewrite)

        addMessage("assistant", buildString {
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("✅ **Демонстрация завершена за ${elapsedSec}с!**")
            appendLine()
            appendLine("**Сводка по всем $totalQuestions вопросам:**")
            appendLine()
            appendLine("| Метрика | Базовый | Эвристика | Rewrite+Эвристика |")
            appendLine("|---|---|---|---|")
            appendLine("| Всего чанков | ${basicAgg.totalChunks} | ${filteredAgg.totalChunks} | ${rewriteAgg.totalChunks} |")
            appendLine(
                "| Средний score | ${"%.4f".format(basicAgg.avgScore)} | ${
                    "%.4f".format(
                        filteredAgg.avgScore
                    )
                } | ${"%.4f".format(rewriteAgg.avgScore)} |"
            )
            appendLine("| Среднее время | ${basicAgg.avgTime}мс | ${filteredAgg.avgTime}мс | ${rewriteAgg.avgTime}мс |")
            appendLine("| Пустые результаты | ${basicAgg.emptyQuestions} | ${filteredAgg.emptyQuestions} | ${rewriteAgg.emptyQuestions} |")
            appendLine(
                "| Чанков / вопрос | ${"%.1f".format(basicAgg.avgChunks)} | ${
                    "%.1f".format(
                        filteredAgg.avgChunks
                    )
                } | ${"%.1f".format(rewriteAgg.avgChunks)} |"
            )
            appendLine()
            appendLine("**Выводы:**")
            appendLine()
            val filteredImprovement = if (filteredAgg.avgScore > basicAgg.avgScore) {
                "на ${"%.1f".format((filteredAgg.avgScore - basicAgg.avgScore) / basicAgg.avgScore * 100)}% выше"
            } else "ниже"
            appendLine("• **Эвристический фильтр**: качество чанков (score) $filteredImprovement, чем у базового. Учитывает не только embedding, но и пересечение ключевых слов.")
            val rewriteImprovement = if (rewriteAgg.avgScore > filteredAgg.avgScore) {
                "дополнительно на ${"%.1f".format((rewriteAgg.avgScore - filteredAgg.avgScore) / filteredAgg.avgScore * 100)}% выше"
            } else "близко к эвристике"
            appendLine("• **Rewrite + Эвристика**: средний score $rewriteImprovement. Расширение запроса меняет эмбеддинги и набор ключевых слов.")
            appendLine()
            appendLine("**Рекомендация:**")
            appendLine("✅ Использовать **${if (rewriteAgg.avgScore >= filteredAgg.avgScore) "Rewrite + Эвристика" else "Эвристический фильтр"}** как основной режим RAG.")
            appendLine("✅ Порог отсечения: **${"%.2f".format(threshold)}**")
            appendLine("✅ Top-K: **$topKBefore → $topKAfter** (базовый использует $topKBefore)")
            appendLine()
            appendLine("Модель: $model")
            appendLine("Конфигурация: reranker=${if (rewriteAgg.avgScore >= filteredAgg.avgScore) "Rewrite+Эвристика" else "Эвристика"}, threshold=$threshold")
        }, metadata = "ИТОГИ")
    }

}
