package mw.phoneguard.models

import kotlinx.serialization.Serializable

/**
 * A command the owner has queued for a device.
 * This is what the DEVICE sees when it polls for work.
 */
@Serializable
data class DeviceCommand(
    val id: String,
    val command: String,
    val payload: String? = null,
    val createdAt: String,
    val expiresAt: String?
)

/**
 * What the OWNER sends to queue a new command.
 * Only these fields are accepted from a user; the server sets the rest.
 */
@Serializable
data class CreateCommandRequest(
    val command: String,               // "LOCK", "ALARM", "MESSAGE", "LOCATE", "LIVE_TRACK", "ALERT_MODE", "LOST_MODE", "WIPE"
    val payload: String? = null,       // JSON-encoded payload
    val expiresInSeconds: Long? = null // optional expiry, default: 24 hours
)

/**
 * What the OWNER sees in command history.
 * Includes status information the device-facing model doesn't need.
 */
@Serializable
data class CommandSummary(
    val id: String,
    val command: String,
    val payload: String?,
    val createdAt: String,
    val expiresAt: String?,
    val deliveredAt: String?,
    val executedAt: String?,
    val error: String?,
    val status: String                 // derived: "queued", "delivered", "executed", "failed", "expired"
)

/**
 * What a DEVICE sends back to report command status.
 */
@Serializable
data class CommandStatusRequest(
    val status: String,                // "delivered", "executed", "failed"
    val error: String? = null          // required when status == "failed"
)
