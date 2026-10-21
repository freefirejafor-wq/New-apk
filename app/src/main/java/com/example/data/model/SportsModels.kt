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
    val iconName: String = "sports"
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
    val appTitle: String = "Football Live Tv",
    val version: String = "7.3",
    val announcement: String = "Welcome to Football Live TV! Multiple high-definition stream servers are active for today's matches.",
    val telegramLink: String = "https://t.me/sports_live_streams",
    val adminPin: String = "1234",
    val forceLowQuality: Boolean = false,
    val isHardwareAccelerationEnabled: Boolean = true,
    val remoteFeedUrl: String = "https://raw.githubusercontent.com/freefirejafor-wq/Upder/main/matches.json",
    val autoSyncOnLaunch: Boolean = true
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
