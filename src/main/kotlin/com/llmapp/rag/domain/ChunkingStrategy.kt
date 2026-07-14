package com.llmapp.rag.domain

sealed interface ChunkingStrategy {
    data class FixedSize(
        val chunkSize: Int = 400,
        val overlap: Int = 60,
    ) : ChunkingStrategy

    data class Structural(
        val mergeSmallSections: Boolean = true,
        val minSectionChars: Int = 100,
        val maxSectionChars: Int = 800,
    ) : ChunkingStrategy
}
