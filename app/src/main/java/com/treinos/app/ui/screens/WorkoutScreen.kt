package com.treinos.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.ui.components.EmptyState
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.formatDateTime

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
                            Text(
                                text = "$done de $planned séries registradas",
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
                        onLogSet = { exerciseToLog = exercise },
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
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (remaining > 0) {
                Text(
                    text = "Descanso: ${formatSeconds(remaining)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = "Total de ${formatSeconds(total)}. A próxima série pode começar quando quiser.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    text = "Descanso concluído. Bora para a próxima série.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            OutlinedButton(onClick = onSkip) { Text("Pular descanso") }
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: SessionExerciseWithSets,
    onLogSet: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = exercise.exercise.exerciseName.ifBlank { exercise.exercise.exerciseId },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${exercise.exercise.plannedSets} séries planejadas • descanso de " +
                    "${exercise.exercise.restSeconds}s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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
                        modifier = Modifier.fillMaxWidth(),
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
                            contentDescription = "Série concluída",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Button(
                onClick = onLogSet,
                modifier = Modifier.fillMaxWidth(),
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
                    onValueChange = { reps = it.filter(Char::isDigit) },
                    label = { Text("Repetições") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { value ->
                        weight = value.filter { it.isDigit() || it == ',' || it == '.' }
                    },
                    label = { Text("Carga (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = rpe,
                    onValueChange = { value ->
                        rpe = value.filter { it.isDigit() || it == ',' || it == '.' }
                    },
                    label = { Text("RPE (opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
            TextButton(
                onClick = {
                    val parsedReps = reps.toIntOrNull()
                    if (reps.isNotBlank() && parsedReps == null) {
                        error = "Informe as repetições como número inteiro."
                        return@TextButton
                    }
                    onConfirm(
                        parsedReps,
                        parseDecimal(weight),
                        parseDecimal(rpe),
                    )
                },
            ) {
                Text("Salvar série")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
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
