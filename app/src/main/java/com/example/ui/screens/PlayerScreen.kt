package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.LiveMatch
import com.example.data.model.StreamSource
import com.example.player.ExoPlayerManager
import com.example.ui.components.MatchCard
import com.example.ui.components.MultipleStreamsModalDialog
import com.example.ui.components.PulsingLiveBadge
import com.example.ui.components.QualityBadge
import com.example.ui.components.TeamFlagBadge
import com.example.ui.theme.*

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    match: LiveMatch?,
    channelTitle: String? = null,
    streams: List<StreamSource>,
    playerManager: ExoPlayerManager,
    onBackClick: () -> Unit,
    otherMatches: List<LiveMatch> = emptyList(),
    onSwitchMatch: (LiveMatch) -> Unit = {},
    onFavoriteToggle: (String) -> Unit = {},
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

    var isFullscreen by remember { mutableStateOf(false) }
    var isControlsLocked by remember { mutableStateOf(false) }
    var showStreamPickerModal by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var autoFallbackCountdown by remember { mutableStateOf(0) }

    fun switchToNextAvailableStream() {
        val currentIndex = streams.indexOfFirst { it.id == currentStream?.id }
        if (streams.isNotEmpty()) {
            val nextIndex = if (currentIndex in 0 until streams.size - 1) currentIndex + 1 else 0
            playerManager.playStream(streams[nextIndex])
        }
    }

    // Auto switch to next backup stream on error after 3 seconds
    LaunchedEffect(errorMessage, currentStream?.id) {
        if (errorMessage != null && streams.size > 1) {
            for (sec in 3 downTo 1) {
                autoFallbackCountdown = sec
                kotlinx.coroutines.delay(1000)
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
            playerManager.playStream(defaultStream)
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
        match != null -> "${match.team1Code} vs ${match.team2Code}"
        channelTitle != null -> channelTitle
        else -> "Live Sports Stream"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDark)
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
                    onBack = {
                        isFullscreen = false
                        (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                )
            }
        } else {
            // Portrait View: Video on top + horizontal streams + match info + other matches
            Column(
                modifier = Modifier
                    .fillMaxSize()
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

                    // Video Overlay Controls
                    PortraitVideoControlsOverlay(
                        title = titleText,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        isMuted = isMuted,
                        isLocked = isControlsLocked,
                        currentPosition = currentPosition,
                        duration = duration,
                        playbackSpeed = playbackSpeed,
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
                            currentStream?.let { playerManager.playStream(it) }
                        },
                        onPickAnotherStream = { showStreamPickerModal = true },
                        onNextFallback = { switchToNextAvailableStream() }
                    )
                }

                // Horizontal Streams Available Buttons (matching screenshots 6, 7 & 9)
                if (streams.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NavySurface)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        streams.forEach { stream ->
                            val isSelected = currentStream?.id == stream.id
                            StreamChipButton(
                                stream = stream,
                                isSelected = isSelected,
                                onClick = { playerManager.playStream(stream) }
                            )
                        }

                        // More streams dialog button
                        OutlinedButton(
                            onClick = { showStreamPickerModal = true },
                            modifier = Modifier.height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(NavyBorder)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = NavySurfaceVariant)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("All Servers", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Match details card or channel banner
                if (match != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .border(1.dp, NavyBorder, RoundedCornerShape(14.dp)),
                        colors = CardDefaults.cardColors(containerColor = NavySurface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TeamFlagBadge(flag = match.team1FlagUrl, code = match.team1Code, teamName = match.team1Name)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(match.team1Name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                                    Text(match.team1Code, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PulsingLiveBadge()
                                Text(
                                    text = match.timeOrScore,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = CyanAccent,
                                        fontSize = 12.sp
                                    )
                                )
                                if (match.matchTime.isNotBlank()) {
                                    Text(
                                        text = match.matchTime,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = GoldYellow,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(match.team2Name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary), textAlign = TextAlign.End)
                                    Text(match.team2Code, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary), textAlign = TextAlign.End)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                TeamFlagBadge(flag = match.team2FlagUrl, code = match.team2Code, teamName = match.team2Name)
                            }
                        }
                    }
                }

                // Other Live Matches List (matching screenshot 6)
                if (otherMatches.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = "OTHER LIVE & UPCOMING MATCHES",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            ),
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(otherMatches, key = { it.id }) { other ->
                                MatchCard(
                                    match = other,
                                    onClick = { onSwitchMatch(other) },
                                    onFavoriteToggle = { onFavoriteToggle(other.id) },
                                    onStreamSelectClick = { onSwitchMatch(other) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Multiple Streams Selection Modal Dialog (matching screenshot 7)
        if (showStreamPickerModal && streams.isNotEmpty()) {
            MultipleStreamsModalDialog(
                streams = streams,
                currentSelectedStream = currentStream,
                onStreamSelected = { selected ->
                    playerManager.playStream(selected)
                },
                onDismiss = { showStreamPickerModal = false }
            )
        }
    }
}

@Composable
fun StreamChipButton(
    stream: StreamSource,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) CyanAccent else NavyBorder
    val containerBg = if (isSelected) CyanAccent.copy(alpha = 0.15f) else NavySurfaceVariant

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("stream_chip_${stream.name.lowercase().replace(" ", "_")}"),
        color = containerBg,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = null,
                tint = if (isSelected) CyanAccent else TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stream.name,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) CyanAccent else TextPrimary,
                    fontSize = 12.sp
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            QualityBadge(quality = stream.quality)
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
    onToggleAspectRatio: () -> Unit = {},
    onFullscreen: () -> Unit,
    onPiP: () -> Unit,
    onRetry: () -> Unit,
    onPickAnotherStream: () -> Unit,
    onNextFallback: () -> Unit
) {
    var showControls by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { showControls = !showControls }
    ) {
        if (errorMessage != null) {
            // Error overlay with instant server fallback
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
        } else if (isBuffering) {
            CircularProgressIndicator(
                color = CyanAccent,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center)
            )
        }

        // Lock button is always visible on top right even if controls hidden
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

        if (showControls && errorMessage == null && !isLocked) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Speed Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable(onClick = onCycleSpeed)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${playbackSpeed}x",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = CyanAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    // Aspect Ratio Button
                    IconButton(onClick = onToggleAspectRatio, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.AspectRatio, contentDescription = "Aspect Ratio", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    // Mute / Unmute Button
                    IconButton(onClick = onToggleMute, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = if (isMuted) LiveRed else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // Lock Controls Button
                    IconButton(onClick = onToggleLock, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.LockOpen, contentDescription = "Lock", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    // PiP Button
                    IconButton(onClick = onPiP, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    // Fullscreen Button
                    IconButton(onClick = onFullscreen, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }

            // Center Play / Pause & Seek
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onRewind, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                }

                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.5.dp, CyanAccent, CircleShape)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = CyanAccent,
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(onClick = onForward, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }

            // Bottom Progress & Timestamp matching screenshot 6/9
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (duration > 0L) {
                    Text(
                        text = formatPlayerTime(currentPosition),
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontSize = 11.sp)
                    )
                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { onSeekTo(it.toLong()) },
                        valueRange = 0f..duration.toFloat(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = CyanAccent,
                            activeTrackColor = CyanAccent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                    Text(
                        text = formatPlayerTime(duration),
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontSize = 11.sp)
                    )
                } else {
                    // Live Stream Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(LiveRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LIVE STREAM FEED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = LiveRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Realtime HLS / Low Latency",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp)
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
    onToggleLock: () -> Unit,
    onToggleMute: () -> Unit,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleAspectRatio: () -> Unit,
    onOpenStreams: () -> Unit,
    onBack: () -> Unit
) {
    var showControls by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { showControls = !showControls }
    ) {
        if (isBuffering) {
            CircularProgressIndicator(
                color = CyanAccent,
                modifier = Modifier
                    .size(54.dp)
                    .align(Alignment.Center)
            )
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
                    // Mute / Unmute
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
                            text = "LIVE STREAM BROADCAST",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = LiveRed,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}
