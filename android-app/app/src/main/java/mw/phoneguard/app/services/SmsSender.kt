package mw.phoneguard.app.services

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import mw.phoneguard.app.data.storage.TokenStore

object SmsSender {
    private const val TAG = "PhoneGuardSmsSender"

    fun sendAlertToTrustedContacts(context: Context, message: String, bypassRateLimit: Boolean = false) {
        val tokenStore = TokenStore.get(context)
        val contacts = tokenStore.getTrustedContacts()
        if (contacts.isEmpty()) {
            Log.w(TAG, "No trusted contacts configured for SMS alert")
            return
        }

        for (contact in contacts) {
            val phone = contact.second
            if (phone.isNotBlank()) {
                if (!bypassRateLimit && !tokenStore.canSendSmsAlertToContact(phone)) {
                    Log.w(TAG, "Skipping SMS alert to ${contact.first} ($phone): Rate limited (1 alert per 10 mins).")
                    continue
                }

                val success = sendSingleSms(context, phone, message)
                if (success && !bypassRateLimit) {
                    tokenStore.recordSmsAlertSent(phone)
                }
            }
        }
    }

    fun sendSingleSms(context: Context, destPhone: String, message: String): Boolean {
        if (destPhone.isBlank()) return false
        return try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            if (smsManager == null) {
                Log.e(TAG, "SmsManager unavailable")
                return false
            }

            smsManager.sendTextMessage(destPhone, null, message, null, null)
            Log.i(TAG, "SMS sent to $destPhone: $message")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $destPhone: ${e.message}", e)
            false
        }
    }
}

