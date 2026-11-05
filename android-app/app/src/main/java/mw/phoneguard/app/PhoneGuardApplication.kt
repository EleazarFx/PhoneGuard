package mw.phoneguard.app

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mw.phoneguard.app.data.local.PhoneGuardDatabase
import mw.phoneguard.app.data.storage.TokenStore

/**
 * Application class runs once when the process starts.
 * We use it to do slow initialization on a background thread
 * so the UI thread stays responsive.
 */
class PhoneGuardApplication : Application() {

    // A coroutine scope that lives as long as the process does.
    // SupervisorJob() means one failed coroutine doesn't cancel the others.
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.d("PhoneGuardApp", "Application.onCreate starting warmup")

        // Pre-warm the heavy initializers on a background thread
        appScope.launch {
            val start = System.currentTimeMillis()

            // Open the database (builds schema if needed) on background thread
            PhoneGuardDatabase.get(applicationContext)

            // Open encrypted prefs (Keystore access) on background thread
            TokenStore.get(applicationContext).let { store ->
                // Touch a few fields to force Keystore initialization
                store.hasUserSession()
                store.getUserEmail()
                store.getDeviceId()
            }

            Log.d(
                "PhoneGuardApp",
                "Warmup complete in ${System.currentTimeMillis() - start}ms"
            )
        }
    }
}