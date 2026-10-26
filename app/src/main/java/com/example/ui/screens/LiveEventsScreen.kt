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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import com.example.data.crex.CrexDashboardState
import com.example.data.model.LiveMatch
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
    onManualRefresh: () -> Unit = {},
    onMatchClick: (LiveMatch) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    onStreamSelectClick: (LiveMatch) -> Unit,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {}
) {
    var showSearchBar by remember { mutableStateOf(false) }

    val totalCount = matches.size
    val cricketCount = remember(matches) { matches.count { it.sport == SportType.CRICKET } }
    val footballCount = remember(matches) { matches.count { it.sport == SportType.FOOTBALL } }
    val boxingCount = remember(matches) { matches.count { it.sport == SportType.BOXING } }
    val racingCount = remember(matches) { matches.count { it.sport == SportType.RACING } }

    val sportFilteredMatches = remember(matches, selectedSport, searchQuery) {
        matches.filter { match ->
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
        sportFilteredMatches.count { it.status == MatchStatus.LIVE }
    }
    val upcomingStatusCount = remember(sportFilteredMatches) {
        sportFilteredMatches.count { it.status == MatchStatus.UPCOMING || it.status == MatchStatus.UNKNOWN }
    }
    val finishedStatusCount = remember(sportFilteredMatches) {
        sportFilteredMatches.count { it.status == MatchStatus.FINISHED }
    }

    val filteredMatches = remember(sportFilteredMatches, selectedStatus) {
        when (selectedStatus) {
            null -> sportFilteredMatches
            MatchStatus.UPCOMING -> sportFilteredMatches.filter {
                it.status == MatchStatus.UPCOMING || it.status == MatchStatus.UNKNOWN
            }
            else -> sportFilteredMatches.filter { it.status == selectedStatus }
        }
    }

    val isCrexLoading = crexState.liveState.isLoading ||
        crexState.upcomingState.isLoading ||
        crexState.finishedState.isLoading

    val isServingStaleCache = remember(filteredMatches, crexState) {
        filteredMatches.any { it.isStale } ||
            crexState.liveState.isStale ||
            crexState.upcomingState.isStale ||
            crexState.finishedState.isStale
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B111E))
    ) {
        // 1. Top Sport Selector Row (All, Cricket, Football, Boxing, Motorsport) right below TopSportsBar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
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

            if (boxingCount > 0) {
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
            }

            if (racingCount > 0) {
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
            }
        }

        // 2. Status Filter Pills Row: All, Live, Upcoming, Finished (no extra date tabs)
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

        if (isServingStaleCache) {
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "সর্বশেষ ক্যাশ করা ম্যাচ তালিকা দেখানো হচ্ছে (Showing cached feed)",
                        style = MaterialTheme.typography.labelSmall.copy(color = GoldYellow),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onManualRefresh,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Retry", color = CyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3. Match Cards List
        if (filteredMatches.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCrexLoading) {
                    CircularProgressIndicator(
                        color = Color(0xFF00D1C1),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(38.dp)
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No Matches Found (Yesterday, Today, Tomorrow)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onManualRefresh,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Sync Matches Now", color = CyanAccent)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                contentPadding = PaddingValues(top = 2.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = filteredMatches,
                    key = { "${it.sport.name}_${it.status}_${it.id}" }
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
internal fun SportIconTopCard(
    sportType: SportType,
    badgeCount: Int,
    isSelected: Boolean,
    showTextLabel: Boolean,
    label: String,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF00D1C1) else Color(0xFF223147)
    val borderWidth = if (isSelected) 1.6.dp else 1.2.dp

    Box(
        modifier = Modifier
            .height(68.dp)
            .width(if (showTextLabel) 114.dp else 70.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0E1624))
            .border(borderWidth, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
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
                    SportType.RACING -> RacingCarOutlineIcon()
                    else -> AllSportOutlineIcon()
                }

                // Red circular count badge with white border at top-left
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-2).dp, y = (-2).dp)
                        .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935))
                        .border(1.2.dp, Color.White, CircleShape)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        ),
                        maxLines = 1
                    )
                }
            }

            if (showTextLabel) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun StatusFilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    val borderColor = if (isSelected) Color(0xFF00D1C1) else Color(0xFF233246)
    val bgColor = if (isSelected) Color(0xFF152336) else Color(0xFF121B2A)

    Box(
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .border(
                width = if (isSelected) 1.5.dp else 1.2.dp,
                color = borderColor,
                shape = RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp)
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
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color = Color.White,
                    fontSize = 15.sp
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
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            val r = size.minDimension * 0.42f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = Color.White,
                radius = r,
                center = c,
                style = stroke
            )
            drawCircle(
                color = Color.White,
                radius = r * 0.76f,
                center = c,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
        Text(
            text = "ALL",
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            )
        )
    }
}

@Composable
private fun CricketOutlineIcon() {
    Canvas(modifier = Modifier.size(44.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()

        // Three vertical stumps
        drawLine(Color.White, Offset(w * 0.30f, h * 0.28f), Offset(w * 0.34f, h * 0.88f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.42f, h * 0.25f), Offset(w * 0.44f, h * 0.88f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.54f, h * 0.24f), Offset(w * 0.54f, h * 0.88f), strokeWidth = stroke, cap = StrokeCap.Round)

        // Bails on top
        drawLine(Color.White, Offset(w * 0.28f, h * 0.26f), Offset(w * 0.56f, h * 0.22f), strokeWidth = stroke, cap = StrokeCap.Round)

        // Diagonal Cricket Bat on right
        rotate(degrees = 25f, pivot = Offset(w * 0.68f, h * 0.55f)) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.60f, h * 0.32f),
                size = Size(w * 0.16f, h * 0.50f),
                cornerRadius = CornerRadius(4f, 4f),
                style = Stroke(width = stroke)
            )
            drawLine(
                color = Color.White,
                start = Offset(w * 0.68f, h * 0.12f),
                end = Offset(w * 0.68f, h * 0.32f),
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
        val stroke = 1.8.dp.toPx()
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
        val stroke = 1.8.dp.toPx()

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
private fun RacingCarOutlineIcon() {
    Canvas(modifier = Modifier.size(44.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()

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
        drawCircle(color = Color(0xFF0E1624), radius = w * 0.12f, center = Offset(w * 0.26f, h * 0.66f))
        drawCircle(color = Color.White, radius = w * 0.12f, center = Offset(w * 0.26f, h * 0.66f), style = Stroke(width = stroke))
        drawCircle(color = Color(0xFF0E1624), radius = w * 0.12f, center = Offset(w * 0.74f, h * 0.66f))
        drawCircle(color = Color.White, radius = w * 0.12f, center = Offset(w * 0.74f, h * 0.66f), style = Stroke(width = stroke))
    }
}
