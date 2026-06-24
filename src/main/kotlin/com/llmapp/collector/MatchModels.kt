package com.llmapp.collector

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StoredGame(
    @SerialName("_id") val mongoId: String,
    @SerialName("home_team") val homeTeam: String,
    @SerialName("away_team") val awayTeam: String,
    @SerialName("home_score") val homeScore: String? = null,
    @SerialName("away_score") val awayScore: String? = null,
    @SerialName("home_scorers") val homeScorers: String? = null,
    @SerialName("away_scorers") val awayScorers: String? = null,
    val group: String? = null,
    val matchday: String? = null,
    @SerialName("local_date") val localDate: String? = null,
    val finished: String? = null,
    val type: String? = null
)

@Serializable
data class StoredTeamStanding(
    @SerialName("team_name") val teamName: String,
    val mp: String,
    val w: String,
    val d: String,
    val l: String,
    val pts: String,
    val gf: String,
    val ga: String,
    val gd: String
)

@Serializable
data class StoredGroupData(
    val name: String,
    val teams: List<StoredTeamStanding>
)

@Serializable
data class CollectorSnapshot(
    val timestamp: Long,
    val games: List<StoredGame>,
    @SerialName("finished_count") val finishedCount: Int,
    @SerialName("total_count") val totalCount: Int,
    val groups: List<StoredGroupData>? = null
)

@Serializable
data class MatchSummary(
    @SerialName("generated_at") val generatedAt: Long,
    @SerialName("total_matches") val totalMatches: Int,
    @SerialName("finished_matches") val finishedMatches: Int,
    @SerialName("upcoming_matches") val upcomingMatches: Int,
    @SerialName("group_standings") val groupStandings: List<StoredGroupData>,
    @SerialName("recent_results") val recentResults: List<GameResult>,
    @SerialName("top_scorers") val topScorers: List<ScorerEntry>,
    @SerialName("knockout_matches") val knockoutMatches: List<GameResult> = emptyList()
)

@Serializable
data class GameResult(
    @SerialName("home_team") val homeTeam: String,
    @SerialName("away_team") val awayTeam: String,
    @SerialName("home_score") val homeScore: String,
    @SerialName("away_score") val awayScore: String,
    val group: String? = null,
    val date: String? = null,
    val scorers: String = ""
)

@Serializable
data class ScorerEntry(
    val player: String,
    val goals: Int,
    val team: String
)
