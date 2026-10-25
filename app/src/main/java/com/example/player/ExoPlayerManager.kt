package com.example.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.data.model.StreamSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class ExoPlayerManager(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentStream = MutableStateFlow<StreamSource?>(null)
    val currentStream: StateFlow<StreamSource?> = _currentStream.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _selectedQualityLabel = MutableStateFlow("Auto")
    val selectedQualityLabel: StateFlow<String> = _selectedQualityLabel.asStateFlow()

    private var positionUpdateJob: kotlinx.coroutines.Job? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    fun getPlayer(): ExoPlayer {
        if (exoPlayer == null) {
            val renderersFactory = DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true)

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15_000, // min buffer 15s
                    50_000, // max buffer 50s
                    1_500,  // playback start buffer 1.5s (fast start)
                    3_000   // re-buffer 3s
                )
                .build()

            exoPlayer = ExoPlayer.Builder(context, renderersFactory)
                .setLoadControl(loadControl)
                .setSeekBackIncrementMs(10_000)
                .setSeekForwardIncrementMs(10_000)
                .build()
                .apply {
                    repeatMode = Player.REPEAT_MODE_OFF
                    playWhenReady = true
                    volume = if (_isMuted.value) 0.0f else 1.0f
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_BUFFERING -> {
                                    _isBuffering.value = true
                                    _errorMessage.value = null
                                }
                                Player.STATE_READY -> {
                                    _isBuffering.value = false
                                    _errorMessage.value = null
                                    _duration.value = duration.coerceAtLeast(0L)
                                    startPositionTracking()
                                }
                                Player.STATE_ENDED -> {
                                    _isBuffering.value = false
                                    _isPlaying.value = false
                                    stopPositionTracking()
                                }
                                Player.STATE_IDLE -> {
                                    _isBuffering.value = false
                                    stopPositionTracking()
                                }
                            }
                        }

                        override fun onIsPlayingChanged(playing: Boolean) {
                            _isPlaying.value = playing
                            if (playing) {
                                startPositionTracking()
                            } else {
                                stopPositionTracking()
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            _isBuffering.value = false
                            _isPlaying.value = false
                            stopPositionTracking()
                            val cause = error.message ?: "Unknown playback error"
                            _errorMessage.value = "Playback failed: $cause. Please switch stream server."
                        }
                    })
                }
        }
        return exoPlayer!!
    }

    private fun startPositionTracking() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (true) {
                exoPlayer?.let { p ->
                    _currentPosition.value = p.currentPosition.coerceAtLeast(0L)
                    _duration.value = p.duration.coerceAtLeast(0L)
                }
                delay(500)
            }
        }
    }

    private fun stopPositionTracking() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    fun toggleMute() {
        val newMuted = !_isMuted.value
        _isMuted.value = newMuted
        exoPlayer?.volume = if (newMuted) 0.0f else 1.0f
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        exoPlayer?.volume = if (muted) 0.0f else 1.0f
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        exoPlayer?.setPlaybackSpeed(speed)
    }

    fun setVideoQualityConstraint(maxHeight: Int?, label: String) {
        _selectedQualityLabel.value = label
        exoPlayer?.let { player ->
            player.trackSelectionParameters = if (maxHeight == null) {
                player.trackSelectionParameters
                    .buildUpon()
                    .clearVideoSizeConstraints()
                    .setMaxVideoBitrate(Int.MAX_VALUE)
                    .build()
            } else {
                val maxWidth = (maxHeight * 16) / 9
                val bitrate = when {
                    maxHeight >= 1080 -> 8_000_000
                    maxHeight >= 720 -> 4_000_000
                    maxHeight >= 480 -> 1_500_000
                    else -> 800_000
                }
                player.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(maxWidth, maxHeight)
                    .setMaxVideoBitrate(bitrate)
                    .build()
            }
        }
    }

    fun playStream(stream: StreamSource, forceLowQuality: Boolean = false) {
        if (stream.url.isBlank()) {
            _isBuffering.value = false
            _errorMessage.value = "Stream URL is empty. Please select another server."
            return
        }
        try {
            val player = getPlayer()
            _currentStream.value = stream
            _errorMessage.value = null
            _isBuffering.value = true

            if (forceLowQuality) {
                _selectedQualityLabel.value = "480p SD"
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSizeSd()
                    .setMaxVideoBitrate(800_000)
                    .build()
            } else if (_selectedQualityLabel.value == "Auto") {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .clearVideoSizeConstraints()
                    .setMaxVideoBitrate(Int.MAX_VALUE)
                    .build()
            }

            val mediaSource = createMediaSource(stream)
            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            _isBuffering.value = false
            _errorMessage.value = "Failed to initialize stream: ${e.localizedMessage ?: "Invalid URL"}"
        }
    }

    private fun createMediaSource(stream: StreamSource): MediaSource {
        val uri = Uri.parse(stream.url.trim())
        val userAgent = stream.userAgent?.takeIf { it.isNotBlank() } ?: "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36"

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(10_000)
            .setAllowCrossProtocolRedirects(true)

        val headers = mutableMapOf<String, String>()
        stream.referer?.takeIf { it.isNotBlank() }?.let { headers["Referer"] = it }
        stream.origin?.takeIf { it.isNotBlank() }?.let { headers["Origin"] = it }
        stream.cookie?.takeIf { it.isNotBlank() }?.let { headers["Cookie"] = it }
        if (headers.isNotEmpty()) {
            httpDataSourceFactory.setDefaultRequestProperties(headers)
        }

        val isHls = stream.url.contains(".m3u8", ignoreCase = true) ||
                stream.quality.contains("HLS", ignoreCase = true)

        return if (isHls) {
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()

            HlsMediaSource.Factory(httpDataSourceFactory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(mediaItem)
        } else {
            val mediaItem = MediaItem.fromUri(uri)
            ProgressiveMediaSource.Factory(httpDataSourceFactory)
                .createMediaSource(mediaItem)
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        exoPlayer?.play()
    }

    fun seekRelative(offsetMs: Long) {
        exoPlayer?.let { player ->
            val newPos = (player.currentPosition + offsetMs).coerceIn(0L, player.duration.coerceAtLeast(0L))
            player.seekTo(newPos)
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun release() {
        stopPositionTracking()
        exoPlayer?.release()
        exoPlayer = null
        _isPlaying.value = false
        _isBuffering.value = false
    }
}
