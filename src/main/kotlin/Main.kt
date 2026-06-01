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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Properties

@Serializable
data class OpenRouterRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenRouterResponse(
    val choices: List<Choice>? = null,
    val error: ErrorResponse? = null
)

@Serializable
data class Choice(
    val message: ResponseMessage?
)

@Serializable
data class ResponseMessage(
    val content: String?
)

@Serializable
data class ErrorResponse(
    val message: String,
    val code: Int? = null
)

fun getApiKey(): String {
    val propsFile = File("openrouter.properties")
    val props = Properties()
    props.load(propsFile.inputStream())
    return props.getProperty("api.key")
}

private val json = Json { ignoreUnknownKeys = true }

data class ModelInfo(
    val id: String,
    val name: String,
    val description: String
)

val freeModels = listOf(
    ModelInfo("openrouter/owl-alpha", "Owl Alpha", "Отличная модель для общих задач"),
    ModelInfo(
        "nvidia/nemotron-3-super-120b-a12b:free",
        "NVIDIA Nemotron 3",
        "Мощная модель от NVIDIA, 120B параметров"
    ),
    ModelInfo(
        "poolside/laguna-m.1:free",
        "Poolside Laguna M.1",
        "Хороший баланс скорости и качества"
    ),
    ModelInfo("openai/gpt-oss-120b:free", "GPT-OSS 120B", "Open source альтернатива от OpenAI"),
    ModelInfo("z-ai/glm-4.5-air:free", "Z-AI GLM 4.5 Air", "Быстрая модель от Zhipu AI"),
    ModelInfo("poolside/laguna-xs.2:free", "Poolside Laguna XS.2", "Компактная и быстрая модель"),
    ModelInfo("google/gemma-4-26b-a4b-it:free", "Google Gemma 4", "Современная модель от Google, 26B")
)

class ChatSession(
    private val apiKey: String,
    private var model: String = "openrouter/owl-alpha",
    private val systemPrompt: String = "Ты полезный ассистент. Отвечай кратко и по делу на русском языке.",
    private val maxHistorySize: Int = 20
) {
    private val messageHistory = mutableListOf<ChatMessage>()

    init {
        messageHistory.add(ChatMessage(role = "system", content = systemPrompt))
    }

    fun changeModel(newModel: String) {
        model = newModel
        println("Модель изменена на: ${freeModels.find { it.id == newModel }?.name ?: newModel}")
    }

    fun getCurrentModel(): String = model

    suspend fun ask(userPrompt: String): String {
        messageHistory.add(ChatMessage(role = "user", content = userPrompt))
        trimHistory()

        val request = OpenRouterRequest(
            model = model,
            messages = messageHistory.toList()
        )

        val client = HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }

            install(HttpTimeout) {
                socketTimeoutMillis = 30_000
                connectTimeoutMillis = 30_000
                requestTimeoutMillis = 120_000
            }
        }

        client.use { client ->
            val responseBody: String =
                client.post("https://openrouter.ai/api/v1/chat/completions") {
                    header(HttpHeaders.Authorization, "Bearer $apiKey")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.body()

            val jsonResponse = json.decodeFromString<OpenRouterResponse>(responseBody)

            jsonResponse.error?.let {
                throw Exception("API Error: ${it.message}")
            }

            val answer = jsonResponse.choices?.firstOrNull()?.message?.content
                ?: throw Exception("Empty API response")

            messageHistory.add(ChatMessage(role = "assistant", content = answer))

            return answer
        }
    }

    private fun trimHistory() {
        if (messageHistory.size > maxHistorySize + 1) {
            val systemMessage = messageHistory.first()
            val recentMessages = messageHistory.takeLast(maxHistorySize)
            messageHistory.clear()
            messageHistory.add(systemMessage)
            messageHistory.addAll(recentMessages)
        }
    }

    fun clearHistory() {
        messageHistory.clear()
        messageHistory.add(ChatMessage(role = "system", content = systemPrompt))
        println("История диалога очищена")
    }

    fun showHistory() {
        println("\nИстория диалога (${messageHistory.size - 1} сообщений):")
        messageHistory.drop(1).forEach { msg ->
            val prefix = if (msg.role == "user") "👤 Промпт: " else "🤖 Ответ: "
            val shortMessage = msg.content.take(80)
            println("$prefix $shortMessage${if (msg.content.length > 80) "..." else ""}")
        }
        println()
    }

    fun getHistorySize(): Int = messageHistory.size - 1
}

fun showModels() {
    println("\n" + "=".repeat(60))
    println("📋 Доступные бесплатные модели:")
    println("=".repeat(60))
    freeModels.forEachIndexed { index, model ->
        println("${index + 1}. ${model.name}")
        println("   ID: ${model.id}")
        println("   📝 ${model.description}")
        println("-".repeat(60))
    }
    println()
}

fun main() = runBlocking {
    val apiKey = getApiKey()
    val chat = ChatSession(apiKey)

    println("=".repeat(60))
    println("🤖 OpenRouter Chat - Бесплатные модели")
    println("=".repeat(60))

    showModels()

    println("💡 Команды:")
    println("  /exit      - завершить сеанс")
    println("  /clear     - очистить историю")
    println("  /history   - показать историю")
    println("  /size      - показать размер истории")
    println("  /models    - показать список доступных моделей")
    println("  /model N   - выбрать модель по номеру (например: /model 3)")
    println("  /model ID  - выбрать модель по ID")
    println("  /current   - показать текущую модель")
    println("  /timeout   - показать настройки таймаута")
    println("=".repeat(60))

    println("\n✅ Текущая модель: ${freeModels.find { it.id == chat.getCurrentModel() }?.name ?: chat.getCurrentModel()}")
    println("⏱️  Таймаут ответа: 2 минуты (120 секунд)")

    while (true) {
        print("\n💬 Промпт: ")
        val input = readlnOrNull()?.trim() ?: break

        when {
            input.equals("/exit", ignoreCase = true) -> {
                println("👋 До свидания!")
                break
            }

            input.equals("/clear", ignoreCase = true) -> {
                chat.clearHistory()
                continue
            }

            input.equals("/history", ignoreCase = true) -> {
                chat.showHistory()
                continue
            }

            input.equals("/size", ignoreCase = true) -> {
                println("📊 В истории ${chat.getHistorySize()} сообщений")
                continue
            }

            input.equals("/models", ignoreCase = true) -> {
                showModels()
                continue
            }

            input.equals("/current", ignoreCase = true) -> {
                val currentModel = freeModels.find { it.id == chat.getCurrentModel() }
                if (currentModel != null) {
                    println("📌 Текущая модель: ${currentModel.name}")
                    println("   ID: ${currentModel.id}")
                    println("   ${currentModel.description}")
                } else {
                    println("📌 Текущая модель: ${chat.getCurrentModel()}")
                }
                continue
            }

            input.equals("/timeout", ignoreCase = true) -> {
                println("⏱️  Настройки таймаута:")
                println("   - Таймаут запроса: 120 секунд (2 минуты)")
                println("   - Таймаут соединения: 30 секунд")
                println("   - Таймаут сокета: 30 секунд")
                continue
            }

            input.startsWith("/model", ignoreCase = true) -> {
                val parts = input.split(" ", limit = 2)
                if (parts.size < 2) {
                    println("❌ Укажите номер или ID модели. Используйте /models для просмотра списка")
                    continue
                }

                val modelArg = parts[1].trim()
                val selectedModel = try {
                    val index = modelArg.toInt() - 1
                    if (index in freeModels.indices) freeModels[index] else null
                } catch (_: NumberFormatException) {
                    freeModels.find { it.id.equals(modelArg, ignoreCase = true) }
                }

                if (selectedModel != null) {
                    chat.changeModel(selectedModel.id)
                    println("✅ Модель изменена на: ${selectedModel.name}")
                } else {
                    println("❌ Модель не найдена. Используйте /models для просмотра доступных моделей")
                }
                continue
            }

            input.isBlank() -> continue
        }

        print("🤔 Думаю (максимум 2 минуты)... ")
        try {
            val answer = chat.ask(input)
            println("\n🤖 Ответ: $answer")
        } catch (e: Exception) {
            println("\n⚠️ Ошибка: ${e.message}")
            if (e.message?.contains("timeout", ignoreCase = true) == true) {
                println("💡 Превышен таймаут в 2 минуты. Попробуйте:")
                println("   - задать более короткий вопрос")
                println("   - выбрать другую модель через /model")
                println("   - повторить попытку позже")
            } else {
                println("💡 Попробуйте выбрать другую модель через команду /model")
            }
        }
    }
}
