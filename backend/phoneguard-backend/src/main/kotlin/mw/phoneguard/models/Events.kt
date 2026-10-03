package mw.phoneguard.models

import kotlinx.serialization.Serializable

/**
 * What a device sends when it posts a theft event.
 * The backend generates the event's UUID server-side, so no id here.
 */
@Serializable
data class PostEventRequest(
    val type: String,                  // "wrong_pin", "sim_change", "snatch", "tamper", "restart"
    val timestamp: Long,               // epoch millis from the device
    val lat: Double? = null,
    val lng: Double? = null,
    val accuracy: Double? = null,
    val battery: Int? = null,
    val captureStatus: String? = null, // "captured", "no_face", "back_fallback", "camera_blocked", "deferred"
    val photoSha256: String? = null,
    val clientSeq: Long? = null        // per-device monotonic sequence number
)

@Serializable
data class EventSummary(
    val id: String,
    val type: String,
    val timestamp: String,
    val lat: Double?,
    val lng: Double?,
    val battery: Int?,
    val captureStatus: String?,
    val threatLevel: String?,          // "Low", "Medium", "High" — computed server-side
    val createdAt: String
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
