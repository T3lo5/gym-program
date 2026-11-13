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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.treinos.app.ui.ActivityLogDraft
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
import androidx.compose.ui.res.stringResource
import com.treinos.app.R

private val modalityOptions = ActivityModality.entries.map { it.storageValue to it.label }

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

/** Estado inicial do formulário: preenchido quando há prefill (edição/copiar). */
internal fun activityLogDraftFrom(prefill: ActivityLogInput?): ActivityLogDraft {
    val input = prefill ?: return ActivityLogDraft(
        dateText = formatDateTime(System.currentTimeMillis()),
    )
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
    // H3-31: um único rascunho saveable; mudar o prefill (edição/copiar último)
    // reinicia o formulário, girar a tela preserva tudo que foi digitado.
    var draft by rememberSaveable(
        inputs = arrayOf(prefill),
        stateSaver = ActivityLogDraftSaver,
    ) { mutableStateOf(activityLogDraftFrom(prefill)) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = if (editingSessionId != null) "Editar registro" else "Registrar atividade",
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
            item { SectionTitle("Modalidade") }
            item {
                ChipGroup(
                    options = modalityOptions,
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
                        Text(stringResource(R.string.log_copy_last, draft.modality.label.lowercase()))
                    }
                }
            }

            item { SectionTitle("Como foi") }
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
                    text = "Esforço de 1 (leve) a 10 (máximo). Deixe vazio se não souber informar.",
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
                item { SectionTitle("Piscina") }
                item {
                    ChipGroup(
                        options = validPoolLengths.map { it.toString() to "${it} m" } + ("" to "Sem piscina"),
                        selected = setOf(draft.poolLengthMeters?.toString() ?: ""),
                        onToggle = { value -> draft = draft.copy(poolLengthMeters = value.toIntOrNull()) },
                    )
                }
                item { SectionTitle("Nado (opcional)") }
                item {
                    ChipGroup(
                        options = swimStrokeOptions,
                        selected = setOf(draft.stroke ?: ""),
                        onToggle = { value ->
                            draft = draft.copy(stroke = if (value == draft.stroke) null else value)
                        },
                    )
                }
            }

            item { SectionTitle("Notas") }
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
                    idleText = if (editingSessionId != null) "Salvar alterações" else "Salvar",
                    busyText = "Salvando…",
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
                            "Excluir este registro",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            item {
                Text(
                    text = "O registro fica salvo apenas neste aparelho. Ele aparece no histórico com " +
                        "duração, distância e pace, mas sem séries.",
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
