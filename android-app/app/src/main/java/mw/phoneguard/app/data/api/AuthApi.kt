package mw.phoneguard.app.data.api

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import mw.phoneguard.app.data.models.DeviceInfo
import mw.phoneguard.app.data.models.LoginRequest
import mw.phoneguard.app.data.models.LoginResponse
import mw.phoneguard.app.data.models.RegisterRequest
import mw.phoneguard.app.data.models.RegisterResponse
import mw.phoneguard.app.data.models.ForgotPasswordRequest
import mw.phoneguard.app.data.models.ResetPasswordRequest
import mw.phoneguard.app.data.models.VerifyNewDeviceRequest

/**
 * Result type used by every API function.
 *
 * We don't want exceptions to escape into the UI. Every call returns either
 * Success(value) or Failure(message).
 */
sealed class ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>()
    data class Failure(val message: String) : ApiResult<Nothing>()
}

sealed class LoginApiResult {
    data class Success(val response: LoginResponse) : LoginApiResult()
    data class VerificationRequired(val message: String) : LoginApiResult()
}

class AuthApi {

    private val client = ApiClient.http

    suspend fun register(email: String, password: String): ApiResult<RegisterResponse> {
        return try {
            val response: HttpResponse = client.post("${ApiClient.BASE_URL}/auth/register") {
                setBody(RegisterRequest(email = email, password = password))
            }
            if (response.status == HttpStatusCode.Created) {
                ApiResult.Success(response.body<RegisterResponse>())
            } else {
                val error = response.body<Map<String, String>>()
                ApiResult.Failure(error["error"] ?: "Registration failed")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun login(email: String, password: String, deviceInfo: DeviceInfo?): ApiResult<LoginApiResult> {
        return try {
            val response: HttpResponse = client.post("${ApiClient.BASE_URL}/auth/login") {
                setBody(LoginRequest(email = email, password = password, deviceInfo = deviceInfo))
            }
            when (response.status) {
                HttpStatusCode.OK -> {
                    ApiResult.Success(LoginApiResult.Success(response.body<LoginResponse>()))
                }
                HttpStatusCode.Accepted -> {
                    val body = response.body<Map<String, String>>()
                    ApiResult.Success(LoginApiResult.VerificationRequired(body["message"] ?: "Verification code sent to email"))
                }
                else -> {
                    val error = response.body<Map<String, String>>()
                    ApiResult.Failure(error["error"] ?: "Login failed")
                }
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun verifyNewDevice(email: String, code: String, deviceInfo: DeviceInfo?): ApiResult<LoginResponse> {
        return try {
            val response: HttpResponse = client.post("${ApiClient.BASE_URL}/auth/verify-new-device") {
                setBody(VerifyNewDeviceRequest(email = email, code = code, deviceInfo = deviceInfo))
            }
            if (response.status == HttpStatusCode.OK) {
                ApiResult.Success(response.body<LoginResponse>())
            } else {
                val error = response.body<Map<String, String>>()
                ApiResult.Failure(error["error"] ?: "Verification failed")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun me(accessToken: String): ApiResult<String> {
        return try {
            val response: HttpResponse = client.post("${ApiClient.BASE_URL}/auth/me") {
                // We'll add auth header injection in the next step.
            }
            ApiResult.Success("not implemented yet")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun requestPasswordReset(email: String): ApiResult<Unit> {
        return try {
            val response: HttpResponse = client.post("${ApiClient.BASE_URL}/auth/forgot-password") {
                setBody(ForgotPasswordRequest(email = email))
            }
            if (response.status == HttpStatusCode.OK) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure("Could not request reset")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun resetPassword(email: String, otp: String, newPassword: String): ApiResult<Unit> {
        return try {
            val response: HttpResponse = client.post("${ApiClient.BASE_URL}/auth/reset-password") {
                setBody(ResetPasswordRequest(email = email, otp = otp, newPassword = newPassword))
            }
            if (response.status == HttpStatusCode.OK) {
                ApiResult.Success(Unit)
            } else {
                val error = response.body<Map<String, String>>()
                ApiResult.Failure(error["error"] ?: "Reset failed")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }
}
