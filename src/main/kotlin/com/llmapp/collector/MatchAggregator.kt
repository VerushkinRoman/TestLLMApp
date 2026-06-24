package com.llmapp.collector

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

object MatchAggregator {

    fun generateSummary(snapshot: CollectorSnapshot): MatchSummary {
        val games = snapshot.games
        val finished = games.filter { it.finished == "TRUE" }

        val groupStandings = snapshot.groups ?: emptyList()

        val recentResults = finished
            .sortedByDescending { it.localDate ?: "" }
            .take(20)
            .map { game ->
                GameResult(
                    homeTeam = game.homeTeam,
                    awayTeam = game.awayTeam,
                    homeScore = game.homeScore ?: "?",
                    awayScore = game.awayScore ?: "?",
                    group = game.group,
                    date = game.localDate,
                    scorers = formatScorers(game)
                )
            }

        val topScorers = extractTopScorers(finished)

        val knockoutMatches = games
            .filter { game ->
                val g = game.group ?: ""
                g == "R32" || g == "R16" || g == "QF" || g == "SF" || g == "FINAL" || g == "3RD"
            }
            .filter { it.homeTeam != "0" && it.awayTeam != "0" }
            .map { game ->
                GameResult(
                    homeTeam = game.homeTeam,
                    awayTeam = game.awayTeam,
                    homeScore = game.homeScore ?: "?",
                    awayScore = game.awayScore ?: "?",
                    group = game.group,
                    date = game.localDate,
                    scorers = formatScorers(game)
                )
            }

        return MatchSummary(
            generatedAt = snapshot.timestamp,
            totalMatches = snapshot.totalCount,
            finishedMatches = snapshot.finishedCount,
            upcomingMatches = snapshot.totalCount - snapshot.finishedCount,
            groupStandings = groupStandings,
            recentResults = recentResults,
            topScorers = topScorers,
            knockoutMatches = knockoutMatches
        )
    }

    fun generateTextSummary(snapshot: CollectorSnapshot): String {
        val summary = generateSummary(snapshot)
        val sb = StringBuilder()
        sb.appendLine("=== СВОДКА МАТЧЕЙ ЧМ-2026 ===")
        sb.appendLine("Всего матчей: ${summary.totalMatches}")
        sb.appendLine("Сыграно: ${summary.finishedMatches}")
        sb.appendLine("Осталось: ${summary.upcomingMatches}")
        sb.appendLine()

        if (summary.groupStandings.isNotEmpty()) {
            sb.appendLine("--- Турнирная таблица ---")
            for (group in summary.groupStandings) {
                sb.append(renderGroupTable(group))
                sb.appendLine()
            }
        }

        if (summary.recentResults.isNotEmpty()) {
            sb.appendLine("--- Последние результаты (до ${summary.recentResults.size}) ---")
            for (gm in summary.recentResults.take(10)) {
                sb.appendLine("${gm.homeTeam} ${gm.homeScore}:${gm.awayScore} ${gm.awayTeam}")
                if (gm.scorers.isNotBlank()) sb.appendLine("  Голы: ${gm.scorers}")
            }
        }

        if (summary.topScorers.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("--- Бомбардиры ---")
            summary.topScorers.take(15).forEachIndexed { i, scorer ->
                val label = when {
                    scorer.goals % 10 == 1 && scorer.goals % 100 != 11 -> "гол"
                    scorer.goals % 10 in 2..4 && scorer.goals % 100 !in 12..14 -> "гола"
                    else -> "голов"
                }
                sb.appendLine("${i + 1}. ${scorer.player} (${scorer.team}) — ${scorer.goals} $label")
            }
        }

        if (summary.knockoutMatches.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("--- Плей-офф ---")
            for (gm in summary.knockoutMatches) {
                sb.appendLine("${gm.homeTeam} ${gm.homeScore}:${gm.awayScore} ${gm.awayTeam}")
                if (gm.scorers.isNotBlank()) sb.appendLine("  Голы: ${gm.scorers}")
            }
        }

        return sb.toString()
    }

    private fun renderGroupTable(group: StoredGroupData): String {
        val sb = StringBuilder()
        sb.appendLine("Группа ${group.name}")

        val headers = listOf("Команда", "И", "В", "Н", "П", "ГЗ", "ГП", "РМ", "О")
        val colWidths = intArrayOf(
            maxOf(group.teams.maxOfOrNull { visibleLen(it.teamName) } ?: 6, 6),
            1, 1, 1, 1, 2, 2, 3, 1
        )
        colWidths[0] = colWidths[0].coerceIn(6, 30)

        fun pad(s: String, w: Int) = s.padEnd(w)
        fun padCenter(s: String, w: Int) = s.padStart((w + s.length) / 2).padEnd(w)

        fun sepLine(left: String, mid: String, right: String): String {
            val parts = colWidths.map { w -> mid.repeat(w + 2) }
            return left + parts.joinToString(mid) + right
        }

        sb.appendLine(sepLine("┌", "┬", "┐"))
        sb.append("│")
        for (i in headers.indices) {
            sb.append(" ${padCenter(headers[i], colWidths[i])} │")
        }
        sb.appendLine()
        sb.appendLine(sepLine("├", "┼", "┤"))

        for (row in group.teams) {
            val vals =
                listOf(row.teamName, row.mp, row.w, row.d, row.l, row.gf, row.ga, row.gd, row.pts)
            sb.append("│")
            for (i in vals.indices) {
                val v = vals[i]
                val w = colWidths[i]
                if (i == 0) sb.append(" ${pad(v, w)} │")
                else sb.append(" ${padCenter(v, w)} │")
            }
            sb.appendLine()
        }
        sb.append(sepLine("└", "┴", "┘"))

        return sb.toString()
    }

    private fun visibleLen(s: String): Int {
        var len = 0
        for (c in s) {
            len += if (c.code > 0x7E) 2 else 1
        }
        return len
    }

    data class RawScorer(val player: String, val minute: String)

    fun parseScorers(raw: String?): List<RawScorer> {
        if (raw.isNullOrBlank() || raw.trim() == "null") return emptyList()

        val trimmed = raw.trim()

        val entries: List<String> = tryParseAsJsonArray(trimmed)
            ?: tryParseAsJsonObject(trimmed)
            ?: tryParseAsBareArray(trimmed)
            ?: smartSplit(stripOuterBraces(trimmed))
            ?: return emptyList()

        return entries.mapNotNull { entry ->
            val cleaned = entry.trim()
                .removeSurrounding("\"")
                .removeSurrounding("'")
                .trim()
            if (cleaned.isBlank() || cleaned == "null") null
            else parseOneScorer(cleaned)
        }
    }

    private fun tryParseAsJsonArray(text: String): List<String>? {
        if (!text.startsWith("[")) return null
        return try {
            val j = Json { ignoreUnknownKeys = true; isLenient = true }
            val element = j.parseToJsonElement(text)
            if (element is JsonArray) element.map { it.jsonPrimitive.content }
            else null
        } catch (_: Exception) {
            null
        }
    }

    private fun tryParseAsJsonObject(text: String): List<String>? {
        if (!text.startsWith("{")) return null
        return try {
            val j = Json { ignoreUnknownKeys = true; isLenient = true }
            val element = j.parseToJsonElement(text)
            if (element is JsonObject) element.values.map { it.jsonPrimitive.content }
            else null
        } catch (_: Exception) {
            null
        }
    }

    private fun tryParseAsBareArray(text: String): List<String>? {
        if (!text.startsWith("\"") || !text.endsWith("\"")) return null
        return try {
            val j = Json { ignoreUnknownKeys = true; isLenient = true }
            val element = j.parseToJsonElement(text)
            if (element is JsonPrimitive) {
                val inner = element.content.trim()
                when {
                    inner.startsWith("[") -> {
                        val arr = j.parseToJsonElement(inner)
                        if (arr is JsonArray) arr.map { it.jsonPrimitive.content } else null
                    }

                    inner.startsWith("{") -> {
                        val obj = j.parseToJsonElement(inner)
                        if (obj is JsonObject) obj.values.map { it.jsonPrimitive.content } else null
                    }

                    else -> listOf(inner)
                }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun stripOuterBraces(text: String): String {
        var s = text
        if (s.startsWith("{") && s.endsWith("}")) s = s.substring(1, s.length - 1).trim()
        if (s.startsWith("[") && s.endsWith("]")) s = s.substring(1, s.length - 1).trim()
        return s
    }

    private fun smartSplit(text: String): List<String>? {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var inQuote = false
        for (ch in text) {
            when (ch) {
                '"' -> inQuote = !inQuote
                '(', '[', '{' -> {
                    depth++; current.append(ch)
                }

                ')', ']', '}' -> {
                    depth--; current.append(ch)
                }

                ',' if depth == 0 && !inQuote -> {
                    result.add(current.toString())
                    current.clear()
                }

                else -> current.append(ch)
            }
        }
        if (current.isNotBlank()) result.add(current.toString())
        return result.takeIf { it.isNotEmpty() }
    }

    private fun parseOneScorer(entry: String): RawScorer? {
        if (entry.isBlank()) return null

        val parenIdx = entry.indexOf('(')
        if (parenIdx > 0) {
            val namePart = entry.substring(0, parenIdx).trim()
            val rest = entry.substring(parenIdx + 1)
            val closeIdx = rest.lastIndexOf(')')
            val inside = if (closeIdx >= 0) rest.substring(0, closeIdx) else rest
            val parts = inside.split(",").map { it.trim() }
            val minute = if (parts.size >= 2) {
                parts.last().removeSuffix("'").trim().removeSuffix("(p)").removeSuffix("(OG)")
                    .trim()
            } else ""
            val player = namePart.ifBlank { parts.firstOrNull() ?: "" }
            return RawScorer(player, minute)
        }

        val tokens = entry.split(" ")
        if (tokens.size >= 2) {
            val minute = tokens.last()
                .removeSuffix("'")
                .removeSuffix("(p)")
                .removeSuffix("(OG)")
                .trim()
            val player = tokens.dropLast(1).joinToString(" ")
            return RawScorer(player, minute)
        }

        return null
    }

    private fun formatScorers(game: StoredGame): String {
        val parts = mutableListOf<String>()
        val home = parseScorers(game.homeScorers)
        val away = parseScorers(game.awayScorers)
        home.forEach { parts.add("${it.player} (${game.homeTeam}, ${it.minute}')") }
        away.forEach { parts.add("${it.player} (${game.awayTeam}, ${it.minute}')") }
        return parts.joinToString("; ")
    }

    private fun extractTopScorers(finishedGames: List<StoredGame>): List<ScorerEntry> {
        val goalCounts = mutableMapOf<String, MutableMap<String, Int>>()
        val teamOfPlayer = mutableMapOf<String, String>()

        for (game in finishedGames) {
            val home = parseScorers(game.homeScorers)
            val away = parseScorers(game.awayScorers)
            home.forEach { scorer ->
                val counts = goalCounts.getOrPut(scorer.player) { mutableMapOf() }
                counts[game.homeTeam] = (counts[game.homeTeam] ?: 0) + 1
                teamOfPlayer[scorer.player] = game.homeTeam
            }
            away.forEach { scorer ->
                val counts = goalCounts.getOrPut(scorer.player) { mutableMapOf() }
                counts[game.awayTeam] = (counts[game.awayTeam] ?: 0) + 1
                teamOfPlayer[scorer.player] = game.awayTeam
            }
        }

        return goalCounts.entries
            .filter { it.key.isNotBlank() && it.key != "null" }
            .map { (player, teamGoals) ->
                val team = teamGoals.maxByOrNull { it.value }?.key ?: teamOfPlayer[player] ?: ""
                val total = teamGoals.values.sum()
                ScorerEntry(player, total, team)
            }
            .sortedByDescending { it.goals }
    }

    fun computeChanges(
        previous: CollectorSnapshot,
        current: CollectorSnapshot
    ): List<String> {
        val changes = mutableListOf<String>()
        val prevGames = previous.games.associateBy { it.mongoId }
        for (game in current.games) {
            val prev = prevGames[game.mongoId]
            if (prev == null) {
                changes.add("+ ${game.homeTeam} vs ${game.awayTeam} (новый матч)")
            } else if (prev.finished != "TRUE" && game.finished == "TRUE") {
                changes.add("✓ ${game.homeTeam} ${game.homeScore}:${game.awayScore} ${game.awayTeam} — матч завершён")
            } else if (prev.homeScore != game.homeScore || prev.awayScore != game.awayScore) {
                changes.add("✎ ${game.homeTeam} ${prev.homeScore.orEmpty()}:${prev.awayScore.orEmpty()} → ${game.homeScore.orEmpty()}:${game.awayScore.orEmpty()} vs ${game.awayTeam}")
            }
        }
        return changes
    }
}
