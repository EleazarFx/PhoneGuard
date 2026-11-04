package mw.phoneguard.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mw.phoneguard.app.data.local.PhoneGuardDatabase
import mw.phoneguard.app.data.storage.TokenStore

data class HomeUiState(
    val userEmail: String? = null,
    val deviceRegistered: Boolean = false,
    val alertModeArmed: Boolean = false,
    val lastCheckInText: String = "Just now",
    val trustedContactsCount: Int = 0,
    val unsyncedEventCount: Int = 0
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenStore = TokenStore(application.applicationContext)
    private val database = PhoneGuardDatabase.get(application.applicationContext)

    private val _uiState = MutableStateFlow(
        HomeUiState(
            userEmail = tokenStore.getUserEmail(),
            deviceRegistered = tokenStore.hasDeviceRegistered(),
            alertModeArmed = false,
            trustedContactsCount = 0,
            unsyncedEventCount = 0
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Observe the pending-sync count from Room in real time
        viewModelScope.launch {
            database.eventDao().observeUnsyncedCount().collect { count ->
                _uiState.value = _uiState.value.copy(unsyncedEventCount = count)
            }
        }
    }

    fun toggleAlertMode(armed: Boolean) {
        _uiState.value = _uiState.value.copy(alertModeArmed = armed)
        // (Later: this will call the backend and save to a real Alert Mode schedule)
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(
            userEmail = tokenStore.getUserEmail(),
            deviceRegistered = tokenStore.hasDeviceRegistered()
        )
    }
}