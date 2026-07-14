package com.llmapp.ui.viewmodel

import com.llmapp.invariants.InvariantManager
import com.llmapp.invariants.InvariantPresets
import com.llmapp.model.TokenStats
import com.llmapp.state.TaskPhase

class CommandHandler(
    private val invariantManager: InvariantManager,
    private val onHandleEvent: (ViewEvent) -> Unit,
    private val onAddAssistantMessage: (String) -> Unit,
    private val onGetTokenStats: () -> TokenStats
) {
    fun handleCommand(text: String) {
        when {
            text.startsWith("/task ") -> {
                val taskName = text.removePrefix("/task ").trim()
                if (taskName.isNotEmpty()) {
                    onHandleEvent(ViewEvent.CreateTask(taskName))
                } else {
                    onAddAssistantMessage("⚠️ Укажите название задачи: /task <название>")
                }
            }

            text == "/status" -> onHandleEvent(ViewEvent.ShowStatus)
            text == "/clear-task" -> onHandleEvent(ViewEvent.ClearTask)

            text == "/transitions" || text == "/available" -> onHandleEvent(ViewEvent.ShowTransitionsDialog)
            text == "/approve-plan" -> onHandleEvent(ViewEvent.ApprovePlan)
            text == "/validate" -> onHandleEvent(ViewEvent.Validate)

            text == "/planning" -> onHandleEvent(ViewEvent.SafeTransitionTo(TaskPhase.PLANNING))
            text == "/execution" -> onHandleEvent(ViewEvent.SafeTransitionTo(TaskPhase.EXECUTION))
            text == "/validation" -> onHandleEvent(ViewEvent.SafeTransitionTo(TaskPhase.VALIDATION))
            text == "/done" -> onHandleEvent(ViewEvent.SafeTransitionTo(TaskPhase.DONE))

            text == "/pause" -> onHandleEvent(ViewEvent.PauseTask("Пауза по команде /pause"))
            text == "/resume" -> onHandleEvent(ViewEvent.ResumeTask)

            text == "/snapshots" -> {
                onHandleEvent(ViewEvent.ToggleSnapshotDialog)
                onAddAssistantMessage("📸 Открыт диалог управления снимками")
            }

            text == "/tokens" -> {
                val stats = onGetTokenStats()
                onAddAssistantMessage(buildTokenStatsMessage(stats))
            }

            text.startsWith("/invariant-preset ") -> {
                val presetName = text.removePrefix("/invariant-preset ").trim()
                if (presetName.isNotEmpty()) {
                    val success = invariantManager.saveInvariantSet(
                        when (presetName.lowercase()) {
                            "android" -> InvariantPresets.getAndroidKMPInvariants()
                            "web" -> InvariantPresets.getWebInvariants()
                            else -> InvariantPresets.getBaseInvariants()
                        }
                    )
                    onAddAssistantMessage(
                        if (success) "✅ Набор инвариантов '$presetName' создан и активирован!"
                        else "❌ Не удалось создать набор инвариантов '$presetName'"
                    )
                } else {
                    onAddAssistantMessage("⚠️ Укажите название пресета: /invariant-preset <android|web|base>")
                }
            }

            text == "/help" -> onAddAssistantMessage(buildHelpText())
            else -> onAddAssistantMessage("⚠️ Неизвестная команда: $text\nИспользуйте /help для списка команд")
        }
    }

    companion object {
        fun buildHelpText(): String {
            return """
                📚 ДОСТУПНЫЕ КОМАНДЫ:
                
                📅 CalendarKMP:
                  Этот бот настроен для работы с проектом CalendarKMP
                  (Kotlin Multiplatform приложение для отслеживания饮酒 календаря)
                  
                🔧 Git-операции:
                  Коммиты, PR, чтение файлов — через GitHub API
                  Нужен GITHUB_PERSONAL_ACCESS_TOKEN (env переменная)
                  Репозиторий: github.com/VerushkinRoman/CalendarKMP
                  
                🎯 Управление задачами:
                  /task <название> - создать новую задачу
                  /status - показать полный статус задачи
                  /clear-task - очистить состояние задачи
                  
                🔒 Инварианты:
                  /invariant-preset <android|web|base> - создать набор инвариантов из пресета
                  
                ⏸️ Управление состоянием:
                  /pause - поставить задачу на паузу
                  /resume - возобновить задачу
                  
                📸 Снимки:
                  /snapshots - открыть диалог управления снимками
                  
                📊 Токены:
                  /tokens - показать статистику токенов
                  
                🔄 Управление переходами:
                  /transitions или /available - показать диалог управления переходами
                  /approve-plan - утвердить план (PLANNING → EXECUTION)
                  /validate - подтвердить валидацию (VALIDATION → DONE)
                  /planning - перейти в PLANNING
                  /execution - перейти в EXECUTION
                  /validation - перейти в VALIDATION
                  /done - завершить задачу
                  
                ℹ️ Справка:
                  /help - показать эту справку
                
                💡 Также используйте панель управления в интерфейсе чата!
            """.trimIndent()
        }

        fun buildTokenStatsMessage(stats: TokenStats): String {
            return """
                📊 СТАТИСТИКА ТОКЕНОВ:
                
                • Запросов: ${stats.requestCount}
                • Всего токенов: ${stats.totalTokens}
                • Prompt токенов: ${stats.totalPromptTokens}
                • Completion токенов: ${stats.totalCompletionTokens}
                • Стоимость: ${stats.getFormattedCost()}
                
            """.trimIndent()
        }
    }
}
