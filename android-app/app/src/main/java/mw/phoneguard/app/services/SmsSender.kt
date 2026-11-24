package mw.phoneguard.app.services

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import mw.phoneguard.app.data.storage.TokenStore

object SmsSender {
    private const val TAG = "PhoneGuardSmsSender"

    fun sendAlertToTrustedContacts(context: Context, message: String, bypassRateLimit: Boolean = false): Boolean {
        val tokenStore = TokenStore.get(context)
        val contacts = tokenStore.getTrustedContacts()
        if (contacts.isEmpty()) {
            Log.w(TAG, "No trusted contacts configured for SMS alert")
            return false
        }

        var anySent = false
        for (contact in contacts) {
            val phone = contact.second
            if (phone.isNotBlank()) {
                if (!bypassRateLimit && !tokenStore.canSendSmsAlertToContact(phone)) {
                    Log.w(TAG, "Skipping SMS alert to ${contact.first} ($phone): Rate limited (1 alert per 10 mins).")
                    continue
                }

                val success = sendSingleSms(context, phone, message)
                if (success) {
                    anySent = true
                    if (!bypassRateLimit) {
                        tokenStore.recordSmsAlertSent(phone)
                    }
                }
            }
        }
        return anySent
    }

    fun sendSingleSms(context: Context, destPhone: String, message: String, targetSubId: Int? = null): Boolean {
        if (destPhone.isBlank()) return false

        val activeSubIds = getActiveSubscriptionIds(context)
        val subIdsToTry = if (targetSubId != null && targetSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            listOf(targetSubId)
        } else if (activeSubIds.isNotEmpty()) {
            activeSubIds
        } else {
            listOf(null)
        }

        var anySuccess = false
        for (subId in subIdsToTry) {
            try {
                val smsManager = if (subId != null && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.getSystemService(SmsManager::class.java)?.createForSubscriptionId(subId)
                            ?: @Suppress("DEPRECATION") SmsManager.getSmsManagerForSubscriptionId(subId)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getSmsManagerForSubscriptionId(subId)
                    }
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.getSystemService(SmsManager::class.java) ?: @Suppress("DEPRECATION") SmsManager.getDefault()
                    } else {
                        @Suppress("DEPRECATION") SmsManager.getDefault()
                    }
                }

                if (smsManager == null) {
                    Log.e(TAG, "SmsManager unavailable for subId $subId")
                    continue
                }

                val parts = smsManager.divideMessage(message)
                if (parts != null && parts.size > 1) {
                    smsManager.sendMultipartTextMessage(destPhone, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(destPhone, null, message, null, null)
                }
                Log.i(TAG, "SMS sent to $destPhone (subId=${subId ?: "default"}): $message")
                anySuccess = true
                break
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SMS to $destPhone on subId $subId: ${e.message}", e)
            }
        }

        return anySuccess
    }

    private fun getActiveSubscriptionIds(context: Context): List<Int> {
        val list = mutableListOf<Int>()
        try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            if (sm != null) {
                @Suppress("MissingPermission")
                val activeList = sm.activeSubscriptionInfoList
                if (!activeList.isNullOrEmpty()) {
                    for (sub in activeList) {
                        if (sub.subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                            list.add(sub.subscriptionId)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query active subscription IDs for SMS dispatch: ${e.message}")
        }
        return list
    }
}
