package com.example.ui.components

import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.LiveMatch
import com.example.data.model.MatchStatus
import com.example.data.model.SportsChannel
import com.example.data.model.StreamSource
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopSportsBar(
    title: String = "SportzX Live TV",
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
    NavigationBar(
        containerColor = NavyDark,
        tonalElevation = 8.dp,
        modifier = Modifier.border(width = 0.5.dp, color = NavyBorder.copy(alpha = 0.5f), shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        NavTab.values().forEach { tab ->
            val isSelected = tab == selectedTab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CyanAccent,
                    selectedTextColor = CyanAccent,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = NavySurfaceVariant
                ),
                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
            )
        }
    }
}

@Composable
fun MatchCard(
    match: LiveMatch,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onStreamSelectClick: () -> Unit
) {
    val team1Visual = remember(match.team1Name, match.team1Code, match.team1FlagUrl) {
        com.example.data.util.TeamFlagResolver.resolve(match.team1Name, match.team1Code, match.team1FlagUrl)
    }
    val team2Visual = remember(match.team2Name, match.team2Code, match.team2FlagUrl) {
        com.example.data.util.TeamFlagResolver.resolve(match.team2Name, match.team2Code, match.team2FlagUrl)
    }

    val sportIcon = when (match.sport) {
        com.example.data.model.SportType.CRICKET -> Icons.Default.SportsCricket
        com.example.data.model.SportType.FOOTBALL -> Icons.Default.SportsSoccer
        com.example.data.model.SportType.BOXING -> Icons.Default.SportsMma
        com.example.data.model.SportType.BASKETBALL -> Icons.Default.SportsBasketball
        com.example.data.model.SportType.HOCKEY -> Icons.Default.SportsHockey
        com.example.data.model.SportType.TENNIS -> Icons.Default.SportsTennis
        else -> Icons.Default.Sports
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (match.status == MatchStatus.LIVE) 1.2.dp else 1.dp,
                color = if (match.status == MatchStatus.LIVE) CyanAccent.copy(alpha = 0.55f) else NavyBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("match_card_${match.id}"),
        colors = CardDefaults.cardColors(containerColor = NavySurface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Tournament / League Header with match type
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(NavySurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            sportIcon,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = match.tournament,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (match.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (match.isFavorite) GoldYellow else TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Teams row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Team 1
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    TeamFlagBadge(
                        flagUrl = team1Visual.flagUrl,
                        emoji = team1Visual.emoji,
                        code = team1Visual.code
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = team1Visual.code,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary,
                                    fontSize = 15.sp
                                ),
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = team1Visual.emoji,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = match.team1Name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextSecondary,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Center Status Badge (LIVE / UPCOMING / FINISHED) & VS — no small score clutter
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    when (match.status) {
                        MatchStatus.LIVE -> {
                            PulsingLiveBadge()
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "VS",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = CyanAccent,
                                    fontSize = 13.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                        MatchStatus.UPCOMING -> {
                            UpcomingBadge()
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "VS",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary,
                                    fontSize = 13.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                        MatchStatus.FINISHED -> {
                            FinishedBadge()
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "VS",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Team 2
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = team2Visual.emoji,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = team2Visual.code,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary,
                                    fontSize = 15.sp
                                ),
                                maxLines = 1,
                                textAlign = TextAlign.End
                            )
                        }
                        Text(
                            text = match.team2Name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextSecondary,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    TeamFlagBadge(
                        flagUrl = team2Visual.flagUrl,
                        emoji = team2Visual.emoji,
                        code = team2Visual.code
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = NavyBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Bottom schedule & stream indicator bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (match.status == MatchStatus.LIVE) Icons.Default.Sensors else Icons.Default.Schedule,
                        contentDescription = null,
                        tint = if (match.status == MatchStatus.LIVE) LiveRed else CyanAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = match.matchTime.ifBlank {
                            when (match.status) {
                                MatchStatus.LIVE -> "Live Broadcast Now"
                                MatchStatus.UPCOMING -> "Upcoming Schedule"
                                MatchStatus.FINISHED -> "Match Completed"
                            }
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (match.streams.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NavySurfaceVariant)
                            .clickable(onClick = onStreamSelectClick)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LiveTv,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${match.streams.size} Streams",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = CyanAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
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

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(NavySurfaceVariant)
            .border(1.5.dp, CyanAccent.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Fallback emoji or code always rendered underneath so it is never blank offline
        if (resolved.emoji.isNotBlank() && resolved.emoji != "🏳️") {
            Text(text = resolved.emoji, fontSize = 20.sp)
        } else {
            Text(
                text = resolved.code.take(3).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 10.sp
                )
            )
        }

        // High-resolution Flag / Club crest overlay via Coil
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

@Composable
fun PulsingLiveBadge() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(LiveRed.copy(alpha = 0.16f))
            .border(0.8.dp, LiveRed.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(LiveRed.copy(alpha = alpha))
            )
            Spacer(modifier = Modifier.width(4.dp))
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
}

@Composable
fun UpcomingBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SkyBlue.copy(alpha = 0.16f))
            .border(0.8.dp, SkyBlue.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
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
            .border(0.8.dp, NavyBorder, RoundedCornerShape(6.dp))
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
