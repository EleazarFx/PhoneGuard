package mw.phoneguard.app.data.api

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import mw.phoneguard.app.data.models.CommandResponse
import mw.phoneguard.app.data.models.CommandStatusRequest
import mw.phoneguard.app.data.models.PostEventRequest
import mw.phoneguard.app.data.models.RegisterDeviceRequest
import mw.phoneguard.app.data.models.RegisterDeviceResponse

class DeviceApi(private val context: android.content.Context? = null) {

    private val client = ApiClient.http

    suspend fun register(
        userAccessToken: String,
        deviceName: String,
        model: String? = null,
        imei1: String? = null,
        imei2: String? = null
    ): ApiResult<RegisterDeviceResponse> {
        return try {
            val response: HttpResponse =
                client.post("${ApiClient.BASE_URL}/devices") {
                    header("Authorization", "Bearer $userAccessToken")
                    setBody(
                        RegisterDeviceRequest(
                            deviceName = deviceName,
                            model = model,
                            imei1 = imei1,
                            imei2 = imei2
                        )
                    )
                }
            if (response.status == HttpStatusCode.Created) {
                ApiResult.Success(response.body<RegisterDeviceResponse>())
            } else {
                val error = response.body<Map<String, String>>()
                ApiResult.Failure(error["error"] ?: "Device registration failed")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun postEvent(
        deviceToken: String,
        deviceId: String,
        event: mw.phoneguard.app.data.local.EventEntity
    ): ApiResult<Unit> {
        return try {
            val response: HttpResponse = client.post("${ApiClient.BASE_URL}/device/$deviceId/events") {
                header("Authorization", "Bearer $deviceToken")
                setBody(
                    PostEventRequest(
                        type = event.type,
                        timestamp = event.timestamp,
                        lat = event.lat,
                        lng = event.lng,
                        accuracy = event.accuracy,
                        battery = event.battery,
                        captureStatus = event.captureStatus,
                        photoSha256 = event.photoSha256,
                        videoSha256 = event.videoSha256,
                        clientSeq = event.clientSeq
                    )
                )
            }

            if (response.status == HttpStatusCode.Created || response.status == HttpStatusCode.OK) {
                ApiResult.Success(Unit)
            } else {
                val error = try { response.body<Map<String, String>>() } catch (_: Exception) { emptyMap() }
                ApiResult.Failure(error["error"] ?: "Failed to post event")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun getCommands(
        deviceToken: String,
        deviceId: String
    ): ApiResult<List<CommandResponse>> {
        return try {
            val response: HttpResponse = client.get("${ApiClient.BASE_URL}/device/$deviceId/commands") {
                header("Authorization", "Bearer $deviceToken")
            }
            if (response.status == HttpStatusCode.OK) {
                val commands = response.body<List<CommandResponse>>()
                ApiResult.Success(commands)
            } else {
                val error = try { response.body<Map<String, String>>() } catch (_: Exception) { emptyMap() }
                ApiResult.Failure(error["error"] ?: "Failed to fetch commands")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun postCommandStatus(
        deviceToken: String,
        deviceId: String,
        commandId: String,
        status: String,
        message: String? = null
    ): ApiResult<Unit> {
        return try {
            val response: HttpResponse = client.post("${ApiClient.BASE_URL}/device/$deviceId/commands/$commandId/status") {
                header("Authorization", "Bearer $deviceToken")
                setBody(
                    CommandStatusRequest(
                        status = status,
                        message = message
                    )
                )
            }
            if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Created) {
                ApiResult.Success(Unit)
            } else {
                val error = try { response.body<Map<String, String>>() } catch (_: Exception) { emptyMap() }
                ApiResult.Failure(error["error"] ?: "Failed to report command status")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }
}
