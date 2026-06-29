package com.llmapp.demo.manager

import com.llmapp.rag.data.HashingEmbeddingService
import com.llmapp.rag.data.WorldCupDocuments
import com.llmapp.rag.domain.ChunkingStrategy
import com.llmapp.rag.domain.ChunkerFactory
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
) : BaseDemoRunner(onMessageAdded, onTypingStateChanged) {

    private val embeddingService = HashingEmbeddingService()
    private val documents = WorldCupDocuments.getAll()

    override suspend fun run() {
        val startTime = System.currentTimeMillis()
        val totalChars = documents.sumOf { it.content.length }

        addMessage("assistant", buildString {
            appendLine("🔬 **Демонстрация RAG Pipeline**")
            appendLine()
            appendLine("Пайплайн: загрузка → чанкинг (2 стратегии) → эмбеддинги → индекс → поиск")
        }, metadata = "ДЕМОНСТРАЦИЯ RAG")
        delay(7.seconds)

        // Шаг 1
        addMessage("assistant", buildString {
            appendLine("📄 **Шаг 1: Загрузка документов**")
            appendLine("Загружено **${documents.size}** статей о ЧМ по футболу (~${totalChars} символов)")
        }, metadata = "Шаг 1/6")
        delay(5.seconds)

        // Шаг 2: Fixed-size
        addMessage("assistant", buildString {
            appendLine("✂️ **Шаг 2: Fixed-size чанкинг**")
            appendLine("Размер: 600 символов, overlap: 100")
        }, metadata = "Шаг 2/6")
        delay(3.seconds)

        val fixedStrategy = ChunkingStrategy.FixedSize()
        val fixedChunks = documents.flatMap { doc ->
            ChunkerFactory.create(fixedStrategy).chunk(doc, fixedStrategy)
        }

        val avgFixed = if (fixedChunks.isNotEmpty()) fixedChunks.map { it.content.length }.average().toInt() else 0
        addMessage("assistant", buildString {
            appendLine("✅ Fixed-size: **$fixedChunks.size** чанков, средний **$avgFixed** символов")
        }, metadata = "Fixed-size")
        delay(5.seconds)

        // Шаг 3: Structural
        addMessage("assistant", buildString {
            appendLine("📑 **Шаг 3: Structural чанкинг**")
            appendLine("Каждый раздел документа — отдельный чанк, мелкие секции объединяются")
        }, metadata = "Шаг 3/6")
        delay(3.seconds)

        val structuralStrategy = ChunkingStrategy.Structural()
        val structuralChunks = documents.flatMap { doc ->
            ChunkerFactory.create(structuralStrategy).chunk(doc, structuralStrategy)
        }

        val avgStruct = if (structuralChunks.isNotEmpty()) structuralChunks.map { it.content.length }.average().toInt() else 0
        addMessage("assistant", buildString {
            appendLine("✅ Structural: **$structuralChunks.size** чанков, средний **$avgStruct** символов")
            appendLine()
            appendLine("**Сравнение:**")
            appendLine("| Параметр | Fixed-size | Structural |")
            appendLine("|---|---|---|")
            appendLine("| Чанков | $fixedChunks.size | $structuralChunks.size |")
            appendLine("| Средний | $avgFixed символов | $avgStruct символов |")
            val diff = fixedChunks.size - structuralChunks.size
            val sizeDiff = avgFixed - avgStruct
            appendLine(if (diff > 0) "Fixed даёт на $diff чанков больше" else "Structural даёт на ${-diff} чанков больше")
            appendLine(if (sizeDiff > 0) "Fixed чанки крупнее на $sizeDiff символов" else "Structural чанки крупнее на ${-sizeDiff} символов")
        }, metadata = "Сравнение")
        delay(5.seconds)

        // Шаг 4: Эмбеддинги
        addMessage("assistant", buildString {
            appendLine("🧠 **Шаг 4: Эмбеддинги**")
            appendLine("Метод: word-level hashing trick, 512d, L2-нормализация")
            appendLine("Генерирую векторы для ${fixedChunks.size + structuralChunks.size} чанков...")
        }, metadata = "Шаг 4/6")
        delay(5.seconds)

        val fixedEmbeddings: List<List<Float>>
        val structuralEmbeddings: List<List<Float>>
        val embedTime = measureTimeMillis {
            withContext(Dispatchers.Default) {
                fixedEmbeddings = embeddingService.embedBatch(fixedChunks.map { it.content })
                structuralEmbeddings = embeddingService.embedBatch(structuralChunks.map { it.content })
            }
        }

        data class ChunkWithEmbedding(val chunk: com.llmapp.rag.domain.Chunk, val embedding: List<Float>)
        val fixedIndex = fixedChunks.zip(fixedEmbeddings) { c, e -> ChunkWithEmbedding(c, e) }
        val structuralIndex = structuralChunks.zip(structuralEmbeddings) { c, e -> ChunkWithEmbedding(c, e) }

        addMessage("assistant", buildString {
            appendLine("✅ ${embedTime}мс: ${fixedIndex.size + structuralIndex.size} векторов по 512 чисел")
        }, metadata = "Шаг 4/6")
        delay(5.seconds)

        // Шаг 5: Сохранение индекса (без вывода содержимого)
        addMessage("assistant", buildString {
            appendLine("💾 **Шаг 5: Сохранение индекса**")
            appendLine("2 JSON-файла в `~/.llm_chat_app/rag_index/`")
        }, metadata = "Шаг 5/6")
        delay(3.seconds)
        addMessage("assistant", buildString {
            appendLine("✅ Сохранено: `index_fixed.json` ($fixedChunks.size чанков), `index_structural.json` ($structuralChunks.size чанков)")
        }, metadata = "Шаг 5/6")
        delay(5.seconds)

        // Шаг 6: Поиск
        val query = "выиграл чемпионат мира 2022 Аргентина Месси финал"
        addMessage("assistant", buildString {
            appendLine("🔍 **Шаг 6: Поиск**")
            appendLine("Запрос: **«$query»**")
            appendLine("Поиск по ${fixedIndex.size + structuralIndex.size} чанкам...")
        }, metadata = "Шаг 6/6")
        delay(3.seconds)

        val queryEmbedding = withContext(Dispatchers.Default) { embeddingService.embed(query) }

        fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
            var dot = 0f; var normA = 0f; var normB = 0f
            for (i in a.indices) { dot += a[i] * b[i]; normA += a[i] * a[i]; normB += b[i] * b[i] }
            return if (sqrt(normA.toDouble()) * sqrt(normB.toDouble()) > 0)
                (dot / sqrt(normA.toDouble()) / sqrt(normB.toDouble())).toFloat() else 0f
        }

        data class ScoredResult(val chunk: com.llmapp.rag.domain.Chunk, val score: Float, val strategyName: String)
        val allScored = mutableListOf<ScoredResult>()
        fixedIndex.forEach { (c, e) -> allScored.add(ScoredResult(c, cosineSimilarity(queryEmbedding, e), "Fixed-size")) }
        structuralIndex.forEach { (c, e) -> allScored.add(ScoredResult(c, cosineSimilarity(queryEmbedding, e), "Structural")) }
        allScored.sortByDescending { it.score }

        addMessage("assistant", buildString {
            appendLine("✅ Просканировано ${allScored.size} чанков")
            appendLine()
            appendLine("**Топ-3 результата:**")
            allScored.take(3).forEachIndexed { idx, r ->
                appendLine("  **#${idx + 1}** score **${"%.4f".format(r.score)}** [${r.strategyName}]")
                appendLine("    → «${r.chunk.section}» (${r.chunk.title})")
                val preview = r.chunk.content.take(200).replace("\n", " ").trim()
                appendLine("    → ${preview}...")
            }
            appendLine()
            val bestStrat = if (allScored.count { it.strategyName == "Fixed-size" && it.score > 0 } >=
                allScored.count { it.strategyName == "Structural" && it.score > 0 }) "Fixed-size" else "Structural"
            appendLine("🏆 Лучшая стратегия: $bestStrat (больше релевантных чанков)")
        }, metadata = "Шаг 6/6")
        delay(5.seconds)

        // Итог
        val elapsed = System.currentTimeMillis() - startTime
        addMessage("assistant", buildString {
            appendLine("✅ **Демонстрация завершена за ${elapsed}мс**")
            appendLine("**Итоги:**")
            appendLine("📄 $documents.size статей, ${fixedChunks.size + structuralChunks.size} чанков, 512d эмбеддинги, 2 JSON-индекса")
            appendLine("🔍 Поиск: косинусное сходство, ранжирование, кросс-стратегический")
            appendLine("💡 Fixed-size — однородные блоки, Structural — семантическая целостность разделов")
        }, metadata = "ИТОГИ")
    }
}
