package mw.phoneguard.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import mw.phoneguard.app.data.storage.TokenStore

class BootReceiver : BroadcastReceiver() {

    private val TAG = "PhoneGuardBoot"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "BootReceiver received action: $action")
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        try {
            // Only start the service if the user has logged in and registered a device.
            // Otherwise the service would be a persistent notification for nothing.
            val tokenStore = TokenStore.get(context.applicationContext)
            if (!tokenStore.hasUserSession() || !tokenStore.hasDeviceRegistered()) {
                Log.i(TAG, "User not signed in / device not registered — skipping service start")
                return
            }

            PhoneGuardService.startOnBoot(context)
            Log.i(TAG, "PhoneGuardService start requested via $action")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start PhoneGuardService in BootReceiver for action $action: ${e.message}", e)
        }
    }
}