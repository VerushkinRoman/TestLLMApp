package com.llmapp.memory

object TaskMemoryTracker {

    private var memory = TaskMemory()
    private var conversationCounter = 0

    fun getMemory(): TaskMemory = memory

    fun reset() {
        memory = TaskMemory()
        conversationCounter = 0
    }

    fun processMessage(): TaskMemory {
        conversationCounter++
        updateState()
        return memory
    }

    private val goalChangeKeywords = listOf(
        "новая цель", "другая цель", "изменим цель", "поменяем цель",
        "забудь", "передумал", "не то", "начнём сначала", "начнем сначала",
        "новая задача", "другая задача",
    )

    fun isExplicitGoalChange(userText: String): Boolean {
        val lower = userText.lowercase()
        return goalChangeKeywords.any { lower.contains(it) }
    }

    private fun clean(text: String): String =
        text.replace("\\s+".toRegex(), " ").trim()

    fun processLLMResult(
        goal: String?,
        constraintsAndPrefs: List<String>? = null,
        progressDone: List<String>? = null,
        progressInProgress: List<String>? = null,
        progressBlocked: List<String>? = null,
        decisions: List<String>? = null,
        criticalContext: List<String>? = null,
        allowGoalOverride: Boolean = false,
        replaceItems: Boolean = false,
    ) {
        val p = memory.progress
        println("🧠 processLLMResult: replaceItems=$replaceItems before=[goal=${memory.goal.take(20)} cap=${memory.constraintsAndPrefs.size} done=${p.done.size} ip=${p.inProgress.size} blk=${p.blocked.size} dec=${memory.decisions.size} ctx=${memory.criticalContext.size}]")
        println("🧠 processLLMResult: input=[goal=$goal cap=${constraintsAndPrefs?.size} done=${progressDone?.size} ip=${progressInProgress?.size} blk=${progressBlocked?.size} dec=${decisions?.size} ctx=${criticalContext?.size}]")

        if (!goal.isNullOrBlank() && (memory.goal.isBlank() || allowGoalOverride)) {
            val candidate = clean(goal)
            val effective =
                if (memory.goal.isNotBlank() && !preserveKeyTerms(memory.goal, candidate)) {
                    println("🧠 goal validation: compressed goal dropped key terms, keeping original")
                    memory.goal
                } else {
                    candidate
                }
            memory = memory.copy(goal = effective)
        }

        processListField(
            constraintsAndPrefs,
            memory.constraintsAndPrefs,
            replaceItems,
            10,
            30
        ) { items ->
            memory = memory.copy(constraintsAndPrefs = items)
        }

        processListField(progressDone, p.done, replaceItems, 5, 30) { items ->
            memory = memory.copy(progress = p.copy(done = items))
        }
        processListField(progressInProgress, p.inProgress, replaceItems, 5, 30) { items ->
            memory = memory.copy(progress = p.copy(inProgress = items))
        }
        processListField(progressBlocked, p.blocked, replaceItems, 5, 30) { items ->
            memory = memory.copy(progress = p.copy(blocked = items))
        }

        processListField(decisions, memory.decisions, replaceItems, 10, 25) { items ->
            memory = memory.copy(decisions = items)
        }

        criticalContext?.forEach { raw ->
            val item = clean(raw)
            if (item.length > 1 && memory.criticalContext.none {
                    it.equals(item, ignoreCase = true) || item.equals(it, ignoreCase = true)
                }) {
                memory = memory.copy(criticalContext = memory.criticalContext + item)
            }
        }

        deduplicate()
        val p2 = memory.progress
        println("🧠 processLLMResult: after=[goal=${memory.goal.take(20)} cap=${memory.constraintsAndPrefs.size} done=${p2.done.size} ip=${p2.inProgress.size} blk=${p2.blocked.size} dec=${memory.decisions.size} ctx=${memory.criticalContext.size}]")
        updateState()
    }

    private fun processListField(
        input: List<String>?,
        current: List<String>,
        replaceItems: Boolean,
        minLen: Int,
        dedupLen: Int,
        setter: (List<String>) -> Unit,
    ) {
        if (input == null) return
        val items = input.map { clean(it) }.filter { it.length > minLen }
        if (replaceItems && items.isNotEmpty()) {
            setter(items)
        } else {
            var added = 0
            items.forEach { item ->
                if (current.none { existing ->
                        existing.contains(item.take(dedupLen), ignoreCase = true) ||
                                item.contains(existing.take(dedupLen), ignoreCase = true)
                    }) {
                    setter(current + item)
                    added++
                }
            }
            if (added > 0) println("  added=$added")
        }
    }

    private fun deduplicate() {
        val p = memory.progress
        memory = memory.copy(
            constraintsAndPrefs = memory.constraintsAndPrefs.distinctBy { it.take(50).lowercase() },
            progress = p.copy(
                done = p.done.distinctBy { it.take(40).lowercase() },
                inProgress = p.inProgress.distinctBy { it.take(40).lowercase() },
                blocked = p.blocked.distinctBy { it.take(40).lowercase() },
            ),
            decisions = memory.decisions.distinctBy { it.take(40).lowercase() },
        )
    }

    private fun preserveKeyTerms(oldGoal: String, newGoal: String): Boolean {
        if (oldGoal.isBlank() || newGoal.isBlank()) return true
        val stopWords = setOf(
            "для", "что", "как", "это", "ещё", "еще", "все", "так", "чтобы", "чтобы",
            "no", "the", "and", "for", "are", "was", "but", "not", "you", "all",
            "can", "has", "had", "its", "any", "use", "set", "get",
        )

        val tokenize: (String) -> Set<String> = { text ->
            text.split(Regex("[\\s,;—–-]+"))
                .map {
                    it.trim().lowercase().removeSurrounding("\"").removeSurrounding("«")
                        .removeSurrounding("»")
                }
                .filter { it.length >= 4 && it !in stopWords }
                .toSet()
        }

        val oldTokens = tokenize(oldGoal)
        if (oldTokens.size <= 2) return true

        val newTokens = tokenize(newGoal)
        if (newTokens.isEmpty()) return false

        val preserved = oldTokens.count { oldToken ->
            newTokens.any { newToken ->
                oldToken in newToken || newToken in oldToken ||
                        oldToken.commonPrefixWith(newToken).length >= 5
            }
        }
        val ratio = preserved.toDouble() / oldTokens.size

        println("🧠 goal validation: preserved $preserved/${oldTokens.size} key terms (ratio=$ratio)")
        return ratio >= 0.7
    }

    fun processCompressionSummary(summary: String) {
        val clean = summary.trim()

        val goalR = Regex("\\[GOAL](.*?)\\[/GOAL]", RegexOption.DOT_MATCHES_ALL)
        val constraintR =
            Regex("\\[CONSTRAINT](.*?)\\[/CONSTRAINT]", RegexOption.DOT_MATCHES_ALL)
        val decisionR = Regex("\\[DECISION](.*?)\\[/DECISION]", RegexOption.DOT_MATCHES_ALL)
        val contextR = Regex("\\[CONTEXT](.*?)\\[/CONTEXT]", RegexOption.DOT_MATCHES_ALL)
        val progDoneR =
            Regex("\\[PROGRESS_DONE](.*?)\\[/PROGRESS_DONE]", RegexOption.DOT_MATCHES_ALL)
        val progInR = Regex(
            "\\[PROGRESS_IN_PROGRESS](.*?)\\[/PROGRESS_IN_PROGRESS]",
            RegexOption.DOT_MATCHES_ALL
        )
        val progBlockedR =
            Regex("\\[PROGRESS_BLOCKED](.*?)\\[/PROGRESS_BLOCKED]", RegexOption.DOT_MATCHES_ALL)

        val bulletRegex = Regex("-\\s*(.+)", RegexOption.MULTILINE)
        val sectionRegex = Regex("##\\s*(.+?)\\n(.*?)(?=\\n##|\\z)", RegexOption.DOT_MATCHES_ALL)

        val hasTags = clean.contains("[GOAL]") || clean.contains("[CONSTRAINT]") ||
                clean.contains("[DECISION]") || clean.contains("[CONTEXT]") ||
                clean.contains("[PROGRESS_DONE]")

        val goal: String?
        val constraints: List<String>?
        val decisions: List<String>?
        val contexts: List<String>?
        val progDone: List<String>?
        val progIn: List<String>?
        val progBlocked: List<String>?

        fun parseTags(regex: Regex): List<String>? =
            regex.findAll(clean).mapNotNull {
                it.groupValues.getOrNull(1)?.trim()?.takeIf { v -> v.length > 3 }
            }.toList().takeIf { it.isNotEmpty() }

        if (hasTags) {
            goal = goalR.find(clean)?.groupValues?.getOrNull(1)?.trim()
                ?.takeIf { it.isNotBlank() && !it.contains("Unchanged", ignoreCase = true) }
            constraints = parseTags(constraintR)
            progDone = parseTags(progDoneR)
            progIn = parseTags(progInR)
            progBlocked = parseTags(progBlockedR)
            decisions = parseTags(decisionR)
            contexts = parseTags(contextR)
        } else {
            val sections = mutableMapOf<String, String>()
            sectionRegex.findAll(clean).forEach { m ->
                sections[m.groupValues[1].trim()] = m.groupValues[2].trim()
            }

            goal = sections.entries.firstOrNull { e ->
                e.key.contains("Goal", ignoreCase = true)
            }?.value?.split("\n")?.firstOrNull()?.trim()
                ?.takeIf { it.isNotBlank() && !it.contains("Unchanged", ignoreCase = true) }

            fun parseBullets(keyword: String): List<String>? {
                val body = sections.entries.firstOrNull { e ->
                    e.key.contains(keyword, ignoreCase = true)
                }?.value ?: return null
                return bulletRegex.findAll(body).mapNotNull {
                    it.groupValues[1].trim().takeIf { c -> c.length > 3 }
                }.toList().takeIf { it.isNotEmpty() }
            }

            constraints = parseBullets("Constraints")
            progDone = parseBullets("Done")
            progIn = parseBullets("In Progress") ?: parseBullets("InProgress")
            progBlocked = parseBullets("Blocked")
            decisions = parseBullets("Decisions")
            contexts = parseBullets("Critical Context") ?: parseBullets("Context")
        }

        processLLMResult(
            goal = goal,
            constraintsAndPrefs = constraints,
            progressDone = progDone,
            progressInProgress = progIn,
            progressBlocked = progBlocked,
            decisions = decisions,
            criticalContext = contexts,
            allowGoalOverride = true,
            replaceItems = true,
        )
    }

    fun formatCompressionAsMarkdown(): String {
        val mem = memory
        val p = mem.progress
        return buildString {
            appendLine("**Memory snapshot:**")
            appendLine()
            if (mem.goal.isNotEmpty()) {
                appendLine("## Goal")
                appendLine(mem.goal)
                appendLine()
            }
            if (mem.constraintsAndPrefs.isNotEmpty()) {
                appendLine("## Constraints & Preferences")
                mem.constraintsAndPrefs.forEach { appendLine("- $it") }
                appendLine()
            }
            if (p.done.isNotEmpty()) {
                appendLine("## Done")
                p.done.forEach { appendLine("- $it") }
                appendLine()
            }
            if (p.inProgress.isNotEmpty()) {
                appendLine("## In Progress")
                p.inProgress.forEach { appendLine("- $it") }
                appendLine()
            }
            if (p.blocked.isNotEmpty()) {
                appendLine("## Blocked")
                p.blocked.forEach { appendLine("- $it") }
                appendLine()
            }
            if (mem.decisions.isNotEmpty()) {
                appendLine("## Key Decisions")
                mem.decisions.forEach { appendLine("- $it") }
                appendLine()
            }
            if (mem.criticalContext.isNotEmpty()) {
                appendLine("## Critical Context")
                mem.criticalContext.forEach { appendLine("- $it") }
            }
        }
    }

    private fun updateState() {
        val p = memory.progress
        val parts = mutableListOf<String>()
        if (memory.goal.isNotEmpty()) parts.add("goal defined")
        if (memory.constraintsAndPrefs.isNotEmpty()) parts.add("constraints: ${memory.constraintsAndPrefs.size}")
        if (p.done.isNotEmpty()) parts.add("done: ${p.done.size}")
        if (p.inProgress.isNotEmpty()) parts.add("in progress: ${p.inProgress.size}")
        if (p.blocked.isNotEmpty()) parts.add("blocked: ${p.blocked.size}")
        if (memory.decisions.isNotEmpty()) parts.add("decisions: ${memory.decisions.size}")
        if (memory.criticalContext.isNotEmpty()) parts.add("context: ${memory.criticalContext.size}")

        val state = when {
            conversationCounter <= 2 -> "Начало диалога, сбор требований"
            memory.goal.isEmpty() -> "Уточнение цели"
            memory.constraintsAndPrefs.isEmpty() -> "Уточнение требований"
            else -> "Выполнение (${parts.joinToString(", ")})"
        }
        println("🧠 state: $state")
        memory = memory.copy(progress = p)  // keep progress, state is derived
    }
}
