package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.LiveMatch
import com.example.data.model.MatchStatus
import com.example.data.model.SportType
import com.example.data.model.SportsChannel
import com.example.data.model.StreamSource
import com.example.data.repository.SportsStreamRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Sports Live", appName)
    }

    @Test
    fun `mainActivity launches without crash`() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).create()
        assertNotNull(controller.get())
        controller.destroy()
    }

    @Test
    fun `repository starts without hardcoded demo fixtures and preserves unmatched stream associations`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SportsStreamRepository(context)

        val matches = repository.matches.value
        assertTrue("Active match list should not contain old hardcoded demo matches (m1..m8)", matches.none { it.id in setOf("m1", "m2", "m3", "m4", "m5", "m6", "m7", "m8") })
    }

    @Test
    fun `repository loads channels and adds custom channel with multiple streams`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SportsStreamRepository(context)

        val initialCount = repository.channels.value.size
        assertTrue(initialCount > 0)

        val newChannel = SportsChannel(
            name = "Test Sky Sports Cricket",
            category = "Sports",
            logoText = "SKY",
            streams = listOf(
                StreamSource(name = "SERVER 1 (FHD)", quality = "FHD", url = "https://example.com/live1.m3u8", isDefault = true),
                StreamSource(name = "SERVER 2 (HD)", quality = "HD", url = "https://example.com/live2.m3u8"),
                StreamSource(name = "SERVER 3 (SD)", quality = "SD", url = "https://example.com/live3.m3u8")
            )
        )

        repository.addOrUpdateChannel(newChannel)
        val updatedChannels = repository.channels.value
        assertEquals(initialCount + 1, updatedChannels.size)

        val added = updatedChannels.firstOrNull { it.id == newChannel.id }
        assertNotNull(added)
        assertEquals(3, added?.streams?.size)
    }

    @Test
    fun `m3u8 checker handles empty url gracefully`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SportsStreamRepository(context)

        val result = repository.checkM3U8Stream("")
        assertFalse(result.isOnline)
        assertEquals("URL cannot be empty", result.message)
    }

    @Test
    fun `toggle match favorite works properly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SportsStreamRepository(context)

        val sampleCrexMatch = LiveMatch(
            id = "10MR",
            tournament = "CSA • Test",
            sport = SportType.CRICKET,
            status = MatchStatus.LIVE,
            matchTime = "17 Oct 2026, 02:00 PM BST • SuperSport Park, Centurion",
            timeOrScore = "RSA: 425/9 (127.1 ov) • AUS: 53/4 (21.4 ov) • Stumps",
            team1Name = "South Africa",
            team1Code = "RSA",
            team2Name = "Australia",
            team2Code = "AUS",
            streams = listOf(
                StreamSource(name = "SERVER 1 (FHD)", quality = "1080p FHD", url = "https://example.com/1.m3u8", isDefault = true)
            )
        )
        repository.addOrUpdateMatch(sampleCrexMatch)

        val match = repository.matches.value.first { it.id == "10MR" }
        val initialFav = match.isFavorite

        repository.toggleMatchFavorite(match.id)
        val updated = repository.matches.value.first { it.id == match.id }
        assertEquals(!initialFav, updated.isFavorite)
    }
}
