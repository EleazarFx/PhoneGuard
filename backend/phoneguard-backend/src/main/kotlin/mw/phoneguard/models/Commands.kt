package mw.phoneguard.models

import kotlinx.serialization.Serializable

/**
 * A command the owner has queued for this device.
 * The device fetches these and executes them.
 */
@Serializable
data class DeviceCommand(
    val id: String,
    val command: String,               // "LOCK", "ALARM", "MESSAGE", "LOCATE", "LIVE_TRACK", etc.
    val payload: String? = null,       // JSON-encoded payload (e.g. {"duration": 30})
    val createdAt: String,
    val expiresAt: String?
)

@Serializable
data class CommandStatusRequest(
    val status: String,                // "delivered", "executed", "failed"
    val error: String? = null          // populated when status == "failed"
)
