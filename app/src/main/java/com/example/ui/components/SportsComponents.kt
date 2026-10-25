package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
    title: String = "Football Live Tv",
    onTelegramClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSearchClick: () -> Unit,
    searchQuery: String = "",
    isSearching: Boolean = false,
    onSearchQueryChange: (String) -> Unit = {},
    onCloseSearch: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF131B26))
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
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 21.sp
                    )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Telegram community button (blue circle with violet ring + white paper plane)
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2AABEE))
                            .border(1.8.dp, Color(0xFF7C3AED), CircleShape)
                            .clickable(onClick = onTelegramClick)
                            .testTag("telegram_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(16.dp)) {
                            val w = size.width
                            val h = size.height
                            val planePath = Path().apply {
                                moveTo(w * 0.08f, h * 0.48f)
                                lineTo(w * 0.90f, h * 0.14f)
                                lineTo(w * 0.73f, h * 0.88f)
                                lineTo(w * 0.46f, h * 0.63f)
                                lineTo(w * 0.33f, h * 0.77f)
                                lineTo(w * 0.36f, h * 0.54f)
                                close()
                            }
                            drawPath(path = planePath, color = Color.White)
                        }
                    }

                    // Plain white Refresh icon
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clickable(onClick = onRefreshClick)
                            .testTag("refresh_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Plain white Search icon
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clickable(onClick = onSearchClick)
                            .testTag("search_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = Color(0xFF233044), thickness = 1.dp)
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
            color = Color(0xFF192336),
            shape = RoundedCornerShape(34.dp),
            tonalElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .border(
                    width = 1.dp,
                    color = Color(0xFF26354D),
                    shape = RoundedCornerShape(34.dp)
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
                            .padding(vertical = 5.dp, horizontal = 2.dp)
                            .clip(RoundedCornerShape(30.dp))
                            .background(
                                if (isSelected) Color(0xFF21324A)
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
                                modifier = Modifier.size(if (isCenterLiveTab) 25.dp else 22.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
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
    onReminderToggle: () -> Unit = {},
    onStreamSelectClick: () -> Unit = {}
) {
    val isClubFootball = remember(match.sport, match.tournament) {
        match.sport == SportType.FOOTBALL &&
            !com.example.data.crex.CrexCricketService.isInternationalFootballLeague("", match.tournament)
    }
    val team1Visual = remember(match.team1Name, match.team1Code, match.team1FlagUrl, isClubFootball) {
        com.example.data.util.TeamFlagResolver.resolve(
            teamName = match.team1Name,
            teamCode = match.team1Code,
            existingFlag = match.team1FlagUrl,
            isClubFootball = isClubFootball
        )
    }
    val team2Visual = remember(match.team2Name, match.team2Code, match.team2FlagUrl, isClubFootball) {
        com.example.data.util.TeamFlagResolver.resolve(
            teamName = match.team2Name,
            teamCode = match.team2Code,
            existingFlag = match.team2FlagUrl,
            isClubFootball = isClubFootball
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

    val isFormula1OrMotorsport = remember(match.sport, formattedHeader, match.team1Name) {
        match.sport == SportType.RACING ||
            formattedHeader.contains("FORMULA 1") ||
            formattedHeader.contains("MOTORSPORT") ||
            match.team1Name.contains("Formula 1", ignoreCase = true)
    }

    // Parse kickoff time ("11:00 PM") & date ("24/10/2026") from startTimeEpochMs or match.matchTime in Asia/Dhaka
    val (upcomingTimeText, upcomingDateText) = remember(match.startTimeEpochMs, match.matchTime) {
        if (match.startTimeEpochMs > 0L) {
            val tz = java.util.TimeZone.getTimeZone("Asia/Dhaka")
            val timeFmt = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.ENGLISH).apply { timeZone = tz }
            val dateFmt = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.ENGLISH).apply { timeZone = tz }
            val d = java.util.Date(match.startTimeEpochMs)
            Pair(timeFmt.format(d).uppercase(), dateFmt.format(d))
        } else {
            val timePart = Regex("""\b\d{1,2}:\d{2}\s*(?:AM|PM)\b""", RegexOption.IGNORE_CASE)
                .find(match.matchTime)?.value?.uppercase()
                ?: "Scheduled"
            val slashDate = Regex("""\b\d{2}/\d{2}/\d{4}\b""").find(match.matchTime)?.value ?: ""
            Pair(timePart, slashDate)
        }
    }

    // Bottom centered text: strictly provider-confirmed status or kickoff time (no scores, no unverified timers)
    val bottomStatusText = remember(
        match.status,
        match.isConfirmedLive,
        match.isStatusUnknown,
        match.isPostponedOrCancelled,
        match.providerStatusLabel,
        upcomingTimeText,
        upcomingDateText
    ) {
        when {
            match.isPostponedOrCancelled -> match.providerStatusLabel.ifBlank { "Postponed" }
            match.isStatusUnknown -> "Unknown"
            match.isConfirmedLive -> {
                if (upcomingDateText.isNotBlank()) "$upcomingTimeText • $upcomingDateText" else upcomingTimeText
            }
            match.status == MatchStatus.FINISHED -> "Finished"
            else -> "Upcoming"
        }
    }

    val team1CodeDisplay = remember(match.team1Code, team1Visual.code, match.team1Name) {
        match.team1Code.ifBlank { team1Visual.code.ifBlank { match.team1Name } }
    }
    val team2CodeDisplay = remember(match.team2Code, team2Visual.code, match.team2Name) {
        match.team2Code.ifBlank { team2Visual.code.ifBlank { match.team2Name } }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .border(
                width = 1.2.dp,
                color = Color(0xFF25344B),
                shape = RoundedCornerShape(13.dp)
            )
            .testTag("match_card_${match.id}"),
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141D2B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            // 1. Top Row: Competition ("SPORT | LEAGUE")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isFormula1OrMotorsport) {
                    F1MiniHeaderMark()
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = formattedHeader,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 12.5.sp,
                        letterSpacing = 0.2.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Middle Row: Left Team Crest + Name | Center Red Dot + "Live" or Kickoff Time & Date | Right Team Name + Crest
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Team: Crest/Flag + Name (no score)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    TeamFlagBadge(
                        flagUrl = team1Visual.flagUrl,
                        emoji = team1Visual.emoji,
                        code = team1CodeDisplay,
                        teamName = match.team1Name,
                        isClubFootball = isClubFootball
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = match.team1Name.ifBlank { team1CodeDisplay },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 13.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Center: Provider-confirmed Live indicator OR Kickoff Time + Date
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    if (match.isConfirmedLive) {
                        LiveGlowingRedDot()
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Live",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFEF4444),
                                fontSize = 11.5.sp
                            )
                        )
                    } else {
                        Text(
                            text = upcomingTimeText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp
                            ),
                            textAlign = TextAlign.Center
                        )
                        if (upcomingDateText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = upcomingDateText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF00BCD4),
                                    fontSize = 12.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Right Team: Name + Crest/Flag (no score)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = match.team2Name.ifBlank { team2CodeDisplay },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 13.5.sp
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
                        code = team2CodeDisplay,
                        teamName = match.team2Name,
                        isClubFootball = isClubFootball
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Bottom Centered Text: Provider-confirmed status (or kickoff time when Live)
            Text(
                text = bottomStatusText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = when {
                        match.isPostponedOrCancelled -> GoldYellow
                        match.isStatusUnknown -> Color(0xFF94A3B8)
                        else -> Color.White
                    },
                    fontSize = 13.5.sp
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun LiveGlowingRedDot() {
    Canvas(modifier = Modifier.size(width = 22.dp, height = 14.dp)) {
        val red = Color(0xFFEF4444)
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = red,
            radius = 2.6.dp.toPx(),
            center = center
        )
        val innerRadius = 5.5.dp.toPx()
        val innerStroke = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
        drawArc(
            color = red.copy(alpha = 0.85f),
            startAngle = 140f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
            size = Size(innerRadius * 2f, innerRadius * 2f),
            style = innerStroke
        )
        drawArc(
            color = red.copy(alpha = 0.85f),
            startAngle = -40f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
            size = Size(innerRadius * 2f, innerRadius * 2f),
            style = innerStroke
        )
        val outerRadius = 8.5.dp.toPx()
        val outerStroke = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
        drawArc(
            color = red.copy(alpha = 0.45f),
            startAngle = 145f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
            size = Size(outerRadius * 2f, outerRadius * 2f),
            style = outerStroke
        )
        drawArc(
            color = red.copy(alpha = 0.45f),
            startAngle = -35f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
            size = Size(outerRadius * 2f, outerRadius * 2f),
            style = outerStroke
        )
    }
}

@Composable
private fun F1MiniHeaderMark() {
    Canvas(modifier = Modifier.size(width = 20.dp, height = 11.dp)) {
        val f1Red = Color(0xFFE10600)
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Butt)
        // Stylized red F1 speedwing mark
        drawLine(color = f1Red, start = Offset(w * 0.12f, h * 0.25f), end = Offset(w * 0.65f, h * 0.25f), strokeWidth = stroke.width)
        drawLine(color = f1Red, start = Offset(w * 0.05f, h * 0.62f), end = Offset(w * 0.55f, h * 0.62f), strokeWidth = stroke.width)
        drawLine(color = f1Red, start = Offset(w * 0.68f, h * 0.85f), end = Offset(w * 0.90f, h * 0.18f), strokeWidth = stroke.width)
    }
}

@Composable
fun TeamFlagBadge(
    flag: String = "",
    code: String = "",
    flagUrl: String = flag,
    emoji: String = "",
    teamName: String = "",
    sizeDp: Int = 42,
    isClubFootball: Boolean = false
) {
    val resolved = remember(flag, code, flagUrl, emoji, teamName, isClubFootball) {
        val lookupVisual = com.example.data.util.TeamFlagResolver.resolve(
            teamName = teamName.ifBlank { code },
            teamCode = code,
            existingFlag = flagUrl.ifBlank { flag },
            isClubFootball = isClubFootball
        )
        com.example.data.util.TeamFlagResolver.TeamVisual(
            code = code.ifBlank { lookupVisual.code },
            flagUrl = lookupVisual.flagUrl,
            emoji = emoji.ifBlank { lookupVisual.emoji },
            countryName = lookupVisual.countryName
        )
    }

    var imageLoadFailed by remember(resolved.flagUrl) { mutableStateOf(false) }

    val isFormula1 = code.equals("F1", ignoreCase = true) ||
        teamName.contains("Formula 1", ignoreCase = true) ||
        teamName.equals("F1", ignoreCase = true)
    val isMotoGp = code.equals("MotoGP", ignoreCase = true) || teamName.contains("MotoGP", ignoreCase = true)

    when {
        isFormula1 -> {
            Box(
                modifier = Modifier.size(sizeDp.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(width = (sizeDp - 4).dp, height = (sizeDp / 2).dp)) {
                    val f1Red = Color(0xFFE10600)
                    val w = size.width
                    val h = size.height
                    val sw = 3.2.dp.toPx()
                    drawLine(color = f1Red, start = Offset(w * 0.08f, h * 0.28f), end = Offset(w * 0.62f, h * 0.28f), strokeWidth = sw)
                    drawLine(color = f1Red, start = Offset(w * 0.02f, h * 0.65f), end = Offset(w * 0.52f, h * 0.65f), strokeWidth = sw)
                    drawLine(color = f1Red, start = Offset(w * 0.02f, h * 0.85f), end = Offset(w * 0.18f, h * 0.28f), strokeWidth = sw)
                    drawLine(color = f1Red, start = Offset(w * 0.68f, h * 0.85f), end = Offset(w * 0.88f, h * 0.22f), strokeWidth = sw)
                }
            }
        }
        isMotoGp && resolved.flagUrl.isBlank() -> {
            Box(
                modifier = Modifier
                    .width((sizeDp + 2).dp)
                    .height((sizeDp - 6).dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "MGP",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontSize = 13.sp,
                        letterSpacing = (-0.5).sp
                    )
                )
            }
        }
        !imageLoadFailed && (resolved.flagUrl.startsWith("http://", ignoreCase = true) ||
            resolved.flagUrl.startsWith("https://", ignoreCase = true)) -> {
            val isCountryFlag = resolved.flagUrl.contains("flagcdn.com", ignoreCase = true)
            if (isCountryFlag) {
                Box(
                    modifier = Modifier
                        .size((sizeDp - 2).dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1C2739))
                        .border(1.dp, Color(0xFF2E3F59), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = resolved.flagUrl,
                        contentDescription = resolved.code,
                        contentScale = ContentScale.Crop,
                        onError = { imageLoadFailed = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }
            } else {
                // Team/Club shield crest or league logo: render cleanly without circular crop
                Box(
                    modifier = Modifier.size(sizeDp.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = resolved.flagUrl,
                        contentDescription = resolved.code,
                        contentScale = ContentScale.Fit,
                        onError = { imageLoadFailed = true },
                        modifier = Modifier.size((sizeDp - 2).dp)
                    )
                }
            }
        }
        else -> {
            Box(
                modifier = Modifier
                    .size((sizeDp - 2).dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1C2739))
                    .border(1.dp, Color(0xFF2E3F59), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!isClubFootball && resolved.emoji.isNotBlank() && resolved.emoji != "🏳️") {
                    Text(text = resolved.emoji, fontSize = 20.sp)
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
    onAddCustomStream: ((StreamSource) -> Unit)? = null,
    onStreamSelected: (StreamSource) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .border(1.3.dp, Color(0xFF00C2B8), RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF182230))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Broadcast Waves Icon inside 62.dp Teal-Bordered Circle
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1B2E3E))
                        .border(1.5.dp, Color(0xFF1E5668), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(30.dp)) {
                        val cyan = Color(0xFF00D1C1)
                        val cx = size.width * 0.5f
                        val cy = size.height * 0.5f
                        val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)

                        // Center dot
                        drawCircle(color = cyan, radius = 2.6.dp.toPx(), center = Offset(cx, cy))

                        // Inner left & right arcs
                        val innerR = 6.8.dp.toPx()
                        drawArc(
                            color = cyan,
                            startAngle = 135f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(cx - innerR, cy - innerR),
                            size = Size(innerR * 2, innerR * 2),
                            style = stroke
                        )
                        drawArc(
                            color = cyan,
                            startAngle = -45f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(cx - innerR, cy - innerR),
                            size = Size(innerR * 2, innerR * 2),
                            style = stroke
                        )

                        // Outer left & right arcs
                        val outerR = 11.5.dp.toPx()
                        drawArc(
                            color = cyan,
                            startAngle = 135f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(cx - outerR, cy - outerR),
                            size = Size(outerR * 2, outerR * 2),
                            style = stroke
                        )
                        drawArc(
                            color = cyan,
                            startAngle = -45f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(cx - outerR, cy - outerR),
                            size = Size(outerR * 2, outerR * 2),
                            style = stroke
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Multiple Streams Available",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 20.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Choose your preferred stream",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF94A3B8),
                        fontSize = 14.5.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // List of streams matching reference screenshot
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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

                // Cancel button matching reference screenshot
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0xFF2A394D), RoundedCornerShape(14.dp))
                        .clickable(onClick = onDismiss),
                    color = Color(0xFF1C2635),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancel",
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8),
                                fontSize = 16.sp
                            )
                        )
                    }
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
    val borderColor = if (isSelected) Color(0xFF00D1C1) else Color(0xFF2E3E52)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("stream_option_${stream.name.lowercase().replace(" ", "_")}"),
        color = Color(0xFF1E2938),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Cyan 3-row server/list icon matching screenshot
                Canvas(modifier = Modifier.size(width = 20.dp, height = 16.dp)) {
                    val cyan = Color(0xFF00E5FF)
                    val rowH = size.height / 3f
                    for (r in 0..2) {
                        val y = r * rowH + rowH * 0.2f
                        val barH = rowH * 0.58f
                        // Left square
                        drawRect(
                            color = cyan,
                            topLeft = Offset(0f, y),
                            size = Size(barH * 1.15f, barH)
                        )
                        // Right bar
                        drawRect(
                            color = cyan,
                            topLeft = Offset(barH * 1.65f, y),
                            size = Size(size.width - barH * 1.65f, barH)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = stream.name.uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        fontSize = 15.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Rounded HD pill on right
            QualityBadge(quality = stream.quality)
        }
    }
}

@Composable
fun QualityBadge(quality: String) {
    val label = if (quality.contains("HD", ignoreCase = true)) "HD" else quality.take(3).uppercase()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF25384B))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color(0xFF00D1C1),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        )
    }
}
