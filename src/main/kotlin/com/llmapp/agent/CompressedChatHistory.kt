package com.llmapp.agent

import com.llmapp.api.OpenRouterClient
import com.llmapp.model.ChatMessage
import com.llmapp.model.OpenRouterRequest

class CompressedChatHistory(
    private val apiClient: OpenRouterClient,
    private val systemPrompt: String,
    private val keepLastMessages: Int = 10,
    private val summarizeEvery: Int = 8,
    private val maxHistorySize: Int = 100
) {
    private val fullHistory = mutableListOf<ChatMessage>()
    private val summaries = mutableListOf<SummaryBlock>()
    private val pendingForSummary = mutableListOf<ChatMessage>()

    var compressionEnabled = true
    private var lastSummaryIndex = 0

    init {
        fullHistory.add(ChatMessage(role = "system", content = systemPrompt))
    }

    fun addUserMessage(content: String) {
        val message = ChatMessage(role = "user", content = content)
        addMessage(message)
    }

    fun addAssistantMessage(content: String) {
        val message = ChatMessage(role = "assistant", content = content)
        addMessage(message)
    }

    private fun addMessage(message: ChatMessage) {
        fullHistory.add(message)
        if (compressionEnabled) {
            pendingForSummary.add(message)
        }
        trim()
    }

    suspend fun generateSummary(): String {
        if (!compressionEnabled || pendingForSummary.size < summarizeEvery / 2) {
            return ""
        }

        println("📝 Создаю summary для ${pendingForSummary.size} сообщений...")

        val messagesToSummarize = pendingForSummary.toList()
        val summaryPrompt = buildSummaryPrompt(messagesToSummarize)

        val summary = try {
            callSummarizationApi(summaryPrompt)
        } catch (e: Exception) {
            println("⚠️ Ошибка при создании summary: ${e.message}")
            fallbackSummary(messagesToSummarize)
        }

        summaries.add(
            SummaryBlock(
                summary = summary,
                startIndex = fullHistory.size - pendingForSummary.size,
                endIndex = fullHistory.size,
                messageCount = pendingForSummary.size,
                timestamp = System.currentTimeMillis()
            )
        )

        lastSummaryIndex = fullHistory.size
        pendingForSummary.clear()

        println("✅ Summary создан: ${summary.take(100)}...")
        return summary
    }

    fun getCompressedHistory(): List<ChatMessage> {
        if (!compressionEnabled || summaries.isEmpty()) {
            return getMessages()
        }

        val compressed = mutableListOf<ChatMessage>()

        compressed.add(ChatMessage(role = "system", content = systemPrompt))

        if (summaries.isNotEmpty()) {
            val contextSummary = buildContextSummary()
            compressed.add(
                ChatMessage(
                    role = "system",
                    content = "### КРАТКОЕ СОДЕРЖАНИЕ ПРЕДЫДУЩЕГО ДИАЛОГА ###\n$contextSummary\n### КОНЕЦ СОДЕРЖАНИЯ ###"
                )
            )
        }

        val recentMessages = getRecentMessages(keepLastMessages)
        compressed.addAll(recentMessages)

        return compressed
    }

    fun getMessagesForRequest(useCompression: Boolean = true): List<ChatMessage> {
        return if (useCompression && compressionEnabled) {
            getCompressedHistory()
        } else {
            getMessages()
        }
    }

    fun getMessages(): List<ChatMessage> = fullHistory.toList()

    fun getRecentMessages(count: Int): List<ChatMessage> {
        val start = maxOf(1, fullHistory.size - count)
        return fullHistory.drop(start)
    }

    fun clear() {
        val systemMessage = fullHistory.firstOrNull()
        fullHistory.clear()
        if (systemMessage != null) {
            fullHistory.add(systemMessage)
        }
        summaries.clear()
        pendingForSummary.clear()
        lastSummaryIndex = 0
    }

    fun size(): Int = fullHistory.size - 1

    fun getCompressionStats(): CompressionStats {
        val fullSize = fullHistory.sumOf { it.content.length }
        val summariesSize = summaries.sumOf { it.summary.length }
        val recentSize = getRecentMessages(keepLastMessages).sumOf { it.content.length }
        val compressedSize = summariesSize + recentSize

        return CompressionStats(
            totalMessages = fullHistory.size - 1,
            summariesCount = summaries.size,
            pendingForSummaryCount = pendingForSummary.size,
            originalSizeChars = fullSize,
            compressedSizeChars = compressedSize,
            compressionRatio = if (fullSize > 0) compressedSize.toDouble() / fullSize else 1.0,
            tokensSaved = estimateTokensSaved()
        )
    }

    private fun buildSummaryPrompt(messages: List<ChatMessage>): String {
        val conversation = messages.joinToString("\n") { msg ->
            when (msg.role) {
                "user" -> "Пользователь: ${msg.content}"
                "assistant" -> "Ассистент: ${msg.content}"
                else -> msg.content
            }
        }

        return """
            Сделай краткое, но информативное резюме следующего диалога. 
            Сохрани все важные факты, решения и ключевые моменты.
            Используй русский язык. Будь лаконичен.
            
            ДИАЛОГ:
            $conversation
            
            РЕЗЮМЕ (3-5 предложений):
        """.trimIndent()
    }

    private fun buildContextSummary(): String {
        return summaries.joinToString("\n\n---\n\n") { block ->
            "[Часть ${summaries.indexOf(block) + 1}] ${block.summary}"
        }
    }

    private suspend fun callSummarizationApi(prompt: String): String {
        val request = OpenRouterRequest(
            model = "google/gemma-4-26b-a4b-it:free",
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            maxTokens = 500,
            temperature = 0.3
        )

        val response = apiClient.sendRequest(request)

        if (response.error != null) {
            throw Exception(response.error.message)
        }

        return response.choices?.firstOrNull()?.message?.content ?: ""
    }

    private fun fallbackSummary(messages: List<ChatMessage>): String {
        val userMessages = messages.filter { it.role == "user" }.take(3)
        val topics = userMessages.joinToString(", ") { it.content.take(50) }
        val assistantResponses = messages.filter { it.role == "assistant" }.take(2)
        val keyPoints = assistantResponses.joinToString("; ") { it.content.take(60) }

        return "Диалог на темы: $topics. Ключевые ответы: $keyPoints. (${messages.size} сообщений)"
    }

    private fun trim() {
        if (fullHistory.size > maxHistorySize + 1) {
            val systemMessage = fullHistory.first()
            val recentMessages = fullHistory.takeLast(maxHistorySize)
            fullHistory.clear()
            fullHistory.add(systemMessage)
            fullHistory.addAll(recentMessages)
        }
    }

    private fun estimateTokensSaved(): Int {
        val originalTokens = fullHistory.sumOf { it.content.length } / 4
        val compressedTokens = summaries.sumOf { it.summary.length } / 4 +
                getRecentMessages(keepLastMessages).sumOf { it.content.length } / 4

        return maxOf(0, originalTokens - compressedTokens)
    }

    data class SummaryBlock(
        val summary: String,
        val startIndex: Int,
        val endIndex: Int,
        val messageCount: Int,
        val timestamp: Long
    )

    data class CompressionStats(
        val totalMessages: Int,
        val summariesCount: Int,
        val pendingForSummaryCount: Int,
        val originalSizeChars: Int,
        val compressedSizeChars: Int,
        val compressionRatio: Double,
        val tokensSaved: Int
    ) {
        fun getFormatted(): String = """
            📊 Статистика компрессии:
               • Сообщений всего: $totalMessages
               • Блоков summary: $summariesCount
               • Ожидают summary: $pendingForSummaryCount
               • Размер оригинал: ${formatSize(originalSizeChars)}
               • Размер сжатый: ${formatSize(compressedSizeChars)}
               • Коэффициент сжатия: ${"%.1f".format(compressionRatio * 100)}%
               • Сэкономлено токенов: ~$tokensSaved
        """.trimIndent()

        private fun formatSize(chars: Int): String = when {
            chars < 1024 -> "$chars символов"
            chars < 1024 * 1024 -> "${chars / 1024} KB"
            else -> "${chars / (1024 * 1024)} MB"
        }
    }
}
