package com.llmapp.ui

import com.llmapp.chat.ChatSession
import com.llmapp.demo.manager.BaseDemoRunner
import com.llmapp.demo.manager.ProjectDemoRunner
import com.llmapp.model.TokenStats
import com.llmapp.demo.manager.PRReviewAgentDemoRunner
import com.llmapp.pr_review.PRReviewDemoRunner
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class DemoType {
    object ProjectDemo : DemoType()
    object PRReviewDemo : DemoType()
    object PRReviewAgentDemo : DemoType()

    val displayName: String
        get() = when (this) {
            ProjectDemo -> "Ассистент разработчика"
            PRReviewDemo -> "AI Code Review"
            PRReviewAgentDemo -> "Ассистент ревью PR"
        }
}

class DemoManager(
    private val chatSession: ChatSession,
    private val onMessageAdded: (ChatMessageUI) -> Unit,
    private val onDemoStarted: () -> Unit,
    private val onDemoFinished: () -> Unit,
    private val onTypingStateChanged: (Boolean) -> Unit,
    @Suppress("unused")
    private val onStatsUpdated: ((TokenStats) -> Unit)? = null,
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _currentDemo = MutableStateFlow<DemoType?>(null)
    val currentDemo: StateFlow<DemoType?> = _currentDemo.asStateFlow()

    private val _progressMessage = MutableStateFlow<String?>(null)
    val progressMessage: StateFlow<String?> = _progressMessage.asStateFlow()

    private var currentDemoJob: Job? = null

    @Volatile
    private var isCancelled = false

    private fun runDemo(
        demoType: DemoType,
        runnerBuilder: () -> BaseDemoRunner
    ) {
        if (_isRunning.value) {
            println("⚠️ Демонстрация уже запущена: ${_currentDemo.value?.displayName}")
            return
        }

        currentDemoJob?.cancel()
        currentDemoJob = null
        isCancelled = false

        _isRunning.value = true
        _currentDemo.value = demoType
        _progressMessage.value = "Запуск ${demoType.displayName}..."
        onDemoStarted()

        currentDemoJob = scope.launch {
            try {
                chatSession.clearHistory()
                val runner = runnerBuilder()

                if (isCancelled) return@launch

                _progressMessage.value = "Выполняется ${demoType.displayName}..."

                runCatchingWithCancel { runner.run() }

                if (!isCancelled) {
                    _progressMessage.value = "${demoType.displayName} завершена ✅"
                } else {
                    _progressMessage.value = "Демонстрация отменена"
                }
            } catch (e: Exception) {
                if (!isCancelled) {
                    _progressMessage.value = "❌ Ошибка: ${e.message}"
                    onMessageAdded(
                        ChatMessageUI(
                            id = java.util.UUID.randomUUID().toString(),
                            role = "assistant",
                            content = "❌ Ошибка в демонстрации ${demoType.displayName}: ${e.message}",
                            metadata = "Ошибка",
                            isDemoMessage = true
                        )
                    )
                }
            } finally {
                if (!isCancelled) {
                    _isRunning.value = false
                    _currentDemo.value = null
                    _progressMessage.value = null
                    onDemoFinished()
                }
                currentDemoJob = null
            }
        }
    }

    private suspend fun runCatchingWithCancel(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            println("⚠️ Демонстрация прервана: ${e.message}")
            throw e
        } catch (e: Exception) {
            if (!isCancelled) throw e
            println("⚠️ Демонстрация прервана во время ошибки")
        }
    }

    fun startProjectDemo() {
        runDemo(DemoType.ProjectDemo) {
            ProjectDemoRunner(
                onMessageAdded = onMessageAdded,
                onTypingStateChanged = onTypingStateChanged,
                chatSession = chatSession,
            )
        }
    }

    fun startPRReviewDemo(prNumber: Int = 2) {
        runDemo(DemoType.PRReviewDemo) {
            PRReviewDemoRunner(
                onMessageAdded = onMessageAdded,
                onTypingStateChanged = onTypingStateChanged,
                prNumber = prNumber,
            )
        }
    }

    fun startPRReviewAgentDemo(prNumber: Int = 0) {
        runDemo(DemoType.PRReviewAgentDemo) {
            PRReviewAgentDemoRunner(
                onMessageAdded = onMessageAdded,
                onTypingStateChanged = onTypingStateChanged,
                prNumber = prNumber,
            )
        }
    }

    fun cancelDemo() {
        if (_isRunning.value) {
            isCancelled = true
            currentDemoJob?.cancel()
            currentDemoJob = null
            _isRunning.value = false
            _currentDemo.value = null
            _progressMessage.value = "Демонстрация отменена"
            onTypingStateChanged(false)
            onDemoFinished()
            onMessageAdded(
                ChatMessageUI(
                    id = java.util.UUID.randomUUID().toString(),
                    role = "assistant",
                    content = "🛑 Демонстрация отменена пользователем",
                    metadata = "Отмена",
                    isDemoMessage = true
                )
            )
        }
    }
}
