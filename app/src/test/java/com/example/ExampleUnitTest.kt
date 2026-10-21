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
}
