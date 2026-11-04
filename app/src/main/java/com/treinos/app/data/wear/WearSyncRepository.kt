package com.treinos.app.data.wear

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.treinos.app.data.prefs.AppPreferences
import com.treinos.protocol.PATH_WORKOUT_STATE
import com.treinos.protocol.WearCommand
import com.treinos.protocol.WearWorkoutState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class WearSyncRepository(
    private val context: Context,
    private val preferences: AppPreferences,
) {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)

    private val _commands = MutableSharedFlow<WearCommand>(replay = 0, extraBufferCapacity = 50)
    val commands: kotlinx.coroutines.flow.Flow<WearCommand> = _commands.asSharedFlow()

    fun publish(state: WearWorkoutState) {
        scope.launch {
            val payload = Json.encodeToString(WearWorkoutState.serializer(), state).toByteArray()
            val request = PutDataRequest.create(PATH_WORKOUT_STATE).apply {
                setData(payload)
            }
            try {
                Tasks.await(Wearable.getDataClient(context).putDataItem(request))
            } catch (e: Exception) {
                // Sem relógio pareado: não quebra o app.
            }
        }
    }

    fun onCommandReceived(command: WearCommand) {
        if (command.commandId.isBlank()) return
        if (preferences.isRecentCommand(command.commandId)) return
        preferences.addRecentCommand(command.commandId)
        _commands.tryEmit(command)
    }

    fun close() {
        job.cancel()
    }
}
