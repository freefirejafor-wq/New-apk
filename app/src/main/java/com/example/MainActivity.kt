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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.data.repository.SportsStreamRepository
import com.example.player.ExoPlayerManager
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
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val matches by repository.matches.collectAsState()
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
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    // Dialog states
    var showStreamPickerModal by remember { mutableStateOf(false) }
    var streamPickerMatch by remember { mutableStateOf<LiveMatch?>(null) }
    var showNetworkStreamDialog by remember { mutableStateOf(false) }
    var showM3UImportDialog by remember { mutableStateOf(false) }
    var showHighlightsDialog by remember { mutableStateOf(false) }
    var showAdminPinDialog by remember { mutableStateOf(false) }
    var showScoresDialog by remember { mutableStateOf(false) }
    var scoresSportTitle by remember { mutableStateOf("Football") }
    var showAnnouncementBanner by remember { mutableStateOf(true) }
    var showTelegramDialog by remember { mutableStateOf(false) }

    fun startPlaying(match: LiveMatch?, title: String?, streams: List<StreamSource>) {
        activeMatch = match
        activeChannelTitle = title
        activeStreams = streams
        if (streams.isNotEmpty()) {
            val defaultStream = streams.find { it.isDefault } ?: streams.first()
            playerManager.playStream(defaultStream, config.forceLowQuality)
        }
        currentScreen = AppScreen.PLAYER
    }

    // Automatic background sync from remote feed on startup
    LaunchedEffect(Unit) {
        if (config.autoSyncOnLaunch && config.remoteFeedUrl.isNotBlank()) {
            repository.syncFromRemoteFeed()
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
                onBackClick = {
                    currentScreen = AppScreen.MAIN_TABS
                },
                otherMatches = matches.filter { it.id != activeMatch?.id },
                onSwitchMatch = { newMatch ->
                    startPlaying(newMatch, null, newMatch.streams)
                },
                onEnterPiP = onEnterPiP
            )
        }

        AppScreen.ADMIN_PANEL -> {
            BackHandler {
                currentScreen = AppScreen.MAIN_TABS
            }
            AdminPanelScreen(
                repository = repository,
                onBackClick = { currentScreen = AppScreen.MAIN_TABS },
                onTestPlayStream = { testStream ->
                    startPlaying(
                        match = null,
                        title = testStream.name,
                        streams = listOf(testStream)
                    )
                }
            )
        }

        AppScreen.MAIN_TABS -> {
            Scaffold(
                topBar = {
                    Column {
                        TopSportsBar(
                            title = config.appTitle,
                            onTelegramClick = { showTelegramDialog = true },
                            onRefreshClick = {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Checking for live match & channel updates...")
                                    val res = repository.syncFromRemoteFeed()
                                    res.fold(
                                        onSuccess = { count ->
                                            snackbarHostState.showSnackbar("Synced $count items from online feed successfully!")
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
                },
                bottomBar = {
                    BottomSportsNavBar(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
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
                                selectedSport = selectedSport,
                                onSportSelect = { selectedSport = it },
                                selectedStatus = selectedStatus,
                                onStatusSelect = { selectedStatus = it },
                                onMatchClick = { match ->
                                    streamPickerMatch = match
                                    showStreamPickerModal = true
                                },
                                onFavoriteToggle = { repository.toggleMatchFavorite(it) },
                                onStreamSelectClick = { match ->
                                    streamPickerMatch = match
                                    showStreamPickerModal = true
                                },
                                searchQuery = searchQuery
                            )
                        }

                        NavTab.SPORTS -> {
                            SportsChannelsScreen(
                                channels = channels,
                                onChannelClick = { channel ->
                                    startPlaying(null, channel.name, channel.streams)
                                },
                                onFavoriteToggle = { repository.toggleChannelFavorite(it) },
                                searchQuery = searchQuery
                            )
                        }

                        NavTab.CATEGORIES -> {
                            CategoriesScreen(
                                categories = categories,
                                onCategoryClick = { cat ->
                                    searchQuery = cat.name
                                    selectedTab = NavTab.SPORTS
                                }
                            )
                        }

                        NavTab.FAVOURITES -> {
                            FavouritesScreen(
                                favoriteMatches = matches.filter { it.isFavorite },
                                favoriteChannels = channels.filter { it.isFavorite },
                                onMatchClick = { match ->
                                    streamPickerMatch = match
                                    showStreamPickerModal = true
                                },
                                onChannelClick = { channel ->
                                    startPlaying(null, channel.name, channel.streams)
                                },
                                onMatchFavoriteToggle = { repository.toggleMatchFavorite(it) },
                                onChannelFavoriteToggle = { repository.toggleChannelFavorite(it) },
                                onStreamSelectClick = { match ->
                                    streamPickerMatch = match
                                    showStreamPickerModal = true
                                }
                            )
                        }

                        NavTab.MENU -> {
                            MenuScreen(
                                config = config,
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
                                onFloatingPlayerClick = { onEnterPiP() },
                                onForceLowQualityToggle = {
                                    repository.updateConfig(config.copy(forceLowQuality = it))
                                },
                                onAdminPanelClick = {
                                    showAdminPinDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal: Stream selection from list
    if (showStreamPickerModal && streamPickerMatch != null) {
        val currentMatch = streamPickerMatch!!
        MultipleStreamsModalDialog(
            streams = currentMatch.streams,
            currentSelectedStream = currentMatch.streams.firstOrNull(),
            matchTitle = "${currentMatch.team1Name} vs ${currentMatch.team2Name}",
            matchTournament = currentMatch.tournament,
            matchStatus = currentMatch.status.name,
            onStreamSelected = { selected ->
                startPlaying(currentMatch, null, listOf(selected) + currentMatch.streams.filter { it.id != selected.id })
            },
            onDismiss = {
                showStreamPickerModal = false
                streamPickerMatch = null
            }
        )
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

    // Modal: Admin PIN verification
    if (showAdminPinDialog) {
        AdminPinDialog(
            correctPin = config.adminPin,
            onSuccess = {
                currentScreen = AppScreen.ADMIN_PANEL
            },
            onDismiss = { showAdminPinDialog = false }
        )
    }

    // Modal: Scores dialog
    if (showScoresDialog) {
        LiveScoresModalDialog(
            sportTitle = scoresSportTitle,
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
