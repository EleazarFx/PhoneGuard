package mw.phoneguard.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond
import mw.phoneguard.services.JwtService

fun Application.configureSecurity(jwtService: JwtService) {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "phoneguard"
            verifier(jwtService.verifier)

            validate { credential ->
                val type = credential.payload.getClaim("type").asString()
                val userId = credential.payload.getClaim("userId").asString()
                if (type == "user" && !userId.isNullOrBlank()) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }

            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Invalid or expired token")
                )
            }
        }
    }
}
