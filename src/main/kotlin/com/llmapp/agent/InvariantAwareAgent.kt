package com.llmapp.agent

import com.llmapp.api.OpenRouterClient
import com.llmapp.chat.ChatHistory
import com.llmapp.invariants.Invariant
import com.llmapp.invariants.InvariantCheckResult
import com.llmapp.invariants.InvariantManager
import com.llmapp.invariants.InvariantPresets
import com.llmapp.invariants.InvariantSet
import com.llmapp.model.OpenRouterRequest
import com.llmapp.model.ResponseControl
import com.llmapp.model.TokenUsage

data class InvariantAwareResponse(
    val content: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val finishReason: String?,
    val responseTimeMs: Long,
    val invariantResults: List<InvariantCheckResult> = emptyList(),
    val violationsFound: Boolean = false,
    val originalContent: String = "",
    val retryCount: Int = 0
)

class InvariantAwareAgent(
    apiKey: String,
    private var model: String,
    systemPrompt: String,
    private var responseControl: ResponseControl = ResponseControl(),
    private val invariantSetName: String? = null,
    private val invariantSet: InvariantSet? = null,
    maxHistorySize: Int = 50,
    val maxRetries: Int = 3,
    private val onAgentMessage: (suspend (String, String?) -> Unit)? = null
) {
    private val apiClient = OpenRouterClient(apiKey)

    // Используем systemPrompt напрямую, но добавляем правила
    private val history = ChatHistory(
        systemPrompt = buildString {
            append(systemPrompt)
            append("\n\n")
            append("ВАЖНЫЕ ПРАВИЛА:\n")
            append("1. Отвечай КРАТКО и ПО ДЕЛУ. Не расписывай лишнего.\n")
            append("2. Используй русский язык для объяснений.\n")
            append("3. Код и технические термины могут быть на английском.\n")
            append("4. Если код длинный - давай его частями.\n")
            append("5. Соблюдай инварианты - это обязательные правила!\n")
        },
        maxHistorySize = maxHistorySize
    )
    private val tokenTracker = TokenTracker()
    private var requestCounter = 0

    // Более строгие параметры по умолчанию
    private val defaultResponseControl = ResponseControl(
        temperature = 0.1,
        maxTokens = 500,
        enabled = true
    )

    // Используем InvariantManager для загрузки инвариантов
    private val invariantManager = InvariantManager()
    private val activeInvariantSet: InvariantSet? by lazy {
        when {
            invariantSet != null -> invariantSet
            invariantSetName != null -> {
                val loaded = invariantManager.loadInvariantSet(invariantSetName)
                if (loaded == null) {
                    println("⚠️ Набор инвариантов '$invariantSetName' не найден, создаю из пресета...")
                    val newSet = when (invariantSetName.lowercase()) {
                        "android" -> InvariantPresets.getAndroidKMPInvariants()
                        "web" -> InvariantPresets.getWebInvariants()
                        else -> InvariantPresets.getBaseInvariants()
                    }
                    invariantManager.saveInvariantSet(newSet)
                    newSet
                } else {
                    loaded
                }
            }

            else -> null
        }
    }

    init {
        tokenTracker.updateModel(model)
        // Если responseControl не задан, используем дефолтный
        if (!responseControl.enabled) {
            responseControl = defaultResponseControl
        }

        activeInvariantSet?.let { set ->
            println("✅ Загружен набор инвариантов: ${set.name}")
            println("   • Инвариантов: ${set.invariants.size}")
            println("   • Температура: ${responseControl.temperature ?: 0.1}")
            set.invariants.forEach { invariant ->
                println("   • ${invariant.name}: ${invariant.description}")
            }
        }
    }

    suspend fun processRequest(userInput: String): InvariantAwareResponse {
        return processRequestWithRetry(userInput, 0)
    }

    private suspend fun processRequestWithRetry(
        userInput: String,
        retryCount: Int
    ): InvariantAwareResponse {
        try {
            val enhancedPrompt = enhancePrompt(userInput)
            history.addUserMessage(enhancedPrompt)

            // Отправляем сообщение о начале обработки
            onAgentMessage?.invoke("🤔 Анализирую запрос...", "⏳ ОБРАБОТКА")

            val (response, responseTime) = sendToLLM()

            if (response.error != null) {
                throw Exception("API Error: ${response.error.message}")
            }

            val answer = response.choices?.firstOrNull()?.message?.content
                ?: throw Exception("Empty API response")

            // Проверяем инварианты
            val currentInvariantSet = activeInvariantSet
            val invariantResults = currentInvariantSet?.check(answer) ?: emptyList()
            val violations = invariantResults.filter { !it.passed }

            // Если есть нарушения
            if (violations.isNotEmpty()) {
                val errorViolations =
                    violations.filter { it.invariant.severity == Invariant.Severity.ERROR }

                // Показываем, что ответ нарушает инварианты (только если есть ERROR)
                if (errorViolations.isNotEmpty()) {
                    onAgentMessage?.invoke(
                        buildString {
                            append("⚠️ МОЙ ОТВЕТ НАРУШАЕТ ИНВАРИАНТЫ!\n\n")
                            append("Нарушения (${errorViolations.size}):\n")
                            errorViolations.forEach { result ->
                                append("🚫 ${result.invariant.name}: ${result.invariant.description}\n")
                                if (result.suggestions.isNotEmpty()) {
                                    append("   💡 ${result.suggestions.joinToString("; ")}\n")
                                }
                            }
                        },
                        "🚫 НАРУШЕНИЯ"
                    )
                }

                // Если это последняя попытка
                if (retryCount >= maxRetries) {
                    onAgentMessage?.invoke(
                        "❌ Я не смог исправить ответ за $maxRetries попыток. Попробуйте изменить запрос.",
                        "❌ ОШИБКА"
                    )
                    val violationMessage = buildViolationMessage(violations, answer)
                    return InvariantAwareResponse(
                        content = violationMessage,
                        promptTokens = response.usage?.promptTokens,
                        completionTokens = response.usage?.completionTokens,
                        totalTokens = response.usage?.totalTokens,
                        finishReason = response.choices.firstOrNull()?.finishReason,
                        responseTimeMs = responseTime,
                        invariantResults = invariantResults,
                        violationsFound = true,
                        originalContent = answer,
                        retryCount = retryCount
                    )
                }

                // Если только WARNING - пропускаем
                if (errorViolations.isEmpty()) {
                    onAgentMessage?.invoke(
                        "ℹ️ Только предупреждения (WARNING), пропускаю...",
                        "ℹ️ ПРОПУСКАЮ"
                    )
                    history.addAssistantMessage(answer)
                    requestCounter++
                    return InvariantAwareResponse(
                        content = answer,
                        promptTokens = response.usage?.promptTokens,
                        completionTokens = response.usage?.completionTokens,
                        totalTokens = response.usage?.totalTokens,
                        finishReason = response.choices.firstOrNull()?.finishReason,
                        responseTimeMs = responseTime,
                        invariantResults = invariantResults,
                        violationsFound = false,
                        originalContent = answer,
                        retryCount = retryCount
                    )
                }

                // Пробуем исправить
                onAgentMessage?.invoke(
                    "🔄 Пытаюсь исправить ответ (попытка ${retryCount + 1}/$maxRetries)...\n" +
                            "Исправляю нарушения: ${errorViolations.joinToString { it.invariant.name }}",
                    "🔄 ИСПРАВЛЕНИЕ"
                )

                // Откатываем последнее сообщение
                history.removeLastMessage() // ассистент
                history.removeLastMessage() // пользователь

                // Отправляем запрос на исправление
                val correctionPrompt = buildStrictCorrectionPrompt(userInput, errorViolations)
                history.addUserMessage(correctionPrompt)

                return processRequestWithRetry(correctionPrompt, retryCount + 1)
            }

            // Все хорошо
            onAgentMessage?.invoke(
                "✅ Все инварианты соблюдены! Ответ готов.",
                "✅ УСПЕШНО"
            )
            history.addAssistantMessage(answer)
            requestCounter++

            response.usage?.let { usage ->
                val tokenUsage = TokenUsage(
                    promptTokens = usage.promptTokens ?: 0,
                    completionTokens = usage.completionTokens ?: 0,
                    totalTokens = usage.totalTokens ?: 0
                )
                tokenTracker.trackRequest(tokenUsage, requestCounter)
            }

            return InvariantAwareResponse(
                content = answer,
                promptTokens = response.usage?.promptTokens,
                completionTokens = response.usage?.completionTokens,
                totalTokens = response.usage?.totalTokens,
                finishReason = response.choices.firstOrNull()?.finishReason,
                responseTimeMs = responseTime,
                invariantResults = invariantResults,
                violationsFound = false,
                originalContent = answer,
                retryCount = retryCount
            )

        } catch (e: Exception) {
            onAgentMessage?.invoke("❌ Ошибка: ${e.message}", "❌ ОШИБКА")
            println("❌ Ошибка в InvariantAwareAgent: ${e.message}")
            throw e
        }
    }

    private fun buildStrictCorrectionPrompt(
        userInput: String,
        violations: List<InvariantCheckResult>
    ): String {
        return buildString {
            append("⚠️ ИСПРАВЬ ОТВЕТ!\n\n")
            append("Твой предыдущий ответ нарушил обязательные правила проекта.\n\n")

            violations.forEach { result ->
                append("❌ ${result.invariant.name}: ${result.invariant.description}\n")
                if (result.suggestions.isNotEmpty()) {
                    append("   💡 ${result.suggestions.joinToString("\n   💡 ")}\n")
                }
            }

            append("\n📌 Оригинальный запрос:\n")
            append(userInput)
            append("\n\n")

            append("🔴 ТРЕБОВАНИЯ К ИСПРАВЛЕННОМУ ОТВЕТУ:\n")
            append("1. Будь КРАТКИМ (максимум 3-5 предложений + пример кода)\n")
            append("2. Используй ТОЛЬКО разрешенные технологии:\n")
            append("3. НЕ используй не разрешенные\n")
            append("4. Соблюдай ВСЕ инварианты\n")
            append("5. Дай пример кода, но кратко\n\n")

            append("Исправленный ответ:\n")
        }
    }

    private fun buildViolationMessage(
        violations: List<InvariantCheckResult>,
        originalContent: String
    ): String {
        return buildString {
            append("⚠️ **Не удалось найти решение, удовлетворяющее всем инвариантам**\n\n")
            append("Нарушенные правила:\n")
            violations.forEach { result ->
                append("• ${result.invariant.name}: ${result.invariant.description}\n")
                if (result.suggestions.isNotEmpty()) {
                    append("  💡 ${result.suggestions.joinToString("\n  💡 ")}\n")
                }
            }
            append("\n---\n\n")
            append("**Предложенный ответ:**\n")
            append(originalContent)
            append("\n\n---\n")
            append("🤔 **Попробуйте уточнить запрос** или ослабить некоторые инварианты.")
        }
    }

    private fun enhancePrompt(userInput: String): String {
        val currentInvariantSet = activeInvariantSet
        val invariantBlock = if (currentInvariantSet != null) {
            buildString {
                append("\n\n### 🔒 ИНВАРИАНТЫ (ОБЯЗАТЕЛЬНЫЕ ПРАВИЛА) ###\n")
                append("Эти правила НЕЛЬЗЯ НАРУШАТЬ!\n\n")

                // Группируем по типу и серьезности
                val errors =
                    currentInvariantSet.invariants.filter { it.severity == Invariant.Severity.ERROR }
                val warnings =
                    currentInvariantSet.invariants.filter { it.severity == Invariant.Severity.WARNING }

                if (errors.isNotEmpty()) {
                    append("🚫 ЗАПРЕЩЕНО (ERROR):\n")
                    errors.forEach { invariant ->
                        append("   • ${invariant.name}: ${invariant.description}\n")
                        if (invariant.allowedValues.isNotEmpty()) {
                            append("     ✅ Разрешено: ${invariant.allowedValues.joinToString(", ")}\n")
                        }
                        if (invariant.forbiddenValues.isNotEmpty()) {
                            append("     ❌ Запрещено: ${invariant.forbiddenValues.joinToString(", ")}\n")
                        }
                    }
                    append("\n")
                }

                if (warnings.isNotEmpty()) {
                    append("⚠️ РЕКОМЕНДАЦИИ (WARNING):\n")
                    warnings.forEach { invariant ->
                        append("   • ${invariant.name}: ${invariant.description}\n")
                    }
                    append("\n")
                }

                append("### КОНЕЦ ИНВАРИАНТОВ ###\n")
                append("\n💡 ОТВЕЧАЙ КРАТКО (3-5 предложений). Не расписывай!\n")
            }
        } else ""

        val formatBlock =
            if (responseControl.enabled && responseControl.formatDescription != null) {
                "\n\nФормат ответа: ${responseControl.formatDescription}"
            } else ""

        return if (invariantBlock.isNotEmpty()) {
            "---\n${invariantBlock}\n---\n\n${userInput}${formatBlock}"
        } else {
            userInput + formatBlock
        }
    }

    private suspend fun sendToLLM(): Pair<com.llmapp.model.OpenRouterResponse, Long> {
        val request = OpenRouterRequest(
            model = model,
            messages = history.getMessages(),
            maxTokens = if (responseControl.enabled) responseControl.maxTokens else null,
            stop = if (responseControl.enabled) responseControl.stopSequences else null,
            temperature = if (responseControl.enabled) responseControl.temperature else null,
            skipContextOptimization = true
        )

        val startTime = System.currentTimeMillis()
        val response = apiClient.sendRequest(request)
        val endTime = System.currentTimeMillis()

        return Pair(response, endTime - startTime)
    }

    // ============================================================
    // ПУБЛИЧНЫЕ МЕТОДЫ
    // ============================================================

    fun changeModel(newModel: String) {
        model = newModel
        tokenTracker.updateModel(newModel)
    }

    fun clearHistory() {
        history.clear()
        tokenTracker.reset()
        requestCounter = 0
    }

    fun getTokenStats() = tokenTracker.stats.value
}
