package com.llmapp.rag.data

import com.llmapp.rag.domain.EmbeddingService
import kotlin.math.sqrt

class HashingEmbeddingService(
    override val dimension: Int = 512,
    private val seed: Int = 42,
) : EmbeddingService {

    override suspend fun embed(text: String): List<Float> {
        val tokens = tokenize(text)
        val vector = FloatArray(dimension)

        for (token in tokens) {
            val hash = hashToken(token)
            val idx = (hash and Int.MAX_VALUE) % dimension
            vector[idx] += 1.0f
        }

        return normalize(vector).toList()
    }

    override suspend fun embedBatch(texts: List<String>): List<List<Float>> {
        return texts.map { embed(it) }
    }

    private fun tokenize(text: String): List<String> {
        val cleaned = text.lowercase()
            .replace(Regex("[^a-zа-яё0-9\\s]"), " ")
        return cleaned.split(Regex("\\s+"))
            .filter { it.length > 1 }
            .map { it.take(20) }
    }

    private fun hashToken(token: String): Int {
        var hash = seed
        for (char in token) {
            hash = hash * 31 + char.code
        }
        return hash
    }

    private fun normalize(vector: FloatArray): FloatArray {
        val magnitude = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        if (magnitude > 0f) {
            for (i in vector.indices) {
                vector[i] /= magnitude
            }
        }
        return vector
    }
}
