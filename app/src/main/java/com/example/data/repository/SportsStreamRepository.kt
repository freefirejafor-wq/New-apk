package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.cloud.CloudCatalogItem
import com.example.data.cloud.CloudSyncRepository
import com.example.data.crex.CrexCategoryFeedState
import com.example.data.crex.CrexCricketService
import com.example.data.crex.CrexDashboardState
import com.example.data.crex.CrexMatchDetailState
import com.example.data.espn.EspnFootballService
import com.example.data.model.*
import com.example.data.playlist.LivePlaylistMatcherService
import com.example.data.sofascore.SofaScoreMetadataService
import com.example.data.util.TeamFlagResolver
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class SportsStreamRepository(
    private val context: Context,
    val crexService: CrexCricketService = CrexCricketService(),
    val espnService: EspnFootballService = EspnFootballService(),
    val sofascoreService: SofaScoreMetadataService = SofaScoreMetadataService(),
    val playlistMatcherService: LivePlaylistMatcherService = LivePlaylistMatcherService(),
    val cloudSyncRepository: CloudSyncRepository? = runCatching { CloudSyncRepository(context) }.getOrNull()
) {
    private fun isFirebaseSignedIn(): Boolean =
        runCatching { Firebase.auth.currentUser != null }.getOrDefault(false)

    companion object {
        // Legacy demo fixture IDs that must never be rendered as standalone fixtures
        private val LEGACY_DEMO_IDS = setOf(
            "cric-t20-nz-ind",
            "cric-wtc-sa-aus",
            "cric-odi-sl-eng",
            "cric-wt20-ausw-nzw",
            "fb-spl-hazem-fayha",
            "mgp-motogp-1",
            "cric-odi-pak-nz",
            "cric-t20-ban-wi",
            "cric-odi-afg-zim",
            "cric-t20-ire-sco",
            "box-ufc-1",
            "box-ufc-2",
            "box-ufc-3",
            "box-ufc-4",
            "box-ufc-5",
            "mgp-f1-2"
        )
    }

    private data class FavoriteMatchMeta(
        val matchId: String,
        val sport: SportType,
        val team1Name: String,
        val team2Name: String,
        val tournament: String
    )

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var cloudCatalogJob: Job? = null
    private var cloudFavoritesJob: Job? = null

    private val prefs: SharedPreferences =
        context.getSharedPreferences("football_live_tv_prefs", Context.MODE_PRIVATE)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val favoriteMatchIds = mutableSetOf<String>()
    private val favoriteMatchMetaMap = ConcurrentHashMap<String, FavoriteMatchMeta>()
    private val favoriteChannelIds = mutableSetOf<String>()

    // Live provider matches from ESPN (Football)
    private val footballProviderMatches = mutableListOf<LiveMatch>()

    // Admin overrides / stream associations keyed by match ID
    private val cloudMatchOverrides = ConcurrentHashMap<String, LiveMatch>()
    private val deletedMatchIds = mutableSetOf<String>()
    private val dismissedNoticeIds = mutableSetOf<String>()

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

    private val _cloudSyncStatus = MutableStateFlow("Connected to Real-Time Cloud Server")
    val cloudSyncStatus: StateFlow<String> = _cloudSyncStatus.asStateFlow()

    private val _providerDiagnostics = MutableStateFlow<List<ProviderSyncDiagnostics>>(emptyList())
    val providerDiagnostics: StateFlow<List<ProviderSyncDiagnostics>> = _providerDiagnostics.asStateFlow()

    private val _unmappedNotices = MutableStateFlow<List<UnmappedAssociationNotice>>(emptyList())
    val unmappedNotices: StateFlow<List<UnmappedAssociationNotice>> = _unmappedNotices.asStateFlow()

    init {
        loadData()
        refreshDiagnosticsSnapshot()
    }

    private fun refreshDiagnosticsSnapshot() {
        val cfg = _config.value
        _providerDiagnostics.value = listOf(
            espnService.getDiagnostics(cfg.footballSyncEnabled),
            crexService.getDiagnostics(cfg.cricketSyncEnabled),
            playlistMatcherService.getDiagnostics()
        )
    }

    /**
     * Starts real-time Firestore snapshot listeners for public Admin catalog items
     * (`cloud_items`) and per-user favorites (`users/{uid}/favorites`) once authenticated.
     */
    fun startCloudSyncForUser(userId: String) {
        val syncRepo = cloudSyncRepository ?: return
        cloudCatalogJob?.cancel()
        cloudFavoritesJob?.cancel()

        cloudCatalogJob = repoScope.launch {
            try {
                syncRepo.observePublicCatalogItems().collect { items ->
                    applyCloudCatalogItems(items)
                }
            } catch (e: Exception) {
                _cloudSyncStatus.value = "Cloud Sync: ${e.localizedMessage ?: "Offline"}"
            }
        }

        cloudFavoritesJob = repoScope.launch {
            try {
                syncRepo.observeUserFavorites(userId).collect { favs ->
                    val matchFavs = favs.filter { it.itemType == "match" }.map { it.itemId }.toSet()
                    val channelFavs = favs.filter { it.itemType == "channel" }.map { it.itemId }.toSet()
                    if (matchFavs.isNotEmpty() || channelFavs.isNotEmpty()) {
                        favoriteMatchIds.addAll(matchFavs)
                        for (fav in favs.filter { it.itemType == "match" }) {
                            val parts = fav.title.split(" vs ", ignoreCase = true)
                            if (parts.size == 2 && !favoriteMatchMetaMap.containsKey(fav.itemId)) {
                                favoriteMatchMetaMap[fav.itemId] = FavoriteMatchMeta(
                                    matchId = fav.itemId,
                                    sport = if (fav.itemId.startsWith("espn-")) SportType.FOOTBALL else SportType.CRICKET,
                                    team1Name = parts[0].trim(),
                                    team2Name = parts[1].trim(),
                                    tournament = ""
                                )
                            }
                        }
                        favoriteChannelIds.addAll(channelFavs)
                        _channels.value = _channels.value.map { ch ->
                            if (ch.id in favoriteChannelIds) ch.copy(isFavorite = true) else ch
                        }
                        rebuildCombinedMatches()
                    }
                }
            } catch (_: Exception) {
                // Handled by FirestoreErrorHandler
            }
        }
    }

    /**
     * Stops active Firestore snapshot listeners immediately on sign-out.
     */
    fun stopCloudSync() {
        cloudCatalogJob?.cancel()
        cloudFavoritesJob?.cancel()
        cloudCatalogJob = null
        cloudFavoritesJob = null
    }

    private fun applyCloudCatalogItems(items: List<CloudCatalogItem>) {
        deletedMatchIds.clear()
        if (items.isEmpty()) {
            rebuildCombinedMatches()
            return
        }
        var channelsChanged = false
        val updatedChannels = _channels.value.toMutableList()

        for (item in items) {
            when (item.itemType) {
                "config" -> {
                    try {
                        val obj = JSONObject(item.payloadJson)
                        val cur = _config.value
                        val leaguesArr = obj.optJSONArray("enabledFootballLeagues")
                        val parsedLeagues = if (leaguesArr != null && leaguesArr.length() > 0) {
                            buildSet {
                                for (i in 0 until leaguesArr.length()) {
                                    val slug = leaguesArr.optString(i).trim()
                                    if (slug.isNotBlank()) add(slug)
                                }
                            }
                        } else {
                            cur.enabledFootballLeagues
                        }
                        val customM3u = obj.optString("customM3uBlock", cur.customM3uBlock)
                        if (customM3u.isNotBlank()) {
                            playlistMatcherService.registerAdminM3uBlock(customM3u)
                        }
                        _config.value = cur.copy(
                            appTitle = obj.optString("appTitle", cur.appTitle).ifBlank { cur.appTitle },
                            version = obj.optString("version", cur.version).ifBlank { cur.version },
                            announcement = obj.optString("announcement", cur.announcement),
                            telegramLink = obj.optString("telegramLink", cur.telegramLink),
                            adminPin = obj.optString("adminPin", cur.adminPin).ifBlank { cur.adminPin },
                            remoteFeedUrl = obj.optString("remoteFeedUrl", cur.remoteFeedUrl).ifBlank { cur.remoteFeedUrl },
                            autoSyncOnLaunch = obj.optBoolean("autoSyncOnLaunch", cur.autoSyncOnLaunch),
                            footballSyncEnabled = obj.optBoolean("footballSyncEnabled", cur.footballSyncEnabled),
                            cricketSyncEnabled = obj.optBoolean("cricketSyncEnabled", cur.cricketSyncEnabled),
                            autoRefreshIntervalSec = obj.optInt("autoRefreshIntervalSec", cur.autoRefreshIntervalSec).coerceIn(15, 600),
                            enabledFootballLeagues = parsedLeagues,
                            customM3uBlock = customM3u
                        )
                    } catch (_: Exception) {
                    }
                }
                "channel" -> {
                    try {
                        val obj = JSONObject(item.payloadJson)
                        if (obj.optBoolean("deleted", false)) {
                            val delId = obj.optString("id")
                            updatedChannels.removeAll { it.id == delId }
                            channelsChanged = true
                        } else {
                            val ch = deserializeSingleChannel(obj)
                            val idx = updatedChannels.indexOfFirst { it.id == ch.id }
                            if (idx >= 0) {
                                updatedChannels[idx] = ch.copy(isFavorite = updatedChannels[idx].isFavorite || ch.id in favoriteChannelIds)
                            } else {
                                updatedChannels.add(0, ch.copy(isFavorite = ch.id in favoriteChannelIds))
                            }
                            channelsChanged = true
                        }
                    } catch (_: Exception) {
                    }
                }
                "match" -> {
                    try {
                        val obj = JSONObject(item.payloadJson)
                        if (obj.optBoolean("deleted", false)) {
                            val delId = obj.optString("id")
                            if (delId.isNotBlank()) deletedMatchIds.add(delId)
                        } else {
                            val m = deserializeSingleMatch(obj)
                            if (m.id !in LEGACY_DEMO_IDS) {
                                cloudMatchOverrides[m.id] = m
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }

        if (channelsChanged) {
            _channels.value = updatedChannels
        }
        rebuildCombinedMatches()
        _cloudSyncStatus.value = "Live Cloud Synced (${items.size} server items active)"
    }

    private fun loadData() {
        // Remove any stale legacy demo match IDs from favourites while preserving real user favourites
        val savedFavs = prefs.getStringSet("favorite_match_ids", emptySet()).orEmpty()
            .filter { it !in LEGACY_DEMO_IDS }
        favoriteMatchIds.clear()
        favoriteMatchIds.addAll(savedFavs)

        val savedFavMetaJson = prefs.getString("favorite_match_meta_v2", null)
        if (!savedFavMetaJson.isNullOrBlank()) {
            try {
                val arr = JSONArray(savedFavMetaJson)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id").trim()
                    if (id.isNotBlank() && id !in LEGACY_DEMO_IDS) {
                        val sport = runCatching { SportType.valueOf(o.optString("sport", "FOOTBALL")) }
                            .getOrDefault(SportType.FOOTBALL)
                        favoriteMatchMetaMap[id] = FavoriteMatchMeta(
                            matchId = id,
                            sport = sport,
                            team1Name = o.optString("team1Name"),
                            team2Name = o.optString("team2Name"),
                            tournament = o.optString("tournament")
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }

        val savedOverridesJson = prefs.getString("local_match_overrides_v2", null)
        if (!savedOverridesJson.isNullOrBlank()) {
            try {
                val arr = JSONArray(savedOverridesJson)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val m = deserializeSingleMatch(o)
                    if (m.id.isNotBlank() && m.id !in LEGACY_DEMO_IDS) {
                        cloudMatchOverrides[m.id] = m
                    }
                }
            } catch (_: Exception) {
            }
        }

        val savedConfigJson = prefs.getString("app_config", null)
        if (savedConfigJson != null) {
            try {
                val obj = JSONObject(savedConfigJson)
                val rawTitle = obj.optString("appTitle", "Sports Live")
                val resolvedTitle = if (
                    rawTitle.equals("Football Live Tv", ignoreCase = true) ||
                    rawTitle.equals("SportzX Live TV", ignoreCase = true) ||
                    rawTitle.equals("NovaStream TV", ignoreCase = true)
                ) "Sports Live" else rawTitle
                val rawAnnounce = obj.optString(
                    "announcement",
                    "Welcome to Sports Live! Enjoy real-time match updates & multi-server HD streams."
                )
                val resolvedAnnounce = rawAnnounce
                    .replace("Football Live TV", "Sports Live", ignoreCase = true)
                    .replace("SportzX Live TV", "Sports Live", ignoreCase = true)
                    .replace("NovaStream TV", "Sports Live", ignoreCase = true)

                val leaguesArr = obj.optJSONArray("enabledFootballLeagues")
                val enabledLeagues = if (leaguesArr != null && leaguesArr.length() > 0) {
                    buildSet {
                        for (i in 0 until leaguesArr.length()) {
                            val s = leaguesArr.optString(i).trim()
                            if (s.isNotBlank()) add(s)
                        }
                        add("esp.1")
                        add("esp.2")
                        add("arg.1")
                        add("mex.1")
                        add("por.1")
                        add("ned.1")
                    }
                } else {
                    AppConfig.DEFAULT_ENABLED_FOOTBALL_LEAGUES
                }

                val savedCustomM3u = obj.optString("customM3uBlock", "")
                if (savedCustomM3u.isNotBlank()) {
                    playlistMatcherService.registerAdminM3uBlock(savedCustomM3u)
                }

                _config.value = AppConfig(
                    appTitle = resolvedTitle,
                    version = obj.optString("version", "9.0"),
                    announcement = resolvedAnnounce,
                    telegramLink = obj.optString("telegramLink", "https://t.me/sports_live_streams"),
                    adminPin = obj.optString("adminPin", "1234"),
                    forceLowQuality = obj.optBoolean("forceLowQuality", false),
                    isHardwareAccelerationEnabled = obj.optBoolean("isHardwareAccelerationEnabled", true),
                    remoteFeedUrl = obj.optString("remoteFeedUrl", "https://api.goscorer.com/api/v3/getLiveMatches"),
                    autoSyncOnLaunch = obj.optBoolean("autoSyncOnLaunch", true),
                    footballSyncEnabled = obj.optBoolean("footballSyncEnabled", true),
                    cricketSyncEnabled = obj.optBoolean("cricketSyncEnabled", true),
                    autoRefreshIntervalSec = obj.optInt("autoRefreshIntervalSec", 25).coerceIn(15, 600),
                    enabledFootballLeagues = enabledLeagues,
                    customM3uBlock = savedCustomM3u
                )
            } catch (e: Exception) {
                _config.value = AppConfig()
            }
        }

        // Start with zero hardcoded demo matches; live provider feeds populate footballProviderMatches & _crexDashboardState
        footballProviderMatches.clear()
        rebuildCombinedMatches()

        val savedChannelsJson = prefs.getString("saved_channels_v5", null)
        if (savedChannelsJson != null) {
            try {
                val list = deserializeChannels(savedChannelsJson)
                if (list.isNotEmpty()) {
                    _channels.value = list.map { ch ->
                        if (ch.id in favoriteChannelIds) ch.copy(isFavorite = true) else ch
                    }
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
            val cfg = _config.value
            val leaguesArr = JSONArray().apply {
                cfg.enabledFootballLeagues.forEach { put(it) }
            }
            val configObj = JSONObject().apply {
                put("appTitle", cfg.appTitle)
                put("version", cfg.version)
                put("announcement", cfg.announcement)
                put("telegramLink", cfg.telegramLink)
                put("adminPin", cfg.adminPin)
                put("forceLowQuality", cfg.forceLowQuality)
                put("isHardwareAccelerationEnabled", cfg.isHardwareAccelerationEnabled)
                put("remoteFeedUrl", cfg.remoteFeedUrl)
                put("autoSyncOnLaunch", cfg.autoSyncOnLaunch)
                put("footballSyncEnabled", cfg.footballSyncEnabled)
                put("cricketSyncEnabled", cfg.cricketSyncEnabled)
                put("autoRefreshIntervalSec", cfg.autoRefreshIntervalSec)
                put("enabledFootballLeagues", leaguesArr)
                put("customM3uBlock", cfg.customM3uBlock)
            }

            val favMetaArr = JSONArray()
            for (meta in favoriteMatchMetaMap.values) {
                if (meta.matchId in favoriteMatchIds) {
                    favMetaArr.put(
                        JSONObject().apply {
                            put("id", meta.matchId)
                            put("sport", meta.sport.name)
                            put("team1Name", meta.team1Name)
                            put("team2Name", meta.team2Name)
                            put("tournament", meta.tournament)
                        }
                    )
                }
            }

            val overridesArr = JSONArray()
            for (ov in cloudMatchOverrides.values) {
                overridesArr.put(serializeSingleMatch(ov))
            }

            prefs.edit()
                .putStringSet("favorite_match_ids", favoriteMatchIds.toSet())
                .putString("favorite_match_meta_v2", favMetaArr.toString())
                .putString("local_match_overrides_v2", overridesArr.toString())
                .putString("saved_channels_v5", channelsJson)
                .putString("app_config", configObj.toString())
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun normalizeTeamPairKey(sport: SportType, team1: String, team2: String): String {
        fun clean(name: String): String {
            return name.lowercase(Locale.US)
                .replace(Regex("[^a-z0-9 ]"), " ")
                .replace(Regex("\\b(fc|cf|sc|ac|afc|club|deportivo|real|sporting)\\b"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
        val pair = listOf(clean(team1), clean(team2)).sorted()
        return "${sport.name}:${pair[0]}::${pair[1]}"
    }

    /**
     * Safely remaps favourite associations and admin-configured stream overrides
     * when a provider match ID changes across refreshes or endpoints.
     * If an association cannot be unambiguously matched, records an `UnmappedAssociationNotice`
     * for the Admin Panel instead of silently deleting it.
     */
    private fun reconcileAndApplyAssociations(
        providerMatches: List<LiveMatch>
    ): List<LiveMatch> {
        val providerById = providerMatches.associateBy { it.id }
        val notices = mutableListOf<UnmappedAssociationNotice>()
        val remappedOverridesByNewId = HashMap<String, LiveMatch>()

        // 1. Reconcile Admin match overrides / custom stream associations
        for ((oldId, overrideMatch) in cloudMatchOverrides.entries.toList()) {
            if (oldId in LEGACY_DEMO_IDS) continue
            if (providerById.containsKey(oldId)) {
                remappedOverridesByNewId[oldId] = overrideMatch
                continue
            }

            val targetKey = normalizeTeamPairKey(
                overrideMatch.sport,
                overrideMatch.team1Name,
                overrideMatch.team2Name
            )
            val candidates = providerMatches.filter { pm ->
                pm.sport == overrideMatch.sport &&
                    normalizeTeamPairKey(pm.sport, pm.team1Name, pm.team2Name) == targetKey
            }

            if (candidates.size == 1) {
                val matched = candidates.first()
                val remapped = overrideMatch.copy(id = matched.id)
                cloudMatchOverrides.remove(oldId)
                cloudMatchOverrides[matched.id] = remapped
                remappedOverridesByNewId[matched.id] = remapped
            } else if (overrideMatch.streams.isNotEmpty() && oldId !in dismissedNoticeIds) {
                val reason = if (candidates.isEmpty()) {
                    "Provider feed-এ এই দলের বর্তমান কোনো ম্যাচ পাওয়া যায়নি (Stream association সংরক্ষিত আছে)"
                } else {
                    "একাধিক সম্ভাব্য ম্যাচ পাওয়া গেছে (${candidates.size}টি) — নিশ্চিতভাবে remap করা যায়নি"
                }
                notices.add(
                    UnmappedAssociationNotice(
                        previousMatchId = oldId,
                        sport = overrideMatch.sport,
                        team1Name = overrideMatch.team1Name,
                        team2Name = overrideMatch.team2Name,
                        tournament = overrideMatch.tournament,
                        hadFavorite = oldId in favoriteMatchIds,
                        preservedStreams = overrideMatch.streams,
                        detectedAtDhaka = EspnFootballService.formatTimestampDhaka(System.currentTimeMillis()),
                        reason = reason
                    )
                )
            }
        }

        // 2. Reconcile User Favourites when provider match IDs change
        for (favId in favoriteMatchIds.toList()) {
            if (providerById.containsKey(favId)) {
                val pm = providerById[favId]!!
                favoriteMatchMetaMap[favId] = FavoriteMatchMeta(
                    matchId = favId,
                    sport = pm.sport,
                    team1Name = pm.team1Name,
                    team2Name = pm.team2Name,
                    tournament = pm.tournament
                )
                continue
            }

            val meta = favoriteMatchMetaMap[favId] ?: continue
            val targetKey = normalizeTeamPairKey(meta.sport, meta.team1Name, meta.team2Name)
            val candidates = providerMatches.filter { pm ->
                pm.sport == meta.sport &&
                    normalizeTeamPairKey(pm.sport, pm.team1Name, pm.team2Name) == targetKey
            }

            if (candidates.size == 1) {
                val matched = candidates.first()
                favoriteMatchIds.remove(favId)
                favoriteMatchIds.add(matched.id)
                favoriteMatchMetaMap.remove(favId)
                favoriteMatchMetaMap[matched.id] = FavoriteMatchMeta(
                    matchId = matched.id,
                    sport = matched.sport,
                    team1Name = matched.team1Name,
                    team2Name = matched.team2Name,
                    tournament = matched.tournament
                )
            } else if (favId !in dismissedNoticeIds) {
                notices.add(
                    UnmappedAssociationNotice(
                        previousMatchId = favId,
                        sport = meta.sport,
                        team1Name = meta.team1Name,
                        team2Name = meta.team2Name,
                        tournament = meta.tournament,
                        hadFavorite = true,
                        preservedStreams = emptyList(),
                        detectedAtDhaka = EspnFootballService.formatTimestampDhaka(System.currentTimeMillis()),
                        reason = "Provider match ID পরিবর্তিত হয়েছে; Favourite সংরক্ষিত আছে কিন্তু বর্তমান ফিক্সচারের সাথে মেলানো যায়নি"
                    )
                )
            }
        }

        _unmappedNotices.value = notices

        // 3. Apply overrides and favourites onto provider matches
        return providerMatches.map { m ->
            val cloudOverride = remappedOverridesByNewId[m.id] ?: cloudMatchOverrides[m.id]
            val merged = if (cloudOverride != null) {
                m.copy(
                    tournament = cloudOverride.tournament.ifBlank { m.tournament },
                    matchTime = cloudOverride.matchTime.ifBlank { m.matchTime },
                    streams = if (cloudOverride.streams.isNotEmpty()) cloudOverride.streams else m.streams,
                    approvedHighlightUrl = cloudOverride.approvedHighlightUrl ?: m.approvedHighlightUrl,
                    sofascoreEventId = cloudOverride.sofascoreEventId ?: m.sofascoreEventId,
                    broadcastChannels = if (cloudOverride.broadcastChannels.isNotEmpty()) {
                        cloudOverride.broadcastChannels
                    } else {
                        m.broadcastChannels
                    }
                )
            } else {
                m
            }
            merged.copy(isFavorite = merged.id in favoriteMatchIds)
        }
    }

    fun dismissUnmappedNotice(noticeId: String) {
        val cleanId = noticeId.removePrefix("stream_").removePrefix("fav_")
        dismissedNoticeIds.add(cleanId)
        _unmappedNotices.value = _unmappedNotices.value.filter { it.previousMatchId != cleanId }
    }

    fun reassignUnmappedStreamToMatch(oldMatchId: String, targetMatchId: String): Boolean {
        val oldOverride = cloudMatchOverrides[oldMatchId] ?: return false
        val targetMatch = _matches.value.find { it.id == targetMatchId } ?: return false
        val reassigned = targetMatch.copy(
            streams = oldOverride.streams,
            approvedHighlightUrl = oldOverride.approvedHighlightUrl ?: targetMatch.approvedHighlightUrl
        )
        cloudMatchOverrides.remove(oldMatchId)
        cloudMatchOverrides[targetMatchId] = reassigned
        dismissedNoticeIds.add(oldMatchId)
        rebuildCombinedMatches()
        saveData()
        return true
    }

    private fun rebuildCombinedMatches() {
        val cfg = _config.value
        val state = _crexDashboardState.value

        // Separate provider feeds strictly by SportType & Admin toggle
        val rawFootball = if (cfg.footballSyncEnabled) {
            footballProviderMatches.filter { it.sport == SportType.FOOTBALL && it.id !in deletedMatchIds }
        } else {
            emptyList()
        }

        val rawCricket = if (cfg.cricketSyncEnabled) {
            buildList {
                addAll(state.liveState.matches)
                addAll(state.upcomingState.matches)
                addAll(state.finishedState.matches)
            }.filter { it.sport == SportType.CRICKET && it.id !in deletedMatchIds }
                .distinctBy { it.id }
        } else {
            emptyList()
        }

        // Automatically enrich every provider match with live playlist streams & broadcast channel schedule ("কোনটায় কখন খেলা")
        val allProviderMatches = (rawFootball + rawCricket).map { m ->
            playlistMatcherService.enrichMatchWithAutoPlaylistStreams(m)
        }
        val enrichedMatches = reconcileAndApplyAssociations(allProviderMatches)

        val fbMatches = enrichedMatches.filter { it.sport == SportType.FOOTBALL }
        val cricMatches = enrichedMatches.filter { it.sport == SportType.CRICKET }

        // Keep CrexDashboardState buckets updated with favourites & overrides
        _crexDashboardState.value = state.copy(
            liveState = state.liveState.copy(matches = cricMatches.filter { it.status == MatchStatus.LIVE }),
            upcomingState = state.upcomingState.copy(
                matches = cricMatches.filter { it.status == MatchStatus.UPCOMING || it.status == MatchStatus.UNKNOWN }
            ),
            finishedState = state.finishedState.copy(matches = cricMatches.filter { it.status == MatchStatus.FINISHED })
        )

        // Order: LIVE first, then UPCOMING, then FINISHED, then UNKNOWN
        val combined = buildList {
            addAll(fbMatches.filter { it.status == MatchStatus.LIVE })
            addAll(cricMatches.filter { it.status == MatchStatus.LIVE })
            addAll(fbMatches.filter { it.status == MatchStatus.UPCOMING })
            addAll(cricMatches.filter { it.status == MatchStatus.UPCOMING })
            addAll(fbMatches.filter { it.status == MatchStatus.FINISHED })
            addAll(cricMatches.filter { it.status == MatchStatus.FINISHED })
            addAll(fbMatches.filter { it.status == MatchStatus.UNKNOWN })
            addAll(cricMatches.filter { it.status == MatchStatus.UNKNOWN })
        }.distinctBy { "${it.sport.name}_${it.id}" }

        _matches.value = combined
        refreshDiagnosticsSnapshot()
    }

    /**
     * Refreshes Football matches from verified ESPN Soccer Scoreboard endpoints.
     */
    suspend fun refreshFootballMatches(force: Boolean = false): List<LiveMatch> = withContext(Dispatchers.IO) {
        val cfg = _config.value
        if (!cfg.footballSyncEnabled) {
            footballProviderMatches.clear()
            rebuildCombinedMatches()
            return@withContext emptyList()
        }
        val playlistJob = async { playlistMatcherService.refreshLivePlaylists(force = force) }
        val feedResult = espnService.fetchScoreboardMatches(
            enabledLeagues = cfg.enabledFootballLeagues,
            forceRefresh = force
        )
        playlistJob.await()
        synchronized(footballProviderMatches) {
            footballProviderMatches.clear()
            footballProviderMatches.addAll(feedResult.matches)
        }
        rebuildCombinedMatches()
        _matches.value.filter { it.sport == SportType.FOOTBALL }
    }

    /**
     * Refreshes all Cricket categories (Live, Upcoming, Finished), Football matches (ESPN),
     * and the auto-updating live M3U Sports Playlists concurrently.
     */
    suspend fun refreshLiveMatches(force: Boolean = false): CrexCategoryFeedState = withContext(Dispatchers.IO) {
        val cfg = _config.value
        val current = _crexDashboardState.value.liveState
        if (current.matches.isEmpty() && cfg.cricketSyncEnabled) {
            _crexDashboardState.value = _crexDashboardState.value.copy(
                liveState = current.copy(isLoading = true, errorMessage = null)
            )
        }

        val customM3uList = if (cfg.remoteFeedUrl.endsWith(".m3u", true) || cfg.remoteFeedUrl.endsWith(".m3u8", true)) {
            listOf(cfg.remoteFeedUrl)
        } else {
            emptyList()
        }

        val playlistDeferred = async {
            playlistMatcherService.refreshLivePlaylists(force = force, extraM3uUrls = customM3uList)
        }

        val fbDeferred = async {
            if (cfg.footballSyncEnabled) {
                espnService.fetchScoreboardMatches(
                    enabledLeagues = cfg.enabledFootballLeagues,
                    forceRefresh = force
                )
            } else {
                null
            }
        }

        val cricDeferred = async {
            if (cfg.cricketSyncEnabled) {
                crexService.fetchSynchronizedDashboard(forceRefresh = force)
            } else {
                CrexDashboardState()
            }
        }

        playlistDeferred.await()
        val fbResult = fbDeferred.await()
        if (fbResult != null) {
            synchronized(footballProviderMatches) {
                footballProviderMatches.clear()
                footballProviderMatches.addAll(fbResult.matches)
            }
        } else {
            synchronized(footballProviderMatches) {
                footballProviderMatches.clear()
            }
        }

        val synchronizedCric = cricDeferred.await()
        _crexDashboardState.value = synchronizedCric.copy(
            activeDetailState = _crexDashboardState.value.activeDetailState
        )

        // Merge newly discovered live M3U channels into the Channels list without removing Admin/user channels
        syncAutoDiscoveredChannelsIntoList()

        rebuildCombinedMatches()
        _crexDashboardState.value.liveState
    }

    private fun syncAutoDiscoveredChannelsIntoList() {
        val autoChannels = playlistMatcherService.buildAutoUpdatedChannels(favoriteChannelIds)
        if (autoChannels.isEmpty()) return
        val currentList = _channels.value.toMutableList()
        val existingByName = currentList.associateBy { it.name.lowercase(Locale.US).trim() }
        val existingIds = currentList.map { it.id }.toHashSet()

        var changed = false
        // Update up to 35 top live sports channels from the M3U playlist
        for (autoCh in autoChannels.take(35)) {
            val normName = autoCh.name.lowercase(Locale.US).trim()
            if (autoCh.id !in existingIds && !existingByName.containsKey(normName)) {
                currentList.add(autoCh)
                existingIds.add(autoCh.id)
                changed = true
            }
        }
        if (changed) {
            _channels.value = currentList
        }
    }

    /**
     * Refreshes Upcoming matches via `GET /api/cricket/upcoming` (`POST getHomeUpcomingMatchesCE {"type": 1}`)
     * and ESPN Football scoreboard.
     */
    suspend fun refreshUpcomingMatches(force: Boolean = false): CrexCategoryFeedState = withContext(Dispatchers.IO) {
        refreshLiveMatches(force = force)
        _crexDashboardState.value.upcomingState
    }

    /**
     * Refreshes Finished matches via `GET /api/cricket/finished` (`POST getHomeUpcomingMatchesCE {"type": 0}`).
     */
    suspend fun refreshFinishedMatches(force: Boolean = false): CrexCategoryFeedState = withContext(Dispatchers.IO) {
        refreshLiveMatches(force = force)
        _crexDashboardState.value.finishedState
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
            val v1 = TeamFlagResolver.resolveForMatch(d.team1Name, "", isNationalTeam = true)
            val v2 = TeamFlagResolver.resolveForMatch(d.team2Name, "", isNationalTeam = true)
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

    /**
     * Enriches a match on-demand with optional SofaScore TV broadcast channels and media highlights
     * if a valid `sofascoreEventId` is configured on the match.
     * Matches broadcast channels strictly against admin-approved channels in `_channels.value`.
     */
    suspend fun enrichMatchWithSofaScoreIfAvailable(matchId: String): LiveMatch? = withContext(Dispatchers.IO) {
        val currentMatch = _matches.value.find { it.id == matchId } ?: return@withContext null
        val sofaEventId = currentMatch.sofascoreEventId ?: return@withContext currentMatch
        val enrichment = sofascoreService.fetchSupplementalMetadata(
            sofascoreEventId = sofaEventId,
            approvedChannels = _channels.value.filter { it.isEnabled }
        ) ?: return@withContext currentMatch
        if (enrichment.tvChannelNames.isEmpty() && enrichment.matchedApprovedStreams.isEmpty() && enrichment.approvedHighlightUrl == null) {
            return@withContext currentMatch
        }
        val mergedStreams = (currentMatch.streams + enrichment.matchedApprovedStreams).distinctBy { it.url }
        val updated = currentMatch.copy(
            streams = mergedStreams,
            broadcastChannels = enrichment.tvChannelNames,
            approvedHighlightUrl = currentMatch.approvedHighlightUrl ?: enrichment.approvedHighlightUrl
        )
        cloudMatchOverrides[updated.id] = updated
        rebuildCombinedMatches()
        updated
    }

    // Match Operations (Admin stream & metadata overrides)
    fun addOrUpdateMatch(match: LiveMatch) {
        cloudMatchOverrides[match.id] = match
        val cur = _crexDashboardState.value
        fun upsert(list: List<LiveMatch>): List<LiveMatch> {
            val mList = list.toMutableList()
            val idx = mList.indexOfFirst { it.id == match.id }
            if (idx >= 0) mList[idx] = match else if (match.sport == SportType.CRICKET) mList.add(0, match)
            return mList
        }
        if (match.sport == SportType.CRICKET) {
            _crexDashboardState.value = when (match.status) {
                MatchStatus.LIVE -> cur.copy(liveState = cur.liveState.copy(matches = upsert(cur.liveState.matches)))
                MatchStatus.UPCOMING, MatchStatus.UNKNOWN -> cur.copy(
                    upcomingState = cur.upcomingState.copy(matches = upsert(cur.upcomingState.matches))
                )
                MatchStatus.FINISHED -> cur.copy(
                    finishedState = cur.finishedState.copy(matches = upsert(cur.finishedState.matches))
                )
            }
        } else if (match.sport == SportType.FOOTBALL) {
            synchronized(footballProviderMatches) {
                val idx = footballProviderMatches.indexOfFirst { it.id == match.id }
                if (idx >= 0) {
                    footballProviderMatches[idx] = match
                }
            }
        }
        rebuildCombinedMatches()
        saveData()

        if (isFirebaseSignedIn()) {
            repoScope.launch {
                cloudSyncRepository?.upsertPublicCatalogItem(
                    rawItemId = match.id,
                    itemType = "match",
                    title = "${match.team1Name} vs ${match.team2Name}",
                    payloadJson = serializeSingleMatch(match).toString()
                )
            }
        }
    }

    fun deleteMatch(matchId: String) {
        cloudMatchOverrides.remove(matchId)
        deletedMatchIds.add(matchId)
        synchronized(footballProviderMatches) {
            footballProviderMatches.removeAll { it.id == matchId }
        }
        val cur = _crexDashboardState.value
        _crexDashboardState.value = cur.copy(
            liveState = cur.liveState.copy(matches = cur.liveState.matches.filter { it.id != matchId }),
            upcomingState = cur.upcomingState.copy(matches = cur.upcomingState.matches.filter { it.id != matchId }),
            finishedState = cur.finishedState.copy(matches = cur.finishedState.matches.filter { it.id != matchId })
        )
        rebuildCombinedMatches()
        saveData()

        if (isFirebaseSignedIn()) {
            repoScope.launch {
                cloudSyncRepository?.deletePublicCatalogItem(matchId, "match")
            }
        }
    }

    fun toggleMatchFavorite(matchId: String) {
        val matchObj = _matches.value.find { it.id == matchId }
        val added: Boolean
        if (matchId in favoriteMatchIds) {
            favoriteMatchIds.remove(matchId)
            favoriteMatchMetaMap.remove(matchId)
            added = false
        } else {
            favoriteMatchIds.add(matchId)
            if (matchObj != null) {
                favoriteMatchMetaMap[matchId] = FavoriteMatchMeta(
                    matchId = matchId,
                    sport = matchObj.sport,
                    team1Name = matchObj.team1Name,
                    team2Name = matchObj.team2Name,
                    tournament = matchObj.tournament
                )
            }
            added = true
        }
        rebuildCombinedMatches()
        saveData()

        if (isFirebaseSignedIn()) {
            val title = matchObj?.let { "${it.team1Name} vs ${it.team2Name}" } ?: matchId
            repoScope.launch {
                if (added) {
                    cloudSyncRepository?.addFavorite(matchId, "match", title)
                } else {
                    cloudSyncRepository?.removeFavorite("match_$matchId")
                }
            }
        }
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

        if (isFirebaseSignedIn()) {
            repoScope.launch {
                cloudSyncRepository?.upsertPublicCatalogItem(
                    rawItemId = channel.id,
                    itemType = "channel",
                    title = channel.name,
                    payloadJson = serializeSingleChannel(channel).toString()
                )
            }
        }
    }

    fun deleteChannel(channelId: String) {
        _channels.value = _channels.value.filter { it.id != channelId }
        saveData()

        if (isFirebaseSignedIn()) {
            repoScope.launch {
                cloudSyncRepository?.deletePublicCatalogItem(channelId, "channel")
            }
        }
    }

    fun toggleChannelFavorite(channelId: String) {
        var isNowFav = false
        var chTitle = channelId
        _channels.value = _channels.value.map {
            if (it.id == channelId) {
                isNowFav = !it.isFavorite
                chTitle = it.name
                it.copy(isFavorite = isNowFav)
            } else {
                it
            }
        }
        if (isNowFav) favoriteChannelIds.add(channelId) else favoriteChannelIds.remove(channelId)
        saveData()

        if (isFirebaseSignedIn()) {
            repoScope.launch {
                if (isNowFav) {
                    cloudSyncRepository?.addFavorite(channelId, "channel", chTitle)
                } else {
                    cloudSyncRepository?.removeFavorite("channel_$channelId")
                }
            }
        }
    }

    fun updateConfig(newConfig: AppConfig) {
        val previous = _config.value
        if (newConfig.customM3uBlock.isNotBlank()) {
            playlistMatcherService.registerAdminM3uBlock(newConfig.customM3uBlock)
        }
        _config.value = newConfig
        saveData()
        rebuildCombinedMatches()

        // If sync toggles or enabled football leagues changed, trigger a fresh background sync
        if (previous.footballSyncEnabled != newConfig.footballSyncEnabled ||
            previous.cricketSyncEnabled != newConfig.cricketSyncEnabled ||
            previous.enabledFootballLeagues != newConfig.enabledFootballLeagues
        ) {
            repoScope.launch {
                refreshLiveMatches(force = true)
            }
        }

        if (isFirebaseSignedIn()) {
            repoScope.launch {
                val leaguesArr = JSONArray().apply {
                    newConfig.enabledFootballLeagues.forEach { put(it) }
                }
                val configObj = JSONObject().apply {
                    put("appTitle", newConfig.appTitle)
                    put("version", newConfig.version)
                    put("announcement", newConfig.announcement)
                    put("telegramLink", newConfig.telegramLink)
                    put("adminPin", newConfig.adminPin)
                    put("remoteFeedUrl", newConfig.remoteFeedUrl)
                    put("autoSyncOnLaunch", newConfig.autoSyncOnLaunch)
                    put("footballSyncEnabled", newConfig.footballSyncEnabled)
                    put("cricketSyncEnabled", newConfig.cricketSyncEnabled)
                    put("autoRefreshIntervalSec", newConfig.autoRefreshIntervalSec)
                    put("enabledFootballLeagues", leaguesArr)
                    put("customM3uBlock", newConfig.customM3uBlock.take(120000))
                }
                cloudSyncRepository?.upsertPublicCatalogItem(
                    rawItemId = "global_app_config",
                    itemType = "config",
                    title = newConfig.appTitle,
                    payloadJson = configObj.toString()
                )
            }
        }
    }

    /**
     * Pushes all current Admin Panel matches (with custom streams), channels, and global config
     * to the Firebase Firestore Cloud Server so all connected users receive them immediately.
     */
    suspend fun pushAllToCloudServer(): Result<Int> = withContext(Dispatchers.IO) {
        val syncRepo = cloudSyncRepository
        if (!isFirebaseSignedIn() || syncRepo == null) {
            return@withContext Result.failure(IllegalStateException("Google Sign-In required to push to Cloud Server"))
        }
        var pushedCount = 0
        try {
            val cfg = _config.value
            val leaguesArr = JSONArray().apply {
                cfg.enabledFootballLeagues.forEach { put(it) }
            }
            val configObj = JSONObject().apply {
                put("appTitle", cfg.appTitle)
                put("version", cfg.version)
                put("announcement", cfg.announcement)
                put("telegramLink", cfg.telegramLink)
                put("adminPin", cfg.adminPin)
                put("remoteFeedUrl", cfg.remoteFeedUrl)
                put("autoSyncOnLaunch", cfg.autoSyncOnLaunch)
                put("footballSyncEnabled", cfg.footballSyncEnabled)
                put("cricketSyncEnabled", cfg.cricketSyncEnabled)
                put("autoRefreshIntervalSec", cfg.autoRefreshIntervalSec)
                put("enabledFootballLeagues", leaguesArr)
                put("customM3uBlock", cfg.customM3uBlock.take(120000))
            }
            syncRepo.upsertPublicCatalogItem(
                rawItemId = "global_app_config",
                itemType = "config",
                title = cfg.appTitle,
                payloadJson = configObj.toString()
            ).getOrThrow()
            pushedCount++

            for (ch in _channels.value) {
                syncRepo.upsertPublicCatalogItem(
                    rawItemId = ch.id,
                    itemType = "channel",
                    title = ch.name,
                    payloadJson = serializeSingleChannel(ch).toString()
                ).getOrThrow()
                pushedCount++
            }

            for (m in _matches.value) {
                if (cloudMatchOverrides.containsKey(m.id)) {
                    syncRepo.upsertPublicCatalogItem(
                        rawItemId = m.id,
                        itemType = "match",
                        title = "${m.team1Name} vs ${m.team2Name}",
                        payloadJson = serializeSingleMatch(m).toString()
                    ).getOrThrow()
                    pushedCount++
                }
            }

            _cloudSyncStatus.value = "Synced $pushedCount items to Cloud Server"
            Result.success(pushedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun serializeSingleMatch(m: LiveMatch): JSONObject {
        return JSONObject().apply {
            put("id", m.id)
            put("tournament", m.tournament)
            put("sport", m.sport.name)
            put("status", m.status.name)
            put("matchTime", m.matchTime)
            put("timeOrScore", m.timeOrScore)
            put("team1Name", m.team1Name)
            put("team1Code", m.team1Code)
            put("team1FlagUrl", m.team1FlagUrl)
            put("team2Name", m.team2Name)
            put("team2Code", m.team2Code)
            put("team2FlagUrl", m.team2FlagUrl)
            put("provider", m.provider)
            put("competitionId", m.competitionId)
            put("startTimeEpochMs", m.startTimeEpochMs)
            put("isNationalTeam", m.isNationalTeam)
            if (m.sofascoreEventId != null) put("sofascoreEventId", m.sofascoreEventId)
            if (!m.approvedHighlightUrl.isNullOrBlank()) put("approvedHighlightUrl", m.approvedHighlightUrl)
            val streamsArr = JSONArray()
            for (s in m.streams) {
                val so = JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("quality", s.quality)
                    put("url", s.url)
                    put("isDefault", s.isDefault)
                    put("isEnabled", s.isEnabled)
                    put("priorityOrder", s.priorityOrder)
                    put("isBackup", s.isBackup)
                    if (!s.userAgent.isNullOrBlank()) put("userAgent", s.userAgent)
                    if (!s.referer.isNullOrBlank()) put("referer", s.referer)
                }
                streamsArr.put(so)
            }
            put("streams", streamsArr)
        }
    }

    private fun deserializeSingleMatch(obj: JSONObject): LiveMatch {
        val streamList = mutableListOf<StreamSource>()
        val streamArr = obj.optJSONArray("streams")
        if (streamArr != null) {
            for (j in 0 until streamArr.length()) {
                val sObj = streamArr.getJSONObject(j)
                val sUrl = sObj.optString("url")
                if (sUrl.isNotBlank()) {
                    streamList.add(
                        StreamSource(
                            id = sObj.optString("id").ifBlank { UUID.randomUUID().toString() },
                            name = sObj.optString("name", "Server ${j + 1}"),
                            quality = sObj.optString("quality", "1080p FHD"),
                            url = sUrl,
                            userAgent = sObj.optString("userAgent").ifBlank { null },
                            referer = sObj.optString("referer").ifBlank { null },
                            isDefault = sObj.optBoolean("isDefault", j == 0),
                            isEnabled = sObj.optBoolean("isEnabled", true),
                            priorityOrder = sObj.optInt("priorityOrder", j),
                            isBackup = sObj.optBoolean("isBackup", false)
                        )
                    )
                }
            }
        }
        val sportStr = obj.optString("sport", "CRICKET").uppercase()
        val sport = try {
            SportType.valueOf(sportStr)
        } catch (_: Exception) {
            SportType.CRICKET
        }
        val statusStr = obj.optString("status", "UPCOMING").uppercase()
        val status = try {
            MatchStatus.valueOf(statusStr)
        } catch (_: Exception) {
            MatchStatus.UNKNOWN
        }
        val t1Name = obj.optString("team1Name").ifBlank { obj.optString("team1", "Team 1") }
        val t2Name = obj.optString("team2Name").ifBlank { obj.optString("team2", "Team 2") }
        val sofaId = obj.optString("sofascoreEventId", "").trim().takeIf { it.isNotBlank() && it != "0" }
        return LiveMatch(
            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
            tournament = obj.optString("tournament", "MATCH"),
            sport = sport,
            status = status,
            matchTime = obj.optString("matchTime").ifBlank { obj.optString("time", "") },
            timeOrScore = obj.optString("timeOrScore").ifBlank { obj.optString("score", "") },
            team1Name = t1Name,
            team1Code = obj.optString("team1Code").ifBlank { t1Name.take(3).uppercase() },
            team1FlagUrl = obj.optString("team1FlagUrl").ifBlank { obj.optString("team1Flag", "") },
            team2Name = t2Name,
            team2Code = obj.optString("team2Code").ifBlank { t2Name.take(3).uppercase() },
            team2FlagUrl = obj.optString("team2FlagUrl").ifBlank { obj.optString("team2Flag", "") },
            streams = streamList,
            provider = obj.optString("provider", if (sport == SportType.FOOTBALL) "ESPN" else "CREX"),
            competitionId = obj.optString("competitionId", ""),
            startTimeEpochMs = obj.optLong("startTimeEpochMs", 0L),
            isNationalTeam = obj.optBoolean("isNationalTeam", sport == SportType.CRICKET),
            sofascoreEventId = sofaId,
            approvedHighlightUrl = obj.optString("approvedHighlightUrl").ifBlank { null }
        )
    }

    private fun serializeSingleChannel(c: SportsChannel): JSONObject {
        return JSONObject().apply {
            put("id", c.id)
            put("name", c.name)
            put("category", c.category)
            put("logoText", c.logoText)
            put("logoUrl", c.logoUrl)
            put("isFavorite", c.isFavorite)
            put("isEnabled", c.isEnabled)
            val streamArr = JSONArray()
            for (s in c.streams) {
                val sObj = JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("quality", s.quality)
                    put("url", s.url)
                    put("isDefault", s.isDefault)
                    put("isEnabled", s.isEnabled)
                    put("priorityOrder", s.priorityOrder)
                    put("isBackup", s.isBackup)
                    if (!s.userAgent.isNullOrBlank()) put("userAgent", s.userAgent)
                    if (!s.referer.isNullOrBlank()) put("referer", s.referer)
                }
                streamArr.put(sObj)
            }
            put("streams", streamArr)
        }
    }

    private fun deserializeSingleChannel(obj: JSONObject): SportsChannel {
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
                            isDefault = sObj.optBoolean("isDefault", j == 0),
                            isEnabled = sObj.optBoolean("isEnabled", true),
                            priorityOrder = sObj.optInt("priorityOrder", j),
                            isBackup = sObj.optBoolean("isBackup", false)
                        )
                    )
                }
            }
        }
        val cName = obj.optString("name").ifBlank { obj.optString("title", "Sports Channel") }
        return SportsChannel(
            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = cName,
            category = obj.optString("category", "Sports"),
            logoText = obj.optString("logoText").ifBlank { cName.take(3).uppercase() },
            logoUrl = obj.optString("logoUrl", ""),
            streams = streamList,
            isFavorite = obj.optBoolean("isFavorite", false),
            isEnabled = obj.optBoolean("isEnabled", true)
        )
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
            root.put("footballSyncEnabled", _config.value.footballSyncEnabled)
            root.put("cricketSyncEnabled", _config.value.cricketSyncEnabled)
            root.put("autoRefreshIntervalSec", _config.value.autoRefreshIntervalSec)

            val matchesArray = JSONArray()
            for (m in _matches.value) {
                matchesArray.put(serializeSingleMatch(m))
            }
            root.put("matches", matchesArray)

            val channelsArray = JSONArray()
            for (c in _channels.value) {
                channelsArray.put(serializeSingleChannel(c))
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
                            isDefault = true,
                            isEnabled = true,
                            priorityOrder = 1
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
            playlistMatcherService.registerAdminM3uBlock(rawText)
            val updatedCfg = _config.value.copy(
                customM3uBlock = (_config.value.customM3uBlock + "\n" + rawText).trim().takeLast(100000)
            )
            _config.value = updatedCfg
            rebuildCombinedMatches()
            saveData()

            if (isFirebaseSignedIn()) {
                repoScope.launch {
                    updateConfig(updatedCfg)
                    for (ch in parsedChannels.take(25)) {
                        cloudSyncRepository?.upsertPublicCatalogItem(
                            rawItemId = ch.id,
                            itemType = "channel",
                            title = ch.name,
                            payloadJson = serializeSingleChannel(ch).toString()
                        )
                    }
                }
            }
        }

        parsedChannels.size
    }

    /**
     * Concurrently scans a pasted M3U / `.m3u8` block, detects which streams are Active (HTTP 200 / HLS)
     * vs Offline, and shows which Sport/League/Match each stream will auto-attach to.
     */
    suspend fun scanBulkM3uText(rawText: String): List<BulkM3uScanItem> = withContext(Dispatchers.IO) {
        val fetchedText = if (rawText.trim().startsWith("http://", true) || rawText.trim().startsWith("https://", true)) {
            if (!rawText.trim().contains("\n") && (rawText.trim().endsWith(".m3u", true) || rawText.trim().contains("raw.githubusercontent"))) {
                try {
                    val req = Request.Builder().url(rawText.trim()).build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string().orEmpty() else rawText
                    }
                } catch (_: Exception) {
                    rawText
                }
            } else {
                rawText
            }
        } else {
            rawText
        }

        val entries = playlistMatcherService.parseFlexibleAdminM3uText(fetchedText)
        if (entries.isEmpty()) return@withContext emptyList()

        val jobs = entries.map { entry ->
            async {
                val res = checkM3U8Stream(entry.url)
                val targetDesc = playlistMatcherService.describeAutoMatchTarget(entry.name, entry.category)
                BulkM3uScanItem(
                    id = entry.id,
                    name = entry.name,
                    url = entry.url,
                    category = entry.category,
                    quality = entry.quality,
                    logoUrl = entry.logoUrl,
                    autoMatchedTarget = targetDesc,
                    checkResult = res
                )
            }
        }
        jobs.awaitAll()
    }

    /**
     * Auto-assigns scanned M3U8 items (either only Active ones or all) into:
     * 1. `LivePlaylistMatcherService` (for automatic Cricket & Football match stream enrichment)
     * 2. `_channels` (merging into existing channels or creating new channel cards)
     * 3. `config.customM3uBlock` & Firestore `cloud_items` so all users receive the update in real time!
     */
    suspend fun applyScannedM3uItems(
        items: List<BulkM3uScanItem>,
        onlyActive: Boolean = true
    ): Int = withContext(Dispatchers.IO) {
        val selected = if (onlyActive) {
            items.filter { it.checkResult?.isOnline == true }.ifEmpty { items }
        } else {
            items
        }
        if (selected.isEmpty()) return@withContext 0

        val playlistEntries = selected.map { item ->
            com.example.data.playlist.PlaylistEntry(
                id = item.id,
                name = item.name,
                cleanName = item.name.lowercase(Locale.US).replace(Regex("[^a-z0-9 ]"), " ").trim(),
                url = item.url,
                quality = item.quality,
                logoUrl = item.logoUrl,
                category = item.category
            )
        }
        val healthMap = selected.mapNotNull { item ->
            item.checkResult?.let { item.url to it.isOnline }
        }.toMap()

        playlistMatcherService.registerAdminCustomM3uEntries(playlistEntries, healthMap)

        val currentChannels = _channels.value.toMutableList()
        val updatedChannelsForCloud = mutableListOf<SportsChannel>()

        for (item in selected) {
            val newStream = StreamSource(
                id = "${item.id}-s",
                name = "${item.name} (${item.quality})",
                quality = item.quality,
                url = item.url,
                userAgent = item.userAgent,
                referer = item.referer,
                isDefault = true,
                isEnabled = true,
                priorityOrder = 1
            )
            val existingIdx = currentChannels.indexOfFirst {
                it.name.equals(item.name, ignoreCase = true) ||
                    it.name.lowercase(Locale.US).contains(item.name.lowercase(Locale.US).take(8))
            }
            if (existingIdx >= 0) {
                val existingCh = currentChannels[existingIdx]
                val mergedStreams = (listOf(newStream) + existingCh.streams.map { it.copy(isDefault = false, priorityOrder = it.priorityOrder + 1) })
                    .distinctBy { it.url }
                val updatedCh = existingCh.copy(streams = mergedStreams, isEnabled = true)
                currentChannels[existingIdx] = updatedCh
                updatedChannelsForCloud.add(updatedCh)
            } else {
                val createdCh = SportsChannel(
                    id = item.id,
                    name = item.name,
                    category = item.category,
                    logoText = item.name.take(4).uppercase(Locale.US),
                    logoUrl = item.logoUrl,
                    streams = listOf(newStream),
                    isFavorite = false,
                    isEnabled = true
                )
                currentChannels.add(0, createdCh)
                updatedChannelsForCloud.add(createdCh)
            }
        }

        _channels.value = currentChannels
        val m3uBlockToSave = selected.joinToString("\n") {
            "#EXTINF:-1 group-title=\"${it.category}\",${it.name}\n${it.url}"
        }
        val mergedM3uBlock = (_config.value.customM3uBlock + "\n" + m3uBlockToSave)
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .takeLast(100000)

        val newCfg = _config.value.copy(customM3uBlock = mergedM3uBlock)
        _config.value = newCfg
        rebuildCombinedMatches()
        saveData()

        if (isFirebaseSignedIn()) {
            updateConfig(newCfg)
            for (ch in updatedChannelsForCloud.take(30)) {
                cloudSyncRepository?.upsertPublicCatalogItem(
                    rawItemId = ch.id,
                    itemType = "channel",
                    title = ch.name,
                    payloadJson = serializeSingleChannel(ch).toString()
                )
            }
            _cloudSyncStatus.value = "Synced ${selected.size} active streams to All Users (Cloud Live)"
        }
        selected.size
    }

    /**
     * Scans all streams across channels and removes dead/offline links while keeping at least 1 stream per channel.
     */
    suspend fun removeDeadStreamsFromChannels(): Int = withContext(Dispatchers.IO) {
        var removedCount = 0
        val updatedList = _channels.value.map { ch ->
            if (ch.streams.size <= 1) {
                ch
            } else {
                val checked = ch.streams.map { s ->
                    s to checkM3U8Stream(s.url).isOnline
                }
                val alive = checked.filter { it.second }.map { it.first }
                if (alive.isNotEmpty() && alive.size < ch.streams.size) {
                    removedCount += (ch.streams.size - alive.size)
                    ch.copy(streams = alive.mapIndexed { idx, s -> s.copy(isDefault = idx == 0, priorityOrder = idx + 1) })
                } else {
                    ch
                }
            }
        }
        if (removedCount > 0) {
            _channels.value = updatedList
            saveData()
            if (isFirebaseSignedIn()) {
                for (ch in updatedList) {
                    cloudSyncRepository?.upsertPublicCatalogItem(
                        rawItemId = ch.id,
                        itemType = "channel",
                        title = ch.name,
                        payloadJson = serializeSingleChannel(ch).toString()
                    )
                }
            }
        }
        removedCount
    }

    fun normalizeRemoteFeedUrl(url: String): String = url.trim()

    /**
     * Manual refresh action that syncs both Football (ESPN) and Cricket (CREX) backend routes.
     */
    suspend fun syncFromRemoteFeed(feedUrl: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        refreshLiveMatches(force = true)
        val total = _matches.value.size
        val state = _crexDashboardState.value
        val err = state.liveState.errorMessage ?: state.upcomingState.errorMessage ?: state.finishedState.errorMessage
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
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36 SportzXLiveTV/8.0"
                )
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
            array.put(serializeSingleChannel(c))
        }
        return array.toString()
    }

    private fun deserializeChannels(json: String): List<SportsChannel> {
        val list = mutableListOf<SportsChannel>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(deserializeSingleChannel(obj))
        }
        return list
    }

    private fun defaultChannels(): List<SportsChannel> {
        val tSportsUrl = "https://live-us1.thaimomo.com/live-as/chtsport-1/playlist.m3u8"
        val tSportsRobiUrl = "https://robitv.com/rn/http://45.117.61.162/01_Tsports_HD/tracks-v1a1/mono.m3u8"
        val aSportsUrl = "https://tvsen6.aynaott.com/zv68oqPDu7MZZwmHhRxt/index.m3u8"
        val aSportsTrackUrl = "https://tvsen6.aynaott.com/zv68oqPDu7MZZwmHhRxt/tracks-v1a1/mono.ts.m3u8"
        val ptvSportsUrl = "https://flussonic.defineatoz.com/PTV/index.m3u8"
        val starSports2AynaUrl = "https://tvsen7.aynaott.com/ssport2hd/index.m3u8"
        val willowAynaUrl = "https://tvsen5.aynaott.com/willowhd/index.m3u8"
        val willowHerokuUrl = "https://warm-caverns-48629-92fab798385f.herokuapp.com/https://d36r8jifhgsk5j.cloudfront.net/Willow_TV540p.m3u8"
        val willowUrl = "https://streams2.sofast.tv/ptnr-yupptv/title-cricketgold/v1/master/611d79b11b77e2f571934fd80ca1413453772ac7/b2048bb8-1686-4432-aa50-647245383e0c/manifest.m3u8"
        val cricketGoldAynaUrl = "https://tvsen6.aynaott.com/M2W2UR49dmeKbZnmdRzN/index.m3u8"
        val sonyTen1GpUrl = "https://drk6xq0vhn.gpcdn.net/live/ten_1_hd_720/index.m3u8"
        val sonyTen2GpUrl = "https://drk6xq0vhn.gpcdn.net/live/ten_2_hd_720/index.m3u8"
        val sonyTen5GpUrl = "https://drk6xq0vhn.gpcdn.net/live/ten_5_hd_720/index.m3u8"
        val sonyTen1Url = "http://103.151.60.162:2122/play/a01c/index.m3u8?hls"
        val sonyTen2Url = "http://103.151.60.162:2122/play/a01b/index.m3u8?hls"
        val sonyTen3Url = "https://d3qs3d2rkhfqrt.cloudfront.net/out/v1/b17adfe543354fdd8d189b110617cddd/index.m3u8"
        val starSelect1Url = "http://103.151.60.162:2122/play/a026/index.m3u8?hls"
        val starSelect2Url = "http://103.151.60.162:2122/play/a027/index.m3u8?hls"
        val starSports2Url = "http://103.151.60.162:2122/play/a00v/index.m3u8?hls"
        val tenCricketUrl = "http://103.151.60.162:2122/play/a0fj/index.m3u8?hls"
        val ddSportsHdUrl = "https://cdn.pishow.tv/ott/live/1161/master.m3u8"
        val beinXtraUrl = "https://bein-xtra-bein.amagi.tv/playlist.m3u8"
        val beinXtra1080pUrl = "https://bein-esp-xumo.amagi.tv/playlistR1080p.m3u8"
        val beinXtra720pUrl = "https://bein-esp-xumo.amagi.tv/playlistR720P.m3u8"
        val beinXtraEspUrl = "https://dc1644a9jazgj.cloudfront.net/beIN_Sports_Xtra_Espanol.m3u8"
        val beinSport1Url = "http://ua102.online24.pm:8222/1101/video.m3u8"
        val cbsGolazoUrl = "https://jmp2.uk/plu-63a0e33a45264d000850ed7e.m3u8"
        val espnHdUrl = "https://jmp2.uk/plu-5e9f2c05172a0f0007db4786.m3u8"
        val espnAynaUrl = "https://tvsen5.aynaott.com/espn/index.m3u8"
        val espnBackupUrl = "http://181.78.197.59:8000/play/a07z/index.m3u8"
        val tycSportsUrl = "https://m.mxonlive.xyz/nexusstream/tycsport.m3u8"
        val telemundoUrl = "https://tda-nnaa-drct-pa6dy.fast.nbcuni.com/live/master_2.m3u8"
        val tsn2Url = "https://tvsen7.aynascope.net/tsn2/index.m3u8"
        val tsn3Url = "https://tvsen7.aynascope.net/tsn3/index.m3u8"
        val realMadridTvUrl = "https://rmtv.akamaized.net/hls/live/2043154/rmtv-en-web/bitrate_3.m3u8"
        val foxSports501Url = "https://raw.githubusercontent.com/stablesports711-hue/stable-sports-movie/refs/heads/main/IPTV/FOX501HD.m3u8"
        val foxSports1Url = "http://85.237.89.160:9590/usa-s/FOX-SPORTS-1/index.m3u8"
        val fuboSportsUrl = "https://wurlfubo.global.transmit.live/hls/67f42332d3dd98936978f581/v1/fubotv_fubo_sports_network_1/lg_us/latest/main/hls/playlist.m3u8"
        val nbcSportsUrl = "https://xumo-xumoent-vc-122-sjv70.fast.nbcuni.com/live/master.m3u8"
        val sportitaliaUrl = "https://edge-001.streamup.eu/sportitalia/sihd_abr/playlist.m3u8"
        val fifaPlusUrl = "https://d2w9q46ikgrcwx.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-of5cbk3sav3w5/v1/sysdata_s_p_a_fifa_7/samsungheadend_us/latest/main/hls/playlist.m3u8"

        return listOf(
            SportsChannel(
                id = "ch-tsports",
                name = "T Sports HD",
                category = "Bangla Sports",
                logoText = "TS",
                streams = listOf(
                    StreamSource(name = "T SPORTS 1 FHD", quality = "1080p", url = tSportsUrl, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "T SPORTS ROBITV HD", quality = "1080p", url = tSportsRobiUrl, priorityOrder = 2),
                    StreamSource(name = "WILLOW CRICKET BACKUP", quality = "1080p", url = willowUrl, priorityOrder = 3, isBackup = true)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-gtv",
                name = "Gazi TV (GTV)",
                category = "Bangla Sports",
                logoText = "GTV",
                streams = listOf(
                    StreamSource(name = "GTV / T SPORTS LIVE", quality = "1080p", url = tSportsUrl, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "T SPORTS ROBITV", quality = "1080p", url = tSportsRobiUrl, priorityOrder = 2),
                    StreamSource(name = "TEN CRICKET BACKUP", quality = "HD", url = tenCricketUrl, priorityOrder = 3, isBackup = true)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-asports",
                name = "A Sports HD",
                category = "Pakistan",
                logoText = "AS",
                streams = listOf(
                    StreamSource(name = "A SPORTS HD LIVE", quality = "1080p", url = aSportsUrl, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "A SPORTS TRACK 2", quality = "1080p", url = aSportsTrackUrl, priorityOrder = 2),
                    StreamSource(name = "PTV SPORTS BACKUP", quality = "1080p", url = ptvSportsUrl, priorityOrder = 3, isBackup = true)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-1",
                name = "Star Sports Select 1 HD",
                category = "Football",
                logoText = "SS1",
                streams = listOf(
                    StreamSource(name = "STAR SPORTS SELECT 1 HD", quality = "1080p", url = starSelect1Url, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "STAR SPORTS SELECT 2 HD", quality = "1080p", url = starSelect2Url, priorityOrder = 2),
                    StreamSource(name = "CBS GOLAZO BACKUP", quality = "720p", url = cbsGolazoUrl, priorityOrder = 3, isBackup = true)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-2",
                name = "CBS Sports Golazo",
                category = "Football",
                logoText = "CBS",
                streams = listOf(
                    StreamSource(name = "CBS GOLAZO FHD", quality = "720p", url = cbsGolazoUrl, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "FIFA+ GLOBAL HD", quality = "720p", url = fifaPlusUrl, priorityOrder = 2, isBackup = true)
                )
            ),
            SportsChannel(
                id = "ch-3",
                name = "beIN Sports",
                category = "Football",
                logoText = "beIN",
                streams = listOf(
                    StreamSource(name = "beIN SPORTS XTRA 1080P", quality = "1080p", url = beinXtra1080pUrl, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "beIN SPORTS XTRA 720P", quality = "720p", url = beinXtra720pUrl, priorityOrder = 2),
                    StreamSource(name = "beIN SPORTS XTRA ES", quality = "1080p", url = beinXtraEspUrl, priorityOrder = 3),
                    StreamSource(name = "beIN SPORT 1 HD", quality = "1080p", url = beinSport1Url, priorityOrder = 4),
                    StreamSource(name = "beIN SPORTS XTRA FHD", quality = "1080p", url = beinXtraUrl, priorityOrder = 5, isBackup = true)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-4",
                name = "ESPN HD",
                category = "Football",
                logoText = "ESPN",
                streams = listOf(
                    StreamSource(name = "ESPN AYNA HD", quality = "1080p", url = espnAynaUrl, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "ESPN 1 HD", quality = "720p", url = espnHdUrl, priorityOrder = 2),
                    StreamSource(name = "ESPN BACKUP 1", quality = "720p", url = espnBackupUrl, priorityOrder = 3, isBackup = true)
                )
            ),
            SportsChannel(
                id = "ch-5",
                name = "Sony Sports Ten 1",
                category = "SonyLiv Live",
                logoText = "TEN1",
                streams = listOf(
                    StreamSource(name = "SONY SPORTS 1 GPCDN", quality = "720p", url = sonyTen1GpUrl, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "SONY TEN 1 HD", quality = "HD", url = sonyTen1Url, priorityOrder = 2),
                    StreamSource(name = "SONY TEN 3 HINDI", quality = "HD", url = sonyTen3Url, priorityOrder = 3, isBackup = true)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-6",
                name = "Sony Sports Ten 2",
                category = "SonyLiv Live",
                logoText = "TEN2",
                streams = listOf(
                    StreamSource(name = "SONY SPORTS 2 GPCDN", quality = "720p", url = sonyTen2GpUrl, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "SONY TEN 2 HD", quality = "HD", url = sonyTen2Url, priorityOrder = 2),
                    StreamSource(name = "SONY SPORTS 5 GPCDN", quality = "720p", url = sonyTen5GpUrl, priorityOrder = 3, isBackup = true)
                )
            ),
            SportsChannel(
                id = "ch-7",
                name = "Sony Sports Ten 5",
                category = "SonyLiv Live",
                logoText = "TEN5",
                streams = listOf(
                    StreamSource(name = "SONY SPORTS 5 GPCDN", quality = "720p", url = sonyTen5GpUrl, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "SONY SPORTS 1 GPCDN", quality = "720p", url = sonyTen1GpUrl, priorityOrder = 2, isBackup = true)
                )
            ),
            SportsChannel(
                id = "ch-8",
                name = "DD Sports HD",
                category = "Cricket",
                logoText = "DD",
                streams = listOf(
                    StreamSource(name = "DD SPORTS FHD", quality = "1080p", url = ddSportsHdUrl, isDefault = true, priorityOrder = 1)
                )
            ),
            SportsChannel(
                id = "ch-9",
                name = "Ten Cricket Live",
                category = "Cricket",
                logoText = "TEN",
                streams = listOf(
                    StreamSource(name = "TEN CRICKET HD", quality = "HD", url = tenCricketUrl, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "CRICKET GOLD HD", quality = "1080p", url = cricketGoldAynaUrl, priorityOrder = 2)
                )
            ),
            SportsChannel(
                id = "ch-18",
                name = "Star Sports",
                category = "Cricket",
                logoText = "STAR",
                streams = listOf(
                    StreamSource(name = "STAR SPORTS 2 AYNA HD", quality = "1080p", url = starSports2AynaUrl, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "STAR SPORTS 2 HD", quality = "HD", url = starSports2Url, priorityOrder = 2),
                    StreamSource(name = "STAR SPORTS SELECT 1", quality = "1080p", url = starSelect1Url, priorityOrder = 3)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-19",
                name = "Sportitalia / TyC Sports",
                category = "Football",
                logoText = "TYC",
                streams = listOf(
                    StreamSource(name = "TYC SPORTS ARGENTINA", quality = "1080p", url = tycSportsUrl, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "TELEMUNDO DEPORTES", quality = "1080p", url = telemundoUrl, priorityOrder = 2),
                    StreamSource(name = "SPORTITALIA LIVE", quality = "1080p", url = sportitaliaUrl, priorityOrder = 3),
                    StreamSource(name = "REAL MADRID TV", quality = "1080p", url = realMadridTvUrl, priorityOrder = 4)
                )
            ),
            SportsChannel(
                id = "ch-20",
                name = "PTV / Ten Sports PK",
                category = "Pakistan",
                logoText = "PTV",
                streams = listOf(
                    StreamSource(name = "PTV SPORTS FLUSSONIC HD", quality = "1080p", url = ptvSportsUrl, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "A SPORTS HD PK", quality = "1080p", url = aSportsUrl, priorityOrder = 2),
                    StreamSource(name = "TEN CRICKET PK", quality = "HD", url = tenCricketUrl, priorityOrder = 3, isBackup = true)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-21",
                name = "TSN / FOX Sports HD",
                category = "Football",
                logoText = "TSN",
                streams = listOf(
                    StreamSource(name = "TSN 2 HD", quality = "1080p", url = tsn2Url, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "TSN 3 HD", quality = "1080p", url = tsn3Url, priorityOrder = 2),
                    StreamSource(name = "FOX SPORTS 501 HD", quality = "1080p", url = foxSports501Url, priorityOrder = 3),
                    StreamSource(name = "FOX SPORTS 1 HD", quality = "720p", url = foxSports1Url, priorityOrder = 4),
                    StreamSource(name = "FUBO SPORTS FHD", quality = "1080p", url = fuboSportsUrl, priorityOrder = 5),
                    StreamSource(name = "NBC SPORTS FHD", quality = "1080p", url = nbcSportsUrl, priorityOrder = 6, isBackup = true)
                )
            ),
            SportsChannel(
                id = "ch-24",
                name = "Willow Cricket HD",
                category = "Cricket",
                logoText = "WILLOW",
                streams = listOf(
                    StreamSource(name = "WILLOW TV AYNA HD", quality = "1080p", url = willowAynaUrl, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "WILLOW SPORTS 540P", quality = "540p", url = willowHerokuUrl, priorityOrder = 2),
                    StreamSource(name = "WILLOW SPORTS FHD", quality = "1080p", url = willowUrl, priorityOrder = 3),
                    StreamSource(name = "CRICKET GOLD HD", quality = "1080p", url = cricketGoldAynaUrl, priorityOrder = 4)
                ),
                isFavorite = true
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
