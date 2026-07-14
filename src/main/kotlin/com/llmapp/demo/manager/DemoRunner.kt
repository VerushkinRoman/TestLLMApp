package com.llmapp.demo.manager

import com.llmapp.ui.models.ChatMessageUI
import com.llmapp.ui.models.RagSourceUI
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.time.Duration.Companion.milliseconds

/**
 * Базовый интерфейс для всех демонстраций
 */
interface DemoRunner {
    suspend fun run()
}

/**
 * Базовый класс для демонстраций с общими вспомогательными методами
 * Поддерживает отмену через корутины
 */
abstract class BaseDemoRunner(
    protected val onMessageAdded: (ChatMessageUI) -> Unit,
    @Suppress("unused") protected val onTypingStateChanged: (Boolean) -> Unit,
    protected val delayMs: Long = 300
) : DemoRunner {

    /**
     * Проверяет, не была ли отменена корутина.
     * Выбрасывает CancellationException если отменена.
     */
    protected suspend fun checkCancelled() {
        currentCoroutineContext().ensureActive()
    }

    /**
     * Добавляет сообщение с проверкой отмены
     */
    protected suspend fun addMessage(
        role: String,
        content: String,
        metadata: String? = null,
        promptTokens: Int? = null,
        completionTokens: Int? = null,
        totalTokens: Int? = null,
        responseTimeMs: Long? = null,
        isDemoMessage: Boolean = true,
        ragSources: List<RagSourceUI>? = null,
    ) {
        checkCancelled()

        val message = ChatMessageUI(
            id = java.util.UUID.randomUUID().toString(),
            role = role,
            content = content,
            metadata = metadata,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = totalTokens,
            responseTimeMs = responseTimeMs,
            isDemoMessage = isDemoMessage,
            ragSources = ragSources,
        )
        onMessageAdded(message)
        if (delayMs > 0) {
            checkCancelled()
            delay(delayMs.milliseconds)
        }
    }

    /**
     * Задержка с проверкой отмены
     */
    @Suppress("unused")
    protected suspend fun delayShort() {
        checkCancelled()
        delay(300.milliseconds)
    }

    @Suppress("unused")
    protected suspend fun delayMedium() {
        checkCancelled()
        delay(500.milliseconds)
    }

    @Suppress("unused")
    protected suspend fun delayLong() {
        checkCancelled()
        delay(1000.milliseconds)
    }

    protected fun stripServiceTags(content: String): String {
        return content
            .replace(Regex("\\[GOAL].*?\\[/GOAL]", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("\\[CONSTRAINT].*?\\[/CONSTRAINT]", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("\\[DECISION].*?\\[/DECISION]", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("\\[CONTEXT].*?\\[/CONTEXT]", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("\\[PROGRESS_DONE].*?\\[/PROGRESS_DONE]", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("\\[PROGRESS_IN_PROGRESS].*?\\[/PROGRESS_IN_PROGRESS]", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("\\[PROGRESS_BLOCKED].*?\\[/PROGRESS_BLOCKED]", RegexOption.DOT_MATCHES_ALL), "")
            .trim()
    }

    @Suppress("unused")
    protected fun formatTime(ms: Long): String = when {
        ms < 1000 -> "${ms}мс"
        ms < 60000 -> "${ms / 1000}.${(ms % 1000) / 100}с"
        else -> "${ms / 60000}м ${(ms % 60000) / 1000}с"
    }

    @Suppress("unused")
    protected fun formatNumber(num: Int): String = when {
        num >= 1_000_000 -> "${num / 1_000_000}M"
        num >= 1_000 -> "${num / 1_000}K"
        else -> num.toString()
    }
}
