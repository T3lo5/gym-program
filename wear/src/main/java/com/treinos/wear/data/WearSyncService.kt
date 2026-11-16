package com.treinos.wear.data

import android.app.Service
import android.content.Intent
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.treinos.protocol.PATH_COMMAND
import com.treinos.protocol.PATH_SESSION_SNAPSHOT
import com.treinos.protocol.PATH_SYNC_BATCH
import com.treinos.protocol.WearCommand
import com.treinos.protocol.WearCommandKind
import com.treinos.protocol.WearSessionSnapshot
import com.treinos.protocol.WearSyncBatch
import com.treinos.protocol.WearWorkoutState
import com.treinos.wear.tile.WorkoutTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class WearSyncService : WearableListenerService(), DataClient.OnDataChangedListener {

    private var stateStore: WearStateStore? = null

    companion object {
        @Volatile private var store: WearStateStore? = null

        @Volatile private var standaloneStore: WearStandaloneStore? = null

        fun setStateStore(store: WearStateStore) {
            this.store = store
        }

        /** H4-16: Room do relógio para o modo standalone. */
        fun setStandaloneStore(store: WearStandaloneStore) {
            this.standaloneStore = store
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        stateStore = store
        Wearable.getDataClient(this).addListener(this)
    }

    override fun onDestroy() {
        Wearable.getDataClient(this).removeListener(this)
        super.onDestroy()
    }

    override fun onPeerConnected(peer: com.google.android.gms.wearable.Node) {
        super.onPeerConnected(peer)
        resendPendingCommands()
        sendRequestState()
        // H4-16: além da fila da H3-20, envia o lote de séries registradas
        // offline no Room do relógio (PATH_SYNC_BATCH).
        sendPendingSets()
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val uri = event.dataItem.uri
            when (uri.path) {
                "/treinos/workout-state" -> processarEstado(event.dataItem.data)
                // H4-16: snapshot da sessão do dia — o relógio guarda no Room
                // próprio e consegue treinar sem o celular.
                PATH_SESSION_SNAPSHOT -> processarSnapshot(event.dataItem.data)
            }
        }
    }

    private fun processarEstado(bytes: ByteArray?) {
        if (bytes == null) return
        val estado = try {
            Json { ignoreUnknownKeys = true }.decodeFromString(
                WearWorkoutState.serializer(),
                String(bytes)
            )
        } catch (e: Exception) {
            Log.w("WearSyncService", "Falha ao decodificar estado", e)
            return
        }
        if (estado.protocolVersion != 1) {
            Log.w("WearSyncService", "Versão de protocolo incompatível: ${estado.protocolVersion}")
            return
        }
        stateStore?.saveState(estado)
        // H4-14: estado novo do celular — o tile do pulso precisa re-render.
        runCatching { WorkoutTileService.requestUpdate(this) }
        // H4-16: sessão finalizada (ou encerrada) no celular quando o lote
        // chegou → o relógio limpa as pendentes daquela sessão (sem duplicar
        // histórico); o celular reenvia o estado atual ao rejeitar o lote.
        if (estado.status != "in_progress") {
            serviceScope.launch {
                val ativa = standaloneStore?.activeSession()
                if (ativa != null && ativa.id == estado.sessionId) {
                    standaloneStore?.clearPendingOfSession(ativa.id)
                }
            }
        }
    }

    private fun processarSnapshot(bytes: ByteArray?) {
        val room = standaloneStore ?: return
        if (bytes == null) return
        val snapshot = try {
            Json { ignoreUnknownKeys = true }.decodeFromString(
                WearSessionSnapshot.serializer(),
                String(bytes)
            )
        } catch (e: Exception) {
            Log.w("WearSyncService", "Falha ao decodificar snapshot", e)
            return
        }
        if (snapshot.protocolVersion != 1) {
            Log.w("WearSyncService", "Versão de snapshot incompatível: ${snapshot.protocolVersion}")
            return
        }
        serviceScope.launch {
            runCatching { room.saveSnapshot(snapshot) }
                .onFailure { Log.w("WearSyncService", "Falha ao salvar snapshot", it) }
        }
    }

    private fun resendPendingCommands() {
        val store = stateStore ?: return
        val queue = store.loadPendingCommands()
        if (queue.isEmpty()) return
        val nodes = runCatching {
            Tasks.await(Wearable.getNodeClient(this).getConnectedNodes())
        }.getOrNull() ?: return
        if (nodes.isEmpty()) return
        for (comando in queue.all()) {
            val payload = Json.encodeToString(WearCommand.serializer(), comando)
            var delivered = false
            for (node in nodes) {
                try {
                    Tasks.await(
                        Wearable.getMessageClient(this)
                            .sendMessage(node.id, PATH_COMMAND, payload.toByteArray()),
                    )
                    delivered = true
                } catch (e: Exception) {
                    Log.w("WearSyncService", "Falha ao reenviar ${comando.commandId}", e)
                }
            }
            if (delivered) {
                queue.removeById(comando.commandId)
            }
        }
        store.savePendingCommands(queue)
    }

    private fun sendRequestState() {
        val nodes = Tasks.await(Wearable.getNodeClient(this).getConnectedNodes())
        val comando = WearCommand(
            commandId = java.util.UUID.randomUUID().toString(),
            sessionId = -1,
            kind = WearCommandKind.REQUEST_STATE,
        )
        val payload = Json.encodeToString(WearCommand.serializer(), comando)
        for (node in nodes) {
            try {
                Tasks.await(Wearable.getMessageClient(this).sendMessage(node.id, PATH_COMMAND, payload.toByteArray()))
            } catch (e: Exception) {
                Log.w("WearSyncService", "Falha ao enviar REQUEST_STATE", e)
            }
        }
    }

    /**
     * H4-16: envia o lote de séries pendentes do Room em `PATH_SYNC_BATCH`
     * (MessageClient). Depois que o envio confirma em ≥ 1 node, as séries
     * deixam de ser pendentes (`pending = false`) — reenviar o mesmo lote não
     * duplica: o celular deduplica por `clientSetId`.
     */
    private fun sendPendingSets() {
        val room = standaloneStore ?: return
        serviceScope.launch {
            val pendentes = runCatching { room.pendingSets() }
                .onFailure { Log.w("WearSyncService", "Falha ao ler pendentes", it) }
                .getOrNull()
                ?: return@launch
            if (pendentes.isEmpty()) return@launch
            val nodes = runCatching {
                Tasks.await(Wearable.getNodeClient(this@WearSyncService).getConnectedNodes())
            }.getOrNull().orEmpty()
            if (nodes.isEmpty()) return@launch
            val ativa = runCatching { room.activeSession() }.getOrNull()
            val lote = WearSyncBatch(
                sessionId = ativa?.id ?: pendentes.first().sessionId,
                batchId = java.util.UUID.randomUUID().toString(),
                // H4-16: sessão finalizada offline → o lote vai com finished = true
                // e o celular aplica as séries e finaliza a sessão.
                finished = ativa?.status == "finished",
                sets = pendentes.map {
                    com.treinos.protocol.WearLoggedSet(
                        clientSetId = it.clientSetId,
                        sessionExerciseId = it.sessionExerciseId,
                        exerciseIndex = it.exerciseIndex,
                        setNumber = it.setNumber,
                        reps = it.reps,
                        weightKg = it.weightKg,
                        rpe = it.rpe,
                        loggedAt = it.loggedAt,
                    )
                },
            )
            val payload = Json.encodeToString(WearSyncBatch.serializer(), lote)
            var entregue = false
            for (node in nodes) {
                try {
                    Tasks.await(
                        Wearable.getMessageClient(this@WearSyncService)
                            .sendMessage(node.id, PATH_SYNC_BATCH, payload.toByteArray()),
                    )
                    entregue = true
                } catch (e: Exception) {
                    Log.w("WearSyncService", "Falha ao enviar lote para ${node.id}", e)
                }
            }
            if (entregue) {
                runCatching { room.markSynced(pendentes.map { it.clientSetId }) }
            }
        }
    }
}
