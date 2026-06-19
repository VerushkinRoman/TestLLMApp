package com.llmapp.demo.manager

import com.llmapp.agent.MemoryAwareAgent
import com.llmapp.api.ApiConfig
import com.llmapp.memory.ResponseStyle
import com.llmapp.memory.UserProfile
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class AnswerRecord(
    val question: String,
    val answer: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val responseTimeMs: Long,
    val answerLength: Int,
    val techMentioned: Boolean,
    val styleMatch: Boolean,
    val questionAnswered: Boolean
)

data class ProfileTestResult(
    val profileName: String,
    val profile: UserProfile,
    val answers: List<AnswerRecord>,
    val totalTokens: Int,
    val avgAnswerLength: Double,
    val avgResponseTime: Double,
    val techMatchRate: Double
)

/**
 * Демонстрация персонализации с LLM-анализом
 */
class PersonalizationDemoRunner(
    onMessageAdded: (ChatMessageUI) -> Unit,
    onTypingStateChanged: (Boolean) -> Unit
) : BaseDemoRunner(onMessageAdded, onTypingStateChanged) {

    override suspend fun run() {
        val apiKey = ApiConfig.getApiKey()
        val model = "nvidia/nemotron-3-super-120b-a12b:free"

        addMessage(
            role = "assistant",
            content = """
            👤 ЗАПУСК ДЕМОНСТРАЦИИ ПЕРСОНАЛИЗАЦИИ
            
            Схема работы:
            1. Создаются профили разных пользователей
            2. Задаются одинаковые вопросы
            3. Собираются ответы и метаданные
            4. LLM анализирует различия и делает выводы
            
            Начинаем сбор данных...
            """.trimIndent(),
            metadata = "ПЕРСОНАЛИЗАЦИЯ"
        )
        delay(2.seconds)

        val profiles = listOf(
            "Алексей (Android)" to UserProfile(
                name = "Алексей",
                experience = "Middle Android разработчик",
                preferredStyle = ResponseStyle.TECHNICAL,
                preferredTech = listOf("Kotlin", "Jetpack Compose", "Coroutines"),
                customNotes = "Предпочитаю примеры кода"
            ),
            "Екатерина (Fullstack)" to UserProfile(
                name = "Екатерина",
                experience = "Senior Fullstack разработчик",
                preferredStyle = ResponseStyle.DETAILED,
                preferredTech = listOf("TypeScript", "React", "Node.js"),
                customNotes = "Нужны объяснения на высоком уровне"
            ),
            "Михаил (Junior)" to UserProfile(
                name = "Михаил",
                experience = "Junior разработчик",
                preferredStyle = ResponseStyle.CONCISE,
                preferredTech = listOf("Python", "JavaScript"),
                customNotes = "Нужны простые объяснения"
            )
        )

        val questions = listOf(
            "Как организовать хранение данных в приложении?",
            "Какую архитектуру вы посоветуете?",
            "Как организовать работу с сетью?"
        )

        val allResults = mutableListOf<ProfileTestResult>()

        for ((profileName, profile) in profiles) {
            val agent = MemoryAwareAgent(
                apiKey = apiKey,
                model = model,
                systemPrompt = "Ты полезный ассистент. Отвечай на русском языке.",
                persistToDisk = false
            )
            agent.updateProfile(profile)

            addMessage(
                role = "assistant",
                content = """
            ─────────────────────────────────────────────────────────────
            👤 ТЕСТ ПРОФИЛЯ: $profileName
            📝 Стек: ${profile.preferredTech.joinToString(", ")}
            🎯 Стиль: ${profile.preferredStyle.name.lowercase()}
            ─────────────────────────────────────────────────────────────
            """.trimIndent(),
                metadata = "Новый профиль"
            )
            delayMedium()

            val results = mutableListOf<AnswerRecord>()

            for ((qIndex, question) in questions.withIndex()) {
                addMessage(role = "user", content = question)
                delayMedium()
                onTypingStateChanged(true)
                delayMedium()

                try {
                    val startTime = System.currentTimeMillis()
                    val response = agent.processRequest(question)
                    onTypingStateChanged(false)
                    val responseTime = System.currentTimeMillis() - startTime

                    val lowerContent = response.content.lowercase()
                    val techMentioned = profile.preferredTech.any { tech ->
                        lowerContent.contains(tech.lowercase())
                    }

                    results.add(
                        AnswerRecord(
                            question = question,
                            answer = response.content,
                            promptTokens = response.promptTokens ?: 0,
                            completionTokens = response.completionTokens ?: 0,
                            totalTokens = response.totalTokens ?: 0,
                            responseTimeMs = responseTime,
                            answerLength = response.content.length,
                            techMentioned = techMentioned,
                            styleMatch = true,
                            questionAnswered = response.content.length > 50
                        )
                    )

                    val metadata = buildString {
                        append("📊 [${qIndex + 1}/${questions.size}]")
                        append(" Токены: ↑${response.promptTokens}/↓${response.completionTokens}/Σ${response.totalTokens}")
                        if (techMentioned) append(" | ✅ Учтены технологии")
                    }

                    addMessage(
                        role = "assistant",
                        content = response.content,
                        metadata = metadata,
                        promptTokens = response.promptTokens,
                        completionTokens = response.completionTokens,
                        totalTokens = response.totalTokens,
                        responseTimeMs = response.responseTimeMs
                    )

                } catch (e: Exception) {
                    onTypingStateChanged(false)
                    addMessage(
                        role = "assistant",
                        content = "❌ Ошибка: ${e.message}",
                        metadata = "Ошибка"
                    )
                }
                delay(300.milliseconds)
            }

            val totalTokens = results.sumOf { it.totalTokens }
            val avgLength = results.map { it.answerLength }.average()
            val techMatchRate = results.count { it.techMentioned }.toDouble() / results.size * 100

            allResults.add(
                ProfileTestResult(
                    profileName = profileName,
                    profile = profile,
                    answers = results,
                    totalTokens = totalTokens,
                    avgAnswerLength = avgLength,
                    avgResponseTime = results.map { it.responseTimeMs }.average(),
                    techMatchRate = techMatchRate
                )
            )

            addMessage(
                role = "assistant",
                content = "─".repeat(60),
                metadata = "Разделитель"
            )
            delayMedium()
        }

        // ========== LLM АНАЛИЗ ==========
        val analysisPrompt = buildAnalysisPrompt(allResults, questions)

        addMessage(
            role = "assistant",
            content = "📝 Отправляем данные на анализ...",
            metadata = "Анализ"
        )
        delayMedium()

        val analysisAgent = MemoryAwareAgent(
            apiKey = apiKey,
            model = model,
            systemPrompt = """Ты эксперт по анализу данных и AI-агентов.
                Твоя задача - проанализировать результаты тестирования
                и сделать объективные выводы.
                Отвечай на русском языке, структурированно.
            """.trimIndent()
        )

        onTypingStateChanged(true)
        delayMedium()

        try {
            val analysis = analysisAgent.processRequest(analysisPrompt)
            onTypingStateChanged(false)

            addMessage(
                role = "assistant",
                content = "🧠 АНАЛИЗ ОТ LLM:\n\n${analysis.content}",
                metadata = "Итоговый анализ",
                promptTokens = analysis.promptTokens,
                completionTokens = analysis.completionTokens,
                totalTokens = analysis.totalTokens,
                responseTimeMs = analysis.responseTimeMs
            )

            // Финальные выводы
            val conclusionsPrompt = """
            На основе проведенного анализа персонализации, сделай краткие выводы:
            1. Что работает хорошо?
            2. Что можно улучшить?
            3. Где применять такую систему?
            
            Ответ должен быть кратким (3-5 предложений).
            """

            val conclusions = analysisAgent.processRequest(conclusionsPrompt)
            addMessage(
                role = "assistant",
                content = "💡 КРАТКИЕ ВЫВОДЫ:\n\n${conclusions.content}",
                metadata = "Финальные выводы",
                promptTokens = conclusions.promptTokens,
                completionTokens = conclusions.completionTokens,
                totalTokens = conclusions.totalTokens,
                responseTimeMs = conclusions.responseTimeMs
            )

        } catch (e: Exception) {
            onTypingStateChanged(false)
            addMessage(
                role = "assistant",
                content = "❌ Ошибка при анализе: ${e.message}",
                metadata = "Ошибка"
            )
        }
    }

    private fun buildAnalysisPrompt(
        results: List<ProfileTestResult>,
        questions: List<String>
    ): String {
        return """
        📊 ДАННЫЕ ДЛЯ АНАЛИЗА ПЕРСОНАЛИЗАЦИИ АГЕНТА
        
        === ОБЩАЯ ИНФОРМАЦИЯ ===
        • Количество протестированных профилей: ${results.size}
        • Количество вопросов: ${questions.size}
        • Всего ответов: ${results.sumOf { it.answers.size }}
        
        === ВОПРОСЫ ===
        ${questions.joinToString("\n") { "  ${questions.indexOf(it) + 1}. $it" }}
        
        === РЕЗУЛЬТАТЫ ПО ПРОФИЛЯМ ===
        
        ${
            results.joinToString("\n\n") { result ->
                """
            ПРОФИЛЬ: ${result.profileName}
            • Опыт: ${result.profile.experience}
            • Технологии: ${result.profile.preferredTech.joinToString(", ")}
            • Стиль: ${result.profile.preferredStyle.name.lowercase()}
            • Заметки: ${result.profile.customNotes}
            
            МЕТРИКИ:
            • Всего токенов: ${result.totalTokens}
            • Средняя длина ответа: ${"%.0f".format(result.avgAnswerLength)} символов
            • Среднее время ответа: ${"%.0f".format(result.avgResponseTime)} мс
            • Совпадение с технологиями профиля: ${"%.1f".format(result.techMatchRate)}%
            
            ОТВЕТЫ:
            ${
                    result.answers.joinToString("\n") { answer ->
                        """
                Вопрос: ${answer.question}
                Длина ответа: ${answer.answerLength} символов
                Токены: ↑${answer.promptTokens}/↓${answer.completionTokens}/Σ${answer.totalTokens}
                Технологии упомянуты: ${if (answer.techMentioned) "✅" else "❌"}
                ${"─".repeat(40)}
                Фрагмент ответа:
                ${answer.answer.take(300)}${if (answer.answer.length > 300) "..." else ""}
                """
                    }
                }
            """
            }
        }
        
        === ЗАДАНИЕ ДЛЯ АНАЛИЗА ===
        
        Проанализируй предоставленные данные и сделай структурированные выводы.
        Включи в анализ:
        
        1. Ключевые наблюдения
        2. Сравнительный анализ
        3. Сильные стороны текущей реализации
        4. Слабые стороны и предложения по улучшению
        5. Выводы и рекомендации
        """.trimIndent()
    }
}
