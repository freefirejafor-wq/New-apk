package com.example

import com.example.data.crex.CrexCricketService
import com.example.data.espn.EspnFootballService
import com.example.data.model.MatchStatus
import com.example.data.model.SportType
import com.example.data.util.TeamFlagResolver
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleUnitTest {

    // Reference time: 2026-10-21 12:00:00 UTC (18:00:00 Asia/Dhaka) -> 1792584000000L
    private val refNowMs = 1792584000000L

    @Test
    fun crexLivePayload_objectKeyedByMatchId_parsesCorrectlyWithoutScoresOnCard() {
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
                "ti": 1792570000000,
                "v": "4R"
              }
            }
        """.trimIndent()

        val matches = service.parseLiveMatchesPayload(sampleLiveJson, referenceNowEpochMs = refNowMs)
        assertEquals(1, matches.size)
        val m = matches.first()
        assertEquals("10MR", m.id)
        assertEquals(MatchStatus.LIVE, m.status)
        assertEquals(SportType.CRICKET, m.sport)
        assertEquals("South Africa", m.team1Name)
        assertEquals("Australia", m.team2Name)
        assertTrue(m.isNationalTeam)
        // MatchCard must never expose raw innings scores in timeOrScore
        assertFalse(m.timeOrScore.contains("425/9"))
        assertTrue(m.matchTime.contains("BST"))
    }

    @Test
    fun crexLivePayload_finishedOrUpcomingInLiveEndpoint_neverMarkedLiveFalsely() {
        val service = CrexCricketService()
        val sampleJson = """
            {
              "FIN1": {
                "b": "U",
                "c": "S",
                "cb": "ICC",
                "fo": "ODI",
                "j": "302/9(50.0",
                "k": "215/10(38.4",
                "res": "Pakistan won by 87 runs",
                "ti": 1792560000000
              },
              "UPC1": {
                "b": "Y",
                "c": "W",
                "cb": "ACB",
                "fo": "ODI",
                "j": "",
                "k": "",
                "res": "",
                "ti": 1792650000000
              }
            }
        """.trimIndent()

        val matches = service.parseLiveMatchesPayload(sampleJson, referenceNowEpochMs = refNowMs)
        val fin = matches.first { it.id == "FIN1" }
        val upc = matches.first { it.id == "UPC1" }
        assertEquals(MatchStatus.FINISHED, fin.status)
        assertEquals(MatchStatus.UPCOMING, upc.status)
    }

    @Test
    fun crexUpcomingPayload_groupedByDate_parsesCorrectlyWithin3DayDhakaWindow() {
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

        val matches = service.parseUpcomingMatchesPayload(sampleUpcomingJson, referenceNowEpochMs = refNowMs)
        assertEquals(1, matches.size)
        val m = matches.first()
        assertEquals("13TK", m.id)
        assertEquals(MatchStatus.UPCOMING, m.status)
        assertEquals("Afghanistan", m.team1Name)
        assertEquals("Bangladesh", m.team2Name)
        assertTrue(m.matchTime.contains("BST"))
    }

    @Test
    fun crexFinishedPayload_array_parsesWithinDhakaWindowAndExcludesScoresFromCard() {
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

        val matches = service.parseFinishedMatchesPayload(sampleFinishedJson, referenceNowEpochMs = refNowMs)
        assertEquals(1, matches.size)
        val m = matches.first()
        assertEquals("13NC", m.id)
        assertEquals(MatchStatus.FINISHED, m.status)
        assertEquals("Pakistan", m.team1Name)
        assertEquals("England", m.team2Name)
        assertFalse(m.timeOrScore.contains("302/9"))
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
              "mt": 1792560000000,
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

        val detail = service.parseSv3DetailPayload("10MR", sampleSv3Json, refNowMs)
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
    fun espnScoreboard_clubAndInternationalParsing_excludesYouthAndRespectsLogosAndDates() {
        val espnService = EspnFootballService()
        val sampleEspnJson = """
            {
              "leagues": [{"name": "English Premier League"}],
              "events": [
                {
                  "id": "401650001",
                  "date": "2026-10-21T19:00Z",
                  "name": "Arsenal at Chelsea",
                  "competitions": [
                    {
                      "competitors": [
                        {
                          "homeAway": "home",
                          "score": "2",
                          "team": {
                            "displayName": "Chelsea",
                            "abbreviation": "CHE",
                            "logo": "https://a.espncdn.com/i/teamlogos/soccer/500/363.png"
                          }
                        },
                        {
                          "homeAway": "away",
                          "score": "1",
                          "team": {
                            "displayName": "Arsenal",
                            "abbreviation": "ARS",
                            "logo": "https://a.espncdn.com/i/teamlogos/soccer/500/359.png"
                          }
                        }
                      ],
                      "status": {
                        "type": {
                          "state": "in",
                          "completed": false,
                          "shortDetail": "67'"
                        }
                      }
                    }
                  ]
                },
                {
                  "id": "401650002",
                  "date": "2026-10-21T16:00Z",
                  "name": "Chelsea U21 vs Arsenal U21",
                  "competitions": [
                    {
                      "competitors": [
                        {
                          "homeAway": "home",
                          "team": {"displayName": "Chelsea U21", "abbreviation": "CHU"}
                        },
                        {
                          "homeAway": "away",
                          "team": {"displayName": "Arsenal U21", "abbreviation": "ARU"}
                        }
                      ],
                      "status": {"type": {"state": "pre"}}
                    }
                  ]
                },
                {
                  "id": "401650003",
                  "date": "2026-10-28T19:00Z",
                  "name": "Liverpool at Everton",
                  "competitions": [
                    {
                      "competitors": [
                        {
                          "homeAway": "home",
                          "team": {"displayName": "Everton", "abbreviation": "EVE"}
                        },
                        {
                          "homeAway": "away",
                          "team": {"displayName": "Liverpool", "abbreviation": "LIV"}
                        }
                      ],
                      "status": {"type": {"state": "pre"}}
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = espnService.parseScoreboardJson("eng.1", sampleEspnJson, referenceNowEpochMs = refNowMs)
        // Youth U21 match (401650002) and next-week match outside Yesterday/Today/Tomorrow (401650003) must be excluded!
        assertEquals(1, parsed.size)
        val match = parsed.first()
        assertEquals("espn-401650001", match.id)
        assertEquals(SportType.FOOTBALL, match.sport)
        assertEquals(MatchStatus.LIVE, match.status)
        assertFalse(match.isNationalTeam)
        assertEquals("https://a.espncdn.com/i/teamlogos/soccer/500/363.png", match.team1FlagUrl)
        assertEquals("https://a.espncdn.com/i/teamlogos/soccer/500/359.png", match.team2FlagUrl)
        // Club team without logo must NEVER fall back to a country flag
        val clubFallback = TeamFlagResolver.resolveForMatch("England FC", "ENG", existingFlag = "", isNationalTeam = false)
        assertEquals("", clubFallback.flagUrl)
        assertEquals("", clubFallback.emoji)
    }
}
