package com.llmapp.demo

import com.llmapp.agent.InvariantAwareAgent
import com.llmapp.invariants.InvariantManager
import com.llmapp.invariants.InvariantPresets
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

class InvariantDemo {
    suspend fun runInvariantDemo() {
        val model = "nvidia/nemotron-3-super-120b-a12b:free"

        println("=".repeat(100))
        println("🔒 ДЕМОНСТРАЦИЯ РАБОТЫ ИНВАРИАНТОВ")
        println("=".repeat(100))

        // Шаг 1: Настройка инвариантов через менеджер
        println("\n📌 ШАГ 1: Настройка инвариантов через InvariantManager")
        println("-".repeat(50))

        val invariantManager = InvariantManager()
        val invariantSet = InvariantPresets.getAndroidKMPInvariants()

        // Сохраняем через менеджер
        invariantManager.saveInvariantSet(invariantSet)
        println("✅ Набор инвариантов сохранен: ${invariantSet.name}")

        // Загружаем обратно для проверки
        val loadedSet = invariantManager.loadInvariantSet(invariantSet.name)
        if (loadedSet != null) {
            println("✅ Набор успешно загружен из хранилища")
            println("   Инвариантов: ${loadedSet.invariants.size}")
        }

        // Показываем все сохраненные наборы
        val allSets = invariantManager.getAllInvariantSets()
        println("📁 Все сохраненные наборы: ${allSets.map { it.name }}")

        println()
        invariantSet.invariants.forEach { invariant ->
            println("   • ${invariant.name}: ${invariant.description}")
            println("     Строгость: ${invariant.severity.name}")
        }

        // Шаг 2: Создание агента с инвариантами по имени
        println("\n📌 ШАГ 2: Создание агента с инвариантами по имени")
        println("-".repeat(50))

        val agent = InvariantAwareAgent(

            model = model,
            systemPrompt = "Ты опытный разработчик на Kotlin. Отвечай на русском языке.",
            invariantSetName = "Android/KMP Project"  // Загружаем по имени через менеджер
        )
        println("✅ Агент создан с проверкой инвариантов из менеджера")

        // Шаг 3: Корректный запрос (не нарушает инварианты)
        println("\n📌 ШАГ 3: Корректный запрос (инварианты соблюдены)")
        println("-".repeat(50))

        val correctRequest = """
            Как организовать хранение данных в KMP приложении с использованием Kotlin и SQLDelight?
            Нужно сделать кроссплатформенное решение.
        """.trimIndent()

        println("👤: $correctRequest")
        println("⏳ Ожидание ответа...")
        delay(1.seconds)

        val response1 = agent.processRequest(correctRequest)
        if (response1.violationsFound) {
            println("❌ Ответ содержит нарушения инвариантов:")
            response1.invariantResults.filter { !it.passed }.forEach { result ->
                println("   ${result.message}")
            }
        } else {
            println("✅ Инварианты соблюдены!")
            println("🤖: ${response1.content.take(300)}...")
        }

        // Шаг 4: Запрос, нарушающий инварианты
        println("\n📌 ШАГ 4: Запрос, нарушающий инварианты")
        println("-".repeat(50))

        val violatingRequest = """
            Давай сделаем простой Android проект на Java с использованием RxJava.
            Я хочу использовать SharedPreferences для хранения данных.
            Архитектуру сделаем MVP.
        """.trimIndent()

        println("👤: $violatingRequest")
        println("⏳ Ожидание ответа (ассистент должен заметить нарушения)...")
        delay(1.seconds)

        val response2 = agent.processRequest(violatingRequest)

        if (response2.violationsFound) {
            println("⚠️ АГЕНТ ОБНАРУЖИЛ НАРУШЕНИЯ ИНВАРИАНТОВ:")
            response2.invariantResults.filter { !it.passed }.forEach { result ->
                println("   • ${result.invariant.name}: ${result.invariant.description}")
                result.suggestions.forEach { suggestion ->
                    println("     💡 $suggestion")
                }
            }
            println("\n📄 Ответ агента:")
            println(response2.content)
        } else {
            println("🤖: ${response2.content.take(200)}...")
        }

        // Шаг 5: Исправленный запрос
        println("\n📌 ШАГ 5: Исправленный запрос (с учетом инвариантов)")
        println("-".repeat(50))

        val fixedRequest = """
            Как организовать хранение данных в KMP проекте с использованием Kotlin и SQLDelight?
            Нужно использовать MVI архитектуру и корутины.
            Данные должны храниться безопасно.
        """.trimIndent()

        println("👤: $fixedRequest")
        println("⏳ Ожидание ответа...")
        delay(1.seconds)

        val response3 = agent.processRequest(fixedRequest)

        if (response3.violationsFound) {
            println("❌ Ответ все еще содержит нарушения:")
            response3.invariantResults.filter { !it.passed }.forEach { result ->
                println("   ${result.message}")
            }
        } else {
            println("✅ Все инварианты соблюдены!")
            println("🤖: ${response3.content.take(300)}...")
        }

        // Шаг 6: Тест на бизнес-правило
        println("\n📌 ШАГ 6: Тест бизнес-правила (кроссплатформенность)")
        println("-".repeat(50))

        val businessRuleRequest = """
            Напиши код для хранения данных только для Android платформы.
            iOS не нужен.
        """.trimIndent()

        println("👤: $businessRuleRequest")
        println("⏳ Проверка бизнес-правила...")
        delay(1.seconds)

        val response4 = agent.processRequest(businessRuleRequest)

        if (response4.violationsFound) {
            val businessViolations = response4.invariantResults.filter {
                !it.passed && it.invariant.type == com.llmapp.invariants.InvariantType.BUSINESS_RULE
            }
            if (businessViolations.isNotEmpty()) {
                println("🚫 БИЗНЕС-ПРАВИЛО НАРУШЕНО:")
                businessViolations.forEach { result ->
                    println("   • ${result.message}")
                }
                println("\n📄 Ответ агента:")
                println(response4.content)
            }
        }

        // Шаг 7: Статистика
        println("\n📊 СТАТИСТИКА ТЕСТИРОВАНИЯ")
        println("=".repeat(50))

        val stats = agent.getTokenStats()
        println("• Запросов: ${stats.requestCount}")
        println("• Всего токенов: ${stats.totalTokens}")
        println("• Стоимость: ${stats.getFormattedCost()}")

        // Шаг 8: Выводы
        println("\n💡 ВЫВОДЫ ПО ДЕМОНСТРАЦИИ")
        println("=".repeat(50))
        println(
            """
            1️⃣ Инварианты позволяют контролировать поведение агента
            2️⃣ Агент явно учитывает инварианты в рассуждениях
            3️⃣ При нарушении инвариантов агент исправляет ответ
            4️⃣ Бизнес-правила защищают от нежелательных решений
            5️⃣ Агент объясняет причины отказа
            
            🎯 Инварианты - мощный инструмент контроля агентов!
        """.trimIndent()
        )
    }
}

fun main() = runBlocking {
    try {
        val demo = InvariantDemo()
        demo.runInvariantDemo()
    } catch (e: Exception) {
        println("\n❌ Ошибка: ${e.message}")
        e.printStackTrace()
    }
}
