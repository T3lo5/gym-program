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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.media.ExerciseMedia
import com.treinos.app.ui.components.EmptyState
import com.treinos.app.ui.components.ExerciseImage
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.decorativeContentDescription
import com.treinos.app.ui.exerciseContentDescription
import com.treinos.app.ui.formatDateTime
import com.treinos.app.ui.progressStateDescription
import com.treinos.app.ui.restStateDescription
import com.treinos.app.ui.setContentDescription
import com.treinos.app.ui.timerShouldAnnounce

@Composable
fun WorkoutScreen(
    session: SessionWithExercises?,
    restRemaining: Int?,
    restTotal: Int?,
    onLeave: () -> Unit,
    onLogSet: (SessionExerciseWithSets, Int?, Double?, Double?) -> Unit,
    onSkipRest: () -> Unit,
    onFinish: () -> Unit,
    hasInProgressSession: Boolean,
    exerciseMedia: Map<String, ExerciseMedia> = emptyMap(),
    onOpenExerciseDetail: (String) -> Unit = {},
) {
    var exerciseToLog by remember { mutableStateOf<SessionExerciseWithSets?>(null) }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = session?.session?.dayName?.ifBlank { "Treino" } ?: "Treino",
                onBack = onLeave,
            )
        },
        bottomBar = {
            if (session != null) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = onFinish,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text("Finalizar treino")
                    }
                }
            }
        },
    ) { padding ->
        val current = session
        if (current == null) {
            if (hasInProgressSession) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Carregando treino…",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            } else {
                EmptyState(
                    title = "Nenhum treino em andamento",
                    description = "Escolha um dia de treino na tela inicial para começar.",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    action = {
                        OutlinedButton(onClick = onLeave) { Text("Voltar") }
                    },
                )
            }
        } else {
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
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = current.session.planName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Iniciado em ${formatDateTime(current.session.startedAt)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            val done = current.exercises.sumOf { it.exercise.completedSets }
                            val planned = current.exercises.sumOf { it.exercise.plannedSets }
                            LinearProgressIndicator(
                                progress = { if (planned > 0) done.toFloat() / planned else 0f },
                                modifier = Modifier.fillMaxWidth().semantics {
                                    progressBarRangeInfo = ProgressBarRangeInfo(
                                        current = done.toFloat(),
                                        range = 0f..planned.coerceAtLeast(1).toFloat(),
                                    )
                                    stateDescription = progressStateDescription(done, planned)
                                },
                            )
                            Text(
                                text = progressStateDescription(done, planned),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                if (restRemaining != null && restTotal != null) {
                    item {
                        RestCard(
                            remaining = restRemaining,
                            total = restTotal,
                            onSkip = onSkipRest,
                        )
                    }
                }

                items(current.exercises.sortedBy { it.exercise.exerciseOrder }, key = { it.exercise.id }) { exercise ->
                    ExerciseCard(
                        exercise = exercise,
                        media = exerciseMedia[exercise.exercise.exerciseId],
                        onLogSet = { exerciseToLog = exercise },
                        onOpenDetail = { onOpenExerciseDetail(exercise.exercise.exerciseId) },
                    )
                }

                item {
                    Text(
                        text = "Cada série é gravada no SQLite do aparelho assim que você confirma. " +
                            "Nada vai para o backend até você sincronizar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }

    exerciseToLog?.let { exercise ->
        AddSetDialog(
            exercise = exercise,
            onDismiss = { exerciseToLog = null },
            onConfirm = { reps, weight, rpe ->
                onLogSet(exercise, reps, weight, rpe)
                exerciseToLog = null
            },
        )
    }
}

@Composable
private fun RestCard(remaining: Int, total: Int, onSkip: () -> Unit) {
    val announce = timerShouldAnnounce(remaining)
    val message = if (remaining > 0) {
        "Descanso: ${formatSeconds(remaining)}"
    } else {
        "Descanso concluído. Bora para a próxima série."
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp).semantics(mergeDescendants = true) {
                stateDescription = if (remaining > 0) restStateDescription(remaining, total) else "Descanso concluído"
                if (announce) liveRegion = LiveRegionMode.Polite
            },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (remaining > 0) {
                LinearProgressIndicator(
                    progress = { if (total > 0) (total - remaining).toFloat() / total else 0f },
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
                )
                Text(
                    text = "Total de ${formatSeconds(total)}. A próxima série pode começar quando quiser.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Text("Pular descanso")
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: SessionExerciseWithSets,
    media: ExerciseMedia?,
    onLogSet: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    val name = exercise.exercise.exerciseName.ifBlank { exercise.exercise.exerciseId }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .semantics { role = Role.Button }
                    .clickable(onClickLabel = "Abrir detalhes do exercício", onClick = onOpenDetail),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExerciseImage(
                    imageUrl = media?.imageUrl,
                    exerciseName = name,
                    contentDescription = decorativeContentDescription(),
                    size = 72.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading(); contentDescription = exerciseContentDescription(name) },
                    )
                    Text(
                        text = "${exercise.exercise.plannedSets} séries planejadas • descanso de " +
                            "${exercise.exercise.restSeconds}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val sets = exercise.sets.sortedBy { it.setNumber }
            if (sets.isEmpty()) {
                Text(
                    text = "Nenhuma série registrada ainda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                sets.forEach { set ->
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {
                                stateDescription = setContentDescription(
                                    set.setNumber,
                                    set.reps,
                                    set.weightKg,
                                    set.rpe,
                                ) + ", série concluída"
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Série ${set.setNumber}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = buildString {
                                set.reps?.let { append("$it reps") }
                                set.weightKg?.let {
                                    if (isNotEmpty()) append(" • ")
                                    append("${formatWeight(it)} kg")
                                }
                                set.rpe?.let {
                                    if (isNotEmpty()) append(" • ")
                                    append("RPE ${formatWeight(it)}")
                                }
                                if (isEmpty()) append("registrada")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Button(
                onClick = onLogSet,
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
            ) {
                Text("Registrar série ${exercise.sets.size + 1}")
            }
        }
    }
}

@Composable
private fun AddSetDialog(
    exercise: SessionExerciseWithSets,
    onDismiss: () -> Unit,
    onConfirm: (Int?, Double?, Double?) -> Unit,
) {
    val last = exercise.sets.maxByOrNull { it.setNumber }
    var reps by remember { mutableStateOf(last?.reps?.toString().orEmpty()) }
    var weight by remember { mutableStateOf(last?.weightKg?.let { formatWeight(it) }.orEmpty()) }
    var rpe by remember { mutableStateOf(last?.rpe?.let { formatWeight(it) }.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    fun submit() {
        val parsedReps = reps.toIntOrNull()
        if (reps.isNotBlank() && parsedReps == null) {
            error = "Informe as repetições como número inteiro."
        } else {
            onConfirm(parsedReps, parseDecimal(weight), parseDecimal(rpe))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Série ${exercise.sets.size + 1} — ${exercise.exercise.exerciseName}")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Planejado: ${exercise.exercise.plannedSets} séries. " +
                        "Preencha o que você fez.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = reps,
                    onValueChange = { reps = it.filter(Char::isDigit); error = null },
                    label = { Text("Repetições") },
                    supportingText = { Text(if (reps.isBlank()) "Opcional" else "Número inteiro") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { value ->
                        weight = value.filter { it.isDigit() || it == ',' || it == '.' }
                        error = null
                    },
                    label = { Text("Carga (kg)") },
                    supportingText = { Text("Opcional. Use vírgula ou ponto.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = rpe,
                    onValueChange = { value ->
                        rpe = value.filter { it.isDigit() || it == ',' || it == '.' }
                        error = null
                    },
                    label = { Text("RPE (opcional)") },
                    supportingText = { Text("Opcional, de 1 a 10.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::submit, modifier = Modifier.minimumInteractiveComponentSize()) {
                Text("Salvar série")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) { Text("Cancelar") }
        },
    )
}

private fun parseDecimal(value: String): Double? =
    value.trim().replace(',', '.').toDoubleOrNull()

private fun formatWeight(value: Double): String {
    val rounded = value.toLong()
    return if (value == rounded.toDouble()) rounded.toString()
    else String.format("%.1f", value)
}

private fun formatSeconds(seconds: Int): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return "${minutes.toString().padStart(2, '0')}:${rest.toString().padStart(2, '0')}"
}
