package com.llmapp.rag

import com.llmapp.rag.data.HuggingFaceEmbeddingService
import com.llmapp.rag.data.JsonIndexRepository
import com.llmapp.rag.domain.IndexRepository
import com.llmapp.rag.domain.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RagContext(
    val chunks: List<SearchResult>,
    val combinedContext: String,
    val searchTimeMs: Long,
)

class RAGEnhancer(
    private val embeddingService: HuggingFaceEmbeddingService = HuggingFaceEmbeddingService(),
    private val repository: IndexRepository = JsonIndexRepository(),
    val topK: Int = 5,
) {
    private var isIndexLoaded = false

    suspend fun ensureIndexLoaded() {
        if (isIndexLoaded) return
        withContext(Dispatchers.Default) {
            repository.loadIndex()
            isIndexLoaded = true
        }
    }

    suspend fun search(query: String): RagContext {
        val start = System.currentTimeMillis()

        val queryEmbedding = withContext(Dispatchers.Default) {
            embeddingService.embed(query)
        }

        val results = withContext(Dispatchers.Default) {
            repository.search(queryEmbedding, topK)
        }

        val searchTime = System.currentTimeMillis() - start

        val context = buildString {
            appendLine("Контекст из базы знаний чемпионатов мира:")
            appendLine()
            for ((i, r) in results.withIndex()) {
                appendLine("--- Фрагмент ${i + 1} (релевантность: ${"%.3f".format(r.score)}) ---")
                appendLine("Источник: ${r.chunk.title} — ${r.chunk.section}")
                appendLine(r.chunk.content.take(2000))
                appendLine()
            }
        }

        return RagContext(
            chunks = results,
            combinedContext = context,
            searchTimeMs = searchTime,
        )
    }

    suspend fun searchWithContext(query: String): String {
        val rag = search(query)
        if (rag.chunks.isEmpty()) return query

        return buildString {
            appendLine("Ответь на вопрос пользователя, используя информацию из предоставленного контекста.")
            appendLine("Если в контексте нет ответа — ответь на основе своих знаний, но укажи это.")
            appendLine()
            appendLine(rag.combinedContext)
            appendLine("=== КОНЕЦ КОНТЕКСТА ===")
            appendLine()
            appendLine("Вопрос: $query")
            appendLine()
            appendLine("Ответь на русском языке, используя факты из контекста. Укажи источник информации.")
        }
    }
}
