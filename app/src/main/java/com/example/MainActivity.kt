package com.example

import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.data.model.*
import com.example.data.repository.SportsStreamRepository
import com.example.player.ExoPlayerManager
import com.example.ui.auth.AuthGateScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOut
import com.example.ui.components.*
import com.example.ui.dialogs.*
import com.example.ui.screens.*
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

enum class AppScreen {
    SPLASH,
    MAIN_TABS,
    PLAYER,
    ADMIN_PANEL
}

class MainActivity : ComponentActivity() {

    private lateinit var repository: SportsStreamRepository
    private lateinit var playerManager: ExoPlayerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = SportsStreamRepository(applicationContext)
        playerManager = ExoPlayerManager(applicationContext)

        setContent {
            MyApplicationTheme {
                FootballLiveTvApp(
                    repository = repository,
                    playerManager = playerManager,
                    onEnterPiP = { triggerPictureInPicture() }
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (playerManager.isPlaying.value) {
            triggerPictureInPicture()
        }
    }

    private fun triggerPictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerManager.release()
    }
}

@Composable
fun FootballLiveTvApp(
    repository: SportsStreamRepository,
    playerManager: ExoPlayerManager,
    onEnterPiP: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val authFlow = remember {
        runCatching { Firebase.auth.authStateFlow() }.getOrElse { kotlinx.coroutines.flow.flowOf(null) }
    }
    val initialUser = remember { runCatching { Firebase.auth.currentUser }.getOrNull() }
    val currentUser by authFlow.collectAsState(initial = initialUser)

    LaunchedEffect(currentUser?.uid) {
        val uid = currentUser?.uid
        if (uid != null) {
            repository.startCloudSyncForUser(uid)
        } else {
            repository.stopCloudSync()
        }
    }

    val matches by repository.matches.collectAsState()
    val crexState by repository.crexDashboardState.collectAsState()
    val channels by repository.channels.collectAsState()
    val categories by repository.categories.collectAsState()
    val highlights by repository.highlights.collectAsState()
    val config by repository.config.collectAsState()

    var currentScreen by remember { mutableStateOf(AppScreen.SPLASH) }
    var selectedTab by remember { mutableStateOf(NavTab.LIVE_EVENTS) }

    // Active playback target
    var activeMatch by remember { mutableStateOf<LiveMatch?>(null) }
    var activeChannelTitle by remember { mutableStateOf<String?>(null) }
    var activeStreams by remember { mutableStateOf<List<StreamSource>>(emptyList()) }

    // Filter states
    var selectedSport by remember { mutableStateOf(SportType.ALL) }
    var selectedStatus by remember { mutableStateOf<MatchStatus?>(null) }
    var selectedChannelCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    // Dialog states
    var showStreamPickerModal by remember { mutableStateOf(false) }
    var streamPickerMatch by remember { mutableStateOf<LiveMatch?>(null) }
    var streamPickerChannel by remember { mutableStateOf<SportsChannel?>(null) }
    var showNetworkStreamDialog by remember { mutableStateOf(false) }
    var showM3UImportDialog by remember { mutableStateOf(false) }
    var showHighlightsDialog by remember { mutableStateOf(false) }
    var showScoresDialog by remember { mutableStateOf(false) }
    var scoresSportTitle by remember { mutableStateOf("Cricket") }
    var showAnnouncementBanner by remember { mutableStateOf(true) }
    var showTelegramDialog by remember { mutableStateOf(false) }
    var showAdminPinDialog by remember { mutableStateOf(false) }

    val isPlayerPlaying by playerManager.isPlaying.collectAsState()
    val currentStream by playerManager.currentStream.collectAsState()

    fun openMatchModal(match: LiveMatch) {
        streamPickerMatch = match
        streamPickerChannel = null
        showStreamPickerModal = true
        // Fetch getSV3 match details on-demand ONLY when a user opens a match
        coroutineScope.launch {
            repository.loadMatchDetailOnDemand(match.id, force = false)
        }
    }

    fun startPlaying(match: LiveMatch?, title: String?, streams: List<StreamSource>) {
        val enabledStreams = streams.filter { it.isEnabled }
        activeMatch = match
        activeChannelTitle = title
        activeStreams = enabledStreams
        if (match != null) {
            coroutineScope.launch {
                repository.loadMatchDetailOnDemand(match.id, force = false)
            }
        }
        if (enabledStreams.isNotEmpty()) {
            val defaultStream = enabledStreams.find { it.isDefault } ?: enabledStreams.first()
            playerManager.playStream(defaultStream, config.forceLowQuality)
        }
        currentScreen = AppScreen.PLAYER
    }

    // 1. Auto-refresh Live list using admin-configured interval ONLY while the app is visible (STARTED/RESUMED).
    // Automatically stops polling when the app goes to background or is closed.
    LaunchedEffect(lifecycleOwner, config.autoRefreshIntervalSec, config.footballSyncEnabled, config.cricketSyncEnabled) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                repository.refreshLiveMatches(force = false)
                val intervalMs = config.autoRefreshIntervalSec.coerceIn(15, 120) * 1000L
                delay(intervalMs)
            }
        }
    }

    // 2. Refresh Upcoming every 15 minutes while the app is visible.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                repository.refreshUpcomingMatches(force = false)
                delay(15 * 60_000L)
            }
        }
    }

    // 3. Refresh Finished when opened (or when All/Scores modal is opened) and then no more often than every 30 minutes.
    LaunchedEffect(selectedStatus, showScoresDialog) {
        if (selectedStatus == MatchStatus.FINISHED || selectedStatus == null || showScoresDialog) {
            repository.refreshFinishedMatches(force = false)
        }
    }

    // Top Level Navigation
    when (currentScreen) {
        AppScreen.SPLASH -> {
            SplashScreen(
                config = config,
                onSplashFinished = {
                    currentScreen = AppScreen.MAIN_TABS
                }
            )
        }

        AppScreen.PLAYER -> {
            PlayerScreen(
                match = activeMatch,
                channelTitle = activeChannelTitle,
                streams = activeStreams,
                playerManager = playerManager,
                forceLowQuality = config.forceLowQuality,
                onBackClick = {
                    currentScreen = AppScreen.MAIN_TABS
                },
                otherMatches = matches.filter { it.id != activeMatch?.id },
                onSwitchMatch = { newMatch ->
                    startPlaying(newMatch, null, newMatch.publicEnabledStreams())
                },
                onFavoriteToggle = { repository.toggleMatchFavorite(it) },
                onEnterPiP = onEnterPiP
            )
        }

        AppScreen.ADMIN_PANEL -> {
            AdminPanelScreen(
                repository = repository,
                onBackClick = {
                    currentScreen = AppScreen.MAIN_TABS
                },
                onTestPlayStream = { stream ->
                    startPlaying(null, stream.name, listOf(stream))
                }
            )
        }

        AppScreen.MAIN_TABS -> {
            Scaffold(
                topBar = {
                    if (selectedTab != NavTab.LIVE_EVENTS) {
                        Column {
                            TopSportsBar(
                                title = config.appTitle,
                            onTelegramClick = { showTelegramDialog = true },
                            onRefreshClick = {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Refreshing 2026 live & upcoming matches...")
                                    val res = repository.syncFromRemoteFeed()
                                    res.fold(
                                        onSuccess = { count ->
                                            snackbarHostState.showSnackbar("Updated $count live & upcoming matches!")
                                        },
                                        onFailure = { err ->
                                            snackbarHostState.showSnackbar("Sync: ${err.localizedMessage ?: "Network error"}")
                                        }
                                    )
                                }
                            },
                            onSearchClick = { isSearching = !isSearching },
                            searchQuery = searchQuery,
                            isSearching = isSearching,
                            onSearchQueryChange = { searchQuery = it },
                            onCloseSearch = {
                                isSearching = false
                                searchQuery = ""
                            }
                        )

                        // Announcement Banner
                        if (showAnnouncementBanner && config.announcement.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(NavySurfaceVariant)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Campaign,
                                    contentDescription = null,
                                    tint = GoldYellow,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = config.announcement,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextPrimary,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                    IconButton(
                                        onClick = { showAnnouncementBanner = false },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                },
                snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState)
                },
                bottomBar = {
                    Column {
                        // Mini Now-Playing Bar when a stream was opened
                        if (currentStream != null && activeStreams.isNotEmpty()) {
                            val nowPlayingTitle = activeMatch?.let { "${it.team1Code} vs ${it.team2Code}" }
                                ?: activeChannelTitle
                                ?: currentStream?.name
                                ?: "Live Stream"
                            Surface(
                                color = NavySurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(0.5.dp, CyanAccent.copy(alpha = 0.4f))
                                    .clickable { currentScreen = AppScreen.PLAYER }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.LiveTv,
                                            contentDescription = null,
                                            tint = CyanAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = nowPlayingTitle,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary,
                                                    fontSize = 12.sp
                                                ),
                                                maxLines = 1
                                            )
                                            Text(
                                                text = currentStream?.name ?: "Tap to return to player",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = CyanAccent,
                                                    fontSize = 10.sp
                                                ),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                if (isPlayerPlaying) playerManager.pause() else playerManager.resume()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                if (isPlayerPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = "Play/Pause",
                                                tint = CyanAccent
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                playerManager.pause()
                                                activeStreams = emptyList()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Close mini player",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        BottomSportsNavBar(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it }
                        )
                    }
                },
                containerColor = NavyDark
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    when (selectedTab) {
                        NavTab.LIVE_EVENTS -> {
                            LiveEventsScreen(
                                matches = matches,
                                crexState = crexState,
                                selectedSport = selectedSport,
                                onSportSelect = { selectedSport = it },
                                selectedStatus = selectedStatus,
                                onStatusSelect = { selectedStatus = it },
                                onManualRefresh = {
                                    coroutineScope.launch {
                                        when (selectedStatus) {
                                            MatchStatus.LIVE -> {
                                                val s = repository.refreshLiveMatches(force = true)
                                                snackbarHostState.showSnackbar(
                                                    s.errorMessage ?: "Refreshed Live Football & Cricket matches"
                                                )
                                            }
                                            MatchStatus.UPCOMING -> {
                                                val s = repository.refreshUpcomingMatches(force = true)
                                                snackbarHostState.showSnackbar(
                                                    s.errorMessage ?: "Refreshed Upcoming Football & Cricket matches"
                                                )
                                            }
                                            MatchStatus.FINISHED -> {
                                                val s = repository.refreshFinishedMatches(force = true)
                                                snackbarHostState.showSnackbar(
                                                    s.errorMessage ?: "Refreshed Finished Football & Cricket matches"
                                                )
                                            }
                                            MatchStatus.UNKNOWN, null -> {
                                                val res = repository.syncFromRemoteFeed()
                                                res.fold(
                                                    onSuccess = { count ->
                                                        snackbarHostState.showSnackbar("Refreshed $count Football & Cricket matches")
                                                    },
                                                    onFailure = { err ->
                                                        snackbarHostState.showSnackbar("Sync Error: ${err.localizedMessage}")
                                                    }
                                                )
                                            }
                                        }
                                    }
                                },
                                onMatchClick = { match ->
                                    openMatchModal(match)
                                },
                                onFavoriteToggle = { repository.toggleMatchFavorite(it) },
                                onStreamSelectClick = { match ->
                                    openMatchModal(match)
                                },
                                searchQuery = searchQuery
                            )
                        }

                        NavTab.SPORTS -> {
                            SportsChannelsScreen(
                                channels = channels.filter { it.isEnabled },
                                selectedCategory = selectedChannelCategory,
                                onCategorySelect = { selectedChannelCategory = it },
                                onChannelClick = { channel ->
                                    val pubStreams = channel.publicEnabledStreams()
                                    if (pubStreams.size > 1) {
                                        streamPickerChannel = channel
                                        streamPickerMatch = null
                                        showStreamPickerModal = true
                                    } else {
                                        startPlaying(null, channel.name, pubStreams)
                                    }
                                },
                                onFavoriteToggle = { repository.toggleChannelFavorite(it) },
                                searchQuery = searchQuery
                            )
                        }

                        NavTab.CATEGORIES -> {
                            CategoriesScreen(
                                categories = categories,
                                searchQuery = searchQuery,
                                onCategoryClick = { cat ->
                                    val nameUpper = cat.name.uppercase()
                                    when {
                                        nameUpper.contains("LIVE EVENT") -> {
                                            selectedStatus = MatchStatus.LIVE
                                            selectedTab = NavTab.LIVE_EVENTS
                                        }
                                        nameUpper.contains("HIGHLIGHT") -> {
                                            showHighlightsDialog = true
                                        }
                                        else -> {
                                            val matchedChannelCat = channels
                                                .map { it.category }
                                                .firstOrNull { it.equals(cat.name, ignoreCase = true) }
                                                ?: "All"
                                            selectedChannelCategory = matchedChannelCat
                                            selectedTab = NavTab.SPORTS
                                        }
                                    }
                                }
                            )
                        }

                        NavTab.FAVOURITES -> {
                            FavouritesScreen(
                                favoriteMatches = matches.filter { it.isFavorite },
                                favoriteChannels = channels.filter { it.isFavorite },
                                searchQuery = searchQuery,
                                onMatchClick = { match ->
                                    openMatchModal(match)
                                },
                                onChannelClick = { channel ->
                                    val pubStreams = channel.publicEnabledStreams()
                                    if (pubStreams.size > 1) {
                                        streamPickerChannel = channel
                                        streamPickerMatch = null
                                        showStreamPickerModal = true
                                    } else {
                                        startPlaying(null, channel.name, pubStreams)
                                    }
                                },
                                onMatchFavoriteToggle = { repository.toggleMatchFavorite(it) },
                                onChannelFavoriteToggle = { repository.toggleChannelFavorite(it) },
                                onStreamSelectClick = { match ->
                                    openMatchModal(match)
                                }
                            )
                        }

                        NavTab.MENU -> {
                            MenuScreen(
                                config = config,
                                userEmail = currentUser?.email,
                                onNetworkStreamClick = { showNetworkStreamDialog = true },
                                onPlaylistsClick = { showM3UImportDialog = true },
                                onHighlightsClick = { showHighlightsDialog = true },
                                onCricketScoreClick = {
                                    scoresSportTitle = "Cricket"
                                    showScoresDialog = true
                                },
                                onFootballScoreClick = {
                                    scoresSportTitle = "Football"
                                    showScoresDialog = true
                                },
                                onFloatingPlayerClick = {
                                    if (currentStream == null && matches.isNotEmpty()) {
                                        val firstLive = matches.firstOrNull { it.status == MatchStatus.LIVE } ?: matches.first()
                                        startPlaying(firstLive, null, firstLive.publicEnabledStreams())
                                    }
                                    onEnterPiP()
                                },
                                onForceLowQualityToggle = { lowQuality ->
                                    repository.updateConfig(config.copy(forceLowQuality = lowQuality))
                                    currentStream?.let { active ->
                                        playerManager.playStream(active, lowQuality)
                                    }
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (lowQuality) "Data Saver enabled (SD 480p max)" else "HD/FHD quality unlocked"
                                        )
                                    }
                                },
                                onAdminPanelClick = { showAdminPinDialog = true },
                                onSignOutClick = {
                                    repository.stopCloudSync()
                                    signOut(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onSignOutComplete = {},
                                        scope = coroutineScope
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdminPinDialog) {
        AdminPinDialog(
            correctPin = config.adminPin,
            onDismiss = { showAdminPinDialog = false },
            onSuccess = {
                showAdminPinDialog = false
                currentScreen = AppScreen.ADMIN_PANEL
            }
        )
    }

    // Modal: Stream selection & on-demand CREX getSV3 match details
    if (showStreamPickerModal && (streamPickerMatch != null || streamPickerChannel != null)) {
        val currentMatch = streamPickerMatch
        val currentChannel = streamPickerChannel
        if (currentMatch != null) {
            val latestMatch = matches.find { it.id == currentMatch.id } ?: currentMatch
            val pubStreams = latestMatch.publicEnabledStreams()
            val detailState = crexState.activeDetailState.takeIf {
                it.matchId == latestMatch.id.removePrefix("crex-")
            }
            MultipleStreamsModalDialog(
                streams = pubStreams,
                currentSelectedStream = pubStreams.firstOrNull(),
                matchTitle = "${latestMatch.team1Name} vs ${latestMatch.team2Name}",
                matchTournament = latestMatch.tournament,
                matchStatus = latestMatch.status.name,
                matchDetailState = detailState,
                onRefreshDetail = {
                    coroutineScope.launch {
                        repository.loadMatchDetailOnDemand(latestMatch.id, force = true)
                    }
                },
                approvedHighlightUrl = latestMatch.approvedHighlightUrl,
                onWatchHighlight = { highlightUrl ->
                    showStreamPickerModal = false
                    streamPickerMatch = null
                    val stream = StreamSource(
                        id = "highlight-${latestMatch.id}",
                        name = "${latestMatch.team1Code} vs ${latestMatch.team2Code} Highlights",
                        url = highlightUrl,
                        quality = "HD"
                    )
                    startPlaying(latestMatch, stream.name, listOf(stream))
                },
                broadcastChannels = latestMatch.broadcastChannels,
                onStreamSelected = { selected ->
                    startPlaying(latestMatch, null, listOf(selected) + pubStreams.filter { it.id != selected.id })
                },
                onDismiss = {
                    showStreamPickerModal = false
                    streamPickerMatch = null
                }
            )
        } else if (currentChannel != null) {
            val pubStreams = currentChannel.publicEnabledStreams()
            MultipleStreamsModalDialog(
                streams = pubStreams,
                currentSelectedStream = pubStreams.firstOrNull(),
                matchTitle = currentChannel.name,
                matchTournament = currentChannel.category.uppercase(),
                matchStatus = "LIVE",
                onStreamSelected = { selected ->
                    startPlaying(null, currentChannel.name, listOf(selected) + pubStreams.filter { it.id != selected.id })
                },
                onDismiss = {
                    showStreamPickerModal = false
                    streamPickerChannel = null
                }
            )
        }
    }

    // Modal: Network stream URL player
    if (showNetworkStreamDialog) {
        NetworkStreamDialog(
            onDismiss = { showNetworkStreamDialog = false },
            onPlayUrl = { directStream ->
                startPlaying(null, directStream.name, listOf(directStream))
            }
        )
    }

    // Modal: M3U Playlist Importer
    if (showM3UImportDialog) {
        M3UPlaylistImportDialog(
            onDismiss = { showM3UImportDialog = false },
            onImport = { playlistData ->
                coroutineScope.launch {
                    val count = repository.importM3UPlaylist(playlistData)
                    snackbarHostState.showSnackbar("Successfully imported $count channels from M3U playlist!")
                }
            }
        )
    }

    // Modal: Highlights & Replays
    if (showHighlightsDialog) {
        HighlightsDialog(
            highlights = highlights,
            onPlayHighlight = { highlight ->
                showHighlightsDialog = false
                val stream = StreamSource(
                    id = highlight.id,
                    name = highlight.title,
                    url = highlight.videoUrl,
                    quality = "1080p HD"
                )
                startPlaying(null, highlight.title, listOf(stream))
            },
            onDismiss = { showHighlightsDialog = false }
        )
    }


    // Modal: Scores dialog
    if (showScoresDialog) {
        LiveScoresModalDialog(
            sportTitle = scoresSportTitle,
            matches = matches,
            onWatchMatch = { match ->
                openMatchModal(match)
            },
            onDismiss = { showScoresDialog = false }
        )
    }

    // Modal: Telegram community dialog
    if (showTelegramDialog) {
        AlertDialog(
            onDismissRequest = { showTelegramDialog = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null, tint = CyanAccent) },
            title = { Text("Join Telegram Community") },
            text = {
                Text("Join our sports live stream updates channel on Telegram to get instant match notifications, direct links, and server status.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(config.telegramLink))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback
                        }
                        showTelegramDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                ) {
                    Text("Open Telegram", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTelegramDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = NavyDark
        )
    }
}
