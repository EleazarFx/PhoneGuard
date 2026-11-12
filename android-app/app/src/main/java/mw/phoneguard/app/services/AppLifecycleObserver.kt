package mw.phoneguard.app.services

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class AppLifecycleObserver : DefaultLifecycleObserver {
    companion object {
        private const val TAG = "AppLifecycleObserver"
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Log.i(TAG, "App moved to foreground (ON_START). Checking session expiry...")
        AppSessionManager.checkExpiry()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        Log.i(TAG, "App moved to background (ON_STOP). Recording leave time...")
        AppSessionManager.touch()
    }
}
