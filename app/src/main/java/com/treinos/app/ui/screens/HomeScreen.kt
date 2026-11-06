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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.treinos.app.data.local.MANUAL_SESSION_SOURCE
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.WorkoutDayWithExercises
import com.treinos.app.data.local.WorkoutPlanWithDetails
import com.treinos.app.ui.ActivityModality
import com.treinos.app.ui.ModalityTab
import com.treinos.app.ui.PlanCycle
import com.treinos.app.ui.SessionKind
import com.treinos.app.ui.defaultModalityTab
import com.treinos.app.ui.groupSessionsByModality
import com.treinos.app.ui.formatDateTime
import com.treinos.app.ui.formatDistanceMeters
import com.treinos.app.ui.formatDuration
import com.treinos.app.ui.formatPacePerKm
import com.treinos.app.ui.formatReviewDate
import com.treinos.app.ui.isToday
import com.treinos.app.ui.goalLabel
import com.treinos.app.ui.planVolumeLabel
import com.treinos.app.ui.progression.REVIEW_POSTPONE_WEEKS
import com.treinos.app.ui.reviewIsDue
import com.treinos.app.ui.reviewStateDescription
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.components.SectionTitle

@Composable
fun HomeScreen(
    plan: WorkoutPlanWithDetails?,
    inProgressSession: SessionWithExercises?,
    sessions: List<SessionWithExercises> = emptyList(),
    planCycle: PlanCycle? = null,
    showDeloadNotice: Boolean = false,
    onGenerate: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onStartDay: (Long) -> Unit,
    onResume: () -> Unit,
    onLogActivity: () -> Unit,
    onOpenMobility: () -> Unit = {},
    onPostponeReview: (Long) -> Unit = {},
    onDismissDeloadNotice: () -> Unit = {},
) {
    val tabsByModality = remember(plan) {
        plan?.let { groupSessionsByModality(it.days) } ?: emptyMap()
    }
    val presentTabs = tabsByModality.keys.filter { tab ->
        tabsByModality[tab]?.isNotEmpty() == true
    }
    val todayWeekday = java.time.LocalDate.now()
        .get(java.time.temporal.ChronoField.DAY_OF_WEEK)
    val defaultTab = remember(plan, todayWeekday) {
        plan?.let { defaultModalityTab(it.days, todayWeekday) }
    }
    var selectedTab by remember { mutableStateOf<ModalityTab?>(defaultTab) }

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
                            Button(onClick = onGenerate, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Text("Gerar meu primeiro treino")
                            }
                        }
                    }
                }
            } else {
                item { ActivePlanCard(plan) }

                if (reviewIsDue(plan.plan.nextReviewAt)) {
                    item {
                        ReviewPlanCard(
                            reviewDate = formatReviewDate(plan.plan.nextReviewAt).orEmpty(),
                            onGenerate = onGenerate,
                            onPostpone = { onPostponeReview(plan.plan.id) },
                        )
                    }
                }

                val cycle = planCycle
                if (showDeloadNotice && cycle != null) {
                    item {
                        DeloadNoticeCard(week = cycle.week, onDismiss = onDismissDeloadNotice)
                    }
                }

                if (presentTabs.isNotEmpty()) {
                    item {
                        TabRow(
                            selectedTabIndex = presentTabs.indexOf(selectedTab ?: presentTabs.first()),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            presentTabs.forEach { tab ->
                                Tab(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    text = {
                                        Text(when (tab) {
                                            ModalityTab.STRENGTH -> "Academia"
                                            ModalityTab.RUN -> "Corrida"
                                            ModalityTab.MOBILITY -> "Mobilidade"
                                        })
                                    },
                                )
                            }
                        }
                    }

                    val currentSessions = tabsByModality[selectedTab ?: presentTabs.first()].orEmpty()
                    item { SectionTitle("Sessões de ${tabLabel(selectedTab ?: presentTabs.first())}") }
                    items(currentSessions, key = { it.day.id }) { day ->
                        ModalityDayCard(day = day, onStart = { onStartDay(day.day.id) })
                    }
                } else {
                    item { SectionTitle("Dias de treino") }
                    items(plan.days.sortedBy { it.day.dayOrder }, key = { it.day.id }) { day ->
                        DayCard(day = day, onStart = { onStartDay(day.day.id) })
                    }
                }

                val todayManual = sessions.filter {
                    it.session.source == MANUAL_SESSION_SOURCE && isToday(it.session.startedAt)
                }
                if (todayManual.isNotEmpty()) {
                    item { SectionTitle("Atividades de hoje") }
                    items(todayManual, key = { it.session.id }) { session ->
                        ActivitySummaryCard(session)
                    }
                }

                item {
                    OutlinedButton(
                        onClick = onGenerate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .minimumInteractiveComponentSize(),
                    ) {
                        Text("Gerar novo treino")
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onLogActivity,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .minimumInteractiveComponentSize(),
                ) {
                    Text("Registrar atividade")
                }
            }

            item {
                OutlinedButton(
                    onClick = onOpenMobility,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize(),
                ) {
                    Text("Mobilidade e alongamento")
                }
            }

            item {
                Text(
                    text = "Atividade avulsa (natação, aula, corrida) entra no histórico sem plano.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Text(
                    text = "Rotinas curtas de mobilidade/alongamento para bem-estar e recuperação.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

private fun tabLabel(tab: ModalityTab): String = when (tab) {
    ModalityTab.STRENGTH -> "musculação"
    ModalityTab.RUN -> "corrida"
    ModalityTab.MOBILITY -> "mobilidade"
}

private fun modalityTabOf(kind: SessionKind): ModalityTab = when (kind) {
    SessionKind.STRENGTH -> ModalityTab.STRENGTH
    SessionKind.RUN -> ModalityTab.RUN
    SessionKind.MOBILITY -> ModalityTab.MOBILITY
}

@Composable
private fun ModalityDayCard(day: WorkoutDayWithExercises, onStart: () -> Unit) {
    val kind = SessionKind.fromModality(day.day.modality)
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
                    when (kind) {
                        SessionKind.RUN -> {
                            val distance = day.exercises.firstOrNull()?.targetDistanceMeters
                            val pace = day.exercises.firstOrNull()?.targetPaceSecondsPerKm
                            Text(
                                text = buildList {
                                    add("${day.exercises.size} bloco(s)")
                                    distance?.let { add(formatDistanceMeters(it)) }
                                    pace?.let { add(com.treinos.app.ui.formatMinuteSecond(pace.toLong()) + "/km") }
                                }.joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        SessionKind.MOBILITY -> {
                            val duration = day.exercises.firstOrNull()?.targetDurationSeconds?.let { it / 60 }
                            Text(
                                text = "${day.exercises.size} rotina(s) • ${duration ?: 0} min",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        SessionKind.STRENGTH -> {
                            Text(
                                text = "${day.exercises.size} exercício${if (day.exercises.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            if (day.day.isRestDay) {
                Text(
                    text = "Dia de descanso",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    day.exercises
                        .sortedBy { it.exerciseOrder }
                        .take(6)
                        .forEach { exercise ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clearAndSetSemantics { },
                                )
                                 Text(
                                     text = exercise.exerciseName,
                                     style = MaterialTheme.typography.bodyMedium,
                                     color = MaterialTheme.colorScheme.onSurface,
                                     maxLines = 1,
                                     modifier = Modifier.weight(1f),
                                 )
                                 if (exercise.targetReps.isNotBlank()) {
                                     Text(
                                         text = "${exercise.sets} séries × ${exercise.targetReps}",
                                         style = MaterialTheme.typography.bodySmall,
                                         color = MaterialTheme.colorScheme.onSurfaceVariant,
                                         maxLines = 1,
                                     )
                                 } else if (exercise.sets > 0) {
                                     Text(
                                         text = "${exercise.sets} série${if (exercise.sets != 1) "s" else ""}",
                                         style = MaterialTheme.typography.bodySmall,
                                         color = MaterialTheme.colorScheme.onSurfaceVariant,
                                         maxLines = 1,
                                     )
                                 }
                             }
                         }
                 }
                 Button(
                     onClick = onStart,
                     modifier = Modifier.fillMaxWidth().padding(top = 4.dp).minimumInteractiveComponentSize(),
                 ) {
                     Text("Iniciar ${tabLabel(modalityTabOf(kind))}")
                }
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
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                review?.let { stateDescription = reviewStateDescription(it, due) }
            },
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
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = planVolumeLabel(plan),
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
private fun ReviewPlanCard(reviewDate: String, onGenerate: () -> Unit, onPostpone: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                if (reviewDate.isNotBlank()) stateDescription = reviewStateDescription(reviewDate, true)
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Revisar plano",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "A revisão de $reviewDate está atrasada. Gere um treino novo ou adie a revisão.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onGenerate,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text("Gerar novo treino")
                }
                OutlinedButton(
                    onClick = onPostpone,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text("Adiar $REVIEW_POSTPONE_WEEKS semanas")
                }
            }
        }
    }
}

@Composable
private fun DeloadNoticeCard(week: Int, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Ciclo do plano",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Semana $week do ciclo — considere reduzir 1 série por exercício.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "O app não muda o plano sozinho. Ajuste as séries ao registrar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Text("Entendi")
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    day.exercises
                        .sortedBy { it.exerciseOrder }
                        .take(6)
                        .forEach { exercise ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clearAndSetSemantics { },
                                )
                                 Text(
                                     text = exercise.exerciseName,
                                     style = MaterialTheme.typography.bodyMedium,
                                     color = MaterialTheme.colorScheme.onSurface,
                                     maxLines = 1,
                                     modifier = Modifier.weight(1f),
                                 )
                                 if (exercise.targetReps.isNotBlank()) {
                                     Text(
                                         text = "${exercise.sets} séries × ${exercise.targetReps}",
                                         style = MaterialTheme.typography.bodySmall,
                                         color = MaterialTheme.colorScheme.onSurfaceVariant,
                                         maxLines = 1,
                                     )
                                 } else if (exercise.sets > 0) {
                                     Text(
                                         text = "${exercise.sets} série${if (exercise.sets != 1) "s" else ""}",
                                         style = MaterialTheme.typography.bodySmall,
                                         color = MaterialTheme.colorScheme.onSurfaceVariant,
                                         maxLines = 1,
                                     )
                                 }
                             }
                         }
                 }
                 Button(
                     onClick = onStart,
                     modifier = Modifier.fillMaxWidth().padding(top = 4.dp).minimumInteractiveComponentSize(),
                 ) {
                     Text("Iniciar treino")
                }
            }
        }
    }
}

@Composable
private fun ActivitySummaryCard(session: SessionWithExercises) {
    val modality = ActivityModality.fromStorageValue(session.session.modality)
    val title = session.session.title.ifBlank { session.session.dayName }.ifBlank { modality.label }
    val distance = formatDistanceMeters(session.session.totalDistanceMeters)
    val pace = when (modality) {
        ActivityModality.RUN ->
            formatPacePerKm(session.session.totalDurationSeconds, session.session.totalDistanceMeters)
        else -> null
    }
    val summary = buildList {
        add(formatDuration(session.session.totalDurationSeconds))
        distance?.let { add(it) }
        pace?.let { add(it) }
        session.session.sessionRpe?.let { add("RPE ${trimNumber(it)}") }
    }.joinToString(" • ")

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "$summary • ${formatDateTime(session.session.startedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun trimNumber(value: Double): String {
    val rounded = value.toLong()
    return if (value == rounded.toDouble()) rounded.toString()
    else String.format("%.1f", value)
}
