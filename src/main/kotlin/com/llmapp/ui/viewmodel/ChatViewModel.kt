package com.llmapp.ui.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llmapp.agent.ChatMemoryAgent
import com.llmapp.agent.MemoryAwareAgent
import com.llmapp.agent.TokenSnapshot
import com.llmapp.api.ApiConfig
import com.llmapp.chat.ChatSession
import com.llmapp.controller.PresetManager
import com.llmapp.memory.ProjectConstraints
import com.llmapp.memory.UserProfile
import com.llmapp.model.TokenStats
import com.llmapp.model.freeModels
import com.llmapp.ui.DemoManager
import com.llmapp.ui.components.MemorySettings
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel : ViewModel() {
    private val apiKey = ApiConfig.getApiKey()

    private var _compressionEnabled = true
    private var _keepLastMessages = 8
    private var _summarizeEvery = 6

    val messages = mutableStateListOf<ChatMessageUI>()
    val isTyping = mutableStateOf(false)
    val currentModel = mutableStateOf("")
    val responseControl = mutableStateOf(com.llmapp.model.ResponseControl())
    val controlEnabled = mutableStateOf(false)
    val availableModels = mutableStateOf(freeModels)

    var compressionEnabled = mutableStateOf(true)
    var keepLastMessages = mutableStateOf(8)
    var summarizeEvery = mutableStateOf(6)

    val draftMessage = mutableStateOf("")
    val cursorPosition = mutableStateOf(0)

    val isGenerating = mutableStateOf(false)
    val isDemoRunning = mutableStateOf(false)

    private var currentGenerationJob: kotlinx.coroutines.Job? = null

    // StateFlow поля
    private val _tokenStatsFlow = MutableStateFlow(TokenStats())
    val tokenStatsFlow: StateFlow<TokenStats> = _tokenStatsFlow.asStateFlow()

    private val _tokenHistoryFlow = MutableStateFlow(emptyList<TokenSnapshot>())
    val tokenHistoryFlow: StateFlow<List<TokenSnapshot>> = _tokenHistoryFlow.asStateFlow()

    private val _contextWarningFlow = MutableStateFlow("")
    val contextWarningFlow: StateFlow<String> = _contextWarningFlow.asStateFlow()

    val compressionStats =
        mutableStateOf(null as com.llmapp.agent.CompressedChatHistory.CompressionStats?)

    // StateFlow для памяти
    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _projectConstraints = MutableStateFlow(ProjectConstraints())
    val projectConstraints: StateFlow<ProjectConstraints> = _projectConstraints.asStateFlow()

    // Поздняя инициализация для остальных полей
    private lateinit var chatSession: ChatSession
    private var memoryAwareAgent: MemoryAwareAgent? = null
    private var chatMemoryService: ChatMemoryAgent? = null
    private var demoManager: DemoManager? = null

    init {
        initChatSession()
    }

    private fun initChatSession() {
        chatSession = ChatSession(
            apiKey = apiKey,
            compressionEnabled = _compressionEnabled,
            keepLastMessages = _keepLastMessages,
            summarizeEvery = _summarizeEvery
        )

        // Теперь можно безопасно устанавливать значения
        currentModel.value = chatSession.getCurrentModel()
        responseControl.value = chatSession.getResponseControl()
        controlEnabled.value = responseControl.value.enabled

        initMemoryAgent(
            apiKey,
            chatSession.getCurrentModel(),
            "Ты полезный ассистент. Отвечай на русском языке."
        )
    }

    fun startPersonalizationDemo() {
        demoManager?.startPersonalizationDemo()
    }

    private fun recreateChatSession() {
        val newSession = ChatSession(
            apiKey = apiKey,
            compressionEnabled = _compressionEnabled,
            keepLastMessages = _keepLastMessages,
            summarizeEvery = _summarizeEvery
        )

        val currentMessages = messages.toList()

        chatSession = newSession

        if (currentMessages.isNotEmpty()) {
            val uiMessages = currentMessages.map { it.role to it.content }
            chatSession.rebuildHistoryFromUiMessages(uiMessages)
        }

        currentModel.value = chatSession.getCurrentModel()
        updateTokenStats()
    }

    fun updateMemorySettings(settings: MemorySettings) {
        memoryAwareAgent?.let { agent ->
            agent.useShortTerm = settings.useShortTerm
            agent.useWorkingMemory = settings.useWorkingMemory
            agent.useLongTerm = settings.useLongTerm
            println("📊 Настройки памяти обновлены: STM=${settings.useShortTerm}, WM=${settings.useWorkingMemory}, LTM=${settings.useLongTerm}")
        }
    }

    fun updateUserProfile(profile: UserProfile) {
        _userProfile.value = profile
        memoryAwareAgent?.updateProfile(profile)
        println("👤 Профиль обновлен: ${profile.name}")
    }

    fun updateProjectConstraints(constraints: ProjectConstraints) {
        _projectConstraints.value = constraints
        memoryAwareAgent?.updateConstraints(constraints)
        println("🔧 Ограничения обновлены")
    }

    fun resetWorkingMemory() {
        memoryAwareAgent?.clearWorkingMemory()
        println("💼 Рабочая память сброшена")
    }

    fun setChatMemoryService(service: ChatMemoryAgent) {
        chatMemoryService = service
    }

    fun initDemoManager(onMessageAdded: (ChatMessageUI) -> Unit) {
        chatMemoryService?.markAsDemoMode()

        demoManager = DemoManager(
            chatSession = chatSession,
            onMessageAdded = { message ->
                onMessageAdded(message)
            },
            onDemoStarted = {
                isDemoRunning.value = true
                isGenerating.value = true
                isTyping.value = true
                _tokenStatsFlow.value = TokenStats()
                _tokenHistoryFlow.value = emptyList()
                _contextWarningFlow.value = "✅ Демонстрация запущена..."
            },
            onDemoFinished = {
                isDemoRunning.value = false
                isGenerating.value = false
                isTyping.value = false
                chatMemoryService?.createNewChat()
                updateTokenStats()
            },
            onTypingStateChanged = { typing ->
                isTyping.value = typing
            },
            onStatsUpdated = { stats ->
                if (isDemoRunning.value) {
                    _tokenStatsFlow.value = stats
                }
            },
            onTokenHistoryUpdated = { history ->
                if (isDemoRunning.value) {
                    _tokenHistoryFlow.value = history
                }
            },
            onContextWarningUpdated = { warning ->
                if (isDemoRunning.value) {
                    _contextWarningFlow.value = warning
                }
            }
        )
    }

    fun startTokenDemo() {
        demoManager?.startTokenDemo()
    }

    fun startCompressionDemo() {
        demoManager?.startCompressionDemo()
    }

    fun startStrategyDemo() {
        demoManager?.startStrategyDemo()
    }

    fun startMemoryDemo() {
        demoManager?.startMemoryDemo()
    }

    fun updateDraft(message: String, cursorPos: Int = cursorPosition.value) {
        if (isDemoRunning.value) return
        draftMessage.value = message
        cursorPosition.value = cursorPos
    }

    fun toggleCompression(enabled: Boolean) {
        if (isDemoRunning.value) return
        _compressionEnabled = enabled
        compressionEnabled.value = enabled
        recreateChatSession()
        println("📊 Компрессия ${if (enabled) "включена" else "выключена"}, сессия пересоздана")
    }

    fun updateCompressionParams(keepLast: Int, sumEvery: Int) {
        if (isDemoRunning.value) return
        _keepLastMessages = keepLast
        _summarizeEvery = sumEvery
        keepLastMessages.value = keepLast
        summarizeEvery.value = sumEvery
        recreateChatSession()
        println("📊 Параметры компрессии обновлены: keepLast=$keepLast, summarizeEvery=$sumEvery")
    }

    fun sendMessage(userMessage: String, addToHistory: Boolean = true) {
        if (isGenerating.value || isDemoRunning.value) return

        if (addToHistory) {
            messages.add(
                ChatMessageUI(
                    id = UUID.randomUUID().toString(),
                    role = "user",
                    content = userMessage
                )
            )
        }

        currentGenerationJob = viewModelScope.launch {
            isGenerating.value = true
            isTyping.value = true

            try {
                val response = chatSession.ask(userMessage, isRegeneration = !addToHistory)

                val metadata = buildString {
                    val size = chatSession.getHistorySize()
                    append("Сообщений в истории: $size")
                    if (chatSession.isCompressionEnabled()) {
                        append(" • Компрессия: вкл")
                        compressionStats.value = chatSession.getCompressionStats()
                    }
                }

                messages.add(
                    ChatMessageUI(
                        id = UUID.randomUUID().toString(),
                        role = "assistant",
                        content = response.content,
                        metadata = metadata,
                        promptTokens = response.promptTokens,
                        completionTokens = response.completionTokens,
                        totalTokens = response.totalTokens,
                        responseTimeMs = response.responseTimeMs,
                        isDemoMessage = false
                    )
                )

                saveCurrentChatIfNeeded()
                updateTokenStats()

            } catch (e: Exception) {
                messages.add(
                    ChatMessageUI(
                        id = UUID.randomUUID().toString(),
                        role = "assistant",
                        content = "Ошибка: ${e.message}",
                        metadata = "Произошла ошибка"
                    )
                )
            } finally {
                isTyping.value = false
                isGenerating.value = false
            }
        }
    }

    fun regenerateMessage(assistantMessageId: String) {
        if (isGenerating.value || isDemoRunning.value) return

        val assistantIndex = messages.indexOfFirst { it.id == assistantMessageId }
        if (assistantIndex == -1) return

        var userMessage: ChatMessageUI? = null
        for (i in assistantIndex - 1 downTo 0) {
            if (messages[i].role == "user") {
                userMessage = messages[i]
                break
            }
        }

        if (userMessage == null) return

        val userIndex = messages.indexOf(userMessage)
        while (messages.size > userIndex + 1) {
            messages.removeAt(messages.size - 1)
        }

        syncHistoryWithMessages()

        sendMessage(userMessage.content, addToHistory = false)
    }

    private fun syncHistoryWithMessages() {
        val uiMessages = messages.map { it.role to it.content }
        chatSession.rebuildHistoryFromUiMessages(uiMessages)
    }

    fun editUserMessage(messageId: String, newContent: String) {
        if (isDemoRunning.value) return

        val messageIndex = messages.indexOfFirst { it.id == messageId }
        if (messageIndex == -1 || messages[messageIndex].role != "user") return

        while (messages.size > messageIndex + 1) {
            messages.removeAt(messages.size - 1)
        }

        messages[messageIndex] = messages[messageIndex].copy(content = newContent)

        syncHistoryWithMessages()

        sendMessage(newContent, addToHistory = false)
    }

    fun stopGeneration() {
        if (isDemoRunning.value) return
        currentGenerationJob?.cancel()
        isTyping.value = false
        isGenerating.value = false
    }

    fun clearHistory() {
        if (isDemoRunning.value) return

        val demoMessages = messages.filter { it.isDemoMessage }
        messages.clear()
        messages.addAll(demoMessages)

        chatSession.clearHistory()
        updateTokenStats()

        chatMemoryService?.saveCurrentChat(emptyList(), currentModel.value)
    }

    private fun saveCurrentChatIfNeeded() {
        if (isDemoRunning.value) {
            println("📝 Демо-режим: пропускаем сохранение")
            return
        }

        val nonDemoMessages = messages.filter { !it.isDemoMessage }
        if (nonDemoMessages.isEmpty()) {
            println("📝 Нет обычных сообщений для сохранения")
            return
        }

        chatMemoryService?.saveCurrentChatDebounced(nonDemoMessages, currentModel.value)
    }

    fun clearTokenStats() {
        if (isDemoRunning.value) return
        chatSession.clearTokenStats()
        updateTokenStats()
    }

    fun changeModel(modelId: String) {
        if (isDemoRunning.value) return
        chatSession.changeModel(modelId)
        currentModel.value = modelId
        memoryAwareAgent?.changeModel(modelId)
        updateTokenStats()
    }

    fun setControlEnabled(enabled: Boolean) {
        if (isDemoRunning.value) return
        val newControl = responseControl.value.copy(enabled = enabled)
        chatSession.setResponseControl(newControl)
        responseControl.value = newControl
        controlEnabled.value = enabled
    }

    fun setFormatDescription(format: String) {
        if (isDemoRunning.value) return
        val newControl = responseControl.value.copy(
            formatDescription = format.ifBlank { null },
            enabled = true
        )
        chatSession.setResponseControl(newControl)
        responseControl.value = newControl
        controlEnabled.value = true
    }

    fun setMaxTokens(tokens: Int?) {
        if (isDemoRunning.value) return
        val newControl = responseControl.value.copy(
            maxTokens = tokens,
            enabled = true
        )
        chatSession.setResponseControl(newControl)
        responseControl.value = newControl
        controlEnabled.value = true
    }

    fun setStopSequences(stops: List<String>?) {
        if (isDemoRunning.value) return
        val newControl = responseControl.value.copy(
            stopSequences = stops,
            enabled = true
        )
        chatSession.setResponseControl(newControl)
        responseControl.value = newControl
        controlEnabled.value = true
    }

    fun setTemperature(temp: Double?) {
        if (isDemoRunning.value) return
        val newControl = responseControl.value.copy(
            temperature = temp,
            enabled = true
        )
        chatSession.setResponseControl(newControl)
        responseControl.value = newControl
        controlEnabled.value = true
    }

    fun loadPreset(presetNumber: Int) {
        if (isDemoRunning.value) return
        val preset = PresetManager.getPreset(presetNumber)
        if (preset != null) {
            chatSession.setResponseControl(preset)
            responseControl.value = preset
            controlEnabled.value = preset.enabled
        }
    }

    fun resetToDefault() {
        if (isDemoRunning.value) return
        val defaultControl = PresetManager.getDefaultControl()
        chatSession.setResponseControl(defaultControl)
        responseControl.value = defaultControl
        controlEnabled.value = defaultControl.enabled
    }

    fun getChatSession(): ChatSession = chatSession

    private fun updateTokenStats() {
        val newStats = chatSession.getTokenStats()
        val newHistory = chatSession.getTokenHistory()
        val newWarning = chatSession.getContextWarning()

        _tokenStatsFlow.value = newStats
        _tokenHistoryFlow.value = newHistory
        _contextWarningFlow.value = newWarning
        compressionStats.value = chatSession.getCompressionStats()
    }

    fun refreshTokenStats() {
        if (isDemoRunning.value) {
            return
        }
        updateTokenStats()
    }

    fun addDemoMessage(message: ChatMessageUI) {
        messages.add(message)
        if (!isDemoRunning.value) {
            updateTokenStats()
        }
    }

    fun refreshApiKeys() {
        chatSession.refreshApiKeys()
        println("✅ API ключи обновлены в чат-сессии")
    }

    fun forceRotateToNextKey() {
        ApiConfig.rotateToNextKey()
        chatSession.refreshApiKeys()
    }

    private fun initMemoryAgent(apiKey: String, model: String, systemPrompt: String) {
        memoryAwareAgent = MemoryAwareAgent(
            apiKey = apiKey,
            model = model,
            systemPrompt = systemPrompt
        )

        _userProfile.value = memoryAwareAgent!!.getUserProfile()
        _projectConstraints.value = memoryAwareAgent!!.getProjectConstraints()

        println("✅ MemoryAwareAgent инициализирован")
        println("   Профиль: ${_userProfile.value.name.ifEmpty { "не настроен" }}")
        println("   Ограничения: ${if (_projectConstraints.value.techStack.isNotEmpty()) "настроены" else "не настроены"}")
    }
}
