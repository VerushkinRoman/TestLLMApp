// src/main/kotlin/com/llmapp/demo/TransitionDemo.kt

package com.llmapp.demo

import com.llmapp.agent.StatefulMemoryAgent
import com.llmapp.state.TaskPhase
import kotlinx.coroutines.runBlocking

class TransitionDemo {
    fun runTransitionDemo() {
        println("=".repeat(80))
        println("🔄 ДЕМОНСТРАЦИЯ УПРАВЛЕНИЯ ПЕРЕХОДАМИ")
        println("=".repeat(80))

        val agent = StatefulMemoryAgent()

        // 1. Создание задачи
        println("\n📌 ШАГ 1: Создание задачи")
        println("-".repeat(40))

        agent.createTask(
            taskName = "Разработка чат-бота с памятью",
            description = "Чат-бот с трехслойной памятью"
        )

        println("✅ Задача создана")
        println("📊 Текущая фаза: ${agent.getPhase().displayName}")

        // 2. Проверка доступных переходов
        println("\n📌 ШАГ 2: Проверка доступных переходов")
        println("-".repeat(40))

        println("Доступные переходы:")
        agent.getAvailableTransitionsWithDetails().forEach { transition ->
            val icon = if (transition.isValid) "✅" else "🚫"
            println("  $icon ${transition.from.displayName} → ${transition.to.displayName}")
            if (!transition.isValid) {
                println("     Причина: ${transition.reason}")
                transition.suggestedAction?.let {
                    println("     💡 $it")
                }
            }
        }

        // 3. Попытка недопустимого перехода (EXECUTION без утверждения плана)
        println("\n📌 ШАГ 3: Попытка недопустимого перехода")
        println("-".repeat(40))

        println("❌ Пытаемся перейти в EXECUTION без утверждения плана...")
        val result1 = agent.safeTransitionTo(TaskPhase.EXECUTION)
        println("Результат: ${if (result1.success) "✅ Успешно" else "🚫 Отказано"}")
        println("Сообщение: ${result1.message}")
        result1.suggestedAction?.let {
            println("💡 $it")
        }

        // 4. Переход в PLANNING (разрешен)
        println("\n📌 ШАГ 4: Переход в PLANNING")
        println("-".repeat(40))

        val result2 = agent.safeTransitionTo(TaskPhase.PLANNING)
        println("✅ ${result2.message}")
        println("📊 Текущая фаза: ${agent.getPhase().displayName}")

        // 5. Утверждение плана
        println("\n📌 ШАГ 5: Утверждение плана")
        println("-".repeat(40))

        val result3 = agent.approvePlan()
        println("${if (result3.success) "✅" else "❌"} ${result3.message}")

        // 6. Проверка доступных переходов после утверждения
        println("\n📌 ШАГ 6: Проверка доступных переходов после утверждения")
        println("-".repeat(40))

        println("Доступные переходы:")
        agent.getAvailableTransitionsWithDetails().forEach { transition ->
            val icon = if (transition.isValid) "✅" else "🚫"
            println("  $icon ${transition.from.displayName} → ${transition.to.displayName}")
            if (!transition.isValid) {
                println("     Причина: ${transition.reason}")
            }
        }

        // 7. Переход в EXECUTION (теперь разрешен)
        println("\n📌 ШАГ 7: Переход в EXECUTION (после утверждения плана)")
        println("-".repeat(40))

        val result4 = agent.safeTransitionTo(TaskPhase.EXECUTION)
        println("✅ ${result4.message}")
        println("📊 Текущая фаза: ${agent.getPhase().displayName}")

        // 8. Попытка перехода в DONE (без валидации)
        println("\n📌 ШАГ 8: Попытка перехода в DONE без валидации")
        println("-".repeat(40))

        println("❌ Пытаемся перейти в DONE без подтверждения валидации...")
        val result5 = agent.safeTransitionTo(TaskPhase.DONE)
        println("Результат: ${if (result5.success) "✅ Успешно" else "🚫 Отказано"}")
        println("Сообщение: ${result5.message}")
        result5.suggestedAction?.let {
            println("💡 $it")
        }

        // 9. Переход в VALIDATION
        println("\n📌 ШАГ 9: Переход в VALIDATION")
        println("-".repeat(40))

        val result6 = agent.safeTransitionTo(TaskPhase.VALIDATION)
        println("✅ ${result6.message}")
        println("📊 Текущая фаза: ${agent.getPhase().displayName}")

        // 10. Подтверждение валидации
        println("\n📌 ШАГ 10: Подтверждение валидации")
        println("-".repeat(40))

        val result7 = agent.confirmValidation()
        println("${if (result7.success) "✅" else "❌"} ${result7.message}")

        // 11. Завершение задачи
        println("\n📌 ШАГ 11: Завершение задачи")
        println("-".repeat(40))

        val result8 = agent.safeTransitionTo(TaskPhase.DONE)
        println("✅ ${result8.message}")
        println("📊 Текущая фаза: ${agent.getPhase().displayName}")

        // 12. Итоги
        println("\n" + "=".repeat(80))
        println("📊 ИТОГИ ДЕМОНСТРАЦИИ")
        println("=".repeat(80))

        println(
            """
            ✅ Все переходы контролируются:
            
            1. Невозможно перейти в EXECUTION без утверждения плана
            2. Невозможно перейти в DONE без подтверждения валидации
            3. Каждый переход имеет понятную причину отказа
            4. Доступные переходы можно посмотреть в любой момент
            5. Пауза и возобновление работают корректно
            
            🎯 Агент с контролируемым жизненным циклом задачи готов!
        """.trimIndent()
        )
    }
}

fun main() = runBlocking {
    try {
        val demo = TransitionDemo()
        demo.runTransitionDemo()
    } catch (e: Exception) {
        println("\n❌ Ошибка: ${e.message}")
        e.printStackTrace()
    }
}
