package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.crex.CrexCricketService
import com.example.data.crex.CrexDashboardState
import com.example.data.model.LiveMatch
import com.example.data.model.MatchDateFilter
import com.example.data.model.MatchStatus
import com.example.data.model.SportType
import com.example.ui.components.MatchCard
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LiveEventsScreen(
    matches: List<LiveMatch>,
    crexState: CrexDashboardState = CrexDashboardState(),
    selectedSport: SportType,
    onSportSelect: (SportType) -> Unit,
    selectedStatus: MatchStatus?,
    onStatusSelect: (MatchStatus?) -> Unit,
    selectedDateFilter: MatchDateFilter? = null,
    onDateFilterSelect: ((MatchDateFilter) -> Unit)? = null,
    onManualRefresh: () -> Unit = {},
    onMatchClick: (LiveMatch) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    onReminderToggle: (String) -> Unit = {},
    onStreamSelectClick: (LiveMatch) -> Unit,
    searchQuery: String = ""
) {
    // Automatically keep Yesterday, Today, and Tomorrow matches in Asia/Dhaka (no extra date buttons in UI)
    val dateFilteredMatches = remember(matches) {
        val nowMs = System.currentTimeMillis()
        matches.filter { match ->
            CrexCricketService.isWithinDhakaThreeDayWindow(match, nowMs)
        }
    }

    val totalCount = dateFilteredMatches.size
    val cricketCount = remember(dateFilteredMatches) { dateFilteredMatches.count { it.sport == SportType.CRICKET } }
    val footballCount = remember(dateFilteredMatches) { dateFilteredMatches.count { it.sport == SportType.FOOTBALL } }
    val boxingCount = remember(dateFilteredMatches) { dateFilteredMatches.count { it.sport == SportType.BOXING } }
    val basketballCount = remember(dateFilteredMatches) { dateFilteredMatches.count { it.sport == SportType.BASKETBALL } }
    val racingCount = remember(dateFilteredMatches) { dateFilteredMatches.count { it.sport == SportType.RACING } }
    val hockeyCount = remember(dateFilteredMatches) { dateFilteredMatches.count { it.sport == SportType.HOCKEY } }

    val sportFilteredMatches = remember(dateFilteredMatches, selectedSport, searchQuery) {
        dateFilteredMatches.filter { match ->
            val sportMatches = when (selectedSport) {
                SportType.ALL -> true
                else -> match.sport == selectedSport
            }
            val queryMatches = if (searchQuery.isBlank()) true else {
                match.team1Name.contains(searchQuery, ignoreCase = true) ||
                    match.team2Name.contains(searchQuery, ignoreCase = true) ||
                    match.team1Code.contains(searchQuery, ignoreCase = true) ||
                    match.team2Code.contains(searchQuery, ignoreCase = true) ||
                    match.tournament.contains(searchQuery, ignoreCase = true)
            }
            sportMatches && queryMatches
        }
    }

    val allStatusCount = sportFilteredMatches.size
    val liveStatusCount = remember(sportFilteredMatches) {
        sportFilteredMatches.count { it.isConfirmedLive }
    }
    val upcomingStatusCount = remember(sportFilteredMatches) {
        sportFilteredMatches.count {
            it.status == MatchStatus.UPCOMING && !it.isPostponedOrCancelled && !it.isStatusUnknown
        }
    }
    val finishedStatusCount = remember(sportFilteredMatches) {
        sportFilteredMatches.count {
            it.status == MatchStatus.FINISHED && !it.isPostponedOrCancelled && !it.isStatusUnknown
        }
    }

    val filteredMatches = remember(sportFilteredMatches, selectedStatus) {
        when (selectedStatus) {
            null -> sportFilteredMatches
            MatchStatus.LIVE -> sportFilteredMatches.filter { it.isConfirmedLive }
            MatchStatus.UPCOMING -> sportFilteredMatches.filter {
                it.status == MatchStatus.UPCOMING && !it.isPostponedOrCancelled && !it.isStatusUnknown
            }
            MatchStatus.FINISHED -> sportFilteredMatches.filter {
                it.status == MatchStatus.FINISHED && !it.isPostponedOrCancelled && !it.isStatusUnknown
            }
        }
    }

    val isProviderLoading = crexState.liveState.isLoading ||
        crexState.upcomingState.isLoading ||
        crexState.finishedState.isLoading ||
        crexState.footballState.isLoading

    val hasProviderError = when (selectedSport) {
        SportType.CRICKET -> crexState.liveState.errorMessage != null &&
            crexState.upcomingState.errorMessage != null
        SportType.FOOTBALL -> crexState.footballState.errorMessage != null
        else -> (crexState.liveState.errorMessage != null && crexState.footballState.errorMessage != null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1520))
    ) {
        // 1. Top Sport Selector Row (All, Cricket, Football, Boxing, Basketball, Motorsport, Hockey)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SportIconTopCard(
                sportType = SportType.ALL,
                badgeCount = totalCount,
                isSelected = selectedSport == SportType.ALL,
                showTextLabel = true,
                label = "All",
                onClick = { onSportSelect(SportType.ALL) }
            )

            SportIconTopCard(
                sportType = SportType.CRICKET,
                badgeCount = cricketCount,
                isSelected = selectedSport == SportType.CRICKET,
                showTextLabel = false,
                label = "Cricket",
                onClick = {
                    onSportSelect(if (selectedSport == SportType.CRICKET) SportType.ALL else SportType.CRICKET)
                }
            )

            SportIconTopCard(
                sportType = SportType.FOOTBALL,
                badgeCount = footballCount,
                isSelected = selectedSport == SportType.FOOTBALL,
                showTextLabel = false,
                label = "Football",
                onClick = {
                    onSportSelect(if (selectedSport == SportType.FOOTBALL) SportType.ALL else SportType.FOOTBALL)
                }
            )

            SportIconTopCard(
                sportType = SportType.BOXING,
                badgeCount = boxingCount,
                isSelected = selectedSport == SportType.BOXING,
                showTextLabel = false,
                label = "Boxing",
                onClick = {
                    onSportSelect(if (selectedSport == SportType.BOXING) SportType.ALL else SportType.BOXING)
                }
            )

            SportIconTopCard(
                sportType = SportType.BASKETBALL,
                badgeCount = basketballCount,
                isSelected = selectedSport == SportType.BASKETBALL,
                showTextLabel = false,
                label = "Basketball",
                onClick = {
                    onSportSelect(if (selectedSport == SportType.BASKETBALL) SportType.ALL else SportType.BASKETBALL)
                }
            )

            SportIconTopCard(
                sportType = SportType.RACING,
                badgeCount = racingCount,
                isSelected = selectedSport == SportType.RACING,
                showTextLabel = false,
                label = "Motorsport",
                onClick = {
                    onSportSelect(if (selectedSport == SportType.RACING) SportType.ALL else SportType.RACING)
                }
            )

            if (hockeyCount > 0) {
                SportIconTopCard(
                    sportType = SportType.HOCKEY,
                    badgeCount = hockeyCount,
                    isSelected = selectedSport == SportType.HOCKEY,
                    showTextLabel = false,
                    label = "Hockey",
                    onClick = {
                        onSportSelect(if (selectedSport == SportType.HOCKEY) SportType.ALL else SportType.HOCKEY)
                    }
                )
            }
        }

        // 2. Status Filter Pills Row: All, Live, Upcoming, Finished
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusFilterPill(
                label = "All ($allStatusCount)",
                isSelected = selectedStatus == null,
                onClick = { onStatusSelect(null) },
                tag = "filter_all"
            )
            StatusFilterPill(
                label = "Live ($liveStatusCount)",
                isSelected = selectedStatus == MatchStatus.LIVE,
                onClick = {
                    onStatusSelect(if (selectedStatus == MatchStatus.LIVE) null else MatchStatus.LIVE)
                },
                tag = "filter_live"
            )
            StatusFilterPill(
                label = "Upcoming ($upcomingStatusCount)",
                isSelected = selectedStatus == MatchStatus.UPCOMING,
                onClick = {
                    onStatusSelect(if (selectedStatus == MatchStatus.UPCOMING) null else MatchStatus.UPCOMING)
                },
                tag = "filter_upcoming"
            )
            StatusFilterPill(
                label = "Finished ($finishedStatusCount)",
                isSelected = selectedStatus == MatchStatus.FINISHED,
                onClick = {
                    onStatusSelect(if (selectedStatus == MatchStatus.FINISHED) null else MatchStatus.FINISHED)
                },
                tag = "filter_finished"
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 3. Match Cards List (with Loading, Empty, and Error States)
        if (filteredMatches.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isProviderLoading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color(0xFF00D1C1),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Loading real-time matches...",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                } else if (hasProviderError) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = LiveRed,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Match feed temporarily unavailable",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Please check your connection or try syncing again.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(onClick = onManualRefresh) {
                            Text("Retry Sync", color = CyanAccent)
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No Matches Found",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "No active fixtures for the selected filter.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(onClick = onManualRefresh) {
                            Text("Sync Live API Matches", color = CyanAccent)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(top = 2.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = filteredMatches,
                    key = { "${it.status}_${it.id}" }
                ) { match ->
                    MatchCard(
                        match = match,
                        onClick = { onMatchClick(match) },
                        onFavoriteToggle = { onFavoriteToggle(match.id) },
                        onReminderToggle = { onReminderToggle(match.id) },
                        onStreamSelectClick = { onStreamSelectClick(match) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SportIconTopCard(
    sportType: SportType,
    badgeCount: Int,
    isSelected: Boolean,
    showTextLabel: Boolean,
    label: String,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF00D1C1) else Color(0xFF263449)
    val borderWidth = if (isSelected) 1.5.dp else 1.3.dp

    Box(
        modifier = Modifier
            .height(64.dp)
            .width(if (showTextLabel) 104.dp else 72.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0xFF131C2B))
            .border(borderWidth, borderColor, RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("sport_chip_${label.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                when (sportType) {
                    SportType.ALL -> AllSportOutlineIcon()
                    SportType.CRICKET -> CricketOutlineIcon()
                    SportType.FOOTBALL -> FootballOutlineIcon()
                    SportType.BOXING -> BoxingOutlineIcon()
                    SportType.BASKETBALL -> BasketballOutlineIcon()
                    SportType.RACING -> RacingCarOutlineIcon()
                    SportType.HOCKEY -> HockeyOutlineIcon()
                    else -> AllSportOutlineIcon()
                }

                // Red-orange circular/pill count badge with white border at top-left
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-1).dp, y = (-1).dp)
                        .defaultMinSize(minWidth = 20.dp, minHeight = 19.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFF04438))
                        .border(1.2.dp, Color.White, RoundedCornerShape(50))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.5.sp
                        ),
                        maxLines = 1
                    )
                }
            }

            if (showTextLabel) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StatusFilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    val borderColor = if (isSelected) Color(0xFF00D1C1) else Color(0xFF263449)
    val bgColor = if (isSelected) Color(0xFF132A38) else Color(0xFF131C2B)

    Box(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .border(
                width = if (isSelected) 1.4.dp else 1.2.dp,
                color = borderColor,
                shape = RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color = Color.White,
                    fontSize = 14.5.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AllSportOutlineIcon() {
    Box(
        modifier = Modifier.size(44.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            val r = size.minDimension * 0.43f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = Color.White,
                radius = r,
                center = c,
                style = stroke
            )
            drawCircle(
                color = Color.White,
                radius = r * 0.75f,
                center = c,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
        Text(
            text = "ALL",
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 8.5.sp
            )
        )
    }
}

@Composable
private fun CricketOutlineIcon() {
    Canvas(modifier = Modifier.size(44.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()

        // Three angled vertical stumps
        drawLine(Color.White, Offset(w * 0.28f, h * 0.30f), Offset(w * 0.34f, h * 0.88f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.38f, h * 0.26f), Offset(w * 0.43f, h * 0.88f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.48f, h * 0.24f), Offset(w * 0.52f, h * 0.88f), strokeWidth = stroke, cap = StrokeCap.Round)

        // Bails on top
        drawLine(Color.White, Offset(w * 0.26f, h * 0.27f), Offset(w * 0.52f, h * 0.21f), strokeWidth = stroke, cap = StrokeCap.Round)

        // Diagonal Cricket Bat on right
        rotate(degrees = 20f, pivot = Offset(w * 0.66f, h * 0.55f)) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.58f, h * 0.32f),
                size = Size(w * 0.14f, h * 0.50f),
                cornerRadius = CornerRadius(3f, 3f),
                style = Stroke(width = stroke)
            )
            drawLine(
                color = Color.White,
                start = Offset(w * 0.65f, h * 0.12f),
                end = Offset(w * 0.65f, h * 0.32f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun FootballOutlineIcon() {
    Canvas(modifier = Modifier.size(44.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()
        val c = Offset(w * 0.56f, h * 0.44f)
        val r = w * 0.32f

        // Soccer ball outline
        drawCircle(color = Color.White, radius = r, center = c, style = Stroke(width = stroke))

        // Inner pentagon
        val pentPath = Path()
        val pr = r * 0.45f
        for (i in 0 until 5) {
            val a = Math.toRadians(-90.0 + i * 72.0)
            val px = c.x + (pr * cos(a)).toFloat()
            val py = c.y + (pr * sin(a)).toFloat()
            if (i == 0) pentPath.moveTo(px, py) else pentPath.lineTo(px, py)
            val ox = c.x + (r * cos(a)).toFloat()
            val oy = c.y + (r * sin(a)).toFloat()
            drawLine(Color.White, Offset(px, py), Offset(ox, oy), strokeWidth = stroke * 0.85f)
        }
        pentPath.close()
        drawPath(pentPath, color = Color.White, style = Stroke(width = stroke * 0.9f))

        // Speed motion lines at bottom-left
        drawLine(Color.White, Offset(w * 0.14f, h * 0.86f), Offset(w * 0.34f, h * 0.68f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.24f, h * 0.92f), Offset(w * 0.44f, h * 0.74f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.12f, h * 0.72f), Offset(w * 0.26f, h * 0.58f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun BoxingOutlineIcon() {
    Canvas(modifier = Modifier.size(44.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()

        // Left boxing glove
        rotate(degrees = -18f, pivot = Offset(w * 0.38f, h * 0.52f)) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.18f, h * 0.26f),
                size = Size(w * 0.34f, h * 0.36f),
                cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
                style = Stroke(width = stroke)
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.22f, h * 0.62f),
                size = Size(w * 0.26f, h * 0.16f),
                cornerRadius = CornerRadius(4f, 4f),
                style = Stroke(width = stroke)
            )
        }

        // Right boxing glove
        rotate(degrees = 18f, pivot = Offset(w * 0.64f, h * 0.52f)) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.48f, h * 0.26f),
                size = Size(w * 0.34f, h * 0.36f),
                cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
                style = Stroke(width = stroke)
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.52f, h * 0.62f),
                size = Size(w * 0.26f, h * 0.16f),
                cornerRadius = CornerRadius(4f, 4f),
                style = Stroke(width = stroke)
            )
        }
    }
}

@Composable
private fun BasketballOutlineIcon() {
    Canvas(modifier = Modifier.size(44.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()

        // Backboard rectangle
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.16f, h * 0.26f),
            size = Size(w * 0.62f, h * 0.42f),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = stroke)
        )
        // Inner backboard target box
        drawRect(
            color = Color.White,
            topLeft = Offset(w * 0.34f, h * 0.42f),
            size = Size(w * 0.26f, h * 0.20f),
            style = Stroke(width = stroke * 0.85f)
        )
        // Basketball at top-right overlapping backboard
        val ballCenter = Offset(w * 0.74f, h * 0.28f)
        val ballRadius = w * 0.17f
        drawCircle(color = Color(0xFF131C2B), radius = ballRadius, center = ballCenter)
        drawCircle(color = Color.White, radius = ballRadius, center = ballCenter, style = Stroke(width = stroke))
        drawLine(Color.White, Offset(ballCenter.x - ballRadius, ballCenter.y), Offset(ballCenter.x + ballRadius, ballCenter.y), strokeWidth = stroke * 0.8f)
        drawLine(Color.White, Offset(ballCenter.x, ballCenter.y - ballRadius), Offset(ballCenter.x, ballCenter.y + ballRadius), strokeWidth = stroke * 0.8f)

        // Hoop & Net below backboard
        val netPath = Path().apply {
            moveTo(w * 0.32f, h * 0.68f)
            lineTo(w * 0.62f, h * 0.68f)
            lineTo(w * 0.56f, h * 0.88f)
            lineTo(w * 0.38f, h * 0.88f)
            close()
        }
        drawPath(netPath, color = Color.White, style = Stroke(width = stroke * 0.9f))
        drawLine(Color.White, Offset(w * 0.38f, h * 0.68f), Offset(w * 0.48f, h * 0.88f), strokeWidth = stroke * 0.7f)
        drawLine(Color.White, Offset(w * 0.56f, h * 0.68f), Offset(w * 0.44f, h * 0.88f), strokeWidth = stroke * 0.7f)
    }
}

@Composable
private fun RacingCarOutlineIcon() {
    Canvas(modifier = Modifier.size(44.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()

        // Race car chassis body
        val bodyPath = Path().apply {
            moveTo(w * 0.10f, h * 0.64f)
            lineTo(w * 0.16f, h * 0.44f)
            lineTo(w * 0.38f, h * 0.44f)
            lineTo(w * 0.52f, h * 0.34f)
            lineTo(w * 0.72f, h * 0.46f)
            lineTo(w * 0.90f, h * 0.52f)
            lineTo(w * 0.92f, h * 0.64f)
            close()
        }
        drawPath(bodyPath, color = Color.White, style = Stroke(width = stroke))

        // Left & right wheels
        drawCircle(color = Color(0xFF131C2B), radius = w * 0.12f, center = Offset(w * 0.26f, h * 0.66f))
        drawCircle(color = Color.White, radius = w * 0.12f, center = Offset(w * 0.26f, h * 0.66f), style = Stroke(width = stroke))
        drawCircle(color = Color(0xFF131C2B), radius = w * 0.12f, center = Offset(w * 0.74f, h * 0.66f))
        drawCircle(color = Color.White, radius = w * 0.12f, center = Offset(w * 0.74f, h * 0.66f), style = Stroke(width = stroke))
    }
}

@Composable
private fun HockeyOutlineIcon() {
    Canvas(modifier = Modifier.size(44.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()

        // Crossed hockey sticks
        drawLine(Color.White, Offset(w * 0.22f, h * 0.22f), Offset(w * 0.74f, h * 0.78f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.74f, h * 0.78f), Offset(w * 0.86f, h * 0.74f), strokeWidth = stroke * 1.2f, cap = StrokeCap.Round)

        drawLine(Color.White, Offset(w * 0.78f, h * 0.22f), Offset(w * 0.26f, h * 0.78f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.26f, h * 0.78f), Offset(w * 0.14f, h * 0.74f), strokeWidth = stroke * 1.2f, cap = StrokeCap.Round)

        // Hockey puck at bottom center
        drawOval(
            color = Color.White,
            topLeft = Offset(w * 0.38f, h * 0.78f),
            size = Size(w * 0.24f, h * 0.10f),
            style = Stroke(width = stroke)
        )
    }
}
