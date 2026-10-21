package com.example.data.model

import java.util.UUID

data class StreamSource(
    val id: String = UUID.randomUUID().toString(),
    val name: String, // e.g., "WILLOW FHD", "SKY CRICKET FHD", "TAPMAD FHD", "SD SERVER"
    val quality: String = "FHD", // FHD, HD, SD
    val url: String, // .m3u8, .mp4, direct stream URL
    val userAgent: String? = null,
    val referer: String? = null,
    val isDefault: Boolean = false,
    val isWorking: Boolean? = null // Result of M3U8 validator check
)
