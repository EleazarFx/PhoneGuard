package mw.phoneguard.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import mw.phoneguard.models.ErrorResponse
import mw.phoneguard.models.LoginRequest
import mw.phoneguard.models.LoginResponse
import mw.phoneguard.models.RegisterRequest
import mw.phoneguard.services.JwtService
import mw.phoneguard.services.UserService

fun Application.configureRouting(jwtService: JwtService) {
    val userService = UserService()

    routing {
        get("/") {
            call.respondText("PhoneGuard backend is alive")
        }

        get("/health") {
            call.respond(mapOf("status" to "ok", "time" to System.currentTimeMillis()))
        }

        post("/auth/register") {
            val req = call.receive<RegisterRequest>()
            val result = userService.register(req)
            if (result == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid email, weak password, or email already registered")
                )
            } else {
                call.respond(HttpStatusCode.Created, result)
            }
        }

        post("/auth/login") {
            val req = call.receive<LoginRequest>()
            val user = userService.authenticate(req)

            if (user == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("Invalid credentials")
                )
                return@post
            }

            val accessToken = jwtService.generateAccessToken(user.id)
            val refreshToken = jwtService.generateRefreshToken(user.id)

            call.respond(
                LoginResponse(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresIn = JwtService.ACCESS_TOKEN_LIFETIME_MS / 1000
                )
            )
        }

        authenticate("auth-jwt") {
            get("/auth/me") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse("Missing principal")
                    )

                val userId = principal.payload.getClaim("userId").asString()
                call.respond(mapOf("userId" to userId))
            }
        }
    }
}
