package com.example.data.model

import java.util.UUID

data class SportsChannel(
    val id: String = UUID.randomUUID().toString(),
    val name: String, // e.g. "TNT Sports", "BeIN Sports", "Sky Cricket"
    val category: String = "Sports",
    val logoText: String = "", // Short badge text e.g. "TNT", "TSN"
    val logoUrl: String = "",
    val streams: List<StreamSource> = emptyList(),
    val isFavorite: Boolean = false
)

data class CategoryItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String, // e.g. "SPORTS", "FANCODE IND", "SONYLIV LIVE"
    val channelCount: Int = 12,
    val iconName: String = "sports",
    val badgeType: String = "GREEN_CIRCLE"
)

data class HighlightItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val tournament: String,
    val duration: String,
    val thumbnailUrl: String = "",
    val videoUrl: String,
    val sport: SportType = SportType.FOOTBALL
)

data class AppConfig(
    val appTitle: String = "Sports Live",
    val version: String = "9.0",
    val announcement: String = "Welcome to Sports Live! Real-time International Cricket & Top Football streams are active.",
    val telegramLink: String = "https://t.me/sports_live_streams",
    val adminPin: String = "1234",
    val forceLowQuality: Boolean = false,
    val isHardwareAccelerationEnabled: Boolean = true,
    val remoteFeedUrl: String = "https://app.local/api",
    val autoSyncOnLaunch: Boolean = true,
    val syncLiveEnabled: Boolean = true,
    val syncUpcomingEnabled: Boolean = true,
    val syncFinishedEnabled: Boolean = true,
    val syncFootballEnabled: Boolean = true,
    val autoRefreshIntervalSeconds: Int = 30,
    val enabledFootballLeagues: List<String> = DEFAULT_FOOTBALL_LEAGUE_ALLOWLIST
) {
    companion object {
        val DEFAULT_FOOTBALL_LEAGUE_ALLOWLIST = listOf(
            "uefa.champions",
            "eng.1",
            "esp.1",
            "ita.1",
            "ger.1",
            "fra.1",
            "fifa.worldq.uefa",
            "fifa.worldq.conmebol",
            "fifa.worldq.afc",
            "uefa.euro",
            "uefa.nations",
            "fifa.friendly"
        )

        val VERIFIED_FOOTBALL_LEAGUE_LABELS = mapOf(
            "uefa.champions" to "UEFA Champions League",
            "uefa.europa" to "UEFA Europa League",
            "eng.1" to "English Premier League",
            "esp.1" to "Spanish La Liga",
            "ita.1" to "Italian Serie A",
            "ger.1" to "German Bundesliga",
            "fra.1" to "French Ligue 1",
            "fifa.world" to "FIFA World Cup",
            "fifa.worldq.uefa" to "FIFA World Cup Qualifiers (UEFA)",
            "fifa.worldq.conmebol" to "FIFA World Cup Qualifiers (CONMEBOL)",
            "fifa.worldq.afc" to "FIFA World Cup Qualifiers (AFC)",
            "uefa.euro" to "UEFA European Championship",
            "uefa.nations" to "UEFA Nations League",
            "conmebol.america" to "Copa America",
            "fifa.friendly" to "International Friendlies"
        )
    }
}

data class UnmatchedStreamAssociation(
    val oldMatchId: String,
    val team1Name: String,
    val team2Name: String,
    val tournament: String,
    val sport: SportType = SportType.FOOTBALL,
    val matchDateText: String,
    val streams: List<StreamSource>,
    val wasFavorite: Boolean,
    val reason: String
)

data class M3U8CheckResult(
    val url: String,
    val isOnline: Boolean,
    val httpStatusCode: Int = 0,
    val latencyMs: Long = 0,
    val contentType: String = "",
    val isHlsStream: Boolean = false,
    val message: String = ""
)

data class CricketScorecard(
    val matchTitle: String,
    val statusText: String,
    val team1Name: String,
    val team1Score: String,
    val team2Name: String,
    val team2Score: String,
    val crr: String,
    val rrr: String,
    val needRunsText: String,
    val batsmen: List<Pair<String, String>>,
    val bowlers: List<Pair<String, String>>,
    val recentBalls: List<String>
)

data class FootballMatchEvent(
    val minute: String,
    val eventType: String,
    val player: String,
    val team: String
)

data class FootballScoreDetail(
    val matchTitle: String,
    val tournament: String,
    val team1: String,
    val team2: String,
    val score: String,
    val time: String,
    val possession: Pair<Int, Int>,
    val shots: Pair<Int, Int>,
    val events: List<FootballMatchEvent>
)
