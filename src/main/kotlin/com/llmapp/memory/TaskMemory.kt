package com.llmapp.memory

import kotlinx.serialization.Serializable

@Serializable
data class TaskMemory(
    val goal: String = "",
    val constraintsAndPrefs: List<String> = emptyList(),
    val progress: Progress = Progress(),
    val decisions: List<String> = emptyList(),
    val criticalContext: List<String> = emptyList(),
) {
    @Serializable
    data class Progress(
        val done: List<String> = emptyList(),
        val inProgress: List<String> = emptyList(),
        val blocked: List<String> = emptyList(),
    )

    fun isEmpty(): Boolean =
        goal.isEmpty() && constraintsAndPrefs.isEmpty() &&
                progress.done.isEmpty() && progress.inProgress.isEmpty() && progress.blocked.isEmpty() &&
                decisions.isEmpty() && criticalContext.isEmpty()

    fun summarize(): String = buildString {
        if (goal.isNotEmpty()) appendLine("Goal: $goal")
        if (constraintsAndPrefs.isNotEmpty()) {
            appendLine("Constraints & Preferences:")
            constraintsAndPrefs.forEach { appendLine("  \u2022 $it") }
        }
        if (progress.done.isNotEmpty()) {
            appendLine("Done:")
            progress.done.forEach { appendLine("  \u2022 $it") }
        }
        if (progress.inProgress.isNotEmpty()) {
            appendLine("In Progress:")
            progress.inProgress.forEach { appendLine("  \u2022 $it") }
        }
        if (progress.blocked.isNotEmpty()) {
            appendLine("Blocked:")
            progress.blocked.forEach { appendLine("  \u2022 $it") }
        }
        if (decisions.isNotEmpty()) {
            appendLine("Key Decisions:")
            decisions.forEach { appendLine("  \u2022 $it") }
        }
        if (criticalContext.isNotEmpty()) {
            appendLine("Critical Context:")
            criticalContext.forEach { appendLine("  \u2022 $it") }
        }
    }
}
