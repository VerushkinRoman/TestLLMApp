package com.llmapp.api

import com.llmapp.model.RouterRequest
import com.llmapp.model.RouterResponse
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

class KodikRouterClient(
    private var apiKey: String,
    private val onApiKeyChanged: ((String) -> Unit)? = null
) : RouterClient {
    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val baseUrl = "https://api.kodikrouter.ru/v1"

    private val fallbackModels = listOf(
        "cognitivecomputations/dolphin-mistral-24b-venice-edition:free",
        "meta-llama/llama-3.2-3b-instruct:free",
        "meta-llama/llama-3.3-70b-instruct:free",
        "google/gemma-4-31b-it:free",
        "qwen/qwen3-coder:free",
        "openai/gpt-oss-120b:free",
        "liquid/lfm-2.5-1.2b-thinking:free",
        "moonshotai/kimi-k2.6:free",
        "nvidia/nemotron-3-nano-30b-a3b:free",
        "poolside/laguna-m.1:free",
        "nousresearch/hermes-3-llama-3.1-405b:free",
        "openai/gpt-oss-20b:free",
        "poolside/laguna-xs.2:free",
        "google/gemma-4-26b-a4b-it:free",
        "z-ai/glm-4.5-air:free",
        "liquid/lfm-2.5-1.2b-instruct:free",
        "tencent/hy3-preview:free",
        "minimax/minimax-m2.5:free",
        "arcee-ai/trinity-large-thinking:free",
        "deepseek/deepseek-v4-flash:free",
        "qwen/qwen3-next-80b-a3b-instruct:free"
    )

    override fun updateApiKey(newApiKey: String) {
        if (apiKey != newApiKey) {
            apiKey = newApiKey
            onApiKeyChanged?.invoke(newApiKey)
            println("🔑 API ключ KodikRouter обновлен, уведомление отправлено")
        }
    }

    fun getCurrentApiKeyPreview(): String = apiKey.take(10) + "..."

    override suspend fun sendRequest(request: RouterRequest): RouterResponse {
        return sendRequestWithRetry(request, 0, 0)
    }

    private suspend fun sendRequestWithRetry(
        request: RouterRequest,
        retryCount: Int,
        modelRetryCount: Int
    ): RouterResponse {
        val currentKeyIndex = ApiConfig.getCurrentKeyIndex()
        val currentApiKeyPreview = getCurrentApiKeyPreview()
        println("🔄 Попытка ${retryCount + 1}/${fallbackModels.size + 1} с моделью: ${request.model} (ключ #$currentKeyIndex: $currentApiKeyPreview)")

        KeyUsageMonitor.recordRequest(currentKeyIndex)

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
                val response = client.post("$baseUrl/chat/completions") {
                    header(HttpHeaders.Authorization, "Bearer $apiKey")
                    header(HttpHeaders.ContentType, "application/json")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                if (response.status.value == 200) {
                    val responseBody = response.body<String>()

                    if (responseBody.isBlank()) {
                        println("⚠️ Пустой ответ от модели ${request.model}")
                        KeyUsageMonitor.recordError(currentKeyIndex, false)
                        return tryNextModel(request, retryCount, modelRetryCount)
                    }

                    return try {
                        val parsed = json.decodeFromString<RouterResponse>(responseBody)
                        if (parsed.error != null) {
                            println("⚠️ Модель ${request.model} вернула ошибку в теле 200: ${parsed.error.message}")
                            KeyUsageMonitor.recordError(currentKeyIndex, true)
                            tryNextModel(request, retryCount, modelRetryCount)
                        } else {
                            ApiConfig.recordSuccess()
                            parsed
                        }
                    } catch (e: Exception) {
                        println("⚠️ Ошибка парсинга JSON от ${request.model}: ${e.message}")
                        KeyUsageMonitor.recordError(currentKeyIndex, false)
                        tryNextModel(request, retryCount, modelRetryCount)
                    }
                }

                val errorText = response.body<String>()
                println("⚠️ Ошибка ${response.status.value} для модели ${request.model}: $errorText")

                return when (response.status.value) {
                    401 -> {
                        println("❌ Неверный API ключ! Пробуем следующий ключ...")
                        KeyUsageMonitor.recordError(currentKeyIndex, false)
                        rotateToNextKeyAndRetry(request, retryCount, modelRetryCount)
                    }

                    429 -> {
                        println("⚠️ Превышен лимит запросов для ключа #$currentKeyIndex")
                        KeyUsageMonitor.recordError(currentKeyIndex, true)
                        rotateToNextKeyAndRetry(request, retryCount, modelRetryCount)
                    }

                    403 -> {
                        println("⚠️ Доступ запрещен для модели ${request.model}")
                        KeyUsageMonitor.recordError(currentKeyIndex, false)
                        tryNextModel(request, retryCount, modelRetryCount)
                    }

                    404 -> {
                        println("⚠️ Модель ${request.model} не найдена")
                        KeyUsageMonitor.recordError(currentKeyIndex, false)
                        tryNextModel(request, retryCount, modelRetryCount)
                    }

                    in 500..599 -> {
                        println("⚠️ Серверная ошибка для ${request.model}")
                        KeyUsageMonitor.recordError(currentKeyIndex, false)
                        tryNextModel(request, retryCount, modelRetryCount)
                    }

                    else -> {
                        println("⚠️ Неизвестная ошибка для ${request.model}")
                        KeyUsageMonitor.recordError(currentKeyIndex, false)
                        tryNextModel(request, retryCount, modelRetryCount)
                    }
                }

            } catch (e: Exception) {
                println("⚠️ Сетевая ошибка для ${request.model}: ${e.message}")
                KeyUsageMonitor.recordError(currentKeyIndex, false)
                return tryNextModel(request, retryCount, modelRetryCount)
            }
        }
    }

    private suspend fun rotateToNextKeyAndRetry(
        originalRequest: RouterRequest,
        currentModelRetry: Int,
        currentKeyRetry: Int
    ): RouterResponse {
        if (currentKeyRetry >= ApiConfig.getTotalKeysCount()) {
            println("❌ Все API ключи перепробованы, все достигли лимита!")
            return RouterResponse(
                error = com.llmapp.model.ErrorResponse(
                    message = "All API keys have reached their limits. Try again later.",
                    code = 429
                )
            )
        }

        val newApiKey = ApiConfig.rotateToNextKey()
        updateApiKey(newApiKey)

        println("🔄 Переключение на ключ #${ApiConfig.getCurrentKeyIndex()}, повторяем запрос...")
        delay(1.seconds)

        return sendRequestWithRetry(
            originalRequest,
            currentModelRetry,
            currentKeyRetry + 1
        )
    }

    private suspend fun tryNextModel(
        originalRequest: RouterRequest,
        currentModelRetry: Int,
        currentKeyRetry: Int
    ): RouterResponse {
        if (currentModelRetry >= fallbackModels.size) {
            if (currentKeyRetry < ApiConfig.getTotalKeysCount()) {
                println("🔄 Все модели перепробованы, пробуем следующий API ключ...")
                return rotateToNextKeyAndRetry(originalRequest, 0, currentKeyRetry + 1)
            }

            println("❌ Все модели и все ключи перепробованы, ни одна комбинация не работает!")
            return RouterResponse(
                error = com.llmapp.model.ErrorResponse(
                    message = "All models and API keys failed. Check API keys and internet connection.",
                    code = 500
                )
            )
        }

        val nextModel = fallbackModels[currentModelRetry]
        println("🔄 Переключение на следующую модель: $nextModel (${currentModelRetry + 1}/${fallbackModels.size})")
        delay(2.seconds)

        return sendRequestWithRetry(
            originalRequest.copy(model = nextModel),
            currentModelRetry + 1,
            currentKeyRetry
        )
    }
}
