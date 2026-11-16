package com.treinos.app.data.wear

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.treinos.protocol.PATH_COMMAND
import com.treinos.protocol.PATH_SYNC_BATCH
import com.treinos.protocol.WearCommand
import com.treinos.protocol.WearSyncBatch
import kotlinx.serialization.json.Json

class WearCommandListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        when (messageEvent.path) {
            PATH_COMMAND -> processarComando(messageEvent.data)
            // H4-16: lote de séries registradas offline no relógio (path novo;
            // celular antigo ignora — não há listener para o path).
            PATH_SYNC_BATCH -> processarLote(messageEvent.data)
        }
    }

    private fun processarComando(data: ByteArray?) {
        if (data == null || data.isEmpty()) return
        val comando = try {
            Json { ignoreUnknownKeys = true }.decodeFromString(WearCommand.serializer(), String(data))
        } catch (e: Exception) {
            Log.w("WearCommandListenerService", "Falha ao decodificar comando", e)
            return
        }
        if (comando.protocolVersion != 1) {
            Log.w("WearCommandListenerService", "Versão de comando incompatível: ${comando.protocolVersion}")
            return
        }
        WearSyncRepositoryHolder.instance?.onCommandReceived(comando)
    }

    private fun processarLote(data: ByteArray?) {
        if (data == null || data.isEmpty()) return
        val lote = try {
            Json { ignoreUnknownKeys = true }.decodeFromString(WearSyncBatch.serializer(), String(data))
        } catch (e: Exception) {
            Log.w("WearCommandListenerService", "Falha ao decodificar lote de sync", e)
            return
        }
        if (lote.protocolVersion != 1) {
            Log.w("WearCommandListenerService", "Versão de lote incompatível: ${lote.protocolVersion}")
            return
        }
        WearSyncRepositoryHolder.instance?.onSyncBatchReceived(lote)
    }

    object WearSyncRepositoryHolder {
        @Volatile var instance: WearSyncRepository? = null
    }
}
