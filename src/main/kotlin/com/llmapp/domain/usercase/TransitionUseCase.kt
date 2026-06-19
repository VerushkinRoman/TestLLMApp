package com.llmapp.domain.usercase

import com.llmapp.agent.StatefulMemoryAgent
import com.llmapp.state.TaskPhase
import com.llmapp.ui.models.ChatMessageUI
import com.llmapp.ui.viewmodel.ChatViewState
import java.util.UUID

class TransitionUseCase(
    private val statefulAgent: StatefulMemoryAgent
) {

    fun showTransitionsDialog(state: ChatViewState): ChatViewState {
        val transitions = statefulAgent.getAvailableTransitionsWithDetails()
        return state.copy(
            showTransitionsDialog = true,
            availableTransitions = transitions
        )
    }

    fun dismissTransitionsDialog(state: ChatViewState): ChatViewState {
        return state.copy(showTransitionsDialog = false)
    }

    fun approvePlan(state: ChatViewState): ChatViewState {
        val result = statefulAgent.approvePlan()

        val message = ChatMessageUI(
            id = UUID.randomUUID().toString(),
            role = "assistant",
            content = if (result.success) {
                "✅ План утвержден! Теперь вы можете перейти к выполнению.\n" +
                        "Используйте переход в EXECUTION или напишите /execution"
            } else {
                "⚠️ ${result.message}"
            },
            isDemoMessage = false
        )

        return state.copy(messages = state.messages + message)
    }

    fun validate(state: ChatViewState): ChatViewState {
        val result = statefulAgent.confirmValidation()

        val message = ChatMessageUI(
            id = UUID.randomUUID().toString(),
            role = "assistant",
            content = if (result.success) {
                "✅ Валидация подтверждена! Теперь вы можете завершить задачу.\n" +
                        "Используйте переход в DONE или напишите /done"
            } else {
                "⚠️ ${result.message}"
            },
            isDemoMessage = false
        )

        return state.copy(messages = state.messages + message)
    }

    fun safeTransitionTo(state: ChatViewState, phase: TaskPhase): ChatViewState {
        val result = statefulAgent.safeTransitionTo(phase)

        val message = ChatMessageUI(
            id = UUID.randomUUID().toString(),
            role = "assistant",
            content = if (result.success) {
                "✅ ${result.message}"
            } else {
                "🚫 ${result.message}\n\n💡 ${result.suggestedAction ?: "Проверьте доступные переходы командой /transitions"}"
            },
            isDemoMessage = false
        )

        return state.copy(messages = state.messages + message)
    }
}
