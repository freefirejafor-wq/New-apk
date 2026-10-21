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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LiveMatch
import com.example.data.model.MatchStatus
import com.example.data.model.SportType
import com.example.ui.components.MatchCard
import com.example.ui.theme.*

@Composable
fun LiveEventsScreen(
    matches: List<LiveMatch>,
    selectedSport: SportType,
    onSportSelect: (SportType) -> Unit,
    selectedStatus: MatchStatus?,
    onStatusSelect: (MatchStatus?) -> Unit,
    onMatchClick: (LiveMatch) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    onStreamSelectClick: (LiveMatch) -> Unit,
    searchQuery: String = ""
) {
    // Filter matches
    val filteredMatches = remember(matches, selectedSport, selectedStatus, searchQuery) {
        matches.filter { match ->
            val matchesSport = selectedSport == SportType.ALL || match.sport == selectedSport
            val matchesStatus = selectedStatus == null || match.status == selectedStatus
            val matchesSearch = if (searchQuery.isBlank()) true else {
                match.team1Name.contains(searchQuery, ignoreCase = true) ||
                match.team2Name.contains(searchQuery, ignoreCase = true) ||
                match.team1Code.contains(searchQuery, ignoreCase = true) ||
                match.team2Code.contains(searchQuery, ignoreCase = true) ||
                match.tournament.contains(searchQuery, ignoreCase = true)
            }
            matchesSport && matchesStatus && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDark)
    ) {
        // Sports Icons Horizontal Carousel with Badges
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SportType.values().forEach { sport ->
                val isSelected = sport == selectedSport
                SportIconBadgeItem(
                    sport = sport,
                    isSelected = isSelected,
                    onClick = { onSportSelect(sport) }
                )
            }
        }

        // Status Filter Chips: All, Live, Upcoming, Finished
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val totalCount = matches.size
            val liveCount = matches.count { it.status == MatchStatus.LIVE }
            val upcomingCount = matches.count { it.status == MatchStatus.UPCOMING }
            val finishedCount = matches.count { it.status == MatchStatus.FINISHED }

            StatusFilterChip(
                label = "All ($totalCount)",
                isSelected = selectedStatus == null,
                icon = Icons.Default.Check,
                onClick = { onStatusSelect(null) }
            )
            StatusFilterChip(
                label = "Live ($liveCount)",
                isSelected = selectedStatus == MatchStatus.LIVE,
                onClick = { onStatusSelect(MatchStatus.LIVE) }
            )
            StatusFilterChip(
                label = "Upcoming ($upcomingCount)",
                isSelected = selectedStatus == MatchStatus.UPCOMING,
                onClick = { onStatusSelect(MatchStatus.UPCOMING) }
            )
            StatusFilterChip(
                label = "Finished ($finishedCount)",
                isSelected = selectedStatus == MatchStatus.FINISHED,
                onClick = { onStatusSelect(MatchStatus.FINISHED) }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Match Cards List
        if (filteredMatches.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SportsScore,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No matches found",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
            ) {
                items(filteredMatches, key = { it.id }) { match ->
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

@Composable
fun SportIconBadgeItem(
    sport: SportType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) CyanAccent else NavyBorder
    val containerBg = if (isSelected) NavySurfaceVariant else NavySurface

    val icon: ImageVector = when (sport) {
        SportType.ALL -> Icons.Default.AllInclusive
        SportType.CRICKET -> Icons.Default.SportsCricket
        SportType.FOOTBALL -> Icons.Default.SportsSoccer
        SportType.BOXING -> Icons.Default.SportsMma
        SportType.BASKETBALL -> Icons.Default.SportsBasketball
        SportType.RACING -> Icons.Default.DirectionsCar
        SportType.HOCKEY -> Icons.Default.SportsHockey
        SportType.TENNIS -> Icons.Default.SportsTennis
    }

    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerBg)
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("sport_tab_${sport.name.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = sport.displayName,
                tint = if (isSelected) CyanAccent else TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = sport.displayName,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) CyanAccent else TextSecondary
                )
            )
        }

        // Notification badge count at top right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-2).dp, y = (2).dp)
                .clip(CircleShape)
                .background(LiveRed)
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                text = sport.badgeCount.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
fun StatusFilterChip(
    label: String,
    isSelected: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) NavySurfaceVariant else NavySurface
    val borderColor = if (isSelected) CyanAccent else NavyBorder
    val textColor = if (isSelected) CyanAccent else TextSecondary

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("chip_${label.lowercase()}"),
        color = containerColor,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
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
