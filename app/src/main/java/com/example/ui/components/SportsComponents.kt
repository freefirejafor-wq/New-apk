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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.LiveMatch
import com.example.data.model.MatchStatus
import com.example.data.model.SportType
import com.example.data.model.StreamSource
import com.example.data.util.TeamFlagResolver
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
        color = Color(0xFF0B111E),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        if (isSearching) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_input"),
                    placeholder = {
                        Text(
                            "Search matches, leagues, channels...",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = NavyBorder,
                        focusedContainerColor = NavySurface,
                        unfocusedContainerColor = NavySurface
                    ),
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    },
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
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 20.sp
                    )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Telegram community circle button (cyan circle with purple/magenta border ring matching screenshot)
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF28A8E9))
                            .border(
                                width = 1.8.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFFA855F7), Color(0xFFEC4899))
                                ),
                                shape = CircleShape
                            )
                            .clickable(onClick = onTelegramClick)
                            .testTag("telegram_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Telegram",
                            tint = Color.White,
                            modifier = Modifier
                                .size(15.dp)
                                .offset(x = (-1).dp, y = 0.dp)
                        )
                    }

                    // Compact Refresh icon button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onRefreshClick)
                            .testTag("refresh_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Compact Search icon button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onSearchClick)
                            .testTag("search_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
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
    // Respect match.isNationalTeam:
    // - National-team Football & International Cricket -> Country Flag
    // - Club Football -> Official Club Crest (from ESPN) or Club Shield Fallback (never a country flag)
    val team1Visual = remember(match.team1Name, match.team1Code, match.team1FlagUrl, match.isNationalTeam) {
        TeamFlagResolver.resolveForMatch(
            teamName = match.team1Name,
            teamCode = match.team1Code,
            existingFlag = match.team1FlagUrl,
            isNationalTeam = match.isNationalTeam
        )
    }
    val team2Visual = remember(match.team2Name, match.team2Code, match.team2FlagUrl, match.isNationalTeam) {
        TeamFlagResolver.resolveForMatch(
            teamName = match.team2Name,
            teamCode = match.team2Code,
            existingFlag = match.team2FlagUrl,
            isNationalTeam = match.isNationalTeam
        )
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

    // Elapsed live timer for LIVE matches (never shows scores)
    val initialSeconds = remember(match.id, match.startTimeEpochMs) {
        val now = System.currentTimeMillis()
        if (match.startTimeEpochMs in 1 until now) {
            ((now - match.startTimeEpochMs) / 1000L).coerceIn(60L, 4 * 3600L)
        } else {
            val hash = kotlin.math.abs(match.id.hashCode())
            val h = (hash % 2)
            val m = 15 + (hash % 40)
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
        String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
    }

    // Parse upcoming/kickoff time & date in Asia/Dhaka timezone
    val (upcomingTimeText, upcomingDateText) = remember(match.matchTime, match.startTimeEpochMs) {
        if (match.startTimeEpochMs > 0L) {
            val dhakaTz = TimeZone.getTimeZone("Asia/Dhaka")
            val timeFmt = SimpleDateFormat("hh:mm a", Locale.US).apply { timeZone = dhakaTz }
            val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = dhakaTz }
            val dt = Date(match.startTimeEpochMs)
            Pair(timeFmt.format(dt), dateFmt.format(dt))
        } else {
            val parts = match.matchTime.split("|", "•", ",")
            val timePart = Regex("""\b\d{1,2}:\d{2}\s*(?:AM|PM)\b""", RegexOption.IGNORE_CASE)
                .find(match.matchTime)?.value?.uppercase()
                ?: parts.firstOrNull()?.trim()?.takeIf { it.contains(":") }
                ?: "Scheduled"
            val datePart = Regex("""\b\d{2}/\d{2}/\d{4}\b""")
                .find(match.matchTime)?.value
                ?: Regex("""\b\d{1,2}\s+[A-Za-z]{3}\s+\d{4}\b""").find(match.matchTime)?.value
                ?: match.matchTime.take(16)
            Pair(timePart, datePart)
        }
    }

    // Score-free bottom status line
    val bottomStatusText = remember(match.status, liveTimerFormatted, match.startTimeEpochMs, match.timeOrScore) {
        when (match.status) {
            MatchStatus.LIVE -> liveTimerFormatted
            MatchStatus.UPCOMING -> {
                val now = System.currentTimeMillis()
                if (match.startTimeEpochMs > now) {
                    val diffMin = ((match.startTimeEpochMs - now) / 60_000L).coerceAtLeast(1L)
                    val hrs = diffMin / 60L
                    val mins = diffMin % 60L
                    when {
                        hrs >= 24L -> "Starts in ${hrs / 24L}d ${hrs % 24L}h"
                        hrs > 0L -> "Starts in ${hrs}h ${mins}m"
                        else -> "Starts in ${mins}m"
                    }
                } else if (match.timeOrScore.startsWith("Starts", ignoreCase = true)) {
                    match.timeOrScore
                } else {
                    "Upcoming"
                }
            }
            MatchStatus.FINISHED -> "Finished"
            MatchStatus.UNKNOWN -> "অনির্ধারিত"
        }
    }

    val team1Display = remember(match.team1Name, match.team1Code, team1Visual.countryName) {
        match.team1Name.ifBlank { team1Visual.countryName }.ifBlank { match.team1Code }
    }
    val team2Display = remember(match.team2Name, match.team2Code, team2Visual.countryName) {
        match.team2Name.ifBlank { team2Visual.countryName }.ifBlank { match.team2Code }
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
            // 1. Top Row: Square Sport Badge + Uppercase Tournament Header + Favourite Toggle
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
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("fav_toggle_${match.id}")
                ) {
                    Icon(
                        imageVector = if (match.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (match.isFavorite) "Remove from Favourites" else "Add to Favourites",
                        tint = if (match.isFavorite) GoldYellow else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Middle Row: Strictly Team 1 Logo/Flag + Name | Center Status/Kickoff | Team 2 Name + Logo/Flag
            // Never displays any score or score badge on the card.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Team: Logo/Flag + Name ONLY
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    TeamFlagBadge(
                        flagUrl = team1Visual.flagUrl,
                        emoji = team1Visual.emoji,
                        code = match.team1Code.ifBlank { team1Display },
                        teamName = match.team1Name,
                        isNationalTeam = match.isNationalTeam
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = team1Display,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Center: Live indicator OR Upcoming Kickoff Time & Date OR Finished OR Unknown
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
                                    fontSize = 13.5.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = upcomingDateText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF00B8D4),
                                    fontSize = 12.sp
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
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = upcomingDateText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextMuted,
                                    fontSize = 11.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                        MatchStatus.UNKNOWN -> {
                            Text(
                                text = "অনির্ধারিত",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = GoldYellow,
                                    fontSize = 12.5.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Right Team: Name + Logo/Flag ONLY
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
                            fontSize = 14.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    TeamFlagBadge(
                        flagUrl = team2Visual.flagUrl,
                        emoji = team2Visual.emoji,
                        code = match.team2Code.ifBlank { team2Display },
                        teamName = match.team2Name,
                        isNationalTeam = match.isNationalTeam
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Bottom Centered Elapsed Timer / Starts In Countdown / Status
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = bottomStatusText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
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
    teamName: String = "",
    isNationalTeam: Boolean = true
) {
    val resolved = remember(flag, code, flagUrl, emoji, teamName, isNationalTeam) {
        val lookupVisual = TeamFlagResolver.resolveForMatch(
            teamName = teamName.ifBlank { code },
            teamCode = code,
            existingFlag = flagUrl.ifBlank { flag },
            isNationalTeam = isNationalTeam
        )
        TeamFlagResolver.TeamVisual(
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
        var imageLoadFailed by remember(resolved.flagUrl) { mutableStateOf(false) }
        val hasRemoteUrl = (resolved.flagUrl.startsWith("http://", ignoreCase = true) ||
            resolved.flagUrl.startsWith("https://", ignoreCase = true)) && !imageLoadFailed

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF1C2739))
                .border(
                    width = 1.dp,
                    color = if (!isNationalTeam) Color(0xFF2D3F5B) else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Clean fallback badge if logo/flag is missing or fails to load
            if (!hasRemoteUrl) {
                if (isNationalTeam && resolved.emoji.isNotBlank() && resolved.emoji != "🏳️") {
                    Text(text = resolved.emoji, fontSize = 22.sp)
                } else {
                    Text(
                        text = resolved.code.take(3).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = CyanAccent,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            if (hasRemoteUrl) {
                AsyncImage(
                    model = resolved.flagUrl,
                    contentDescription = teamName.ifBlank { resolved.code },
                    contentScale = if (isNationalTeam) ContentScale.Crop else ContentScale.Fit,
                    onError = { imageLoadFailed = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isNationalTeam) 0.dp else 4.dp)
                        .clip(if (isNationalTeam) CircleShape else RoundedCornerShape(4.dp))
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
    approvedHighlightUrl: String? = null,
    broadcastChannels: List<String> = emptyList(),
    onWatchHighlight: ((String) -> Unit)? = null,
    onRefreshDetail: (() -> Unit)? = null,
    onStreamSelected: (StreamSource) -> Unit,
    onDismiss: () -> Unit
) {
    // Only display enabled & authorized stream sources in priority order (never expose raw stream URLs)
    val enabledStreams = remember(streams) {
        streams
            .filter { it.isEnabled && it.url.isNotBlank() }
            .sortedWith(
                compareBy<StreamSource> { it.isBackup }
                    .thenBy { it.priorityOrder }
                    .thenByDescending { it.isDefault }
            )
    }

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

                if (broadcastChannels.isNotEmpty()) {
                    Text(
                        text = "TV Broadcast: ${broadcastChannels.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = CyanAccent,
                            fontSize = 11.sp
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
                            text = "MULTIPLE STREAMS AVAILABLE (${enabledStreams.size})",
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
                    text = "Select an authorized channel or streaming server to watch:",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontSize = 12.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (enabledStreams.isEmpty()) {
                    Surface(
                        color = NavySurface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No active stream sources are currently enabled for this match.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        enabledStreams.forEach { stream ->
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
                }

                // Optional Approved Highlight button (only shown when an approved playable link exists)
                if (!approvedHighlightUrl.isNullOrBlank() && onWatchHighlight != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            onWatchHighlight(approvedHighlightUrl)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("watch_highlight_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldYellow.copy(alpha = 0.2f),
                            contentColor = GoldYellow
                        )
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = "Watch Highlight",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Watch Official Highlight",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
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
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (stream.isBackup) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = GoldYellow.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "BACKUP",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = GoldYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            ),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Quality Badge (FHD, HD, SD)
            QualityBadge(quality = stream.quality)
        }
    }
}

@Composable
fun QualityBadge(quality: String) {
    val (bg, fg) = when (quality.uppercase()) {
        "FHD", "1080P", "1080P FHD" -> Pair(QualityFHD.copy(alpha = 0.25f), QualityFHD)
        "HD", "720P", "720P HD" -> Pair(QualityHD.copy(alpha = 0.25f), QualityHD)
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
