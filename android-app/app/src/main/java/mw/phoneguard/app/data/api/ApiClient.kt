package mw.phoneguard.app.data.api

import android.os.Build
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * A single shared HTTP client for the whole app.
 *
 * `BASE_URL` automatically detects the runtime environment:
 * - **Android Emulator:** Uses `http://10.0.2.2:8080` (native host loopback).
 * - **Physical Device:** Uses `http://127.0.0.1:8080` (requires `adb reverse tcp:8080 tcp:8080`).
 */
object ApiClient {

    private val isEmulator: Boolean
        get() = (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)

    val BASE_URL: String
        get() = if (isEmulator) {
            "http://10.0.2.2:8080"
        } else {
            "http://127.0.0.1:8080"
        }

    val http: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                })
            }

            install(Logging) {
                level = LogLevel.INFO
            }

            defaultRequest {
                contentType(ContentType.Application.Json)
            }
        }
    }
}
