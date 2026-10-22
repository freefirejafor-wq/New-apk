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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.*
import com.example.data.repository.SportsStreamRepository
import com.example.ui.components.QualityBadge
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class AdminTab(val label: String) {
    M3U8_CHECKER("M3U8 Checker"),
    CHANNELS("Channels & Streams"),
    MATCHES("Live Matches"),
    SETTINGS("App Control")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    repository: SportsStreamRepository,
    onBackClick: () -> Unit,
    onTestPlayStream: (StreamSource) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val matches by repository.matches.collectAsState()
    val channels by repository.channels.collectAsState()
    val config by repository.config.collectAsState()
    val cloudSyncStatus by repository.cloudSyncStatus.collectAsState()

    var selectedTab by remember { mutableStateOf(AdminTab.M3U8_CHECKER) }

    // Dialog states
    var showAddChannelDialog by remember { mutableStateOf(false) }
    var channelBeingEdited by remember { mutableStateOf<SportsChannel?>(null) }
    var showAddMatchDialog by remember { mutableStateOf(false) }
    var matchBeingEdited by remember { mutableStateOf<LiveMatch?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = GoldYellow,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Admin Control Panel",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyDark)
            )
        },
        containerColor = NavyDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Admin Tabs Navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NavySurface)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdminTab.values().forEach { tab ->
                    val isSelected = tab == selectedTab
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldYellow,
                            selectedLabelColor = Color.Black,
                            containerColor = NavySurfaceVariant,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) GoldYellow else NavyBorder
                        )
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                AdminTab.M3U8_CHECKER -> {
                    M3U8StreamCheckerTab(
                        repository = repository,
                        onTestPlayStream = onTestPlayStream
                    )
                }
                AdminTab.CHANNELS -> {
                    ChannelsManagerTab(
                        channels = channels,
                        onAddChannelClick = {
                            channelBeingEdited = null
                            showAddChannelDialog = true
                        },
                        onEditChannelClick = { ch ->
                            channelBeingEdited = ch
                            showAddChannelDialog = true
                        },
                        onDeleteChannel = { repository.deleteChannel(it) },
                        onTestPlayStream = onTestPlayStream
                    )
                }
                AdminTab.MATCHES -> {
                    MatchesManagerTab(
                        matches = matches,
                        onAddMatchClick = {
                            matchBeingEdited = null
                            showAddMatchDialog = true
                        },
                        onEditMatchClick = { match ->
                            matchBeingEdited = match
                            showAddMatchDialog = true
                        },
                        onDeleteMatch = { repository.deleteMatch(it) },
                        onToggleStatus = { match ->
                            val nextStatus = when (match.status) {
                                MatchStatus.UPCOMING -> MatchStatus.LIVE
                                MatchStatus.LIVE -> MatchStatus.FINISHED
                                MatchStatus.FINISHED -> MatchStatus.LIVE
                            }
                            repository.addOrUpdateMatch(match.copy(status = nextStatus))
                        },
                        onTestPlayStream = onTestPlayStream
                    )
                }
                AdminTab.SETTINGS -> {
                    AppSettingsTab(
                        config = config,
                        cloudSyncStatus = cloudSyncStatus,
                        onUpdateConfig = { repository.updateConfig(it) },
                        onResetDefaults = { repository.resetToDefaults() },
                        onSyncRemote = { repository.syncFromRemoteFeed(it) },
                        onPushAllToCloud = { repository.pushAllToCloudServer() },
                        onExportDataJson = { repository.exportCurrentDataAsJson() }
                    )
                }
            }
        }
    }

    // Add or Edit Channel Dialog with Multiple Streams Available
    if (showAddChannelDialog) {
        AddChannelModalDialog(
            channelToEdit = channelBeingEdited,
            onDismiss = {
                showAddChannelDialog = false
                channelBeingEdited = null
            },
            onSaveChannel = { newChannel ->
                repository.addOrUpdateChannel(newChannel)
                showAddChannelDialog = false
                channelBeingEdited = null
            },
            onCheckUrl = { url, callback ->
                coroutineScope.launch {
                    val res = repository.checkM3U8Stream(url)
                    callback(res)
                }
            }
        )
    }

    // Add or Edit Match Dialog with Multiple Streams Available
    if (showAddMatchDialog) {
        AddMatchModalDialog(
            matchToEdit = matchBeingEdited,
            onDismiss = {
                showAddMatchDialog = false
                matchBeingEdited = null
            },
            onSaveMatch = { savedMatch ->
                repository.addOrUpdateMatch(savedMatch)
                showAddMatchDialog = false
                matchBeingEdited = null
            },
            onCheckUrl = { url, callback ->
                coroutineScope.launch {
                    val res = repository.checkM3U8Stream(url)
                    callback(res)
                }
            }
        )
    }
}

// TAB 1: M3U8 STREAM CHECKER
@Composable
fun M3U8StreamCheckerTab(
    repository: SportsStreamRepository,
    onTestPlayStream: (StreamSource) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var urlInput by remember { mutableStateOf("https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8") }
    var isChecking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<M3U8CheckResult?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NavyBorder, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = NavySurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "M3U8 & HLS Stream Health Checker",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Verify whether an M3U8 live stream is online, check latency, response code, and test play directly.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_m3u8_url_input"),
                        label = { Text("Stream URL (M3U8 / HLS / MP4)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = NavyBorder,
                            focusedContainerColor = NavyDark,
                            unfocusedContainerColor = NavyDark
                        ),
                        singleLine = false,
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Preset Test URLs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Sample Streams:",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary),
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        AssistChip(
                            onClick = { urlInput = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8" },
                            label = { Text("Akamai Live", fontSize = 11.sp) }
                        )
                        AssistChip(
                            onClick = { urlInput = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8" },
                            label = { Text("Mux HLS", fontSize = 11.sp) }
                        )
                        AssistChip(
                            onClick = { urlInput = "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8" },
                            label = { Text("Sintel HLS", fontSize = 11.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            isChecking = true
                            checkResult = null
                            coroutineScope.launch {
                                val res = repository.checkM3U8Stream(urlInput)
                                checkResult = res
                                isChecking = false
                            }
                        },
                        enabled = !isChecking && urlInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("admin_check_stream_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing Stream...", color = Color.Black, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Check Stream Now", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Check Result Card
        checkResult?.let { result ->
            item {
                val isSuccess = result.isOnline
                val cardBorder = if (isSuccess) LiveGreen else LiveRed
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, cardBorder, RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = NavySurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (isSuccess) LiveGreen else LiveRed)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isSuccess) "STREAM ONLINE" else "STREAM OFFLINE / ERROR",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSuccess) LiveGreen else LiveRed
                                    )
                                )
                            }

                            if (result.httpStatusCode > 0) {
                                Text(
                                    text = "HTTP ${result.httpStatusCode}",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = result.message,
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column {
                                Text("Latency", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                                Text("${result.latencyMs} ms", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = CyanAccent))
                            }
                            Column {
                                Text("Content-Type", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                                Text(result.contentType, style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column {
                                Text("HLS Format", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                                Text(if (result.isHlsStream) "Valid HLS" else "Unknown", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = if (result.isHlsStream) LiveGreen else GoldYellow))
                            }
                        }

                        if (isSuccess) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    onTestPlayStream(
                                        StreamSource(
                                            name = "M3U8 Test Stream",
                                            quality = "FHD",
                                            url = result.url
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = NavySurfaceVariant),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = CyanAccent)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test Play Stream In Player", color = CyanAccent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// TAB 2: CHANNELS & MULTIPLE STREAMS MANAGER
@Composable
fun ChannelsManagerTab(
    channels: List<SportsChannel>,
    onAddChannelClick: () -> Unit,
    onEditChannelClick: (SportsChannel) -> Unit = {},
    onDeleteChannel: (String) -> Unit,
    onTestPlayStream: (StreamSource) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Button(
                onClick = onAddChannelClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("admin_add_channel_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add Channel With Multiple Streams",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                )
            }
        }

        item {
            Text(
                text = "EXISTING CHANNELS (${channels.size})",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
            )
        }

        items(channels, key = { it.id }) { ch ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NavyBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = NavySurface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (ch.logoText.isNotBlank()) ch.logoText else ch.name.take(3),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = ch.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Text(
                                    text = "${ch.category} • ${ch.streams.size} Stream Servers",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onEditChannelClick(ch) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = CyanAccent)
                            }
                            IconButton(onClick = { onDeleteChannel(ch.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LiveRed)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Streams list
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ch.streams.forEach { s ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NavySurfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    QualityBadge(quality = s.quality)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = s.name,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(
                                    onClick = { onTestPlayStream(s) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = CyanAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// TAB 3: MATCHES & MULTIPLE STREAMS MANAGER
@Composable
fun MatchesManagerTab(
    matches: List<LiveMatch>,
    onAddMatchClick: () -> Unit,
    onEditMatchClick: (LiveMatch) -> Unit,
    onDeleteMatch: (String) -> Unit,
    onToggleStatus: (LiveMatch) -> Unit,
    onTestPlayStream: (StreamSource) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Button(
                onClick = onAddMatchClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("admin_add_match_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add Live Match With Multi-Streams",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                )
            }
        }

        item {
            Text(
                text = "ACTIVE MATCHES (${matches.size})",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
            )
        }

        items(matches, key = { it.id }) { m ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NavyBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = NavySurface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = m.tournament,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                            Text(
                                text = "${m.team1Name} vs ${m.team2Name}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Status Toggle
                            AssistChip(
                                onClick = { onToggleStatus(m) },
                                label = {
                                    Text(
                                        text = m.status.name,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            color = if (m.status == MatchStatus.LIVE) LiveRed else CyanAccent
                                        )
                                    )
                                }
                            )

                            IconButton(onClick = { onDeleteMatch(m.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LiveRed)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Score / Time: ${m.timeOrScore} • ${m.streams.size} Multiple Streams configured",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        m.streams.forEach { s ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NavySurfaceVariant)
                                    .clickable { onTestPlayStream(s) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    QualityBadge(quality = s.quality)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(s.name, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Edit streams and match action button
                    OutlinedButton(
                        onClick = { onEditMatchClick(m) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(CyanAccent)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Edit Streams & Channels (${m.streams.size} Available)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// TAB 4: APP CONTROL & ANNOUNCEMENT SETTINGS
@Composable
fun AppSettingsTab(
    config: AppConfig,
    cloudSyncStatus: String = "Connected to Real-Time Cloud Server",
    onUpdateConfig: (AppConfig) -> Unit,
    onResetDefaults: () -> Unit,
    onSyncRemote: suspend (String) -> Result<Int>,
    onPushAllToCloud: (suspend () -> Result<Int>)? = null,
    onExportDataJson: (() -> String)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var announcement by remember(config.announcement) { mutableStateOf(config.announcement) }
    var telegramLink by remember(config.telegramLink) { mutableStateOf(config.telegramLink) }
    var adminPin by remember(config.adminPin) { mutableStateOf(config.adminPin) }
    var appTitle by remember(config.appTitle) { mutableStateOf(config.appTitle) }
    var remoteFeedUrl by remember(config.remoteFeedUrl) { mutableStateOf(config.remoteFeedUrl) }
    var isSyncingRemote by remember { mutableStateOf(false) }
    var syncStatusMessage by remember { mutableStateOf<String?>(null) }
    var isPushingCloud by remember { mutableStateOf(false) }
    var cloudPushMessage by remember { mutableStateOf<String?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var autoSyncOnLaunch by remember(config.autoSyncOnLaunch) { mutableStateOf(config.autoSyncOnLaunch) }
    var showSampleJsonModal by remember { mutableStateOf(false) }
    var showExportJsonModal by remember { mutableStateOf(false) }
    var exportedJsonText by remember { mutableStateOf("") }
    var exportCopiedToast by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    var copiedToast by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.2.dp, LiveGreen.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = NavySurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = LiveGreen,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Real-Time Cloud Server (Firestore)",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "অ্যাডমিন প্যানেল বা ওয়েব কনসোল (Firebase Console -> cloud_items) থেকে যেকোনো ম্যাচ, চ্যানেল বা স্ট্রিম পরিবর্তন করলে সকল ইউজারের অ্যাপে অটোমেটিক রিয়েল-টাইম আপডেট চলে যাবে।",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Status: $cloudSyncStatus",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = LiveGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            isPushingCloud = true
                            cloudPushMessage = null
                            coroutineScope.launch {
                                val res = onPushAllToCloud?.invoke()
                                isPushingCloud = false
                                cloudPushMessage = res?.fold(
                                    onSuccess = { "✓ Successfully published $it items to Cloud Server for all users!" },
                                    onFailure = { "Cloud sync error: ${it.localizedMessage}" }
                                )
                            }
                        },
                        enabled = !isPushingCloud,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_push_cloud_server_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = LiveGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isPushingCloud) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Publishing to Cloud Server...", color = Color.Black, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Push All Matches & Channels to Cloud Server", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    cloudPushMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (msg.startsWith("✓")) LiveGreen else LiveRed,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NavyBorder, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = NavySurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "App Control & Announcement Broadcast",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = appTitle,
                        onValueChange = { appTitle = it },
                        label = { Text("App Title") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = NavyBorder,
                            focusedContainerColor = NavyDark,
                            unfocusedContainerColor = NavyDark
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = announcement,
                        onValueChange = { announcement = it },
                        label = { Text("Broadcast Banner Message") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = NavyBorder,
                            focusedContainerColor = NavyDark,
                            unfocusedContainerColor = NavyDark
                        ),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = telegramLink,
                        onValueChange = { telegramLink = it },
                        label = { Text("Telegram Community URL") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = NavyBorder,
                            focusedContainerColor = NavyDark,
                            unfocusedContainerColor = NavyDark
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = adminPin,
                        onValueChange = { adminPin = it },
                        label = { Text("Admin Passcode PIN") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = NavyBorder,
                            focusedContainerColor = NavyDark,
                            unfocusedContainerColor = NavyDark
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            onUpdateConfig(
                                config.copy(
                                    appTitle = appTitle,
                                    announcement = announcement,
                                    telegramLink = telegramLink,
                                    adminPin = adminPin,
                                    remoteFeedUrl = remoteFeedUrl,
                                    autoSyncOnLaunch = autoSyncOnLaunch
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save Settings", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // CARD 2: RAW JSON / PASTEBIN LIVE UPDATE CONTROL
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = NavySurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Online Feed (Pastebin / Raw JSON)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "APK রি-বিল্ড না করেই প্রতিদিনের নতুন ম্যাচ ও চ্যানেল অনলাইন (Pastebin বা GitHub Raw) থেকে রিয়েল-টাইমে আপডেট করুন।",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = remoteFeedUrl,
                        onValueChange = { remoteFeedUrl = it },
                        label = { Text("Pastebin or Raw JSON URL") },
                        placeholder = { Text("https://pastebin.com/raw/xxxx or raw.githubusercontent.com/...") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = NavyBorder,
                            focusedContainerColor = NavyDark,
                            unfocusedContainerColor = NavyDark
                        ),
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "টিপস: pastebin.com/xxxx লিঙ্ক দিলেও অ্যাপ নিজে থেকে /raw/ লিঙ্কে রূপান্তর করে নিবে।",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = GoldYellow,
                            fontSize = 11.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Auto-sync toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(NavyDark)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Sync on App Startup",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "ইউজার অ্যাপ ওপেন করলেই স্বয়ংক্রিয়ভাবে অনলাইন ফিড লোড হবে",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                            )
                        }
                        Switch(
                            checked = autoSyncOnLaunch,
                            onCheckedChange = {
                                autoSyncOnLaunch = it
                                onUpdateConfig(config.copy(autoSyncOnLaunch = it))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = CyanAccent
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                isSyncingRemote = true
                                syncStatusMessage = null
                                coroutineScope.launch {
                                    val result = onSyncRemote(remoteFeedUrl)
                                    isSyncingRemote = false
                                    syncStatusMessage = result.fold(
                                        onSuccess = { "Synced $it items from remote feed successfully!" },
                                        onFailure = { "Sync failed: ${it.localizedMessage}" }
                                    )
                                }
                            },
                            enabled = !isSyncingRemote && remoteFeedUrl.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isSyncingRemote) {
                                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sync Now", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                exportedJsonText = onExportDataJson?.invoke() ?: "{}"
                                exportCopiedToast = false
                                showExportJsonModal = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(GoldYellow)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldYellow)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = GoldYellow, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export JSON", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showSampleJsonModal = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(CyanAccent)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("View Sample JSON Template", fontWeight = FontWeight.SemiBold)
                    }

                    syncStatusMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = NavyDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                modifier = Modifier.padding(10.dp),
                                color = if (msg.startsWith("Synced")) LiveGreen else LiveRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NavyBorder, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = NavySurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Reset & Restore Feeds",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = LiveRed
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Restore default sports matches, channels, and tested stream servers.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showResetConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = LiveRed),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(LiveRed))
                    ) {
                        Text("Reset to Default Stream Feeds")
                    }
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset to Defaults?") },
            text = { Text("This will reload all preloaded sports matches, test streams, and channels.") },
            confirmButton = {
                Button(
                    onClick = {
                        onResetDefaults()
                        showResetConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveRed)
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = NavyDark
        )
    }

    if (showSampleJsonModal) {
        val sampleJson = """
{
  "announcement": "Welcome to Football Live TV! Enjoy today's high-voltage matches in Ultra HD.",
  "telegram": "https://t.me/sports_live_streams",
  "matches": [
    {
      "tournament": "UEFA CHAMPIONS LEAGUE",
      "sport": "FOOTBALL",
      "team1": "Real Madrid",
      "team2": "Manchester City",
      "status": "LIVE",
      "score": "2 - 1",
      "time": "LIVE 74'",
      "streams": [
        {
          "name": "Server 1 - Willow HD",
          "url": "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8",
          "quality": "1080p FHD"
        },
        {
          "name": "Server 2 - Sky Sports",
          "url": "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
          "quality": "720p HD"
        }
      ]
    },
    {
      "tournament": "ICC CHAMPIONS TROPHY",
      "sport": "CRICKET",
      "team1": "Pakistan",
      "team2": "India",
      "status": "LIVE",
      "score": "PAK 214/4 (38.2 ov)",
      "time": "LIVE",
      "streams": [
        {
          "name": "Server 1 - PTV Sports Live",
          "url": "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8",
          "quality": "1080p FHD"
        }
      ]
    }
  ],
  "channels": [
    {
      "name": "T Sports HD",
      "category": "Live Sports",
      "url": "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"
    },
    {
      "name": "Sony Sports Ten 1",
      "category": "Cricket & Football",
      "url": "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
    }
  ]
}
""".trimIndent()

        Dialog(onDismissRequest = { showSampleJsonModal = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(vertical = 12.dp)
                    .border(1.dp, CyanAccent, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = NavyDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Code, contentDescription = null, tint = CyanAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Pastebin JSON Template",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }
                        IconButton(onClick = { showSampleJsonModal = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        "এই JSON কপি করে pastebin.com এ পেস্ট করে Create Paste করুন, তারপর লিঙ্কটি অ্যাপে দিন:",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(NavySurface)
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = sampleJson,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = GoldYellow,
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (copiedToast) {
                        Text(
                            "✓ JSON copied to clipboard! Paste it on Pastebin.com",
                            color = LiveGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(sampleJson))
                                copiedToast = true
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy JSON", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                remoteFeedUrl = "https://raw.githubusercontent.com/freefirejafor-wq/Upder/main/matches.json"
                                onUpdateConfig(config.copy(remoteFeedUrl = remoteFeedUrl))
                                showSampleJsonModal = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = NavySurfaceVariant),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Use Demo URL", color = CyanAccent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showExportJsonModal) {
        Dialog(onDismissRequest = { showExportJsonModal = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f),
                colors = CardDefaults.cardColors(containerColor = NavyDark),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldYellow)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = GoldYellow)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Live Data JSON Export",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }
                        IconButton(onClick = { showExportJsonModal = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        "১ ক্লিকে পুরো JSON কপি করুন এবং আপনার Pastebin বা GitHub-এ পেস্ট করে সেভ করুন। এতে সাথে সাথে পৃথিবীর সব ইউজারের অ্যাপ লাইভ আপডেট পেয়ে যাবে!",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(NavySurface)
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = exportedJsonText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = GoldYellow,
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (exportCopiedToast) {
                        Text(
                            "✓ JSON কপি করা হয়েছে! Pastebin.com বা GitHub এ গিয়ে পেস্ট করে সেভ করুন।",
                            color = LiveGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(exportedJsonText))
                            exportCopiedToast = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldYellow),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Full JSON to Clipboard", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// MODAL DIALOG: ADD OR EDIT CHANNEL WITH MULTIPLE STREAMS AVAILABLE
@Composable
fun AddChannelModalDialog(
    channelToEdit: SportsChannel? = null,
    onDismiss: () -> Unit,
    onSaveChannel: (SportsChannel) -> Unit,
    onCheckUrl: (String, (M3U8CheckResult) -> Unit) -> Unit
) {
    var channelName by remember(channelToEdit) { mutableStateOf(channelToEdit?.name ?: "") }
    var category by remember(channelToEdit) { mutableStateOf(channelToEdit?.category ?: "Sports") }
    var logoText by remember(channelToEdit) { mutableStateOf(channelToEdit?.logoText ?: "") }
    var logoUrl by remember(channelToEdit) { mutableStateOf(channelToEdit?.logoUrl ?: "") }

    // Dynamic list of multiple streams
    var streamsList by remember(channelToEdit) {
        mutableStateOf(
            if (channelToEdit != null && channelToEdit.streams.isNotEmpty()) {
                channelToEdit.streams
            } else {
                listOf(
                    StreamSource(name = "SERVER 1 (FHD)", quality = "FHD", url = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8", isDefault = true),
                    StreamSource(name = "SERVER 2 (HD)", quality = "HD", url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                )
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .border(1.dp, CyanAccent, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = NavyDark),
            shape = RoundedCornerShape(20.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = if (channelToEdit != null) "Edit Channel & Multi-Streams" else "Add Channel & Multi-Streams",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 17.sp
                        )
                    )
                }

                item {
                    OutlinedTextField(
                        value = channelName,
                        onValueChange = { channelName = it },
                        label = { Text("Channel Name (e.g. Willow Cricket)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = NavyBorder,
                            focusedContainerColor = NavySurface,
                            unfocusedContainerColor = NavySurface
                        )
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("Category") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = CyanAccent,
                                unfocusedBorderColor = NavyBorder,
                                focusedContainerColor = NavySurface,
                                unfocusedContainerColor = NavySurface
                            )
                        )
                        OutlinedTextField(
                            value = logoText,
                            onValueChange = { logoText = it },
                            label = { Text("Logo Badge") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = CyanAccent,
                                unfocusedBorderColor = NavyBorder,
                                focusedContainerColor = NavySurface,
                                unfocusedContainerColor = NavySurface
                            )
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "MULTIPLE STREAM SERVERS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent,
                                fontSize = 11.sp
                            )
                        )
                        TextButton(
                            onClick = {
                                val nextNum = streamsList.size + 1
                                streamsList = streamsList + StreamSource(
                                    name = "SERVER $nextNum (FHD)",
                                    quality = "FHD",
                                    url = ""
                                )
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Add Stream", color = CyanAccent, fontSize = 12.sp)
                        }
                    }
                }

                items(streamsList.size) { index ->
                    val s = streamsList[index]
                    var sName by remember(s.id) { mutableStateOf(s.name) }
                    var sQuality by remember(s.id) { mutableStateOf(s.quality) }
                    var sUrl by remember(s.id) { mutableStateOf(s.url) }
                    var testStatus by remember(s.id) { mutableStateOf<String?>(null) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, NavyBorder, RoundedCornerShape(10.dp)),
                        colors = CardDefaults.cardColors(containerColor = NavySurface)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Stream #${index + 1}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                if (streamsList.size > 1) {
                                    IconButton(
                                        onClick = {
                                            streamsList = streamsList.filterIndexed { i, _ -> i != index }
                                        },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = LiveRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = sName,
                                    onValueChange = {
                                        sName = it
                                        streamsList = streamsList.toMutableList().also { list -> list[index] = list[index].copy(name = it) }
                                    },
                                    label = { Text("Server Name", fontSize = 11.sp) },
                                    modifier = Modifier.weight(2f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = sQuality,
                                    onValueChange = {
                                        sQuality = it
                                        streamsList = streamsList.toMutableList().also { list -> list[index] = list[index].copy(quality = it) }
                                    },
                                    label = { Text("Quality", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = sUrl,
                                onValueChange = {
                                    sUrl = it
                                    streamsList = streamsList.toMutableList().also { list -> list[index] = list[index].copy(url = it) }
                                },
                                label = { Text("Stream URL (.m3u8)", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            // Quick check button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                testStatus?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (it.contains("Active")) LiveGreen else LiveRed,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    )
                                } ?: Spacer(modifier = Modifier.width(1.dp))

                                TextButton(
                                    onClick = {
                                        onCheckUrl(sUrl) { res ->
                                            testStatus = if (res.isOnline) "Active (${res.latencyMs}ms)" else "Offline"
                                        }
                                    }
                                ) {
                                    Text("Check M3U8", color = CyanAccent, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }

                        Button(
                            onClick = {
                                if (channelName.isNotBlank() && streamsList.isNotEmpty()) {
                                    onSaveChannel(
                                        SportsChannel(
                                            id = channelToEdit?.id ?: UUID.randomUUID().toString(),
                                            name = channelName,
                                            category = category,
                                            logoText = logoText,
                                            logoUrl = logoUrl,
                                            streams = streamsList.filter { it.url.isNotBlank() },
                                            isFavorite = channelToEdit?.isFavorite ?: false
                                        )
                                    )
                                }
                            },
                            enabled = channelName.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                        ) {
                            Text("Save Channel", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// MODAL DIALOG: ADD OR EDIT LIVE MATCH WITH MULTIPLE STREAMS AVAILABLE
@Composable
fun AddMatchModalDialog(
    matchToEdit: LiveMatch? = null,
    onDismiss: () -> Unit,
    onSaveMatch: (LiveMatch) -> Unit,
    onCheckUrl: (String, (M3U8CheckResult) -> Unit) -> Unit
) {
    var tournament by remember(matchToEdit) { mutableStateOf(matchToEdit?.tournament ?: "INTL FOOTBALL • FIFA WORLD CUP QUALIFIERS") }
    var sport by remember(matchToEdit) { mutableStateOf(matchToEdit?.sport ?: SportType.FOOTBALL) }
    var team1Name by remember(matchToEdit) { mutableStateOf(matchToEdit?.team1Name ?: "Argentina") }
    var team1Code by remember(matchToEdit) { mutableStateOf(matchToEdit?.team1Code ?: "ARG") }
    var team1Flag by remember(matchToEdit) { mutableStateOf(matchToEdit?.team1FlagUrl ?: "https://flagcdn.com/w160/ar.png") }

    var team2Name by remember(matchToEdit) { mutableStateOf(matchToEdit?.team2Name ?: "Brazil") }
    var team2Code by remember(matchToEdit) { mutableStateOf(matchToEdit?.team2Code ?: "BRA") }
    var team2Flag by remember(matchToEdit) { mutableStateOf(matchToEdit?.team2FlagUrl ?: "https://flagcdn.com/w160/br.png") }

    var timeOrScore by remember(matchToEdit) { mutableStateOf(matchToEdit?.timeOrScore ?: "2 - 1") }
    var matchTime by remember(matchToEdit) { mutableStateOf(matchToEdit?.matchTime ?: "Today, 09:00 PM") }
    var status by remember(matchToEdit) { mutableStateOf(matchToEdit?.status ?: MatchStatus.LIVE) }

    var streamsList by remember(matchToEdit) {
        mutableStateOf(
            if (matchToEdit != null && matchToEdit.streams.isNotEmpty()) {
                matchToEdit.streams
            } else {
                listOf(
                    StreamSource(name = "SERVER 1 (FHD)", quality = "FHD", url = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8", isDefault = true),
                    StreamSource(name = "SERVER 2 (HD)", quality = "HD", url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"),
                    StreamSource(name = "TAPMAD FHD", quality = "FHD", url = "https://diceyk6a7voy4.cloudfront.net/e78752a1-2e83-43fa-85ae-3d508be29366/hls/de:02b6989d-e714-4227-9c57-a4c1a221a244:playlist.m3u8")
                )
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .border(1.dp, CyanAccent, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = NavyDark),
            shape = RoundedCornerShape(20.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = if (matchToEdit != null) "Edit Match & Multiple Streams" else "Add Live Match With Multi-Streams",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 17.sp
                        )
                    )
                    Text(
                        text = "Configure match details, schedule time, automatic country/club flags, and stream servers",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                    )
                }

                item {
                    OutlinedTextField(
                        value = tournament,
                        onValueChange = { tournament = it },
                        label = { Text("Tournament / League Header") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    // Team 1
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = team1Name,
                            onValueChange = {
                                team1Name = it
                                val resolved = com.example.data.util.TeamFlagResolver.resolve(it)
                                if (resolved.flagUrl.isNotBlank()) {
                                    team1Code = resolved.code
                                    team1Flag = resolved.flagUrl
                                }
                            },
                            label = { Text("Team 1 Name") },
                            modifier = Modifier.weight(1.5f)
                        )
                        OutlinedTextField(
                            value = team1Code,
                            onValueChange = { team1Code = it },
                            label = { Text("Code") },
                            modifier = Modifier.weight(0.8f)
                        )
                        OutlinedTextField(
                            value = team1Flag,
                            onValueChange = { team1Flag = it },
                            label = { Text("Flag URL/Emoji") },
                            modifier = Modifier.weight(1.2f),
                            singleLine = true
                        )
                    }
                }

                item {
                    // Team 2
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = team2Name,
                            onValueChange = {
                                team2Name = it
                                val resolved = com.example.data.util.TeamFlagResolver.resolve(it)
                                if (resolved.flagUrl.isNotBlank()) {
                                    team2Code = resolved.code
                                    team2Flag = resolved.flagUrl
                                }
                            },
                            label = { Text("Team 2 Name") },
                            modifier = Modifier.weight(1.5f)
                        )
                        OutlinedTextField(
                            value = team2Code,
                            onValueChange = { team2Code = it },
                            label = { Text("Code") },
                            modifier = Modifier.weight(0.8f)
                        )
                        OutlinedTextField(
                            value = team2Flag,
                            onValueChange = { team2Flag = it },
                            label = { Text("Flag URL/Emoji") },
                            modifier = Modifier.weight(1.2f),
                            singleLine = true
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = timeOrScore,
                            onValueChange = { timeOrScore = it },
                            label = { Text("Score / Countdown") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = matchTime,
                            onValueChange = { matchTime = it },
                            label = { Text("Match Time / Date") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }

                item {
                    // Sport & Status Selector Row
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "SPORT & MATCH STATUS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SportType.values().filter { it != SportType.ALL }.forEach { sType ->
                                FilterChip(
                                    selected = sport == sType,
                                    onClick = { sport = sType },
                                    label = { Text(sType.displayName, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
                                        selectedLabelColor = CyanAccent
                                    )
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MatchStatus.values().forEach { st ->
                                FilterChip(
                                    selected = status == st,
                                    onClick = { status = st },
                                    label = { Text(st.name, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = if (st == MatchStatus.LIVE) LiveRed.copy(alpha = 0.2f) else GoldYellow.copy(alpha = 0.2f),
                                        selectedLabelColor = if (st == MatchStatus.LIVE) LiveRed else GoldYellow
                                    )
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "MULTIPLE STREAMS AVAILABLE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent,
                                fontSize = 11.sp
                            )
                        )
                        TextButton(
                            onClick = {
                                val nextNum = streamsList.size + 1
                                streamsList = streamsList + StreamSource(
                                    name = "SERVER $nextNum (FHD)",
                                    quality = "FHD",
                                    url = ""
                                )
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Add Stream", color = CyanAccent, fontSize = 12.sp)
                        }
                    }
                }

                items(streamsList.size) { index ->
                    val s = streamsList[index]
                    var sName by remember(s.id) { mutableStateOf(s.name) }
                    var sQuality by remember(s.id) { mutableStateOf(s.quality) }
                    var sUrl by remember(s.id) { mutableStateOf(s.url) }
                    var testStatus by remember(s.id) { mutableStateOf<String?>(null) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, NavyBorder, RoundedCornerShape(10.dp)),
                        colors = CardDefaults.cardColors(containerColor = NavySurface)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Stream Server #${index + 1}", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
                                if (streamsList.size > 1) {
                                    IconButton(
                                        onClick = { streamsList = streamsList.filterIndexed { i, _ -> i != index } },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, tint = LiveRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = sName,
                                    onValueChange = {
                                        sName = it
                                        streamsList = streamsList.toMutableList().also { list -> list[index] = list[index].copy(name = it) }
                                    },
                                    label = { Text("Server Name", fontSize = 11.sp) },
                                    modifier = Modifier.weight(2f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = sQuality,
                                    onValueChange = {
                                        sQuality = it
                                        streamsList = streamsList.toMutableList().also { list -> list[index] = list[index].copy(quality = it) }
                                    },
                                    label = { Text("Quality", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            OutlinedTextField(
                                value = sUrl,
                                onValueChange = {
                                    sUrl = it
                                    streamsList = streamsList.toMutableList().also { list -> list[index] = list[index].copy(url = it) }
                                },
                                label = { Text("Stream URL (.m3u8)", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                testStatus?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (it.contains("Active")) LiveGreen else LiveRed,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    )
                                } ?: Spacer(modifier = Modifier.width(1.dp))

                                TextButton(
                                    onClick = {
                                        onCheckUrl(sUrl) { res ->
                                            testStatus = if (res.isOnline) "Active (${res.latencyMs}ms)" else "Offline"
                                        }
                                    }
                                ) {
                                    Text("Check M3U8", color = CyanAccent, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }

                        Button(
                            onClick = {
                                if (team1Name.isNotBlank() && team2Name.isNotBlank()) {
                                    val v1 = com.example.data.util.TeamFlagResolver.resolve(team1Name, team1Code, team1Flag)
                                    val v2 = com.example.data.util.TeamFlagResolver.resolve(team2Name, team2Code, team2Flag)
                                    onSaveMatch(
                                        LiveMatch(
                                            id = matchToEdit?.id ?: UUID.randomUUID().toString(),
                                            tournament = tournament,
                                            sport = sport,
                                            team1Name = team1Name,
                                            team1Code = v1.code,
                                            team1FlagUrl = v1.flagUrl.ifBlank { v1.emoji },
                                            team2Name = team2Name,
                                            team2Code = v2.code,
                                            team2FlagUrl = v2.flagUrl.ifBlank { v2.emoji },
                                            status = status,
                                            timeOrScore = timeOrScore,
                                            matchTime = matchTime.ifBlank { "Today" },
                                            streams = streamsList.filter { it.url.isNotBlank() },
                                            isFavorite = matchToEdit?.isFavorite ?: false
                                        )
                                    )
                                }
                            },
                            enabled = team1Name.isNotBlank() && team2Name.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                        ) {
                            Text("Save Match", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
