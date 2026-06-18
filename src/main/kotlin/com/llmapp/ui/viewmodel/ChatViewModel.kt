package com.llmapp.ui.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llmapp.agent.ChatMemoryAgent
import com.llmapp.agent.InvariantAwareAgent
import com.llmapp.agent.MemoryAwareAgent
import com.llmapp.agent.StatefulMemoryAgent
import com.llmapp.agent.TokenSnapshot
import com.llmapp.api.ApiConfig
import com.llmapp.chat.ChatSession
import com.llmapp.controller.PresetManager
import com.llmapp.invariants.InvariantManager
import com.llmapp.invariants.InvariantPresets
import com.llmapp.invariants.InvariantSet
import com.llmapp.memory.LongTermMemoryManager
import com.llmapp.memory.ProjectConstraints
import com.llmapp.memory.ResponseStyle
import com.llmapp.memory.UserProfile
import com.llmapp.model.TokenStats
import com.llmapp.model.freeModels
import com.llmapp.state.TaskPhase
import com.llmapp.ui.DemoManager
import com.llmapp.ui.components.MemorySettings
import com.llmapp.ui.components.NamedProfile
import com.llmapp.ui.components.ProfileManager
import com.llmapp.ui.models.ChatMessageUI
import com.llmapp.ui.models.TaskStateUI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

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

    // StateFlow для профиля
    private lateinit var profileManager: ProfileManager
    private val _activeProfile = MutableStateFlow(UserProfile())
    val activeProfile: StateFlow<UserProfile> = _activeProfile.asStateFlow()

    private val _showProfileManager = MutableStateFlow(false)
    val showProfileManager: StateFlow<Boolean> = _showProfileManager.asStateFlow()

    private val _showWelcomeDialog = MutableStateFlow(false)
    val showWelcomeDialog: StateFlow<Boolean> = _showWelcomeDialog.asStateFlow()

    private val _allProfiles = MutableStateFlow<List<UserProfile>>(emptyList())
    val allProfiles: StateFlow<List<UserProfile>> = _allProfiles.asStateFlow()

    private var isFirstLaunch = true

    // StatefulMemoryAgent
    private lateinit var statefulAgent: StatefulMemoryAgent
    private val _taskState = MutableStateFlow<TaskStateUI?>(null)
    val taskState: StateFlow<TaskStateUI?> = _taskState.asStateFlow()

    private val _showSnapshotDialog = MutableStateFlow(false)
    val showSnapshotDialog: StateFlow<Boolean> = _showSnapshotDialog.asStateFlow()

    private val _snapshots = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val snapshots: StateFlow<List<Pair<String, String>>> = _snapshots.asStateFlow()

    private val _showCreateTaskDialog = MutableStateFlow(false)
    val showCreateTaskDialog: StateFlow<Boolean> = _showCreateTaskDialog.asStateFlow()

    // Инварианты
    private val _activeInvariantSetName = MutableStateFlow<String?>(null)
    val activeInvariantSetName: StateFlow<String?> = _activeInvariantSetName.asStateFlow()

    private val invariantManager = InvariantManager()
    private var invariantAwareAgent: InvariantAwareAgent? = null

    // Поздняя инициализация для остальных полей
    private lateinit var chatSession: ChatSession
    private var memoryAwareAgent: MemoryAwareAgent? = null
    private var chatMemoryService: ChatMemoryAgent? = null
    private var demoManager: DemoManager? = null

    init {
        initChatSession()
        initProfileManager()
        checkFirstLaunch()
        initStatefulAgent()
        loadSelectedInvariantSet()
        viewModelScope.launch {
            kotlinx.coroutines.delay(100.milliseconds)
            loadSelectedInvariantSet()
            if (_activeInvariantSetName.value == null) {
                val set = InvariantPresets.getBaseInvariants()
                invariantManager.saveInvariantSet(set)
                selectInvariantSet(set)
            }
        }
    }

    // ============================================================
    // МЕТОДЫ ДЛЯ РАБОТЫ С ИНВАРИАНТАМИ
    // ============================================================

    fun selectInvariantSet(set: InvariantSet) {
        _activeInvariantSetName.value = set.name
        saveSelectedInvariantSet(set.name)
        recreateChatSessionWithInvariants(set)
    }

    fun clearActiveInvariantSet() {
        _activeInvariantSetName.value = null
        val prefs = java.util.prefs.Preferences.userRoot().node("com.llmapp.invariants")
        prefs.remove("active_set")
        println("🔒 Активный набор инвариантов сброшен")
    }

    private fun recreateChatSessionWithInvariants(set: InvariantSet) {
        // Создаем агента с инвариантами
        invariantAwareAgent = InvariantAwareAgent(
            apiKey = apiKey,
            model = currentModel.value,
            systemPrompt = "Ты полезный ассистент. Отвечай на русском языке.",
            invariantSet = set
        )

        println("🔒 Агент обновлен с инвариантами: ${set.name}")
        println("   • Инвариантов: ${set.invariants.size}")

        // Обновляем статистику
        updateTokenStats()
    }

    private fun saveSelectedInvariantSet(name: String) {
        val prefs = java.util.prefs.Preferences.userRoot().node("com.llmapp.invariants")
        prefs.put("active_set", name)
    }

    private fun loadSelectedInvariantSet() {
        val prefs = java.util.prefs.Preferences.userRoot().node("com.llmapp.invariants")
        val name = prefs.get("active_set", null)
        if (name != null) {
            val set = invariantManager.loadInvariantSet(name)
            if (set != null) {
                _activeInvariantSetName.value = name
                recreateChatSessionWithInvariants(set)
            }
        }
    }

    fun refreshInvariantSets() {
        val allSets = invariantManager.getAllInvariantSets()
        println("📁 Доступные наборы инвариантов: ${allSets.map { it.name }}")
    }

    fun createInvariantSetFromPreset(presetName: String): Boolean {
        // Используем saveInvariantSet напрямую
        val set = when (presetName.lowercase()) {
            "android" -> InvariantPresets.getAndroidKMPInvariants()
            "web" -> InvariantPresets.getWebInvariants()
            else -> InvariantPresets.getBaseInvariants()
        }

        // Сохраняем набор
        val success = invariantManager.saveInvariantSet(set)
        if (success) {
            refreshInvariantSets()
            selectInvariantSet(set)
            println("✅ Набор инвариантов '$presetName' создан")
        } else {
            println("❌ Не удалось создать набор инвариантов '$presetName'")
        }
        return success
    }

    // ============================================================
    // ОСТАЛЬНЫЕ МЕТОДЫ
    // ============================================================

    fun toggleCreateTaskDialog() {
        _showCreateTaskDialog.value = !_showCreateTaskDialog.value
    }

    fun createNewTask(name: String, description: String = "") {
        if (!::statefulAgent.isInitialized) {
            println("⚠️ StatefulAgent не инициализирован")
            return
        }
        statefulAgent.createTask(name, description)
        updateTaskStateUI()
        _showCreateTaskDialog.value = false

        messages.add(
            ChatMessageUI(
                id = UUID.randomUUID().toString(),
                role = "assistant",
                content = """
                ✅ Задача '$name' создана!
                
                📋 Текущая фаза: Сбор требований
                🎯 Ожидается: Опишите, что нужно сделать
                
                💡 Используйте панель управления для навигации по этапам.
                📝 Укажите стек технологий и ограничения в описании задачи.
            """.trimIndent(),
                isDemoMessage = false
            )
        )
    }

    private fun initChatSession() {
        chatSession = ChatSession(
            apiKey = apiKey,
            compressionEnabled = _compressionEnabled,
            keepLastMessages = _keepLastMessages,
            summarizeEvery = _summarizeEvery
        )

        currentModel.value = chatSession.getCurrentModel()
        responseControl.value = chatSession.getResponseControl()
        controlEnabled.value = responseControl.value.enabled

        initMemoryAgent(
            apiKey,
            chatSession.getCurrentModel()
        )
    }

    private fun initProfileManager() {
        val storageDir = File(System.getProperty("user.home"), ".llm_chat_app")
        val longTermManager = LongTermMemoryManager(storageDir)
        profileManager = ProfileManager(storageDir, longTermManager)
        loadAllProfiles()
    }

    fun startStatefulDemo() {
        demoManager?.startStatefulDemo()
    }

    private fun loadAllProfiles() {
        _allProfiles.value = profileManager.getAllProfiles()
    }

    private fun loadActiveProfile() {
        val profile = profileManager.getActiveProfile()
        _activeProfile.value = profile
        memoryAwareAgent?.updateProfile(profile)
        println("👤 Загружен активный профиль: ${profile.name}")
    }

    fun loadPresetProfile(preset: NamedProfile) {
        val profile = profileManager.createProfileFromPreset(preset)
        _activeProfile.value = profile
        memoryAwareAgent?.updateProfile(profile)
        profileManager.setActiveProfile(profile)
        loadAllProfiles()
        println("📋 Загружен пресет: ${preset.name}")
    }

    fun updateExistingProfile(profile: UserProfile) {
        if (profile.name.isEmpty()) return

        if (profileManager.updateProfile(profile)) {
            _activeProfile.value = profile
            memoryAwareAgent?.updateProfile(profile)
            profileManager.setActiveProfile(profile)
            loadAllProfiles()
            println("🔄 Обновлен профиль: ${profile.name}")
        } else {
            println("⚠️ Не удалось обновить профиль: ${profile.name}")
        }
    }

    fun switchToProfile(profile: UserProfile) {
        _activeProfile.value = profile
        memoryAwareAgent?.updateProfile(profile)
        profileManager.setActiveProfile(profile)
        loadAllProfiles()
        println("🔄 Переключен на профиль: ${profile.name}")
    }

    fun deleteProfile(name: String) {
        if (name == _activeProfile.value.name) {
            val emptyProfile = UserProfile()
            _activeProfile.value = emptyProfile
            memoryAwareAgent?.updateProfile(emptyProfile)
            profileManager.setActiveProfile(emptyProfile)
        }
        profileManager.deleteProfile(name)
        loadAllProfiles()
        println("🗑️ Удален профиль: $name")
    }

    fun toggleProfileManager() {
        _showProfileManager.value = !_showProfileManager.value
        if (_showProfileManager.value) {
            loadAllProfiles()
        }
    }

    fun dismissProfileManager() {
        _showProfileManager.value = false
    }

    fun dismissWelcomeDialog() {
        _showWelcomeDialog.value = false
        profileManager.setFirstLaunchCompleted()
        val defaultProfile = UserProfile(
            name = "Пользователь",
            experience = "Разработчик",
            preferredStyle = ResponseStyle.BALANCED
        )
        _activeProfile.value = defaultProfile
        memoryAwareAgent?.updateProfile(defaultProfile)
        profileManager.setActiveProfile(defaultProfile)
        loadAllProfiles()
    }

    private fun checkFirstLaunch() {
        if (profileManager.isFirstLaunch()) {
            _showWelcomeDialog.value = true
            isFirstLaunch = true
        } else {
            loadActiveProfile()
            isFirstLaunch = false
        }
    }

    fun startInvariantDemo() {
        demoManager?.startInvariantDemo()
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
                updateTaskStateUI()
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
            },
            onTaskStateUpdated = {
                updateTaskStateUI()
            },
            statefulAgent = if (::statefulAgent.isInitialized) statefulAgent else initStatefulAgent(),
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

        // ============================================================
        // КОМАНДЫ STATEFUL AGENT
        // ============================================================

        // /task <название> - создать задачу
        if (userMessage.startsWith("/task ")) {
            val taskName = userMessage.removePrefix("/task ").trim()
            if (taskName.isNotEmpty()) {
                createTask(taskName)
                messages.add(
                    ChatMessageUI(
                        id = UUID.randomUUID().toString(),
                        role = "assistant",
                        content = "✅ Задача '$taskName' создана!\n\n" +
                                "📋 Текущая фаза: Сбор требований\n" +
                                "🎯 Ожидается: Опишите, что нужно сделать\n\n" +
                                "Используйте панель управления для навигации по этапам.\n" +
                                "Доступные команды:\n" +
                                "  • /status - показать статус задачи\n" +
                                "  • /snapshots - открыть диалог снимков\n" +
                                "  • /pause - поставить задачу на паузу\n" +
                                "  • /resume - возобновить задачу",
                        isDemoMessage = false
                    )
                )
            } else {
                messages.add(
                    ChatMessageUI(
                        id = UUID.randomUUID().toString(),
                        role = "assistant",
                        content = "⚠️ Укажите название задачи: /task <название>",
                        isDemoMessage = false
                    )
                )
            }
            return
        }

        // /invariant-preset - создать набор инвариантов из пресета
        if (userMessage.startsWith("/invariant-preset ")) {
            val presetName = userMessage.removePrefix("/invariant-preset ").trim()
            if (presetName.isNotEmpty()) {
                val success = createInvariantSetFromPreset(presetName)
                messages.add(
                    ChatMessageUI(
                        id = UUID.randomUUID().toString(),
                        role = "assistant",
                        content = if (success) {
                            "✅ Набор инвариантов '$presetName' создан и активирован!"
                        } else {
                            "❌ Не удалось создать набор инвариантов '$presetName'"
                        },
                        isDemoMessage = false
                    )
                )
            } else {
                messages.add(
                    ChatMessageUI(
                        id = UUID.randomUUID().toString(),
                        role = "assistant",
                        content = "⚠️ Укажите название пресета: /invariant-preset <android|web|base>",
                        isDemoMessage = false
                    )
                )
            }
            return
        }

        // /status - показать статус
        if (userMessage == "/status") {
            if (::statefulAgent.isInitialized) {
                val status = statefulAgent.getFullStatus()
                messages.add(
                    ChatMessageUI(
                        id = UUID.randomUUID().toString(),
                        role = "assistant",
                        content = status,
                        isDemoMessage = false
                    )
                )
            } else {
                messages.add(
                    ChatMessageUI(
                        id = UUID.randomUUID().toString(),
                        role = "assistant",
                        content = "⚠️ StatefulAgent не инициализирован. Создайте задачу командой /task <название>",
                        isDemoMessage = false
                    )
                )
            }
            return
        }

        // /snapshots - открыть диалог снимков
        if (userMessage == "/snapshots") {
            if (::statefulAgent.isInitialized) {
                toggleSnapshotDialog()
                messages.add(
                    ChatMessageUI(
                        id = UUID.randomUUID().toString(),
                        role = "assistant",
                        content = "📸 Открыт диалог управления снимками",
                        isDemoMessage = false
                    )
                )
            } else {
                messages.add(
                    ChatMessageUI(
                        id = UUID.randomUUID().toString(),
                        role = "assistant",
                        content = "⚠️ StatefulAgent не инициализирован. Создайте задачу командой /task <название>",
                        isDemoMessage = false
                    )
                )
            }
            return
        }

        // /pause - пауза
        if (userMessage == "/pause") {
            if (::statefulAgent.isInitialized) {
                pauseTask("Пауза по команде /pause")
            } else {
                messages.add(
                    ChatMessageUI(
                        id = UUID.randomUUID().toString(),
                        role = "assistant",
                        content = "⚠️ StatefulAgent не инициализирован",
                        isDemoMessage = false
                    )
                )
            }
            return
        }

        // /resume - возобновить
        if (userMessage == "/resume") {
            if (::statefulAgent.isInitialized) {
                resumeTask()
            } else {
                messages.add(
                    ChatMessageUI(
                        id = UUID.randomUUID().toString(),
                        role = "assistant",
                        content = "⚠️ StatefulAgent не инициализирован",
                        isDemoMessage = false
                    )
                )
            }
            return
        }

        // /help - справка по командам
        if (userMessage == "/help") {
            messages.add(
                ChatMessageUI(
                    id = UUID.randomUUID().toString(),
                    role = "assistant",
                    content = """
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
              
            ℹ️ Справка:
              /help - показать эту справку
            
            💡 Также используйте панель управления в интерфейсе чата!
        """.trimIndent(),
                    isDemoMessage = false
                )
            )
            return
        }

        // /clear-task - очистить задачу
        if (userMessage == "/clear-task") {
            clearTask()
            return
        }

        // /tokens - статистика токенов
        if (userMessage == "/tokens") {
            val stats = chatSession.getTokenStats()
            messages.add(
                ChatMessageUI(
                    id = UUID.randomUUID().toString(),
                    role = "assistant",
                    content = """
                    📊 СТАТИСТИКА ТОКЕНОВ:
                    
                    • Запросов: ${stats.requestCount}
                    • Всего токенов: ${stats.totalTokens}
                    • Prompt токенов: ${stats.totalPromptTokens}
                    • Completion токенов: ${stats.totalCompletionTokens}
                    • Стоимость: ${stats.getFormattedCost()}
                    
                    ${chatSession.getContextWarning()}
                """.trimIndent(),
                    isDemoMessage = false
                )
            )
            return
        }

        // ============================================================
        // ОБЫЧНЫЙ ЧАТ
        // ============================================================

        if (_activeProfile.value.name.isEmpty()) {
            loadActiveProfile()
        }

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

                // Автоматический переход на основе ответа (если StatefulAgent инициализирован)
                if (::statefulAgent.isInitialized) {
                    val state = statefulAgent.getCurrentTaskState()
                    if (state.taskName.isNotEmpty()) {
                        val lowerContent = response.content.lowercase()
                        when {
                            lowerContent.contains("план утвержден") || lowerContent.contains("утверждаю план") -> {
                                transitionTo(TaskPhase.EXECUTION)
                            }

                            lowerContent.contains("код написан") || lowerContent.contains("готово") -> {
                                transitionTo(TaskPhase.VALIDATION)
                            }

                            lowerContent.contains("проверка пройдена") || lowerContent.contains("все работает") -> {
                                transitionTo(TaskPhase.DONE)
                            }
                        }
                    }
                }

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

    fun initStatefulAgent(apiKey: String = ApiConfig.getApiKey()): StatefulMemoryAgent {
        if (!::statefulAgent.isInitialized) {
            statefulAgent = StatefulMemoryAgent(apiKey = apiKey)
            println("🧠 StatefulMemoryAgent инициализирован")
        }
        updateTaskStateUI()
        return statefulAgent
    }

    fun createTask(taskName: String, description: String = "") {
        if (!::statefulAgent.isInitialized) {
            println("⚠️ StatefulAgent не инициализирован")
            return
        }
        statefulAgent.createTask(taskName, description)
        updateTaskStateUI()
    }

    fun transitionTo(phase: TaskPhase) {
        if (!::statefulAgent.isInitialized) {
            println("⚠️ StatefulAgent не инициализирован")
            return
        }
        val result = statefulAgent.transitionTo(phase)
        if (result.success) {
            updateTaskStateUI()
            messages.add(
                ChatMessageUI(
                    id = UUID.randomUUID().toString(),
                    role = "assistant",
                    content = "🔄 ${result.message}",
                    isDemoMessage = false
                )
            )
        }
    }

    fun pauseTask(reason: String = "Пауза по запросу пользователя") {
        if (!::statefulAgent.isInitialized) {
            println("⚠️ StatefulAgent не инициализирован")
            return
        }
        val result = statefulAgent.pause(reason)
        if (result.success) {
            updateTaskStateUI()
            messages.add(
                ChatMessageUI(
                    id = UUID.randomUUID().toString(),
                    role = "assistant",
                    content = "⏸️ ${result.message}",
                    isDemoMessage = false
                )
            )
        }
    }

    fun resumeTask() {
        if (!::statefulAgent.isInitialized) {
            println("⚠️ StatefulAgent не инициализирован")
            return
        }
        val result = statefulAgent.resume()
        if (result.success) {
            updateTaskStateUI()
            messages.add(
                ChatMessageUI(
                    id = UUID.randomUUID().toString(),
                    role = "assistant",
                    content = "▶️ ${result.message}",
                    isDemoMessage = false
                )
            )
        }
    }

    fun blockTask(reason: String = "Блокировка") {
        if (!::statefulAgent.isInitialized) {
            println("⚠️ StatefulAgent не инициализирован")
            return
        }
        val result = statefulAgent.block(reason)
        if (result.success) {
            updateTaskStateUI()
            messages.add(
                ChatMessageUI(
                    id = UUID.randomUUID().toString(),
                    role = "assistant",
                    content = "🚫 ${result.message}",
                    isDemoMessage = false
                )
            )
        }
    }

    fun unblockTask() {
        if (!::statefulAgent.isInitialized) {
            println("⚠️ StatefulAgent не инициализирован")
            return
        }
        val result = statefulAgent.unblock()
        if (result.success) {
            updateTaskStateUI()
            messages.add(
                ChatMessageUI(
                    id = UUID.randomUUID().toString(),
                    role = "assistant",
                    content = "🔓 ${result.message}",
                    isDemoMessage = false
                )
            )
        }
    }

    private fun updateTaskStateUI() {
        if (!::statefulAgent.isInitialized) {
            _taskState.value = null
            return
        }
        val state = statefulAgent.getCurrentTaskState()
        _taskState.value = TaskStateUI(
            phase = state.phase,
            step = state.step,
            expectedAction = state.expectedAction,
            isPaused = statefulAgent.isPaused(),
            isBlocked = state.phase == TaskPhase.BLOCKED,
            progress = statefulAgent.getProgress(),
            taskName = state.taskName,
            elapsedTime = state.getElapsedTime(),
            availableTransitions = statefulAgent.getAvailableTransitions()
        )
    }

// ============================================================
// МЕТОДЫ ДЛЯ РАБОТЫ СО СНИМКАМИ
// ============================================================

    fun toggleSnapshotDialog() {
        _showSnapshotDialog.value = !_showSnapshotDialog.value
        if (_showSnapshotDialog.value) {
            updateSnapshots()
        }
    }

    fun dismissSnapshotDialog() {
        _showSnapshotDialog.value = false
    }

    fun createSnapshot(name: String) {
        if (!::statefulAgent.isInitialized) {
            println("⚠️ StatefulAgent не инициализирован")
            messages.add(
                ChatMessageUI(
                    id = UUID.randomUUID().toString(),
                    role = "assistant",
                    content = "⚠️ StatefulAgent не инициализирован. Сначала создайте задачу командой /task <название>",
                    isDemoMessage = false
                )
            )
            return
        }
        val id = statefulAgent.createSnapshot(name)
        updateSnapshots()
        messages.add(
            ChatMessageUI(
                id = UUID.randomUUID().toString(),
                role = "assistant",
                content = "📸 Снимок '$name' создан (ID: ${id.take(8)})",
                isDemoMessage = false
            )
        )
    }

    fun restoreSnapshot(id: String) {
        if (!::statefulAgent.isInitialized) {
            println("⚠️ StatefulAgent не инициализирован")
            messages.add(
                ChatMessageUI(
                    id = UUID.randomUUID().toString(),
                    role = "assistant",
                    content = "⚠️ StatefulAgent не инициализирован",
                    isDemoMessage = false
                )
            )
            return
        }
        if (statefulAgent.restoreFromSnapshot(id)) {
            updateTaskStateUI()
            updateSnapshots()
            messages.add(
                ChatMessageUI(
                    id = UUID.randomUUID().toString(),
                    role = "assistant",
                    content = "📸 Восстановлен снимок: $id",
                    isDemoMessage = false
                )
            )
        } else {
            messages.add(
                ChatMessageUI(
                    id = UUID.randomUUID().toString(),
                    role = "assistant",
                    content = "❌ Не удалось восстановить снимок: $id",
                    isDemoMessage = false
                )
            )
        }
        _showSnapshotDialog.value = false
    }

    fun getSnapshotDetails(id: String): String {
        if (!::statefulAgent.isInitialized) {
            return "⚠️ StatefulAgent не инициализирован"
        }
        return statefulAgent.getSnapshotDetails(id)
    }

    private fun updateSnapshots() {
        if (!::statefulAgent.isInitialized) {
            _snapshots.value = emptyList()
            return
        }
        _snapshots.value = statefulAgent.getSnapshots()
    }

    fun clearTask() {
        if (!::statefulAgent.isInitialized) {
            println("⚠️ StatefulAgent не инициализирован")
            return
        }
        // Создаем новую пустую задачу
        statefulAgent.createTask("", "")
        updateTaskStateUI()
        _snapshots.value = emptyList()
        messages.add(
            ChatMessageUI(
                id = UUID.randomUUID().toString(),
                role = "assistant",
                content = "🗑️ Состояние задачи очищено",
                isDemoMessage = false
            )
        )
    }

    private fun initMemoryAgent(apiKey: String, model: String) {
        memoryAwareAgent = MemoryAwareAgent(
            apiKey = apiKey,
            model = model,
            systemPrompt = "Ты полезный ассистент. Отвечай на русском языке.",
            persistToDisk = true
        )

        _userProfile.value = memoryAwareAgent!!.getUserProfile()
        _projectConstraints.value = memoryAwareAgent!!.getProjectConstraints()

        println("✅ MemoryAwareAgent инициализирован")
        println("   Профиль: ${_userProfile.value.name.ifEmpty { "не настроен" }}")
        println("   Ограничения: ${if (_projectConstraints.value.techStack.isNotEmpty()) "настроены" else "не настроены"}")
    }
}
