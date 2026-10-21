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
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(if (sportTitle.contains("Cricket", ignoreCase = true)) 0 else 1) }

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
                            text = "Live Match Center",
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
                            Text("Cricket Live", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal, color = if (selectedTab == 0) CyanAccent else TextSecondary)
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text("Football Live", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal, color = if (selectedTab == 1) CyanAccent else TextSecondary)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    // CRICKET SCORECARD
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, NavyBorder, RoundedCornerShape(12.dp)),
                                colors = CardDefaults.cardColors(containerColor = NavySurface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "ODI TRI-SERIES • MATCH 2",
                                            style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent, fontWeight = FontWeight.Bold)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(LiveRed)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Pakistan 🇵🇰", fontWeight = FontWeight.Bold, color = TextPrimary)
                                            Text("302/9 (50.0 ov)", color = TextSecondary, fontSize = 13.sp)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("England 🏴󠁧󠁢󠁥󠁮󠁧󠁿", fontWeight = FontWeight.Bold, color = CyanAccent)
                                            Text("156/3 (26.0 ov)", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "England need 147 runs in 144 balls to win",
                                        style = MaterialTheme.typography.bodySmall.copy(color = GoldYellow, fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = "CRR: 6.00 • RRR: 6.12",
                                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = NavyBorder)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Batsmen table
                                    Text("BATTERS", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("J. Duckett *", color = TextPrimary, fontSize = 12.sp)
                                        Text("45 (46b, 5x4, 1x6)", color = CyanAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("J. Root", color = TextPrimary, fontSize = 12.sp)
                                        Text("30 (29b, 3x4)", color = TextPrimary, fontSize = 12.sp)
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Bowlers table
                                    Text("BOWLERS", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Shaheen Afridi *", color = TextPrimary, fontSize = 12.sp)
                                        Text("6.0 - 0 - 35 - 2", color = TextPrimary, fontSize = 12.sp)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Haris Rauf", color = TextPrimary, fontSize = 12.sp)
                                        Text("4.0 - 0 - 23 - 1", color = TextPrimary, fontSize = 12.sp)
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Recent Over
                                    Text("RECENT BALLS: 1 • 4 • 0 • 2 • W • 1", color = GoldYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    // FOOTBALL SCORECARD
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, NavyBorder, RoundedCornerShape(12.dp)),
                                colors = CardDefaults.cardColors(containerColor = NavySurface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "UEFA CHAMPIONS LEAGUE",
                                            style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent, fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "74' 2nd Half",
                                            style = MaterialTheme.typography.labelSmall.copy(color = LiveRed, fontWeight = FontWeight.Bold)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                            Text("PSG 🇫🇷", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                                        }
                                        Text(
                                            text = "2 - 1",
                                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, color = CyanAccent)
                                        )
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                            Text("Barcelona 🇪🇸", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = NavyBorder)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text("KEY MATCH EVENTS", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("⚽ 22'", color = CyanAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Goal: K. Mbappé (PSG)", color = TextPrimary, fontSize = 12.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("⚽ 54'", color = GoldYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Goal: Lamine Yamal (Barcelona)", color = TextPrimary, fontSize = 12.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("⚽ 68'", color = CyanAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Goal: O. Dembélé (PSG)", color = TextPrimary, fontSize = 12.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🟨 71'", color = GoldYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Yellow Card: Gavi (Barcelona)", color = TextSecondary, fontSize = 12.sp)
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = NavyBorder)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text("MATCH STATS", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Possession bar
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("56%", color = CyanAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("Possession", color = TextSecondary, fontSize = 11.sp)
                                        Text("44%", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    LinearProgressIndicator(
                                        progress = { 0.56f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = CyanAccent,
                                        trackColor = NavySurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("14 (8)", color = CyanAccent, fontSize = 12.sp)
                                        Text("Shots (On Target)", color = TextSecondary, fontSize = 11.sp)
                                        Text("10 (5)", color = TextPrimary, fontSize = 12.sp)
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
