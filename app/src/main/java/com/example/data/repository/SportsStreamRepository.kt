package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.crex.CrexCategoryFeedState
import com.example.data.crex.CrexCricketService
import com.example.data.crex.CrexDashboardState
import com.example.data.crex.CrexMatchDetailState
import com.example.data.model.*
import com.example.data.util.TeamFlagResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class SportsStreamRepository(
    private val context: Context,
    val crexService: CrexCricketService = CrexCricketService()
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("football_live_tv_prefs", Context.MODE_PRIVATE)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val favoriteMatchIds = mutableSetOf<String>()

    private val _crexDashboardState = MutableStateFlow(CrexDashboardState())
    val crexDashboardState: StateFlow<CrexDashboardState> = _crexDashboardState.asStateFlow()

    private val _matches = MutableStateFlow<List<LiveMatch>>(emptyList())
    val matches: StateFlow<List<LiveMatch>> = _matches.asStateFlow()

    private val _channels = MutableStateFlow<List<SportsChannel>>(emptyList())
    val channels: StateFlow<List<SportsChannel>> = _channels.asStateFlow()

    private val _categories = MutableStateFlow<List<CategoryItem>>(emptyList())
    val categories: StateFlow<List<CategoryItem>> = _categories.asStateFlow()

    private val _highlights = MutableStateFlow<List<HighlightItem>>(emptyList())
    val highlights: StateFlow<List<HighlightItem>> = _highlights.asStateFlow()

    private val _config = MutableStateFlow(AppConfig())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    init {
        loadData()
    }

    private val footballMatches = mutableListOf<LiveMatch>()

    private fun loadData() {
        val savedFavs = prefs.getStringSet("favorite_match_ids", emptySet()).orEmpty()
        favoriteMatchIds.clear()
        favoriteMatchIds.addAll(savedFavs)

        val savedConfigJson = prefs.getString("app_config", null)
        if (savedConfigJson != null) {
            try {
                val obj = JSONObject(savedConfigJson)
                val rawTitle = obj.optString("appTitle", "SportzX Live TV")
                val resolvedTitle = if (rawTitle.equals("Football Live Tv", ignoreCase = true)) "SportzX Live TV" else rawTitle
                val rawAnnounce = obj.optString("announcement", "Welcome to SportzX Live TV! Enjoy real-time match updates & multi-server HD streams.")
                val resolvedAnnounce = rawAnnounce.replace("Football Live TV", "SportzX Live TV", ignoreCase = true)
                _config.value = AppConfig(
                    appTitle = resolvedTitle,
                    version = obj.optString("version", "8.0"),
                    announcement = resolvedAnnounce,
                    telegramLink = obj.optString("telegramLink", "https://t.me/sports_live_streams"),
                    adminPin = obj.optString("adminPin", "1234"),
                    forceLowQuality = obj.optBoolean("forceLowQuality", false),
                    isHardwareAccelerationEnabled = obj.optBoolean("isHardwareAccelerationEnabled", true),
                    remoteFeedUrl = obj.optString("remoteFeedUrl", "https://api.goscorer.com/api/v3/getLiveMatches"),
                    autoSyncOnLaunch = obj.optBoolean("autoSyncOnLaunch", true)
                )
            } catch (e: Exception) {
                _config.value = AppConfig()
            }
        }

        footballMatches.clear()
        footballMatches.addAll(defaultFootballMatches())
        rebuildCombinedMatches()

        val savedChannelsJson = prefs.getString("saved_channels_v3", null)
        if (savedChannelsJson != null) {
            try {
                val list = deserializeChannels(savedChannelsJson)
                if (list.isNotEmpty()) {
                    _channels.value = list
                } else {
                    _channels.value = defaultChannels()
                }
            } catch (e: Exception) {
                _channels.value = defaultChannels()
            }
        } else {
            _channels.value = defaultChannels()
        }

        _categories.value = defaultCategories()
        _highlights.value = defaultHighlights()
    }

    private fun saveData() {
        try {
            val channelsJson = serializeChannels(_channels.value)
            val configObj = JSONObject().apply {
                put("appTitle", _config.value.appTitle)
                put("version", _config.value.version)
                put("announcement", _config.value.announcement)
                put("telegramLink", _config.value.telegramLink)
                put("adminPin", _config.value.adminPin)
                put("forceLowQuality", _config.value.forceLowQuality)
                put("isHardwareAccelerationEnabled", _config.value.isHardwareAccelerationEnabled)
                put("remoteFeedUrl", _config.value.remoteFeedUrl)
                put("autoSyncOnLaunch", _config.value.autoSyncOnLaunch)
            }
            prefs.edit()
                .putStringSet("favorite_match_ids", favoriteMatchIds.toSet())
                .putString("saved_channels_v3", channelsJson)
                .putString("app_config", configObj.toString())
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyFavorites(list: List<LiveMatch>): List<LiveMatch> {
        return list.map { m ->
            m.copy(isFavorite = m.id in favoriteMatchIds)
        }
    }

    private fun rebuildCombinedMatches() {
        val state = _crexDashboardState.value
        val fbWithFavs = applyFavorites(footballMatches)
        val combined = buildList {
            addAll(applyFavorites(state.liveState.matches))
            addAll(fbWithFavs.filter { it.status == MatchStatus.LIVE })
            addAll(applyFavorites(state.upcomingState.matches))
            addAll(fbWithFavs.filter { it.status == MatchStatus.UPCOMING })
            addAll(applyFavorites(state.finishedState.matches))
            addAll(fbWithFavs.filter { it.status == MatchStatus.FINISHED })
        }
        _matches.value = combined
    }

    /**
     * Refreshes Live matches via `GET /api/cricket/live` (`https://api.goscorer.com/api/v3/getLiveMatches`).
     * Uses shared 25-second cache and in-flight deduplication.
     */
    suspend fun refreshLiveMatches(force: Boolean = false): CrexCategoryFeedState {
        val current = _crexDashboardState.value.liveState
        if (current.matches.isEmpty()) {
            _crexDashboardState.value = _crexDashboardState.value.copy(
                liveState = current.copy(isLoading = true, errorMessage = null)
            )
        }
        val fetched = crexService.fetchLiveMatches(forceRefresh = force)
        val withFavs = fetched.copy(matches = applyFavorites(fetched.matches))
        _crexDashboardState.value = _crexDashboardState.value.copy(liveState = withFavs)
        rebuildCombinedMatches()
        return withFavs
    }

    /**
     * Refreshes Upcoming matches via `GET /api/cricket/upcoming` (`POST getHomeUpcomingMatchesCE {"type": 1}`).
     * Uses 15-minute cache and in-flight deduplication.
     */
    suspend fun refreshUpcomingMatches(force: Boolean = false): CrexCategoryFeedState {
        val current = _crexDashboardState.value.upcomingState
        if (current.matches.isEmpty()) {
            _crexDashboardState.value = _crexDashboardState.value.copy(
                upcomingState = current.copy(isLoading = true, errorMessage = null)
            )
        }
        val fetched = crexService.fetchUpcomingMatches(forceRefresh = force)
        val withFavs = fetched.copy(matches = applyFavorites(fetched.matches))
        _crexDashboardState.value = _crexDashboardState.value.copy(upcomingState = withFavs)
        rebuildCombinedMatches()
        return withFavs
    }

    /**
     * Refreshes Finished matches via `GET /api/cricket/finished` (`POST getHomeUpcomingMatchesCE {"type": 0}`).
     * Called when Finished tab is opened and then no more often than every 30 minutes unless manually forced.
     */
    suspend fun refreshFinishedMatches(force: Boolean = false): CrexCategoryFeedState {
        val current = _crexDashboardState.value.finishedState
        val now = System.currentTimeMillis()
        if (!force && current.lastUpdatedEpochMs != null &&
            (now - current.lastUpdatedEpochMs) < CrexCricketService.FINISHED_CACHE_TTL_MS &&
            current.matches.isNotEmpty()
        ) {
            return current
        }

        if (current.matches.isEmpty()) {
            _crexDashboardState.value = _crexDashboardState.value.copy(
                finishedState = current.copy(isLoading = true, errorMessage = null)
            )
        }
        val fetched = crexService.fetchFinishedMatches(forceRefresh = force)
        val withFavs = fetched.copy(matches = applyFavorites(fetched.matches))
        _crexDashboardState.value = _crexDashboardState.value.copy(finishedState = withFavs)
        rebuildCombinedMatches()
        return withFavs
    }

    /**
     * Fetches individual match details on-demand via `GET /api/cricket/matches/{matchId}`
     * (`GET https://api.goscorer.com/api/v3/getSV3?key={matchId}`) ONLY when a user opens a match.
     */
    suspend fun loadMatchDetailOnDemand(matchId: String, force: Boolean = false): CrexMatchDetailState {
        _crexDashboardState.value = _crexDashboardState.value.copy(
            activeDetailState = CrexMatchDetailState(
                matchId = matchId,
                isLoading = true,
                detail = _crexDashboardState.value.activeDetailState.detail?.takeIf { it.matchId == matchId }
            )
        )
        val detailState = crexService.fetchMatchDetail(matchId = matchId, forceRefresh = force)
        _crexDashboardState.value = _crexDashboardState.value.copy(activeDetailState = detailState)

        // If getSV3 resolved human-readable team names in speech_names, update the match cards in place
        val d = detailState.detail
        if (d != null) {
            val v1 = TeamFlagResolver.resolve(d.team1Name, d.team1Key)
            val v2 = TeamFlagResolver.resolve(d.team2Name, d.team2Key)
            fun enrich(list: List<LiveMatch>): List<LiveMatch> = list.map { m ->
                if (m.id == matchId) {
                    m.copy(
                        team1Name = d.team1Name,
                        team1Code = v1.code,
                        team1FlagUrl = v1.flagUrl,
                        team2Name = d.team2Name,
                        team2Code = v2.code,
                        team2FlagUrl = v2.flagUrl
                    )
                } else {
                    m
                }
            }
            val cur = _crexDashboardState.value
            _crexDashboardState.value = cur.copy(
                liveState = cur.liveState.copy(matches = enrich(cur.liveState.matches)),
                upcomingState = cur.upcomingState.copy(matches = enrich(cur.upcomingState.matches)),
                finishedState = cur.finishedState.copy(matches = enrich(cur.finishedState.matches))
            )
            rebuildCombinedMatches()
        }
        return detailState
    }

    // Match Operations
    fun addOrUpdateMatch(match: LiveMatch) {
        val cur = _crexDashboardState.value
        fun upsert(list: List<LiveMatch>): List<LiveMatch> {
            val mList = list.toMutableList()
            val idx = mList.indexOfFirst { it.id == match.id }
            if (idx >= 0) mList[idx] = match else mList.add(0, match)
            return mList
        }
        _crexDashboardState.value = when (match.status) {
            MatchStatus.LIVE -> cur.copy(liveState = cur.liveState.copy(matches = upsert(cur.liveState.matches)))
            MatchStatus.UPCOMING -> cur.copy(upcomingState = cur.upcomingState.copy(matches = upsert(cur.upcomingState.matches)))
            MatchStatus.FINISHED -> cur.copy(finishedState = cur.finishedState.copy(matches = upsert(cur.finishedState.matches)))
        }
        rebuildCombinedMatches()
    }

    fun deleteMatch(matchId: String) {
        val cur = _crexDashboardState.value
        _crexDashboardState.value = cur.copy(
            liveState = cur.liveState.copy(matches = cur.liveState.matches.filter { it.id != matchId }),
            upcomingState = cur.upcomingState.copy(matches = cur.upcomingState.matches.filter { it.id != matchId }),
            finishedState = cur.finishedState.copy(matches = cur.finishedState.matches.filter { it.id != matchId })
        )
        rebuildCombinedMatches()
    }

    fun toggleMatchFavorite(matchId: String) {
        if (matchId in favoriteMatchIds) {
            favoriteMatchIds.remove(matchId)
        } else {
            favoriteMatchIds.add(matchId)
        }
        val cur = _crexDashboardState.value
        _crexDashboardState.value = cur.copy(
            liveState = cur.liveState.copy(matches = applyFavorites(cur.liveState.matches)),
            upcomingState = cur.upcomingState.copy(matches = applyFavorites(cur.upcomingState.matches)),
            finishedState = cur.finishedState.copy(matches = applyFavorites(cur.finishedState.matches))
        )
        rebuildCombinedMatches()
        saveData()
    }

    // Channel Operations
    fun addOrUpdateChannel(channel: SportsChannel) {
        val current = _channels.value.toMutableList()
        val index = current.indexOfFirst { it.id == channel.id }
        if (index >= 0) {
            current[index] = channel
        } else {
            current.add(0, channel)
        }
        _channels.value = current
        saveData()
    }

    fun deleteChannel(channelId: String) {
        _channels.value = _channels.value.filter { it.id != channelId }
        saveData()
    }

    fun toggleChannelFavorite(channelId: String) {
        _channels.value = _channels.value.map {
            if (it.id == channelId) it.copy(isFavorite = !it.isFavorite) else it
        }
        saveData()
    }

    fun updateConfig(newConfig: AppConfig) {
        _config.value = newConfig
        saveData()
    }

    fun resetToDefaults() {
        _channels.value = defaultChannels()
        _categories.value = defaultCategories()
        _highlights.value = defaultHighlights()
        _config.value = AppConfig()
        saveData()
    }

    /**
     * Exports all current matches, channels, and config as a clean formatted JSON string.
     */
    fun exportCurrentDataAsJson(): String {
        return try {
            val root = JSONObject()
            root.put("announcement", _config.value.announcement)
            root.put("telegram", _config.value.telegramLink)
            root.put("appTitle", _config.value.appTitle)

            val matchesArray = JSONArray()
            for (m in _matches.value) {
                val mo = JSONObject()
                mo.put("id", m.id)
                mo.put("tournament", m.tournament)
                mo.put("sport", m.sport.name)
                mo.put("team1", m.team1Name)
                mo.put("team1Code", m.team1Code)
                mo.put("team2", m.team2Name)
                mo.put("team2Code", m.team2Code)
                mo.put("team1Flag", m.team1FlagUrl)
                mo.put("team2Flag", m.team2FlagUrl)
                mo.put("status", m.status.name)
                mo.put("score", m.timeOrScore)
                mo.put("time", m.matchTime)

                val streamsArr = JSONArray()
                for (s in m.streams) {
                    val so = JSONObject()
                    so.put("name", s.name)
                    so.put("url", s.url)
                    so.put("quality", s.quality)
                    so.put("isDefault", s.isDefault)
                    if (!s.userAgent.isNullOrBlank()) so.put("userAgent", s.userAgent)
                    if (!s.referer.isNullOrBlank()) so.put("referer", s.referer)
                    streamsArr.put(so)
                }
                mo.put("streams", streamsArr)
                matchesArray.put(mo)
            }
            root.put("matches", matchesArray)

            val channelsArray = JSONArray()
            for (c in _channels.value) {
                val co = JSONObject()
                co.put("id", c.id)
                co.put("name", c.name)
                co.put("category", c.category)
                co.put("logoUrl", c.logoUrl)

                val streamsArr = JSONArray()
                for (s in c.streams) {
                    val so = JSONObject()
                    so.put("name", s.name)
                    so.put("url", s.url)
                    so.put("quality", s.quality)
                    if (!s.userAgent.isNullOrBlank()) so.put("userAgent", s.userAgent)
                    if (!s.referer.isNullOrBlank()) so.put("referer", s.referer)
                    streamsArr.put(so)
                }
                co.put("streams", streamsArr)
                channelsArray.put(co)
            }
            root.put("channels", channelsArray)

            root.toString(2)
        } catch (e: Exception) {
            "{}"
        }
    }

    // M3U / IPTV Playlist Importer
    suspend fun importM3UPlaylist(contentOrUrl: String): Int = withContext(Dispatchers.IO) {
        val rawText = if (contentOrUrl.trim().startsWith("http://", ignoreCase = true) ||
            contentOrUrl.trim().startsWith("https://", ignoreCase = true)
        ) {
            try {
                val req = Request.Builder().url(contentOrUrl.trim()).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() ?: "" else ""
                }
            } catch (e: Exception) {
                ""
            }
        } else {
            contentOrUrl
        }

        if (rawText.isBlank()) return@withContext 0

        val lines = rawText.lines()
        val parsedChannels = mutableListOf<SportsChannel>()
        var currentName = ""
        var currentCategory = "Imported IPTV"
        var currentLogo = ""
        var currentUserAgent: String? = null
        var currentReferer: String? = null

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                val groupMatch = Regex("""group-title="([^"]+)"""", RegexOption.IGNORE_CASE).find(trimmed)
                if (groupMatch != null) {
                    currentCategory = groupMatch.groupValues[1].trim()
                }
                val logoMatch = Regex("""tvg-logo="([^"]+)"""", RegexOption.IGNORE_CASE).find(trimmed)
                if (logoMatch != null) {
                    currentLogo = logoMatch.groupValues[1].trim()
                }
                val uaMatch = Regex("""user-agent="([^"]+)"""", RegexOption.IGNORE_CASE).find(trimmed)
                if (uaMatch != null) {
                    currentUserAgent = uaMatch.groupValues[1].trim()
                }
                val refMatch = Regex("""referer="([^"]+)"""", RegexOption.IGNORE_CASE).find(trimmed)
                if (refMatch != null) {
                    currentReferer = refMatch.groupValues[1].trim()
                }
                val commaIndex = trimmed.lastIndexOf(',')
                currentName = if (commaIndex != -1 && commaIndex < trimmed.length - 1) {
                    trimmed.substring(commaIndex + 1).trim()
                } else {
                    "Channel ${parsedChannels.size + 1}"
                }
            } else if (trimmed.startsWith("#EXTVLCOPT:", ignoreCase = true)) {
                val opt = trimmed.substringAfter(":", "").trim()
                if (opt.startsWith("http-user-agent=", ignoreCase = true)) {
                    currentUserAgent = opt.substringAfter("=").trim()
                } else if (opt.startsWith("http-referrer=", ignoreCase = true) || opt.startsWith("http-referer=", ignoreCase = true)) {
                    currentReferer = opt.substringAfter("=").trim()
                }
            } else if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                if (currentName.isBlank()) {
                    currentName = "Channel ${parsedChannels.size + 1}"
                }
                var cleanUrl = trimmed
                if (trimmed.contains("|")) {
                    cleanUrl = trimmed.substringBefore("|").trim()
                    val headerPart = trimmed.substringAfter("|")
                    headerPart.split("&", "|").forEach { kv ->
                        val k = kv.substringBefore("=").trim()
                        val v = kv.substringAfter("=", "").trim()
                        if (k.equals("User-Agent", ignoreCase = true) && v.isNotBlank()) currentUserAgent = v
                        if ((k.equals("Referer", ignoreCase = true) || k.equals("Origin", ignoreCase = true)) && v.isNotBlank()) currentReferer = v
                    }
                }
                val newChannel = SportsChannel(
                    name = currentName,
                    category = currentCategory,
                    logoText = currentName.take(3).uppercase(),
                    logoUrl = currentLogo,
                    streams = listOf(
                        StreamSource(
                            name = "$currentName (Direct)",
                            quality = if (cleanUrl.contains("1080") || cleanUrl.contains("fhd", ignoreCase = true)) "FHD" else "HD",
                            url = cleanUrl,
                            userAgent = currentUserAgent,
                            referer = currentReferer,
                            isDefault = true
                        )
                    )
                )
                parsedChannels.add(newChannel)
                currentName = ""
                currentCategory = "Imported IPTV"
                currentLogo = ""
                currentUserAgent = null
                currentReferer = null
            }
        }

        if (parsedChannels.isNotEmpty()) {
            val existing = _channels.value.toMutableList()
            existing.addAll(0, parsedChannels)
            _channels.value = existing
            saveData()
        }

        parsedChannels.size
    }

    fun normalizeRemoteFeedUrl(url: String): String = url.trim()

    /**
     * Manual refresh action that syncs Live, Upcoming, and Finished matches from the CREX backend routes.
     */
    suspend fun syncFromRemoteFeed(feedUrl: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        val liveRes = refreshLiveMatches(force = true)
        val upRes = refreshUpcomingMatches(force = true)
        val finRes = if (_crexDashboardState.value.finishedState.lastUpdatedEpochMs != null) {
            refreshFinishedMatches(force = true)
        } else {
            refreshFinishedMatches(force = false)
        }

        val total = liveRes.matches.size + upRes.matches.size + finRes.matches.size
        val err = liveRes.errorMessage ?: upRes.errorMessage ?: finRes.errorMessage
        if (total == 0 && err != null) {
            Result.failure(Exception(err))
        } else {
            Result.success(total)
        }
    }

    private fun defaultHighlights(): List<HighlightItem> {
        val hls1 = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        val hls2 = "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8"
        val hls3 = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"

        return listOf(
            HighlightItem(
                title = "Pakistan vs England - ODI Series Highlights",
                tournament = "CRICKET • ODI SERIES",
                duration = "12:45",
                videoUrl = hls2,
                sport = SportType.CRICKET
            ),
            HighlightItem(
                title = "Afghanistan vs Zimbabwe - ODI Highlights",
                tournament = "CRICKET • ODI SERIES",
                duration = "10:15",
                videoUrl = hls1,
                sport = SportType.CRICKET
            ),
            HighlightItem(
                title = "South Africa vs Australia - Test Match Recap",
                tournament = "CRICKET • TEST SERIES",
                duration = "09:30",
                videoUrl = hls3,
                sport = SportType.CRICKET
            )
        )
    }

    // Real M3U8 & Stream Health Checker using OkHttp
    suspend fun checkM3U8Stream(url: String): M3U8CheckResult = withContext(Dispatchers.IO) {
        if (url.isBlank()) {
            return@withContext M3U8CheckResult(
                url = url,
                isOnline = false,
                message = "URL cannot be empty"
            )
        }
        val startTime = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36 SportzXLiveTV/8.0")
                .header("Accept", "*/*")
                .build()

            val response = httpClient.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val statusCode = response.code
            val contentType = response.header("Content-Type") ?: "unknown"
            val isSuccess = response.isSuccessful

            var isHls = false
            if (contentType.contains("mpegurl", ignoreCase = true) || url.contains(".m3u8", ignoreCase = true)) {
                isHls = true
            }

            val peek = response.peekBody(512).string()
            if (peek.contains("#EXTM3U")) {
                isHls = true
            }
            response.close()

            M3U8CheckResult(
                url = url,
                isOnline = isSuccess,
                httpStatusCode = statusCode,
                latencyMs = latency,
                contentType = contentType,
                isHlsStream = isHls,
                message = if (isSuccess) "Active (${latency}ms) - $contentType" else "HTTP Error $statusCode"
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            M3U8CheckResult(
                url = url,
                isOnline = false,
                httpStatusCode = 0,
                latencyMs = latency,
                message = "Connection failed: ${e.localizedMessage ?: "Timeout"}"
            )
        }
    }

    private fun serializeChannels(channels: List<SportsChannel>): String {
        val array = JSONArray()
        for (c in channels) {
            val obj = JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("category", c.category)
                put("logoText", c.logoText)
                put("logoUrl", c.logoUrl)
                put("isFavorite", c.isFavorite)

                val streamArr = JSONArray()
                for (s in c.streams) {
                    val sObj = JSONObject().apply {
                        put("id", s.id)
                        put("name", s.name)
                        put("quality", s.quality)
                        put("url", s.url)
                        put("isDefault", s.isDefault)
                        if (!s.userAgent.isNullOrBlank()) put("userAgent", s.userAgent)
                        if (!s.referer.isNullOrBlank()) put("referer", s.referer)
                    }
                    streamArr.put(sObj)
                }
                put("streams", streamArr)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeChannels(json: String): List<SportsChannel> {
        val list = mutableListOf<SportsChannel>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val streamList = mutableListOf<StreamSource>()
            val streamArr = obj.optJSONArray("streams") ?: obj.optJSONArray("servers")
            if (streamArr != null) {
                for (j in 0 until streamArr.length()) {
                    val sObj = streamArr.getJSONObject(j)
                    val sUrl = sObj.optString("url").ifBlank { sObj.optString("link").ifBlank { sObj.optString("stream") } }
                    if (sUrl.isNotBlank()) {
                        streamList.add(
                            StreamSource(
                                id = sObj.optString("id").ifBlank { UUID.randomUUID().toString() },
                                name = sObj.optString("name", "Stream ${j + 1}"),
                                quality = sObj.optString("quality", "FHD"),
                                url = sUrl,
                                userAgent = sObj.optString("userAgent").ifBlank { null },
                                referer = sObj.optString("referer").ifBlank { null },
                                isDefault = sObj.optBoolean("isDefault", j == 0)
                            )
                        )
                    }
                }
            }

            val directUrl = obj.optString("url").ifBlank { obj.optString("link").ifBlank { obj.optString("stream") } }
            if (directUrl.isNotBlank() && streamList.none { it.url == directUrl }) {
                streamList.add(
                    StreamSource(
                        id = UUID.randomUUID().toString(),
                        name = "Direct Stream",
                        quality = "HD",
                        url = directUrl,
                        isDefault = true
                    )
                )
            }

            val cName = obj.optString("name").ifBlank { obj.optString("title").ifBlank { obj.optString("channel", "Sports Channel") } }
            val cCat = obj.optString("category").ifBlank { obj.optString("group", "Sports") }
            val cLogo = obj.optString("logoUrl").ifBlank { obj.optString("logo").ifBlank { obj.optString("image", "") } }

            list.add(
                SportsChannel(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = cName,
                    category = cCat,
                    logoText = obj.optString("logoText").ifBlank { cName.take(3).uppercase() },
                    logoUrl = cLogo,
                    streams = streamList,
                    isFavorite = obj.optBoolean("isFavorite", false)
                )
            )
        }
        return list
    }

    private fun defaultChannels(): List<SportsChannel> {
        val streamHls1 = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"
        val streamHls2 = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        val streamHls3 = "https://diceyk6a7voy4.cloudfront.net/e78752a1-2e83-43fa-85ae-3d508be29366/hls/de:02b6989d-e714-4227-9c57-a4c1a221a244:playlist.m3u8"

        return listOf(
            SportsChannel(
                id = "ch-tsports",
                name = "T Sports HD",
                category = "Bangla Sports",
                logoText = "TS",
                streams = listOf(
                    StreamSource(name = "T SPORTS 1 FHD", quality = "FHD", url = streamHls1, isDefault = true),
                    StreamSource(name = "T SPORTS 2 HD", quality = "HD", url = streamHls2)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-gtv",
                name = "Gazi TV (GTV)",
                category = "Bangla Sports",
                logoText = "GTV",
                streams = listOf(
                    StreamSource(name = "GTV LIVE FHD", quality = "FHD", url = streamHls2, isDefault = true),
                    StreamSource(name = "GTV BACKUP HD", quality = "HD", url = streamHls3)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-1",
                name = "TNT Sports",
                category = "Football",
                logoText = "TNT",
                streams = listOf(
                    StreamSource(name = "TNT SPORTS 1 HD", quality = "FHD", url = streamHls1, isDefault = true),
                    StreamSource(name = "TNT SPORTS 2 HD", quality = "HD", url = streamHls2),
                    StreamSource(name = "TNT BACKUP SD", quality = "SD", url = streamHls3)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-2",
                name = "TSN",
                category = "Sports",
                logoText = "TSN",
                streams = listOf(
                    StreamSource(name = "TSN 1 FHD", quality = "FHD", url = streamHls2, isDefault = true),
                    StreamSource(name = "TSN 2 HD", quality = "HD", url = streamHls1)
                )
            ),
            SportsChannel(
                id = "ch-3",
                name = "BeIN Sports",
                category = "Football",
                logoText = "beIN",
                streams = listOf(
                    StreamSource(name = "BeIN SPORTS 1 PREMIUM", quality = "FHD", url = streamHls3, isDefault = true),
                    StreamSource(name = "BeIN SPORTS 2 ENGLISH", quality = "FHD", url = streamHls1),
                    StreamSource(name = "BeIN SERVER 3", quality = "HD", url = streamHls2)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-4",
                name = "FOX Sports",
                category = "Sports",
                logoText = "FOX",
                streams = listOf(
                    StreamSource(name = "FS1 HD", quality = "FHD", url = streamHls1, isDefault = true),
                    StreamSource(name = "FS2 HD", quality = "HD", url = streamHls2)
                )
            ),
            SportsChannel(
                id = "ch-5",
                name = "Sony Sports Ten 1",
                category = "SonyLiv Live",
                logoText = "TEN1",
                streams = listOf(StreamSource(name = "SONY TEN 1 FHD", quality = "FHD", url = streamHls2, isDefault = true))
            ),
            SportsChannel(
                id = "ch-6",
                name = "Sony Sports Ten 2",
                category = "SonyLiv Live",
                logoText = "TEN2",
                streams = listOf(StreamSource(name = "SONY TEN 2 FHD", quality = "FHD", url = streamHls3, isDefault = true))
            ),
            SportsChannel(
                id = "ch-8",
                name = "ICC TV Official",
                category = "Cricket",
                logoText = "ICC",
                streams = listOf(StreamSource(name = "ICC TV LIVE FHD", quality = "FHD", url = streamHls2, isDefault = true))
            ),
            SportsChannel(
                id = "ch-9",
                name = "CricHD Live 1",
                category = "Cricket",
                logoText = "CRIC",
                streams = listOf(StreamSource(name = "CRICHD SERVER 1", quality = "FHD", url = streamHls1, isDefault = true))
            ),
            SportsChannel(
                id = "ch-18",
                name = "Star Sports",
                category = "Cricket",
                logoText = "STAR",
                streams = listOf(
                    StreamSource(name = "STAR SPORTS 1 HD", quality = "FHD", url = streamHls1, isDefault = true),
                    StreamSource(name = "STAR SPORTS SELECT 1", quality = "HD", url = streamHls2)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-19",
                name = "FanCode",
                category = "FanCode IND",
                logoText = "FC",
                streams = listOf(
                    StreamSource(name = "FANCODE LIVE 1", quality = "FHD", url = streamHls3, isDefault = true),
                    StreamSource(name = "FANCODE LIVE 2", quality = "HD", url = streamHls1)
                )
            ),
            SportsChannel(
                id = "ch-20",
                name = "PTV Sports",
                category = "Pakistan",
                logoText = "PTV",
                streams = listOf(
                    StreamSource(name = "PTV SPORTS HD LIVE", quality = "FHD", url = streamHls2, isDefault = true),
                    StreamSource(name = "PTV SPORTS SD", quality = "SD", url = streamHls3)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-21",
                name = "A Sports",
                category = "Pakistan",
                logoText = "A",
                streams = listOf(StreamSource(name = "A SPORTS HD", quality = "FHD", url = streamHls1, isDefault = true))
            ),
            SportsChannel(
                id = "ch-23",
                name = "Tapmad PK",
                category = "Tapmad PK",
                logoText = "tapmad",
                streams = listOf(
                    StreamSource(name = "TAPMAD LIVE 1", quality = "FHD", url = streamHls3, isDefault = true),
                    StreamSource(name = "TAPMAD LIVE 2", quality = "HD", url = streamHls1)
                )
            ),
            SportsChannel(
                id = "ch-24",
                name = "Willow Cricket",
                category = "Cricket",
                logoText = "WILLOW",
                streams = listOf(
                    StreamSource(name = "WILLOW HD 1", quality = "FHD", url = streamHls1, isDefault = true),
                    StreamSource(name = "WILLOW HD 2", quality = "HD", url = streamHls2)
                ),
                isFavorite = true
            )
        )
    }

    private fun defaultFootballMatches(): List<LiveMatch> {
        val streamHls1 = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        val streamHls2 = "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8"
        val streamHls3 = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

        return listOf(
            LiveMatch(
                id = "fb-ucl-2026-rm-bvb",
                tournament = "UEFA CHAMPIONS LEAGUE • LEAGUE PHASE",
                sport = SportType.FOOTBALL,
                status = MatchStatus.LIVE,
                matchTime = "Live Now • Santiago Bernabéu, Madrid",
                timeOrScore = "",
                team1Name = "Real Madrid",
                team1Code = "RMA",
                team1FlagUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/86.png",
                team2Name = "Borussia Dortmund",
                team2Code = "BVB",
                team2FlagUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/124.png",
                streams = listOf(
                    StreamSource(name = "TNT SPORTS 1 FHD", quality = "1080p FHD", url = streamHls1, isDefault = true),
                    StreamSource(name = "SONY TEN 2 HD", quality = "720p HD", url = streamHls2),
                    StreamSource(name = "BEIN SPORTS 1", quality = "480p SD", url = streamHls3)
                )
            ),
            LiveMatch(
                id = "fb-ucl-2026-bar-bay",
                tournament = "UEFA CHAMPIONS LEAGUE • LEAGUE PHASE",
                sport = SportType.FOOTBALL,
                status = MatchStatus.UPCOMING,
                matchTime = "22 Oct 2026, 01:00 AM BST • Estadi Olímpic, Barcelona",
                timeOrScore = "",
                team1Name = "Barcelona",
                team1Code = "BAR",
                team1FlagUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/83.png",
                team2Name = "Bayern Munich",
                team2Code = "BAY",
                team2FlagUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/132.png",
                streams = listOf(
                    StreamSource(name = "TNT SPORTS 1 FHD", quality = "1080p FHD", url = streamHls1, isDefault = true),
                    StreamSource(name = "SONY TEN 2 HD", quality = "720p HD", url = streamHls2),
                    StreamSource(name = "BEIN SPORTS ENGLISH", quality = "480p SD", url = streamHls3)
                )
            ),
            LiveMatch(
                id = "fb-epl-2026-ars-liv",
                tournament = "ENGLISH PREMIER LEAGUE • MATCHWEEK 9",
                sport = SportType.FOOTBALL,
                status = MatchStatus.UPCOMING,
                matchTime = "25 Oct 2026, 09:30 PM BST • Emirates Stadium, London",
                timeOrScore = "",
                team1Name = "Arsenal",
                team1Code = "ARS",
                team1FlagUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/359.png",
                team2Name = "Liverpool",
                team2Code = "LIV",
                team2FlagUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/364.png",
                streams = listOf(
                    StreamSource(name = "SKY SPORTS MAIN EVENT", quality = "1080p FHD", url = streamHls2, isDefault = true),
                    StreamSource(name = "STAR SPORTS SELECT 1", quality = "720p HD", url = streamHls1)
                )
            ),
            LiveMatch(
                id = "fb-wcq-2026-arg-bra",
                tournament = "FIFA WORLD CUP QUALIFIERS • CONMEBOL",
                sport = SportType.FOOTBALL,
                status = MatchStatus.FINISHED,
                matchTime = "Full Time • Estadio Monumental, Buenos Aires",
                timeOrScore = "",
                team1Name = "Argentina",
                team1Code = "ARG",
                team1FlagUrl = "https://flagcdn.com/w160/ar.png",
                team2Name = "Brazil",
                team2Code = "BRA",
                team2FlagUrl = "https://flagcdn.com/w160/br.png",
                streams = listOf(
                    StreamSource(name = "TYC SPORTS REPLAY HD", quality = "1080p FHD", url = streamHls1, isDefault = true),
                    StreamSource(name = "BEIN SPORTS REPLAY", quality = "720p HD", url = streamHls2)
                )
            )
        )
    }

    private fun defaultCategories(): List<CategoryItem> {
        return listOf(
            CategoryItem(name = "LIVE EVENTS", channelCount = 22),
            CategoryItem(name = "CRICKET", channelCount = 12),
            CategoryItem(name = "FOOTBALL", channelCount = 14),
            CategoryItem(name = "BANGLA SPORTS", channelCount = 6),
            CategoryItem(name = "PAKISTAN", channelCount = 8),
            CategoryItem(name = "SONYLIV LIVE", channelCount = 8),
            CategoryItem(name = "FANCODE IND", channelCount = 10),
            CategoryItem(name = "TAPMAD PK", channelCount = 6),
            CategoryItem(name = "SPORTS", channelCount = 20),
            CategoryItem(name = "HIGHLIGHTS", channelCount = 3)
        )
    }
}
