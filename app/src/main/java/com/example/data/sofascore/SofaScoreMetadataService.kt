package com.example.data.sofascore

import com.example.data.model.SportsChannel
import com.example.data.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class SofaScoreSupplementalMeta(
    val eventId: String,
    val tvChannelNames: List<String> = emptyList(),
    val matchedApprovedStreams: List<StreamSource> = emptyList(),
    val approvedHighlightUrl: String? = null,
    val statusNote: String = "OK"
)

/**
 * Optional SofaScore supplemental metadata helper for:
 *  - TV channels: `https://api.sofascore.com/api/v1/tv/event/{eventId}/country-channels`
 *  - Highlights / media: `https://api.sofascore.com/api/v1/event/{eventId}/media`
 *
 * Rules:
 *  - Only called when a verified real `sofascoreEventId` is provided.
 *  - Handles 403 / empty / network errors gracefully without breaking the main match feed.
 *  - Matches TV channel names ONLY against admin-configured & enabled `SportsChannel`s.
 *  - Only returns a highlight URL if it is an approved playable stream/video URL.
 */
class SofaScoreMetadataService(
    baseClient: OkHttpClient? = null
) {
    companion object {
        private const val TV_CHANNELS_URL_TEMPLATE = "https://api.sofascore.com/api/v1/tv/event/%s/country-channels"
        private const val EVENT_MEDIA_URL_TEMPLATE = "https://api.sofascore.com/api/v1/event/%s/media"
    }

    private val httpClient: OkHttpClient = (baseClient?.newBuilder() ?: OkHttpClient.Builder())
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, SofaScoreSupplementalMeta>()

    @Volatile
    var lastStatusSummary: String = "Idle (Optional metadata)"
        private set

    suspend fun fetchSupplementalMetadata(
        sofascoreEventId: String?,
        approvedChannels: List<SportsChannel>
    ): SofaScoreSupplementalMeta? = withContext(Dispatchers.IO) {
        val cleanId = sofascoreEventId?.trim().orEmpty()
        if (cleanId.isBlank() || !cleanId.all { it.isDigit() }) {
            return@withContext null
        }

        cache[cleanId]?.let { return@withContext it }

        val channelNames = mutableListOf<String>()
        var highlightUrl: String? = null
        val notes = mutableListOf<String>()

        // 1. Country TV Channels (optional)
        try {
            val tvUrl = String.format(Locale.US, TV_CHANNELS_URL_TEMPLATE, cleanId)
            val req = Request.Builder()
                .url(tvUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; SportsLiveTV)")
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        channelNames.addAll(parseTvChannelsPayload(body))
                    }
                } else {
                    notes.add("TV HTTP ${resp.code}")
                }
            }
        } catch (e: Exception) {
            notes.add("TV unavailable: ${e.localizedMessage ?: "error"}")
        }

        // 2. Event Media / Highlights (optional)
        try {
            val mediaUrl = String.format(Locale.US, EVENT_MEDIA_URL_TEMPLATE, cleanId)
            val req = Request.Builder()
                .url(mediaUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; SportsLiveTV)")
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        highlightUrl = parseApprovedPlayableHighlight(body)
                    }
                } else {
                    notes.add("Media HTTP ${resp.code}")
                }
            }
        } catch (e: Exception) {
            notes.add("Media unavailable: ${e.localizedMessage ?: "error"}")
        }

        val matchedStreams = matchBroadcastNamesToApprovedChannels(channelNames, approvedChannels)
        val statusNote = if (notes.isEmpty()) "Synced event $cleanId" else notes.joinToString(", ")
        lastStatusSummary = statusNote

        val meta = SofaScoreSupplementalMeta(
            eventId = cleanId,
            tvChannelNames = channelNames,
            matchedApprovedStreams = matchedStreams,
            approvedHighlightUrl = highlightUrl,
            statusNote = statusNote
        )
        cache[cleanId] = meta
        meta
    }

    internal fun parseTvChannelsPayload(rawJson: String): List<String> {
        return try {
            val root = JSONObject(rawJson)
            val names = mutableSetOf<String>()
            val countryChannels = root.optJSONObject("countryChannels")
            if (countryChannels != null) {
                val keys = countryChannels.keys()
                while (keys.hasNext()) {
                    val cc = keys.next()
                    val arr = countryChannels.optJSONArray(cc) ?: continue
                    for (i in 0 until arr.length()) {
                        val item = arr.opt(i)
                        when (item) {
                            is JSONObject -> {
                                val n = item.optString("name").trim()
                                if (n.isNotBlank()) names.add(n)
                            }
                            is String -> {
                                if (item.isNotBlank()) names.add(item.trim())
                            }
                        }
                    }
                }
            }
            names.toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun parseApprovedPlayableHighlight(rawJson: String): String? {
        return try {
            val root = JSONObject(rawJson)
            val mediaArr = root.optJSONArray("media") ?: return null
            for (i in 0 until mediaArr.length()) {
                val m = mediaArr.optJSONObject(i) ?: continue
                val url = m.optString("url").trim()
                if (url.endsWith(".m3u8", ignoreCase = true) || url.endsWith(".mp4", ignoreCase = true)) {
                    return url
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Matches SofaScore broadcast channel names strictly against admin-approved & enabled channels.
     */
    internal fun matchBroadcastNamesToApprovedChannels(
        broadcastChannelNames: List<String>,
        approvedChannels: List<SportsChannel>
    ): List<StreamSource> {
        if (broadcastChannelNames.isEmpty() || approvedChannels.isEmpty()) return emptyList()
        val matchedStreams = mutableListOf<StreamSource>()
        for (channel in approvedChannels.filter { it.isEnabled }) {
            val chNorm = channel.name.trim().lowercase(Locale.US)
            if (chNorm.length < 3) continue
            val isMatch = broadcastChannelNames.any { tvName ->
                val tvNorm = tvName.trim().lowercase(Locale.US)
                tvNorm.contains(chNorm) || chNorm.contains(tvNorm)
            }
            if (isMatch) {
                matchedStreams.addAll(channel.publicEnabledStreams())
            }
        }
        return matchedStreams.distinctBy { it.id }
    }
}
