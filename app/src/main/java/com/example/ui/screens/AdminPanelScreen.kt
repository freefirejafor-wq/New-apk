package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import com.example.R
import com.example.data.espn.EspnFootballService
import com.example.data.model.*
import com.example.data.repository.SportsStreamRepository
import com.example.data.util.TeamFlagResolver
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.onGoogleSignInClicked
import com.example.ui.components.MatchCard
import com.example.ui.theme.*
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

enum class AdminTab(val label: String, val icon: ImageVector) {
    MATCHES("Live Events", Icons.Default.Podcasts),
    CHANNELS("Sports TV", Icons.Default.SportsSoccer),
    M3U8_CHECKER("M3U8 Auto", Icons.Default.PlaylistAddCheck),
    SETTINGS("App Control", Icons.Default.FormatListBulleted)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    repository: SportsStreamRepository,
    onBackClick: () -> Unit,
    onTestPlayStream: (StreamSource) -> Unit
) {
    BackHandler(onBack = onBackClick)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val authFlow = remember {
        runCatching { Firebase.auth.authStateFlow() }.getOrElse { kotlinx.coroutines.flow.flowOf(null) }
    }
    val initialUser = remember { runCatching { Firebase.auth.currentUser }.getOrNull() }
    val currentUser by authFlow.collectAsState(initial = initialUser)

    val matches by repository.matches.collectAsState()
    val channels by repository.channels.collectAsState()
    val config by repository.config.collectAsState()
    val cloudSyncStatus by repository.cloudSyncStatus.collectAsState()
    val providerDiagnostics by repository.providerDiagnostics.collectAsState()

    var selectedTab by remember { mutableStateOf(AdminTab.MATCHES) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isPushingCloud by remember { mutableStateOf(false) }

    // Filter states for Admin Live Events tab (matching LiveEventsScreen)
    var selectedSport by remember { mutableStateOf(SportType.ALL) }
    var selectedStatus by remember { mutableStateOf<MatchStatus?>(null) }
    var selectedChannelCategory by remember { mutableStateOf("All") }

    // Modal Dialog states
    var showAddChannelDialog by remember { mutableStateOf(false) }
    var channelBeingEdited by remember { mutableStateOf<SportsChannel?>(null) }
    var showAddMatchDialog by remember { mutableStateOf(false) }
    var matchBeingEdited by remember { mutableStateOf<LiveMatch?>(null) }
    var showLeagueQuickModal by remember { mutableStateOf(false) }

    fun pushUpdatesToAllUsers(customMessage: String? = null) {
        coroutineScope.launch {
            isPushingCloud = true
            val res = repository.pushAllToCloudServer()
            isPushingCloud = false
            res.fold(
                onSuccess = { count ->
                    snackbarHostState.showSnackbar(
                        customMessage ?: "লাইভ আপডেট সফল! $count টি আইটেম সব ইউজারের ফোনে পাঠিয়ে দেওয়া হয়েছে।"
                    )
                },
                onFailure = {
                    snackbarHostState.showSnackbar(
                        customMessage ?: "লোকাল সেভ হয়েছে (সব ইউজারকে লাইভ আপডেট দিতে Google Sign-In করুন)"
                    )
                }
            )
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B111E))
                    .statusBarsPadding()
            ) {
                // Exact same TopSportsBar visual style with Back button, Logo, Title & right-side Telegram/Refresh/Search icons
                if (isSearching) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_search_input"),
                            placeholder = {
                                Text(
                                    "Search matches, leagues, channels, streams...",
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
                                Icon(Icons.Default.Search, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                IconButton(onClick = {
                                    isSearching = false
                                    searchQuery = ""
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close search", tint = TextSecondary)
                                }
                            }
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to App",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .border(1.2.dp, Color(0xFFFF5722), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_app_logo),
                                    contentDescription = "App Logo",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = config.appTitle,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            fontSize = 18.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = CyanAccent.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.border(0.8.dp, CyanAccent, RoundedCornerShape(6.dp))
                                    ) {
                                        Text(
                                            text = "ADMIN",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = CyanAccent,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 9.sp
                                            ),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (currentUser != null) "🟢 Cloud Live • ${currentUser?.email ?: "Synced"}" else "⚪ Local Mode • Sign in to broadcast",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (currentUser != null) LiveGreen else GoldYellow,
                                        fontSize = 10.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Exact same Telegram circle button with purple-pink ring border
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
                                    .clickable {
                                        selectedTab = AdminTab.SETTINGS
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Send,
                                    contentDescription = "Telegram Settings",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(15.dp)
                                        .offset(x = (-1).dp, y = 0.dp)
                                )
                            }

                            // Refresh / Cloud Push icon button
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Syncing live matches, playlists & cloud updates...")
                                            repository.refreshLiveMatches(force = true)
                                            pushUpdatesToAllUsers("সকল ম্যাচ, চ্যানেল ও প্লেলিস্ট রিফ্রেশ এবং ইউজারদের কাছে সিঙ্ক হয়েছে!")
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isPushingCloud) {
                                    CircularProgressIndicator(
                                        color = CyanAccent,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Refresh & Sync",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Search icon button
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable { isSearching = !isSearching },
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
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        bottomBar = {
            // Exact same floating rounded pill navigation bar as BottomSportsNavBar
            AdminBottomPillNavBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        },
        containerColor = Color(0xFF0B111E)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0B111E))
        ) {
            when (selectedTab) {
                AdminTab.MATCHES -> {
                    AdminLiveEventsSection(
                        matches = matches,
                        channels = channels,
                        config = config,
                        selectedSport = selectedSport,
                        onSportSelect = { selectedSport = it },
                        selectedStatus = selectedStatus,
                        onStatusSelect = { selectedStatus = it },
                        searchQuery = searchQuery,
                        onAddMatchClick = {
                            matchBeingEdited = null
                            showAddMatchDialog = true
                        },
                        onEditMatchClick = { match ->
                            matchBeingEdited = match
                            showAddMatchDialog = true
                        },
                        onDeleteMatchClick = { matchId ->
                            repository.deleteMatch(matchId)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("ম্যাচটি হাইড করা হয়েছে এবং সব ইউজারের জন্য আপডেট হয়েছে।")
                            }
                        },
                        onQuickStatusChange = { match, newStatus ->
                            val updated = match.copy(
                                status = newStatus,
                                timeOrScore = when (newStatus) {
                                    MatchStatus.LIVE -> "LIVE"
                                    MatchStatus.FINISHED -> "Finished"
                                    else -> match.timeOrScore
                                }
                            )
                            repository.addOrUpdateMatch(updated)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("${match.team1Code} vs ${match.team2Code} -> ${newStatus.name} সব ইউজারের জন্য আপডেট হয়েছে!")
                            }
                        },
                        onTestPlayStream = onTestPlayStream,
                        onOpenLeagueFilterModal = { showLeagueQuickModal = true },
                        onPushAllToCloud = { pushUpdatesToAllUsers() }
                    )
                }

                AdminTab.CHANNELS -> {
                    AdminSportsChannelsSection(
                        channels = channels,
                        selectedCategory = selectedChannelCategory,
                        onCategorySelect = { selectedChannelCategory = it },
                        searchQuery = searchQuery,
                        onAddChannelClick = {
                            channelBeingEdited = null
                            showAddChannelDialog = true
                        },
                        onEditChannelClick = { ch ->
                            channelBeingEdited = ch
                            showAddChannelDialog = true
                        },
                        onToggleChannelEnabled = { ch ->
                            val updated = ch.copy(isEnabled = !ch.isEnabled)
                            repository.addOrUpdateChannel(updated)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    "${updated.name} ${if (updated.isEnabled) "Enabled" else "Hidden"} for all users!"
                                )
                            }
                        },
                        onDeleteChannelClick = { chId ->
                            repository.deleteChannel(chId)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("চ্যানেলটি ডিলিট করা হয়েছে এবং সব ইউজারের জন্য সিঙ্ক হয়েছে।")
                            }
                        },
                        onTestPlayStream = onTestPlayStream,
                        onCleanDeadLinks = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Scanning all channel servers for dead links...")
                                val removed = repository.removeDeadStreamsFromChannels()
                                snackbarHostState.showSnackbar("Cleaned $removed offline links & synced active servers!")
                            }
                        }
                    )
                }

                AdminTab.M3U8_CHECKER -> {
                    AdminBulkM3u8ScannerSection(
                        repository = repository,
                        onTestPlayStream = onTestPlayStream,
                        onShowMessage = { msg ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(msg)
                            }
                        }
                    )
                }

                AdminTab.SETTINGS -> {
                    AdminAppControlSection(
                        config = config,
                        currentUserEmail = currentUser?.email,
                        cloudSyncStatus = cloudSyncStatus,
                        providerDiagnostics = providerDiagnostics,
                        onGoogleSignIn = {
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    val uid = Firebase.auth.currentUser?.uid
                                    if (uid != null) repository.startCloudSyncForUser(uid)
                                    pushUpdatesToAllUsers("Google Admin সংযুক্ত হয়েছে এবং ক্লাউড সিঙ্ক চালু হয়েছে!")
                                },
                                onAuthError = { err ->
                                    coroutineScope.launch { snackbarHostState.showSnackbar(err) }
                                },
                                scope = coroutineScope
                            )
                        },
                        onSaveConfig = { updatedConfig ->
                            repository.updateConfig(updatedConfig)
                            pushUpdatesToAllUsers("অ্যাপ কনফিগারেশন সেভ হয়েছে এবং সব ইউজারের ফোনে লাইভ আপডেট হয়েছে!")
                        },
                        onPushAllCloud = { pushUpdatesToAllUsers() },
                        onOpenLeagueModal = { showLeagueQuickModal = true },
                        onSyncRemoteFeedNow = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Syncing remote feed & live M3U playlists...")
                                val res = repository.syncFromRemoteFeed()
                                res.fold(
                                    onSuccess = { total ->
                                        snackbarHostState.showSnackbar("Synced $total matches & active streams!")
                                    },
                                    onFailure = { err ->
                                        snackbarHostState.showSnackbar("Sync error: ${err.localizedMessage}")
                                    }
                                )
                            }
                        },
                        onResetDefaults = {
                            repository.resetToDefaults()
                            pushUpdatesToAllUsers("ডিফল্ট চ্যানেল ও সেটিংস রিস্টোর করা হয়েছে।")
                        }
                    )
                }
            }
        }
    }

    // Modal: Add or Edit Channel with Multi-Streams & Custom Headers
    if (showAddChannelDialog || channelBeingEdited != null) {
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
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("${newChannel.name} সেভ হয়েছে এবং সব ইউজারের ফোনে আপডেট হয়েছে!")
                }
            },
            onCheckUrl = { url, callback ->
                coroutineScope.launch {
                    val res = repository.checkM3U8Stream(url)
                    callback(res)
                }
            }
        )
    }

    // Modal: Add or Edit Match with Multi-Streams, Channel Picker & Custom Headers
    if (showAddMatchDialog || matchBeingEdited != null) {
        AddMatchModalDialog(
            matchToEdit = matchBeingEdited,
            availableChannels = channels,
            onDismiss = {
                showAddMatchDialog = false
                matchBeingEdited = null
            },
            onSaveMatch = { newMatch ->
                repository.addOrUpdateMatch(newMatch)
                showAddMatchDialog = false
                matchBeingEdited = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("${newMatch.team1Name} vs ${newMatch.team2Name} এর স্ট্রিম সব ইউজারের জন্য আপডেট হয়েছে!")
                }
            },
            onCheckUrl = { url, callback ->
                coroutineScope.launch {
                    val res = repository.checkM3U8Stream(url)
                    callback(res)
                }
            }
        )
    }

    // Modal: Football & Cricket League Visibility Manager
    if (showLeagueQuickModal) {
        LeagueVisibilityDialog(
            config = config,
            onDismiss = { showLeagueQuickModal = false },
            onSaveConfig = { newConfig ->
                repository.updateConfig(newConfig)
                showLeagueQuickModal = false
                pushUpdatesToAllUsers("লিগ ফিল্টার সেভ হয়েছে এবং সব ইউজারের জন্য আপডেট হয়েছে!")
            }
        )
    }
}

@Composable
private fun AdminBottomPillNavBar(
    selectedTab: AdminTab,
    onTabSelected: (AdminTab) -> Unit
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
                AdminTab.values().forEach { tab ->
                    val isSelected = tab == selectedTab
                    val activeCyan = Color(0xFF00D1C1)
                    val inactiveGrey = Color(0xFF94A3B8)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(
                                if (isSelected) Color(0xFF223348) else Color.Transparent
                            )
                            .clickable { onTabSelected(tab) }
                            .testTag("admin_tab_${tab.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (isSelected) activeCyan else inactiveGrey,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isSelected) activeCyan else inactiveGrey,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 10.sp
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

// =========================================================================================
// TAB 1: ADMIN LIVE EVENTS SECTION (EXACT SAME UI AS LiveEventsScreen + ADMIN STREAM PINNING)
// =========================================================================================
@Composable
private fun AdminLiveEventsSection(
    matches: List<LiveMatch>,
    channels: List<SportsChannel>,
    config: AppConfig,
    selectedSport: SportType,
    onSportSelect: (SportType) -> Unit,
    selectedStatus: MatchStatus?,
    onStatusSelect: (MatchStatus?) -> Unit,
    searchQuery: String,
    onAddMatchClick: () -> Unit,
    onEditMatchClick: (LiveMatch) -> Unit,
    onDeleteMatchClick: (String) -> Unit,
    onQuickStatusChange: (LiveMatch, MatchStatus) -> Unit,
    onTestPlayStream: (StreamSource) -> Unit,
    onOpenLeagueFilterModal: () -> Unit,
    onPushAllToCloud: () -> Unit
) {
    val totalCount = matches.size
    val cricketCount = remember(matches) { matches.count { it.sport == SportType.CRICKET } }
    val footballCount = remember(matches) { matches.count { it.sport == SportType.FOOTBALL } }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B111E))
    ) {
        // 1. Exact same Top Sport Selector Row (All, Cricket, Football) from LiveEventsScreen
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
        }

        // 2. Exact same Status Filter Pills Row (All, Live, Upcoming, Finished)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusFilterPill(
                label = "All ($allStatusCount)",
                isSelected = selectedStatus == null,
                onClick = { onStatusSelect(null) },
                tag = "admin_filter_all"
            )
            StatusFilterPill(
                label = "Live ($liveStatusCount)",
                isSelected = selectedStatus == MatchStatus.LIVE,
                onClick = {
                    onStatusSelect(if (selectedStatus == MatchStatus.LIVE) null else MatchStatus.LIVE)
                },
                tag = "admin_filter_live"
            )
            StatusFilterPill(
                label = "Upcoming ($upcomingStatusCount)",
                isSelected = selectedStatus == MatchStatus.UPCOMING,
                onClick = {
                    onStatusSelect(if (selectedStatus == MatchStatus.UPCOMING) null else MatchStatus.UPCOMING)
                },
                tag = "admin_filter_upcoming"
            )
            StatusFilterPill(
                label = "Finished ($finishedStatusCount)",
                isSelected = selectedStatus == MatchStatus.FINISHED,
                onClick = {
                    onStatusSelect(if (selectedStatus == MatchStatus.FINISHED) null else MatchStatus.FINISHED)
                },
                tag = "admin_filter_finished"
            )
        }

        // 3. Compact Admin Action Bar (Add Match, League Visibility, Push to All Users)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onAddMatchClick,
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Match", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = onOpenLeagueFilterModal,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.6f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    "Leagues (${config.enabledFootballLeagues.size})",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }

            OutlinedButton(
                onClick = onPushAllToCloud,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, LiveGreen.copy(alpha = 0.7f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = LiveGreen, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("Sync to Users", color = LiveGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // 4. Match Cards List (Exact same MatchCard + Admin Stream Pinning & Control Strip)
        if (filteredMatches.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No Matches Found in Filter",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onAddMatchClick,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                    ) {
                        Text("+ Add Custom Live Match", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = filteredMatches,
                    key = { "adm_${it.sport.name}_${it.id}" }
                ) { match ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF111A2B))
                            .border(1.dp, Color(0xFF233248), RoundedCornerShape(18.dp))
                    ) {
                        // Exact same MatchCard seen by users
                        MatchCard(
                            match = match,
                            onClick = { onEditMatchClick(match) },
                            onFavoriteToggle = { onEditMatchClick(match) },
                            onStreamSelectClick = { onEditMatchClick(match) }
                        )

                        // Admin Control Bar attached to the bottom of the MatchCard
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0D1522))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Stream Pin / Edit button
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = CyanAccent.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .border(1.dp, CyanAccent, RoundedCornerShape(8.dp))
                                        .clickable { onEditMatchClick(match) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.SettingsInputAntenna,
                                            contentDescription = null,
                                            tint = CyanAccent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Pin/Edit Streams (${match.streams.size})",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = CyanAccent,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }

                                // Test Play Default Stream
                                if (match.streams.isNotEmpty()) {
                                    Surface(
                                        color = LiveGreen.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .border(1.dp, LiveGreen, RoundedCornerShape(8.dp))
                                            .clickable {
                                                val def = match.streams.find { it.isDefault } ?: match.streams.first()
                                                onTestPlayStream(def)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = "Test Play",
                                                tint = LiveGreen,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Play",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = LiveGreen,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            // Status Quick Switch & Hide Match
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val nextStatus = when (match.status) {
                                    MatchStatus.UPCOMING -> MatchStatus.LIVE
                                    MatchStatus.LIVE -> MatchStatus.FINISHED
                                    else -> MatchStatus.LIVE
                                }
                                TextButton(
                                    onClick = { onQuickStatusChange(match, nextStatus) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        text = "Set ${nextStatus.name}",
                                        color = GoldYellow,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                IconButton(
                                    onClick = { onDeleteMatchClick(match.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Hide Match",
                                        tint = LiveRed,
                                        modifier = Modifier.size(18.dp)
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

// =========================================================================================
// TAB 2: ADMIN SPORTS CHANNELS SECTION (EXACT SAME CIRCULAR LOGO STYLE AS SportsChannelsScreen)
// =========================================================================================
@Composable
private fun AdminSportsChannelsSection(
    channels: List<SportsChannel>,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    searchQuery: String,
    onAddChannelClick: () -> Unit,
    onEditChannelClick: (SportsChannel) -> Unit,
    onToggleChannelEnabled: (SportsChannel) -> Unit,
    onDeleteChannelClick: (String) -> Unit,
    onTestPlayStream: (StreamSource) -> Unit,
    onCleanDeadLinks: () -> Unit
) {
    val distinctCategories = remember(channels) {
        listOf("All") + channels.map { it.category }.filter { it.isNotBlank() }.distinct()
    }

    val filteredChannels = remember(channels, searchQuery, selectedCategory) {
        channels.filter { ch ->
            val matchesCat = selectedCategory.equals("All", ignoreCase = true) ||
                ch.category.equals(selectedCategory, ignoreCase = true)
            val matchesQuery = if (searchQuery.isBlank()) true else {
                ch.name.contains(searchQuery, ignoreCase = true) ||
                    ch.category.contains(searchQuery, ignoreCase = true) ||
                    ch.logoText.contains(searchQuery, ignoreCase = true)
            }
            matchesCat && matchesQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B111E))
            .padding(horizontal = 12.dp)
    ) {
        // Category Filter Row matching SportsChannelsScreen
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            distinctCategories.forEach { cat ->
                val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = { onCategorySelect(cat) },
                    label = {
                        Text(
                            text = cat,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
                        selectedLabelColor = CyanAccent,
                        containerColor = NavySurface,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) CyanAccent else NavyBorder
                    )
                )
            }
        }

        // Top Channel Admin Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onAddChannelClick,
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("+ Add TV Channel", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = onCleanDeadLinks,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldYellow.copy(alpha = 0.7f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(Icons.Default.CleaningServices, contentDescription = null, tint = GoldYellow, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("Remove Dead Links", color = GoldYellow, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredChannels, key = { it.id }) { channel ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            1.dp,
                            if (channel.isEnabled) NavyBorder else LiveRed.copy(alpha = 0.5f),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onEditChannelClick(channel) },
                    colors = CardDefaults.cardColors(containerColor = NavySurface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            // Exact same Circular Channel Logo Badge from SportsChannelsScreen
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .border(1.5.dp, NavyBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                val logoUrl = channel.logoUrl.trim()
                                if (logoUrl.startsWith("http://", true) || logoUrl.startsWith("https://", true)) {
                                    AsyncImage(
                                        model = logoUrl,
                                        contentDescription = channel.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(6.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    val text = if (channel.logoText.isNotBlank()) channel.logoText else channel.name.take(4).uppercase()
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = Color(0xFF0F172A),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp
                                        ),
                                        maxLines = 1
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = channel.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = CyanAccent.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = channel.category,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = CyanAccent,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "${channel.streams.size} servers • ${channel.streams.firstOrNull()?.url?.take(38) ?: "No stream"}...",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (channel.streams.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        val def = channel.streams.find { it.isDefault } ?: channel.streams.first()
                                        onTestPlayStream(def)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.PlayCircleFilled, contentDescription = "Test Play", tint = LiveGreen, modifier = Modifier.size(22.dp))
                                }
                            }

                            IconButton(
                                onClick = { onEditChannelClick(channel) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Channel", tint = CyanAccent, modifier = Modifier.size(18.dp))
                            }

                            Switch(
                                checked = channel.isEnabled,
                                onCheckedChange = { onToggleChannelEnabled(channel) },
                                modifier = Modifier.padding(horizontal = 2.dp),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = CyanAccent
                                )
                            )

                            IconButton(
                                onClick = { onDeleteChannelClick(channel.id) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Channel", tint = LiveRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================================
// TAB 3: BULK M3U / M3U8 SMART IMPORTER & LIVE HEALTH SCANNER (MODULE 1)
// =========================================================================================
@Composable
private fun AdminBulkM3u8ScannerSection(
    repository: SportsStreamRepository,
    onTestPlayStream: (StreamSource) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var bulkM3uInput by remember {
        mutableStateOf(
            """
            T Sports: https://robitv.com/rn/http://45.117.61.162/01_Tsports_HD/tracks-v1a1/mono.m3u8
            A Sports: https://tvsen6.aynaott.com/zv68oqPDu7MZZwmHhRxt/tracks-v1a1/mono.ts.m3u8
            PTV Sports: https://flussonic.defineatoz.com/PTV/index.m3u8
            Star Sports 2: https://tvsen7.aynaott.com/ssport2hd/index.m3u8
            Willow TV: https://tvsen5.aynaott.com/willowhd/index.m3u8
            Sony Sports 1: https://drk6xq0vhn.gpcdn.net/live/ten_1_hd_720/index.m3u8
            Sony Sports 2: https://drk6xq0vhn.gpcdn.net/live/ten_2_hd_720/index.m3u8
            Sony Sports 5: https://drk6xq0vhn.gpcdn.net/live/ten_5_hd_720/index.m3u8
            #EXTINF:-1 group-title="Sports",TyC Sports
            https://m.mxonlive.xyz/nexusstream/tycsport.m3u8
            #EXTINF:-1 group-title="Sports",Telemundo Deportes
            https://tda-nnaa-drct-pa6dy.fast.nbcuni.com/live/master_2.m3u8
            #EXTINF:-1 group-title="Sports",BeIN Sports Extra
            https://bein-esp-xumo.amagi.tv/playlistR1080p.m3u8
            """.trimIndent()
        )
    }

    var isScanning by remember { mutableStateOf(false) }
    var isApplying by remember { mutableStateOf(false) }
    var scannedItems by remember { mutableStateOf<List<BulkM3uScanItem>>(emptyList()) }

    val activeCount = remember(scannedItems) { scannedItems.count { it.checkResult?.isOnline == true } }
    val deadCount = remember(scannedItems) { scannedItems.count { it.checkResult?.isOnline == false } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B111E))
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "⚡ Bulk M3U / M3U8 Auto-Detector",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )
                            Text(
                                text = "একসাথে অনেকগুলো #EXTINF বা .m3u8 লিংক পেস্ট করুন। কোনটা Active তা অটো চেক করে নির্দিষ্ট খেলা ও চ্যানেলে যুক্ত হয়ে সব ইউজারের ফোনে আপডেট যাবে।",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        TextButton(onClick = { bulkM3uInput = "" }) {
                            Text("Clear", color = LiveRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = bulkM3uInput,
                        onValueChange = { bulkM3uInput = it },
                        label = { Text("Paste #EXTINF / Channel: https://...m3u8 / Raw M3U URL") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .testTag("bulk_m3u8_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = NavyBorder,
                            focusedContainerColor = Color(0xFF0E1624),
                            unfocusedContainerColor = Color(0xFF0E1624)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (bulkM3uInput.isBlank()) return@Button
                                coroutineScope.launch {
                                    isScanning = true
                                    scannedItems = repository.scanBulkM3uText(bulkM3uInput)
                                    isScanning = false
                                    val onCount = scannedItems.count { it.checkResult?.isOnline == true }
                                    onShowMessage("স্ক্যান সম্পন্ন: ${scannedItems.size}টির মধ্যে $onCount টি Active (🟢 LIVE) পাওয়া গেছে!")
                                }
                            },
                            enabled = !isScanning && bulkM3uInput.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scanning...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.Radar, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("1. Scan Active Links", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isApplying = true
                                    val itemsToApply = if (scannedItems.isNotEmpty()) {
                                        scannedItems
                                    } else {
                                        repository.scanBulkM3uText(bulkM3uInput)
                                    }
                                    scannedItems = itemsToApply
                                    val addedCount = repository.applyScannedM3uItems(itemsToApply, onlyActive = true)
                                    isApplying = false
                                    onShowMessage("সফলভাবে $addedCount টি সচল স্ট্রিম সংশ্লিষ্ট ম্যাচ ও চ্যানেলে যুক্ত হয়ে সব ইউজারের জন্য আপডেট হয়েছে!")
                                }
                            },
                            enabled = !isApplying && bulkM3uInput.isNotBlank(),
                            modifier = Modifier.weight(1.15f),
                            colors = ButtonDefaults.buttonColors(containerColor = LiveGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isApplying) {
                                CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Syncing...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("2. Auto-Assign Active & Sync", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        if (scannedItems.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SCAN RESULTS: 🟢 $activeCount ACTIVE • 🔴 $deadCount OFFLINE",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = CyanAccent,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    )
                    if (deadCount > 0) {
                        TextButton(
                            onClick = {
                                scannedItems = scannedItems.filter { it.checkResult?.isOnline == true }
                            }
                        ) {
                            Text("Hide Offline ($deadCount)", color = LiveRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            items(scannedItems, key = { it.url }) { item ->
                val isOnline = item.checkResult?.isOnline == true
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (isOnline) LiveGreen.copy(alpha = 0.55f) else LiveRed.copy(alpha = 0.4f),
                            RoundedCornerShape(14.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = NavySurface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .border(1.5.dp, if (isOnline) LiveGreen else LiveRed, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = item.name.take(4).uppercase(Locale.US),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF0F172A),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = if (isOnline) LiveGreen.copy(alpha = 0.2f) else LiveRed.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (isOnline) "🟢 LIVE (${item.checkResult?.latencyMs ?: 0}ms)" else "🔴 OFFLINE",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isOnline) LiveGreen else LiveRed,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 9.sp
                                            ),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.autoMatchedTarget,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = CyanAccent,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp
                                    )
                                )
                                Text(
                                    text = item.url,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextSecondary,
                                        fontSize = 10.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                onTestPlayStream(
                                    StreamSource(
                                        name = item.name,
                                        quality = item.quality,
                                        url = item.url
                                    )
                                )
                            }
                        ) {
                            Icon(
                                Icons.Default.PlayCircleFilled,
                                contentDescription = "Test Play",
                                tint = if (isOnline) LiveGreen else GoldYellow,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================================
// TAB 4: APP CONTROL, REMOTE AUTO-SYNC, LEAGUES, TELEGRAM, NOTICE & SECURITY (MODULES 3,4,5,6)
// =========================================================================================
@Composable
private fun AdminAppControlSection(
    config: AppConfig,
    currentUserEmail: String?,
    cloudSyncStatus: String,
    providerDiagnostics: List<ProviderSyncDiagnostics>,
    onGoogleSignIn: () -> Unit,
    onSaveConfig: (AppConfig) -> Unit,
    onPushAllCloud: () -> Unit,
    onOpenLeagueModal: () -> Unit,
    onSyncRemoteFeedNow: () -> Unit,
    onResetDefaults: () -> Unit
) {
    var appTitle by remember(config.appTitle) { mutableStateOf(config.appTitle) }
    var telegramLink by remember(config.telegramLink) { mutableStateOf(config.telegramLink) }
    var announcement by remember(config.announcement) { mutableStateOf(config.announcement) }
    var remoteFeedUrl by remember(config.remoteFeedUrl) { mutableStateOf(config.remoteFeedUrl) }
    var adminPin by remember(config.adminPin) { mutableStateOf(config.adminPin) }
    var refreshIntervalText by remember(config.autoRefreshIntervalSec) {
        mutableStateOf(config.autoRefreshIntervalSec.toString())
    }
    var footballEnabled by remember(config.footballSyncEnabled) { mutableStateOf(config.footballSyncEnabled) }
    var cricketEnabled by remember(config.cricketSyncEnabled) { mutableStateOf(config.cricketSyncEnabled) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B111E))
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Exact same App Logo Header as MenuScreen
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .border(2.dp, Color(0xFFFF5722), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_app_logo),
                        contentDescription = "Sports Live Logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${config.appTitle} • Admin Control",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                )
                Text(
                    text = cloudSyncStatus,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = CyanAccent,
                        fontSize = 11.sp
                    )
                )
            }
        }

        // Module 6: Real-Time Cloud Sync & Admin Identity Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "☁️ Real-Time Cloud Broadcast",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            )
                            Text(
                                text = if (currentUserEmail != null) {
                                    "Signed in as: $currentUserEmail (All changes update users live)"
                                } else {
                                    "Sign in with Google (masfitanha@gmail.com) so every change updates all users instantly"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (currentUserEmail != null) LiveGreen else GoldYellow,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (currentUserEmail == null) {
                            Button(
                                onClick = onGoogleSignIn,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sign in with Google", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = onPushAllCloud,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Push All Updates to Users", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Module 4: League & Sport Visibility Manager
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NavyBorder, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "⚽🏏 Sport & League Visibility Manager",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cricket Live & Upcoming Sync (CREX)", color = TextPrimary, fontSize = 13.sp)
                        Switch(
                            checked = cricketEnabled,
                            onCheckedChange = {
                                cricketEnabled = it
                                onSaveConfig(config.copy(cricketSyncEnabled = it))
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CyanAccent)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Football Live & Upcoming Sync (ESPN)", color = TextPrimary, fontSize = 13.sp)
                        Switch(
                            checked = footballEnabled,
                            onCheckedChange = {
                                footballEnabled = it
                                onSaveConfig(config.copy(footballSyncEnabled = it))
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CyanAccent)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedButton(
                        onClick = onOpenLeagueModal,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent)
                    ) {
                        Icon(Icons.Default.Checklist, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Configure Football Leagues (${config.enabledFootballLeagues.size} Enabled)",
                            color = CyanAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Module 5: App Title, Live Notice Banner & Telegram Link
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NavyBorder, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "📢 App Title, Telegram & Live Notice",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    )

                    OutlinedTextField(
                        value = appTitle,
                        onValueChange = { appTitle = it },
                        label = { Text("App Header Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = telegramLink,
                        onValueChange = { telegramLink = it },
                        label = { Text("Telegram Channel Link (Top Bar Button)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = announcement,
                        onValueChange = { announcement = it },
                        label = { Text("Live Notice / Breaking Announcement Banner") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Module 3 & 6: Remote Auto-Sync Playlist URL & Admin Security PIN
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NavyBorder, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "🔗 Remote Auto-Sync M3U URL & Admin PIN",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    )

                    OutlinedTextField(
                        value = remoteFeedUrl,
                        onValueChange = { remoteFeedUrl = it },
                        label = { Text("Remote Auto-Sync M3U / Feed URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = refreshIntervalText,
                            onValueChange = { refreshIntervalText = it },
                            label = { Text("Auto Refresh (sec)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = adminPin,
                            onValueChange = { adminPin = it },
                            label = { Text("Admin PIN Lock") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val interval = refreshIntervalText.toIntOrNull()?.coerceIn(15, 600) ?: 30
                                onSaveConfig(
                                    config.copy(
                                        appTitle = appTitle.ifBlank { "Sports Live" },
                                        telegramLink = telegramLink.trim(),
                                        announcement = announcement.trim(),
                                        remoteFeedUrl = remoteFeedUrl.trim(),
                                        adminPin = adminPin.ifBlank { "1234" },
                                        autoRefreshIntervalSec = interval,
                                        footballSyncEnabled = footballEnabled,
                                        cricketSyncEnabled = cricketEnabled
                                    )
                                )
                            },
                            modifier = Modifier.weight(1.3f),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save & Update All Users", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onSyncRemoteFeedNow,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Sync Feed Now", color = CyanAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Provider Diagnostics Snapshot
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NavyBorder, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📡 Live Feed Diagnostics",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                        TextButton(onClick = onResetDefaults) {
                            Text("Reset Defaults", color = LiveRed, fontSize = 11.sp)
                        }
                    }
                    providerDiagnostics.forEach { diag ->
                        Text(
                            text = "• ${diag.providerName}: ${diag.statusText}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================================
// MODAL DIALOG: FOOTBALL LEAGUE VISIBILITY MANAGER (WITH PRIORITY LEAGUES AT TOP)
// =========================================================================================
@Composable
private fun LeagueVisibilityDialog(
    config: AppConfig,
    onDismiss: () -> Unit,
    onSaveConfig: (AppConfig) -> Unit
) {
    var enabledSet by remember(config.enabledFootballLeagues) {
        mutableStateOf(config.enabledFootballLeagues)
    }

    val prioritySlugs = remember {
        listOf(
            "esp.1" to "FOOTBALL | LALIGA",
            "esp.2" to "FOOTBALL | LALIGA 2",
            "arg.1" to "FOOTBALL | LIGA PROFESIONAL DE FÚTBOL",
            "mex.1" to "FOOTBALL | LIGA MX, APERTURA",
            "por.1" to "FOOTBALL | LIGA PORTUGAL BETCLIC",
            "ned.1" to "FOOTBALL | VRIENDENLOTERIJ EREDIVISIE",
            "eng.1" to "FOOTBALL | ENGLISH PREMIER LEAGUE",
            "uefa.champions" to "FOOTBALL | UEFA CHAMPIONS LEAGUE",
            "ita.1" to "FOOTBALL | ITALIAN SERIE A",
            "ger.1" to "FOOTBALL | GERMAN BUNDESLIGA",
            "fra.1" to "FOOTBALL | FRENCH LIGUE 1",
            "bra.1" to "FOOTBALL | BRAZILIAN SERIE A",
            "ksa.1" to "FOOTBALL | SAUDI PRO LEAGUE",
            "usa.1" to "FOOTBALL | MLS"
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 580.dp)
                .border(1.dp, CyanAccent, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = NavyDark),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Football League Visibility",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                )
                Text(
                    text = "যেই লিগগুলো অন রাখবেন শুধুমাত্র সেগুলোর লাইভ ও আপকামিং ম্যাচ হোমপেজে দেখাবে",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 11.sp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { enabledSet = AppConfig.DEFAULT_ENABLED_FOOTBALL_LEAGUES },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Text("Select All", color = CyanAccent, fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            enabledSet = setOf("esp.1", "esp.2", "arg.1", "mex.1", "por.1", "ned.1", "eng.1", "uefa.champions")
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Text("Priority Only", color = GoldYellow, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(prioritySlugs, key = { it.first }) { (slug, displayHeader) ->
                        val isChecked = slug in enabledSet
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(NavySurface)
                                .clickable {
                                    enabledSet = if (isChecked) enabledSet - slug else enabledSet + slug
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayHeader,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                )
                                Text(
                                    text = "League ID: $slug",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextSecondary,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    enabledSet = if (checked) enabledSet + slug else enabledSet - slug
                                },
                                colors = CheckboxDefaults.colors(checkedColor = CyanAccent)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Button(
                        onClick = {
                            onSaveConfig(config.copy(enabledFootballLeagues = enabledSet))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                    ) {
                        Text("Save & Sync", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// =========================================================================================
// MODAL DIALOG: ADD OR EDIT CHANNEL WITH MULTI-STREAMS & CUSTOM HEADERS
// =========================================================================================
@Composable
fun AddChannelModalDialog(
    channelToEdit: SportsChannel? = null,
    onDismiss: () -> Unit,
    onSaveChannel: (SportsChannel) -> Unit,
    onCheckUrl: (String, (M3U8CheckResult) -> Unit) -> Unit
) {
    var channelName by remember(channelToEdit) { mutableStateOf(channelToEdit?.name ?: "") }
    var category by remember(channelToEdit) { mutableStateOf(channelToEdit?.category ?: "Cricket") }
    var logoText by remember(channelToEdit) { mutableStateOf(channelToEdit?.logoText ?: "") }
    var logoUrl by remember(channelToEdit) { mutableStateOf(channelToEdit?.logoUrl ?: "") }

    var streamsList by remember(channelToEdit) {
        mutableStateOf(
            if (channelToEdit != null && channelToEdit.streams.isNotEmpty()) {
                channelToEdit.streams
            } else {
                listOf(
                    StreamSource(
                        name = "SERVER 1 (1080p)",
                        quality = "1080p",
                        url = "https://robitv.com/rn/http://45.117.61.162/01_Tsports_HD/tracks-v1a1/mono.m3u8",
                        isDefault = true
                    )
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = if (channelToEdit != null) "Edit Channel & Servers" else "Add Channel & Servers",
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
                        label = { Text("Channel Name (e.g. T Sports HD)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("Category") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = logoText,
                            onValueChange = { logoText = it },
                            label = { Text("Short Badge") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = logoUrl,
                        onValueChange = { logoUrl = it },
                        label = { Text("Logo Image URL (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "STREAM SERVERS (.M3U8)",
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
                                    name = "SERVER $nextNum (1080p)",
                                    quality = "1080p",
                                    url = "",
                                    priorityOrder = nextNum
                                )
                            }
                        ) {
                            Text("+ Add Server", color = CyanAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                items(streamsList.size) { index ->
                    val s = streamsList[index]
                    var sName by remember(s.id) { mutableStateOf(s.name) }
                    var sQuality by remember(s.id) { mutableStateOf(s.quality) }
                    var sUrl by remember(s.id) { mutableStateOf(s.url) }
                    var sReferer by remember(s.id) { mutableStateOf(s.referer.orEmpty()) }
                    var sUserAgent by remember(s.id) { mutableStateOf(s.userAgent.orEmpty()) }
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
                                    text = "Server #${index + 1} ${if (index == 0 || s.isDefault) "(Default)" else ""}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (index == 0 || s.isDefault) CyanAccent else TextSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = {
                                            streamsList = streamsList.mapIndexed { i, item ->
                                                item.copy(isDefault = i == index, priorityOrder = if (i == index) 1 else i + 2)
                                            }.sortedBy { it.priorityOrder }
                                        }
                                    ) {
                                        Text("Pin #1", color = GoldYellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    if (streamsList.size > 1) {
                                        IconButton(
                                            onClick = { streamsList = streamsList.filterIndexed { i, _ -> i != index } },
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = LiveRed, modifier = Modifier.size(16.dp))
                                        }
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

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = sReferer,
                                    onValueChange = {
                                        sReferer = it
                                        streamsList = streamsList.toMutableList().also { list ->
                                            list[index] = list[index].copy(referer = it.ifBlank { null })
                                        }
                                    },
                                    label = { Text("Referer (Optional)", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = sUserAgent,
                                    onValueChange = {
                                        sUserAgent = it
                                        streamsList = streamsList.toMutableList().also { list ->
                                            list[index] = list[index].copy(userAgent = it.ifBlank { null })
                                        }
                                    },
                                    label = { Text("User-Agent (Optional)", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

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
                                            testStatus = if (res.isOnline) "🟢 Active (${res.latencyMs}ms)" else "🔴 Offline"
                                        }
                                    }
                                ) {
                                    Text("Check M3U8", color = CyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Button(
                            onClick = {
                                if (channelName.isNotBlank()) {
                                    onSaveChannel(
                                        SportsChannel(
                                            id = channelToEdit?.id ?: UUID.randomUUID().toString(),
                                            name = channelName,
                                            category = category,
                                            logoText = logoText.ifBlank { channelName.take(4).uppercase() },
                                            logoUrl = logoUrl,
                                            streams = streamsList.filter { it.url.isNotBlank() },
                                            isFavorite = channelToEdit?.isFavorite ?: false,
                                            isEnabled = channelToEdit?.isEnabled ?: true
                                        )
                                    )
                                }
                            },
                            enabled = channelName.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                        ) {
                            Text("Save & Sync", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================================
// MODAL DIALOG: ADD OR EDIT MATCH WITH STREAM PINNING, CHANNEL QUICK-PICKER & CUSTOM HEADERS
// =========================================================================================
@Composable
fun AddMatchModalDialog(
    matchToEdit: LiveMatch? = null,
    availableChannels: List<SportsChannel> = emptyList(),
    onDismiss: () -> Unit,
    onSaveMatch: (LiveMatch) -> Unit,
    onCheckUrl: (String, (M3U8CheckResult) -> Unit) -> Unit
) {
    var tournament by remember(matchToEdit) { mutableStateOf(matchToEdit?.tournament ?: "FOOTBALL | LALIGA") }
    var sport by remember(matchToEdit) { mutableStateOf(matchToEdit?.sport ?: SportType.FOOTBALL) }
    var team1Name by remember(matchToEdit) { mutableStateOf(matchToEdit?.team1Name ?: "Real Madrid") }
    var team1Code by remember(matchToEdit) { mutableStateOf(matchToEdit?.team1Code ?: "RMA") }
    var team1Flag by remember(matchToEdit) { mutableStateOf(matchToEdit?.team1FlagUrl ?: "") }

    var team2Name by remember(matchToEdit) { mutableStateOf(matchToEdit?.team2Name ?: "Barcelona") }
    var team2Code by remember(matchToEdit) { mutableStateOf(matchToEdit?.team2Code ?: "BAR") }
    var team2Flag by remember(matchToEdit) { mutableStateOf(matchToEdit?.team2FlagUrl ?: "") }

    var timeOrScore by remember(matchToEdit) { mutableStateOf(matchToEdit?.timeOrScore ?: "LIVE") }
    var matchTime by remember(matchToEdit) { mutableStateOf(matchToEdit?.matchTime ?: "09:00 PM | Today") }
    var status by remember(matchToEdit) { mutableStateOf(matchToEdit?.status ?: MatchStatus.LIVE) }

    var streamsList by remember(matchToEdit) {
        mutableStateOf(
            if (matchToEdit != null && matchToEdit.streams.isNotEmpty()) {
                matchToEdit.streams
            } else {
                listOf(
                    StreamSource(
                        name = "T Sports HD",
                        quality = "1080p",
                        url = "https://robitv.com/rn/http://45.117.61.162/01_Tsports_HD/tracks-v1a1/mono.m3u8",
                        isDefault = true
                    )
                )
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .border(1.dp, CyanAccent, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = NavyDark),
            shape = RoundedCornerShape(20.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = if (matchToEdit != null) "Pin / Edit Match Streams" else "Add Match With Live Streams",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 17.sp
                        )
                    )
                    Text(
                        text = "এই ম্যাচে স্ট্রিম পিন বা পরিবর্তন করলে সাথে সাথে সব ইউজারের অ্যাপে আপডেট হয়ে যাবে।",
                        style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent, fontSize = 11.sp)
                    )
                }

                // Quick 1-Tap Channel Stream Pin Bar
                if (availableChannels.isNotEmpty()) {
                    item {
                        Text(
                            text = "⚡ 1-TAP ATTACH CHANNEL TO THIS MATCH:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = GoldYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableChannels.forEach { ch ->
                                val firstStream = ch.streams.firstOrNull() ?: return@forEach
                                AssistChip(
                                    onClick = {
                                        val pinned = firstStream.copy(
                                            id = UUID.randomUUID().toString(),
                                            name = "${ch.name} (${firstStream.quality})",
                                            isDefault = true,
                                            priorityOrder = 1
                                        )
                                        streamsList = listOf(pinned) + streamsList.mapIndexed { idx, old ->
                                            old.copy(isDefault = false, priorityOrder = idx + 2)
                                        }
                                    },
                                    label = { Text("+ ${ch.name}", fontSize = 11.sp, color = Color.White) },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = NavySurface)
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = tournament,
                        onValueChange = { tournament = it },
                        label = { Text("Tournament Header (e.g. FOOTBALL | LALIGA)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = team1Name,
                            onValueChange = {
                                team1Name = it
                                val resolved = TeamFlagResolver.resolve(it)
                                if (resolved.flagUrl.isNotBlank()) {
                                    team1Code = resolved.code
                                    team1Flag = resolved.flagUrl
                                }
                            },
                            label = { Text("Team 1") },
                            modifier = Modifier.weight(1.4f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = team2Name,
                            onValueChange = {
                                team2Name = it
                                val resolved = TeamFlagResolver.resolve(it)
                                if (resolved.flagUrl.isNotBlank()) {
                                    team2Code = resolved.code
                                    team2Flag = resolved.flagUrl
                                }
                            },
                            label = { Text("Team 2") },
                            modifier = Modifier.weight(1.4f),
                            singleLine = true
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SportType.values().filter { it == SportType.CRICKET || it == SportType.FOOTBALL }.forEach { sType ->
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
                        MatchStatus.values().filter { it != MatchStatus.UNKNOWN }.forEach { st ->
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

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "MATCH SERVERS (${streamsList.size})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent,
                                fontSize = 11.sp
                            )
                        )
                        TextButton(
                            onClick = {
                                val nextNum = streamsList.size + 1
                                streamsList = listOf(
                                    StreamSource(
                                        name = "SERVER $nextNum (1080p)",
                                        quality = "1080p",
                                        url = "",
                                        isDefault = false,
                                        priorityOrder = nextNum
                                    )
                                ) + streamsList
                            }
                        ) {
                            Text("+ Add New .m3u8", color = CyanAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                items(streamsList.size) { index ->
                    val s = streamsList[index]
                    var sName by remember(s.id) { mutableStateOf(s.name) }
                    var sQuality by remember(s.id) { mutableStateOf(s.quality) }
                    var sUrl by remember(s.id) { mutableStateOf(s.url) }
                    var sReferer by remember(s.id) { mutableStateOf(s.referer.orEmpty()) }
                    var sUserAgent by remember(s.id) { mutableStateOf(s.userAgent.orEmpty()) }
                    var testStatus by remember(s.id) { mutableStateOf<String?>(null) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, if (index == 0 || s.isDefault) CyanAccent else NavyBorder, RoundedCornerShape(10.dp)),
                        colors = CardDefaults.cardColors(containerColor = NavySurface)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Server #${index + 1} ${if (index == 0 || s.isDefault) "★ DEFAULT" else ""}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (index == 0 || s.isDefault) CyanAccent else TextSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = {
                                            val chosen = streamsList[index].copy(isDefault = true, priorityOrder = 1)
                                            val others = streamsList.filterIndexed { i, _ -> i != index }
                                                .mapIndexed { idx, item -> item.copy(isDefault = false, priorityOrder = idx + 2) }
                                            streamsList = listOf(chosen) + others
                                        }
                                    ) {
                                        Text("📌 Pin #1", color = GoldYellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    if (streamsList.size > 1) {
                                        IconButton(
                                            onClick = { streamsList = streamsList.filterIndexed { i, _ -> i != index } },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = null, tint = LiveRed, modifier = Modifier.size(16.dp))
                                        }
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

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = sReferer,
                                    onValueChange = {
                                        sReferer = it
                                        streamsList = streamsList.toMutableList().also { list ->
                                            list[index] = list[index].copy(referer = it.ifBlank { null })
                                        }
                                    },
                                    label = { Text("Referer (Optional)", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = sUserAgent,
                                    onValueChange = {
                                        sUserAgent = it
                                        streamsList = streamsList.toMutableList().also { list ->
                                            list[index] = list[index].copy(userAgent = it.ifBlank { null })
                                        }
                                    },
                                    label = { Text("User-Agent (Optional)", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

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
                                            testStatus = if (res.isOnline) "🟢 Active (${res.latencyMs}ms)" else "🔴 Offline"
                                        }
                                    }
                                ) {
                                    Text("Check M3U8", color = CyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Button(
                            onClick = {
                                if (team1Name.isNotBlank() && team2Name.isNotBlank()) {
                                    val isNat = matchToEdit?.isNationalTeam ?: (sport == SportType.CRICKET)
                                    val v1 = TeamFlagResolver.resolveForMatch(team1Name, team1Code, team1Flag, isNationalTeam = isNat)
                                    val v2 = TeamFlagResolver.resolveForMatch(team2Name, team2Code, team2Flag, isNationalTeam = isNat)
                                    onSaveMatch(
                                        LiveMatch(
                                            id = matchToEdit?.id ?: UUID.randomUUID().toString(),
                                            tournament = tournament,
                                            sport = sport,
                                            team1Name = team1Name,
                                            team1Code = v1.code,
                                            team1FlagUrl = v1.flagUrl,
                                            team2Name = team2Name,
                                            team2Code = v2.code,
                                            team2FlagUrl = v2.flagUrl,
                                            status = status,
                                            timeOrScore = timeOrScore,
                                            matchTime = matchTime.ifBlank { "Today" },
                                            streams = streamsList.filter { it.url.isNotBlank() },
                                            isFavorite = matchToEdit?.isFavorite ?: false,
                                            provider = matchToEdit?.provider ?: "ADMIN",
                                            competitionId = matchToEdit?.competitionId ?: "",
                                            startTimeEpochMs = matchToEdit?.startTimeEpochMs ?: System.currentTimeMillis(),
                                            isNationalTeam = isNat,
                                            sofascoreEventId = matchToEdit?.sofascoreEventId,
                                            broadcastChannels = matchToEdit?.broadcastChannels ?: emptyList(),
                                            approvedHighlightUrl = matchToEdit?.approvedHighlightUrl
                                        )
                                    )
                                }
                            },
                            enabled = team1Name.isNotBlank() && team2Name.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                        ) {
                            Text("Save & Sync Users", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
