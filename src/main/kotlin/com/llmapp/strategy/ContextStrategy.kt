package com.llmapp.strategy

import com.llmapp.model.ChatMessage

interface ContextStrategy {
    fun addMessage(message: ChatMessage)
    fun getContextForRequest(): List<ChatMessage>
    fun getFullHistory(): List<ChatMessage>
    fun clear()
    fun getStats(): StrategyStats
    fun getName(): String
    fun getDescription(): String
}

data class StrategyStats(
    val totalMessages: Int,
    val contextSizeTokens: Int,
    val estimatedTokensSaved: Int,
    val additionalInfo: Map<String, Any> = emptyMap()
)

class SlidingWindowStrategy(
    private val windowSize: Int = 20,
    systemPrompt: String
) : ContextStrategy {
    private val messages = mutableListOf<ChatMessage>()

    init {
        messages.add(ChatMessage(role = "system", content = systemPrompt))
    }

    override fun addMessage(message: ChatMessage) {
        messages.add(message)
        while (messages.size > windowSize + 1) {
            messages.removeAt(1)
        }
    }

    override fun getContextForRequest(): List<ChatMessage> = messages.toList()
    override fun getFullHistory(): List<ChatMessage> = messages.toList()

    override fun clear() {
        val system = messages.firstOrNull()
        messages.clear()
        if (system != null) messages.add(system)
    }

    override fun getStats(): StrategyStats = StrategyStats(
        totalMessages = messages.size - 1,
        contextSizeTokens = estimateTokens(messages),
        estimatedTokensSaved = 0,
        additionalInfo = mapOf(
            "windowSize" to windowSize,
            "droppedMessages" to maxOf(0, messages.size - windowSize - 1)
        )
    )

    override fun getName(): String = "Sliding Window"
    override fun getDescription(): String =
        "Хранит только последние $windowSize сообщений. Старые отбрасываются."

    private fun estimateTokens(messages: List<ChatMessage>): Int =
        messages.sumOf { it.content.length / 4 }
}

class StickyFactsStrategy(
    private val keepLastMessages: Int = 10,
    private val systemPrompt: String
) : ContextStrategy {
    private val messages = mutableListOf<ChatMessage>()
    private val facts = mutableMapOf<String, String>()

    init {
        messages.add(ChatMessage(role = "system", content = systemPrompt))
    }

    override fun addMessage(message: ChatMessage) {
        messages.add(message)

        if (message.role == "user") {
            extractFacts(message.content)
        }

        while (messages.size > keepLastMessages + 1) {
            messages.removeAt(1)
        }
    }

    private fun extractFacts(content: String) {
        val lowerContent = content.lowercase()

        val patterns = mapOf(
            "цель" to listOf("хочу", "нужно", "цель", "задача", "требуется"),
            "ограничение" to listOf("нельзя", "ограничение", "запрещено", "только"),
            "предпочтение" to listOf("нравится", "предпочитаю", "лучше", "хотелось бы"),
            "решение" to listOf("решили", "договорились", "выбрали", "остановились на"),
            "технология" to listOf("kotlin", "compose", "android", "ktor", "flow", "coroutine")
        )

        patterns.forEach { (category, keywords) ->
            keywords.forEach { keyword ->
                if (lowerContent.contains(keyword)) {
                    val sentences = content.split(Regex("[.!?]"))
                    for (sentence in sentences) {
                        if (sentence.lowercase().contains(keyword)) {
                            val trimmed = sentence.trim()
                            if (trimmed.length in 10..200) {
                                facts[category] = trimmed
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    override fun getContextForRequest(): List<ChatMessage> {
        val context = mutableListOf<ChatMessage>()
        context.add(ChatMessage(role = "system", content = systemPrompt))

        if (facts.isNotEmpty()) {
            val factsBlock = buildString {
                append("\n### ИЗВЛЕЧЕННЫЕ ФАКТЫ ИЗ ДИАЛОГА ###\n")
                facts.forEach { (key, value) ->
                    append("• $key: $value\n")
                }
                append("### КОНЕЦ ФАКТОВ ###\n")
            }
            context.add(ChatMessage(role = "system", content = factsBlock))
        }

        context.addAll(messages.drop(1).takeLast(keepLastMessages))
        return context
    }

    override fun getFullHistory(): List<ChatMessage> = messages.toList()

    override fun clear() {
        val system = messages.firstOrNull()
        messages.clear()
        if (system != null) messages.add(system)
        facts.clear()
    }

    override fun getStats(): StrategyStats = StrategyStats(
        totalMessages = messages.size - 1,
        contextSizeTokens = estimateTokens(getContextForRequest()),
        estimatedTokensSaved = estimateTokens(messages) - estimateTokens(getContextForRequest()),
        additionalInfo = mapOf(
            "factsCount" to facts.size,
            "keepLastMessages" to keepLastMessages
        )
    )

    override fun getName(): String = "Sticky Facts"
    override fun getDescription(): String =
        "Хранит ключевые факты + последние $keepLastMessages сообщений"

    fun getFacts(): Map<String, String> = facts.toMap()

    private fun estimateTokens(messages: List<ChatMessage>): Int =
        messages.sumOf { it.content.length / 4 }
}

class BranchingStrategy(
    private val systemPrompt: String
) : ContextStrategy {
    data class Branch(
        val id: String,
        val name: String,
        val messages: MutableList<ChatMessage>,
        val parentBranchId: String? = null,
        val checkpointIndex: Int = 0
    )

    private var currentBranchId: String
    private val branches = mutableMapOf<String, Branch>()
    private val checkpoints = mutableListOf<Checkpoint>()

    init {
        val mainBranch = Branch(
            id = generateId(),
            name = "main",
            messages = mutableListOf(ChatMessage(role = "system", content = systemPrompt))
        )
        branches[mainBranch.id] = mainBranch
        currentBranchId = mainBranch.id
    }

    override fun addMessage(message: ChatMessage) {
        val currentBranch = branches[currentBranchId] ?: return
        currentBranch.messages.add(message)
    }

    fun createCheckpoint(name: String): String {
        val currentBranch = branches[currentBranchId] ?: return ""
        val checkpointId = generateId()

        checkpoints.add(
            Checkpoint(
                id = checkpointId,
                name = name,
                branchId = currentBranchId,
                messageIndex = currentBranch.messages.size,
                timestamp = System.currentTimeMillis()
            )
        )

        return checkpointId
    }

    fun createBranch(checkpointId: String, branchName: String): String {
        val checkpoint = checkpoints.find { it.id == checkpointId } ?: return ""
        val parentBranch = branches[checkpoint.branchId] ?: return ""

        val branchMessages = parentBranch.messages
            .take(checkpoint.messageIndex)
            .toMutableList()

        val newBranch = Branch(
            id = generateId(),
            name = branchName,
            messages = branchMessages,
            parentBranchId = checkpoint.branchId,
            checkpointIndex = checkpoint.messageIndex
        )

        branches[newBranch.id] = newBranch
        return newBranch.id
    }

    fun switchToBranch(branchId: String): Boolean {
        if (branches.containsKey(branchId)) {
            currentBranchId = branchId
            return true
        }
        return false
    }

    fun getAllBranches(): List<BranchInfo> {
        return branches.values.map { branch ->
            BranchInfo(
                id = branch.id,
                name = branch.name,
                messageCount = branch.messages.size - 1,
                isCurrent = branch.id == currentBranchId,
                parentBranchName = branches[branch.parentBranchId]?.name
            )
        }
    }

    override fun getContextForRequest(): List<ChatMessage> {
        return branches[currentBranchId]?.messages?.toList() ?: emptyList()
    }

    override fun getFullHistory(): List<ChatMessage> {
        return branches[currentBranchId]?.messages?.toList() ?: emptyList()
    }

    override fun clear() {
        val mainBranch = Branch(
            id = generateId(),
            name = "main",
            messages = mutableListOf(ChatMessage(role = "system", content = systemPrompt))
        )
        branches.clear()
        checkpoints.clear()
        branches[mainBranch.id] = mainBranch
        currentBranchId = mainBranch.id
    }

    override fun getStats(): StrategyStats {
        val currentBranch = branches[currentBranchId] ?: return StrategyStats(0, 0, 0)
        return StrategyStats(
            totalMessages = currentBranch.messages.size - 1,
            contextSizeTokens = estimateTokens(currentBranch.messages),
            estimatedTokensSaved = 0,
            additionalInfo = mapOf(
                "branchesCount" to branches.size,
                "checkpointsCount" to checkpoints.size,
                "currentBranch" to currentBranch.name
            )
        )
    }

    override fun getName(): String = "Branching"
    override fun getDescription(): String = "Позволяет создавать ветки диалога от чекпоинтов"

    private fun estimateTokens(messages: List<ChatMessage>): Int =
        messages.sumOf { it.content.length / 4 }

    private fun generateId(): String = java.util.UUID.randomUUID().toString()

    data class Checkpoint(
        val id: String,
        val name: String,
        val branchId: String,
        val messageIndex: Int,
        val timestamp: Long
    )

    data class BranchInfo(
        val id: String,
        val name: String,
        val messageCount: Int,
        val isCurrent: Boolean,
        val parentBranchName: String?
    )
}
