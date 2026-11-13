package com.treinos.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.treinos.app.ui.HapticEvent
import com.treinos.app.ui.timerShouldAnnounce
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
import com.treinos.app.ui.hapticFeedbackForEvent
import androidx.compose.ui.res.stringResource
import com.treinos.app.R

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
        val haptics = LocalHapticFeedback.current
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.mobility_discard_title)) },
            text = {
                Text(
                    stringResource(R.string.mobility_discard_body)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.performHapticFeedback(hapticFeedbackForEvent(HapticEvent.MobilityDiscarded))
                        showDiscardDialog = false
                        // Volta para a lista de rotinas (como antes), mas só
                        // depois da confirmação — nada é descartado em silêncio.
                        onClearSession()
                    },
                ) {
                    Text(stringResource(R.string.mobility_discard_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }            ) {
                Text(stringResource(R.string.common_continue))
            }
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
                title = stringResource(R.string.screen_mobility),
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
                            text = stringResource(R.string.mobility_safety_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.mobility_safety_body),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(if (showSafety) stringResource(R.string.mobility_safety_hide) else stringResource(R.string.mobility_safety_show))
                        if (showSafety) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.mobility_safety_seek), style = MaterialTheme.typography.labelLarge)
                                Text(stringResource(R.string.mobility_safety_arm_leg), style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.mobility_safety_numbness), style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.mobility_safety_strength), style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.mobility_safety_trauma), style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.mobility_safety_pain), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            item { Text(stringResource(R.string.mobility_curated), style = MaterialTheme.typography.titleMedium) }

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
                                imageRes = cover.imageRes,
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
                            Text(stringResource(R.string.mobility_start))
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

    val sideLabel = movement?.side?.let { if (it == "left") stringResource(R.string.mobility_side_left) else stringResource(R.string.mobility_side_right) }
    val timerAnnouncement = when {
        timerShouldAnnounce(session.secondsRemaining) ->
            "Temporizador ${formatDuration(session.secondsRemaining.toLong())}"
        else -> null
    }
    var lastTimerAnnounced by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session.secondsRemaining) {
        lastTimerAnnounced = timerAnnouncement
    }

    fun finalize() {
        onFinishMobility(planId, dayId, routine.id, session.elapsedSeconds)
        onClearSession()
    }

    val haptics = LocalHapticFeedback.current

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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            timerAnnouncement?.let {
                Text(
                    text = it,
                    modifier = Modifier
                        .size(0.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
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
                            imageRes = movement.imageRes,
                            size = 160.dp,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                    if (session.inTransition) {
                        // H3-31: transição de 5 s entre movimentos, com toque
                        // para pular.
                        Text(
                            stringResource(R.string.mobility_transition),
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
                                stringResource(R.string.mobility_next, next.name),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            onClick = {
                                haptics.performHapticFeedback(hapticFeedbackForEvent(HapticEvent.MobilityControl))
                                onNextMovement()
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text(stringResource(R.string.mobility_skip_transition))
                        }
                    } else {
                        Text(
                            formatDuration(session.secondsRemaining.toLong()),
                            style = MaterialTheme.typography.displayLarge,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                        if (nextMovement != null && movement != null) {
                            Text(
                                stringResource(R.string.mobility_next, nextMovement.name),
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
                    onClick = {
                        haptics.performHapticFeedback(hapticFeedbackForEvent(HapticEvent.MobilityControl))
                        onPreviousMovement()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = session.movementIndex > 0,
                ) {
                    Text(stringResource(R.string.common_back))
                }
                Button(
                    onClick = {
                        haptics.performHapticFeedback(hapticFeedbackForEvent(HapticEvent.MobilityControl))
                        onTogglePause()
                    },
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
                            session.running && !session.paused -> stringResource(R.string.mobility_pause)
                            session.paused -> stringResource(R.string.mobility_resume)
                            else -> stringResource(R.string.mobility_start)
                        }
                    )
                }
                OutlinedButton(
                    onClick = {
                        haptics.performHapticFeedback(hapticFeedbackForEvent(HapticEvent.MobilityControl))
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
                            session.inTransition -> stringResource(R.string.mobility_skip)
                            !isLast -> stringResource(R.string.mobility_advance)
                            else -> stringResource(R.string.mobility_finish)
                        }
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    haptics.performHapticFeedback(hapticFeedbackForEvent(HapticEvent.MobilityFinished))
                    finalize()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.mobility_finish_early))
            }
        }
    }
}
