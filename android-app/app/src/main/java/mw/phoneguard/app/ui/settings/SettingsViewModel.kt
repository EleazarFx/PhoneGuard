package mw.phoneguard.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mw.phoneguard.app.data.storage.TokenStore

data class SettingsUiState(
    val videoEvidenceEnabled: Boolean = false,
    val periodicLocationEnabled: Boolean = true,
    val offlineAutoLockEnabled: Boolean = false,
    val remoteWipeEnabled: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = TokenStore.get(application)

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            videoEvidenceEnabled = tokenStore.isVideoEvidenceEnabled(),
            periodicLocationEnabled = tokenStore.isPeriodicLocationEnabled(),
            offlineAutoLockEnabled = tokenStore.isOfflineAutoLockEnabled(),
            remoteWipeEnabled = tokenStore.isRemoteWipeEnabled()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun toggleVideoEvidence(enabled: Boolean) {
        tokenStore.saveVideoEvidenceEnabled(enabled)
        _uiState.value = _uiState.value.copy(videoEvidenceEnabled = enabled)
    }

    fun togglePeriodicLocation(enabled: Boolean) {
        tokenStore.savePeriodicLocationEnabled(enabled)
        _uiState.value = _uiState.value.copy(periodicLocationEnabled = enabled)
    }

    fun toggleOfflineAutoLock(enabled: Boolean) {
        tokenStore.saveOfflineAutoLockEnabled(enabled)
        _uiState.value = _uiState.value.copy(offlineAutoLockEnabled = enabled)
    }

    fun toggleRemoteWipe(enabled: Boolean) {
        tokenStore.saveRemoteWipeEnabled(enabled)
        _uiState.value = _uiState.value.copy(remoteWipeEnabled = enabled)
    }
}
