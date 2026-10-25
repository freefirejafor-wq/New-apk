package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.crex.CrexMatchDetailState
import com.example.data.model.LiveMatch
import com.example.data.model.MatchStatus
import com.example.data.model.StreamSource
import com.example.player.ExoPlayerManager
import com.example.ui.components.FinishedBadge
import com.example.ui.components.LiveGlowingRedDot
import com.example.ui.components.MatchCard
import com.example.ui.components.MultipleStreamsModalDialog
import com.example.ui.components.PulsingLiveBadge
import com.example.ui.components.QualityBadge
import com.example.ui.components.TeamFlagBadge
import com.example.ui.components.UpcomingBadge
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    match: LiveMatch?,
    channelTitle: String? = null,
    streams: List<StreamSource>,
    playerManager: ExoPlayerManager,
    forceLowQuality: Boolean = false,
    matchDetailState: CrexMatchDetailState? = null,
    onRefreshDetail: () -> Unit = {},
    onBackClick: () -> Unit,
    otherMatches: List<LiveMatch> = emptyList(),
    onSwitchMatch: (LiveMatch) -> Unit = {},
    onFavoriteToggle: (String) -> Unit = {},
    onReminderToggle: (String) -> Unit = {},
    onEnterPiP: () -> Unit = {}
) {
    val context = LocalContext.current
    val isPlaying by playerManager.isPlaying.collectAsState()
    val isBuffering by playerManager.isBuffering.collectAsState()
    val currentStream by playerManager.currentStream.collectAsState()
    val errorMessage by playerManager.errorMessage.collectAsState()

    val isMuted by playerManager.isMuted.collectAsState()
    val currentPosition by playerManager.currentPosition.collectAsState()
    val duration by playerManager.duration.collectAsState()
    val playbackSpeed by playerManager.playbackSpeed.collectAsState()
    val selectedQualityLabel by playerManager.selectedQualityLabel.collectAsState()

    var isFullscreen by remember { mutableStateOf(false) }
    var isControlsLocked by remember { mutableStateOf(false) }
    var showStreamPickerModal by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var autoFallbackCountdown by remember { mutableStateOf(0) }

    fun switchToNextAvailableStream() {
        val currentIndex = streams.indexOfFirst { it.id == currentStream?.id }
        if (streams.isNotEmpty()) {
            val nextIndex = if (currentIndex in 0 until streams.size - 1) currentIndex + 1 else 0
            playerManager.playStream(streams[nextIndex], forceLowQuality)
        }
    }

    // Auto switch to next backup stream on error after 3 seconds
    LaunchedEffect(errorMessage, currentStream?.id) {
        if (errorMessage != null && streams.size > 1) {
            for (sec in 3 downTo 1) {
                autoFallbackCountdown = sec
                delay(1000)
            }
            autoFallbackCountdown = 0
            if (playerManager.errorMessage.value != null) {
                switchToNextAvailableStream()
            }
        } else {
            autoFallbackCountdown = 0
        }
    }

    // Start playback when stream is set
    LaunchedEffect(streams) {
        if (streams.isNotEmpty() && currentStream == null) {
            val defaultStream = streams.find { it.isDefault } ?: streams.first()
            playerManager.playStream(defaultStream, forceLowQuality)
        }
    }

    // Handle back button
    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            playerManager.pause()
            onBackClick()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val titleText = when {
        match != null -> "${match.team1Name} vs ${match.team2Name}"
        channelTitle != null -> channelTitle
        else -> "Live Sports Stream"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1526))
    ) {
        if (isFullscreen) {
            // Fullscreen Player
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = playerManager.getPlayer()
                            useController = false
                            this.resizeMode = resizeMode
                            this.keepScreenOn = true
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { view ->
                        view.player = playerManager.getPlayer()
                        view.resizeMode = resizeMode
                        view.keepScreenOn = isPlaying
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay Controls
                FullscreenPlayerOverlay(
                    title = titleText,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    isLocked = isControlsLocked,
                    isMuted = isMuted,
                    currentPosition = currentPosition,
                    duration = duration,
                    currentStream = currentStream,
                    selectedQualityLabel = selectedQualityLabel,
                    onToggleLock = { isControlsLocked = !isControlsLocked },
                    onToggleMute = { playerManager.toggleMute() },
                    onPlayPause = { if (isPlaying) playerManager.pause() else playerManager.resume() },
                    onRewind = { playerManager.seekRelative(-10_000) },
                    onForward = { playerManager.seekRelative(10_000) },
                    onSeekTo = { playerManager.seekTo(it) },
                    onToggleFullscreen = {
                        isFullscreen = false
                        (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    },
                    onToggleAspectRatio = {
                        resizeMode = when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    onOpenStreams = { showStreamPickerModal = true },
                    onOpenQuality = { showQualityDialog = true },
                    onBack = {
                        isFullscreen = false
                        (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                )
            }
        } else {
            // Portrait View matching Screenshot 1 & Screenshot 3 pixel-for-pixel
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0D1526))
                    .statusBarsPadding()
            ) {
                // Video Container (16:9 Aspect Ratio)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = playerManager.getPlayer()
                                useController = false
                                this.resizeMode = resizeMode
                                this.keepScreenOn = true
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { view ->
                            view.player = playerManager.getPlayer()
                            view.resizeMode = resizeMode
                            view.keepScreenOn = isPlaying
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Video Overlay Controls matching Screenshot 1
                    PortraitVideoControlsOverlay(
                        title = titleText,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        isMuted = isMuted,
                        isLocked = isControlsLocked,
                        currentPosition = currentPosition,
                        duration = duration,
                        playbackSpeed = playbackSpeed,
                        selectedQualityLabel = selectedQualityLabel,
                        errorMessage = errorMessage,
                        autoFallbackCountdown = autoFallbackCountdown,
                        onBack = onBackClick,
                        onToggleMute = { playerManager.toggleMute() },
                        onToggleLock = { isControlsLocked = !isControlsLocked },
                        onPlayPause = { if (isPlaying) playerManager.pause() else playerManager.resume() },
                        onRewind = { playerManager.seekRelative(-10_000) },
                        onForward = { playerManager.seekRelative(10_000) },
                        onSeekTo = { playerManager.seekTo(it) },
                        onCycleSpeed = {
                            val nextSpeed = when (playbackSpeed) {
                                1.0f -> 1.25f
                                1.25f -> 1.5f
                                1.5f -> 0.75f
                                else -> 1.0f
                            }
                            playerManager.setPlaybackSpeed(nextSpeed)
                        },
                        onOpenQuality = { showQualityDialog = true },
                        onToggleAspectRatio = {
                            resizeMode = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        onFullscreen = {
                            isFullscreen = true
                            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        },
                        onPiP = onEnterPiP,
                        onRetry = {
                            currentStream?.let { playerManager.playStream(it, forceLowQuality) }
                        },
                        onPickAnotherStream = { showStreamPickerModal = true },
                        onNextFallback = { switchToNextAvailableStream() }
                    )
                }

                // Horizontal Streams Available Row (Screenshot 3)
                if (streams.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0D1526))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        streams.forEach { stream ->
                            val isSelected = currentStream?.id == stream.id
                            StreamChipButton(
                                stream = stream,
                                isSelected = isSelected,
                                onClick = { playerManager.playStream(stream, forceLowQuality) }
                            )
                        }
                    }
                }

                // Current Match Strip (Screenshot 1 & Screenshot 3)
                if (match != null) {
                    val detail = matchDetailState?.detail
                    val t1Score = detail?.team1Score?.takeIf { it.isNotBlank() } ?: match.team1Score
                    val t2Score = detail?.team2Score?.takeIf { it.isNotBlank() } ?: match.team2Score

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0D1526))
                            .clickable { showStreamPickerModal = true }
                            .padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left area: Team 1 Crest | Red Dot + Live | Team 2 Crest
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                TeamFlagBadge(
                                    flag = match.team1FlagUrl,
                                    code = match.team1Code,
                                    teamName = match.team1Name,
                                    sizeDp = 50
                                )
                                if (t1Score.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = t1Score,
                                        color = Color(0xFF00D1C1),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                LiveGlowingRedDot()
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    text = if (match.status == MatchStatus.FINISHED) "Finished" else "Live",
                                    color = if (match.status == MatchStatus.FINISHED) Color(0xFF94A3B8) else Color(0xFFEF4444),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                TeamFlagBadge(
                                    flag = match.team2FlagUrl,
                                    code = match.team2Code,
                                    teamName = match.team2Name,
                                    sizeDp = 50
                                )
                                if (t2Score.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = t2Score,
                                        color = Color(0xFF00D1C1),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(18.dp))

                        // Thin vertical divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(46.dp)
                                .background(Color(0xFF64748B).copy(alpha = 0.7f))
                        )

                        // Right area: Light grey circle indicator (Screenshot 1 & 3)
                        Box(
                            modifier = Modifier
                                .width(82.dp)
                                .height(46.dp)
                                .clickable {
                                    onRefreshDetail()
                                    showStreamPickerModal = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFD1D5DB))
                                    .border(2.dp, Color(0xFF64748B), CircleShape)
                            )
                        }
                    }
                }

                // Other Matches List (Screenshot 1 & Screenshot 3)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
                ) {
                    items(otherMatches, key = { "${it.status}_${it.id}" }) { other ->
                        MatchCard(
                            match = other,
                            onClick = { onSwitchMatch(other) },
                            onFavoriteToggle = { onFavoriteToggle(other.id) },
                            onReminderToggle = { onReminderToggle(other.id) },
                            onStreamSelectClick = { onSwitchMatch(other) }
                        )
                    }
                }
            }
        }

        // Multiple Streams Selection Modal Dialog
        if (showStreamPickerModal && streams.isNotEmpty()) {
            MultipleStreamsModalDialog(
                streams = streams,
                currentSelectedStream = currentStream,
                matchTitle = match?.let { "${it.team1Name} vs ${it.team2Name}" } ?: channelTitle,
                matchTournament = match?.tournament,
                matchStatus = match?.status?.name ?: "LIVE",
                matchDetailState = matchDetailState,
                onRefreshDetail = onRefreshDetail,
                onStreamSelected = { selected ->
                    playerManager.playStream(selected, forceLowQuality)
                },
                onDismiss = { showStreamPickerModal = false }
            )
        }

        // Video Quality / Resolution Selector Dialog
        if (showQualityDialog) {
            val qualityOptions = listOf(
                Pair("Auto", null),
                Pair("1080p FHD", 1080),
                Pair("720p HD", 720),
                Pair("480p SD", 480),
                Pair("360p Data Saver", 360)
            )
            Dialog(onDismissRequest = { showQualityDialog = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .border(1.dp, CyanAccent, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = NavyDark),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Select Video Quality",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        qualityOptions.forEach { (label, maxHeight) ->
                            val isSelected = selectedQualityLabel == label
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) NavySurfaceVariant else Color.Transparent)
                                    .clickable {
                                        playerManager.setVideoQualityConstraint(maxHeight, label)
                                        showQualityDialog = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) CyanAccent else TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = CyanAccent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StreamChipButton(
    stream: StreamSource,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF00D1C1) else Color(0xFF25344A)
    val borderWidth = if (isSelected) 1.3.dp else 1.dp
    val containerBg = if (isSelected) Color(0xFF162234) else Color(0xFF162030)

    Surface(
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag("stream_chip_${stream.name.lowercase().replace(" ", "_")}"),
        color = containerBg,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = stream.name.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    fontSize = 15.sp,
                    letterSpacing = 0.2.sp
                )
            )
        }
    }
}

fun formatPlayerTime(timeMs: Long): String {
    if (timeMs <= 0L) return "00:00"
    val totalSeconds = (timeMs / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
}

@Composable
fun PortraitVideoControlsOverlay(
    title: String,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isMuted: Boolean,
    isLocked: Boolean,
    currentPosition: Long,
    duration: Long,
    playbackSpeed: Float = 1.0f,
    selectedQualityLabel: String = "Auto",
    errorMessage: String?,
    autoFallbackCountdown: Int = 0,
    onBack: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleLock: () -> Unit,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onCycleSpeed: () -> Unit = {},
    onOpenQuality: () -> Unit = {},
    onToggleAspectRatio: () -> Unit = {},
    onFullscreen: () -> Unit,
    onPiP: () -> Unit,
    onRetry: () -> Unit,
    onPickAnotherStream: () -> Unit,
    onNextFallback: () -> Unit
) {
    val context = LocalContext.current
    var showControls by remember { mutableStateOf(true) }
    var gestureFeedbackText by remember { mutableStateOf<String?>(null) }

    // Auto-hide controls after 4 seconds when playing
    LaunchedEffect(showControls, isPlaying, isLocked) {
        if (showControls && isPlaying && !isLocked) {
            delay(4000L)
            showControls = false
        }
    }

    LaunchedEffect(gestureFeedbackText) {
        if (gestureFeedbackText != null) {
            delay(900L)
            gestureFeedbackText = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isLocked) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        if (!isLocked) {
                            if (offset.x < size.width / 2f) {
                                onRewind()
                                gestureFeedbackText = "⏪ -10s"
                            } else {
                                onForward()
                                gestureFeedbackText = "⏩ +10s"
                            }
                        }
                    }
                )
            }
            .pointerInput(isLocked) {
                if (!isLocked) {
                    var startX = 0f
                    detectVerticalDragGestures(
                        onDragStart = { pos -> startX = pos.x },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val delta = -dragAmount / size.height.coerceAtLeast(1)
                            if (startX < size.width / 2f) {
                                // Left side: Brightness
                                val window = (context as? Activity)?.window
                                if (window != null) {
                                    val lp = window.attributes
                                    val current = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                                    val next = (current + delta).coerceIn(0.05f, 1.0f)
                                    lp.screenBrightness = next
                                    window.attributes = lp
                                    gestureFeedbackText = "☀️ Brightness ${(next * 100).toInt()}%"
                                }
                            } else {
                                // Right side: Volume
                                val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                                if (audio != null) {
                                    val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                    val curVol = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    val step = if (delta > 0.02f) 1 else if (delta < -0.02f) -1 else 0
                                    if (step != 0) {
                                        val newVol = (curVol + step).coerceIn(0, maxVol)
                                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                        gestureFeedbackText = "🔊 Volume ${(newVol * 100) / maxVol}%"
                                    }
                                }
                            }
                        }
                    )
                }
            }
    ) {
        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = LiveRed, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (autoFallbackCountdown > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Switching to next backup server in ${autoFallbackCountdown}s...",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = GoldYellow,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onNextFallback,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                        ) {
                            Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Next Server", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onRetry,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                        ) {
                            Text("Retry", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onPickAnotherStream,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                        ) {
                            Text("All Servers", fontSize = 12.sp)
                        }
                    }
                }
            }
        } else if (isBuffering && !showControls) {
            CircularProgressIndicator(
                color = CyanAccent,
                modifier = Modifier
                    .size(42.dp)
                    .align(Alignment.Center)
            )
        }

        // Gesture Feedback Pill
        gestureFeedbackText?.let { feedback ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 42.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .border(1.dp, CyanAccent, RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = feedback,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        // Lock button when locked
        if (isLocked) {
            IconButton(
                onClick = onToggleLock,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .border(1.dp, GoldYellow, CircleShape)
            ) {
                Icon(Icons.Default.Lock, contentDescription = "Unlock", tint = GoldYellow, modifier = Modifier.size(20.dp))
            }
        }

        // Screenshot 1 Exact Video Controls Overlay
        if (showControls && errorMessage == null && !isLocked) {
            // Top Bar: Back arrow + Title on Left | Volume + Lock on Right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onToggleMute, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    IconButton(onClick = onToggleLock, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.LockOpen,
                            contentDescription = "Lock",
                            tint = Color.White,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }
            }

            // Bottom Control Bar + Seekbar (Screenshot 1)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                // 7-Icon Control Row: Fit-Screen | PiP | FastRewind | Play/Pause | FastForward | Settings | Fullscreen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Fit-Screen / Aspect Ratio Icon (white rounded box with 4 inner directional arrows)
                    IconButton(onClick = onToggleAspectRatio, modifier = Modifier.size(36.dp)) {
                        Canvas(modifier = Modifier.size(23.dp)) {
                            val w = size.width
                            val h = size.height
                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset.Zero,
                                size = Size(w, h),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            val cx = w / 2f
                            val cy = h / 2f
                            drawLine(
                                color = Color.White,
                                start = Offset(w * 0.25f, cy),
                                end = Offset(w * 0.75f, cy),
                                strokeWidth = 1.8.dp.toPx()
                            )
                            drawLine(
                                color = Color.White,
                                start = Offset(cx, h * 0.25f),
                                end = Offset(cx, h * 0.75f),
                                strokeWidth = 1.8.dp.toPx()
                            )
                        }
                    }

                    // 2. Picture-in-Picture Icon (white rectangle with solid bottom-right inner box)
                    IconButton(onClick = onPiP, modifier = Modifier.size(36.dp)) {
                        Canvas(modifier = Modifier.size(width = 25.dp, height = 19.dp)) {
                            val w = size.width
                            val h = size.height
                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset.Zero,
                                size = Size(w, h),
                                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(w * 0.45f, h * 0.44f),
                                size = Size(w * 0.40f, h * 0.38f)
                            )
                        }
                    }

                    // 3. Fast Rewind ⏪
                    IconButton(onClick = onRewind, modifier = Modifier.size(38.dp)) {
                        Icon(
                            Icons.Default.FastRewind,
                            contentDescription = "Rewind 10s",
                            tint = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // 4. Play / Pause ▶
                    IconButton(onClick = onPlayPause, modifier = Modifier.size(42.dp)) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    // 5. Fast Forward ⏩
                    IconButton(onClick = onForward, modifier = Modifier.size(38.dp)) {
                        Icon(
                            Icons.Default.FastForward,
                            contentDescription = "Forward 10s",
                            tint = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // 6. Settings Gear ⚙
                    IconButton(onClick = onOpenQuality, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Video Settings",
                            tint = Color.White,
                            modifier = Modifier.size(25.dp)
                        )
                    }

                    // 7. Fullscreen Corners ⛶
                    IconButton(onClick = onFullscreen, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Seekbar Row: 00:00 [--------] 00:00
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatPlayerTime(currentPosition),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color(0xFFD1D5DB),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    if (duration > 0L) {
                        Slider(
                            value = currentPosition.toFloat(),
                            onValueChange = { onSeekTo(it.toLong()) },
                            valueRange = 0f..duration.toFloat(),
                            modifier = Modifier
                                .weight(1f)
                                .height(20.dp)
                                .padding(horizontal = 10.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color(0xFF3A3A3A)
                            )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF3A3A3A))
                        )
                    }
                    Text(
                        text = formatPlayerTime(duration),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color(0xFFD1D5DB),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun FullscreenPlayerOverlay(
    title: String,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isLocked: Boolean,
    isMuted: Boolean,
    currentPosition: Long,
    duration: Long,
    currentStream: StreamSource?,
    selectedQualityLabel: String = "Auto",
    onToggleLock: () -> Unit,
    onToggleMute: () -> Unit,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleAspectRatio: () -> Unit,
    onOpenStreams: () -> Unit,
    onOpenQuality: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showControls by remember { mutableStateOf(true) }
    var gestureFeedbackText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(showControls, isPlaying, isLocked) {
        if (showControls && isPlaying && !isLocked) {
            delay(4000L)
            showControls = false
        }
    }

    LaunchedEffect(gestureFeedbackText) {
        if (gestureFeedbackText != null) {
            delay(900L)
            gestureFeedbackText = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isLocked) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        if (!isLocked) {
                            if (offset.x < size.width / 2f) {
                                onRewind()
                                gestureFeedbackText = "⏪ -10s"
                            } else {
                                onForward()
                                gestureFeedbackText = "⏩ +10s"
                            }
                        }
                    }
                )
            }
            .pointerInput(isLocked) {
                if (!isLocked) {
                    var startX = 0f
                    detectVerticalDragGestures(
                        onDragStart = { pos -> startX = pos.x },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val delta = -dragAmount / size.height.coerceAtLeast(1)
                            if (startX < size.width / 2f) {
                                val window = (context as? Activity)?.window
                                if (window != null) {
                                    val lp = window.attributes
                                    val current = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                                    val next = (current + delta).coerceIn(0.05f, 1.0f)
                                    lp.screenBrightness = next
                                    window.attributes = lp
                                    gestureFeedbackText = "☀️ Brightness ${(next * 100).toInt()}%"
                                }
                            } else {
                                val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                                if (audio != null) {
                                    val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                    val curVol = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    val step = if (delta > 0.02f) 1 else if (delta < -0.02f) -1 else 0
                                    if (step != 0) {
                                        val newVol = (curVol + step).coerceIn(0, maxVol)
                                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                        gestureFeedbackText = "🔊 Volume ${(newVol * 100) / maxVol}%"
                                    }
                                }
                            }
                        }
                    )
                }
            }
    ) {
        if (isBuffering) {
            CircularProgressIndicator(
                color = CyanAccent,
                modifier = Modifier
                    .size(54.dp)
                    .align(Alignment.Center)
            )
        }

        gestureFeedbackText?.let { feedback ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .border(1.dp, CyanAccent, RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = feedback,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        // Lock button is always visible on right
        IconButton(
            onClick = onToggleLock,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .border(1.dp, if (isLocked) GoldYellow else Color.White, CircleShape)
        ) {
            Icon(
                if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = "Lock",
                tint = if (isLocked) GoldYellow else Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        if (showControls && !isLocked) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onOpenQuality) {
                        Text(selectedQualityLabel, color = GoldYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    IconButton(onClick = onToggleMute) {
                        Icon(
                            if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = if (isMuted) LiveRed else Color.White
                        )
                    }
                    currentStream?.let { s ->
                        QualityBadge(quality = s.quality)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(onClick = onOpenStreams) {
                        Icon(Icons.Default.Tune, contentDescription = "Streams", tint = CyanAccent)
                    }
                    IconButton(onClick = onToggleAspectRatio) {
                        Icon(Icons.Default.AspectRatio, contentDescription = "Aspect Ratio", tint = Color.White)
                    }
                    IconButton(onClick = onToggleFullscreen) {
                        Icon(Icons.Default.FullscreenExit, contentDescription = "Exit Fullscreen", tint = Color.White)
                    }
                }
            }

            // Center Controls
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onRewind, modifier = Modifier.size(54.dp)) {
                    Icon(Icons.Default.Replay10, contentDescription = "Rewind", tint = Color.White, modifier = Modifier.size(40.dp))
                }

                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .border(2.dp, CyanAccent, CircleShape)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = CyanAccent,
                        modifier = Modifier.size(44.dp)
                    )
                }

                IconButton(onClick = onForward, modifier = Modifier.size(54.dp)) {
                    Icon(Icons.Default.Forward10, contentDescription = "Forward", tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }

            // Bottom Progress & Timestamp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (duration > 0L) {
                    Text(
                        text = formatPlayerTime(currentPosition),
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White)
                    )
                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { onSeekTo(it.toLong()) },
                        valueRange = 0f..duration.toFloat(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = CyanAccent,
                            activeTrackColor = CyanAccent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                    Text(
                        text = formatPlayerTime(duration),
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White)
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(LiveRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "LIVE BROADCAST",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = LiveRed,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    currentStream?.let {
                        Text(
                            text = "Server: ${it.name} (${it.quality})",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                }
            }
        }
    }
}
