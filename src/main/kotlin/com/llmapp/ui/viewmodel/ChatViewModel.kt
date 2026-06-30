package com.llmapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llmapp.agent.ChatMemoryAgent
import com.llmapp.agent.StatefulMemoryAgent
import com.llmapp.api.ApiConfig
import com.llmapp.chat.ChatSession
import com.llmapp.collector.MatchAggregator
import com.llmapp.collector.MatchStore
import com.llmapp.collector.PeriodicCollector
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
import com.llmapp.mcp.McpIntegration
import com.llmapp.memory.LongTermMemoryManager
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
    private val _state = MutableStateFlow(ChatViewState.initial())
    val state: StateFlow<ChatViewState> = _state.asStateFlow()
    val taskState: StateFlow<TaskStateUI?> get() = taskUseCase.taskState
    val demoManagerCurrentDemo = MutableStateFlow<com.llmapp.ui.DemoType?>(null)
    val demoManagerProgress = MutableStateFlow<String?>(null)

    private val invariantManager = InvariantManager()
    private lateinit var chatSession: ChatSession
    private val dataMcpIntegration = McpIntegration(
        name = "data",
        serverUrl = "https://alcoserver.ru:4454/mcp",
        onLog = { handleEvent(ViewEvent.OnMcpLog(it)) }
    )
    private val pipelineMcpIntegration = McpIntegration(
        name = "pipeline",
        serverUrl = "https://alcoserver.ru:4456/mcp",
        onLog = { handleEvent(ViewEvent.OnMcpLog(it)) }
    )
    private lateinit var statefulAgent: StatefulMemoryAgent
    private lateinit var profileManager: ProfileManager
    private var chatMemoryService: ChatMemoryAgent? = null

    private lateinit var messageUseCase: MessageUseCase
    private lateinit var taskUseCase: TaskUseCase
    private lateinit var transitionUseCase: TransitionUseCase
    private lateinit var profileUseCase: ProfileUseCase
    private lateinit var snapshotUseCase: SnapshotUseCase
    private lateinit var modelUseCase: ModelUseCase
    private lateinit var compressionUseCase: CompressionUseCase
    private lateinit var memoryUseCase: MemoryUseCase

    private lateinit var demoHandler: DemoHandler
    private lateinit var commandHandler: CommandHandler
    private var collector: PeriodicCollector? = null
    private var matchStore: MatchStore? = null

    init {
        initServices()
        initUseCases()
        initHandlers()
        loadSelectedInvariantSet()

        viewModelScope.launch {
            taskUseCase.taskState.collect { taskState ->
                updateState { copy(taskState = taskState) }
            }
        }

        viewModelScope.launch {
            kotlinx.coroutines.delay(100.milliseconds)
            if (_state.value.activeInvariantSetName == null) {
                val set = InvariantPresets.getBaseInvariants()
                invariantManager.saveInvariantSet(set)
                handleEvent(ViewEvent.SelectInvariantSet(set))
            }
        }

        viewModelScope.launch {
            demoHandler.demoManagerCurrentDemo.collect { demo ->
                demoManagerCurrentDemo.value = demo
            }
        }
        viewModelScope.launch {
            demoHandler.demoManagerProgress.collect { progress ->
                demoManagerProgress.value = progress
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
        ).also {
            it.dataIntegration = dataMcpIntegration
            it.pipelineIntegration = pipelineMcpIntegration
            it.logListener = { msg -> addLogMessage(msg) }
        }
        statefulAgent = StatefulMemoryAgent(apiKey = apiKey)
        val storageDir = File(System.getProperty("user.home"), ".llm_chat_app")
        val longTermManager = LongTermMemoryManager(storageDir)
        profileManager = ProfileManager(storageDir, longTermManager)
        val store = MatchStore(File(storageDir, "match_collector"))
        matchStore = store
        collector = PeriodicCollector(
            store = store,
            onLog = { handleEvent(ViewEvent.OnCollectorLog(it)) },
            onSummaryGenerated = {
                val text = MatchAggregator.generateTextSummary(
                    store.loadLatestSnapshot() ?: return@PeriodicCollector
                )
                handleEvent(ViewEvent.OnCollectorSummary(text))
            }
        )
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

    private fun initHandlers() {
        demoHandler = DemoHandler(
            chatSession = chatSession,
            statefulAgent = statefulAgent,
            taskUseCase = taskUseCase,
            viewModelScope = viewModelScope,
            chatMemoryService = chatMemoryService
        )
        commandHandler = CommandHandler(
            invariantManager = invariantManager,
            onHandleEvent = { handleEvent(it) },
            onAddAssistantMessage = { addAssistantMessage(it) },
            onGetTokenStats = { chatSession.getTokenStats() }
        )
    }

    fun handleEvent(event: ViewEvent) {
        when (event) {
            is ViewEvent.SendMessage -> {
                if (_state.value.isGenerating || _state.value.isDemoRunning) return
                if (event.text.startsWith("/")) {
                    commandHandler.handleCommand(event.text)
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

            is ViewEvent.ChangeModel -> {
                _state.value = modelUseCase.changeModel(_state.value, event.modelId)
            }

            is ViewEvent.ToggleRagMode -> {
                updateState { copy(ragModeEnabled = event.enabled) }
            }

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

            is ViewEvent.InitDemoManager -> {
                demoHandler.initDemoManager(
                    onMessageAdded = event.onMessageAdded,
                    updateState = { update -> updateState(update) },
                    updateTokenStats = { updateTokenStats() }
                )
            }

            ViewEvent.StartTokenDemo -> demoHandler.startTokenDemo()
            ViewEvent.StartCompressionDemo -> demoHandler.startCompressionDemo()
            ViewEvent.StartStrategyDemo -> demoHandler.startStrategyDemo()
            ViewEvent.StartMemoryDemo -> demoHandler.startMemoryDemo()
            ViewEvent.StartPersonalizationDemo -> demoHandler.startPersonalizationDemo()
            ViewEvent.StartStatefulDemo -> demoHandler.startStatefulDemo()
            ViewEvent.StartInvariantDemo -> demoHandler.startInvariantDemo()
            ViewEvent.StartTransitionDemo -> demoHandler.startTransitionDemo()
            is ViewEvent.StartRagDemo -> demoHandler.startRagDemo(event.query)
            ViewEvent.StartRagComparisonDemo -> demoHandler.startRagComparisonDemo()
            ViewEvent.CancelDemo -> demoHandler.cancelDemo()

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
                updateState { copy(invariantSets = allSets) }
                println("🔄 Список инвариантов обновлен: ${allSets.size} наборов")
            }

            ViewEvent.ClearTokenStats -> {
                if (_state.value.isDemoRunning) return
                chatSession.clearTokenStats()
                updateTokenStats()
            }

            ViewEvent.RefreshTokenStats -> {
                if (!_state.value.isDemoRunning) updateTokenStats()
            }

            ViewEvent.RefreshApiKeys -> chatSession.refreshApiKeys()

            ViewEvent.ForceRotateToNextKey -> {
                ApiConfig.rotateToNextKey()
                chatSession.refreshApiKeys()
            }

            is ViewEvent.SetChatMemoryService -> {
                chatMemoryService = event.service
            }

            is ViewEvent.ExecuteCommand -> {
                commandHandler.handleCommand(event.command)
            }

            is ViewEvent.GetChatSession -> {
                event.onResult(chatSession)
            }

            is ViewEvent.AddMessage -> {
                updateState { copy(messages = messages + event.message) }
            }

            is ViewEvent.AddDemoMessage -> {
                updateState { copy(messages = messages + event.message) }
                if (!_state.value.isDemoRunning) updateTokenStats()
            }

            is ViewEvent.SetControlEnabled -> {
                val newControl = _state.value.responseControl.copy(enabled = event.enabled)
                chatSession.setResponseControl(newControl)
                updateState { copy(responseControl = newControl, controlEnabled = event.enabled) }
            }

            is ViewEvent.SetFormatDescription -> {
                val newControl = _state.value.responseControl.copy(
                    formatDescription = event.format.ifBlank { null },
                    enabled = true
                )
                chatSession.setResponseControl(newControl)
                updateState { copy(responseControl = newControl, controlEnabled = true) }
            }

            is ViewEvent.SetMaxTokens -> {
                val newControl = _state.value.responseControl.copy(
                    maxTokens = event.tokens,
                    enabled = true
                )
                chatSession.setResponseControl(newControl)
                updateState { copy(responseControl = newControl, controlEnabled = true) }
            }

            is ViewEvent.SetStopSequences -> {
                val newControl = _state.value.responseControl.copy(
                    stopSequences = event.stops,
                    enabled = true
                )
                chatSession.setResponseControl(newControl)
                updateState { copy(responseControl = newControl, controlEnabled = true) }
            }

            is ViewEvent.SetTemperature -> {
                val newControl = _state.value.responseControl.copy(
                    temperature = event.temp,
                    enabled = true
                )
                chatSession.setResponseControl(newControl)
                updateState { copy(responseControl = newControl, controlEnabled = true) }
            }

            is ViewEvent.LoadPreset -> {
                val preset = com.llmapp.controller.PresetManager.getPreset(event.number)
                if (preset != null) {
                    chatSession.setResponseControl(preset)
                    updateState { copy(responseControl = preset, controlEnabled = preset.enabled) }
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

            ViewEvent.ConnectDataMcp -> {
                viewModelScope.launch {
                    updateState { copy(dataMcpConnected = true) }
                    addLogMessage("🔄 Подключение к MCP Data серверу (alcoserver.ru:4454)...")
                    try {
                        val result = dataMcpIntegration.connect()
                        addLogMessage("✅ MCP Data подключен: ${result.name} v${result.version}")
                        updateState { copy(dataMcpServerName = "${result.name} v${result.version}") }
                    } catch (e: Exception) {
                        addLogMessage("❌ MCP Data: ${e.message}")
                        updateState { copy(dataMcpConnected = false) }
                    }
                }
            }

            ViewEvent.DisconnectDataMcp -> {
                dataMcpIntegration.disconnect()
                addLogMessage("🔌 MCP Data отключен")
                updateState { copy(dataMcpConnected = false, dataMcpServerName = null) }
            }

            ViewEvent.ConnectPipelineMcp -> {
                viewModelScope.launch {
                    updateState { copy(pipelineMcpConnected = true) }
                    addLogMessage("🔄 Подключение к MCP Pipeline серверу (alcoserver.ru:4456)...")
                    try {
                        val result = pipelineMcpIntegration.connect()
                        addLogMessage("✅ MCP Pipeline подключен: ${result.name} v${result.version}")
                        updateState { copy(pipelineMcpServerName = "${result.name} v${result.version}") }
                    } catch (e: Exception) {
                        addLogMessage("❌ MCP Pipeline: ${e.message}")
                        updateState { copy(pipelineMcpConnected = false) }
                    }
                }
            }

            ViewEvent.DisconnectPipelineMcp -> {
                pipelineMcpIntegration.disconnect()
                addLogMessage("🔌 MCP Pipeline отключен")
                updateState { copy(pipelineMcpConnected = false, pipelineMcpServerName = null) }
            }

            is ViewEvent.OnMcpLog -> {
                addLogMessage("MCP: ${event.message}")
            }

            is ViewEvent.StartCollector -> {
                viewModelScope.launch {
                    addCollectorLog("🔄 Запуск периодического сбора (интервал: ${event.intervalMinutes} мин)...")
                    updateState {
                        copy(
                            collectorRunning = true,
                            collectorInterval = event.intervalMinutes
                        )
                    }
                    collector?.start(event.intervalMinutes)
                }
            }

            ViewEvent.StopCollector -> {
                collector?.stop()
                updateState { copy(collectorRunning = false) }
                addCollectorLog("⏹ Сбор остановлен")
            }

            ViewEvent.CollectNow -> {
                viewModelScope.launch {
                    addCollectorLog("📡 Принудительный сбор...")
                    collector?.collectOnce()
                }
            }

            is ViewEvent.OnCollectorLog -> {
                addCollectorLog(event.message)
            }

            is ViewEvent.OnCollectorSummary -> {
                updateState { copy(collectorSummary = event.summaryText) }
            }

            ViewEvent.ClearCollectorLog -> {
                updateState { copy(collectorLog = emptyList()) }
            }
        }
    }

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

    private fun addAssistantMessage(content: String) {
        val message = ChatMessageUI(
            id = UUID.randomUUID().toString(),
            role = "assistant",
            content = content,
            isDemoMessage = false
        )
        _state.value = _state.value.copy(messages = _state.value.messages + message)
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

    private fun addLogMessage(msg: String) {
        val entry = "[${java.time.LocalTime.now().withNano(0)}] $msg"
        updateState { copy(mcpLog = mcpLog + entry) }
    }

    private fun addCollectorLog(msg: String) {
        val entry = "[${java.time.LocalTime.now().withNano(0)}] $msg"
        updateState { copy(collectorLog = collectorLog + entry) }
    }
}
