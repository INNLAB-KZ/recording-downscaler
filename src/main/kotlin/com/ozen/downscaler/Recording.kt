package com.ozen.downscaler

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Recording(
    val id: String,
    @SerialName("channel_id") val channelId: String,
    @SerialName("s3_key") val s3Key: String? = null,
    val url: String? = null,
    @SerialName("audio_s3_key") val audioS3Key: String? = null,
    @SerialName("audio_url") val audioUrl: String? = null,
    @SerialName("video_quality") val videoQuality: String? = null,
    @SerialName("audio_quality") val audioQuality: String? = null,
    val status: String,
)
