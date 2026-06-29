package com.llmapp.rag.domain

data class Chunk(
    val chunkId: String,
    val documentId: String,
    val title: String,
    val section: String,
    val content: String,
    val source: String,
    val charOffset: Int,
    val charLength: Int,
    val embedding: List<Float>? = null,
)
