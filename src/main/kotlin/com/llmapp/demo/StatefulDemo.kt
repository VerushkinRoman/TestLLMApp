package com.llmapp.demo

import com.llmapp.agent.StatefulMemoryAgent
import com.llmapp.memory.ProjectConstraints
import com.llmapp.memory.ResponseStyle
import com.llmapp.memory.UserProfile
import com.llmapp.state.TaskPhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

class StatefulDemo {

    suspend fun runFullDemo() {
        println("=".repeat(100))
        println("🧠 ПОЛНАЯ ДЕМОНСТРАЦИЯ STATEFUL АГЕНТА")
        println("=".repeat(100))

        val agent = StatefulMemoryAgent()

        // ============================================================
        // ШАГ 1: НАСТРОЙКА ПРОФИЛЯ И ОГРАНИЧЕНИЙ
        // ============================================================
        println("\n📌 ШАГ 1: Настройка профиля и ограничений")
        println("-".repeat(60))

        val profile = UserProfile(
            name = "Алексей",
            experience = "Middle Android разработчик, 4 года",
            preferredStyle = ResponseStyle.TECHNICAL,
            preferredTech = listOf("Kotlin", "Compose", "KMP", "Ktor", "Coroutines"),
            commonGoals = listOf("Изучить агентные системы", "Улучшить архитектуру"),
            customNotes = "Предпочитаю примеры кода и практические решения"
        )
        agent.updateProfile(profile)
        println("✅ Профиль сохранен: ${profile.name}")

        val constraints = ProjectConstraints(
            techStack = listOf("Kotlin", "KMP", "Compose Multiplatform"),
            forbiddenTech = listOf("Java", "RxJava", "XML layouts"),
            architecture = "MVI + Clean Architecture",
            codingStandards = "Kotlin Coding Conventions, Detekt",
            specialRules = "Все новые фичи должны иметь модульные тесты"
        )
        agent.updateConstraints(constraints)
        println("✅ Ограничения сохранены")

        delay(1.seconds)

        // ============================================================
        // ШАГ 2: СОЗДАНИЕ ЗАДАЧИ
        // ============================================================
        println("\n📌 ШАГ 2: Создание задачи")
        println("-".repeat(60))

        val task = agent.createTask(
            taskName = "Разработка чат-бота с памятью",
            description = "Чат-бот с трехслойной памятью и state machine",
            initialContext = mapOf(
                "требования" to "Кроссплатформенность, память, state machine",
                "срок" to "2 недели"
            )
        )

        println("✅ Задача создана:")
        println("   📋 ${task.taskName}")
        println("   📍 Фаза: ${task.phase.displayName}")
        println("   📌 Шаг: ${task.step}")
        println("   🎯 Ожидается: ${task.expectedAction}")

        delay(2.seconds)

        println("\n📌 Проверка доступных переходов:")
        val available = agent.getAvailableTransitions()
        println("   Доступные переходы из ${agent.getPhase().displayName}:")
        available.forEach { phase ->
            println("      → ${phase.displayName}")
        }

        // ============================================================
        // ШАГ 3: РАБОТА В ФАЗЕ INIT
        // ============================================================
        println("\n📌 ШАГ 3: Сбор требований (фаза INIT)")
        println("-".repeat(60))

        val response1 = agent.processRequest(
            "Давайте спроектируем архитектуру. Нужны UseCases, репозитории и DI."
        )
        println("👤: Давайте спроектируем архитектуру...")
        println("🤖: ${response1.content.take(150)}...")
        println("📊 Фаза: ${response1.taskState.phase.displayName}")
        println("🎯 Ожидается: ${response1.taskState.expectedAction}")

        // Обновляем шаг вручную
        agent.updateStep("Обсуждение архитектуры")
        agent.updateExpectedAction("Утвердите план или уточните детали")
        println("\n📌 Обновлен шаг: ${agent.getStep()}")

        delay(2.seconds)

        println("\n📌 Проверка возможности перехода:")
        val canTransition = agent.canTransitionTo(TaskPhase.EXECUTION)
        println("   Можно перейти в EXECUTION? ${if (canTransition) "✅ Да" else "❌ Нет"}")

        // ============================================================
        // ШАГ 4: ПЕРЕХОД В PLANNING
        // ============================================================
        println("\n📌 ШАГ 4: Переход в PLANNING")
        println("-".repeat(60))

        val transition1 = agent.transitionTo(TaskPhase.PLANNING, "Требования собраны")
        println("🔄 ${transition1.message}")
        println("📊 Текущая фаза: ${agent.getPhase().displayName}")

        delay(1.seconds)

        // Работа в PLANNING
        val response2 = agent.processRequest(
            "Нужен план разработки. Какие модули будем делать?"
        )
        println("👤: Нужен план разработки...")
        println("🤖: ${response2.content.take(150)}...")
        println("📊 Фаза: ${response2.taskState.phase.displayName}")

        // Сохраняем решение
        agent.saveDecisionToLongTerm(
            topic = "Модули",
            decision = "Будем делать 4 модуля: core, data, domain, presentation",
            context = "На основе обсуждения архитектуры"
        )
        println("💾 Решение сохранено в LTM")

        delay(2.seconds)

        // ============================================================
        // ШАГ 5: ПАУЗА
        // ============================================================
        println("\n📌 ШАГ 5: Пауза")
        println("-".repeat(60))

        val pauseResult = agent.pause("Нужно обсудить план с командой")
        println("⏸️ ${pauseResult.message}")

        // Проверяем состояние паузы
        println("📊 isPaused: ${agent.isPaused()}")
        println("📊 Причина: ${agent.getPauseReason()}")

        // Пытаемся отправить запрос на паузе
        val responsePaused = agent.processRequest("Продолжим планирование")
        println("\n👤: Продолжим планирование")
        println("🤖: ${responsePaused.content.take(100)}...")

        delay(2.seconds)

        // ============================================================
        // ШАГ 5.5: БЛОКИРОВКА
        // ============================================================
        println("\n📌 ШАГ 5.5: Демонстрация блокировки")
        println("-".repeat(60))

        val blockResult = agent.block("Нужно согласовать API ключи с командой")
        println("🚫 ${blockResult.message}")
        println("📊 Текущая фаза: ${agent.getPhase().displayName}")

        // Пытаемся отправить запрос в заблокированном состоянии
        println("\n👤: Заблокировано?")
        val blockedResponse = agent.processRequest("Можем ли мы продолжить?")
        println("🤖: ${blockedResponse.content.take(100)}...")
        println("📊 Фаза: ${blockedResponse.taskState.phase.displayName}")

        delay(2.seconds)

        // Разблокировка
        println("\n🔓 Разблокировка...")
        val unblockResult = agent.unblock()
        println("🔓 ${unblockResult.message}")
        println("📊 Текущая фаза: ${agent.getPhase().displayName}")

        delay(2.seconds)

        // ============================================================
        // ШАГ 6: ВОЗОБНОВЛЕНИЕ
        // ============================================================
        println("\n📌 ШАГ 6: Возобновление")
        println("-".repeat(60))

        val resumeResult = agent.resume()
        println("▶️ ${resumeResult.message}")
        println("📊 Текущая фаза: ${agent.getPhase().displayName}")

        // Продолжаем диалог
        val response3 = agent.processRequest(
            "Продолжим. Какой план по модулям? Нужно утвердить."
        )
        println("👤: Продолжим. Какой план по модулям?")
        println("🤖: ${response3.content.take(150)}...")
        println("📊 Фаза: ${response3.taskState.phase.displayName}")

        delay(2.seconds)

        // ============================================================
        // ШАГ 7: ПЕРЕХОД В EXECUTION
        // ============================================================
        println("\n📌 ШАГ 7: Переход в EXECUTION")
        println("-".repeat(60))

        val transition2 = agent.transitionTo(TaskPhase.EXECUTION, "План утвержден")
        println("🔄 ${transition2.message}")

        // Работа в EXECUTION
        val response4 = agent.processRequest(
            "Начинаем писать код. Создай базовую структуру UseCase'ов."
        )
        println("👤: Начинаем писать код...")
        println("🤖: ${response4.content.take(150)}...")
        println("📊 Фаза: ${response4.taskState.phase.displayName}")

        // Добавляем знание
        agent.addKnowledge(
            "use_case_pattern",
            "UseCase должен быть одноразовым, принимать параметры и возвращать Result"
        )
        println("📚 Знание добавлено")

        delay(2.seconds)

        // ============================================================
        // ШАГ 8: ОБНОВЛЕНИЕ КОНТЕКСТА
        // ============================================================
        println("\n📌 ШАГ 8: Обновление контекста")
        println("-".repeat(60))

        agent.updateContext("текущий_модуль", "domain")
        agent.updateContext("кол_во_юзкейсов", "5")
        agent.updateContext("статус", "в процессе")

        println("📝 Контекст обновлен:")
        println("   • текущий_модуль: ${agent.getContext("текущий_модуль")}")
        println("   • кол_во_юзкейсов: ${agent.getContext("кол_во_юзкейсов")}")
        println("   • статус: ${agent.getContext("статус")}")

        delay(1.seconds)

        // ============================================================
        // ШАГ 9: СНИМКИ
        // ============================================================
        println("\n📌 ШАГ 9: Работа со снимками")
        println("-".repeat(60))

        val snapshotId = agent.createSnapshot("before_validation")
        println("📸 Снимок создан: $snapshotId")

        println("\n📸 Все снимки:")
        agent.getSnapshots().forEach { (name, desc) ->
            println("   • $name: $desc")
        }

        // Детали снимка
        println("\n📄 Детали снимка:")
        println(agent.getSnapshotDetails(snapshotId))

        delay(2.seconds)

        // ============================================================
        // ШАГ 10: ПЕРЕХОД В VALIDATION
        // ============================================================
        println("\n📌 ШАГ 10: Переход в VALIDATION")
        println("-".repeat(60))

        val transition3 = agent.transitionTo(TaskPhase.VALIDATION, "Код написан")
        println("🔄 ${transition3.message}")

        val response5 = agent.processRequest(
            "Проверь код UseCase'ов. Все ли правильно?"
        )
        println("👤: Проверь код...")
        println("🤖: ${response5.content.take(150)}...")
        println("📊 Фаза: ${response5.taskState.phase.displayName}")

        // Проверяем прогресс
        println("📊 Прогресс: ${"%.0f".format(agent.getProgress() * 100)}%")

        delay(2.seconds)

        // ============================================================
        // ШАГ 11: ЗАВЕРШЕНИЕ
        // ============================================================
        println("\n📌 ШАГ 11: Завершение задачи")
        println("-".repeat(60))

        // Автоматический переход через ответ
        val response6 = agent.processRequest(
            "Все работает, проверка пройдена. Завершаем."
        )
        println("👤: Все работает, проверка пройдена...")
        println("🤖: ${response6.content.take(150)}...")
        println("📊 Фаза: ${response6.taskState.phase.displayName}")

        if (response6.transitionResult != null) {
            println("🔄 ${response6.transitionResult.message}")
        }

        println("\n✅ Задача завершена: ${agent.isTaskComplete()}")

        // ============================================================
        // ШАГ 12: ВОССТАНОВЛЕНИЕ ИЗ СНИМКА
        // ============================================================
        println("\n📌 ШАГ 12: Восстановление из снимка")
        println("-".repeat(60))

        // Находим снимок до валидации
        val snapshots = agent.getSnapshots()
        val validationSnapshot = snapshots.find { it.first.contains("before_validation") }

        if (validationSnapshot != null) {
            println("📸 Восстанавливаем снимок: ${validationSnapshot.first}")
            val restored = agent.restoreFromSnapshot(validationSnapshot.first)
            if (restored) {
                println("✅ Восстановлено!")
                println("📊 Текущая фаза: ${agent.getPhase().displayName}")
                println("📌 Шаг: ${agent.getStep()}")
                println("🎯 Ожидается: ${agent.getExpectedAction()}")
            }
        }

        delay(2.seconds)

        // ============================================================
        // ШАГ 13: СМЕНА МОДЕЛИ
        // ============================================================
        println("\n📌 ШАГ 13: Смена модели")
        println("-".repeat(60))

        agent.changeModel("google/gemma-4-26b-a4b-it:free")
        println("🔄 Модель изменена на: google/gemma-4-26b-a4b-it:free")

        // ============================================================
        // ШАГ 14: ПОЛНЫЙ СТАТУС
        // ============================================================
        println("\n📌 ШАГ 14: Полный статус агента")
        println("-".repeat(60))

        println("\n${agent.getFullStatus()}")

        // ============================================================
        // ШАГ 15: СТАТИСТИКА ПАМЯТИ
        // ============================================================
        println("\n📌 ШАГ 15: Статистика памяти")
        println("-".repeat(60))

        println(agent.getMemoryStats())

        // ============================================================
        // ИТОГИ
        // ============================================================
        println("\n" + "=".repeat(100))
        println("🎯 ИТОГИ ДЕМОНСТРАЦИИ")
        println("=".repeat(100))

        println(
            """
            
            ✅ Все методы задействованы:
            
            📋 Управление задачей:
               • createTask() - создание задачи
               • getCurrentTaskState() - получение состояния
               • getPhase() / getStep() / getExpectedAction() - текущие параметры
               • isPaused() / getPauseReason() - статус паузы
               • getProgress() / isTaskComplete() - прогресс и завершение
            
            🔄 Переходы:
               • transitionTo() - ручной переход
               • pause() / resume() - пауза и возобновление
               • block() / unblock() - блокировка и разблокировка
            
            📝 Контекст:
               • updateStep() / updateExpectedAction() - обновление параметров
               • updateContext() / getContext() / getAllContext() - работа с контекстом
            
            📸 Снимки:
               • createSnapshot() / restoreFromSnapshot() - сохранение и восстановление
               • getSnapshots() - список снимков
               • getSnapshotDetails() - детали снимка
            
            🧠 Память:
               • updateProfile() / getUserProfile() - профиль
               • updateConstraints() / getProjectConstraints() - ограничения
               • addKnowledge() / getAllKnowledge() - знания
               • saveDecisionToLongTerm() / getAllDecisions() - решения
               • getWorkingMemory() - рабочая память
               • clearWorkingMemory() / resetWorkingMemory() - очистка памяти
            
            ⚙️ Системные:
               • changeModel() - смена модели
               • getTokenStats() - статистика токенов
               • getFullStatus() - полный статус
               • getMemoryStats() - статистика памяти
            
        """.trimIndent()
        )

        println("✨ Демонстрация успешно завершена!")
    }
}

fun main() = runBlocking {
    try {
        val demo = StatefulDemo()
        demo.runFullDemo()
    } catch (e: Exception) {
        println("\n❌ Ошибка: ${e.message}")
        e.printStackTrace()
    }
}
