package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.crex.CrexDashboardState
import com.example.data.model.LiveMatch
import com.example.data.model.MatchStatus
import com.example.data.model.SportType
import com.example.ui.components.MatchCard
import com.example.ui.components.PremiumCricketEmblem
import com.example.ui.components.PremiumFootballEmblem
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
    // Only Cricket and Football are shown in the sport line
    val supportedMatches = remember(matches) {
        matches.filter { it.sport == SportType.CRICKET || it.sport == SportType.FOOTBALL }
    }

    val cricketCount = remember(supportedMatches) {
        supportedMatches.count { it.sport == SportType.CRICKET }
    }
    val footballCount = remember(supportedMatches) {
        supportedMatches.count { it.sport == SportType.FOOTBALL }
    }

    // Filter matches strictly by Sport (Cricket / Football / All), Status, and Search Query
    val filteredMatches = remember(supportedMatches, selectedSport, selectedStatus, searchQuery) {
        supportedMatches.filter { match ->
            val sportMatches = when (selectedSport) {
                SportType.CRICKET -> match.sport == SportType.CRICKET
                SportType.FOOTBALL -> match.sport == SportType.FOOTBALL
                else -> true
            }
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

    val isCrexLoading = crexState.liveState.isLoading ||
        crexState.upcomingState.isLoading ||
        crexState.finishedState.isLoading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F192D),
                        NavyDark,
                        Color(0xFF0D1628)
                    )
                )
            )
    ) {
        // Top Sport Row: ONLY Cricket and Football — 2 balanced premium gradient cards side-by-side
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isCricketActive = selectedSport == SportType.CRICKET || selectedSport == SportType.ALL
            val isFootballActive = selectedSport == SportType.FOOTBALL || selectedSport == SportType.ALL

            PremiumSportSelectorCard(
                modifier = Modifier.weight(1f),
                label = "Cricket",
                subtitle = if (cricketCount > 0) "$cricketCount Matches" else "INTL • LIVE",
                sportType = SportType.CRICKET,
                isSelected = selectedSport == SportType.CRICKET,
                isIlluminated = isCricketActive,
                gradientColors = if (selectedSport == SportType.CRICKET) {
                    listOf(Color(0xFF059669), Color(0xFF0284C7))
                } else if (selectedSport == SportType.ALL) {
                    listOf(Color(0xFF0D4A4D), Color(0xFF0F3460))
                } else {
                    listOf(Color(0xFF132A3E), Color(0xFF112036))
                },
                borderColor = if (selectedSport == SportType.CRICKET) {
                    Color(0xFF34D399)
                } else if (selectedSport == SportType.ALL) {
                    CyanAccent.copy(alpha = 0.65f)
                } else {
                    NavyBorder
                },
                onClick = {
                    if (selectedSport == SportType.CRICKET) {
                        onSportSelect(SportType.ALL)
                    } else {
                        onSportSelect(SportType.CRICKET)
                    }
                }
            )

            PremiumSportSelectorCard(
                modifier = Modifier.weight(1f),
                label = "Football",
                subtitle = if (footballCount > 0) "$footballCount Matches" else "INTL • HD",
                sportType = SportType.FOOTBALL,
                isSelected = selectedSport == SportType.FOOTBALL,
                isIlluminated = isFootballActive,
                gradientColors = if (selectedSport == SportType.FOOTBALL) {
                    listOf(Color(0xFF2563EB), Color(0xFF06B6D4))
                } else if (selectedSport == SportType.ALL) {
                    listOf(Color(0xFF153462), Color(0xFF0F3460))
                } else {
                    listOf(Color(0xFF142642), Color(0xFF112036))
                },
                borderColor = if (selectedSport == SportType.FOOTBALL) {
                    Color(0xFF38BDF8)
                } else if (selectedSport == SportType.ALL) {
                    CyanAccent.copy(alpha = 0.65f)
                } else {
                    NavyBorder
                },
                onClick = {
                    if (selectedSport == SportType.FOOTBALL) {
                        onSportSelect(SportType.ALL)
                    } else {
                        onSportSelect(SportType.FOOTBALL)
                    }
                }
            )
        }

        // Neatly Organized Status Filter Row (All, Live, Upcoming, Finished) in a unified 4-tab bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            color = NavySurface,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, NavyBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusFilterSegment(
                    modifier = Modifier.weight(1f),
                    label = "All",
                    isSelected = selectedStatus == null,
                    activeBrush = Brush.horizontalGradient(
                        colors = listOf( Color(0xFF0284C7), Color(0xFF00B4D8))
                    ),
                    dotColor = null,
                    onClick = { onStatusSelect(null) },
                    tag = "filter_all"
                )
                StatusFilterSegment(
                    modifier = Modifier.weight(1f),
                    label = "Live",
                    isSelected = selectedStatus == MatchStatus.LIVE,
                    activeBrush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFFDC2626), Color(0xFFEF4444))
                    ),
                    dotColor = LiveRed,
                    onClick = {
                        onStatusSelect(if (selectedStatus == MatchStatus.LIVE) null else MatchStatus.LIVE)
                    },
                    tag = "filter_live"
                )
                StatusFilterSegment(
                    modifier = Modifier.weight(1f),
                    label = "Upcoming",
                    isSelected = selectedStatus == MatchStatus.UPCOMING,
                    activeBrush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF0284C7), Color(0xFF38BDF8))
                    ),
                    dotColor = null,
                    onClick = {
                        onStatusSelect(if (selectedStatus == MatchStatus.UPCOMING) null else MatchStatus.UPCOMING)
                    },
                    tag = "filter_upcoming"
                )
                StatusFilterSegment(
                    modifier = Modifier.weight(1f),
                    label = "Finished",
                    isSelected = selectedStatus == MatchStatus.FINISHED,
                    activeBrush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF334155), Color(0xFF475569))
                    ),
                    dotColor = null,
                    onClick = {
                        onStatusSelect(if (selectedStatus == MatchStatus.FINISHED) null else MatchStatus.FINISHED)
                    },
                    tag = "filter_finished"
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredMatches.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCrexLoading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = CyanAccent,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Loading Live Match Updates...",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (selectedSport == SportType.CRICKET) {
                            PremiumCricketEmblem(size = 56.dp, isSelected = true)
                        } else {
                            PremiumFootballEmblem(size = 56.dp, isSelected = true)
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No Matches Found",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap All, Live, Upcoming, or Finished above to switch match filters.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            // Match list directly below Status Filter Row — no "LIVE NOW" text header
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 2.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = filteredMatches,
                    key = { "${it.status}_${it.id}" }
                ) { match ->
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
private fun PremiumSportSelectorCard(
    modifier: Modifier = Modifier,
    label: String,
    subtitle: String,
    sportType: SportType,
    isSelected: Boolean,
    isIlluminated: Boolean,
    gradientColors: List<Color>,
    borderColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(colors = gradientColors))
            .border(
                width = if (isSelected) 1.6.dp else 1.1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("sport_chip_${label.lowercase()}")
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (sportType == SportType.CRICKET) {
                    PremiumCricketEmblem(size = 38.dp, isSelected = isIlluminated)
                } else {
                    PremiumFootballEmblem(size = 38.dp, isSelected = isIlluminated)
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 15.sp,
                            letterSpacing = 0.2.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) Color(0xFFE0F2FE) else CyanAccent,
                            fontSize = 10.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Active status dot indicator
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Color.White
                        else if (isIlluminated) CyanAccent
                        else TextTertiary.copy(alpha = 0.5f)
                    )
            )
        }
    }
}

@Composable
private fun StatusFilterSegment(
    modifier: Modifier = Modifier,
    label: String,
    isSelected: Boolean,
    activeBrush: Brush,
    dotColor: Color?,
    onClick: () -> Unit,
    tag: String
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .then(
                if (isSelected) {
                    Modifier.background(activeBrush, shape)
                } else {
                    Modifier.background(Color.Transparent, shape)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color.White else dotColor)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (isSelected) Color.White else TextPrimary.copy(alpha = 0.85f),
                    fontSize = 12.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
