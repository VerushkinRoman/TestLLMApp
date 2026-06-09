package com.llmapp.controller

import com.llmapp.model.SavedChat
import com.llmapp.model.SavedChatMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class ChatStorageData(
    val chats: MutableList<SavedChat> = mutableListOf()
)

class ChatStorageManager {
    private val storageDir: File
    private val storageFile: File
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        val userHome = System.getProperty("user.home")
        storageDir = File(userHome, ".llm_chat_app")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        storageFile = File(storageDir, "saved_chats.json")
    }

    private fun loadData(): ChatStorageData {
        return if (storageFile.exists()) {
            try {
                json.decodeFromString<ChatStorageData>(storageFile.readText())
            } catch (_: Exception) {
                ChatStorageData()
            }
        } else {
            ChatStorageData()
        }
    }

    private fun saveData(data: ChatStorageData) {
        storageFile.writeText(json.encodeToString(ChatStorageData.serializer(), data))
    }

    @OptIn(ExperimentalUuidApi::class)
    fun saveChat(
        agentId: String,
        title: String,
        messages: List<SavedChatMessage>,
        modelUsed: String
    ): SavedChat {
        val data = loadData()
        val now = System.currentTimeMillis()

        val messagesWithIds = messages.map { msg ->
            if (msg.id.isEmpty()) {
                msg.copy(id = Uuid.random().toString())
            } else {
                msg
            }
        }

        val chat = SavedChat(
            id = Uuid.random().toString(),
            agentId = agentId,
            title = title,
            messages = messagesWithIds,
            createdAt = now,
            lastModified = now,
            modelUsed = modelUsed
        )
        data.chats.add(chat)
        saveData(data)
        return chat
    }

    fun updateChat(chatId: String, messages: List<SavedChatMessage>, modelUsed: String) {
        val data = loadData()
        val index = data.chats.indexOfFirst { it.id == chatId }
        if (index != -1) {
            data.chats[index] = data.chats[index].copy(
                messages = messages,
                lastModified = System.currentTimeMillis(),
                modelUsed = modelUsed
            )
            saveData(data)
        }
    }

    fun getChatsByAgent(agentId: String): List<SavedChat> {
        return loadData().chats
            .filter { it.agentId == agentId }
            .sortedByDescending { it.lastModified }
    }

    fun getChat(chatId: String): SavedChat? {
        return loadData().chats.find { it.id == chatId }
    }

    fun deleteChat(chatId: String) {
        val data = loadData()
        data.chats.removeAll { it.id == chatId }
        saveData(data)
    }

    fun renameChat(chatId: String, newTitle: String) {
        val data = loadData()
        val index = data.chats.indexOfFirst { it.id == chatId }
        if (index != -1) {
            data.chats[index] = data.chats[index].copy(
                title = newTitle,
                lastModified = System.currentTimeMillis()
            )
            saveData(data)
        }
    }

    fun getLastActiveChat(agentId: String): SavedChat? {
        return loadData().chats
            .filter { it.agentId == agentId }
            .maxByOrNull { it.lastModified }
    }
}
