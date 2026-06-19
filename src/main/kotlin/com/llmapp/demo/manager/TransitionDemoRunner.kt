package com.llmapp.demo.manager

import com.llmapp.agent.StatefulMemoryAgent
import com.llmapp.state.TaskPhase
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

class TransitionDemoRunner(
    onMessageAdded: (ChatMessageUI) -> Unit,
    onTypingStateChanged: (Boolean) -> Unit,
    private val statefulAgent: StatefulMemoryAgent,
    private val onTaskStateUpdated: (() -> Unit)? = null
) : BaseDemoRunner(onMessageAdded, onTypingStateChanged) {

    override suspend fun run() {
        addMessage(
            role = "assistant",
            content = """
            ${"=".repeat(80)}
            🔄 ДЕМОНСТРАЦИЯ УПРАВЛЕНИЯ ПЕРЕХОДАМИ
            ${"=".repeat(80)}
            
            Эта демонстрация покажет, как агент управляет жизненным циклом задачи:
            
            1️⃣ Создание задачи
            2️⃣ Проверка доступных переходов
            3️⃣ Попытка недопустимого перехода (отказ)
            4️⃣ Разрешенный переход в PLANNING
            5️⃣ Утверждение плана
            6️⃣ Переход в EXECUTION
            7️⃣ Попытка перехода в DONE без валидации (отказ)
            8️⃣ Переход в VALIDATION
            9️⃣ Подтверждение валидации
            🔟 Завершение задачи
            
            Наблюдайте за панелью состояния задачи!
            """.trimIndent(),
            metadata = "🔄 ДЕМОНСТРАЦИЯ ПЕРЕХОДОВ"
        )
        delay(6.seconds)
        checkCancelled()

        addMessage(
            role = "assistant",
            content = "📌 ШАГ 1/10: Создание задачи",
            metadata = "📌 ШАГ 1/10"
        )
        onTypingStateChanged(true)
        delay(2.seconds)
        checkCancelled()
        onTypingStateChanged(false)

        statefulAgent.createTask(
            taskName = "Разработка чат-бота с памятью",
            description = "Чат-бот с трехслойной памятью и управлением переходами"
        )
        onTaskStateUpdated?.invoke()

        addMessage(
            role = "assistant",
            content = """
            ✅ Задача создана!
            
            📋 Название: Разработка чат-бота с памятью
            📍 Текущая фаза: ${statefulAgent.getPhase().displayName}
            🎯 Ожидается: ${statefulAgent.getExpectedAction()}
            """.trimIndent(),
            metadata = "✅ ЗАДАЧА СОЗДАНА"
        )
        delay(4.seconds)
        checkCancelled()

        addMessage(
            role = "assistant",
            content = "📌 ШАГ 2/10: Проверка доступных переходов",
            metadata = "📌 ШАГ 2/10"
        )
        delay(2.seconds)
        checkCancelled()

        val transitions = statefulAgent.getAvailableTransitionsWithDetails()
        val transitionsText = buildString {
            appendLine("📊 ДОСТУПНЫЕ ПЕРЕХОДЫ:")
            appendLine()
            transitions.forEach { transition ->
                val icon = if (transition.isValid) "✅" else "🚫"
                appendLine("$icon ${transition.from.displayName} → ${transition.to.displayName}")
                if (!transition.isValid) {
                    appendLine("   Причина: ${transition.reason}")
                    transition.suggestedAction?.let {
                        appendLine("   💡 $it")
                    }
                }
            }
        }

        addMessage(
            role = "assistant",
            content = transitionsText,
            metadata = "📊 ДОСТУПНЫЕ ПЕРЕХОДЫ"
        )
        delay(6.seconds)
        checkCancelled()

        addMessage(
            role = "assistant",
            content = "📌 ШАГ 3/10: Попытка недопустимого перехода",
            metadata = "📌 ШАГ 3/10"
        )
        delay(2.seconds)
        checkCancelled()

        addMessage(
            role = "assistant",
            content = "❌ Пытаемся перейти в EXECUTION без утверждения плана...",
            metadata = "⏳ ПОПЫТКА ПЕРЕХОДА"
        )
        onTypingStateChanged(true)
        delay(4.seconds)
        checkCancelled()
        onTypingStateChanged(false)

        val result1 = statefulAgent.safeTransitionTo(TaskPhase.EXECUTION)
        onTaskStateUpdated?.invoke()

        addMessage(
            role = "assistant",
            content = """
            🚫 ПЕРЕХОД ОТКАЗАН!
            
            Результат: ${if (result1.success) "✅ Успешно" else "🚫 Отказано"}
            Сообщение: ${result1.message}
            ${result1.suggestedAction?.let { "💡 $it" } ?: ""}
            """.trimIndent(),
            metadata = "🚫 ОТКАЗАНО"
        )
        delay(4.seconds)
        checkCancelled()

        addMessage(
            role = "assistant",
            content = "📌 ШАГ 4/10: Переход в PLANNING (разрешен)",
            metadata = "📌 ШАГ 4/10"
        )
        delay(2.seconds)
        checkCancelled()

        onTypingStateChanged(true)
        delay(2.seconds)
        checkCancelled()
        onTypingStateChanged(false)

        val result2 = statefulAgent.safeTransitionTo(TaskPhase.PLANNING)
        onTaskStateUpdated?.invoke()

        addMessage(
            role = "assistant",
            content = """
            ✅ ${result2.message}
            
            📊 Текущая фаза: ${statefulAgent.getPhase().displayName}
            🎯 Ожидается: ${statefulAgent.getExpectedAction()}
            """.trimIndent(),
            metadata = "✅ ПЕРЕХОД В PLANNING"
        )
        delay(4.seconds)
        checkCancelled()

        addMessage(
            role = "assistant",
            content = "📌 ШАГ 5/10: Утверждение плана",
            metadata = "📌 ШАГ 5/10"
        )
        delay(2.seconds)
        checkCancelled()

        onTypingStateChanged(true)
        delay(2.seconds)
        checkCancelled()
        onTypingStateChanged(false)

        val result3 = statefulAgent.approvePlan()
        onTaskStateUpdated?.invoke()

        addMessage(
            role = "assistant",
            content = """
            ${if (result3.success) "✅" else "❌"} ${result3.message}
            
            📊 Текущая фаза: ${statefulAgent.getPhase().displayName}
            """.trimIndent(),
            metadata = if (result3.success) "✅ ПЛАН УТВЕРЖДЕН" else "❌ ОШИБКА"
        )
        delay(4.seconds)
        checkCancelled()

        addMessage(
            role = "assistant",
            content = "📌 ШАГ 6/10: Проверка доступных переходов после утверждения",
            metadata = "📌 ШАГ 6/10"
        )
        delay(2.seconds)
        checkCancelled()

        val transitionsAfter = statefulAgent.getAvailableTransitionsWithDetails()
        val transitionsAfterText = buildString {
            appendLine("📊 ДОСТУПНЫЕ ПЕРЕХОДЫ ПОСЛЕ УТВЕРЖДЕНИЯ ПЛАНА:")
            appendLine()
            transitionsAfter.forEach { transition ->
                val icon = if (transition.isValid) "✅" else "🚫"
                appendLine("$icon ${transition.from.displayName} → ${transition.to.displayName}")
                if (!transition.isValid) {
                    appendLine("   Причина: ${transition.reason}")
                }
            }
        }

        addMessage(
            role = "assistant",
            content = transitionsAfterText,
            metadata = "📊 ДОСТУПНЫЕ ПЕРЕХОДЫ"
        )
        delay(4.seconds)
        checkCancelled()

        addMessage(
            role = "assistant",
            content = "📌 ШАГ 7/10: Переход в EXECUTION (теперь разрешен)",
            metadata = "📌 ШАГ 7/10"
        )
        delay(2.seconds)
        checkCancelled()

        onTypingStateChanged(true)
        delay(2.seconds)
        checkCancelled()
        onTypingStateChanged(false)

        val result4 = statefulAgent.safeTransitionTo(TaskPhase.EXECUTION)
        onTaskStateUpdated?.invoke()

        addMessage(
            role = "assistant",
            content = """
            ✅ ${result4.message}
            
            📊 Текущая фаза: ${statefulAgent.getPhase().displayName}
            🎯 Ожидается: ${statefulAgent.getExpectedAction()}
            """.trimIndent(),
            metadata = "✅ ПЕРЕХОД В EXECUTION"
        )
        delay(4.seconds)
        checkCancelled()

        addMessage(
            role = "assistant",
            content = "📌 ШАГ 8/10: Попытка перехода в DONE без валидации",
            metadata = "📌 ШАГ 8/10"
        )
        delay(2.seconds)
        checkCancelled()

        addMessage(
            role = "assistant",
            content = "❌ Пытаемся перейти в DONE без подтверждения валидации...",
            metadata = "⏳ ПОПЫТКА ПЕРЕХОДА"
        )
        onTypingStateChanged(true)
        delay(4.seconds)
        checkCancelled()
        onTypingStateChanged(false)

        val result5 = statefulAgent.safeTransitionTo(TaskPhase.DONE)
        onTaskStateUpdated?.invoke()

        addMessage(
            role = "assistant",
            content = """
            🚫 ПЕРЕХОД ОТКАЗАН!
            
            Результат: ${if (result5.success) "✅ Успешно" else "🚫 Отказано"}
            Сообщение: ${result5.message}
            ${result5.suggestedAction?.let { "💡 $it" } ?: ""}
            """.trimIndent(),
            metadata = "🚫 ОТКАЗАНО"
        )
        delay(4.seconds)
        checkCancelled()

        addMessage(
            role = "assistant",
            content = "📌 ШАГ 9/10: Переход в VALIDATION",
            metadata = "📌 ШАГ 9/10"
        )
        delay(2.seconds)
        checkCancelled()

        onTypingStateChanged(true)
        delay(2.seconds)
        checkCancelled()
        onTypingStateChanged(false)

        val result6 = statefulAgent.safeTransitionTo(TaskPhase.VALIDATION)
        onTaskStateUpdated?.invoke()

        addMessage(
            role = "assistant",
            content = """
            ✅ ${result6.message}
            
            📊 Текущая фаза: ${statefulAgent.getPhase().displayName}
            🎯 Ожидается: ${statefulAgent.getExpectedAction()}
            """.trimIndent(),
            metadata = "✅ ПЕРЕХОД В VALIDATION"
        )
        delay(4.seconds)
        checkCancelled()

        addMessage(
            role = "assistant",
            content = "📌 ШАГ 10/10: Подтверждение валидации и завершение",
            metadata = "📌 ШАГ 10/10"
        )
        delay(2.seconds)
        checkCancelled()

        onTypingStateChanged(true)
        delay(2.seconds)
        checkCancelled()
        onTypingStateChanged(false)

        val result7 = statefulAgent.confirmValidation()
        onTaskStateUpdated?.invoke()

        addMessage(
            role = "assistant",
            content = """
            ${if (result7.success) "✅" else "❌"} ${result7.message}
            
            📊 Текущая фаза: ${statefulAgent.getPhase().displayName}
            """.trimIndent(),
            metadata = if (result7.success) "✅ ВАЛИДАЦИЯ ПОДТВЕРЖДЕНА" else "❌ ОШИБКА"
        )
        delay(4.seconds)
        checkCancelled()

        onTypingStateChanged(true)
        delay(2.seconds)
        checkCancelled()
        onTypingStateChanged(false)

        val result8 = statefulAgent.safeTransitionTo(TaskPhase.DONE)
        onTaskStateUpdated?.invoke()

        addMessage(
            role = "assistant",
            content = """
            ✅ ${result8.message}
            
            📊 Текущая фаза: ${statefulAgent.getPhase().displayName}
            🎉 Задача завершена!
            """.trimIndent(),
            metadata = "✅ ЗАВЕРШЕНО"
        )
        delay(2.seconds)
        checkCancelled()

        addMessage(
            role = "assistant",
            content = """
            ${"=".repeat(80)}
            📊 ИТОГИ ДЕМОНСТРАЦИИ УПРАВЛЕНИЯ ПЕРЕХОДАМИ
            ${"=".repeat(80)}
            
            ✅ Все переходы контролируются:
            
            1️⃣ Невозможно перейти в EXECUTION без утверждения плана
            2️⃣ Невозможно перейти в DONE без подтверждения валидации
            3️⃣ Каждый переход имеет понятную причину отказа
            4️⃣ Доступные переходы можно посмотреть в любой момент
            5️⃣ Пауза и возобновление работают корректно
            
            📊 Финальное состояние:
            • Фаза: ${statefulAgent.getPhase().displayName}
            • Прогресс: ${(statefulAgent.getProgress() * 100).toInt()}%
            • Задача завершена: ${statefulAgent.isTaskComplete()}
            
            🎯 Агент с контролируемым жизненным циклом задачи готов!
            """.trimIndent(),
            metadata = "🏁 ДЕМОНСТРАЦИЯ ЗАВЕРШЕНА"
        )
        checkCancelled()
    }
}
