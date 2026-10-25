package com.example.data.espn

import com.example.data.model.FootballCompetitionOption
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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

data class EspnFootballFeedState(
    val matches: List<LiveMatch> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isStale: Boolean = false,
    val lastUpdatedEpochMs: Long? = null,
    val lastUpdatedDhakaText: String = "Never"
)

/**
 * Backend Gateway & Service for Football data via ESPN Soccer Scoreboard API:
 * `https://site.api.espn.com/apis/site/v2/sports/soccer/{leagueId}/scoreboard`
 */
class EspnFootballService(
    baseClient: OkHttpClient? = null
) {
    companion object {
        const val LOCAL_API_BASE = "https://app.local"
        const val ROUTE_SCOREBOARD_PREFIX = "/api/football/scoreboard/"
        private const val UPSTREAM_ESPN_BASE = "https://site.api.espn.com/apis/site/v2/sports/soccer"

        const val CACHE_TTL_MS = 45_000L
        val DHAKA_TIMEZONE: TimeZone = TimeZone.getTimeZone("Asia/Dhaka")
        private val UTC_TIMEZONE: TimeZone = TimeZone.getTimeZone("UTC")

        /**
         * All verified ESPN senior international national-team and senior first-team club competitions.
         * Every leagueId was verified against `site.api.espn.com/apis/site/v2/sports/soccer/{leagueId}/scoreboard`.
         */
        val VERIFIED_COMPETITIONS: List<FootballCompetitionOption> = listOf(
            // Required International & Major Tournaments
            FootballCompetitionOption("uefa.champions", "UEFA Champions League", "Major Club & Continental", isNationalTeam = false),
            FootballCompetitionOption("fifa.worldq.uefa", "FIFA World Cup Qualifiers — UEFA", "International", isNationalTeam = true),
            FootballCompetitionOption("fifa.worldq.conmebol", "FIFA World Cup Qualifiers — CONMEBOL", "International", isNationalTeam = true),
            FootballCompetitionOption("uefa.nations", "UEFA Nations League", "International", isNationalTeam = true),

            // Required Major Club Leagues (Big Five)
            FootballCompetitionOption("eng.1", "English Premier League", "Top Club Leagues", isNationalTeam = false),
            FootballCompetitionOption("esp.1", "La Liga", "Top Club Leagues", isNationalTeam = false),
            FootballCompetitionOption("ita.1", "Serie A", "Top Club Leagues", isNationalTeam = false),
            FootballCompetitionOption("ger.1", "Bundesliga", "Top Club Leagues", isNationalTeam = false),
            FootballCompetitionOption("fra.1", "Ligue 1", "Top Club Leagues", isNationalTeam = false),

            // Additional Verified Senior International National-Team Competitions
            FootballCompetitionOption("fifa.world", "FIFA World Cup", "International", isNationalTeam = true),
            FootballCompetitionOption("fifa.worldq.concacaf", "FIFA World Cup Qualifiers — CONCACAF", "International", isNationalTeam = true),
            FootballCompetitionOption("fifa.worldq.afc", "FIFA World Cup Qualifiers — AFC", "International", isNationalTeam = true),
            FootballCompetitionOption("fifa.worldq.caf", "FIFA World Cup Qualifiers — CAF", "International", isNationalTeam = true),
            FootballCompetitionOption("fifa.worldq.ofc", "FIFA World Cup Qualifiers — OFC", "International", isNationalTeam = true),
            FootballCompetitionOption("fifa.friendly", "International Friendly", "International", isNationalTeam = true),
            FootballCompetitionOption("conmebol.america", "Copa América", "International", isNationalTeam = true),
            FootballCompetitionOption("uefa.euro", "UEFA European Championship", "International", isNationalTeam = true),
            FootballCompetitionOption("caf.nations", "Africa Cup of Nations", "International", isNationalTeam = true),
            FootballCompetitionOption("concacaf.gold", "CONCACAF Gold Cup", "International", isNationalTeam = true),

            // Additional Verified Continental & Senior First-Team Professional Club Competitions
            FootballCompetitionOption("uefa.europa", "UEFA Europa League", "Major Club & Continental", isNationalTeam = false),
            FootballCompetitionOption("uefa.europa.conf", "UEFA Conference League", "Major Club & Continental", isNationalTeam = false),
            FootballCompetitionOption("conmebol.libertadores", "CONMEBOL Libertadores", "Major Club & Continental", isNationalTeam = false),
            FootballCompetitionOption("conmebol.sudamericana", "CONMEBOL Sudamericana", "Major Club & Continental", isNationalTeam = false),
            FootballCompetitionOption("afc.cup", "AFC Champions League Two", "Major Club & Continental", isNationalTeam = false),
            FootballCompetitionOption("ned.1", "Dutch Eredivisie", "Professional Club Leagues", isNationalTeam = false),
            FootballCompetitionOption("por.1", "Portuguese Primeira Liga", "Professional Club Leagues", isNationalTeam = false),
            FootballCompetitionOption("bel.1", "Belgian Pro League", "Professional Club Leagues", isNationalTeam = false),
            FootballCompetitionOption("tur.1", "Turkish Süper Lig", "Professional Club Leagues", isNationalTeam = false),
            FootballCompetitionOption("sco.1", "Scottish Premiership", "Professional Club Leagues", isNationalTeam = false),
            FootballCompetitionOption("sui.1", "Swiss Super League", "Professional Club Leagues", isNationalTeam = false),
            FootballCompetitionOption("aut.1", "Austrian Bundesliga", "Professional Club Leagues", isNationalTeam = false),
            FootballCompetitionOption("gre.1", "Greek Super League", "Professional Club Leagues", isNationalTeam = false),
            FootballCompetitionOption("bra.1", "Brazilian Série A", "Professional Club Leagues", isNationalTeam = false),
            FootballCompetitionOption("arg.1", "Argentine Liga Profesional", "Professional Club Leagues", isNationalTeam = false),
            FootballCompetitionOption("usa.1", "Major League Soccer (MLS)", "Professional Club Leagues", isNationalTeam = false),
            FootballCompetitionOption("mex.1", "Mexican Liga MX", "Professional Club Leagues", isNationalTeam = false),
            FootballCompetitionOption("ksa.1", "Saudi Pro League", "Professional Club Leagues", isNationalTeam = false),
            FootballCompetitionOption("jpn.1", "Japanese J1 League", "Professional Club Leagues", isNationalTeam = false),
            FootballCompetitionOption("aus.1", "Australian A-League Men", "Professional Club Leagues", isNationalTeam = false),
            FootballCompetitionOption("ind.1", "Indian Super League", "Professional Club Leagues", isNationalTeam = false),
            FootballCompetitionOption("eng.fa", "English FA Cup", "Domestic Club Cups", isNationalTeam = false),
            FootballCompetitionOption("esp.copa_del_rey", "Spanish Copa del Rey", "Domestic Club Cups", isNationalTeam = false),
            FootballCompetitionOption("ita.coppa_italia", "Coppa Italia", "Domestic Club Cups", isNationalTeam = false),
            FootballCompetitionOption("ger.dfb_pokal", "German DFB-Pokal", "Domestic Club Cups", isNationalTeam = false),
            FootballCompetitionOption("fra.coupe_de_france", "Coupe de France", "Domestic Club Cups", isNationalTeam = false)
        )

        private val competitionById: Map<String, FootballCompetitionOption> =
            VERIFIED_COMPETITIONS.associateBy { it.leagueId }

        fun isNationalTeamLeague(leagueId: String): Boolean {
            return competitionById[leagueId]?.isNationalTeam ?: leagueId.startsWith("fifa.")
        }

        fun formatEpochDhakaDateAndTime(epochMs: Long): String {
            if (epochMs <= 0L) return ""
            val timeFmt = SimpleDateFormat("hh:mm a", Locale.US).apply { timeZone = DHAKA_TIMEZONE }
            val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = DHAKA_TIMEZONE }
            val dt = Date(epochMs)
            return "${timeFmt.format(dt)} | ${dateFmt.format(dt)}"
        }

        fun formatTimestampDhaka(epochMs: Long): String {
            if (epochMs <= 0L) return "Never"
            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a 'BST'", Locale.US).apply {
                timeZone = DHAKA_TIMEZONE
            }
            return sdf.format(Date(epochMs))
        }

        fun buildDhakaDateRangeParam(referenceNowEpochMs: Long = System.currentTimeMillis()): String {
            val (startMs, endMs) = TeamFlagResolver.getDhakaThreeDayWindowBounds(referenceNowEpochMs)
            // Expand query by 1 day in UTC on each side so UTC vs Asia/Dhaka (+6) boundary matches are never missed,
            // then filter strictly by startMs..endMs in Asia/Dhaka after parsing.
            val queryStartCal = Calendar.getInstance(UTC_TIMEZONE, Locale.US).apply {
                timeInMillis = startMs - 12 * 3600_000L
            }
            val queryEndCal = Calendar.getInstance(UTC_TIMEZONE, Locale.US).apply {
                timeInMillis = endMs + 12 * 3600_000L
            }
            val fmt = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = UTC_TIMEZONE }
            return "${fmt.format(queryStartCal.time)}-${fmt.format(queryEndCal.time)}"
        }

        fun parseIsoUtcEpochMs(isoText: String): Long {
            val trimmed = isoText.trim()
            if (trimmed.isBlank()) return 0L
            val patterns = listOf(
                "yyyy-MM-dd'T'HH:mm'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
            )
            for (pattern in patterns) {
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                        timeZone = UTC_TIMEZONE
                    }
                    val parsed = sdf.parse(trimmed)
                    if (parsed != null) return parsed.time
                } catch (_: Exception) {
                }
            }
            return 0L
        }
    }

    private data class CachedLeaguePayload(
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
    private val leagueCache = ConcurrentHashMap<String, CachedLeaguePayload>()
    private val inFlightCalls = ConcurrentHashMap<String, Deferred<Result<CachedLeaguePayload>>>()
    private val routeErrors = ConcurrentHashMap<String, RouteErrorState>()

    @Volatile
    private var lastGoodFeed: List<LiveMatch> = emptyList()

    @Volatile
    private var lastSyncEpochMs: Long? = null

    @Volatile
    private var lastErrorMessage: String? = null

    @Volatile
    private var isLastFeedStale: Boolean = false

    private val backendGatewayInterceptor = Interceptor { chain ->
        val original = chain.request()
        val url = original.url
        if (url.host == "app.local" && url.encodedPath.startsWith(ROUTE_SCOREBOARD_PREFIX)) {
            val leagueId = url.encodedPath.removePrefix(ROUTE_SCOREBOARD_PREFIX).trim()
            val datesParam = url.queryParameter("dates").orEmpty()
            val upstreamUrl = if (datesParam.isNotBlank()) {
                "$UPSTREAM_ESPN_BASE/$leagueId/scoreboard?dates=$datesParam"
            } else {
                "$UPSTREAM_ESPN_BASE/$leagueId/scoreboard"
            }
            val upstreamReq = Request.Builder()
                .url(upstreamUrl)
                .get()
                .header("User-Agent", "Mozilla/5.0 (Android; SportsLiveTV)")
                .header("Accept", "application/json")
                .build()
            chain.proceed(upstreamReq)
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

    fun getDiagnostics(enabled: Boolean): ProviderSyncDiagnostics {
        val syncEpoch = lastSyncEpochMs
        return ProviderSyncDiagnostics(
            providerName = "ESPN Football Scoreboard",
            enabled = enabled,
            lastSyncEpochMs = syncEpoch,
            lastSyncDhakaText = syncEpoch?.let { formatTimestampDhaka(it) } ?: "Never",
            statusText = when {
                !enabled -> "Disabled by Admin"
                isLastFeedStale -> "Stale Cache Fallback (${lastGoodFeed.size} matches)"
                lastErrorMessage != null && lastGoodFeed.isEmpty() -> "Error"
                syncEpoch != null -> "Synced (${lastGoodFeed.size} matches)"
                else -> "Ready"
            },
            lastError = lastErrorMessage,
            activeMatchCount = lastGoodFeed.size,
            isStale = isLastFeedStale
        )
    }

    suspend fun fetchScoreboardMatches(
        enabledLeagues: Set<String>,
        forceRefresh: Boolean = false,
        referenceNowEpochMs: Long = System.currentTimeMillis()
    ): EspnFootballFeedState = withContext(Dispatchers.IO) {
        val validLeagues = VERIFIED_COMPETITIONS
            .map { it.leagueId }
            .filter { it in enabledLeagues }

        if (validLeagues.isEmpty()) {
            lastGoodFeed = emptyList()
            lastSyncEpochMs = System.currentTimeMillis()
            lastErrorMessage = null
            isLastFeedStale = false
            return@withContext EspnFootballFeedState(
                matches = emptyList(),
                isLoading = false,
                errorMessage = null,
                isStale = false,
                lastUpdatedEpochMs = lastSyncEpochMs,
                lastUpdatedDhakaText = formatTimestampDhaka(lastSyncEpochMs ?: 0L)
            )
        }

        val datesQuery = buildDhakaDateRangeParam(referenceNowEpochMs)
        val deferredResults = validLeagues.map { leagueId ->
            serviceScope.async {
                leagueId to fetchSingleLeagueScoreboard(
                    leagueId = leagueId,
                    datesQuery = datesQuery,
                    forceRefresh = forceRefresh
                )
            }
        }.awaitAll()

        val allParsedMatches = mutableListOf<LiveMatch>()
        var successCount = 0
        var firstError: String? = null

        for ((leagueId, result) in deferredResults) {
            result.fold(
                onSuccess = { entry ->
                    successCount++
                    val parsed = runCatching {
                        parseScoreboardJson(
                            leagueId = leagueId,
                            rawJson = entry.rawBody,
                            referenceNowEpochMs = referenceNowEpochMs
                        )
                    }.getOrDefault(emptyList())
                    allParsedMatches.addAll(parsed)
                },
                onFailure = { err ->
                    if (firstError == null) {
                        firstError = "${competitionById[leagueId]?.name ?: leagueId}: ${err.localizedMessage ?: "Request failed"}"
                    }
                    // Use cached league data if available
                    val cached = leagueCache[leagueId]
                    if (cached != null) {
                        val staleParsed = runCatching {
                            parseScoreboardJson(
                                leagueId = leagueId,
                                rawJson = cached.rawBody,
                                referenceNowEpochMs = referenceNowEpochMs
                            ).map { it.copy(isStale = true) }
                        }.getOrDefault(emptyList())
                        allParsedMatches.addAll(staleParsed)
                    }
                }
            )
        }

        val deduplicated = allParsedMatches
            .distinctBy { it.id }
            .sortedWith(
                compareBy<LiveMatch> {
                    when (it.status) {
                        MatchStatus.LIVE -> 0
                        MatchStatus.UPCOMING -> 1
                        MatchStatus.FINISHED -> 2
                        MatchStatus.UNKNOWN -> 3
                    }
                }.thenBy { it.startTimeEpochMs }
            )

        if (successCount > 0) {
            val now = System.currentTimeMillis()
            lastGoodFeed = deduplicated
            lastSyncEpochMs = now
            lastErrorMessage = firstError
            isLastFeedStale = false
            EspnFootballFeedState(
                matches = deduplicated,
                isLoading = false,
                errorMessage = firstError,
                isStale = false,
                lastUpdatedEpochMs = now,
                lastUpdatedDhakaText = formatTimestampDhaka(now)
            )
        } else {
            // Total network failure: preserve last good feed as stale instead of wiping data
            isLastFeedStale = lastGoodFeed.isNotEmpty()
            lastErrorMessage = firstError ?: "Failed to reach ESPN scoreboard endpoints"
            val staleList = lastGoodFeed.map { it.copy(isStale = true) }
            EspnFootballFeedState(
                matches = staleList,
                isLoading = false,
                errorMessage = lastErrorMessage,
                isStale = staleList.isNotEmpty(),
                lastUpdatedEpochMs = lastSyncEpochMs,
                lastUpdatedDhakaText = lastSyncEpochMs?.let { formatTimestampDhaka(it) } ?: "Never"
            )
        }
    }

    private suspend fun fetchSingleLeagueScoreboard(
        leagueId: String,
        datesQuery: String,
        forceRefresh: Boolean
    ): Result<CachedLeaguePayload> {
        val now = System.currentTimeMillis()
        val cacheKey = "$leagueId|$datesQuery"
        val cached = leagueCache[leagueId]
        if (cached != null) {
            val age = now - cached.fetchedAtEpochMs
            val effectiveTtl = if (forceRefresh) 3_000L else CACHE_TTL_MS
            if (age in 0 until effectiveTtl) {
                return Result.success(cached)
            }
        }

        val errState = routeErrors[leagueId]
        if (errState != null && now < errState.backoffUntilEpochMs && !forceRefresh) {
            return Result.failure(IOException(errState.lastErrorMessage ?: "Backing off ESPN $leagueId"))
        }

        val deferred: Deferred<Result<CachedLeaguePayload>> = dedupMutex.withLock {
            val existing = inFlightCalls[cacheKey]
            if (existing != null && existing.isActive) {
                existing
            } else {
                val created = serviceScope.async {
                    try {
                        val routeUrl = "$LOCAL_API_BASE$ROUTE_SCOREBOARD_PREFIX$leagueId?dates=$datesQuery"
                        val req = Request.Builder().url(routeUrl).get().build()
                        httpClient.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) {
                                throw IOException("HTTP ${resp.code} for ESPN $leagueId")
                            }
                            val body = resp.body?.string()?.trim().orEmpty()
                            if (body.isBlank()) {
                                throw IOException("Empty response for ESPN $leagueId")
                            }
                            val payload = CachedLeaguePayload(body, System.currentTimeMillis())
                            leagueCache[leagueId] = payload
                            routeErrors.remove(leagueId)
                            Result.success(payload)
                        }
                    } catch (e: Exception) {
                        val failures = (routeErrors[leagueId]?.consecutiveFailures ?: 0) + 1
                        val backoffSec = min(60L, 5L * (1L shl (failures - 1).coerceAtMost(4)))
                        routeErrors[leagueId] = RouteErrorState(
                            consecutiveFailures = failures,
                            backoffUntilEpochMs = System.currentTimeMillis() + backoffSec * 1000L,
                            lastErrorMessage = e.localizedMessage ?: "ESPN error"
                        )
                        Result.failure(e)
                    } finally {
                        dedupMutex.withLock {
                            inFlightCalls.remove(cacheKey)
                        }
                    }
                }
                inFlightCalls[cacheKey] = created
                created
            }
        }
        return deferred.await()
    }

    /**
     * Parses ESPN scoreboard JSON for a given `leagueId`:
     * - Enforces Yesterday / Today / Tomorrow window in `Asia/Dhaka`
     * - Filters out U17, U18, U19, U20, U21, Youth, Academy, Reserve, B-team, Amateur, Virtual/Esports
     * - Uses Club Crests (`team.logo`) for Club Football (never national flags)
     * - Uses National Flags for National-Team Football
     * - Maps status strictly (`"in"` -> `LIVE`, `"post"` -> `FINISHED`, `"pre"` -> `UPCOMING`, otherwise `UNKNOWN`)
     * - Never puts scores onto `MatchCard`
     */
    internal fun parseScoreboardJson(
        leagueId: String,
        rawJson: String,
        referenceNowEpochMs: Long = System.currentTimeMillis()
    ): List<LiveMatch> {
        val root = JSONObject(rawJson)
        val leaguesArr = root.optJSONArray("leagues")
        val leagueObj = leaguesArr?.optJSONObject(0)
        val leagueName = leagueObj?.optString("name")?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: competitionById[leagueId]?.name
            ?: leagueId.uppercase(Locale.US)

        val isNational = isNationalTeamLeague(leagueId)

        // Reject if entire league is youth/reserve/amateur/esports
        if (TeamFlagResolver.isYouthOrNonSeniorEvent("", "", leagueName)) {
            return emptyList()
        }

        val eventsArr = root.optJSONArray("events") ?: return emptyList()
        val result = mutableListOf<LiveMatch>()

        for (i in 0 until eventsArr.length()) {
            val ev = eventsArr.optJSONObject(i) ?: continue
            val eventId = ev.optString("id").trim()
            if (eventId.isBlank()) continue

            val eventName = ev.optString("name").trim()
            val dateIso = ev.optString("date").trim()
            val compArr = ev.optJSONArray("competitions")
            val compObj = compArr?.optJSONObject(0)
            val compDateIso = compObj?.optString("date")?.trim().orEmpty()
            val startEpochMs = parseIsoUtcEpochMs(dateIso.ifBlank { compDateIso })

            // Rule 4: Strictly keep ONLY Yesterday, Today, and Tomorrow in Asia/Dhaka timezone
            if (!TeamFlagResolver.isWithinYesterdayTodayTomorrowDhaka(startEpochMs, referenceNowEpochMs)) {
                continue
            }

            val competitorsArr = compObj?.optJSONArray("competitors") ?: continue
            if (competitorsArr.length() < 2) continue

            var homeObj: JSONObject? = null
            var awayObj: JSONObject? = null
            for (cIdx in 0 until competitorsArr.length()) {
                val c = competitorsArr.optJSONObject(cIdx) ?: continue
                val ha = c.optString("homeAway").lowercase(Locale.US)
                if (ha == "home" && homeObj == null) homeObj = c
                else if (ha == "away" && awayObj == null) awayObj = c
            }
            val firstComp = homeObj ?: competitorsArr.optJSONObject(0) ?: continue
            val secondComp = awayObj ?: competitorsArr.optJSONObject(1) ?: continue

            val team1Obj = firstComp.optJSONObject("team") ?: JSONObject()
            val team2Obj = secondComp.optJSONObject("team") ?: JSONObject()

            val t1Name = team1Obj.optString("displayName")
                .ifBlank { team1Obj.optString("shortDisplayName") }
                .ifBlank { team1Obj.optString("name") }
                .trim()
            val t2Name = team2Obj.optString("displayName")
                .ifBlank { team2Obj.optString("shortDisplayName") }
                .ifBlank { team2Obj.optString("name") }
                .trim()

            if (t1Name.isBlank() || t2Name.isBlank() ||
                t1Name.equals("TBD", ignoreCase = true) ||
                t2Name.equals("TBD", ignoreCase = true)
            ) {
                continue
            }

            // Rule 2: Exclude U17, U18, U19, U20, U21, Youth, Academy, Reserve, B-team, Amateur, Virtual/Esports
            if (TeamFlagResolver.isYouthOrNonSeniorEvent(t1Name, t2Name, "$leagueName $eventName")) {
                continue
            }

            val t1Abbr = team1Obj.optString("abbreviation").trim()
                .ifBlank { t1Name.take(3).uppercase(Locale.US) }
            val t2Abbr = team2Obj.optString("abbreviation").trim()
                .ifBlank { t2Name.take(3).uppercase(Locale.US) }

            val t1EspnLogo = extractTeamLogoUrl(team1Obj)
            val t2EspnLogo = extractTeamLogoUrl(team2Obj)

            // Rule 5: Club Football uses club crests ONLY (never country flags).
            // National-Team Football uses official national flags.
            val t1FlagOrCrest: String
            val t2FlagOrCrest: String
            val t1DisplayCode: String
            val t2DisplayCode: String

            if (isNational) {
                val v1 = TeamFlagResolver.resolve(t1Name, t1Abbr, t1EspnLogo)
                val v2 = TeamFlagResolver.resolve(t2Name, t2Abbr, t2EspnLogo)
                t1FlagOrCrest = v1.flagUrl
                t2FlagOrCrest = v2.flagUrl
                t1DisplayCode = t1Name
                t2DisplayCode = t2Name
            } else {
                // Club Football: use ESPN club crest URL directly; never fall back to national flag
                t1FlagOrCrest = t1EspnLogo
                t2FlagOrCrest = t2EspnLogo
                t1DisplayCode = t1Name
                t2DisplayCode = t2Name
            }

            // Rule 4: Parse status strictly from provider response
            val statusObj = compObj.optJSONObject("status") ?: ev.optJSONObject("status")
            val matchStatus = parseEspnMatchStatus(statusObj)

            val dhakaKickoff = formatEpochDhakaDateAndTime(startEpochMs)
            val displayClock = statusObj?.optString("displayClock")?.trim().orEmpty()

            // Rule 5: Never put scores in MatchCard fields!
            val statusCountdownOrClock = when (matchStatus) {
                MatchStatus.LIVE -> {
                    if (displayClock.isNotBlank() && displayClock != "0'" && displayClock != "0:00") {
                        "LIVE • $displayClock"
                    } else {
                        "LIVE"
                    }
                }
                MatchStatus.UPCOMING -> formatStartsInText(startEpochMs, referenceNowEpochMs)
                MatchStatus.FINISHED -> "Finished"
                MatchStatus.UNKNOWN -> "অনির্ধারিত"
            }

            result.add(
                LiveMatch(
                    id = "espn-$eventId",
                    tournament = "FOOTBALL | ${leagueName.uppercase(Locale.US)}",
                    sport = SportType.FOOTBALL,
                    team1Name = t1Name,
                    team1Code = t1DisplayCode,
                    team1FlagUrl = t1FlagOrCrest,
                    team2Name = t2Name,
                    team2Code = t2DisplayCode,
                    team2FlagUrl = t2FlagOrCrest,
                    status = matchStatus,
                    timeOrScore = statusCountdownOrClock,
                    matchTime = dhakaKickoff,
                    streams = emptyList<StreamSource>(),
                    provider = "ESPN",
                    competitionId = leagueId,
                    startTimeEpochMs = startEpochMs,
                    isNationalTeam = isNational
                )
            )
        }

        return result
    }

    internal fun parseEspnMatchStatus(statusObj: JSONObject?): MatchStatus {
        if (statusObj == null) return MatchStatus.UNKNOWN
        val typeObj = statusObj.optJSONObject("type") ?: return MatchStatus.UNKNOWN
        val state = typeObj.optString("state").trim().lowercase(Locale.US)
        val completed = typeObj.optBoolean("completed", false)
        val typeName = typeObj.optString("name").trim().uppercase(Locale.US)

        // Postponed, canceled, abandoned, or suspended matches must NOT be marked LIVE or UPCOMING
        if (typeName.contains("POSTPONED") ||
            typeName.contains("CANCELED") ||
            typeName.contains("CANCELLED") ||
            typeName.contains("ABANDONED") ||
            typeName.contains("SUSPENDED") ||
            typeName.contains("TBD")
        ) {
            return MatchStatus.UNKNOWN
        }

        return when {
            completed || state == "post" || typeName.contains("FINAL") || typeName.contains("FULL_TIME") -> {
                MatchStatus.FINISHED
            }
            state == "in" -> {
                MatchStatus.LIVE
            }
            state == "pre" || typeName == "STATUS_SCHEDULED" -> {
                MatchStatus.UPCOMING
            }
            else -> {
                MatchStatus.UNKNOWN
            }
        }
    }

    private fun extractTeamLogoUrl(teamObj: JSONObject): String {
        val direct = teamObj.optString("logo").trim()
        if (direct.startsWith("http://", ignoreCase = true) || direct.startsWith("https://", ignoreCase = true)) {
            return direct
        }
        val logosArr = teamObj.optJSONArray("logos")
        if (logosArr != null && logosArr.length() > 0) {
            val firstHref = logosArr.optJSONObject(0)?.optString("href")?.trim().orEmpty()
            if (firstHref.startsWith("http://", ignoreCase = true) || firstHref.startsWith("https://", ignoreCase = true)) {
                return firstHref
            }
        }
        return ""
    }

    private fun formatStartsInText(startEpochMs: Long, nowEpochMs: Long): String {
        val diffMs = startEpochMs - nowEpochMs
        if (diffMs <= 0L) return "Starting soon"
        val totalMinutes = max(1L, diffMs / 60_000L)
        val hours = totalMinutes / 60L
        val mins = totalMinutes % 60L
        return when {
            hours >= 24L -> {
                val days = hours / 24L
                val remHrs = hours % 24L
                if (remHrs > 0L) "Starts in ${days}d ${remHrs}h" else "Starts in ${days}d"
            }
            hours >= 1L -> {
                if (mins > 0L) "Starts in ${hours}h ${mins}m" else "Starts in $hours hours"
            }
            else -> "Starts in $mins mins"
        }
    }
}
