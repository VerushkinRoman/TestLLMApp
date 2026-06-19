// src/main/kotlin/com/llmapp/ui/viewmodel/ChatViewModel.kt

package com.llmapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llmapp.agent.ChatMemoryAgent
import com.llmapp.agent.StatefulMemoryAgent
import com.llmapp.api.ApiConfig
import com.llmapp.chat.ChatSession
import com.llmapp.domain.usercase.CompressionUseCase
import com.llmapp.domain.usercase.MemoryUseCase
import com.llmapp.domain.usercase.MessageUseCase
import com.llmapp.domain.usercase.ModelUseCase
import com.llmapp.domain.usercase.ProfileUseCase
import com.llmapp.domain.usercase.SnapshotUseCase
import com.llmapp.domain.usercase.TaskUseCase
import com.llmapp.domain.usercase.TransitionUseCase
import com.llmapp.invariants.InvariantManager
import com.llmapp.invariants.InvariantPresets
import com.llmapp.memory.LongTermMemoryManager
import com.llmapp.model.TokenStats
import com.llmapp.state.TaskPhase
import com.llmapp.ui.DemoManager
import com.llmapp.ui.DemoType
import com.llmapp.ui.components.ProfileManager
import com.llmapp.ui.models.ChatMessageUI
import com.llmapp.ui.models.TaskStateUI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.time.Duration.Companion.milliseconds

class ChatViewModel : ViewModel() {
    // ============================================================
    // СОСТОЯНИЕ
    // ============================================================

    private val _state = MutableStateFlow(ChatViewState.initial())
    val state: StateFlow<ChatViewState> = _state.asStateFlow()

    // StateFlow для UI
    val taskState: StateFlow<TaskStateUI?> get() = taskUseCase.taskState
    val demoManagerCurrentDemo = MutableStateFlow<DemoType?>(null)
    val demoManagerProgress = MutableStateFlow<String?>(null)

    // ============================================================
    // СЕРВИСЫ
    // ============================================================

    private val invariantManager = InvariantManager()
    private lateinit var chatSession: ChatSession
    private lateinit var statefulAgent: StatefulMemoryAgent
    private lateinit var profileManager: ProfileManager
    private var chatMemoryService: ChatMemoryAgent? = null
    private var demoManager: DemoManager? = null

    // ============================================================
    // USECASE'ы
    // ============================================================

    private lateinit var messageUseCase: MessageUseCase
    private lateinit var taskUseCase: TaskUseCase
    private lateinit var transitionUseCase: TransitionUseCase
    private lateinit var profileUseCase: ProfileUseCase
    private lateinit var snapshotUseCase: SnapshotUseCase
    private lateinit var modelUseCase: ModelUseCase
    private lateinit var compressionUseCase: CompressionUseCase
    private lateinit var memoryUseCase: MemoryUseCase

    // ============================================================
    // ИНИЦИАЛИЗАЦИЯ
    // ============================================================

    init {
        initServices()
        initUseCases()
        loadSelectedInvariantSet()

        viewModelScope.launch {
            kotlinx.coroutines.delay(100.milliseconds)
            if (_state.value.activeInvariantSetName == null) {
                val set = InvariantPresets.getBaseInvariants()
                invariantManager.saveInvariantSet(set)
                handleEvent(ViewEvent.SelectInvariantSet(set))
            }
        }
    }

    private fun initServices() {
        val apiKey = ApiConfig.getApiKey()

        chatSession = ChatSession(
            apiKey = apiKey,
            compressionEnabled = _state.value.compressionEnabled,
            keepLastMessages = _state.value.keepLastMessages,
            summarizeEvery = _state.value.summarizeEvery
        )

        statefulAgent = StatefulMemoryAgent(apiKey = apiKey)

        val storageDir = File(System.getProperty("user.home"), ".llm_chat_app")
        val longTermManager = LongTermMemoryManager(storageDir)
        profileManager = ProfileManager(storageDir, longTermManager)
    }

    private fun initUseCases() {
        messageUseCase = MessageUseCase(
            chatSession = chatSession,
            onStateUpdate = { _state.value = it },
            onTokenStatsUpdate = { updateTokenStats() }
        )

        taskUseCase = TaskUseCase(statefulAgent = statefulAgent)
        transitionUseCase = TransitionUseCase(statefulAgent = statefulAgent)
        profileUseCase = ProfileUseCase(profileManager = profileManager)
        snapshotUseCase = SnapshotUseCase(statefulAgent = statefulAgent)
        modelUseCase = ModelUseCase(
            chatSession = chatSession,
            onTokenStatsUpdate = { updateTokenStats() }
        )
        compressionUseCase = CompressionUseCase(
            onChatSessionUpdate = { newSession -> chatSession = newSession },
            onTokenStatsUpdate = { updateTokenStats() }
        )
        memoryUseCase = MemoryUseCase(
            memoryAwareAgent = com.llmapp.agent.MemoryAwareAgent(
                apiKey = ApiConfig.getApiKey(),
                model = chatSession.getCurrentModel(),
                systemPrompt = "Ты полезный ассистент. Отвечай на русском языке.",
                persistToDisk = true
            )
        )
    }

    // ============================================================
    // ЕДИНЫЙ МЕТОД ОБРАБОТКИ СОБЫТИЙ
    // ============================================================

    fun handleEvent(event: ViewEvent) {
        when (event) {
            // ============================================================
            // СООБЩЕНИЯ
            // ============================================================

            is ViewEvent.SendMessage -> {
                if (_state.value.isGenerating || _state.value.isDemoRunning) return
                if (event.text.startsWith("/")) {
                    handleCommand(event.text)
                    return
                }
                messageUseCase.sendMessage(
                    _state.value,
                    event.text,
                    event.addToHistory
                ) { newState ->
                    _state.value = newState
                    updateTokenStats()
                }
            }

            is ViewEvent.RegenerateMessage -> {
                messageUseCase.regenerateMessage(_state.value, event.messageId) { newState ->
                    _state.value = newState
                    updateTokenStats()
                }
            }

            is ViewEvent.EditUserMessage -> {
                messageUseCase.editUserMessage(
                    _state.value,
                    event.messageId,
                    event.newContent
                ) { newState ->
                    _state.value = newState
                    updateTokenStats()
                }
            }

            is ViewEvent.UpdateDraft -> {
                if (_state.value.isDemoRunning) return
                updateState { copy(draftMessage = event.text, cursorPosition = event.cursorPos) }
            }

            ViewEvent.StopGeneration -> {
                _state.value = messageUseCase.stopGeneration(_state.value)
            }

            ViewEvent.ClearHistory -> {
                _state.value = messageUseCase.clearHistory(_state.value)
                updateTokenStats()
            }

            // ============================================================
            // ЗАДАЧИ
            // ============================================================

            is ViewEvent.CreateTask -> {
                _state.value = taskUseCase.createTask(_state.value, event.name, event.description)
                updateState { copy(showCreateTaskDialog = false) }
            }

            is ViewEvent.TransitionTo -> {
                _state.value = taskUseCase.transitionTo(_state.value, event.phase)
            }

            is ViewEvent.PauseTask -> {
                _state.value = taskUseCase.pauseTask(_state.value, event.reason)
            }

            ViewEvent.ResumeTask -> {
                _state.value = taskUseCase.resumeTask(_state.value)
            }

            ViewEvent.ShowStatus -> {
                _state.value = taskUseCase.getStatus(_state.value)
            }

            ViewEvent.ClearTask -> {
                _state.value = taskUseCase.clearTask(_state.value)
            }

            // ============================================================
            // ПЕРЕХОДЫ
            // ============================================================

            ViewEvent.ShowTransitionsDialog -> {
                _state.value = transitionUseCase.showTransitionsDialog(_state.value)
            }

            ViewEvent.DismissTransitionsDialog -> {
                _state.value = transitionUseCase.dismissTransitionsDialog(_state.value)
            }

            ViewEvent.ApprovePlan -> {
                _state.value = transitionUseCase.approvePlan(_state.value)
            }

            ViewEvent.Validate -> {
                _state.value = transitionUseCase.validate(_state.value)
            }

            is ViewEvent.SafeTransitionTo -> {
                _state.value = transitionUseCase.safeTransitionTo(_state.value, event.phase)
            }

            // ============================================================
            // СНИМКИ
            // ============================================================

            ViewEvent.ToggleSnapshotDialog -> {
                _state.value = snapshotUseCase.toggleSnapshotDialog(_state.value)
            }

            ViewEvent.DismissSnapshotDialog -> {
                _state.value = snapshotUseCase.dismissSnapshotDialog(_state.value)
            }

            is ViewEvent.CreateSnapshot -> {
                _state.value = snapshotUseCase.createSnapshot(_state.value, event.name)
            }

            is ViewEvent.RestoreSnapshot -> {
                _state.value = snapshotUseCase.restoreSnapshot(_state.value, event.id)
                updateTokenStats()
            }

            // ============================================================
            // ПРОФИЛИ
            // ============================================================

            ViewEvent.ToggleProfileManager -> {
                _state.value = profileUseCase.toggleProfileManager(_state.value)
            }

            ViewEvent.DismissProfileManager -> {
                _state.value = profileUseCase.dismissProfileManager(_state.value)
            }

            is ViewEvent.LoadPresetProfile -> {
                _state.value = profileUseCase.loadPresetProfile(_state.value, event.preset)
            }

            is ViewEvent.UpdateExistingProfile -> {
                _state.value = profileUseCase.updateExistingProfile(_state.value, event.profile)
            }

            is ViewEvent.SwitchToProfile -> {
                _state.value = profileUseCase.switchToProfile(_state.value, event.profile)
            }

            is ViewEvent.DeleteProfile -> {
                _state.value = profileUseCase.deleteProfile(_state.value, event.name)
            }

            ViewEvent.DismissWelcomeDialog -> {
                _state.value = profileUseCase.dismissWelcomeDialog(_state.value)
            }

            // ============================================================
            // ПАМЯТЬ
            // ============================================================

            is ViewEvent.UpdateMemorySettings -> {
                _state.value = memoryUseCase.updateMemorySettings(_state.value, event.settings)
            }

            is ViewEvent.UpdateUserProfile -> {
                _state.value = memoryUseCase.updateUserProfile(_state.value, event.profile)
            }

            is ViewEvent.UpdateProjectConstraints -> {
                _state.value =
                    memoryUseCase.updateProjectConstraints(_state.value, event.constraints)
            }

            ViewEvent.ResetWorkingMemory -> {
                _state.value = memoryUseCase.resetWorkingMemory(_state.value)
            }

            // ============================================================
            // МОДЕЛЬ
            // ============================================================

            is ViewEvent.ChangeModel -> {
                _state.value = modelUseCase.changeModel(_state.value, event.modelId)
            }

            // ============================================================
            // КОМПРЕССИЯ
            // ============================================================

            is ViewEvent.ToggleCompression -> {
                _state.value = compressionUseCase.toggleCompression(_state.value, event.enabled)
            }

            is ViewEvent.UpdateCompressionParams -> {
                _state.value = compressionUseCase.updateCompressionParams(
                    _state.value,
                    event.keepLast,
                    event.summarizeEvery
                )
            }

            // ============================================================
            // ДЕМОНСТРАЦИИ
            // ============================================================

            is ViewEvent.InitDemoManager -> {
                initDemoManager(event.onMessageAdded)
            }

            ViewEvent.StartTokenDemo -> demoManager?.startTokenDemo()
            ViewEvent.StartCompressionDemo -> demoManager?.startCompressionDemo()
            ViewEvent.StartStrategyDemo -> demoManager?.startStrategyDemo()
            ViewEvent.StartMemoryDemo -> demoManager?.startMemoryDemo()
            ViewEvent.StartPersonalizationDemo -> demoManager?.startPersonalizationDemo()
            ViewEvent.StartStatefulDemo -> demoManager?.startStatefulDemo()
            ViewEvent.StartInvariantDemo -> demoManager?.startInvariantDemo()
            ViewEvent.StartTransitionDemo -> demoManager?.startTransitionDemo()
            ViewEvent.CancelDemo -> demoManager?.cancelDemo()

            // ============================================================
            // ИНВАРИАНТЫ
            // ============================================================

            is ViewEvent.SelectInvariantSet -> {
                updateState { copy(activeInvariantSetName = event.set.name) }
                val prefs = Preferences.userRoot().node("com.llmapp.invariants")
                prefs.put("active_set", event.set.name)
                println("🔒 Выбран набор инвариантов: ${event.set.name}")
            }

            is ViewEvent.CreateInvariantSetFromPreset -> {
                val set = when (event.name.lowercase()) {
                    "android" -> InvariantPresets.getAndroidKMPInvariants()
                    "web" -> InvariantPresets.getWebInvariants()
                    else -> InvariantPresets.getBaseInvariants()
                }
                invariantManager.saveInvariantSet(set)
            }

            ViewEvent.ClearActiveInvariantSet -> {
                updateState { copy(activeInvariantSetName = null) }
                val prefs = Preferences.userRoot().node("com.llmapp.invariants")
                prefs.remove("active_set")
                println("🔒 Активный набор инвариантов сброшен")
            }

            ViewEvent.RefreshInvariantSets -> {
                val allSets = invariantManager.getAllInvariantSets()
                updateState {
                    copy(invariantSets = allSets)
                }
                println("🔄 Список инвариантов обновлен: ${allSets.size} наборов")
            }

            // ============================================================
            // ТОКЕНЫ
            // ============================================================

            ViewEvent.ClearTokenStats -> {
                if (_state.value.isDemoRunning) return
                chatSession.clearTokenStats()
                updateTokenStats()
            }

            ViewEvent.RefreshTokenStats -> {
                if (!_state.value.isDemoRunning) {
                    updateTokenStats()
                }
            }

            // ============================================================
            // API
            // ============================================================

            ViewEvent.RefreshApiKeys -> chatSession.refreshApiKeys()
            ViewEvent.ForceRotateToNextKey -> {
                ApiConfig.rotateToNextKey()
                chatSession.refreshApiKeys()
            }

            // ============================================================
            // СИСТЕМНЫЕ
            // ============================================================

            is ViewEvent.SetChatMemoryService -> {
                chatMemoryService = event.service
            }

            is ViewEvent.ExecuteCommand -> {
                handleCommand(event.command)
            }

            is ViewEvent.GetChatSession -> {
                event.onResult(chatSession)
            }

            is ViewEvent.AddMessage -> {
                updateState { copy(messages = messages + event.message) }
            }

            is ViewEvent.AddDemoMessage -> {
                updateState { copy(messages = messages + event.message) }
                if (!_state.value.isDemoRunning) {
                    updateTokenStats()
                }
            }

            is ViewEvent.SetControlEnabled -> {
                val newControl = _state.value.responseControl.copy(enabled = event.enabled)
                chatSession.setResponseControl(newControl)
                updateState {
                    copy(
                        responseControl = newControl,
                        controlEnabled = event.enabled
                    )
                }
            }

            is ViewEvent.SetFormatDescription -> {
                val newControl = _state.value.responseControl.copy(
                    formatDescription = event.format.ifBlank { null },
                    enabled = true
                )
                chatSession.setResponseControl(newControl)
                updateState {
                    copy(
                        responseControl = newControl,
                        controlEnabled = true
                    )
                }
            }

            is ViewEvent.SetMaxTokens -> {
                val newControl = _state.value.responseControl.copy(
                    maxTokens = event.tokens,
                    enabled = true
                )
                chatSession.setResponseControl(newControl)
                updateState {
                    copy(
                        responseControl = newControl,
                        controlEnabled = true
                    )
                }
            }

            is ViewEvent.SetStopSequences -> {
                val newControl = _state.value.responseControl.copy(
                    stopSequences = event.stops,
                    enabled = true
                )
                chatSession.setResponseControl(newControl)
                updateState {
                    copy(
                        responseControl = newControl,
                        controlEnabled = true
                    )
                }
            }

            is ViewEvent.SetTemperature -> {
                val newControl = _state.value.responseControl.copy(
                    temperature = event.temp,
                    enabled = true
                )
                chatSession.setResponseControl(newControl)
                updateState {
                    copy(
                        responseControl = newControl,
                        controlEnabled = true
                    )
                }
            }

            is ViewEvent.LoadPreset -> {
                val preset = com.llmapp.controller.PresetManager.getPreset(event.number)
                if (preset != null) {
                    chatSession.setResponseControl(preset)
                    updateState {
                        copy(
                            responseControl = preset,
                            controlEnabled = preset.enabled
                        )
                    }
                }
            }

            ViewEvent.ResetToDefault -> {
                val defaultControl = com.llmapp.controller.PresetManager.getDefaultControl()
                chatSession.setResponseControl(defaultControl)
                updateState {
                    copy(
                        responseControl = defaultControl,
                        controlEnabled = defaultControl.enabled
                    )
                }
            }

            is ViewEvent.BlockTask -> {
                _state.value = taskUseCase.blockTask(_state.value, event.reason)
            }

            ViewEvent.UnblockTask -> {
                _state.value = taskUseCase.unblockTask(_state.value)
            }

            ViewEvent.ToggleCreateTaskDialog -> {
                updateState { copy(showCreateTaskDialog = !showCreateTaskDialog) }
            }

            is ViewEvent.RebuildHistoryFromUiMessages -> {
                chatSession.rebuildHistoryFromUiMessages(event.messages)
            }
        }
    }

    // ============================================================
    // ОБНОВЛЕНИЕ СОСТОЯНИЯ
    // ============================================================

    private fun updateState(update: ChatViewState.() -> ChatViewState) {
        _state.update { it.update() }
    }

    private fun updateTokenStats() {
        updateState {
            copy(
                tokenStats = chatSession.getTokenStats(),
                tokenHistory = chatSession.getTokenHistory(),
                contextWarning = chatSession.getContextWarning(),
                compressionStats = chatSession.getCompressionStats()
            )
        }
    }

    // ============================================================
    // ДЕМОНСТРАЦИИ (ВНУТРЕННИЙ МЕТОД)
    // ============================================================

    private fun initDemoManager(onMessageAdded: (ChatMessageUI) -> Unit) {
        chatMemoryService?.markAsDemoMode()

        demoManager = DemoManager(
            chatSession = chatSession,
            onMessageAdded = { message -> onMessageAdded(message) },
            onDemoStarted = {
                updateState {
                    copy(
                        isDemoRunning = true,
                        isGenerating = true,
                        isTyping = true,
                        tokenStats = TokenStats(),
                        tokenHistory = emptyList(),
                        contextWarning = "✅ Демонстрация запущена..."
                    )
                }
                demoManagerCurrentDemo.value = demoManager?.currentDemo?.value
                demoManagerProgress.value = demoManager?.progressMessage?.value
            },
            onDemoFinished = {
                updateState {
                    copy(
                        isDemoRunning = false,
                        isGenerating = false,
                        isTyping = false
                    )
                }
                chatMemoryService?.createNewChat()
                updateTokenStats()
                demoManagerCurrentDemo.value = null
                demoManagerProgress.value = null
            },
            onTypingStateChanged = { typing -> updateState { copy(isTyping = typing) } },
            onStatsUpdated = { stats ->
                if (_state.value.isDemoRunning) updateState { copy(tokenStats = stats) }
            },
            onTokenHistoryUpdated = { history ->
                if (_state.value.isDemoRunning) updateState { copy(tokenHistory = history) }
            },
            onContextWarningUpdated = { warning ->
                if (_state.value.isDemoRunning) updateState { copy(contextWarning = warning) }
            },
            onTaskStateUpdated = {
                // Обновляем состояние задачи через TaskUseCase
                // taskState обновляется автоматически через StateFlow
                // Но вызываем принудительное обновление для UI
                taskUseCase.taskState.value?.let { state ->
                    // Можно обновить что-то дополнительно в UI
                    println("🔄 Состояние задачи обновлено: ${state.phase.displayName}")
                }
            },
            statefulAgent = statefulAgent
        )

        viewModelScope.launch {
            demoManager?.isRunning?.collect { running ->
                updateState { copy(isDemoRunning = running) }
            }
        }
        viewModelScope.launch {
            demoManager?.currentDemo?.collect { demo ->
                demoManagerCurrentDemo.value = demo
            }
        }
        viewModelScope.launch {
            demoManager?.progressMessage?.collect { progress ->
                demoManagerProgress.value = progress
            }
        }
    }

    // ============================================================
    // ОБРАБОТКА КОМАНД
    // ============================================================

    private fun handleCommand(text: String) {
        when {
            text.startsWith("/task ") -> {
                val taskName = text.removePrefix("/task ").trim()
                if (taskName.isNotEmpty()) {
                    handleEvent(ViewEvent.CreateTask(taskName))
                } else {
                    addAssistantMessage("⚠️ Укажите название задачи: /task <название>")
                }
            }

            text == "/status" -> handleEvent(ViewEvent.ShowStatus)
            text == "/clear-task" -> handleEvent(ViewEvent.ClearTask)

            text == "/transitions" || text == "/available" -> handleEvent(ViewEvent.ShowTransitionsDialog)
            text == "/approve-plan" -> handleEvent(ViewEvent.ApprovePlan)
            text == "/validate" -> handleEvent(ViewEvent.Validate)

            text == "/planning" -> handleEvent(ViewEvent.SafeTransitionTo(TaskPhase.PLANNING))
            text == "/execution" -> handleEvent(ViewEvent.SafeTransitionTo(TaskPhase.EXECUTION))
            text == "/validation" -> handleEvent(ViewEvent.SafeTransitionTo(TaskPhase.VALIDATION))
            text == "/done" -> handleEvent(ViewEvent.SafeTransitionTo(TaskPhase.DONE))

            text == "/pause" -> handleEvent(ViewEvent.PauseTask("Пауза по команде /pause"))
            text == "/resume" -> handleEvent(ViewEvent.ResumeTask)

            text == "/snapshots" -> {
                handleEvent(ViewEvent.ToggleSnapshotDialog)
                addAssistantMessage("📸 Открыт диалог управления снимками")
            }

            text == "/tokens" -> {
                val stats = chatSession.getTokenStats()
                addAssistantMessage(buildTokenStatsMessage(stats))
            }

            text.startsWith("/invariant-preset ") -> {
                val presetName = text.removePrefix("/invariant-preset ").trim()
                if (presetName.isNotEmpty()) {
                    val success = invariantManager.saveInvariantSet(
                        when (presetName.lowercase()) {
                            "android" -> InvariantPresets.getAndroidKMPInvariants()
                            "web" -> InvariantPresets.getWebInvariants()
                            else -> InvariantPresets.getBaseInvariants()
                        }
                    )
                    addAssistantMessage(
                        if (success) "✅ Набор инвариантов '$presetName' создан и активирован!"
                        else "❌ Не удалось создать набор инвариантов '$presetName'"
                    )
                } else {
                    addAssistantMessage("⚠️ Укажите название пресета: /invariant-preset <android|web|base>")
                }
            }

            text == "/help" -> addAssistantMessage(buildHelpText())
            else -> addAssistantMessage("⚠️ Неизвестная команда: $text\nИспользуйте /help для списка команд")
        }
    }

    // ============================================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ============================================================

    private fun addAssistantMessage(content: String) {
        val message = ChatMessageUI(
            id = UUID.randomUUID().toString(),
            role = "assistant",
            content = content,
            isDemoMessage = false
        )
        _state.value = _state.value.copy(messages = _state.value.messages + message)
    }

    private fun buildHelpText(): String {
        return """
            📚 ДОСТУПНЫЕ КОМАНДЫ:
            
            🎯 Управление задачами:
              /task <название> - создать новую задачу
              /status - показать полный статус задачи
              /clear-task - очистить состояние задачи
              
            🔒 Инварианты:
              /invariant-preset <android|web|base> - создать набор инвариантов из пресета
              
            ⏸️ Управление состоянием:
              /pause - поставить задачу на паузу
              /resume - возобновить задачу
              
            📸 Снимки:
              /snapshots - открыть диалог управления снимками
              
            📊 Токены:
              /tokens - показать статистику токенов
              
            🔄 Управление переходами:
              /transitions или /available - показать диалог управления переходами
              /approve-plan - утвердить план (PLANNING → EXECUTION)
              /validate - подтвердить валидацию (VALIDATION → DONE)
              /planning - перейти в PLANNING
              /execution - перейти в EXECUTION
              /validation - перейти в VALIDATION
              /done - завершить задачу
              
            ℹ️ Справка:
              /help - показать эту справку
            
            💡 Также используйте панель управления в интерфейсе чата!
        """.trimIndent()
    }

    private fun buildTokenStatsMessage(stats: TokenStats): String {
        return """
            📊 СТАТИСТИКА ТОКЕНОВ:
            
            • Запросов: ${stats.requestCount}
            • Всего токенов: ${stats.totalTokens}
            • Prompt токенов: ${stats.totalPromptTokens}
            • Completion токенов: ${stats.totalCompletionTokens}
            • Стоимость: ${stats.getFormattedCost()}
            
            ${chatSession.getContextWarning()}
        """.trimIndent()
    }

    private fun loadSelectedInvariantSet() {
        val prefs = Preferences.userRoot().node("com.llmapp.invariants")
        val name = prefs.get("active_set", null)
        if (name != null) {
            val set = invariantManager.loadInvariantSet(name)
            if (set != null) {
                updateState { copy(activeInvariantSetName = name) }
            }
        }
    }
}
