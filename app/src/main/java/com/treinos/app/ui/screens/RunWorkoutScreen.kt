package com.treinos.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.ui.HapticEvent
import com.treinos.app.ui.KeepScreenOnEffect
import com.treinos.app.ui.RunLogInput
import com.treinos.app.ui.RunPlanTarget
import com.treinos.app.ui.UiState
import com.treinos.app.ui.formatPacePerKm
import com.treinos.app.ui.formatRunPlanTarget
import com.treinos.app.ui.hapticFeedbackForEvent
import com.treinos.app.ui.parseDraftNumber
import com.treinos.app.ui.parseDistanceKm
import com.treinos.app.ui.validateRunLog
import com.treinos.app.ui.components.EmptyState
import com.treinos.app.ui.components.LoadingState
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.components.SectionTitle
import androidx.compose.ui.res.stringResource
import com.treinos.app.R

/**
 * Corrida planejada (H3-29): a tela distingue carregando (antes do Room
 * emitir), sessão inexistente (empty state com volta — nunca um formulário
 * morto) e o formulário de registro.
 *
 * H3-31: os campos sobrevivem à rotação (`rememberSaveable`), o alvo do dia
 * planejado aparece no topo e a distância é digitada em km.
 */
@Composable
fun RunWorkoutScreen(
    state: UiState<SessionWithExercises?>,
    onLeave: () -> Unit,
    onFinish: (sessionId: Long, input: RunLogInput) -> Unit,
    planTarget: RunPlanTarget? = null,
) {
    when (val current = state) {
        is UiState.Loading -> RunWorkoutLoading(onLeave)
        is UiState.Empty -> RunWorkoutMissing(onLeave)
        is UiState.Content -> {
            val session = current.data
            if (session == null) {
                RunWorkoutMissing(onLeave)
            } else {
                RunWorkoutForm(
                    session = session,
                    planTarget = planTarget,
                    onFinish = onFinish,
                    onLeave = onLeave,
                )
            }
        }
    }
}

@Composable
private fun RunWorkoutLoading(onLeave: () -> Unit) {
    Scaffold(
        topBar = { ScreenTopBar(title = stringResource(R.string.screen_run), onBack = onLeave) },
    ) { padding ->
        LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
    }
}

@Composable
private fun RunWorkoutMissing(onLeave: () -> Unit) {
    Scaffold(
        topBar = { ScreenTopBar(title = stringResource(R.string.screen_run), onBack = onLeave) },
    ) { padding ->
        EmptyState(
            title = stringResource(R.string.run_session_not_found),
            description = stringResource(R.string.run_session_not_found_body),
            modifier = Modifier.fillMaxSize().padding(padding),
            action = {
                Button(onClick = onLeave, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Text(stringResource(R.string.common_back))
                }
            },
        )
    }
}

@Composable
private fun RunWorkoutForm(
    session: SessionWithExercises,
    planTarget: RunPlanTarget?,
    onFinish: (sessionId: Long, input: RunLogInput) -> Unit,
    onLeave: () -> Unit,
) {
    var durationMinutes by rememberSaveable { mutableStateOf("") }
    var distanceKm by rememberSaveable { mutableStateOf("") }
    var rpe by rememberSaveable { mutableStateOf("") }
    var durationError by rememberSaveable { mutableStateOf<String?>(null) }
    var distanceError by rememberSaveable { mutableStateOf<String?>(null) }
    var rpeError by rememberSaveable { mutableStateOf<String?>(null) }
    var pace by rememberSaveable { mutableStateOf<String?>(null) }
    var showDiscardConfirm by rememberSaveable { mutableStateOf(false) }

    // Sessão de corrida em andamento mantém a tela acesa (H3-26).
    KeepScreenOnEffect(enabled = true)

    fun compute() {
        val dur = durationMinutes.toIntOrNull() ?: 0
        val km = parseDistanceKm(distanceKm)
        val input = RunLogInput(
            durationSeconds = dur.toLong() * 60,
            distanceKm = km,
            rpe = parseDraftNumber(rpe),
        )
        val result = validateRunLog(input)
        // H3-47: o erro fica inline no campo correspondente (supportingText
        // + isError), não em um único texto abaixo do formulário. Duração é
        // o único campo obrigatório, então fica com toda mensagem do
        // validador que não seja de distância ou RPE — inclusive
        // "Informe uma duração de pelo menos 1 minuto." do campo em branco,
        // que antes passava despercebida e liberava o envio com 0 s.
        val error = result.error
        durationError = error?.takeIf {
            !it.startsWith("Distância") && !it.startsWith("RPE")
        }
        distanceError = if (km == null && distanceKm.isNotBlank()) {
            "Distância inválida. Use até uma casa decimal, com vírgula ou ponto (ex.: 5,2)."
        } else if (error != null && error.startsWith("Distância")) {
            error
        } else {
            null
        }
        rpeError = if (error != null && error.startsWith("RPE")) {
            error
        } else {
            null
        }
        pace = formatPacePerKm(dur.toLong() * 60, km?.let { it * 1000.0 })
    }

    val runTitle = session.session.dayName.takeIf { it.isNotBlank() } ?: stringResource(R.string.screen_run)
    Scaffold(
        topBar = {
            ScreenTopBar(
                title = runTitle,
                onBack = onLeave,
            )
        },
    ) { padding ->
        // H3-29: rolagem no formulário — em telas pequenas o botão de finalizar
        // fica alcançável em vez de cortado fora da tela.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (planTarget != null) {
                // H3-31: o alvo do dia planejado no topo — a corrida se comporta
                // como parte do plano, não como protótipo.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.run_plan_target),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = planTarget.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        formatRunPlanTarget(planTarget)?.let { alvo ->
                            Text(
                                text = alvo,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        Text(
                            text = stringResource(R.string.run_plan_target_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.run_record_planned),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.run_record_planned_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionTitle(stringResource(R.string.run_your_run))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = durationMinutes,
                    onValueChange = { durationMinutes = it; compute() },
                    label = { Text(stringResource(R.string.run_duration)) },
                    supportingText = {
                        Text(durationError ?: stringResource(R.string.run_duration_support))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("run_duration_field"),
                    isError = durationError != null,
                )
                OutlinedTextField(
                    value = distanceKm,
                    onValueChange = { distanceKm = it; compute() },
                    label = { Text(stringResource(R.string.run_distance)) },
                    supportingText = {
                        Text(distanceError ?: stringResource(R.string.run_distance_support))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("run_distance_field"),
                    isError = distanceError != null,
                )
                OutlinedTextField(
                    value = rpe,
                    onValueChange = { rpe = it; compute() },
                    label = { Text(stringResource(R.string.run_rpe)) },
                    supportingText = {
                        Text(rpeError ?: stringResource(R.string.run_rpe_support))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                    isError = rpeError != null,
                )
            }

            pace?.let { p ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.run_estimated_pace, p),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Button(
                onClick = {
                    compute()
                    if (durationError == null && distanceError == null && rpeError == null) {
                        val dur = durationMinutes.toIntOrNull() ?: 0
                        onFinish(
                            session.session.id,
                            RunLogInput(
                                durationSeconds = dur.toLong() * 60,
                                distanceKm = parseDistanceKm(distanceKm),
                                rpe = parseDraftNumber(rpe),
                            ),
                        )
                    }
                },
                enabled = durationError == null && distanceError == null && rpeError == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .minimumInteractiveComponentSize(),
            ) {
                Text(stringResource(R.string.run_finish))
            }

            TextButton(
                onClick = { showDiscardConfirm = true },
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    }

    if (showDiscardConfirm) {
        val haptics = LocalHapticFeedback.current
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(R.string.run_discard_title)) },
            text = {
                Text(
                    stringResource(R.string.run_discard_body)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.performHapticFeedback(hapticFeedbackForEvent(HapticEvent.RunDiscarded))
                        showDiscardConfirm = false
                        onLeave()
                    },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text(stringResource(R.string.run_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardConfirm = false },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text(stringResource(R.string.common_continue))
                }
            },
        )
    }
}
