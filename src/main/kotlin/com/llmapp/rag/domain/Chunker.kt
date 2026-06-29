package com.llmapp.rag.domain

interface Chunker {
    fun chunk(document: Document, strategy: ChunkingStrategy): List<Chunk>
}

class FixedSizeChunker : Chunker {
    override fun chunk(document: Document, strategy: ChunkingStrategy): List<Chunk> {
        val config = strategy as? ChunkingStrategy.FixedSize
            ?: ChunkingStrategy.FixedSize()

        val chunks = mutableListOf<Chunk>()
        val text = document.content
        var start = 0

        var chunkIndex = 0
        while (start < text.length) {
            val end = minOf(start + config.chunkSize, text.length)
            val content = text.substring(start, end)

            val sectionName = findSectionForOffset(document.sections, start)

            chunks.add(
                Chunk(
                    chunkId = "${document.id}_fixed_${chunkIndex}",
                    documentId = document.id,
                    title = document.title,
                    section = sectionName,
                    content = content.trim(),
                    source = document.source,
                    charOffset = start,
                    charLength = content.length,
                )
            )

            chunkIndex++
            start += config.chunkSize - config.overlap
            if (start >= text.length) break
        }

        return chunks
    }

    private fun findSectionForOffset(sections: List<Section>, offset: Int): String {
        var cumulative = 0
        for (section in sections) {
            cumulative += section.content.length
            if (offset < cumulative) return section.heading
        }
        return sections.lastOrNull()?.heading ?: "main"
    }
}

class StructuralChunker : Chunker {
    override fun chunk(document: Document, strategy: ChunkingStrategy): List<Chunk> {
        val config = strategy as? ChunkingStrategy.Structural
            ?: ChunkingStrategy.Structural()

        val chunks = mutableListOf<Chunk>()

        for ((index, section) in document.sections.withIndex()) {
            val text = section.content.trim()
            if (text.length < config.minSectionChars && config.mergeSmallSections && index > 0) {
                val lastChunk = chunks.lastOrNull()
                if (lastChunk != null) {
                    chunks[chunks.size - 1] = lastChunk.copy(
                        content = lastChunk.content + "\n\n" + text,
                        charLength = lastChunk.charLength + text.length + 2,
                    )
                    continue
                }
            }

            chunks.add(
                Chunk(
                    chunkId = "${document.id}_struct_${index}",
                    documentId = document.id,
                    title = document.title,
                    section = section.heading,
                    content = text,
                    source = document.source,
                    charOffset = document.sections.take(index).sumOf { it.content.length },
                    charLength = text.length,
                )
            )
        }

        return chunks
    }
}

object ChunkerFactory {
    fun create(strategy: ChunkingStrategy): Chunker = when (strategy) {
        is ChunkingStrategy.FixedSize -> FixedSizeChunker()
        is ChunkingStrategy.Structural -> StructuralChunker()
    }
}
