package com.llmapp.rag.domain

sealed interface ChunkingStrategy {
    data class FixedSize(
        val chunkSize: Int = 600,
        val overlap: Int = 100,
    ) : ChunkingStrategy

    data class Structural(
        val mergeSmallSections: Boolean = true,
        val minSectionChars: Int = 100,
    ) : ChunkingStrategy
}
