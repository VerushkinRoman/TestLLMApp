package com.llmapp.ui.viewmodel

import com.llmapp.agent.StatefulMemoryAgent
import com.llmapp.chat.ChatSession
import com.llmapp.domain.usercase.TaskUseCase
import com.llmapp.model.TokenStats
import com.llmapp.ui.DemoManager
import com.llmapp.ui.DemoType
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.launch

class DemoHandler(
    private val chatSession: ChatSession,
    private val statefulAgent: StatefulMemoryAgent,
    private val taskUseCase: TaskUseCase,
    private val viewModelScope: CoroutineScope,
    private val chatMemoryService: com.llmapp.agent.ChatMemoryAgent?
) {
    private var demoManager: DemoManager? = null
    private val _demoManagerCurrentDemo = MutableStateFlow<DemoType?>(null)
    val demoManagerCurrentDemo: StateFlow<DemoType?> = _demoManagerCurrentDemo.asStateFlow()
    private val _demoManagerProgress = MutableStateFlow<String?>(null)
    val demoManagerProgress: StateFlow<String?> = _demoManagerProgress.asStateFlow()

    fun initDemoManager(
        onMessageAdded: (ChatMessageUI) -> Unit,
        updateState: (ChatViewState.() -> ChatViewState) -> Unit,
        updateTokenStats: () -> Unit
    ) {
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
                _demoManagerCurrentDemo.value = demoManager?.currentDemo?.value
                _demoManagerProgress.value = demoManager?.progressMessage?.value
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
                _demoManagerCurrentDemo.value = null
                _demoManagerProgress.value = null
            },
            onTypingStateChanged = { typing -> updateState { copy(isTyping = typing) } },
            onStatsUpdated = {
                // Only update during demo
            },
            onTokenHistoryUpdated = {
                // Only update during demo
            },
            onContextWarningUpdated = {
                // Only update during demo
            },
            onTaskStateUpdated = {
                taskUseCase.taskState.value?.let { state ->
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
                _demoManagerCurrentDemo.value = demo
            }
        }
        viewModelScope.launch {
            demoManager?.progressMessage?.collect { progress ->
                _demoManagerProgress.value = progress
            }
        }
    }

    fun startTokenDemo() = demoManager?.startTokenDemo()
    fun startCompressionDemo() = demoManager?.startCompressionDemo()
    fun startStrategyDemo() = demoManager?.startStrategyDemo()
    fun startMemoryDemo() = demoManager?.startMemoryDemo()
    fun startPersonalizationDemo() = demoManager?.startPersonalizationDemo()
    fun startStatefulDemo() = demoManager?.startStatefulDemo()
    fun startInvariantDemo() = demoManager?.startInvariantDemo()
    fun startTransitionDemo() = demoManager?.startTransitionDemo()
    fun startRagDemo(query: String) = demoManager?.startRagDemo(query)
    fun startRagComparisonDemo() = demoManager?.startRagComparisonDemo()
    fun cancelDemo() = demoManager?.cancelDemo()

}
