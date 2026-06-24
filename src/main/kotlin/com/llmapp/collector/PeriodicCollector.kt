package com.llmapp.collector

import com.llmapp.mcp.McpClient
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.minutes

class PeriodicCollector(
    private val store: MatchStore,
    private val onLog: (String) -> Unit = {},
    private val onSummaryGenerated: ((MatchSummary) -> Unit)? = null
) {
    private var job: Job? = null
    private var mcpClient: McpClient? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private var intervalMinutes: Double = 15.0

    val isRunning: Boolean get() = job?.isActive == true

    suspend fun start(interval: Double = 15.0) {
        if (isRunning) return
        intervalMinutes = interval
        val label =
            if (interval < 1.0) "${(interval * 60).toLong()}сек" else "${interval.toLong()}мин"
        onLog("[${timestamp()}] Запуск сбора матчей (интервал: $label)")

        mcpClient = McpClient(onLog = { onLog("[MCP] $it") })
        try {
            mcpClient?.initialize()
            onLog("[${timestamp()}] MCP подключён")
        } catch (e: Exception) {
            onLog("[${timestamp()}] Ошибка MCP: ${e.message}")
            return
        }

        job = scope.launch {
            collectOnce()
            while (isActive) {
                delay(intervalMinutes.minutes)
                collectOnce()
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        onLog("[${timestamp()}] Остановка сбора")
        job?.cancel()
        job = null
        CoroutineScope(Dispatchers.IO).launch {
            try {
                mcpClient?.close()
            } catch (_: Exception) {
            }
            mcpClient = null
            onLog("[${timestamp()}] MCP отключён")
        }
    }

    suspend fun collectOnce(): CollectorSnapshot? {
        val client = mcpClient
        if (client == null) {
            onLog("[${timestamp()}] MCP недоступен. Переподключение...")
            try {
                mcpClient = McpClient(onLog = { onLog("[MCP] $it") })
                mcpClient?.initialize()
            } catch (e: Exception) {
                onLog("[${timestamp()}] Ошибка переподключения: ${e.message}")
                return null
            }
        }

        return try {
            onLog("[${timestamp()}] >> Начало сбора данных <<")
            val mcp = client ?: return null

            val prevSnapshot = store.loadLatestSnapshot()

            onLog("[${timestamp()}] Запрос get_games...")
            val gamesResult = mcp.callTool("get_games", emptyMap())
            val gamesText =
                gamesResult.content.filterIsInstance<TextContent>().joinToString("\n") { it.text }
            val games = parseGames(gamesText)
            val finished = games.count { it.finished == "TRUE" }

            val groups = fetchGroupStandings(mcp)

            val snapshot = CollectorSnapshot(
                timestamp = System.currentTimeMillis(),
                games = games,
                finishedCount = finished,
                totalCount = games.size,
                groups = groups
            )

            store.saveSnapshot(snapshot)

            if (prevSnapshot != null) {
                val changes = MatchAggregator.computeChanges(prevSnapshot, snapshot)
                if (changes.isNotEmpty()) {
                    onLog("[${timestamp()}] Изменения (${changes.size}):")
                    changes.forEach { onLog("[${timestamp()}]   $it") }
                } else {
                    onLog("[${timestamp()}] Данные без изменений")
                }
            } else {
                onLog("[${timestamp()}] Первый сбор: ${games.size} матчей, ${groups?.size ?: 0} групп")
            }

            onLog("[${timestamp()}] Сохранено ${games.size} матчей ($finished завершено)")

            val summary = MatchAggregator.generateSummary(snapshot)
            onSummaryGenerated?.invoke(summary)

            snapshot
        } catch (e: Exception) {
            onLog("[${timestamp()}] Ошибка: ${e.message ?: e.javaClass.simpleName}")
            null
        }
    }

    private suspend fun fetchGroupStandings(client: McpClient): List<StoredGroupData>? {
        return try {
            onLog("[${timestamp()}] Запрос get_teams...")
            val teamsResult = client.callTool("get_teams", emptyMap())
            val teamsText =
                teamsResult.content.filterIsInstance<TextContent>().joinToString("\n") { it.text }
            val teamNameById = parseTeamNames(teamsText)

            onLog("[${timestamp()}] Запрос get_groups...")
            val groupsResult = client.callTool("get_groups", emptyMap())
            val groupsText =
                groupsResult.content.filterIsInstance<TextContent>().joinToString("\n") { it.text }
            val groups = parseGroups(groupsText, teamNameById)
            onLog("[${timestamp()}] Загружено ${groups.size} групп")
            groups
        } catch (e: Exception) {
            onLog("[${timestamp()}] Ошибка загрузки групп: ${e.message}")
            null
        }
    }

    private fun parseTeamNames(jsonText: String): Map<String, String> {
        return try {
            val element = json.parseToJsonElement(jsonText)

            val teamsArray = element.jsonObject["teams"]?.jsonArray
            if (teamsArray == null) {
                onLog("[${timestamp()}] get_teams: нет поля teams, response=${jsonText.take(200)}")
                return emptyMap()
            }

            val map = mutableMapOf<String, String>()
            for (teamObj in teamsArray) {
                val obj = teamObj.jsonObject
                val name = obj["name_en"]?.jsonPrimitive?.contentOrNull
                    ?: obj["name"]?.jsonPrimitive?.contentOrNull
                    ?: obj["fifa_code"]?.jsonPrimitive?.contentOrNull
                    ?: "?"

                for (key in listOf("_id", "id", "team_id", "numeric_id")) {
                    val id = obj[key]?.jsonPrimitive?.contentOrNull
                    if (!id.isNullOrBlank()) map[id] = name
                }

                val groupsField = obj["groups"]?.jsonPrimitive?.contentOrNull
                if (groupsField != null) map["group:$groupsField"] = name
            }

            onLog("[${timestamp()}] Загружено ${teamsArray.size} команд, ${map.size} алиасов")
            map
        } catch (e: Exception) {
            onLog("[${timestamp()}] Ошибка парсинга команд: ${e.message}")
            emptyMap()
        }
    }

    private fun parseGroups(
        jsonText: String,
        teamNames: Map<String, String>
    ): List<StoredGroupData> {
        return try {
            val element = json.parseToJsonElement(jsonText)
            val groupsArray = element.jsonObject["groups"]?.jsonArray
            if (groupsArray == null) {
                onLog("[${timestamp()}] get_groups: нет поля groups, response=${jsonText.take(200)}")
                return emptyList()
            }
            val resolved = mutableListOf<String>()
            val unresolved = mutableListOf<String>()
            val groups = groupsArray.mapNotNull { groupElem ->
                try {
                    val obj = groupElem.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val teamsArray = obj["teams"]?.jsonArray ?: return@mapNotNull null
                    val teams = teamsArray.map { teamObj ->
                        val t = teamObj.jsonObject
                        val teamId = t["team_id"]?.jsonPrimitive?.content ?: ""
                        val resolvedName = teamNames[teamId]
                        if (resolvedName != null) resolved.add(teamId)
                        else unresolved.add(teamId)
                        StoredTeamStanding(
                            teamName = resolvedName ?: teamId,
                            mp = t["mp"]?.jsonPrimitive?.content ?: "0",
                            w = t["w"]?.jsonPrimitive?.content ?: "0",
                            d = t["d"]?.jsonPrimitive?.content ?: "0",
                            l = t["l"]?.jsonPrimitive?.content ?: "0",
                            pts = t["pts"]?.jsonPrimitive?.content ?: "0",
                            gf = t["gf"]?.jsonPrimitive?.content ?: "0",
                            ga = t["ga"]?.jsonPrimitive?.content ?: "0",
                            gd = t["gd"]?.jsonPrimitive?.content ?: "0"
                        )
                    }
                    StoredGroupData(
                        name = name,
                        teams = teams.sortedByDescending { it.pts.toIntOrNull() ?: 0 })
                } catch (_: Exception) {
                    null
                }
            }.sortedBy { it.name }
            if (unresolved.isNotEmpty()) {
                onLog("[${timestamp()}] Не разрешено ID команд: ${unresolved.distinct().take(10)}")
            }
            groups
        } catch (e: Exception) {
            onLog("[${timestamp()}] Ошибка парсинга групп: ${e.message}")
            emptyList()
        }
    }

    private fun timestamp(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))

    private fun parseGames(jsonText: String): List<StoredGame> {
        return try {
            val element = json.parseToJsonElement(jsonText)
            val gamesArray = element.jsonObject["games"]?.jsonArray
                ?: (element as? JsonArray ?: return emptyList())

            gamesArray.mapNotNull { gameElem ->
                try {
                    val obj = gameElem.jsonObject
                    StoredGame(
                        mongoId = obj["_id"]?.jsonPrimitive?.content
                            ?: obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                        homeTeam = obj["home_team_name_en"]?.jsonPrimitive?.contentOrNull
                            ?: obj["home_team_id"]?.jsonPrimitive?.content ?: "",
                        awayTeam = obj["away_team_name_en"]?.jsonPrimitive?.contentOrNull
                            ?: obj["away_team_id"]?.jsonPrimitive?.content ?: "",
                        homeScore = obj["home_score"]?.jsonPrimitive?.contentOrNull,
                        awayScore = obj["away_score"]?.jsonPrimitive?.contentOrNull,
                        homeScorers = obj["home_scorers"]?.jsonPrimitive?.contentOrNull,
                        awayScorers = obj["away_scorers"]?.jsonPrimitive?.contentOrNull,
                        group = obj["group"]?.jsonPrimitive?.contentOrNull,
                        matchday = obj["matchday"]?.jsonPrimitive?.contentOrNull,
                        localDate = obj["local_date"]?.jsonPrimitive?.contentOrNull,
                        finished = obj["finished"]?.jsonPrimitive?.contentOrNull,
                        type = obj["type"]?.jsonPrimitive?.contentOrNull
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            onLog("[${timestamp()}] Ошибка парсинга: ${e.message}")
            emptyList()
        }
    }

}
