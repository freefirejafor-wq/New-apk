package com.example.data.util

import com.example.data.model.LiveMatch
import com.example.data.model.MatchStatus
import com.example.data.model.SportType
import com.example.data.model.StreamSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Integrates the live cricket score scraping and JSON parsing engine modeled after
 * `https://github.com/mskian/live-cricket-score-api` (Cricbuzz live scores & JSON schema).
 *
 * Strictly filters all matches through [TeamFlagResolver.isSeniorInternationalMatch]
 * so ONLY Senior National Main Teams (no U-17, U-18, U-19, Emerging, A-Teams, Women, or Domestic/Franchise teams)
 * are included.
 */
object MskianLiveCricketFetcher {

    private val defaultStreams = listOf(
        StreamSource(
            name = "T SPORTS FHD (BD)",
            quality = "FHD",
            url = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8",
            isDefault = true
        ),
        StreamSource(
            name = "STAR SPORTS 1 HD",
            quality = "FHD",
            url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        ),
        StreamSource(
            name = "PTV / WILLOW CRICKET HD",
            quality = "HD",
            url = "https://diceyk6a7voy4.cloudfront.net/e78752a1-2e83-43fa-85ae-3d508be29366/hls/de:02b6989d-e714-4227-9c57-a4c1a221a244:playlist.m3u8"
        )
    )

    /**
     * Fetches live, upcoming, and recent Senior International Cricket matches from Cricbuzz
     * using the `mskian/live-cricket-score-api` scraping pattern + ESPNcricinfo live RSS fallback.
     */
    fun fetchSeniorInternationalCricketMatches(httpClient: OkHttpClient): List<LiveMatch> {
        val results = mutableListOf<LiveMatch>()
        val seenKeys = mutableSetOf<String>()

        fun addIfSeniorUnique(match: LiveMatch) {
            if (!TeamFlagResolver.isSeniorInternationalMatch(match.team1Name, match.team2Name, match.tournament)) {
                return
            }
            val key = "${match.team1Code}_${match.team2Code}"
            val reverseKey = "${match.team2Code}_${match.team1Code}"
            if (key !in seenKeys && reverseKey !in seenKeys) {
                seenKeys.add(key)
                results.add(match)
            }
        }

        // 1. Scrape Cricbuzz Live Scores & Upcoming Pages (exact source used by mskian/live-cricket-score-api)
        val cricbuzzUrls = listOf(
            "https://www.cricbuzz.com/cricket-match/live-scores" to MatchStatus.LIVE,
            "https://www.cricbuzz.com/cricket-match/live-scores/upcoming-matches" to MatchStatus.UPCOMING,
            "https://www.cricbuzz.com/cricket-match/live-scores/recent-matches" to MatchStatus.FINISHED
        )

        for ((url, defaultStatus) in cricbuzzUrls) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                    )
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val html = resp.body?.string().orEmpty()
                        if (html.isNotBlank()) {
                            val parsed = parseCricbuzzHtml(html, defaultStatus)
                            parsed.forEach { addIfSeniorUnique(it) }
                        }
                    }
                }
            } catch (_: Exception) {
                // Continue to next source on timeout
            }
        }

        // 2. Also fetch live international cricket scores from Cricinfo Live RSS feed as secondary source
        try {
            val rssReq = Request.Builder()
                .url("https://static.cricinfo.com/rss/livescores.xml")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            httpClient.newCall(rssReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val xml = resp.body?.string().orEmpty()
                    if (xml.isNotBlank()) {
                        val parsedRss = parseCricinfoRss(xml)
                        parsedRss.forEach { addIfSeniorUnique(it) }
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore
        }

        return results
    }

    /**
     * Parses the HTML structure from Cricbuzz live-scores pages (`cb-mtch-lst`, `cb-lv-scrs-well`, etc.)
     * using the same selectors as `mskian/live-cricket-score-api`.
     */
    fun parseCricbuzzHtml(html: String, fallbackStatus: MatchStatus): List<LiveMatch> {
        val matches = mutableListOf<LiveMatch>()

        // Match each match item block in Cricbuzz's live-scores page
        val blockRegex = Regex(
            """<div[^>]*class="[^"]*cb-mtch-lst\s+cb-col\s+cb-col-100\s+cb-tms-itm[^"]*"[^>]*>(.*?)</div>\s*</div>\s*</div>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val anchorTitleRegex = Regex(
            """<a[^>]*class="[^"]*text-hvr-underline[^"]*"[^>]*title="([^"]+)"[^>]*>""",
            RegexOption.IGNORE_CASE
        )
        val scoreSpanRegex = Regex(
            """<div[^>]*class="[^"]*cb-lv-scrs-col[^"]*"[^>]*>(.*?)</div>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val liveTextRegex = Regex(
            """<div[^>]*class="[^"]*(?:cb-text-live|cb-text-complete|cb-text-preview|cb-text-inprogress|cb-text-rain|cb-text-stumps|cb-text-lunch|cb-text-tea)[^"]*"[^>]*>(.*?)</div>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        val blocks = blockRegex.findAll(html).map { it.groupValues[1] }.toList()
        val candidateChunks = if (blocks.isNotEmpty()) {
            blocks
        } else {
            // Fallback: split by cb-mtch-lst
            html.split("cb-mtch-lst").drop(1)
        }

        for (chunk in candidateChunks) {
            val titleMatch = anchorTitleRegex.find(chunk)
                ?: Regex("""title="([^"]+\s+vs\s+[^"]+)"""", RegexOption.IGNORE_CASE).find(chunk)
                ?: continue

            val rawTitle = cleanHtmlText(titleMatch.groupValues[1])
            val teamsAndFormat = parseTeamsFromTitle(rawTitle) ?: continue
            val (team1Raw, team2Raw, matchFormatLabel) = teamsAndFormat

            // Strictly verify Senior National Main Teams (no U18, U19, Youth, A-Team, Women, or Franchise)
            if (!TeamFlagResolver.isSeniorInternationalMatch(team1Raw, team2Raw, rawTitle)) {
                continue
            }

            val rawScoreText = scoreSpanRegex.find(chunk)?.groupValues?.get(1)?.let { cleanHtmlText(it) }.orEmpty()
            val rawStatusUpdate = liveTextRegex.find(chunk)?.groupValues?.get(1)?.let { cleanHtmlText(it) }.orEmpty()

            val v1 = TeamFlagResolver.resolve(team1Raw)
            val v2 = TeamFlagResolver.resolve(team2Raw)

            val resolvedStatus = when {
                rawStatusUpdate.contains("won by", ignoreCase = true) ||
                    rawStatusUpdate.contains("Match drawn", ignoreCase = true) ||
                    rawStatusUpdate.contains("Match tied", ignoreCase = true) -> MatchStatus.FINISHED
                rawScoreText.isNotBlank() ||
                    rawStatusUpdate.contains("opt to", ignoreCase = true) ||
                    rawStatusUpdate.contains("Session", ignoreCase = true) ||
                    rawStatusUpdate.contains("Stumps", ignoreCase = true) ||
                    rawStatusUpdate.contains("Innings Break", ignoreCase = true) ||
                    rawStatusUpdate.contains("need", ignoreCase = true) ||
                    rawStatusUpdate.contains("lead by", ignoreCase = true) ||
                    rawStatusUpdate.contains("trail by", ignoreCase = true) -> MatchStatus.LIVE
                else -> fallbackStatus
            }

            val displayScore = when {
                rawScoreText.isNotBlank() -> rawScoreText
                resolvedStatus == MatchStatus.FINISHED && rawStatusUpdate.isNotBlank() -> rawStatusUpdate
                resolvedStatus == MatchStatus.LIVE -> "${v1.code} vs ${v2.code} • Live"
                else -> matchFormatLabel.ifBlank { "Upcoming International" }
            }

            val displayTimeOrUpdate = when {
                rawStatusUpdate.isNotBlank() -> rawStatusUpdate
                matchFormatLabel.isNotBlank() -> "International • $matchFormatLabel"
                else -> "Today • International Match"
            }

            val tournamentTitle = buildString {
                append("INTL CRICKET • ")
                if (matchFormatLabel.isNotBlank()) {
                    append(matchFormatLabel.uppercase())
                } else {
                    append("${v1.countryName.uppercase()} VS ${v2.countryName.uppercase()}")
                }
            }

            matches.add(
                LiveMatch(
                    id = "cricbuzz-${v1.code.lowercase()}-${v2.code.lowercase()}",
                    tournament = tournamentTitle,
                    sport = SportType.CRICKET,
                    team1Name = v1.countryName,
                    team1Code = v1.code,
                    team1FlagUrl = v1.flagUrl,
                    team2Name = v2.countryName,
                    team2Code = v2.code,
                    team2FlagUrl = v2.flagUrl,
                    status = resolvedStatus,
                    timeOrScore = displayScore.take(36),
                    matchTime = displayTimeOrUpdate.take(55),
                    streams = defaultStreams,
                    isFavorite = v1.code == "BAN" || v2.code == "BAN" || v1.code == "IND" || v2.code == "IND"
                )
            )
        }

        return matches
    }

    /**
     * Parses `mskian/live-cricket-score-api` JSON output format directly if the user or remote feed
     * points to a self-hosted `mskian/live-cricket-score-api` endpoint.
     *
     * Supported fields from `mskian/live-cricket-score-api`:
     * - `title`: e.g. "Bangladesh vs South Africa, 1st Test"
     * - `status` / `update`: e.g. "Day 1: 3rd Session - Bangladesh opt to bat"
     * - `score` / `livescore`: e.g. "BAN 242/6 (68.4)"
     * - `current_batsmen`: list of `{ "name": "...", "score": "..." }`
     * - `current_bowler`: `{ "name": "..." }`
     */
    fun parseMskianApiJson(jsonText: String): List<LiveMatch> {
        val list = mutableListOf<LiveMatch>()
        val trimmed = jsonText.trim()
        if (trimmed.isBlank()) return emptyList()

        try {
            val objects = mutableListOf<JSONObject>()
            if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                if (root.has("title") && (root.has("score") || root.has("livescore") || root.has("status") || root.has("update"))) {
                    objects.add(root)
                } else if (root.has("matches") && root.optJSONArray("matches") != null) {
                    val arr = root.getJSONArray("matches")
                    for (i in 0 until arr.length()) {
                        objects.add(arr.getJSONObject(i))
                    }
                }
            } else if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    if (item.has("title") && (item.has("score") || item.has("livescore"))) {
                        objects.add(item)
                    }
                }
            }

            for (obj in objects) {
                val rawTitle = obj.optString("title", "")
                val parsedTeams = parseTeamsFromTitle(rawTitle) ?: continue
                val (t1Raw, t2Raw, formatLabel) = parsedTeams

                // Strictly reject U17, U18, U19, Youth, Women, A-Teams, or Club/Domestic matches
                if (!TeamFlagResolver.isSeniorInternationalMatch(t1Raw, t2Raw, rawTitle)) {
                    continue
                }

                val v1 = TeamFlagResolver.resolve(t1Raw)
                val v2 = TeamFlagResolver.resolve(t2Raw)

                val rawScore = obj.optString("score").ifBlank { obj.optString("livescore", "") }
                val rawStatus = obj.optString("update").ifBlank { obj.optString("status", "") }
                val runRate = obj.optString("runrate", "")

                // Extract active batsman info if present in mskian/live-cricket-score-api response
                var batterSummary = ""
                val batsmenArr = obj.optJSONArray("current_batsmen")
                if (batsmenArr != null && batsmenArr.length() > 0) {
                    val firstBatter = batsmenArr.optJSONObject(0)
                    if (firstBatter != null) {
                        val bName = firstBatter.optString("name", "")
                        val bScore = firstBatter.optString("score", "")
                        if (bName.isNotBlank()) {
                            batterSummary = "$bName $bScore".trim()
                        }
                    }
                } else {
                    val legacyBatter = obj.optString("batterone", "")
                    val legacyRuns = obj.optString("batsmanonerun", "")
                    if (legacyBatter.isNotBlank() && legacyBatter != "Data Not Found") {
                        batterSummary = "$legacyBatter $legacyRuns".trim()
                    }
                }

                val isFinished = rawStatus.contains("won by", ignoreCase = true) ||
                    rawStatus.contains("Match drawn", ignoreCase = true) ||
                    rawStatus.contains("Complete", ignoreCase = true)
                val isLive = !isFinished && (rawScore.isNotBlank() && !rawScore.equals("Data Not Found", ignoreCase = true))

                val matchStatus = when {
                    isFinished -> MatchStatus.FINISHED
                    isLive -> MatchStatus.LIVE
                    else -> MatchStatus.UPCOMING
                }

                val scoreDisplay = when {
                    rawScore.isNotBlank() && !rawScore.equals("Data Not Found", ignoreCase = true) -> rawScore
                    isFinished && rawStatus.isNotBlank() -> rawStatus
                    else -> "${v1.code} vs ${v2.code}"
                }

                val detailDisplay = buildString {
                    if (rawStatus.isNotBlank() && !rawStatus.equals("Data Not Found", ignoreCase = true)) {
                        append(rawStatus)
                    } else if (formatLabel.isNotBlank()) {
                        append(formatLabel)
                    } else {
                        append("International Cricket")
                    }
                    if (batterSummary.isNotBlank()) {
                        append(" • ")
                        append(batterSummary)
                    } else if (runRate.isNotBlank() && !runRate.equals("Data Not Found", ignoreCase = true)) {
                        append(" ($runRate)")
                    }
                }

                list.add(
                    LiveMatch(
                        id = "mskian-${v1.code.lowercase()}-${v2.code.lowercase()}",
                        tournament = "INTL CRICKET • ${formatLabel.ifBlank { "INTERNATIONAL SERIES" }.uppercase()}",
                        sport = SportType.CRICKET,
                        team1Name = v1.countryName,
                        team1Code = v1.code,
                        team1FlagUrl = v1.flagUrl,
                        team2Name = v2.countryName,
                        team2Code = v2.code,
                        team2FlagUrl = v2.flagUrl,
                        status = matchStatus,
                        timeOrScore = scoreDisplay.take(36),
                        matchTime = detailDisplay.take(60),
                        streams = defaultStreams,
                        isFavorite = true
                    )
                )
            }
        } catch (_: Exception) {
            // Ignore malformed JSON
        }

        return list
    }

    private fun parseCricinfoRss(xml: String): List<LiveMatch> {
        val list = mutableListOf<LiveMatch>()
        val itemRegex = Regex("""<item>(.*?)</item>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val titleRegex = Regex("""<title>(.*?)</title>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

        for (match in itemRegex.findAll(xml)) {
            val itemContent = match.groupValues[1]
            val rawTitle = titleRegex.find(itemContent)?.groupValues?.get(1)?.let { cleanHtmlText(it) } ?: continue
            // Example RSS title: "Bangladesh 242/6 * v South Africa"
            val vsSplit = rawTitle.split(Regex("""\s+(?:vs?\.?)\s+""", RegexOption.IGNORE_CASE), limit = 2)
            if (vsSplit.size < 2) continue

            val side1 = vsSplit[0].trim()
            val side2 = vsSplit[1].trim()

            // Extract team name (strip numbers, slashes, overs, asterisks)
            val team1Clean = side1.replace(Regex("""[\d/*().]+.*$"""), "").trim()
            val team2Clean = side2.replace(Regex("""[\d/*().]+.*$"""), "").trim()

            if (!TeamFlagResolver.isSeniorInternationalMatch(team1Clean, team2Clean, rawTitle)) {
                continue
            }

            val v1 = TeamFlagResolver.resolve(team1Clean)
            val v2 = TeamFlagResolver.resolve(team2Clean)

            val hasDigits = Regex("""\d+/\d+|\d+\s*\*""").containsMatchIn(rawTitle)

            list.add(
                LiveMatch(
                    id = "rss-cric-${v1.code.lowercase()}-${v2.code.lowercase()}",
                    tournament = "INTL CRICKET • LIVE INTERNATIONAL SERIES",
                    sport = SportType.CRICKET,
                    team1Name = v1.countryName,
                    team1Code = v1.code,
                    team1FlagUrl = v1.flagUrl,
                    team2Name = v2.countryName,
                    team2Code = v2.code,
                    team2FlagUrl = v2.flagUrl,
                    status = if (hasDigits) MatchStatus.LIVE else MatchStatus.UPCOMING,
                    timeOrScore = if (hasDigits) rawTitle.take(34) else "${v1.code} vs ${v2.code}",
                    matchTime = "Today • Live International Cricket",
                    streams = defaultStreams,
                    isFavorite = v1.code == "BAN" || v2.code == "BAN"
                )
            )
        }
        return list
    }

    /**
     * Extracts `(team1, team2, matchFormatLabel)` from a Cricbuzz / mskian title like:
     * `"Bangladesh vs South Africa, 1st Test - Live Cricket Score"`
     */
    private fun parseTeamsFromTitle(rawTitle: String): Triple<String, String, String>? {
        val withoutSuffix = rawTitle
            .substringBefore(" - Live")
            .substringBefore(" | ")
            .trim()

        val mainPart = withoutSuffix.substringBefore(",").trim()
        val formatPart = withoutSuffix.substringAfter(",", "").trim()

        val parts = mainPart.split(Regex("""\s+vs\.?\s+|\s+v\s+""", RegexOption.IGNORE_CASE), limit = 2)
        if (parts.size < 2) return null

        val t1 = parts[0].trim()
        val t2 = parts[1].trim()
        if (t1.isBlank() || t2.isBlank()) return null

        return Triple(t1, t2, formatPart)
    }

    private fun cleanHtmlText(raw: String): String {
        return raw
            .replace(Regex("""<[^>]+>"""), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&quot;", "\"")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
