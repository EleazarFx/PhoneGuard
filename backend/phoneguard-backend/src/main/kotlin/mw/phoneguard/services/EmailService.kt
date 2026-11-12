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
            put("mail.smtp.host", smtpHost)
            put("mail.smtp.port", smtpPort)
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
}