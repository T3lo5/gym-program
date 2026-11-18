package com.treinos.wear.data

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fachada das chamadas GMS do Data Layer.
 *
 * `Tasks.await` lança `IllegalStateException: Must not be called on the main
 * application thread` quando roda na main — e os callbacks da activity
 * (`scope.launch` do Compose) e do `WearableListenerService`
 * (`onPeerConnected`) rodam na main. Era o crash "abre e fecha na sequência":
 * o processo morria com o app na tela (o pior ponto era
 * `WearSyncService.sendRequestState`, chamado sem proteção no
 * `onPeerConnected` — o relógio reconecta ao celular justo quando o usuário
 * abre o app).
 *
 * Todo await passa a rodar em IO e qualquer falha vira valor neutro: o
 * comando fica na fila (H3-20) em vez de derrubar o processo.
 */
class WearRpc(private val io: CoroutineDispatcher = Dispatchers.IO) {

    /**
     * Roda [block] fora da main; qualquer falha vira `null` — nunca propaga
     * exceção para o chamador.
     */
    suspend fun <T> offMain(block: () -> T): T? =
        withContext(io) { runCatching { block() } }.getOrNull()

    /** Nodes alcançáveis; falha → lista vazia (sem crash). */
    suspend fun connectedNodes(context: Context): List<Node> =
        offMain { Tasks.await(Wearable.getNodeClient(context).getConnectedNodes()) }
            .orEmpty()

    /** Envia mensagem a um node; `true` só com entrega confirmada pelo GMS. */
    suspend fun sendMessage(context: Context, nodeId: String, path: String, payload: ByteArray): Boolean =
        offMain {
            Tasks.await(Wearable.getMessageClient(context).sendMessage(nodeId, path, payload))
            true
        } ?: false
}
