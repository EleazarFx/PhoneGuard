package mw.phoneguard.app.data.api

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
 * The BASE_URL is `http://localhost:8080` — when running on a phone, the
 * localhost is tunneled through `adb reverse` to the laptop's backend.
 *
 * Once the app runs against a real server, this becomes the server's URL.
 */
object ApiClient {

    const val BASE_URL = "http://localhost:8080"

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