package mw.phoneguard.models

import kotlinx.serialization.Serializable

@Serializable
data class RegisterDeviceRequest(
    val deviceName: String,
    val model: String? = null,
    val imei1: String? = null,
    val imei2: String? = null
)

@Serializable
data class RegisterDeviceResponse(
    val deviceId: String,
    val deviceToken: String,
    val deviceName: String
)

@Serializable
data class DeviceSummary(
    val id: String,
    val deviceName: String,
    val model: String?,
    val status: String,
    val lastSeen: String?,
    val lastBattery: Int?
)
