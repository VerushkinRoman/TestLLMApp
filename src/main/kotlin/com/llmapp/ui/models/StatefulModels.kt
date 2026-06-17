package com.llmapp.ui.models

import com.llmapp.state.TaskPhase

data class TaskStateUI(
    val phase: TaskPhase,
    val step: String,
    val expectedAction: String,
    val isPaused: Boolean = false,
    val isBlocked: Boolean = false,
    val progress: Float = 0f,
    val taskName: String = "",
    val elapsedTime: String = "",
    val availableTransitions: List<TaskPhase> = emptyList()
) {
    val displayPhase: String get() = phase.displayName
    val phaseEmoji: String get() = phase.emoji
}