package mw.phoneguard.app.data.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mw.phoneguard.app.data.api.ApiResult
import mw.phoneguard.app.data.api.DeviceApi
import mw.phoneguard.app.data.local.PhoneGuardDatabase
import mw.phoneguard.app.data.storage.TokenStore

class SyncManager(context: Context) {

    private val db = PhoneGuardDatabase.get(context.applicationContext)
    private val tokenStore = TokenStore.get(context.applicationContext)
    private val deviceApi = DeviceApi()
    private val TAG = "PhoneGuardSync"

    /**
     * Reads all unsynced events from Room, POSTs them to the backend,
     * and marks them synced if successful.
     */
    suspend fun syncPendingEvents() = withContext(Dispatchers.IO) {
        val deviceId = tokenStore.getDeviceId()
        val deviceToken = tokenStore.getDeviceToken()

        if (deviceId == null || deviceToken == null) {
            Log.w(TAG, "Device not registered. Cannot sync events.")
            return@withContext
        }

        val unsynced = db.eventDao().getUnsynced()
        if (unsynced.isEmpty()) {
            Log.d(TAG, "No pending events to sync.")
            return@withContext
        }

        Log.d(TAG, "Attempting to sync ${unsynced.size} events...")

        for (event in unsynced) {
            when (val result = deviceApi.postEvent(deviceToken, deviceId, event)) {
                is ApiResult.Success -> {
                    val now = System.currentTimeMillis()
                    db.eventDao().markSynced(event.id, now)
                    Log.d(TAG, "✅ Synced event ${event.id} (${event.type})")
                }
                is ApiResult.Failure -> {
                    Log.e(TAG, "❌ Failed to sync event ${event.id}: ${result.message}")
                    // Break the loop on network failure so we don't hammer the server.
                    // WorkManager will retry the whole batch later.
                    break
                }
            }
        }
    }
}