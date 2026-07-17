package com.llmapp.demo.assistant

import com.llmapp.assistant.FileAssistantAgent
import com.llmapp.assistant.LocalFileTools
import com.llmapp.demo.manager.BaseDemoRunner
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

/**
 * Демо-раннер для AI Файл-ассистента.
 *
 * Каждый сценарий — это конкретная задача над проектом CalendarKMP,
 * которую агент выполняет автономно: сам планирует, какие файлы читать,
 * искать, создавать.
 *
 * Сценарии:
 * 1) Поиск всех мест использования Result (sealed interface) + анализ паттерна
 * 2) Проверка MVI-архитектуры всех ViewModel (BaseViewModel, State, Event)
 * 3) Генерация документации (README.md) на основе исходников
 */
class FileAssistantRunner(
    onMessageAdded: (ChatMessageUI) -> Unit,
    onTypingStateChanged: (Boolean) -> Unit,
    private val projectPath: String = "/Users/posse/StudioProjects/CalendarKMP",
) : BaseDemoRunner(onMessageAdded, onTypingStateChanged) {

    private lateinit var agent: FileAssistantAgent
    private lateinit var tools: LocalFileTools

    override suspend fun run() {
        agent = FileAssistantAgent(
            projectPath = projectPath,
            onProgress = { _ ->
                // Прогресс-обновления видны в статус-баре DemoManager
            }
        )
        tools = LocalFileTools(projectPath)

        // ═══════════════════════════════════════════
        // ШАГ 1: Вступление + обзор проекта
        // ═══════════════════════════════════════════
        addMessage("assistant", buildString {
            appendLine("**AI Файл-ассистент — Демонстрация**")
            appendLine()
            appendLine("Агент работает с локальными файлами проекта **CalendarKMP**.")
            appendLine("Каждый сценарий — это задача, которую агент выполняет автономно:")
            appendLine("сам читает файлы, ищет, анализирует, создаёт документацию.")
            appendLine()
            appendLine("**Проект:** `$projectPath`")
        }, isDemoMessage = true)
        delay(2.seconds)

        // Обзор проекта через тулзы
        addMessage("assistant", "Обнаруживаю структуру проекта...", isDemoMessage = true)
        val allFiles = withContext(Dispatchers.IO) { tools.listFiles() }
        val stats = withContext(Dispatchers.IO) { tools.projectStats() }

        addMessage("assistant", buildString {
            appendLine("**Структура проекта:** ${allFiles.size} файлов")
            appendLine()
            stats.entries.sortedByDescending { it.value.second }.take(6).forEach { (ext, pair) ->
                appendLine("- .$ext: ${pair.first} файлов")
            }
        }, isDemoMessage = true)
        delay(2.seconds)

        // ═══════════════════════════════════════════
        // СЦЕНАРИЙ 1: Поиск использований Result
        // ═══════════════════════════════════════════
        addMessage("assistant", buildString {
            appendLine("---")
            appendLine()
            appendLine("**Сценарий 1: Анализ паттерна Result**")
            appendLine()
            appendLine("Задача агенту: *\"Найди все использования sealed interface Result в проекте, определи где он определён, где возвращается из методов, где обрабатывается через onSuccess/onError, и дай анализ покрытия паттерна\"*")
            appendLine()
            appendLine("Агент начинает работу...")
        }, isDemoMessage = true)
        delay(1.seconds)

        val task1 = "Найди в проекте CalendarKMP всё, что связано с sealed interface Result. " +
                "Выполни: 1) найди файл где определён Result (common/domain/utils/Result.kt), " +
                "прочитай его, " +
                "2) найди все методы которые возвращают Result или EmptyResult — посчитай их количество, " +
                "3) найди все места где вызывается onSuccess/onError/map/asEmptyDataResult — " +
                "перечисли 10-15 ключевых мест, " +
                "4) определи есть ли методы, которые возвращают raw Result без обёртки " +
                "(нарушают контракт). " +
                "Дай итоговый анализ: сколько методов используют Result, процент покрытия, " +
                "и рекомендации по улучшению."

        val result1 = withContext(Dispatchers.IO) {
            agent.executeTask(task1)
        }

        addMessage("assistant", buildString {
            appendLine("**Результат Сценария 1:**")
            appendLine()
            appendLine(result1)
        }, isDemoMessage = true)
        delay(3.seconds)

        // ═══════════════════════════════════════════
        // СЦЕНАРИЙ 2: Проверка MVI-архитектуры
        // ═══════════════════════════════════════════
        addMessage("assistant", buildString {
            appendLine("---")
            appendLine()
            appendLine("**Сценарий 2: Проверка MVI-архитектуры ViewModel**")
            appendLine()
            appendLine("Задача агенту: *\"Найди все ViewModel в проекте, проверь каждый на соответствие MVI-паттерну: наследование BaseViewModel, State как data class, Event как sealed interface. Сгенерируй отчёт\"*")
            appendLine()
            appendLine("Агент начинает работу...")
        }, isDemoMessage = true)
        delay(1.seconds)

        val task2 = "Проверь все ViewModel в проекте CalendarKMP на соответствие MVI-архитектуре. " +
                "Выполни: 1) найди все файлы с 'ViewModel' в названии, " +
                "2) для каждого определи: наследует ли BaseViewModel (common/presentation), " +
                "есть ли State как data class с default-значениями, " +
                "Event как sealed interface/class, " +
                "3) найди BaseViewModel — прочитай и опиши его контракт " +
                "(viewStates, viewActions, obtainEvent, withViewModelScope), " +
                "4) для каждого ViewModel укажи пройдена ли проверка (✅/❌). " +
                "Дай итоговую статистику: сколько ViewModel, сколько соответствует, " +
                "какие нарушения встречаются чаще всего."

        val result2 = withContext(Dispatchers.IO) {
            agent.executeTask(task2)
        }

        addMessage("assistant", buildString {
            appendLine("**Результат Сценария 2:**")
            appendLine()
            appendLine(result2)
        }, isDemoMessage = true)
        delay(3.seconds)

        // ═══════════════════════════════════════════
        // СЦЕНАРИЙ 3: Генерация документации
        // ═══════════════════════════════════════════
        addMessage("assistant", buildString {
            appendLine("---")
            appendLine()
            appendLine("**Сценарий 3: Генерация документации**")
            appendLine()
            appendLine("Задача агенту: *\"Прочитай ключевые файлы проекта (build.gradle, main ViewModel, Repository, DI-модули) и сгенерируй README.md. Сохрани его в docs/GENERATED_README.md\"*")
            appendLine()
            appendLine("Агент начинает работу...")
        }, isDemoMessage = true)
        delay(1.seconds)

        val task3 = "Прочитай ключевые файлы проекта CalendarKMP: " +
                "1) build.gradle.kts (корневой и модульные), settings.gradle.kts, " +
                "libs.versions.toml — определи используемые библиотеки и платформы, " +
                "2) основные ViewModel (MainViewModel, SettingsViewModel и др.), " +
                "3) Repository-файлы (CalendarRepository, AuthRepository и др.), " +
                "4) DI-модули (Koin module definitions). " +
                "На основе проанализированных исходников сгенерируй README.md на русском языке. " +
                "README должен содержать: описание проекта, технологии, архитектуру (Clean Architecture + MVI), " +
                "структуру проекта по модулям, описание ключевых компонентов, инструкцию по запуску. " +
                "Сохрани результат в файл docs/GENERATED_README.md"

        val result3 = withContext(Dispatchers.IO) {
            agent.executeTask(task3)
        }

        addMessage("assistant", buildString {
            appendLine("**Результат Сценария 3:**")
            appendLine()
            appendLine(result3)
        }, isDemoMessage = true)
        delay(3.seconds)

        // ═══════════════════════════════════════════
        // ИТОГИ (через LLM)
        // ═══════════════════════════════════════════
        addMessage("assistant", "Подводжу итоги...", isDemoMessage = true)

        val summaryTask = buildString {
            appendLine("Ты — AI-ассистент. Три сценария были выполнены над проектом CalendarKMP.")
            appendLine("Вот результаты каждого сценария:")
            appendLine()
            appendLine("=== СЦЕНАРИЙ 1: Анализ Result ===")
            appendLine(result1.take(3000))
            appendLine()
            appendLine("=== СЦЕНАРИЙ 2: Проверка MVI ===")
            appendLine(result2.take(3000))
            appendLine()
            appendLine("=== СЦЕНАРИЙ 3: Генерация README ===")
            appendLine(result3.take(3000))
            appendLine()
            appendLine("Составь краткий итоговый отчёт (5-10 предложений) на русском языке:")
            appendLine("- Что удалось найти/сделать в каждом сценарии")
            appendLine("- Ключевые цифры (количество файлов, методов, проверок)")
            appendLine("- Общая оценка работы агента")
            appendLine("- Вывод: какие задачи агент решает хорошо, какие требуют улучшений")
        }

        val summaryResult = withContext(Dispatchers.IO) {
            agent.executeTask(summaryTask)
        }

        addMessage("assistant", buildString {
            appendLine("**Итоги демонстрации**")
            appendLine()
            appendLine(summaryResult)
            appendLine()
            appendLine("---")
            appendLine("Для интерактивного использования: `/file` в чате.")
        }, isDemoMessage = true)
    }
}
