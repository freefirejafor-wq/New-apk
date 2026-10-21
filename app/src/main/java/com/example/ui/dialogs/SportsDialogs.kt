package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.StreamSource
import com.example.ui.theme.*

@Composable
fun NetworkStreamDialog(
    onDismiss: () -> Unit,
    onPlayUrl: (StreamSource) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("Custom Stream") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(1.dp, CyanAccent, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = NavyDark),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Podcasts, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Network Stream",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 18.sp
                    )
                )
                Text(
                    text = "Enter any direct M3U8, HLS, or MP4 stream address",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Stream Label") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Stream URL (.m3u8, .mp4)") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://example.com/live.m3u8") },
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick Test Stream:",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                    TextButton(
                        onClick = {
                            title = "Akamai FHD Live"
                            url = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"
                        }
                    ) {
                        Text("Load Sample HLS", color = CyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Button(
                        onClick = {
                            if (url.isNotBlank()) {
                                onPlayUrl(
                                    StreamSource(
                                        name = title.ifBlank { "Network Stream" },
                                        quality = "FHD",
                                        url = url.trim()
                                    )
                                )
                                onDismiss()
                            }
                        },
                        enabled = url.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                    ) {
                        Text("Play", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminPinDialog(
    correctPin: String,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(1.dp, GoldYellow, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = NavyDark),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = GoldYellow, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Admin Authorization",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 18.sp
                    )
                )
                Text(
                    text = "Enter your admin PIN to access app control and M3U8 tools (Default: 1234)",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it
                        error = false
                    },
                    label = { Text("PIN Passcode") },
                    modifier = Modifier.fillMaxWidth().testTag("admin_pin_input"),
                    visualTransformation = PasswordVisualTransformation(),
                    isError = error,
                    singleLine = true
                )

                if (error) {
                    Text(
                        text = "Incorrect PIN. Default is 1234.",
                        color = LiveRed,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Button(
                        onClick = {
                            if (pinInput.trim() == correctPin.trim()) {
                                onSuccess()
                                onDismiss()
                            } else {
                                error = true
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("admin_pin_submit_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldYellow)
                    ) {
                        Text("Unlock", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun M3UPlaylistImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .border(1.dp, CyanAccent, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = NavyDark),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Import M3U / IPTV Playlist",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 18.sp
                    )
                )
                Text(
                    text = "Paste an M3U playlist URL (e.g. https://.../playlist.m3u) or raw #EXTINF text to load sports channels automatically.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("M3U Playlist URL or Text") },
                    placeholder = { Text("https://example.com/sports.m3u") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = NavyBorder,
                        focusedContainerColor = NavySurface,
                        unfocusedContainerColor = NavySurface
                    ),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Sample M3U quick loader
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick Demo:",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                    TextButton(
                        onClick = {
                            input = """
#EXTM3U
#EXTINF:-1 tvg-id="willow" tvg-name="Willow HD" tvg-logo="https://example.com/willow.png" group-title="Cricket", Willow Sports HD
https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8
#EXTINF:-1 tvg-id="sky" tvg-name="Sky Sports Main Event" group-title="Football", Sky Sports UHD
https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8
                            """.trimIndent()
                        }
                    ) {
                        Text("Fill Sample M3U", color = CyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Button(
                        onClick = {
                            if (input.isNotBlank()) {
                                isImporting = true
                                onImport(input.trim())
                                onDismiss()
                            }
                        },
                        enabled = input.isNotBlank() && !isImporting,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Import Now", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HighlightsDialog(
    highlights: List<com.example.data.model.HighlightItem>,
    onPlayHighlight: (com.example.data.model.HighlightItem) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .border(1.dp, CyanAccent, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = NavyDark),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SmartDisplay, contentDescription = null, tint = CyanAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Match Highlights & Recaps",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 17.sp
                            )
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(highlights, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, NavyBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    onPlayHighlight(item)
                                    onDismiss()
                                },
                            colors = CardDefaults.cardColors(containerColor = NavySurface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(NavySurfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.PlayCircle,
                                        contentDescription = null,
                                        tint = CyanAccent,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            fontSize = 13.sp
                                        ),
                                        maxLines = 2
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.tournament,
                                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(NavySurfaceVariant)
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = item.duration,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = CyanAccent,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NavySurfaceVariant)
                ) {
                    Text("Close", color = TextPrimary)
                }
            }
        }
    }
}

@Composable
fun LiveScoresModalDialog(
    sportTitle: String,
    matches: List<com.example.data.model.LiveMatch> = emptyList(),
    onWatchMatch: (com.example.data.model.LiveMatch) -> Unit = {},
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(if (sportTitle.contains("Cricket", ignoreCase = true)) 0 else 1) }
    var selectedStatusFilter by remember { mutableStateOf<com.example.data.model.MatchStatus?>(null) }

    val cricketMatches = remember(matches) {
        matches.filter { it.sport == com.example.data.model.SportType.CRICKET }
    }
    val footballMatches = remember(matches) {
        matches.filter { it.sport == com.example.data.model.SportType.FOOTBALL }
    }

    val sportMatches = if (selectedTab == 0) cricketMatches else footballMatches
    val activeList = remember(sportMatches, selectedStatusFilter) {
        if (selectedStatusFilter == null) sportMatches
        else sportMatches.filter { it.status == selectedStatusFilter }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .border(1.dp, CyanAccent, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = NavyDark),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (selectedTab == 0) Icons.Default.SportsCricket else Icons.Default.SportsSoccer,
                            contentDescription = null,
                            tint = CyanAccent
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "International Match Center",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 17.sp
                            )
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Sport Switcher Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = NavySurface,
                    contentColor = CyanAccent,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "🏏 Cricket (${cricketMatches.size})",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 0) CyanAccent else TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "⚽ Football (${footballMatches.size})",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) CyanAccent else TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Status Filter Row: All, Live, Upcoming, Finished
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val liveCount = sportMatches.count { it.status == com.example.data.model.MatchStatus.LIVE }
                    val upCount = sportMatches.count { it.status == com.example.data.model.MatchStatus.UPCOMING }
                    val finCount = sportMatches.count { it.status == com.example.data.model.MatchStatus.FINISHED }

                    FilterChip(
                        selected = selectedStatusFilter == null,
                        onClick = { selectedStatusFilter = null },
                        label = { Text("All (${sportMatches.size})", fontSize = 10.sp) }
                    )
                    FilterChip(
                        selected = selectedStatusFilter == com.example.data.model.MatchStatus.LIVE,
                        onClick = { selectedStatusFilter = com.example.data.model.MatchStatus.LIVE },
                        label = { Text("Live ($liveCount)", fontSize = 10.sp) }
                    )
                    FilterChip(
                        selected = selectedStatusFilter == com.example.data.model.MatchStatus.UPCOMING,
                        onClick = { selectedStatusFilter = com.example.data.model.MatchStatus.UPCOMING },
                        label = { Text("Upcoming ($upCount)", fontSize = 10.sp) }
                    )
                    FilterChip(
                        selected = selectedStatusFilter == com.example.data.model.MatchStatus.FINISHED,
                        onClick = { selectedStatusFilter = com.example.data.model.MatchStatus.FINISHED },
                        label = { Text("Done ($finCount)", fontSize = 10.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (activeList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matches returned by CREX for this tab.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 390.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(activeList, key = { "${it.status}_${it.id}" }) { match ->
                            val v1 = com.example.data.util.TeamFlagResolver.resolve(match.team1Name, match.team1Code, match.team1FlagUrl)
                            val v2 = com.example.data.util.TeamFlagResolver.resolve(match.team2Name, match.team2Code, match.team2FlagUrl)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        if (match.status == com.example.data.model.MatchStatus.LIVE) CyanAccent.copy(alpha = 0.5f) else NavyBorder,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        onWatchMatch(match)
                                        onDismiss()
                                    },
                                colors = CardDefaults.cardColors(containerColor = NavySurface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = match.tournament,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = CyanAccent,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            ),
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        when (match.status) {
                                            com.example.data.model.MatchStatus.LIVE -> com.example.ui.components.PulsingLiveBadge()
                                            com.example.data.model.MatchStatus.UPCOMING -> com.example.ui.components.UpcomingBadge()
                                            com.example.data.model.MatchStatus.FINISHED -> com.example.ui.components.FinishedBadge()
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            com.example.ui.components.TeamFlagBadge(
                                                flagUrl = v1.flagUrl,
                                                emoji = v1.emoji,
                                                code = v1.code
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "${v1.emoji} ${match.team1Name}",
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary,
                                                    fontSize = 13.sp,
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = v1.code,
                                                    color = TextSecondary,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(horizontal = 6.dp)
                                        ) {
                                            Text(
                                                text = "VS",
                                                fontWeight = FontWeight.ExtraBold,
                                                color = CyanAccent,
                                                fontSize = 13.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.End,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = "${match.team2Name} ${v2.emoji}",
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    textAlign = TextAlign.End
                                                )
                                                Text(
                                                    text = v2.code,
                                                    color = TextSecondary,
                                                    fontSize = 10.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            com.example.ui.components.TeamFlagBadge(
                                                flagUrl = v2.flagUrl,
                                                emoji = v2.emoji,
                                                code = v2.code
                                            )
                                        }
                                    }

                                    if (match.timeOrScore.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = match.timeOrScore,
                                            color = if (match.status == com.example.data.model.MatchStatus.LIVE) GoldYellow else TextPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp
                                        )
                                    }

                                    if (match.matchTime.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = match.matchTime,
                                            color = TextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NavySurfaceVariant)
                ) {
                    Text("Close", color = TextPrimary)
                }
            }
        }
    }
}
