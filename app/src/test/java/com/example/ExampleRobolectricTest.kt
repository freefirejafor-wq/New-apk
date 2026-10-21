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
        assertEquals("Football Live TV", appName)
    }

    @Test
    fun `repository loads default matches with multiple streams`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SportsStreamRepository(context)

        val matches = repository.matches.value
        assertTrue("Matches should not be empty", matches.isNotEmpty())

        val firstMatch = matches.first()
        assertTrue("First match should have multiple stream servers", firstMatch.streams.size >= 2)
        assertNotNull("Match tournament should not be null", firstMatch.tournament)
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

        val match = repository.matches.value.first()
        val initialFav = match.isFavorite

        repository.toggleMatchFavorite(match.id)
        val updated = repository.matches.value.first { it.id == match.id }
        assertEquals(!initialFav, updated.isFavorite)
    }
}
