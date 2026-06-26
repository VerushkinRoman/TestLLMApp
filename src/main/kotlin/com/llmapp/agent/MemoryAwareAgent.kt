package com.llmapp.agent

import com.llmapp.api.ClientFactory
import com.llmapp.api.RouterClient
import com.llmapp.chat.ChatHistory
import com.llmapp.memory.Decision
import com.llmapp.memory.KnowledgeBase
import com.llmapp.memory.LongTermMemory
import com.llmapp.memory.LongTermMemoryManager
import com.llmapp.memory.ProjectConstraints
import com.llmapp.memory.TaskState
import com.llmapp.memory.UserProfile
import com.llmapp.memory.WorkingMemory
import com.llmapp.model.RouterRequest
import com.llmapp.model.RouterResponse
import com.llmapp.model.ResponseControl
import com.llmapp.model.TokenUsage
import java.io.File

data class MemoryAwareResponse(
    val content: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val finishReason: String?,
    val responseTimeMs: Long,
    val memoryUsed: MemoryUsageInfo
)

data class MemoryUsageInfo(
    val shortTermUsed: Boolean,
    val workingMemoryUsed: Boolean,
    val longTermUsed: Boolean,
    val workingMemorySnapshot: WorkingMemory?,
    val longTermSnapshot: LongTermMemory?
)

class MemoryAwareAgent(
    apiKey: String,
    private var model: String,
    systemPrompt: String,
    private var responseControl: ResponseControl = ResponseControl(),
    userHome: String = System.getProperty("user.home"),
    private val persistToDisk: Boolean = true
) {
    private val apiClient: RouterClient = ClientFactory.create(apiKey)
    private val tokenTracker = TokenTracker()
    private var requestCounter = 0

    private val shortTermHistory = ChatHistory(systemPrompt, maxHistorySize = 50)
    private var workingMemory = WorkingMemory()
    private val longTermManager = LongTermMemoryManager(File(userHome, ".llm_memory"))
    private var longTermMemory = LongTermMemory()

    var useShortTerm = true
    var useWorkingMemory = true
    var useLongTerm = true

    init {
        tokenTracker.updateModel(model)
        if (persistToDisk) {
            loadLongTermMemory()
        } else {
            longTermMemory = LongTermMemory()
            println("🧠 Демо-режим: временная память (без сохранения)")
        }
    }

    private fun loadLongTermMemory() {
        longTermMemory = LongTermMemory(
            profile = longTermManager.loadProfile(),
            projectConstraints = longTermManager.loadConstraints(),
            knowledgeBase = KnowledgeBase(longTermManager.loadAllKnowledge()),
            savedDecisions = emptyList()
        )
    }

    fun startNewTask(taskName: String, initialContext: Map<String, String> = emptyMap()) {
        workingMemory = WorkingMemory(
            taskId = java.util.UUID.randomUUID().toString(),
            taskName = taskName,
            currentState = TaskState.INIT,
            contextData = initialContext
        )
        println("📋 Начата новая задача: $taskName")
    }

    fun updateTaskState(newState: TaskState) {
        workingMemory = workingMemory.copy(currentState = newState)
        println("📊 Состояние задачи: ${newState.displayName}")
    }

    /**
     * Сохранить решение в рабочую память (с использованием addDecision)
     */
    fun addDecisionToWorkingMemory(topic: String, decision: String, context: String = "") {
        val decisionObj = Decision(topic, decision, context = context)
        workingMemory = workingMemory.addDecision(decisionObj)
        println("💾 Решение добавлено в рабочую память: $topic")
    }

    /**
     * Сохранить решение в долговременную память (с использованием saveDecision)
     */
    fun saveDecisionToLongTerm(topic: String, decision: String, context: String = "") {
        val decisionObj = Decision(topic, decision, context = context)
        longTermMemory = longTermMemory.saveDecision(decisionObj)
        if (persistToDisk) {
            longTermManager.saveKnowledge("decision_$topic", decision)
        }
        println("💾 Решение сохранено: $topic${if (!persistToDisk) " (временно)" else ""}")
    }

    /**
     * Добавить знание в knowledge base (с использованием addToKnowledge)
     */
    fun addKnowledge(key: String, value: String) {
        longTermMemory = longTermMemory.addToKnowledge(key, value)
        if (persistToDisk) {
            longTermManager.saveKnowledge(key, value)
        }
        println("📚 Знание добавлено: $key${if (!persistToDisk) " (временно)" else ""}")
    }

    /**
     * Получить знание из knowledge base
     */
    fun getKnowledge(key: String): String? {
        return longTermMemory.knowledgeBase.entries[key]
    }

    /**
     * Получить все знания
     */
    fun getAllKnowledge(): Map<String, String> {
        return longTermMemory.knowledgeBase.entries
    }

    /**
     * Получить все сохраненные решения
     */
    fun getAllDecisions(): List<Decision> {
        return longTermMemory.savedDecisions
    }

    /**
     * Обновить контекстные данные рабочей памяти
     */
    fun updateWorkingContext(key: String, value: String) {
        workingMemory = workingMemory.copy(
            contextData = workingMemory.contextData + (key to value)
        )
        println("📝 Контекст рабочей памяти обновлен: $key")
    }

    /**
     * Получить значение из контекста рабочей памяти
     */
    fun getWorkingContext(key: String): String? {
        return workingMemory.contextData[key]
    }

    /**
     * Очистить контекст рабочей памяти
     */
    fun clearWorkingContext() {
        workingMemory = workingMemory.copy(contextData = emptyMap())
        println("🗑️ Контекст рабочей памяти очищен")
    }

    fun getWorkingMemory(): WorkingMemory = workingMemory

    fun getUserProfile(): UserProfile = longTermMemory.profile

    fun getProjectConstraints(): ProjectConstraints = longTermMemory.projectConstraints

    fun updateProfile(profile: UserProfile) {
        longTermMemory = longTermMemory.updateProfile(profile)
        if (persistToDisk) {
            longTermManager.saveProfile(profile)
        }
        println("👤 Профиль обновлен: ${profile.name}${if (!persistToDisk) " (временно)" else ""}")
    }

    fun updateConstraints(constraints: ProjectConstraints) {
        longTermMemory = longTermMemory.updateConstraints(constraints)
        if (persistToDisk) {
            longTermManager.saveConstraints(constraints)
        }
        println("🔧 Ограничения обновлены${if (!persistToDisk) " (временно)" else ""}")
    }

    fun clearWorkingMemory() {
        workingMemory = workingMemory.clear()
        println("🗑️ Рабочая память полностью очищена")
    }

    suspend fun processRequest(userInput: String): MemoryAwareResponse {
        try {
            val enhancedPrompt = buildEnhancedPrompt(userInput)

            if (useShortTerm) {
                shortTermHistory.addUserMessage(enhancedPrompt)
            }

            val (response, responseTime) = sendToLLM()

            if (response.error != null) {
                throw Exception("API Error: ${response.error.message}")
            }

            val answer = response.choices?.firstOrNull()?.message?.content
                ?: throw Exception("Empty API response")

            if (useShortTerm) {
                shortTermHistory.addAssistantMessage(answer)
            }
            requestCounter++

            response.usage?.let { usage ->
                val tokenUsage = TokenUsage(
                    promptTokens = usage.promptTokens ?: 0,
                    completionTokens = usage.completionTokens ?: 0,
                    totalTokens = usage.totalTokens ?: 0
                )
                tokenTracker.trackRequest(tokenUsage, requestCounter)
            }

            return MemoryAwareResponse(
                content = answer,
                promptTokens = response.usage?.promptTokens,
                completionTokens = response.usage?.completionTokens,
                totalTokens = response.usage?.totalTokens,
                finishReason = response.choices.firstOrNull()?.finishReason,
                responseTimeMs = responseTime,
                memoryUsed = MemoryUsageInfo(
                    shortTermUsed = useShortTerm,
                    workingMemoryUsed = useWorkingMemory && workingMemory.taskName.isNotEmpty(),
                    longTermUsed = useLongTerm,
                    workingMemorySnapshot = if (useWorkingMemory && workingMemory.taskName.isNotEmpty()) workingMemory else null,
                    longTermSnapshot = if (useLongTerm) longTermMemory else null
                )
            )
        } catch (e: Exception) {
            println("❌ Ошибка в MemoryAwareAgent: ${e.message}")
            throw e
        }
    }

    private fun buildEnhancedPrompt(userInput: String): String {
        val memoryBlocks = mutableListOf<String>()

        if (useLongTerm) {
            memoryBlocks.add(buildLongTermMemoryBlock())
        }

        if (useWorkingMemory && workingMemory.taskName.isNotEmpty()) {
            memoryBlocks.add(workingMemory.getContextSummary())
        }

        val formatBlock =
            if (responseControl.enabled && responseControl.formatDescription != null) {
                "\n\nФормат ответа: ${responseControl.formatDescription}"
            } else ""

        return if (memoryBlocks.isNotEmpty()) {
            buildString {
                append("---\n")
                append("КОНТЕКСТ ПАМЯТИ АССИСТЕНТА:\n")
                append("---\n\n")
                append(memoryBlocks.joinToString("\n\n"))
                append("\n\n---\n")
                append("ТЕКУЩИЙ ЗАПРОС ПОЛЬЗОВАТЕЛЯ:\n")
                append(userInput)
                append(formatBlock)
            }
        } else {
            userInput + formatBlock
        }
    }

    private fun buildLongTermMemoryBlock(): String = buildString {
        append("📚 ДОЛГОВРЕМЕННАЯ ПАМЯТЬ:\n")

        val profile = longTermMemory.profile
        if (profile.name.isNotBlank() || profile.experience.isNotBlank()) {
            append("\n👤 Профиль пользователя:\n")
            if (profile.name.isNotBlank()) append("   • Имя: ${profile.name}\n")
            if (profile.experience.isNotBlank()) append("   • Опыт: ${profile.experience}\n")
            if (profile.preferredTech.isNotEmpty()) {
                append("   • Технологии: ${profile.preferredTech.joinToString(", ")}\n")
            }
            append("   • Стиль ответов: ${profile.preferredStyle.name.lowercase()}\n")
        }

        val constraints = longTermMemory.projectConstraints
        if (constraints.techStack.isNotEmpty() || constraints.forbiddenTech.isNotEmpty()) {
            append("\n🔧 Ограничения проекта:\n")
            if (constraints.techStack.isNotEmpty()) {
                append("   • Стек: ${constraints.techStack.joinToString(", ")}\n")
            }
            if (constraints.forbiddenTech.isNotEmpty()) {
                append("   • Запрещено: ${constraints.forbiddenTech.joinToString(", ")}\n")
            }
            if (constraints.architecture.isNotBlank()) {
                append("   • Архитектура: ${constraints.architecture}\n")
            }
            if (constraints.codingStandards.isNotBlank()) {
                append("   • Стандарты: ${constraints.codingStandards.take(80)}...\n")
            }
        }

        // Добавляем сохраненные знания - используем правильный доступ к entries
        val knowledgeEntries = longTermMemory.knowledgeBase.entries
        if (knowledgeEntries.isNotEmpty()) {
            append("\n📖 Сохраненные знания:\n")
            // Берем последние 5 знаний (самые свежие)
            knowledgeEntries.toList().takeLast(5).forEach { (key, value) ->
                append("   • $key: ${value.take(80)}${if (value.length > 80) "..." else ""}\n")
            }
            if (knowledgeEntries.size > 5) {
                append("   • ... и еще ${knowledgeEntries.size - 5} знаний\n")
            }
        }

        // Добавляем последние решения
        val savedDecisions = longTermMemory.savedDecisions
        if (savedDecisions.isNotEmpty()) {
            append("\n💡 Последние решения:\n")
            savedDecisions.takeLast(3).forEach { decision ->
                append("   • ${decision.topic}: ${decision.decision.take(60)}${if (decision.decision.length > 60) "..." else ""}\n")
            }
            if (savedDecisions.size > 3) {
                append("   • ... и еще ${savedDecisions.size - 3} решений\n")
            }
        }
    }

    private suspend fun sendToLLM(): Pair<RouterResponse, Long> {
        val messages = if (useShortTerm) {
            shortTermHistory.getMessages()
        } else {
            listOf(com.llmapp.model.ChatMessage(role = "user", content = "Текущий запрос"))
        }

        val request = RouterRequest(
            model = model,
            messages = messages,
            maxTokens = if (responseControl.enabled) responseControl.maxTokens else null,
            stop = if (responseControl.enabled) responseControl.stopSequences else null,
            temperature = if (responseControl.enabled) responseControl.temperature else null
        )

        val startTime = System.currentTimeMillis()
        val response = apiClient.sendRequest(request)
        val endTime = System.currentTimeMillis()

        return Pair(response, endTime - startTime)
    }

    fun getTokenStats() = tokenTracker.stats.value

    fun changeModel(newModel: String) {
        model = newModel
        tokenTracker.updateModel(newModel)
        println("🔄 Модель в MemoryAwareAgent изменена: $newModel")
    }
}
