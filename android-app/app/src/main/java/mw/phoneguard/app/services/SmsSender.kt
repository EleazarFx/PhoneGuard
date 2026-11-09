package mw.phoneguard.app.services

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import mw.phoneguard.app.data.storage.TokenStore

object SmsSender {
    private const val TAG = "PhoneGuardSmsSender"

    fun sendAlertToTrustedContacts(context: Context, message: String) {
        val tokenStore = TokenStore.get(context)
        val contacts = tokenStore.getTrustedContacts()
        if (contacts.isEmpty()) {
            Log.w(TAG, "No trusted contacts configured for SMS alert")
            return
        }

        val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        if (smsManager == null) {
            Log.e(TAG, "SmsManager unavailable")
            return
        }

        for (contact in contacts) {
            val phone = contact.second
            if (phone.isNotBlank()) {
                try {
                    smsManager.sendTextMessage(phone, null, message, null, null)
                    Log.i(TAG, "Independent SMS sent to ${contact.first} ($phone): $message")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send SMS to $phone: ${e.message}", e)
                }
            }
        }
    }
}
