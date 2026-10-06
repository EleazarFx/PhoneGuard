package mw.phoneguard.models

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

@Serializable
data class EventSummary(
    val id: String,
    val type: String,
    val timestamp: Long,               // epoch millis from device
    val receivedAt: String,            // server timestamp ISO-ish
    val lat: Double?,
    val lng: Double?,
    val accuracy: Double?,
    val battery: Int?,
    val captureStatus: String?,
    val photoSha256: String?,
    val threatLevel: String?
)

@Serializable
data class HeartbeatRequest(
    val battery: Int? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val alertModeActive: Boolean = false
)

@Serializable
data class HeartbeatResponse(
    val serverTime: Long,
    val acknowledged: Boolean
)
