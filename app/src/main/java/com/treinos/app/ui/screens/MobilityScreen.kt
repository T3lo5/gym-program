package com.treinos.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.treinos.app.ui.KeepScreenOnEffect
import com.treinos.app.ui.MobilityRoutine
import com.treinos.app.ui.MobilityRoutineTemplates
import com.treinos.app.ui.MobilityScreenState
import com.treinos.app.ui.MobilitySessionState
import com.treinos.app.data.media.ExerciseMedia
import com.treinos.app.ui.components.AppIcons
import com.treinos.app.ui.components.ExerciseImage
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.formatDuration

/**
 * Mobilidade e alongamento (H2-7) com o estado da rotina no `AppViewModel`
 * (H3-31): girar o aparelho não volta para a lista nem perde o movimento — e a
 * transição de 5 s entre movimentos passa a valer, com toque para pular.
 */
@Composable
fun MobilityScreen(
    state: MobilityScreenState,
    onBack: () -> Unit,
    exerciseMedia: Map<String, ExerciseMedia> = emptyMap(),
    onFinishMobility: (planId: Long, dayId: Long, routineId: String, elapsedSeconds: Long) -> Unit = { _, _, _, _ -> },
    onStartRoutine: (MobilityRoutine) -> Unit = {},
    onStartPreset: (String) -> Unit = {},
    onTogglePause: () -> Unit = {},
    onPreviousMovement: () -> Unit = {},
    onNextMovement: () -> Unit = {},
    onClearSession: () -> Unit = {},
    planId: Long = 0L,
    dayId: Long = 0L,
    presetRoutineId: String? = null,
) {
    var showSafety by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

    // Rotina planejada: inicia uma vez; se já houver rotina em andamento
    // (rotação, por exemplo), o ViewModel ignora o preset.
    LaunchedEffect(presetRoutineId) {
        if (presetRoutineId != null) onStartPreset(presetRoutineId)
    }

    // Rotina de mobilidade em andamento mantém a tela acesa (H3-26).
    KeepScreenOnEffect(enabled = state.session != null)

    val routine = state.routine
    val session = state.session
    if (routine == null || session == null) {
        MobilityRoutineList(
            onBack = onBack,
            showSafety = showSafety,
            onToggleSafety = { showSafety = !showSafety },
            onStartRoutine = onStartRoutine,
            exerciseMedia = exerciseMedia,
        )
    } else {
        MobilityPlayer(
            routine = routine,
            session = session,
            planId = planId,
            dayId = dayId,
            exerciseMedia = exerciseMedia,
            onFinishMobility = onFinishMobility,
            onTogglePause = onTogglePause,
            onPreviousMovement = onPreviousMovement,
            onNextMovement = onNextMovement,
            onClearSession = onClearSession,
            onAskDiscard = { showDiscardDialog = true },
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Descartar rotina?") },
            text = {
                Text(
                    "A rotina em andamento será interrompida e o tempo decorrido não " +
                        "será registrado no histórico."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        // Volta para a lista de rotinas (como antes), mas só
                        // depois da confirmação — nada é descartado em silêncio.
                        onClearSession()
                    },
                ) {
                    Text("Descartar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Continuar") }
            },
        )
    }
}

@Composable
private fun MobilityRoutineList(
    onBack: () -> Unit,
    showSafety: Boolean,
    onToggleSafety: () -> Unit,
    onStartRoutine: (MobilityRoutine) -> Unit,
    exerciseMedia: Map<String, ExerciseMedia> = emptyMap(),
) {
    Scaffold(
        topBar = {
            ScreenTopBar(
                title = "Mobilidade e alongamento",
                onBack = onBack,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Aviso de segurança",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Esta rotina é para bem-estar e recuperação, não substitui avaliação profissional. " +
                                "Não é diagnóstico, tratamento ou reabilitação.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = onToggleSafety) {
                            Text(if (showSafety) "Ocultar sinais de alerta" else "Ver sinais de alerta")
                        }
                        if (showSafety) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Procure um profissional se houver:", style = MaterialTheme.typography.labelLarge)
                                Text("• Dor irradiada para braço/perna", style = MaterialTheme.typography.bodySmall)
                                Text("• Formigamento ou dormência", style = MaterialTheme.typography.bodySmall)
                                Text("• Perda de força súbita", style = MaterialTheme.typography.bodySmall)
                                Text("• Trauma recente ou febre", style = MaterialTheme.typography.bodySmall)
                                Text("• Dor que piora com o movimento", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            item { Text("Rotinas curadas", style = MaterialTheme.typography.titleMedium) }

            items(MobilityRoutineTemplates.routines.filter { it.movements.isNotEmpty() }) { routine ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val cover = routine.movements.firstOrNull { movement ->
                            movement.catalogId?.let { exerciseMedia[it]?.imageUrl } != null
                        }
                        if (cover != null) {
                            ExerciseImage(
                                imageUrl = cover.catalogId?.let { exerciseMedia[it]?.imageUrl },
                                exerciseName = cover.name,
                                size = 56.dp,
                            )
                        }
                        Text(routine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(routine.description, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${formatDuration(MobilityRoutineTemplates.totalDurationSeconds(routine).toLong())} • ${routine.movements.size} movimentos • ${routine.focus}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { onStartRoutine(routine) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Iniciar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobilityPlayer(
    routine: MobilityRoutine,
    session: MobilitySessionState,
    planId: Long,
    dayId: Long,
    exerciseMedia: Map<String, ExerciseMedia> = emptyMap(),
    onFinishMobility: (planId: Long, dayId: Long, routineId: String, elapsedSeconds: Long) -> Unit,
    onTogglePause: () -> Unit,
    onPreviousMovement: () -> Unit,
    onNextMovement: () -> Unit,
    onClearSession: () -> Unit,
    onAskDiscard: () -> Unit,
) {
    val movement = routine.movements.getOrNull(session.movementIndex)
    val nextMovement = routine.movements.getOrNull(session.movementIndex + 1)
    val isLast = session.movementIndex >= routine.movements.size - 1

    fun finalize() {
        onFinishMobility(planId, dayId, routine.id, session.elapsedSeconds)
        onClearSession()
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = routine.name,
                // H3-31: voltar sem finalizar pergunta antes de descartar.
                onBack = onAskDiscard,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Movimento ${session.movementIndex + 1} de ${routine.movements.size}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (movement != null) {
                        Text(
                            movement.name + (movement.side?.let { " (${if (it == "left") "esquerdo" else "direito"})" } ?: ""),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            movement.cue,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        ExerciseImage(
                            imageUrl = movement.catalogId?.let { exerciseMedia[it]?.imageUrl },
                            exerciseName = movement.name,
                            size = 160.dp,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                    if (session.inTransition) {
                        // H3-31: transição de 5 s entre movimentos, com toque
                        // para pular.
                        Text(
                            "Transição",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            formatDuration(session.transitionSecondsRemaining.toLong()),
                            style = MaterialTheme.typography.displayLarge,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clickable { onNextMovement() },
                        )
                        nextMovement?.let { next ->
                            Text(
                                "Próximo: ${next.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            onClick = onNextMovement,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text("Pular transição")
                        }
                    } else {
                        Text(
                            formatDuration(session.secondsRemaining.toLong()),
                            style = MaterialTheme.typography.displayLarge,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                        if (nextMovement != null && movement != null) {
                            Text(
                                "Próximo: ${nextMovement.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onPreviousMovement,
                    modifier = Modifier.weight(1f),
                    enabled = session.movementIndex > 0,
                ) {
                    Text("Voltar")
                }
                Button(
                    onClick = onTogglePause,
                    modifier = Modifier.weight(1f),
                    enabled = session.running,
                ) {
                    Icon(
                        imageVector = if (session.running && !session.paused) AppIcons.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(
                        when {
                            session.running && !session.paused -> "Pausar"
                            session.paused -> "Retomar"
                            else -> "Iniciar"
                        }
                    )
                }
                OutlinedButton(
                    onClick = {
                        when {
                            session.inTransition -> onNextMovement()
                            !isLast -> onNextMovement()
                            else -> finalize()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    if (session.inTransition || !isLast) {
                        Icon(
                            imageVector = AppIcons.SkipNext,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                    Text(
                        when {
                            session.inTransition -> "Pular"
                            !isLast -> "Avançar"
                            else -> "Finalizar"
                        }
                    )
                }
            }

            OutlinedButton(
                onClick = ::finalize,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Finalizar antes do fim")
            }
        }
    }
}
