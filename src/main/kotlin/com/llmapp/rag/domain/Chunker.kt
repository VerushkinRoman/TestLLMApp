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

            val midPoint = (start + end) / 2
            val sectionName = findSectionForOffset(document.sections, document.content, midPoint)

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

    private fun findSectionForOffset(sections: List<Section>, content: String, offset: Int): String {
        if (sections.isEmpty()) return "main"

        // Find positions of all section headings in the document content
        // to determine actual section boundaries
        data class Boundary(val heading: String, val position: Int)
        val foundBoundaries = mutableListOf<Boundary>()

        for (section in sections) {
            val headingInContent = "\n${section.heading}\n"
            val idx = content.indexOf(headingInContent)
            if (idx >= 0) {
                foundBoundaries.add(Boundary(section.heading, idx))
            }
        }

        // For sections not found in content (e.g., "Плей-офф" which spans
        // "1/8 финала и четвертьфинал" + "Полуфинал против Хорватии" in content),
        // estimate their position by interpolating between found boundaries
        if (foundBoundaries.isNotEmpty()) {
            foundBoundaries.sortBy { it.position }

            // Before first heading → belongs to intro/main section
            if (offset < foundBoundaries.first().position) {
                return "main"
            }

            // Find the nearest preceding boundary
            var result = foundBoundaries.first().heading
            for (b in foundBoundaries) {
                if (b.position <= offset) result = b.heading
            }
            return result
        }

        // If no headings found at all, fall back to the old behavior but use a
        // more reasonable estimate based on content length distribution
        if (sections.size == 1) return sections.first().heading

        val avgSectionLength = content.length / sections.size
        val estimatedSection = (offset / avgSectionLength).coerceAtMost(sections.size - 1)
        return sections[estimatedSection].heading
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

            if (text.length > config.maxSectionChars) {
                val subChunks = splitLargeSection(text, config.maxSectionChars)
                subChunks.forEachIndexed { subIndex, subText ->
                    chunks.add(
                        Chunk(
                            chunkId = "${document.id}_struct_${index}_$subIndex",
                            documentId = document.id,
                            title = document.title,
                            section = if (subIndex == 0) section.heading else "${section.heading} (продолжение ${subIndex + 1})",
                            content = subText,
                            source = document.source,
                            charOffset = document.sections.take(index).sumOf { it.content.length } + subIndex * config.maxSectionChars,
                            charLength = subText.length,
                        )
                    )
                }
            } else {
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
        }

        return chunks
    }

    private fun splitLargeSection(text: String, maxSize: Int): List<String> {
        val result = mutableListOf<String>()
        var remaining = text

        while (remaining.length > maxSize) {
            val breakPoint = findBreakPoint(remaining, maxSize)
            result.add(remaining.substring(0, breakPoint).trim())
            remaining = remaining.substring(breakPoint).trim()
        }

        if (remaining.isNotBlank()) {
            result.add(remaining)
        }

        return result
    }

    private fun findBreakPoint(text: String, maxSize: Int): Int {
        val newLinePos = text.lastIndexOf('\n', maxSize - 1)
        if (newLinePos > maxSize * 0.6) return newLinePos

        val sentenceEnd = text.lastIndexOf(". ", maxSize - 1)
        if (sentenceEnd > maxSize * 0.6) return sentenceEnd + 1

        val commaPos = text.lastIndexOf(", ", maxSize - 1)
        if (commaPos > maxSize * 0.6) return commaPos + 1

        return maxSize
    }
}

object ChunkerFactory {
    fun create(strategy: ChunkingStrategy): Chunker = when (strategy) {
        is ChunkingStrategy.FixedSize -> FixedSizeChunker()
        is ChunkingStrategy.Structural -> StructuralChunker()
    }
}
