package com.treinos.wear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import com.treinos.wear.tile.WorkoutTileService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode
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
                onLogSet = { sessionId, reps, weightKg ->
                    scope.launch { sendCommand(sessionId, WearCommandKind.LOG_SET, reps, weightKg) }
                },
                onSkipRest = { sessionId ->
                    scope.launch { sendCommand(sessionId, WearCommandKind.SKIP_REST) }
                },
                onFinish = { sessionId ->
                    scope.launch {
                        sendCommand(sessionId, WearCommandKind.FINISH_WORKOUT)
                        // H4-14 (regra 13): o relógio pediu o fim — o tile precisa
                        // re-renderizar (vira ocioso quando o estado permitir).
                        runCatching { WorkoutTileService.requestUpdate(this@WorkoutWearActivity) }
                    }
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

    private suspend fun sendCommand(
        sessionId: Long,
        kind: WearCommandKind,
        reps: Int? = null,
        weightKg: Double? = null,
    ) {
        // H4-15: o LOG_SET do pulso passa a preencher reps/weightKg (antes
        // nulos) e o celular deixa de cair no fallback da última série.
        val comando = WearCommand(
            commandId = UUID.randomUUID().toString(),
            sessionId = sessionId,
            kind = kind,
            reps = reps,
            weightKg = weightKg,
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
    onLogSet: (Long, Int?, Double?) -> Unit,
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
                // H4-15: editor de carga/reps da próxima série. Parte do default
                // ao abrir e reseta ao trocar de exercício (sessionId ou
                // exerciseIndex novos); a edição não persiste entre aberturas.
                var editor by remember { mutableStateOf(WearSetEditorState(0.0, WearSetEditor.DEFAULT_REPS)) }
                LaunchedEffect(state.sessionId, state.exerciseIndex) {
                    editor = WearSetEditor.reduce(
                        editor,
                        WearSetEditorAction.Reset(state.lastWeightKg, state.lastReps, state.targetReps),
                    )
                }

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
                    // H4-15: controles +/− de carga e reps (passos de 2,5 kg e
                    // 1 rep). Sem debounce: editar é local, não envia comando.
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.editor_weight_label),
                            fontSize = 14.sp,
                        )
                        StepButton(
                            label = stringResource(R.string.editor_decrease),
                            descricao = stringResource(R.string.editor_decrease_weight_desc),
                            onClick = { editor = WearSetEditor.reduce(editor, WearSetEditorAction.DecrementWeight) },
                        )
                        Text(
                            text = formatarCarga(editor.weightKg),
                            fontSize = 14.sp,
                        )
                        StepButton(
                            label = stringResource(R.string.editor_increase),
                            descricao = stringResource(R.string.editor_increase_weight_desc),
                            onClick = { editor = WearSetEditor.reduce(editor, WearSetEditorAction.IncrementWeight) },
                        )
                    }
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.editor_reps_label),
                            fontSize = 14.sp,
                        )
                        StepButton(
                            label = stringResource(R.string.editor_decrease),
                            descricao = stringResource(R.string.editor_decrease_reps_desc),
                            onClick = { editor = WearSetEditor.reduce(editor, WearSetEditorAction.DecrementReps) },
                        )
                        Text(
                            text = "${editor.reps}",
                            fontSize = 14.sp,
                        )
                        StepButton(
                            label = stringResource(R.string.editor_increase),
                            descricao = stringResource(R.string.editor_increase_reps_desc),
                            onClick = { editor = WearSetEditor.reduce(editor, WearSetEditorAction.IncrementReps) },
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
                        onClick = {
                            dispatch {
                                // H4-15: carga 0,0 = sem carga (peso corporal) →
                                // null no comando, mesma regra de
                                // WearSetEditorState.toLogSetCommand.
                                onLogSet(state.sessionId, editor.reps, editor.weightKg.takeIf { it != 0.0 })
                            }
                        },
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

/**
 * H4-15: botão de passo (+/−) do editor. Sem o `WearDebounce` dos botões que
 * enviam comando — editar é local e precisa aceitar toques repetidos.
 */
@Composable
private fun StepButton(
    label: String,
    descricao: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .semantics { contentDescription = descricao },
    ) {
        Text(text = label, fontSize = 18.sp)
    }
}

/** Carga do editor sempre com 1 casa decimal ("20,0 kg"), como em WearFormat. */
private fun formatarCarga(weightKg: Double): String {
    val texto = BigDecimal.valueOf(weightKg).setScale(1, RoundingMode.HALF_UP).toPlainString()
    return "${texto.replace('.', ',')} kg"
}