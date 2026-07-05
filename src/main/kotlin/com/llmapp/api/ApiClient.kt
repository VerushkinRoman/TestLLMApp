package com.llmapp.api

import com.llmapp.model.ModelList
import com.llmapp.model.RouterRequest
import com.llmapp.model.RouterResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

class ApiClient(
    private val baseUrl: String = ApiConfig.getBaseUrl()
) : RouterClient {

    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val fallbackModels = ModelList.chatModels

    override suspend fun sendRequest(request: RouterRequest): RouterResponse {
        return sendWithFallback(request, 0)
    }

    private suspend fun sendWithFallback(
        request: RouterRequest,
        fallbackIndex: Int,
        retryCount: Int = 0,
    ): RouterResponse {
        val model = if (fallbackIndex == 0) request.model
        else fallbackModels.getOrNull(fallbackIndex) ?: run {
            println("🔄 Все модели исчерпаны, начинаю новый цикл с первой модели")
            delay(10.seconds)
            return sendWithFallback(request, 0)
        }

        println("🌐 $baseUrl/chat/completions | model=$model")

        val client = HttpClient {
            install(ContentNegotiation) { json(this@ApiClient.json) }
            install(Auth) {
                basic {
                    credentials {
                        BasicAuthCredentials(
                            username = ApiConfig.getLlmUser(),
                            password = ApiConfig.getLlmUserPassword(),
                        )
                    }
                }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 300_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 300_000
            }
        }

        return try {
            client.use { c ->
                val response = c.post("$baseUrl/chat/completions") {
                    contentType(ContentType.Application.Json)
                    setBody(request.copy(model = model))
                }

                if (response.status.value == 429) {
                    if (retryCount < 3) {
                        println("⚠️ 429 Too Many Requests, retry $retryCount in 5s")
                        delay(5.seconds)
                        return sendWithFallback(request, fallbackIndex, retryCount + 1)
                    }
                    println("⚠️ 429 исчерпал ретраи, перехожу к следующей модели")
                    return sendWithFallback(request, fallbackIndex + 1)
                }

                if (!response.status.isSuccess()) {
                    val bodyText = response.bodyAsText()
                    println("⚠️ $model | ${response.status} | ${bodyText.take(200)}")

                    if (response.status.value == 502 && retryCount < 1) {
                        delay(3.seconds)
                        return sendWithFallback(request, fallbackIndex, retryCount + 1)
                    }

                    delay(2.seconds)
                    return sendWithFallback(request, fallbackIndex + 1)
                }

                val body = response.body<RouterResponse>()
                if (body.error != null) {
                    println("⚠️ Модель $model вернула ошибку: ${body.error.message}")
                    delay(1.seconds)
                    return sendWithFallback(request, fallbackIndex + 1)
                }
                body
            }
        } catch (e: Exception) {
            println("❌ $model: ${e.message}")
            delay(5.seconds)
            return sendWithFallback(request, fallbackIndex + 1)
        }
    }
}
