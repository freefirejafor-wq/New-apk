package com.example.data.model

import java.util.UUID

data class SportsChannel(
    val id: String = UUID.randomUUID().toString(),
    val name: String, // e.g. "TNT Sports", "BeIN Sports", "Sky Cricket"
    val category: String = "Sports",
    val logoText: String = "", // Short badge text e.g. "TNT", "TSN"
    val logoUrl: String = "",
    val streams: List<StreamSource> = emptyList(),
    val isFavorite: Boolean = false,
    val isEnabled: Boolean = true
) {
    fun publicEnabledStreams(): List<StreamSource> {
        return streams
            .filter { it.isEnabled && it.url.isNotBlank() }
            .sortedWith(
                compareBy<StreamSource> { it.isBackup }
                    .thenByDescending { it.isDefault }
                    .thenBy { it.priorityOrder }
            )
    }
}

data class CategoryItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String, // e.g. "SPORTS", "FANCODE IND", "SONYLIV LIVE"
    val channelCount: Int = 12,
    val iconName: String = "sports"
)

data class HighlightItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val tournament: String,
    val duration: String,
    val thumbnailUrl: String = "",
    val videoUrl: String,
    val sport: SportType = SportType.FOOTBALL,
    val matchId: String? = null,
    val isApproved: Boolean = true
)

data class FootballCompetitionOption(
    val leagueId: String,
    val name: String,
    val category: String, // "International" or "Club"
    val isNationalTeam: Boolean
)

data class ProviderSyncDiagnostics(
    val providerName: String,
    val enabled: Boolean = true,
    val lastSyncEpochMs: Long? = null,
    val lastSyncDhakaText: String = "Never",
    val statusText: String = "Idle",
    val lastError: String? = null,
    val activeMatchCount: Int = 0,
    val isStale: Boolean = false
)

data class UnmappedAssociationNotice(
    val previousMatchId: String,
    val sport: SportType,
    val team1Name: String,
    val team2Name: String,
    val tournament: String,
    val hadFavorite: Boolean,
    val preservedStreams: List<StreamSource> = emptyList(),
    val detectedAtDhaka: String,
    val reason: String
)

data class AppConfig(
    val appTitle: String = "Sports Live",
    val version: String = "9.0",
    val announcement: String = "Welcome to Sports Live! Ultra HD multi-server live sports streams are active.",
    val telegramLink: String = "https://t.me/sports_live_streams",
    val adminPin: String = "1234",
    val forceLowQuality: Boolean = false,
    val isHardwareAccelerationEnabled: Boolean = true,
    val remoteFeedUrl: String = "https://api.goscorer.com/api/v3/getLiveMatches",
    val autoSyncOnLaunch: Boolean = true,
    val footballSyncEnabled: Boolean = true,
    val cricketSyncEnabled: Boolean = true,
    val autoRefreshIntervalSec: Int = 30,
    val enabledFootballLeagues: Set<String> = DEFAULT_ENABLED_FOOTBALL_LEAGUES
) {
    companion object {
        val DEFAULT_ENABLED_FOOTBALL_LEAGUES: Set<String> = setOf(
            // Required International & Major Tournaments
            "uefa.champions",
            "fifa.worldq.uefa",
            "fifa.worldq.conmebol",
            "uefa.nations",
            // Required Major Club Leagues (Big Five)
            "eng.1",
            "esp.1",
            "ita.1",
            "ger.1",
            "fra.1",
            // Additional Verified Senior International Competitions
            "fifa.world",
            "fifa.worldq.concacaf",
            "fifa.worldq.afc",
            "fifa.worldq.caf",
            "fifa.worldq.ofc",
            "fifa.friendly",
            "conmebol.america",
            "uefa.euro",
            "caf.nations",
            "afc.cup",
            "concacaf.gold",
            // Additional Verified Senior First-Team Club Competitions
            "uefa.europa",
            "uefa.europa.conf",
            "conmebol.libertadores",
            "conmebol.sudamericana",
            "ned.1",
            "por.1",
            "bel.1",
            "tur.1",
            "sco.1",
            "sui.1",
            "aut.1",
            "gre.1",
            "bra.1",
            "arg.1",
            "usa.1",
            "mex.1",
            "ksa.1",
            "jpn.1",
            "aus.1",
            "ind.1",
            "eng.fa",
            "esp.copa_del_rey",
            "ita.coppa_italia",
            "ger.dfb_pokal",
            "fra.coupe_de_france"
        )
    }
}

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
    val batsmen: List<Pair<String, String>>, // Name to "Runs (Balls)"
    val bowlers: List<Pair<String, String>>, // Name to "Wkts-Runs (Overs)"
    val recentBalls: List<String>
)

data class FootballMatchEvent(
    val minute: String,
    val eventType: String, // "GOAL", "YELLOW_CARD", "RED_CARD", "SUB"
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
    val possession: Pair<Int, Int>, // e.g. 58% vs 42%
    val shots: Pair<Int, Int>,
    val events: List<FootballMatchEvent>
)
