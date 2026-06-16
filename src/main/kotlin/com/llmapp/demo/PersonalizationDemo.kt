package com.llmapp.demo

import com.llmapp.agent.MemoryAwareAgent
import com.llmapp.api.ApiConfig
import com.llmapp.memory.ProjectConstraints
import com.llmapp.memory.ResponseStyle
import com.llmapp.memory.UserProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Демонстрация персонализации с LLM-анализом
 */
class PersonalizationDemo {
    suspend fun runPersonalizationDemo() {
        val apiKey = ApiConfig.getApiKey()
        val model = "openai/gpt-oss-20b:free"

        println("=".repeat(100))
        println("👤 ДЕМОНСТРАЦИЯ ПЕРСОНАЛИЗАЦИИ С LLM-АНАЛИЗОМ")
        println("=".repeat(100))
        println()
        println("📌 Схема работы:")
        println("   1. Создаются профили разных пользователей")
        println("   2. Задаются одинаковые вопросы")
        println("   3. Собираются ответы и метаданные")
        println("   4. LLM анализирует различия и делает выводы")
        println()

        // ========== ШАГ 1: СОЗДАНИЕ ПРОФИЛЕЙ ==========
        println("📌 ШАГ 1: Создание профилей пользователей")
        println("-".repeat(80))

        val profiles = createProfiles()
        println("✅ Создано ${profiles.size} профилей:")
        profiles.forEachIndexed { index, (name, profile) ->
            println("   ${index + 1}. $name - ${profile.experience}")
        }
        println()

        delay(1.seconds)

        // ========== ШАГ 2: СБОР ДАННЫХ ==========
        println("📌 ШАГ 2: Сбор ответов для анализа")
        println("-".repeat(80))

        val questions = listOf(
            "Как организовать хранение данных в приложении?",
            "Какую архитектуру вы посоветуете для моего проекта?",
            "Какие best practices для работы с корутинами?",
            "Как организовать работу с сетью?",
            "Напиши пример обработки ошибок в API клиенте"
        )

        val allResults = mutableListOf<ProfileTestResult>()

        for ((profileName, profile) in profiles) {
            println("\n👤 Тестируем: $profileName")
            println("   📝 Стек: ${profile.preferredTech.joinToString(", ")}")
            println("   🎯 Стиль: ${profile.preferredStyle.name.lowercase()}")

            val agent = MemoryAwareAgent(
                apiKey = apiKey,
                model = model,
                systemPrompt = "Ты полезный ассистент. Отвечай на русском языке."
            )
            agent.updateProfile(profile)

            // Применяем ограничения если есть
            val constraints = getConstraintsForProfile(profileName)
            constraints?.let { agent.updateConstraints(it) }

            val results = mutableListOf<AnswerRecord>()

            for ((qIndex, question) in questions.withIndex()) {
                try {
                    val startTime = System.currentTimeMillis()
                    val response = agent.processRequest(question)
                    val responseTime = System.currentTimeMillis() - startTime

                    // Анализируем ответ прямо здесь, без отдельного метода
                    val lowerContent = response.content.lowercase()
                    val techMentioned = profile.preferredTech.any { tech ->
                        lowerContent.contains(tech.lowercase())
                    }

                    // Проверяем соответствие стилю
                    val styleMatch = when (profile.preferredStyle) {
                        ResponseStyle.CONCISE -> response.content.length < 500
                        ResponseStyle.DETAILED -> response.content.length > 800
                        ResponseStyle.TECHNICAL -> {
                            response.content.contains("```") ||
                                    response.content.contains("class") ||
                                    response.content.contains("fun ") ||
                                    response.content.contains("interface")
                        }

                        ResponseStyle.BALANCED -> response.content.length in 400..1000
                    }

                    // Проверяем, отвечен ли вопрос
                    val questionAnswered = !response.content.contains("не знаю") &&
                            !response.content.contains("не могу") &&
                            !response.content.contains("извините") &&
                            response.content.length > 50

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
                            styleMatch = styleMatch,
                            questionAnswered = questionAnswered
                        )
                    )

                    println("   ✓ Вопрос ${qIndex + 1}/${questions.size}: ${question.take(40)}...")
                    delay(300.milliseconds)

                } catch (e: Exception) {
                    println("   ❌ Ошибка: ${e.message}")
                }
            }

            val totalTokens = results.sumOf { it.totalTokens }
            val avgLength =
                if (results.isNotEmpty()) results.map { it.answerLength }.average() else 0.0
            val avgResponseTime =
                if (results.isNotEmpty()) results.map { it.responseTimeMs }.average() else 0.0
            val techMatchRate = if (results.isNotEmpty()) {
                results.count { it.techMentioned }.toDouble() / results.size * 100
            } else 0.0

            allResults.add(
                ProfileTestResult(
                    profileName = profileName,
                    profile = profile,
                    answers = results,
                    totalTokens = totalTokens,
                    avgAnswerLength = avgLength,
                    avgResponseTime = avgResponseTime,
                    techMatchRate = techMatchRate
                )
            )

            delay(1.seconds)
        }

        println("\n✅ Сбор данных завершен!")
        println("   • Протестировано профилей: ${allResults.size}")
        println("   • Всего ответов: ${allResults.sumOf { it.answers.size }}")
        println()

        delay(2.seconds)

        // ========== ШАГ 3: LLM АНАЛИЗИРУЕТ ДАННЫЕ ==========
        println("=".repeat(100))
        println("📊 ШАГ 3: LLM анализирует собранные данные")
        println("=".repeat(100))
        println()

        val analysisPrompt = buildAnalysisPrompt(allResults, questions)

        println("📝 Отправляем данные на анализ...")
        println("-".repeat(60))

        val analysisAgent = MemoryAwareAgent(
            apiKey = apiKey,
            model = model,
            systemPrompt = """Ты эксперт по анализу данных и AI-агентов.
                Твоя задача - проанализировать результаты тестирования
                и сделать объективные выводы.
                Отвечай на русском языке, структурированно.
            """.trimIndent()
        )

        try {
            val analysis = analysisAgent.processRequest(analysisPrompt)

            println("\n" + "=".repeat(100))
            println("🧠 АНАЛИЗ ОТ LLM:")
            println("=".repeat(100))
            println()
            println(analysis.content)
            println()
            println("=".repeat(100))

        } catch (e: Exception) {
            println("❌ Ошибка при анализе: ${e.message}")
        }
    }

    private fun createProfiles(): List<Pair<String, UserProfile>> {
        return listOf(
            "Алексей (Android разработчик)" to UserProfile(
                name = "Алексей",
                experience = "Middle Android разработчик",
                preferredStyle = ResponseStyle.TECHNICAL,
                preferredTech = listOf(
                    "Kotlin",
                    "Jetpack Compose",
                    "Coroutines",
                    "Flow",
                    "Retrofit"
                ),
                commonGoals = listOf("Разработка мобильных приложений", "Изучение KMP"),
                customNotes = "Предпочитаю примеры кода. Важна производительность."
            ),
            "Екатерина (Fullstack разработчик)" to UserProfile(
                name = "Екатерина",
                experience = "Senior Fullstack разработчик",
                preferredStyle = ResponseStyle.DETAILED,
                preferredTech = listOf("TypeScript", "React", "Node.js", "Python", "PostgreSQL"),
                commonGoals = listOf("Архитектура микросервисов", "DevOps"),
                customNotes = "Нужны объяснения на высоком уровне."
            ),
            "Михаил (Начинающий разработчик)" to UserProfile(
                name = "Михаил",
                experience = "Junior разработчик, стаж 6 месяцев",
                preferredStyle = ResponseStyle.CONCISE,
                preferredTech = listOf("Python", "JavaScript", "HTML/CSS"),
                commonGoals = listOf("Изучение основ программирования"),
                customNotes = "Нужны простые объяснения, без сложной терминологии."
            ),
            "Сергей (Архитектор)" to UserProfile(
                name = "Сергей",
                experience = "Solutions Architect, 15 лет",
                preferredStyle = ResponseStyle.TECHNICAL,
                preferredTech = listOf("Kotlin", "KMP", "Compose Multiplatform", "Ktor"),
                commonGoals = listOf("Кроссплатформенные решения", "Агентные системы"),
                customNotes = "Глубокий технический уровень. Нужна архитектурная обоснованность."
            )
        )
    }

    private fun getConstraintsForProfile(profileName: String): ProjectConstraints? {
        return if (profileName.contains("Архитектор")) {
            ProjectConstraints(
                techStack = listOf("Kotlin", "KMP", "Compose Multiplatform", "Ktor"),
                forbiddenTech = listOf("Java", "Spring Boot", "RxJava"),
                architecture = "Clean Architecture + MVI",
                codingStandards = "Kotlin Coding Conventions",
                specialRules = "Все решения должны быть кроссплатформенными."
            )
        } else null
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
                Стиль соответствует: ${if (answer.styleMatch) "✅" else "❌"}
                Вопрос отвечен: ${if (answer.questionAnswered) "✅" else "❌"}
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
        
        1. Ключевые наблюдения:
           - Как влияет профиль пользователя на ответы?
           - Какие метрики лучше всего отражают персонализацию?
           - Есть ли корреляции между типом профиля и стилем ответов?
        
        2. Сравнительный анализ:
           - Сравни ответы для разных профилей на одинаковые вопросы
           - Какие профили дают наиболее персонализированные ответы?
           - Где персонализация работает лучше всего?
        
        3. Сильные стороны текущей реализации:
           - Что работает хорошо?
           - Какие аспекты персонализации наиболее эффективны?
        
        4. Слабые стороны и предложения по улучшению:
           - Что можно улучшить?
           - Какие дополнительные данные могли бы усилить персонализацию?
        
        5. Выводы и рекомендации:
           - Общая оценка персонализации
           - Рекомендации по улучшению
           - Где использовать такую систему?
        
        Формат ответа:
        - Структурированный, с заголовками
        - На русском языке
        - С конкретными примерами из данных
        - Объективный анализ, без общих фраз
        """.trimIndent()
    }
}

// ========== DATA CLASSES ==========

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

fun main() = runBlocking {
    try {
        val demo = PersonalizationDemo()
        demo.runPersonalizationDemo()
    } catch (e: Exception) {
        println("\n❌ Критическая ошибка: ${e.message}")
        e.printStackTrace()
    }
}
