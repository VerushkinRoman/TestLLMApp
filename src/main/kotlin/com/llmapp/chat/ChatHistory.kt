package com.llmapp.chat

import com.llmapp.model.ChatMessage

class ChatHistory(
    systemPrompt: String,
    private val maxHistorySize: Int = 20
) {
    private val messages = mutableListOf<ChatMessage>()

    init {
        messages.add(ChatMessage(role = "system", content = systemPrompt))
    }

    fun addUserMessage(content: String) {
        messages.add(ChatMessage(role = "user", content = content))
        trim()
    }

    fun addAssistantMessage(content: String) {
        messages.add(ChatMessage(role = "assistant", content = content))
        trim()
    }

    fun getMessages(): List<ChatMessage> = messages.toList()

    fun clear() {
        val systemMessage = messages.first()
        messages.clear()
        messages.add(systemMessage)
    }

    fun removeLastMessage() {
        if (messages.size > 1) {
            messages.removeAt(messages.size - 1)
        }
    }

    fun size(): Int = messages.size - 1

    private fun trim() {
        if (messages.size > maxHistorySize + 1) {
            val systemMessage = messages.first()
            val recentMessages = messages.takeLast(maxHistorySize)
            messages.clear()
            messages.add(systemMessage)
            messages.addAll(recentMessages)
        }
    }
}
