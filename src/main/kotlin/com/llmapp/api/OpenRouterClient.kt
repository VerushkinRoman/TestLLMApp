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
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class OpenRouterClient(private val apiKey: String) {
    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    // Полный список всех бесплатных моделей в порядке приоритета
    private val fallbackModels = listOf(
        // NVIDIA Nemotron серия
        "nvidia/nemotron-3-nano-30b-a3b:free",           // Легкая и быстрая
        "nvidia/nemotron-3-super-120b-a12b:free",        // Мощная для сложных задач
        "nvidia/nemotron-3-ultra-550b-a55b:free",        // Фронтир-модель
        "nvidia/nemotron-3-nano-omni:free",              // Мультимодальная
        "nvidia/nemotron-nano-9b-v2:free",               // Компактная
        "nvidia/nemotron-nano-12b-2-vl:free",            // Для видео и OCR

        // OpenAI GPT-OSS серия
        "openai/gpt-oss-20b:free",                       // Легкий и быстрый
        "openai/gpt-oss-120b:free",                      // Мощный с reasoning

        // Google Gemma серия
        "google/gemma-4-26b-a4b-it:free",                // Баланс качества и скорости
        "google/gemma-4-31b-it:free",                    // Лучшая для кода

        // Poolside кодинг-модели
        "poolside/laguna-xs.2:free",                     // Компактный кодинг-агент
        "poolside/laguna-m.1:free",                      // Флагманский кодинг-агент

        // Другие модели
        "openrouter/owl-alpha",                          // Agentic workloads
        "z-ai/glm-4.5-air:free",                         // MoE архитектура
        "moonshotai/kimi-k2.6:free"                      // Мультимодальная
    )

    suspend fun sendRequest(request: OpenRouterRequest, retryCount: Int = 0): OpenRouterResponse {
        println("🔄 Попытка ${retryCount + 1}/${fallbackModels.size + 1} с моделью: ${request.model}")

        val client = HttpClient {
            install(ContentNegotiation) {
                json(json)
            }

            install(HttpTimeout) {
                socketTimeoutMillis = 60.seconds.inWholeMilliseconds
                connectTimeoutMillis = 30.seconds.inWholeMilliseconds
                requestTimeoutMillis = 5.minutes.inWholeMilliseconds
            }
        }

        client.use { client ->
            try {
                val response = client.post("https://openrouter.ai/api/v1/chat/completions") {
                    header(HttpHeaders.Authorization, "Bearer $apiKey")
                    header(HttpHeaders.ContentType, "application/json")
                    header("HTTP-Referer", "https://localhost")
                    header("X-Title", "LLM Chat App")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                if (response.status.value == 200) {
                    val responseBody = response.body<String>()

                    if (responseBody.isBlank()) {
                        println("⚠️ Пустой ответ от модели ${request.model}")
                        return tryNextModel(request, retryCount)
                    }

                    return try {
                        json.decodeFromString<OpenRouterResponse>(responseBody)
                    } catch (e: Exception) {
                        println("⚠️ Ошибка парсинга JSON от ${request.model}: ${e.message}")
                        tryNextModel(request, retryCount)
                    }
                }

                val errorText = response.body<String>()
                println("⚠️ Ошибка ${response.status.value} для модели ${request.model}: $errorText")

                return when (response.status.value) {
                    401 -> {
                        println("❌ Неверный API ключ! Проверьте openrouter.properties")
                        OpenRouterResponse(
                            error = com.llmapp.model.ErrorResponse(
                                message = "Invalid API key",
                                code = 401
                            )
                        )
                    }
                    429 -> {
                        println("⚠️ Превышен лимит запросов для ${request.model}")
                        tryNextModel(request, retryCount)
                    }
                    403 -> {
                        println("⚠️ Доступ запрещен для ${request.model}")
                        tryNextModel(request, retryCount)
                    }
                    404 -> {
                        println("⚠️ Модель ${request.model} не найдена")
                        tryNextModel(request, retryCount)
                    }
                    in 500..599 -> {
                        println("⚠️ Серверная ошибка для ${request.model}")
                        tryNextModel(request, retryCount)
                    }
                    else -> {
                        println("⚠️ Неизвестная ошибка для ${request.model}")
                        tryNextModel(request, retryCount)
                    }
                }

            } catch (e: Exception) {
                println("⚠️ Сетевая ошибка для ${request.model}: ${e.message}")
                return tryNextModel(request, retryCount)
            }
        }
    }

    private suspend fun tryNextModel(
        originalRequest: OpenRouterRequest,
        currentRetry: Int
    ): OpenRouterResponse {
        if (currentRetry >= fallbackModels.size) {
            println("❌ Все модели перепробованы, ни одна не работает!")
            return OpenRouterResponse(
                error = com.llmapp.model.ErrorResponse(
                    message = "All models failed. Check API key and internet connection.",
                    code = 500
                )
            )
        }

        val nextModel = fallbackModels[currentRetry]
        println("🔄 Переключение на следующую модель: $nextModel (${currentRetry + 1}/${fallbackModels.size})")
        delay(2.seconds)

        return sendRequest(
            originalRequest.copy(model = nextModel),
            currentRetry + 1
        )
    }
}
