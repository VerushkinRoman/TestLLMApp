package com.llmapp.demo.manager

import com.llmapp.agent.StatefulMemoryAgent
import com.llmapp.memory.ProjectConstraints
import com.llmapp.memory.ResponseStyle
import com.llmapp.memory.UserProfile
import com.llmapp.state.TaskPhase
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Полная демонстрация Stateful агента
 */
class StatefulDemoRunner(
    onMessageAdded: (ChatMessageUI) -> Unit,
    onTypingStateChanged: (Boolean) -> Unit,
    private val onTaskStateUpdated: (() -> Unit)? = null,
    private val statefulAgent: StatefulMemoryAgent
) : BaseDemoRunner(onMessageAdded, onTypingStateChanged) {

    override suspend fun run() {
        addMessage(
            role = "assistant",
            content = """
            🧠 ЗАПУСК ДЕМОНСТРАЦИИ STATEFUL АГЕНТА
            
            Эта демонстрация покажет работу агента с конечным автоматом.
            Все сообщения будут отображаться здесь, как в обычном диалоге.
            
            Агент будет:
            1. Настраивать профиль и ограничения
            2. Создавать задачу
            3. Собирать требования
            4. Планировать
            5. Выполнять
            6. Проверять
            7. Завершать задачу
            
            Также вы увидите:
            • Паузу и возобновление
            • Сохранение решений
            • Добавление знаний
            • Полный статус агента
            """.trimIndent(),
            metadata = "🧠 STATEFUL ДЕМО"
        )
        delay(3.seconds)

        val agent = statefulAgent

        // ========== ШАГ 1: Профиль ==========
        addMessage(
            role = "assistant",
            content = "📌 ШАГ 1/12: Настройка профиля и ограничений",
            metadata = "⚙️ Настройка"
        )
        onTaskStateUpdated?.invoke()
        delayMedium()

        val profile = UserProfile(
            name = "Алексей",
            experience = "Middle Android разработчик, 4 года",
            preferredStyle = ResponseStyle.TECHNICAL,
            preferredTech = listOf("Kotlin", "Compose", "KMP", "Ktor", "Coroutines"),
            commonGoals = listOf("Изучить агентные системы", "Улучшить архитектуру"),
            customNotes = "Предпочитаю примеры кода и практические решения"
        )
        agent.updateProfile(profile)

        val constraints = ProjectConstraints(
            techStack = listOf("Kotlin", "KMP", "Compose Multiplatform"),
            forbiddenTech = listOf("Java", "RxJava", "XML layouts"),
            architecture = "MVI + Clean Architecture",
            codingStandards = "Kotlin Coding Conventions, Detekt",
            specialRules = "Все новые фичи должны иметь модульные тесты"
        )
        agent.updateConstraints(constraints)

        addMessage(
            role = "assistant",
            content = """
            ✅ Профиль и ограничения настроены:
            
            👤 Профиль: ${profile.name} (${profile.experience})
            📚 Технологии: ${profile.preferredTech.joinToString(", ")}
            🔧 Стек: ${constraints.techStack.joinToString(", ")}
            🚫 Запрещено: ${constraints.forbiddenTech.joinToString(", ")}
            """.trimIndent(),
            metadata = "✅ Шаг 1/12"
        )
        onTaskStateUpdated?.invoke()
        delay(2.seconds)

        // ========== ШАГ 2: Задача ==========
        addMessage(
            role = "assistant",
            content = "📌 ШАГ 2/12: Создание задачи",
            metadata = "📋 Создание"
        )
        delayMedium()

        val task = agent.createTask(
            taskName = "Разработка чат-бота с памятью",
            description = "Чат-бот с трехслойной памятью и state machine",
            initialContext = mapOf(
                "требования" to "Кроссплатформенность, память, state machine",
                "срок" to "2 недели"
            )
        )

        addMessage(
            role = "assistant",
            content = """
            📋 ЗАДАЧА СОЗДАНА:
            
            📌 Название: ${task.taskName}
            📍 Фаза: ${task.phase.displayName}
            📌 Шаг: ${task.step}
            🎯 Ожидается: ${task.expectedAction}
            🕐 Создана: ${task.getElapsedTime()} назад
            """.trimIndent(),
            metadata = "✅ Шаг 2/12"
        )
        onTaskStateUpdated?.invoke()
        delay(2.seconds)

        // ========== ШАГ 3: INIT ==========
        addMessage(
            role = "assistant",
            content = "📌 ШАГ 3/12: Сбор требований (фаза INIT)",
            metadata = "📋 INIT"
        )
        delayMedium()

        val userQuestion1 = "Давайте спроектируем архитектуру для чат-бота. Нужны UseCases, репозитории и DI. Какую архитектуру вы предложите?"

        addMessage(role = "user", content = userQuestion1)
        delayMedium()
        onTypingStateChanged(true)
        delayMedium()

        val response1 = agent.processRequest(userQuestion1)
        onTypingStateChanged(false)

        addMessage(
            role = "assistant",
            content = response1.content,
            metadata = "📊 Токены: ${response1.totalTokens ?: 0} | ⏱️ ${response1.responseTimeMs}мс"
        )

        agent.updateStep("Обсуждение архитектуры")
        agent.updateExpectedAction("Утвердите план или уточните детали")

        addMessage(
            role = "assistant",
            content = "📌 Шаг обновлен: ${agent.getStep()}\n🎯 Ожидается: ${agent.getExpectedAction()}",
            metadata = "📝 Обновление"
        )
        onTaskStateUpdated?.invoke()
        delay(3.seconds)

        // ========== ШАГ 4: PLANNING ==========
        addMessage(
            role = "assistant",
            content = "📌 ШАГ 4/12: Переход в PLANNING",
            metadata = "🔄 Переход"
        )
        delayMedium()

        val transition1 = agent.transitionTo(TaskPhase.PLANNING, "Требования собраны")
        addMessage(
            role = "assistant",
            content = "🔄 ${transition1.message}",
            metadata = "✅ Шаг 4/12"
        )
        onTaskStateUpdated?.invoke()
        delayMedium()

        // ========== ШАГ 5: PLANNING диалог ==========
        addMessage(
            role = "assistant",
            content = "📌 ШАГ 5/12: Работа в PLANNING - разработка плана",
            metadata = "📐 PLANNING"
        )
        delayMedium()

        val userQuestion2 = "Нужен план разработки. Какие модули будем делать и в каком порядке?"

        addMessage(role = "user", content = userQuestion2)
        delayMedium()
        onTypingStateChanged(true)
        delayMedium()

        val response2 = agent.processRequest(userQuestion2)
        onTypingStateChanged(false)

        addMessage(
            role = "assistant",
            content = response2.content,
            metadata = "📊 Токены: ${response2.totalTokens ?: 0} | ⏱️ ${response2.responseTimeMs}мс"
        )

        agent.saveDecisionToLongTerm("Модули", "Будем делать 4 модуля: core, data, domain, presentation", "На основе обсуждения архитектуры")
        addMessage(
            role = "assistant",
            content = "💾 Решение сохранено в LTM: Модули\n📚 Всего решений: ${agent.getAllDecisions().size}",
            metadata = "💾 Сохранено"
        )
        onTaskStateUpdated?.invoke()
        delay(3.seconds)

        // ========== ШАГ 6: Пауза ==========
        addMessage(
            role = "assistant",
            content = "📌 ШАГ 6/12: ДЕМОНСТРАЦИЯ ПАУЗЫ",
            metadata = "⏸️ Пауза"
        )
        delayMedium()

        val pauseResult = agent.pause("Нужно обсудить план с командой")
        addMessage(
            role = "assistant",
            content = "⏸️ ${pauseResult.message}",
            metadata = "⏸️ Шаг 6/12"
        )

        val pausedState = agent.getCurrentTaskState()
        addMessage(
            role = "assistant",
            content = """
            📊 СОСТОЯНИЕ НА ПАУЗЕ:
            📍 Фаза: ${pausedState.phase.displayName}
            📌 Шаг: ${pausedState.step}
            🎯 Ожидается: ${pausedState.expectedAction}
            💬 Причина: ${agent.getPauseReason()}
            """.trimIndent(),
            metadata = "⏸️ Состояние"
        )

        addMessage(role = "user", content = "Продолжим планирование")
        delayMedium()
        onTypingStateChanged(true)
        delayMedium()

        val responsePaused = agent.processRequest("Продолжим планирование")
        onTypingStateChanged(false)

        addMessage(
            role = "assistant",
            content = responsePaused.content,
            metadata = "⏸️ Ответ на паузе"
        )
        onTaskStateUpdated?.invoke()
        delay(3.seconds)

        // ========== ШАГ 7: Возобновление ==========
        addMessage(
            role = "assistant",
            content = "📌 ШАГ 7/12: ДЕМОНСТРАЦИЯ ВОЗОБНОВЛЕНИЯ",
            metadata = "▶️ Возобновление"
        )
        delayMedium()

        val resumeResult = agent.resume()
        addMessage(
            role = "assistant",
            content = "▶️ ${resumeResult.message}",
            metadata = "▶️ Шаг 7/12"
        )

        val resumedState = agent.getCurrentTaskState()
        addMessage(
            role = "assistant",
            content = """
            📊 СОСТОЯНИЕ ПОСЛЕ ВОЗОБНОВЛЕНИЯ:
            📍 Фаза: ${resumedState.phase.displayName}
            📌 Шаг: ${resumedState.step}
            🎯 Ожидается: ${resumedState.expectedAction}
            """.trimIndent(),
            metadata = "▶️ Состояние"
        )
        onTaskStateUpdated?.invoke()
        delay(2.seconds)

        // ========== ШАГ 8: EXECUTION ==========
        addMessage(
            role = "assistant",
            content = "📌 ШАГ 8/12: Переход в EXECUTION",
            metadata = "⚡ Переход"
        )
        delayMedium()

        val transition2 = agent.transitionTo(TaskPhase.EXECUTION, "План утвержден")
        addMessage(
            role = "assistant",
            content = "🔄 ${transition2.message}",
            metadata = "✅ Шаг 8/12"
        )
        onTaskStateUpdated?.invoke()
        delayMedium()

        // ========== ШАГ 9: EXECUTION диалог ==========
        addMessage(
            role = "assistant",
            content = "📌 ШАГ 9/12: Работа в EXECUTION - написание кода",
            metadata = "⚡ EXECUTION"
        )
        delayMedium()

        val userQuestion4 = "Начинаем писать код. Создай базовую структуру UseCase'ов для чат-бота."

        addMessage(role = "user", content = userQuestion4)
        delayMedium()
        onTypingStateChanged(true)
        delayMedium()

        val response4 = agent.processRequest(userQuestion4)
        onTypingStateChanged(false)

        addMessage(
            role = "assistant",
            content = response4.content,
            metadata = "📊 Токены: ${response4.totalTokens ?: 0} | ⏱️ ${response4.responseTimeMs}мс"
        )

        agent.addKnowledge("use_case_pattern", "UseCase должен быть одноразовым, принимать параметры и возвращать Result")
        addMessage(
            role = "assistant",
            content = "📚 Знание добавлено: use_case_pattern\n📚 Всего знаний: ${agent.getAllKnowledge().size}",
            metadata = "📚 Знание"
        )
        onTaskStateUpdated?.invoke()
        delay(3.seconds)

        // ========== ШАГ 10: VALIDATION ==========
        addMessage(
            role = "assistant",
            content = "📌 ШАГ 10/12: Переход в VALIDATION",
            metadata = "🔍 Переход"
        )
        delayMedium()

        val transition3 = agent.transitionTo(TaskPhase.VALIDATION, "Код написан")
        addMessage(
            role = "assistant",
            content = "🔄 ${transition3.message}",
            metadata = "✅ Шаг 10/12"
        )
        onTaskStateUpdated?.invoke()
        delayMedium()

        // ========== ШАГ 11: Проверка ==========
        addMessage(
            role = "assistant",
            content = "📌 ШАГ 11/12: Проверка кода",
            metadata = "🔍 VALIDATION"
        )
        delayMedium()

        val userQuestion5 = "Проверь код UseCase'ов. Все ли правильно? Есть ли улучшения?"

        addMessage(role = "user", content = userQuestion5)
        delayMedium()
        onTypingStateChanged(true)
        delayMedium()

        val response5 = agent.processRequest(userQuestion5)
        onTypingStateChanged(false)

        addMessage(
            role = "assistant",
            content = response5.content,
            metadata = "📊 Токены: ${response5.totalTokens ?: 0} | ⏱️ ${response5.responseTimeMs}мс"
        )
        onTaskStateUpdated?.invoke()
        delay(3.seconds)

        // ========== ШАГ 12: Завершение ==========
        addMessage(
            role = "assistant",
            content = "📌 ШАГ 12/12: Завершение задачи",
            metadata = "✅ Завершение"
        )
        delayMedium()

        val userQuestion6 = "Все работает, проверка пройдена. Завершаем задачу."

        addMessage(role = "user", content = userQuestion6)
        delayMedium()
        onTypingStateChanged(true)
        delayMedium()

        val response6 = agent.processRequest(userQuestion6)
        onTypingStateChanged(false)

        addMessage(
            role = "assistant",
            content = response6.content,
            metadata = "📊 Токены: ${response6.totalTokens ?: 0} | ⏱️ ${response6.responseTimeMs}мс"
        )

        if (response6.transitionResult != null) {
            addMessage(
                role = "assistant",
                content = "🔄 Автоматический переход: ${response6.transitionResult.message}",
                metadata = "🔄 Авто-переход"
            )
        }

        addMessage(
            role = "assistant",
            content = "✅ Задача завершена: ${agent.isTaskComplete()}",
            metadata = "✅ Статус"
        )
        onTaskStateUpdated?.invoke()
        delay(2.seconds)

        // ========== Финальный статус ==========
        addMessage(
            role = "assistant",
            content = "📊 ПОЛНЫЙ СТАТУС АГЕНТА",
            metadata = "📊 Статус"
        )
        delayMedium()

        val fullStatus = agent.getFullStatus()
        addMessage(
            role = "assistant",
            content = fullStatus,
            metadata = "📊 Финальный статус"
        )
        delay(2.seconds)

        // ========== Итоги ==========
        addMessage(
            role = "assistant",
            content = """
            ${"=".repeat(70)}
            🎯 ДЕМОНСТРАЦИЯ STATEFUL АГЕНТА ЗАВЕРШЕНА
            ${"=".repeat(70)}
            
            📊 ИТОГИ:
            
            ✅ Пройдены все этапы:
            • Профиль настроен (${profile.name})
            • Ограничения применены (${constraints.techStack.joinToString(", ")})
            • Задача создана: ${task.taskName}
            • Требования собраны
            • План утвержден
            • Код написан
            • Проверка пройдена
            • Задача завершена
            
            🧠 ПАМЯТЬ АГЕНТА:
            • Знаний: ${agent.getAllKnowledge().size}
            • Решений: ${agent.getAllDecisions().size}
            • Всего токенов: ${agent.getTokenStats().totalTokens}
            
            💡 КЛЮЧЕВЫЕ ВЫВОДЫ:
            1. Агент запомнил ваш профиль и ограничения
            2. State machine контролировала этапы задачи
            3. Пауза и возобновление работают корректно
            4. Снимки состояния сохраняют прогресс
            5. Все решения и знания сохранены в LTM
            """.trimIndent(),
            metadata = "🎉 ДЕМОНСТРАЦИЯ ЗАВЕРШЕНА"
        )

        delayMedium()
        addMessage(
            role = "assistant",
            content = "✅ Демонстрация успешно завершена! Можете продолжать общение с агентом.",
            metadata = "✨ Готово!"
        )
        onTaskStateUpdated?.invoke()
    }
}
