package com.treinos.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.treinos.app.data.prefs.AppPreferences
import com.treinos.app.ui.ActivityLogDraft
import com.treinos.app.ui.ActivityLogDraftCodec
import com.treinos.app.ui.ActivityLogField
import com.treinos.app.ui.ActivityLogInput
import com.treinos.app.ui.ActivityModality
import com.treinos.app.ui.components.BusyButton
import com.treinos.app.ui.components.ChipGroup
import com.treinos.app.ui.components.ConfirmationDialog
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.components.SectionTitle
import com.treinos.app.ui.formatDateTime
import com.treinos.app.ui.swimStrokeOptions
import com.treinos.app.ui.validPoolLengths
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.FlowPreview
import com.treinos.app.R

/**
 * H3-52: rótulos localizados para os dados que o app persiste com valor de
 * storage (`ActivityModality.label` e os nomes de nado continuam como estão —
 * são dados gravados no banco, não literais de UI). A UI resolve o rótulo no
 * idioma escolhido a partir do valor de storage.
 */
@Composable
internal fun activityModalityLabel(modality: ActivityModality): String = stringResource(
    when (modality) {
        ActivityModality.STRENGTH -> R.string.generate_modality_strength
        ActivityModality.RUN -> R.string.generate_modality_run
        ActivityModality.SWIM -> R.string.activity_modality_swim
        ActivityModality.MOBILITY -> R.string.generate_modality_mobility
        ActivityModality.OTHER -> R.string.activity_modality_other
    },
)

/** Rótulo localizado do nado; valor desconhecido aparece como veio do registro. */
@Composable
internal fun strokeDisplayName(stroke: String): String = when (stroke) {
    "crawl" -> stringResource(R.string.log_stroke_crawl)
    "costas" -> stringResource(R.string.log_stroke_backstroke)
    "peito" -> stringResource(R.string.log_stroke_breaststroke)
    "borboleta" -> stringResource(R.string.log_stroke_butterfly)
    "misto" -> stringResource(R.string.log_stroke_medley)
    "outro" -> stringResource(R.string.log_stroke_other)
    else -> stroke
}

/**
 * H3-31: rascunho do registro avulso como um único valor saveable — girar o
 * aparelho (ou o teclado recriar a Activity) não perde o que foi digitado.
 * O `Saver` é coberto por round-trip em `ActivityLogSaverTest`.
 */
val ActivityLogDraftSaver: Saver<ActivityLogDraft, Any> = listSaver(
    save = { draft ->
        listOf(
            draft.modality.storageValue,
            draft.title,
            draft.dateText,
            draft.durationText,
            draft.distanceText,
            draft.poolLengthMeters,
            draft.stroke,
            draft.rpeText,
            draft.notes,
        )
    },
    restore = { list ->
        ActivityLogDraft(
            modality = ActivityModality.fromStorageValue(list[0] as? String),
            title = list[1] as String,
            dateText = list[2] as String,
            durationText = list[3] as String,
            distanceText = list[4] as String,
            poolLengthMeters = list[5] as Int?,
            stroke = list[6] as String?,
            rpeText = list[7] as String,
            notes = list[8] as String,
        )
    },
)

/**
 * Estado inicial do formulário: prefill (edição/copiar último) vence sobre o
 * rascunho persistido (H3-56). Sem prefill, tenta o rascunho salvo em
 * `AppPreferences.activityDraftV1`; se ilegível, cai no vazio com data de hoje.
 */
internal fun activityLogDraftFrom(
    prefill: ActivityLogInput?,
    persistedDraft: ActivityLogDraft?,
): ActivityLogDraft {
    val input = prefill
    if (input != null) {
        return ActivityLogDraft(
            modality = input.modality,
            title = input.title,
            dateText = formatDateTime(input.startedAt),
            durationText = input.durationMinutes.toString(),
            distanceText = input.distanceMeters?.toLong()?.toString() ?: "",
            poolLengthMeters = input.poolLengthMeters,
            stroke = input.stroke,
            rpeText = input.rpe?.toString() ?: "",
            notes = input.notes,
        )
    }
    return persistedDraft ?: ActivityLogDraft(dateText = formatDateTime(System.currentTimeMillis()))
}

@Composable
fun LogActivityScreen(
    prefill: ActivityLogInput?,
    editingSessionId: Long?,
    isSaving: Boolean,
    errors: Map<ActivityLogField, String>,
    canCopyLast: Boolean,
    onBack: () -> Unit,
    onModalityChanged: (ActivityModality) -> Unit,
    onCopyLast: (ActivityModality) -> Unit,
    onSave: (ActivityLogDraft) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember(context) { AppPreferences(context) }

    // H3-31: um único rascunho saveable; mudar o prefill (edição/copiar último)
    // reinicia o formulário, girar a tela preserva tudo que foi digitado.
    // H3-56: sem prefill, o rascunho persistido em `AppPreferences` sobrevive à
    // morte do processo; `prefill` (editar/copiar) vence sobre o rascunho.
    val persistedDraft = remember(preferences) {
        ActivityLogDraftCodec.decode(preferences.activityDraftV1)
    }
    var draft by rememberSaveable(
        inputs = arrayOf(prefill),
        stateSaver = ActivityLogDraftSaver,
    ) { mutableStateOf(activityLogDraftFrom(prefill, persistedDraft)) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    // Persiste o rascunho a cada mudança (debounce 300 ms) — sobrevive à morte
    // do processo. A rotação já é coberta pelo `ActivityLogDraftSaver`.
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    LaunchedEffect(draft, preferences) {
        snapshotFlow { draft }
            .debounce(300L)
            .collect { current ->
                preferences.activityDraftV1 = ActivityLogDraftCodec.encode(current)
            }
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = if (editingSessionId != null) {
                    stringResource(R.string.log_edit_title)
                } else {
                    stringResource(R.string.screen_log_activity)
                },
                onBack = onBack,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SectionTitle(stringResource(R.string.log_section_modality)) }
            item {
                ChipGroup(
                    options = ActivityModality.entries.map { it.storageValue to activityModalityLabel(it) },
                    selected = setOf(draft.modality.storageValue),
                    onToggle = { value ->
                        ActivityModality.entries.firstOrNull { it.storageValue == value }?.let {
                            draft = draft.copy(modality = it)
                            onModalityChanged(it)
                        }
                    },
                )
            }
            if (canCopyLast) {
                item {
                    OutlinedButton(
                        onClick = { onCopyLast(draft.modality) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.log_copy_last, activityModalityLabel(draft.modality).lowercase()))
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.log_section_how)) }
            item {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it) },
                    label = { Text(stringResource(R.string.log_title_label)) },
                    supportingText = errors[ActivityLogField.Title]?.let { { Text(it) } },
                    isError = errors.containsKey(ActivityLogField.Title),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = draft.dateText,
                    onValueChange = { draft = draft.copy(dateText = it) },
                    label = { Text(stringResource(R.string.log_date_label)) },
                    supportingText = errors[ActivityLogField.Date]?.let { { Text(it) } },
                    isError = errors.containsKey(ActivityLogField.Date),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = draft.durationText,
                        onValueChange = { draft = draft.copy(durationText = it) },
                        label = { Text(stringResource(R.string.log_duration_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = errors[ActivityLogField.Duration]?.let { { Text(it) } },
                        isError = errors.containsKey(ActivityLogField.Duration),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = draft.rpeText,
                        onValueChange = { draft = draft.copy(rpeText = it) },
                        label = { Text(stringResource(R.string.log_rpe_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = errors[ActivityLogField.Rpe]?.let { { Text(it) } },
                        isError = errors.containsKey(ActivityLogField.Rpe),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.log_rpe_support),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = draft.distanceText,
                    onValueChange = { draft = draft.copy(distanceText = it) },
                    label = { Text(stringResource(R.string.log_distance_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = errors[ActivityLogField.Distance]?.let { { Text(it) } },
                    isError = errors.containsKey(ActivityLogField.Distance),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (draft.modality == ActivityModality.SWIM) {
                item { SectionTitle(stringResource(R.string.log_pool_section)) }
                item {
                    ChipGroup(
                        options = validPoolLengths.map { it.toString() to stringResource(R.string.log_pool_length, it) } +
                            ("" to stringResource(R.string.log_pool_none)),
                        selected = setOf(draft.poolLengthMeters?.toString() ?: ""),
                        onToggle = { value -> draft = draft.copy(poolLengthMeters = value.toIntOrNull()) },
                    )
                }
                item { SectionTitle(stringResource(R.string.log_stroke_section)) }
                item {
                    ChipGroup(
                        options = swimStrokeOptions.map { (value, _) -> value to strokeDisplayName(value) },
                        selected = setOf(draft.stroke ?: ""),
                        onToggle = { value ->
                            draft = draft.copy(stroke = if (value == draft.stroke) null else value)
                        },
                    )
                }
            }

            item { SectionTitle(stringResource(R.string.log_notes_section)) }
            item {
                OutlinedTextField(
                    value = draft.notes,
                    onValueChange = { draft = draft.copy(notes = it) },
                    label = { Text(stringResource(R.string.log_notes_placeholder)) },
                    supportingText = errors[ActivityLogField.Notes]?.let { { Text(it) } },
                    isError = errors.containsKey(ActivityLogField.Notes),
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                BusyButton(
                    busy = isSaving,
                    idleText = if (editingSessionId != null) {
                        stringResource(R.string.workout_set_dialog_save_changes)
                    } else {
                        stringResource(R.string.common_save)
                    },
                    busyText = stringResource(R.string.log_btn_saving),
                    onClick = {
                        onSave(
                            draft.copy(
                                poolLengthMeters = if (draft.modality == ActivityModality.SWIM) draft.poolLengthMeters else null,
                                stroke = if (draft.modality == ActivityModality.SWIM) draft.stroke else null,
                            )
                        )
                    },
                    enabled = errors.isEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
            }

            if (editingSessionId != null) {
                item {
                    TextButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.log_delete_record),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.log_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmationDialog(
            visible = true,
            onDismiss = { showDeleteDialog = false },
            onConfirm = onDelete,
            title = stringResource(R.string.history_activity_delete_title),
            message = stringResource(R.string.log_activity_delete_body),
            confirmLabel = stringResource(R.string.common_delete),
            isDestructive = true,
        )
    }
}
