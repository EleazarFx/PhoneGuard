package mw.phoneguard.app

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mw.phoneguard.app.data.local.PhoneGuardDatabase
import mw.phoneguard.app.data.storage.TokenStore
import timber.log.Timber

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
        mw.phoneguard.app.services.CrashHandler.init(this)
        if (BuildConfig.DEBUG) {
            Timber.plant(object : Timber.DebugTree() {
                override fun createStackElementTag(element: StackTraceElement): String {
                    return "PhoneGuardService · [${element.className.substringAfterLast('.')}]"
                }
            })
        } else {
            Timber.plant(PhoneGuardReleaseTree())
        }
        Timber.d("Application.onCreate starting warmup")

        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(mw.phoneguard.app.services.AppLifecycleObserver())

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

            Timber.d(
                "Warmup complete in ${System.currentTimeMillis() - start}ms"
            )
        }
    }
}

class PhoneGuardReleaseTree : Timber.DebugTree() {
    override fun createStackElementTag(element: StackTraceElement): String {
        return "PhoneGuardService · [${element.className.substringAfterLast('.')}]"
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < Log.INFO) return // RELEASE hides DEBUG and VERBOSE, shows INFO+ only
        val redacted = redact(message)
        super.log(priority, tag ?: "PhoneGuardService", redacted, t)
    }

    private fun redact(msg: String): String {
        var s = msg
        val regexes = listOf(
            Regex("(access_token|refresh_token|device_token|token|password|otp|code)=[^\\s&]+", RegexOption.IGNORE_CASE),
            Regex("(Bearer\\s+[A-Za-z0-9\\-_\\.]+)", RegexOption.IGNORE_CASE),
            Regex("(/data/user/\\d+/[^\\s]+\\.(jpg|png|jpeg))", RegexOption.IGNORE_CASE)
        )
        for (r in regexes) {
            s = r.replace(s) { match ->
                val k = match.groupValues.getOrNull(1) ?: "sensitive"
                "$k=[REDACTED]"
            }
        }
        return s
    }
}
