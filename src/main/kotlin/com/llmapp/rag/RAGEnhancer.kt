package com.llmapp.rag

import com.llmapp.rag.data.HuggingFaceEmbeddingService
import com.llmapp.rag.data.JsonIndexRepository
import com.llmapp.rag.data.WorldCupDocuments
import com.llmapp.rag.domain.ChunkerFactory
import com.llmapp.rag.domain.ChunkingStrategy
import com.llmapp.rag.domain.IndexRepository
import com.llmapp.rag.domain.IndexResult
import com.llmapp.rag.domain.QueryRewriter
import com.llmapp.rag.domain.Reranker
import com.llmapp.rag.domain.RerankerConfig
import com.llmapp.rag.domain.RerankerResult
import com.llmapp.rag.domain.RerankerType
import com.llmapp.rag.domain.SearchResult
import com.llmapp.rag.domain.SimpleQueryRewriter
import com.llmapp.rag.domain.SwitchingReranker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class RagMode(val label: String) {
    BASIC("Базовый (без фильтра)"),
    FILTERED("Фильтр релевантности"),
    REWRITE_FILTER("Rewrite + Фильтр"),
}

data class RagContext(
    val chunks: List<SearchResult>,
    val combinedContext: String,
    val searchTimeMs: Long,
    val mode: RagMode = RagMode.BASIC,
    val rerankerResult: RerankerResult? = null,
    val originalQuery: String = "",
    val rewrittenQuery: String? = null,
)

data class MultiModeResult(
    val basic: RagContext,
    val filtered: RagContext?,
    val rewriteFilter: RagContext?,
    val comparisonTimeMs: Long,
)

class RAGEnhancer(
    private val embeddingService: HuggingFaceEmbeddingService = HuggingFaceEmbeddingService(),
    private val repository: IndexRepository = JsonIndexRepository(),
    var topK: Int = 5,
    private val reranker: Reranker = SwitchingReranker(),
    private val queryRewriter: QueryRewriter = SimpleQueryRewriter(),
    var mode: RagMode = RagMode.BASIC,
    var rerankerConfig: RerankerConfig = RerankerConfig(
        type = RerankerType.SIMILARITY_THRESHOLD,
        similarityThreshold = 0.3f,
        topKBefore = 20,
        topKAfter = topK,
    ),
) {
    private var isIndexLoaded = false
    private var isIndexing = false
    private val indexMutex = Mutex()
    private var indexLoadAttempted = false
    var onIndexLog: ((String) -> Unit)? = null

    suspend fun ensureIndexLoaded() {
        if (isIndexLoaded) return

        // Quick check under mutex: already loaded, already indexing, or already attempted?
        indexMutex.withLock {
            if (isIndexLoaded) return
            if (isIndexing) return
            if (indexLoadAttempted) return
            indexLoadAttempted = true
        }

        // Outside mutex: try to load existing index
        if (repository.isIndexAvailable()) {
            repository.loadIndex()
            indexMutex.withLock { isIndexLoaded = true }
            log("RAG-индекс загружен с диска")
            return
        }

        // Need to build from scratch
        indexMutex.withLock { isIndexing = true }
        log("RAG-индекс не найден, запускаю автоматическую индексацию...")
        try {
            autoBuildIndex()
            indexMutex.withLock { isIndexLoaded = true }
        } finally {
            indexMutex.withLock { isIndexing = false }
        }
    }

    suspend fun autoBuildIndex() {
        log("📚 Автоиндексация: загружаю документы...")
        try {
            val documents = WorldCupDocuments.getAll()
            log("📚 Документов: ${documents.size}, всего символов: ${documents.sumOf { it.content.length }}")

            val fixedStrategy = ChunkingStrategy.FixedSize()
            val structuralStrategy = ChunkingStrategy.Structural()

            log("📚 Fixed-size чанкинг (${fixedStrategy.chunkSize}, overlap ${fixedStrategy.overlap})...")
            val fixedResult = buildSingleIndex(fixedStrategy, "fixed", documents)
            if (fixedResult != null) log("📚 Fixed-size: ${fixedResult.chunks.size} чанков")

            log("📚 Structural чанкинг (по секциям)...")
            val structuralResult = buildSingleIndex(structuralStrategy, "structural", documents)
            if (structuralResult != null) log("📚 Structural: ${structuralResult.chunks.size} чанков")

            withContext(Dispatchers.Default) {
                repository.loadIndex()
                isIndexLoaded = true
            }
            log("✅ Автоиндексация завершена")
        } catch (e: Exception) {
            log("❌ Ошибка автоиндексации: ${e.message}")
            throw e
        }
    }

    private suspend fun buildSingleIndex(
        strategy: ChunkingStrategy,
        strategyName: String,
        documents: List<com.llmapp.rag.domain.Document>,
    ): IndexResult? = withContext(Dispatchers.Default) {
        try {
            val chunker = ChunkerFactory.create(strategy)
            val allChunks = documents.flatMap { doc -> chunker.chunk(doc, strategy) }

            val texts = allChunks.map { it.content }
            val embeddings = embeddingService.embedBatch(texts)

            val chunksWithEmbeddings = allChunks.zip(embeddings) { chunk, emb ->
                chunk.copy(embedding = emb)
            }

            val result = IndexResult(
                chunks = chunksWithEmbeddings,
                strategy = strategy,
                totalDocuments = documents.size,
                totalChunks = chunksWithEmbeddings.size,
                embeddingDimension = embeddingService.dimension,
                indexedAt = System.currentTimeMillis(),
            )

            repository.saveIndex(result)
            log("📚 Сохранён index_$strategyName.json (${result.chunks.size} чанков)")
            result
        } catch (e: Exception) {
            log("❌ Ошибка $strategyName: ${e.message}")
            null
        }
    }

    suspend fun search(query: String): RagContext {
        ensureIndexLoaded()
        return searchInternal(query, mode)
    }

    private suspend fun searchInternal(query: String, searchMode: RagMode): RagContext {
        val start = System.currentTimeMillis()
        var actualQuery = query
        var rewrittenQuery: String? = null

        if (searchMode == RagMode.REWRITE_FILTER) {
            actualQuery = withContext(Dispatchers.Default) {
                queryRewriter.rewrite(query)
            }
            rewrittenQuery = actualQuery
        }

        val queryEmbedding = withContext(Dispatchers.Default) {
            embeddingService.embed(actualQuery)
        }

        val fetchTopK = if (searchMode != RagMode.BASIC) rerankerConfig.topKBefore else topK
        val results = withContext(Dispatchers.Default) {
            repository.search(queryEmbedding, fetchTopK)
        }

        val rerankerResult: RerankerResult?
        val finalResults: List<SearchResult>

        if (searchMode != RagMode.BASIC) {
            val rrResult = withContext(Dispatchers.Default) {
                reranker.rerank(query, results, rerankerConfig)
            }
            rerankerResult = rrResult
            finalResults = rrResult.filteredResults
        } else {
            rerankerResult = null
            finalResults = results.take(topK).mapIndexed { i, r -> r.copy(rank = i + 1) }
        }

        val searchTime = System.currentTimeMillis() - start

        val context = buildString {
            appendLine("Контекст из базы знаний чемпионатов мира:")
            appendLine()
            if (rewrittenQuery != null && rewrittenQuery != query) {
                appendLine("(Запрос был расширен: «$query» → «$rewrittenQuery»)")
                appendLine()
            }
            if (rerankerResult != null) {
                val removed = rerankerResult.removedCount
                if (removed > 0) {
                    appendLine("(Отфильтровано $removed нерелевантных фрагментов, порог ${rerankerResult.config.similarityThreshold})")
                    appendLine()
                }
            }
            for ((i, r) in finalResults.withIndex()) {
                appendLine("--- Фрагмент ${i + 1} (релевантность: ${"%.3f".format(r.score)}) ---")
                appendLine("Источник: ${r.chunk.title} — ${r.chunk.section}")
                appendLine(r.chunk.content.take(2000))
                appendLine()
            }
        }

        return RagContext(
            chunks = finalResults,
            combinedContext = context,
            searchTimeMs = searchTime,
            mode = searchMode,
            rerankerResult = rerankerResult,
            originalQuery = query,
            rewrittenQuery = rewrittenQuery,
        )
    }

    suspend fun compareModes(query: String): MultiModeResult {
        ensureIndexLoaded()
        val overallStart = System.currentTimeMillis()
        val basic = searchInternal(query, RagMode.BASIC)
        val filtered = searchInternal(query, RagMode.FILTERED)
        val rewriteFilter = searchInternal(query, RagMode.REWRITE_FILTER)
        val elapsed = System.currentTimeMillis() - overallStart
        return MultiModeResult(
            basic = basic,
            filtered = filtered,
            rewriteFilter = rewriteFilter,
            comparisonTimeMs = elapsed,
        )
    }

    suspend fun searchWithContext(query: String): String {
        ensureIndexLoaded()
        val rag = searchInternal(query, mode)
        if (rag.chunks.isEmpty()) return query

        return buildString {
            appendLine("Ответь на вопрос пользователя, используя информацию из предоставленного контекста.")
            appendLine("Если в контексте нет ответа — ответь на основе своих знаний, но укажи это.")
            appendLine()
            appendLine(rag.combinedContext)
            appendLine("=== КОНЕЦ КОНТЕКСТА ===")
            appendLine()
            appendLine("Вопрос: ${rag.rewrittenQuery ?: query}")
            appendLine()
            appendLine("Ответь на русском языке, используя факты из контекста. Укажи источник информации.")
        }
    }

    private fun log(msg: String) {
        println(msg)
        onIndexLog?.invoke(msg)
    }
}
