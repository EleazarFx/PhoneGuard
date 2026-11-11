package mw.phoneguard.app.data.models

import kotlinx.serialization.Serializable

@Serializable
data class CommandResponse(
    val id: String,
    val type: String,
    val params: Map<String, String>? = null,
    val duration: Int? = null,
    val message: String? = null,
    val minutes: Int? = null,
    val expiresAt: Long? = null,
    val createdAt: Long? = null
)

@Serializable
data class CommandStatusRequest(
    val status: String,
    val message: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
