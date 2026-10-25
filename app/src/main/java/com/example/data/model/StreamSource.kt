package com.example.data.model

import java.util.UUID

data class StreamSource(
    val id: String = UUID.randomUUID().toString(),
    val name: String, // e.g., "STREAM 1", "STREAM 2", "SPORT TV1"
    val quality: String = "HD", // FHD, HD, SD
    val url: String, // .m3u8, .mp4, direct stream URL
    val userAgent: String? = null,
    val referer: String? = null,
    val origin: String? = null,
    val cookie: String? = null,
    val drmScheme: String? = null, // e.g. "clearkey", "widevine"
    val drmLicenseUrl: String? = null,
    val isDefault: Boolean = false,
    val isWorking: Boolean? = null, // Result of M3U8 validator check
    val isEnabled: Boolean = true,
    val isAuthorized: Boolean = true,
    val isBackup: Boolean = false,
    val displayOrder: Int = 0
)
