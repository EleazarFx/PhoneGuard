package mw.phoneguard.app.services

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import android.util.Log
import mw.phoneguard.app.data.storage.TokenStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsCommandReceiver : BroadcastReceiver() {
    private val TAG = "PhoneGuardSmsReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()
        Thread {
            try {
                processSmsIntent(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing incoming SMS: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun processSmsIntent(context: Context, intent: Intent) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val tokenStore = TokenStore.get(context)
        val trustedContacts = tokenStore.getTrustedContacts()

        for (sms in messages) {
            val sender = sms.originatingAddress ?: continue
            val body = sms.messageBody?.trim() ?: continue

            Log.d(TAG, "Incoming SMS from $sender: $body")

            val isTrusted = trustedContacts.any { contact ->
                phoneNumberMatches(contact.second, sender)
            }

            if (!isTrusted) {
                Log.d(TAG, "Ignoring SMS from untrusted sender: $sender")
                continue
            }

            if (body.startsWith("GUARD ", ignoreCase = true)) {
                val parts = body.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val command = parts[1].uppercase()
                    val providedCode = parts.getOrNull(2) ?: ""

                    // Check if sender is locked out due to 3 wrong codes in 10 minutes
                    if (tokenStore.isSmsSenderLockedOut(sender)) {
                        Log.w(TAG, "Ignoring SMS command from $sender: Sender is locked out for 30 minutes due to 3 wrong code attempts.")
                        continue
                    }

                    val expectedCode = tokenStore.getOwnerSmsCode()

                    if (providedCode.isBlank() || providedCode != expectedCode) {
                        val attempts = tokenStore.recordSmsCodeFailure(sender)
                        Log.w(TAG, "Invalid SMS command code from $sender (attempt $attempts/3)")
                        continue
                    }

                    // Authenticated code matches! Reset failure counter for this sender
                    tokenStore.resetSmsCodeFailures(sender)

                    when (command) {
                        "LOCATE" -> {
                            Log.i(TAG, "Valid authenticated GUARD LOCATE command received via SMS from $sender")
                            val deviceName = "${Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }} ${Build.MODEL}"
                            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                            val location = LocationHelper.getLastKnownLocation(context)
                            val mapsUrl = LocationHelper.formatMapsUrl(location)

                            val replyMessage = "[PhoneGuard] LOCATE: $deviceName. Armed: ${tokenStore.isAlertModeArmed()}. Time: $timeStr. Maps: $mapsUrl"
                            SmsSender.sendSingleSms(context, sender, replyMessage)
                        }
                        "LOCK" -> {
                            Log.i(TAG, "Valid authenticated GUARD LOCK command received via SMS from $sender")
                            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                            val adminComponent = ComponentName(context, PhoneGuardAdminReceiver::class.java)
                            if (dpm?.isAdminActive(adminComponent) == true) {
                                try {
                                    dpm.lockNow()
                                    SmsSender.sendSingleSms(context, sender, "[PhoneGuard] Device locked successfully.")
                                } catch (e: Exception) {
                                    SmsSender.sendSingleSms(context, sender, "[PhoneGuard] Failed to lock device: ${e.message}")
                                }
                            } else {
                                SmsSender.sendSingleSms(context, sender, "[PhoneGuard] Device admin not active; cannot lock via SMS.")
                            }
                        }
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun phoneNumberMatches(registered: String, incoming: String): Boolean {
        if (PhoneNumberUtils.compare(registered, incoming)) return true
        val cleanReg = registered.replace(Regex("[^0-9+]"), "")
        val cleanInc = incoming.replace(Regex("[^0-9+]"), "")
        return cleanReg == cleanInc || cleanInc.endsWith(cleanReg) || cleanReg.endsWith(cleanInc)
    }
}
