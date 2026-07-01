package com.llmapp.rag.domain

enum class RerankerType {
    NONE,
    SIMILARITY_THRESHOLD,
    HEURISTIC,
}

data class RerankerConfig(
    val type: RerankerType = RerankerType.SIMILARITY_THRESHOLD,
    val similarityThreshold: Float = 0.3f,
    val topKBefore: Int = 20,
    val topKAfter: Int = 5,
)

data class RerankerResult(
    val originalResults: List<SearchResult>,
    val filteredResults: List<SearchResult>,
    val config: RerankerConfig,
    val removedCount: Int,
    val rerankTimeMs: Long,
)

interface Reranker {
    suspend fun rerank(query: String, results: List<SearchResult>, config: RerankerConfig): RerankerResult
}

class SimilarityThresholdReranker : Reranker {
    override suspend fun rerank(query: String, results: List<SearchResult>, config: RerankerConfig): RerankerResult {
        val start = System.currentTimeMillis()
        val filtered = results
            .filter { it.score >= config.similarityThreshold }
            .sortedByDescending { it.score }
            .take(config.topKAfter)
            .mapIndexed { i, r -> r.copy(rank = i + 1) }
        val elapsed = System.currentTimeMillis() - start
        return RerankerResult(
            originalResults = results,
            filteredResults = filtered,
            config = config,
            removedCount = results.size - filtered.size,
            rerankTimeMs = elapsed,
        )
    }
}

class HeuristicReranker : Reranker {
    override suspend fun rerank(query: String, results: List<SearchResult>, config: RerankerConfig): RerankerResult {
        val start = System.currentTimeMillis()
        val queryTerms = query.lowercase().split("\\s+".toRegex()).filter { it.length > 2 }.toSet()
        val scored = results.map { r ->
            val chunkLower = r.chunk.content.lowercase()
            val titleLower = r.chunk.title.lowercase()
            val sectionLower = r.chunk.section.lowercase()
            val keywordOverlap = queryTerms.count { it in chunkLower }
            val titleBonus = if (queryTerms.any { it in titleLower }) 0.15f else 0f
            val sectionBonus = if (queryTerms.any { it in sectionLower }) 0.1f else 0f
            val keywordRatio = if (queryTerms.isNotEmpty()) keywordOverlap.toFloat() / queryTerms.size else 0f
            val keywordScore = keywordRatio * 0.3f
            val heuristicScore = r.score + keywordScore + titleBonus + sectionBonus
            r to heuristicScore
        }
        val filtered = scored
            .filter { (r, _) -> r.score >= config.similarityThreshold }
            .sortedByDescending { (_, s) -> s }
            .take(config.topKAfter)
            .mapIndexed { i, (r, s) -> r.copy(rank = i + 1, score = s) }
        val elapsed = System.currentTimeMillis() - start
        return RerankerResult(
            originalResults = results,
            filteredResults = filtered,
            config = config,
            removedCount = results.size - filtered.size,
            rerankTimeMs = elapsed,
        )
    }
}

class SwitchingReranker : Reranker {
    private val thresholdReranker = SimilarityThresholdReranker()
    private val heuristicReranker = HeuristicReranker()

    override suspend fun rerank(query: String, results: List<SearchResult>, config: RerankerConfig): RerankerResult {
        return when (config.type) {
            RerankerType.HEURISTIC -> heuristicReranker.rerank(query, results, config)
            RerankerType.SIMILARITY_THRESHOLD, RerankerType.NONE -> thresholdReranker.rerank(query, results, config)
        }
    }
}
