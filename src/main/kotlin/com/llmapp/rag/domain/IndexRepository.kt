package com.llmapp.rag.domain

data class IndexResult(
    val chunks: List<Chunk>,
    val strategy: ChunkingStrategy,
    val totalDocuments: Int,
    val totalChunks: Int,
    val embeddingDimension: Int,
    val indexedAt: Long,
)

data class SearchResult(
    val chunk: Chunk,
    val score: Float,
    val rank: Int,
)

data class SourceInfo(
    val source: String,
    val title: String,
    val section: String,
    val chunkId: String,
    val score: Float,
)

data class Quote(
    val text: String,
    val source: SourceInfo,
)

data class RagAnswer(
    val answer: String,
    val sources: List<SourceInfo>,
    val quotes: List<Quote>,
    val chunks: List<SearchResult> = emptyList(),
    val isUnknown: Boolean = false,
    val unknownReason: String? = null,
    val topScore: Float = 0f,
    val totalChunks: Int = 0,
) {
    val shouldSayIdontKnow: Boolean
        get() = isUnknown

    @Suppress("unused")
    val iDontKnowMessage: String?
        get() = if (isUnknown) answer else null
}

interface IndexRepository {
    suspend fun saveIndex(index: IndexResult)
    suspend fun loadIndex(): IndexResult?
    suspend fun loadIndexForStrategy(strategyName: String): IndexResult?
    suspend fun search(query: List<Float>, topK: Int = 3): List<SearchResult>
    fun cosineSimilarity(a: List<Float>, b: List<Float>): Float
    fun isIndexAvailable(): Boolean
}
