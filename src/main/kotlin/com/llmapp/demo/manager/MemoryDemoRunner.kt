package com.llmapp.demo.manager

import com.llmapp.agent.MemoryAwareAgent
import com.llmapp.api.ApiConfig
import com.llmapp.memory.ProjectConstraints
import com.llmapp.memory.ResponseStyle
import com.llmapp.memory.TaskState
import com.llmapp.memory.UserProfile
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Демонстрация трехслойной модели памяти
 */
class MemoryDemoRunner(
    onMessageAdded: (ChatMessageUI) -> Unit,
    onTypingStateChanged: (Boolean) -> Unit
) : BaseDemoRunner(onMessageAdded, onTypingStateChanged) {

    override suspend fun run() {
        val apiKey = ApiConfig.getApiKey()
        val model = "nvidia/nemotron-3-super-120b-a12b:free"

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
            """.trimIndent(),
            metadata = "ДЕМОНСТРАЦИЯ ПАМЯТИ"
        )
        delay(4.seconds)

        val agent = MemoryAwareAgent(
            apiKey = apiKey,
            model = model,
            systemPrompt = "Ты опытный технический архитектор. Отвечай на русском языке, по делу, с примерами кода если нужно.",
            persistToDisk = false
        )

        // ========== ШАГ 1: ПРОФИЛЬ ==========
        addMessage(
            role = "assistant",
            content = """
            ${"=".repeat(80)}
            📝 ШАГ 1: НАСТРОЙКА ПРОФИЛЯ ПОЛЬЗОВАТЕЛЯ
            ${"=".repeat(80)}
            
            Сохраняем информацию о пользователе в ДОЛГОВРЕМЕННУЮ ПАМЯТЬ.
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
            """.trimIndent(),
            metadata = "Профиль сохранен"
        )
        delay(4.seconds)

        // ========== ШАГ 2: ОГРАНИЧЕНИЯ ==========
        addMessage(
            role = "assistant",
            content = """
            ${"=".repeat(80)}
            🔧 ШАГ 2: НАСТРОЙКА ОГРАНИЧЕНИЙ ПРОЕКТА
            ${"=".repeat(80)}
            
            Сохраняем технические ограничения в ДОЛГОВРЕМЕННУЮ ПАМЯТЬ.
            """.trimIndent(),
            metadata = "Шаг 2/10"
        )
        delay(3.seconds)

        val constraints = ProjectConstraints(
            techStack = listOf("Kotlin", "KMP", "Compose Multiplatform", "Ktor", "SQLDelight"),
            forbiddenTech = listOf("Java", "RxJava", "XML layouts (для новых UI)", "Java Spring"),
            architecture = "MVI с Clean Architecture (data/domain/presentation)",
            codingStandards = "Kotlin Coding Conventions, 4 пробела, максимальная длина строки 120 символов, использование Detekt",
            specialRules = "1. Все новые фичи должны иметь модульные тесты"
        )
        agent.updateConstraints(constraints)

        addMessage(
            role = "assistant",
            content = """
            ✅ ОГРАНИЧЕНИЯ СОХРАНЕНЫ:
            
            📚 Разрешенный стек: ${constraints.techStack.joinToString(", ")}
            🚫 Запрещено: ${constraints.forbiddenTech.joinToString(", ")}
            🏗️ Архитектура: ${constraints.architecture}
            📋 Особые правила: ${constraints.specialRules.take(100)}...
            """.trimIndent(),
            metadata = "Ограничения сохранены"
        )
        delay(4.seconds)

        // ========== ШАГ 3: ЗНАНИЯ ==========
        addMessage(
            role = "assistant",
            content = """
            ${"=".repeat(80)}
            📚 ШАГ 3: ДОБАВЛЕНИЕ ЗНАНИЙ В БАЗУ ЗНАНИЙ
            ${"=".repeat(80)}
            
            Добавляем знания в Knowledge Base через addKnowledge().
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
            """.trimIndent(),
            metadata = "Знания добавлены"
        )
        delay(5.seconds)

        // ========== ШАГ 4: ЗАДАЧА ==========
        addMessage(
            role = "assistant",
            content = """
            ${"=".repeat(80)}
            💼 ШАГ 4: СОЗДАНИЕ ЗАДАЧИ И РАБОЧЕЙ ПАМЯТИ
            ${"=".repeat(80)}
            
            Создаем задачу в РАБОЧЕЙ ПАМЯТИ.
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
            """.trimIndent(),
            metadata = "Задача создана"
        )
        delay(4.seconds)

        // ========== ШАГ 5: КОНТЕКСТ ==========
        addMessage(
            role = "assistant",
            content = """
            ${"=".repeat(80)}
            📝 ШАГ 5: РАБОТА С КОНТЕКСТОМ РАБОЧЕЙ ПАМЯТИ
            ${"=".repeat(80)}
            
            Добавляем динамические данные в контекст через updateWorkingContext().
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
            ${"=".repeat(80)}
            💡 ШАГ 6: ДОБАВЛЕНИЕ РЕШЕНИЙ В РАБОЧУЮ ПАМЯТЬ
            ${"=".repeat(80)}
            
            Добавляем решения через addDecisionToWorkingMemory().
            """.trimIndent(),
            metadata = "Шаг 6/10"
        )
        delay(3.seconds)

        agent.addDecisionToWorkingMemory(
            "Выбор архитектуры",
            "Используем Clean Architecture с тремя слоями: data, domain, presentation",
            "Обсудили на техмитинге"
        )
        agent.addDecisionToWorkingMemory(
            "Управление состоянием",
            "MVI с использованием StateFlow и SharedFlow",
            "Выбрали после сравнения с MVVM"
        )
        agent.addDecisionToWorkingMemory(
            "Хранение данных",
            "SQLDelight для кроссплатформенного хранения",
            "Room не работает на iOS"
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
            
            ⚠️ Эти решения временные! Для постоянного хранения используйте saveDecisionToLongTerm().
            """.trimIndent(),
            metadata = "Решения добавлены"
        )
        delay(6.seconds)

        // ========== ШАГ 7: ДИАЛОГ ==========
        addMessage(
            role = "assistant",
            content = """
            ${"=".repeat(80)}
            💬 ШАГ 7: ДИАЛОГ С АГЕНТОМ ПО ПРОЕКТУ
            ${"=".repeat(80)}
            
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
            delayMedium()
            onTypingStateChanged(true)
            delayMedium()

            try {
                val response = agent.processRequest(question)
                onTypingStateChanged(false)

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
                onTypingStateChanged(false)
                addMessage(
                    role = "assistant",
                    content = "❌ Ошибка: ${e.message}",
                    metadata = "Ошибка"
                )
            }
            delay(2.seconds)
        }

        // ========== ШАГ 8: СОХРАНЕНИЕ РЕШЕНИЙ ==========
        addMessage(
            role = "assistant",
            content = """
            ${"=".repeat(80)}
            💾 ШАГ 8: СОХРАНЕНИЕ РЕШЕНИЙ В ДОЛГОВРЕМЕННУЮ ПАМЯТЬ
            ${"=".repeat(80)}
            
            Сохраняем важные решения через saveDecisionToLongTerm().
            """.trimIndent(),
            metadata = "Шаг 8/10"
        )
        delay(3.seconds)

        agent.saveDecisionToLongTerm(
            "Архитектура финальное решение",
            "Clean Architecture + MVI + трехслойная память",
            "На основе анализа требований"
        )
        agent.saveDecisionToLongTerm(
            "Хранение данных финальное решение",
            "SQLDelight для истории + Realm для кэша",
            "Для кроссплатформенности"
        )
        agent.saveDecisionToLongTerm(
            "Тестирование стратегия",
            "Unit тесты на UseCases и ViewModels + Integration тесты",
            "Для обеспечения качества"
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
            """.trimIndent(),
            metadata = "Решения сохранены"
        )
        delay(5.seconds)

        // ========== ШАГ 9: СРАВНЕНИЕ ==========
        addMessage(
            role = "assistant",
            content = """
            ${"=".repeat(80)}
            ⚖️ ШАГ 9: СРАВНЕНИЕ - АГЕНТ С ПАМЯТЬЮ VS АГЕНТ БЕЗ ПАМЯТИ
            ${"=".repeat(80)}
            
            Создадим второго агента БЕЗ долговременной памяти и зададим ему тот же вопрос.
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
        delayMedium()
        onTypingStateChanged(true)
        delayMedium()

        try {
            val responseWithMemory =
                agent.processRequest("Какой стек технологий я предпочитаю и какие у нас ограничения? Напиши кратко.")
            onTypingStateChanged(false)
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
            onTypingStateChanged(false)
            addMessage(role = "assistant", content = "❌ Ошибка: ${e.message}")
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
        delayMedium()
        onTypingStateChanged(true)
        delayMedium()

        try {
            val responseWithoutMemory =
                agentNoMemory.processRequest("Какой стек технологий я предпочитаю и какие у нас ограничения? Напиши кратко.")
            onTypingStateChanged(false)
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
            onTypingStateChanged(false)
            addMessage(role = "assistant", content = "❌ Ошибка: ${e.message}")
        }
        delay(4.seconds)

        // ========== ШАГ 10: ОЧИСТКА ==========
        addMessage(
            role = "assistant",
            content = """
            ${"=".repeat(80)}
            🗑️ ШАГ 10: ОЧИСТКА КОНТЕКСТА РАБОЧЕЙ ПАМЯТИ
            ${"=".repeat(80)}
            
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
            ${"=".repeat(100)}
            📊 ФИНАЛЬНЫЙ ОТЧЕТ ПО ДЕМОНСТРАЦИИ
            ${"=".repeat(100)}
            
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
            """.trimIndent(),
            metadata = "Финальный отчет"
        )
        delay(6.seconds)

        addMessage(
            role = "assistant",
            content = """
            ${"=".repeat(100)}
            🎯 ИТОГОВЫЕ ВЫВОДЫ
            ${"=".repeat(100)}
            
            1️⃣ КРАТКОСРОЧНАЯ ПАМЯТЬ - связность разговора
            2️⃣ РАБОЧАЯ ПАМЯТЬ - контекст текущей задачи
            3️⃣ ДОЛГОВРЕМЕННАЯ ПАМЯТЬ - персонализация и правила
            
            ✨ ДЕМОНСТРАЦИЯ УСПЕШНО ЗАВЕРШЕНА!
            """.trimIndent(),
            metadata = "Итоговые выводы"
        )
    }
}
