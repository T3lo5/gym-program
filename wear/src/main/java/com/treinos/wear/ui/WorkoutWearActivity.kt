package com.treinos.wear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.treinos.protocol.PATH_COMMAND
import com.treinos.protocol.WearCommand
import com.treinos.protocol.WearCommandKind
import com.treinos.protocol.WearWorkoutState
import com.treinos.wear.R
import com.treinos.wear.WearApplication
import com.treinos.wear.data.WearStateStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.UUID

class WorkoutWearActivity : ComponentActivity() {

    private val stateStore: WearStateStore by lazy {
        (application as WearApplication).stateStore
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scope = rememberCoroutineScope()
            val connectionWatcher = remember {
                WearConnectionWatcher(this@WorkoutWearActivity, scope)
            }
            DisposableEffect(Unit) {
                connectionWatcher.start()
                onDispose { connectionWatcher.stop() }
            }
            val connected by connectionWatcher.connected.collectAsState()

            WorkoutWearScreen(
                state = stateStore.state.collectAsState().value,
                connected = connected,
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
            commandId = UUID.randomUUID().toString(),
            sessionId = -1,
            kind = WearCommandKind.REQUEST_STATE,
        )
        val payload = Json.encodeToString(WearCommand.serializer(), comando)
        for (node in nodes) {
            try {
                Tasks.await(Wearable.getMessageClient(this).sendMessage(node.id, PATH_COMMAND, payload.toByteArray()))
            } catch (e: Exception) {
                // Ignorado: sem relógio/conexão não deve quebrar a tela.
            }
        }
    }

    private suspend fun sendCommand(sessionId: Long, kind: WearCommandKind) {
        val comando = WearCommand(
            commandId = UUID.randomUUID().toString(),
            sessionId = sessionId,
            kind = kind,
        )

        val fila = stateStore.loadPendingCommands()
        fila.add(comando)
        stateStore.savePendingCommands(fila)

        val nodes = Tasks.await(Wearable.getNodeClient(this).getConnectedNodes())
        val payload = Json.encodeToString(WearCommand.serializer(), comando)
        var entregue = false
        for (node in nodes) {
            try {
                Tasks.await(Wearable.getMessageClient(this).sendMessage(node.id, PATH_COMMAND, payload.toByteArray()))
                entregue = true
            } catch (e: Exception) {
                // Tenta o próximo node; se nenhum confirmar, o comando segue na fila.
            }
        }
        if (entregue) {
            val pendentes = stateStore.loadPendingCommands()
            pendentes.removeById(comando.commandId)
            stateStore.savePendingCommands(pendentes)
        }
    }
}

@Composable
fun WorkoutWearScreen(
    state: WearWorkoutState?,
    connected: Boolean,
    onLogSet: (Long) -> Unit,
    onSkipRest: (Long) -> Unit,
    onFinish: (Long) -> Unit,
    onRequestState: () -> Unit,
) {
    var timerState by remember { mutableStateOf(WearTimerState(0, false, false)) }
    val debounce = remember { WearDebounce() }
    var buttonsEnabled by remember { mutableStateOf(true) }

    fun dispatch(action: () -> Unit) {
        val now = System.currentTimeMillis()
        if (!debounce.canSend(now)) return
        debounce.markSent(now)
        buttonsEnabled = false
        action()
    }

    LaunchedEffect(buttonsEnabled) {
        if (!buttonsEnabled) {
            while (!debounce.canSend(System.currentTimeMillis())) {
                delay(100)
            }
            buttonsEnabled = true
        }
    }

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
                val finishedLabel = stringResource(R.string.workout_finished)
                val refreshLabel = stringResource(R.string.refresh)
                Text(
                    text = finishedLabel,
                    fontSize = 20.sp,
                    style = MaterialTheme.typography.titleLarge,
                )
                Button(
                    onClick = onRequestState,
                    modifier = Modifier.semantics { contentDescription = refreshLabel },
                ) {
                    Text(text = refreshLabel)
                }
            }
        } else if (WearSessionKind.from(state.modality) == WearSessionKind.Strength) {
                // Sincroniza com o estado publicado pelo celular: descanso novo reinicia,
                // mesma chave não toca, descanso nulo/0 zera um timer rodando.
                LaunchedEffect(state) {
                    timerState = WearTimer.syncFromState(timerState, state)
                }

                // Tick local de 1 s: o descanso conta no pulso sem depender do celular.
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(1_000L)
                        if (timerState.isRunning) {
                            timerState = WearTimer.reduce(timerState, WearTimerAction.Tick)
                        }
                    }
                }

                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!connected) {
                        val noConnectionLabel = stringResource(R.string.no_connection)
                        Text(
                            text = noConnectionLabel,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.semantics { contentDescription = noConnectionLabel },
                        )
                    }
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
                        text = stringResource(R.string.set_progress_format, state.setNumber, state.plannedSets),
                        fontSize = 16.sp,
                    )
                    if (state.targetReps.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.target_reps_format, state.targetReps),
                            fontSize = 16.sp,
                        )
                    }
                    val ultima = formatLastSet(state.lastWeightKg, state.lastReps)
                    if (ultima != null) {
                        Text(
                            text = stringResource(R.string.last_set_format, ultima),
                            fontSize = 16.sp,
                        )
                    }
                    if (timerState.remainingSeconds > 0) {
                        val restLabel = stringResource(R.string.rest_remaining_format, timerState.remainingSeconds)
                        Text(
                            text = restLabel,
                            fontSize = 18.sp,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.semantics { contentDescription = restLabel },
                        )
                    }
                    val completeSetLabel = stringResource(R.string.complete_set)
                    Button(
                        onClick = { dispatch { onLogSet(state.sessionId) } },
                        enabled = buttonsEnabled,
                        modifier = Modifier.semantics { contentDescription = completeSetLabel },
                    ) {
                        Text(text = completeSetLabel)
                    }
                    val skipRestLabel = stringResource(R.string.skip_rest)
                    Button(
                        onClick = {
                            dispatch {
                                onSkipRest(state.sessionId)
                                timerState = WearTimer.reduce(timerState, WearTimerAction.Skip)
                            }
                        },
                        enabled = buttonsEnabled,
                        modifier = Modifier.semantics { contentDescription = skipRestLabel },
                    ) {
                        Text(text = skipRestLabel)
                    }
                    val finishLabel = stringResource(R.string.finish_workout)
                    Button(
                        onClick = { dispatch { onFinish(state.sessionId) } },
                        enabled = buttonsEnabled,
                        modifier = Modifier.semantics { contentDescription = finishLabel },
                    ) {
                        Text(text = finishLabel)
                    }
                }
            } else {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!connected) {
                        val noConnectionLabel = stringResource(R.string.no_connection)
                        Text(
                            text = noConnectionLabel,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.semantics { contentDescription = noConnectionLabel },
                        )
                    }
                    Text(
                        text = state.dayName,
                        fontSize = 20.sp,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                    )
                    Text(
                        text = stringResource(R.string.in_progress),
                        fontSize = 18.sp,
                    )
                    val finishLabel = stringResource(R.string.finish_workout)
                    Button(
                        onClick = { dispatch { onFinish(state.sessionId) } },
                        enabled = buttonsEnabled,
                        modifier = Modifier.semantics { contentDescription = finishLabel },
                    ) {
                        Text(text = finishLabel)
                    }
                }
            }
    }
}