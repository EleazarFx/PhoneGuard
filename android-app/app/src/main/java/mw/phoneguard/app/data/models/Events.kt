package mw.phoneguard.app.data.models

import kotlinx.serialization.Serializable

@Serializable
data class PostEventRequest(
    val type: String,
    val timestamp: Long,
    val lat: Double? = null,
    val lng: Double? = null,
    val accuracy: Double? = null,
    val battery: Int? = null,
    val captureStatus: String? = null,
    val photoSha256: String? = null,
    val clientSeq: Long? = null
)