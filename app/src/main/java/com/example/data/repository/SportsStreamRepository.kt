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
import com.example.data.sofascore.SofaScoreMetadataService
import com.example.data.util.TeamFlagResolver
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
            crexService.getDiagnostics(cfg.cricketSyncEnabled)
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
                            enabledFootballLeagues = parsedLeagues
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
                    }
                } else {
                    AppConfig.DEFAULT_ENABLED_FOOTBALL_LEAGUES
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
                    enabledFootballLeagues = enabledLeagues
                )
            } catch (e: Exception) {
                _config.value = AppConfig()
            }
        }

        // Start with zero hardcoded demo matches; live provider feeds populate footballProviderMatches & _crexDashboardState
        footballProviderMatches.clear()
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
                .putString("saved_channels_v3", channelsJson)
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

        val allProviderMatches = rawFootball + rawCricket
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
        val feedResult = espnService.fetchScoreboardMatches(
            enabledLeagues = cfg.enabledFootballLeagues,
            forceRefresh = force
        )
        synchronized(footballProviderMatches) {
            footballProviderMatches.clear()
            footballProviderMatches.addAll(feedResult.matches)
        }
        rebuildCombinedMatches()
        _matches.value.filter { it.sport == SportType.FOOTBALL }
    }

    /**
     * Refreshes all Cricket categories (Live, Upcoming, Finished) and Football matches concurrently,
     * with cross-feed deduplication by provider `matchId`.
     */
    suspend fun refreshLiveMatches(force: Boolean = false): CrexCategoryFeedState = withContext(Dispatchers.IO) {
        val cfg = _config.value
        val current = _crexDashboardState.value.liveState
        if (current.matches.isEmpty() && cfg.cricketSyncEnabled) {
            _crexDashboardState.value = _crexDashboardState.value.copy(
                liveState = current.copy(isLoading = true, errorMessage = null)
            )
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
        rebuildCombinedMatches()
        _crexDashboardState.value.liveState
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
            val v1 = TeamFlagResolver.resolveForMatch(d.team1Name, d.team1Key, isNationalTeam = true)
            val v2 = TeamFlagResolver.resolveForMatch(d.team2Name, d.team2Key, isNationalTeam = true)
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
            saveData()
        }

        parsedChannels.size
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
                    StreamSource(name = "T SPORTS 1 FHD", quality = "FHD", url = streamHls1, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "T SPORTS 2 HD", quality = "HD", url = streamHls2, priorityOrder = 2, isBackup = true)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-gtv",
                name = "Gazi TV (GTV)",
                category = "Bangla Sports",
                logoText = "GTV",
                streams = listOf(
                    StreamSource(name = "GTV LIVE FHD", quality = "FHD", url = streamHls2, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "GTV BACKUP HD", quality = "HD", url = streamHls3, priorityOrder = 2, isBackup = true)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-1",
                name = "TNT Sports",
                category = "Football",
                logoText = "TNT",
                streams = listOf(
                    StreamSource(name = "TNT SPORTS 1 HD", quality = "FHD", url = streamHls1, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "TNT SPORTS 2 HD", quality = "HD", url = streamHls2, priorityOrder = 2),
                    StreamSource(name = "TNT BACKUP SD", quality = "SD", url = streamHls3, priorityOrder = 3, isBackup = true)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-2",
                name = "TSN",
                category = "Sports",
                logoText = "TSN",
                streams = listOf(
                    StreamSource(name = "TSN 1 FHD", quality = "FHD", url = streamHls2, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "TSN 2 HD", quality = "HD", url = streamHls1, priorityOrder = 2)
                )
            ),
            SportsChannel(
                id = "ch-3",
                name = "BeIN Sports",
                category = "Football",
                logoText = "beIN",
                streams = listOf(
                    StreamSource(name = "BeIN SPORTS 1 PREMIUM", quality = "FHD", url = streamHls3, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "BeIN SPORTS 2 ENGLISH", quality = "FHD", url = streamHls1, priorityOrder = 2),
                    StreamSource(name = "BeIN SERVER 3", quality = "HD", url = streamHls2, priorityOrder = 3, isBackup = true)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-4",
                name = "FOX Sports",
                category = "Sports",
                logoText = "FOX",
                streams = listOf(
                    StreamSource(name = "FS1 HD", quality = "FHD", url = streamHls1, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "FS2 HD", quality = "HD", url = streamHls2, priorityOrder = 2)
                )
            ),
            SportsChannel(
                id = "ch-5",
                name = "Sony Sports Ten 1",
                category = "SonyLiv Live",
                logoText = "TEN1",
                streams = listOf(StreamSource(name = "SONY TEN 1 FHD", quality = "FHD", url = streamHls2, isDefault = true, priorityOrder = 1))
            ),
            SportsChannel(
                id = "ch-6",
                name = "Sony Sports Ten 2",
                category = "SonyLiv Live",
                logoText = "TEN2",
                streams = listOf(StreamSource(name = "SONY TEN 2 FHD", quality = "FHD", url = streamHls3, isDefault = true, priorityOrder = 1))
            ),
            SportsChannel(
                id = "ch-8",
                name = "ICC TV Official",
                category = "Cricket",
                logoText = "ICC",
                streams = listOf(StreamSource(name = "ICC TV LIVE FHD", quality = "FHD", url = streamHls2, isDefault = true, priorityOrder = 1))
            ),
            SportsChannel(
                id = "ch-9",
                name = "CricHD Live 1",
                category = "Cricket",
                logoText = "CRIC",
                streams = listOf(StreamSource(name = "CRICHD SERVER 1", quality = "FHD", url = streamHls1, isDefault = true, priorityOrder = 1))
            ),
            SportsChannel(
                id = "ch-18",
                name = "Star Sports",
                category = "Cricket",
                logoText = "STAR",
                streams = listOf(
                    StreamSource(name = "STAR SPORTS 1 HD", quality = "FHD", url = streamHls1, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "STAR SPORTS SELECT 1", quality = "HD", url = streamHls2, priorityOrder = 2)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-19",
                name = "FanCode",
                category = "FanCode IND",
                logoText = "FC",
                streams = listOf(
                    StreamSource(name = "FANCODE LIVE 1", quality = "FHD", url = streamHls3, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "FANCODE LIVE 2", quality = "HD", url = streamHls1, priorityOrder = 2)
                )
            ),
            SportsChannel(
                id = "ch-20",
                name = "PTV Sports",
                category = "Pakistan",
                logoText = "PTV",
                streams = listOf(
                    StreamSource(name = "PTV SPORTS HD LIVE", quality = "FHD", url = streamHls2, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "PTV SPORTS SD", quality = "SD", url = streamHls3, priorityOrder = 2, isBackup = true)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-21",
                name = "A Sports",
                category = "Pakistan",
                logoText = "A",
                streams = listOf(StreamSource(name = "A SPORTS HD", quality = "FHD", url = streamHls1, isDefault = true, priorityOrder = 1))
            ),
            SportsChannel(
                id = "ch-23",
                name = "Tapmad PK",
                category = "Tapmad PK",
                logoText = "tapmad",
                streams = listOf(
                    StreamSource(name = "TAPMAD LIVE 1", quality = "FHD", url = streamHls3, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "TAPMAD LIVE 2", quality = "HD", url = streamHls1, priorityOrder = 2)
                )
            ),
            SportsChannel(
                id = "ch-24",
                name = "Willow Cricket",
                category = "Cricket",
                logoText = "WILLOW",
                streams = listOf(
                    StreamSource(name = "WILLOW HD 1", quality = "FHD", url = streamHls1, isDefault = true, priorityOrder = 1),
                    StreamSource(name = "WILLOW HD 2", quality = "HD", url = streamHls2, priorityOrder = 2)
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
