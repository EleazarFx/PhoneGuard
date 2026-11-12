package mw.phoneguard.services

import mw.phoneguard.db.KnownDevices
import mw.phoneguard.db.NewDeviceVerificationTokens
import mw.phoneguard.db.Users
import mw.phoneguard.models.DeviceInfo
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random

class NewDeviceService(private val emailService: EmailService) {

    fun computeFingerprint(deviceInfo: DeviceInfo?): String {
        if (deviceInfo == null) return "unknown_device"
        val raw = listOfNotNull(
            deviceInfo.imei?.takeIf { it.isNotBlank() },
            deviceInfo.androidId?.takeIf { it.isNotBlank() },
            deviceInfo.manufacturer?.takeIf { it.isNotBlank() },
            deviceInfo.model?.takeIf { it.isNotBlank() }
        ).joinToString(":")
        if (raw.isBlank()) return "unknown_device"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun isDeviceKnown(userId: UUID, fingerprint: String): Boolean {
        return transaction {
            val row = KnownDevices.selectAll()
                .where { (KnownDevices.userId eq userId) and (KnownDevices.deviceFingerprint eq fingerprint) }
                .singleOrNull()

            if (row != null) {
                // Update lastSeenAt
                KnownDevices.update({ KnownDevices.id eq row[KnownDevices.id] }) {
                    it[lastSeenAt] = LocalDateTime.now()
                }
                true
            } else {
                false
            }
        }
    }

    fun recordKnownDevice(userId: UUID, fingerprint: String) {
        transaction {
            val existing = KnownDevices.selectAll()
                .where { (KnownDevices.userId eq userId) and (KnownDevices.deviceFingerprint eq fingerprint) }
                .singleOrNull()

            val now = LocalDateTime.now()
            if (existing != null) {
                KnownDevices.update({ KnownDevices.id eq existing[KnownDevices.id] }) {
                    it[lastSeenAt] = now
                }
            } else {
                KnownDevices.insert {
                    it[KnownDevices.userId] = EntityID(userId, Users)
                    it[deviceFingerprint] = fingerprint
                    it[firstSeenAt] = now
                    it[lastSeenAt] = now
                }
            }
        }
    }

    fun checkRateLimit(email: String): Boolean {
        return transaction {
            val oneHourAgo = LocalDateTime.now().minusHours(1)
            val count = NewDeviceVerificationTokens.selectAll()
                .where { (NewDeviceVerificationTokens.email eq email.lowercase()) and (NewDeviceVerificationTokens.createdAt.greaterEq(oneHourAgo)) }
                .count()
            count < 3
        }
    }

    suspend fun createAndSendVerificationCode(user: AuthenticatedUser, fingerprint: String): Boolean {
        if (!checkRateLimit(user.email)) {
            return false
        }

        val otp = String.format("%06d", Random.nextInt(1_000_000))
        val expires = LocalDateTime.now().plusMinutes(15)
        val userId = UUID.fromString(user.id)

        transaction {
            // Mark existing unused tokens for this user as used
            NewDeviceVerificationTokens.update({
                (NewDeviceVerificationTokens.userId eq userId) and (NewDeviceVerificationTokens.used eq false)
            }) {
                it[used] = true
            }

            NewDeviceVerificationTokens.insert {
                it[NewDeviceVerificationTokens.userId] = EntityID(userId, Users)
                it[email] = user.email.lowercase()
                it[deviceFingerprint] = fingerprint
                it[otpCode] = otp
                it[expiresAt] = expires
                it[used] = false
                it[createdAt] = LocalDateTime.now()
            }
        }

        try {
            emailService.sendNewDeviceVerificationCode(user.email, otp)
            println("📧 New device verification code sent to ${user.email}")
        } catch (e: Exception) {
            println("❌ Failed to send verification email: ${e.message}")
        }
        return true
    }

    fun verifyCodeAndRegisterDevice(email: String, code: String, fingerprint: String): String? {
        return transaction {
            val userRow = Users.selectAll()
                .where { Users.email eq email.lowercase() }
                .singleOrNull()
                ?: return@transaction null
            val userId = userRow[Users.id].value

            val tokenRow = NewDeviceVerificationTokens.selectAll()
                .where {
                    (NewDeviceVerificationTokens.userId eq userId) and
                    (NewDeviceVerificationTokens.otpCode eq code) and
                    (NewDeviceVerificationTokens.used eq false)
                }
                .singleOrNull()
                ?: return@transaction null

            if (tokenRow[NewDeviceVerificationTokens.expiresAt].isBefore(LocalDateTime.now())) {
                return@transaction null
            }

            // Mark token as used
            NewDeviceVerificationTokens.update({ NewDeviceVerificationTokens.id eq tokenRow[NewDeviceVerificationTokens.id] }) {
                it[used] = true
            }

            // Record device as known
            val now = LocalDateTime.now()
            val existingDevice = KnownDevices.selectAll()
                .where { (KnownDevices.userId eq userId) and (KnownDevices.deviceFingerprint eq fingerprint) }
                .singleOrNull()

            if (existingDevice != null) {
                KnownDevices.update({ KnownDevices.id eq existingDevice[KnownDevices.id] }) {
                    it[lastSeenAt] = now
                }
            } else {
                KnownDevices.insert {
                    it[KnownDevices.userId] = EntityID(userId, Users)
                    it[deviceFingerprint] = fingerprint
                    it[firstSeenAt] = now
                    it[lastSeenAt] = now
                }
            }

            userId.toString()
        }
    }
}
