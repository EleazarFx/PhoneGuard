package mw.phoneguard.app.data.api

import android.content.Context
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import mw.phoneguard.app.data.models.CommandResponse
import mw.phoneguard.app.data.models.CommandStatusRequest
import mw.phoneguard.app.data.models.RegisterDeviceRequest
import mw.phoneguard.app.data.models.RegisterDeviceResponse
import java.io.File

class DeviceApi(private val context: Context? = null) {

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
            val photoFile = context?.let { File(it.filesDir, "captures/${event.id}.jpg") }
            val response: HttpResponse = if (photoFile != null && photoFile.exists()) {
                client.post("${ApiClient.BASE_URL}/device/$deviceId/events") {
                    header("Authorization", "Bearer $deviceToken")
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("type", event.type)
                                append("timestamp", event.timestamp.toString())
                                if (event.lat != null) append("lat", event.lat.toString())
                                if (event.lng != null) append("lng", event.lng.toString())
                                if (event.accuracy != null) append("accuracy", event.accuracy.toString())
                                if (event.battery != null) append("battery", event.battery.toString())
                                if (event.captureStatus != null) append("captureStatus", event.captureStatus)
                                if (event.photoSha256 != null) append("photoSha256", event.photoSha256)
                                if (event.clientSeq != null) append("clientSeq", event.clientSeq.toString())
                                append("photo", photoFile.readBytes(), Headers.build {
                                    append(HttpHeaders.ContentType, "image/jpeg")
                                    append(HttpHeaders.ContentDisposition, "filename=\"${event.id}.jpg\"")
                                })
                            }
                        )
                    )
                }
            } else {
                client.post("${ApiClient.BASE_URL}/device/$deviceId/events") {
                    header("Authorization", "Bearer $deviceToken")
                    setBody(
                        mw.phoneguard.app.data.models.PostEventRequest(
                            type = event.type,
                            timestamp = event.timestamp,
                            lat = event.lat,
                            lng = event.lng,
                            accuracy = event.accuracy,
                            battery = event.battery,
                            captureStatus = event.captureStatus,
                            photoSha256 = event.photoSha256,
                            clientSeq = event.clientSeq
                        )
                    )
                }
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
