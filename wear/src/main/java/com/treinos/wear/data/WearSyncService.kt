package com.treinos.wear.data

import android.app.Service
import android.content.Intent
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.treinos.protocol.WearCommand
import com.treinos.protocol.WearCommandKind
import com.treinos.protocol.WearWorkoutState
import kotlinx.serialization.json.Json

class WearSyncService : WearableListenerService(), DataClient.OnDataChangedListener {

    private var stateStore: WearStateStore? = null

    companion object {
        @Volatile private var store: WearStateStore? = null

        fun setStateStore(store: WearStateStore) {
            this.store = store
        }
    }

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
        sendRequestState()
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val uri = event.dataItem.uri
            if (uri.path != "/treinos/workout-state") continue
            val bytes = event.dataItem.data ?: continue
            val estado = try {
                Json { ignoreUnknownKeys = true }.decodeFromString(
                    WearWorkoutState.serializer(),
                    String(bytes)
                )
            } catch (e: Exception) {
                Log.w("WearSyncService", "Falha ao decodificar estado", e)
                continue
            }
            if (estado.protocolVersion != 1) {
                Log.w("WearSyncService", "Versão de protocolo incompatível: ${estado.protocolVersion}")
                continue
            }
            stateStore?.saveState(estado)
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        if (messageEvent.path != "/treinos/command") return
        val data = messageEvent.data
        if (data.isEmpty()) return
        val comando = try {
            Json { ignoreUnknownKeys = true }.decodeFromString(WearCommand.serializer(), String(data))
        } catch (e: Exception) {
            Log.w("WearSyncService", "Falha ao decodificar comando", e)
            return
        }
        if (comando.protocolVersion != 1) {
            Log.w("WearSyncService", "Versão de comando incompatível: ${comando.protocolVersion}")
            return
        }
        stateStore?.enqueueCommand(comando.commandId)
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
                Tasks.await(Wearable.getMessageClient(this).sendMessage(node.id, "/treinos/command", payload.toByteArray()))
            } catch (e: Exception) {
                Log.w("WearSyncService", "Falha ao enviar REQUEST_STATE", e)
            }
        }
    }
}
