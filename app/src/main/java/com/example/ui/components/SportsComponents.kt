package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.LiveMatch
import com.example.data.model.MatchStatus
import com.example.data.model.SportType
import com.example.data.model.SportsChannel
import com.example.data.model.StreamSource
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopSportsBar(
    title: String = "Sports Live",
    onTelegramClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSearchClick: () -> Unit,
    searchQuery: String = "",
    isSearching: Boolean = false,
    onSearchQueryChange: (String) -> Unit = {},
    onCloseSearch: () -> Unit = {}
) {
    Surface(
        color = NavyDark,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        if (isSearching) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_input"),
                    placeholder = { Text("Search matches, channels...", color = TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = NavyBorder,
                        focusedContainerColor = NavySurface,
                        unfocusedContainerColor = NavySurface
                    ),
                    trailingIcon = {
                        IconButton(onClick = onCloseSearch) {
                            Icon(Icons.Default.Close, contentDescription = "Close search", tint = TextSecondary)
                        }
                    }
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 22.sp
                    )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Telegram community button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(NavySurfaceVariant)
                            .clickable(onClick = onTelegramClick)
                            .testTag("telegram_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Telegram",
                            tint = CyanAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Refresh button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(NavySurfaceVariant)
                            .clickable(onClick = onRefreshClick)
                            .testTag("refresh_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Search button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(NavySurfaceVariant)
                            .clickable(onClick = onSearchClick)
                            .testTag("search_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

enum class NavTab(val label: String, val icon: ImageVector) {
    FAVOURITES("Favourites", Icons.Default.Star),
    SPORTS("Sports", Icons.Default.SportsSoccer),
    LIVE_EVENTS("Live Events", Icons.Default.Podcasts),
    CATEGORIES("Categories", Icons.Default.Tv),
    MENU("Menu", Icons.Default.FormatListBulleted)
}

@Composable
fun BottomSportsNavBar(
    selectedTab: NavTab,
    onTabSelected: (NavTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Surface(
            color = Color(0xFF1A2434),
            shape = RoundedCornerShape(38.dp),
            tonalElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .border(
                    width = 1.dp,
                    color = Color(0xFF28374E),
                    shape = RoundedCornerShape(38.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NavTab.values().forEach { tab ->
                    val isSelected = tab == selectedTab
                    val isCenterLiveTab = tab == NavTab.LIVE_EVENTS
                    val activeCyan = Color(0xFF00D1C1)
                    val inactiveGrey = Color(0xFF94A3B8)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(
                                if (isSelected) Color(0xFF233246)
                                else Color.Transparent
                            )
                            .clickable { onTabSelected(tab) }
                            .testTag("nav_tab_${tab.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isCenterLiveTab) Icons.Default.Podcasts else tab.icon,
                                contentDescription = tab.label,
                                tint = if (isSelected) activeCyan else inactiveGrey,
                                modifier = Modifier.size(if (isCenterLiveTab) 26.dp else 23.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) activeCyan else inactiveGrey,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumCricketEmblem(
    size: Dp = 36.dp,
    isSelected: Boolean = true
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = if (isSelected) {
                        listOf(Color(0xFF10B981).copy(alpha = 0.42f), Color(0xFF047857).copy(alpha = 0.25f))
                    } else {
                        listOf(Color(0xFF0EA5E9).copy(alpha = 0.28f), Color(0xFF1E293B).copy(alpha = 0.6f))
                    }
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF34D399), Color(0xFF38BDF8))
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(size * 0.16f)) {
            val w = this.size.width
            val h = this.size.height

            val stumpColor = Color(0xFFFDE68A).copy(alpha = 0.45f)
            val stumpStroke = w * 0.055f
            drawLine(
                color = stumpColor,
                start = Offset(w * 0.24f, h * 0.22f),
                end = Offset(w * 0.24f, h * 0.78f),
                strokeWidth = stumpStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = stumpColor,
                start = Offset(w * 0.36f, h * 0.20f),
                end = Offset(w * 0.36f, h * 0.78f),
                strokeWidth = stumpStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = stumpColor,
                start = Offset(w * 0.20f, h * 0.20f),
                end = Offset(w * 0.40f, h * 0.20f),
                strokeWidth = stumpStroke,
                cap = StrokeCap.Round
            )

            rotate(degrees = -35f, pivot = Offset(w * 0.5f, h * 0.5f)) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFF8FAFC), Color(0xFF93C5FD))
                    ),
                    topLeft = Offset(w * 0.455f, h * 0.06f),
                    size = Size(w * 0.09f, h * 0.28f),
                    cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
                )
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFDE047), Color(0xFFF59E0B), Color(0xFFD97706))
                    ),
                    topLeft = Offset(w * 0.39f, h * 0.31f),
                    size = Size(w * 0.22f, h * 0.58f),
                    cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
                )
            }

            val ballCenter = Offset(w * 0.67f, h * 0.66f)
            val ballRadius = w * 0.23f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF6B6B), Color(0xFFEF4444), Color(0xFF991B1B)),
                    center = Offset(ballCenter.x - ballRadius * 0.25f, ballCenter.y - ballRadius * 0.25f),
                    radius = ballRadius * 1.3f
                ),
                radius = ballRadius,
                center = ballCenter
            )
        }
    }
}

@Composable
fun PremiumFootballEmblem(
    size: Dp = 36.dp,
    isSelected: Boolean = true
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = if (isSelected) {
                        listOf(Color(0xFF38BDF8).copy(alpha = 0.42f), Color(0xFF1D4ED8).copy(alpha = 0.28f))
                    } else {
                        listOf(Color(0xFF38BDF8).copy(alpha = 0.25f), Color(0xFF1E293B).copy(alpha = 0.6f))
                    }
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF38BDF8), Color(0xFF818CF8))
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(size * 0.16f)) {
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w * 0.5f, h * 0.5f)
            val radius = w * 0.44f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFE0F2FE), Color(0xFF93C5FD)),
                    center = Offset(center.x - radius * 0.25f, center.y - radius * 0.25f),
                    radius = radius * 1.35f
                ),
                radius = radius,
                center = center
            )

            drawCircle(
                color = Color(0xFF0284C7),
                radius = radius,
                center = center,
                style = Stroke(width = w * 0.055f)
            )
        }
    }
}

@Composable
fun SportHeaderSquareBadge(sport: SportType, tournament: String) {
    val isMotoGp = sport == SportType.RACING || tournament.contains("MOTOGP", ignoreCase = true)
    if (isMotoGp) {
        Text(
            text = "MGP",
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 9.sp,
                letterSpacing = (-0.5).sp
            )
        )
    } else {
        Box(
            modifier = Modifier
                .size(19.dp)
                .background(Color.White, RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(13.dp)) {
                val w = size.width
                val h = size.height
                // Athlete silhouette mark inside white square badge
                drawCircle(
                    color = Color(0xFF0F172A),
                    radius = w * 0.14f,
                    center = Offset(w * 0.55f, h * 0.22f)
                )
                drawLine(
                    color = Color(0xFF0F172A),
                    start = Offset(w * 0.30f, h * 0.42f),
                    end = Offset(w * 0.78f, h * 0.28f),
                    strokeWidth = w * 0.13f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFF0F172A),
                    start = Offset(w * 0.52f, h * 0.35f),
                    end = Offset(w * 0.42f, h * 0.70f),
                    strokeWidth = w * 0.14f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFF0F172A),
                    start = Offset(w * 0.42f, h * 0.65f),
                    end = Offset(w * 0.24f, h * 0.90f),
                    strokeWidth = w * 0.12f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFF0F172A),
                    start = Offset(w * 0.48f, h * 0.62f),
                    end = Offset(w * 0.72f, h * 0.88f),
                    strokeWidth = w * 0.12f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun MatchCard(
    match: LiveMatch,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit = {},
    onStreamSelectClick: () -> Unit = {}
) {
    val team1Visual = remember(match.team1Name, match.team1Code, match.team1FlagUrl) {
        com.example.data.util.TeamFlagResolver.resolve(match.team1Name, match.team1Code, match.team1FlagUrl)
    }
    val team2Visual = remember(match.team2Name, match.team2Code, match.team2FlagUrl) {
        com.example.data.util.TeamFlagResolver.resolve(match.team2Name, match.team2Code, match.team2FlagUrl)
    }

    // Format top tournament title as "SPORT | TOURNAMENT" in uppercase
    val formattedHeader = remember(match.sport, match.tournament) {
        val raw = match.tournament.trim().uppercase()
        if (raw.contains("|")) {
            raw
        } else {
            val sportPrefix = when (match.sport) {
                SportType.CRICKET -> "CRICKET"
                SportType.FOOTBALL -> "FOOTBALL"
                SportType.BOXING -> "BOXING"
                SportType.RACING -> "MOTORSPORT"
                else -> match.sport.displayName.uppercase()
            }
            "$sportPrefix | ${raw.replace("•", "|")}"
        }
    }

    // Ticking live timer for LIVE matches (e.g. "01:56:40")
    val initialSeconds = remember(match.id, match.timeOrScore) {
        val parts = match.timeOrScore.trim().split(":")
        if (parts.size == 3) {
            val h = parts[0].toLongOrNull() ?: 1L
            val m = parts[1].toLongOrNull() ?: 30L
            val s = parts[2].toLongOrNull() ?: 40L
            h * 3600L + m * 60L + s
        } else {
            val hash = kotlin.math.abs(match.id.hashCode())
            val h = (hash % 2)
            val m = 25 + (hash % 34)
            val s = 10 + (hash % 49)
            h * 3600L + m * 60L + s
        }
    }

    var liveSeconds by remember(initialSeconds) { mutableStateOf(initialSeconds) }
    LaunchedEffect(match.status, initialSeconds) {
        if (match.status == MatchStatus.LIVE) {
            while (true) {
                kotlinx.coroutines.delay(1000L)
                liveSeconds += 1L
            }
        }
    }

    val liveTimerFormatted = remember(liveSeconds) {
        val hrs = liveSeconds / 3600L
        val mins = (liveSeconds % 3600L) / 60L
        val secs = liveSeconds % 60L
        String.format("%02d:%02d:%02d", hrs, mins, secs)
    }

    // Parse upcoming time & date (e.g. "07:40 PM" and "22/10/2026")
    val (upcomingTimeText, upcomingDateText) = remember(match.matchTime) {
        val parts = match.matchTime.split("|", "•", ",")
        val timePart = Regex("""\b\d{1,2}:\d{2}\s*(?:AM|PM)\b""", RegexOption.IGNORE_CASE)
            .find(match.matchTime)?.value?.uppercase()
            ?: parts.firstOrNull()?.trim()?.takeIf { it.contains(":") }
            ?: "07:40 PM"
        val datePart = Regex("""\b\d{2}/\d{2}/\d{4}\b""")
            .find(match.matchTime)?.value
            ?: "22/10/2026"
        Pair(timePart, datePart)
    }

    val bottomStatusText = remember(match.status, liveTimerFormatted, match.timeOrScore, match.matchTime) {
        when (match.status) {
            MatchStatus.LIVE -> liveTimerFormatted
            MatchStatus.UPCOMING -> {
                if (match.timeOrScore.startsWith("Starts", ignoreCase = true)) {
                    match.timeOrScore
                } else {
                    "Starts in 4 hours"
                }
            }
            MatchStatus.FINISHED -> "Finished"
        }
    }

    val team1Display = remember(match.team1Code, match.team1Name) {
        match.team1Code.ifBlank { match.team1Name }
    }
    val team2Display = remember(match.team2Code, match.team2Name) {
        match.team2Code.ifBlank { match.team2Name }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .border(
                width = 1.2.dp,
                color = Color(0xFF243247),
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("match_card_${match.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // 1. Top Row: Square Sport Badge + Uppercase Tournament Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SportHeaderSquareBadge(sport = match.sport, tournament = formattedHeader)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formattedHeader,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        letterSpacing = 0.2.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Middle Row: Strictly Team 1 Flag + Name | Center Status | Team 2 Name + Flag
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Team: Flag + Name ONLY
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    TeamFlagBadge(
                        flagUrl = team1Visual.flagUrl,
                        emoji = team1Visual.emoji,
                        code = team1Display,
                        teamName = match.team1Name
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = team1Display,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Center: Live indicator OR Upcoming Time & Date
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    when (match.status) {
                        MatchStatus.LIVE -> {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF471D26))
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Live",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFD9383A),
                                    fontSize = 13.sp
                                )
                            )
                        }
                        MatchStatus.UPCOMING -> {
                            Text(
                                text = upcomingTimeText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = upcomingDateText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF00B8D4),
                                    fontSize = 12.5.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                        MatchStatus.FINISHED -> {
                            Text(
                                text = "Finished",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }

                // Right Team: Name + Flag ONLY
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = team2Display,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    TeamFlagBadge(
                        flagUrl = team2Visual.flagUrl,
                        emoji = team2Visual.emoji,
                        code = team2Display,
                        teamName = match.team2Name
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Bottom Centered Elapsed Timer / Starts In Countdown
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = bottomStatusText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.5.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun TeamFlagBadge(
    flag: String = "",
    code: String = "",
    flagUrl: String = flag,
    emoji: String = "",
    teamName: String = ""
) {
    val resolved = remember(flag, code, flagUrl, emoji, teamName) {
        val lookupVisual = com.example.data.util.TeamFlagResolver.resolve(
            teamName = teamName.ifBlank { code },
            teamCode = code,
            existingFlag = flagUrl.ifBlank { flag }
        )
        com.example.data.util.TeamFlagResolver.TeamVisual(
            code = code.ifBlank { lookupVisual.code },
            flagUrl = flagUrl.ifBlank { lookupVisual.flagUrl },
            emoji = emoji.ifBlank { lookupVisual.emoji },
            countryName = lookupVisual.countryName
        )
    }

    val isMotoGp = code.equals("MotoGP", ignoreCase = true) || teamName.contains("MotoGP", ignoreCase = true)

    if (isMotoGp && resolved.flagUrl.isBlank()) {
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "MGP",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 14.sp,
                    letterSpacing = (-0.5).sp
                )
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF1C2739)),
            contentAlignment = Alignment.Center
        ) {
            if (resolved.emoji.isNotBlank() && resolved.emoji != "🏳️") {
                Text(text = resolved.emoji, fontSize = 22.sp)
            } else {
                Text(
                    text = resolved.code.take(3).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 11.sp
                    )
                )
            }

            if (resolved.flagUrl.startsWith("http://", ignoreCase = true) ||
                resolved.flagUrl.startsWith("https://", ignoreCase = true)
            ) {
                AsyncImage(
                    model = resolved.flagUrl,
                    contentDescription = resolved.code,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
        }
    }
}

@Composable
fun PulsingLiveBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(LiveRed.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "LIVE",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                color = LiveRed,
                fontSize = 10.sp
            )
        )
    }
}

@Composable
fun UpcomingBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SkyBlue.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "UPCOMING",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                color = SkyBlue,
                fontSize = 10.sp
            )
        )
    }
}

@Composable
fun FinishedBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(NavySurfaceVariant)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "FINISHED",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                fontSize = 10.sp
            )
        )
    }
}

@Composable
fun MultipleStreamsModalDialog(
    streams: List<StreamSource>,
    currentSelectedStream: StreamSource? = null,
    matchTitle: String? = null,
    matchTournament: String? = null,
    matchStatus: String? = null,
    matchDetailState: com.example.data.crex.CrexMatchDetailState? = null,
    onRefreshDetail: (() -> Unit)? = null,
    onStreamSelected: (StreamSource) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(1.5.dp, CyanAccentVariant.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = NavyDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Broadcast Waves Icon
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(NavySurfaceVariant)
                        .border(1.dp, CyanAccent.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Podcasts,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (matchTournament != null) {
                    Text(
                        text = matchTournament,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                if (matchTitle != null) {
                    Text(
                        text = matchTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 17.sp
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Multiple Streams Available Header
                Card(
                    colors = CardDefaults.cardColors(containerColor = NavySurfaceVariant.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (matchStatus == "LIVE") LiveRed else CyanAccent)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MULTIPLE STREAMS AVAILABLE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = GoldYellow,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Select a channel or streaming server to watch:",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontSize = 12.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // List of streams
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    streams.forEach { stream ->
                        val isSelected = currentSelectedStream?.id == stream.id
                        StreamItemButton(
                            stream = stream,
                            isSelected = isSelected,
                            onClick = {
                                onStreamSelected(stream)
                                onDismiss()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Cancel button
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextSecondary,
                        containerColor = NavySurface
                    ),
                    border = null
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel",
                        modifier = Modifier.size(16.dp),
                        tint = TextSecondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun StreamItemButton(
    stream: StreamSource,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) CyanAccent else NavyBorder
    val containerColor = if (isSelected) NavySurfaceVariant else NavySurface

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("stream_option_${stream.name.lowercase().replace(" ", "_")}"),
        color = containerColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.FormatListBulleted,
                    contentDescription = null,
                    tint = if (isSelected) CyanAccent else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stream.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSelected) CyanAccent else TextPrimary,
                        fontSize = 14.sp
                    )
                )
            }

            // Quality Badge (FHD, HD, SD)
            QualityBadge(quality = stream.quality)
        }
    }
}

@Composable
fun QualityBadge(quality: String) {
    val (bg, fg) = when (quality.uppercase()) {
        "FHD", "1080P" -> Pair(QualityFHD.copy(alpha = 0.25f), QualityFHD)
        "HD", "720P" -> Pair(QualityHD.copy(alpha = 0.25f), QualityHD)
        else -> Pair(QualitySD.copy(alpha = 0.25f), QualitySD)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = quality.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                color = fg,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        )
    }
}
