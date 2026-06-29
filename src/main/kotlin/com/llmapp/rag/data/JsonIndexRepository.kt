package com.llmapp.rag.data

import com.llmapp.rag.domain.Chunk
import com.llmapp.rag.domain.ChunkingStrategy
import com.llmapp.rag.domain.IndexRepository
import com.llmapp.rag.domain.IndexResult
import com.llmapp.rag.domain.SearchResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.sqrt

private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

@Serializable
data class ChunkData(
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

@Serializable
data class IndexData(
    val chunks: List<ChunkData>,
    val strategyName: String,
    val strategyParams: Map<String, String>,
    val totalDocuments: Int,
    val totalChunks: Int,
    val embeddingDimension: Int,
    val indexedAt: Long,
)

class JsonIndexRepository(
    private val indexDir: String = System.getProperty("user.home") + "/.llm_chat_app/rag_index",
) : IndexRepository {

    private var currentIndex: IndexResult? = null

    override suspend fun saveIndex(index: IndexResult) {
        val dir = File(indexDir)
        if (!dir.exists()) dir.mkdirs()

        val strategyDesc = when (val s = index.strategy) {
            is ChunkingStrategy.FixedSize -> mapOf(
                "type" to "fixed",
                "chunkSize" to s.chunkSize.toString(),
                "overlap" to s.overlap.toString(),
            )

            is ChunkingStrategy.Structural -> mapOf(
                "type" to "structural",
                "mergeSmallSections" to s.mergeSmallSections.toString(),
                "minSectionChars" to s.minSectionChars.toString(),
            )
        }

        val strategyName = when (index.strategy) {
            is ChunkingStrategy.FixedSize -> "fixed"
            is ChunkingStrategy.Structural -> "structural"
        }

        val data = IndexData(
            chunks = index.chunks.map { it.toChunkData() },
            strategyName = strategyName,
            strategyParams = strategyDesc,
            totalDocuments = index.totalDocuments,
            totalChunks = index.totalChunks,
            embeddingDimension = index.embeddingDimension,
            indexedAt = index.indexedAt,
        )

        val file = File(dir, "index_${strategyName}.json")
        file.writeText(json.encodeToString(data))
        currentIndex = index
    }

    override suspend fun loadIndex(): IndexResult? {
        val dir = File(indexDir)
        if (!dir.exists()) return null

        val file = File(dir, "index_fixed.json")
        if (!file.exists()) return null
        if (currentIndex != null) return currentIndex

        return try {
            val data = json.decodeFromString<IndexData>(file.readText())
            val strategy = parseStrategy(data.strategyName, data.strategyParams)

            IndexResult(
                chunks = data.chunks.map { it.toChunk() },
                strategy = strategy,
                totalDocuments = data.totalDocuments,
                totalChunks = data.totalChunks,
                embeddingDimension = data.embeddingDimension,
                indexedAt = data.indexedAt,
            ).also { currentIndex = it }
        } catch (_: Exception) {
            null
        }
    }

    fun loadIndexForStrategy(strategyName: String): IndexResult? {
        val dir = File(indexDir)
        val file = File(dir, "index_${strategyName}.json")
        if (!file.exists()) return null

        return try {
            val data = json.decodeFromString<IndexData>(file.readText())
            val strategy = parseStrategy(data.strategyName, data.strategyParams)
            IndexResult(
                chunks = data.chunks.map { it.toChunk() },
                strategy = strategy,
                totalDocuments = data.totalDocuments,
                totalChunks = data.totalChunks,
                embeddingDimension = data.embeddingDimension,
                indexedAt = data.indexedAt,
            )
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun search(query: List<Float>, topK: Int): List<SearchResult> {
        val idx = currentIndex ?: return emptyList()

        val scored = idx.chunks.mapNotNull { chunk ->
            val emb = chunk.embedding ?: return@mapNotNull null
            val score = cosineSimilarity(query, emb)
            chunk to score
        }

        return scored
            .sortedByDescending { (_, score) -> score }
            .take(topK)
            .mapIndexed { i, (chunk, score) ->
                SearchResult(chunk = chunk, score = score, rank = i + 1)
            }
    }

    override fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        val size = minOf(a.size, b.size)
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in 0 until size) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 0) (dot / denom).toFloat() else 0f
    }

    private fun parseStrategy(name: String, params: Map<String, String>): ChunkingStrategy {
        return when (name) {
            "fixed" -> ChunkingStrategy.FixedSize(
                chunkSize = params["chunkSize"]?.toIntOrNull() ?: 600,
                overlap = params["overlap"]?.toIntOrNull() ?: 100,
            )

            "structural" -> ChunkingStrategy.Structural(
                mergeSmallSections = params["mergeSmallSections"]?.toBoolean() ?: true,
                minSectionChars = params["minSectionChars"]?.toIntOrNull() ?: 100,
            )

            else -> ChunkingStrategy.FixedSize()
        }
    }

    private fun ChunkData.toChunk() = Chunk(
        chunkId = chunkId,
        documentId = documentId,
        title = title,
        section = section,
        content = content,
        source = source,
        charOffset = charOffset,
        charLength = charLength,
        embedding = embedding,
    )

    private fun Chunk.toChunkData() = ChunkData(
        chunkId = chunkId,
        documentId = documentId,
        title = title,
        section = section,
        content = content,
        source = source,
        charOffset = charOffset,
        charLength = charLength,
        embedding = embedding,
    )
}
