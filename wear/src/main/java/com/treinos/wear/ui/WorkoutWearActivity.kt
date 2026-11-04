package com.treinos.wear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.TimeText
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.treinos.protocol.WearCommand
import com.treinos.protocol.WearCommandKind
import com.treinos.protocol.WearWorkoutState
import com.treinos.wear.WearApplication
import com.treinos.wear.data.WearStateStore
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class WorkoutWearActivity : ComponentActivity() {

    private val stateStore: WearStateStore by lazy {
        (application as WearApplication).stateStore
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scope = rememberCoroutineScope()
            WorkoutWearScreen(
                state = stateStore.state.collectAsState().value,
                onLogSet = { sessionId ->
                    scope.launch { sendCommand(sessionId, WearCommandKind.LOG_SET) }
                },
                onSkipRest = { sessionId ->
                    scope.launch { sendCommand(sessionId, WearCommandKind.SKIP_REST) }
                },
                onFinish = { sessionId ->
                    scope.launch { sendCommand(sessionId, WearCommandKind.FINISH_WORKOUT) }
                },
                onRequestState = {
                    scope.launch { sendRequestState() }
                },
            )
        }
    }

    private suspend fun sendRequestState() {
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
                // Ignorado: sem relógio/conexão não deve quebrar a tela.
            }
        }
    }

    private suspend fun sendCommand(sessionId: Long, kind: WearCommandKind) {
        val nodes = Tasks.await(Wearable.getNodeClient(this).getConnectedNodes())
        val comando = WearCommand(
            commandId = java.util.UUID.randomUUID().toString(),
            sessionId = sessionId,
            kind = kind,
        )
        val payload = Json.encodeToString(WearCommand.serializer(), comando)
        for (node in nodes) {
            try {
                Tasks.await(Wearable.getMessageClient(this).sendMessage(node.id, "/treinos/command", payload.toByteArray()))
            } catch (e: Exception) {
                // Ignorado
            }
        }
    }
}

@Composable
fun WorkoutWearScreen(
    state: WearWorkoutState?,
    onLogSet: (Long) -> Unit,
    onSkipRest: (Long) -> Unit,
    onFinish: (Long) -> Unit,
    onRequestState: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (state == null || state.status == "finished") {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Text(
                    text = "Treino Concluído!",
                    fontSize = 20.sp,
                    style = MaterialTheme.typography.titleLarge,
                )
                Button(onClick = onRequestState) {
                    Text(text = "Atualizar")
                }
            }
        } else {
            val remaining = state.restRemainingSeconds ?: 0
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${state.dayName}",
                    fontSize = 16.sp,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                )
                Text(
                    text = "${state.exerciseName}",
                    fontSize = 20.sp,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                )
                Text(
                    text = "Série ${state.setNumber} / ${state.plannedSets}",
                    fontSize = 16.sp,
                )
                Text(
                    text = "Alvo: ${state.targetReps}",
                    fontSize = 16.sp,
                )
                if (remaining > 0) {
                    Text(
                        text = "Descanso: ${remaining}s",
                        fontSize = 18.sp,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Button(onClick = { onLogSet(state.sessionId) }) {
                    Text(text = "Concluir Série")
                }
                Button(onClick = { onSkipRest(state.sessionId) }) {
                    Text(text = "Pular Descanso")
                }
                Button(onClick = { onFinish(state.sessionId) }) {
                    Text(text = "Finalizar Treino")
                }
            }
        }
    }
}
