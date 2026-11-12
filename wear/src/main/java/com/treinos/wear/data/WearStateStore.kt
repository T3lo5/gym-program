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
        private const val KEY_PENDING_COMMANDS = "wear_pending_commands_v2"
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

    fun savePendingCommands(queue: WearCommandQueue) {
        prefs.edit().putString(KEY_PENDING_COMMANDS, queue.encode()).apply()
    }

    fun loadPendingCommands(): WearCommandQueue {
        return WearCommandQueue.decode(prefs.getString(KEY_PENDING_COMMANDS, null))
    }
}
