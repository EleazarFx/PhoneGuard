package mw.phoneguard.services

import io.ktor.server.config.ApplicationConfig
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

class EmailService(config: ApplicationConfig) {

    private val smtpUser: String = config.property("smtp.user").getString()
    private val smtpPassword: String = config.property("smtp.password").getString()
    private val fromName: String = config.property("smtp.fromName").getString()
    private val smtpHost: String = config.property("smtp.host").getString()
    private val smtpPort: String = config.property("smtp.port").getString()

    private val session: Session = run {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.starttls.required", "true")
            put("mail.smtp.host", smtpHost)
            put("mail.smtp.port", smtpPort)
            put("mail.smtp.connectiontimeout", "5000")
            put("mail.smtp.timeout", "5000")
            put("mail.smtp.writetimeout", "5000")
            put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3")
            put("mail.smtp.ssl.trust", smtpHost)
        }
        Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(smtpUser, smtpPassword)
        })
    }

    suspend fun sendPasswordResetOtp(to: String, otp: String) {
        withContext(Dispatchers.IO) {
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(smtpUser, fromName))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                subject = "PhoneGuard Password Reset Code"
                setText("""
                    Hello,
                    
                    Your PhoneGuard password reset code is:
                    
                    $otp
                    
                    This code expires in 15 minutes.
                """.trimIndent(), "utf-8")
            }
            Transport.send(message)
        }
    }

    suspend fun sendNewDeviceVerificationCode(to: String, code: String) {
        withContext(Dispatchers.IO) {
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(smtpUser, fromName))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                subject = "PhoneGuard New Device Verification Code"
                setText("""
                    Hello,
                    
                    A login attempt was detected from a new device.
                    Your PhoneGuard verification code is:
                    
                    $code
                    
                    This code expires in 15 minutes. If you did not attempt to log in, please secure your account.
                """.trimIndent(), "utf-8")
            }
            Transport.send(message)
        }
    }

    suspend fun sendSecurityAlert(
        to: String,
        deviceName: String,
        eventType: String,
        timestamp: Long,
        lat: Double? = null,
        lng: Double? = null
    ) {
        withContext(Dispatchers.IO) {
            val eventTitle = when (eventType) {
                "sim_change" -> "Unauthorized SIM Card Inserted"
                "snatch" -> "Motion / Snatch Theft Detected"
                "wrong_pin" -> "Failed PIN Entry Attempt"
                else -> "Security Alert ($eventType)"
            }

            val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(timestamp))
            val locationStr = if (lat != null && lng != null) "https://maps.google.com/?q=$lat,$lng" else "Location unavailable"

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(smtpUser, fromName))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                subject = "[PhoneGuard Alert] $eventTitle on $deviceName"
                setText("""
                    Hello,

                    PhoneGuard detected a critical security event on your registered device:

                    Device: $deviceName
                    Event Type: $eventTitle ($eventType)
                    Time: $dateStr
                    Location: $locationStr

                    If this was not you, please log in to your PhoneGuard account or track your device immediately.

                    Stay safe,
                    PhoneGuard Security Team
                """.trimIndent(), "utf-8")
            }
            try {
                Transport.send(message)
                println("[BACKEND EMAIL SENT] Successfully sent silent security email alert to $to for event '$eventType'")
            } catch (e: Exception) {
                println("[BACKEND EMAIL ERROR] Failed to send email to $to: ${e.message}")
            }
        }
    }
}