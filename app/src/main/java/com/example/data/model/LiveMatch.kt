package com.example.data.model

import java.util.UUID

enum class MatchStatus {
    LIVE,
    UPCOMING,
    FINISHED
}

enum class SportType(val displayName: String, val badgeCount: Int) {
    ALL("All", 62),
    CRICKET("Cricket", 9),
    FOOTBALL("Football", 31),
    BOXING("Boxing", 5),
    BASKETBALL("Basketball", 3),
    RACING("Racing", 2),
    HOCKEY("Hockey", 11),
    TENNIS("Tennis", 1)
}

data class LiveMatch(
    val id: String = UUID.randomUUID().toString(),
    val tournament: String, // e.g. "CRICKET | ODI TRI-SERIES IN PAKISTAN"
    val sport: SportType = SportType.FOOTBALL,
    val team1Name: String,
    val team1Code: String,
    val team1FlagUrl: String = "",
    val team2Name: String,
    val team2Code: String,
    val team2FlagUrl: String = "",
    val status: MatchStatus = MatchStatus.LIVE,
    val timeOrScore: String, // e.g., "06:25:22" or "Starts in 4 hours" or "302/9 vs 156/3"
    val matchTime: String = "Today",
    val streams: List<StreamSource> = emptyList(),
    val isFavorite: Boolean = false
)
