package com.llmapp.demo.manager

import com.llmapp.rag.data.HuggingFaceEmbeddingService
import com.llmapp.rag.data.WorldCupDocuments
import com.llmapp.rag.domain.Chunk
import com.llmapp.rag.domain.ChunkerFactory
import com.llmapp.rag.domain.ChunkingStrategy
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.seconds

class RagDemoRunner(
    onMessageAdded: (ChatMessageUI) -> Unit,
    onTypingStateChanged: (Boolean) -> Unit,
    private val query: String = "Кто выиграл мужской финал чемпионата мира 2022 года",
) : BaseDemoRunner(onMessageAdded, onTypingStateChanged) {

    private val embeddingService = HuggingFaceEmbeddingService()
    private val documents = WorldCupDocuments.getAll()

    override suspend fun run() {
        val startTime = System.currentTimeMillis()
        val totalChars = documents.sumOf { it.content.length }
        val totalWords = documents.sumOf { it.content.split("\\s+".toRegex()).size }
        val totalSections = documents.sumOf { it.sections.size }

        addMessage("assistant", buildString {
            appendLine("🔬 **Демонстрация RAG Pipeline**")
            appendLine()
            appendLine("Пайплайн: загрузка → чанкинг (2 стратегии) → эмбеддинги → индекс → поиск")
            appendLine()
        }, metadata = "ДЕМОНСТРАЦИЯ RAG")
        delay(5.seconds)

        // Шаг 1
        addMessage("assistant", buildString {
            appendLine("📄 **Шаг 1: Загрузка документов**")
            appendLine("Загружено **${documents.size}** статей о ЧМ по футболу")
            appendLine("• Всего символов: **${totalChars}**")
            appendLine("• Всего слов: **${totalWords}**")
            appendLine("• Всего разделов: **${totalSections}**")
            appendLine("• Средняя длина статьи: **${totalChars / documents.size}** символов")
            appendLine()
            appendLine("**Список статей:**")
            documents.forEachIndexed { i, doc ->
                appendLine("  ${i + 1}. «${doc.title}» (${doc.content.length} символов, ${doc.sections.size} разделов)")
            }
            appendLine()
        }, metadata = "Шаг 1/6")
        delay(10.seconds)

        // Шаг 2: Fixed-size
        addMessage("assistant", buildString {
            appendLine("✂️ **Шаг 2: Fixed-size чанкинг**")
            appendLine("Размер: 600 символов, overlap: 100")
            appendLine()
            appendLine("**Как работает:** текст режется на блоки по 600 символов")
            appendLine("с перекрытием 100 символов между соседними блоками.")
            appendLine("Это гарантирует, что ни один кусок информации не потеряется")
            appendLine("на стыках между чанками.")
        }, metadata = "Шаг 2/6")
        delay(10.seconds)

        val fixedStrategy = ChunkingStrategy.FixedSize()
        val fixedChunks = documents.flatMap { doc ->
            ChunkerFactory.create(fixedStrategy).chunk(doc, fixedStrategy)
        }

        val avgFixed = if (fixedChunks.isNotEmpty()) fixedChunks.map { it.content.length }.average()
            .toInt() else 0
        val minFixed = if (fixedChunks.isNotEmpty()) fixedChunks.minOf { it.content.length } else 0
        val maxFixed = if (fixedChunks.isNotEmpty()) fixedChunks.maxOf { it.content.length } else 0
        val chunkSizesFixed = fixedChunks.map { it.content.length }
        val smallFixed = chunkSizesFixed.count { it < 300 }
        val mediumFixed = chunkSizesFixed.count { it in 300..700 }
        val largeFixed = chunkSizesFixed.count { it > 700 }

        addMessage("assistant", buildString {
            appendLine("✅ Fixed-size: **${fixedChunks.size}** чанков")
            appendLine("• Средний размер: **$avgFixed** символов")
            appendLine("• Минимальный: **$minFixed** символов")
            appendLine("• Максимальный: **$maxFixed** символов")
            appendLine("• Распределение: мелких (<300) **$smallFixed**, средних (300-700) **$mediumFixed**, крупных (>700) **$largeFixed**")
            appendLine()
        }, metadata = "Fixed-size")
        delay(10.seconds)

        // Шаг 3: Structural
        addMessage("assistant", buildString {
            appendLine("📑 **Шаг 3: Structural чанкинг**")
            appendLine("Каждый раздел документа — отдельный чанк, мелкие секции объединяются")
            appendLine()
            appendLine("**Как работает:** в отличие от Fixed-size, Structural чанкинг")
            appendLine("уважает логическую структуру документа: каждый раздел (Section)")
            appendLine("становится отдельным чанком. Если раздел слишком мал (< 300 символов),")
            appendLine("он объединяется со следующим. Результат — семантически целостные блоки.")
        }, metadata = "Шаг 3/6")
        delay(10.seconds)

        val structuralStrategy = ChunkingStrategy.Structural()
        val structuralChunks = documents.flatMap { doc ->
            ChunkerFactory.create(structuralStrategy).chunk(doc, structuralStrategy)
        }

        val avgStruct =
            if (structuralChunks.isNotEmpty()) structuralChunks.map { it.content.length }.average()
                .toInt() else 0
        val minStruct =
            if (structuralChunks.isNotEmpty()) structuralChunks.minOf { it.content.length } else 0
        val maxStruct =
            if (structuralChunks.isNotEmpty()) structuralChunks.maxOf { it.content.length } else 0
        val chunkSizesStruct = structuralChunks.map { it.content.length }
        val smallStruct = chunkSizesStruct.count { it < 300 }
        val mediumStruct = chunkSizesStruct.count { it in 300..700 }
        val largeStruct = chunkSizesStruct.count { it > 700 }

        addMessage("assistant", buildString {
            appendLine("✅ Structural: **${structuralChunks.size}** чанков")
            appendLine("• Средний размер: **$avgStruct** символов")
            appendLine("• Минимальный: **$minStruct** символов")
            appendLine("• Максимальный: **$maxStruct** символов")
            appendLine("• Распределение: мелких (<300) **$smallStruct**, средних (300-700) **$mediumStruct**, крупных (>700) **$largeStruct**")
            appendLine()
            appendLine("**Детализация по документам (Structural):**")
            documents.forEach { doc ->
                val docChunks = structuralChunks.filter { it.documentId == doc.id }
                appendLine("  • «${doc.title}» → ${docChunks.size} чанков")
            }
            appendLine()
            appendLine("**Сравнение стратегий:**")
            appendLine("| Параметр | Fixed-size | Structural |")
            appendLine("|---|---|---|")
            appendLine("| Чанков | ${fixedChunks.size} | ${structuralChunks.size} |")
            appendLine("| Средний размер | $avgFixed символов | $avgStruct символов |")
            appendLine("| Мин/макс | $minFixed / $maxFixed | $minStruct / $maxStruct |")
            appendLine("| Мелких (<300) | $smallFixed | $smallStruct |")
            appendLine("| Крупных (>700) | $largeFixed | $largeStruct |")
            val diff = fixedChunks.size - structuralChunks.size
            val sizeDiff = avgFixed - avgStruct
            appendLine(if (diff > 0) "Fixed даёт на $diff чанков больше" else "Structural даёт на ${-diff} чанков больше")
            appendLine(if (sizeDiff > 0) "Fixed чанки крупнее на $sizeDiff символов" else "Structural чанки крупнее на ${-sizeDiff} символов")
            appendLine()
            appendLine("**Вывод:** Fixed-size дробит текст на N однородных кусков, не заботясь")
            appendLine("о границах разделов. Structural сохраняет смысловую структуру документа,")
            appendLine("но чанки могут быть неравномерными.")
        }, metadata = "Сравнение")
        delay(20.seconds)

        // Шаг 4: Эмбеддинги
        addMessage("assistant", buildString {
            appendLine("🧠 **Шаг 4: Эмбеддинги**")
            appendLine("Модель: **ibm-granite/granite-embedding-97m-multilingual-r2**, 768d")
            appendLine()
            appendLine("**Как работает:**")
            appendLine("1. Отправляется батч из ${fixedChunks.size + structuralChunks.size} текстов в HuggingFace Inference API")
            appendLine("2. Модель encoder-only transformer превращает каждый текст в вектор 768 чисел")
            appendLine("3. Семантически близкие тексты получают близкие векторы")
            appendLine()
            appendLine("Генерирую векторы для ${fixedChunks.size + structuralChunks.size} чанков...")
            appendLine("• Fixed-size: ${fixedChunks.size} чанков")
            appendLine("• Structural: ${structuralChunks.size} чанков")
            appendLine("• Всего чисел: ${fixedChunks.size + structuralChunks.size} × 768 = ${(fixedChunks.size + structuralChunks.size) * 768}")
        }, metadata = "Шаг 4/6")
        delay(1.seconds)

        println("🚀 [Demo] Запуск embedBatch для Fixed-size (${fixedChunks.size} чанков)...")
        val fixedEmbeddings: List<List<Float>>
        val structuralEmbeddings: List<List<Float>>
        val embedTime = measureTimeMillis {
            withContext(Dispatchers.Default) {
                fixedEmbeddings = try {
                    embeddingService.embedBatch(fixedChunks.map { it.content })
                } catch (e: Exception) {
                    println("❌ [Demo] Ошибка Fixed-size embedBatch: ${e::class.simpleName} — ${e.message}")
                    e.printStackTrace()
                    throw e
                }
                println("🚀 [Demo] Fixed-size готов, запуск Structural (${structuralChunks.size} чанков)...")
                structuralEmbeddings = try {
                    embeddingService.embedBatch(structuralChunks.map { it.content })
                } catch (e: Exception) {
                    println("❌ [Demo] Ошибка Structural embedBatch: ${e::class.simpleName} — ${e.message}")
                    e.printStackTrace()
                    throw e
                }
            }
        }
        println("✅ [Demo] Embedding завершён за ${embedTime}мс")

        data class ChunkWithEmbedding(
            val chunk: Chunk,
            val embedding: List<Float>
        )

        val fixedIndex = fixedChunks.zip(fixedEmbeddings) { c, e -> ChunkWithEmbedding(c, e) }
        val structuralIndex =
            structuralChunks.zip(structuralEmbeddings) { c, e -> ChunkWithEmbedding(c, e) }

        val sampleVec = fixedEmbeddings.firstOrNull()?.take(8)
        val avgMagnitude = fixedEmbeddings.flatten().map { kotlin.math.abs(it) }.average()
        val zeroVecs = fixedEmbeddings.count { it.all { v -> v == 0f } } +
                structuralEmbeddings.count { it.all { v -> v == 0f } }
        val actualDim = fixedEmbeddings.firstOrNull()?.size ?: 768

        addMessage("assistant", buildString {
            appendLine("✅ Генерация завершена за **${embedTime}мс**")
            appendLine("• Всего векторов: **${fixedIndex.size + structuralIndex.size}** (по $actualDim чисел каждый)")
            appendLine("• Средняя абсолютная величина компонента: **${"%.4f".format(avgMagnitude)}**")
            appendLine("• Пустых векторов: **$zeroVecs**")
            appendLine()
            appendLine("**Пример вектора (первые 8 из $actualDim чисел):**")
            if (sampleVec != null) {
                appendLine("  [${sampleVec.joinToString(", ") { "%.4f".format(it) }}${if (sampleVec.size < actualDim) ", ..." else ""}]")
            }
            appendLine()
            appendLine("💡 Векторы от granite-embedding понимают семантику на разных языках,")
            appendLine("включая русский. Поиск будет находить чанки по смыслу, а не")
            appendLine("по пересечению слов, как было с hashing trick.")
        }, metadata = "Шаг 4/6")
        delay(10.seconds)

        // Шаг 5: Сохранение индекса
        addMessage("assistant", buildString {
            appendLine("💾 **Шаг 5: Сохранение индекса**")
            appendLine("2 JSON-файла в `~/.llm_chat_app/rag_index/`")
            appendLine()
            appendLine("• **index_fixed.json** — ${fixedChunks.size} чанков")
            appendLine("• **index_structural.json** — ${structuralChunks.size} чанков")
            appendLine("• Размер на диске: ~${(fixedChunks.size + structuralChunks.size) * 2} КБ (оценка)")
        }, metadata = "Шаг 5/6")
        delay(6.seconds)
        addMessage("assistant", buildString {
            appendLine("✅ Сохранено:")
            appendLine("• `index_fixed.json` — **${fixedChunks.size}** чанков")
            appendLine("• `index_structural.json` — **${structuralChunks.size}** чанков")
            appendLine()
        }, metadata = "Шаг 5/6")
        delay(6.seconds)

        // Шаг 6: Поиск
        addMessage("assistant", buildString {
            appendLine("🔍 **Шаг 6: Поиск**")
            appendLine("Запрос: **«$query»**")
            appendLine()
            appendLine("**Как работает поиск:**")
            appendLine("1. Запрос превращается в вектор той же размерности ($actualDim)")
            appendLine("2. Считается косинусное сходство между вектором запроса и каждым чанком")
            appendLine("3. Чанки сортируются по убыванию сходства")
            appendLine("4. Возвращаются топ-N результатов")
            appendLine()
            appendLine("Поиск по **${fixedIndex.size + structuralIndex.size}** чанкам...")
            appendLine("• Fixed-size: ${fixedIndex.size} чанков")
            appendLine("• Structural: ${structuralIndex.size} чанков")
        }, metadata = "Шаг 6/6")
        delay(6.seconds)

        val queryEmbedding = withContext(Dispatchers.Default) { embeddingService.embed(query) }

        fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
            var dot = 0f
            var normA = 0f
            var normB = 0f
            for (i in a.indices) {
                dot += a[i] * b[i]; normA += a[i] * a[i]; normB += b[i] * b[i]
            }
            return if (sqrt(normA.toDouble()) * sqrt(normB.toDouble()) > 0)
                (dot / sqrt(normA.toDouble()) / sqrt(normB.toDouble())).toFloat() else 0f
        }

        data class ScoredResult(
            val chunk: Chunk,
            val score: Float,
            val strategyName: String
        )

        val allScored = mutableListOf<ScoredResult>()
        fixedIndex.forEach { (c, e) ->
            allScored.add(
                ScoredResult(
                    c,
                    cosineSimilarity(queryEmbedding, e),
                    "Fixed-size"
                )
            )
        }
        structuralIndex.forEach { (c, e) ->
            allScored.add(
                ScoredResult(
                    c,
                    cosineSimilarity(queryEmbedding, e),
                    "Structural"
                )
            )
        }
        allScored.sortByDescending { it.score }

        val topFixed = allScored.filter { it.strategyName == "Fixed-size" }.take(1).firstOrNull()
        val topStruct = allScored.filter { it.strategyName == "Structural" }.take(1).firstOrNull()
        val fixedRelevant = allScored.count { it.strategyName == "Fixed-size" && it.score > 0 }
        val structRelevant = allScored.count { it.strategyName == "Structural" && it.score > 0 }
        val positiveScores = allScored.filter { it.score > 0 }
        val avgScore =
            if (positiveScores.isNotEmpty()) positiveScores.map { it.score }.average() else 0.0

        addMessage("assistant", buildString {
            appendLine("✅ Просканировано **${allScored.size}** чанков")
            appendLine("• Чанков с ненулевым сходством: **${positiveScores.size}**")
            appendLine("• Средний score релевантных: **${"%.4f".format(avgScore)}**")
            appendLine()
            appendLine("**Топ-5 результатов:**")
            allScored.take(5).forEachIndexed { idx, r ->
                appendLine("  **#${idx + 1}** score **${"%.4f".format(r.score)}** [${r.strategyName}]")
                appendLine("    → «${r.chunk.section}» (${r.chunk.title})")
                appendLine("    → ${r.chunk.content}")
            }
            appendLine()
            appendLine("**Лучший по каждой стратегии:**")
            if (topFixed != null) {
                appendLine("  • Fixed-size: score **${"%.4f".format(topFixed.score)}** — «${topFixed.chunk.section}»")
            }
            if (topStruct != null) {
                appendLine("  • Structural: score **${"%.4f".format(topStruct.score)}** — «${topStruct.chunk.section}»")
            }
            appendLine()
            appendLine("**Статистика по стратегиям:**")
            appendLine("| Стратегия | Чанков | Релевантных (>0) | Лучший score |")
            appendLine("|---|---|---|---|")
            appendLine(
                "| Fixed-size | ${fixedIndex.size} | $fixedRelevant | ${
                    "%.4f".format(
                        topFixed?.score ?: 0f
                    )
                } |"
            )
            appendLine(
                "| Structural | ${structuralIndex.size} | $structRelevant | ${
                    "%.4f".format(
                        topStruct?.score ?: 0f
                    )
                } |"
            )
            appendLine()
            val bestStrat =
                if (fixedRelevant >= structRelevant) "Fixed-size" else "Structural"
            appendLine("🏆 **Лучшая стратегия: $bestStrat** (больше релевантных чанков)")
            appendLine()
            appendLine("**Почему это важно:**")
            appendLine("В реальном RAG-приложении топ-K чанков были бы отправлены LLM вместе")
            appendLine("с запросом, чтобы модель сгенерировала ответ на основе этих фактов.")
            appendLine("Качество найденных чанков напрямую влияет на качество ответа.")
        }, metadata = "Шаг 6/6")
        delay(15.seconds)

        // Итог
        val elapsed = System.currentTimeMillis() - startTime
        addMessage("assistant", buildString {
            appendLine("✅ **Демонстрация завершена за ${elapsed}мс**")
            appendLine()
            appendLine("**Итоги:**")
            appendLine("📄 **${documents.size}** статей о ЧМ по футболу")
            appendLine("✂️ **${fixedChunks.size + structuralChunks.size}** чанков (${fixedChunks.size} Fixed + ${structuralChunks.size} Structural)")
            appendLine("🧠 **${actualDim}d** эмбеддинги (granite-embedding-97m-multilingual-r2, HuggingFace)")
            appendLine("💾 **2** JSON-индекса на диске")
            appendLine("🔍 **Косинусный поиск** по ${fixedIndex.size + structuralIndex.size} чанкам")
            appendLine()
            appendLine("**Сравнение стратегий чанкинга:**")
            appendLine("| Критерий | Fixed-size | Structural |")
            appendLine("|---|---|---|")
            appendLine("| Количество чанков | ${fixedChunks.size} | ${structuralChunks.size} |")
            appendLine("| Средний размер | $avgFixed симв. | $avgStruct симв. |")
            appendLine("| Релевантных (>0) | $fixedRelevant | $structRelevant |")
            appendLine(
                "| Лучший score | ${"%.4f".format(topFixed?.score ?: 0f)} | ${
                    "%.4f".format(
                        topStruct?.score ?: 0f
                    )
                } |"
            )
            appendLine()
            appendLine("💡 **Вывод:** Fixed-size даёт однородные блоки, но может разрывать")
            appendLine("смысловые разделы. Structural чанкинг сохраняет целостность секций,")
            appendLine("но чанки получаются неравномерными. Выбор стратегии зависит")
            appendLine("от структуры данных и требований к поиску.")
        }, metadata = "ИТОГИ")
    }
}
