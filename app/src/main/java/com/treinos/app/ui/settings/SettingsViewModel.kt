package com.treinos.app.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.treinos.shared.util.BackendDiscovery
import com.treinos.shared.util.BackendRepository
import com.treinos.shared.util.treinosDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backendRepository: BackendRepository
) : ViewModel() {

    private val backendUrlKey = stringPreferencesKey(BackendDiscovery.DATASTORE_KEY)

    // Backend connection
    var backendUrl by mutableStateOf(DEFAULT_BACKEND_URL)
        private set

    var backendStatus by mutableStateOf(BackendStatus.Unknown)
        private set

    enum class BackendStatus {
        Unknown, Connecting, Connected, Error
    }

    init {
        loadSavedBackendUrl()
    }

    private fun loadSavedBackendUrl() {
        viewModelScope.launch {
            val saved = context.treinosDataStore.data
                .map { prefs -> prefs[backendUrlKey] ?: DEFAULT_BACKEND_URL }
                .first()
            backendUrl = saved
        }
    }

    fun updateBackendUrl(url: String) {
        backendUrl = url
    }

    fun saveBackendUrl(url: String) {
        backendUrl = url
        viewModelScope.launch {
            context.treinosDataStore.edit { prefs ->
                prefs[backendUrlKey] = url
            }
        }
    }

    fun testBackendConnection() {
        viewModelScope.launch {
            backendStatus = BackendStatus.Connecting
            try {
                val health = backendRepository.healthCheck()
                backendStatus = if (health.status == "healthy") BackendStatus.Connected else BackendStatus.Error
            } catch (e: Exception) {
                backendStatus = BackendStatus.Error
            }
        }
    }

    // AI Generation preferences
    var availableEquipment by mutableStateOf(listOf("body only", "dumbbell", "barbell"))
    var targetMuscles by mutableStateOf(listOf("chest", "back", "legs", "shoulders", "arms"))
    var currentLevel by mutableStateOf("intermediate")

    private var _sessionDurationMinutes by mutableStateOf(60)
    var sessionDurationMinutes: Int
        get() = _sessionDurationMinutes
        set(value) { _sessionDurationMinutes = value.coerceIn(20, 180) }

    private companion object {
        const val DEFAULT_BACKEND_URL = "http://10.0.2.2:3000/"
    }
}
