package com.llmapp.ui

import com.llmapp.agent.CompressedLLMAgent
import com.llmapp.agent.ContextStrategyType
import com.llmapp.agent.LLMAgent
import com.llmapp.agent.MemoryAwareAgent
import com.llmapp.agent.StatefulMemoryAgent
import com.llmapp.agent.StrategicLLMAgent
import com.llmapp.agent.TokenSnapshot
import com.llmapp.api.ApiConfig
import com.llmapp.chat.ChatSession
import com.llmapp.demo.DemoData
import com.llmapp.demo.StrategyTestScenario
import com.llmapp.memory.ProjectConstraints
import com.llmapp.memory.ResponseStyle
import com.llmapp.memory.TaskState
import com.llmapp.memory.UserProfile
import com.llmapp.model.TokenStats
import com.llmapp.state.TaskPhase
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

sealed class DemoType {
    object TokenComparison : DemoType()
    object CompressionComparison : DemoType()
    object StrategyComparison : DemoType()
    object MemoryComparison : DemoType()
    object Personalization : DemoType()
    object StatefulAgent : DemoType()
}

class DemoManager(
    private val chatSession: ChatSession,
    private val onMessageAdded: (ChatMessageUI) -> Unit,
    private val onDemoStarted: () -> Unit,
    private val onDemoFinished: () -> Unit,
    private val onTypingStateChanged: (Boolean) -> Unit,
    private val onStatsUpdated: ((TokenStats) -> Unit)? = null,
    private val onTokenHistoryUpdated: ((List<TokenSnapshot>) -> Unit)? = null,
    private val onContextWarningUpdated: ((String) -> Unit)? = null
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    // Текущее состояние демонстрации
    private val _isRunning = MutableStateFlow(false)
    private val _currentDemo = MutableStateFlow<DemoType?>(null)

    private val primaryModel = "openai/gpt-oss-20b:free"

    // ============================================================
    // ПУБЛИЧНЫЕ МЕТОДЫ ЗАПУСКА
    // ============================================================

    fun startTokenDemo() {
        if (_isRunning.value) return
        _isRunning.value = true
        _currentDemo.value = DemoType.TokenComparison
        onDemoStarted()

        scope.launch {
            try {
                chatSession.clearHistory()
                runTokenComparison()
            } catch (e: Exception) {
                addMessage(
                    role = "assistant",
                    content = "❌ Ошибка: ${e.message}",
                    metadata = "Ошибка"
                )
            } finally {
                _isRunning.value = false
                _currentDemo.value = null
                onDemoFinished()
            }
        }
    }

    fun startCompressionDemo() {
        if (_isRunning.value) return
        _isRunning.value = true
        _currentDemo.value = DemoType.CompressionComparison
        onDemoStarted()

        scope.launch {
            try {
                chatSession.clearHistory()
                runCompressionComparison()
            } catch (e: Exception) {
                addMessage(
                    role = "assistant",
                    content = "❌ Ошибка: ${e.message}",
                    metadata = "Ошибка"
                )
            } finally {
                _isRunning.value = false
                _currentDemo.value = null
                onDemoFinished()
            }
        }
    }

    fun startStrategyDemo() {
        if (_isRunning.value) return
        _isRunning.value = true
        _currentDemo.value = DemoType.StrategyComparison
        onDemoStarted()

        scope.launch {
            try {
                chatSession.clearHistory()
                runStrategyComparison()
            } catch (e: Exception) {
                addMessage(
                    role = "assistant",
                    content = "❌ Ошибка при выполнении демонстрации стратегий: ${e.message}",
                    metadata = "Ошибка"
                )
            } finally {
                _isRunning.value = false
                _currentDemo.value = null
                onDemoFinished()
            }
        }
    }

    fun startMemoryDemo() {
        if (_isRunning.value) return
        _isRunning.value = true
        _currentDemo.value = DemoType.MemoryComparison
        onDemoStarted()

        scope.launch {
            try {
                chatSession.clearHistory()
                runMemoryDemonstration()
            } catch (e: Exception) {
                addMessage(
                    role = "assistant",
                    content = "❌ Ошибка: ${e.message}",
                    metadata = "Ошибка"
                )
            } finally {
                _isRunning.value = false
                _currentDemo.value = null
                onDemoFinished()
            }
        }
    }

    fun startPersonalizationDemo() {
        if (_isRunning.value) return
        _isRunning.value = true
        _currentDemo.value = DemoType.Personalization
        onDemoStarted()

        scope.launch {
            try {
                chatSession.clearHistory()
                runPersonalizationDemo()
            } catch (e: Exception) {
                addMessage(
                    role = "assistant",
                    content = "❌ Ошибка: ${e.message}",
                    metadata = "Ошибка"
                )
            } finally {
                _isRunning.value = false
                _currentDemo.value = null
                onDemoFinished()
            }
        }
    }

    fun startStatefulDemo() {
        if (_isRunning.value) return
        _isRunning.value = true
        _currentDemo.value = DemoType.StatefulAgent
        onDemoStarted()

        scope.launch {
            try {
                chatSession.clearHistory()
                runStatefulDemonstration()
            } catch (e: Exception) {
                addMessage(
                    role = "assistant",
                    content = "❌ Ошибка: ${e.message}",
                    metadata = "Ошибка"
                )
            } finally {
                _isRunning.value = false
                _currentDemo.value = null
                onDemoFinished()
            }
        }
    }

// ============================================================
// STATEFUL ДЕМОНСТРАЦИЯ
// ============================================================

    private suspend fun runStatefulDemonstration() {
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
        
        Начинаем...
        """.trimIndent(),
            metadata = "🧠 STATEFUL ДЕМО"
        )
        delay(3.seconds)

        val agent = StatefulMemoryAgent()

        // ============================================================
        // ШАГ 1: Настройка профиля
        // ============================================================
        addMessage(
            role = "assistant",
            content = "📌 ШАГ 1/12: Настройка профиля и ограничений",
            metadata = "⚙️ Настройка"
        )
        delay(1.seconds)

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
        delay(2.seconds)

        // ============================================================
        // ШАГ 2: Создание задачи
        // ============================================================
        addMessage(
            role = "assistant",
            content = "📌 ШАГ 2/12: Создание задачи",
            metadata = "📋 Создание"
        )
        delay(1.seconds)

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
        delay(2.seconds)

        // ============================================================
        // ШАГ 3: Сбор требований (INIT) - ПОЛНЫЙ ОТВЕТ LLM
        // ============================================================
        addMessage(
            role = "assistant",
            content = "📌 ШАГ 3/12: Сбор требований (фаза INIT)",
            metadata = "📋 INIT"
        )
        delay(1.seconds)

        val userQuestion1 =
            "Давайте спроектируем архитектуру для чат-бота. Нужны UseCases, репозитории и DI. Какую архитектуру вы предложите?"

        addMessage(
            role = "user",
            content = userQuestion1
        )
        delay(1.seconds)

        addMessage(
            role = "assistant",
            content = "⏳ Агент думает над ответом...",
            metadata = "⏳ Генерация"
        )

        val response1 = agent.processRequest(userQuestion1)

        // ПОЛНЫЙ ОТВЕТ - НЕ ОБРЕЗАЕМ
        addMessage(
            role = "assistant",
            content = response1.content,
            metadata = "📊 Токены: ${response1.totalTokens ?: 0} | ⏱️ ${response1.responseTimeMs}мс"
        )

        // Обновляем шаг
        agent.updateStep("Обсуждение архитектуры")
        agent.updateExpectedAction("Утвердите план или уточните детали")

        addMessage(
            role = "assistant",
            content = "📌 Шаг обновлен: ${agent.getStep()}\n🎯 Ожидается: ${agent.getExpectedAction()}",
            metadata = "📝 Обновление"
        )
        delay(3.seconds)

        // ============================================================
        // ШАГ 4: Переход в PLANNING
        // ============================================================
        addMessage(
            role = "assistant",
            content = "📌 ШАГ 4/12: Переход в PLANNING",
            metadata = "🔄 Переход"
        )
        delay(1.seconds)

        val transition1 = agent.transitionTo(TaskPhase.PLANNING, "Требования собраны")
        addMessage(
            role = "assistant",
            content = "🔄 ${transition1.message}",
            metadata = "✅ Шаг 4/12"
        )
        delay(1.seconds)

        // ============================================================
        // ШАГ 5: Работа в PLANNING - ПОЛНЫЙ ОТВЕТ LLM
        // ============================================================
        addMessage(
            role = "assistant",
            content = "📌 ШАГ 5/12: Работа в PLANNING - разработка плана",
            metadata = "📐 PLANNING"
        )
        delay(1.seconds)

        val userQuestion2 = "Нужен план разработки. Какие модули будем делать и в каком порядке?"

        addMessage(
            role = "user",
            content = userQuestion2
        )
        delay(1.seconds)

        addMessage(
            role = "assistant",
            content = "⏳ Агент разрабатывает план...",
            metadata = "⏳ Генерация"
        )

        val response2 = agent.processRequest(userQuestion2)

        // ПОЛНЫЙ ОТВЕТ - НЕ ОБРЕЗАЕМ
        addMessage(
            role = "assistant",
            content = response2.content,
            metadata = "📊 Токены: ${response2.totalTokens ?: 0} | ⏱️ ${response2.responseTimeMs}мс"
        )

        // Сохраняем решение
        agent.saveDecisionToLongTerm(
            topic = "Модули",
            decision = "Будем делать 4 модуля: core, data, domain, presentation",
            context = "На основе обсуждения архитектуры"
        )
        addMessage(
            role = "assistant",
            content = "💾 Решение сохранено в LTM: Модули\n📚 Всего решений: ${agent.getAllDecisions().size}",
            metadata = "💾 Сохранено"
        )
        delay(3.seconds)

        // ============================================================
        // ШАГ 6: Пауза
        // ============================================================
        addMessage(
            role = "assistant",
            content = "📌 ШАГ 6/12: ДЕМОНСТРАЦИЯ ПАУЗЫ",
            metadata = "⏸️ Пауза"
        )
        delay(1.seconds)

        addMessage(
            role = "assistant",
            content = "⏸️ Ставим задачу на паузу...",
            metadata = "⏸️ Пауза"
        )

        val pauseResult = agent.pause("Нужно обсудить план с командой")
        addMessage(
            role = "assistant",
            content = "⏸️ ${pauseResult.message}",
            metadata = "⏸️ Шаг 6/12"
        )

        // Показываем состояние на паузе
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

        // Пытаемся отправить запрос на паузе
        addMessage(
            role = "user",
            content = "Продолжим планирование"
        )
        delay(1.seconds)

        addMessage(
            role = "assistant",
            content = "⏳ Попытка запроса на паузе...",
            metadata = "⏳ Ожидание"
        )

        val responsePaused = agent.processRequest("Продолжим планирование")
        addMessage(
            role = "assistant",
            content = responsePaused.content,
            metadata = "⏸️ Ответ на паузе"
        )
        delay(3.seconds)

        // ============================================================
        // ШАГ 7: Возобновление
        // ============================================================
        addMessage(
            role = "assistant",
            content = "📌 ШАГ 7/12: ДЕМОНСТРАЦИЯ ВОЗОБНОВЛЕНИЯ",
            metadata = "▶️ Возобновление"
        )
        delay(1.seconds)

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
        delay(2.seconds)

        // ============================================================
        // ШАГ 8: Переход в EXECUTION
        // ============================================================
        addMessage(
            role = "assistant",
            content = "📌 ШАГ 8/12: Переход в EXECUTION",
            metadata = "⚡ Переход"
        )
        delay(1.seconds)

        val transition2 = agent.transitionTo(TaskPhase.EXECUTION, "План утвержден")
        addMessage(
            role = "assistant",
            content = "🔄 ${transition2.message}",
            metadata = "✅ Шаг 8/12"
        )
        delay(1.seconds)

        // ============================================================
        // ШАГ 9: Работа в EXECUTION - ПОЛНЫЙ ОТВЕТ LLM
        // ============================================================
        addMessage(
            role = "assistant",
            content = "📌 ШАГ 9/12: Работа в EXECUTION - написание кода",
            metadata = "⚡ EXECUTION"
        )
        delay(1.seconds)

        val userQuestion4 = "Начинаем писать код. Создай базовую структуру UseCase'ов для чат-бота."

        addMessage(
            role = "user",
            content = userQuestion4
        )
        delay(1.seconds)

        addMessage(
            role = "assistant",
            content = "⏳ Агент пишет код...",
            metadata = "⏳ Генерация"
        )

        val response4 = agent.processRequest(userQuestion4)

        // ПОЛНЫЙ ОТВЕТ - НЕ ОБРЕЗАЕМ
        addMessage(
            role = "assistant",
            content = response4.content,
            metadata = "📊 Токены: ${response4.totalTokens ?: 0} | ⏱️ ${response4.responseTimeMs}мс"
        )

        // Добавляем знание
        agent.addKnowledge(
            "use_case_pattern",
            "UseCase должен быть одноразовым, принимать параметры и возвращать Result"
        )
        addMessage(
            role = "assistant",
            content = "📚 Знание добавлено: use_case_pattern\n📚 Всего знаний: ${agent.getAllKnowledge().size}",
            metadata = "📚 Знание"
        )
        delay(3.seconds)

        // ============================================================
        // ШАГ 10: Переход в VALIDATION
        // ============================================================
        addMessage(
            role = "assistant",
            content = "📌 ШАГ 10/12: Переход в VALIDATION",
            metadata = "🔍 Переход"
        )
        delay(1.seconds)

        val transition3 = agent.transitionTo(TaskPhase.VALIDATION, "Код написан")
        addMessage(
            role = "assistant",
            content = "🔄 ${transition3.message}",
            metadata = "✅ Шаг 10/12"
        )
        delay(1.seconds)

        // ============================================================
        // ШАГ 11: Проверка - ПОЛНЫЙ ОТВЕТ LLM
        // ============================================================
        addMessage(
            role = "assistant",
            content = "📌 ШАГ 11/12: Проверка кода",
            metadata = "🔍 VALIDATION"
        )
        delay(1.seconds)

        val userQuestion5 = "Проверь код UseCase'ов. Все ли правильно? Есть ли улучшения?"

        addMessage(
            role = "user",
            content = userQuestion5
        )
        delay(1.seconds)

        addMessage(
            role = "assistant",
            content = "⏳ Агент проверяет код...",
            metadata = "⏳ Проверка"
        )

        val response5 = agent.processRequest(userQuestion5)

        // ПОЛНЫЙ ОТВЕТ - НЕ ОБРЕЗАЕМ
        addMessage(
            role = "assistant",
            content = response5.content,
            metadata = "📊 Токены: ${response5.totalTokens ?: 0} | ⏱️ ${response5.responseTimeMs}мс"
        )
        delay(3.seconds)

        // ============================================================
        // ШАГ 12: Завершение задачи - ПОЛНЫЙ ОТВЕТ LLM
        // ============================================================
        addMessage(
            role = "assistant",
            content = "📌 ШАГ 12/12: Завершение задачи",
            metadata = "✅ Завершение"
        )
        delay(1.seconds)

        val userQuestion6 = "Все работает, проверка пройдена. Завершаем задачу."

        addMessage(
            role = "user",
            content = userQuestion6
        )
        delay(1.seconds)

        addMessage(
            role = "assistant",
            content = "⏳ Агент завершает задачу...",
            metadata = "⏳ Завершение"
        )

        val response6 = agent.processRequest(userQuestion6)

        // ПОЛНЫЙ ОТВЕТ - НЕ ОБРЕЗАЕМ
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
        delay(2.seconds)

        // ============================================================
        // ФИНАЛЬНЫЙ СТАТУС
        // ============================================================
        addMessage(
            role = "assistant",
            content = "📊 ПОЛНЫЙ СТАТУС АГЕНТА",
            metadata = "📊 Статус"
        )
        delay(1.seconds)

        val fullStatus = agent.getFullStatus()
        addMessage(
            role = "assistant",
            content = fullStatus,
            metadata = "📊 Финальный статус"
        )
        delay(2.seconds)

        // ============================================================
        // ИТОГИ
        // ============================================================
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

        delay(1.seconds)
        addMessage(
            role = "assistant",
            content = "✅ Демонстрация успешно завершена! Можете продолжать общение с агентом.",
            metadata = "✨ Готово!"
        )
    }

    // ============================================================
    // 1. ДЕМОНСТРАЦИЯ ТОКЕНОВ
    // ============================================================
    private suspend fun runTokenComparison() {
        addMessage(
            role = "assistant",
            content = DemoData.getTokenDemoIntro(),
            metadata = "ДЕМОНСТРАЦИЯ ТОКЕНОВ"
        )
        delay(1.seconds)

        // Используем chatSession для всех запросов
        var requestCounter = 0

        // ТЕСТ 1: Короткий диалог
        addMessage(
            role = "assistant",
            content = DemoData.getShortDialogueIntro(),
            metadata = "Тест 1/3"
        )
        delay(1.seconds)

        for (message in DemoData.shortDialogue) {
            addMessage(role = "user", content = message)
            delay(300.milliseconds)
            onTypingStateChanged(true)
            delay(500.milliseconds)

            val response = chatSession.ask(message)
            requestCounter++

            val stats = chatSession.getTokenStats()
            val metadata = DemoData.formatTokenMetadata(
                response.promptTokens ?: 0,
                response.completionTokens ?: 0,
                response.totalTokens ?: 0,
                stats.totalTokens,
                stats.estimatedCostUsd
            )

            addMessage(
                role = "assistant",
                content = response.content,
                metadata = metadata,
                promptTokens = response.promptTokens,
                completionTokens = response.completionTokens,
                totalTokens = response.totalTokens,
                responseTimeMs = response.responseTimeMs
            )
            onTypingStateChanged(false)
            delay(500.milliseconds)
        }

        val stats1 = chatSession.getTokenStats()
        addMessage(
            role = "assistant",
            content = DemoData.getShortDialogueSummary(
                stats1.totalTokens,
                stats1.totalPromptTokens,
                stats1.totalCompletionTokens,
                stats1.estimatedCostUsd
            ),
            metadata = "Промежуточная статистика"
        )
        delay(2.seconds)

        // ТЕСТ 2: Длинный диалог
        chatSession.clearHistory()

        addMessage(
            role = "assistant",
            content = DemoData.getLongDialogueIntro(DemoData.longDialogueTopics.size),
            metadata = "Тест 2/3"
        )
        delay(1.seconds)

        for ((index, question) in DemoData.longDialogueTopics.withIndex()) {
            addMessage(
                role = "user",
                content = question.take(80) + if (question.length > 80) "..." else ""
            )
            delay(300.milliseconds)
            onTypingStateChanged(true)
            delay(500.milliseconds)

            val response = try {
                chatSession.ask(question)
            } catch (e: Exception) {
                addMessage(
                    role = "assistant",
                    content = "⚠️ Ошибка: ${e.message?.take(100)}. Продолжаем...",
                    metadata = "Ошибка"
                )
                onTypingStateChanged(false)
                delay(500.milliseconds)
                continue
            }
            requestCounter++

            val stats = chatSession.getTokenStats()
            val contextStatus = chatSession.getContextWarning()
            val metadata = DemoData.formatLongDialogueMetadata(
                index + 1,
                DemoData.longDialogueTopics.size,
                response.promptTokens ?: 0,
                response.completionTokens ?: 0,
                response.totalTokens ?: 0,
                stats.totalTokens,
                stats.estimatedCostUsd,
                contextStatus
            )

            addMessage(
                role = "assistant",
                content = response.content,
                metadata = metadata,
                promptTokens = response.promptTokens,
                completionTokens = response.completionTokens,
                totalTokens = response.totalTokens,
                responseTimeMs = response.responseTimeMs
            )
            onTypingStateChanged(false)
            delay(300.milliseconds)
        }

        val finalStats = chatSession.getTokenStats()
        addMessage(
            role = "assistant",
            content = DemoData.getFinalTokenStats(
                finalStats.requestCount,
                finalStats.totalTokens,
                finalStats.totalPromptTokens,
                finalStats.totalCompletionTokens,
                finalStats.estimatedCostUsd
            ),
            metadata = "Финальная статистика"
        )

        // ТЕСТ 3: Дополнительные запросы
        addMessage(
            role = "assistant",
            content = DemoData.getExtraQuestionsIntro(),
            metadata = "Тест 3/3"
        )
        delay(1.seconds)

        for (question in DemoData.extraQuestions) {
            addMessage(role = "user", content = question)
            delay(300.milliseconds)
            onTypingStateChanged(true)
            delay(500.milliseconds)

            val response = chatSession.ask(question)
            requestCounter++

            val stats = chatSession.getTokenStats()
            addMessage(
                role = "assistant",
                content = response.content,
                metadata = "📊 Всего токенов: ${stats.totalTokens} | 💰 ${
                    String.format(
                        "%.6f",
                        stats.estimatedCostUsd
                    )
                }",
                promptTokens = response.promptTokens,
                completionTokens = response.completionTokens,
                totalTokens = response.totalTokens,
                responseTimeMs = response.responseTimeMs
            )
            onTypingStateChanged(false)
            delay(300.milliseconds)
        }

        val finalStats2 = chatSession.getTokenStats()
        addMessage(
            role = "assistant",
            content = DemoData.getTokenDemoConclusion(
                finalStats2.requestCount,
                finalStats2.totalTokens,
                finalStats2.totalPromptTokens,
                finalStats2.totalCompletionTokens,
                finalStats2.estimatedCostUsd,
                chatSession.getContextWarning()
            ),
            metadata = "Итоговый анализ"
        )
    }

    // ============================================================
    // 2. ДЕМОНСТРАЦИЯ КОМПРЕССИИ
    // ============================================================
    private suspend fun runCompressionComparison() {
        addMessage(
            role = "assistant",
            content = DemoData.getCompressionDemoIntro(DemoData.longDialogueTopics.size),
            metadata = "ДЕМОНСТРАЦИЯ КОМПРЕССИИ"
        )
        delay(2.seconds)

        val apiKey = ApiConfig.getApiKey()

        // Тест 1: Без компрессии
        addMessage(
            role = "assistant",
            content = DemoData.getNoCompressionTestIntro(),
            metadata = "Тест без компрессии"
        )
        delay(1.seconds)

        val regularAgent = LLMAgent(
            apiKey = apiKey,
            model = primaryModel,
            systemPrompt = "Ты полезный ассистент. Отвечай на русском языке кратко и по делу.",
            maxHistorySize = 200
        )

        var regularTotalTokens = 0
        var regularTotalPrompt = 0
        var regularTotalCompletion = 0
        val regularStartTime = System.currentTimeMillis()

        for ((index, question) in DemoData.longDialogueTopics.withIndex()) {
            addMessage(
                role = "user",
                content = question.take(60) + if (question.length > 60) "..." else ""
            )
            delay(300.milliseconds)
            onTypingStateChanged(true)
            delay(500.milliseconds)

            val response = regularAgent.processRequest(question)

            updateStats(regularAgent)

            regularTotalTokens += response.totalTokens ?: 0
            regularTotalPrompt += response.promptTokens ?: 0
            regularTotalCompletion += response.completionTokens ?: 0

            val metadata = DemoData.formatCompressionTestMetadata(
                index + 1,
                DemoData.longDialogueTopics.size,
                response.promptTokens ?: 0,
                response.completionTokens ?: 0,
                response.totalTokens ?: 0,
                regularTotalTokens
            )

            addMessage(
                role = "assistant",
                content = response.content,
                metadata = metadata,
                promptTokens = response.promptTokens,
                completionTokens = response.completionTokens,
                totalTokens = response.totalTokens,
                responseTimeMs = response.responseTimeMs
            )
            onTypingStateChanged(false)
            delay(300.milliseconds)
        }

        val regularEndTime = System.currentTimeMillis()
        val regularTime = regularEndTime - regularStartTime
        val regularCost = regularAgent.getTokenStats().estimatedCostUsd

        addMessage(
            role = "assistant",
            content = DemoData.getNoCompressionResults(
                DemoData.longDialogueTopics.size,
                regularTotalTokens,
                regularTotalPrompt,
                regularTotalCompletion,
                regularTime,
                regularCost
            ),
            metadata = "Итог без компрессии"
        )
        delay(3.seconds)

        // Тест 2: С компрессией
        addMessage(
            role = "assistant",
            content = DemoData.getCompressionTestIntro(8, 6),
            metadata = "Тест с компрессией"
        )
        delay(1.seconds)

        val compressedAgent = CompressedLLMAgent(
            apiKey = apiKey,
            model = primaryModel,
            systemPrompt = "Ты полезный ассистент. Отвечай на русском языке кратко и по делу.",
            maxHistorySize = 200,
            keepLastMessages = 8,
            summarizeEvery = 6
        )
        compressedAgent.compressionEnabled = true

        var compressedTotalTokens = 0
        var compressedTotalPrompt = 0
        var compressedTotalCompletion = 0
        val compressedStartTime = System.currentTimeMillis()

        for ((index, question) in DemoData.longDialogueTopics.withIndex()) {
            addMessage(
                role = "user",
                content = question.take(60) + if (question.length > 60) "..." else ""
            )
            delay(300.milliseconds)
            onTypingStateChanged(true)
            delay(500.milliseconds)

            val response = compressedAgent.processRequest(question)

            compressedTotalTokens += response.totalTokens ?: 0
            compressedTotalPrompt += response.promptTokens ?: 0
            compressedTotalCompletion += response.completionTokens ?: 0

            val metadata = DemoData.formatCompressionTestMetadata(
                index + 1,
                DemoData.longDialogueTopics.size,
                response.promptTokens ?: 0,
                response.completionTokens ?: 0,
                response.totalTokens ?: 0,
                compressedTotalTokens
            )

            addMessage(
                role = "assistant",
                content = response.content,
                metadata = metadata,
                promptTokens = response.promptTokens,
                completionTokens = response.completionTokens,
                totalTokens = response.totalTokens,
                responseTimeMs = response.responseTimeMs
            )
            onTypingStateChanged(false)
            delay(300.milliseconds)
        }

        val compressedEndTime = System.currentTimeMillis()
        val compressedTime = compressedEndTime - compressedStartTime
        val compressedCost = compressedAgent.getTokenStats().estimatedCostUsd

        val tokensSaved = regularTotalTokens - compressedTotalTokens
        val tokensSavedPercent = if (regularTotalTokens > 0) {
            (tokensSaved.toDouble() / regularTotalTokens) * 100
        } else 0.0

        val compressionStats = compressedAgent.getCompressionStats()

        addMessage(
            role = "assistant",
            content = DemoData.getCompressionComparisonResults(
                regularTotalTokens, regularTotalPrompt, regularTotalCompletion,
                compressedTotalTokens, compressedTotalPrompt, compressedTotalCompletion,
                regularTime, compressedTime,
                regularCost, compressedCost,
                tokensSaved, tokensSavedPercent,
                compressionStats
            ),
            metadata = "Сравнительный анализ"
        )
    }

    // ============================================================
    // 3. ДЕМОНСТРАЦИЯ СТРАТЕГИЙ
    // ============================================================
    private suspend fun runStrategyComparison() {
        addMessage(
            role = "assistant",
            content = """
            🧪 ЗАПУСК ДЕМОНСТРАЦИИ СТРАТЕГИЙ УПРАВЛЕНИЯ КОНТЕКСТОМ
            
            Будут протестированы 3 стратегии:
            1. Sliding Window - скользящее окно (только последние N сообщений)
            2. Sticky Facts - сохранение ключевых фактов
            3. Branching - ветвление диалога
            
            Сценарий: сбор требований к KMP приложению для заметок (12 вопросов)
            """.trimIndent(),
            metadata = "ДЕМОНСТРАЦИЯ СТРАТЕГИЙ"
        )
        delay(2.seconds)

        val apiKey = ApiConfig.getApiKey()
        val model = "openai/gpt-oss-20b:free"

        // Тест 1: Sliding Window - НОВЫЙ агент для каждой стратегии
        addMessage(
            role = "assistant",
            content = "\n" + "━".repeat(60) + "\n🔵 ТЕСТ 1: Sliding Window Strategy\n" + "━".repeat(
                60
            ),
            metadata = "Тест 1/3"
        )
        delay(1.seconds)

        runStrategyTest(
            apiKey = apiKey,
            model = model,
            strategy = ContextStrategyType.SLIDING_WINDOW,
            strategyName = "Sliding Window",
            scenario = StrategyTestScenario.TZQuestions
        )

        delay(2.seconds)

        // Тест 2: Sticky Facts - НОВЫЙ агент
        addMessage(
            role = "assistant",
            content = "\n" + "━".repeat(60) + "\n🟢 ТЕСТ 2: Sticky Facts Strategy\n" + "━".repeat(60),
            metadata = "Тест 2/3"
        )
        delay(1.seconds)

        runStrategyTest(
            apiKey = apiKey,
            model = model,
            strategy = ContextStrategyType.STICKY_FACTS,
            strategyName = "Sticky Facts",
            scenario = StrategyTestScenario.TZQuestions
        )

        delay(2.seconds)

        addMessage(
            role = "assistant",
            content = "\n" + "━".repeat(60) + "\n🟣 ТЕСТ 3: Branching Strategy\n" + "━".repeat(60),
            metadata = "Тест 3/3"
        )
        delay(1.seconds)

        runStrategyTest(
            apiKey = apiKey,
            model = model,
            strategy = ContextStrategyType.BRANCHING,
            strategyName = "Branching",
            scenario = StrategyTestScenario.TZQuestions
        )

        delay(2.seconds)

        addMessage(
            role = "assistant",
            content = """
            
            ${"═".repeat(100)}
            📊 СРАВНИТЕЛЬНЫЙ АНАЛИЗ СТРАТЕГИЙ
            ${"═".repeat(100)}
            
            💡 РЕКОМЕНДАЦИИ:
            • Для коротких диалогов (до 10 сообщений) - любая стратегия подходит
            • Для длинных диалогов (20+ сообщений) - используйте Sticky Facts
            • Для исследовательских задач и A/B тестирования - Branching
            • Если важна предсказуемость расхода токенов - Sliding Window
            
            """.trimIndent(),
            metadata = "Сравнительный анализ"
        )
    }

    private suspend fun runStrategyTest(
        apiKey: String,
        model: String,
        strategy: ContextStrategyType,
        strategyName: String,
        scenario: List<String>
    ) {
        val agent = StrategicLLMAgent(
            apiKey = apiKey,
            model = model,
            systemPrompt = "Ты опытный технический архитектор. Отвечай кратко, по делу, на русском языке."
        )
        agent.setStrategy(strategy)

        addMessage(
            role = "assistant",
            content = "📌 Стратегия: ${agent.getCurrentStrategyName()}\n📝 Начинаем диалог из ${scenario.size} вопросов...",
            metadata = "Начало теста"
        )
        delay(500.milliseconds)

        var cumulativePromptTokens = 0
        var cumulativeCompletionTokens = 0
        var cumulativeTotalTokens = 0

        for ((index, question) in scenario.withIndex()) {
            addMessage(
                role = "user",
                content = question,
                metadata = "[${index + 1}/${scenario.size}]"
            )
            delay(500.milliseconds)

            onTypingStateChanged(true)
            delay(500.milliseconds)

            try {
                val response = agent.processRequest(question)

                updateStats(agent)

                val promptTokens = response.promptTokens ?: 0
                val completionTokens = response.completionTokens ?: 0
                val totalTokens = response.totalTokens ?: 0

                cumulativePromptTokens += promptTokens
                cumulativeCompletionTokens += completionTokens
                cumulativeTotalTokens += totalTokens

                val metadata = buildString {
                    append("📊 Токены: ↑$promptTokens ↓$completionTokens Σ$totalTokens")
                    append(" | Всего за тест: ↑$cumulativePromptTokens ↓$cumulativeCompletionTokens Σ$cumulativeTotalTokens")
                    append(" | Стратегия: ${response.strategyUsed}")
                }

                addMessage(
                    role = "assistant",
                    content = response.content,
                    metadata = metadata,
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                    totalTokens = totalTokens,
                    responseTimeMs = response.responseTimeMs
                )

                if (strategy == ContextStrategyType.STICKY_FACTS && index % 3 == 0 && index > 0) {
                    val facts = agent.getFacts()
                    if (facts.isNotEmpty()) {
                        val factsText = facts.entries.joinToString("\n") {
                            "• ${it.key}: ${it.value.take(80)}"
                        }
                        addMessage(
                            role = "assistant",
                            content = "📝 Извлеченные факты из диалога:\n$factsText",
                            metadata = "Автоматическое извлечение фактов"
                        )
                    }
                }

                delay(500.milliseconds)

            } catch (e: Exception) {
                addMessage(
                    role = "assistant",
                    content = "❌ Ошибка: ${e.message}",
                    metadata = "Ошибка"
                )
            } finally {
                onTypingStateChanged(false)
            }
        }

        addMessage(
            role = "assistant",
            content = """
            
            📊 ИТОГ ТЕСТА ($strategyName)
            ${"─".repeat(40)}
            • Запросов: ${scenario.size}
            • Всего токенов: $cumulativeTotalTokens
            • Prompt: $cumulativePromptTokens | Completion: $cumulativeCompletionTokens
            
            """.trimIndent(),
            metadata = "Итоговая статистика"
        )
    }

    // ============================================================
    // 4. ДЕМОНСТРАЦИЯ ПАМЯТИ
    // ============================================================
    private suspend fun runMemoryDemonstration() {
        addMessage(
            role = "assistant",
            content = """
            🧠 ЗАПУСК ПОЛНОЙ ДЕМОНСТРАЦИИ ТРЕХСЛОЙНОЙ МОДЕЛИ ПАМЯТИ
            
            Эта демонстрация покажет ВСЕ возможности системы памяти:
            
            1. Настройку профиля пользователя
            2. Настройку ограничений проекта
            3. Создание рабочей задачи с контекстом
            4. Добавление решений в рабочую память
            5. Добавление знаний в долговременную память
            6. Полноценный диалог по проекту (8 вопросов)
            7. Работу с контекстом (updateWorkingContext/getWorkingContext)
            8. Сохранение решений в долговременную память
            9. Сравнение ответов С памятью и БЕЗ памяти
            10. Очистку контекста рабочей памяти
            
            Начинаем...
            """.trimIndent(),
            metadata = "ДЕМОНСТРАЦИЯ ПАМЯТИ"
        )
        delay(4.seconds)

        val apiKey = ApiConfig.getApiKey()
        val model = "nvidia/nemotron-3-super-120b-a12b:free"

        // ========== СОЗДАЕМ АГЕНТА С ПАМЯТЬЮ ==========
        val agent = MemoryAwareAgent(
            apiKey = apiKey,
            model = model,
            systemPrompt = "Ты опытный технический архитектор. Отвечай на русском языке, по делу, с примерами кода если нужно.",
            persistToDisk = false
        )

        // ========== ШАГ 1: ПРОФИЛЬ ПОЛЬЗОВАТЕЛЯ ==========
        addMessage(
            role = "assistant",
            content = """
            ${"═".repeat(80)}
            📝 ШАГ 1: НАСТРОЙКА ПРОФИЛЯ ПОЛЬЗОВАТЕЛЯ
            ${"═".repeat(80)}
            
            Сохраняем информацию о пользователе в ДОЛГОВРЕМЕННУЮ ПАМЯТЬ.
            Эти данные будут использоваться во всех диалогах и сохранятся между сессиями.
            """.trimIndent(),
            metadata = "Шаг 1/10"
        )
        delay(3.seconds)

        val profile = UserProfile(
            name = "Алексей",
            experience = "Middle Android разработчик",
            preferredStyle = ResponseStyle.TECHNICAL,
            preferredTech = listOf("Kotlin", "Compose", "KMP", "Ktor", "Flow", "Coroutines"),
            commonGoals = listOf(
                "Изучить разработку агентов",
                "Улучшить архитектуру",
                "Перейти на KMP"
            ),
            customNotes = "Предпочитаю примеры кода и практические решения."
        )
        agent.updateProfile(profile)

        addMessage(
            role = "assistant",
            content = """
            ✅ ПРОФИЛЬ СОХРАНЕН:
            
            👤 Имя: ${profile.name}
            📊 Опыт: ${profile.experience}
            🛠️ Технологии: ${profile.preferredTech.joinToString(", ")}
            🎯 Цели: ${profile.commonGoals.joinToString(", ")}
            📝 Заметки: ${profile.customNotes}
            
            📁 Файл: ~/.llm_memory/profile.md
            """.trimIndent(),
            metadata = "Профиль сохранен"
        )
        delay(4.seconds)

        // ========== ШАГ 2: ОГРАНИЧЕНИЯ ПРОЕКТА ==========
        addMessage(
            role = "assistant",
            content = """
            ${"═".repeat(80)}
            🔧 ШАГ 2: НАСТРОЙКА ОГРАНИЧЕНИЙ ПРОЕКТА
            ${"═".repeat(80)}
            
            Сохраняем технические ограничения в ДОЛГОВРЕМЕННУЮ ПАМЯТЬ.
            Агент будет учитывать их при генерации ответов.
            """.trimIndent(),
            metadata = "Шаг 2/10"
        )
        delay(3.seconds)

        val constraints = ProjectConstraints(
            techStack = listOf("Kotlin", "KMP", "Compose Multiplatform", "Ktor", "SQLDelight"),
            forbiddenTech = listOf("Java", "RxJava", "XML layouts (для новых UI)", "Java Spring"),
            architecture = "MVI с Clean Architecture (data/domain/presentation)",
            codingStandards = "Kotlin Coding Conventions, 4 пробела, максимальная длина строки 120 символов, использование Detekt",
            specialRules = """
            1. Все новые фичи должны иметь модульные тесты
            2. Интеграционные тесты обязательны для API слоя
            3. Код должен быть кроссплатформенным (where possible)
            4. Документация на русском языке для всех public API
            5. Использовать sealed classes для состояния и событий
            """.trimIndent()
        )
        agent.updateConstraints(constraints)

        addMessage(
            role = "assistant",
            content = """
            ✅ ОГРАНИЧЕНИЯ СОХРАНЕНЫ:
            
            📚 Разрешенный стек: ${constraints.techStack.joinToString(", ")}
            🚫 Запрещено: ${constraints.forbiddenTech.joinToString(", ")}
            🏗️ Архитектура: ${constraints.architecture}
            📏 Стандарты: ${constraints.codingStandards.take(80)}...
            📋 Особые правила: ${constraints.specialRules.take(100)}...
            
            📁 Файл: ~/.llm_memory/constraints.md
            """.trimIndent(),
            metadata = "Ограничения сохранены"
        )
        delay(4.seconds)

        // ========== ШАГ 3: ДОБАВЛЕНИЕ ЗНАНИЙ ==========
        addMessage(
            role = "assistant",
            content = """
            ${"═".repeat(80)}
            📚 ШАГ 3: ДОБАВЛЕНИЕ ЗНАНИЙ В БАЗУ ЗНАНИЙ
            ${"═".repeat(80)}
            
            Добавляем знания в Knowledge Base через addKnowledge().
            Эти знания будут доступны агенту во всех диалогах.
            """.trimIndent(),
            metadata = "Шаг 3/10"
        )
        delay(3.seconds)

        agent.addKnowledge(
            "лучшая_практика_kmp",
            "Для KMP проектов используй expect/actual механизм для платформенно-зависимого кода"
        )
        agent.addKnowledge(
            "кодинг_стандарт_проекта",
            "Используем 4 пробела для отступов, максимальная длина строки 120 символов. Запрещены табуляции."
        )
        agent.addKnowledge(
            "архитектурное_решение",
            "Применяем Clean Architecture с разделением на data (репозитории), domain (use cases), presentation (ViewModels + Compose UI)"
        )

        addMessage(
            role = "assistant",
            content = """
            ✅ ЗНАНИЯ ДОБАВЛЕНЫ (${agent.getAllKnowledge().size} записей):
            
            • лучшая_практика_kmp: "${agent.getKnowledge("лучшая_практика_kmp")}"
            • кодинг_стандарт_проекта: "${agent.getKnowledge("кодинг_стандарт_проекта")}"
            • архитектурное_решение: "${agent.getKnowledge("архитектурное_решение")?.take(80)}..."
            
            📁 Файл: ~/.llm_memory/knowledge.md
            """.trimIndent(),
            metadata = "Знания добавлены"
        )
        delay(5.seconds)

        // ========== ШАГ 4: СОЗДАНИЕ ЗАДАЧИ ==========
        addMessage(
            role = "assistant",
            content = """
            ${"═".repeat(80)}
            💼 ШАГ 4: СОЗДАНИЕ ЗАДАЧИ И РАБОЧЕЙ ПАМЯТИ
            ${"═".repeat(80)}
            
            Создаем задачу в РАБОЧЕЙ ПАМЯТИ.
            Рабочая память хранит контекст ТЕКУЩЕЙ задачи и временные решения.
            """.trimIndent(),
            metadata = "Шаг 4/10"
        )
        delay(3.seconds)

        agent.startNewTask(
            taskName = "Разработка чат-бота с трехслойной памятью",
            initialContext = mapOf(
                "требования" to "Чат-бот должен помнить контекст диалога между сессиями",
                "ограничения" to "Использовать только бесплатные модели OpenRouter",
                "срок" to "2 недели на MVP"
            )
        )
        agent.updateTaskState(TaskState.PLANNING)

        addMessage(
            role = "assistant",
            content = """
            ✅ ЗАДАЧА СОЗДАНА:
            
            📋 Название: ${agent.getWorkingMemory().taskName}
            📍 Состояние: ${agent.getWorkingMemory().currentState.displayName}
            🎯 Контекст задачи:
               • требования: ${agent.getWorkingMemory().contextData["требования"]}
               • ограничения: ${agent.getWorkingMemory().contextData["ограничения"]}
               • срок: ${agent.getWorkingMemory().contextData["срок"]}
            
            Рабочая память активна! Агент теперь знает, над чем мы работаем.
            """.trimIndent(),
            metadata = "Задача создана"
        )
        delay(4.seconds)

        // ========== ШАГ 5: КОНТЕКСТ ==========
        addMessage(
            role = "assistant",
            content = """
            ${"═".repeat(80)}
            📝 ШАГ 5: РАБОТА С КОНТЕКСТОМ РАБОЧЕЙ ПАМЯТИ
            ${"═".repeat(80)}
            
            Добавляем динамические данные в контекст через updateWorkingContext().
            Эти данные характеризуют ТЕКУЩЕЕ состояние задачи.
            """.trimIndent(),
            metadata = "Шаг 5/10"
        )
        delay(3.seconds)

        agent.updateWorkingContext("текущий_спринт", "Спринт #42 - Разработка архитектуры памяти")
        agent.updateWorkingContext("дедлайн_спринта", "2026-07-15")
        agent.updateWorkingContext(
            "текущие_задачи",
            "1. Спроектировать MemoryAwareAgent, 2. Написать тесты, 3. Интегрировать с UI"
        )
        agent.updateWorkingContext("блокеры", "Нужно согласование API ключей OpenRouter")
        agent.updateWorkingContext(
            "выполнено",
            "Создан прототип MemoryAwareAgent, реализованы три слоя памяти"
        )

        addMessage(
            role = "assistant",
            content = """
            ✅ КОНТЕКСТ ОБНОВЛЕН (${agent.getWorkingMemory().contextData.size} ключей):
            
            • текущий_спринт = "${agent.getWorkingContext("текущий_спринт")}"
            • дедлайн_спринта = "${agent.getWorkingContext("дедлайн_спринта")}"
            • текущие_задачи = "${agent.getWorkingContext("текущие_задачи")?.take(60)}..."
            • блокеры = "${agent.getWorkingContext("блокеры")}"
            • выполнено = "${agent.getWorkingContext("выполнено")?.take(60)}..."
            """.trimIndent(),
            metadata = "Контекст обновлен"
        )
        delay(5.seconds)

        // ========== ШАГ 6: РЕШЕНИЯ ==========
        addMessage(
            role = "assistant",
            content = """
            ${"═".repeat(80)}
            💡 ШАГ 6: ДОБАВЛЕНИЕ РЕШЕНИЙ В РАБОЧУЮ ПАМЯТЬ
            ${"═".repeat(80)}
            
            Добавляем решения через addDecisionToWorkingMemory().
            Это временные решения для ТЕКУЩЕЙ задачи.
            """.trimIndent(),
            metadata = "Шаг 6/10"
        )
        delay(3.seconds)

        agent.addDecisionToWorkingMemory(
            topic = "Выбор архитектуры",
            decision = "Используем Clean Architecture с тремя слоями: data, domain, presentation",
            context = "Обсудили на техмитинге, выбрали из 3 вариантов"
        )
        agent.addDecisionToWorkingMemory(
            topic = "Управление состоянием",
            decision = "MVI с использованием StateFlow и SharedFlow",
            context = "Выбрали после сравнения с MVVM"
        )
        agent.addDecisionToWorkingMemory(
            topic = "Хранение данных",
            decision = "SQLDelight для кроссплатформенного хранения",
            context = "Room не работает на iOS, SQLDelight - лучший выбор для KMP"
        )

        addMessage(
            role = "assistant",
            content = """
            ✅ РЕШЕНИЯ ДОБАВЛЕНЫ В РАБОЧУЮ ПАМЯТЬ (${agent.getWorkingMemory().decisions.size} решений):
            
            ${
                agent.getWorkingMemory().decisions.joinToString("\n\n") {
                    "   📌 ${it.topic}:\n      Решение: ${it.decision.take(80)}...\n      Контекст: ${it.context}"
                }
            }
            
            ⚠️ Эти решения временные! Они будут потеряны при создании новой задачи.
            Для постоянного хранения используйте saveDecisionToLongTerm().
            """.trimIndent(),
            metadata = "Решения добавлены"
        )
        delay(6.seconds)

        // ========== ШАГ 7: ДИАЛОГ ==========
        addMessage(
            role = "assistant",
            content = """
            ${"═".repeat(80)}
            💬 ШАГ 7: ДИАЛОГ С АГЕНТОМ ПО ПРОЕКТУ
            ${"═".repeat(80)}
            
            Теперь зададим агенту 8 вопросов по проекту.
            Агент будет использовать ВСЕ три слоя памяти.
            """.trimIndent(),
            metadata = "Шаг 7/10"
        )
        delay(4.seconds)

        val projectQuestions = listOf(
            "Какая архитектура лучше всего подойдет для нашего чат-бота с памятью?",
            "Как организовать кроссплатформенное хранение истории диалогов?",
            "Как тестировать агента с памятью? Напиши пример теста.",
            "Какие могут быть проблемы с управлением состоянием в MVI?",
            "Как оптимизировать потребление токенов при длинных диалогах?",
            "Как организовать обработку ошибок при работе с OpenRouter API?",
            "Как сохранять контекст между перезапусками приложения?",
            "Какие best practices для корутин в KMP проекте?"
        )

        for ((index, question) in projectQuestions.withIndex()) {
            addMessage(role = "user", content = question)
            delay(1.seconds)

            onTypingStateChanged(true)
            delay(1.seconds)

            try {
                val response = agent.processRequest(question)

                updateStats(agent)

                val usedMemoryLayers = buildString {
                    val layers = mutableListOf<String>()
                    if (response.memoryUsed.longTermUsed) layers.add("Долговременная")
                    if (response.memoryUsed.workingMemoryUsed) layers.add("Рабочая")
                    if (response.memoryUsed.shortTermUsed) layers.add("Краткосрочная")
                    append("🧠 ${layers.joinToString(" + ")}")
                }

                addMessage(
                    role = "assistant",
                    content = response.content,
                    metadata = "📊 [${index + 1}/${projectQuestions.size}] Токены: ↑${response.promptTokens} ↓${response.completionTokens} Σ${response.totalTokens} | $usedMemoryLayers",
                    promptTokens = response.promptTokens,
                    completionTokens = response.completionTokens,
                    totalTokens = response.totalTokens,
                    responseTimeMs = response.responseTimeMs
                )
            } catch (e: Exception) {
                addMessage(
                    role = "assistant",
                    content = "❌ Ошибка: ${e.message}",
                    metadata = "Ошибка"
                )
            } finally {
                onTypingStateChanged(false)
            }

            delay(2.seconds)
        }

        // ========== ШАГ 8: СОХРАНЕНИЕ РЕШЕНИЙ ==========
        addMessage(
            role = "assistant",
            content = """
            ${"═".repeat(80)}
            💾 ШАГ 8: СОХРАНЕНИЕ РЕШЕНИЙ В ДОЛГОВРЕМЕННУЮ ПАМЯТЬ
            ${"═".repeat(80)}
            
            Сохраняем важные решения через saveDecisionToLongTerm().
            Эти решения сохранятся НАВСЕГДА и будут доступны в следующих сессиях.
            """.trimIndent(),
            metadata = "Шаг 8/10"
        )
        delay(3.seconds)

        agent.saveDecisionToLongTerm(
            topic = "Архитектура финальное решение",
            decision = "Clean Architecture + MVI + трехслойная память (Short-term, Working, Long-term)",
            context = "На основе анализа требований и 8 вопросов из диалога"
        )
        agent.saveDecisionToLongTerm(
            topic = "Хранение данных финальное решение",
            decision = "SQLDelight для истории + Realm для кэша (опционально)",
            context = "Для кроссплатформенности и производительности"
        )
        agent.saveDecisionToLongTerm(
            topic = "Тестирование стратегия",
            decision = "Unit тесты на UseCases и ViewModels + Integration тесты на репозитории",
            context = "Для обеспечения качества"
        )

        addMessage(
            role = "assistant",
            content = """
            ✅ РЕШЕНИЯ СОХРАНЕНЫ В LTM (${agent.getAllDecisions().size} всего):
            
            Новые решения:
            ${
                agent.getAllDecisions().takeLast(3)
                    .joinToString("\n") { "   • ${it.topic}: ${it.decision.take(80)}..." }
            }
            
            📁 Эти решения сохранены в ~/.llm_memory/knowledge.md
            и будут доступны при следующем запуске!
            """.trimIndent(),
            metadata = "Решения сохранены"
        )
        delay(5.seconds)

        // ========== ШАГ 9: СРАВНЕНИЕ ==========
        addMessage(
            role = "assistant",
            content = """
            ${"═".repeat(80)}
            ⚖️ ШАГ 9: СРАВНЕНИЕ - АГЕНТ С ПАМЯТЬЮ VS АГЕНТ БЕЗ ПАМЯТИ
            ${"═".repeat(80)}
            
            Создадим второго агента БЕЗ долговременной памяти и зададим ему тот же вопрос.
            Вы увидите РАЗНИЦУ в ответах!
            """.trimIndent(),
            metadata = "Шаг 9/10"
        )
        delay(4.seconds)

        val agentNoMemory = MemoryAwareAgent(
            apiKey = apiKey,
            model = model,
            systemPrompt = "Ты опытный технический архитектор. Отвечай на русском языке."
        )
        agentNoMemory.useLongTerm = false
        agentNoMemory.useWorkingMemory = false

        // Задаем вопрос агенту С памятью
        addMessage(
            role = "assistant",
            content = "🔵 ЗАПРОС К АГЕНТУ С ПАМЯТЬЮ:",
            metadata = "Вопрос агенту A"
        )
        addMessage(
            role = "user",
            content = "Какой стек технологий я предпочитаю и какие у нас ограничения? Напиши кратко."
        )
        delay(1.seconds)
        onTypingStateChanged(true)
        delay(1.seconds)

        try {
            val responseWithMemory =
                agent.processRequest("Какой стек технологий я предпочитаю и какие у нас ограничения? Напиши кратко.")
            addMessage(
                role = "assistant",
                content = "✅ С ПАМЯТЬЮ:\n\n${responseWithMemory.content}",
                metadata = "📊 Токены: ↑${responseWithMemory.promptTokens} ↓${responseWithMemory.completionTokens} Σ${responseWithMemory.totalTokens}",
                promptTokens = responseWithMemory.promptTokens,
                completionTokens = responseWithMemory.completionTokens,
                totalTokens = responseWithMemory.totalTokens,
                responseTimeMs = responseWithMemory.responseTimeMs
            )
        } catch (e: Exception) {
            addMessage(role = "assistant", content = "❌ Ошибка: ${e.message}")
        } finally {
            onTypingStateChanged(false)
        }
        delay(4.seconds)

        // Задаем вопрос агенту БЕЗ памяти
        addMessage(
            role = "assistant",
            content = "🔴 ЗАПРОС К АГЕНТУ БЕЗ ПАМЯТИ:",
            metadata = "Вопрос агенту Б"
        )
        addMessage(
            role = "user",
            content = "Какой стек технологий я предпочитаю и какие у нас ограничения? Напиши кратко."
        )
        delay(1.seconds)
        onTypingStateChanged(true)
        delay(1.seconds)

        try {
            val responseWithoutMemory =
                agentNoMemory.processRequest("Какой стек технологий я предпочитаю и какие у нас ограничения? Напиши кратко.")
            addMessage(
                role = "assistant",
                content = "❌ БЕЗ ПАМЯТИ:\n\n${responseWithoutMemory.content}",
                metadata = "📊 Токены: ↑${responseWithoutMemory.promptTokens} ↓${responseWithoutMemory.completionTokens} Σ${responseWithoutMemory.totalTokens}",
                promptTokens = responseWithoutMemory.promptTokens,
                completionTokens = responseWithoutMemory.completionTokens,
                totalTokens = responseWithoutMemory.totalTokens,
                responseTimeMs = responseWithoutMemory.responseTimeMs
            )
        } catch (e: Exception) {
            addMessage(role = "assistant", content = "❌ Ошибка: ${e.message}")
        } finally {
            onTypingStateChanged(false)
        }
        delay(4.seconds)

        // ========== ШАГ 10: ОЧИСТКА ==========
        addMessage(
            role = "assistant",
            content = """
            ${"═".repeat(80)}
            🗑️ ШАГ 10: ОЧИСТКА КОНТЕКСТА РАБОЧЕЙ ПАМЯТИ
            ${"═".repeat(80)}
            
            Демонстрация clearWorkingContext() - очистка контекста задачи.
            Рабочая память очищается, но долговременная остается!
            """.trimIndent(),
            metadata = "Шаг 10/10"
        )
        delay(3.seconds)

        agent.clearWorkingContext()

        addMessage(
            role = "assistant",
            content = """
            🗑️ ПОСЛЕ ОЧИСТКИ clearWorkingContext():
            • Контекстных данных: ${agent.getWorkingMemory().contextData.size}
            
            ⚠️ Рабочая память очищена, но ДОЛГОВРЕМЕННАЯ ПАМЯТЬ сохранилась!
            • Профиль: ${agent.getUserProfile().name}
            • Ограничения: ${if (agent.getProjectConstraints().techStack.isNotEmpty()) "сохранены" else "нет"}
            • Знаний: ${agent.getAllKnowledge().size}
            """.trimIndent(),
            metadata = "После очистки"
        )
        delay(5.seconds)

        // ========== ФИНАЛЬНЫЙ ОТЧЕТ ==========
        addMessage(
            role = "assistant",
            content = """
            
            ${"═".repeat(100)}
            📊 ФИНАЛЬНЫЙ ОТЧЕТ ПО ДЕМОНСТРАЦИИ
            ${"═".repeat(100)}
            
            💼 РАБОЧАЯ ПАМЯТЬ (Working Memory):
            • Название задачи: ${agent.getWorkingMemory().taskName}
            • Состояние: ${agent.getWorkingMemory().currentState.displayName}
            • Решений в WM: ${agent.getWorkingMemory().decisions.size}
            • Контекстных данных: ${agent.getWorkingMemory().contextData.size}
            
            📚 ДОЛГОВРЕМЕННАЯ ПАМЯТЬ (Long-term Memory):
            • Профиль: ${agent.getUserProfile().name} (${agent.getUserProfile().experience})
            • Технологии: ${agent.getUserProfile().preferredTech.joinToString(", ")}
            • Сохраненных знаний: ${agent.getAllKnowledge().size}
            • Сохраненных решений: ${agent.getAllDecisions().size}
            
            📊 СТАТИСТИКА ЗАПРОСОВ:
            • Всего запросов: ${agent.getTokenStats().requestCount}
            • Всего токенов: ${agent.getTokenStats().totalTokens}
            
            📁 ФАЙЛЫ СОХРАНЕНЫ В ~/.llm_memory/:
            • profile.md - профиль пользователя
            • constraints.md - ограничения проекта
            • knowledge.md - база знаний и решений
            
            """.trimIndent(),
            metadata = "Финальный отчет"
        )
        delay(6.seconds)

        addMessage(
            role = "assistant",
            content = """
            
            ${"═".repeat(100)}
            🎯 ИТОГОВЫЕ ВЫВОДЫ
            ${"═".repeat(100)}
            
            1️⃣ КРАТКОСРОЧНАЯ ПАМЯТЬ - связность разговора
            2️⃣ РАБОЧАЯ ПАМЯТЬ - контекст текущей задачи
            3️⃣ ДОЛГОВРЕМЕННАЯ ПАМЯТЬ - персонализация и правила
            
            ✨ ДЕМОНСТРАЦИЯ УСПЕШНО ЗАВЕРШЕНА!
            
            """.trimIndent(),
            metadata = "Итоговые выводы"
        )
    }

    // ============================================================
    // 5. ДЕМОНСТРАЦИЯ ПЕРСОНАЛИЗАЦИИ
    // ============================================================
    private suspend fun runPersonalizationDemo() {
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

        val apiKey = ApiConfig.getApiKey()
        val model = "nvidia/nemotron-3-super-120b-a12b:free"

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
            delay(1.seconds)

            val results = mutableListOf<AnswerRecord>()

            for ((qIndex, question) in questions.withIndex()) {
                addMessage(role = "user", content = question)
                delay(500.milliseconds)

                onTypingStateChanged(true)
                delay(500.milliseconds)

                try {
                    val startTime = System.currentTimeMillis()
                    val response = agent.processRequest(question)

                    updateStats(agent)

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
                    addMessage(
                        role = "assistant",
                        content = "❌ Ошибка: ${e.message}",
                        metadata = "Ошибка"
                    )
                } finally {
                    onTypingStateChanged(false)
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

            delay(1.seconds)
        }

        val analysisPrompt = buildAnalysisPrompt(allResults, questions)

        addMessage(
            role = "assistant",
            content = "📝 Отправляем данные на анализ...",
            metadata = "Анализ"
        )
        delay(1.seconds)

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
        delay(1.seconds)

        try {
            val analysis = analysisAgent.processRequest(analysisPrompt)

            addMessage(
                role = "assistant",
                content = """
                🧠 АНАЛИЗ ОТ LLM:
                
                ${analysis.content}
                """.trimIndent(),
                metadata = "Итоговый анализ",
                promptTokens = analysis.promptTokens,
                completionTokens = analysis.completionTokens,
                totalTokens = analysis.totalTokens,
                responseTimeMs = analysis.responseTimeMs
            )

            // Добавляем финальные выводы от LLM
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
                content = """
                💡 КРАТКИЕ ВЫВОДЫ:
                
                ${conclusions.content}
                """.trimIndent(),
                metadata = "Финальные выводы",
                promptTokens = conclusions.promptTokens,
                completionTokens = conclusions.completionTokens,
                totalTokens = conclusions.totalTokens,
                responseTimeMs = conclusions.responseTimeMs
            )

        } catch (e: Exception) {
            addMessage(
                role = "assistant",
                content = "❌ Ошибка при анализе: ${e.message}",
                metadata = "Ошибка"
            )
        } finally {
            onTypingStateChanged(false)
        }
    }

    // ============================================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ============================================================

    // src/main/kotlin/com/llmapp/ui/DemoManager.kt

    private suspend fun addMessage(
        role: String,
        content: String,
        metadata: String? = null,
        promptTokens: Int? = null,
        completionTokens: Int? = null,
        totalTokens: Int? = null,
        responseTimeMs: Long? = null
    ) {
        val message = ChatMessageUI(
            id = UUID.randomUUID().toString(),
            role = role,
            content = content,
            metadata = metadata,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = totalTokens,
            responseTimeMs = responseTimeMs,
            isDemoMessage = true
        )
        onMessageAdded(message)
        delay(300.milliseconds)
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

    private fun updateStats(agent: Any) {
        when (agent) {
            is LLMAgent -> {
                onStatsUpdated?.invoke(agent.getTokenStats())
                onTokenHistoryUpdated?.invoke(agent.getTokenHistory())
                onContextWarningUpdated?.invoke(agent.getContextWarning())
            }

            is CompressedLLMAgent -> {
                onStatsUpdated?.invoke(agent.getTokenStats())
                onTokenHistoryUpdated?.invoke(agent.getTokenHistory())
                onContextWarningUpdated?.invoke(agent.getContextWarning())
            }

            is StrategicLLMAgent -> {
                onStatsUpdated?.invoke(agent.getTokenStats())
                onTokenHistoryUpdated?.invoke(agent.getTokenHistory())
                val stats = agent.getStrategyStats()
                val contextSize = stats.contextSizeTokens
                val warning = when {
                    contextSize > 100000 -> "🔴 КРИТИЧЕСКИ: Контекст $contextSize токенов"
                    contextSize > 70000 -> "⚠️ ВНИМАНИЕ: Контекст $contextSize токенов"
                    else -> "✅ Контекст в порядке: $contextSize токенов"
                }
                onContextWarningUpdated?.invoke(warning)
            }

            is MemoryAwareAgent -> {
                onStatsUpdated?.invoke(agent.getTokenStats())
                val stats = agent.getTokenStats()
                val contextSize = stats.totalTokens
                val warning = when {
                    contextSize > 100000 -> "🔴 КРИТИЧЕСКИ: $contextSize токенов"
                    contextSize > 70000 -> "⚠️ ВНИМАНИЕ: $contextSize токенов"
                    else -> "✅ Контекст в порядке: $contextSize токенов"
                }
                onContextWarningUpdated?.invoke(warning)
                val snapshot = TokenSnapshot(
                    requestNumber = stats.requestCount,
                    promptTokens = stats.totalPromptTokens,
                    completionTokens = stats.totalCompletionTokens,
                    totalTokens = stats.totalTokens,
                    cumulativeTokens = stats.totalTokens,
                    cumulativeCost = stats.estimatedCostUsd,
                    contextUsagePercent = 0.0,
                    timestamp = System.currentTimeMillis(),
                    contextWindowSize = 131072
                )
                onTokenHistoryUpdated?.invoke(listOf(snapshot))
            }

            else -> {
                // fallback
            }
        }
    }

    // ============================================================
    // DATA CLASSES
    // ============================================================

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
}
