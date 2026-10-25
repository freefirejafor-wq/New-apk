package com.example.data.crex

import com.example.data.model.LiveMatch
import com.example.data.model.MatchStatus

/**
 * State container for each CREX match category (Live, Upcoming, Finished),
 * supporting loading, empty, stale-data (cached fallback on error), and API-error states.
 */
data class CrexCategoryFeedState(
    val category: MatchStatus,
    val matches: List<LiveMatch> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isStale: Boolean = false,
    val lastUpdatedEpochMs: Long? = null,
    val lastUpdatedDhakaText: String? = null,
    val backoffUntilEpochMs: Long = 0L
)

/**
 * Combined UI state for the CREX cricket integration across all categories.
 */
data class CrexDashboardState(
    val liveState: CrexCategoryFeedState = CrexCategoryFeedState(category = MatchStatus.LIVE, isLoading = true),
    val upcomingState: CrexCategoryFeedState = CrexCategoryFeedState(category = MatchStatus.UPCOMING, isLoading = true),
    val finishedState: CrexCategoryFeedState = CrexCategoryFeedState(category = MatchStatus.FINISHED, isLoading = false),
    val activeDetailState: CrexMatchDetailState = CrexMatchDetailState()
) {
    fun stateFor(status: MatchStatus?): CrexCategoryFeedState {
        return when (status) {
            MatchStatus.LIVE -> liveState
            MatchStatus.UPCOMING -> upcomingState
            MatchStatus.FINISHED -> finishedState
            MatchStatus.UNKNOWN, null -> {
                val combined = (liveState.matches + upcomingState.matches + finishedState.matches)
                    .distinctBy { it.id }
                val anyLoading = (liveState.isLoading || upcomingState.isLoading) && combined.isEmpty()
                val anyStale = liveState.isStale || upcomingState.isStale || finishedState.isStale
                val latestUpdate = listOfNotNull(
                    liveState.lastUpdatedEpochMs,
                    upcomingState.lastUpdatedEpochMs,
                    finishedState.lastUpdatedEpochMs
                ).maxOrNull()
                val latestText = listOfNotNull(
                    liveState.lastUpdatedDhakaText,
                    upcomingState.lastUpdatedDhakaText,
                    finishedState.lastUpdatedDhakaText
                ).firstOrNull()
                val err = liveState.errorMessage ?: upcomingState.errorMessage ?: finishedState.errorMessage
                CrexCategoryFeedState(
                    category = MatchStatus.LIVE,
                    matches = combined,
                    isLoading = anyLoading,
                    errorMessage = err,
                    isStale = anyStale,
                    lastUpdatedEpochMs = latestUpdate,
                    lastUpdatedDhakaText = latestText
                )
            }
        }
    }
}

/**
 * On-demand match detail state loaded from `GET /api/cricket/matches/{matchId}`
 * (`https://api.goscorer.com/api/v3/getSV3?key={matchId}`).
 */
data class CrexMatchDetailState(
    val matchId: String? = null,
    val isLoading: Boolean = false,
    val detail: CrexMatchDetail? = null,
    val errorMessage: String? = null,
    val isStale: Boolean = false,
    val lastUpdatedDhakaText: String? = null
)

/**
 * Normalized match details strictly extracted from `getSV3?key={matchId}` fields.
 */
data class CrexMatchDetail(
    val matchId: String,
    val team1Key: String,
    val team1Name: String,
    val team2Key: String,
    val team2Name: String,
    val format: String,
    val matchNumber: String,
    val seriesKey: String,
    val team1Score: String,
    val team2Score: String,
    val startTimeDhaka: String,
    val statusText: String,
    val recentOvers: List<String>,
    val commentaryLanguages: List<String>,
    val fetchedAtDhaka: String
)
