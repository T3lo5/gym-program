package com.treinos.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.unit.dp
import com.treinos.app.data.local.SET_TYPE_AMRAP
import com.treinos.app.data.local.SET_TYPE_DROP
import com.treinos.app.data.local.SET_TYPE_NORMAL
import com.treinos.app.data.local.SET_TYPE_WARMUP
import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutPlanExerciseEntity
import com.treinos.app.data.media.ExerciseMedia
import com.treinos.app.data.SwapResult
import com.treinos.app.data.SwapScope
import com.treinos.app.data.sessionSwapCandidates
import com.treinos.app.ui.HapticEvent
import com.treinos.app.ui.KeepScreenOnEffect
import com.treinos.app.ui.SetInput
import com.treinos.app.ui.SetRecord
import com.treinos.app.ui.DEFAULT_PLATE_INVENTORY
import com.treinos.app.ui.components.EmptyState
import com.treinos.app.ui.components.ExerciseImage
import com.treinos.app.ui.components.NoteEditDialog
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.components.SetEditDialog
import com.treinos.app.ui.calculateProgress
import com.treinos.app.ui.decorativeContentDescription
import com.treinos.app.ui.equipmentLabel
import com.treinos.app.ui.exerciseContentDescription
import com.treinos.app.ui.formatDateTime
import com.treinos.app.ui.formatDecimal
import com.treinos.app.ui.formatKg
import com.treinos.app.ui.hapticFeedbackForEvent
import com.treinos.app.ui.isPlateLoadedEquipment
import com.treinos.app.ui.muscleLabel
import com.treinos.app.ui.progression.PersonalRecord
import com.treinos.app.ui.progression.ProgressionAction
import com.treinos.app.ui.progression.ProgressionSuggestion
import com.treinos.app.ui.progression.RecordSet
import com.treinos.app.ui.progression.isPersonalRecord
import com.treinos.app.ui.progressStateDescription
import com.treinos.app.ui.restStateDescription
import com.treinos.app.ui.setContentDescription
import com.treinos.app.ui.timerShouldAnnounce
import com.treinos.app.ui.workoutexecution.SupersetGroup
import com.treinos.app.ui.workoutexecution.canJoinSuperset
import com.treinos.app.ui.workoutexecution.supersetGroups
import androidx.compose.ui.res.stringResource
import com.treinos.app.R

@Composable
fun WorkoutScreen(
    session: SessionWithExercises?,
    restRemaining: Int?,
    restTotal: Int?,
    onLeave: () -> Unit,
    onLogSet: (SessionExerciseWithSets, SetInput) -> Unit,
    onSkipRest: () -> Unit,
    onFinish: () -> Unit,
    hasInProgressSession: Boolean,
    restPaused: Boolean = false,
    onPauseRest: () -> Unit = {},
    onResumeRest: () -> Unit = {},
    onExtendRest: () -> Unit = {},
    exerciseMedia: Map<String, ExerciseMedia> = emptyMap(),
    progressionSuggestions: Map<String, ProgressionSuggestion> = emptyMap(),
    personalRecords: Map<String, PersonalRecord> = emptyMap(),
    onOpenExerciseDetail: (String) -> Unit = {},
    onUpdateSet: (SetLogEntity, SetInput) -> Unit = { _, _ -> },
    onDeleteSet: (SetLogEntity) -> Unit = {},
    onAbandon: () -> Unit = {},
    onNextExercise: () -> Unit = {},
    availableEquipment: List<String> = emptyList(),
    onSwapExercise: ((Long, String, SwapScope) -> Unit)? = null,
    onLoadCatalog: () -> Unit = {},
    // H4-6: agrupamento de superset derivado do plano do dia da sessão.
    planExercises: List<WorkoutPlanExerciseEntity> = emptyList(),
    onToggleSuperset: (Long, Boolean) -> Unit = { _, _ -> },
    // H4-11: notas da sessão e do exercício durante o treino. A nota do
    // exercício é do PLANO (reaparece nas próximas sessões do dia).
    onEditSessionNote: (String) -> Unit = {},
    onEditExerciseNote: (SessionExerciseEntity, String) -> Unit = { _, _ -> },
) {
    // H3-31: o alvo do diálogo sobrevive à rotação — guardamos apenas os ids
    // (Bundle-friendly) e rederivamos os objetos da sessão a cada composição.
    var exerciseToLogId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editSetId by rememberSaveable { mutableStateOf<Long?>(null) }
    // H4-1: o selo de PR é por sessão — uma vez batido, fica no card do
    // exercício até o fim da sessão, mesmo que a série seguinte não seja PR.
    var prSessionExercises by remember(session?.session?.id) { mutableStateOf(emptySet<String>()) }
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
    var setSavedAnnouncement by remember { mutableStateOf<String?>(null) }

    // Troca de exercício durante o treino em andamento (H3-41/H3-42).
    var showSwapConfirm by remember { mutableStateOf(false) }
    var swapTargetId by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()

    // H4-11: alvo do diálogo de nota em rememberSaveable (padrão H3-31) —
    // guardamos ids (Bundle-friendly) e rederivamos os objetos da sessão a
    // cada composição; o rascunho do texto usa a key do diálogo.
    var showSessionNote by rememberSaveable { mutableStateOf(false) }
    var exerciseNoteTargetId by rememberSaveable { mutableStateOf<Long?>(null) }
    val exerciseNoteTarget = session?.exercises
        ?.firstOrNull { it.exercise.id == exerciseNoteTargetId }?.exercise

    // H4-6: agrupamento de superset derivado do plano vivo. O cabeçalho do
    // grupo é renderizado DENTRO do card do primeiro membro — nenhum item
    // extra na LazyColumn, então o índice de rolagem do H3-27 se mantém.
    val sortedPlanExercises = remember(planExercises) { planExercises.sortedBy { it.exerciseOrder } }
    val groups = remember(sortedPlanExercises) { supersetGroups(sortedPlanExercises) }
    val supersetMembership = remember(groups) {
        val membership = mutableMapOf<String, Triple<Int, Int, SupersetGroup>>()
        groups.forEachIndexed { groupIndex, group ->
            group.exerciseIds.forEachIndexed { memberIndex, exerciseId ->
                membership.putIfAbsent(exerciseId, Triple(groupIndex, memberIndex, group))
            }
        }
        membership
    }

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
                title = session?.session?.dayName?.ifBlank { stringResource(R.string.screen_workout) }
                    ?: stringResource(R.string.screen_workout),
                onBack = onLeave,
                actions = {
                    if (session != null) {
                        TextButton(
                            onClick = { showAbandonConfirm = true },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Text(
                                text = stringResource(R.string.home_abandon_confirm),
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
                            Text(stringResource(R.string.action_finish))
                        }
                    }
                }
            }
        },
    ) { padding ->
        // Anúncio TalkBack: série salva (dispara quando o texto muda).
        setSavedAnnouncement?.let { announcement ->
            Text(
                text = announcement,
                modifier = Modifier
                    .size(0.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

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
                        text = stringResource(R.string.workout_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            } else {
                EmptyState(
                    title = stringResource(R.string.workout_none_title),
                    description = stringResource(R.string.workout_none_body),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    action = {
                        OutlinedButton(onClick = onLeave) { Text(stringResource(R.string.common_back)) }
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
                                text = stringResource(R.string.workout_started, formatDateTime(current.session.startedAt)),
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
                            // H4-11: nota da sessão — texto atual ou convite
                            // para adicionar; tocar abre o diálogo de nota.
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = { showSessionNote = true }),
                            ) {
                                Text(
                                    text = stringResource(R.string.workout_note_session_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    text = current.session.notes.ifBlank {
                                        stringResource(R.string.workout_note_add)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }

                items(current.exercises.sortedBy { it.exercise.exerciseOrder }, key = { it.exercise.id }) { exercise ->
                    // H4-6: rótulos derivados do grupo (A, A1, A2…); exercício
                    // fora do plano (troca só-na-sessão) fica sem rótulo e sem
                    // botão, e o descanso volta ao normal.
                    val membership = supersetMembership[exercise.exercise.exerciseId]
                    val supersetHeader = membership
                        ?.takeIf { (_, memberIndex, _) -> memberIndex == 0 }
                        ?.let { (groupIndex, _, _) ->
                            stringResource(R.string.workout_superset_header, supersetGroupLetter(groupIndex))
                        }
                    val supersetLabel = membership
                        ?.let { (groupIndex, memberIndex, _) ->
                            stringResource(
                                R.string.workout_superset_label,
                                supersetGroupLetter(groupIndex) + (memberIndex + 1),
                            )
                        }
                    ExerciseCard(
                        exercise = exercise,
                        media = exerciseMedia[exercise.exercise.exerciseId],
                        onLogSet = { exerciseToLogId = exercise.exercise.id },
                        onRepeatLastSet = { reps, weight, rpe ->
                            // H4-4: repetir registra série de trabalho (normal) —
                            // a última série pode ser aquecimento.
                            onLogSet(exercise, SetInput(reps = reps, weightKg = weight, rpe = rpe))
                        },
                        repeatHaptic = { reps, weight, rpe ->
                            // H4-1: repetir a última série também avalia PR.
                            val candidate = RecordSet(
                                sessionId = session?.session?.id ?: 0L,
                                sessionStartedAt = session?.session?.startedAt ?: 0L,
                                reps = reps,
                                weightKg = weight,
                                rpe = rpe,
                            )
                            if (isPersonalRecord(candidate, personalRecords[exercise.exercise.exerciseId])) {
                                prSessionExercises = prSessionExercises + exercise.exercise.exerciseId
                                HapticEvent.PersonalRecord
                            } else {
                                HapticEvent.RepeatLastSet
                            }
                        },
                        showPrSeal = exercise.exercise.exerciseId in prSessionExercises,
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
                        onSwapExercise = {
                            swapTargetId = exercise.exercise.id
                            showSwapConfirm = true
                        },
                        supersetHeader = supersetHeader,
                        supersetLabel = supersetLabel,
                        canToggleSuperset = canJoinSuperset(sortedPlanExercises, exercise.exercise.exerciseId),
                        isSuperset = membership != null,
                        onToggleSuperset = { enabled -> onToggleSuperset(exercise.exercise.id, enabled) },
                        // H4-11: nota do exercício no plano, casada por
                        // `exerciseOrder` (a troca THIS_SESSION preserva a ordem).
                        exerciseNote = sortedPlanExercises
                            .firstOrNull { it.exerciseOrder == exercise.exercise.exerciseOrder }
                            ?.notes.orEmpty(),
                        onEditNote = { exerciseNoteTargetId = exercise.exercise.id },
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.workout_footer),
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
        // H3-52: os templates do anúncio são resolvidos na composição — o
        // callback roda fora dela e não pode chamar `stringResource`.
        val savedDetailTemplate = stringResource(R.string.workout_set_saved_detail)
        val savedTemplate = stringResource(R.string.workout_set_saved)
        val prSavedTemplate = stringResource(R.string.workout_set_saved_pr)
        val prSavedDetailTemplate = stringResource(R.string.workout_set_saved_pr_detail)
        val repsTemplate = stringResource(R.string.common_reps_short)
        SetEditDialog(
            // H3-31: chave por diálogo — reabrir para outra série não herda o
            // rascunho da anterior, e girar não perde o que foi digitado.
            key = "log-${exercise.exercise.id}-${exercise.sets.size + 1}",
            title = stringResource(
                R.string.workout_set_dialog_title,
                exercise.sets.size + 1,
                exercise.exercise.exerciseName,
            ),
            helper = stringResource(R.string.workout_set_dialog_helper, exercise.exercise.plannedSets),
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
            confirmLabel = stringResource(R.string.workout_set_dialog_save),
            onDelete = null,
            onDismiss = { exerciseToLogId = null },
            confirmHaptic = { input ->
                // H4-1: PR substitui o SetSaved na mesma confirmação (um háptico).
                val candidate = RecordSet(
                    sessionId = session?.session?.id ?: 0L,
                    sessionStartedAt = session?.session?.startedAt ?: 0L,
                    reps = input.reps,
                    weightKg = input.weightKg,
                    rpe = input.rpe,
                )
                if (isPersonalRecord(candidate, personalRecords[exercise.exercise.exerciseId])) {
                    prSessionExercises = prSessionExercises + exercise.exercise.exerciseId
                    HapticEvent.PersonalRecord
                } else {
                    HapticEvent.SetSaved
                }
            },
            onConfirm = { input ->
                val nextSetNumber = exercise.sets.size + 1
                val partes = mutableListOf<String>()
                input.reps?.let { partes.add(String.format(repsTemplate, it)) }
                input.weightKg?.let { partes.add(formatKg(it)) }
                val detalhe = partes.joinToString(" • ")
                val candidate = RecordSet(
                    sessionId = session?.session?.id ?: 0L,
                    sessionStartedAt = session?.session?.startedAt ?: 0L,
                    reps = input.reps,
                    weightKg = input.weightKg,
                    rpe = input.rpe,
                )
                val bateuPr = isPersonalRecord(candidate, personalRecords[exercise.exercise.exerciseId])
                setSavedAnnouncement =
                    when {
                        bateuPr && detalhe.isNotBlank() ->
                            String.format(prSavedDetailTemplate, nextSetNumber, detalhe)
                        bateuPr -> String.format(prSavedTemplate, nextSetNumber)
                        detalhe.isNotBlank() -> String.format(savedDetailTemplate, nextSetNumber, detalhe)
                        else -> String.format(savedTemplate, nextSetNumber)
                    }
                onLogSet(exercise, input)
                exerciseToLogId = null
            },
            // H4-5: atalho de anilhas só para equipamento carregado com anilhas.
            plateInventory = if (
                isPlateLoadedEquipment(exerciseMedia[exercise.exercise.exerciseId]?.equipment)
            ) {
                DEFAULT_PLATE_INVENTORY
            } else {
                null
            },
        )
    }

    editTarget?.let { (exercise, set) ->
        SetEditDialog(
            key = "edit-${set.id}",
            title = stringResource(R.string.workout_set_edit_title, set.setNumber, exercise.exercise.exerciseName),
            helper = stringResource(R.string.workout_set_dialog_edit_helper),
            suggestion = null,
            initialReps = set.reps?.toString().orEmpty(),
            initialWeight = set.weightKg?.let { formatDecimal(it, 1) }.orEmpty(),
            initialRpe = set.rpe?.let { formatDecimal(it, 1) }.orEmpty(),
            initialSetType = set.setType,
            initialRir = set.rir?.toString().orEmpty(),
            confirmLabel = stringResource(R.string.workout_set_dialog_save_changes),
            onDelete = { setToDelete = set },
            onDismiss = { editSetId = null },
            onConfirm = { input ->
                onUpdateSet(set, input)
                editSetId = null
            },
            // H4-5: o diálogo é o mesmo no treino e no Histórico; o atalho
            // aparece sempre que o inventário é passado, sem mudar o salvamento.
            plateInventory = if (
                isPlateLoadedEquipment(exerciseMedia[exercise.exercise.exerciseId]?.equipment)
            ) {
                DEFAULT_PLATE_INVENTORY
            } else {
                null
            },
        )
    }

    setToDelete?.let { set ->
        AlertDialog(
            onDismissRequest = { setToDelete = null },
            title = { Text(stringResource(R.string.workout_set_delete_title)) },
            text = {
                Text(
                    stringResource(R.string.workout_set_delete_body, set.setNumber)
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
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { setToDelete = null }, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showFinishConfirm) {
        val done = session?.exercises?.sumOf { it.sets.size } ?: 0
        val planned = session?.exercises?.sumOf { it.exercise.plannedSets } ?: 0
        val haptics = LocalHapticFeedback.current
        AlertDialog(
            onDismissRequest = { showFinishConfirm = false },
            title = { Text(stringResource(R.string.workout_finish_title)) },
            text = {
                Text(
                    stringResource(R.string.workout_finish_body, done, planned)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.performHapticFeedback(hapticFeedbackForEvent(HapticEvent.WorkoutFinished))
                        showFinishConfirm = false
                        onFinish()
                    },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text(stringResource(R.string.workout_finish_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishConfirm = false }, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showAbandonConfirm) {
        val haptics = LocalHapticFeedback.current
        AlertDialog(
            onDismissRequest = { showAbandonConfirm = false },
            title = { Text(stringResource(R.string.home_abandon_title)) },
            text = {
                Text(
                    stringResource(R.string.home_abandon_body)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.performHapticFeedback(hapticFeedbackForEvent(HapticEvent.WorkoutAbandoned))
                        showAbandonConfirm = false
                        onAbandon()
                    },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text(stringResource(R.string.home_abandon_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAbandonConfirm = false }, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    // H3-44/H3-43: troca de exercício durante o treino em andamento.
    if (showSwapConfirm && swapTargetId != null && session != null) {
        val exerciseEntity = session.exercises.firstOrNull { it.exercise.id == swapTargetId }
        val takenExerciseIds = session.exercises.map { it.exercise.exerciseId }.toList()
        // H3-44: catálogo local (cache/asset) já deve estar pronto. Vazio aqui
        // significa "não tenho substitutos", não "ainda estou baixando": nada de
        // spinner nem de disparar rede ao abrir o diálogo.
        val catalogEmpty = exerciseMedia.isEmpty()
        val candidates = exerciseEntity?.let {
            sessionSwapCandidates(
                sessionExercise = it.exercise,
                takenExerciseIds = takenExerciseIds,
                catalog = exerciseMedia,
                availableEquipment = availableEquipment,
            )
        } ?: emptyList()
        val currentName = exerciseEntity?.exercise?.exerciseName
            ?.takeIf { it.isNotBlank() }
            ?: swapTargetId.toString()
        SwapExerciseSheet(
            currentExerciseName = currentName,
            candidates = candidates,
            catalogEmpty = catalogEmpty,
            onDismiss = { showSwapConfirm = false },
            onConfirm = { replacementId, scope ->
                showSwapConfirm = false
                onSwapExercise?.invoke(swapTargetId!!, replacementId, scope)
            },
            onRefreshCatalog = onLoadCatalog,
        )
    }

    // H4-11: diálogo da nota da sessão. Cancelar/fechar por fora não grava.
    if (showSessionNote && session != null) {
        NoteEditDialog(
            key = "session-note-${session.session.id}",
            title = stringResource(R.string.workout_note_session_title),
            initialNote = session.session.notes,
            onDismiss = { showSessionNote = false },
            onConfirm = { note ->
                showSessionNote = false
                onEditSessionNote(note)
            },
        )
    }

    // H4-11: diálogo da nota do exercício — alvo rederivado por id (H3-31) e
    // nota inicial lida do slot do plano de mesmo `exerciseOrder`.
    exerciseNoteTarget?.let { exercise ->
        NoteEditDialog(
            key = "exercise-note-${exercise.id}",
            title = stringResource(R.string.workout_note_exercise_title),
            initialNote = sortedPlanExercises
                .firstOrNull { it.exerciseOrder == exercise.exerciseOrder }
                ?.notes.orEmpty(),
            onDismiss = { exerciseNoteTargetId = null },
            onConfirm = { note ->
                exerciseNoteTargetId = null
                onEditExerciseNote(exercise, note)
            },
        )
    }
}

/**
 * Seleção do substituto de exercício (H3-44/H3-43).
 *
 * Bottom sheet com busca, cartões selecionáveis, escopo explícito e confirmação
 * única — no lugar do antigo AlertDialog com um par de botões por linha.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwapExerciseSheet(
    currentExerciseName: String,
    candidates: List<ExerciseMedia>,
    catalogEmpty: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, SwapScope) -> Unit,
    onRefreshCatalog: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var search by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var scope by remember { mutableStateOf(SwapScope.THIS_SESSION) }

    val filtered = if (search.isBlank()) {
        candidates
    } else {
        candidates.filter { it.name.contains(search, ignoreCase = true) }
    }
    val selected = filtered.firstOrNull { it.id == selectedId }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .testTag("swap_sheet_list")
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.workout_swap_exercise),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stringResource(R.string.workout_swap_replace_label, currentExerciseName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
                    }
                }
            }

            if (catalogEmpty) {
                item {
                    Text(
                        text = stringResource(R.string.workout_swap_catalog_offline),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.workout_swap_catalog_update_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    Button(
                        onClick = onRefreshCatalog,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_btn_update_catalog))
                    }
                }
            } else if (candidates.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.workout_swap_none_same_muscle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                item {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.workout_swap_search_label)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.workout_swap_count, filtered.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(filtered, key = { it.id }) { candidate ->
                    val isSelected = candidate.id == selectedId
                    val muscles = candidate.primaryMuscles
                        .joinToString(" • ") { muscleLabel(it) }
                    val equipment = candidate.equipment
                        .takeIf { it.isNotBlank() }
                        ?.let { " • ${equipmentLabel(it)}" }
                        .orEmpty()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { selectedId = candidate.id },
                            )
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                MaterialTheme.shapes.medium,
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = MaterialTheme.shapes.medium,
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                        ) {
                            Text(
                                text = candidate.name,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = "$muscles$equipment",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.workout_swap_search_empty, search),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                item {
                    Text(
                        text = stringResource(R.string.workout_swap_scope_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = scope == SwapScope.THIS_SESSION,
                            onClick = { scope = SwapScope.THIS_SESSION },
                            label = { Text(stringResource(R.string.workout_swap_scope_session)) },
                        )
                        FilterChip(
                            selected = scope == SwapScope.ALL_FUTURE,
                            onClick = { scope = SwapScope.ALL_FUTURE },
                            label = { Text(stringResource(R.string.workout_swap_scope_all)) },
                        )
                    }
                }
                item {
                    Text(
                        text = if (scope == SwapScope.THIS_SESSION) {
                            stringResource(R.string.workout_swap_scope_session_desc)
                        } else {
                            stringResource(R.string.workout_swap_scope_all_desc)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                    Button(
                        onClick = { selected?.let { onConfirm(it.id, scope) } },
                        enabled = selected != null,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(stringResource(R.string.workout_swap_confirm))
                    }
                }
            }
        }
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
    val restStarted = remaining == total && remaining > 0 && !paused && !finished
    var restStartedAnnouncement by remember { mutableStateOf<String?>(null) }
    // H3-52: o template do anúncio é resolvido na composição; o efeito roda
    // fora dela e formata com o template já traduzido.
    val restStartedTemplate = stringResource(R.string.workout_rest_started_announcement)
    val restStateDone = stringResource(R.string.workout_rest_state_done)
    val restStatePaused = stringResource(R.string.workout_rest_state_paused)
    LaunchedEffect(remaining, total, paused, finished) {
        if (restStarted) {
            restStartedAnnouncement = String.format(restStartedTemplate, formatSeconds(remaining))
        } else {
            restStartedAnnouncement = null
        }
    }
    val message = when {
        finished -> stringResource(R.string.workout_rest_finished)
        paused -> stringResource(R.string.workout_rest_paused, formatSeconds(remaining))
        else -> stringResource(R.string.workout_rest_running, formatSeconds(remaining))
    }
    val haptics = LocalHapticFeedback.current
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
                    finished -> restStateDone
                    paused -> restStatePaused
                    else -> restStateDescription(remaining, total)
                }
                if (announce) liveRegion = LiveRegionMode.Polite
            },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            restStartedAnnouncement?.let {
                Text(
                    text = it,
                    modifier = Modifier
                        .size(0.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
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
                    text = stringResource(R.string.workout_rest_total, formatSeconds(total)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        haptics.performHapticFeedback(hapticFeedbackForEvent(HapticEvent.RestAdjusted))
                        onSkip()
                    },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text(stringResource(R.string.common_skip))
                }
                if (!finished) {
                    OutlinedButton(
                        onClick = {
                            haptics.performHapticFeedback(hapticFeedbackForEvent(HapticEvent.RestAdjusted))
                            onExtend()
                        },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text(stringResource(R.string.workout_rest_extend))
                    }
                    OutlinedButton(
                        onClick = {
                            haptics.performHapticFeedback(hapticFeedbackForEvent(HapticEvent.RestAdjusted))
                            onTogglePause()
                        },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text(if (paused) stringResource(R.string.workout_rest_resume) else stringResource(R.string.workout_rest_pause))
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
    repeatHaptic: (Int?, Double?, Double?) -> HapticEvent = { _, _, _ -> HapticEvent.RepeatLastSet },
    showPrSeal: Boolean = false,
    onOpenDetail: () -> Unit,
    onEditSet: (SetLogEntity) -> Unit,
    onNextExercise: () -> Unit,
    onSwapExercise: (() -> Unit)? = null,
    // H4-6: rótulos do superset e botão de alternância com o anterior.
    supersetHeader: String? = null,
    supersetLabel: String? = null,
    canToggleSuperset: Boolean = false,
    isSuperset: Boolean = false,
    onToggleSuperset: (Boolean) -> Unit = {},
    // H4-11: nota do exercício no plano — texto atual ou convite; tocar abre
    // o diálogo. A nota vive no plano e reaparece nas próximas sessões do dia.
    exerciseNote: String = "",
    onEditNote: () -> Unit = {},
) {
    val name = exercise.exercise.exerciseName.ifBlank { exercise.exercise.exerciseId }
    // H3-27: progresso derivado das séries gravadas — editar/excluir série
    // reflete aqui sem agregado extra.
    val completed = exerciseIsCompleted(exercise)
    val haptics = LocalHapticFeedback.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // H4-6: cabeçalho do grupo dentro do card do primeiro membro.
            supersetHeader?.let { header ->
                Text(
                    text = header,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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
                        .clickable(
                            onClickLabel = stringResource(R.string.a11y_open_exercise_details),
                            onClick = onOpenDetail,
                        ),
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
                        // H4-6: rótulo do membro na ordem de execução (A1, A2…).
                        supersetLabel?.let { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = stringResource(R.string.workout_planned_sets, exercise.exercise.plannedSets, exercise.exercise.restSeconds),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (showPrSeal) {
                            // H4-1: selo do recorde batido nesta sessão.
                            Text(
                                text = stringResource(R.string.workout_pr_seal),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                val sets = exercise.sets.sortedBy { it.setNumber }
                if (sets.isEmpty()) {
                    Text(
                        text = stringResource(R.string.workout_no_sets),
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
                                    onClickLabel = stringResource(R.string.workout_set_edit_label, set.setNumber),
                                    role = Role.Button,
                                    onClick = { onEditSet(set) },
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.workout_set, set.setNumber),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = setDetailText(set),
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
                    OutlinedButton(
                        onClick = {
                            // H4-1: o evento é decidido antes de gravar — PR
                            // substitui o RepeatLastSet na mesma confirmação.
                            val event = repeatHaptic(lastSet.reps, lastSet.weightKg, lastSet.rpe)
                            haptics.performHapticFeedback(hapticFeedbackForEvent(event))
                            onRepeatLastSet(lastSet.reps, lastSet.weightKg, lastSet.rpe)
                        },
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                    ) {
                        val label = lastSetLabel(lastSet, stringResource(R.string.common_reps_short))
                        Text(if (label == null) stringResource(R.string.workout_repeat_last) else stringResource(R.string.workout_repeat_last_with_label, label))
                    }
                }
            }

            Button(
                onClick = if (completed) {
                    {
                        haptics.performHapticFeedback(hapticFeedbackForEvent(HapticEvent.ExerciseAdvanced))
                        onNextExercise()
                    }
                } else onLogSet,
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
                        stringResource(R.string.workout_exercise_done)
                    } else {
                        stringResource(R.string.workout_log_set, exercise.sets.size + 1)
                    }
                )
            }

            // Troca de exercício durante o treino em andamento (H3-41/H3-42).
            if (onSwapExercise != null) {
                OutlinedButton(
                    onClick = onSwapExercise,
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().padding(top = 4.dp),
                ) {
                    Text(stringResource(R.string.workout_swap_exercise))
                }
            }

            // H4-6: alternar o superset com o exercício anterior. O botão
            // mostra o estado atual e pede o inverso; gravar o estado já
            // vigente é no-op. Não aparece no primeiro exercício do dia.
            if (canToggleSuperset) {
                OutlinedButton(
                    onClick = { onToggleSuperset(!isSuperset) },
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().padding(top = 4.dp),
                ) {
                    Text(
                        stringResource(
                            if (isSuperset) {
                                R.string.workout_superset_toggle_off
                            } else {
                                R.string.workout_superset_toggle_on
                            },
                        ),
                    )
                }
                Text(
                    text = stringResource(R.string.workout_superset_toggle_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // H4-11: nota do exercício no plano — texto atual ou convite;
            // tocar abre o diálogo de nota.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEditNote),
            ) {
                Text(
                    text = stringResource(R.string.workout_note_exercise_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = exerciseNote.ifBlank { stringResource(R.string.workout_note_exercise_add) },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** H4-6: letra do grupo derivada do índice (A, B, …); além de Z usa G+n. */
private fun supersetGroupLetter(groupIndex: Int): String =
    if (groupIndex < 26) ('A' + groupIndex).toString() else "G${groupIndex + 1}"

@Composable
private fun setDetailText(set: SetLogEntity): String {
    val typePart = setTypeLabel(set.setType)
    val repsPart = set.reps?.let { stringResource(R.string.workout_reps, it) }
    val weightPart = set.weightKg?.let { formatKg(it) }
    val rpePart = set.rpe?.let { "RPE ${formatDecimal(it, 1)}" }
    val rirPart = set.rir?.let { "RIR $it" }
    return buildString {
        typePart?.let { append(it) }
        repsPart?.let { if (isNotEmpty()) append(" • "); append(it) }
        weightPart?.let { if (isNotEmpty()) append(" • "); append(it) }
        rpePart?.let { if (isNotEmpty()) append(" • "); append(it) }
        rirPart?.let { if (isNotEmpty()) append(" • "); append(it) }
        if (repsPart == null && weightPart == null && rpePart == null && rirPart == null) {
            append(stringResource(R.string.workout_set_recorded))
        }
    }
}

/** H4-4: rótulo do tipo; `null` para série normal (sem selo). */
@Composable
private fun setTypeLabel(setType: String): String? = when (setType) {
    SET_TYPE_WARMUP -> stringResource(R.string.workout_set_type_warmup)
    SET_TYPE_DROP -> stringResource(R.string.workout_set_type_drop)
    SET_TYPE_AMRAP -> stringResource(R.string.workout_set_type_amrap)
    else -> null
}

/** Verdadeiro quando as séries planejadas do exercício já foram registradas. */
private fun exerciseIsCompleted(exercise: SessionExerciseWithSets): Boolean =
    calculateProgress(
        plannedSets = exercise.exercise.plannedSets,
        completedSets = exercise.exercise.completedSets,
        sets = exercise.sets.map { SetRecord(it.setNumber, it.reps, it.weightKg, it.rpe) },
    ).isCompleted

/** Rótulo da última série: "60 kg × 10", "10 reps" ou "60 kg"; `null` sem dados. */
private fun lastSetLabel(set: SetLogEntity, repsTemplate: String): String? = when {
    set.weightKg != null && set.reps != null -> "${formatKg(set.weightKg)} × ${set.reps}"
    set.reps != null -> String.format(repsTemplate, set.reps)
    set.weightKg != null -> formatKg(set.weightKg)
    else -> null
}

private fun formatSeconds(seconds: Int): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return "${minutes.toString().padStart(2, '0')}:${rest.toString().padStart(2, '0')}"
}
