package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.crex.CrexDashboardState
import com.example.data.model.LiveMatch
import com.example.data.model.MatchStatus
import com.example.data.model.SportType
import com.example.ui.components.MatchCard
import com.example.ui.theme.*

@Composable
fun LiveEventsScreen(
    matches: List<LiveMatch>,
    crexState: CrexDashboardState = CrexDashboardState(),
    selectedSport: SportType,
    onSportSelect: (SportType) -> Unit,
    selectedStatus: MatchStatus?,
    onStatusSelect: (MatchStatus?) -> Unit,
    onManualRefresh: () -> Unit = {},
    onMatchClick: (LiveMatch) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    onStreamSelectClick: (LiveMatch) -> Unit,
    searchQuery: String = ""
) {
    // Filter matches strictly by Sport, Status, and Search Query
    val filteredMatches = remember(matches, selectedSport, selectedStatus, searchQuery) {
        matches.filter { match ->
            val sportMatches = selectedSport == SportType.ALL || match.sport == selectedSport
            val statusMatches = selectedStatus == null || match.status == selectedStatus
            val queryMatches = if (searchQuery.isBlank()) true else {
                match.team1Name.contains(searchQuery, ignoreCase = true) ||
                    match.team2Name.contains(searchQuery, ignoreCase = true) ||
                    match.team1Code.contains(searchQuery, ignoreCase = true) ||
                    match.team2Code.contains(searchQuery, ignoreCase = true) ||
                    match.tournament.contains(searchQuery, ignoreCase = true)
            }
            sportMatches && statusMatches && queryMatches
        }
    }

    val liveMatchesList = remember(filteredMatches) {
        filteredMatches.filter { it.status == MatchStatus.LIVE }
    }
    val upcomingMatchesList = remember(filteredMatches) {
        filteredMatches.filter { it.status == MatchStatus.UPCOMING }
    }
    val finishedMatchesList = remember(filteredMatches) {
        filteredMatches.filter { it.status == MatchStatus.FINISHED }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDark)
    ) {
        // Sport Type Filter Row (All, Cricket, Football, etc. with icons)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SportType.values().forEach { sport ->
                val isSelected = sport == selectedSport
                val icon = when (sport) {
                    SportType.ALL -> Icons.Default.GridView
                    SportType.CRICKET -> Icons.Default.SportsCricket
                    SportType.FOOTBALL -> Icons.Default.SportsSoccer
                    SportType.BOXING -> Icons.Default.SportsMma
                    SportType.BASKETBALL -> Icons.Default.SportsBasketball
                    SportType.HOCKEY -> Icons.Default.SportsHockey
                    SportType.TENNIS -> Icons.Default.SportsTennis
                    else -> Icons.Default.SportsMotorsports
                }

                SportFilterChip(
                    label = sport.displayName,
                    icon = icon,
                    isSelected = isSelected,
                    onClick = { onSportSelect(sport) }
                )
            }
        }

        // Status Filter Row (All, Live, Upcoming, Finished)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusFilterChip(
                label = "All",
                isSelected = selectedStatus == null,
                accentColor = CyanAccent,
                onClick = { onStatusSelect(null) },
                tag = "filter_all"
            )
            StatusFilterChip(
                label = "Live",
                isSelected = selectedStatus == MatchStatus.LIVE,
                accentColor = LiveRed,
                onClick = { onStatusSelect(if (selectedStatus == MatchStatus.LIVE) null else MatchStatus.LIVE) },
                tag = "filter_live"
            )
            StatusFilterChip(
                label = "Upcoming",
                isSelected = selectedStatus == MatchStatus.UPCOMING,
                accentColor = SkyBlue,
                onClick = { onStatusSelect(if (selectedStatus == MatchStatus.UPCOMING) null else MatchStatus.UPCOMING) },
                tag = "filter_upcoming"
            )
            StatusFilterChip(
                label = "Finished",
                isSelected = selectedStatus == MatchStatus.FINISHED,
                accentColor = TextSecondary,
                onClick = { onStatusSelect(if (selectedStatus == MatchStatus.FINISHED) null else MatchStatus.FINISHED) },
                tag = "filter_finished"
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (filteredMatches.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SportsSoccer,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Matches Found",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Try selecting another sport or status filter.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextTertiary
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selectedStatus != null) {
                    item {
                        SectionBannerHeader(
                            title = when (selectedStatus) {
                                MatchStatus.LIVE -> "LIVE MATCHES (${filteredMatches.size})"
                                MatchStatus.UPCOMING -> "UPCOMING SCHEDULE (${filteredMatches.size})"
                                MatchStatus.FINISHED -> "FINISHED MATCHES (${filteredMatches.size})"
                            },
                            dotColor = when (selectedStatus) {
                                MatchStatus.LIVE -> LiveRed
                                MatchStatus.UPCOMING -> SkyBlue
                                MatchStatus.FINISHED -> TextSecondary
                            }
                        )
                    }
                    items(filteredMatches, key = { "${it.status}_${it.id}" }) { match ->
                        MatchCard(
                            match = match,
                            onClick = { onMatchClick(match) },
                            onFavoriteToggle = { onFavoriteToggle(match.id) },
                            onStreamSelectClick = { onStreamSelectClick(match) }
                        )
                    }
                } else {
                    if (liveMatchesList.isNotEmpty()) {
                        item {
                            SectionBannerHeader(
                                title = "LIVE NOW (${liveMatchesList.size})",
                                dotColor = LiveRed
                            )
                        }
                        items(liveMatchesList, key = { "live_${it.id}" }) { match ->
                            MatchCard(
                                match = match,
                                onClick = { onMatchClick(match) },
                                onFavoriteToggle = { onFavoriteToggle(match.id) },
                                onStreamSelectClick = { onStreamSelectClick(match) }
                            )
                        }
                    }

                    if (upcomingMatchesList.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            SectionBannerHeader(
                                title = "UPCOMING SCHEDULE (${upcomingMatchesList.size})",
                                dotColor = SkyBlue
                            )
                        }
                        items(upcomingMatchesList, key = { "up_${it.id}" }) { match ->
                            MatchCard(
                                match = match,
                                onClick = { onMatchClick(match) },
                                onFavoriteToggle = { onFavoriteToggle(match.id) },
                                onStreamSelectClick = { onStreamSelectClick(match) }
                            )
                        }
                    }

                    if (finishedMatchesList.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            SectionBannerHeader(
                                title = "FINISHED MATCHES (${finishedMatchesList.size})",
                                dotColor = TextSecondary
                            )
                        }
                        items(finishedMatchesList, key = { "fin_${it.id}" }) { match ->
                            MatchCard(
                                match = match,
                                onClick = { onMatchClick(match) },
                                onFavoriteToggle = { onFavoriteToggle(match.id) },
                                onStreamSelectClick = { onStreamSelectClick(match) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionBannerHeader(
    title: String,
    dotColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                fontSize = 12.sp,
                letterSpacing = 0.6.sp
            )
        )
    }
}

@Composable
private fun SportFilterChip(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) CyanAccent else NavySurface
    val contentColor = if (isSelected) Color.Black else TextSecondary
    val borderColor = if (isSelected) CyanAccent else NavyBorder

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("sport_chip_${label.lowercase()}"),
        color = bgColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor,
                    fontSize = 12.sp
                )
            )
        }
    }
}

@Composable
private fun StatusFilterChip(
    label: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    tag: String
) {
    val bgColor = if (isSelected) accentColor.copy(alpha = 0.18f) else NavySurface
    val borderColor = if (isSelected) accentColor else NavyBorder
    val textColor = if (isSelected) accentColor else TextSecondary

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag(tag),
        color = bgColor,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = textColor,
                    fontSize = 12.sp
                )
            )
        }
    }
}
