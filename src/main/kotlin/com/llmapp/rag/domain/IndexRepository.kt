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

interface IndexRepository {
    suspend fun saveIndex(index: IndexResult)
    suspend fun loadIndex(): IndexResult?
    suspend fun search(query: List<Float>, topK: Int = 5): List<SearchResult>
    fun cosineSimilarity(a: List<Float>, b: List<Float>): Float
}
