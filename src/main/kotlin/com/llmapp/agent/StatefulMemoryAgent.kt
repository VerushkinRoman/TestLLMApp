// src/main/kotlin/com/llmapp/agent/StatefulMemoryAgent.kt

package com.llmapp.agent

import com.llmapp.api.ApiConfig
import com.llmapp.memory.ProjectConstraints
import com.llmapp.memory.UserProfile
import com.llmapp.memory.WorkingMemory
import com.llmapp.state.TaskPhase
import com.llmapp.state.TaskStateMachine
import com.llmapp.state.TransitionResult
import com.llmapp.state.StateTransition
import java.io.File
import com.llmapp.state.TaskState as StateTaskState

data class StatefulResponse(
    val content: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val responseTimeMs: Long,
    val taskState: StateTaskState,
    val transitionResult: TransitionResult? = null,
    val memoryUsed: MemoryUsageInfo? = null
)

class StatefulMemoryAgent(
    apiKey: String = ApiConfig.getApiKey(),
    private var model: String = "nvidia/nemotron-3-super-120b-a12b:free",
    systemPrompt: String = "Ты полезный ассистент. Отвечай на русском языке.",
    storageDir: String = System.getProperty("user.home") + "/.llm_task_states"
) {
    private val memoryAgent = MemoryAwareAgent(
        apiKey = apiKey,
        model = model,
        systemPrompt = systemPrompt,
        persistToDisk = true
    )

    private val stateMachine = TaskStateMachine(File(storageDir))

    private var sessionMessages: MutableList<Pair<String, String>> = mutableListOf()
    private var isResumedFromPause: Boolean = false
    private var currentTaskId: String = ""

    // ============================================================
    // УПРАВЛЕНИЕ ЗАДАЧЕЙ
    // ============================================================

    fun createTask(
        taskName: String,
        description: String = "",
        initialContext: Map<String, String> = emptyMap()
    ): StateTaskState {
        sessionMessages.clear()
        isResumedFromPause = false

        val state = stateMachine.createTask(taskName, description, initialContext)
        currentTaskId = state.taskId

        memoryAgent.startNewTask(taskName, initialContext)
        memoryAgent.updateTaskState(com.llmapp.memory.TaskState.INIT)

        sessionMessages.add("system" to "Начинаем работу над задачей: $taskName")

        createSnapshot("initial_${state.taskId}")

        return state
    }

    fun getCurrentTaskState(): StateTaskState = stateMachine.getCurrentState()
    fun getPhase(): TaskPhase = stateMachine.getPhase()
    fun getStep(): String = stateMachine.getStep()
    fun getExpectedAction(): String = stateMachine.getExpectedAction()
    fun isPaused(): Boolean = stateMachine.isPaused()
    fun getProgress(): Float = stateMachine.getProgress()
    fun isTaskComplete(): Boolean = stateMachine.isTaskComplete()
    fun getPauseReason(): String = stateMachine.getPauseReason()
    fun canTransitionTo(phase: TaskPhase): Boolean = stateMachine.canTransitionTo(phase)
    fun getAvailableTransitions(): List<TaskPhase> = stateMachine.getAvailableTransitions()
    fun getTaskHistory(): List<StateTransition> = stateMachine.getCurrentState().history
    fun getTaskDescription(): String = stateMachine.getCurrentState().description
    fun getCurrentUserProfile(): UserProfile = memoryAgent.getUserProfile()
    fun getCurrentConstraints(): ProjectConstraints = memoryAgent.getProjectConstraints()

    // ============================================================
    // ПЕРЕХОДЫ
    // ============================================================

    fun transitionTo(phase: TaskPhase, reason: String = ""): TransitionResult {
        if (!canTransitionTo(phase)) {
            return TransitionResult(
                success = false,
                message = "🚫 Переход в ${phase.displayName} не разрешен из текущей фазы",
                state = stateMachine.getCurrentState()
            )
        }

        val result = stateMachine.transitionTo(phase, reason, "user")
        if (result.success) {
            syncWithMemory(phase)
            createSnapshot("after_transition_${System.currentTimeMillis()}")
        }
        return result
    }

    fun pause(reason: String = "Пауза"): TransitionResult {
        val result = stateMachine.pause(reason)
        if (result.success) {
            stateMachine.createSnapshot(
                "pause_${System.currentTimeMillis()}",
                sessionMessages.toList()
            )
        }
        return result
    }

    fun resume(): TransitionResult {
        val result = stateMachine.resume()
        if (result.success) {
            isResumedFromPause = true
            syncWithMemory(stateMachine.getPhase())
        }
        return result
    }

    fun block(reason: String): TransitionResult {
        val result = stateMachine.block(reason)
        if (result.success) {
            createSnapshot("blocked_${System.currentTimeMillis()}")
        }
        return result
    }

    fun unblock(): TransitionResult {
        val result = stateMachine.unblock()
        if (result.success) {
            syncWithMemory(stateMachine.getPhase())
        }
        return result
    }

    // ============================================================
    // ОБНОВЛЕНИЕ КОНТЕКСТА
    // ============================================================

    fun updateStep(step: String): StateTaskState {
        val state = stateMachine.updateStep(step)
        createSnapshot("step_updated_${System.currentTimeMillis()}")
        return state
    }

    fun updateExpectedAction(action: String): StateTaskState {
        val state = stateMachine.updateExpectedAction(action)
        createSnapshot("action_updated_${System.currentTimeMillis()}")
        return state
    }

    fun updateContext(key: String, value: String): StateTaskState {
        val state = stateMachine.updateContext(key, value)
        return state
    }

    fun getContext(key: String): String? = stateMachine.getContext(key)
    fun getAllContext(): Map<String, String> = stateMachine.getCurrentState().context

    // ============================================================
    // ЗАПРОС К АГЕНТУ
    // ============================================================

    suspend fun processRequest(userInput: String): StatefulResponse {
        if (stateMachine.isPaused()) {
            return StatefulResponse(
                content = "⏸️ Задача на паузе. Используйте resume() для продолжения.\n\nПричина: ${stateMachine.getPauseReason()}",
                promptTokens = 0,
                completionTokens = 0,
                totalTokens = 0,
                responseTimeMs = 0,
                taskState = stateMachine.getCurrentState()
            )
        }

        sessionMessages.add("user" to userInput)

        val enhancedPrompt = buildStatefulPrompt(userInput)

        val startTime = System.currentTimeMillis()
        val response = memoryAgent.processRequest(enhancedPrompt)
        val responseTime = System.currentTimeMillis() - startTime

        sessionMessages.add("assistant" to response.content)

        // ✅ АВТОМАТИЧЕСКИЙ ПЕРЕХОД
        val transitionResult = autoTransitionBasedOnResponse(response.content)

        if (transitionResult?.success == true) {
            createSnapshot("auto_transition_${System.currentTimeMillis()}")
        }

        return StatefulResponse(
            content = response.content,
            promptTokens = response.promptTokens,
            completionTokens = response.completionTokens,
            totalTokens = response.totalTokens,
            responseTimeMs = responseTime,
            taskState = stateMachine.getCurrentState(),
            transitionResult = transitionResult,
            memoryUsed = response.memoryUsed
        )
    }

    // ============================================================
    // ВОССТАНОВЛЕНИЕ ИЗ СНИМКА
    // ============================================================

    fun restoreFromSnapshot(snapshotId: String): Boolean {
        if (!stateMachine.hasSnapshot(snapshotId)) {
            println("⚠️ Снимок $snapshotId не найден")
            return false
        }

        val success = stateMachine.restoreFromSnapshot(snapshotId)
        if (success) {
            val snapshot = stateMachine.loadSnapshot(snapshotId)
            snapshot?.let {
                sessionMessages.clear()
                sessionMessages.addAll(it.messages)
                isResumedFromPause = true
                currentTaskId = it.state.taskId
                syncWithMemory(it.state.phase)

                // Восстанавливаем контекст в memory agent
                it.state.context.forEach { (key, value) ->
                    memoryAgent.updateWorkingContext(key, value)
                }

                println("✅ Восстановлен снимок: $snapshotId")
            }
            return true
        }
        return false
    }

    fun getSnapshots(): List<Pair<String, String>> {
        return stateMachine.getAllSnapshots().map { (name, snapshot) ->
            val state = snapshot.state
            name to "${state.phase.emoji} ${state.taskName} (${state.phase.displayName}) - ${state.getElapsedTime()} назад"
        }
    }

    fun createSnapshot(name: String): String {
        return stateMachine.createSnapshot(name, sessionMessages.toList())
    }

    fun getSnapshotDetails(snapshotId: String): String {
        val snapshot = stateMachine.loadSnapshot(snapshotId)
        return snapshot?.state?.getSummary() ?: "Снимок не найден"
    }

    // ============================================================
    // ПРОКСИ МЕТОДЫ К MEMORY AGENT
    // ============================================================

    fun updateProfile(profile: UserProfile) {
        memoryAgent.updateProfile(profile)
        updateContext("user_profile", profile.name)
        createSnapshot("profile_updated_${System.currentTimeMillis()}")
    }

    fun getUserProfile(): UserProfile = memoryAgent.getUserProfile()

    fun updateConstraints(constraints: ProjectConstraints) {
        memoryAgent.updateConstraints(constraints)
        updateContext("project_constraints", constraints.techStack.joinToString(", "))
        createSnapshot("constraints_updated_${System.currentTimeMillis()}")
    }

    fun getProjectConstraints(): ProjectConstraints = memoryAgent.getProjectConstraints()

    fun getWorkingMemory(): WorkingMemory = memoryAgent.getWorkingMemory()

    fun addKnowledge(key: String, value: String) {
        memoryAgent.addKnowledge(key, value)
        updateContext("knowledge_$key", value.take(100))
    }

    fun saveDecisionToLongTerm(topic: String, decision: String, context: String = "") {
        memoryAgent.saveDecisionToLongTerm(topic, decision, context)
        updateContext("decision_$topic", decision.take(100))
        createSnapshot("decision_saved_${System.currentTimeMillis()}")
    }

    fun getAllKnowledge(): Map<String, String> = memoryAgent.getAllKnowledge()
    fun getAllDecisions(): List<com.llmapp.memory.Decision> = memoryAgent.getAllDecisions()

    fun getTokenStats() = memoryAgent.getTokenStats()

    fun changeModel(newModel: String) {
        model = newModel
        memoryAgent.changeModel(newModel)
        updateContext("model", newModel)
    }

    fun clearWorkingMemory() {
        memoryAgent.clearWorkingMemory()
        createSnapshot("memory_cleared_${System.currentTimeMillis()}")
    }

    fun resetWorkingMemory() {
        memoryAgent.clearWorkingMemory()
        createSnapshot("memory_reset_${System.currentTimeMillis()}")
    }

    fun getMemoryStats(): String {
        return buildString {
            appendLine("📊 Статистика памяти:")
            appendLine("  • Профиль: ${if (getUserProfile().name.isNotEmpty()) getUserProfile().name else "не настроен"}")
            appendLine("  • Ограничения: ${if (getProjectConstraints().techStack.isNotEmpty()) "настроены" else "не настроены"}")
            appendLine("  • Знаний: ${getAllKnowledge().size}")
            appendLine("  • Решений: ${getAllDecisions().size}")
            appendLine("  • Рабочая память: ${getWorkingMemory().taskName.ifEmpty { "нет задачи" }}")
        }
    }

    // ============================================================
    // ОТЧЕТЫ И СТАТУСЫ
    // ============================================================

    fun getFullStatus(): String = buildString {
        appendLine("=".repeat(60))
        appendLine("📋 ПОЛНЫЙ СТАТУС АГЕНТА")
        appendLine("=".repeat(60))
        appendLine()

        val state = stateMachine.getCurrentState()
        appendLine("📌 ЗАДАЧА:")
        appendLine("  • Название: ${state.taskName}")
        appendLine("  • ID: ${state.taskId}")
        appendLine("  • Фаза: ${state.phase.displayName}")
        appendLine("  • Шаг: ${state.step}")
        appendLine("  • Ожидается: ${state.expectedAction}")
        appendLine("  • Прогресс: ${"%.0f".format(state.getPhaseProgress() * 100)}%")
        if (stateMachine.isPaused()) {
            appendLine("  • ⏸️ ПАУЗА: ${stateMachine.getPauseReason()}")
        }
        if (state.context.isNotEmpty()) {
            appendLine("  • Контекст: ${state.context.size} ключей")
        }

        appendLine("  • Доступные переходы: ${getAvailableTransitions().joinToString { it.displayName }}")

        appendLine()
        appendLine("🧠 ПАМЯТЬ:")
        appendLine("  • Профиль: ${getUserProfile().name.ifEmpty { "не настроен" }}")
        appendLine("  • Технологии: ${getUserProfile().preferredTech.joinToString(", ")}")
        appendLine("  • Знаний в LTM: ${getAllKnowledge().size}")
        appendLine("  • Решений в LTM: ${getAllDecisions().size}")
        appendLine("  • Токенов: ${getTokenStats().totalTokens}")

        appendLine()
        appendLine("📸 СНИМКИ: ${getSnapshots().size}")

        appendLine()
        appendLine("🤖 МОДЕЛЬ: $model")

        appendLine()
        appendLine("=".repeat(60))
    }

    // ============================================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ============================================================

    private fun syncWithMemory(phase: TaskPhase) {
        val memoryPhase = when (phase) {
            TaskPhase.INIT -> com.llmapp.memory.TaskState.INIT
            TaskPhase.PLANNING -> com.llmapp.memory.TaskState.PLANNING
            TaskPhase.EXECUTION -> com.llmapp.memory.TaskState.EXECUTING
            TaskPhase.VALIDATION -> com.llmapp.memory.TaskState.VALIDATING
            TaskPhase.DONE -> com.llmapp.memory.TaskState.DONE
            TaskPhase.PAUSED, TaskPhase.BLOCKED -> com.llmapp.memory.TaskState.BLOCKED
        }
        memoryAgent.updateTaskState(memoryPhase)
    }

    private fun buildStatefulPrompt(userInput: String): String {
        val state = stateMachine.getCurrentState()

        return buildString {
            append("---\n")
            append("📋 ТЕКУЩЕЕ СОСТОЯНИЕ ЗАДАЧИ:\n")
            append("---\n")
            append("Фаза: ${state.phase.displayName}\n")
            append("Шаг: ${state.step}\n")
            append("Ожидается: ${state.expectedAction}\n")
            if (state.context.isNotEmpty()) {
                append("Контекст:\n")
                state.context.forEach { (key, value) ->
                    append("  • $key: $value\n")
                }
            }
            if (isResumedFromPause) {
                append("\n⏸️ ЗАДАЧА ВОЗОБНОВЛЕНА ПОСЛЕ ПАУЗЫ\n")
                append("Продолжаем с того же места.\n")
            }
            append("\n---\n")
            append("ПОЛЬЗОВАТЕЛЬ:\n")
            append(userInput)
            append("\n\nОтветь с учетом текущего состояния задачи.")
        }
    }

    private fun autoTransitionBasedOnResponse(content: String): TransitionResult? {
        val lower = content.lowercase()

        return when {
            // Завершение
            lower.contains("завершено") || lower.contains("готово") || lower.contains("выполнено") -> {
                if (stateMachine.getPhase() == TaskPhase.EXECUTION) {
                    stateMachine.transitionTo(
                        TaskPhase.VALIDATION,
                        "Автоматический переход: выполнение завершено"
                    )
                } else null
            }
            // Проверка пройдена → Done
            lower.contains("проверка пройдена") || lower.contains("все работает") || lower.contains("успешно") -> {
                if (stateMachine.getPhase() == TaskPhase.VALIDATION) {
                    stateMachine.transitionTo(
                        TaskPhase.DONE,
                        "Автоматический переход: проверка пройдена"
                    )
                } else null
            }
            // План утвержден → Execution
            lower.contains("план утвержден") || lower.contains("план готов") || lower.contains("утверждаю") -> {
                if (stateMachine.getPhase() == TaskPhase.PLANNING) {
                    stateMachine.transitionTo(
                        TaskPhase.EXECUTION,
                        "Автоматический переход: план утвержден"
                    )
                } else null
            }
            // Требуется доработка → Execution
            lower.contains("надо переделать") || lower.contains("не работает") || lower.contains("ошибка") -> {
                if (stateMachine.getPhase() == TaskPhase.VALIDATION) {
                    stateMachine.transitionTo(
                        TaskPhase.EXECUTION,
                        "Автоматический переход: требуется доработка"
                    )
                } else null
            }
            // Нужны уточнения → Init
            lower.contains("нужны уточнения") || lower.contains("непонятно") || lower.contains("уточните") -> {
                if (stateMachine.getPhase() == TaskPhase.PLANNING || stateMachine.getPhase() == TaskPhase.EXECUTION) {
                    stateMachine.transitionTo(
                        TaskPhase.INIT,
                        "Автоматический переход: нужны уточнения"
                    )
                } else null
            }
            else -> null
        }
    }
}
