package com.llmapp.ui.viewmodel

import com.llmapp.agent.AvailableTransition
import com.llmapp.agent.CompressedChatHistory.CompressionStats
import com.llmapp.agent.TokenSnapshot
import com.llmapp.invariants.InvariantSet
import com.llmapp.memory.ProjectConstraints
import com.llmapp.memory.UserProfile
import com.llmapp.model.ModelInfo
import com.llmapp.model.ResponseControl
import com.llmapp.model.TokenStats
import com.llmapp.model.freeModels
import com.llmapp.ui.models.ChatMessageUI
import com.llmapp.ui.models.TaskStateUI

data class ChatViewState(
    // Сообщения
    val messages: List<ChatMessageUI> = emptyList(),
    val isTyping: Boolean = false,
    val isGenerating: Boolean = false,
    val isDemoRunning: Boolean = false,

    // Ввод
    val draftMessage: String = "",
    val cursorPosition: Int = 0,

    // Модель
    val currentModel: String = "nvidia/nemotron-3-super-120b-a12b:free",
    val availableModels: List<ModelInfo> = freeModels,

    // Управление ответами
    val responseControl: ResponseControl = ResponseControl(),
    val controlEnabled: Boolean = true,

    // Токены
    val tokenStats: TokenStats = TokenStats(),
    val tokenHistory: List<TokenSnapshot> = emptyList(),
    val contextWarning: String = "",
    val compressionStats: CompressionStats? = null,

    // Память
    val userProfile: UserProfile = UserProfile(),
    val projectConstraints: ProjectConstraints = ProjectConstraints(),

    // Профили
    val activeProfile: UserProfile = UserProfile(),
    val allProfiles: List<UserProfile> = emptyList(),
    val showProfileManager: Boolean = false,
    val showWelcomeDialog: Boolean = false,

    // Задача
    val taskState: TaskStateUI? = null,
    val showCreateTaskDialog: Boolean = false,

    // Снимки
    val showSnapshotDialog: Boolean = false,
    val snapshots: List<Pair<String, String>> = emptyList(),

    // Переходы
    val showTransitionsDialog: Boolean = false,
    val availableTransitions: List<AvailableTransition> = emptyList(),

    // Инварианты
    val activeInvariantSetName: String? = null,
    val invariantSets: List<InvariantSet> = emptyList(),

    // Демонстрации
    val demoManagerCurrentDemo: com.llmapp.ui.DemoType? = null,
    val demoManagerProgress: String? = null,

    // Компрессия
    val compressionEnabled: Boolean = true,
    val keepLastMessages: Int = 8,
    val summarizeEvery: Int = 6,

    // MCP
    val mcpConnected: Boolean = false,
    val mcpServerName: String? = null,
    val mcpLog: List<String> = emptyList(),

    // Коллектор матчей
    val collectorRunning: Boolean = false,
    val collectorInterval: Double = 15.0,
    val collectorLog: List<String> = emptyList(),
    val collectorSummary: String? = null,
    val collectorLastRun: String? = null,
    val showCollectorPanel: Boolean = false
) {
    companion object {
        fun initial() = ChatViewState()
    }
}
