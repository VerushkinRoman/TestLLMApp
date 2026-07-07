package com.llmapp.demo

import com.llmapp.agent.MemoryAwareAgent
import com.llmapp.memory.ProjectConstraints
import com.llmapp.memory.ResponseStyle
import com.llmapp.memory.TaskState
import com.llmapp.memory.UserProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

class MemoryDemo {

    suspend fun runMemoryDemonstration() {
        val model = "openai/gpt-oss-20b:free"

        println("=".repeat(100))
        println("🧠 ДЕМОНСТРАЦИЯ ТРЕХСЛОЙНОЙ МОДЕЛИ ПАМЯТИ")
        println("=".repeat(100))

        val agent = MemoryAwareAgent(

            model = model,
            systemPrompt = "Ты полезный ассистент. Отвечай на русском языке."
        )

        // Шаг 1: Настройка долговременной памяти (профиль)
        println("\n📌 ШАГ 1: Настройка долговременной памяти (профиль пользователя)")
        println("-".repeat(50))

        val profile = UserProfile(
            name = "Алексей",
            experience = "Middle Android разработчик",
            preferredStyle = ResponseStyle.TECHNICAL,
            preferredTech = listOf("Kotlin", "Compose", "KMP", "Ktor"),
            commonGoals = listOf("Изучить разработку агентов", "Улучшить архитектуру приложений"),
            customNotes = "Предпочитаю примеры кода и практические решения"
        )
        agent.updateProfile(profile)
        println("✅ Профиль сохранен в долговременную память")

        // Шаг 2: Настройка ограничений проекта
        println("\n📌 ШАГ 2: Настройка ограничений проекта")
        println("-".repeat(50))

        val constraints = ProjectConstraints(
            techStack = listOf("Kotlin", "KMP", "Compose Multiplatform"),
            forbiddenTech = listOf("Java", "XML (для новых UI)", "RxJava"),
            architecture = "MVI с Clean Architecture",
            codingStandards = "Kotlin Coding Conventions, Detekt",
            specialRules = "Все новые фичи должны иметь модульные тесты"
        )
        agent.updateConstraints(constraints)
        println("✅ Ограничения сохранены в долговременную память")

        delay(1.seconds)

        // Шаг 3: Начало задачи - рабочая память
        println("\n📌 ШАГ 3: Создание рабочей памяти (новая задача)")
        println("-".repeat(50))

        agent.startNewTask(
            taskName = "Разработка чат-бота с памятью",
            initialContext = mapOf(
                "требования" to "Чат-бот должен помнить контекст диалога",
                "ограничения" to "Использовать бесплатные модели KodikRouter"
            )
        )
        agent.updateTaskState(TaskState.PLANNING)
        println("✅ Задача создана, состояние: ${agent.getWorkingMemory().currentState.displayName}")

        delay(1.seconds)

        // Шаг 4: Диалог с использованием всех слоев памяти
        println("\n📌 ШАГ 4: Диалог с использованием всех слоев памяти")
        println("-".repeat(50))

        val questions = listOf(
            "Какую архитектуру ты предлагаешь для чат-бота?",
            "Напиши пример базового класса для управления историей сообщений",
            "Как организовать сохранение контекста между сессиями?",
            "Какие могут быть проблемы с большим контекстом?"
        )

        for ((index, question) in questions.withIndex()) {
            println("\n[${index + 1}] 👤: $question")

            val response = agent.processRequest(question)

            println("🤖: ${response.content.take(200)}...")
            println("📊 Использована память: " + buildString {
                if (response.memoryUsed.shortTermUsed) append("Краткосрочная ")
                if (response.memoryUsed.workingMemoryUsed) append("+ Рабочая ")
                if (response.memoryUsed.longTermUsed) append("+ Долговременная")
            })
            println("📊 Токены: ↑${response.promptTokens} ↓${response.completionTokens} Σ${response.totalTokens}")

            delay(1.seconds)
        }

        // Шаг 5: Сохранение решения в долговременную память
        println("\n📌 ШАГ 5: Сохранение решения в долговременную память")
        println("-".repeat(50))

        agent.saveDecisionToLongTerm(
            topic = "Архитектура чат-бота",
            decision = "Использовать трехслойную модель памяти: short-term (текущий диалог), working (данные задачи), long-term (профиль/ограничения)",
            context = "На основе анализа требований и ограничений проекта"
        )
        println("✅ Решение сохранено")

        // Шаг 6: Показ текущего состояния памяти
        printMemoryState(agent)

        // Шаг 7: Тест без долговременной памяти
        println("\n📌 ШАГ 6: Сравнение - ответ без долговременной памяти")
        println("-".repeat(50))

        agent.useLongTerm = false
        println("⚠️ Долговременная память ОТКЛЮЧЕНА")

        val testQuestion = "Какой стек технологий я предпочитаю?"
        println("👤: $testQuestion")

        val responseNoLongTerm = agent.processRequest(testQuestion)
        println("🤖: ${responseNoLongTerm.content}")

        // Возвращаем настройки
        agent.useLongTerm = true

        // Шаг 8: Тест без рабочей памяти
        println("\n📌 ШАГ 7: Сравнение - ответ без рабочей памяти")
        println("-".repeat(50))

        agent.useWorkingMemory = false
        println("⚠️ Рабочая память ОТКЛЮЧЕНА")

        println("👤: Как называется текущая задача?")
        val responseNoWorking = agent.processRequest("Как называется текущая задача?")
        println("🤖: ${responseNoWorking.content}")

        // Выводы
        printConclusions()
    }

    private fun printMemoryState(agent: MemoryAwareAgent) {
        println("\n" + "=".repeat(100))
        println("📊 ТЕКУЩЕЕ СОСТОЯНИЕ ПАМЯТИ")
        println("=".repeat(100))

        println("\n🧠 ДОЛГОВРЕМЕННАЯ ПАМЯТЬ:")
        val profile = agent.getUserProfile()
        println("   👤 Профиль: ${profile.name} (${profile.experience})")
        println("   📚 Технологии: ${profile.preferredTech.joinToString(", ")}")
        println("   🎯 Стиль: ${profile.preferredStyle.name.lowercase()}")

        val constraints = agent.getProjectConstraints()
        println("   🔧 Стек: ${constraints.techStack.joinToString(", ")}")
        println("   🚫 Запрещено: ${constraints.forbiddenTech.joinToString(", ")}")

        println("\n💼 РАБОЧАЯ ПАМЯТЬ:")
        val working = agent.getWorkingMemory()
        println("   📋 Задача: ${working.taskName}")
        println("   📍 Состояние: ${working.currentState.displayName}")
        println("   📝 Данные: ${working.contextData.keys.joinToString(", ")}")
        println("   💡 Решений: ${working.decisions.size}")

        println("\n💬 КРАТКОСРОЧНАЯ ПАМЯТЬ:")
        println("   (содержит последние сообщения диалога)")
    }

    private fun printConclusions() {
        println("\n" + "=".repeat(100))
        println("📊 ВЫВОДЫ ПО ТРЕХСЛОЙНОЙ МОДЕЛИ ПАМЯТИ")
        println("=".repeat(100))

        println(
            """
            
            | 1️⃣ КРАТКОСРОЧНАЯ ПАМЯТЬ (Short-term)
            |    ✅ Хранит: текущий диалог, последние сообщения
            |    ✅ Влияние: обеспечивает связность разговора
            |    ❌ Ограничения: теряется при очистке истории
            |
            | 2️⃣ РАБОЧАЯ ПАМЯТЬ (Working Memory)
            |    ✅ Хранит: текущую задачу, ее состояние, принятые решения
            |    ✅ Влияние: агент знает, на каком этапе находится задача
            |    ✅ Пример: "Мы сейчас на этапе планирования, сначала утвердим план"
            |    ❌ Ограничения: привязана к конкретной задаче
            |
            | 3️⃣ ДОЛГОВРЕМЕННАЯ ПАМЯТЬ (Long-term)
            |    ✅ Хранит: профиль пользователя, ограничения проекта, знания
            |    ✅ Влияние: персонализация ответов, соблюдение правил
            |    ✅ Пример: агент знает, что пользователь пишет на Kotlin, а не Python
            |    ❌ Ограничения: требует явного сохранения
            |
            | 💡 КЛЮЧЕВЫЕ ВЫВОДЫ:
            |
            | 1. Три слоя памяти решают разные задачи:
            |    - Short-term для связности диалога
            |    - Working для управления задачами
            |    - Long-term для персонализации
            |
            | 2. Без долговременной памяти агент "забывает" кто вы
            |    - Не знает ваш стек технологий
            |    - Не учитывает ограничения проекта
            |    - Ответы получаются обобщенными
            |
            | 3. Без рабочей памяти агент не понимает контекст задачи
            |    - Может перепрыгивать этапы
            |    - Не помнит принятые решения
            |
            | 4. Все три слоя вместе дают синергетический эффект:
            |    - Агент помнит, кто вы (long-term)
            |    - Знает, над чем работаете (working)
            |    - Поддерживает беседу (short-term)
            |
        """.trimMargin()
        )
    }
}

fun main() = runBlocking {
    try {
        val demo = MemoryDemo()
        demo.runMemoryDemonstration()
    } catch (e: Exception) {
        println("\n❌ Ошибка: ${e.message}")
        e.printStackTrace()
    }
}
