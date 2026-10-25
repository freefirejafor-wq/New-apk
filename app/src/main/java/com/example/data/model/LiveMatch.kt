package com.example.data.model

import java.util.UUID

enum class MatchStatus {
    LIVE,
    UPCOMING,
    FINISHED,
    UNKNOWN
}

enum class SportType(val displayName: String, val badgeCount: Int) {
    ALL("All", 0),
    CRICKET("Cricket", 0),
    FOOTBALL("Football", 0),
    BOXING("Boxing", 0),
    BASKETBALL("Basketball", 0),
    RACING("Racing", 0),
    HOCKEY("Hockey", 0),
    TENNIS("Tennis", 0)
}

data class LiveMatch(
    val id: String = UUID.randomUUID().toString(),
    val tournament: String, // e.g. "FOOTBALL | ENGLISH PREMIER LEAGUE"
    val sport: SportType = SportType.FOOTBALL,
    val team1Name: String,
    val team1Code: String,
    val team1FlagUrl: String = "",
    val team2Name: String,
    val team2Code: String,
    val team2FlagUrl: String = "",
    val status: MatchStatus = MatchStatus.UPCOMING,
    val timeOrScore: String = "", // Internal status/countdown text; scores are NEVER rendered on MatchCard
    val matchTime: String = "",
    val streams: List<StreamSource> = emptyList(),
    val isFavorite: Boolean = false,
    val provider: String = "", // "ESPN" or "CREX" or "ADMIN"
    val competitionId: String = "", // e.g. "eng.1", "uefa.champions", or CREX series key
    val startTimeEpochMs: Long = 0L,
    val isNationalTeam: Boolean = true, // True for International Cricket & National-Team Football; False for Club Football
    val isStale: Boolean = false,
    val sofascoreEventId: String? = null,
    val broadcastChannels: List<String> = emptyList(),
    val approvedHighlightUrl: String? = null
) {
    /**
     * Returns only enabled and valid stream sources sorted by primary/default first,
     * then priorityOrder, with backup streams last.
     */
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
