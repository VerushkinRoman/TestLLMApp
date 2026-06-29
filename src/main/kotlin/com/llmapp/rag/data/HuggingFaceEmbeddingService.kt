package com.llmapp.rag.data

import com.llmapp.rag.domain.EmbeddingService
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Properties

class HuggingFaceEmbeddingService(
    private val modelId: String = "ibm-granite/granite-embedding-97m-multilingual-r2",
    private val batchSize: Int = 10,
) : EmbeddingService {

    override val dimension: Int = 768

    private val apiToken: String = run {
        val props = Properties()
        File("keys.properties").inputStream().use { props.load(it) }
        props.getProperty("huggingface.key")
            ?: error("huggingface.key не найден в keys.properties")
    }

    private val client = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            socketTimeoutMillis = 120_000
        }
    }

    override suspend fun embed(text: String): List<Float> {
        return embedBatch(listOf(text)).first()
    }

    override suspend fun embedBatch(texts: List<String>): List<List<Float>> {
        val results = mutableListOf<List<Float>>()
        val chunks = texts.chunked(batchSize)

        println("🤗 [HF] Отправляю ${texts.size} текстов на $modelId, батчами по $batchSize (${chunks.size} запросов)...")

        for ((i, chunk) in chunks.withIndex()) {
            println("  🤗 [HF] Батч ${i + 1}/${chunks.size} (${chunk.size} текстов)...")

            val response = withContext(Dispatchers.IO) {
                client.post("https://router.huggingface.co/hf-inference/models/$modelId") {
                    header(HttpHeaders.Authorization, "Bearer $apiToken")
                    contentType(ContentType.Application.Json)
                    setBody(mapOf("inputs" to chunk))
                }
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                throw RuntimeException("HF API error ${response.status}: $errorBody")
            }

            val raw = response.bodyAsText()
            val batchResult: List<List<Float>> = Json.decodeFromString(raw)
            results.addAll(batchResult)
        }

        println("🤗 [HF] Успешно: ${results.size} векторов, ${results.firstOrNull()?.size} размерность")
        return results
    }
}
