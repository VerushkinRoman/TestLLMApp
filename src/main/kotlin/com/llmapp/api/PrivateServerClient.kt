package com.llmapp.api

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
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class PrivateServerClient(
    private val baseUrl: String = "https://alcoserver.ru:18333"
) : RouterClient {
    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    override suspend fun sendRequest(request: RouterRequest): RouterResponse {
        val client = HttpClient {
            install(ContentNegotiation) { json(this@PrivateServerClient.json) }
            install(Auth) {
                basic {
                    credentials {
                        BasicAuthCredentials(
                            username = ApiConfig.getPrivateServerUser(),
                            password = ApiConfig.getPrivateServerPassword(),
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
                val response = c.post("$baseUrl/v1/chat/completions") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                response.body<RouterResponse>()
            }
        } catch (e: Exception) {
            println("❌ PrivateServer: ${e.message}")
            RouterResponse(
                error = com.llmapp.model.ErrorResponse(
                    message = "PrivateServer error: ${e.message}"
                )
            )
        }
    }
}
