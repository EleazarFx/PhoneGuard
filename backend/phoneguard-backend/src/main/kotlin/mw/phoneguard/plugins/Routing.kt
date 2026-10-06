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

import mw.phoneguard.models.CommandStatusRequest
import mw.phoneguard.models.CreateCommandRequest


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


            // Queue a command for a device
            post("/devices/{deviceId}/commands") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.payload.getClaim("userId").asString()
                val deviceId = call.parameters["deviceId"]!!

                val req = call.receive<CreateCommandRequest>()
                val result = deviceService.createCommand(
                    userId = userId,
                    deviceId = deviceId,
                    command = req.command,
                    payload = req.payload,
                    expiresInSeconds = req.expiresInSeconds
                )

                if (result == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid command, or device not found, or not owned by you")
                    )
                    return@post
                }

                call.respond(HttpStatusCode.Created, result)
            }

            // Read command history for a device
            get("/devices/{deviceId}/commands") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.payload.getClaim("userId").asString()
                val deviceId = call.parameters["deviceId"]!!

                val commands = deviceService.listCommandsForOwner(userId, deviceId)
                if (commands == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Device not found or not owned by you"))
                    return@get
                }
                call.respond(commands)
            }

            // Read the event feed for a device
            get("/devices/{deviceId}/events") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.payload.getClaim("userId").asString()
                val deviceId = call.parameters["deviceId"]!!

                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val events = deviceService.listEventsForOwner(userId, deviceId, limit)
                if (events == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Device not found or not owned by you"))
                    return@get
                }
                call.respond(events)
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


            // Report status of a command the device fetched
            post("/device/{deviceId}/commands/{commandId}/status") {
                val urlDeviceId = call.parameters["deviceId"]!!
                val authed = call.requireMatchingDevice(urlDeviceId) ?: return@post
                val commandId = call.parameters["commandId"]!!

                val req = call.receive<CommandStatusRequest>()

                if (req.status !in setOf("delivered", "executed", "failed")) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid status"))
                    return@post
                }

                val ok = deviceService.updateCommandStatus(
                    deviceId = authed.deviceId,
                    commandId = commandId,
                    status = req.status,
                    error = req.error
                )

                if (!ok) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Command not found for this device"))
                    return@post
                }

                call.respond(HttpStatusCode.OK, mapOf("acknowledged" to true))
            }


        }


    }
}
