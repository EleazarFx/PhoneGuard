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

import mw.phoneguard.models.HealthResponse

import mw.phoneguard.models.RegisterDeviceRequest
import mw.phoneguard.models.RegisterDeviceResponse
import mw.phoneguard.services.DeviceService

import mw.phoneguard.models.HeartbeatRequest
import mw.phoneguard.models.HeartbeatResponse
import mw.phoneguard.models.PostEventRequest


fun Application.configureRouting(jwtService: JwtService) {
    val userService = UserService()
    val deviceService = DeviceService()

    routing {
        get("/") {
            call.respondText("PhoneGuard backend is alive")
        }

        get("/health") {
            call.respond(HealthResponse("ok", System.currentTimeMillis()))
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

            // Register a new device (owner must be authenticated)
            post("/devices") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.payload.getClaim("userId").asString()

                val req = call.receive<RegisterDeviceRequest>()
                val device = deviceService.registerForUser(userId, req)

                if (device == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid device data"))
                    return@post
                }

                val deviceToken = jwtService.generateDeviceToken(userId, device.id)
                call.respond(
                    HttpStatusCode.Created,
                    RegisterDeviceResponse(
                        deviceId = device.id,
                        deviceToken = deviceToken,
                        deviceName = device.deviceName
                    )
                )
            }

            // List all devices owned by the authenticated user
            get("/devices") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.payload.getClaim("userId").asString()
                call.respond(deviceService.listForUser(userId))
            }


        }


        // Device-scoped routes (auth-device scheme)
        authenticate("auth-device") {

            // Post an event (the phone reporting a theft trigger)
            post("/device/{deviceId}/events") {
                val urlDeviceId = call.parameters["deviceId"]!!
                val authed = call.requireMatchingDevice(urlDeviceId) ?: return@post

                val req = call.receive<PostEventRequest>()
                val event = deviceService.postEvent(authed.deviceId, req)

                if (event == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid event data"))
                    return@post
                }

                call.respond(HttpStatusCode.Created, event)
            }

            // Heartbeat (the phone saying "I'm alive")
            post("/device/{deviceId}/heartbeat") {
                val urlDeviceId = call.parameters["deviceId"]!!
                val authed = call.requireMatchingDevice(urlDeviceId) ?: return@post

                val req = call.receive<HeartbeatRequest>()
                val ok = deviceService.recordHeartbeat(authed.deviceId, req)

                if (!ok) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Device not found"))
                    return@post
                }

                call.respond(HeartbeatResponse(serverTime = System.currentTimeMillis(), acknowledged = true))
            }

            // Fetch pending commands (the phone polling for work to do)
            get("/device/{deviceId}/commands") {
                val urlDeviceId = call.parameters["deviceId"]!!
                val authed = call.requireMatchingDevice(urlDeviceId) ?: return@get

                val commands = deviceService.pendingCommands(authed.deviceId)
                call.respond(commands)
            }
        }


    }
}
