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
            if (token == null) {
                loadLocalState(isOfflineMode = true)
                return@launch
            }

            val result = contactApi.listContacts(token)
            if (result.isSuccess) {
                val dtos = result.getOrDefault(emptyList())
                val now = System.currentTimeMillis()
                val entities = dtos.map { dto ->
                    ContactEntity(
                        id = dto.id,
                        name = dto.name,
                        phone = dto.phone,
                        email = dto.email,
                        createdAt = dto.createdAt,
                        lastSyncedTimestamp = now
                    )
                }
                contactDao.deleteAll()
                contactDao.insertAll(entities)
                tokenStore.saveTrustedContacts(entities.map { it.name to it.phone })
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isOffline = false,
                    lastSyncedText = formatTimestamp(now),
                    errorMessage = null
                )
            } else {
                Log.w("ContactsViewModel", "Failed to sync contacts from backend, using cache: ${result.exceptionOrNull()?.message}")
                loadLocalState(isOfflineMode = true, error = "Offline mode: Showing cached contacts")
            }
        }
    }

    private suspend fun loadLocalState(isOfflineMode: Boolean, error: String? = null) {
        val entities = contactDao.getAll()
        val lastSync = contactDao.getLastSyncedTime() ?: 0L
        val syncText = if (lastSync > 0L) formatTimestamp(lastSync) else "Never"
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
        if (name.isBlank() || phone.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Name and phone cannot be blank")
            return
        }
        if (_uiState.value.contacts.size >= 3) {
            _uiState.value = _uiState.value.copy(errorMessage = "Maximum 3 contacts allowed")
            return
        }

        viewModelScope.launch {
            val token = tokenStore.getUserAccessToken()
            if (token != null) {
                val result = contactApi.createContact(token, name.trim(), phone.trim())
                if (result.isSuccess) {
                    refresh()
                } else {
                    _uiState.value = _uiState.value.copy(errorMessage = result.exceptionOrNull()?.message ?: "Failed to add contact")
                }
            } else {
                val newId = java.util.UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val entity = ContactEntity(
                    id = newId,
                    name = name.trim(),
                    phone = phone.trim(),
                    createdAt = now.toString(),
                    lastSyncedTimestamp = now
                )
                contactDao.insert(entity)
                loadLocalState(isOfflineMode = true, error = "Added offline")
            }
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
