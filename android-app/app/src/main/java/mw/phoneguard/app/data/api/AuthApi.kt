package mw.phoneguard.app.data.api

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import mw.phoneguard.app.data.models.LoginRequest
import mw.phoneguard.app.data.models.LoginResponse
import mw.phoneguard.app.data.models.RegisterRequest
import mw.phoneguard.app.data.models.RegisterResponse

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

    suspend fun login(email: String, password: String): ApiResult<LoginResponse> {
        return try {
            val response: HttpResponse = client.post("${ApiClient.BASE_URL}/auth/login") {
                setBody(LoginRequest(email = email, password = password))
            }
            if (response.status == HttpStatusCode.OK) {
                ApiResult.Success(response.body<LoginResponse>())
            } else {
                val error = response.body<Map<String, String>>()
                ApiResult.Failure(error["error"] ?: "Login failed")
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
}