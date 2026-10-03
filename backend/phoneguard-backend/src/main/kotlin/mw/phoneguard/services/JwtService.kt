package mw.phoneguard.services

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

class JwtService(
    secret: String,
    private val issuer: String,
    private val audience: String
) {
    private val algorithm: Algorithm = Algorithm.HMAC256(secret) //HMAC256 algorithm for signing the JWT

    val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .build()

    fun generateAccessToken(userId: String): String {
        val expiresAt = Date(System.currentTimeMillis() + ACCESS_TOKEN_LIFETIME_MS)
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("type", "user")
            .withExpiresAt(expiresAt)
            .sign(algorithm)
    }

    fun generateRefreshToken(userId: String): String {
        val expiresAt = Date(System.currentTimeMillis() + REFRESH_TOKEN_LIFETIME_MS)
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("type", "refresh")
            .withExpiresAt(expiresAt)
            .sign(algorithm)
    }

    fun generateDeviceToken(userId: String, deviceId: String): String {
        val expiresAt = Date(System.currentTimeMillis() + DEVICE_TOKEN_LIFETIME_MS)
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("deviceId", deviceId)
            .withClaim("type", "device")
            .withExpiresAt(expiresAt)
            .sign(algorithm)
    }

    companion object {
        const val ACCESS_TOKEN_LIFETIME_MS = 15L * 60 * 1000                  // 15 minutes
        const val REFRESH_TOKEN_LIFETIME_MS = 30L * 24 * 60 * 60 * 1000       // 30 days
        const val DEVICE_TOKEN_LIFETIME_MS = 365L * 24 * 60 * 60 * 1000       // 365 days
    }
}
