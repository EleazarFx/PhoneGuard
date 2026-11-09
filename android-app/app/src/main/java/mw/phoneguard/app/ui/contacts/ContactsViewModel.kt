package mw.phoneguard.app.ui.contacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mw.phoneguard.app.data.storage.TokenStore

data class ContactsUiState(
    val contacts: List<Pair<String, String>> = emptyList(),
    val errorMessage: String? = null
)

class ContactsViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = TokenStore.get(application)

    private val _uiState = MutableStateFlow(
        ContactsUiState(contacts = tokenStore.getTrustedContacts())
    )
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    fun addContact(name: String, phone: String) {
        if (name.isBlank() || phone.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Name and phone cannot be blank")
            return
        }
        val current = _uiState.value.contacts.toMutableList()
        if (current.size >= 3) {
            _uiState.value = _uiState.value.copy(errorMessage = "Maximum 3 trusted contacts allowed")
            return
        }
        current.add(name.trim() to phone.trim())
        tokenStore.saveTrustedContacts(current)
        _uiState.value = ContactsUiState(contacts = current, errorMessage = null)
    }

    fun removeContact(index: Int) {
        val current = _uiState.value.contacts.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            tokenStore.saveTrustedContacts(current)
            _uiState.value = ContactsUiState(contacts = current, errorMessage = null)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
