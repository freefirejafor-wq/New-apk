package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.cloud.CloudCatalogItem
import com.example.data.cloud.CloudSyncRepository
import com.example.data.crex.CrexCategoryFeedState
import com.example.data.crex.CrexCricketService
import com.example.data.crex.CrexDashboardState
import com.example.data.crex.CrexMatchDetailState
import com.example.data.crex.FootballProviderState
import com.example.data.model.*
import com.example.data.util.TeamFlagResolver
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class SportsStreamRepository(
    private val context: Context,
    val crexService: CrexCricketService = CrexCricketService(),
    private val providedCloudSyncRepository: CloudSyncRepository? = null
) {
    companion object {
        private val LEGACY_DEMO_MATCH_PREFIXES = listOf(
            "fb-ucl-",
            "fb-epl-",
            "fb-laliga-",
            "fb-seriea-",
            "fb-bundesliga-",
            "fb-ligue1-",
            "fb-wcq-",
            "fb-euro-",
            "demo-",
            "sample-"
        )

        fun isLegacyDemoMatchId(id: String): Boolean {
            val lower = id.lowercase().trim()
            return LEGACY_DEMO_MATCH_PREFIXES.any { lower.startsWith(it) }
        }
    }

    val cloudSyncRepository: CloudSyncRepository by lazy {
        providedCloudSyncRepository ?: CloudSyncRepository(context)
    }

    private fun isFirebaseAuthenticated(): Boolean {
        return try {
            Firebase.auth.currentUser != null
        } catch (_: Exception) {
            false
        }
    }

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
    private val favoriteMatchMeta = mutableMapOf<String, LiveMatch>()
    private val reminderMatchIds = mutableSetOf<String>()
    private val favoriteChannelIds = mutableSetOf<String>()
    private val footballMatches = mutableListOf<LiveMatch>()
    private val apiHighlights = mutableListOf<HighlightItem>()
    private val customHighlights = mutableListOf<HighlightItem>()
    private val cloudMatchOverrides = mutableMapOf<String, LiveMatch>()
    private val sessionAddedMatches = mutableMapOf<String, LiveMatch>()
    private val deletedMatchIds = mutableSetOf<String>()
    private val dismissedUnmatchedIds = mutableSetOf<String>()
    private var lastMultiSportFetchMs: Long = 0L

    private val _crexDashboardState = MutableStateFlow(CrexDashboardState())
    val crexDashboardState: StateFlow<CrexDashboardState> = _crexDashboardState.asStateFlow()

    private val _matches = MutableStateFlow<List<LiveMatch>>(emptyList())
    val matches: StateFlow<List<LiveMatch>> = _matches.asStateFlow()

    private val _unmatchedAdminAssociations = MutableStateFlow<List<UnmatchedStreamAssociation>>(emptyList())
    val unmatchedAdminAssociations: StateFlow<List<UnmatchedStreamAssociation>> = _unmatchedAdminAssociations.asStateFlow()

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

    init {
        loadData()
    }

    /**
     * Starts real-time Firestore snapshot listeners for public Admin catalog items
     * (`cloud_items`) and per-user favorites (`users/{uid}/favorites`) once authenticated.
     */
    fun startCloudSyncForUser(userId: String) {
        cloudCatalogJob?.cancel()
        cloudFavoritesJob?.cancel()

        cloudCatalogJob = repoScope.launch {
            try {
                cloudSyncRepository.observePublicCatalogItems().collect { items ->
                    applyCloudCatalogItems(items)
                }
            } catch (e: Exception) {
                _cloudSyncStatus.value = "Cloud Sync: ${e.localizedMessage ?: "Offline"}"
            }
        }

        cloudFavoritesJob = repoScope.launch {
            try {
                cloudSyncRepository.observeUserFavorites(userId).collect { favs ->
                    val matchFavs = favs.filter { it.itemType == "match" }.map { it.itemId }.toSet()
                    val channelFavs = favs.filter { it.itemType == "channel" }.map { it.itemId }.toSet()
                    if (matchFavs.isNotEmpty() || channelFavs.isNotEmpty()) {
                        favoriteMatchIds.addAll(matchFavs)
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
        cloudMatchOverrides.clear()
        deletedMatchIds.clear()
        if (items.isEmpty()) {
            rebuildCombinedMatches()
            return
        }
        var channelsChanged = false
        val updatedChannels = _channels.value.toMutableList()
        var highlightsChanged = false
        val updatedHighlights = _highlights.value.toMutableList()

        for (item in items) {
            when (item.itemType) {
                "config" -> {
                    try {
                        val obj = JSONObject(item.payloadJson)
                        val cur = _config.value
                        val leaguesArr = obj.optJSONArray("enabledFootballLeagues")
                        val parsedLeagues = if (leaguesArr != null && leaguesArr.length() > 0) {
                            buildList {
                                for (i in 0 until leaguesArr.length()) {
                                    val code = leaguesArr.optString(i).trim()
                                    if (code in AppConfig.VERIFIED_FOOTBALL_LEAGUE_LABELS.keys) add(code)
                                }
                            }.ifEmpty { cur.enabledFootballLeagues }
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
                            syncLiveEnabled = obj.optBoolean("syncLiveEnabled", cur.syncLiveEnabled),
                            syncUpcomingEnabled = obj.optBoolean("syncUpcomingEnabled", cur.syncUpcomingEnabled),
                            syncFinishedEnabled = obj.optBoolean("syncFinishedEnabled", cur.syncFinishedEnabled),
                            syncFootballEnabled = obj.optBoolean("syncFootballEnabled", cur.syncFootballEnabled),
                            autoRefreshIntervalSeconds = obj.optInt("autoRefreshIntervalSeconds", cur.autoRefreshIntervalSeconds).coerceIn(15, 300),
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
                            if (m.id.isNotBlank() && !isLegacyDemoMatchId(m.id)) {
                                val sanitized = if (m.sport == SportType.FOOTBALL) {
                                    CrexCricketService.revalidateFootballMatchStatus(
                                        m.copy(
                                            status = if (m.status == MatchStatus.LIVE) MatchStatus.UPCOMING else m.status,
                                            providerStatusLabel = if (m.providerStatusLabel.equals("Live", ignoreCase = true)) "Unknown" else m.providerStatusLabel
                                        )
                                    )
                                } else {
                                    m
                                }
                                cloudMatchOverrides[m.id] = sanitized
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
                "highlight" -> {
                    try {
                        val obj = JSONObject(item.payloadJson)
                        if (obj.optBoolean("deleted", false)) {
                            val delId = obj.optString("id")
                            updatedHighlights.removeAll { it.id == delId }
                            highlightsChanged = true
                        } else {
                            val h = deserializeSingleHighlight(obj)
                            val idx = updatedHighlights.indexOfFirst { it.id == h.id }
                            if (idx >= 0) {
                                updatedHighlights[idx] = h
                            } else {
                                updatedHighlights.add(0, h)
                            }
                            highlightsChanged = true
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }

        if (channelsChanged) {
            _channels.value = updatedChannels
        }
        if (highlightsChanged) {
            _highlights.value = updatedHighlights
        }
        rebuildCombinedMatches()
        _cloudSyncStatus.value = "Live Cloud Synced (${items.size} server items active)"
    }

    private fun loadData() {
        // Purge any stale legacy demo match caches from older versions
        prefs.edit()
            .remove("cached_demo_matches")
            .remove("default_football_cache")
            .apply()

        val savedFavs = prefs.getStringSet("favorite_match_ids", emptySet()).orEmpty()
        favoriteMatchIds.clear()
        favoriteMatchIds.addAll(savedFavs)

        val savedFavMetaJson = prefs.getString("favorite_match_meta_v1", null)
        favoriteMatchMeta.clear()
        if (!savedFavMetaJson.isNullOrBlank()) {
            try {
                val arr = JSONArray(savedFavMetaJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val m = deserializeSingleMatch(obj)
                    if (m.id.isNotBlank()) {
                        favoriteMatchMeta[m.id] = m
                    }
                }
            } catch (_: Exception) {
            }
        }

        val savedReminders = prefs.getStringSet("reminder_match_ids", emptySet()).orEmpty()
        reminderMatchIds.clear()
        reminderMatchIds.addAll(savedReminders)

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
                val rawAnnounce = obj.optString("announcement", "Welcome to Sports Live! Enjoy real-time match updates & multi-server HD streams.")
                val resolvedAnnounce = rawAnnounce
                    .replace("Football Live TV", "Sports Live", ignoreCase = true)
                    .replace("SportzX Live TV", "Sports Live", ignoreCase = true)
                    .replace("NovaStream TV", "Sports Live", ignoreCase = true)

                val leaguesArr = obj.optJSONArray("enabledFootballLeagues")
                val parsedLeagues = if (leaguesArr != null && leaguesArr.length() > 0) {
                    buildList {
                        for (i in 0 until leaguesArr.length()) {
                            val code = leaguesArr.optString(i).trim()
                            if (code in AppConfig.VERIFIED_FOOTBALL_LEAGUE_LABELS.keys) add(code)
                        }
                    }.ifEmpty { AppConfig.DEFAULT_FOOTBALL_LEAGUE_ALLOWLIST }
                } else {
                    AppConfig.DEFAULT_FOOTBALL_LEAGUE_ALLOWLIST
                }

                val savedRemoteUrl = obj.optString("remoteFeedUrl", "https://api.goscorer.com/api/v3/getLiveMatches")
                val cleanRemoteUrl = if (savedRemoteUrl.contains("githubusercontent.com", ignoreCase = true) ||
                    savedRemoteUrl.contains("pastebin.com", ignoreCase = true)
                ) {
                    "https://api.goscorer.com/api/v3/getLiveMatches"
                } else {
                    savedRemoteUrl
                }

                _config.value = AppConfig(
                    appTitle = resolvedTitle,
                    version = obj.optString("version", "9.0"),
                    announcement = resolvedAnnounce,
                    telegramLink = obj.optString("telegramLink", "https://t.me/sports_live_streams"),
                    adminPin = obj.optString("adminPin", "1234"),
                    forceLowQuality = obj.optBoolean("forceLowQuality", false),
                    isHardwareAccelerationEnabled = obj.optBoolean("isHardwareAccelerationEnabled", true),
                    remoteFeedUrl = cleanRemoteUrl,
                    autoSyncOnLaunch = obj.optBoolean("autoSyncOnLaunch", true),
                    syncLiveEnabled = obj.optBoolean("syncLiveEnabled", true),
                    syncUpcomingEnabled = obj.optBoolean("syncUpcomingEnabled", true),
                    syncFinishedEnabled = obj.optBoolean("syncFinishedEnabled", true),
                    syncFootballEnabled = obj.optBoolean("syncFootballEnabled", true),
                    autoRefreshIntervalSeconds = obj.optInt("autoRefreshIntervalSeconds", 30).coerceIn(15, 300),
                    enabledFootballLeagues = parsedLeagues
                )
            } catch (e: Exception) {
                _config.value = AppConfig()
            }
        }

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

        val savedOverridesJson = prefs.getString("local_match_overrides_v1", null)
        if (!savedOverridesJson.isNullOrBlank()) {
            try {
                val arr = JSONArray(savedOverridesJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val m = deserializeSingleMatch(obj)
                    if (m.id.isNotBlank() && !isLegacyDemoMatchId(m.id)) {
                        val sanitized = if (m.sport == SportType.FOOTBALL) {
                            CrexCricketService.revalidateFootballMatchStatus(
                                m.copy(
                                    status = if (m.status == MatchStatus.LIVE) MatchStatus.UPCOMING else m.status,
                                    providerStatusLabel = if (m.providerStatusLabel.equals("Live", ignoreCase = true)) "Unknown" else m.providerStatusLabel
                                )
                            )
                        } else {
                            m
                        }
                        cloudMatchOverrides[m.id] = sanitized
                    }
                }
            } catch (_: Exception) {
            }
        }

        // Do NOT populate hardcoded/demo football matches into active list!
        footballMatches.clear()
        rebuildCombinedMatches()

        _categories.value = defaultCategories()
        customHighlights.clear()
        customHighlights.addAll(defaultHighlights())
        rebuildDynamicHighlights()
    }

    private fun saveData() {
        try {
            val channelsJson = serializeChannels(_channels.value)
            val overridesArr = JSONArray()
            for (m in cloudMatchOverrides.values) {
                overridesArr.put(serializeSingleMatch(m))
            }
            val favMetaArr = JSONArray()
            for (m in favoriteMatchMeta.values) {
                if (m.id in favoriteMatchIds) {
                    favMetaArr.put(serializeSingleMatch(m))
                }
            }
            val leaguesArr = JSONArray()
            _config.value.enabledFootballLeagues.forEach { leaguesArr.put(it) }

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
                put("syncLiveEnabled", _config.value.syncLiveEnabled)
                put("syncUpcomingEnabled", _config.value.syncUpcomingEnabled)
                put("syncFinishedEnabled", _config.value.syncFinishedEnabled)
                put("syncFootballEnabled", _config.value.syncFootballEnabled)
                put("autoRefreshIntervalSeconds", _config.value.autoRefreshIntervalSeconds)
                put("enabledFootballLeagues", leaguesArr)
            }
            prefs.edit()
                .putStringSet("favorite_match_ids", favoriteMatchIds.toSet())
                .putString("favorite_match_meta_v1", favMetaArr.toString())
                .putStringSet("reminder_match_ids", reminderMatchIds.toSet())
                .putString("saved_channels_v3", channelsJson)
                .putString("local_match_overrides_v1", overridesArr.toString())
                .putString("app_config", configObj.toString())
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Safely remaps any existing favourites and admin stream associations (`cloudMatchOverrides`)
     * whose old match ID no longer matches the new provider match ID, by comparing normalized
     * `(team1, team2, sport, match date in Asia/Dhaka)`.
     *
     * Any old stream/favourite association that cannot be confidently matched is preserved
     * and surfaced in `_unmatchedAdminAssociations` for the Admin Panel instead of being silently deleted.
     */
    internal fun remapLegacyFavoritesAndStreamsToProviderMatches(providerMatches: List<LiveMatch>) {
        if (providerMatches.isEmpty()) return

        val providerIds = providerMatches.map { it.id }.toSet()
        val unmatchedList = mutableListOf<UnmatchedStreamAssociation>()

        fun normalizeTeamKey(name: String): String {
            return name.lowercase()
                .replace("women", "")
                .replace("u19", "")
                .replace("fc", "")
                .replace("cf", "")
                .replace(Regex("[^a-z0-9]"), "")
                .trim()
        }

        fun findMatchingProviderFixture(oldMatch: LiveMatch): LiveMatch? {
            val oldT1 = normalizeTeamKey(oldMatch.team1Name)
            val oldT2 = normalizeTeamKey(oldMatch.team2Name)
            val oldC1 = oldMatch.team1Code.lowercase().trim()
            val oldC2 = oldMatch.team2Code.lowercase().trim()
            val oldDateKey = CrexCricketService.getDhakaDateKey(oldMatch.startTimeEpochMs)

            return providerMatches.firstOrNull { candidate ->
                if (candidate.sport != oldMatch.sport) return@firstOrNull false
                val cT1 = normalizeTeamKey(candidate.team1Name)
                val cT2 = normalizeTeamKey(candidate.team2Name)
                val cC1 = candidate.team1Code.lowercase().trim()
                val cC2 = candidate.team2Code.lowercase().trim()

                val teamsMatchDirect = (oldT1.isNotBlank() && oldT1 == cT1 && oldT2.isNotBlank() && oldT2 == cT2) ||
                    (oldC1.isNotBlank() && oldC1 == cC1 && oldC2.isNotBlank() && oldC2 == cC2)
                val teamsMatchSwapped = (oldT1.isNotBlank() && oldT1 == cT2 && oldT2.isNotBlank() && oldT2 == cT1) ||
                    (oldC1.isNotBlank() && oldC1 == cC2 && oldC2.isNotBlank() && oldC2 == cC1)

                if (!teamsMatchDirect && !teamsMatchSwapped) return@firstOrNull false

                val candDateKey = CrexCricketService.getDhakaDateKey(candidate.startTimeEpochMs)
                oldDateKey.isBlank() || candDateKey.isBlank() || oldDateKey == candDateKey
            }
        }

        // 1. Remap Admin Match Stream Overrides
        var changed = false
        val overridesSnapshot = cloudMatchOverrides.toMap()
        for ((oldId, overrideMatch) in overridesSnapshot) {
            if (oldId in providerIds) continue
            val matchedFixture = findMatchingProviderFixture(overrideMatch)
            if (matchedFixture != null) {
                // Safe remap to new provider ID
                val remapped = matchedFixture.copy(
                    streams = (overrideMatch.streams + matchedFixture.streams).distinctBy { "${it.name}_${it.url}" }
                )
                cloudMatchOverrides.remove(oldId)
                cloudMatchOverrides[matchedFixture.id] = remapped
                if (oldId in favoriteMatchIds) {
                    favoriteMatchIds.remove(oldId)
                    favoriteMatchIds.add(matchedFixture.id)
                    favoriteMatchMeta[matchedFixture.id] = remapped
                }
                changed = true
            } else if (oldId !in dismissedUnmatchedIds) {
                unmatchedList.add(
                    UnmatchedStreamAssociation(
                        oldMatchId = oldId,
                        team1Name = overrideMatch.team1Name,
                        team2Name = overrideMatch.team2Name,
                        tournament = overrideMatch.tournament,
                        sport = overrideMatch.sport,
                        matchDateText = overrideMatch.matchTime,
                        streams = overrideMatch.streams,
                        wasFavorite = oldId in favoriteMatchIds,
                        reason = "Provider match ID changed or fixture not in active 3-day feed; preserved for admin review"
                    )
                )
            }
        }

        // 2. Remap Favourite Match IDs using saved favoriteMatchMeta
        val favIdsSnapshot = favoriteMatchIds.toSet()
        for (favId in favIdsSnapshot) {
            if (favId in providerIds) continue
            val meta = favoriteMatchMeta[favId] ?: cloudMatchOverrides[favId]
            if (meta != null) {
                val matchedFixture = findMatchingProviderFixture(meta)
                if (matchedFixture != null) {
                    favoriteMatchIds.remove(favId)
                    favoriteMatchIds.add(matchedFixture.id)
                    favoriteMatchMeta[matchedFixture.id] = matchedFixture
                    changed = true
                } else if (favId !in dismissedUnmatchedIds && unmatchedList.none { it.oldMatchId == favId }) {
                    unmatchedList.add(
                        UnmatchedStreamAssociation(
                            oldMatchId = favId,
                            team1Name = meta.team1Name,
                            team2Name = meta.team2Name,
                            tournament = meta.tournament,
                            sport = meta.sport,
                            matchDateText = meta.matchTime,
                            streams = meta.streams,
                            wasFavorite = true,
                            reason = "Saved favourite match ID could not be automatically matched to new provider feed"
                        )
                    )
                }
            }
        }

        _unmatchedAdminAssociations.value = unmatchedList
        if (changed) {
            saveData()
        }
    }

    /**
     * Allows the Admin to manually reassign an unmatched stream/favourite association to an active provider match.
     */
    fun reassignUnmatchedAssociation(oldMatchId: String, targetNewMatchId: String): Boolean {
        val targetMatch = _matches.value.find { it.id == targetNewMatchId } ?: return false
        val oldOverride = cloudMatchOverrides.remove(oldMatchId)
        val oldFavMeta = favoriteMatchMeta.remove(oldMatchId)
        val streamsToKeep = oldOverride?.streams ?: oldFavMeta?.streams.orEmpty()

        if (streamsToKeep.isNotEmpty()) {
            val existingCustom = cloudMatchOverrides[targetNewMatchId]?.streams.orEmpty()
            val mergedStreams = (streamsToKeep + existingCustom + targetMatch.streams)
                .distinctBy { "${it.name}_${it.url}" }
            cloudMatchOverrides[targetNewMatchId] = targetMatch.copy(streams = mergedStreams)
        }
        if (oldMatchId in favoriteMatchIds) {
            favoriteMatchIds.remove(oldMatchId)
            favoriteMatchIds.add(targetNewMatchId)
            favoriteMatchMeta[targetNewMatchId] = targetMatch
        }
        dismissedUnmatchedIds.add(oldMatchId)
        _unmatchedAdminAssociations.value = _unmatchedAdminAssociations.value.filterNot { it.oldMatchId == oldMatchId }
        saveData()
        rebuildCombinedMatches()
        return true
    }

    fun dismissUnmatchedAssociation(oldMatchId: String) {
        dismissedUnmatchedIds.add(oldMatchId)
        _unmatchedAdminAssociations.value = _unmatchedAdminAssociations.value.filterNot { it.oldMatchId == oldMatchId }
    }

    /**
     * Smart Auto-Stream Linker:
     * Automatically maps every CREX or ESPN match to the most relevant broadcast sports channels,
     * while placing Admin/User custom stream servers at Priority #1 and filtering out disabled or unauthorized streams.
     */
    private fun resolveSmartStreamsForMatch(
        match: LiveMatch,
        customOverrideStreams: List<StreamSource>?
    ): List<StreamSource> {
        val result = mutableListOf<StreamSource>()
        val allChannels = _channels.value

        // 1. Priority #1: Custom Admin or User-attached servers for this specific match
        if (!customOverrideStreams.isNullOrEmpty()) {
            result.addAll(customOverrideStreams.filter { it.isEnabled && it.isAuthorized })
        }

        val combinedText = "${match.tournament} ${match.team1Name} ${match.team2Name} ${match.team1Code} ${match.team2Code}".lowercase()

        fun addChannelStreamsByKeyword(vararg keywords: String) {
            for (kw in keywords) {
                val matchedChannels = allChannels.filter { ch ->
                    ch.name.contains(kw, ignoreCase = true) || ch.category.contains(kw, ignoreCase = true)
                }
                for (ch in matchedChannels) {
                    val validChannelStreams = ch.streams.filter { it.isEnabled && it.isAuthorized }
                    val primary = validChannelStreams.firstOrNull() ?: continue
                    val streamLabel = if (primary.name.contains(ch.name, ignoreCase = true)) {
                        primary.name
                    } else {
                        "${ch.name} (${primary.quality})"
                    }
                    if (result.none { it.name.equals(streamLabel, ignoreCase = true) }) {
                        result.add(
                            primary.copy(
                                id = "${match.id}_${ch.id}",
                                name = streamLabel,
                                isDefault = result.isEmpty(),
                                displayOrder = result.size
                            )
                        )
                    }
                }
            }
        }

        // 2. Priority #2: Match direct team/tournament names against any imported M3U or custom channels
        val t1Short = match.team1Name.takeIf { it.length >= 4 && !it.startsWith("Team ") }
        val t2Short = match.team2Name.takeIf { it.length >= 4 && !it.startsWith("Team ") }
        if (t1Short != null) addChannelStreamsByKeyword(t1Short)
        if (t2Short != null) addChannelStreamsByKeyword(t2Short)

        // 3. Priority #3: Sport & Country Broadcast Rights Auto-Mapping
        when (match.sport) {
            SportType.CRICKET -> {
                val isBangladesh = combinedText.contains("bangladesh") || combinedText.contains("ban")
                val isPakistan = combinedText.contains("pakistan") || combinedText.contains("pak")
                val isIndia = combinedText.contains("india") || combinedText.contains("ind")

                if (isBangladesh) {
                    addChannelStreamsByKeyword("T Sports", "Gazi TV", "GTV", "Willow", "Star Sports", "ICC", "CricHD")
                } else if (isPakistan) {
                    addChannelStreamsByKeyword("PTV Sports", "A Sports", "Tapmad", "Willow", "T Sports", "CricHD")
                } else if (isIndia) {
                    addChannelStreamsByKeyword("Star Sports", "FanCode", "Sony Sports Ten 1", "Willow", "T Sports", "ICC")
                } else {
                    addChannelStreamsByKeyword("Willow", "T Sports", "Star Sports", "PTV Sports", "ICC", "CricHD", "Sony Sports Ten 1")
                }
            }
            SportType.FOOTBALL -> {
                addChannelStreamsByKeyword("BeIN", "TNT Sports", "Sony Sports Ten 2", "TSN", "FOX Sports", "Football")
            }
            SportType.BOXING, SportType.RACING -> {
                addChannelStreamsByKeyword("TNT Sports", "FOX Sports", "TSN", "Sony Sports Ten 1", "BeIN")
            }
            else -> {
                addChannelStreamsByKeyword("T Sports", "TNT Sports", "BeIN", "Star Sports", "Willow")
            }
        }

        // 4. Fallback if still fewer than 3 servers
        if (result.size < 3) {
            for (s in match.streams.filter { it.isEnabled && it.isAuthorized }) {
                if (result.none { it.name.equals(s.name, ignoreCase = true) }) {
                    result.add(s.copy(isDefault = result.isEmpty(), displayOrder = result.size))
                }
            }
        }

        // Sort so primary streams come before backup streams, then by displayOrder
        val sorted = result
            .filter { it.isEnabled && it.isAuthorized && it.url.isNotBlank() }
            .sortedWith(compareBy<StreamSource> { it.isBackup }.thenBy { it.displayOrder })
            .take(6)

        return sorted.mapIndexed { index, streamSource ->
            streamSource.copy(isDefault = index == 0)
        }
    }

    private fun applyFavoritesAndOverrides(list: List<LiveMatch>): List<LiveMatch> {
        return list.map { m ->
            val cloudOverride = cloudMatchOverrides[m.id]
            // Preserve real provider status and metadata; only merge custom streams from cloudOverride
            val merged = if (m.sport == SportType.FOOTBALL) {
                CrexCricketService.revalidateFootballMatchStatus(m)
            } else {
                m
            }
            val smartStreams = resolveSmartStreamsForMatch(merged, cloudOverride?.streams)
            val isClubFb = merged.sport == SportType.FOOTBALL &&
                !CrexCricketService.isInternationalFootballLeague("", merged.tournament)
            val v1 = TeamFlagResolver.resolve(
                teamName = merged.team1Name,
                teamCode = merged.team1Code,
                existingFlag = merged.team1FlagUrl,
                isClubFootball = isClubFb
            )
            val v2 = TeamFlagResolver.resolve(
                teamName = merged.team2Name,
                teamCode = merged.team2Code,
                existingFlag = merged.team2FlagUrl,
                isClubFootball = isClubFb
            )
            val isFav = merged.id in favoriteMatchIds
            val finalMatch = merged.copy(
                team1Code = v1.code,
                team1FlagUrl = v1.flagUrl,
                team2Code = v2.code,
                team2FlagUrl = v2.flagUrl,
                streams = smartStreams,
                isFavorite = isFav,
                isReminderSet = merged.id in reminderMatchIds
            )
            if (isFav) {
                favoriteMatchMeta[finalMatch.id] = finalMatch
            }
            finalMatch
        }
    }

    private fun rebuildDynamicHighlights() {
        val sampleStreams = listOf(
            "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8",
            "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        )
        val crexFinishedHighlights = _crexDashboardState.value.finishedState.matches.take(8).mapIndexed { idx, m ->
            HighlightItem(
                id = "crex-hl-${m.id}",
                title = "${m.team1Name} vs ${m.team2Name} • ${m.timeOrScore.ifBlank { "Full Match Highlights" }}",
                tournament = "CRICKET • ${m.tournament.uppercase()}",
                duration = "11:${15 + (idx * 5) % 40}",
                thumbnailUrl = m.team1FlagUrl,
                videoUrl = m.streams.firstOrNull()?.url ?: sampleStreams[idx % sampleStreams.size],
                sport = SportType.CRICKET
            )
        }
        val combined = buildList {
            addAll(apiHighlights)
            addAll(crexFinishedHighlights)
            addAll(customHighlights)
        }.distinctBy { it.id }
        _highlights.value = combined
    }

    /**
     * Strictly filters CREX matches to verified International Cricket within the `Asia/Dhaka`
     * Yesterday / Today / Tomorrow window. Never falls back to domestic matches.
     */
    private fun filterInternationalCricket(matches: List<LiveMatch>): List<LiveMatch> {
        return matches.filter {
            CrexCricketService.isInternationalCricketMatch(it) &&
                CrexCricketService.isWithinDhakaThreeDayWindow(it)
        }
    }

    private fun rebuildCombinedMatches() {
        val state = _crexDashboardState.value
        val cfg = _config.value

        val rawCricketLive = if (cfg.syncLiveEnabled) {
            filterInternationalCricket(state.liveState.matches.filter { it.id !in deletedMatchIds })
        } else {
            emptyList()
        }
        val rawCricketFinished = if (cfg.syncFinishedEnabled) {
            filterInternationalCricket(state.finishedState.matches.filter { it.id !in deletedMatchIds })
        } else {
            emptyList()
        }
        val rawCricketUpcoming = if (cfg.syncUpcomingEnabled) {
            filterInternationalCricket(state.upcomingState.matches.filter { it.id !in deletedMatchIds })
        } else {
            emptyList()
        }

        // Deduplicate CREX matches across feeds by provider match ID (Priority: LIVE > FINISHED > UPCOMING)
        val seenCricketIds = mutableSetOf<String>()
        val dedupLiveCricket = rawCricketLive.filter { seenCricketIds.add(it.id) }
        val dedupFinishedCricket = rawCricketFinished.filter { seenCricketIds.add(it.id) }
        val dedupUpcomingCricket = rawCricketUpcoming.filter { seenCricketIds.add(it.id) }

        val activeFootball = if (cfg.syncFootballEnabled) {
            footballMatches
                .filter { it.id !in deletedMatchIds && !isLegacyDemoMatchId(it.id) }
                .filter {
                    !TeamFlagResolver.isNonSeniorOrExcludedCompetition("${it.tournament} ${it.team1Name} ${it.team2Name}") &&
                        !TeamFlagResolver.isNonSeniorFootballTeamName(it.team1Name) &&
                        !TeamFlagResolver.isNonSeniorFootballTeamName(it.team2Name)
                }
                .map { CrexCricketService.revalidateFootballMatchStatus(it) }
                .filter { CrexCricketService.isWithinDhakaThreeDayWindow(it) }
                .distinctBy { it.id }
        } else {
            emptyList()
        }

        val allProviderMatches = dedupLiveCricket + dedupUpcomingCricket + dedupFinishedCricket + activeFootball
        remapLegacyFavoritesAndStreamsToProviderMatches(allProviderMatches)

        val fbWithFavs = applyFavoritesAndOverrides(activeFootball)
        val crexLive = applyFavoritesAndOverrides(dedupLiveCricket)
        val crexUpcoming = applyFavoritesAndOverrides(dedupUpcomingCricket)
        val crexFinished = applyFavoritesAndOverrides(dedupFinishedCricket)
        val sessionMatches = applyFavoritesAndOverrides(
            sessionAddedMatches.values.filter { it.id !in deletedMatchIds && !isLegacyDemoMatchId(it.id) }
        )

        val combined = buildList {
            addAll(crexLive)
            addAll(fbWithFavs.filter { it.isConfirmedLive })
            addAll(sessionMatches.filter { it.isConfirmedLive })
            addAll(crexUpcoming)
            addAll(fbWithFavs.filter { it.status == MatchStatus.UPCOMING && !it.isConfirmedLive })
            addAll(sessionMatches.filter { it.status == MatchStatus.UPCOMING && !it.isConfirmedLive })
            addAll(crexFinished)
            addAll(fbWithFavs.filter { it.status == MatchStatus.FINISHED })
            addAll(sessionMatches.filter { it.status == MatchStatus.FINISHED })
        }.distinctBy { it.id }

        _matches.value = combined
        rebuildDynamicHighlights()
    }

    /**
     * Refreshes the separate Football provider (`FootballProviderState`) using the Admin-configured allowlist.
     * Never substitutes Cricket or demo fixtures if the Football feed fails.
     */
    suspend fun refreshFootballMatches(force: Boolean = false): FootballProviderState {
        val cfg = _config.value
        if (!cfg.syncFootballEnabled) {
            val disabledState = FootballProviderState(
                matches = emptyList(),
                isLoading = false,
                errorMessage = null,
                activeLeagues = cfg.enabledFootballLeagues
            )
            footballMatches.clear()
            _crexDashboardState.value = _crexDashboardState.value.copy(footballState = disabledState)
            rebuildCombinedMatches()
            return disabledState
        }

        val now = System.currentTimeMillis()
        if (!force && lastMultiSportFetchMs > 0L && (now - lastMultiSportFetchMs) < 30_000L && footballMatches.isNotEmpty()) {
            return _crexDashboardState.value.footballState
        }

        val currentFbState = _crexDashboardState.value.footballState
        if (currentFbState.matches.isEmpty()) {
            _crexDashboardState.value = _crexDashboardState.value.copy(
                footballState = currentFbState.copy(isLoading = true, errorMessage = null)
            )
        }

        val fbState = crexService.fetchFootballMatches(
            enabledLeagues = cfg.enabledFootballLeagues,
            forceRefresh = force
        )
        lastMultiSportFetchMs = now
        footballMatches.clear()
        footballMatches.addAll(fbState.matches)

        val enrichedFbState = fbState.copy(
            matches = applyFavoritesAndOverrides(fbState.matches)
        )
        _crexDashboardState.value = _crexDashboardState.value.copy(footballState = enrichedFbState)

        val realHighlights = crexService.fetchRealHighlights(enabledLeagues = cfg.enabledFootballLeagues)
        if (realHighlights.isNotEmpty()) {
            apiHighlights.clear()
            apiHighlights.addAll(realHighlights)
        }

        rebuildCombinedMatches()
        return enrichedFbState
    }

    /**
     * Refreshes Live matches via `GET /api/cricket/live` (`https://api.goscorer.com/api/v3/getLiveMatches`)
     * AND updates Football matches from the separate Football provider.
     */
    suspend fun refreshLiveMatches(force: Boolean = false): CrexCategoryFeedState {
        val cfg = _config.value
        refreshFootballMatches(force = force)
        if (!cfg.syncLiveEnabled) {
            return _crexDashboardState.value.liveState
        }

        val current = _crexDashboardState.value.liveState
        if (current.matches.isEmpty()) {
            _crexDashboardState.value = _crexDashboardState.value.copy(
                liveState = current.copy(isLoading = true, errorMessage = null)
            )
        }
        val fetched = crexService.fetchLiveMatches(forceRefresh = force)
        val filtered = filterInternationalCricket(fetched.matches)
        val withFavs = fetched.copy(matches = applyFavoritesAndOverrides(filtered))
        _crexDashboardState.value = _crexDashboardState.value.copy(liveState = withFavs)
        rebuildCombinedMatches()
        return withFavs
    }

    /**
     * Refreshes Upcoming matches via `GET /api/cricket/upcoming` (`POST getHomeUpcomingMatchesCE {"type": 1}`).
     */
    suspend fun refreshUpcomingMatches(force: Boolean = false): CrexCategoryFeedState {
        val cfg = _config.value
        refreshFootballMatches(force = force)
        if (!cfg.syncUpcomingEnabled) {
            return _crexDashboardState.value.upcomingState
        }

        val current = _crexDashboardState.value.upcomingState
        if (current.matches.isEmpty()) {
            _crexDashboardState.value = _crexDashboardState.value.copy(
                upcomingState = current.copy(isLoading = true, errorMessage = null)
            )
        }
        val fetched = crexService.fetchUpcomingMatches(forceRefresh = force)
        val filtered = filterInternationalCricket(fetched.matches)
        val withFavs = fetched.copy(matches = applyFavoritesAndOverrides(filtered))
        _crexDashboardState.value = _crexDashboardState.value.copy(upcomingState = withFavs)
        rebuildCombinedMatches()
        return withFavs
    }

    /**
     * Refreshes Finished matches via `GET /api/cricket/finished` (`POST getHomeUpcomingMatchesCE {"type": 0}`).
     */
    suspend fun refreshFinishedMatches(force: Boolean = false): CrexCategoryFeedState {
        val cfg = _config.value
        if (!cfg.syncFinishedEnabled) {
            return _crexDashboardState.value.finishedState
        }

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
        val filtered = filterInternationalCricket(fetched.matches)
        val withFavs = fetched.copy(matches = applyFavoritesAndOverrides(filtered))
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
                        team2FlagUrl = v2.flagUrl,
                        team1Score = d.team1Score.ifBlank { m.team1Score },
                        team2Score = d.team2Score.ifBlank { m.team2Score }
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
        cloudMatchOverrides[match.id] = match
        sessionAddedMatches[match.id] = match
        saveData()
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

        if (isFirebaseAuthenticated()) {
            repoScope.launch {
                cloudSyncRepository.upsertPublicCatalogItem(
                    rawItemId = match.id,
                    itemType = "match",
                    title = "${match.team1Name} vs ${match.team2Name}",
                    payloadJson = serializeSingleMatch(match).toString()
                )
            }
        }
    }

    /**
     * Quick-attach a custom `.m3u8` / `.mp4` stream (with optional Referer / Origin / User-Agent)
     * directly to any CREX or ESPN match.
     */
    fun attachCustomStreamToMatch(matchId: String, newStream: StreamSource): LiveMatch? {
        val targetMatch = _matches.value.find { it.id == matchId } ?: return null
        val existingCustom = cloudMatchOverrides[matchId]?.streams.orEmpty()
        val updatedCustomStreams = listOf(newStream.copy(isDefault = true, isEnabled = true, isAuthorized = true)) +
            existingCustom.filterNot { it.url == newStream.url && it.name == newStream.name }.map { it.copy(isDefault = false) }
        val updatedMatch = targetMatch.copy(streams = updatedCustomStreams)
        addOrUpdateMatch(updatedMatch)
        return _matches.value.find { it.id == matchId } ?: updatedMatch
    }

    fun deleteMatch(matchId: String) {
        cloudMatchOverrides.remove(matchId)
        sessionAddedMatches.remove(matchId)
        deletedMatchIds.add(matchId)
        saveData()
        val cur = _crexDashboardState.value
        _crexDashboardState.value = cur.copy(
            liveState = cur.liveState.copy(matches = cur.liveState.matches.filter { it.id != matchId }),
            upcomingState = cur.upcomingState.copy(matches = cur.upcomingState.matches.filter { it.id != matchId }),
            finishedState = cur.finishedState.copy(matches = cur.finishedState.matches.filter { it.id != matchId })
        )
        rebuildCombinedMatches()

        if (isFirebaseAuthenticated()) {
            repoScope.launch {
                cloudSyncRepository.deletePublicCatalogItem(matchId, "match")
            }
        }
    }

    fun toggleMatchFavorite(matchId: String) {
        val added: Boolean
        val matchObj = _matches.value.find { it.id == matchId }
        if (matchId in favoriteMatchIds) {
            favoriteMatchIds.remove(matchId)
            favoriteMatchMeta.remove(matchId)
            added = false
        } else {
            favoriteMatchIds.add(matchId)
            if (matchObj != null) {
                favoriteMatchMeta[matchId] = matchObj
            }
            added = true
        }
        val cur = _crexDashboardState.value
        _crexDashboardState.value = cur.copy(
            liveState = cur.liveState.copy(matches = applyFavoritesAndOverrides(cur.liveState.matches)),
            upcomingState = cur.upcomingState.copy(matches = applyFavoritesAndOverrides(cur.upcomingState.matches)),
            finishedState = cur.finishedState.copy(matches = applyFavoritesAndOverrides(cur.finishedState.matches))
        )
        rebuildCombinedMatches()
        saveData()

        if (isFirebaseAuthenticated()) {
            val title = matchObj?.let { "${it.team1Name} vs ${it.team2Name}" } ?: matchId
            repoScope.launch {
                if (added) {
                    cloudSyncRepository.addFavorite(matchId, "match", title)
                } else {
                    cloudSyncRepository.removeFavorite("match_$matchId")
                }
            }
        }
    }

    fun toggleMatchReminder(matchId: String): Boolean {
        val isNowSet = if (matchId in reminderMatchIds) {
            reminderMatchIds.remove(matchId)
            false
        } else {
            reminderMatchIds.add(matchId)
            true
        }
        val cur = _crexDashboardState.value
        _crexDashboardState.value = cur.copy(
            liveState = cur.liveState.copy(matches = applyFavoritesAndOverrides(cur.liveState.matches)),
            upcomingState = cur.upcomingState.copy(matches = applyFavoritesAndOverrides(cur.upcomingState.matches)),
            finishedState = cur.finishedState.copy(matches = applyFavoritesAndOverrides(cur.finishedState.matches))
        )
        rebuildCombinedMatches()
        saveData()
        return isNowSet
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

        if (isFirebaseAuthenticated()) {
            repoScope.launch {
                cloudSyncRepository.upsertPublicCatalogItem(
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

        if (isFirebaseAuthenticated()) {
            repoScope.launch {
                cloudSyncRepository.deletePublicCatalogItem(channelId, "channel")
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

        if (isFirebaseAuthenticated()) {
            repoScope.launch {
                if (isNowFav) {
                    cloudSyncRepository.addFavorite(channelId, "channel", chTitle)
                } else {
                    cloudSyncRepository.removeFavorite("channel_$channelId")
                }
            }
        }
    }

    // Highlight Operations
    fun addOrUpdateHighlight(highlight: HighlightItem) {
        val current = _highlights.value.toMutableList()
        val idx = current.indexOfFirst { it.id == highlight.id }
        if (idx >= 0) current[idx] = highlight else current.add(0, highlight)
        _highlights.value = current

        if (isFirebaseAuthenticated()) {
            repoScope.launch {
                cloudSyncRepository.upsertPublicCatalogItem(
                    rawItemId = highlight.id,
                    itemType = "highlight",
                    title = highlight.title,
                    payloadJson = serializeSingleHighlight(highlight).toString()
                )
            }
        }
    }

    fun deleteHighlight(highlightId: String) {
        _highlights.value = _highlights.value.filter { it.id != highlightId }
        if (isFirebaseAuthenticated()) {
            repoScope.launch {
                cloudSyncRepository.deletePublicCatalogItem(highlightId, "highlight")
            }
        }
    }

    fun updateConfig(newConfig: AppConfig) {
        val previousLeagues = _config.value.enabledFootballLeagues
        val previousFbEnabled = _config.value.syncFootballEnabled
        _config.value = newConfig
        saveData()
        rebuildCombinedMatches()

        if (previousLeagues != newConfig.enabledFootballLeagues || previousFbEnabled != newConfig.syncFootballEnabled) {
            repoScope.launch {
                refreshFootballMatches(force = true)
            }
        }

        if (isFirebaseAuthenticated()) {
            repoScope.launch {
                val leaguesArr = JSONArray()
                newConfig.enabledFootballLeagues.forEach { leaguesArr.put(it) }
                val configObj = JSONObject().apply {
                    put("appTitle", newConfig.appTitle)
                    put("version", newConfig.version)
                    put("announcement", newConfig.announcement)
                    put("telegramLink", newConfig.telegramLink)
                    put("adminPin", newConfig.adminPin)
                    put("remoteFeedUrl", newConfig.remoteFeedUrl)
                    put("autoSyncOnLaunch", newConfig.autoSyncOnLaunch)
                    put("syncLiveEnabled", newConfig.syncLiveEnabled)
                    put("syncUpcomingEnabled", newConfig.syncUpcomingEnabled)
                    put("syncFinishedEnabled", newConfig.syncFinishedEnabled)
                    put("syncFootballEnabled", newConfig.syncFootballEnabled)
                    put("autoRefreshIntervalSeconds", newConfig.autoRefreshIntervalSeconds)
                    put("enabledFootballLeagues", leaguesArr)
                }
                cloudSyncRepository.upsertPublicCatalogItem(
                    rawItemId = "global_app_config",
                    itemType = "config",
                    title = newConfig.appTitle,
                    payloadJson = configObj.toString()
                )
            }
        }
    }

    /**
     * Pushes all current Admin Panel matches (with custom streams), channels, highlights, and global config
     * to the Firebase Firestore Cloud Server so all connected users receive them immediately.
     */
    suspend fun pushAllToCloudServer(): Result<Int> = withContext(Dispatchers.IO) {
        if (Firebase.auth.currentUser == null) {
            return@withContext Result.failure(IllegalStateException("Google Sign-In required to push to Cloud Server"))
        }
        var pushedCount = 0
        try {
            val cfg = _config.value
            val leaguesArr = JSONArray()
            cfg.enabledFootballLeagues.forEach { leaguesArr.put(it) }
            val configObj = JSONObject().apply {
                put("appTitle", cfg.appTitle)
                put("version", cfg.version)
                put("announcement", cfg.announcement)
                put("telegramLink", cfg.telegramLink)
                put("adminPin", cfg.adminPin)
                put("remoteFeedUrl", cfg.remoteFeedUrl)
                put("autoSyncOnLaunch", cfg.autoSyncOnLaunch)
                put("syncLiveEnabled", cfg.syncLiveEnabled)
                put("syncUpcomingEnabled", cfg.syncUpcomingEnabled)
                put("syncFinishedEnabled", cfg.syncFinishedEnabled)
                put("syncFootballEnabled", cfg.syncFootballEnabled)
                put("autoRefreshIntervalSeconds", cfg.autoRefreshIntervalSeconds)
                put("enabledFootballLeagues", leaguesArr)
            }
            cloudSyncRepository.upsertPublicCatalogItem(
                rawItemId = "global_app_config",
                itemType = "config",
                title = cfg.appTitle,
                payloadJson = configObj.toString()
            ).getOrThrow()
            pushedCount++

            for (ch in _channels.value) {
                cloudSyncRepository.upsertPublicCatalogItem(
                    rawItemId = ch.id,
                    itemType = "channel",
                    title = ch.name,
                    payloadJson = serializeSingleChannel(ch).toString()
                ).getOrThrow()
                pushedCount++
            }

            for (m in _matches.value) {
                if (m.streams.isNotEmpty() || m.id in cloudMatchOverrides) {
                    cloudSyncRepository.upsertPublicCatalogItem(
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
            put("providerStatusLabel", m.providerStatusLabel)
            put("isPostponedOrCancelled", m.isPostponedOrCancelled)
            put("matchTime", m.matchTime)
            put("timeOrScore", m.timeOrScore)
            put("startTimeEpochMs", m.startTimeEpochMs)
            put("team1Name", m.team1Name)
            put("team1Code", m.team1Code)
            put("team1FlagUrl", m.team1FlagUrl)
            put("team2Name", m.team2Name)
            put("team2Code", m.team2Code)
            put("team2FlagUrl", m.team2FlagUrl)
            put("team1Score", m.team1Score)
            put("team2Score", m.team2Score)
            val streamsArr = JSONArray()
            for (s in m.streams) {
                val so = JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("quality", s.quality)
                    put("url", s.url)
                    put("isDefault", s.isDefault)
                    put("isEnabled", s.isEnabled)
                    put("isAuthorized", s.isAuthorized)
                    put("isBackup", s.isBackup)
                    put("displayOrder", s.displayOrder)
                    if (!s.userAgent.isNullOrBlank()) put("userAgent", s.userAgent)
                    if (!s.referer.isNullOrBlank()) put("referer", s.referer)
                    if (!s.origin.isNullOrBlank()) put("origin", s.origin)
                    if (!s.cookie.isNullOrBlank()) put("cookie", s.cookie)
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
                            origin = sObj.optString("origin").ifBlank { null },
                            cookie = sObj.optString("cookie").ifBlank { null },
                            isDefault = sObj.optBoolean("isDefault", j == 0),
                            isEnabled = sObj.optBoolean("isEnabled", true),
                            isAuthorized = sObj.optBoolean("isAuthorized", true),
                            isBackup = sObj.optBoolean("isBackup", false),
                            displayOrder = sObj.optInt("displayOrder", j)
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
        val statusStr = obj.optString("status", "").trim().uppercase()
        val parsedStatus = try {
            if (statusStr.isBlank()) null else MatchStatus.valueOf(statusStr)
        } catch (_: Exception) {
            null
        }
        val status = parsedStatus ?: MatchStatus.UPCOMING
        val rawProviderStatusLabel = obj.optString("providerStatusLabel", "").trim()
        val resolvedProviderStatusLabel = when {
            rawProviderStatusLabel.isNotBlank() -> rawProviderStatusLabel
            parsedStatus == null -> "Unknown"
            else -> ""
        }
        val t1Name = obj.optString("team1Name").ifBlank { obj.optString("team1", "Team 1") }
        val t2Name = obj.optString("team2Name").ifBlank { obj.optString("team2", "Team 2") }
        val rawT1Code = obj.optString("team1Code")
        val rawT2Code = obj.optString("team2Code")
        val rawT1Flag = obj.optString("team1FlagUrl").ifBlank { obj.optString("team1Flag", "") }
        val rawT2Flag = obj.optString("team2FlagUrl").ifBlank { obj.optString("team2Flag", "") }
        val tournament = obj.optString("tournament", "MATCH")
        val isClubFb = sport == SportType.FOOTBALL && !CrexCricketService.isInternationalFootballLeague("", tournament)
        val v1 = TeamFlagResolver.resolve(t1Name, rawT1Code, rawT1Flag, isClubFootball = isClubFb)
        val v2 = TeamFlagResolver.resolve(t2Name, rawT2Code, rawT2Flag, isClubFootball = isClubFb)

        return LiveMatch(
            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
            tournament = tournament,
            sport = sport,
            status = status,
            providerStatusLabel = resolvedProviderStatusLabel,
            isPostponedOrCancelled = obj.optBoolean("isPostponedOrCancelled", false),
            matchTime = obj.optString("matchTime").ifBlank { obj.optString("time", "") },
            timeOrScore = obj.optString("timeOrScore").ifBlank { obj.optString("score", "") },
            startTimeEpochMs = obj.optLong("startTimeEpochMs", 0L),
            team1Name = t1Name,
            team1Code = v1.code,
            team1FlagUrl = v1.flagUrl,
            team2Name = t2Name,
            team2Code = v2.code,
            team2FlagUrl = v2.flagUrl,
            team1Score = obj.optString("team1Score", ""),
            team2Score = obj.optString("team2Score", ""),
            streams = streamList
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
            val streamArr = JSONArray()
            for (s in c.streams) {
                val sObj = JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("quality", s.quality)
                    put("url", s.url)
                    put("isDefault", s.isDefault)
                    put("isEnabled", s.isEnabled)
                    put("isAuthorized", s.isAuthorized)
                    put("isBackup", s.isBackup)
                    put("displayOrder", s.displayOrder)
                    if (!s.userAgent.isNullOrBlank()) put("userAgent", s.userAgent)
                    if (!s.referer.isNullOrBlank()) put("referer", s.referer)
                    if (!s.origin.isNullOrBlank()) put("origin", s.origin)
                    if (!s.cookie.isNullOrBlank()) put("cookie", s.cookie)
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
                            origin = sObj.optString("origin").ifBlank { null },
                            cookie = sObj.optString("cookie").ifBlank { null },
                            isDefault = sObj.optBoolean("isDefault", j == 0),
                            isEnabled = sObj.optBoolean("isEnabled", true),
                            isAuthorized = sObj.optBoolean("isAuthorized", true),
                            isBackup = sObj.optBoolean("isBackup", false),
                            displayOrder = sObj.optInt("displayOrder", j)
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
            isFavorite = obj.optBoolean("isFavorite", false)
        )
    }

    private fun serializeSingleHighlight(h: HighlightItem): JSONObject {
        return JSONObject().apply {
            put("id", h.id)
            put("title", h.title)
            put("tournament", h.tournament)
            put("duration", h.duration)
            put("thumbnailUrl", h.thumbnailUrl)
            put("videoUrl", h.videoUrl)
            put("sport", h.sport.name)
        }
    }

    private fun deserializeSingleHighlight(obj: JSONObject): HighlightItem {
        val sportStr = obj.optString("sport", "CRICKET").uppercase()
        val sport = runCatching { SportType.valueOf(sportStr) }.getOrDefault(SportType.CRICKET)
        return HighlightItem(
            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
            title = obj.optString("title", "Match Highlight"),
            tournament = obj.optString("tournament", "HIGHLIGHTS"),
            duration = obj.optString("duration", "10:00"),
            thumbnailUrl = obj.optString("thumbnailUrl", ""),
            videoUrl = obj.optString("videoUrl", ""),
            sport = sport
        )
    }

    fun resetToDefaults() {
        _channels.value = defaultChannels()
        _categories.value = defaultCategories()
        _highlights.value = defaultHighlights()
        _config.value = AppConfig()
        saveData()
    }

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
                    so.put("isEnabled", s.isEnabled)
                    so.put("isAuthorized", s.isAuthorized)
                    so.put("isBackup", s.isBackup)
                    so.put("displayOrder", s.displayOrder)
                    if (!s.userAgent.isNullOrBlank()) so.put("userAgent", s.userAgent)
                    if (!s.referer.isNullOrBlank()) so.put("referer", s.referer)
                    if (!s.origin.isNullOrBlank()) so.put("origin", s.origin)
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
                    so.put("isEnabled", s.isEnabled)
                    so.put("isAuthorized", s.isAuthorized)
                    so.put("isBackup", s.isBackup)
                    so.put("displayOrder", s.displayOrder)
                    if (!s.userAgent.isNullOrBlank()) so.put("userAgent", s.userAgent)
                    if (!s.referer.isNullOrBlank()) so.put("referer", s.referer)
                    if (!s.origin.isNullOrBlank()) so.put("origin", s.origin)
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
        var currentOrigin: String? = null

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
                } else if (opt.startsWith("http-origin=", ignoreCase = true)) {
                    currentOrigin = opt.substringAfter("=").trim()
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
                        if (k.equals("Referer", ignoreCase = true) && v.isNotBlank()) currentReferer = v
                        if (k.equals("Origin", ignoreCase = true) && v.isNotBlank()) currentOrigin = v
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
                            origin = currentOrigin,
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
                currentOrigin = null
            }
        }

        if (parsedChannels.isNotEmpty()) {
            val existing = _channels.value.toMutableList()
            for (ch in parsedChannels.reversed()) {
                val existingIdx = existing.indexOfFirst { it.name.equals(ch.name, ignoreCase = true) }
                if (existingIdx >= 0) {
                    val prev = existing[existingIdx]
                    val mergedStreams = (ch.streams + prev.streams).distinctBy { it.url }
                    existing[existingIdx] = prev.copy(
                        logoUrl = ch.logoUrl.ifBlank { prev.logoUrl },
                        streams = mergedStreams
                    )
                } else {
                    existing.add(0, ch)
                }
            }
            _channels.value = existing
            saveData()
            rebuildCombinedMatches()
        }

        parsedChannels.size
    }

    fun normalizeRemoteFeedUrl(url: String): String = url.trim()

    /**
     * Manual refresh action that syncs Live, Upcoming, and Finished matches from CREX + ESPN Scoreboards.
     */
    suspend fun syncFromRemoteFeed(feedUrl: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        val fbRes = refreshFootballMatches(force = true)
        val liveRes = refreshLiveMatches(force = true)
        val upRes = refreshUpcomingMatches(force = true)
        val finRes = refreshFinishedMatches(force = true)

        val total = _matches.value.size
        val err = liveRes.errorMessage ?: upRes.errorMessage ?: finRes.errorMessage ?: fbRes.errorMessage
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
                title = "Afghanistan vs Bangladesh - ODI Highlights",
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
            ),
            HighlightItem(
                title = "UEFA Champions League - Matchday Goals & Extended Recap",
                tournament = "FOOTBALL • CHAMPIONS LEAGUE",
                duration = "14:20",
                videoUrl = hls2,
                sport = SportType.FOOTBALL
            ),
            HighlightItem(
                title = "Premier League Weekend - Top Goals & Moments",
                tournament = "FOOTBALL • PREMIER LEAGUE",
                duration = "11:10",
                videoUrl = hls3,
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
                        put("isEnabled", s.isEnabled)
                        put("isAuthorized", s.isAuthorized)
                        put("isBackup", s.isBackup)
                        put("displayOrder", s.displayOrder)
                        if (!s.userAgent.isNullOrBlank()) put("userAgent", s.userAgent)
                        if (!s.referer.isNullOrBlank()) put("referer", s.referer)
                        if (!s.origin.isNullOrBlank()) put("origin", s.origin)
                        if (!s.cookie.isNullOrBlank()) put("cookie", s.cookie)
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
                                origin = sObj.optString("origin").ifBlank { null },
                                cookie = sObj.optString("cookie").ifBlank { null },
                                isDefault = sObj.optBoolean("isDefault", j == 0),
                                isEnabled = sObj.optBoolean("isEnabled", true),
                                isAuthorized = sObj.optBoolean("isAuthorized", true),
                                isBackup = sObj.optBoolean("isBackup", false),
                                displayOrder = sObj.optInt("displayOrder", j)
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
                    StreamSource(name = "T SPORTS 1 FHD", quality = "FHD", url = streamHls1, isDefault = true, displayOrder = 0),
                    StreamSource(name = "T SPORTS 2 HD", quality = "HD", url = streamHls2, isBackup = true, displayOrder = 1)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-gtv",
                name = "Gazi TV (GTV)",
                category = "Bangla Sports",
                logoText = "GTV",
                streams = listOf(
                    StreamSource(name = "GTV LIVE FHD", quality = "FHD", url = streamHls2, isDefault = true, displayOrder = 0),
                    StreamSource(name = "GTV BACKUP HD", quality = "HD", url = streamHls3, isBackup = true, displayOrder = 1)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-1",
                name = "TNT Sports",
                category = "Football",
                logoText = "TNT",
                streams = listOf(
                    StreamSource(name = "TNT SPORTS 1 HD", quality = "FHD", url = streamHls1, isDefault = true, displayOrder = 0),
                    StreamSource(name = "TNT SPORTS 2 HD", quality = "HD", url = streamHls2, displayOrder = 1),
                    StreamSource(name = "TNT BACKUP SD", quality = "SD", url = streamHls3, isBackup = true, displayOrder = 2)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-2",
                name = "TSN",
                category = "Sports",
                logoText = "TSN",
                streams = listOf(
                    StreamSource(name = "TSN 1 FHD", quality = "FHD", url = streamHls2, isDefault = true, displayOrder = 0),
                    StreamSource(name = "TSN 2 HD", quality = "HD", url = streamHls1, displayOrder = 1)
                )
            ),
            SportsChannel(
                id = "ch-3",
                name = "BeIN Sports",
                category = "Football",
                logoText = "beIN",
                streams = listOf(
                    StreamSource(name = "BeIN SPORTS 1 PREMIUM", quality = "FHD", url = streamHls3, isDefault = true, displayOrder = 0),
                    StreamSource(name = "BeIN SPORTS 2 ENGLISH", quality = "FHD", url = streamHls1, displayOrder = 1),
                    StreamSource(name = "BeIN SERVER 3", quality = "HD", url = streamHls2, isBackup = true, displayOrder = 2)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-4",
                name = "FOX Sports",
                category = "Sports",
                logoText = "FOX",
                streams = listOf(
                    StreamSource(name = "FS1 HD", quality = "FHD", url = streamHls1, isDefault = true, displayOrder = 0),
                    StreamSource(name = "FS2 HD", quality = "HD", url = streamHls2, displayOrder = 1)
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
                    StreamSource(name = "STAR SPORTS 1 HD", quality = "FHD", url = streamHls1, isDefault = true, displayOrder = 0),
                    StreamSource(name = "STAR SPORTS SELECT 1", quality = "HD", url = streamHls2, displayOrder = 1)
                ),
                isFavorite = true
            ),
            SportsChannel(
                id = "ch-19",
                name = "FanCode",
                category = "FanCode IND",
                logoText = "FC",
                streams = listOf(
                    StreamSource(name = "FANCODE LIVE 1", quality = "FHD", url = streamHls3, isDefault = true, displayOrder = 0),
                    StreamSource(name = "FANCODE LIVE 2", quality = "HD", url = streamHls1, displayOrder = 1)
                )
            ),
            SportsChannel(
                id = "ch-20",
                name = "PTV Sports",
                category = "Pakistan",
                logoText = "PTV",
                streams = listOf(
                    StreamSource(name = "PTV SPORTS HD LIVE", quality = "FHD", url = streamHls2, isDefault = true, displayOrder = 0),
                    StreamSource(name = "PTV SPORTS SD", quality = "SD", url = streamHls3, isBackup = true, displayOrder = 1)
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
                    StreamSource(name = "TAPMAD LIVE 1", quality = "FHD", url = streamHls3, isDefault = true, displayOrder = 0),
                    StreamSource(name = "TAPMAD LIVE 2", quality = "HD", url = streamHls1, displayOrder = 1)
                )
            ),
            SportsChannel(
                id = "ch-24",
                name = "Willow Cricket",
                category = "Cricket",
                logoText = "WILLOW",
                streams = listOf(
                    StreamSource(name = "WILLOW HD 1", quality = "FHD", url = streamHls1, isDefault = true, displayOrder = 0),
                    StreamSource(name = "WILLOW HD 2", quality = "HD", url = streamHls2, displayOrder = 1)
                ),
                isFavorite = true
            )
        )
    }

    private fun defaultCategories(): List<CategoryItem> {
        return listOf(
            CategoryItem(name = "LIVE EVENTS", channelCount = 8, badgeType = "RED_DOT"),
            CategoryItem(name = "Bangla Sports", channelCount = 2, badgeType = "BD_FLAG"),
            CategoryItem(name = "HIGHLIGHTS", channelCount = 5, badgeType = "GOLD_STAR"),
            CategoryItem(name = "Cricket", channelCount = 4, badgeType = "GREEN_CIRCLE"),
            CategoryItem(name = "Football", channelCount = 2, badgeType = "GREEN_CIRCLE"),
            CategoryItem(name = "SonyLiv Live", channelCount = 2, badgeType = "DARK_BADGE"),
            CategoryItem(name = "FanCode IND", channelCount = 1, badgeType = "ORANGE_SQUARE"),
            CategoryItem(name = "Pakistan", channelCount = 2, badgeType = "PK_FLAG"),
            CategoryItem(name = "Tapmad PK", channelCount = 1, badgeType = "GREEN_SQUARE"),
            CategoryItem(name = "Sports", channelCount = 2, badgeType = "BLUE_CIRCLE")
        )
    }
}
