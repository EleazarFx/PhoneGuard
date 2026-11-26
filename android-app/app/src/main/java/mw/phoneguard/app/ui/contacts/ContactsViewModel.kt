package mw.phoneguard.app.ui.contacts

import android.app.Application
import android.text.format.DateFormat
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mw.phoneguard.app.data.api.ContactApi
import mw.phoneguard.app.data.local.ContactEntity
import mw.phoneguard.app.data.local.PhoneGuardDatabase
import mw.phoneguard.app.data.storage.TokenStore
import mw.phoneguard.app.data.sync.SyncManager
import java.util.Date

data class ContactsUiState(
    val contacts: List<ContactEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isOffline: Boolean = false,
    val lastSyncedText: String = "Never",
    val errorMessage: String? = null
)

class ContactsViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = TokenStore.get(application)
    private val database = PhoneGuardDatabase.get(application)
    private val contactDao = database.contactDao()
    private val contactApi = ContactApi()
    private val syncManager = SyncManager(application)

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            contactDao.observeAll().collect { entities ->
                _uiState.value = _uiState.value.copy(contacts = entities)
                tokenStore.saveTrustedContacts(entities.map { it.name to it.phone })
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val token = tokenStore.getUserAccessToken()

            // Try syncing any pending offline contacts first
            if (token != null) {
                try {
                    syncManager.syncPendingContacts()
                } catch (e: Exception) {
                    Log.w("ContactsViewModel", "Failed to sync pending contacts: ${e.message}")
                }
            }

            if (token == null) {
                loadLocalState(isOfflineMode = true)
                return@launch
            }

            var activeToken = token
            var result = contactApi.listContacts(activeToken)

            if (result.isFailure) {
                val err = result.exceptionOrNull()?.message ?: ""
                val isAuthError = err.contains("401") || err.contains("Unauthorized", ignoreCase = true) || err.contains("expired", ignoreCase = true)
                if (isAuthError) {
                    val rToken = tokenStore.getUserRefreshToken()
                    if (rToken != null) {
                        val refreshRes = mw.phoneguard.app.data.api.AuthApi().refreshToken(rToken)
                        if (refreshRes is mw.phoneguard.app.data.api.ApiResult.Success) {
                            tokenStore.saveUserTokens(refreshRes.value.accessToken, refreshRes.value.refreshToken)
                            activeToken = refreshRes.value.accessToken
                            result = contactApi.listContacts(activeToken)
                        }
                    }
                }
            }

            if (result.isSuccess) {
                val dtos = result.getOrDefault(emptyList())
                val now = System.currentTimeMillis()
                val remoteEntities = dtos.map { dto ->
                    ContactEntity(
                        id = dto.id,
                        name = dto.name,
                        phone = dto.phone,
                        email = dto.email,
                        createdAt = dto.createdAt,
                        lastSyncedTimestamp = now
                    )
                }

                if (remoteEntities.isNotEmpty()) {
                    contactDao.insertAll(remoteEntities)
                    val remoteIds = remoteEntities.map { it.id }
                    contactDao.deleteSyncedNotIn(remoteIds)
                } else {
                    contactDao.deleteSyncedAll()
                }

                val updatedEntities = contactDao.getAll()
                tokenStore.saveTrustedContacts(updatedEntities.map { it.name to it.phone })
                val lastSync = contactDao.getLastSyncedTime() ?: now
                _uiState.value = _uiState.value.copy(
                    contacts = updatedEntities,
                    isLoading = false,
                    isOffline = false,
                    lastSyncedText = formatTimestamp(lastSync),
                    errorMessage = null
                )
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: ""
                val isAuthError = errorMsg.contains("401") || errorMsg.contains("Unauthorized", ignoreCase = true) || errorMsg.contains("expired", ignoreCase = true)
                val userFacingError = if (isAuthError) {
                    "Session expired. Log in again to sync."
                } else {
                    "Offline mode: Showing cached contacts"
                }
                Log.w("ContactsViewModel", "Failed to sync contacts from backend, using cache: $errorMsg")
                loadLocalState(isOfflineMode = true, error = userFacingError)
            }
        }
    }

    private suspend fun loadLocalState(isOfflineMode: Boolean, error: String? = null) {
        var entities = contactDao.getAll()

        // Fallback restoration: if Room DB is empty but TokenStore has cached trusted contacts, restore them into Room
        if (entities.isEmpty()) {
            val cachedContacts = tokenStore.getTrustedContacts()
            if (cachedContacts.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val restored = cachedContacts.map { (name, phone) ->
                    ContactEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        name = name,
                        phone = phone,
                        createdAt = now.toString(),
                        lastSyncedTimestamp = 0L
                    )
                }
                contactDao.insertAll(restored)
                entities = contactDao.getAll()
            }
        }

        val hasUnsynced = entities.any { it.lastSyncedTimestamp == 0L }
        val lastSync = contactDao.getLastSyncedTime() ?: 0L
        val syncText = when {
            hasUnsynced -> "Pending sync"
            lastSync > 0L -> formatTimestamp(lastSync)
            else -> "Never"
        }
        tokenStore.saveTrustedContacts(entities.map { it.name to it.phone })
        _uiState.value = _uiState.value.copy(
            contacts = entities,
            isLoading = false,
            isOffline = isOfflineMode,
            lastSyncedText = syncText,
            errorMessage = error
        )
    }

    fun addContact(name: String, phone: String) {
        val trimmedName = name.trim()
        val trimmedPhone = phone.trim()
        if (trimmedName.isBlank() || trimmedPhone.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Name and phone cannot be blank")
            return
        }
        if (_uiState.value.contacts.size >= 3) {
            _uiState.value = _uiState.value.copy(errorMessage = "Maximum 3 contacts allowed")
            return
        }

        viewModelScope.launch {
            val tempId = java.util.UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val token = tokenStore.getUserAccessToken()

            if (token != null) {
                val result = contactApi.createContact(token, trimmedName, trimmedPhone)
                if (result.isSuccess) {
                    val dto = result.getOrNull()
                    val syncedEntity = ContactEntity(
                        id = dto?.id ?: tempId,
                        name = dto?.name ?: trimmedName,
                        phone = dto?.phone ?: trimmedPhone,
                        email = dto?.email,
                        createdAt = dto?.createdAt ?: now.toString(),
                        lastSyncedTimestamp = now
                    )
                    contactDao.insert(syncedEntity)
                    tokenStore.saveTrustedContacts(contactDao.getAll().map { it.name to it.phone })
                    refresh()
                    return@launch
                }
            }

            // If token is null or creation API failed, save locally with lastSyncedTimestamp = 0L (pending sync)
            val entity = ContactEntity(
                id = tempId,
                name = trimmedName,
                phone = trimmedPhone,
                createdAt = now.toString(),
                lastSyncedTimestamp = 0L
            )
            contactDao.insert(entity)
            loadLocalState(isOfflineMode = true, error = "Saved locally (pending sync)")
        }
    }

    fun removeContact(contactId: String) {
        viewModelScope.launch {
            val token = tokenStore.getUserAccessToken()
            if (token != null) {
                val result = contactApi.deleteContact(token, contactId)
                if (result.isSuccess) {
                    contactDao.deleteById(contactId)
                    refresh()
                } else {
                    contactDao.deleteById(contactId)
                    refresh()
                }
            } else {
                contactDao.deleteById(contactId)
                loadLocalState(isOfflineMode = true)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun formatTimestamp(time: Long): String {
        return try {
            val date = Date(time)
            DateFormat.format("hh:mm a, MMM dd", date).toString()
        } catch (_: Exception) {
            "Just now"
        }
    }
}
