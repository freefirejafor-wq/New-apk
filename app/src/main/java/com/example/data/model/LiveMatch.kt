package com.example.data.model

import java.util.UUID

enum class MatchStatus {
    LIVE,
    UPCOMING,
    FINISHED
}

enum class MatchDateFilter(val labelBn: String, val dayOffsetFromTodayDhaka: Int?) {
    ALL_DAYS("সব দিন", null),
    YESTERDAY("গতকাল", -1),
    TODAY("আজ", 0),
    TOMORROW("আগামীকাল", 1)
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
    val tournament: String, // e.g. "CRICKET | ODI TRI-SERIES IN PAKISTAN"
    val sport: SportType = SportType.FOOTBALL,
    val team1Name: String,
    val team1Code: String,
    val team1FlagUrl: String = "",
    val team2Name: String,
    val team2Code: String,
    val team2FlagUrl: String = "",
    val team1Score: String = "",
    val team2Score: String = "",
    val status: MatchStatus = MatchStatus.UPCOMING,
    val timeOrScore: String, // e.g., "IND 184/4 (18.2 ov)" or "Starts in 2 hours"
    val matchTime: String = "",
    val startTimeEpochMs: Long = 0L,
    val streams: List<StreamSource> = emptyList(),
    val isFavorite: Boolean = false,
    val isReminderSet: Boolean = false,
    val providerStatusLabel: String = "", // e.g. "Live", "Upcoming", "Finished", "Unknown", "Postponed", "Cancelled"
    val isPostponedOrCancelled: Boolean = false,
    val isStaleCache: Boolean = false
) {
    val isStatusUnknown: Boolean
        get() = providerStatusLabel.equals("Unknown", ignoreCase = true) ||
            (isStaleCache && status == MatchStatus.LIVE)

    val isConfirmedLive: Boolean
        get() = status == MatchStatus.LIVE && !isPostponedOrCancelled && !isStatusUnknown
}
