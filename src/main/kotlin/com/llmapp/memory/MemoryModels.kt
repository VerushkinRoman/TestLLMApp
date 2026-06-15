package com.llmapp.memory

import kotlinx.serialization.Serializable
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Рабочая память - данные текущей задачи
 */
@Serializable
data class WorkingMemory(
    val taskId: String = "",
    val taskName: String = "",
    val currentState: TaskState = TaskState.INIT,
    val contextData: Map<String, String> = emptyMap(),
    val decisions: List<Decision> = emptyList(),
    val pendingQuestions: List<String> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun addDecision(decision: Decision): WorkingMemory {
        return copy(
            decisions = decisions + decision,
            lastUpdated = System.currentTimeMillis()
        )
    }

    fun clear(): WorkingMemory = WorkingMemory()

    fun getContextSummary(): String = buildString {
        append("📋 ТЕКУЩАЯ ЗАДАЧА:\n")
        append("   • Название: $taskName\n")
        append("   • Стадия: ${currentState.displayName}\n")
        if (contextData.isNotEmpty()) {
            append("   • Данные:\n")
            contextData.forEach { (key, value) ->
                append("      - $key: ${value.take(100)}${if (value.length > 100) "..." else ""}\n")
            }
        }
        if (decisions.isNotEmpty()) {
            append("   • Принятые решения:\n")
            decisions.takeLast(5).forEach { decision ->
                append("      - ${decision.topic}: ${decision.decision.take(80)}\n")
            }
        }
    }
}

@Serializable
enum class TaskState {
    INIT,           // Начальная
    CLARIFYING,     // Уточнение требований
    PLANNING,       // Планирование
    EXECUTING,      // Выполнение
    VALIDATING,     // Проверка
    DONE,           // Завершена
    BLOCKED;        // Заблокирована

    val displayName: String
        get() = when (this) {
            INIT -> "Инициализация"
            CLARIFYING -> "Уточнение требований"
            PLANNING -> "Планирование"
            EXECUTING -> "Выполнение"
            VALIDATING -> "Проверка"
            DONE -> "Завершена"
            BLOCKED -> "Заблокирована"
        }
}

@Serializable
data class Decision(
    val topic: String,
    val decision: String,
    val timestamp: Long = System.currentTimeMillis(),
    val context: String = ""
)

/**
 * Долговременная память - профиль, решения, знания
 */
@Serializable
data class LongTermMemory(
    val profile: UserProfile = UserProfile(),
    val projectConstraints: ProjectConstraints = ProjectConstraints(),
    val knowledgeBase: KnowledgeBase = KnowledgeBase(),
    val savedDecisions: List<Decision> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun addToKnowledge(key: String, value: String): LongTermMemory {
        return copy(
            knowledgeBase = KnowledgeBase(knowledgeBase.entries + (key to value)),
            lastUpdated = System.currentTimeMillis()
        )
    }

    fun saveDecision(decision: Decision): LongTermMemory {
        return copy(
            savedDecisions = savedDecisions + decision,
            lastUpdated = System.currentTimeMillis()
        )
    }

    fun updateProfile(newProfile: UserProfile): LongTermMemory {
        return copy(profile = newProfile, lastUpdated = System.currentTimeMillis())
    }

    fun updateConstraints(newConstraints: ProjectConstraints): LongTermMemory {
        return copy(projectConstraints = newConstraints, lastUpdated = System.currentTimeMillis())
    }
}

@Serializable
data class UserProfile(
    val name: String = "",
    val experience: String = "",
    val preferredStyle: ResponseStyle = ResponseStyle.BALANCED,
    val preferredTech: List<String> = emptyList(),
    val commonGoals: List<String> = emptyList(),
    val customNotes: String = ""
)

@Serializable
enum class ResponseStyle {
    CONCISE,    // Краткий
    DETAILED,   // Детальный
    BALANCED,   // Сбалансированный
    TECHNICAL   // Технический
}

@Serializable
data class ProjectConstraints(
    val techStack: List<String> = emptyList(),
    val forbiddenTech: List<String> = emptyList(),
    val architecture: String = "",
    val codingStandards: String = "",
    val specialRules: String = ""
)

@Serializable
data class KnowledgeBase(
    val entries: Map<String, String> = emptyMap()
)

/**
 * Менеджер долговременной памяти (файловое хранилище .md)
 */
class LongTermMemoryManager(private val storageDir: File) {

    init {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
    }

    fun saveProfile(profile: UserProfile): Boolean {
        return try {
            val file = File(storageDir, "profile.md")
            file.writeText(formatProfileToMarkdown(profile))
            true
        } catch (e: Exception) {
            println("❌ Ошибка сохранения профиля: ${e.message}")
            false
        }
    }

    fun loadProfile(): UserProfile {
        val file = File(storageDir, "profile.md")
        if (!file.exists()) return UserProfile()
        return try {
            parseProfileFromMarkdown(file.readText())
        } catch (_: Exception) {
            UserProfile()
        }
    }

    fun saveConstraints(constraints: ProjectConstraints): Boolean {
        return try {
            val file = File(storageDir, "constraints.md")
            file.writeText(formatConstraintsToMarkdown(constraints))
            true
        } catch (e: Exception) {
            println("❌ Ошибка сохранения ограничений: ${e.message}")
            false
        }
    }

    fun loadConstraints(): ProjectConstraints {
        val file = File(storageDir, "constraints.md")
        if (!file.exists()) return ProjectConstraints()
        return try {
            parseConstraintsFromMarkdown(file.readText())
        } catch (_: Exception) {
            ProjectConstraints()
        }
    }

    fun saveKnowledge(key: String, value: String): Boolean {
        val knowledgeFile = File(storageDir, "knowledge.md")
        val content = if (knowledgeFile.exists()) knowledgeFile.readText() else ""
        val newEntry = "## $key\n\n$value\n\n---\n"
        knowledgeFile.writeText(newEntry + content)
        return true
    }

    fun loadAllKnowledge(): Map<String, String> {
        val knowledgeFile = File(storageDir, "knowledge.md")
        if (!knowledgeFile.exists()) return emptyMap()
        return parseKnowledgeFromMarkdown(knowledgeFile.readText())
    }

    private fun formatProfileToMarkdown(profile: UserProfile): String = """
# 👤 Профиль пользователя

## Основная информация
- **Имя**: ${profile.name.ifEmpty { "—" }}
- **Опыт**: ${profile.experience.ifEmpty { "—" }}
- **Предпочитаемый стиль**: ${profile.preferredStyle.name.lowercase()}

## Технологии
${profile.preferredTech.joinToString("\n") { "- $it" }.ifEmpty { "- (не указаны)" }}

## Цели
${profile.commonGoals.joinToString("\n") { "- $it" }.ifEmpty { "- (не указаны)" }}

## Заметки
${profile.customNotes.ifEmpty { "—" }}

---
*Последнее обновление: ${formatTimestamp(System.currentTimeMillis())}*
    """.trimIndent()

    private fun parseProfileFromMarkdown(content: String): UserProfile {
        var name = ""
        var experience = ""
        var preferredStyle = ResponseStyle.BALANCED
        val preferredTech = mutableListOf<String>()
        val commonGoals = mutableListOf<String>()
        var customNotes = ""

        val lines = content.lines()
        var currentSection = ""

        for (line in lines) {
            when {
                line.startsWith("- **Имя**") -> name =
                    line.substringAfter("**").substringAfter(": ").trim()

                line.startsWith("- **Опыт**") -> experience =
                    line.substringAfter("**").substringAfter(": ").trim()

                line.startsWith("- **Предпочитаемый стиль**") -> {
                    val style = line.substringAfter(": ").trim().lowercase()
                    preferredStyle = when {
                        style.contains("concise") -> ResponseStyle.CONCISE
                        style.contains("detailed") -> ResponseStyle.DETAILED
                        style.contains("technical") -> ResponseStyle.TECHNICAL
                        else -> ResponseStyle.BALANCED
                    }
                }

                line.startsWith("## Технологии") -> currentSection = "tech"
                line.startsWith("## Цели") -> currentSection = "goals"
                line.startsWith("## Заметки") -> currentSection = "notes"
                line.startsWith("- ") && currentSection == "tech" -> {
                    val tech = line.drop(2).trim()
                    if (tech.isNotEmpty() && tech != "(не указаны)") preferredTech.add(tech)
                }

                line.startsWith("- ") && currentSection == "goals" -> {
                    val goal = line.drop(2).trim()
                    if (goal.isNotEmpty() && goal != "(не указаны)") commonGoals.add(goal)
                }

                currentSection == "notes" && line.isNotBlank() && !line.startsWith("---") && !line.startsWith(
                    "*Последнее"
                ) -> {
                    customNotes += line + "\n"
                }
            }
        }

        return UserProfile(
            name = name,
            experience = experience,
            preferredStyle = preferredStyle,
            preferredTech = preferredTech,
            commonGoals = commonGoals,
            customNotes = customNotes.trim()
        )
    }

    private fun formatConstraintsToMarkdown(constraints: ProjectConstraints): String = """
# 🔧 Ограничения проекта

## Технологический стек
${constraints.techStack.joinToString("\n") { "- $it" }.ifEmpty { "- (не указаны)" }}

## Запрещенные технологии
${constraints.forbiddenTech.joinToString("\n") { "- $it" }.ifEmpty { "- (нет)" }}

## Архитектура
${constraints.architecture.ifEmpty { "—" }}

## Стандарты кодирования
${constraints.codingStandards.ifEmpty { "—" }}

## Особые правила
${constraints.specialRules.ifEmpty { "—" }}

---
*Последнее обновление: ${formatTimestamp(System.currentTimeMillis())}*
    """.trimIndent()

    private fun parseConstraintsFromMarkdown(content: String): ProjectConstraints {
        val techStack = mutableListOf<String>()
        val forbiddenTech = mutableListOf<String>()
        var architecture = ""
        var codingStandards = ""
        var specialRules = ""

        val lines = content.lines()
        var currentSection = ""

        for (line in lines) {
            when {
                line.startsWith("## Технологический стек") -> currentSection = "tech"
                line.startsWith("## Запрещенные технологии") -> currentSection = "forbidden"
                line.startsWith("## Архитектура") -> currentSection = "arch"
                line.startsWith("## Стандарты кодирования") -> currentSection = "standards"
                line.startsWith("## Особые правила") -> currentSection = "rules"
                line.startsWith("- ") && currentSection == "tech" -> {
                    val tech = line.drop(2).trim()
                    if (tech.isNotEmpty() && tech != "(не указаны)") techStack.add(tech)
                }

                line.startsWith("- ") && currentSection == "forbidden" -> {
                    val tech = line.drop(2).trim()
                    if (tech.isNotEmpty() && tech != "(нет)") forbiddenTech.add(tech)
                }

                currentSection == "arch" && line.isNotBlank() && !line.startsWith("---") && !line.startsWith(
                    "*Последнее"
                ) -> {
                    if (line != "—") architecture += line + "\n"
                }

                currentSection == "standards" && line.isNotBlank() && !line.startsWith("---") && !line.startsWith(
                    "*Последнее"
                ) -> {
                    if (line != "—") codingStandards += line + "\n"
                }

                currentSection == "rules" && line.isNotBlank() && !line.startsWith("---") && !line.startsWith(
                    "*Последнее"
                ) -> {
                    if (line != "—") specialRules += line + "\n"
                }
            }
        }

        return ProjectConstraints(
            techStack = techStack,
            forbiddenTech = forbiddenTech,
            architecture = architecture.trim(),
            codingStandards = codingStandards.trim(),
            specialRules = specialRules.trim()
        )
    }

    private fun parseKnowledgeFromMarkdown(content: String): Map<String, String> {
        val knowledge = mutableMapOf<String, String>()
        val sections = content.split("\n---\n")

        for (section in sections) {
            val lines = section.lines()
            var key = ""
            val value = StringBuilder()
            var inValue = false

            for (line in lines) {
                if (line.startsWith("## ")) {
                    key = line.drop(3).trim()
                    inValue = true
                } else if (inValue && line.isNotBlank()) {
                    value.append(line).append("\n")
                }
            }

            if (key.isNotEmpty() && value.isNotEmpty()) {
                knowledge[key] = value.toString().trim()
            }
        }

        return knowledge
    }

    private fun formatTimestamp(timestamp: Long): String {
        val dateTime =
            LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
        return dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
    }
}
