// src/main/kotlin/com/llmapp/state/TaskState.kt
package com.llmapp.state

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
enum class TaskPhase {
    INIT,
    PLANNING,
    EXECUTION,
    VALIDATION,
    DONE,
    PAUSED,
    BLOCKED;

    val displayName: String
        get() = when (this) {
            INIT -> "📋 Сбор требований"
            PLANNING -> "📐 Планирование"
            EXECUTION -> "⚡ Выполнение"
            VALIDATION -> "🔍 Проверка"
            DONE -> "✅ Завершена"
            PAUSED -> "⏸️ На паузе"
            BLOCKED -> "🚫 Заблокирована"
        }

    val emoji: String
        get() = when (this) {
            INIT -> "📋"
            PLANNING -> "📐"
            EXECUTION -> "⚡"
            VALIDATION -> "🔍"
            DONE -> "✅"
            PAUSED -> "⏸️"
            BLOCKED -> "🚫"
        }
}

@Serializable
data class TaskState(
    val taskId: String = "",
    val taskName: String = "",
    val phase: TaskPhase = TaskPhase.INIT,
    val step: String = "",
    val expectedAction: String = "",
    val description: String = "",
    val context: Map<String, String> = emptyMap(),
    val history: List<StateTransition> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val parentTaskId: String? = null,
    val subtasks: List<String> = emptyList()
) {
    fun getFormattedLastUpdate(): String {
        val dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(lastUpdated),
            ZoneId.systemDefault()
        )
        return dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
    }

    fun getElapsedTime(): String {
        val diff = System.currentTimeMillis() - createdAt
        return when {
            diff < 60_000 -> "${diff / 1000} сек"
            diff < 3600_000 -> "${diff / 60_000} мин"
            diff < 86400_000 -> "${diff / 3600_000} ч"
            else -> "${diff / 86400_000} дн"
        }
    }

    fun getPhaseProgress(): Float {
        val phases = listOf(TaskPhase.INIT, TaskPhase.PLANNING, TaskPhase.EXECUTION, TaskPhase.VALIDATION, TaskPhase.DONE)
        val currentIndex = phases.indexOf(phase)
        val doneIndex = phases.indexOf(TaskPhase.DONE)
        return if (doneIndex > 0 && currentIndex >= 0) currentIndex.toFloat() / doneIndex else 0f
    }

    fun getSummary(): String = buildString {
        append("📋 Задача: $taskName\n")
        append("📍 Фаза: ${phase.displayName}\n")
        append("📌 Шаг: ${step.ifEmpty { "—" }}\n")
        append("🎯 Ожидается: ${expectedAction.ifEmpty { "—" }}\n")
        append("🕐 Создана: ${getElapsedTime()} назад\n")
        append("🔄 Обновлена: ${getFormattedLastUpdate()}")
    }
}

@Serializable
data class StateTransition(
    val fromPhase: TaskPhase,
    val toPhase: TaskPhase,
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String = "",
    val triggeredBy: String = ""
)

@Serializable
data class AllowedTransition(
    val from: TaskPhase,
    val to: TaskPhase,
    val requiresUserInput: Boolean = false,
    val requiresValidation: Boolean = false,
    val description: String = ""
)

@Serializable
data class TaskSnapshot(
    val state: TaskState,
    val messages: List<Pair<String, String>> = emptyList(),
    val tokenStats: Map<String, Int> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)
