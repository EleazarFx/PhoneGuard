package mw.phoneguard.app.ui.activity

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mw.phoneguard.app.data.local.EventEntity
import mw.phoneguard.app.data.local.PhoneGuardDatabase
import mw.phoneguard.app.data.storage.TokenStore

class ActivityViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = TokenStore.get(application.applicationContext)
    private val db = PhoneGuardDatabase.get(application.applicationContext)

    val events: StateFlow<List<EventEntity>> = db.eventDao()
        .observeAll(tokenStore.getDeviceId() ?: "")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun refresh() {
        viewModelScope.launch {
            try {
                val syncManager = mw.phoneguard.app.data.sync.SyncManager(getApplication())
                syncManager.syncPendingEvents()
            } catch (e: Exception) {
                android.util.Log.e("ActivityViewModel", "Failed to sync during refresh", e)
            }
        }
    }
}