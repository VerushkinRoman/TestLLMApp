package com.llmapp.mcp

import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class McpIntegration(
    private val onLog: ((String) -> Unit)? = null
) {
    private val client = McpClient(onLog)
    private var connected = false
    private var tools: List<McpToolInfo> = emptyList()

    private fun log(msg: String) = onLog?.invoke(msg)

    suspend fun connect(): InitResult {
        val result = client.initialize()
        val rawTools = client.listTools()
        tools = rawTools.map { tool ->
            val paramList = mutableListOf<McpToolParam>()
            tool.inputSchema.properties?.forEach { (key, value) ->
                val obj = value.jsonObject
                paramList.add(
                    McpToolParam(
                        name = key,
                        type = obj["type"]?.jsonPrimitive?.content ?: "string",
                        description = obj["description"]?.jsonPrimitive?.content ?: ""
                    )
                )
            }
            McpToolInfo(
                name = tool.name,
                description = tool.description ?: "",
                parameters = paramList
            )
        }
        connected = true
        return result
    }

    fun disconnect() {
        client.close()
        connected = false
        tools = emptyList()
    }

    fun isConnected(): Boolean = connected

    fun getToolDescriptions(): String {
        if (tools.isEmpty()) return ""
        val sb = StringBuilder()
        sb.appendLine("You have access to the World Cup 2026 MCP server. Call ONE tool per response using this format:")
        sb.appendLine()
        sb.appendLine("[MCP_CALL]")
        sb.appendLine("{\"tool\": \"tool_name\", \"arguments\": {\"key\": \"value\"}}")
        sb.appendLine()
        sb.appendLine("IMPORTANT: The JSON MUST be valid — every quote and brace MUST be closed. Do NOT truncate or cut off the JSON. A broken JSON will cause an error.")
        sb.appendLine()
        sb.appendLine("Available tools and their response fields (use this to decide which tool answers the question):")
        sb.appendLine()
        sb.appendLine("=== get_team (HAS 1 REQUIRED PARAMETER) ===")
        sb.appendLine("  Parameters: name (string, required) — team name (e.g. \"Argentina\", \"Brazil\")")
        sb.appendLine("  Example call: {\"tool\": \"get_team\", \"arguments\": {\"name\": \"Argentina\"}}")
        sb.appendLine("  Returns: { team: { name_en, name_fa, flag, fifa_code, iso2, groups (group letter A-L), id (team numeric ID) } }")
        sb.appendLine("  Answers: team's group letter, flag, FIFA code, numeric team_id. Start here if you need to find a team's group.")
        sb.appendLine()
        sb.appendLine("=== get_teams (HAS 1 OPTIONAL PARAMETER) ===")
        sb.appendLine("  Parameters: group (string, optional) — filter by group letter A-L. If omitted, returns ALL 48 teams.")
        sb.appendLine("  Example call: {\"tool\": \"get_teams\", \"arguments\": {\"group\": \"J\"}} or with empty args: {\"tool\": \"get_teams\", \"arguments\": {}}")
        sb.appendLine("  Returns: { teams: [ { name_en, name_fa, flag, fifa_code, iso2, groups (group letter), id } ] }")
        sb.appendLine("  Answers: list of teams in a group, flags by country, FIFA codes, all teams in tournament.")
        sb.appendLine()
        sb.appendLine("=== get_group (HAS 1 REQUIRED PARAMETER) ===")
        sb.appendLine("  Parameters: name (string, required) — group letter A-L (e.g. \"J\")")
        sb.appendLine("  Example call: {\"tool\": \"get_group\", \"arguments\": {\"name\": \"J\"}}")
        sb.appendLine("  Returns: { group: { name (letter A-L), teams: [ { team_id, mp (matches played), w (wins), d (draws), l (losses), pts (points), gf (goals for), ga (goals against), gd (goal difference) } ] } }")
        sb.appendLine("  Answers: position in group table, points, wins/losses/draws, goals for/against, goal difference. Does NOT have goal scorers.")
        sb.appendLine()
        sb.appendLine("=== get_groups (NO PARAMETERS) ===")
        sb.appendLine("  Parameters: NONE — do NOT pass any arguments.")
        sb.appendLine("  Example call: {\"tool\": \"get_groups\", \"arguments\": {}}")
        sb.appendLine("  Returns: { groups: [ { name (letter), teams: [ { team_id, mp, w, d, l, pts, gf, ga, gd } ] } ] }")
        sb.appendLine("  Answers: same as get_group but for ALL 12 groups at once (large response). Only use if you need all groups.")
        sb.appendLine()
        sb.appendLine("=== get_games (NO PARAMETERS — returns ALL games) ===")
        sb.appendLine("  Parameters: NONE — do NOT pass any arguments. Do NOT try to filter by group or team. It returns everything.")
        sb.appendLine("  Example call: {\"tool\": \"get_games\", \"arguments\": {}}")
        sb.appendLine("  Returns ALL games (group stage + knockout) with full details:")
        sb.appendLine("  { games: [ {")
        sb.appendLine("    id, home_team_id, away_team_id,")
        sb.appendLine("    home_score, away_score (e.g. \"3\", \"0\", or \"null\" for future games),")
        sb.appendLine("    home_scorers (string with player names and minutes, e.g. \"Lionel Messi 17'\",\"Lionel Messi 60'\"),")
        sb.appendLine("    away_scorers (same format, or \"null\" if none),")
        sb.appendLine("    group (group letter A-L, or R32/QF/SF/FINAL), matchday (\"1\",\"2\",\"3\" for group stage),")
        sb.appendLine("    local_date (e.g. \"06/22/2026 12:00\"), finished (\"TRUE\"/\"FALSE\"),")
        sb.appendLine("    home_team_name_en, away_team_name_en (human-readable team names)")
        sb.appendLine("  } ] }")
        sb.appendLine("  THIS IS THE ONLY TOOL that returns goal scorers (home_scorers, away_scorers).")
        sb.appendLine("  Answers: who scored goals and in which minute, match scores (e.g. 3-0), match dates and times, which games a team played, match results (win/loss/draw), upcoming matches (finished=FALSE).")
        sb.appendLine()
        sb.appendLine("=== get_game (HAS 1 REQUIRED PARAMETER) ===")
        sb.appendLine("  Parameters: id (string, required) — game numeric ID")
        sb.appendLine("  Example call: {\"tool\": \"get_game\", \"arguments\": {\"id\": \"19\"}}")
        sb.appendLine("  Returns: { game: { id, home_team_id, away_team_id, home_score, away_score, home_scorers, away_scorers, group, matchday, local_date, finished, type } }")
        sb.appendLine("  Same fields as get_games but for a single game. Does NOT include team names (only IDs).")
        sb.appendLine()
        sb.appendLine("=== get_stadiums (NO PARAMETERS) ===")
        sb.appendLine("  Parameters: NONE — do NOT pass any arguments.")
        sb.appendLine("  Example call: {\"tool\": \"get_stadiums\", \"arguments\": {}}")
        sb.appendLine("  Returns: { stadiums: [ { id, name_en, name_fa, fifa_name, city_en, city_fa, country_en, country_fa, capacity (number), region (Eastern/Central/Western) } ] }")
        sb.appendLine("  Answers: stadium names, capacity (number of seats), city, country, region. Use to compare stadium sizes, find where a match is played.")
        sb.appendLine()
        sb.appendLine("=== get_stadium (HAS 1 REQUIRED PARAMETER) ===")
        sb.appendLine("  Parameters: id (string, required) — stadium numeric ID")
        sb.appendLine("  Example call: {\"tool\": \"get_stadium\", \"arguments\": {\"id\": \"4\"}}")
        sb.appendLine("  Returns: { stadium: { id, name_en, name_fa, fifa_name, city_en, city_fa, country_en, country_fa, capacity, region } }")
        sb.appendLine("  Same as get_stadiums but for a single stadium.")
        sb.appendLine()
        sb.appendLine("CRITICAL RULES:")
        sb.appendLine("- For get_group, the parameter name is EXACTLY \"name\" (not \"group\", not \"letter\", not \"id\").")
        sb.appendLine("- Do NOT output answer text together with [MCP_CALL]. Output ONLY the [MCP_CALL] block, nothing else.")
        sb.appendLine("- Do NOT call any tool twice with the same arguments.")
        sb.appendLine("- Before calling a tool, check: do I already have this exact data from a previous call? If yes, skip it.")
        sb.appendLine("- Once you have all the data to answer the user, stop calling tools and just answer in Russian.")
        sb.appendLine()
        sb.appendLine("DECIDE WHICH TOOL TO CALL BASED ON THE QUESTION:")
        sb.appendLine("- Position in group / points / wins-losses / goal difference: get_team (to find group) → get_group (to find position)")
        sb.appendLine("- Goals scored / who scored / goal scorers / match scores / results / when a match is played / upcoming matches: get_games (home_scorers/away_scorers are ONLY here; NOT in get_group/get_groups)")
        sb.appendLine("- Stadium name / capacity / how many seats / city / location / region: get_stadiums")
        sb.appendLine("- Team flag / FIFA country code / team ID / what group a team is in: get_team")
        sb.appendLine("- All teams in a group / list of countries / flags per group: get_teams (with optional group filter)")
        sb.appendLine()
        sb.appendLine("After calling a tool, you will receive the result. Use the data to answer in Russian.")
        return sb.toString()
    }

    suspend fun executeToolCall(jsonString: String): String {
        val json = Json { ignoreUnknownKeys = true }
        println("🔧 MCP -> server: $jsonString")
        val obj = json.parseToJsonElement(jsonString).jsonObject
        val toolName =
            obj["tool"]?.jsonPrimitive?.content ?: throw McpException("Missing 'tool' in MCP call")
        val args =
            obj["arguments"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
        log("🔧 MCP вызов: $toolName $args")
        println("🔧 MCP -> server: $toolName($args)")
        val result = client.callTool(toolName, args)
        val text = result.content.filterIsInstance<TextContent>().joinToString("\n") { it.text }
        log("📦 MCP результат: ${text.take(200)}")
        println("📦 MCP <- server: ${text.take(500)}")
        return text
    }

    data class McpToolInfo(
        val name: String,
        val description: String,
        val parameters: List<McpToolParam>
    )

    data class McpToolParam(
        val name: String,
        val type: String,
        val description: String
    )
}
