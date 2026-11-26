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
import mw.phoneguard.app.services.ServiceLogger

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
        var userToken = tokenStore.getUserAccessToken() ?: run {
            Log.w(TAG, "No user access token. Cannot sync contacts.")
            return@withContext
        }

        val unsynced = db.contactDao().getUnsynced()
        if (unsynced.isEmpty()) {
            val allContacts = db.contactDao().getAll()
            tokenStore.saveTrustedContacts(allContacts.map { it.name to it.phone })
            return@withContext
        }

        Log.d(TAG, "Attempting to sync ${unsynced.size} contacts...")

        val authApi = mw.phoneguard.app.data.api.AuthApi()

        for (contact in unsynced) {
            var result = contactApi.createContact(userToken, contact.name, contact.phone, contact.email)

            if (result.isFailure) {
                val errorMsg = result.exceptionOrNull()?.message ?: ""
                val isAuthError = errorMsg.contains("401") || errorMsg.contains("token", ignoreCase = true) || errorMsg.contains("Unauthorized", ignoreCase = true)
                if (isAuthError) {
                    val refreshToken = tokenStore.getUserRefreshToken()
                    if (refreshToken != null) {
                        Log.i(TAG, "User access token expired. Attempting token refresh...")
                        val refreshResult = authApi.refreshToken(refreshToken)
                        if (refreshResult is mw.phoneguard.app.data.api.ApiResult.Success) {
                            val newAccess = refreshResult.value.accessToken
                            val newRefresh = refreshResult.value.refreshToken
                            tokenStore.saveUserTokens(newAccess, newRefresh)
                            userToken = newAccess
                            Log.i(TAG, "Token refresh successful. Retrying contact sync for ${contact.name}...")
                            result = contactApi.createContact(userToken, contact.name, contact.phone, contact.email)
                        } else {
                            Log.w(TAG, "Token refresh failed: ${(refreshResult as? mw.phoneguard.app.data.api.ApiResult.Failure)?.message}")
                        }
                    }
                }
            }

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

        val allContacts = db.contactDao().getAll()
        tokenStore.saveTrustedContacts(allContacts.map { it.name to it.phone })
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

        val targetAlertTypes = setOf("sim_change", "snatch", "wrong_pin")
        val isSilentEmailEnabled = tokenStore.isSilenceEmailEnabled()

        for (event in unsynced) {
            val eventToSync = if (event.deviceId != currentDeviceId) event.copy(deviceId = currentDeviceId) else event
            val shouldSendSilentEmail = isSilentEmailEnabled && eventToSync.type in targetAlertTypes

            if (shouldSendSilentEmail) {
                ServiceLogger.log(TAG, "Silent email alert requested for event: ${eventToSync.type}")
            }

            when (val result = deviceApi.postEvent(currentDeviceToken, currentDeviceId, eventToSync, silentEmailAlert = if (shouldSendSilentEmail) true else null)) {
                is ApiResult.Success -> {
                    val now = System.currentTimeMillis()
                    db.eventDao().markSynced(eventToSync.id, now)
                    Log.d(TAG, "✅ Synced event ${eventToSync.id} (${eventToSync.type}) [silentEmail=$shouldSendSilentEmail]")
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
                            val retryResult = deviceApi.postEvent(currentDeviceToken, currentDeviceId, retryEvent, silentEmailAlert = if (shouldSendSilentEmail) true else null)
                            if (retryResult is ApiResult.Success) {
                                val now = System.currentTimeMillis()
                                db.eventDao().markSynced(retryEvent.id, now)
                                Log.d(TAG, "✅ Synced event ${retryEvent.id} (${retryEvent.type}) after re-registration [silentEmail=$shouldSendSilentEmail]")
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
            model = android.os.Build.MODEL,
            imei1 = tokenStore.getImei1(),
            imei2 = tokenStore.getImei2()
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