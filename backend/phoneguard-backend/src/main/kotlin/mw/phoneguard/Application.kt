package mw.phoneguard

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import mw.phoneguard.db.DatabaseFactory
import mw.phoneguard.plugins.configureRouting
import mw.phoneguard.plugins.configureSecurity
import mw.phoneguard.plugins.configureSerialization
import mw.phoneguard.services.JwtService

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureSerialization()
    DatabaseFactory.init(this)

    val config = environment.config
    val jwtSecret = config.property("jwt.secret").getString()
    val jwtIssuer = config.property("jwt.issuer").getString()
    val jwtAudience = config.property("jwt.audience").getString()
    val jwtService = JwtService(jwtSecret, jwtIssuer, jwtAudience)

    configureSecurity(jwtService)
    configureRouting(jwtService)
}