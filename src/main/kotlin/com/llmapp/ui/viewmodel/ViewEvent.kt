package com.llmapp.ui.viewmodel

import com.llmapp.agent.ChatMemoryAgent
import com.llmapp.chat.ChatSession
import com.llmapp.invariants.InvariantSet
import com.llmapp.memory.ProjectConstraints
import com.llmapp.memory.UserProfile
import com.llmapp.state.TaskPhase
import com.llmapp.rag.RagMode
import com.llmapp.rag.domain.RerankerType
import com.llmapp.ui.components.MemorySettings
import com.llmapp.ui.components.NamedProfile
import com.llmapp.ui.models.ChatMessageUI

sealed class ViewEvent {
    // ============================================================
    // СООБЩЕНИЯ
    // ============================================================
    data class SendMessage(val text: String, val addToHistory: Boolean = true) : ViewEvent()
    data class RegenerateMessage(val messageId: String) : ViewEvent()
    data class EditUserMessage(val messageId: String, val newContent: String) : ViewEvent()
    data class UpdateDraft(val text: String, val cursorPos: Int) : ViewEvent()
    object StopGeneration : ViewEvent()
    object ClearHistory : ViewEvent()

    // ============================================================
    // ЗАДАЧИ
    // ============================================================
    data class CreateTask(val name: String, val description: String = "") : ViewEvent()
    data class TransitionTo(val phase: TaskPhase) : ViewEvent()
    data class PauseTask(val reason: String = "Пауза") : ViewEvent()
    object ResumeTask : ViewEvent()
    object ClearTask : ViewEvent()
    object ShowStatus : ViewEvent()

    // ============================================================
    // ПЕРЕХОДЫ
    // ============================================================
    object ShowTransitionsDialog : ViewEvent()
    object DismissTransitionsDialog : ViewEvent()
    object ApprovePlan : ViewEvent()
    object Validate : ViewEvent()
    data class SafeTransitionTo(val phase: TaskPhase) : ViewEvent()

    // ============================================================
    // СНИМКИ
    // ============================================================
    object ToggleSnapshotDialog : ViewEvent()
    object DismissSnapshotDialog : ViewEvent()
    data class CreateSnapshot(val name: String) : ViewEvent()
    data class RestoreSnapshot(val id: String) : ViewEvent()

    // ============================================================
    // ПРОФИЛИ
    // ============================================================
    object ToggleProfileManager : ViewEvent()
    object DismissProfileManager : ViewEvent()
    data class LoadPresetProfile(val preset: NamedProfile) : ViewEvent()
    data class UpdateExistingProfile(val profile: UserProfile) : ViewEvent()
    data class SwitchToProfile(val profile: UserProfile) : ViewEvent()
    data class DeleteProfile(val name: String) : ViewEvent()
    object DismissWelcomeDialog : ViewEvent()

    // ============================================================
    // ПАМЯТЬ
    // ============================================================
    data class UpdateMemorySettings(val settings: MemorySettings) : ViewEvent()
    data class UpdateUserProfile(val profile: UserProfile) : ViewEvent()
    data class UpdateProjectConstraints(val constraints: ProjectConstraints) : ViewEvent()
    object ResetWorkingMemory : ViewEvent()

    // ============================================================
    // МОДЕЛЬ
    // ============================================================
    data class ChangeModel(val modelId: String) : ViewEvent()

    // ============================================================
    // КОМПРЕССИЯ
    // ============================================================
    data class ToggleCompression(val enabled: Boolean) : ViewEvent()
    data class UpdateCompressionParams(val keepLast: Int, val summarizeEvery: Int) : ViewEvent()

    // ============================================================
    // ДЕМОНСТРАЦИИ
    // ============================================================
    data class InitDemoManager(val onMessageAdded: (ChatMessageUI) -> Unit) : ViewEvent()
    object StartTokenDemo : ViewEvent()
    object StartCompressionDemo : ViewEvent()
    object StartStrategyDemo : ViewEvent()
    object StartMemoryDemo : ViewEvent()
    object StartPersonalizationDemo : ViewEvent()
    object StartStatefulDemo : ViewEvent()
    object StartInvariantDemo : ViewEvent()
    object StartTransitionDemo : ViewEvent()
    data class StartRagDemo(val query: String) : ViewEvent()
    object StartRagComparisonDemo : ViewEvent()
    object StartRagImprovedDemo : ViewEvent()
    object StartRagStructuredDemo : ViewEvent()
    object CancelDemo : ViewEvent()

    // ============================================================
    // RAG
    // ============================================================
    data class ToggleRagMode(val enabled: Boolean) : ViewEvent()
    data object ToggleRagSettings : ViewEvent()
    data class SetRagMode(val mode: RagMode) : ViewEvent()
    data class SetRerankerType(val type: RerankerType) : ViewEvent()
    data class SetSimilarityThreshold(val threshold: Float) : ViewEvent()
    data class SetTopKBefore(val topK: Int) : ViewEvent()
    data class SetTopKAfter(val topK: Int) : ViewEvent()

    // ============================================================
    // ИНВАРИАНТЫ
    // ============================================================
    data class SelectInvariantSet(val set: InvariantSet) : ViewEvent()
    data class CreateInvariantSetFromPreset(val name: String) : ViewEvent()
    object ClearActiveInvariantSet : ViewEvent()
    object RefreshInvariantSets : ViewEvent()

    // ============================================================
    // ТОКЕНЫ
    // ============================================================
    object ClearTokenStats : ViewEvent()
    object RefreshTokenStats : ViewEvent()

    // ============================================================
    // API
    // ============================================================
    object RefreshApiKeys : ViewEvent()
    object ForceRotateToNextKey : ViewEvent()

    // ============================================================
    // СИСТЕМНЫЕ
    // ============================================================
    data class SetChatMemoryService(val service: ChatMemoryAgent) : ViewEvent()
    data class ExecuteCommand(val command: String) : ViewEvent()
    data class GetChatSession(val onResult: (ChatSession) -> Unit) : ViewEvent()
    data class RebuildHistoryFromUiMessages(val messages: List<Pair<String, String>>) : ViewEvent()

    // Дополнительные события для UI
    data class AddMessage(val message: ChatMessageUI) : ViewEvent()
    data class AddDemoMessage(val message: ChatMessageUI) : ViewEvent()

    data class SetControlEnabled(val enabled: Boolean) : ViewEvent()
    data class SetFormatDescription(val format: String) : ViewEvent()
    data class SetMaxTokens(val tokens: Int?) : ViewEvent()
    data class SetStopSequences(val stops: List<String>?) : ViewEvent()
    data class SetTemperature(val temp: Double?) : ViewEvent()
    data class LoadPreset(val number: Int) : ViewEvent()
    object ResetToDefault : ViewEvent()

    data class BlockTask(val reason: String = "Блокировка") : ViewEvent()
    object UnblockTask : ViewEvent()
    object ToggleCreateTaskDialog : ViewEvent()

    // ============================================================
    // MCP
    // ============================================================
    object ConnectDataMcp : ViewEvent()
    object DisconnectDataMcp : ViewEvent()
    object ConnectPipelineMcp : ViewEvent()
    object DisconnectPipelineMcp : ViewEvent()
    data class OnMcpLog(val message: String) : ViewEvent()

    // ============================================================
    // КОЛЛЕКТОР МАТЧЕЙ (периодический сбор данных)
    // ============================================================
    data class StartCollector(val intervalMinutes: Double = 15.0) : ViewEvent()
    object StopCollector : ViewEvent()
    object CollectNow : ViewEvent()
    data class OnCollectorLog(val message: String) : ViewEvent()
    data class OnCollectorSummary(val summaryText: String) : ViewEvent()
    object ClearCollectorLog : ViewEvent()
}
