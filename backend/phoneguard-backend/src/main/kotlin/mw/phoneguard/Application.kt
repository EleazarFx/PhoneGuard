package mw.phoneguard

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import mw.phoneguard.db.DatabaseFactory
import mw.phoneguard.plugins.configureRouting
import mw.phoneguard.plugins.configureSecurity
import mw.phoneguard.plugins.configureSerialization
import mw.phoneguard.services.EmailService          
import mw.phoneguard.services.JwtService

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureSerialization()
    DatabaseFactory.init(this)

    val config = environment.config
    val jwtService = JwtService(
        config.property("jwt.secret").getString(),
        config.property("jwt.issuer").getString(),
        config.property("jwt.audience").getString()
    )

    val emailService = EmailService(config)

    configureSecurity(jwtService)
    configureRouting(jwtService, emailService)
}