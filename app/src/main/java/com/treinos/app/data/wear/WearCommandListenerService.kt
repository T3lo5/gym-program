package com.treinos.app.data.wear

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.treinos.protocol.WearCommand
import kotlinx.serialization.json.Json

class WearCommandListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        if (messageEvent.path != "/treinos/command") return
        val data = messageEvent.data
        if (data.isEmpty()) return
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

    object WearSyncRepositoryHolder {
        @Volatile var instance: WearSyncRepository? = null
    }
}
