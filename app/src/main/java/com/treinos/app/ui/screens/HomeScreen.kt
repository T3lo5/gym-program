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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.WorkoutDayWithExercises
import com.treinos.app.data.local.WorkoutPlanWithDetails
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.components.SectionTitle
import com.treinos.app.ui.formatDateTime
import com.treinos.app.ui.formatReviewDate
import com.treinos.app.ui.goalLabel
import com.treinos.app.ui.reviewIsDue

@Composable
fun HomeScreen(
    plan: WorkoutPlanWithDetails?,
    inProgressSession: SessionWithExercises?,
    onGenerate: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onStartDay: (Long) -> Unit,
    onResume: () -> Unit,
) {
    Scaffold(
        topBar = {
            ScreenTopBar(
                title = "Treinos",
                actions = {
                    TextButton(onClick = onHistory) { Text("Histórico") }
                    TextButton(onClick = onSettings) { Text("Ajustes") }
                },
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
            if (inProgressSession != null) {
                item {
                    InProgressCard(session = inProgressSession, onResume = onResume)
                }
            }

            if (plan == null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "Nenhum treino ainda",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = "Gere um plano sob demanda. O backend devolve os treinos e a " +
                                    "data em que eles devem ser revistos. Depois disso, tudo fica " +
                                    "salvo no aparelho.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = onGenerate) {
                                Text("Gerar meu primeiro treino")
                            }
                        }
                    }
                }
            } else {
                item { ActivePlanCard(plan) }

                item { SectionTitle("Dias de treino") }

                items(plan.days.sortedBy { it.day.dayOrder }, key = { it.day.id }) { day ->
                    DayCard(day = day, onStart = { onStartDay(day.day.id) })
                }

                item {
                    OutlinedButton(
                        onClick = onGenerate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        Text("Gerar novo treino")
                    }
                }
            }

            item {
                Text(
                    text = "Os treinos e as séries ficam salvos apenas neste aparelho. " +
                        "O backend só recebe dados quando você toca em Sincronizar, em Ajustes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun InProgressCard(session: SessionWithExercises, onResume: () -> Unit) {
    val done = session.exercises.sumOf { it.sets.size }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Treino em andamento",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${session.session.dayName} • iniciado em " +
                    formatDateTime(session.session.startedAt) + " • $done série(s)",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onResume,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Retomar treino")
            }
        }
    }
}

@Composable
private fun ActivePlanCard(plan: WorkoutPlanWithDetails) {
    val entity = plan.plan
    val review = formatReviewDate(entity.nextReviewAt)
    val due = reviewIsDue(entity.nextReviewAt)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = entity.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            Text(
                text = "${goalLabel(entity.goal)} • ${entity.daysPerWeek}x por semana",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = if (entity.source == "ai") "Gerado com IA" else "Gerado localmente",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
            if (review != null) {
                Text(
                    text = if (due) {
                        "Revisão atrasada desde $review — gere um treino novo."
                    } else {
                        "Próxima revisão: $review"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (due) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun DayCard(day: WorkoutDayWithExercises, onStart: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = day.day.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        text = "${day.exercises.size} exercício${if (day.exercises.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            if (day.day.isRestDay) {
                Text(
                    text = "Dia de descanso",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        items = day.exercises.sortedBy { it.exerciseOrder }.take(6),
                        key = { it.exerciseId + it.exerciseOrder.toString() },
                    ) { exercise ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = exercise.exerciseName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Text("Iniciar treino")
                }
            }
        }
    }
}
