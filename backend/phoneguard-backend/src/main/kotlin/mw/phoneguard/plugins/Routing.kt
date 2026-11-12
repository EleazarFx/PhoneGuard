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
import io.ktor.server.routing.delete
import io.ktor.server.routing.routing
import mw.phoneguard.models.ContactDto
import mw.phoneguard.models.CreateContactRequest
import mw.phoneguard.models.ErrorResponse
import mw.phoneguard.models.LoginRequest
import mw.phoneguard.models.LoginResponse
import mw.phoneguard.models.RegisterRequest
import mw.phoneguard.services.ContactService
import mw.phoneguard.services.JwtService
import mw.phoneguard.services.SmsCommandService
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
import mw.phoneguard.models.ForgotPasswordRequest
import mw.phoneguard.models.ResetPasswordRequest
import mw.phoneguard.models.VerifyNewDeviceRequest
import mw.phoneguard.services.PasswordResetService
import mw.phoneguard.services.EmailService
import mw.phoneguard.services.NewDeviceService
import java.util.UUID

fun Application.configureRouting(jwtService: JwtService, emailService: EmailService) {
    val userService = UserService()
    val deviceService = DeviceService()
    val contactService = ContactService()
    val resetService = PasswordResetService(emailService)
    val newDeviceService = NewDeviceService(emailService)

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

            val fingerprint = newDeviceService.computeFingerprint(req.deviceInfo)
            val userIdUuid = UUID.fromString(user.id)

            // Edge case per spec Section 9.9 / Requirement 11c:
            // If account has no email verified yet (emailVerified == false), fall back to allowing login
            // and record device as known so the user is not blocked.
            if (!user.emailVerified) {
                newDeviceService.recordKnownDevice(userIdUuid, fingerprint)
                val accessToken = jwtService.generateAccessToken(user.id)
                val refreshToken = jwtService.generateRefreshToken(user.id)
                call.respond(
                    LoginResponse(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresIn = JwtService.ACCESS_TOKEN_LIFETIME_MS / 1000
                    )
                )
                return@post
            }

            val isKnown = newDeviceService.isDeviceKnown(userIdUuid, fingerprint)
            if (isKnown) {
                val accessToken = jwtService.generateAccessToken(user.id)
                val refreshToken = jwtService.generateRefreshToken(user.id)
                call.respond(
                    LoginResponse(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresIn = JwtService.ACCESS_TOKEN_LIFETIME_MS / 1000
                    )
                )
            } else {
                val sent = newDeviceService.createAndSendVerificationCode(user, fingerprint)
                if (!sent) {
                    call.respond(
                        HttpStatusCode.TooManyRequests,
                        ErrorResponse("Rate limit exceeded: maximum 3 verification codes per hour.")
                    )
                    return@post
                }
                call.respond(
                    HttpStatusCode.Accepted,
                    mapOf(
                        "status" to "verification_required",
                        "message" to "New device detected. Verification code sent to email."
                    )
                )
            }
        }

        post("/auth/verify-new-device") {
            val req = call.receive<VerifyNewDeviceRequest>()
            val fingerprint = newDeviceService.computeFingerprint(req.deviceInfo)
            val userIdStr = newDeviceService.verifyCodeAndRegisterDevice(req.email, req.code, fingerprint)

            if (userIdStr == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid or expired verification code")
                )
                return@post
            }

            val accessToken = jwtService.generateAccessToken(userIdStr)
            val refreshToken = jwtService.generateRefreshToken(userIdStr)

            call.respond(
                LoginResponse(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresIn = JwtService.ACCESS_TOKEN_LIFETIME_MS / 1000
                )
            )
        }

        post("/auth/forgot-password") {
            val req = call.receive<ForgotPasswordRequest>()
            resetService.requestReset(req.email)
            call.respond(HttpStatusCode.OK, mapOf("message" to "If this email exists, you'll receive a code."))
        }

        post("/auth/reset-password") {
            val req = call.receive<ResetPasswordRequest>()
            if (req.newPassword.length < 8) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Password must be at least 8 characters"))
                return@post
            }
            val success = resetService.resetPassword(req.email, req.otp, req.newPassword)
            if (success) {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Password reset successful."))
            } else {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid or expired code."))
            }
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

            // List trusted contacts for user
            get("/contacts") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.payload.getClaim("userId").asString()
                call.respond(contactService.listForUser(userId))
            }

            // Create trusted contact for user (max 3 per user)
            post("/contacts") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.payload.getClaim("userId").asString()
                val req = call.receive<CreateContactRequest>()
                val (contact, error) = contactService.createContact(userId, req)
                if (contact == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(error ?: "Failed to create contact"))
                } else {
                    call.respond(HttpStatusCode.Created, contact)
                }
            }

            // Delete trusted contact for user
            delete("/contacts/{id}") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.payload.getClaim("userId").asString()
                val contactId = call.parameters["id"]!!
                val success = contactService.deleteContact(userId, contactId)
                if (!success) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Contact not found"))
                } else {
                    call.respond(HttpStatusCode.OK, mapOf("deleted" to true))
                }
            }
        }

        // Incoming SMS simulation / endpoint to test SMS command parsing
        post("/sms/incoming") {
            @kotlinx.serialization.Serializable
            data class IncomingSmsRequest(val sender: String, val message: String)

            val req = call.receive<IncomingSmsRequest>()
            val result = SmsCommandService.processIncomingSms(req.sender, req.message, deviceService)
            if (result.success) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
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
