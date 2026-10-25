package com.example.data.crex

import com.example.data.model.LiveMatch
import com.example.data.model.MatchStatus
import com.example.data.model.ProviderSyncDiagnostics
import com.example.data.model.SportType
import com.example.data.model.StreamSource
import com.example.data.util.TeamFlagResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * CREX Cricket Data Service & Local Backend Gateway.
 *
 * Exposes internal backend routes:
 *  - GET https://app.local/api/cricket/live              -> GET  https://api.goscorer.com/api/v3/getLiveMatches
 *  - GET https://app.local/api/cricket/upcoming          -> POST https://oc.crickapi.com/oc/getHomeUpcomingMatchesCE {"type": 1}
 *  - GET https://app.local/api/cricket/finished          -> POST https://oc.crickapi.com/oc/getHomeUpcomingMatchesCE {"type": 0}
 *  - GET https://app.local/api/cricket/matches/{matchId} -> GET  https://api.goscorer.com/api/v3/getSV3?key={matchId}
 *
 * Enforces:
 *  - Top-level `matchId -> match data` dictionary parsing for `getLiveMatches` (never assumes `events[]`)
 *  - Strict Senior International Cricket filtering (excludes domestic, franchise, youth/U19, reserve, amateur)
 *  - Strict `Asia/Dhaka` Yesterday, Today, and Tomorrow date window filtering
 *  - Accurate status resolution (`LIVE` only when provider marks in-progress; never marks `LIVE` just from feed name or date)
 *  - Deduplication by provider `matchId` across Live, Upcoming, and Finished feeds
 *  - Score-free `MatchCard` fields (`timeOrScore` carries status/format metadata only; full innings scores are in `getSV3` detail)
 */
class CrexCricketService(
    baseClient: OkHttpClient? = null
) {
    companion object {
        const val LOCAL_API_BASE = "https://app.local"
        const val ROUTE_LIVE = "/api/cricket/live"
        const val ROUTE_UPCOMING = "/api/cricket/upcoming"
        const val ROUTE_FINISHED = "/api/cricket/finished"
        const val ROUTE_MATCH_DETAIL_PREFIX = "/api/cricket/matches/"

        private const val UPSTREAM_LIVE_URL = "https://api.goscorer.com/api/v3/getLiveMatches"
        private const val UPSTREAM_SCHEDULE_URL = "https://oc.crickapi.com/oc/getHomeUpcomingMatchesCE"
        private const val UPSTREAM_SV3_URL = "https://api.goscorer.com/api/v3/getSV3?key="

        const val LIVE_CACHE_TTL_MS = 25_000L
        const val UPCOMING_CACHE_TTL_MS = 15 * 60_000L
        const val FINISHED_CACHE_TTL_MS = 30 * 60_000L
        const val DETAIL_CACHE_TTL_MS = 25_000L

        private val DHAKA_TIMEZONE: TimeZone = TimeZone.getTimeZone("Asia/Dhaka")

        // Verified CREX team fkey dictionary from CREX speech_names & finished match results.
        // Dynamically updated whenever getSV3 speech_names or finished results are parsed.
        private val teamNameCache = ConcurrentHashMap<String, String>().apply {
            put("O", "India")
            put("P", "South Africa")
            put("Q", "Australia")
            put("R", "New Zealand")
            put("S", "England")
            put("T", "Sri Lanka")
            put("U", "Pakistan")
            put("V", "West Indies")
            put("VO", "West Indies")
            put("VL", "England")
            put("VN", "South Africa")
            put("VK", "Australia")
            put("W", "Bangladesh")
            put("X", "Zimbabwe")
            put("Y", "Afghanistan")
            put("Z", "Ireland")
            put("N", "UAE")
            put("11", "UAE")
            put("12", "Netherlands")
            put("15", "Oman")
            put("16", "India Women")
            put("19", "Nepal")
            put("1H", "New Zealand Women")
            put("1I", "Australia Women")
            put("1J", "England Women")
            put("1K", "South Africa Women")
            put("1L", "West Indies Women")
            put("1M", "Pakistan Women")
            put("1N", "Sri Lanka Women")
            put("1O", "Bangladesh Women")
            put("6I", "Zimbabwe Women")
            put("6V", "Kenya")
            put("6W", "Scotland")
            put("6X", "Namibia")
            put("6Y", "Jersey")
            put("6Z", "Nigeria")
            put("70", "Canada")
            put("71", "USA")
            put("72", "Bermuda")
            put("73", "Hong Kong")
            put("74", "Malaysia")
            put("75", "Singapore")
            put("8X", "Papua New Guinea")
            put("CB", "Qatar")
            put("CC", "Kuwait")
            put("CD", "Bahrain")
            put("CE", "Saudi Arabia")
            put("HO", "Uganda")
            put("IO", "Denmark")
            put("1DY", "Zambia")
            put("1F7", "Lesotho")
            put("1IA", "Mozambique")
            put("1LO", "Gambia")
            put("8E", "Afghanistan U19")
            put("8J", "Bangladesh U19")
            // Domestic / Franchise teams kept in dictionary so they are recognized and filtered out accurately
            put("3J", "Victoria")
            put("3K", "Western Australia")
            put("3L", "New South Wales")
            put("3M", "South Australia")
            put("FU", "Queensland")
            put("FV", "Tasmania")
            put("4R", "Adelaide Strikers Women")
            put("4V", "Melbourne Renegades Women")
            put("4W", "Melbourne Stars Women")
            put("4X", "Perth Scorchers Women")
            put("50", "Sydney Sixers Women")
            put("8W", "Sydney Thunder Women")
            put("6A", "Dolphins")
            put("6C", "Warriors")
            put("6D", "Lions")
            put("6E", "Knights")
            put("J8", "Northern Cape")
            put("J9", "Garden Route Badgers")
            put("JA", "Mpumalanga")
            put("JC", "Eastern Cape")
            put("JD", "Limpopo")
            put("JE", "North West")
            put("JG", "Rocks")
            put("GJ", "Kerala")
            put("GL", "Haryana")
            put("GS", "Goa")
            put("GT", "Gujarat")
            put("GU", "Vidarbha")
            put("GW", "Baroda")
            put("H0", "Maharashtra")
            put("H2", "Assam")
            put("H3", "Punjab")
            put("H4", "Bengal")
            put("H5", "Hyderabad")
            put("H7", "Tamil Nadu")
            put("H9", "Mumbai")
            put("HC", "Karnataka")
            put("HD", "Uttar Pradesh")
            put("GC", "Sikkim")
            put("1CJ", "Khulna Division")
            put("1CK", "Rajshahi Division")
            put("1CL", "Rangpur Division")
            put("1CN", "Sylhet Division")
            put("1CO", "Dhaka Division")
            put("1CP", "Chattogram Division")
            put("1CQ", "Barishal Division")
        }

        // Series metadata learned from getLiveMatches (seriesKey -> "Board • Format")
        private val seriesMetaCache = ConcurrentHashMap<String, String>()

        fun formatEpochDhaka(epochMs: Long, includeSeconds: Boolean = false): String {
            if (epochMs <= 0L) return ""
            val pattern = if (includeSeconds) "dd MMM yyyy, hh:mm:ss a 'BST'" else "dd MMM yyyy, hh:mm a 'BST'"
            val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = DHAKA_TIMEZONE
            }
            return sdf.format(Date(epochMs))
        }

        fun formatSourceDateStringDhaka(rawDate: String): String {
            val trimmed = rawDate.trim()
            if (trimmed.isBlank()) return ""
            val parsedEpoch = parseSourceDateEpochDhaka(trimmed)
            if (parsedEpoch > 0L) {
                val out = SimpleDateFormat("dd MMM yyyy '(Asia/Dhaka)'", Locale.US).apply {
                    timeZone = DHAKA_TIMEZONE
                }
                return out.format(Date(parsedEpoch))
            }
            return trimmed
        }

        fun parseSourceDateEpochDhaka(rawDate: String): Long {
            val trimmed = rawDate.trim()
            if (trimmed.isBlank()) return 0L
            val patterns = listOf("yyyy/MM/dd", "MM/dd/yyyy", "yyyy-MM-dd")
            for (p in patterns) {
                try {
                    val parser = SimpleDateFormat(p, Locale.US).apply {
                        timeZone = DHAKA_TIMEZONE
                        isLenient = false
                    }
                    val parsed = parser.parse(trimmed)
                    if (parsed != null) {
                        // Add 12 hours so the timestamp sits squarely inside that calendar day in Asia/Dhaka
                        return parsed.time + (12L * 3600_000L)
                    }
                } catch (_: Exception) {
                }
            }
            return 0L
        }
    }

    private data class CachedRouteResponse(
        val rawBody: String,
        val fetchedAtEpochMs: Long
    )

    private data class RouteErrorState(
        val consecutiveFailures: Int = 0,
        val backoffUntilEpochMs: Long = 0L,
        val lastErrorMessage: String? = null
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dedupMutex = Mutex()
    private val routeCache = ConcurrentHashMap<String, CachedRouteResponse>()
    private val inFlightCalls = ConcurrentHashMap<String, Deferred<Result<CachedRouteResponse>>>()
    private val routeErrors = ConcurrentHashMap<String, RouteErrorState>()

    @Volatile
    private var lastDiagnostics = ProviderSyncDiagnostics(
        providerName = "CREX (International Cricket)",
        enabled = true,
        statusText = "IDLE"
    )

    fun getDiagnostics(enabled: Boolean): ProviderSyncDiagnostics {
        return lastDiagnostics.copy(enabled = enabled)
    }

    fun clearCache() {
        routeCache.clear()
        routeErrors.clear()
    }

    /**
     * OkHttp Interceptor that translates the app's own `/api/cricket/...` backend routes
     * into the read-only CREX upstream requests.
     */
    private val backendGatewayInterceptor = Interceptor { chain ->
        val original = chain.request()
        val url = original.url
        if (url.host == "app.local" && url.encodedPath.startsWith("/api/cricket")) {
            val path = url.encodedPath
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val upstreamRequest: Request = when {
                path == ROUTE_LIVE -> {
                    Request.Builder()
                        .url(UPSTREAM_LIVE_URL)
                        .get()
                        .header("User-Agent", "Mozilla/5.0 (Android; SportzXLiveTV)")
                        .header("Accept", "application/json")
                        .build()
                }
                path == ROUTE_UPCOMING -> {
                    val body = """{"type":1}""".toRequestBody(jsonMediaType)
                    Request.Builder()
                        .url(UPSTREAM_SCHEDULE_URL)
                        .post(body)
                        .header("User-Agent", "Mozilla/5.0 (Android; SportzXLiveTV)")
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .build()
                }
                path == ROUTE_FINISHED -> {
                    val body = """{"type":0}""".toRequestBody(jsonMediaType)
                    Request.Builder()
                        .url(UPSTREAM_SCHEDULE_URL)
                        .post(body)
                        .header("User-Agent", "Mozilla/5.0 (Android; SportzXLiveTV)")
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .build()
                }
                path.startsWith(ROUTE_MATCH_DETAIL_PREFIX) -> {
                    val matchId = path.removePrefix(ROUTE_MATCH_DETAIL_PREFIX).trim()
                    Request.Builder()
                        .url("$UPSTREAM_SV3_URL$matchId")
                        .get()
                        .header("User-Agent", "Mozilla/5.0 (Android; SportzXLiveTV)")
                        .header("Accept", "application/json")
                        .build()
                }
                else -> original
            }
            chain.proceed(upstreamRequest)
        } else {
            chain.proceed(original)
        }
    }

    private val httpClient: OkHttpClient = (baseClient?.newBuilder() ?: OkHttpClient.Builder())
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor(backendGatewayInterceptor)
        .build()

    private val defaultCricketStreams = listOf(
        StreamSource(
            name = "T SPORTS FHD (BD)",
            quality = "FHD",
            url = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8",
            isDefault = true,
            isEnabled = true,
            priorityOrder = 1,
            isBackup = false
        ),
        StreamSource(
            name = "STAR SPORTS 1 HD",
            quality = "FHD",
            url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            isDefault = false,
            isEnabled = true,
            priorityOrder = 2,
            isBackup = false
        ),
        StreamSource(
            name = "WILLOW / PTV SPORTS HD (Backup)",
            quality = "HD",
            url = "https://diceyk6a7voy4.cloudfront.net/e78752a1-2e83-43fa-85ae-3d508be29366/hls/de:02b6989d-e714-4227-9c57-a4c1a221a244:playlist.m3u8",
            isDefault = false,
            isEnabled = true,
            priorityOrder = 3,
            isBackup = true
        )
    )

    /**
     * Executes a request against the app's `/api/cricket/...` route with:
     * 1. Shared server-side cache (`ttlMs`)
     * 2. In-flight request deduplication (`inFlightCalls`)
     * 3. Exponential error backoff (`routeErrors`)
     */
    private suspend fun executeBackendRoute(
        routePath: String,
        ttlMs: Long,
        forceRefresh: Boolean
    ): Result<CachedRouteResponse> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = routeCache[routePath]

        if (cached != null) {
            val age = now - cached.fetchedAtEpochMs
            val effectiveTtl = if (forceRefresh) 2_500L else ttlMs
            if (age in 0 until effectiveTtl) {
                return@withContext Result.success(cached)
            }
        }

        val errState = routeErrors[routePath]
        if (errState != null && now < errState.backoffUntilEpochMs && !forceRefresh) {
            val waitSec = ((errState.backoffUntilEpochMs - now) / 1000L).coerceAtLeast(1L)
            return@withContext Result.failure(
                IOException("${errState.lastErrorMessage ?: "Upstream API error"} (backing off ${waitSec}s)")
            )
        }

        val deferred: Deferred<Result<CachedRouteResponse>> = dedupMutex.withLock {
            val existing = inFlightCalls[routePath]
            if (existing != null && existing.isActive) {
                existing
            } else {
                val created = serviceScope.async {
                    try {
                        val req = Request.Builder()
                            .url("$LOCAL_API_BASE$routePath")
                            .get()
                            .build()
                        httpClient.newCall(req).execute().use { resp ->
                            if (resp.code == 401 || resp.code == 403) {
                                throw IOException("Endpoint $routePath returned HTTP ${resp.code} (Authentication/Credentials required)")
                            }
                            if (!resp.isSuccessful) {
                                throw IOException("HTTP ${resp.code} from $routePath")
                            }
                            val body = resp.body?.string()?.trim().orEmpty()
                            if (body.isBlank()) {
                                throw IOException("Empty response body from $routePath")
                            }
                            val entry = CachedRouteResponse(
                                rawBody = body,
                                fetchedAtEpochMs = System.currentTimeMillis()
                            )
                            routeCache[routePath] = entry
                            routeErrors.remove(routePath)
                            Result.success(entry)
                        }
                    } catch (e: Exception) {
                        val prevFailures = (routeErrors[routePath]?.consecutiveFailures ?: 0) + 1
                        val backoffSec = min(60L, 5L * (1L shl (prevFailures - 1).coerceAtMost(4)))
                        val backoffUntil = System.currentTimeMillis() + backoffSec * 1000L
                        routeErrors[routePath] = RouteErrorState(
                            consecutiveFailures = prevFailures,
                            backoffUntilEpochMs = backoffUntil,
                            lastErrorMessage = e.localizedMessage ?: "Network failure"
                        )
                        Result.failure(e)
                    } finally {
                        dedupMutex.withLock {
                            inFlightCalls.remove(routePath)
                        }
                    }
                }
                inFlightCalls[routePath] = created
                created
            }
        }

        deferred.await()
    }

    /**
     * Unified fetch & deduplication across Live, Upcoming, and Finished CREX endpoints.
     * Ensures that:
     * 1. Matches from `getLiveMatches` that are actually `UPCOMING` or `FINISHED` are routed to their true status bucket.
     * 2. Duplicate matches across `getLiveMatches`, `getHomeUpcomingMatchesCE(type=1)`, and `getHomeUpcomingMatchesCE(type=0)`
     *    are deduplicated by provider `matchId`.
     */
    suspend fun fetchSynchronizedDashboard(
        forceRefresh: Boolean = false,
        referenceNowEpochMs: Long = System.currentTimeMillis()
    ): CrexDashboardState = withContext(Dispatchers.IO) {
        val liveDeferred = async { fetchLiveMatchesRaw(forceRefresh, referenceNowEpochMs) }
        val upcomingDeferred = async { fetchUpcomingMatchesRaw(forceRefresh, referenceNowEpochMs) }
        val finishedDeferred = async { fetchFinishedMatchesRaw(forceRefresh, referenceNowEpochMs) }

        val rawLiveState = liveDeferred.await()
        val rawUpcomingState = upcomingDeferred.await()
        val rawFinishedState = finishedDeferred.await()

        // Deduplicate by matchId across all three feeds.
        // Priority: FINISHED > LIVE > UPCOMING > UNKNOWN
        val mergedById = LinkedHashMap<String, LiveMatch>()

        fun statusRank(status: MatchStatus): Int = when (status) {
            MatchStatus.FINISHED -> 4
            MatchStatus.LIVE -> 3
            MatchStatus.UPCOMING -> 2
            MatchStatus.UNKNOWN -> 1
        }

        val allIncoming = buildList {
            addAll(rawLiveState.matches)
            addAll(rawFinishedState.matches)
            addAll(rawUpcomingState.matches)
        }

        for (match in allIncoming) {
            val existing = mergedById[match.id]
            if (existing == null || statusRank(match.status) > statusRank(existing.status)) {
                mergedById[match.id] = match
            }
        }

        val deduplicated = mergedById.values.toList()
        val trueLive = deduplicated.filter { it.status == MatchStatus.LIVE }
        val trueUpcoming = deduplicated.filter { it.status == MatchStatus.UPCOMING || it.status == MatchStatus.UNKNOWN }
            .sortedBy { if (it.startTimeEpochMs > 0L) it.startTimeEpochMs else Long.MAX_VALUE }
        val trueFinished = deduplicated.filter { it.status == MatchStatus.FINISHED }
            .sortedByDescending { it.startTimeEpochMs }

        val nowEpoch = maxOf(
            rawLiveState.lastUpdatedEpochMs ?: 0L,
            rawUpcomingState.lastUpdatedEpochMs ?: 0L,
            rawFinishedState.lastUpdatedEpochMs ?: 0L
        ).takeIf { it > 0L } ?: System.currentTimeMillis()
        val nowDhaka = formatEpochDhaka(nowEpoch, includeSeconds = true)

        val anyError = listOfNotNull(
            rawLiveState.errorMessage,
            rawUpcomingState.errorMessage,
            rawFinishedState.errorMessage
        ).firstOrNull()
        val anyStale = rawLiveState.isStale || rawUpcomingState.isStale || rawFinishedState.isStale

        lastDiagnostics = ProviderSyncDiagnostics(
            providerName = "CREX (International Cricket)",
            enabled = true,
            lastSyncEpochMs = nowEpoch,
            lastSyncDhakaText = nowDhaka,
            statusText = when {
                anyError == null -> "SUCCESS (${deduplicated.size} matches, ${trueLive.size} live)"
                deduplicated.isNotEmpty() -> "STALE_CACHE (${deduplicated.size} matches)"
                else -> "ERROR"
            },
            lastError = anyError,
            activeMatchCount = deduplicated.size,
            isStale = anyStale
        )

        CrexDashboardState(
            liveState = rawLiveState.copy(
                category = MatchStatus.LIVE,
                matches = trueLive
            ),
            upcomingState = rawUpcomingState.copy(
                category = MatchStatus.UPCOMING,
                matches = trueUpcoming
            ),
            finishedState = rawFinishedState.copy(
                category = MatchStatus.FINISHED,
                matches = trueFinished
            )
        )
    }

    /**
     * 1. GET /api/cricket/live -> GET https://api.goscorer.com/api/v3/getLiveMatches
     * Note: Filters to matches whose true resolved status is `MatchStatus.LIVE`.
     */
    suspend fun fetchLiveMatches(
        forceRefresh: Boolean = false,
        referenceNowEpochMs: Long = System.currentTimeMillis()
    ): CrexCategoryFeedState {
        val raw = fetchLiveMatchesRaw(forceRefresh, referenceNowEpochMs)
        return raw.copy(matches = raw.matches.filter { it.status == MatchStatus.LIVE })
    }

    private suspend fun fetchLiveMatchesRaw(
        forceRefresh: Boolean = false,
        referenceNowEpochMs: Long = System.currentTimeMillis()
    ): CrexCategoryFeedState {
        val result = executeBackendRoute(
            routePath = ROUTE_LIVE,
            ttlMs = LIVE_CACHE_TTL_MS,
            forceRefresh = forceRefresh
        )
        return result.fold(
            onSuccess = { entry ->
                try {
                    val parsed = parseLiveMatchesPayload(entry.rawBody, referenceNowEpochMs)
                    CrexCategoryFeedState(
                        category = MatchStatus.LIVE,
                        matches = parsed,
                        isLoading = false,
                        errorMessage = null,
                        isStale = false,
                        lastUpdatedEpochMs = entry.fetchedAtEpochMs,
                        lastUpdatedDhakaText = formatEpochDhaka(entry.fetchedAtEpochMs, includeSeconds = true)
                    )
                } catch (e: Exception) {
                    buildErrorOrStaleState(
                        category = MatchStatus.LIVE,
                        routePath = ROUTE_LIVE,
                        error = e,
                        parser = { parseLiveMatchesPayload(it, referenceNowEpochMs) }
                    )
                }
            },
            onFailure = { error ->
                buildErrorOrStaleState(
                    category = MatchStatus.LIVE,
                    routePath = ROUTE_LIVE,
                    error = error,
                    parser = { parseLiveMatchesPayload(it, referenceNowEpochMs) }
                )
            }
        )
    }

    /**
     * 2. GET /api/cricket/upcoming -> POST https://oc.crickapi.com/oc/getHomeUpcomingMatchesCE {"type": 1}
     */
    suspend fun fetchUpcomingMatches(
        forceRefresh: Boolean = false,
        referenceNowEpochMs: Long = System.currentTimeMillis()
    ): CrexCategoryFeedState {
        return fetchUpcomingMatchesRaw(forceRefresh, referenceNowEpochMs)
    }

    private suspend fun fetchUpcomingMatchesRaw(
        forceRefresh: Boolean = false,
        referenceNowEpochMs: Long = System.currentTimeMillis()
    ): CrexCategoryFeedState {
        val result = executeBackendRoute(
            routePath = ROUTE_UPCOMING,
            ttlMs = UPCOMING_CACHE_TTL_MS,
            forceRefresh = forceRefresh
        )
        return result.fold(
            onSuccess = { entry ->
                try {
                    val parsed = parseUpcomingMatchesPayload(entry.rawBody, referenceNowEpochMs)
                    CrexCategoryFeedState(
                        category = MatchStatus.UPCOMING,
                        matches = parsed,
                        isLoading = false,
                        errorMessage = null,
                        isStale = false,
                        lastUpdatedEpochMs = entry.fetchedAtEpochMs,
                        lastUpdatedDhakaText = formatEpochDhaka(entry.fetchedAtEpochMs, includeSeconds = true)
                    )
                } catch (e: Exception) {
                    buildErrorOrStaleState(
                        category = MatchStatus.UPCOMING,
                        routePath = ROUTE_UPCOMING,
                        error = e,
                        parser = { parseUpcomingMatchesPayload(it, referenceNowEpochMs) }
                    )
                }
            },
            onFailure = { error ->
                buildErrorOrStaleState(
                    category = MatchStatus.UPCOMING,
                    routePath = ROUTE_UPCOMING,
                    error = error,
                    parser = { parseUpcomingMatchesPayload(it, referenceNowEpochMs) }
                )
            }
        )
    }

    /**
     * 3. GET /api/cricket/finished -> POST https://oc.crickapi.com/oc/getHomeUpcomingMatchesCE {"type": 0}
     */
    suspend fun fetchFinishedMatches(
        forceRefresh: Boolean = false,
        referenceNowEpochMs: Long = System.currentTimeMillis()
    ): CrexCategoryFeedState {
        return fetchFinishedMatchesRaw(forceRefresh, referenceNowEpochMs)
    }

    private suspend fun fetchFinishedMatchesRaw(
        forceRefresh: Boolean = false,
        referenceNowEpochMs: Long = System.currentTimeMillis()
    ): CrexCategoryFeedState {
        val result = executeBackendRoute(
            routePath = ROUTE_FINISHED,
            ttlMs = FINISHED_CACHE_TTL_MS,
            forceRefresh = forceRefresh
        )
        return result.fold(
            onSuccess = { entry ->
                try {
                    val parsed = parseFinishedMatchesPayload(entry.rawBody, referenceNowEpochMs)
                    CrexCategoryFeedState(
                        category = MatchStatus.FINISHED,
                        matches = parsed,
                        isLoading = false,
                        errorMessage = null,
                        isStale = false,
                        lastUpdatedEpochMs = entry.fetchedAtEpochMs,
                        lastUpdatedDhakaText = formatEpochDhaka(entry.fetchedAtEpochMs, includeSeconds = true)
                    )
                } catch (e: Exception) {
                    buildErrorOrStaleState(
                        category = MatchStatus.FINISHED,
                        routePath = ROUTE_FINISHED,
                        error = e,
                        parser = { parseFinishedMatchesPayload(it, referenceNowEpochMs) }
                    )
                }
            },
            onFailure = { error ->
                buildErrorOrStaleState(
                    category = MatchStatus.FINISHED,
                    routePath = ROUTE_FINISHED,
                    error = error,
                    parser = { parseFinishedMatchesPayload(it, referenceNowEpochMs) }
                )
            }
        )
    }

    /**
     * 4. GET /api/cricket/matches/{matchId} -> GET https://api.goscorer.com/api/v3/getSV3?key={matchId}
     * Called on-demand ONLY when a user opens a specific match.
     */
    suspend fun fetchMatchDetail(matchId: String, forceRefresh: Boolean = false): CrexMatchDetailState {
        val cleanId = matchId.removePrefix("crex-").trim()
        if (cleanId.isBlank()) {
            return CrexMatchDetailState(
                matchId = matchId,
                isLoading = false,
                errorMessage = "Missing match key"
            )
        }
        val routePath = "$ROUTE_MATCH_DETAIL_PREFIX$cleanId"
        val result = executeBackendRoute(
            routePath = routePath,
            ttlMs = DETAIL_CACHE_TTL_MS,
            forceRefresh = forceRefresh
        )
        return result.fold(
            onSuccess = { entry ->
                try {
                    val detail = parseSv3DetailPayload(cleanId, entry.rawBody, entry.fetchedAtEpochMs)
                    CrexMatchDetailState(
                        matchId = cleanId,
                        isLoading = false,
                        detail = detail,
                        errorMessage = null,
                        isStale = false,
                        lastUpdatedDhakaText = formatEpochDhaka(entry.fetchedAtEpochMs, includeSeconds = true)
                    )
                } catch (e: Exception) {
                    CrexMatchDetailState(
                        matchId = cleanId,
                        isLoading = false,
                        detail = null,
                        errorMessage = "Failed to parse match details: ${e.localizedMessage ?: "Malformed JSON"}"
                    )
                }
            },
            onFailure = { error ->
                val cached = routeCache[routePath]
                val staleDetail = cached?.let {
                    runCatching { parseSv3DetailPayload(cleanId, it.rawBody, it.fetchedAtEpochMs) }.getOrNull()
                }
                CrexMatchDetailState(
                    matchId = cleanId,
                    isLoading = false,
                    detail = staleDetail,
                    errorMessage = error.localizedMessage ?: "Failed to load match details",
                    isStale = staleDetail != null,
                    lastUpdatedDhakaText = cached?.let { formatEpochDhaka(it.fetchedAtEpochMs, includeSeconds = true) }
                )
            }
        )
    }

    private fun buildErrorOrStaleState(
        category: MatchStatus,
        routePath: String,
        error: Throwable,
        parser: (String) -> List<LiveMatch>
    ): CrexCategoryFeedState {
        val cached = routeCache[routePath]
        val errState = routeErrors[routePath]
        val cachedMatches = if (cached != null) {
            runCatching { parser(cached.rawBody).map { m -> m.copy(isStale = true) } }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        return CrexCategoryFeedState(
            category = category,
            matches = cachedMatches,
            isLoading = false,
            errorMessage = error.localizedMessage ?: "API request failed",
            isStale = cachedMatches.isNotEmpty(),
            lastUpdatedEpochMs = cached?.fetchedAtEpochMs,
            lastUpdatedDhakaText = cached?.let { formatEpochDhaka(it.fetchedAtEpochMs, includeSeconds = true) },
            backoffUntilEpochMs = errState?.backoffUntilEpochMs ?: 0L
        )
    }

    /**
     * Parses `GET https://api.goscorer.com/api/v3/getLiveMatches`.
     * Inspects the JSON object keyed by matchId (e.g. "10MR": {...}, "1220": {...}).
     * Never assumes an `events[]` array.
     */
    internal fun parseLiveMatchesPayload(
        rawJson: String,
        referenceNowEpochMs: Long = System.currentTimeMillis()
    ): List<LiveMatch> {
        val trimmed = rawJson.trim()
        val matches = mutableListOf<Pair<Int, LiveMatch>>()

        if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            val keys = root.keys()
            while (keys.hasNext()) {
                val matchKey = keys.next()
                val obj = root.optJSONObject(matchKey) ?: continue
                val parsed = parseSingleLiveMatchObject(matchKey, obj, referenceNowEpochMs)
                if (parsed != null) {
                    matches.add(parsed)
                }
            }
        } else if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val matchKey = obj.optString("mf").ifBlank { obj.optString("id", "live-$i") }
                val parsed = parseSingleLiveMatchObject(matchKey, obj, referenceNowEpochMs)
                if (parsed != null) {
                    matches.add(parsed)
                }
            }
        }

        return matches
            .sortedBy { it.first }
            .map { it.second }
            .distinctBy { it.id }
    }

    private fun parseSingleLiveMatchObject(
        matchKey: String,
        obj: JSONObject,
        referenceNowEpochMs: Long
    ): Pair<Int, LiveMatch>? {
        val t1Key = obj.optString("b").trim()
        val t2Key = obj.optString("c").trim()
        if (t1Key.isBlank() && t2Key.isBlank()) return null

        val format = obj.optString("fo").trim() // e.g. "Test", "T20I", "ODI", "T20"
        val board = obj.optString("cb").trim()  // e.g. "ICC", "CSA", "BCCI", "PCB", "ACB"
        val seriesKey = obj.optString("q").removePrefix("^").trim()
        val venueKey = obj.optString("v").trim()
        val resText = obj.optString("res").trim()
        val ifsCode = obj.optString("ifs").trim()
        val dCode = obj.optInt("d", -1)
        val ipCode = obj.optInt("ip", 0)
        val sField = obj.optString("s").trim()
        val finishTime = obj.optLong("finishTime", 0L)
        val startEpochMs = obj.optLong("ti", 0L).takeIf { it > 0L } ?: obj.optLong("fdst", 0L)

        // Learn series metadata from source if available
        if (seriesKey.isNotBlank() && (board.isNotBlank() || format.isNotBlank())) {
            val meta = listOf(board, format).filter { it.isNotBlank() }.joinToString(" • ")
            if (meta.isNotBlank()) {
                seriesMetaCache[seriesKey] = meta
            }
        }

        val t1ResolvedName = resolveCrexTeamName(t1Key)
        val t2ResolvedName = resolveCrexTeamName(t2Key)

        val tournamentHeader = buildList {
            if (board.isNotBlank()) add(board)
            if (format.isNotBlank()) add(format)
            if (seriesKey.isNotBlank()) add("Series $seriesKey")
        }.joinToString(" • ").ifBlank { "International Cricket" }

        // Strict Senior International Cricket filter
        if (!TeamFlagResolver.isSeniorInternationalCricketMatch(
                team1Name = t1ResolvedName,
                team2Name = t2ResolvedName,
                tournament = tournamentHeader,
                format = format
            )
        ) {
            return null
        }

        val jScore = formatCrexScoreOvers(obj.optString("j"))
        val kScore = formatCrexScoreOvers(obj.optString("k"))
        val lScore = formatCrexScoreOvers(obj.optString("l"))
        val mScore = formatCrexScoreOvers(obj.optString("m"))
        val hasAnyInningsProgress = jScore.isNotBlank() || kScore.isNotBlank() || lScore.isNotBlank() || mScore.isNotBlank() || sField.isNotBlank()

        // Accurate status resolution:
        // Never assume LIVE just because the match appeared in getLiveMatches or is today.
        val isCompleted = finishTime > 0L ||
            ifsCode.equals("C", ignoreCase = true) ||
            ifsCode.equals("F", ignoreCase = true) ||
            resText.contains(" won ", ignoreCase = true) ||
            resText.contains("abandoned", ignoreCase = true) ||
            resText.contains("no result", ignoreCase = true) ||
            resText.contains("match drawn", ignoreCase = true) ||
            resText.contains("match tied", ignoreCase = true)

        val isNotStarted = !isCompleted && (
            ifsCode.equals("NS", ignoreCase = true) ||
                (dCode == 1 && !hasAnyInningsProgress) ||
                (!hasAnyInningsProgress && startEpochMs > referenceNowEpochMs)
            )

        val isActivelyLive = !isCompleted && !isNotStarted && (
            hasAnyInningsProgress ||
                ipCode == 1 ||
                ifsCode.equals("L", ignoreCase = true) ||
                ifsCode.equals("I", ignoreCase = true) ||
                dCode == 2
            )

        val resolvedStatus: MatchStatus = when {
            isCompleted -> MatchStatus.FINISHED
            isActivelyLive -> MatchStatus.LIVE
            isNotStarted -> MatchStatus.UPCOMING
            else -> MatchStatus.UNKNOWN
        }

        // Strict Yesterday / Today / Tomorrow date window check in Asia/Dhaka timezone.
        // For multi-day Tests that are actively LIVE right now, they are playing today.
        // For FINISHED, check finishTime (if present) or startEpochMs.
        val effectiveDateEpochMs = when (resolvedStatus) {
            MatchStatus.LIVE -> if (startEpochMs > 0L && TeamFlagResolver.isWithinYesterdayTodayTomorrowDhaka(startEpochMs, referenceNowEpochMs)) {
                startEpochMs
            } else {
                referenceNowEpochMs
            }
            MatchStatus.FINISHED -> if (finishTime > 0L) finishTime else startEpochMs
            MatchStatus.UPCOMING, MatchStatus.UNKNOWN -> startEpochMs
        }

        if (effectiveDateEpochMs <= 0L || !TeamFlagResolver.isWithinYesterdayTodayTomorrowDhaka(effectiveDateEpochMs, referenceNowEpochMs)) {
            return null
        }

        val v1 = TeamFlagResolver.resolveForMatch(t1ResolvedName, t1Key, isNationalTeam = true)
        val v2 = TeamFlagResolver.resolveForMatch(t2ResolvedName, t2Key, isNationalTeam = true)

        // Score-free status summary for MatchCard (no innings scores or score badges on MatchCard)
        val cardStatusSummary = when (resolvedStatus) {
            MatchStatus.LIVE -> listOf("Live Now", format.ifBlank { "International" })
                .filter { it.isNotBlank() }
                .joinToString(" • ")
            MatchStatus.FINISHED -> listOf(
                resText.ifBlank { "Match Finished" },
                format
            ).filter { it.isNotBlank() }.joinToString(" • ")
            MatchStatus.UPCOMING -> listOf("Upcoming", format.ifBlank { "Scheduled" })
                .filter { it.isNotBlank() }
                .joinToString(" • ")
            MatchStatus.UNKNOWN -> "অনির্ধারিত • Status Pending"
        }

        val dhakaStart = formatEpochDhaka(if (startEpochMs > 0L) startEpochMs else effectiveDateEpochMs)
        val matchTimeText = buildString {
            if (dhakaStart.isNotBlank()) {
                append(dhakaStart)
            }
            if (venueKey.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append("Venue #$venueKey")
            }
        }.ifBlank { "Asia/Dhaka Time" }

        val sortRank = when (resolvedStatus) {
            MatchStatus.LIVE -> 0
            MatchStatus.UPCOMING -> 1
            MatchStatus.FINISHED -> 2
            MatchStatus.UNKNOWN -> 3
        }

        return sortRank to LiveMatch(
            id = matchKey,
            tournament = tournamentHeader,
            sport = SportType.CRICKET,
            team1Name = t1ResolvedName,
            team1Code = v1.code,
            team1FlagUrl = v1.flagUrl,
            team2Name = t2ResolvedName,
            team2Code = v2.code,
            team2FlagUrl = v2.flagUrl,
            status = resolvedStatus,
            timeOrScore = cardStatusSummary,
            matchTime = matchTimeText,
            streams = defaultCricketStreams,
            provider = "CREX",
            competitionId = seriesKey.ifBlank { "crex.intl" },
            startTimeEpochMs = if (startEpochMs > 0L) startEpochMs else effectiveDateEpochMs,
            isNationalTeam = true
        )
    }

    /**
     * Parses `POST https://oc.crickapi.com/oc/getHomeUpcomingMatchesCE` with `{"type": 1}`.
     * Handles the date-grouped response `[{"dt": "2026/10/21", "m": [...]}]`.
     */
    internal fun parseUpcomingMatchesPayload(
        rawJson: String,
        referenceNowEpochMs: Long = System.currentTimeMillis()
    ): List<LiveMatch> {
        val trimmed = rawJson.trim()
        val result = mutableListOf<LiveMatch>()
        if (!trimmed.startsWith("[")) return result

        val outerArray = JSONArray(trimmed)
        for (i in 0 until outerArray.length()) {
            val groupOrMatch = outerArray.optJSONObject(i) ?: continue
            val groupDate = groupOrMatch.optString("dt")
            val matchesArr = groupOrMatch.optJSONArray("m")
            if (matchesArr != null) {
                for (j in 0 until matchesArr.length()) {
                    val mObj = matchesArr.optJSONObject(j) ?: continue
                    parseSingleUpcomingMatchObject(mObj, groupDate, referenceNowEpochMs)?.let { result.add(it) }
                }
            } else {
                parseSingleUpcomingMatchObject(groupOrMatch, groupDate, referenceNowEpochMs)?.let { result.add(it) }
            }
        }
        return result.distinctBy { it.id }
    }

    private fun parseSingleUpcomingMatchObject(
        obj: JSONObject,
        groupDate: String,
        referenceNowEpochMs: Long
    ): LiveMatch? {
        val t1Key = obj.optString("t1f").trim()
        val t2Key = obj.optString("t2f").trim()
        if (t1Key.isBlank() && t2Key.isBlank()) return null

        val matchKey = obj.optString("mf").trim()
            .ifBlank { obj.optString("nf").trim() }
            .ifBlank { obj.optLong("id", 0L).takeIf { it > 0L }?.toString().orEmpty() }
        if (matchKey.isBlank()) return null

        val seriesKey = obj.optString("sf").removePrefix("^").trim()
        val matchNumber = obj.optString("mn").removePrefix("^").trim()
        val formatInt = obj.optInt("ft", 0)
        val formatLabel = formatCodeToLabel(formatInt)
        val venueKey = obj.optString("vf").takeIf { it != "null" }?.trim().orEmpty()
        val startEpochMs = obj.optLong("t", 0L)
        val rawDate = obj.optString("dt").ifBlank { groupDate }
        val resultNote = obj.optString("result").trim()

        val t1Name = resolveCrexTeamName(t1Key)
        val t2Name = resolveCrexTeamName(t2Key)

        val seriesMeta = seriesMetaCache[seriesKey]
        val tournamentHeader = buildList {
            if (!seriesMeta.isNullOrBlank()) add(seriesMeta)
            else if (formatLabel.isNotBlank()) add(formatLabel)
            if (seriesKey.isNotBlank()) add("Series $seriesKey")
            if (matchNumber.isNotBlank() && matchNumber != "0") add("Match #$matchNumber")
        }.joinToString(" • ").ifBlank { "International Cricket" }

        // Strict Senior International Cricket filter
        if (!TeamFlagResolver.isSeniorInternationalCricketMatch(
                team1Name = t1Name,
                team2Name = t2Name,
                tournament = tournamentHeader,
                format = formatLabel
            )
        ) {
            return null
        }

        val effectiveEpochMs = if (startEpochMs > 0L) startEpochMs else parseSourceDateEpochDhaka(rawDate)
        if (effectiveEpochMs <= 0L || !TeamFlagResolver.isWithinYesterdayTodayTomorrowDhaka(effectiveEpochMs, referenceNowEpochMs)) {
            return null
        }

        val v1 = TeamFlagResolver.resolveForMatch(t1Name, t1Key, isNationalTeam = true)
        val v2 = TeamFlagResolver.resolveForMatch(t2Name, t2Key, isNationalTeam = true)

        val dhakaTime = if (startEpochMs > 0L) {
            formatEpochDhaka(startEpochMs)
        } else {
            formatSourceDateStringDhaka(rawDate)
        }

        val matchTimeText = buildString {
            append(dhakaTime.ifBlank { rawDate })
            if (venueKey.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append("Venue #$venueKey")
            }
        }

        val resolvedStatus = if (resultNote.contains(" won ", ignoreCase = true) ||
            resultNote.contains("abandoned", ignoreCase = true)
        ) {
            MatchStatus.FINISHED
        } else {
            MatchStatus.UPCOMING
        }

        val subInfo = buildList {
            if (formatLabel.isNotBlank()) add(formatLabel)
            if (matchNumber.isNotBlank() && matchNumber != "0") add("Match $matchNumber")
            if (resultNote.isNotBlank()) add(resultNote)
        }.joinToString(" • ").ifBlank { "Upcoming" }

        return LiveMatch(
            id = matchKey,
            tournament = tournamentHeader,
            sport = SportType.CRICKET,
            team1Name = t1Name,
            team1Code = v1.code,
            team1FlagUrl = v1.flagUrl,
            team2Name = t2Name,
            team2Code = v2.code,
            team2FlagUrl = v2.flagUrl,
            status = resolvedStatus,
            timeOrScore = subInfo,
            matchTime = matchTimeText,
            streams = defaultCricketStreams,
            provider = "CREX",
            competitionId = seriesKey.ifBlank { "crex.intl" },
            startTimeEpochMs = effectiveEpochMs,
            isNationalTeam = true
        )
    }

    /**
     * Parses `POST https://oc.crickapi.com/oc/getHomeUpcomingMatchesCE` with `{"type": 0}`.
     */
    internal fun parseFinishedMatchesPayload(
        rawJson: String,
        referenceNowEpochMs: Long = System.currentTimeMillis()
    ): List<LiveMatch> {
        val trimmed = rawJson.trim()
        val result = mutableListOf<LiveMatch>()
        if (!trimmed.startsWith("[")) return result

        val arr = JSONArray(trimmed)
        // First pass: learn any winning team names directly present in "result" + "winner"
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            learnWinnerNameFromFinishedObject(obj)
        }

        // Second pass: normalize into LiveMatch cards
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val nestedM = obj.optJSONArray("m")
            if (nestedM != null) {
                for (j in 0 until nestedM.length()) {
                    val item = nestedM.optJSONObject(j) ?: continue
                    learnWinnerNameFromFinishedObject(item)
                    parseSingleFinishedMatchObject(item, referenceNowEpochMs)?.let { result.add(it) }
                }
            } else {
                parseSingleFinishedMatchObject(obj, referenceNowEpochMs)?.let { result.add(it) }
            }
        }
        return result.distinctBy { it.id }
    }

    private fun learnWinnerNameFromFinishedObject(obj: JSONObject) {
        val winnerKey = obj.optString("winner").trim()
        val resText = obj.optString("result").trim()
        if (winnerKey.isNotBlank() && resText.contains(" won ", ignoreCase = true)) {
            val winnerName = resText.substringBefore(" won ", "").trim()
            if (winnerName.length >= 3) {
                teamNameCache.putIfAbsent(winnerKey, winnerName)
            }
        }
    }

    private fun parseSingleFinishedMatchObject(
        obj: JSONObject,
        referenceNowEpochMs: Long
    ): LiveMatch? {
        val t1Key = obj.optString("t1f").trim()
        val t2Key = obj.optString("t2f").trim()
        if (t1Key.isBlank() && t2Key.isBlank()) return null

        val matchKey = obj.optString("mfkey").trim()
            .ifBlank { obj.optString("mf").trim() }
            .ifBlank { "$t1Key-$t2Key-${obj.optString("date")}" }

        val seriesKey = obj.optString("sfkey").ifBlank { obj.optString("sf") }.removePrefix("^").trim()
        val matchNumber = obj.optString("match_number").ifBlank { obj.optString("mn") }.removePrefix("^").trim()
        val formatInt = obj.optInt("ft", 0)
        val formatLabel = formatCodeToLabel(formatInt)
        val venueKey = obj.optString("vf").takeIf { it != "null" }?.trim().orEmpty()
        val dateStr = obj.optString("date").ifBlank { obj.optString("dt") }.trim()
        val resultText = obj.optString("result").trim()
        val startEpochMs = obj.optLong("t", 0L).takeIf { it > 0L } ?: parseSourceDateEpochDhaka(dateStr)

        val t1Name = resolveCrexTeamName(t1Key)
        val t2Name = resolveCrexTeamName(t2Key)

        val seriesMeta = seriesMetaCache[seriesKey]
        val tournamentHeader = buildList {
            if (!seriesMeta.isNullOrBlank()) add(seriesMeta)
            else if (formatLabel.isNotBlank()) add(formatLabel)
            if (seriesKey.isNotBlank()) add("Series $seriesKey")
            if (matchNumber.isNotBlank() && matchNumber != "0") add("Match #$matchNumber")
        }.joinToString(" • ").ifBlank { "International Cricket" }

        // Strict Senior International Cricket filter
        if (!TeamFlagResolver.isSeniorInternationalCricketMatch(
                team1Name = t1Name,
                team2Name = t2Name,
                tournament = tournamentHeader,
                format = formatLabel
            )
        ) {
            return null
        }

        // Strict Yesterday / Today / Tomorrow date window check in Asia/Dhaka
        if (startEpochMs <= 0L || !TeamFlagResolver.isWithinYesterdayTodayTomorrowDhaka(startEpochMs, referenceNowEpochMs)) {
            return null
        }

        val v1 = TeamFlagResolver.resolveForMatch(t1Name, t1Key, isNationalTeam = true)
        val v2 = TeamFlagResolver.resolveForMatch(t2Name, t2Key, isNationalTeam = true)

        // Score-free status summary for MatchCard (never show innings scores on MatchCard)
        val statusSummary = buildList {
            if (resultText.isNotBlank()) add(resultText) else add("Match Finished")
            if (formatLabel.isNotBlank()) add(formatLabel)
        }.joinToString(" • ")

        val dhakaDate = formatSourceDateStringDhaka(dateStr)
        val matchTimeText = buildString {
            if (dhakaDate.isNotBlank()) append(dhakaDate)
            if (venueKey.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append("Venue #$venueKey")
            }
        }.ifBlank { "Finished" }

        return LiveMatch(
            id = matchKey,
            tournament = tournamentHeader,
            sport = SportType.CRICKET,
            team1Name = t1Name,
            team1Code = v1.code,
            team1FlagUrl = v1.flagUrl,
            team2Name = t2Name,
            team2Code = v2.code,
            team2FlagUrl = v2.flagUrl,
            status = MatchStatus.FINISHED,
            timeOrScore = statusSummary,
            matchTime = matchTimeText,
            streams = defaultCricketStreams,
            provider = "CREX",
            competitionId = seriesKey.ifBlank { "crex.intl" },
            startTimeEpochMs = startEpochMs,
            isNationalTeam = true
        )
    }

    /**
     * Parses `GET https://api.goscorer.com/api/v3/getSV3?key={matchId}` on-demand.
     */
    internal fun parseSv3DetailPayload(
        matchId: String,
        rawJson: String,
        fetchedAtEpochMs: Long
    ): CrexMatchDetail {
        val obj = JSONObject(rawJson)

        // 1. Extract speech_names dictionary if present and update shared teamNameCache
        val speechObj = obj.optJSONObject("speech_names")
        if (speechObj != null) {
            val sKeys = speechObj.keys()
            while (sKeys.hasNext()) {
                val k = sKeys.next().trim()
                val v = speechObj.optString(k).trim()
                if (k.isNotBlank() && v.isNotBlank()) {
                    teamNameCache[k] = v
                }
            }
        }

        // 2. Extract team keys from "a" (e.g. "P.Q") or "F" (e.g. "^P.^Q") or speech_names keys
        val aField = obj.optString("a").ifBlank { obj.optString("F") }
        val parts = aField.split(".")
            .map { it.removePrefix("^").trim() }
            .filter { it.isNotBlank() }

        val t1Key = parts.getOrNull(0) ?: speechObj?.keys()?.asSequence()?.firstOrNull().orEmpty()
        val t2Key = parts.getOrNull(1) ?: speechObj?.keys()?.asSequence()?.drop(1)?.firstOrNull().orEmpty()

        val t1Name = speechObj?.optString(t1Key)?.takeIf { it.isNotBlank() } ?: resolveCrexTeamName(t1Key)
        val t2Name = speechObj?.optString(t2Key)?.takeIf { it.isNotBlank() } ?: resolveCrexTeamName(t2Key)

        val format = obj.optString("fo").trim()
        val matchNumber = obj.optString("mn").removePrefix("^").trim()
        val seriesKey = obj.optString("sf").removePrefix("^").trim()

        val jScore = formatCrexScoreOvers(obj.optString("j"))
        val kScore = formatCrexScoreOvers(obj.optString("k"))

        val startEpoch = obj.optLong("mt", 0L)
            .takeIf { it > 0L }
            ?: obj.optLong("fdst", 0L).takeIf { it > 0L }
            ?: obj.optLong("sd", 0L)
        val startTimeDhaka = formatEpochDhaka(startEpoch)

        // Parse recent overs from "rb" array if present
        val recentOvers = mutableListOf<String>()
        val rbArr = obj.optJSONArray("rb")
        if (rbArr != null) {
            for (i in 0 until rbArr.length()) {
                val overObj = rbArr.optJSONObject(i) ?: continue
                val overNum = overObj.optInt("o", -1)
                val overRuns = overObj.optInt("r", 0)
                val teamScoreAfter = overObj.optString("ts").trim()
                val ballsArr = overObj.optJSONArray("b")
                val ballsText = mutableListOf<String>()
                if (ballsArr != null) {
                    for (bIdx in 0 until ballsArr.length()) {
                        val bObj = ballsArr.optJSONObject(bIdx) ?: continue
                        val u = bObj.optString("u").trim()
                        if (u.isNotBlank()) ballsText.add(u)
                    }
                }
                if (overNum >= 0) {
                    val summary = buildString {
                        append("Over ${overNum + 1}: ")
                        if (ballsText.isNotEmpty()) {
                            append(ballsText.joinToString(" "))
                            append(" ")
                        }
                        append("($overRuns runs")
                        if (teamScoreAfter.isNotBlank()) append(", $teamScoreAfter")
                        append(")")
                    }
                    recentOvers.add(summary)
                }
            }
        }

        // Parse commentary languages from "cl" array if present
        val languages = mutableListOf<String>()
        val clArr = obj.optJSONArray("cl")
        if (clArr != null) {
            for (i in 0 until clArr.length()) {
                val langObj = clArr.optJSONObject(i) ?: continue
                val name = langObj.optString("name").trim()
                if (name.isNotBlank()) languages.add(name)
            }
        }

        val statusSummary = buildString {
            if (jScore.isNotBlank()) append("$t1Name: $jScore")
            if (kScore.isNotBlank()) {
                if (isNotEmpty()) append(" | ")
                append("$t2Name: $kScore")
            }
        }.ifBlank { "Match Key: $matchId" }

        return CrexMatchDetail(
            matchId = matchId,
            team1Key = t1Key,
            team1Name = t1Name,
            team2Key = t2Key,
            team2Name = t2Name,
            format = format,
            matchNumber = matchNumber,
            seriesKey = seriesKey,
            team1Score = jScore,
            team2Score = kScore,
            startTimeDhaka = startTimeDhaka,
            statusText = statusSummary,
            recentOvers = recentOvers,
            commentaryLanguages = languages,
            fetchedAtDhaka = formatEpochDhaka(fetchedAtEpochMs, includeSeconds = true)
        )
    }

    private fun resolveCrexTeamName(teamKey: String): String {
        val clean = teamKey.removePrefix("^").trim()
        if (clean.isBlank()) return "TBD"
        return teamNameCache[clean] ?: "Team $clean"
    }

    private fun formatCrexScoreOvers(raw: String): String {
        val clean = raw.trim().removePrefix("!")
        if (clean.isBlank() || clean == "0" || clean == "null") return ""
        return if (clean.contains("(") && !clean.endsWith(")")) {
            val parts = clean.split("(")
            val scorePart = parts[0].trim()
            val oversPart = parts.getOrNull(1)?.removeSuffix(")")?.trim().orEmpty()
            if (oversPart.isNotBlank()) "$scorePart ($oversPart ov)" else scorePart
        } else {
            clean
        }
    }

    private fun formatCodeToLabel(ft: Int): String {
        return when (ft) {
            1 -> "ODI"
            2 -> "T20I"
            3 -> "Test"
            4 -> "T10"
            else -> ""
        }
    }
}
