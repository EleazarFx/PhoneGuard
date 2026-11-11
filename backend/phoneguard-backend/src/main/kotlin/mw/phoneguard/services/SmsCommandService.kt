package mw.phoneguard.services

import mw.phoneguard.db.Devices
import mw.phoneguard.db.TrustedContacts
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object SmsCommandService {
    data class SmsCommandResult(
        val success: Boolean,
        val command: String?,
        val message: String,
        val targetDeviceId: String? = null
    )

    fun processIncomingSms(senderPhone: String, messageText: String, deviceService: DeviceService): SmsCommandResult {
        val cleanText = messageText.trim()
        val parts = cleanText.split("\\s+".toRegex())
        val rawCmd = if (parts.firstOrNull()?.equals("GUARD", ignoreCase = true) == true && parts.size >= 2) {
            parts[1].uppercase()
        } else {
            parts.firstOrNull()?.uppercase() ?: ""
        }

        if (rawCmd !in setOf("LOCK", "LOCATE")) {
            return SmsCommandResult(false, null, "Invalid or unsupported SMS command: $rawCmd")
        }

        return transaction {
            val cleanSender = senderPhone.replace(Regex("[^0-9+]"), "")

            val contacts = TrustedContacts.selectAll().toList()
            val matchedContact = contacts.firstOrNull { row ->
                val phone = row[TrustedContacts.phone].replace(Regex("[^0-9+]"), "")
                phone == cleanSender || cleanSender.endsWith(phone) || phone.endsWith(cleanSender)
            }

            if (matchedContact == null) {
                return@transaction SmsCommandResult(false, rawCmd, "Sender phone $senderPhone is not a registered trusted contact")
            }

            val userId = matchedContact[TrustedContacts.userId].value

            val devices = Devices.selectAll().where { Devices.userId eq userId }.toList()
            if (devices.isEmpty()) {
                return@transaction SmsCommandResult(false, rawCmd, "No registered devices found for user")
            }

            val targetDevice = devices.first()
            val deviceId = targetDevice[Devices.id].value.toString()

            val cmdSummary = deviceService.createCommand(
                userId = userId.toString(),
                deviceId = deviceId,
                command = rawCmd,
                payload = "Triggered via SMS command from $senderPhone",
                expiresInSeconds = 3600
            )

            if (cmdSummary != null) {
                SmsCommandResult(
                    success = true,
                    command = rawCmd,
                    message = "SMS command '$rawCmd' parsed and queued for device $deviceId",
                    targetDeviceId = deviceId
                )
            } else {
                SmsCommandResult(false, rawCmd, "Failed to queue command for device $deviceId")
            }
        }
    }
}
