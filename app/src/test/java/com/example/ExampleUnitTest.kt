package com.example

import com.example.data.crex.CrexCricketService
import com.example.data.model.MatchStatus
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleUnitTest {

    @Test
    fun crexLivePayload_objectKeyedByMatchId_parsesCorrectly() {
        val service = CrexCricketService()
        val sampleLiveJson = """
            {
              "10MR": {
                "b": "P",
                "c": "Q",
                "cb": "CSA",
                "fo": "Test",
                "j": "!425/9(127.1",
                "k": "53/4(21.4",
                "q": "^2EW",
                "res": "Stumps",
                "ti": 1792310400000,
                "v": "4R"
              }
            }
        """.trimIndent()

        val matches = service.parseLiveMatchesPayload(sampleLiveJson)
        assertEquals(1, matches.size)
        val m = matches.first()
        assertEquals("10MR", m.id)
        assertEquals(MatchStatus.LIVE, m.status)
        assertEquals("South Africa", m.team1Name)
        assertEquals("Australia", m.team2Name)
        assertTrue(m.timeOrScore.contains("425/9 (127.1 ov)"))
        assertTrue(m.timeOrScore.contains("53/4 (21.4 ov)"))
        assertTrue(m.timeOrScore.contains("Stumps"))
        assertTrue(m.matchTime.contains("BST"))
    }

    @Test
    fun crexUpcomingPayload_groupedByDate_parsesCorrectly() {
        val service = CrexCricketService()
        val sampleUpcomingJson = """
            [
              {
                "dt": "2026/10/21",
                "m": [
                  {
                    "id": 51608,
                    "result": "",
                    "sf": "2MN",
                    "t1f": "Y",
                    "t2f": "W",
                    "mf": "13TK",
                    "mn": "3",
                    "ft": 1,
                    "dt": "2026/10/21",
                    "vf": "6U",
                    "t": 1792582200000,
                    "nf": "13TK"
                  }
                ]
              }
            ]
        """.trimIndent()

        val matches = service.parseUpcomingMatchesPayload(sampleUpcomingJson)
        assertEquals(1, matches.size)
        val m = matches.first()
        assertEquals("13TK", m.id)
        assertEquals(MatchStatus.UPCOMING, m.status)
        assertEquals("Afghanistan", m.team1Name)
        assertEquals("Bangladesh", m.team2Name)
        assertTrue(m.timeOrScore.contains("ODI"))
        assertTrue(m.matchTime.contains("BST"))
    }

    @Test
    fun crexFinishedPayload_array_parsesScoresAndLearnsWinner() {
        val service = CrexCricketService()
        val sampleFinishedJson = """
            [
              {
                "t1f": "U",
                "t2f": "S",
                "sfkey": "2M3",
                "date": "10/20/2026",
                "match_number": "2",
                "match_type": 5,
                "mfkey": "13NC",
                "result": "Pakistan won by 87 runs",
                "bf": "U",
                "score1": "302/9",
                "overs1": "50.0",
                "score2": "215/10",
                "overs2": "38.4",
                "winner": "U",
                "ft": 1,
                "vf": "24"
              }
            ]
        """.trimIndent()

        val matches = service.parseFinishedMatchesPayload(sampleFinishedJson)
        assertEquals(1, matches.size)
        val m = matches.first()
        assertEquals("13NC", m.id)
        assertEquals(MatchStatus.FINISHED, m.status)
        assertEquals("Pakistan", m.team1Name)
        assertEquals("England", m.team2Name)
        assertTrue(m.timeOrScore.contains("302/9 (50.0 ov)"))
        assertTrue(m.timeOrScore.contains("215/10 (38.4 ov)"))
        assertTrue(m.timeOrScore.contains("Pakistan won by 87 runs"))
    }

    @Test
    fun crexSv3Detail_parsesSpeechNamesAndRecentOvers() {
        val service = CrexCricketService()
        val sampleSv3Json = """
            {
              "a": "P.Q",
              "fo": "Test",
              "mn": "2",
              "sf": "2EW",
              "j": "425/9(127.1",
              "k": "54/4(21.5",
              "mt": 1792310400000,
              "speech_names": {
                "P": "South Africa",
                "Q": "Australia"
              },
              "rb": [
                {
                  "o": 19,
                  "r": 2,
                  "ts": "50/4",
                  "b": [{"u": "0"}, {"u": "2"}]
                }
              ],
              "cl": [{"code": "en", "name": "English"}]
            }
        """.trimIndent()

        val detail = service.parseSv3DetailPayload("10MR", sampleSv3Json, 1792560000000L)
        assertEquals("10MR", detail.matchId)
        assertEquals("South Africa", detail.team1Name)
        assertEquals("Australia", detail.team2Name)
        assertEquals("Test", detail.format)
        assertEquals("425/9 (127.1 ov)", detail.team1Score)
        assertEquals("54/4 (21.5 ov)", detail.team2Score)
        assertTrue(detail.startTimeDhaka.contains("BST"))
        assertEquals(1, detail.recentOvers.size)
    }

    @Test
    fun dhakaThreeDayWindow_filtersYesterdayTodayTomorrowStrictly() {
        val nowMs = 1792560000000L // Fixed epoch
        val oneDayMs = 24L * 3600_000L
        assertTrue(CrexCricketService.isWithinDhakaThreeDayWindow(nowMs, nowMs))
        assertTrue(CrexCricketService.isWithinDhakaThreeDayWindow(nowMs - oneDayMs, nowMs))
        assertTrue(CrexCricketService.isWithinDhakaThreeDayWindow(nowMs + oneDayMs, nowMs))
        assertFalse(CrexCricketService.isWithinDhakaThreeDayWindow(nowMs - 3 * oneDayMs, nowMs))
        assertFalse(CrexCricketService.isWithinDhakaThreeDayWindow(nowMs + 3 * oneDayMs, nowMs))

        assertTrue(CrexCricketService.matchesDateFilterDhaka(nowMs, com.example.data.model.MatchDateFilter.TODAY, nowMs))
        assertTrue(CrexCricketService.matchesDateFilterDhaka(nowMs - oneDayMs, com.example.data.model.MatchDateFilter.YESTERDAY, nowMs))
        assertTrue(CrexCricketService.matchesDateFilterDhaka(nowMs + oneDayMs, com.example.data.model.MatchDateFilter.TOMORROW, nowMs))
    }

    @Test
    fun clubFootball_neverUsesCountryFlagUrl() {
        val clubResolved = com.example.data.util.TeamFlagResolver.resolve(
            teamName = "Real Madrid",
            teamCode = "RMA",
            existingFlag = "https://flagcdn.com/w160/es.png",
            isClubFootball = true
        )
        assertFalse("Club football must not use a country flag URL", clubResolved.flagUrl.contains("flagcdn.com"))
        assertTrue("Club football should resolve official club crest", clubResolved.flagUrl.contains("espncdn.com"))

        val nationalResolved = com.example.data.util.TeamFlagResolver.resolve(
            teamName = "Argentina",
            teamCode = "ARG",
            existingFlag = "",
            isClubFootball = false
        )
        assertTrue("National team must resolve country flag", nationalResolved.flagUrl.contains("flagcdn.com/w160/ar.png"))
    }

    @Test
    fun espnFootball_statusMapping_onlyConfirmedInProgressIsLive_missingOrStaleIsUnknown() {
        val service = CrexCricketService()
        val nowIso = java.time.Instant.now().toString()
        val futureIso = java.time.Instant.now().plusSeconds(3600 * 5).toString()
        val sampleEspnJson = """
            {
              "leagues": [{"name": "English Premier League"}],
              "events": [
                {
                  "id": "401",
                  "name": "Arsenal vs Chelsea",
                  "date": "$nowIso",
                  "competitions": [
                    {
                      "id": "401",
                      "date": "$nowIso",
                      "status": {
                        "type": {
                          "state": "in",
                          "name": "STATUS_IN_PROGRESS",
                          "completed": false,
                          "detail": "54'"
                        }
                      },
                      "competitors": [
                        {"homeAway": "home", "score": "2", "team": {"displayName": "Arsenal", "abbreviation": "ARS"}},
                        {"homeAway": "away", "score": "1", "team": {"displayName": "Chelsea", "abbreviation": "CHE"}}
                      ]
                    }
                  ]
                },
                {
                  "id": "402",
                  "name": "Liverpool vs Everton",
                  "date": "$futureIso",
                  "competitions": [
                    {
                      "id": "402",
                      "date": "$futureIso",
                      "status": {
                        "type": {
                          "state": "pre",
                          "name": "STATUS_SCHEDULED",
                          "completed": false,
                          "detail": "Scheduled"
                        }
                      },
                      "competitors": [
                        {"homeAway": "home", "score": "0", "team": {"displayName": "Liverpool", "abbreviation": "LIV"}},
                        {"homeAway": "away", "score": "0", "team": {"displayName": "Everton", "abbreviation": "EVE"}}
                      ]
                    }
                  ]
                },
                {
                  "id": "403",
                  "name": "Fulham vs Brentford",
                  "date": "$nowIso",
                  "competitions": [
                    {
                      "id": "403",
                      "date": "$nowIso",
                      "status": {
                        "type": {
                          "state": "",
                          "name": "",
                          "completed": false,
                          "detail": ""
                        }
                      },
                      "competitors": [
                        {"homeAway": "home", "team": {"displayName": "Fulham", "abbreviation": "FUL"}},
                        {"homeAway": "away", "team": {"displayName": "Brentford", "abbreviation": "BRE"}}
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = service.parseEspnScoreboardPayload(
            rawJson = sampleEspnJson,
            sportType = com.example.data.model.SportType.FOOTBALL,
            leagueCode = "eng.1"
        )
        assertEquals(3, parsed.size)

        val liveMatch = parsed.first { it.id == "espn-football-401" }
        assertTrue(liveMatch.isConfirmedLive)
        assertEquals(MatchStatus.LIVE, liveMatch.status)
        assertEquals("Live", liveMatch.providerStatusLabel)

        val upcomingMatch = parsed.first { it.id == "espn-football-402" }
        assertFalse(upcomingMatch.isConfirmedLive)
        assertEquals(MatchStatus.UPCOMING, upcomingMatch.status)
        assertEquals("Upcoming", upcomingMatch.providerStatusLabel)

        val unknownMatch = parsed.first { it.id == "espn-football-403" }
        assertFalse("Missing status must never be marked Live", unknownMatch.isConfirmedLive)
        assertTrue("Missing status must be flagged as Unknown", unknownMatch.isStatusUnknown)
        assertEquals("Unknown", unknownMatch.providerStatusLabel)
    }

    @Test
    fun espnFootball_excludesYouthU17U18AcademyReserveAndBTeams() {
        val service = CrexCricketService()
        val nowIso = java.time.Instant.now().toString()
        val youthJson = """
            {
              "leagues": [{"name": "Spanish LaLiga"}],
              "events": [
                {
                  "id": "501",
                  "name": "Barcelona B vs Real Madrid Castilla",
                  "date": "$nowIso",
                  "competitions": [
                    {
                      "id": "501",
                      "date": "$nowIso",
                      "status": {"type": {"state": "pre", "name": "STATUS_SCHEDULED", "completed": false}},
                      "competitors": [
                        {"homeAway": "home", "team": {"displayName": "Barcelona B", "abbreviation": "BAR"}},
                        {"homeAway": "away", "team": {"displayName": "Real Madrid Castilla", "abbreviation": "RMC"}}
                      ]
                    }
                  ]
                },
                {
                  "id": "502",
                  "name": "England U17 vs France U17",
                  "date": "$nowIso",
                  "competitions": [
                    {
                      "id": "502",
                      "date": "$nowIso",
                      "status": {"type": {"state": "in", "name": "STATUS_IN_PROGRESS", "completed": false}},
                      "competitors": [
                        {"homeAway": "home", "team": {"displayName": "England U17", "abbreviation": "ENG"}},
                        {"homeAway": "away", "team": {"displayName": "France U17", "abbreviation": "FRA"}}
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = service.parseEspnScoreboardPayload(
            rawJson = youthJson,
            sportType = com.example.data.model.SportType.FOOTBALL,
            leagueCode = "esp.1"
        )
        assertTrue("Youth, U17/U18, reserve, and B-team matches must be excluded", parsed.isEmpty())
    }
}

