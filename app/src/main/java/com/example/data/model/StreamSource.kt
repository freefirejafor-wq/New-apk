package com.example.data.model

import java.util.UUID

data class StreamSource(
    val id: String = UUID.randomUUID().toString(),
    val name: String, // e.g., "WILLOW FHD", "SKY CRICKET FHD", "TAPMAD FHD", "SD SERVER"
    val quality: String = "FHD", // FHD, HD, SD
    val url: String, // .m3u8, .mp4, direct stream URL (kept internal; not shown in public UI)
    val userAgent: String? = null,
    val referer: String? = null,
    val isDefault: Boolean = false,
    val isWorking: Boolean? = null, // Result of M3U8 validator check
    val isEnabled: Boolean = true, // Admin control: whether this stream is enabled for public users
    val priorityOrder: Int = 0, // Lower number = higher priority in stream chooser
    val isBackup: Boolean = false // Whether this stream is a backup server
)
