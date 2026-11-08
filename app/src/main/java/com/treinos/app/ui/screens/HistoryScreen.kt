package com.treinos.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.treinos.app.data.local.MANUAL_SESSION_SOURCE
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.WorkoutSessionEntity
import com.treinos.app.data.media.ExerciseMedia
import com.treinos.app.ui.ActivityModality
import com.treinos.app.ui.components.EmptyState
import com.treinos.app.ui.components.ExerciseImage
import com.treinos.app.ui.components.KeyValueRow
import com.treinos.app.ui.components.LoadingState
import com.treinos.app.ui.components.MetricCard
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.components.SectionTitle
import com.treinos.app.ui.decorativeContentDescription
import com.treinos.app.ui.formatDateTime
import com.treinos.app.ui.formatDistanceMeters
import com.treinos.app.ui.formatDuration
import com.treinos.app.ui.formatDateShort
import com.treinos.app.ui.formatMonthYear
import com.treinos.app.ui.formatPacePer100m
import com.treinos.app.ui.formatPacePerKm
import com.treinos.app.ui.formatVolumeKg
import com.treinos.app.ui.history.HistoryMetrics
import com.treinos.app.ui.history.STATUS_COMPLETED
import com.treinos.app.ui.history.STATUS_IN_PROGRESS
import com.treinos.app.ui.history.buildHistoryMetrics
import com.treinos.app.ui.history.buildMonthGrid
import com.treinos.app.ui.history.firstDayOfMonth
import com.treinos.app.ui.history.isBeforeCurrentMonth
import com.treinos.app.ui.history.lastDayOfMonth
import com.treinos.app.ui.history.sessionDates
import com.treinos.app.ui.history.sessionDay
import com.treinos.app.ui.history.weekDayLabels
import com.treinos.app.ui.swimStrokeOptions
import com.treinos.app.ui.UiState
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(
    sessions: List<SessionWithExercises>,
    sessionsState: UiState<List<SessionWithExercises>> = UiState.Content(emptyList()),
    onBack: () -> Unit,
    exerciseMedia: Map<String, ExerciseMedia> = emptyMap(),
    onOpenExerciseDetail: (String) -> Unit = {},
    onEditActivity: (Long) -> Unit = {},
    onDeleteActivity: (Long) -> Unit = {},
    muscleByExerciseId: Map<String, String> = emptyMap(),
    onGenerate: () -> Unit = {},
) {
    // H3-29: Loading até o primeiro emit do Room — o empty state ("Nenhum
    // treino registrado") só aparece quando o banco realmente está vazio.
    if (sessionsState is UiState.Loading) {
        LoadingState(modifier = Modifier.fillMaxSize())
        return
    }
    val today = remember { LocalDate.now() }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var modalityFilter by remember { mutableStateOf<String?>(null) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    val modalities = sessions.map { it.session.modality }.distinct()
    val modalitySessions = if (modalityFilter == null) {
        sessions
    } else {
        sessions.filter { it.session.modality == modalityFilter }
    }

    val firstDay = firstDayOfMonth(month.year, month.monthValue)
    val lastDay = lastDayOfMonth(month.year, month.monthValue)
    val monthSessions = modalitySessions.filter { sessionDay(it) in firstDay..lastDay }
    val metrics = buildHistoryMetrics(modalitySessions, muscleByExerciseId, firstDay, lastDay)
    val completedDates = sessionDates(monthSessions.filter { it.session.status == STATUS_COMPLETED })
    val inProgressDates = sessionDates(monthSessions.filter { it.session.status == STATUS_IN_PROGRESS })
    val visible = selectedDate?.let { day -> monthSessions.filter { sessionDay(it) == day } } ?: monthSessions

    Scaffold(
        topBar = { ScreenTopBar(title = "Histórico", onBack = onBack) },
    ) { padding ->
        if (sessions.isEmpty()) {
            EmptyState(
                title = "Nenhum treino registrado",
                description = "Inicie um treino na tela inicial ou registre uma atividade avulsa. " +
                    "Tudo aparece aqui mesmo sem conexão.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                action = {
                    Button(onClick = onGenerate, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Text("Gerar treino")
                    }
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    MonthNavigator(
                        month = month,
                        canGoNext = isBeforeCurrentMonth(month.year, month.monthValue, today),
                        onNavigate = { deltaMonths ->
                            month = month.plusMonths(deltaMonths)
                            selectedDate = null
                        },
                    )
                }

                item {
                    MonthCalendar(
                        month = month,
                        today = today,
                        selectedDate = selectedDate,
                        completedDates = completedDates,
                        inProgressDates = inProgressDates,
                        onSelectDay = { date ->
                            selectedDate = if (selectedDate == date) null else date
                        },
                    )
                }

                // H3-28: chips logo após o calendário — filtrar muda o que está
                // na área visível, sem rolar uma tela.
                if (modalities.size > 1) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = modalityFilter == null,
                                onClick = { modalityFilter = null },
                                label = { Text("Todas") },
                            )
                            modalities.forEach { modality ->
                                FilterChip(
                                    selected = modalityFilter == modality,
                                    onClick = { modalityFilter = modality },
                                    label = {
                                        Text(ActivityModality.fromStorageValue(modality).label)
                                    },
                                )
                            }
                        }
                    }
                }

                selectedDate?.let { date ->
                    item {
                        FilterChip(
                            selected = true,
                            onClick = { selectedDate = null },
                            label = { Text("Dia ${formatDateShort(date)} • limpar") },
                        )
                    }
                }

                if (selectedDate == null) {
                    item {
                        MetricsCards(metrics)
                    }
                    if (metrics.volumeByMuscle.isNotEmpty()) {
                        item { VolumeByMuscle(metrics) }
                    }
                }

                if (visible.isEmpty()) {
                    item {
                        Text(
                            text = if (monthSessions.isEmpty()) {
                                "Nenhum treino em ${formatMonthYear(month.year, month.monthValue)}. " +
                                    "Use as setas para ver outros meses."
                            } else {
                                "Nenhum registro neste dia."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(visible, key = { it.session.id }) { session ->
                    HistorySessionRow(
                        session = session,
                        exerciseMedia = exerciseMedia,
                        onOpenExerciseDetail = onOpenExerciseDetail,
                        onEditActivity = onEditActivity,
                        onDeleteActivity = onDeleteActivity,
                    )
                }

                if (selectedDate != null) {
                    // H3-28: com dia selecionado, a lista daquele dia vem antes
                    // das métricas e do volume.
                    item {
                        MetricsCards(metrics)
                    }
                    if (metrics.volumeByMuscle.isNotEmpty()) {
                        item { VolumeByMuscle(metrics) }
                    }
                }
            }
        }
    }
}

/** H3-28: ranking de volume por músculo, extraído para poder reordenar a tela. */
@Composable
private fun VolumeByMuscle(metrics: HistoryMetrics) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SectionTitle("Volume por músculo")
        metrics.volumeByMuscle.entries
            .sortedByDescending { it.value }
            .forEach { (muscle, volume) ->
                KeyValueRow(label = muscle, value = formatVolumeKg(volume))
            }
    }
}

@Composable
private fun HistorySessionRow(
    session: SessionWithExercises,
    exerciseMedia: Map<String, ExerciseMedia>,
    onOpenExerciseDetail: (String) -> Unit,
    onEditActivity: (Long) -> Unit,
    onDeleteActivity: (Long) -> Unit,
) {
    if (session.session.source == MANUAL_SESSION_SOURCE) {
        ActivityCard(
            session = session.session,
            onEdit = { onEditActivity(session.session.id) },
            onDelete = { onDeleteActivity(session.session.id) },
        )
    } else {
        SessionCard(
            session = session,
            exerciseMedia = exerciseMedia,
            onOpenExerciseDetail = onOpenExerciseDetail,
        )
    }
}

@Composable
private fun MonthNavigator(
    month: YearMonth,
    canGoNext: Boolean,
    onNavigate: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = { onNavigate(-1L) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mês anterior")
        }
        Text(
            text = formatMonthYear(month.year, month.monthValue),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onNavigate(1L) }, enabled = canGoNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Próximo mês")
        }
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate?,
    completedDates: Set<LocalDate>,
    inProgressDates: Set<LocalDate>,
    onSelectDay: (LocalDate) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                weekDayLabels().forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            buildMonthGrid(month.year, month.monthValue).chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        val hasSession = date != null && (date in completedDates || date in inProgressDates)
                        DayCell(
                            date = date,
                            isToday = date == today,
                            isSelected = date != null && date == selectedDate,
                            hasCompleted = date != null && date in completedDates,
                            hasInProgress = date != null && date in inProgressDates,
                            modifier = Modifier.weight(1f),
                            onClick = { date?.let(onSelectDay) },
                            clickable = hasSession,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    isToday: Boolean,
    isSelected: Boolean,
    hasCompleted: Boolean,
    hasInProgress: Boolean,
    clickable: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val contentColor = when {
        date == null -> Color.Transparent
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val workoutCount = (if (hasCompleted) 1 else 0) + (if (hasInProgress) 1 else 0)
    val dayDescription = date?.let {
        val label = it.format(DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("pt-BR")))
        if (workoutCount > 0) "$label, $workoutCount treino${if (workoutCount > 1) "s" else ""}" else label
    }
    Column(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .wrapContentSize(Alignment.Center)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            )
            .then(
                if (date != null && clickable) {
                    Modifier
                        .clickable(onClick = onClick)
                        .semantics {
                            role = Role.Button
                            contentDescription = dayDescription.orEmpty()
                            stateDescription = when {
                                isSelected -> "Selecionado"
                                else -> if (hasInProgress && !hasCompleted) "Em andamento" else "Concluído"
                            }
                        }
                } else {
                    Modifier.clearAndSetSemantics { }
                },
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = date?.dayOfMonth?.toString() ?: "",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = contentColor,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (hasCompleted) DayDot(filled = true)
            if (hasInProgress) DayDot(filled = false)
        }
    }
}

/** Ponto cheio = dia com treino concluído; ponto oco = só treino em andamento. */
@Composable
private fun DayDot(filled: Boolean) {
    val color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    Box(
        modifier = Modifier
            .size(7.dp)
            .then(
                if (filled) Modifier.background(color, CircleShape)
                else Modifier.border(1.5.dp, color, CircleShape)
            ),
    )
}

@Composable
private fun MetricsCards(metrics: HistoryMetrics) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetricCard(
                label = "Treinos",
                value = metrics.completedCount.toString(),
                detail = if (metrics.sessionCount > metrics.completedCount) {
                    "${metrics.sessionCount - metrics.completedCount} em andamento"
                } else {
                    null
                },
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                label = "Séries",
                value = metrics.totalSets.toString(),
                detail = "${metrics.totalReps} reps",
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetricCard(
                label = "Volume",
                value = formatVolumeKg(metrics.totalVolumeKg),
                detail = "com carga registrada",
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                label = "Duração",
                value = formatDuration(metrics.totalDurationSeconds),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Card do registro avulso: resumo e notas, sem séries e sem expansão de exercícios. */
@Composable
private fun ActivityCard(
    session: WorkoutSessionEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteDialog by remember(session.id) { mutableStateOf(false) }
    val modality = ActivityModality.fromStorageValue(session.modality)
    val title = session.title.ifBlank { session.dayName }.ifBlank { modality.label }
    val distance = formatDistanceMeters(session.totalDistanceMeters)
    val pace = when (modality) {
        ActivityModality.SWIM ->
            formatPacePer100m(session.totalDurationSeconds, session.totalDistanceMeters)
        ActivityModality.RUN ->
            formatPacePerKm(session.totalDurationSeconds, session.totalDistanceMeters)
        else -> null
    }
    val summary = buildList {
        add(formatDuration(session.totalDurationSeconds))
        distance?.let { add(it) }
        pace?.let { add(it) }
        session.sessionRpe?.let { add("RPE ${trimNumber(it)}") }
    }.joinToString(" • ")

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "$title • $summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatDateTime(session.startedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            session.poolLengthMeters?.let {
                Text(text = "Piscina ${it} m", style = MaterialTheme.typography.bodySmall)
            }
            session.stroke?.let { stroke ->
                Text(
                    text = strokeLabel(stroke),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (session.notes.isNotBlank()) {
                Text(
                    text = session.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit, modifier = Modifier.minimumInteractiveComponentSize()) { Text("Editar") }
                TextButton(onClick = { showDeleteDialog = true }, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Excluir o registro?") },
            text = { Text("$title sai do histórico e do próximo backup. Não dá para desfazer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }, modifier = Modifier.minimumInteractiveComponentSize()) { Text("Cancelar") }
            },
        )
    }
}

private fun strokeLabel(stroke: String): String =
    "Nado: ${swimStrokeOptions.firstOrNull { it.first == stroke }?.second ?: stroke}"

@Composable
private fun SessionCard(
    session: SessionWithExercises,
    exerciseMedia: Map<String, ExerciseMedia>,
    onOpenExerciseDetail: (String) -> Unit,
) {
    var expanded by remember(session.session.id) { mutableStateOf(false) }
    val totalSets = session.exercises.sumOf { it.sets.size }
    val completed = session.session.status == STATUS_COMPLETED

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = if (expanded) "Expandido" else "Recolhido"
            }
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = if (completed) "Concluído" else "Em andamento",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (completed) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        contentDescription = decorativeContentDescription(),
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
                                .minimumInteractiveComponentSize()
                                .semantics { role = Role.Button }
                                .clickable(
                                    onClickLabel = "Abrir detalhes do exercício",
                                    onClick = { onOpenExerciseDetail(exercise.exercise.exerciseId) },
                                ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ExerciseImage(
                                imageUrl = exerciseMedia[exercise.exercise.exerciseId]?.imageUrl,
                                exerciseName = exercise.exercise.exerciseName.ifBlank { exercise.exercise.exerciseId },
                                contentDescription = decorativeContentDescription(),
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
