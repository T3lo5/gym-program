package com.treinos.wear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import com.treinos.protocol.PATH_COMMAND
import com.treinos.protocol.WearCommand
import com.treinos.protocol.WearCommandKind
import com.treinos.protocol.WearWorkoutState
import com.treinos.wear.R
import com.treinos.wear.WearApplication
import com.treinos.wear.data.WearRpc
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

    /**
     * Todas as chamadas GMS passam por aqui: `Tasks.await` na main derrubava o
     * processo (crash "abre e fecha na sequência") — ver [WearRpc].
     */
    private val rpc = WearRpc()

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
            val state by stateStore.state.collectAsState()

            // H4-16: modo standalone — sem o celular, a sessão vem do snapshot
            // no Room (WearStandaloneStore) e o reducer local registra as
            // séries; o lote sincroniza no onPeerConnected (WearSyncService).
            var standalone by remember { mutableStateOf<WearStandaloneState?>(null) }
            var standaloneStarted by rememberSaveable { mutableStateOf(false) }
            var agora by remember { mutableStateOf(System.currentTimeMillis()) }
            LaunchedEffect(connected) {
                if (connected) {
                    standalone = null
                    standaloneStarted = false
                } else {
                    standalone = runCatching {
                        val store = (application as WearApplication).standaloneStore
                        val snapshot = store.loadSnapshot()
                        if (snapshot == null) {
                            WearStandaloneState(pendingCount = store.pendingCount())
                        } else {
                            val sets = store.setsOfSession(snapshot.sessionId)
                            val primeiro = snapshot.exercises.firstOrNull()
                            WearStandaloneSession.reduce(
                                WearStandaloneState(),
                                WearStandaloneAction.Start(
                                    snapshot = snapshot,
                                    loggedSets = sets.count {
                                        it.sessionExerciseId == primeiro?.sessionExerciseId
                                    },
                                    pendingCount = store.pendingCount(),
                                ),
                            )
                        }
                    }.getOrNull()
                }
            }
            // Tick de 1 s: o descanso conta no pulso por wall-clock
            // (restante = max(0, restEndsAt - now), padrão H3-26).
            LaunchedEffect(Unit) {
                while (true) {
                    delay(1_000L)
                    agora = System.currentTimeMillis()
                }
            }
            // Integração: abriu com o celular ao alcance e sem estado local —
            // pede o estado na hora, em vez de mostrar tela vazia.
            LaunchedEffect(connected, state == null) {
                if (connected && state == null) {
                    sendRequestState()
                }
            }

            fun registrarSerieStandalone(reps: Int?, weightKg: Double?) {
                val atual = standalone ?: return
                val exercicio = atual.exercise ?: return
                scope.launch {
                    val store = (application as WearApplication).standaloneStore
                    val now = System.currentTimeMillis()
                    runCatching {
                        store.logSetLocal(
                            sessionId = atual.sessionId,
                            sessionExerciseId = exercicio.sessionExerciseId,
                            exerciseIndex = atual.exerciseIndex,
                            setNumber = atual.setNumber,
                            reps = reps,
                            weightKg = weightKg,
                            rpe = null,
                            loggedAt = now,
                        )
                        store.saveRestEndsAt(atual.sessionId, now + exercicio.restSeconds * 1000L)
                    }
                    standalone = WearStandaloneSession.reduce(
                        atual,
                        WearStandaloneAction.LogSet(reps, weightKg, now),
                    )
                }
            }

            fun pularDescansoStandalone() {
                val atual = standalone ?: return
                scope.launch {
                    runCatching {
                        (application as WearApplication).standaloneStore
                            .saveRestEndsAt(atual.sessionId, null)
                    }
                    standalone = WearStandaloneSession.reduce(atual, WearStandaloneAction.SkipRest(System.currentTimeMillis()))
                }
            }

            fun finalizarStandalone() {
                val atual = standalone ?: return
                scope.launch {
                    runCatching {
                        (application as WearApplication).standaloneStore
                            .finishSession(atual.sessionId)
                    }
                    standalone = WearStandaloneSession.reduce(atual, WearStandaloneAction.Finish(System.currentTimeMillis()))
                    // H4-14 (regra 13): o tile precisa re-renderizar.
                    runCatching { WorkoutTileService.requestUpdate(this@WorkoutWearActivity) }
                }
            }

            WorkoutWearScreen(
                state = state,
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
                standalone = standalone,
                standaloneStarted = standaloneStarted,
                standaloneRestante = WearStandaloneSession.restante(standalone?.restEndsAt, agora),
                onStandaloneStart = { standaloneStarted = true },
                onStandaloneLogSet = ::registrarSerieStandalone,
                onStandaloneSkipRest = ::pularDescansoStandalone,
                onStandaloneFinish = ::finalizarStandalone,
            )
        }
    }

    private suspend fun sendRequestState() {
        val nodes = rpc.connectedNodes(this)
        val comando = WearCommand(
            commandId = UUID.randomUUID().toString(),
            sessionId = -1,
            kind = WearCommandKind.REQUEST_STATE,
        )
        val payload = Json.encodeToString(WearCommand.serializer(), comando)
        for (node in nodes) {
            // Falha de entrega não quebra a tela: sem relógio/conexão o
            // usuário continua com o estado local (ou pede de novo).
            rpc.sendMessage(this, node.id, PATH_COMMAND, payload.toByteArray())
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

        val nodes = rpc.connectedNodes(this)
        val payload = Json.encodeToString(WearCommand.serializer(), comando)
        var entregue = false
        for (node in nodes) {
            if (rpc.sendMessage(this, node.id, PATH_COMMAND, payload.toByteArray())) {
                entregue = true
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
    /** H4-16: estado do modo standalone (`null` quando conectado). */
    standalone: WearStandaloneState? = null,
    standaloneStarted: Boolean = false,
    standaloneRestante: Int = 0,
    onStandaloneStart: () -> Unit = {},
    onStandaloneLogSet: (Int?, Double?) -> Unit = { _, _ -> },
    onStandaloneSkipRest: () -> Unit = {},
    onStandaloneFinish: () -> Unit = {},
) {
    var timerState by remember { mutableStateOf(WearTimerState(0, false, false)) }
    val debounce = remember { WearDebounce() }
    var buttonsEnabled by remember { mutableStateOf(true) }
    val haptic = LocalHapticFeedback.current

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
        // Tela redonda: canto recorta texto colado na borda — padding maior
        // quando a tela é quadrada em pixels (relógio redondo).
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val redonda = maxWidth == maxHeight
            val padHorizontal: Dp = if (redonda) 22.dp else 16.dp

            // H4-16: modo standalone — sem o celular, a sessão vem do snapshot do
            // Room e o reducer local registra as séries; nada aqui envia comando.
            if (!connected) {
                StandaloneScreen(
                    standalone = standalone,
                    started = standaloneStarted,
                    restante = standaloneRestante,
                    padHorizontal = padHorizontal,
                    onStart = onStandaloneStart,
                    onLogSet = onStandaloneLogSet,
                    onSkipRest = onStandaloneSkipRest,
                    onFinish = onStandaloneFinish,
                    onRequestState = onRequestState,
                )
            } else if (state == null) {
                // Sem estado publicado pelo celular (primeira abertura, celular
                // longe do app): NÃO é "treino concluído" — pede o estado.
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = padHorizontal, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    val semTreino = stringResource(R.string.no_workout_state)
                    val dica = stringResource(R.string.no_workout_hint)
                    Text(
                        text = semTreino,
                        fontSize = 20.sp,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { contentDescription = semTreino },
                    )
                    Text(
                        text = dica,
                        fontSize = 13.sp,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val refreshLabel = stringResource(R.string.refresh)
                    Button(
                        onClick = onRequestState,
                        modifier = Modifier.semantics { contentDescription = refreshLabel },
                    ) {
                        Text(text = refreshLabel)
                    }
                }
            } else if (state.status == "finished") {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = padHorizontal, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    val finishedLabel = stringResource(R.string.workout_finished)
                    val refreshLabel = stringResource(R.string.refresh)
                    Text(
                        text = finishedLabel,
                        fontSize = 20.sp,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { contentDescription = finishedLabel },
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
                        .padding(horizontal = padHorizontal, vertical = 16.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "${state.dayName}",
                        fontSize = 14.sp,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        text = "${state.exerciseName}",
                        fontSize = 20.sp,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                    )
                    Text(
                        text = stringResource(R.string.set_progress_format, state.setNumber, state.plannedSets),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.targetReps.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.target_reps_format, state.targetReps),
                            fontSize = 14.sp,
                        )
                    }
                    val ultima = formatLastSet(state.lastWeightKg, state.lastReps)
                    if (ultima != null) {
                        Text(
                            text = stringResource(R.string.last_set_format, ultima),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        // Descanso em curso: a informação mais importante da tela
                        // agora — número grande e na cor de destaque.
                        val restLabel = stringResource(R.string.rest_remaining_format, timerState.remainingSeconds)
                        Text(
                            text = restLabel,
                            fontSize = 26.sp,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics { contentDescription = restLabel },
                        )
                    }
                    val completeSetLabel = stringResource(R.string.complete_set)
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                    val confirmLabel = stringResource(R.string.confirm_finish)
                    ConfirmFinalizeButton(
                        label = finishLabel,
                        confirmLabel = confirmLabel,
                        enabled = buttonsEnabled,
                        onConfirmed = { dispatch { onFinish(state.sessionId) } },
                    )
                }
            } else {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = padHorizontal, vertical = 16.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
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
                    val confirmLabel = stringResource(R.string.confirm_finish)
                    ConfirmFinalizeButton(
                        label = finishLabel,
                        confirmLabel = confirmLabel,
                        enabled = buttonsEnabled,
                        onConfirmed = { dispatch { onFinish(state.sessionId) } },
                    )
                }
            }
        }
    }
}

/**
 * "Finalizar Treino" em duas etapas: o primeiro toque vira "Confirmar fim?"
 * (vermelho) e volta sozinho depois de 4 s — fim de treino acidental no pulso
 * era irreversível.
 */
@Composable
private fun ConfirmFinalizeButton(
    label: String,
    confirmLabel: String,
    enabled: Boolean,
    onConfirmed: () -> Unit,
) {
    var pedindoConfirmacao by remember { mutableStateOf(false) }
    LaunchedEffect(pedindoConfirmacao) {
        if (pedindoConfirmacao) {
            delay(4_000L)
            pedindoConfirmacao = false
        }
    }
    Button(
        onClick = {
            if (pedindoConfirmacao) {
                pedindoConfirmacao = false
                onConfirmed()
            } else {
                pedindoConfirmacao = true
            }
        },
        enabled = enabled,
        colors = if (pedindoConfirmacao) {
            // Posicional: a assinatura de buttonColors varia entre versões do
            // wear compose (backgroundColor/containerColor).
            ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)
        } else {
            ButtonDefaults.buttonColors()
        },
        modifier = Modifier.semantics {
            contentDescription = if (pedindoConfirmacao) confirmLabel else label
        },
    ) {
        Text(text = if (pedindoConfirmacao) confirmLabel else label)
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

/**
 * H4-16: tela do modo standalone — treina a sessão inteira sem o celular. A
 * série registrada vai para o Room do relógio (`pending = true`) e o lote
 * sincroniza quando os dois se reconectarem (dedupe por `clientSetId` no
 * celular). O editor de carga/reps da H4-15 é reusado: no standalone o default
 * é a carga 0,0 e o primeiro inteiro do alvo (não há "última série" publicada
 * pelo celular) — decisão registrada na spec.
 */
@Composable
private fun StandaloneScreen(
    standalone: WearStandaloneState?,
    started: Boolean,
    restante: Int,
    padHorizontal: Dp,
    onStart: () -> Unit,
    onLogSet: (Int?, Double?) -> Unit,
    onSkipRest: () -> Unit,
    onFinish: () -> Unit,
    onRequestState: () -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = padHorizontal, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        when {
            standalone == null || !standalone.hasSnapshot -> {
                // Sem snapshot no Room e sem conexão: não quebra — pede estado.
                val semTreino = stringResource(R.string.standalone_no_workout)
                Text(
                    text = semTreino,
                    fontSize = 18.sp,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { contentDescription = semTreino },
                )
                val refreshLabel = stringResource(R.string.refresh)
                Button(
                    onClick = onRequestState,
                    modifier = Modifier.semantics { contentDescription = refreshLabel },
                ) {
                    Text(text = refreshLabel)
                }
            }
            standalone.status == "finished" -> {
                val finishedLabel = stringResource(R.string.workout_finished)
                Text(
                    text = finishedLabel,
                    fontSize = 20.sp,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { contentDescription = finishedLabel },
                )
                Text(
                    text = stringResource(R.string.standalone_waiting_sync),
                    fontSize = 14.sp,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            !started -> {
                Text(
                    text = standalone.dayName.ifEmpty { stringResource(R.string.tile_workout_fallback) },
                    fontSize = 20.sp,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                )
                val iniciarLabel = stringResource(R.string.standalone_start_today)
                Button(
                    onClick = onStart,
                    modifier = Modifier.semantics { contentDescription = iniciarLabel },
                ) {
                    Text(text = iniciarLabel)
                }
            }
            WearSessionKind.from(standalone.modality) == WearSessionKind.Strength -> {
                var editor by remember(standalone.exerciseIndex) {
                    mutableStateOf(WearSetEditorState(0.0, WearSetEditor.DEFAULT_REPS))
                }
                LaunchedEffect(standalone.exerciseIndex) {
                    editor = WearSetEditor.reduce(
                        editor,
                        WearSetEditorAction.Reset(null, null, standalone.exercise?.targetReps ?: ""),
                    )
                }
                Text(
                    text = standalone.dayName,
                    fontSize = 14.sp,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = standalone.exercise?.exerciseName ?: "",
                    fontSize = 20.sp,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                )
                Text(
                    text = stringResource(R.string.set_progress_format, standalone.setNumber, standalone.plannedSets),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val alvo = standalone.exercise?.targetReps ?: ""
                if (alvo.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.target_reps_format, alvo),
                        fontSize = 14.sp,
                    )
                }
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
                ) {
                    Text(text = stringResource(R.string.editor_weight_label), fontSize = 14.sp)
                    StepButton(
                        label = stringResource(R.string.editor_decrease),
                        descricao = stringResource(R.string.editor_decrease_weight_desc),
                        onClick = { editor = WearSetEditor.reduce(editor, WearSetEditorAction.DecrementWeight) },
                    )
                    Text(text = formatarCarga(editor.weightKg), fontSize = 14.sp)
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
                    Text(text = stringResource(R.string.editor_reps_label), fontSize = 14.sp)
                    StepButton(
                        label = stringResource(R.string.editor_decrease),
                        descricao = stringResource(R.string.editor_decrease_reps_desc),
                        onClick = { editor = WearSetEditor.reduce(editor, WearSetEditorAction.DecrementReps) },
                    )
                    Text(text = "${editor.reps}", fontSize = 14.sp)
                    StepButton(
                        label = stringResource(R.string.editor_increase),
                        descricao = stringResource(R.string.editor_increase_reps_desc),
                        onClick = { editor = WearSetEditor.reduce(editor, WearSetEditorAction.IncrementReps) },
                    )
                }
                if (restante > 0) {
                    val restLabel = stringResource(R.string.rest_remaining_format, restante)
                    Text(
                        text = restLabel,
                        fontSize = 26.sp,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { contentDescription = restLabel },
                    )
                }
                val completeSetLabel = stringResource(R.string.complete_set)
                val haptic = LocalHapticFeedback.current
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        // H4-15: carga 0,0 = sem carga (peso corporal) → null.
                        onLogSet(editor.reps, editor.weightKg.takeIf { it != 0.0 })
                    },
                    modifier = Modifier.semantics { contentDescription = completeSetLabel },
                ) {
                    Text(text = completeSetLabel)
                }
                val skipRestLabel = stringResource(R.string.skip_rest)
                Button(
                    onClick = onSkipRest,
                    modifier = Modifier.semantics { contentDescription = skipRestLabel },
                ) {
                    Text(text = skipRestLabel)
                }
                val finishLabel = stringResource(R.string.finish_workout)
                val confirmLabel = stringResource(R.string.confirm_finish)
                ConfirmFinalizeButton(
                    label = finishLabel,
                    confirmLabel = confirmLabel,
                    enabled = true,
                    onConfirmed = onFinish,
                )
            }
            else -> {
                // Corrida/mobilidade: finaliza offline, sem séries.
                Text(
                    text = standalone.dayName,
                    fontSize = 20.sp,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                )
                Text(
                    text = stringResource(R.string.in_progress),
                    fontSize = 18.sp,
                )
                val finishLabel = stringResource(R.string.finish_workout)
                val confirmLabel = stringResource(R.string.confirm_finish)
                ConfirmFinalizeButton(
                    label = finishLabel,
                    confirmLabel = confirmLabel,
                    enabled = true,
                    onConfirmed = onFinish,
                )
            }
        }
    }
}

/** Carga do editor sempre com 1 casa decimal ("20,0 kg"), como em WearFormat. */
private fun formatarCarga(weightKg: Double): String {
    val texto = BigDecimal.valueOf(weightKg).setScale(1, RoundingMode.HALF_UP).toPlainString()
    return "${texto.replace('.', ',')} kg"
}
