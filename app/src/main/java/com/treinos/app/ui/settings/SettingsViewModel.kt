package com.treinos.app.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.rx2.RxPreferenceDataStoreBuilder
import androidx.datastore.preferences.rx2.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.treinos.shared.util.BackendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val backendRepository: BackendRepository
) : ViewModel() {

    private const val KEY_BACKEND_URL = "backend_base_url"
    private const val DEFAULT_BACKEND_URL = "http://10.0.2.2:3000/"

    private val dataStore = context.preferencesDataStore("treinos_settings")

    // Backend connection
    private var _backendUrl by mutableStateOf(DEFAULT_BACKEND_URL)
    val backendUrl: String
        get() = _backendUrl
        set(value) { _backendUrl = value }

    private var _backendStatus by mutableStateOf<BackendStatus>(BackendStatus.Unknown)
    val backendStatus: BackendStatus
        get() = _backendStatus

    enum class BackendStatus {
        Unknown, Connecting, Connected, Error
    }

    init {
        loadSavedBackendUrl()
    }

    private fun loadSavedBackendUrl() {
        viewModelScope.launch {
            val saved = dataStore.data
                .map { prefs -> prefs.getString("backend_base_url") ?: DEFAULT_BACKEND_URL }
                .first()
            _backendUrl = saved
        }
    }

    suspend fun saveBackendUrl(url: String) {
        _backendUrl = url
        dataStore.edit { prefs ->
            prefs.putString("backend_base_url", url)
        }.await()
    }

    fun testBackendConnection() {
        viewModelScope.launch {
            _backendStatus = BackendStatus.Connecting
            try {
                val health = backendRepository.healthCheck()
                _backendStatus = if (health.status == "healthy") BackendStatus.Connected else BackendStatus.Error
            } catch (e: Exception) {
                _backendStatus = BackendStatus.Error
            }
        }
    }

    // AI Generation preferences
    private var _availableEquipment by mutableStateOf(listOf("body only", "dumbbell", "barbell"))
    val availableEquipment: List<String>
        get() = _availableEquipment
        set(value) { _availableEquipment = value }

    private var _targetMuscles by mutableStateOf(listOf("chest", "back", "legs", "shoulders", "arms"))
    val targetMuscles: List<String>
        get() = _targetMuscles
        set(value) { _targetMuscles = value }

    private var _currentLevel by mutableStateOf("intermediate")
    val currentLevel: String
        get() = _currentLevel
        set(value) { _currentLevel = value }

    private var _sessionDurationMinutes by mutableStateOf(60)
    val sessionDurationMinutes: Int
        get() = _sessionDurationMinutes
        set(value) { _sessionDurationMinutes = value.coerceIn(20, 180) }
}