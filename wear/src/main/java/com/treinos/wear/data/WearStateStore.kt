package com.treinos.wear.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import com.treinos.protocol.WearWorkoutState

class WearStateStore(
    context: Context,
) {

    private val prefs: SharedPreferences = context.getSharedPreferences("wear_state", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<WearWorkoutState?>(null)
    val state: StateFlow<WearWorkoutState?> = _state.asStateFlow()

    companion object {
        private const val KEY_STATE = "wear_last_state"
        private const val KEY_PENDING_COMMANDS = "wear_pending_commands"
    }

    fun saveState(state: WearWorkoutState) {
        prefs.edit().putString(KEY_STATE, Json.encodeToString(WearWorkoutState.serializer(), state)).apply()
        _state.value = state
    }

    fun loadState(): WearWorkoutState? {
        val json = prefs.getString(KEY_STATE, null) ?: return null
        return try {
            Json.decodeFromString(WearWorkoutState.serializer(), json).also { _state.value = it }
        } catch (e: Exception) {
            null
        }
    }

    fun clearState() {
        prefs.edit().remove(KEY_STATE).apply()
        _state.value = null
    }

    fun enqueueCommand(commandId: String) {
        val ids = (prefs.getStringSet(KEY_PENDING_COMMANDS, emptySet()) ?: emptySet()).toMutableSet()
        ids.add(commandId)
        if (ids.size > 20) ids.remove(ids.first())
        prefs.edit().putStringSet(KEY_PENDING_COMMANDS, ids).apply()
    }

    fun loadPendingCommands(): List<String> {
        return (prefs.getStringSet(KEY_PENDING_COMMANDS, emptySet()) ?: emptySet()).toList()
    }

    fun clearPendingCommands() {
        prefs.edit().remove(KEY_PENDING_COMMANDS).apply()
    }
}
