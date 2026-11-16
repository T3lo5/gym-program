package com.treinos.app.data.wear

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.treinos.app.data.prefs.AppPreferences
import com.treinos.protocol.PATH_SESSION_SNAPSHOT
import com.treinos.protocol.PATH_WORKOUT_STATE
import com.treinos.protocol.WearCommand
import com.treinos.protocol.WearSessionSnapshot
import com.treinos.protocol.WearSyncBatch
import com.treinos.protocol.WearWorkoutState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
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

    /** H4-16: lotes de séries do relógio, já deduplicados por `clientSetId`. */
    private val _syncBatches = MutableSharedFlow<WearSyncBatch>(replay = 0, extraBufferCapacity = 50)

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

    /**
     * H4-16: publica o snapshot da sessão do dia (`PATH_SESSION_SNAPSHOT`) —
     * o relógio guarda no Room próprio e treina sem o celular. Falha sem
     * relógio pareado não quebra o app.
     */
    fun publishSnapshot(snapshot: WearSessionSnapshot) {
        scope.launch {
            val payload = Json.encodeToString(WearSessionSnapshot.serializer(), snapshot).toByteArray()
            val request = PutDataRequest.create(PATH_SESSION_SNAPSHOT).apply {
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

    /**
     * H4-16: lote de séries do relógio. Dedupe por `clientSetId` (mesmo padrão
     * do `CommandDedupe`): reenviar o lote não emite série repetida. Lote com
     * `protocolVersion != 1` é ignorado e o relógio mantém pendente.
     */
    fun onSyncBatchReceived(batch: WearSyncBatch) {
        if (batch.protocolVersion != 1) return
        val novas = batch.sets.filter { !preferences.isSyncedSet(it.clientSetId) }
        if (novas.isEmpty() && !batch.finished) return
        preferences.addSyncedSets(batch.sets.map { it.clientSetId })
        _syncBatches.tryEmit(batch.copy(sets = novas))
    }

    fun observeSyncBatches(): Flow<WearSyncBatch> = _syncBatches.asSharedFlow()

    fun close() {
        job.cancel()
    }
}
