package com.llmapp.rag.ui

import com.llmapp.rag.domain.ChunkingStrategy
import com.llmapp.rag.domain.SearchResult

sealed interface IndexEvent {
    data object ClearAction : IndexEvent
    data object BuildIndex : IndexEvent
    data class SelectStrategy(val strategy: ChunkingStrategy) : IndexEvent
    data class Search(val query: String) : IndexEvent
    data object ClearSearch : IndexEvent
    data object ShowFixedIndex : IndexEvent
    data object ShowStructuralIndex : IndexEvent
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
