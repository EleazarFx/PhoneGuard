package mw.phoneguard.services

import at.favre.lib.crypto.bcrypt.BCrypt
import mw.phoneguard.db.PasswordResetTokens
import mw.phoneguard.db.Users
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import kotlin.random.Random

class PasswordResetService(private val emailService: EmailService) {

    suspend fun requestReset(email: String): Boolean {
        val otpToSend = transaction {
            val userRow = Users.selectAll().where { Users.email eq email.lowercase() }.singleOrNull()
                ?: return@transaction null

            val userId = userRow[Users.id].value
            val otp = String.format("%06d", Random.nextInt(1_000_000))
            val expires = LocalDateTime.now().plusMinutes(15)

            PasswordResetTokens.update({ (PasswordResetTokens.userId eq userId) and (PasswordResetTokens.used eq false) }) {
                it[used] = true
            }

            PasswordResetTokens.insert {
                it[PasswordResetTokens.userId] = EntityID(userId, Users)
                it[otpCode] = otp
                it[expiresAt] = expires
            }
            otp
        }

        if (otpToSend != null) {
            try {
                emailService.sendPasswordResetOtp(email, otpToSend)
                println("📧 OTP sent to $email")
            } catch (e: Exception) {
                println("❌ Failed to send email: ${e.message}")
            }
        }
        return true // Always true to prevent email enumeration
    }

    fun resetPassword(email: String, otp: String, newPassword: String): Boolean {
        return transaction {
            val userRow = Users.selectAll().where { Users.email eq email.lowercase() }.singleOrNull()
                ?: return@transaction false
            val userId = userRow[Users.id].value

            val tokenRow = PasswordResetTokens.selectAll()
                .where { (PasswordResetTokens.userId eq userId) and (PasswordResetTokens.otpCode eq otp) and (PasswordResetTokens.used eq false) }
                .singleOrNull() ?: return@transaction false

            if (tokenRow[PasswordResetTokens.expiresAt].isBefore(LocalDateTime.now())) {
                return@transaction false
            }

            val newHash = BCrypt.withDefaults().hashToString(12, newPassword.toCharArray())
            Users.update({ Users.id eq userId }) {
                it[passwordHash] = newHash
            }

            PasswordResetTokens.update({ PasswordResetTokens.id eq tokenRow[PasswordResetTokens.id] }) {
                it[used] = true
            }
            true
        }
    }
}