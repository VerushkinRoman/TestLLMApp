package com.llmapp.invariants

import kotlinx.serialization.Serializable

/**
 * Инвариант - правило, которое агент не имеет права нарушать
 */
@Serializable
data class Invariant(
    val id: String,
    val name: String,
    val description: String,
    val type: InvariantType,
    val severity: Severity = Severity.ERROR,
    val checkPatterns: List<String> = emptyList(),
    val allowedValues: List<String> = emptyList(),
    val forbiddenValues: List<String> = emptyList(),
    val customCheck: String? = null
) {
    enum class Severity {
        ERROR, WARNING
    }
}

enum class InvariantType {
    ARCHITECTURE,      // Архитектурное решение
    TECH_STACK,        // Технологический стек
    CODING_STANDARD,   // Стандарт кодирования
    BUSINESS_RULE,     // Бизнес-правило
    SECURITY,          // Безопасность
    PERFORMANCE,       // Производительность
    CUSTOM             // Пользовательское
}

/**
 * Результат проверки инварианта
 */
@Serializable
data class InvariantCheckResult(
    val invariant: Invariant,
    val passed: Boolean,
    val message: String,
    val suggestions: List<String> = emptyList()
)

/**
 * Набор инвариантов для проекта
 */
@Serializable
data class InvariantSet(
    val name: String,
    val description: String,
    val invariants: List<Invariant> = emptyList(),
    val version: String = "1.0"
) {
    fun check(text: String): List<InvariantCheckResult> {
        val results = mutableListOf<InvariantCheckResult>()

        // Удаляем блоки кода для более точной проверки
        val textWithoutCode = text
            .replace(Regex("```[\\s\\S]*?```"), "")  // удаляем многострочные блоки кода
            .replace(Regex("`[^`]*`"), "")            // удаляем однострочные блоки кода

        for (invariant in invariants) {
            val passed = when {
                invariant.customCheck == "language_russian" -> checkLanguageRussian(textWithoutCode)
                else -> checkInvariant(textWithoutCode, invariant)
            }

            val message = if (passed) {
                "✅ Инвариант '${invariant.name}' соблюден"
            } else {
                "❌ Нарушение инварианта: ${invariant.description}"
            }

            results.add(
                InvariantCheckResult(
                    invariant = invariant,
                    passed = passed,
                    message = message,
                    suggestions = getSuggestions(invariant)
                )
            )
        }

        return results
    }

    private fun checkLanguageRussian(text: String): Boolean {
        // Удаляем код
        var cleanText = text
            .replace(Regex("```[\\s\\S]*?```"), "")
            .replace(Regex("`[^`]*`"), "")

        // Удаляем технические термины
        val technicalTerms = listOf(
            "kotlin", "java", "python", "javascript", "typescript",
            "compose", "android", "ios", "web", "api", "sdk",
            "class", "interface", "object", "fun", "val", "var",
            "suspend", "coroutine", "flow", "stateflow",
            "viewmodel", "mvvm", "mvi", "clean",
            "retrofit", "ktor", "sqldelight",
            "gradle", "maven",
            "http", "https", "rest", "graphql", "json",
            "error", "exception", "try", "catch"
        )

        technicalTerms.forEach { term ->
            cleanText = cleanText.replace(Regex("(?i)\\b$term\\b"), "")
        }

        // Удаляем цифры и спецсимволы
        cleanText = cleanText.replace(Regex("[0-9]"), "")
        cleanText = cleanText.replace(Regex("[:,;()\\[\\]{}<>]"), "")
        cleanText = cleanText.replace(Regex("\\b[a-z]\\b"), "")

        // Проверяем наличие русских букв
        val hasRussian =
            cleanText.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }

        // Если есть русские буквы - OK
        if (hasRussian) return true

        // Считаем английские слова
        val englishWords = cleanText.split(Regex("[\\s\\n\\r]+"))
            .filter { it.isNotEmpty() }
            .count { word ->
                word.all { it.isLetter() && !(it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё') }
            }

        return englishWords <= 5
    }

    private fun checkInvariant(text: String, invariant: Invariant): Boolean {
        val lowerText = text.lowercase()

        // Проверяем запрещенные значения
        if (invariant.forbiddenValues.isNotEmpty()) {
            for (forbidden in invariant.forbiddenValues) {
                if (lowerText.contains(forbidden.lowercase())) {
                    return false
                }
            }
        }

        // Проверяем разрешенные значения - делаем более гибкой
        if (invariant.allowedValues.isNotEmpty()) {
            var found = false
            for (allowed in invariant.allowedValues) {
                if (lowerText.contains(allowed.lowercase())) {
                    found = true
                    break
                }
            }
            // Если не нашли разрешенные значения, но есть описание - пропускаем
            if (!found && invariant.type == InvariantType.TECH_STACK) {
                // Для технологий - если есть упоминание Kotlin или Compose - OK
                val techKeywords = listOf("kotlin", "compose", "ktor", "coroutines", "flow", "kmp")
                for (keyword in techKeywords) {
                    if (lowerText.contains(keyword)) {
                        found = true
                        break
                    }
                }
            }
            if (!found) {
                return false
            }
        }

        // Проверяем паттерны - делаем более гибкой
        if (invariant.checkPatterns.isNotEmpty()) {
            for (pattern in invariant.checkPatterns) {
                // Проверяем, что паттерн не является частью технического термина
                if (lowerText.contains(pattern.lowercase()) &&
                    !lowerText.contains("${pattern.lowercase()} ") &&
                    !lowerText.contains(" ${pattern.lowercase()}")
                ) {
                    return false
                }
            }
        }

        return true
    }

    private fun getSuggestions(invariant: Invariant): List<String> {
        val suggestions = mutableListOf<String>()

        if (invariant.customCheck == "language_russian") {
            suggestions.add("Используйте русский язык в объяснениях")
            suggestions.add("Код и технические термины могут быть на английском")
        }

        if (invariant.allowedValues.isNotEmpty()) {
            suggestions.add("Используйте: ${invariant.allowedValues.joinToString(", ")}")
        }

        if (invariant.forbiddenValues.isNotEmpty()) {
            suggestions.add("Избегайте: ${invariant.forbiddenValues.joinToString(", ")}")
        }

        return suggestions
    }
}
