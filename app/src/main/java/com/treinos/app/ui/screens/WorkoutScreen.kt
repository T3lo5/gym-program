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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.media.ExerciseMedia
import com.treinos.app.ui.KeepScreenOnEffect
import com.treinos.app.ui.SetInputErrors
import com.treinos.app.ui.SetInputValidation
import com.treinos.app.ui.SetRecord
import com.treinos.app.ui.components.EmptyState
import com.treinos.app.ui.components.ExerciseImage
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.calculateProgress
import com.treinos.app.ui.decorativeContentDescription
import com.treinos.app.ui.exerciseContentDescription
import com.treinos.app.ui.formatDateTime
import com.treinos.app.ui.formatDecimal
import com.treinos.app.ui.formatKg
import com.treinos.app.ui.progression.ProgressionAction
import com.treinos.app.ui.progression.ProgressionSuggestion
import com.treinos.app.ui.progressStateDescription
import com.treinos.app.ui.restStateDescription
import com.treinos.app.ui.setContentDescription
import com.treinos.app.ui.timerShouldAnnounce
import com.treinos.app.ui.validateSetInput

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
    restPaused: Boolean = false,
    onPauseRest: () -> Unit = {},
    onResumeRest: () -> Unit = {},
    onExtendRest: () -> Unit = {},
    exerciseMedia: Map<String, ExerciseMedia> = emptyMap(),
    progressionSuggestions: Map<String, ProgressionSuggestion> = emptyMap(),
    onOpenExerciseDetail: (String) -> Unit = {},
    onUpdateSet: (SetLogEntity, Int?, Double?, Double?) -> Unit = { _, _, _, _ -> },
    onDeleteSet: (SetLogEntity) -> Unit = {},
    onAbandon: () -> Unit = {},
    onNextExercise: () -> Unit = {},
) {
    // H3-31: o alvo do diálogo sobrevive à rotação — guardamos apenas os ids
    // (Bundle-friendly) e rederivamos os objetos da sessão a cada composição.
    var exerciseToLogId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editSetId by rememberSaveable { mutableStateOf<Long?>(null) }
    val exerciseToLog = session?.exercises?.firstOrNull { it.exercise.id == exerciseToLogId }
    val editTarget = editSetId?.let { setId ->
        session?.exercises
            ?.firstOrNull { candidate -> candidate.sets.any { it.id == setId } }
            ?.let { exercise -> exercise to exercise.sets.first { it.id == setId } }
    }
    var setToDelete by remember { mutableStateOf<SetLogEntity?>(null) }
    var showFinishConfirm by remember { mutableStateOf(false) }
    var showAbandonConfirm by remember { mutableStateOf(false) }
    var scrollTargetIndex by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()

    // Sessão ativa mantém a tela acesa (H3-26); ao sair da tela a flag é liberada.
    KeepScreenOnEffect(enabled = session != null)

    // "Exercício concluído — próximo" (H3-27): rola a lista até o próximo
    // exercício com séries pendentes.
    LaunchedEffect(scrollTargetIndex) {
        scrollTargetIndex?.let { listState.animateScrollToItem(it) }
        scrollTargetIndex = null
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = session?.session?.dayName?.ifBlank { "Treino" } ?: "Treino",
                onBack = onLeave,
                actions = {
                    if (session != null) {
                        TextButton(
                            onClick = { showAbandonConfirm = true },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Text(
                                text = "Abandonar",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (session != null) {
                Column {
                    // Faixa fixa acima do botão de finalizar: o timer continua
                    // visível em qualquer ponto da rolagem (H3-26).
                    if (restRemaining != null && restTotal != null) {
                        RestBar(
                            remaining = restRemaining,
                            total = restTotal,
                            paused = restPaused,
                            onSkip = onSkipRest,
                            onExtend = onExtendRest,
                            onTogglePause = if (restPaused) onResumeRest else onPauseRest,
                        )
                    }
                    Surface(tonalElevation = 3.dp) {
                        Button(
                            onClick = { showFinishConfirm = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            Text("Finalizar treino")
                        }
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
                state = listState,
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

                items(current.exercises.sortedBy { it.exercise.exerciseOrder }, key = { it.exercise.id }) { exercise ->
                    ExerciseCard(
                        exercise = exercise,
                        media = exerciseMedia[exercise.exercise.exerciseId],
                        onLogSet = { exerciseToLogId = exercise.exercise.id },
                        onRepeatLastSet = { reps, weight, rpe ->
                            onLogSet(exercise, reps, weight, rpe)
                        },
                        onOpenDetail = { onOpenExerciseDetail(exercise.exercise.exerciseId) },
                        onEditSet = { set -> editSetId = set.id },
                        onNextExercise = {
                            // Rola até o primeiro exercício com séries pendentes;
                            // o ViewModel alinha o índice usado pelo relógio.
                            val sorted = current.exercises.sortedBy { it.exercise.exerciseOrder }
                            val nextIndex = sorted.indexOfFirst { candidate ->
                                !exerciseIsCompleted(candidate)
                            }
                            if (nextIndex >= 0) {
                                scrollTargetIndex = nextIndex + 1 // item 0 é o card de resumo
                            }
                            onNextExercise()
                        },
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
        val last = exercise.sets.maxByOrNull { it.setNumber }
        SetDialog(
            // H3-31: chave por diálogo — reabrir para outra série não herda o
            // rascunho da anterior, e girar não perde o que foi digitado.
            key = "log-${exercise.exercise.id}-${exercise.sets.size + 1}",
            title = "Série ${exercise.sets.size + 1} — ${exercise.exercise.exerciseName}",
            helper = "Planejado: ${exercise.exercise.plannedSets} séries. Preencha o que você fez.",
            suggestion = progressionSuggestions[exercise.exercise.exerciseId]
                ?.takeIf { it.action != ProgressionAction.NONE },
            initialReps = last?.reps?.toString().orEmpty(),
            initialWeight = last?.weightKg?.let { formatDecimal(it, 1) }
                ?: progressionSuggestions[exercise.exercise.exerciseId]
                    ?.takeIf { it.action != ProgressionAction.NONE }
                    ?.suggestedWeightKg
                    ?.let { formatDecimal(it, 1) }
                    .orEmpty(),
            initialRpe = last?.rpe?.let { formatDecimal(it, 1) }.orEmpty(),
            confirmLabel = "Salvar série",
            onDelete = null,
            onDismiss = { exerciseToLogId = null },
            onConfirm = { reps, weight, rpe ->
                onLogSet(exercise, reps, weight, rpe)
                exerciseToLogId = null
            },
        )
    }

    editTarget?.let { (exercise, set) ->
        SetDialog(
            key = "edit-${set.id}",
            title = "Editar série ${set.setNumber} — ${exercise.exercise.exerciseName}",
            helper = "Corrija o que você fez: a série já registrada é atualizada no lugar.",
            suggestion = null,
            initialReps = set.reps?.toString().orEmpty(),
            initialWeight = set.weightKg?.let { formatDecimal(it, 1) }.orEmpty(),
            initialRpe = set.rpe?.let { formatDecimal(it, 1) }.orEmpty(),
            confirmLabel = "Salvar alterações",
            onDelete = { setToDelete = set },
            onDismiss = { editSetId = null },
            onConfirm = { reps, weight, rpe ->
                onUpdateSet(set, reps, weight, rpe)
                editSetId = null
            },
        )
    }

    setToDelete?.let { set ->
        AlertDialog(
            onDismissRequest = { setToDelete = null },
            title = { Text("Excluir série?") },
            text = {
                Text(
                    "A série ${set.setNumber} será removida do treino e o progresso do " +
                        "exercício será recalculado. Essa ação não pode ser desfeita."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSet(set)
                        setToDelete = null
                        editSetId = null
                    },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { setToDelete = null }, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Text("Cancelar")
                }
            },
        )
    }

    if (showFinishConfirm) {
        val done = session?.exercises?.sumOf { it.sets.size } ?: 0
        val planned = session?.exercises?.sumOf { it.exercise.plannedSets } ?: 0
        AlertDialog(
            onDismissRequest = { showFinishConfirm = false },
            title = { Text("Finalizar treino?") },
            text = {
                Text(
                    "$done de $planned séries registradas. Finalizar encerra a sessão e " +
                        "salva o treino no aparelho."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFinishConfirm = false
                        onFinish()
                    },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text("Finalizar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishConfirm = false }, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Text("Cancelar")
                }
            },
        )
    }

    if (showAbandonConfirm) {
        AlertDialog(
            onDismissRequest = { showAbandonConfirm = false },
            title = { Text("Abandonar treino?") },
            text = {
                Text(
                    "O treino em andamento e as séries já registradas serão excluídos do " +
                        "aparelho. Essa ação não pode ser desfeita."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAbandonConfirm = false
                        onAbandon()
                    },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text("Abandonar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAbandonConfirm = false }, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Text("Cancelar")
                }
            },
        )
    }
}

/**
 * Faixa fixa de descanso (H3-26): vive fora da lista rolável, então o timer
 * continua visível em qualquer ponto da rolagem. Ao zerar entra em destaque
 * visual e o anúncio para leitor de tela é repetido.
 */
@Composable
private fun RestBar(
    remaining: Int,
    total: Int,
    paused: Boolean,
    onSkip: () -> Unit,
    onExtend: () -> Unit,
    onTogglePause: () -> Unit,
) {
    val announce = timerShouldAnnounce(remaining)
    val finished = remaining <= 0
    val message = when {
        finished -> "Descanso concluído. Bora para a próxima série."
        paused -> "Descanso pausado: ${formatSeconds(remaining)}"
        else -> "Descanso: ${formatSeconds(remaining)}"
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (finished) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp).semantics(mergeDescendants = true) {
                stateDescription = when {
                    finished -> "Descanso concluído"
                    paused -> "Descanso pausado"
                    else -> restStateDescription(remaining, total)
                }
                if (announce) liveRegion = LiveRegionMode.Polite
            },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (!finished) {
                LinearProgressIndicator(
                    progress = { if (total > 0) (total - remaining).toFloat() / total else 0f },
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
                )
                Text(
                    text = "Total de ${formatSeconds(total)}. A próxima série pode começar quando quiser.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text("Pular")
                }
                if (!finished) {
                    OutlinedButton(
                        onClick = onExtend,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text("+15 s")
                    }
                    OutlinedButton(
                        onClick = onTogglePause,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text(if (paused) "Continuar" else "Pausar")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: SessionExerciseWithSets,
    media: ExerciseMedia?,
    onLogSet: () -> Unit,
    onRepeatLastSet: (Int?, Double?, Double?) -> Unit,
    onOpenDetail: () -> Unit,
    onEditSet: (SetLogEntity) -> Unit,
    onNextExercise: () -> Unit,
) {
    val name = exercise.exercise.exerciseName.ifBlank { exercise.exercise.exerciseId }
    // H3-27: progresso derivado das séries gravadas — editar/excluir série
    // reflete aqui sem agregado extra.
    val completed = exerciseIsCompleted(exercise)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Exercício com as séries planejadas cumpridas fica atenuado (H3-27);
            // o botão de ação fica fora do bloco, em contraste pleno.
            Column(
                modifier = Modifier.alpha(if (completed) 0.55f else 1f),
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
                                .minimumInteractiveComponentSize()
                                .semantics(mergeDescendants = true) {
                                    stateDescription = setContentDescription(
                                        set.setNumber,
                                        set.reps,
                                        set.weightKg,
                                        set.rpe,
                                    ) + ", série concluída"
                                }
                                .clickable(
                                    onClickLabel = "Editar série ${set.setNumber}",
                                    role = Role.Button,
                                    onClick = { onEditSet(set) },
                                ),
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
                                        append(formatKg(it))
                                    }
                                    set.rpe?.let {
                                        if (isNotEmpty()) append(" • ")
                                        append("RPE ${formatDecimal(it, 1)}")
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

                // "Repetir última série" em 1 toque (H3-26): grava com carga/reps/RPE
                // da última série e dispara o descanso, sem abrir o diálogo.
                val lastSet = sets.maxByOrNull { it.setNumber }
                if (lastSet != null) {
                    val haptics = LocalHapticFeedback.current
                    OutlinedButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRepeatLastSet(lastSet.reps, lastSet.weightKg, lastSet.rpe)
                        },
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                    ) {
                        val label = lastSetLabel(lastSet)
                        Text(if (label == null) "Repetir última série" else "Repetir última série ($label)")
                    }
                }
            }

            Button(
                onClick = if (completed) onNextExercise else onLogSet,
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
            ) {
                // H3-32: ícone de add na ação principal de registrar série.
                if (!completed) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                // H3-27: cumprido o planejado, o botão avança em vez de empilhar
                // séries extras sem sinalizar.
                Text(
                    if (completed) {
                        "Exercício concluído — próximo"
                    } else {
                        "Registrar série ${exercise.sets.size + 1}"
                    }
                )
            }
        }
    }
}

/** Verdadeiro quando as séries planejadas do exercício já foram registradas. */
private fun exerciseIsCompleted(exercise: SessionExerciseWithSets): Boolean =
    calculateProgress(
        plannedSets = exercise.exercise.plannedSets,
        completedSets = exercise.exercise.completedSets,
        sets = exercise.sets.map { SetRecord(it.setNumber, it.reps, it.weightKg, it.rpe) },
    ).isCompleted

/** Rótulo da última série: "60 kg × 10", "10 reps" ou "60 kg"; `null` sem dados. */
private fun lastSetLabel(set: SetLogEntity): String? = when {
    set.weightKg != null && set.reps != null -> "${formatKg(set.weightKg)} × ${set.reps}"
    set.reps != null -> "${set.reps} reps"
    set.weightKg != null -> formatKg(set.weightKg)
    else -> null
}

@Composable
private fun SetDialog(
    key: String,
    title: String,
    helper: String?,
    suggestion: ProgressionSuggestion?,
    initialReps: String,
    initialWeight: String,
    initialRpe: String,
    confirmLabel: String,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    onConfirm: (Int?, Double?, Double?) -> Unit,
) {
    // H3-31: o que foi digitado sobrevive à rotação (e ao teclado que recria
    // a Activity). `key` evita herdar rascunho de outro diálogo na mesma posição.
    var reps by rememberSaveable(key = key) { mutableStateOf(initialReps) }
    var weight by rememberSaveable(key = key) { mutableStateOf(initialWeight) }
    var rpe by rememberSaveable(key = key) { mutableStateOf(initialRpe) }
    var errors by remember { mutableStateOf(SetInputErrors()) }
    val haptics = LocalHapticFeedback.current
    val suggestionText = suggestionLabel(suggestion)

    // H3-27: valor preenchido e inválido bloqueia o salvamento — nada é
    // gravado com carga/reps/RPE `null` por falha de parse.
    fun submit() {
        when (val result = validateSetInput(repsText = reps, weightText = weight, rpeText = rpe)) {
            is SetInputValidation.Invalid -> errors = result.errors
            is SetInputValidation.Valid -> {
                // Háptico curto ao confirmar o salvamento (H3-26).
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onConfirm(result.input.reps, result.input.weightKg, result.input.rpe)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                helper?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                suggestionText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                OutlinedTextField(
                    value = reps,
                    onValueChange = {
                        reps = it.filter(Char::isDigit)
                        errors = errors.copy(reps = null)
                    },
                    label = { Text("Repetições") },
                    supportingText = {
                        Text(errors.reps ?: if (reps.isBlank()) "Opcional" else "Número inteiro, de 1 a 100")
                    },
                    isError = errors.reps != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { value ->
                        weight = value.filter { it.isDigit() || it == ',' || it == '.' }
                        errors = errors.copy(weight = null)
                    },
                    label = { Text("Carga (kg)") },
                    supportingText = { Text(errors.weight ?: "Opcional. Use vírgula ou ponto.") },
                    isError = errors.weight != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = rpe,
                    onValueChange = { value ->
                        rpe = value.filter { it.isDigit() || it == ',' || it == '.' }
                        errors = errors.copy(rpe = null)
                    },
                    label = { Text("RPE (opcional)") },
                    supportingText = { Text(errors.rpe ?: "Opcional, de 1 a 10.") },
                    isError = errors.rpe != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = ::submit, modifier = Modifier.minimumInteractiveComponentSize()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Excluir série")
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) { Text("Cancelar") }
            }
        },
    )
}

/** Texto de apoio do motor de progressão; `null` quando não há o que sugerir. */
private fun suggestionLabel(suggestion: ProgressionSuggestion?): String? {
    if (suggestion == null || suggestion.action == ProgressionAction.NONE) return null
    val weight = suggestion.suggestedWeightKg?.let { formatKg(it) }
    val reps = if (weight == null) suggestion.suggestedReps?.let { "$it repetições" } else null
    val target = weight ?: reps ?: return null
    return "Sugestão: $target — ${suggestion.reason}"
}

private fun formatSeconds(seconds: Int): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return "${minutes.toString().padStart(2, '0')}:${rest.toString().padStart(2, '0')}"
}
