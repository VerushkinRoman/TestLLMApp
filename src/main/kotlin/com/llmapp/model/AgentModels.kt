package com.llmapp.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
data class SavedChat(
    val id: String,
    val agentId: String,
    val title: String,
    val messages: List<SavedChatMessage>,
    val createdAt: Long,
    val lastModified: Long,
    val modelUsed: String
)

@Serializable
data class SavedChatMessage(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val metadata: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val responseTimeMs: Long? = null
)

fun SavedChat.getFormattedDate(): String {
    val dateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(lastModified), ZoneId.systemDefault())
    val now = LocalDateTime.now()

    return when {
        dateTime.toLocalDate() == now.toLocalDate() -> "Сегодня, ${
            dateTime.format(
                DateTimeFormatter.ofPattern(
                    "HH:mm"
                )
            )
        }"

        dateTime.toLocalDate() == now.minusDays(1).toLocalDate() -> "Вчера, ${
            dateTime.format(
                DateTimeFormatter.ofPattern("HH:mm")
            )
        }"

        dateTime.year == now.year -> dateTime.format(DateTimeFormatter.ofPattern("dd MMM, HH:mm"))
        else -> dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    }
}
