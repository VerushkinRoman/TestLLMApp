package com.llmapp.rag.ui

import com.llmapp.rag.RagMode
import com.llmapp.rag.domain.ChunkingStrategy
import com.llmapp.rag.domain.RerankerConfig
import com.llmapp.rag.domain.RerankerType
import com.llmapp.rag.domain.SearchResult

sealed interface IndexEvent {
    data object ClearAction : IndexEvent
    data object BuildIndex : IndexEvent
    data class SelectStrategy(val strategy: ChunkingStrategy) : IndexEvent
    data class Search(val query: String) : IndexEvent
    data object ClearSearch : IndexEvent
    data object ShowFixedIndex : IndexEvent
    data object ShowStructuralIndex : IndexEvent
    data class SetRagMode(val mode: RagMode) : IndexEvent
    data class SetRerankerType(val type: RerankerType) : IndexEvent
    data class SetThreshold(val threshold: Float) : IndexEvent
    data class SetTopKBefore(val topK: Int) : IndexEvent
    data class SetTopKAfter(val topK: Int) : IndexEvent
    data object CompareModes : IndexEvent
    data object ClearComparison : IndexEvent
}

data class IndexState(
    val isBuilding: Boolean = false,
    val isIndexed: Boolean = false,
    val currentStrategy: ChunkingStrategy = ChunkingStrategy.FixedSize(),
    val fixedSizeStats: IndexStats? = null,
    val structuralStats: IndexStats? = null,
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
    val log: List<String> = emptyList(),
    val ragMode: RagMode = RagMode.BASIC,
    val rerankerConfig: RerankerConfig = RerankerConfig(),
    val comparisonResult: RagComparisonResult? = null,
    val isComparing: Boolean = false,
)

data class IndexStats(
    val totalDocuments: Int,
    val totalChunks: Int,
    val strategyName: String,
    val avgChunkChars: Int,
    val minChunkChars: Int,
    val maxChunkChars: Int,
    val chunkDurations: List<Int> = emptyList(),
    val totalChars: Int,
)

sealed interface IndexAction

data class RagComparisonResult(
    val basicResults: List<SearchResult>,
    val basicTimeMs: Long,
    val basicChunksCount: Int,
    val filteredResults: List<SearchResult>,
    val filteredTimeMs: Long,
    val filteredChunksCount: Int,
    val filteredRemoved: Int,
    val filteredThreshold: Float,
    val rewriteFilterResults: List<SearchResult>,
    val rewriteFilterTimeMs: Long,
    val rewriteFilterChunksCount: Int,
    val rewriteFilterRemoved: Int,
    val rewriteFilterThreshold: Float,
    val originalQuery: String,
    val rewrittenQuery: String,
    val totalComparisonTimeMs: Long,
)
