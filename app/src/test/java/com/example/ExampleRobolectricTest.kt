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
    fun `repository starts without hardcoded demo fixtures`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SportsStreamRepository(context)

        val matches = repository.matches.value
        // Hardcoded demo matches like fb-spl-hazem-fayha or cric-t20-nz-ind must never appear
        assertTrue(matches.none { it.id == "fb-spl-hazem-fayha" || it.id == "cric-t20-nz-ind" })
    }

    @Test
    fun `repository loads channels and filters publicEnabledStreams by priority and backup`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SportsStreamRepository(context)

        val initialCount = repository.channels.value.size
        assertTrue(initialCount > 0)

        val newChannel = SportsChannel(
            name = "Test Sky Sports Cricket",
            category = "Sports",
            logoText = "SKY",
            streams = listOf(
                StreamSource(id = "bkp", name = "BACKUP SERVER", quality = "HD", url = "https://example.com/backup.m3u8", isEnabled = true, isBackup = true, priorityOrder = 0),
                StreamSource(id = "dis", name = "DISABLED SERVER", quality = "FHD", url = "https://example.com/disabled.m3u8", isEnabled = false, priorityOrder = 0),
                StreamSource(id = "pri", name = "PRIMARY SERVER", quality = "FHD", url = "https://example.com/primary.m3u8", isDefault = true, isEnabled = true, isBackup = false, priorityOrder = 1)
            )
        )

        repository.addOrUpdateChannel(newChannel)
        val added = repository.channels.value.firstOrNull { it.id == newChannel.id }
        assertNotNull(added)
        val pubStreams = added!!.publicEnabledStreams()
        // Disabled stream excluded; Primary stream ordered before Backup stream
        assertEquals(2, pubStreams.size)
        assertEquals("pri", pubStreams[0].id)
        assertEquals("bkp", pubStreams[1].id)
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
    fun `unmapped stream override surfaces admin notice instead of silent deletion`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SportsStreamRepository(context)

        val orphanOverride = LiveMatch(
            id = "old-provider-id-999",
            tournament = "FOOTBALL | UEFA CHAMPIONS LEAGUE",
            sport = SportType.FOOTBALL,
            status = MatchStatus.LIVE,
            matchTime = "Today",
            timeOrScore = "LIVE NOW",
            team1Name = "Real Madrid",
            team1Code = "RMA",
            team2Name = "Bayern Munich",
            team2Code = "BAY",
            isNationalTeam = false,
            streams = listOf(
                StreamSource(name = "SERVER 1 (FHD)", quality = "1080p FHD", url = "https://example.com/1.m3u8", isDefault = true)
            )
        )
        repository.addOrUpdateMatch(orphanOverride)

        val notices = repository.unmappedNotices.value
        assertTrue(notices.any { it.previousMatchId == "old-provider-id-999" })
    }
}
