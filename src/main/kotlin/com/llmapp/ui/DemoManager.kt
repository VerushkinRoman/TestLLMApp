package com.llmapp.ui

import com.llmapp.agent.StatefulMemoryAgent
import com.llmapp.agent.TokenSnapshot
import com.llmapp.chat.ChatSession
import com.llmapp.demo.manager.BaseDemoRunner
import com.llmapp.demo.manager.CompressionDemoRunner
import com.llmapp.demo.manager.InvariantDemoRunner
import com.llmapp.demo.manager.MemoryDemoRunner
import com.llmapp.demo.manager.PersonalizationDemoRunner
import com.llmapp.demo.manager.StatefulDemoRunner
import com.llmapp.demo.manager.StrategyDemoRunner
import com.llmapp.demo.manager.TokenDemoRunner
import com.llmapp.demo.manager.TransitionDemoRunner
import com.llmapp.demo.manager.RagDemoRunner
import com.llmapp.model.TokenStats
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class DemoType {
    object TokenComparison : DemoType()
    object CompressionComparison : DemoType()
    object StrategyComparison : DemoType()
    object MemoryComparison : DemoType()
    object Personalization : DemoType()
    object StatefulAgent : DemoType()
    object InvariantDemo : DemoType()
    object TransitionDemo : DemoType()
    object RagPipeline : DemoType()

    val displayName: String
        get() = when (this) {
            TokenComparison -> "Отслеживание токенов"
            CompressionComparison -> "Сжатие контекста"
            StrategyComparison -> "Стратегии контекста"
            MemoryComparison -> "Модель памяти"
            Personalization -> "Персонализация"
            StatefulAgent -> "Stateful Agent"
            InvariantDemo -> "Инварианты"
            TransitionDemo -> "Управление переходами"
            RagPipeline -> "RAG Pipeline"
        }
}

class DemoManager(
    private val chatSession: ChatSession,
    private val onMessageAdded: (ChatMessageUI) -> Unit,
    private val onDemoStarted: () -> Unit,
    private val onDemoFinished: () -> Unit,
    private val onTypingStateChanged: (Boolean) -> Unit,
    private val onStatsUpdated: ((TokenStats) -> Unit)?,
    private val onTokenHistoryUpdated: ((List<TokenSnapshot>) -> Unit)?,
    private val onContextWarningUpdated: ((String) -> Unit)?,
    private val onTaskStateUpdated: (() -> Unit),
    private val statefulAgent: StatefulMemoryAgent
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    // Состояние выполнения демонстрации
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    // Текущий тип демонстрации
    private val _currentDemo = MutableStateFlow<DemoType?>(null)
    val currentDemo: StateFlow<DemoType?> = _currentDemo.asStateFlow()

    // Сообщение о прогрессе
    private val _progressMessage = MutableStateFlow<String?>(null)
    val progressMessage: StateFlow<String?> = _progressMessage.asStateFlow()

    // Job текущей демонстрации для отмены
    private var currentDemoJob: Job? = null

    // Флаг отмены
    @Volatile
    private var isCancelled = false

    // ============================================================
    // ПРИВАТНЫЙ МЕТОД ЗАПУСКА
    // ============================================================

    private fun runDemo(
        demoType: DemoType,
        runnerBuilder: () -> BaseDemoRunner
    ) {
        if (_isRunning.value) {
            println("⚠️ Демонстрация уже запущена: ${_currentDemo.value?.displayName}")
            return
        }

        // Отменяем предыдущий Job если есть
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

                // Проверяем отмену перед запуском
                if (isCancelled) {
                    println("⚠️ Демонстрация отменена до запуска")
                    return@launch
                }

                _progressMessage.value = "Выполняется ${demoType.displayName}..."

                // Запускаем демонстрацию с проверкой отмены
                runCatchingWithCancel {
                    runner.run()
                }

                // Если не была отменена, показываем завершение
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
                // Очищаем состояние только если это не отмена (или отмена уже обработана)
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

    /**
     * Запускает блок с возможностью отмены через проверку isCancelled
     */
    private suspend fun runCatchingWithCancel(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Это ожидаемое исключение при отмене корутины
            println("⚠️ Демонстрация прервана: ${e.message}")
            throw e
        } catch (e: Exception) {
            if (!isCancelled) {
                throw e
            }
            println("⚠️ Демонстрация прервана во время ошибки")
        }
    }

    // ============================================================
    // ПУБЛИЧНЫЕ МЕТОДЫ ЗАПУСКА
    // ============================================================

    fun startTokenDemo() {
        runDemo(DemoType.TokenComparison) {
            TokenDemoRunner(
                onMessageAdded = onMessageAdded,
                onTypingStateChanged = onTypingStateChanged,
                onStatsUpdated = onStatsUpdated,
                onTokenHistoryUpdated = onTokenHistoryUpdated,
                onContextWarningUpdated = onContextWarningUpdated
            )
        }
    }

    fun startCompressionDemo() {
        runDemo(DemoType.CompressionComparison) {
            CompressionDemoRunner(
                onMessageAdded = onMessageAdded,
                onTypingStateChanged = onTypingStateChanged
            )
        }
    }

    fun startStrategyDemo() {
        runDemo(DemoType.StrategyComparison) {
            StrategyDemoRunner(
                onMessageAdded = onMessageAdded,
                onTypingStateChanged = onTypingStateChanged
            )
        }
    }

    fun startMemoryDemo() {
        runDemo(DemoType.MemoryComparison) {
            MemoryDemoRunner(
                onMessageAdded = onMessageAdded,
                onTypingStateChanged = onTypingStateChanged
            )
        }
    }

    fun startPersonalizationDemo() {
        runDemo(DemoType.Personalization) {
            PersonalizationDemoRunner(
                onMessageAdded = onMessageAdded,
                onTypingStateChanged = onTypingStateChanged
            )
        }
    }

    fun startStatefulDemo() {
        runDemo(DemoType.StatefulAgent) {
            StatefulDemoRunner(
                onMessageAdded = onMessageAdded,
                onTypingStateChanged = onTypingStateChanged,
                onTaskStateUpdated = onTaskStateUpdated,
                statefulAgent = statefulAgent
            )
        }
    }

    fun startInvariantDemo() {
        runDemo(DemoType.InvariantDemo) {
            InvariantDemoRunner(
                onMessageAdded = onMessageAdded,
                onTypingStateChanged = onTypingStateChanged
            )
        }
    }

    fun startTransitionDemo() {
        runDemo(DemoType.TransitionDemo) {
            TransitionDemoRunner(
                onMessageAdded = onMessageAdded,
                onTypingStateChanged = onTypingStateChanged,
                statefulAgent = statefulAgent,
                onTaskStateUpdated = onTaskStateUpdated
            )
        }
    }

    fun startRagDemo(query: String) {
        runDemo(DemoType.RagPipeline) {
            RagDemoRunner(
                onMessageAdded = onMessageAdded,
                onTypingStateChanged = onTypingStateChanged,
                query = query
            )
        }
    }

    // ============================================================
    // ОТМЕНА ДЕМОНСТРАЦИИ
    // ============================================================

    fun cancelDemo() {
        if (_isRunning.value) {
            println("🛑 Отмена демонстрации: ${_currentDemo.value?.displayName}")

            // Устанавливаем флаг отмены
            isCancelled = true

            // Отменяем Job
            currentDemoJob?.cancel()
            currentDemoJob = null

            // Сбрасываем состояние
            _isRunning.value = false
            _currentDemo.value = null
            _progressMessage.value = "Демонстрация отменена"

            // Сбрасываем состояние печати
            onTypingStateChanged(false)

            // Вызываем колбэк завершения
            onDemoFinished()

            // Добавляем сообщение об отмене
            onMessageAdded(
                ChatMessageUI(
                    id = java.util.UUID.randomUUID().toString(),
                    role = "assistant",
                    content = "🛑 Демонстрация отменена пользователем",
                    metadata = "Отмена",
                    isDemoMessage = true
                )
            )

            println("✅ Демонстрация отменена")
        }
    }
}
