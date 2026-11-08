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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.ui.KeepScreenOnEffect
import com.treinos.app.ui.RunLogInput
import com.treinos.app.ui.RunPlanTarget
import com.treinos.app.ui.UiState
import com.treinos.app.ui.formatPacePerKm
import com.treinos.app.ui.formatRunPlanTarget
import com.treinos.app.ui.parseDraftNumber
import com.treinos.app.ui.parseDistanceKm
import com.treinos.app.ui.validateRunLog
import com.treinos.app.ui.components.EmptyState
import com.treinos.app.ui.components.LoadingState
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.components.SectionTitle

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
        topBar = { ScreenTopBar(title = "Corrida", onBack = onLeave) },
    ) { padding ->
        LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
    }
}

@Composable
private fun RunWorkoutMissing(onLeave: () -> Unit) {
    Scaffold(
        topBar = { ScreenTopBar(title = "Corrida", onBack = onLeave) },
    ) { padding ->
        EmptyState(
            title = "Sessão não encontrada",
            description = "A corrida em andamento não foi encontrada no aparelho. " +
                "Ela pode ter sido abandonada ou finalizada em outra tela.",
            modifier = Modifier.fillMaxSize().padding(padding),
            action = {
                Button(onClick = onLeave, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Text("Voltar")
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
    var error by rememberSaveable { mutableStateOf<String?>(null) }
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
        // H3-31: texto presente que não parseia (ex.: "5,25") não pode passar
        // silenciosamente por "sem distância" — mostra o erro e bloqueia.
        error = if (km == null && distanceKm.isNotBlank()) {
            "Distância inválida. Use até uma casa decimal, com vírgula ou ponto (ex.: 5,2)."
        } else {
            result.error
        }
        pace = formatPacePerKm(dur.toLong() * 60, km?.let { it * 1000.0 })
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = session.session.dayName.ifBlank { "Corrida" },
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
                            text = "Alvo do plano",
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
                            text = "Registre o que você conseguiu — o alvo não precisa ser batido.",
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
                            text = "Registre a corrida planejada",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Duração, distância e RPE. Nada de reps ou carga nesta sessão.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionTitle("Sua corrida")

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = durationMinutes,
                    onValueChange = { durationMinutes = it; compute() },
                    label = { Text("Duração (min)") },
                    supportingText = { Text("Opcional; se não preencher, usa a duração do plano") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                )
                OutlinedTextField(
                    value = distanceKm,
                    onValueChange = { distanceKm = it; compute() },
                    label = { Text("Distância (km)") },
                    supportingText = { Text("Opcional. Ex.: 5,2 para 5 km") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                )
                OutlinedTextField(
                    value = rpe,
                    onValueChange = { rpe = it; compute() },
                    label = { Text("RPE (opcional)") },
                    supportingText = { Text("De 1 a 10. Use vírgula ou ponto para decimais.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
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
                            text = "Pace estimado: $p",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            error?.let { e ->
                Text(
                    text = e,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Button(
                onClick = {
                    compute()
                    if (error == null) {
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
                enabled = error == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .minimumInteractiveComponentSize(),
            ) {
                Text("Finalizar corrida")
            }

            TextButton(
                onClick = { showDiscardConfirm = true },
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Text("Cancelar")
            }
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Descartar treino?") },
            text = {
                Text(
                    "Você sai sem registrar a corrida. A sessão continua em andamento " +
                        "e pode ser retomada na tela inicial."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        onLeave()
                    },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text("Descartar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardConfirm = false },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text("Continuar")
                }
            },
        )
    }
}
