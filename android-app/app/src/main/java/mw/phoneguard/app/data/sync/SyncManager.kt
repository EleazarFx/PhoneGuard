package mw.phoneguard.app.data.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mw.phoneguard.app.data.api.ApiResult
import mw.phoneguard.app.data.api.ContactApi
import mw.phoneguard.app.data.api.DeviceApi
import mw.phoneguard.app.data.local.ContactEntity
import mw.phoneguard.app.data.local.PhoneGuardDatabase
import mw.phoneguard.app.data.storage.TokenStore

class SyncManager(context: Context) {

    private val db = PhoneGuardDatabase.get(context.applicationContext)
    private val tokenStore = TokenStore.get(context.applicationContext)
    private val deviceApi = DeviceApi(context.applicationContext)
    private val contactApi = ContactApi()
    private val TAG = "PhoneGuardSync"

    /**
     * Syncs all pending data (contacts and events).
     */
    suspend fun syncAll() = withContext(Dispatchers.IO) {
        syncPendingContacts()
        syncPendingEvents()
    }

    /**
     * Reads all unsynced contacts from Room (lastSyncedTimestamp == 0),
     * POSTs them to the backend, and marks them synced if successful.
     */
    suspend fun syncPendingContacts() = withContext(Dispatchers.IO) {
        val userToken = tokenStore.getUserAccessToken() ?: run {
            Log.w(TAG, "No user access token. Cannot sync contacts.")
            return@withContext
        }

        val unsynced = db.contactDao().getUnsynced()
        if (unsynced.isEmpty()) {
            return@withContext
        }

        Log.d(TAG, "Attempting to sync ${unsynced.size} contacts...")

        for (contact in unsynced) {
            val result = contactApi.createContact(userToken, contact.name, contact.phone, contact.email)
            if (result.isSuccess) {
                val dto = result.getOrNull()
                val now = System.currentTimeMillis()
                db.contactDao().deleteById(contact.id)
                if (dto != null) {
                    db.contactDao().insert(
                        ContactEntity(
                            id = dto.id,
                            name = dto.name,
                            phone = dto.phone,
                            email = dto.email,
                            createdAt = dto.createdAt,
                            lastSyncedTimestamp = now
                        )
                    )
                } else {
                    db.contactDao().insert(contact.copy(lastSyncedTimestamp = now))
                }
                Log.d(TAG, "✅ Synced contact ${contact.name}")
            } else {
                Log.e(TAG, "❌ Failed to sync contact ${contact.name}: ${result.exceptionOrNull()?.message}")
                break
            }
        }
    }

    /**
     * Reads all unsynced events from Room, POSTs them to the backend,
     * and marks them synced if successful. Also syncs pending contacts.
     */
    suspend fun syncPendingEvents() = withContext(Dispatchers.IO) {
        // Also trigger contact sync whenever event sync runs
        syncPendingContacts()

        val (initialDeviceId, initialDeviceToken) = run {
            val dId = tokenStore.getDeviceId()
            val dTok = tokenStore.getDeviceToken()
            if (dId != null && dTok != null) {
                Pair(dId, dTok)
            } else {
                reRegisterDeviceIfNeeded() ?: run {
                    Log.w(TAG, "Device not registered. Cannot sync events.")
                    return@withContext
                }
            }
        }

        var currentDeviceId = initialDeviceId
        var currentDeviceToken = initialDeviceToken

        val unsynced = db.eventDao().getUnsynced()
        if (unsynced.isEmpty()) {
            Log.d(TAG, "No pending events to sync.")
            return@withContext
        }

        Log.d(TAG, "Attempting to sync ${unsynced.size} events...")

        for (event in unsynced) {
            val eventToSync = if (event.deviceId != currentDeviceId) event.copy(deviceId = currentDeviceId) else event
            when (val result = deviceApi.postEvent(currentDeviceToken, currentDeviceId, eventToSync)) {
                is ApiResult.Success -> {
                    val now = System.currentTimeMillis()
                    db.eventDao().markSynced(eventToSync.id, now)
                    Log.d(TAG, "✅ Synced event ${eventToSync.id} (${eventToSync.type})")
                }
                is ApiResult.Failure -> {
                    Log.e(TAG, "❌ Failed to sync event ${eventToSync.id}: ${result.message}")
                    if (result.message.contains("Device not found", ignoreCase = true) ||
                        result.message.contains("not authorize", ignoreCase = true) ||
                        result.message.contains("404", ignoreCase = true)) {
                        val reReg = reRegisterDeviceIfNeeded()
                        if (reReg != null) {
                            currentDeviceId = reReg.first
                            currentDeviceToken = reReg.second
                            val retryEvent = eventToSync.copy(deviceId = currentDeviceId)
                            val retryResult = deviceApi.postEvent(currentDeviceToken, currentDeviceId, retryEvent)
                            if (retryResult is ApiResult.Success) {
                                val now = System.currentTimeMillis()
                                db.eventDao().markSynced(retryEvent.id, now)
                                Log.d(TAG, "✅ Synced event ${retryEvent.id} (${retryEvent.type}) after re-registration")
                                continue
                            }
                        }
                    }
                    break
                }
            }
        }
    }

    private suspend fun reRegisterDeviceIfNeeded(): Pair<String, String>? {
        val userToken = tokenStore.getUserAccessToken() ?: return null
        val deviceName = android.os.Build.MODEL ?: "Android Device"
        Log.i(TAG, "Attempting automatic device re-registration with backend...")
        val regResult = deviceApi.register(
            userAccessToken = userToken,
            deviceName = deviceName,
            model = android.os.Build.MODEL
        )
        if (regResult is ApiResult.Success) {
            val newId = regResult.value.deviceId
            val newToken = regResult.value.deviceToken
            tokenStore.saveDeviceIdentity(newId, newToken)
            db.eventDao().updateUnsyncedDeviceId(newId)
            Log.i(TAG, "Successfully re-registered device with ID $newId")
            return Pair(newId, newToken)
        }
        return null
    }
}