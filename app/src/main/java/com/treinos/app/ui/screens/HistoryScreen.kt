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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.media.ExerciseMedia
import com.treinos.app.ui.components.ExerciseImage
import com.treinos.app.ui.components.EmptyState
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.formatDateTime
import com.treinos.app.ui.formatDuration

@Composable
fun HistoryScreen(
    sessions: List<SessionWithExercises>,
    onBack: () -> Unit,
    exerciseMedia: Map<String, ExerciseMedia> = emptyMap(),
    onOpenExerciseDetail: (String) -> Unit = {},
) {
    Scaffold(
        topBar = { ScreenTopBar(title = "Histórico", onBack = onBack) },
    ) { padding ->
        if (sessions.isEmpty()) {
            EmptyState(
                title = "Nenhum treino registrado",
                description = "Inicie um treino na tela inicial. As séries aparecem aqui assim que " +
                    "forem salvas no aparelho.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(sessions, key = { it.session.id }) { session ->
                    SessionCard(
                        session = session,
                        exerciseMedia = exerciseMedia,
                        onOpenExerciseDetail = onOpenExerciseDetail,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: SessionWithExercises,
    exerciseMedia: Map<String, ExerciseMedia>,
    onOpenExerciseDetail: (String) -> Unit,
) {
    var expanded by remember(session.session.id) { mutableStateOf(false) }
    val totalSets = session.exercises.sumOf { it.sets.size }
    val completed = session.session.status == "completed"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = session.session.dayName.ifBlank { "Treino" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (completed) "Concluído" else "Em andamento",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (completed) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Recolher" else "Expandir",
                    )
                }
            }

            Text(
                text = session.session.planName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${formatDateTime(session.session.startedAt)} • " +
                    "duração ${formatDuration(session.session.totalDurationSeconds)} • " +
                    "$totalSets série(s)",
                style = MaterialTheme.typography.bodySmall,
            )

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        session.exercises
                            .sortedBy { it.exercise.exerciseOrder }
                            .forEach { exercise ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenExerciseDetail(exercise.exercise.exerciseId) },
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ExerciseImage(
                                        imageUrl = exerciseMedia[exercise.exercise.exerciseId]?.imageUrl,
                                        exerciseName = exercise.exercise.exerciseName.ifBlank { exercise.exercise.exerciseId },
                                        size = 48.dp,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = exercise.exercise.exerciseName.ifBlank { exercise.exercise.exerciseId },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        if (exercise.sets.isEmpty()) {
                                            Text(
                                                text = "Sem séries registradas",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        } else {
                                            exercise.sets.sortedBy { it.setNumber }.forEach { set ->
                                                Text(
                                                    text = buildString {
                                                        append("Série ${set.setNumber}")
                                                        set.reps?.let { append(" • $it reps") }
                                                        set.weightKg?.let { append(" • ${trimNumber(it)} kg") }
                                                        set.rpe?.let { append(" • RPE ${trimNumber(it)}") }
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
            }
        }
    }
}

private fun trimNumber(value: Double): String {
    val rounded = value.toLong()
    return if (value == rounded.toDouble()) rounded.toString()
    else String.format("%.1f", value)
}
