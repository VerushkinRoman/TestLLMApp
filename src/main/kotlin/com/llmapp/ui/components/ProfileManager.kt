package com.llmapp.ui.components

import com.llmapp.memory.LongTermMemoryManager
import com.llmapp.memory.ResponseStyle
import com.llmapp.memory.UserProfile
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.prefs.Preferences

class ProfileManager(
    private val storageDir: File,
    private val longTermManager: LongTermMemoryManager
) {
    companion object {
        private const val PREFS_NODE = "com.llmapp.profiles"
        private const val KEY_ACTIVE_PROFILE = "active_profile_name"
        private const val KEY_FIRST_LAUNCH = "first_launch"
    }

    private val prefs: Preferences = Preferences.userRoot().node(PREFS_NODE)

    fun setFirstLaunchCompleted() {
        prefs.putBoolean(KEY_FIRST_LAUNCH, false)
    }

    fun setActiveProfile(profile: UserProfile) {
        if (profile.name.isNotEmpty()) {
            prefs.put(KEY_ACTIVE_PROFILE, profile.name)
            saveProfile(profile)
        }
    }

    // ===== МЕТОДЫ ДЛЯ РАБОТЫ С ПРОФИЛЯМИ =====

    fun saveProfile(profile: UserProfile): Boolean {
        // Сохраняем через LongTermMemoryManager
        val result = longTermManager.saveProfile(profile)

        // Также сохраняем в отдельный файл для быстрого доступа
        saveProfileToFile(profile)

        return result
    }

    fun getAllProfiles(): List<UserProfile> {
        val profilesFromLTM = mutableListOf<UserProfile>()

        // Пробуем загрузить профиль из LongTermMemoryManager
        val ltmProfile = longTermManager.loadProfile()
        if (ltmProfile.name.isNotEmpty()) {
            profilesFromLTM.add(ltmProfile)
        }

        // Также загружаем из файлов
        val profileFile = File(storageDir, "profiles")
        if (profileFile.exists()) {
            val files = profileFile.listFiles { file -> file.extension == "md" }
            if (files != null && files.isNotEmpty()) {
                val fileProfiles = files.mapNotNull { file ->
                    try {
                        parseProfileFromFile(file.readText())
                    } catch (e: Exception) {
                        println("⚠️ Ошибка загрузки профиля ${file.name}: ${e.message}")
                        null
                    }
                }
                profilesFromLTM.addAll(fileProfiles)
            }
        }

        // Удаляем дубликаты по имени
        return profilesFromLTM.distinctBy { it.name }
    }

    fun deleteProfile(name: String): Boolean {
        // Удаляем из файла
        val profileFile = File(storageDir, "profiles")
        val file = File(profileFile, "${sanitizeFileName(name)}.md")
        val fileDeleted = if (file.exists()) {
            file.delete()
        } else true

        // Примечание: LongTermMemoryManager не имеет метода удаления профиля,
        // поэтому мы просто перезаписываем его пустым профилем
        val currentProfile = longTermManager.loadProfile()
        if (currentProfile.name == name) {
            longTermManager.saveProfile(UserProfile())
        }

        return fileDeleted
    }

    fun updateProfile(profile: UserProfile): Boolean {
        if (profile.name.isEmpty()) return false

        // Обновляем через LongTermMemoryManager
        val result = longTermManager.saveProfile(profile)

        // Также обновляем файл
        val profileFile = File(File(storageDir, "profiles"), "${sanitizeFileName(profile.name)}.md")
        if (profileFile.exists()) {
            saveProfileToFile(profile)
        } else {
            // Если файла нет, создаем новый
            saveProfileToFile(profile)
        }

        return result
    }

    fun createProfileFromPreset(preset: NamedProfile): UserProfile {
        val profile = preset.profile.copy(
            name = preset.name,
            customNotes = preset.profile.customNotes
        )
        // Сохраняем через LongTermMemoryManager
        longTermManager.saveProfile(profile)
        // И в файл
        saveProfileToFile(profile)
        return profile
    }

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====

    private fun saveProfileToFile(profile: UserProfile): Boolean {
        return try {
            val profileFile =
                File(File(storageDir, "profiles"), "${sanitizeFileName(profile.name)}.md")
            if (!profileFile.parentFile.exists()) {
                profileFile.parentFile.mkdirs()
            }
            val content = formatProfileToMarkdown(profile)
            profileFile.writeText(content)
            true
        } catch (e: Exception) {
            println("⚠️ Ошибка сохранения профиля в файл: ${e.message}")
            false
        }
    }

    private fun sanitizeFileName(name: String): String {
        // Удаляем недопустимые символы для имени файла
        return name.replace(Regex("[<>:\"/\\\\|?*]"), "_")
    }

    private fun formatProfileToMarkdown(profile: UserProfile): String = """
# 👤 Профиль пользователя

**Имя**: ${profile.name}
**Опыт**: ${profile.experience}
**Стиль**: ${profile.preferredStyle.name.lowercase()}

## Технологии
${profile.preferredTech.joinToString("\n") { "- $it" }.ifEmpty { "- (не указаны)" }}

## Цели
${profile.commonGoals.joinToString("\n") { "- $it" }.ifEmpty { "- (не указаны)" }}

## Заметки
${profile.customNotes.ifEmpty { "—" }}

---
*Последнее обновление: ${formatTimestamp(System.currentTimeMillis())}*
    """.trimIndent()

    private fun formatTimestamp(timestamp: Long): String {
        val dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp),
            ZoneId.systemDefault()
        )
        return dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
    }

    private fun parseProfileFromFile(content: String): UserProfile {
        var name = ""
        var experience = ""
        var style = ResponseStyle.BALANCED
        val tech = mutableListOf<String>()
        val goals = mutableListOf<String>()
        var notes = ""

        val lines = content.lines()
        var currentSection = ""

        for (line in lines) {
            when {
                line.startsWith("**Имя**") -> name = line.substringAfter(":").trim()
                line.startsWith("**Опыт**") -> experience = line.substringAfter(":").trim()
                line.startsWith("**Стиль**") -> {
                    val styleText = line.substringAfter(":").trim().lowercase()
                    style = when {
                        styleText.contains("кратк") || styleText.contains("concise") -> ResponseStyle.CONCISE
                        styleText.contains("деталь") || styleText.contains("detailed") -> ResponseStyle.DETAILED
                        styleText.contains("технич") || styleText.contains("technical") -> ResponseStyle.TECHNICAL
                        else -> ResponseStyle.BALANCED
                    }
                }

                line.startsWith("## Технологии") -> currentSection = "tech"
                line.startsWith("## Цели") -> currentSection = "goals"
                line.startsWith("## Заметки") -> currentSection = "notes"
                line.startsWith("- ") && currentSection == "tech" -> {
                    val t = line.drop(2).trim()
                    if (t.isNotEmpty() && t != "(не указаны)") tech.add(t)
                }

                line.startsWith("- ") && currentSection == "goals" -> {
                    val g = line.drop(2).trim()
                    if (g.isNotEmpty() && g != "(не указаны)") goals.add(g)
                }

                currentSection == "notes" && line.isNotBlank() && !line.startsWith("---") && !line.startsWith(
                    "*Последнее"
                ) -> {
                    notes += line + "\n"
                }
            }
        }

        return UserProfile(
            name = name.ifEmpty { "Пользователь" },
            experience = experience,
            preferredStyle = style,
            preferredTech = tech,
            commonGoals = goals,
            customNotes = notes.trim()
        )
    }
}
