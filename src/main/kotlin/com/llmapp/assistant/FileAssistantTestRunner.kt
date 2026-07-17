package com.llmapp.assistant

import kotlinx.coroutines.runBlocking

private const val PROJECT_PATH = "/Users/posse/StudioProjects/CalendarKMP"

private val SCENARIOS = listOf(
    "Сценарий 1: Анализ Result" to
            "Найди в проекте CalendarKMP всё, что связано с sealed interface Result. " +
            "Выполни: 1) найди файл где определён Result (common/domain/utils/Result.kt), " +
            "прочитай его, " +
            "2) найди все методы которые возвращают Result или EmptyResult — посчитай их количество, " +
            "3) найди все места где вызывается onSuccess/onError/map/asEmptyDataResult — " +
            "перечисли 10-15 ключевых мест, " +
            "4) определи есть ли методы, которые возвращают raw Result без обёртки " +
            "(нарушают контракт). " +
            "Дай итоговый анализ: сколько методов используют Result, процент покрытия, " +
            "и рекомендации по улучшению.",

    "Сценарий 2: Проверка MVI" to
            "Проверь все ViewModel в проекте CalendarKMP на соответствие MVI-архитектуре. " +
            "Выполни: 1) найди все файлы с 'ViewModel' в названии, " +
            "2) для каждого определи: наследует ли BaseViewModel (common/presentation), " +
            "есть ли State как data class с default-значениями, " +
            "Event как sealed interface/class, " +
            "3) найди BaseViewModel — прочитай и опиши его контракт " +
            "(viewStates, viewActions, obtainEvent, withViewModelScope), " +
            "4) для каждого ViewModel укажи пройдена ли проверка (✅/❌). " +
            "Дай итоговую статистику: сколько ViewModel, сколько соответствует, " +
            "какие нарушения встречаются чаще всего.",

    "Сценарий 3: Генерация README" to
            "Прочитай ключевые файлы проекта CalendarKMP: " +
            "1) build.gradle.kts (корневой и модульные), settings.gradle.kts, " +
            "libs.versions.toml — определи используемые библиотеки и платформы, " +
            "2) основные ViewModel (MainViewModel, SettingsViewModel и др.), " +
            "3) Repository-файлы (CalendarRepository, AuthRepository и др.), " +
            "4) DI-модули (Koin module definitions). " +
            "На основе проанализированных исходников сгенерируй README.md на русском языке. " +
            "README должен содержать: описание проекта, технологии, архитектуру (Clean Architecture + MVI), " +
            "структуру проекта по модулям, описание ключевых компонентов, инструкцию по запуску. " +
            "Сохрани результат в файл docs/GENERATED_README.md",
)

fun main(args: Array<String>) {
    System.setOut(java.io.PrintStream(System.out, true, "UTF-8"))
    System.setErr(java.io.PrintStream(System.err, true, "UTF-8"))

    val scenarioFilter = args.firstOrNull()?.toIntOrNull()

    println("═".repeat(70))
    println("  FileAssistant Test Runner (Console)")
    println("═".repeat(70))
    println("  Project: $PROJECT_PATH")
    println("  Scenarios: ${if (scenarioFilter != null) "#$scenarioFilter" else "all (1-3)"}")
    println()

    val agent = FileAssistantAgent(
        projectPath = PROJECT_PATH,
        onProgress = { msg -> println("  [progress] $msg") },
    )

    val tools = LocalFileTools(PROJECT_PATH)
    val stats = tools.projectStats()
    val files = tools.listFiles()
    println("  Project stats: ${files.size} files")
    stats.entries.sortedByDescending { it.value.second }.take(5).forEach { (ext, pair) ->
        println("    .$ext: ${pair.first} files, ${pair.second / 1024}KB")
    }
    println()

    val results = mutableListOf<Pair<String, String>>()

    val scenariosToRun = if (scenarioFilter != null) {
        listOf(SCENARIOS[scenarioFilter - 1])
    } else {
        SCENARIOS
    }

    for ((index, scenario) in scenariosToRun.withIndex()) {
        val scenarioNum = if (scenarioFilter != null) scenarioFilter else index + 1
        val (name, task) = scenario

        println("═".repeat(70))
        println("  SCENARIO #$scenarioNum: $name")
        println("═".repeat(70))
        println("  Task: ${task.take(120)}...")
        println()

        val startTime = System.currentTimeMillis()
        val result = runBlocking {
            agent.executeTask(task)
        }
        val elapsed = System.currentTimeMillis() - startTime

        println()
        println("─".repeat(70))
        println("  RESULT (${elapsed}ms, ${(elapsed / 1000)}s)")
        println("─".repeat(70))
        println(result)
        println("─".repeat(70))
        println()

        results.add(name to result)
    }

    println()
    println("═".repeat(70))
    println("  SUMMARY")
    println("═".repeat(70))
    for ((name, result) in results) {
        println()
        println("  $name:")
        println("  ${result.take(300).replace("\n", "\n  ")}")
        if (result.length > 300) println("  ... (${result.length} chars total)")
    }
    println()
    println("═".repeat(70))
    println("  Done.")
    println("═".repeat(70))
}
