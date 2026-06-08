package com.llmapp.agent

import com.llmapp.chat.ChatSession
import com.llmapp.controller.ChatStorageManager
import com.llmapp.model.SavedChat
import com.llmapp.model.SavedChatMessage
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ChatMemoryAgent(
    private val chatSession: ChatSession,
    private val storageManager: ChatStorageManager,
) {
    private val _currentChatId = MutableStateFlow<String?>(null)

    private val _savedChats = MutableStateFlow<List<SavedChat>>(emptyList())
    val savedChats: StateFlow<List<SavedChat>> = _savedChats.asStateFlow()

    private var saveDebounceJob: kotlinx.coroutines.Job? = null

    fun loadChats() {
        _savedChats.value = storageManager.getChatsByAgent("chat_memory")
    }

    @OptIn(ExperimentalUuidApi::class)
    fun createNewChat(): String {
        val chatId = Uuid.random().toString()
        _currentChatId.value = chatId
        return chatId
    }

    @OptIn(ExperimentalUuidApi::class)
    fun loadChat(chatId: String): List<ChatMessageUI> {
        val chat = storageManager.getChat(chatId)
        if (chat != null) {
            _currentChatId.value = chatId

            val messages = chat.messages.mapNotNull { msg ->
                when (msg.role) {
                    "user" -> Pair("user", msg.content)
                    "assistant" -> Pair("assistant", msg.content)
                    else -> null
                }
            }

            chatSession.rebuildHistoryFromUiMessages(messages)

            return chat.messages.map { msg ->
                ChatMessageUI(
                    id = msg.id,
                    role = msg.role,
                    content = msg.content,
                    metadata = msg.metadata,
                    promptTokens = msg.promptTokens,
                    completionTokens = msg.completionTokens,
                    totalTokens = msg.totalTokens,
                    responseTimeMs = msg.responseTimeMs
                )
            }
        }
        return emptyList()
    }

    fun saveCurrentChat(
        messages: List<ChatMessageUI>,
        modelUsed: String
    ) {
        if (messages.isEmpty()) return

        val chatId = _currentChatId.value
        val savedMessages = messages.map { msg ->
            SavedChatMessage(
                id = msg.id,
                role = msg.role,
                content = msg.content,
                timestamp = System.currentTimeMillis(),
                metadata = msg.metadata,
                promptTokens = msg.promptTokens,
                completionTokens = msg.completionTokens,
                totalTokens = msg.totalTokens,
                responseTimeMs = msg.responseTimeMs
            )
        }

        val title = messages.firstOrNull { it.role == "user" }?.content?.take(50)?.let { title ->
            if (title.length < (messages.first { it.role == "user" }.content.length)) "$title..." else title
        } ?: "Новый чат"

        if (chatId != null) {
            val existingChat = storageManager.getChat(chatId)
            if (existingChat != null) {
                storageManager.updateChat(chatId, savedMessages, modelUsed)
                if (existingChat.title == "Новый чат" && messages.size >= 2) {
                    storageManager.renameChat(chatId, title)
                }
            } else {
                storageManager.saveChat("chat_memory", title, savedMessages, modelUsed)
            }
        } else {
            val newChat = storageManager.saveChat("chat_memory", title, savedMessages, modelUsed)
            _currentChatId.value = newChat.id
        }

        loadChats()
    }

    fun saveCurrentChatDebounced(messages: List<ChatMessageUI>, modelUsed: String) {
        saveDebounceJob?.cancel()
        saveDebounceJob = CoroutineScope(Dispatchers.Main).launch {
            delay(500.milliseconds)
            saveCurrentChat(messages, modelUsed)
        }
    }

    fun deleteChat(chatId: String) {
        storageManager.deleteChat(chatId)
        if (_currentChatId.value == chatId) {
            _currentChatId.value = null
        }
        loadChats()
    }

    fun renameChat(chatId: String, newTitle: String) {
        storageManager.renameChat(chatId, newTitle)
        loadChats()
    }
}
