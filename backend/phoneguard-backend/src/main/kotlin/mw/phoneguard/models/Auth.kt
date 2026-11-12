package mw.phoneguard.models

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String
)

@Serializable
data class RegisterResponse(
    val userId: String,
    val email: String
)

@Serializable
data class DeviceInfo(
    val model: String? = null,
    val manufacturer: String? = null,
    val androidId: String? = null,
    val imei: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    val deviceInfo: DeviceInfo? = null
)

@Serializable
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long
)

@Serializable
data class VerifyNewDeviceRequest(
    val email: String,
    val code: String,
    val deviceInfo: DeviceInfo? = null
)

@Serializable
data class ErrorResponse(
    val error: String
)

@Serializable
data class HealthResponse(
    val status: String,
    val time: Long
)

@Serializable
data class ForgotPasswordRequest(val email: String)

@Serializable
data class ResetPasswordRequest(val email: String, val otp: String, val newPassword: String)