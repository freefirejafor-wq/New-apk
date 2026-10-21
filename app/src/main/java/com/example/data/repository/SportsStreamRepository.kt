package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.*
import com.example.data.util.MskianLiveCricketFetcher
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

class SportsStreamRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("football_live_tv_prefs", Context.MODE_PRIVATE)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

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

    private fun loadData() {
        val savedConfigJson = prefs.getString("app_config", null)
        if (savedConfigJson != null) {
            try {
                val obj = JSONObject(savedConfigJson)
                val rawTitle = obj.optString("appTitle", "SportzX Live TV")
                val resolvedTitle = if (rawTitle.equals("Football Live Tv", ignoreCase = true)) "SportzX Live TV" else rawTitle
                val rawAnnounce = obj.optString("announcement", "Welcome to SportzX Live TV! Multiple high-definition stream servers are active.")
                val resolvedAnnounce = rawAnnounce.replace("Football Live TV", "SportzX Live TV", ignoreCase = true)
                _config.value = AppConfig(
                    appTitle = resolvedTitle,
                    version = obj.optString("version", "8.0"),
                    announcement = resolvedAnnounce,
                    telegramLink = obj.optString("telegramLink", "https://t.me/sports_live_streams"),
                    adminPin = obj.optString("adminPin", "1234"),
                    forceLowQuality = obj.optBoolean("forceLowQuality", false),
                    isHardwareAccelerationEnabled = obj.optBoolean("isHardwareAccelerationEnabled", true),
                    remoteFeedUrl = obj.optString("remoteFeedUrl", "https://raw.githubusercontent.com/freefirejafor-wq/Upder/main/matches.json"),
                    autoSyncOnLaunch = obj.optBoolean("autoSyncOnLaunch", true)
                )
            } catch (e: Exception) {
                _config.value = AppConfig()
            }
        }

        val savedMatchesJson = prefs.getString("saved_matches_v10_senior_intl", null)
        if (savedMatchesJson != null) {
            try {
                val list = deserializeMatches(savedMatchesJson).filter {
                    TeamFlagResolver.isSeniorInternationalMatch(it.team1Name, it.team2Name, it.tournament)
                }
                if (list.isNotEmpty()) {
                    _matches.value = list
                } else {
                    _matches.value = defaultMatches()
                }
            } catch (e: Exception) {
                _matches.value = defaultMatches()
            }
        } else {
            _matches.value = defaultMatches()
        }

        val savedChannelsJson = prefs.getString("saved_channels", null)
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
            val matchesJson = serializeMatches(_matches.value)
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
                .putString("saved_matches_v10_senior_intl", matchesJson)
                .putString("saved_channels", channelsJson)
                .putString("app_config", configObj.toString())
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Match Operations
    fun addOrUpdateMatch(match: LiveMatch) {
        val current = _matches.value.toMutableList()
        val index = current.indexOfFirst { it.id == match.id }
        if (index >= 0) {
            current[index] = match
        } else {
            current.add(0, match)
        }
        _matches.value = current
        saveData()
    }

    fun deleteMatch(matchId: String) {
        _matches.value = _matches.value.filter { it.id != matchId }
        saveData()
    }

    fun toggleMatchFavorite(matchId: String) {
        _matches.value = _matches.value.map {
            if (it.id == matchId) it.copy(isFavorite = !it.isFavorite) else it
        }
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
        _matches.value = defaultMatches()
        _channels.value = defaultChannels()
        _categories.value = defaultCategories()
        _highlights.value = defaultHighlights()
        _config.value = AppConfig()
        saveData()
    }

    /**
     * Exports all current matches, channels, and config as a clean formatted JSON string.
     * Can be pasted directly to Pastebin or GitHub to broadcast updates to all users.
     */
    fun exportCurrentDataAsJson(): String {
        return try {
            val root = org.json.JSONObject()
            root.put("announcement", _config.value.announcement)
            root.put("telegram", _config.value.telegramLink)
            root.put("appTitle", _config.value.appTitle)

            val matchesArray = org.json.JSONArray()
            for (m in _matches.value) {
                val mo = org.json.JSONObject()
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

                val streamsArr = org.json.JSONArray()
                for (s in m.streams) {
                    val so = org.json.JSONObject()
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

            val channelsArray = org.json.JSONArray()
            for (c in _channels.value) {
                val co = org.json.JSONObject()
                co.put("id", c.id)
                co.put("name", c.name)
                co.put("category", c.category)
                co.put("logoUrl", c.logoUrl)

                val streamsArr = org.json.JSONArray()
                for (s in c.streams) {
                    val so = org.json.JSONObject()
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
                // Parse attributes
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
                // Name is after comma
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
                // Support pipe syntax: https://stream.m3u8|Referer=https://...&User-Agent=...
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
                // Reset
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

    // Remote Feed Synchronizer with automatic URL normalization (Pastebin, GitHub Raw, Gist)
    fun normalizeRemoteFeedUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return ""

        // Handle Pastebin: https://pastebin.com/xyz -> https://pastebin.com/raw/xyz
        val pbMatch = Regex("""^https?://(?:www\.)?pastebin\.com/([a-zA-Z0-9]+)$""", RegexOption.IGNORE_CASE).find(trimmed)
        if (pbMatch != null) {
            val id = pbMatch.groupValues[1]
            if (id.lowercase() != "raw") {
                return "https://pastebin.com/raw/$id"
            }
        }

        // Handle GitHub blob: https://github.com/user/repo/blob/branch/file.json -> https://raw.githubusercontent.com/user/repo/branch/file.json
        val ghMatch = Regex("""^https?://github\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.*)$""", RegexOption.IGNORE_CASE).find(trimmed)
        if (ghMatch != null) {
            val user = ghMatch.groupValues[1]
            val repo = ghMatch.groupValues[2]
            val branch = ghMatch.groupValues[3]
            val path = ghMatch.groupValues[4]
            return "https://raw.githubusercontent.com/$user/$repo/$branch/$path"
        }

        // Handle GitHub Gist: https://gist.github.com/user/id -> https://gist.githubusercontent.com/user/id/raw
        val gistMatch = Regex("""^https?://gist\.github\.com/([^/]+)/([a-zA-Z0-9]+)$""", RegexOption.IGNORE_CASE).find(trimmed)
        if (gistMatch != null) {
            val user = gistMatch.groupValues[1]
            val id = gistMatch.groupValues[2]
            return "https://gist.githubusercontent.com/$user/$id/raw"
        }

        // Handle Paste.ee: https://paste.ee/p/xyz -> https://paste.ee/r/xyz
        val pasteEeMatch = Regex("""^https?://(?:www\.)?paste\.ee/p/([a-zA-Z0-9]+)$""", RegexOption.IGNORE_CASE).find(trimmed)
        if (pasteEeMatch != null) {
            return "https://paste.ee/r/${pasteEeMatch.groupValues[1]}"
        }

        return trimmed
    }

    suspend fun syncFromRemoteFeed(feedUrl: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        var totalUpdated = 0

        // 1. Fetch real live & upcoming International Football, Club Football, and Cricket matches from public scoreboard APIs
        val realApiMatches = try {
            fetchRealTimeSportsMatches()
        } catch (e: Exception) {
            emptyList()
        }

        // 2. Also fetch custom JSON feed if configured
        val rawTarget = feedUrl?.takeIf { it.isNotBlank() } ?: _config.value.remoteFeedUrl
        val targetUrl = normalizeRemoteFeedUrl(rawTarget)
        var customRemoteMatches = emptyList<LiveMatch>()

        if (targetUrl.isNotBlank()) {
            try {
                val request = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", "Mozilla/5.0 (Android; SportzXLiveTV/8.0)")
                    .header("Accept", "application/json, text/plain, */*")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()?.trim() ?: ""
                        if (body.isNotBlank()) {
                            // Support mskian/live-cricket-score-api JSON format or Cricbuzz HTML directly
                            val mskianParsed = MskianLiveCricketFetcher.parseMskianApiJson(body)
                            if (mskianParsed.isNotEmpty()) {
                                customRemoteMatches = mskianParsed
                                totalUpdated += mskianParsed.size
                            } else if (body.contains("cb-mtch-lst", ignoreCase = true)) {
                                val cbParsed = MskianLiveCricketFetcher.parseCricbuzzHtml(body, MatchStatus.LIVE)
                                if (cbParsed.isNotEmpty()) {
                                    customRemoteMatches = cbParsed
                                    totalUpdated += cbParsed.size
                                }
                            } else if (body.startsWith("[")) {
                                val parsed = deserializeMatches(body)
                                if (parsed.isNotEmpty()) {
                                    customRemoteMatches = parsed
                                    totalUpdated += parsed.size
                                }
                            } else if (body.startsWith("{")) {
                                val rootJson = JSONObject(body)
                                val container = if (rootJson.has("data") && rootJson.optJSONObject("data") != null) {
                                    rootJson.getJSONObject("data")
                                } else if (rootJson.has("feed") && rootJson.optJSONObject("feed") != null) {
                                    rootJson.getJSONObject("feed")
                                } else {
                                    rootJson
                                }

                                val matchesKey = listOf("matches", "live_matches", "events", "match_list", "games").firstOrNull { container.has(it) }
                                if (matchesKey != null) {
                                    val matchesArray = container.getJSONArray(matchesKey)
                                    val parsed = deserializeMatches(matchesArray.toString())
                                    if (parsed.isNotEmpty()) {
                                        customRemoteMatches = parsed
                                        totalUpdated += parsed.size
                                    }
                                }

                                val channelsKey = listOf("channels", "tv_channels", "sports_channels", "tv").firstOrNull { container.has(it) }
                                if (channelsKey != null) {
                                    val channelsArray = container.getJSONArray(channelsKey)
                                    val parsed = deserializeChannels(channelsArray.toString())
                                    if (parsed.isNotEmpty()) {
                                        _channels.value = parsed
                                        totalUpdated += parsed.size
                                    }
                                }

                                val announcementKey = listOf("announcement", "banner", "notice", "message", "broadcast").firstOrNull { container.has(it) }
                                if (announcementKey != null) {
                                    val newAnnounce = container.optString(announcementKey)
                                    if (newAnnounce.isNotBlank()) {
                                        _config.value = _config.value.copy(announcement = newAnnounce)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Proceed with real sports API + curated international schedule
            }
        }

        // Merge custom remote matches + real live API matches + curated senior international schedule (strictly Senior National Teams only)
        val merged = mutableListOf<LiveMatch>()
        val seenKeys = mutableSetOf<String>()

        fun addUnique(list: List<LiveMatch>) {
            for (m in list) {
                if (!TeamFlagResolver.isSeniorInternationalMatch(m.team1Name, m.team2Name, m.tournament)) {
                    continue
                }
                val v1 = TeamFlagResolver.resolve(m.team1Name, m.team1Code, m.team1FlagUrl)
                val v2 = TeamFlagResolver.resolve(m.team2Name, m.team2Code, m.team2FlagUrl)
                val normalizedMatch = m.copy(
                    team1Name = v1.countryName.ifBlank { m.team1Name },
                    team1Code = v1.code,
                    team1FlagUrl = v1.flagUrl,
                    team2Name = v2.countryName.ifBlank { m.team2Name },
                    team2Code = v2.code,
                    team2FlagUrl = v2.flagUrl
                )
                val key = "${normalizedMatch.sport.name}_${normalizedMatch.team1Code}_${normalizedMatch.team2Code}"
                val reverseKey = "${normalizedMatch.sport.name}_${normalizedMatch.team2Code}_${normalizedMatch.team1Code}"
                if (key !in seenKeys && reverseKey !in seenKeys) {
                    seenKeys.add(key)
                    merged.add(normalizedMatch)
                }
            }
        }

        addUnique(realApiMatches)
        addUnique(customRemoteMatches)
        addUnique(defaultMatches())

        // Sort so LIVE matches appear first, then UPCOMING, then FINISHED
        val sorted = merged.sortedBy {
            when (it.status) {
                MatchStatus.LIVE -> 0
                MatchStatus.UPCOMING -> 1
                MatchStatus.FINISHED -> 2
            }
        }

        if (sorted.isNotEmpty()) {
            _matches.value = sorted
            totalUpdated = sorted.size
            saveData()
        }

        Result.success(totalUpdated)
    }

    /**
     * Fetches real-time Senior National Team International Football and International Cricket
     * fixtures, live scores, local match times, and official national flags from public sports APIs.
     * Strictly excludes any U17/U18/U19/U20/U21/U23, Youth, Women, or Club matches.
     */
    private fun fetchRealTimeSportsMatches(): List<LiveMatch> {
        val results = mutableListOf<LiveMatch>()

        // 1. Fetch Live & Upcoming Senior International Cricket via mskian/live-cricket-score-api (Cricbuzz Live Scores & Cricinfo RSS)
        try {
            val mskianCricketMatches = MskianLiveCricketFetcher.fetchSeniorInternationalCricketMatches(httpClient)
            results.addAll(mskianCricketMatches)
        } catch (_: Exception) {
            // Continue with secondary endpoints
        }

        val akamaiHls1 = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"
        val akamaiHls2 = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        val akamaiHls3 = "https://diceyk6a7voy4.cloudfront.net/e78752a1-2e83-43fa-85ae-3d508be29366/hls/de:02b6989d-e714-4227-9c57-a4c1a221a244:playlist.m3u8"

        data class EndpointSpec(val label: String, val sport: SportType, val url: String)

        val espnEndpoints = listOf(
            EndpointSpec("INTL FOOTBALL • FIFA WORLD CUP QUALIFIERS (CONMEBOL)", SportType.FOOTBALL, "https://site.api.espn.com/apis/site/v2/sports/soccer/fifa.worldq.conmebol/scoreboard"),
            EndpointSpec("INTL FOOTBALL • FIFA WORLD CUP QUALIFIERS (UEFA)", SportType.FOOTBALL, "https://site.api.espn.com/apis/site/v2/sports/soccer/fifa.worldq.uefa/scoreboard"),
            EndpointSpec("INTL FOOTBALL • FIFA WORLD CUP QUALIFIERS (AFC)", SportType.FOOTBALL, "https://site.api.espn.com/apis/site/v2/sports/soccer/fifa.worldq.afc/scoreboard"),
            EndpointSpec("INTL FOOTBALL • FIFA WORLD CUP QUALIFIERS (CAF)", SportType.FOOTBALL, "https://site.api.espn.com/apis/site/v2/sports/soccer/fifa.worldq.caf/scoreboard"),
            EndpointSpec("INTL FOOTBALL • UEFA NATIONS LEAGUE", SportType.FOOTBALL, "https://site.api.espn.com/apis/site/v2/sports/soccer/uefa.nations/scoreboard"),
            EndpointSpec("INTL FOOTBALL • INTERNATIONAL FRIENDLIES", SportType.FOOTBALL, "https://site.api.espn.com/apis/site/v2/sports/soccer/fifa.friendly/scoreboard"),
            EndpointSpec("INTL CRICKET • ICC INTERNATIONAL SERIES", SportType.CRICKET, "https://site.api.espn.com/apis/site/v2/sports/cricket/8039/scoreboard"),
            EndpointSpec("INTL CRICKET • ICC WORLD TEST CHAMPIONSHIP", SportType.CRICKET, "https://site.api.espn.com/apis/site/v2/sports/cricket/8048/scoreboard")
        )

        for (spec in espnEndpoints) {
            try {
                val req = Request.Builder()
                    .url(spec.url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    val json = JSONObject(body)
                    val events = json.optJSONArray("events") ?: return@use
                    for (i in 0 until minOf(events.length(), 8)) {
                        val ev = events.getJSONObject(i)
                        val comps = ev.optJSONArray("competitions") ?: continue
                        if (comps.length() == 0) continue
                        val comp = comps.getJSONObject(0)
                        val competitors = comp.optJSONArray("competitors") ?: continue
                        if (competitors.length() < 2) continue

                        val home = competitors.getJSONObject(0)
                        val away = competitors.getJSONObject(1)
                        val homeTeam = home.optJSONObject("team") ?: continue
                        val awayTeam = away.optJSONObject("team") ?: continue

                        val rawT1Name = homeTeam.optString("displayName").ifBlank { homeTeam.optString("shortDisplayName", "") }
                        val rawT2Name = awayTeam.optString("displayName").ifBlank { awayTeam.optString("shortDisplayName", "") }
                        val eventName = ev.optString("name", "")

                        // Strictly enforce Senior National Main Teams only (no U17/U18/U19/Youth/Women/Club)
                        if (!TeamFlagResolver.isSeniorInternationalMatch(rawT1Name, rawT2Name, "$eventName ${spec.label}")) {
                            continue
                        }

                        val t1Code = homeTeam.optString("abbreviation").ifBlank { rawT1Name.take(3).uppercase() }
                        val t1Logo = homeTeam.optString("logo")
                        val t2Code = awayTeam.optString("abbreviation").ifBlank { rawT2Name.take(3).uppercase() }
                        val t2Logo = awayTeam.optString("logo")

                        val homeScore = home.optString("score", "0")
                        val awayScore = away.optString("score", "0")

                        val statusObj = ev.optJSONObject("status") ?: comp.optJSONObject("status")
                        val typeObj = statusObj?.optJSONObject("type")
                        val stateStr = typeObj?.optString("state", "pre") ?: "pre"
                        val detailStr = typeObj?.optString("shortDetail", "") ?: ""

                        val isoDate = ev.optString("date")
                        val formattedSchedule = formatIsoMatchTime(isoDate)

                        val matchStatus = when (stateStr.lowercase()) {
                            "in" -> MatchStatus.LIVE
                            "post" -> MatchStatus.FINISHED
                            else -> MatchStatus.UPCOMING
                        }

                        val v1 = TeamFlagResolver.resolve(rawT1Name, t1Code, t1Logo)
                        val v2 = TeamFlagResolver.resolve(rawT2Name, t2Code, t2Logo)

                        val scoreOrCountdown = when (matchStatus) {
                            MatchStatus.LIVE -> if (spec.sport == SportType.CRICKET) "${v1.code} $homeScore vs ${v2.code} $awayScore" else "$homeScore - $awayScore"
                            MatchStatus.FINISHED -> "FT ($homeScore - $awayScore)"
                            MatchStatus.UPCOMING -> computeRelativeCountdown(isoDate)
                        }

                        val matchClockOrDate = when (matchStatus) {
                            MatchStatus.LIVE -> detailStr.ifBlank { "Live Now • $formattedSchedule" }
                            MatchStatus.FINISHED -> "Finished • $formattedSchedule"
                            MatchStatus.UPCOMING -> formattedSchedule
                        }

                        results.add(
                            LiveMatch(
                                id = "espn-${ev.optString("id", UUID.randomUUID().toString())}",
                                tournament = spec.label,
                                sport = spec.sport,
                                team1Name = v1.countryName.ifBlank { rawT1Name },
                                team1Code = v1.code,
                                team1FlagUrl = v1.flagUrl,
                                team2Name = v2.countryName.ifBlank { rawT2Name },
                                team2Code = v2.code,
                                team2FlagUrl = v2.flagUrl,
                                status = matchStatus,
                                timeOrScore = scoreOrCountdown,
                                matchTime = matchClockOrDate,
                                streams = listOf(
                                    StreamSource(name = "T SPORTS / SONY TEN FHD", quality = "FHD", url = akamaiHls1, isDefault = true),
                                    StreamSource(name = "STAR SPORTS 1 HD", quality = "FHD", url = akamaiHls2),
                                    StreamSource(name = "BEIN SPORTS 1 ENGLISH", quality = "HD", url = akamaiHls3)
                                )
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                // Ignore individual endpoint timeout and continue
            }
        }
        return results
    }

    private fun formatIsoMatchTime(isoDate: String): String {
        if (isoDate.isBlank()) return "Today, 08:30 PM"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val parsedDate = parser.parse(isoDate) ?: return "Today, 08:30 PM"
            val nowCal = Calendar.getInstance()
            val matchCal = Calendar.getInstance().apply { time = parsedDate }

            val timeFmt = SimpleDateFormat("hh:mm a", Locale.US)
            val dateFmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.US)

            val isToday = nowCal.get(Calendar.YEAR) == matchCal.get(Calendar.YEAR) &&
                nowCal.get(Calendar.DAY_OF_YEAR) == matchCal.get(Calendar.DAY_OF_YEAR)

            val tomorrowCal = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            val isTomorrow = tomorrowCal.get(Calendar.YEAR) == matchCal.get(Calendar.YEAR) &&
                tomorrowCal.get(Calendar.DAY_OF_YEAR) == matchCal.get(Calendar.DAY_OF_YEAR)

            when {
                isToday -> "Today, ${timeFmt.format(parsedDate)}"
                isTomorrow -> "Tomorrow, ${timeFmt.format(parsedDate)}"
                else -> dateFmt.format(parsedDate)
            }
        } catch (e: Exception) {
            "Today, 08:30 PM"
        }
    }

    private fun computeRelativeCountdown(isoDate: String): String {
        if (isoDate.isBlank()) return "Upcoming Match"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val parsedDate = parser.parse(isoDate) ?: return "Upcoming Match"
            val diffMs = parsedDate.time - System.currentTimeMillis()
            if (diffMs <= 0) return "Starting Soon"
            val totalMinutes = diffMs / (1000 * 60)
            val hours = totalMinutes / 60
            val mins = totalMinutes % 60
            when {
                hours >= 24 -> "In ${hours / 24}d ${hours % 24}h"
                hours >= 1 -> "Starts in ${hours}h ${mins}m"
                else -> "Starts in ${mins}m"
            }
        } catch (e: Exception) {
            "Upcoming"
        }
    }

    private fun defaultHighlights(): List<HighlightItem> {
        val hls1 = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        val hls2 = "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8"
        val hls3 = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"

        return listOf(
            HighlightItem(
                title = "Bangladesh vs South Africa - All Wickets & Day 1 Highlights",
                tournament = "INTL CRICKET • ICC WORLD TEST CHAMPIONSHIP",
                duration = "12:45",
                videoUrl = hls2,
                sport = SportType.CRICKET
            ),
            HighlightItem(
                title = "Argentina vs Brazil - Full Goals & Extended Highlights (2-1)",
                tournament = "INTL FOOTBALL • FIFA WORLD CUP QUALIFIERS",
                duration = "10:15",
                videoUrl = hls1,
                sport = SportType.FOOTBALL
            ),
            HighlightItem(
                title = "India vs New Zealand - Session 3 Boundaries & Wickets",
                tournament = "INTL CRICKET • ICC TEST SERIES",
                duration = "09:30",
                videoUrl = hls3,
                sport = SportType.CRICKET
            ),
            HighlightItem(
                title = "Spain vs France - UEFA Nations League Classic Recap",
                tournament = "INTL FOOTBALL • UEFA NATIONS LEAGUE A",
                duration = "08:20",
                videoUrl = hls2,
                sport = SportType.FOOTBALL
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
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36 FootballLiveTV/7.3")
                .header("Accept", "*/*")
                .build()

            val response = httpClient.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val statusCode = response.code
            val contentType = response.header("Content-Type") ?: "unknown"
            val isSuccess = response.isSuccessful

            // Check if M3U8 manifest
            var isHls = false
            if (contentType.contains("mpegurl", ignoreCase = true) || url.contains(".m3u8", ignoreCase = true)) {
                isHls = true
            }

            // Quick peek at the first 512 bytes for #EXTM3U
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

    // Serialization Helpers
    private fun serializeMatches(matches: List<LiveMatch>): String {
        val array = JSONArray()
        for (m in matches) {
            val obj = JSONObject().apply {
                put("id", m.id)
                put("tournament", m.tournament)
                put("sport", m.sport.name)
                put("team1Name", m.team1Name)
                put("team1Code", m.team1Code)
                put("team1FlagUrl", m.team1FlagUrl)
                put("team2Name", m.team2Name)
                put("team2Code", m.team2Code)
                put("team2FlagUrl", m.team2FlagUrl)
                put("status", m.status.name)
                put("timeOrScore", m.timeOrScore)
                put("matchTime", m.matchTime)
                put("isFavorite", m.isFavorite)

                val streamArr = JSONArray()
                for (s in m.streams) {
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

    private fun deserializeMatches(json: String): List<LiveMatch> {
        val list = mutableListOf<LiveMatch>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val streamList = mutableListOf<StreamSource>()

            // 1. Array of streams
            val streamArr = obj.optJSONArray("streams") ?: obj.optJSONArray("servers") ?: obj.optJSONArray("sources")
            if (streamArr != null) {
                for (j in 0 until streamArr.length()) {
                    val sObj = streamArr.getJSONObject(j)
                    val sUrl = sObj.optString("url").ifBlank { sObj.optString("link").ifBlank { sObj.optString("stream") } }
                    if (sUrl.isNotBlank()) {
                        streamList.add(
                            StreamSource(
                                id = sObj.optString("id").ifBlank { UUID.randomUUID().toString() },
                                name = sObj.optString("name").ifBlank { sObj.optString("server", "Server ${j + 1}") },
                                quality = sObj.optString("quality", "1080p HD"),
                                url = sUrl,
                                userAgent = sObj.optString("userAgent").ifBlank { null },
                                referer = sObj.optString("referer").ifBlank { null },
                                isDefault = sObj.optBoolean("isDefault", j == 0)
                            )
                        )
                    }
                }
            }

            // 2. Direct single stream or multi-server shortcuts
            val directUrl1 = obj.optString("url").ifBlank { obj.optString("stream").ifBlank { obj.optString("stream_url").ifBlank { obj.optString("link") } } }
            if (directUrl1.isNotBlank() && streamList.none { it.url == directUrl1 }) {
                streamList.add(
                    StreamSource(
                        id = UUID.randomUUID().toString(),
                        name = "Server 1 HD",
                        quality = "1080p HD",
                        url = directUrl1,
                        isDefault = true
                    )
                )
            }
            val directUrl2 = obj.optString("url2").ifBlank { obj.optString("stream2").ifBlank { obj.optString("server2") } }
            if (directUrl2.isNotBlank() && streamList.none { it.url == directUrl2 }) {
                streamList.add(
                    StreamSource(
                        id = UUID.randomUUID().toString(),
                        name = "Server 2 (Backup)",
                        quality = "720p HD",
                        url = directUrl2,
                        isDefault = false
                    )
                )
            }
            val directUrl3 = obj.optString("url3").ifBlank { obj.optString("stream3").ifBlank { obj.optString("server3") } }
            if (directUrl3.isNotBlank() && streamList.none { it.url == directUrl3 }) {
                streamList.add(
                    StreamSource(
                        id = UUID.randomUUID().toString(),
                        name = "Server 3 (Fast)",
                        quality = "HD",
                        url = directUrl3,
                        isDefault = false
                    )
                )
            }

            val sportStr = obj.optString("sport").ifBlank { obj.optString("category", "FOOTBALL") }
            val sportType = try {
                SportType.valueOf(sportStr.trim().uppercase())
            } catch (_: Exception) {
                if (sportStr.contains("cric", ignoreCase = true)) SportType.CRICKET else SportType.FOOTBALL
            }

            val statusStr = obj.optString("status", "LIVE")
            val status = when (statusStr.trim().uppercase()) {
                "LIVE" -> MatchStatus.LIVE
                "UPCOMING" -> MatchStatus.UPCOMING
                "FINISHED", "ENDED" -> MatchStatus.FINISHED
                else -> MatchStatus.LIVE
            }

            val t1 = obj.optString("team1Name").ifBlank {
                obj.optString("homeTeam").ifBlank {
                    obj.optString("team1").ifBlank {
                        obj.optString("home", "Team 1")
                    }
                }
            }
            val t2 = obj.optString("team2Name").ifBlank {
                obj.optString("awayTeam").ifBlank {
                    obj.optString("team2").ifBlank {
                        obj.optString("away", "Team 2")
                    }
                }
            }

            val timeScore = obj.optString("timeOrScore").ifBlank {
                obj.optString("score").ifBlank {
                    obj.optString("time", if (status == MatchStatus.LIVE) "LIVE" else "19:00")
                }
            }

            val mTime = obj.optString("matchTime").ifBlank {
                obj.optString("date", "TODAY")
            }

            val rawCode1 = obj.optString("team1Code").ifBlank { t1.take(3).uppercase() }
            val rawFlag1 = obj.optString("team1FlagUrl").ifBlank { obj.optString("team1Flag").ifBlank { obj.optString("homeFlag", "") } }
            val visual1 = TeamFlagResolver.resolve(t1, rawCode1, rawFlag1)

            val rawCode2 = obj.optString("team2Code").ifBlank { t2.take(3).uppercase() }
            val rawFlag2 = obj.optString("team2FlagUrl").ifBlank { obj.optString("team2Flag").ifBlank { obj.optString("awayFlag", "") } }
            val visual2 = TeamFlagResolver.resolve(t2, rawCode2, rawFlag2)

            list.add(
                LiveMatch(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    tournament = obj.optString("tournament").ifBlank { obj.optString("league", "LIVE TOURNAMENT") },
                    sport = sportType,
                    team1Name = t1,
                    team1Code = visual1.code,
                    team1FlagUrl = visual1.flagUrl.ifBlank { visual1.emoji },
                    team2Name = t2,
                    team2Code = visual2.code,
                    team2FlagUrl = visual2.flagUrl.ifBlank { visual2.emoji },
                    status = status,
                    timeOrScore = timeScore,
                    matchTime = mTime,
                    streams = streamList,
                    isFavorite = obj.optBoolean("isFavorite", false)
                )
            )
        }
        return list
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

    // Strictly Senior National Main Team International Matches (Cricket & Football) with HD Country Flags & Exact Schedules
    private fun defaultMatches(): List<LiveMatch> {
        val akamaiHls1 = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"
        val akamaiHls2 = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        val akamaiHls3 = "https://diceyk6a7voy4.cloudfront.net/e78752a1-2e83-43fa-85ae-3d508be29366/hls/de:02b6989d-e714-4227-9c57-a4c1a221a244:playlist.m3u8"
        val bigBuckBunny = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        val sintelHls = "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8"

        val dayFmt = SimpleDateFormat("dd MMM", Locale.US)
        val cal = Calendar.getInstance()
        val todayStr = dayFmt.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrowStr = dayFmt.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val nextDayStr = dayFmt.format(cal.time)

        return listOf(
            // ================= SENIOR INTERNATIONAL CRICKET (LIVE) =================
            LiveMatch(
                id = "intl-cric-1",
                tournament = "INTL CRICKET • ICC WORLD TEST CHAMPIONSHIP",
                sport = SportType.CRICKET,
                team1Name = "Bangladesh",
                team1Code = "BAN",
                team1FlagUrl = "https://flagcdn.com/w160/bd.png",
                team2Name = "South Africa",
                team2Code = "RSA",
                team2FlagUrl = "https://flagcdn.com/w160/za.png",
                status = MatchStatus.LIVE,
                timeOrScore = "BAN 242/6 (68.4 ov)",
                matchTime = "Today ($todayStr) • Mirpur Test Live",
                streams = listOf(
                    StreamSource(name = "T SPORTS FHD (BD)", quality = "FHD", url = akamaiHls1, isDefault = true),
                    StreamSource(name = "GAZI TV (GTV HD)", quality = "FHD", url = akamaiHls2),
                    StreamSource(name = "SUPERSPORT CRICKET", quality = "FHD", url = akamaiHls3),
                    StreamSource(name = "WILLOW CRICKET HD", quality = "HD", url = sintelHls)
                ),
                isFavorite = true
            ),
            LiveMatch(
                id = "intl-cric-2",
                tournament = "INTL CRICKET • ICC WORLD TEST CHAMPIONSHIP",
                sport = SportType.CRICKET,
                team1Name = "India",
                team1Code = "IND",
                team1FlagUrl = "https://flagcdn.com/w160/in.png",
                team2Name = "New Zealand",
                team2Code = "NZ",
                team2FlagUrl = "https://flagcdn.com/w160/nz.png",
                status = MatchStatus.LIVE,
                timeOrScore = "IND 318/4 (64.2 ov)",
                matchTime = "Today ($todayStr) • Session 3 Live",
                streams = listOf(
                    StreamSource(name = "STAR SPORTS 1 HD", quality = "FHD", url = akamaiHls2, isDefault = true),
                    StreamSource(name = "JIO CINEMA / SPORTS18 FHD", quality = "FHD", url = akamaiHls1),
                    StreamSource(name = "SKY SPORT NZ 1", quality = "HD", url = akamaiHls3)
                ),
                isFavorite = true
            ),
            LiveMatch(
                id = "intl-cric-3",
                tournament = "INTL CRICKET • ICC WORLD TEST CHAMPIONSHIP",
                sport = SportType.CRICKET,
                team1Name = "Pakistan",
                team1Code = "PAK",
                team1FlagUrl = "https://flagcdn.com/w160/pk.png",
                team2Name = "England",
                team2Code = "ENG",
                team2FlagUrl = "https://flagcdn.com/w160/gb-eng.png",
                status = MatchStatus.LIVE,
                timeOrScore = "PAK 295/5 (76.0 ov)",
                matchTime = "Today ($todayStr) • Rawalpindi Test Live",
                streams = listOf(
                    StreamSource(name = "PTV SPORTS HD", quality = "FHD", url = akamaiHls1, isDefault = true),
                    StreamSource(name = "A SPORTS FHD", quality = "FHD", url = akamaiHls3),
                    StreamSource(name = "SKY SPORTS CRICKET", quality = "FHD", url = akamaiHls2),
                    StreamSource(name = "TAPMAD FHD", quality = "HD", url = sintelHls)
                ),
                isFavorite = true
            ),

            // ================= SENIOR INTERNATIONAL FOOTBALL (LIVE) =================
            LiveMatch(
                id = "intl-foot-1",
                tournament = "INTL FOOTBALL • FIFA WORLD CUP QUALIFIERS",
                sport = SportType.FOOTBALL,
                team1Name = "Argentina",
                team1Code = "ARG",
                team1FlagUrl = "https://flagcdn.com/w160/ar.png",
                team2Name = "Brazil",
                team2Code = "BRA",
                team2FlagUrl = "https://flagcdn.com/w160/br.png",
                status = MatchStatus.LIVE,
                timeOrScore = "2 - 1",
                matchTime = "Today ($todayStr) • 2nd Half 74'",
                streams = listOf(
                    StreamSource(name = "TYC SPORTS INTL FHD", quality = "FHD", url = akamaiHls1, isDefault = true),
                    StreamSource(name = "BEIN SPORTS 1 PREMIUM", quality = "FHD", url = akamaiHls3),
                    StreamSource(name = "SPORTV BRASIL HD", quality = "HD", url = akamaiHls2)
                ),
                isFavorite = true
            ),
            LiveMatch(
                id = "intl-foot-2",
                tournament = "INTL FOOTBALL • UEFA NATIONS LEAGUE A",
                sport = SportType.FOOTBALL,
                team1Name = "Spain",
                team1Code = "ESP",
                team1FlagUrl = "https://flagcdn.com/w160/es.png",
                team2Name = "France",
                team2Code = "FRA",
                team2FlagUrl = "https://flagcdn.com/w160/fr.png",
                status = MatchStatus.LIVE,
                timeOrScore = "1 - 1",
                matchTime = "Today ($todayStr) • 2nd Half 63'",
                streams = listOf(
                    StreamSource(name = "SONY TEN 2 FHD", quality = "FHD", url = akamaiHls2, isDefault = true),
                    StreamSource(name = "TF1 FRANCE FHD", quality = "FHD", url = akamaiHls1),
                    StreamSource(name = "UEFA TV STREAM", quality = "HD", url = sintelHls)
                ),
                isFavorite = true
            ),

            // ================= SENIOR INTERNATIONAL FOOTBALL & CRICKET (UPCOMING) =================
            LiveMatch(
                id = "intl-foot-3",
                tournament = "INTL FOOTBALL • UEFA NATIONS LEAGUE A",
                sport = SportType.FOOTBALL,
                team1Name = "Portugal",
                team1Code = "POR",
                team1FlagUrl = "https://flagcdn.com/w160/pt.png",
                team2Name = "Germany",
                team2Code = "GER",
                team2FlagUrl = "https://flagcdn.com/w160/de.png",
                status = MatchStatus.UPCOMING,
                timeOrScore = "Starts in 3h 15m",
                matchTime = "Tonight ($todayStr), 12:45 AM",
                streams = listOf(
                    StreamSource(name = "RTP 1 PORTUGAL FHD", quality = "FHD", url = akamaiHls1, isDefault = true),
                    StreamSource(name = "SONY TEN 1 FHD", quality = "FHD", url = akamaiHls2),
                    StreamSource(name = "ZDF GERMANY HD", quality = "HD", url = akamaiHls3)
                ),
                isFavorite = true
            ),
            LiveMatch(
                id = "intl-foot-4",
                tournament = "INTL FOOTBALL • UEFA NATIONS LEAGUE A",
                sport = SportType.FOOTBALL,
                team1Name = "England",
                team1Code = "ENG",
                team1FlagUrl = "https://flagcdn.com/w160/gb-eng.png",
                team2Name = "Italy",
                team2Code = "ITA",
                team2FlagUrl = "https://flagcdn.com/w160/it.png",
                status = MatchStatus.UPCOMING,
                timeOrScore = "Starts in 3h 30m",
                matchTime = "Tonight ($todayStr), 01:00 AM",
                streams = listOf(
                    StreamSource(name = "ITV 1 / TNT SPORTS FHD", quality = "FHD", url = akamaiHls1, isDefault = true),
                    StreamSource(name = "SONY TEN 3 HD", quality = "FHD", url = akamaiHls3),
                    StreamSource(name = "RAI 1 ITALIA HD", quality = "HD", url = akamaiHls2)
                )
            ),
            LiveMatch(
                id = "intl-cric-4",
                tournament = "INTL CRICKET • ODI INTERNATIONAL SERIES",
                sport = SportType.CRICKET,
                team1Name = "Australia",
                team1Code = "AUS",
                team1FlagUrl = "https://flagcdn.com/w160/au.png",
                team2Name = "Pakistan",
                team2Code = "PAK",
                team2FlagUrl = "https://flagcdn.com/w160/pk.png",
                status = MatchStatus.UPCOMING,
                timeOrScore = "1st ODI • Melbourne",
                matchTime = "Tomorrow ($tomorrowStr), 09:30 AM",
                streams = listOf(
                    StreamSource(name = "FOX CRICKET 501 FHD", quality = "FHD", url = akamaiHls2, isDefault = true),
                    StreamSource(name = "STAR SPORTS 1 HD", quality = "FHD", url = akamaiHls1),
                    StreamSource(name = "PTV SPORTS HD", quality = "HD", url = akamaiHls3)
                )
            ),
            LiveMatch(
                id = "intl-cric-5",
                tournament = "INTL CRICKET • ODI INTERNATIONAL SERIES",
                sport = SportType.CRICKET,
                team1Name = "Afghanistan",
                team1Code = "AFG",
                team1FlagUrl = "https://flagcdn.com/w160/af.png",
                team2Name = "Bangladesh",
                team2Code = "BAN",
                team2FlagUrl = "https://flagcdn.com/w160/bd.png",
                status = MatchStatus.UPCOMING,
                timeOrScore = "1st ODI • Sharjah",
                matchTime = "Tomorrow ($tomorrowStr), 04:00 PM",
                streams = listOf(
                    StreamSource(name = "T SPORTS LIVE FHD", quality = "FHD", url = akamaiHls1, isDefault = true),
                    StreamSource(name = "NAGORIK TV HD", quality = "HD", url = akamaiHls2),
                    StreamSource(name = "EUROSPORT INDIA FHD", quality = "FHD", url = akamaiHls3)
                ),
                isFavorite = true
            ),
            LiveMatch(
                id = "intl-foot-5",
                tournament = "INTL FOOTBALL • AFC ASIAN CUP QUALIFIERS",
                sport = SportType.FOOTBALL,
                team1Name = "Bangladesh",
                team1Code = "BAN",
                team1FlagUrl = "https://flagcdn.com/w160/bd.png",
                team2Name = "India",
                team2Code = "IND",
                team2FlagUrl = "https://flagcdn.com/w160/in.png",
                status = MatchStatus.UPCOMING,
                timeOrScore = "Starts at 07:30 PM",
                matchTime = "Tomorrow ($tomorrowStr), 07:30 PM",
                streams = listOf(
                    StreamSource(name = "T SPORTS BD FHD", quality = "FHD", url = akamaiHls1, isDefault = true),
                    StreamSource(name = "STAR SPORTS 3 HD", quality = "FHD", url = akamaiHls2)
                ),
                isFavorite = true
            ),
            LiveMatch(
                id = "intl-cric-6",
                tournament = "INTL CRICKET • ODI INTERNATIONAL SERIES",
                sport = SportType.CRICKET,
                team1Name = "Sri Lanka",
                team1Code = "SL",
                team1FlagUrl = "https://flagcdn.com/w160/lk.png",
                team2Name = "West Indies",
                team2Code = "WI",
                team2FlagUrl = "https://flagcdn.com/w160/jm.png",
                status = MatchStatus.UPCOMING,
                timeOrScore = "2nd ODI • Pallekele",
                matchTime = "Tomorrow ($tomorrowStr), 03:00 PM",
                streams = listOf(
                    StreamSource(name = "SONY TEN 5 FHD", quality = "FHD", url = akamaiHls1, isDefault = true),
                    StreamSource(name = "TNT SPORTS 3 HD", quality = "HD", url = akamaiHls3)
                )
            ),
            LiveMatch(
                id = "intl-foot-6",
                tournament = "INTL FOOTBALL • FIFA WORLD CUP QUALIFIERS",
                sport = SportType.FOOTBALL,
                team1Name = "Uruguay",
                team1Code = "URU",
                team1FlagUrl = "https://flagcdn.com/w160/uy.png",
                team2Name = "Colombia",
                team2Code = "COL",
                team2FlagUrl = "https://flagcdn.com/w160/co.png",
                status = MatchStatus.UPCOMING,
                timeOrScore = "CONMEBOL Qualifier",
                matchTime = "$nextDayStr, 06:00 AM",
                streams = listOf(
                    StreamSource(name = "BEIN SPORTS 1 FHD", quality = "FHD", url = akamaiHls1, isDefault = true),
                    StreamSource(name = "TYC SPORTS INTL", quality = "HD", url = akamaiHls2)
                )
            ),

            // ================= SENIOR INTERNATIONAL (FINISHED) =================
            LiveMatch(
                id = "intl-foot-7",
                tournament = "INTL FOOTBALL • UEFA NATIONS LEAGUE A",
                sport = SportType.FOOTBALL,
                team1Name = "Netherlands",
                team1Code = "NED",
                team1FlagUrl = "https://flagcdn.com/w160/nl.png",
                team2Name = "Belgium",
                team2Code = "BEL",
                team2FlagUrl = "https://flagcdn.com/w160/be.png",
                status = MatchStatus.FINISHED,
                timeOrScore = "FT (2 - 1)",
                matchTime = "Finished ($todayStr)",
                streams = listOf(
                    StreamSource(name = "FULL MATCH HIGHLIGHTS FHD", quality = "FHD", url = bigBuckBunny, isDefault = true)
                )
            ),
            LiveMatch(
                id = "intl-cric-7",
                tournament = "INTL CRICKET • T20I INTERNATIONAL SERIES",
                sport = SportType.CRICKET,
                team1Name = "Sri Lanka",
                team1Code = "SL",
                team1FlagUrl = "https://flagcdn.com/w160/lk.png",
                team2Name = "West Indies",
                team2Code = "WI",
                team2FlagUrl = "https://flagcdn.com/w160/jm.png",
                status = MatchStatus.FINISHED,
                timeOrScore = "SL won by 73 runs",
                matchTime = "Finished ($todayStr)",
                streams = listOf(
                    StreamSource(name = "MATCH HIGHLIGHTS FHD", quality = "FHD", url = bigBuckBunny, isDefault = true)
                )
            )
        )
    }

    private fun defaultChannels(): List<SportsChannel> {
        val streamHls1 = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"
        val streamHls2 = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        val streamHls3 = "https://diceyk6a7voy4.cloudfront.net/e78752a1-2e83-43fa-85ae-3d508be29366/hls/de:02b6989d-e714-4227-9c57-a4c1a221a244:playlist.m3u8"

        return listOf(
            SportsChannel(
                id = "ch-1",
                name = "TNT Sports",
                category = "Sports",
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
                category = "Sports",
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
                name = "TUDN",
                category = "Sports",
                logoText = "TUDN",
                streams = listOf(StreamSource(name = "TUDN LIVE", quality = "FHD", url = streamHls2, isDefault = true))
            ),
            SportsChannel(
                id = "ch-6",
                name = "D Sports",
                category = "Sports",
                logoText = "DSports",
                streams = listOf(StreamSource(name = "D SPORTS HD", quality = "HD", url = streamHls3, isDefault = true))
            ),
            SportsChannel(
                id = "ch-7",
                name = "Premier Sports",
                category = "Sports",
                logoText = "PREMIER",
                streams = listOf(StreamSource(name = "PREMIER 1 FHD", quality = "FHD", url = streamHls1, isDefault = true))
            ),
            SportsChannel(
                id = "ch-8",
                name = "Premiere",
                category = "Sports",
                logoText = "PREMIERE",
                streams = listOf(StreamSource(name = "PREMIERE HD", quality = "HD", url = streamHls2, isDefault = true))
            ),
            SportsChannel(
                id = "ch-9",
                name = "Nova Sports",
                category = "Sports",
                logoText = "NOVA",
                streams = listOf(StreamSource(name = "NOVA SPORTS 1", quality = "FHD", url = streamHls1, isDefault = true))
            ),
            SportsChannel(
                id = "ch-10",
                name = "SPOTV",
                category = "Sports",
                logoText = "SPOTV",
                streams = listOf(
                    StreamSource(name = "SPOTV 1 HD", quality = "FHD", url = streamHls2, isDefault = true),
                    StreamSource(name = "SPOTV 2 HD", quality = "HD", url = streamHls3)
                )
            ),
            SportsChannel(
                id = "ch-11",
                name = "SporTV",
                category = "Sports",
                logoText = "sporTV",
                streams = listOf(StreamSource(name = "SporTV 1", quality = "FHD", url = streamHls1, isDefault = true))
            ),
            SportsChannel(
                id = "ch-12",
                name = "CT Sport",
                category = "Sports",
                logoText = "CT",
                streams = listOf(StreamSource(name = "CT SPORT LIVE", quality = "HD", url = streamHls3, isDefault = true))
            ),
            SportsChannel(
                id = "ch-13",
                name = "Fubo Sports",
                category = "Sports",
                logoText = "fubo",
                streams = listOf(StreamSource(name = "FUBO NETWORK", quality = "FHD", url = streamHls1, isDefault = true))
            ),
            SportsChannel(
                id = "ch-14",
                name = "EuroSport",
                category = "Sports",
                logoText = "EURO",
                streams = listOf(
                    StreamSource(name = "EUROSPORT 1", quality = "FHD", url = streamHls2, isDefault = true),
                    StreamSource(name = "EUROSPORT 2", quality = "HD", url = streamHls1)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-15",
                name = "SportDigital",
                category = "Sports",
                logoText = "SD",
                streams = listOf(StreamSource(name = "SPORTDIGITAL HD", quality = "HD", url = streamHls3, isDefault = true))
            ),
            SportsChannel(
                id = "ch-16",
                name = "CBS Golazo",
                category = "Sports",
                logoText = "GOLAZO",
                streams = listOf(StreamSource(name = "CBS GOLAZO 24/7", quality = "FHD", url = streamHls1, isDefault = true))
            ),
            SportsChannel(
                id = "ch-17",
                name = "DAZN",
                category = "Sports",
                logoText = "DAZN",
                streams = listOf(
                    StreamSource(name = "DAZN 1 FHD", quality = "FHD", url = streamHls3, isDefault = true),
                    StreamSource(name = "DAZN 2 HD", quality = "HD", url = streamHls2)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-18",
                name = "Star Sports",
                category = "Sports",
                logoText = "STAR",
                streams = listOf(
                    StreamSource(name = "STAR SPORTS 1 HD", quality = "FHD", url = streamHls1, isDefault = true),
                    StreamSource(name = "STAR SPORTS HINDI", quality = "HD", url = streamHls2)
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
                id = "ch-22",
                name = "TEN Sports",
                category = "Sports",
                logoText = "TEN",
                streams = listOf(StreamSource(name = "TEN SPORTS HD", quality = "FHD", url = streamHls2, isDefault = true))
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
                category = "Sports",
                logoText = "WILLOW",
                streams = listOf(
                    StreamSource(name = "WILLOW HD 1", quality = "FHD", url = streamHls1, isDefault = true),
                    StreamSource(name = "WILLOW HD 2", quality = "HD", url = streamHls2)
                ),
                isFavorite = true
            )
        )
    }

    private fun defaultCategories(): List<CategoryItem> {
        return listOf(
            CategoryItem(name = "SPORTS", channelCount = 24),
            CategoryItem(name = "LIVE EVENTS HD", channelCount = 18),
            CategoryItem(name = "LIVE EVENTS SD", channelCount = 14),
            CategoryItem(name = "FANCODE IND", channelCount = 10),
            CategoryItem(name = "FANCODE 2 IND", channelCount = 8),
            CategoryItem(name = "FANCODE BD", channelCount = 6),
            CategoryItem(name = "FANCODE NP", channelCount = 4),
            CategoryItem(name = "FANCODE SL", channelCount = 4),
            CategoryItem(name = "SONYLIV LIVE", channelCount = 8),
            CategoryItem(name = "SONYLIV LIVE 2", channelCount = 6),
            CategoryItem(name = "SONYLIV LIVE 3", channelCount = 6),
            CategoryItem(name = "ICC TV", channelCount = 5),
            CategoryItem(name = "CRICHD", channelCount = 12),
            CategoryItem(name = "PLUTO TV", channelCount = 20),
            CategoryItem(name = "TUBI TV", channelCount = 15),
            CategoryItem(name = "ROKU", channelCount = 16),
            CategoryItem(name = "FREE TV", channelCount = 25),
            CategoryItem(name = "FIRE TV", channelCount = 18),
            CategoryItem(name = "PAKISTAN", channelCount = 12),
            CategoryItem(name = "TAPMAD PK", channelCount = 8),
            CategoryItem(name = "NEWS", channelCount = 15),
            CategoryItem(name = "ISLAMIC", channelCount = 10)
        )
    }
}
