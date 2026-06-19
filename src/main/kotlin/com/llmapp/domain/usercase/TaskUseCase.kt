package com.llmapp.domain.usercase

import com.llmapp.agent.StatefulMemoryAgent
import com.llmapp.state.TaskPhase
import com.llmapp.ui.models.ChatMessageUI
import com.llmapp.ui.models.TaskStateUI
import com.llmapp.ui.viewmodel.ChatViewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class TaskUseCase(
    private val statefulAgent: StatefulMemoryAgent
) {
    private val _taskState = MutableStateFlow<TaskStateUI?>(null)
    val taskState: StateFlow<TaskStateUI?> = _taskState.asStateFlow()

    fun createTask(
        state: ChatViewState,
        name: String,
        description: String = ""
    ): ChatViewState {
        statefulAgent.createTask(name, description)
        updateTaskStateUI()

        val helpText = buildString {
            appendLine("✅ Задача '$name' создана!")
            appendLine()
            appendLine("📋 Текущая фаза: Сбор требований")
            appendLine("🎯 Ожидается: Опишите, что нужно сделать")
            appendLine()
            appendLine("💡 Управление задачей:")
            appendLine("  • Опишите требования в чате")
            appendLine("  • Используйте панель управления внизу")
            appendLine("  • Или используйте команды:")
            appendLine("    /planning - перейти в планирование")
            appendLine("    /execution - перейти в выполнение")
            appendLine("    /validation - перейти в проверку")
            appendLine("    /done - завершить задачу")
            appendLine("    /approve-plan - утвердить план")
            appendLine("    /validate - подтвердить валидацию")
            appendLine("    /transitions - показать все доступные переходы")
            appendLine("    /status - показать статус задачи")
            appendLine("    /help - полная справка")
        }

        val taskMessage = ChatMessageUI(
            id = UUID.randomUUID().toString(),
            role = "assistant",
            content = helpText,
            isDemoMessage = false
        )

        return state.copy(
            messages = state.messages + taskMessage,
            showCreateTaskDialog = false
        )
    }

    fun transitionTo(state: ChatViewState, phase: TaskPhase): ChatViewState {
        val result = statefulAgent.transitionTo(phase)
        if (result.success) {
            updateTaskStateUI()
            val message = ChatMessageUI(
                id = UUID.randomUUID().toString(),
                role = "assistant",
                content = "🔄 ${result.message}",
                isDemoMessage = false
            )
            return state.copy(messages = state.messages + message)
        }
        return state
    }

    fun pauseTask(state: ChatViewState, reason: String = "Пауза"): ChatViewState {
        val result = statefulAgent.pause(reason)
        if (result.success) {
            updateTaskStateUI()
            val message = ChatMessageUI(
                id = UUID.randomUUID().toString(),
                role = "assistant",
                content = "⏸️ ${result.message}",
                isDemoMessage = false
            )
            return state.copy(messages = state.messages + message)
        }
        return state
    }

    fun resumeTask(state: ChatViewState): ChatViewState {
        val result = statefulAgent.resume()
        if (result.success) {
            updateTaskStateUI()
            val message = ChatMessageUI(
                id = UUID.randomUUID().toString(),
                role = "assistant",
                content = "▶️ ${result.message}",
                isDemoMessage = false
            )
            return state.copy(messages = state.messages + message)
        }
        return state
    }

    fun blockTask(state: ChatViewState, reason: String = "Блокировка"): ChatViewState {
        val result = statefulAgent.block(reason)
        if (result.success) {
            updateTaskStateUI()
            val message = ChatMessageUI(
                id = UUID.randomUUID().toString(),
                role = "assistant",
                content = "🚫 ${result.message}",
                isDemoMessage = false
            )
            return state.copy(messages = state.messages + message)
        }
        return state
    }

    fun unblockTask(state: ChatViewState): ChatViewState {
        val result = statefulAgent.unblock()
        if (result.success) {
            updateTaskStateUI()
            val message = ChatMessageUI(
                id = UUID.randomUUID().toString(),
                role = "assistant",
                content = "🔓 ${result.message}",
                isDemoMessage = false
            )
            return state.copy(messages = state.messages + message)
        }
        return state
    }

    fun getStatus(state: ChatViewState): ChatViewState {
        val status = statefulAgent.getFullStatus()
        val message = ChatMessageUI(
            id = UUID.randomUUID().toString(),
            role = "assistant",
            content = status,
            isDemoMessage = false
        )
        return state.copy(messages = state.messages + message)
    }

    fun clearTask(state: ChatViewState): ChatViewState {
        statefulAgent.createTask("", "")
        updateTaskStateUI()
        val message = ChatMessageUI(
            id = UUID.randomUUID().toString(),
            role = "assistant",
            content = "🗑️ Состояние задачи очищено",
            isDemoMessage = false
        )
        return state.copy(messages = state.messages + message)
    }

    private fun updateTaskStateUI() {
        val state = statefulAgent.getCurrentTaskState()
        _taskState.value = TaskStateUI(
            phase = state.phase,
            step = state.step,
            expectedAction = state.expectedAction,
            isPaused = statefulAgent.isPaused(),
            isBlocked = state.phase == TaskPhase.BLOCKED,
            progress = statefulAgent.getProgress(),
            taskName = state.taskName,
            elapsedTime = state.getElapsedTime(),
            availableTransitions = statefulAgent.getAvailableTransitions()
        )
    }
}
