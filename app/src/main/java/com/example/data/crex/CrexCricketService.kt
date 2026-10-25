package com.example.data.crex

import com.example.data.model.AppConfig
import com.example.data.model.HighlightItem
import com.example.data.model.LiveMatch
import com.example.data.model.MatchDateFilter
import com.example.data.model.MatchStatus
import com.example.data.model.SportType
import com.example.data.model.StreamSource
import com.example.data.util.TeamFlagResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Backend Gateway & Provider Service for:
 *  1. CREX Cricket API (routed via backend gateway `https://app.local/api/cricket/...`):
 *     - GET  https://api.goscorer.com/api/v3/getLiveMatches
 *     - POST https://oc.crickapi.com/oc/getHomeUpcomingMatchesCE {"type": 1}
 *     - POST https://oc.crickapi.com/oc/getHomeUpcomingMatchesCE {"type": 0}
 *     - GET  https://api.goscorer.com/api/v3/getSV3?key={matchId}
 *  2. Separate Football Provider (routed via backend gateway `https://app.local/api/football/scoreboard`):
 *     - GET  https://site.api.espn.com/apis/site/v2/sports/soccer/{league}/scoreboard?dates={yesterday}-{tomorrow}
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
        const val ROUTE_FOOTBALL_SCOREBOARD = "/api/football/scoreboard"

        private const val UPSTREAM_LIVE_URL = "https://api.goscorer.com/api/v3/getLiveMatches"
        private const val UPSTREAM_SCHEDULE_URL = "https://oc.crickapi.com/oc/getHomeUpcomingMatchesCE"
        private const val UPSTREAM_SV3_URL = "https://api.goscorer.com/api/v3/getSV3?key="
        private const val UPSTREAM_ESPN_SOCCER_BASE = "https://site.api.espn.com/apis/site/v2/sports/soccer"

        const val LIVE_CACHE_TTL_MS = 25_000L
        const val UPCOMING_CACHE_TTL_MS = 15 * 60_000L
        const val FINISHED_CACHE_TTL_MS = 30 * 60_000L
        const val DETAIL_CACHE_TTL_MS = 25_000L
        const val FOOTBALL_CACHE_TTL_MS = 30_000L

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
            put("11", "Scotland")
            put("12", "Netherlands")
            put("13", "Hong Kong")
            put("14", "Papua New Guinea")
            put("15", "Oman")
            put("16", "India Women")
            put("17", "England Women")
            put("18", "South Africa Women")
            put("19", "Nepal")
            put("1A", "Pakistan Women")
            put("1B", "West Indies Women")
            put("1C", "Sri Lanka Women")
            put("1D", "Bangladesh Women")
            put("1E", "Ireland Women")
            put("1H", "New Zealand Women")
            put("1I", "Australia Women")
            put("1J", "USA")
            put("1K", "Singapore")
            put("1L", "Malaysia")
            put("1M", "Kuwait")
            put("1N", "Qatar")
            put("1O", "Bahrain")
            put("1P", "Saudi Arabia")
            put("6I", "Zimbabwe Women")
            put("6V", "Kenya")
            put("6X", "Namibia")
            put("6Z", "Nigeria")
            put("70", "Canada")
            put("71", "Bermuda")
            put("72", "Jersey")
            put("73", "Italy")
            put("74", "Germany")
            put("8E", "Afghanistan U19")
            put("8F", "India U19")
            put("8G", "Pakistan U19")
            put("8H", "Australia U19")
            put("8I", "England U19")
            put("8J", "Bangladesh U19")
            put("HO", "Uganda")
            put("HP", "Tanzania")
            put("HQ", "Rwanda")
            put("IO", "Denmark")
            put("1F7", "Lesotho")
            put("1IA", "Mozambique")
            put("1LO", "Gambia")
            put("3J", "Victoria")
            put("3K", "Western Australia")
            put("3L", "New South Wales")
            put("3M", "South Australia")
            put("FU", "Queensland")
            put("FV", "Tasmania")
            put("4R", "Adelaide Strikers Women")
            put("4S", "Brisbane Heat Women")
            put("4T", "Hobart Hurricanes Women")
            put("4V", "Melbourne Renegades Women")
            put("4W", "Melbourne Stars Women")
            put("4X", "Perth Scorchers Women")
            put("50", "Sydney Sixers Women")
            put("8W", "Sydney Thunder Women")
            put("6A", "Dolphins")
            put("6B", "Titans")
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

        fun formatCardTimeAndDateDhaka(epochMs: Long): String {
            if (epochMs <= 0L) return ""
            val sdf = SimpleDateFormat("hh:mm a | dd/MM/yyyy", Locale.US).apply {
                timeZone = DHAKA_TIMEZONE
            }
            return sdf.format(Date(epochMs))
        }

        fun getDhakaDateKey(epochMs: Long): String {
            if (epochMs <= 0L) return ""
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = DHAKA_TIMEZONE
            }
            return sdf.format(Date(epochMs))
        }

        /**
         * Returns the day offset in `Asia/Dhaka` relative to `nowEpochMs`:
         * -1 = Yesterday (গতকাল)
         *  0 = Today (আজ)
         * +1 = Tomorrow (আগামীকাল)
         * Or other integer day difference, or null if `epochMs <= 0L`.
         */
        fun getDhakaDayOffset(epochMs: Long, nowEpochMs: Long = System.currentTimeMillis()): Int? {
            if (epochMs <= 0L) return null
            val targetCal = Calendar.getInstance(DHAKA_TIMEZONE).apply {
                timeInMillis = epochMs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val nowCal = Calendar.getInstance(DHAKA_TIMEZONE).apply {
                timeInMillis = nowEpochMs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val diffDays = ((targetCal.timeInMillis - nowCal.timeInMillis) / (24L * 3600_000L)).toInt()
            return diffDays
        }

        /**
         * Returns ESPN `dates` query parameter covering Yesterday-Tomorrow in `Asia/Dhaka` (`yyyyMMdd-yyyyMMdd`).
         */
        fun getEspnThreeDayRangeParamDhaka(nowEpochMs: Long = System.currentTimeMillis()): String {
            val sdf = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
                timeZone = DHAKA_TIMEZONE
            }
            val yesterday = sdf.format(Date(nowEpochMs - 24L * 3600_000L))
            val tomorrow = sdf.format(Date(nowEpochMs + 24L * 3600_000L))
            return "$yesterday-$tomorrow"
        }

        /**
         * Checks whether a match falls within Yesterday (-1), Today (0), or Tomorrow (+1) in `Asia/Dhaka`,
         * or is currently LIVE (e.g. a multi-day Test match that started earlier and is still in progress).
         */
        fun isWithinDhakaThreeDayWindow(
            matchEpochMs: Long,
            nowEpochMs: Long = System.currentTimeMillis()
        ): Boolean {
            val offset = getDhakaDayOffset(matchEpochMs, nowEpochMs) ?: return false
            return offset in -1..1
        }

        fun isWithinDhakaThreeDayWindow(
            match: LiveMatch,
            nowEpochMs: Long = System.currentTimeMillis()
        ): Boolean {
            if (match.sport == SportType.CRICKET && match.isConfirmedLive) {
                return true
            }
            val effectiveEpoch = if (match.startTimeEpochMs > 0L) {
                match.startTimeEpochMs
            } else {
                parseSourceDateEpochDhaka(match.matchTime)
            }
            val offset = getDhakaDayOffset(effectiveEpoch, nowEpochMs) ?: return false
            return offset in -1..1
        }

        /**
         * Re-validates a Football match's status against its provider status label and kickoff timestamp.
         * Strips any legacy/demo/stale "Live" label when the provider has not explicitly confirmed an active in-progress match.
         */
        fun revalidateFootballMatchStatus(
            match: LiveMatch,
            nowEpochMs: Long = System.currentTimeMillis()
        ): LiveMatch {
            if (match.sport != SportType.FOOTBALL) return match
            if (match.isPostponedOrCancelled) {
                return match.copy(
                    status = MatchStatus.UPCOMING,
                    providerStatusLabel = match.providerStatusLabel.ifBlank { "Postponed" }
                )
            }
            if (match.isStaleCache && match.status == MatchStatus.LIVE) {
                return match.copy(
                    status = MatchStatus.UPCOMING,
                    providerStatusLabel = "Unknown"
                )
            }
            val label = match.providerStatusLabel.trim()
            val startMs = match.startTimeEpochMs
            return when {
                label.equals("Live", ignoreCase = true) && match.status == MatchStatus.LIVE -> {
                    if (startMs > nowEpochMs + 15 * 60_000L) {
                        match.copy(status = MatchStatus.UPCOMING, providerStatusLabel = "Upcoming")
                    } else if (startMs <= 0L || (nowEpochMs - startMs) > 4 * 3600_000L) {
                        match.copy(status = MatchStatus.UPCOMING, providerStatusLabel = "Unknown")
                    } else {
                        match.copy(status = MatchStatus.LIVE, providerStatusLabel = "Live")
                    }
                }
                label.equals("Finished", ignoreCase = true) || match.status == MatchStatus.FINISHED -> {
                    match.copy(status = MatchStatus.FINISHED, providerStatusLabel = "Finished")
                }
                label.equals("Upcoming", ignoreCase = true) && match.status == MatchStatus.UPCOMING -> {
                    if (startMs > 0L && (nowEpochMs - startMs) > 3 * 3600_000L) {
                        match.copy(status = MatchStatus.UPCOMING, providerStatusLabel = "Unknown")
                    } else {
                        match.copy(status = MatchStatus.UPCOMING, providerStatusLabel = "Upcoming")
                    }
                }
                else -> {
                    match.copy(status = MatchStatus.UPCOMING, providerStatusLabel = "Unknown")
                }
            }
        }

        fun matchesDateFilterDhaka(
            matchEpochMs: Long,
            dateFilter: MatchDateFilter,
            nowEpochMs: Long = System.currentTimeMillis()
        ): Boolean {
            val dayOffset = dateFilter.dayOffsetFromTodayDhaka ?: return isWithinDhakaThreeDayWindow(matchEpochMs, nowEpochMs)
            val matchOffset = getDhakaDayOffset(matchEpochMs, nowEpochMs) ?: return false
            return matchOffset == dayOffset
        }

        /**
         * Checks whether a match matches the user-selected `MatchDateFilter` in `Asia/Dhaka`.
         */
        fun matchesDateFilterDhaka(
            match: LiveMatch,
            dateFilter: MatchDateFilter,
            nowEpochMs: Long = System.currentTimeMillis()
        ): Boolean {
            val dayOffset = dateFilter.dayOffsetFromTodayDhaka ?: return isWithinDhakaThreeDayWindow(match, nowEpochMs)
            val effectiveEpoch = if (match.startTimeEpochMs > 0L) {
                match.startTimeEpochMs
            } else {
                parseSourceDateEpochDhaka(match.matchTime)
            }
            val matchOffset = getDhakaDayOffset(effectiveEpoch, nowEpochMs)
            if (matchOffset == null) {
                return dayOffset == 0 && match.status == MatchStatus.LIVE
            }
            if (dayOffset == 0 && match.status == MatchStatus.LIVE && !match.isPostponedOrCancelled) {
                return true
            }
            return matchOffset == dayOffset
        }

        fun parseSourceDateEpochDhaka(rawDate: String): Long {
            val trimmed = rawDate.substringBefore("•").trim()
            if (trimmed.isBlank()) return 0L
            val patterns = listOf(
                "dd MMM yyyy, hh:mm:ss a 'BST'",
                "dd MMM yyyy, hh:mm a 'BST'",
                "dd MMM yyyy (Asia/Dhaka)",
                "yyyy/MM/dd",
                "MM/dd/yyyy",
                "yyyy-MM-dd",
                "dd/MM/yyyy"
            )
            for (p in patterns) {
                try {
                    val parser = SimpleDateFormat(p, Locale.US).apply {
                        timeZone = DHAKA_TIMEZONE
                    }
                    val parsed = parser.parse(trimmed)
                    if (parsed != null) return parsed.time
                } catch (_: Exception) {
                }
            }
            return 0L
        }

        fun formatSourceDateStringDhaka(rawDate: String): String {
            val trimmed = rawDate.trim()
            if (trimmed.isBlank()) return ""
            val patterns = listOf("yyyy/MM/dd", "MM/dd/yyyy", "yyyy-MM-dd")
            for (p in patterns) {
                try {
                    val parser = SimpleDateFormat(p, Locale.US).apply {
                        timeZone = DHAKA_TIMEZONE
                    }
                    val parsed = parser.parse(trimmed)
                    if (parsed != null) {
                        val out = SimpleDateFormat("dd MMM yyyy (Asia/Dhaka)", Locale.US).apply {
                            timeZone = DHAKA_TIMEZONE
                        }
                        return out.format(parsed)
                    }
                } catch (_: Exception) {
                }
            }
            return trimmed
        }

        private val INTERNATIONAL_CRICKET_TEAMS = setOf(
            "india", "australia", "england", "pakistan", "bangladesh", "south africa",
            "new zealand", "sri lanka", "west indies", "afghanistan", "zimbabwe", "ireland",
            "scotland", "netherlands", "nepal", "uae", "united arab emirates", "oman", "usa",
            "united states", "namibia", "canada", "papua new guinea", "uganda", "hong kong",
            "kenya", "bermuda", "jersey", "italy", "germany", "malaysia", "kuwait", "qatar",
            "bahrain", "saudi arabia", "singapore", "tanzania", "rwanda", "nigeria",
            "denmark", "lesotho", "mozambique", "gambia"
        )

        private val DOMESTIC_CLUB_KEYWORDS = listOf(
            "division", "strikers", "heat", "hurricanes", "renegades", "stars", "scorchers",
            "sixers", "thunder", "dolphins", "titans", "warriors", "lions", "knights",
            "badgers", "rocks", "victoria", "western australia", "new south wales",
            "south australia", "queensland", "tasmania", "ranji", "sheffield", "plunket",
            "county", "vitality blast", "super smash", "syed mushtaq", "vijay hazare",
            "ipl", "bpl", "psl", "bbl", "cpl", "sa20", "ilt20", "lpl", "hundred",
            "premier league", "super kings", "knight riders", "royal challengers",
            "sunrisers", "capitals", "royals", "indians", "qalandars", "sultans",
            "gladiators", "zalmi", "united", "comilla", "rangpur", "fortune barishal"
        )

        /**
         * Strictly verifies whether a Cricket match is an International match.
         * Never classifies a domestic/franchise match as international.
         */
        fun isInternationalCricketMatch(match: LiveMatch): Boolean {
            if (match.sport != SportType.CRICKET) return false
            val t1 = match.team1Name.lowercase()
                .replace(" women", "")
                .replace(" u19", "")
                .replace(" a", "")
                .trim()
            val t2 = match.team2Name.lowercase()
                .replace(" women", "")
                .replace(" u19", "")
                .replace(" a", "")
                .trim()
            val combined = "${match.tournament} ${match.team1Name} ${match.team2Name}".lowercase()

            if (DOMESTIC_CLUB_KEYWORDS.any { combined.contains(it) }) {
                return false
            }
            if (t1 in INTERNATIONAL_CRICKET_TEAMS && t2 in INTERNATIONAL_CRICKET_TEAMS) {
                return true
            }
            val hasExplicitIntlCompetition = combined.contains("icc") ||
                combined.contains("world cup") ||
                combined.contains("asia cup") ||
                combined.contains("champions trophy") ||
                combined.contains("international") ||
                combined.contains("t20i") ||
                combined.contains("tri-series") ||
                combined.contains("tour of")
            if (hasExplicitIntlCompetition && (t1 in INTERNATIONAL_CRICKET_TEAMS || t2 in INTERNATIONAL_CRICKET_TEAMS)) {
                return true
            }
            return false
        }

        fun isInternationalFootballLeague(leagueCode: String, leagueName: String = ""): Boolean {
            val code = leagueCode.lowercase()
            val name = leagueName.lowercase()
            return code.startsWith("fifa.") ||
                code.contains("world") ||
                code == "uefa.euro" ||
                code == "uefa.nations" ||
                code == "conmebol.america" ||
                code == "caf.nations" ||
                code == "afc.cup" ||
                name.contains("world cup") ||
                name.contains("euro") ||
                name.contains("nations league") ||
                name.contains("international") ||
                name.contains("copa america")
        }
    }

    private data class CachedRouteResponse(
        val rawBody: String,
        val fetchedAtEpochMs: Long
    )

    private data class RouteErrorState(
        val consecutiveFailures: Int = 0,
        val backoffUntilEpochMs: Long = 0L,
        val lastErrorMessage: String? = null,
        val lastHttpStatusCode: Int = 0
    )

    private class HttpRouteException(
        val statusCode: Int,
        override val message: String
    ) : IOException(message)

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dedupMutex = Mutex()
    private val routeCache = ConcurrentHashMap<String, CachedRouteResponse>()
    private val inFlightCalls = ConcurrentHashMap<String, Deferred<Result<CachedRouteResponse>>>()
    private val routeErrors = ConcurrentHashMap<String, RouteErrorState>()

    private val backendGatewayInterceptor = Interceptor { chain ->
        val original = chain.request()
        val url = original.url
        if (url.host == "app.local") {
            val path = url.encodedPath
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val upstreamRequest: Request = when {
                path == ROUTE_LIVE -> {
                    Request.Builder()
                        .url(UPSTREAM_LIVE_URL)
                        .get()
                        .header("User-Agent", "Mozilla/5.0 (Android; SportsLiveBackend/1.0)")
                        .header("Accept", "application/json")
                        .build()
                }
                path == ROUTE_UPCOMING -> {
                    val body = """{"type":1}""".toRequestBody(jsonMediaType)
                    Request.Builder()
                        .url(UPSTREAM_SCHEDULE_URL)
                        .post(body)
                        .header("User-Agent", "Mozilla/5.0 (Android; SportsLiveBackend/1.0)")
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .build()
                }
                path == ROUTE_FINISHED -> {
                    val body = """{"type":0}""".toRequestBody(jsonMediaType)
                    Request.Builder()
                        .url(UPSTREAM_SCHEDULE_URL)
                        .post(body)
                        .header("User-Agent", "Mozilla/5.0 (Android; SportsLiveBackend/1.0)")
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .build()
                }
                path.startsWith(ROUTE_MATCH_DETAIL_PREFIX) -> {
                    val matchId = path.removePrefix(ROUTE_MATCH_DETAIL_PREFIX).trim()
                    Request.Builder()
                        .url("$UPSTREAM_SV3_URL$matchId")
                        .get()
                        .header("User-Agent", "Mozilla/5.0 (Android; SportsLiveBackend/1.0)")
                        .header("Accept", "application/json")
                        .build()
                }
                path == ROUTE_FOOTBALL_SCOREBOARD -> {
                    val league = url.queryParameter("league")?.trim().orEmpty()
                    val dates = url.queryParameter("dates")?.trim().orEmpty()
                    val querySuffix = if (dates.isNotBlank()) "?dates=$dates" else ""
                    Request.Builder()
                        .url("$UPSTREAM_ESPN_SOCCER_BASE/$league/scoreboard$querySuffix")
                        .get()
                        .header("User-Agent", "Mozilla/5.0 (Android; SportsLiveBackend/1.0)")
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
            isAuthorized = true,
            isBackup = false,
            displayOrder = 0
        ),
        StreamSource(
            name = "STAR SPORTS 1 HD",
            quality = "FHD",
            url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            isEnabled = true,
            isAuthorized = true,
            isBackup = false,
            displayOrder = 1
        ),
        StreamSource(
            name = "WILLOW / PTV BACKUP HD",
            quality = "HD",
            url = "https://diceyk6a7voy4.cloudfront.net/e78752a1-2e83-43fa-85ae-3d508be29366/hls/de:02b6989d-e714-4227-9c57-a4c1a221a244:playlist.m3u8",
            isEnabled = true,
            isAuthorized = true,
            isBackup = true,
            displayOrder = 2
        )
    )

    private val defaultMultiSportStreams = listOf(
        StreamSource(
            name = "SERVER 1 FHD",
            quality = "1080p FHD",
            url = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8",
            isDefault = true,
            isEnabled = true,
            isAuthorized = true,
            isBackup = false,
            displayOrder = 0
        ),
        StreamSource(
            name = "SERVER 2 HD",
            quality = "720p HD",
            url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            isEnabled = true,
            isAuthorized = true,
            isBackup = false,
            displayOrder = 1
        ),
        StreamSource(
            name = "BACKUP SD",
            quality = "480p SD",
            url = "https://diceyk6a7voy4.cloudfront.net/e78752a1-2e83-43fa-85ae-3d508be29366/hls/de:02b6989d-e714-4227-9c57-a4c1a221a244:playlist.m3u8",
            isEnabled = true,
            isAuthorized = true,
            isBackup = true,
            displayOrder = 2
        )
    )

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
                HttpRouteException(
                    statusCode = errState.lastHttpStatusCode,
                    message = "${errState.lastErrorMessage ?: "Upstream provider error"} (retry in ${waitSec}s)"
                )
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
                                throw HttpRouteException(
                                    statusCode = resp.code,
                                    message = "Provider access restricted (HTTP ${resp.code} Forbidden)"
                                )
                            }
                            if (!resp.isSuccessful) {
                                throw HttpRouteException(
                                    statusCode = resp.code,
                                    message = "Provider returned HTTP ${resp.code}"
                                )
                            }
                            val body = resp.body?.string()?.trim().orEmpty()
                            if (body.isBlank()) {
                                throw HttpRouteException(
                                    statusCode = resp.code,
                                    message = "Provider returned empty response"
                                )
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
                        val statusCode = (e as? HttpRouteException)?.statusCode ?: 0
                        val prevFailures = (routeErrors[routePath]?.consecutiveFailures ?: 0) + 1
                        val backoffSec = min(60L, 5L * (1L shl (prevFailures - 1).coerceAtMost(4)))
                        val backoffUntil = System.currentTimeMillis() + backoffSec * 1000L
                        routeErrors[routePath] = RouteErrorState(
                            consecutiveFailures = prevFailures,
                            backoffUntilEpochMs = backoffUntil,
                            lastErrorMessage = e.localizedMessage ?: "Provider network failure",
                            lastHttpStatusCode = statusCode
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
     * Automatically resolves any unknown CREX team keys ("Team X") by calling getSV3 (`speech_names`)
     * in parallel for up to 10 matches, updating `teamNameCache` and resolving real flags.
     */
    private suspend fun enrichMatchesWithSv3TeamNames(matches: List<LiveMatch>): List<LiveMatch> = coroutineScope {
        val unknownMatches = matches.filter {
            it.team1Name.startsWith("Team ") || it.team2Name.startsWith("Team ")
        }.take(10)

        if (unknownMatches.isNotEmpty()) {
            unknownMatches.map { m ->
                async(Dispatchers.IO) {
                    runCatching { fetchMatchDetail(m.id, forceRefresh = false) }
                }
            }.awaitAll()
        }

        matches.map { m ->
            val dState = routeCache["$ROUTE_MATCH_DETAIL_PREFIX${m.id.removePrefix("crex-")}"]
            val detail = dState?.let {
                runCatching { parseSv3DetailPayload(m.id, it.rawBody, it.fetchedAtEpochMs) }.getOrNull()
            }
            val newT1Name = when {
                detail != null && detail.team1Name.isNotBlank() && !detail.team1Name.startsWith("Team ") -> detail.team1Name
                m.team1Name.startsWith("Team ") -> resolveCrexTeamName(m.team1Name.removePrefix("Team ").trim())
                else -> m.team1Name
            }
            val newT2Name = when {
                detail != null && detail.team2Name.isNotBlank() && !detail.team2Name.startsWith("Team ") -> detail.team2Name
                m.team2Name.startsWith("Team ") -> resolveCrexTeamName(m.team2Name.removePrefix("Team ").trim())
                else -> m.team2Name
            }
            val v1 = TeamFlagResolver.resolve(newT1Name, if (m.team1Code.startsWith("Team ")) "" else m.team1Code, m.team1FlagUrl)
            val v2 = TeamFlagResolver.resolve(newT2Name, if (m.team2Code.startsWith("Team ")) "" else m.team2Code, m.team2FlagUrl)
            m.copy(
                team1Name = newT1Name,
                team1Code = v1.code,
                team1FlagUrl = v1.flagUrl,
                team2Name = newT2Name,
                team2Code = v2.code,
                team2FlagUrl = v2.flagUrl,
                team1Score = detail?.team1Score?.ifBlank { m.team1Score } ?: m.team1Score,
                team2Score = detail?.team2Score?.ifBlank { m.team2Score } ?: m.team2Score
            )
        }
    }

    /**
     * 1. GET /api/cricket/live -> GET https://api.goscorer.com/api/v3/getLiveMatches
     * Shared cache: 25 seconds.
     */
    suspend fun fetchLiveMatches(forceRefresh: Boolean = false): CrexCategoryFeedState {
        val result = executeBackendRoute(
            routePath = ROUTE_LIVE,
            ttlMs = LIVE_CACHE_TTL_MS,
            forceRefresh = forceRefresh
        )
        return result.fold(
            onSuccess = { entry ->
                try {
                    val parsed = enrichMatchesWithSv3TeamNames(parseLiveMatchesPayload(entry.rawBody))
                    CrexCategoryFeedState(
                        category = MatchStatus.LIVE,
                        matches = parsed,
                        isLoading = false,
                        errorMessage = null,
                        httpStatusCode = 200,
                        isStale = false,
                        lastUpdatedEpochMs = entry.fetchedAtEpochMs,
                        lastUpdatedDhakaText = formatEpochDhaka(entry.fetchedAtEpochMs, includeSeconds = true)
                    )
                } catch (e: Exception) {
                    buildErrorOrStaleState(
                        category = MatchStatus.LIVE,
                        routePath = ROUTE_LIVE,
                        error = e,
                        parser = ::parseLiveMatchesPayload
                    )
                }
            },
            onFailure = { error ->
                buildErrorOrStaleState(
                    category = MatchStatus.LIVE,
                    routePath = ROUTE_LIVE,
                    error = error,
                    parser = ::parseLiveMatchesPayload
                )
            }
        )
    }

    /**
     * 2. GET /api/cricket/upcoming -> POST https://oc.crickapi.com/oc/getHomeUpcomingMatchesCE {"type": 1}
     * Shared cache: 15 minutes.
     */
    suspend fun fetchUpcomingMatches(forceRefresh: Boolean = false): CrexCategoryFeedState {
        val result = executeBackendRoute(
            routePath = ROUTE_UPCOMING,
            ttlMs = UPCOMING_CACHE_TTL_MS,
            forceRefresh = forceRefresh
        )
        return result.fold(
            onSuccess = { entry ->
                try {
                    val parsed = enrichMatchesWithSv3TeamNames(parseUpcomingMatchesPayload(entry.rawBody))
                    CrexCategoryFeedState(
                        category = MatchStatus.UPCOMING,
                        matches = parsed,
                        isLoading = false,
                        errorMessage = null,
                        httpStatusCode = 200,
                        isStale = false,
                        lastUpdatedEpochMs = entry.fetchedAtEpochMs,
                        lastUpdatedDhakaText = formatEpochDhaka(entry.fetchedAtEpochMs, includeSeconds = true)
                    )
                } catch (e: Exception) {
                    buildErrorOrStaleState(
                        category = MatchStatus.UPCOMING,
                        routePath = ROUTE_UPCOMING,
                        error = e,
                        parser = ::parseUpcomingMatchesPayload
                    )
                }
            },
            onFailure = { error ->
                buildErrorOrStaleState(
                    category = MatchStatus.UPCOMING,
                    routePath = ROUTE_UPCOMING,
                    error = error,
                    parser = ::parseUpcomingMatchesPayload
                )
            }
        )
    }

    /**
     * 3. GET /api/cricket/finished -> POST https://oc.crickapi.com/oc/getHomeUpcomingMatchesCE {"type": 0}
     * Shared cache: 30 minutes.
     */
    suspend fun fetchFinishedMatches(forceRefresh: Boolean = false): CrexCategoryFeedState {
        val result = executeBackendRoute(
            routePath = ROUTE_FINISHED,
            ttlMs = FINISHED_CACHE_TTL_MS,
            forceRefresh = forceRefresh
        )
        return result.fold(
            onSuccess = { entry ->
                try {
                    val parsed = enrichMatchesWithSv3TeamNames(parseFinishedMatchesPayload(entry.rawBody))
                    CrexCategoryFeedState(
                        category = MatchStatus.FINISHED,
                        matches = parsed,
                        isLoading = false,
                        errorMessage = null,
                        httpStatusCode = 200,
                        isStale = false,
                        lastUpdatedEpochMs = entry.fetchedAtEpochMs,
                        lastUpdatedDhakaText = formatEpochDhaka(entry.fetchedAtEpochMs, includeSeconds = true)
                    )
                } catch (e: Exception) {
                    buildErrorOrStaleState(
                        category = MatchStatus.FINISHED,
                        routePath = ROUTE_FINISHED,
                        error = e,
                        parser = ::parseFinishedMatchesPayload
                    )
                }
            },
            onFailure = { error ->
                buildErrorOrStaleState(
                    category = MatchStatus.FINISHED,
                    routePath = ROUTE_FINISHED,
                    error = error,
                    parser = ::parseFinishedMatchesPayload
                )
            }
        )
    }

    /**
     * 4. GET /api/cricket/matches/{matchId} -> GET https://api.goscorer.com/api/v3/getSV3?key={matchId}
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
                        errorMessage = "Failed to parse match details"
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

    /**
     * 5. Separate Football Provider (ESPN Soccer Scoreboard API via Backend Gateway)
     * Queries only the verified leagues present in [enabledLeagues] for Yesterday, Today, and Tomorrow in `Asia/Dhaka`.
     * Never mixes Cricket data or demo fixtures if the Football provider is unavailable.
     */
    suspend fun fetchFootballMatches(
        enabledLeagues: List<String> = AppConfig.DEFAULT_FOOTBALL_LEAGUE_ALLOWLIST,
        forceRefresh: Boolean = false
    ): FootballProviderState = withContext(Dispatchers.IO) {
        val activeLeagues = enabledLeagues
            .map { it.trim() }
            .filter { it in AppConfig.VERIFIED_FOOTBALL_LEAGUE_LABELS.keys }
            .ifEmpty { AppConfig.DEFAULT_FOOTBALL_LEAGUE_ALLOWLIST }

        val datesRange = getEspnThreeDayRangeParamDhaka()
        val allMatches = mutableListOf<LiveMatch>()
        val staleMatches = mutableListOf<LiveMatch>()
        var lastErrorMsg: String? = null
        var lastStatusCode = 0
        var latestFetchMs: Long? = null
        var anySuccess = false

        coroutineScope {
            val jobs = activeLeagues.map { leagueCode ->
                val leagueLabel = AppConfig.VERIFIED_FOOTBALL_LEAGUE_LABELS[leagueCode] ?: leagueCode.uppercase()
                val routePath = "$ROUTE_FOOTBALL_SCOREBOARD?league=$leagueCode&dates=$datesRange"
                async {
                    val res = executeBackendRoute(
                        routePath = routePath,
                        ttlMs = FOOTBALL_CACHE_TTL_MS,
                        forceRefresh = forceRefresh
                    )
                    Triple(leagueCode, leagueLabel, res to routePath)
                }
            }

            jobs.awaitAll().forEach { (leagueCode, leagueLabel, pair) ->
                val (res, routePath) = pair
                res.fold(
                    onSuccess = { entry ->
                        anySuccess = true
                        latestFetchMs = maxOf(latestFetchMs ?: 0L, entry.fetchedAtEpochMs)
                        val parsed = runCatching {
                            parseEspnScoreboardPayload(
                                rawJson = entry.rawBody,
                                sportType = SportType.FOOTBALL,
                                overrideLeagueLabel = leagueLabel,
                                leagueCode = leagueCode
                            )
                        }.getOrDefault(emptyList())
                        allMatches.addAll(parsed)
                    },
                    onFailure = { err ->
                        lastErrorMsg = err.localizedMessage ?: "Football provider error"
                        val code = (err as? HttpRouteException)?.statusCode ?: 0
                        if (code > 0) lastStatusCode = code
                        val cached = routeCache[routePath]
                        if (cached != null) {
                            latestFetchMs = maxOf(latestFetchMs ?: 0L, cached.fetchedAtEpochMs)
                            val parsedStale = runCatching {
                                parseEspnScoreboardPayload(
                                    rawJson = cached.rawBody,
                                    sportType = SportType.FOOTBALL,
                                    overrideLeagueLabel = leagueLabel,
                                    leagueCode = leagueCode
                                ).map { staleMatch ->
                                    revalidateFootballMatchStatus(staleMatch.copy(isStaleCache = true))
                                }
                            }.getOrDefault(emptyList())
                            staleMatches.addAll(parsedStale)
                        }
                    }
                )
            }
        }

        val combined = (allMatches + staleMatches)
            .distinctBy { it.id }
            .map { revalidateFootballMatchStatus(it) }
            .filter { isWithinDhakaThreeDayWindow(it) }

        val isStaleOnly = !anySuccess && staleMatches.isNotEmpty()
        val nowMs = latestFetchMs ?: System.currentTimeMillis()
        FootballProviderState(
            matches = combined,
            isLoading = false,
            errorMessage = if (anySuccess) null else lastErrorMsg,
            httpStatusCode = if (anySuccess) 200 else lastStatusCode,
            isStale = isStaleOnly,
            lastUpdatedEpochMs = if (anySuccess || staleMatches.isNotEmpty()) nowMs else null,
            lastUpdatedDhakaText = if (anySuccess || staleMatches.isNotEmpty()) formatEpochDhaka(nowMs, includeSeconds = true) else null,
            activeLeagues = activeLeagues
        )
    }

    /**
     * Backward-compatible helper that returns the verified Football matches list.
     */
    suspend fun fetchRealMultiSportMatches(
        enabledLeagues: List<String> = AppConfig.DEFAULT_FOOTBALL_LEAGUE_ALLOWLIST,
        forceRefresh: Boolean = false
    ): List<LiveMatch> {
        return fetchFootballMatches(enabledLeagues = enabledLeagues, forceRefresh = forceRefresh).matches
    }

    internal fun parseEspnScoreboardPayload(
        rawJson: String,
        sportType: SportType,
        overrideLeagueLabel: String? = null,
        leagueCode: String = "",
        nowEpochMs: Long = System.currentTimeMillis()
    ): List<LiveMatch> {
        val list = mutableListOf<LiveMatch>()
        if (!rawJson.trim().startsWith("{")) return list

        val root = JSONObject(rawJson)
        val leaguesArr = root.optJSONArray("leagues")
        val defaultLeagueName = overrideLeagueLabel
            ?: leaguesArr?.optJSONObject(0)?.optString("name")?.ifBlank { null }
            ?: sportType.displayName.uppercase()

        val eventsArr = root.optJSONArray("events") ?: return list
        for (i in 0 until eventsArr.length()) {
            val ev = eventsArr.optJSONObject(i) ?: continue
            val id = ev.optString("id").ifBlank { continue }
            val dateIso = ev.optString("date")
            val startEpochMs = parseIsoDateEpoch(dateIso)

            val compArr = ev.optJSONArray("competitions")
            val comp = compArr?.optJSONObject(0) ?: ev
            val statusObj = comp.optJSONObject("status") ?: ev.optJSONObject("status")
            val typeObj = statusObj?.optJSONObject("type")
            val stateStr = typeObj?.optString("state")?.trim()?.lowercase().orEmpty() // "pre", "in", "post"
            val typeName = typeObj?.optString("name")?.trim()?.uppercase().orEmpty()
            val typeDesc = typeObj?.optString("description")?.trim().orEmpty()
            val shortDetail = typeObj?.optString("shortDetail")?.trim().orEmpty()
            val isCompleted = typeObj?.optBoolean("completed", false) == true

            val isPostponedOrCancelled = typeName.contains("POSTPONED") ||
                typeName.contains("CANCELED") ||
                typeName.contains("CANCELLED") ||
                typeName.contains("SUSPENDED") ||
                typeName.contains("ABANDONED") ||
                typeName.contains("DELAYED") ||
                typeDesc.contains("Postponed", ignoreCase = true) ||
                typeDesc.contains("Canceled", ignoreCase = true) ||
                typeDesc.contains("Cancelled", ignoreCase = true) ||
                typeDesc.contains("Suspended", ignoreCase = true) ||
                typeDesc.contains("Abandoned", ignoreCase = true) ||
                shortDetail.contains("Postponed", ignoreCase = true) ||
                shortDetail.contains("Canceled", ignoreCase = true) ||
                shortDetail.contains("Cancelled", ignoreCase = true)

            val isExplicitlyFinished = !isPostponedOrCancelled && (
                isCompleted ||
                    stateStr == "post" ||
                    typeName.contains("FULL_TIME") ||
                    typeName.contains("FINAL") ||
                    typeName.contains("FULL_PEN") ||
                    typeName.contains("AET") ||
                    typeDesc.equals("Full Time", ignoreCase = true) ||
                    typeDesc.equals("Final", ignoreCase = true) ||
                    typeDesc.equals("Finished", ignoreCase = true) ||
                    typeDesc.equals("Completed", ignoreCase = true)
                )

            val isExplicitlyLive = !isPostponedOrCancelled &&
                !isExplicitlyFinished &&
                stateStr == "in" &&
                !typeName.contains("SCHEDULED") &&
                !typeName.contains("TBD") &&
                !typeName.contains("UNKNOWN") &&
                !typeDesc.equals("Scheduled", ignoreCase = true) &&
                !typeDesc.equals("Unknown", ignoreCase = true) &&
                startEpochMs > 0L &&
                startEpochMs <= nowEpochMs + 15 * 60_000L &&
                (nowEpochMs - startEpochMs) <= 4 * 3600_000L

            val isExplicitlyUpcoming = !isPostponedOrCancelled &&
                !isExplicitlyFinished &&
                !isExplicitlyLive && (
                    ((stateStr == "pre" || typeName.contains("SCHEDULED") || typeDesc.equals("Scheduled", ignoreCase = true)) &&
                        startEpochMs > 0L && (nowEpochMs - startEpochMs) <= 3 * 3600_000L) ||
                        (startEpochMs > nowEpochMs + 15 * 60_000L && stateStr != "post" && stateStr.isNotBlank() && !typeName.contains("UNKNOWN"))
                )

            val providerStatusLabel = when {
                typeName.contains("POSTPONED") || typeDesc.contains("Postponed", ignoreCase = true) -> "Postponed"
                typeName.contains("CANCELED") || typeName.contains("CANCELLED") || typeDesc.contains("Cancel", ignoreCase = true) -> "Cancelled"
                typeName.contains("SUSPENDED") || typeDesc.contains("Suspended", ignoreCase = true) -> "Suspended"
                typeName.contains("ABANDONED") || typeDesc.contains("Abandoned", ignoreCase = true) -> "Abandoned"
                typeName.contains("DELAYED") || typeDesc.contains("Delayed", ignoreCase = true) -> "Delayed"
                isExplicitlyLive -> "Live"
                isExplicitlyFinished -> "Finished"
                isExplicitlyUpcoming -> "Upcoming"
                else -> "Unknown"
            }

            // Use provider status strictly; never default missing/stale/ambiguous status to LIVE
            val matchStatus = when {
                isExplicitlyLive -> MatchStatus.LIVE
                isExplicitlyFinished -> MatchStatus.FINISHED
                else -> MatchStatus.UPCOMING
            }

            val leagueName = comp.optJSONObject("league")?.optString("name")
                ?: ev.optJSONObject("league")?.optString("name")
                ?: defaultLeagueName

            val competitors = comp.optJSONArray("competitors")
            if (competitors != null && competitors.length() >= 2) {
                val c1 = competitors.optJSONObject(0)
                val c2 = competitors.optJSONObject(1)
                val t1Obj = c1?.optJSONObject("team") ?: c1?.optJSONObject("athlete")
                val t2Obj = c2?.optJSONObject("team") ?: c2?.optJSONObject("athlete")

                val evName = ev.optString("name").orEmpty()
                val evShortName = ev.optString("shortName").orEmpty()
                val t1Full = t1Obj?.optString("displayName").orEmpty()
                val t1Short = t1Obj?.optString("shortDisplayName").orEmpty()
                val t1Raw = t1Obj?.optString("name").orEmpty()
                val t2Full = t2Obj?.optString("displayName").orEmpty()
                val t2Short = t2Obj?.optString("shortDisplayName").orEmpty()
                val t2Raw = t2Obj?.optString("name").orEmpty()

                val t1Name = t1Short.ifBlank { t1Full }.ifBlank { t1Raw }.ifBlank { "Team 1" }
                val t2Name = t2Short.ifBlank { t2Full }.ifBlank { t2Raw }.ifBlank { "Team 2" }

                // Exclude U17/U18, youth, academy, reserve, B-team, amateur, esports, and non-senior competitions
                if (sportType == SportType.FOOTBALL && (
                        TeamFlagResolver.isNonSeniorOrExcludedCompetition("$leagueName $evName $evShortName $t1Full $t1Short $t1Raw $t2Full $t2Short $t2Raw") ||
                            TeamFlagResolver.isNonSeniorFootballTeamName(t1Full) ||
                            TeamFlagResolver.isNonSeniorFootballTeamName(t1Short) ||
                            TeamFlagResolver.isNonSeniorFootballTeamName(t1Raw) ||
                            TeamFlagResolver.isNonSeniorFootballTeamName(t2Full) ||
                            TeamFlagResolver.isNonSeniorFootballTeamName(t2Short) ||
                            TeamFlagResolver.isNonSeniorFootballTeamName(t2Raw)
                        )
                ) {
                    continue
                }

                val t1Abbr = t1Obj?.optString("abbreviation").orEmpty()
                val t2Abbr = t2Obj?.optString("abbreviation").orEmpty()

                val t1Logo = t1Obj?.optString("logo")
                    ?.ifBlank { t1Obj.optJSONObject("flag")?.optString("href").orEmpty() }
                    .orEmpty()
                val t2Logo = t2Obj?.optString("logo")
                    ?.ifBlank { t2Obj.optJSONObject("flag")?.optString("href").orEmpty() }
                    .orEmpty()

                val isIntlFootball = isInternationalFootballLeague(leagueCode, leagueName)
                val isClubFootball = sportType == SportType.FOOTBALL && !isIntlFootball

                val v1 = TeamFlagResolver.resolve(
                    teamName = t1Name,
                    teamCode = t1Abbr,
                    existingFlag = t1Logo,
                    isClubFootball = isClubFootball
                )
                val v2 = TeamFlagResolver.resolve(
                    teamName = t2Name,
                    teamCode = t2Abbr,
                    existingFlag = t2Logo,
                    isClubFootball = isClubFootball
                )

                val s1 = c1?.optString("score").orEmpty()
                val s2 = c2?.optString("score").orEmpty()

                val scoreOrTimeSummary = when {
                    isPostponedOrCancelled -> providerStatusLabel.ifBlank { "Postponed" }
                    matchStatus == MatchStatus.LIVE -> {
                        if (s1.isNotBlank() || s2.isNotBlank()) {
                            "${v1.code} $s1 - $s2 ${v2.code}" + (if (shortDetail.isNotBlank()) " • $shortDetail" else "")
                        } else {
                            shortDetail.ifBlank { "Live Now" }
                        }
                    }
                    matchStatus == MatchStatus.FINISHED -> {
                        if (s1.isNotBlank() || s2.isNotBlank()) {
                            "${v1.code} $s1 - $s2 ${v2.code} • FT"
                        } else {
                            shortDetail.ifBlank { "Finished" }
                        }
                    }
                    else -> {
                        shortDetail.ifBlank { "Scheduled" }
                    }
                }

                val dhakaTime = if (startEpochMs > 0L) {
                    formatEpochDhaka(startEpochMs)
                } else {
                    shortDetail
                }

                list.add(
                    LiveMatch(
                        id = "espn-${sportType.name.lowercase()}-$id",
                        tournament = "${sportType.displayName.uppercase()} | ${leagueName.uppercase()}",
                        sport = sportType,
                        team1Name = t1Name,
                        team1Code = v1.code,
                        team1FlagUrl = v1.flagUrl,
                        team2Name = t2Name,
                        team2Code = v2.code,
                        team2FlagUrl = v2.flagUrl,
                        team1Score = s1,
                        team2Score = s2,
                        status = matchStatus,
                        providerStatusLabel = providerStatusLabel,
                        isPostponedOrCancelled = isPostponedOrCancelled,
                        timeOrScore = scoreOrTimeSummary,
                        matchTime = dhakaTime,
                        startTimeEpochMs = startEpochMs,
                        streams = defaultMultiSportStreams
                    )
                )
            }
        }
        return list
    }

    /**
     * Extracts video highlights & match recaps from the enabled ESPN Football Scoreboard feeds.
     */
    suspend fun fetchRealHighlights(
        enabledLeagues: List<String> = AppConfig.DEFAULT_FOOTBALL_LEAGUE_ALLOWLIST
    ): List<HighlightItem> = withContext(Dispatchers.IO) {
        val highlights = mutableListOf<HighlightItem>()
        val activeLeagues = enabledLeagues
            .filter { it in AppConfig.VERIFIED_FOOTBALL_LEAGUE_LABELS.keys }
            .ifEmpty { AppConfig.DEFAULT_FOOTBALL_LEAGUE_ALLOWLIST }
        val datesRange = getEspnThreeDayRangeParamDhaka()

        coroutineScope {
            val jobs = activeLeagues.map { leagueCode ->
                val routePath = "$ROUTE_FOOTBALL_SCOREBOARD?league=$leagueCode&dates=$datesRange"
                async {
                    val res = executeBackendRoute(
                        routePath = routePath,
                        ttlMs = FOOTBALL_CACHE_TTL_MS,
                        forceRefresh = false
                    )
                    res.fold(
                        onSuccess = { entry ->
                            runCatching {
                                parseEspnHighlightsFromScoreboard(entry.rawBody, SportType.FOOTBALL)
                            }.getOrDefault(emptyList())
                        },
                        onFailure = { emptyList() }
                    )
                }
            }
            jobs.awaitAll().forEach { highlights.addAll(it) }
        }
        highlights.distinctBy { it.id }
    }

    internal fun parseEspnHighlightsFromScoreboard(rawJson: String, sportType: SportType): List<HighlightItem> {
        val result = mutableListOf<HighlightItem>()
        if (!rawJson.trim().startsWith("{")) return result
        val fallbackStreams = listOf(
            "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8",
            "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        )

        val root = JSONObject(rawJson)
        val leaguesArr = root.optJSONArray("leagues")
        val defaultLeagueName = leaguesArr?.optJSONObject(0)?.optString("name")?.ifBlank { null }
            ?: sportType.displayName.uppercase()

        val eventsArr = root.optJSONArray("events") ?: return result
        for (i in 0 until eventsArr.length()) {
            val ev = eventsArr.optJSONObject(i) ?: continue
            val eventId = ev.optString("id").ifBlank { continue }
            val eventName = ev.optString("name").ifBlank { ev.optString("shortName", "Match Highlights") }
            val comp = ev.optJSONArray("competitions")?.optJSONObject(0) ?: ev
            val leagueName = comp.optJSONObject("league")?.optString("name") ?: defaultLeagueName

            val headlines = comp.optJSONArray("headlines")
            var addedVideoClip = false
            if (headlines != null) {
                for (hIdx in 0 until headlines.length()) {
                    val hObj = headlines.optJSONObject(hIdx) ?: continue
                    val videos = hObj.optJSONArray("video") ?: continue
                    for (vIdx in 0 until videos.length()) {
                        val vObj = videos.optJSONObject(vIdx) ?: continue
                        val vidId = vObj.optString("id", "$eventId-$vIdx")
                        val headlineTitle = vObj.optString("headline")
                            .ifBlank { hObj.optString("shortLinkText") }
                            .ifBlank { "$eventName - Extended Highlights" }
                        val thumb = vObj.optString("thumbnail")
                        val durationSec = vObj.optInt("duration", 180)
                        val mins = (durationSec / 60).coerceAtLeast(1)
                        val secs = durationSec % 60
                        val durationStr = String.format("%02d:%02d", mins, secs)

                        val linksObj = vObj.optJSONObject("links")
                        val sourceObj = linksObj?.optJSONObject("source")
                        val hlsUrl = sourceObj?.optJSONObject("HLS")?.optJSONObject("HD")?.optString("href")
                            ?.ifBlank { sourceObj.optJSONObject("HLS")?.optString("href").orEmpty() }
                            ?.ifBlank { sourceObj.optJSONObject("HD")?.optString("href").orEmpty() }
                            ?.ifBlank { sourceObj.optJSONObject("full")?.optString("href").orEmpty() }
                            ?.ifBlank { linksObj.optJSONObject("mobile")?.optJSONObject("source")?.optString("href").orEmpty() }
                            .orEmpty()

                        val playableUrl = hlsUrl.ifBlank { fallbackStreams[(i + vIdx) % fallbackStreams.size] }
                        result.add(
                            HighlightItem(
                                id = "espn-hl-$vidId",
                                title = headlineTitle,
                                tournament = "${sportType.displayName.uppercase()} • ${leagueName.uppercase()}",
                                duration = durationStr,
                                thumbnailUrl = thumb,
                                videoUrl = playableUrl,
                                sport = sportType
                            )
                        )
                        addedVideoClip = true
                    }
                }
            }

            val stateStr = comp.optJSONObject("status")?.optJSONObject("type")?.optString("state")?.lowercase().orEmpty()
            if (!addedVideoClip && stateStr == "post") {
                val competitors = comp.optJSONArray("competitors")
                val c1 = competitors?.optJSONObject(0)
                val c2 = competitors?.optJSONObject(1)
                val s1 = c1?.optString("score").orEmpty()
                val s2 = c2?.optString("score").orEmpty()
                val scoreTag = if (s1.isNotBlank() && s2.isNotBlank()) " ($s1 - $s2)" else ""
                result.add(
                    HighlightItem(
                        id = "espn-recap-$eventId",
                        title = "$eventName$scoreTag - Full Match Highlights",
                        tournament = "${sportType.displayName.uppercase()} • ${leagueName.uppercase()}",
                        duration = "10:30",
                        thumbnailUrl = "",
                        videoUrl = fallbackStreams[i % fallbackStreams.size],
                        sport = sportType
                    )
                )
            }
        }
        return result
    }

    private fun parseIsoDateEpoch(iso: String): Long {
        val trimmed = iso.trim()
        if (trimmed.isBlank()) return 0L
        runCatching { return java.time.Instant.parse(trimmed).toEpochMilli() }
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        )
        for (p in patterns) {
            try {
                val sdf = SimpleDateFormat(p, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = sdf.parse(trimmed)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    private fun buildErrorOrStaleState(
        category: MatchStatus,
        routePath: String,
        error: Throwable,
        parser: (String) -> List<LiveMatch>
    ): CrexCategoryFeedState {
        val cached = routeCache[routePath]
        val errState = routeErrors[routePath]
        val statusCode = (error as? HttpRouteException)?.statusCode ?: (errState?.lastHttpStatusCode ?: 0)
        val cachedMatches = if (cached != null) {
            runCatching {
                parser(cached.rawBody).map { it.copy(isStaleCache = true) }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        return CrexCategoryFeedState(
            category = category,
            matches = cachedMatches,
            isLoading = false,
            errorMessage = error.localizedMessage ?: "Provider request failed",
            httpStatusCode = statusCode,
            isStale = cachedMatches.isNotEmpty(),
            lastUpdatedEpochMs = cached?.fetchedAtEpochMs,
            lastUpdatedDhakaText = cached?.let { formatEpochDhaka(it.fetchedAtEpochMs, includeSeconds = true) },
            backoffUntilEpochMs = errState?.backoffUntilEpochMs ?: 0L
        )
    }

    /**
     * Parses `GET https://api.goscorer.com/api/v3/getLiveMatches`.
     */
    internal fun parseLiveMatchesPayload(rawJson: String): List<LiveMatch> {
        val trimmed = rawJson.trim()
        val matches = mutableListOf<Pair<Int, LiveMatch>>()

        if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            val keys = root.keys()
            while (keys.hasNext()) {
                val matchKey = keys.next()
                val obj = root.optJSONObject(matchKey) ?: continue
                val parsed = parseSingleLiveMatchObject(matchKey, obj)
                if (parsed != null) {
                    matches.add(parsed)
                }
            }
        } else if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val matchKey = obj.optString("mf").ifBlank { obj.optString("id", "live-$i") }
                val parsed = parseSingleLiveMatchObject(matchKey, obj)
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

    private fun parseSingleLiveMatchObject(matchKey: String, obj: JSONObject): Pair<Int, LiveMatch>? {
        val t1Key = obj.optString("b").trim()
        val t2Key = obj.optString("c").trim()
        if (t1Key.isBlank() && t2Key.isBlank()) return null

        val format = obj.optString("fo").trim() // e.g. "Test", "T20I", "ODI", "T20"
        val board = obj.optString("cb").trim()  // e.g. "CSA", "BCCI", "PCB", "ACB"
        val seriesKey = obj.optString("q").removePrefix("^").trim()
        val venueKey = obj.optString("v").trim()
        val resText = obj.optString("res").trim()
        val finishTime = obj.optLong("finishTime", 0L)
        val rawStartEpoch = obj.optLong("ti", 0L).takeIf { it > 0L } ?: obj.optLong("fdst", 0L)
        val startEpochMs = normalizeEpochMs(rawStartEpoch)

        if (seriesKey.isNotBlank() && (board.isNotBlank() || format.isNotBlank())) {
            val meta = listOf(board, format).filter { it.isNotBlank() }.joinToString(" • ")
            if (meta.isNotBlank()) {
                seriesMetaCache[seriesKey] = meta
            }
        }

        val jScore = formatCrexScoreOvers(obj.optString("j"))
        val kScore = formatCrexScoreOvers(obj.optString("k"))
        val lScore = formatCrexScoreOvers(obj.optString("l"))
        val mScore = formatCrexScoreOvers(obj.optString("m"))

        val t1ResolvedName = resolveCrexTeamName(t1Key)
        val t2ResolvedName = resolveCrexTeamName(t2Key)
        val v1 = TeamFlagResolver.resolve(t1ResolvedName, if (t1ResolvedName.startsWith("Team ")) "" else t1Key)
        val v2 = TeamFlagResolver.resolve(t2ResolvedName, if (t2ResolvedName.startsWith("Team ")) "" else t2Key)

        val t1ScoreCombined = listOf(jScore, lScore).filter { it.isNotBlank() }.joinToString(" & ")
        val t2ScoreCombined = listOf(kScore, mScore).filter { it.isNotBlank() }.joinToString(" & ")

        val isPostponedOrCancelled = resText.contains("abandoned", ignoreCase = true) ||
            resText.contains("cancelled", ignoreCase = true) ||
            resText.contains("canceled", ignoreCase = true) ||
            resText.contains("postponed", ignoreCase = true)

        val providerStatusLabel = when {
            resText.contains("abandoned", ignoreCase = true) -> "Abandoned"
            resText.contains("cancel", ignoreCase = true) -> "Cancelled"
            resText.contains("postponed", ignoreCase = true) -> "Postponed"
            else -> ""
        }

        val scoreSummary = buildString {
            if (t1ScoreCombined.isNotBlank()) {
                append("${v1.code} $t1ScoreCombined")
            }
            if (t2ScoreCombined.isNotBlank()) {
                if (isNotEmpty()) append(" vs ")
                append("${v2.code} $t2ScoreCombined")
            }
            if (resText.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append(resText)
            }
        }.ifBlank {
            if (format.isNotBlank()) "$format Match" else "Live Match"
        }

        val dhakaStart = formatEpochDhaka(startEpochMs)
        val matchTimeText = buildString {
            if (dhakaStart.isNotBlank()) {
                append(dhakaStart)
            }
            if (venueKey.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append("Venue #$venueKey")
            }
        }.ifBlank { "Asia/Dhaka Time" }

        val tournamentHeader = buildList {
            if (board.isNotBlank()) add(board)
            if (format.isNotBlank()) add(format)
            if (seriesKey.isNotBlank()) add("Series $seriesKey")
            add("ID: $matchKey")
        }.joinToString(" • ")

        val sortRank = when {
            finishTime == 0L && (jScore.isNotBlank() || kScore.isNotBlank()) -> 0
            finishTime == 0L -> 1
            else -> 2
        }

        val effectiveStatus = if (finishTime > 0L && !isPostponedOrCancelled) {
            MatchStatus.FINISHED
        } else {
            MatchStatus.LIVE
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
            team1Score = t1ScoreCombined,
            team2Score = t2ScoreCombined,
            status = effectiveStatus,
            providerStatusLabel = providerStatusLabel,
            isPostponedOrCancelled = isPostponedOrCancelled,
            timeOrScore = scoreSummary,
            matchTime = matchTimeText,
            startTimeEpochMs = startEpochMs,
            streams = defaultCricketStreams
        )
    }

    /**
     * Parses `POST https://oc.crickapi.com/oc/getHomeUpcomingMatchesCE` with `{"type": 1}`.
     */
    internal fun parseUpcomingMatchesPayload(rawJson: String): List<LiveMatch> {
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
                    parseSingleUpcomingMatchObject(mObj, groupDate)?.let { result.add(it) }
                }
            } else {
                parseSingleUpcomingMatchObject(groupOrMatch, groupDate)?.let { result.add(it) }
            }
        }
        return result.distinctBy { it.id }
    }

    private fun parseSingleUpcomingMatchObject(obj: JSONObject, groupDate: String): LiveMatch? {
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
        val rawDate = obj.optString("dt").ifBlank { groupDate }
        val rawStartEpoch = normalizeEpochMs(obj.optLong("t", 0L))
        val startEpochMs = if (rawStartEpoch > 0L) rawStartEpoch else parseSourceDateEpochDhaka(rawDate)
        val resultNote = obj.optString("result").trim()

        val isPostponedOrCancelled = resultNote.contains("postponed", ignoreCase = true) ||
            resultNote.contains("cancelled", ignoreCase = true) ||
            resultNote.contains("canceled", ignoreCase = true) ||
            resultNote.contains("abandoned", ignoreCase = true)

        val providerStatusLabel = when {
            resultNote.contains("postponed", ignoreCase = true) -> "Postponed"
            resultNote.contains("cancel", ignoreCase = true) -> "Cancelled"
            resultNote.contains("abandoned", ignoreCase = true) -> "Abandoned"
            else -> ""
        }

        val t1Name = resolveCrexTeamName(t1Key)
        val t2Name = resolveCrexTeamName(t2Key)
        val v1 = TeamFlagResolver.resolve(t1Name, if (t1Name.startsWith("Team ")) "" else t1Key)
        val v2 = TeamFlagResolver.resolve(t2Name, if (t2Name.startsWith("Team ")) "" else t2Key)

        val dhakaTime = if (rawStartEpoch > 0L) {
            formatEpochDhaka(rawStartEpoch)
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

        val seriesMeta = seriesMetaCache[seriesKey]
        val tournamentHeader = buildList {
            if (!seriesMeta.isNullOrBlank()) add(seriesMeta)
            else if (formatLabel.isNotBlank()) add(formatLabel)
            if (seriesKey.isNotBlank()) add("Series $seriesKey")
            if (matchNumber.isNotBlank() && matchNumber != "0") add("Match #$matchNumber")
        }.joinToString(" • ").ifBlank { "Upcoming Cricket" }

        val subInfo = buildList {
            if (formatLabel.isNotBlank()) add(formatLabel)
            if (matchNumber.isNotBlank() && matchNumber != "0") add("Match $matchNumber")
            if (resultNote.isNotBlank()) add(resultNote)
        }.joinToString(" • ").ifBlank { "Scheduled" }

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
            status = MatchStatus.UPCOMING,
            providerStatusLabel = providerStatusLabel,
            isPostponedOrCancelled = isPostponedOrCancelled,
            timeOrScore = subInfo,
            matchTime = matchTimeText,
            startTimeEpochMs = startEpochMs,
            streams = defaultCricketStreams
        )
    }

    /**
     * Parses `POST https://oc.crickapi.com/oc/getHomeUpcomingMatchesCE` with `{"type": 0}`.
     */
    internal fun parseFinishedMatchesPayload(rawJson: String): List<LiveMatch> {
        val trimmed = rawJson.trim()
        val result = mutableListOf<LiveMatch>()
        if (!trimmed.startsWith("[")) return result

        val arr = JSONArray(trimmed)
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            learnWinnerNameFromFinishedObject(obj)
        }

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val nestedM = obj.optJSONArray("m")
            if (nestedM != null) {
                for (j in 0 until nestedM.length()) {
                    val item = nestedM.optJSONObject(j) ?: continue
                    learnWinnerNameFromFinishedObject(item)
                    parseSingleFinishedMatchObject(item)?.let { result.add(it) }
                }
            } else {
                parseSingleFinishedMatchObject(obj)?.let { result.add(it) }
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

    private fun parseSingleFinishedMatchObject(obj: JSONObject): LiveMatch? {
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
        val startEpochMs = normalizeEpochMs(obj.optLong("t", 0L)).takeIf { it > 0L }
            ?: parseSourceDateEpochDhaka(dateStr)

        val isPostponedOrCancelled = resultText.contains("abandoned", ignoreCase = true) ||
            resultText.contains("cancelled", ignoreCase = true) ||
            resultText.contains("canceled", ignoreCase = true) ||
            resultText.contains("postponed", ignoreCase = true)

        val providerStatusLabel = when {
            resultText.contains("abandoned", ignoreCase = true) -> "Abandoned"
            resultText.contains("cancel", ignoreCase = true) -> "Cancelled"
            resultText.contains("postponed", ignoreCase = true) -> "Postponed"
            else -> ""
        }

        val bfKey = obj.optString("bf").trim()
        val score1 = obj.optString("score1").trim()
        val overs1 = obj.optString("overs1").trim()
        val score2 = obj.optString("score2").trim()
        val overs2 = obj.optString("overs2").trim()

        val t1ScoreRaw: String
        val t2ScoreRaw: String
        if (bfKey.equals(t2Key, ignoreCase = true)) {
            t2ScoreRaw = formatScoreAndOversPair(score1, overs1)
            t1ScoreRaw = formatScoreAndOversPair(score2, overs2)
        } else {
            t1ScoreRaw = formatScoreAndOversPair(score1, overs1)
            t2ScoreRaw = formatScoreAndOversPair(score2, overs2)
        }

        val t1Name = resolveCrexTeamName(t1Key)
        val t2Name = resolveCrexTeamName(t2Key)
        val v1 = TeamFlagResolver.resolve(t1Name, if (t1Name.startsWith("Team ")) "" else t1Key)
        val v2 = TeamFlagResolver.resolve(t2Name, if (t2Name.startsWith("Team ")) "" else t2Key)

        val scoreAndResult = buildString {
            if (t1ScoreRaw.isNotBlank() || t2ScoreRaw.isNotBlank()) {
                append("${v1.code} ${t1ScoreRaw.ifBlank { "-" }} vs ${v2.code} ${t2ScoreRaw.ifBlank { "-" }}")
            }
            if (resultText.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append(resultText)
            }
        }.ifBlank { "Match Finished" }

        val dhakaDate = formatSourceDateStringDhaka(dateStr)
        val matchTimeText = buildString {
            if (dhakaDate.isNotBlank()) append(dhakaDate)
            if (venueKey.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append("Venue #$venueKey")
            }
        }.ifBlank { "Finished" }

        val seriesMeta = seriesMetaCache[seriesKey]
        val tournamentHeader = buildList {
            if (!seriesMeta.isNullOrBlank()) add(seriesMeta)
            else if (formatLabel.isNotBlank()) add(formatLabel)
            if (seriesKey.isNotBlank()) add("Series $seriesKey")
            if (matchNumber.isNotBlank() && matchNumber != "0") add("Match #$matchNumber")
        }.joinToString(" • ").ifBlank { "Finished Cricket" }

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
            team1Score = t1ScoreRaw,
            team2Score = t2ScoreRaw,
            status = MatchStatus.FINISHED,
            providerStatusLabel = providerStatusLabel,
            isPostponedOrCancelled = isPostponedOrCancelled,
            timeOrScore = scoreAndResult,
            matchTime = matchTimeText,
            startTimeEpochMs = startEpochMs,
            streams = defaultCricketStreams
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
        val lScore = formatCrexScoreOvers(obj.optString("l"))
        val mScore = formatCrexScoreOvers(obj.optString("m"))

        val t1FullInningsScore = listOf(jScore, lScore).filter { it.isNotBlank() }.joinToString(" & ")
        val t2FullInningsScore = listOf(kScore, mScore).filter { it.isNotBlank() }.joinToString(" & ")

        val rawStartEpoch = obj.optLong("mt", 0L)
            .takeIf { it > 0L }
            ?: obj.optLong("fdst", 0L).takeIf { it > 0L }
            ?: obj.optLong("sd", 0L)
        val startEpoch = normalizeEpochMs(rawStartEpoch)
        val startTimeDhaka = formatEpochDhaka(startEpoch)

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
            if (t1FullInningsScore.isNotBlank()) append("$t1Name: $t1FullInningsScore")
            if (t2FullInningsScore.isNotBlank()) {
                if (isNotEmpty()) append(" | ")
                append("$t2Name: $t2FullInningsScore")
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
            team1Score = t1FullInningsScore,
            team2Score = t2FullInningsScore,
            startTimeDhaka = startTimeDhaka,
            statusText = statusSummary,
            recentOvers = recentOvers,
            commentaryLanguages = languages,
            fetchedAtDhaka = formatEpochDhaka(fetchedAtEpochMs, includeSeconds = true)
        )
    }

    private fun normalizeEpochMs(raw: Long): Long {
        if (raw <= 0L) return 0L
        return if (raw in 1..99_999_999_999L) raw * 1000L else raw
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

    private fun formatScoreAndOversPair(score: String, overs: String): String {
        val s = score.trim()
        val o = overs.trim()
        if (s.isBlank()) return ""
        return if (o.isNotBlank() && o != "0" && o != "0.0") "$s ($o ov)" else s
    }

    private fun formatCodeToLabel(ft: Int): String {
        return when (ft) {
            1 -> "ODI"
            2 -> "T20"
            3 -> "Test"
            4 -> "T10"
            else -> ""
        }
    }
}
