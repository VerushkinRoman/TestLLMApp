package com.llmapp.rag.domain

interface EmbeddingService {
    suspend fun embed(text: String): List<Float>
    suspend fun embedBatch(texts: List<String>): List<List<Float>>
    val dimension: Int
}
