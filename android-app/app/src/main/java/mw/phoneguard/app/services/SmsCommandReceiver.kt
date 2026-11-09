package mw.phoneguard.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import mw.phoneguard.app.data.storage.TokenStore

class SmsCommandReceiver : BroadcastReceiver() {
    private val TAG = "PhoneGuardSmsReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val sender = sms.originatingAddress ?: continue
                val body = sms.messageBody?.trim() ?: continue

                Log.d(TAG, "Incoming SMS from $sender: $body")

                val tokenStore = TokenStore.get(context)
                val trustedContacts = tokenStore.getTrustedContacts()

                val isTrusted = trustedContacts.any { contact ->
                    phoneNumberMatches(contact.second, sender)
                }

                if (!isTrusted) {
                    Log.d(TAG, "Ignoring SMS from untrusted sender: $sender")
                    continue
                }

                if (body.startsWith("GUARD ", ignoreCase = true)) {
                    val parts = body.split(" ")
                    if (parts.size >= 2) {
                        val command = parts[1].uppercase()
                        when (command) {
                            "LOCATE" -> {
                                Log.i(TAG, "Valid GUARD LOCATE command received via SMS from $sender")
                                val replyMessage = "[PhoneGuard] Device status: Protected. Alert armed: ${tokenStore.isAlertModeArmed()}."
                                sendSms(context, sender, replyMessage)
                            }
                            "LOCK" -> {
                                Log.i(TAG, "Valid GUARD LOCK command received via SMS from $sender")
                                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
                                val adminComponent = android.content.ComponentName(context, PhoneGuardAdminReceiver::class.java)
                                if (dpm?.isAdminActive(adminComponent) == true) {
                                    try {
                                        dpm.lockNow()
                                        sendSms(context, sender, "[PhoneGuard] Device locked successfully.")
                                    } catch (e: Exception) {
                                        sendSms(context, sender, "[PhoneGuard] Failed to lock device: ${e.message}")
                                    }
                                } else {
                                    sendSms(context, sender, "[PhoneGuard] Device admin not active; cannot lock via SMS.")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing incoming SMS: ${e.message}", e)
        }
    }

    private fun phoneNumberMatches(registered: String, incoming: String): Boolean {
        val cleanReg = registered.replace(Regex("[^0-9+]"), "")
        val cleanInc = incoming.replace(Regex("[^0-9+]"), "")
        return cleanReg == cleanInc || cleanInc.endsWith(cleanReg) || cleanReg.endsWith(cleanInc)
    }

    private fun sendSms(context: Context, dest: String, message: String) {
        try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager?.sendTextMessage(dest, null, message, null, null)
            Log.i(TAG, "Sent SMS reply to $dest")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS reply: ${e.message}")
        }
    }
}
