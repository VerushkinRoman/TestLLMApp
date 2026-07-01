package com.llmapp.rag.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llmapp.rag.RAGEnhancer
import com.llmapp.rag.data.HuggingFaceEmbeddingService
import com.llmapp.rag.data.JsonIndexRepository
import com.llmapp.rag.data.WorldCupDocuments
import com.llmapp.rag.domain.ChunkerFactory
import com.llmapp.rag.domain.ChunkingStrategy
import com.llmapp.rag.domain.IndexResult
import com.llmapp.rag.RagMode
import com.llmapp.rag.domain.RerankerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IndexViewModel(
    private val embeddingService: HuggingFaceEmbeddingService = HuggingFaceEmbeddingService(),
    private val repository: JsonIndexRepository = JsonIndexRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(IndexState())
    val state: StateFlow<IndexState> = _state.asStateFlow()

    private val _actions = MutableSharedFlow<IndexAction?>(
        replay = 1,
        extraBufferCapacity = 1,
    )
    val actions: SharedFlow<IndexAction?> = _actions.asSharedFlow()

    private val documents = WorldCupDocuments.getAll()
    private val enhancer = RAGEnhancer(repository = repository)

    fun obtainEvent(event: IndexEvent) {
        when (event) {
            is IndexEvent.ClearAction -> _actions.tryEmit(null)
            is IndexEvent.BuildIndex -> buildIndex()
            is IndexEvent.SelectStrategy -> selectStrategy(event.strategy)
            is IndexEvent.Search -> search(event.query)
            is IndexEvent.ClearSearch -> clearSearch()
            is IndexEvent.ShowFixedIndex -> showIndex("fixed")
            is IndexEvent.ShowStructuralIndex -> showIndex("structural")
            is IndexEvent.SetRagMode -> setRagMode(event.mode)
            is IndexEvent.SetRerankerType -> setRerankerType(event.type)
            is IndexEvent.SetThreshold -> setThreshold(event.threshold)
            is IndexEvent.SetTopKBefore -> setTopKBefore(event.topK)
            is IndexEvent.SetTopKAfter -> setTopKAfter(event.topK)
            is IndexEvent.CompareModes -> compareModes()
            is IndexEvent.ClearComparison -> clearComparison()
        }
    }

    private fun selectStrategy(strategy: ChunkingStrategy) {
        _state.value = _state.value.copy(currentStrategy = strategy, searchResults = emptyList())
    }

    private fun setRagMode(mode: RagMode) {
        _state.value = _state.value.copy(ragMode = mode)
        enhancer.mode = mode
    }

    private fun setRerankerType(type: RerankerType) {
        val newConfig = _state.value.rerankerConfig.copy(type = type)
        _state.value = _state.value.copy(rerankerConfig = newConfig)
        enhancer.rerankerConfig = newConfig
    }

    private fun setThreshold(threshold: Float) {
        val newConfig = _state.value.rerankerConfig.copy(similarityThreshold = threshold)
        _state.value = _state.value.copy(rerankerConfig = newConfig)
        enhancer.rerankerConfig = newConfig
    }

    private fun setTopKBefore(topK: Int) {
        val newConfig = _state.value.rerankerConfig.copy(topKBefore = topK)
        _state.value = _state.value.copy(rerankerConfig = newConfig)
        enhancer.rerankerConfig = newConfig
    }

    private fun setTopKAfter(topK: Int) {
        val newConfig = _state.value.rerankerConfig.copy(topKAfter = topK)
        _state.value = _state.value.copy(rerankerConfig = newConfig)
        enhancer.rerankerConfig = newConfig
    }

    private fun buildIndex() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isBuilding = true,
                error = null,
                log = emptyList(),
            )
            println("═══════════════════════════════════════")
            println("📚 INDEX: Начало построения индекса")
            println("📚 INDEX: Документов: ${documents.size}")
            println("📚 INDEX: Всего символов: ${documents.sumOf { it.content.length }}")
            appendLog("Построение индекса...")
            appendLog("Документов: ${documents.size}")
            appendLog("Всего символов: ${documents.sumOf { it.content.length }}")

            val fixedStrategy = ChunkingStrategy.FixedSize()
            val structuralStrategy = ChunkingStrategy.Structural()

            println("📚 INDEX: Строю fixed-size индекс (chunk=${fixedStrategy.chunkSize}, overlap=${fixedStrategy.overlap})...")
            appendLog("Fixed-size: chunk=${fixedStrategy.chunkSize}, overlap=${fixedStrategy.overlap}")
            val fixedResult = buildSingleIndex(fixedStrategy, "fixed")
            _state.value = _state.value.copy(fixedSizeStats = fixedResult?.let { computeStats(it) })
            if (fixedResult != null) {
                println("📚 INDEX: Fixed-size индекс готов: ${fixedResult.chunks.size} чанков")
                appendLog("Fixed-size: ${fixedResult.chunks.size} чанков")
            }

            println("📚 INDEX: Строю structural индекс (по секциям)...")
            appendLog("Structural: по секциям документов")
            val structuralResult = buildSingleIndex(structuralStrategy, "structural")
            _state.value =
                _state.value.copy(structuralStats = structuralResult?.let { computeStats(it) })
            if (structuralResult != null) {
                println("📚 INDEX: Structural индекс готов: ${structuralResult.chunks.size} чанков")
                appendLog("Structural: ${structuralResult.chunks.size} чанков")
            }

            _state.value = _state.value.copy(
                isBuilding = false,
                isIndexed = fixedResult != null || structuralResult != null,
            )
            println("📚 INDEX: Построение индекса завершено!")
            appendLog("Готово!")
        }
    }

    private suspend fun buildSingleIndex(
        strategy: ChunkingStrategy,
        strategyName: String,
    ): IndexResult? = withContext(Dispatchers.Default) {
        try {
            val chunker = ChunkerFactory.create(strategy)
            println("  📚 INDEX: Стратегия '$strategyName' — разбиваю ${documents.size} документов на чанки...")

            val allChunks = documents.flatMap { doc ->
                chunker.chunk(doc, strategy)
            }
            println("  📚 INDEX: Получено ${allChunks.size} чанков")

            println("  📚 INDEX: Генерирую эмбеддинги для ${allChunks.size} чанков через HuggingFace (dim=${embeddingService.dimension})...")
            appendLog("Эмбеддинги: ${allChunks.size} чанков, размерность ${embeddingService.dimension}")
            val texts = allChunks.map { it.content }
            val embeddings = embeddingService.embedBatch(texts)
            println("  ✅ 📚 INDEX: Эмбеддинги получены: ${embeddings.size} векторов, dim=${embeddings.firstOrNull()?.size}")

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

            println("  📚 INDEX: Сохраняю index_${strategyName}.json...")
            repository.saveIndex(result)
            println("  ✅ 📚 INDEX: index_${strategyName}.json сохранён")
            appendLog("Сохранён index_${strategyName}.json")
            result
        } catch (e: Exception) {
            println("  ❌ 📚 INDEX: Ошибка при построении $strategyName: ${e.message}")
            appendLog("Ошибка $strategyName: ${e.message}")
            _state.value =
                _state.value.copy(error = "Ошибка построения $strategyName: ${e.message}")
            null
        }
    }

    private fun showIndex(strategyName: String) {
        viewModelScope.launch {
            val result = repository.loadIndexForStrategy(strategyName)
            if (result != null) {
                val stats = computeStats(result)
                when (strategyName) {
                    "fixed" -> _state.value = _state.value.copy(fixedSizeStats = stats)
                    "structural" -> _state.value = _state.value.copy(structuralStats = stats)
                }
                appendLog("Loaded $strategyName index: ${result.chunks.size} chunks")
            } else {
                appendLog("No $strategyName index found. Build the index first.")
            }
        }
    }

    private fun search(query: String) {
        if (query.isBlank()) return
        _state.value = _state.value.copy(searchQuery = query, isSearching = true)

        viewModelScope.launch {
            try {
                appendLog("Поиск [$ragModeLabel]: \"$query\"")
                val ragContext = enhancer.search(query)
                _state.value = _state.value.copy(
                    searchResults = ragContext.chunks,
                    isSearching = false,
                )
                val removed = ragContext.rerankerResult?.removedCount ?: 0
                appendLog("Найдено ${ragContext.chunks.size} результатов${if (removed > 0) " (отсеяно $removed)" else ""}")
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSearching = false,
                    error = "Search failed: ${e.message}",
                )
            }
        }
    }

    private fun compareModes() {
        val query = _state.value.searchQuery
        if (query.isBlank()) return
        _state.value = _state.value.copy(isComparing = true)

        viewModelScope.launch {
            try {
                appendLog("Сравнение режимов для: \"$query\"")
                val multi = enhancer.compareModes(query)

                val result = RagComparisonResult(
                    basicResults = multi.basic.chunks,
                    basicTimeMs = multi.basic.searchTimeMs,
                    basicChunksCount = multi.basic.chunks.size,
                    filteredResults = multi.filtered?.chunks ?: emptyList(),
                    filteredTimeMs = multi.filtered?.searchTimeMs ?: 0,
                    filteredChunksCount = multi.filtered?.chunks?.size ?: 0,
                    filteredRemoved = multi.filtered?.rerankerResult?.removedCount ?: 0,
                    filteredThreshold = multi.filtered?.rerankerResult?.config?.similarityThreshold ?: 0f,
                    rewriteFilterResults = multi.rewriteFilter?.chunks ?: emptyList(),
                    rewriteFilterTimeMs = multi.rewriteFilter?.searchTimeMs ?: 0,
                    rewriteFilterChunksCount = multi.rewriteFilter?.chunks?.size ?: 0,
                    rewriteFilterRemoved = multi.rewriteFilter?.rerankerResult?.removedCount ?: 0,
                    rewriteFilterThreshold = multi.rewriteFilter?.rerankerResult?.config?.similarityThreshold ?: 0f,
                    originalQuery = query,
                    rewrittenQuery = multi.rewriteFilter?.rewrittenQuery ?: query,
                    totalComparisonTimeMs = multi.comparisonTimeMs,
                )

                _state.value = _state.value.copy(
                    comparisonResult = result,
                    isComparing = false,
                )
                appendLog("Сравнение завершено за ${multi.comparisonTimeMs}мс")
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isComparing = false,
                    error = "Comparison failed: ${e.message}",
                )
            }
        }
    }

    private fun clearComparison() {
        _state.value = _state.value.copy(comparisonResult = null)
    }

    private fun clearSearch() {
        _state.value = _state.value.copy(searchQuery = "", searchResults = emptyList())
    }

    private fun computeStats(result: IndexResult): IndexStats {
        val chunks = result.chunks
        val charLengths = chunks.map { it.content.length }
        return IndexStats(
            totalDocuments = result.totalDocuments,
            totalChunks = chunks.size,
            strategyName = when (result.strategy) {
                is ChunkingStrategy.FixedSize -> "Fixed Size"
                is ChunkingStrategy.Structural -> "Structural"
            },
            avgChunkChars = if (charLengths.isNotEmpty()) charLengths.average().toInt() else 0,
            minChunkChars = charLengths.minOrNull() ?: 0,
            maxChunkChars = charLengths.maxOrNull() ?: 0,
            totalChars = charLengths.sum(),
        )
    }

    private fun appendLog(msg: String) {
        val current = _state.value.log
        _state.value = _state.value.copy(log = current + msg)
    }

    private val ragModeLabel: String
        get() = when (_state.value.ragMode) {
            RagMode.BASIC -> "Базовый"
            RagMode.FILTERED -> "Фильтр"
            RagMode.REWRITE_FILTER -> "Rewrite+Фильтр"
        }
}
