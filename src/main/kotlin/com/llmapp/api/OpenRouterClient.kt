package com.llmapp.api

import com.llmapp.model.OpenRouterRequest
import com.llmapp.model.OpenRouterResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class OpenRouterClient(private val apiKey: String) {
    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    suspend fun sendRequest(request: OpenRouterRequest): OpenRouterResponse {
        val client = HttpClient {
            install(ContentNegotiation) {
                json(json)
            }

            install(HttpTimeout) {
                socketTimeoutMillis = 30.seconds.inWholeMilliseconds
                connectTimeoutMillis = 30.seconds.inWholeMilliseconds
                requestTimeoutMillis = 5.minutes.inWholeMilliseconds
            }
        }

        return client.use { client ->
            val responseBody: String =
                client.post("https://openrouter.ai/api/v1/chat/completions") {
                    header(HttpHeaders.Authorization, "Bearer $apiKey")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.body()

            json.decodeFromString<OpenRouterResponse>(responseBody)
        }
    }
}
