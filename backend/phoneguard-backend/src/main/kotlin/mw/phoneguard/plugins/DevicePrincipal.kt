package mw.phoneguard.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import mw.phoneguard.models.ErrorResponse

/**
 * A small data class holding the authenticated device's identity.
 * Extracted once from the JWT payload so routes don't repeat the parsing.
 */
data class AuthedDevice(
    val userId: String,
    val deviceId: String
)

/**
 * Attempts to extract the authenticated device from the call context.
 * Returns null if the principal is missing or the claims are missing.
 */
fun ApplicationCall.authedDevice(): AuthedDevice? {
    val principal = principal<JWTPrincipal>() ?: return null
    val userId = principal.payload.getClaim("userId").asString() ?: return null
    val deviceId = principal.payload.getClaim("deviceId").asString() ?: return null
    return AuthedDevice(userId, deviceId)
}

/**
 * Requires the authenticated device's token deviceId to match the
 * deviceId from the URL path. If they don't match, responds 403
 * and returns null so the caller can early-return.
 */
suspend fun ApplicationCall.requireMatchingDevice(urlDeviceId: String): AuthedDevice? {
    val authed = authedDevice()
    if (authed == null) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Missing device principal"))
        return null
    }
    if (authed.deviceId != urlDeviceId) {
        respond(
            HttpStatusCode.Forbidden,
            ErrorResponse("Token does not authorize this device")
        )
        return null
    }
    return authed
}
