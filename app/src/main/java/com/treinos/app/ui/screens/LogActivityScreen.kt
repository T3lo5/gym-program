package com.treinos.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.treinos.app.ui.ActivityLogDraft
import com.treinos.app.ui.ActivityLogField
import com.treinos.app.ui.ActivityLogInput
import com.treinos.app.ui.ActivityModality
import com.treinos.app.ui.components.ChipGroup
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.components.SectionTitle
import com.treinos.app.ui.formatDateTime
import com.treinos.app.ui.swimStrokeOptions
import com.treinos.app.ui.validPoolLengths

private val modalityOptions = ActivityModality.entries.map { it.storageValue to it.label }

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
    var modality by remember(prefill) { mutableStateOf(prefill?.modality ?: ActivityModality.SWIM) }
    var title by remember(prefill) { mutableStateOf(prefill?.title ?: "") }
    var dateText by remember(prefill) {
        mutableStateOf(formatDateTime(prefill?.startedAt ?: System.currentTimeMillis()))
    }
    var durationText by remember(prefill) { mutableStateOf((prefill?.durationMinutes ?: 45).toString()) }
    var distanceText by remember(prefill) {
        mutableStateOf(prefill?.distanceMeters?.toLong()?.toString() ?: "")
    }
    var poolLength by remember(prefill) { mutableStateOf(prefill?.poolLengthMeters) }
    var stroke by remember(prefill) { mutableStateOf(prefill?.stroke) }
    var rpeText by remember(prefill) { mutableStateOf(prefill?.rpe?.toString() ?: "") }
    var notes by remember(prefill) { mutableStateOf(prefill?.notes ?: "") }
    var showDeleteDialog by remember { mutableStateOf(false) }

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
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SectionTitle("Modalidade") }
            item {
                ChipGroup(
                    options = modalityOptions,
                    selected = setOf(modality.storageValue),
                    onToggle = { value ->
                        ActivityModality.entries.firstOrNull { it.storageValue == value }?.let {
                            modality = it
                            onModalityChanged(it)
                        }
                    },
                )
            }
            if (canCopyLast) {
                item {
                    OutlinedButton(
                        onClick = { onCopyLast(modality) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Copiar último treino de ${modality.label.lowercase()}")
                    }
                }
            }

            item { SectionTitle("Como foi") }
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título (opcional)") },
                    supportingText = errors[ActivityLogField.Title]?.let { { Text(it) } },
                    isError = errors.containsKey(ActivityLogField.Title),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Quando (dd/mm/aaaa hh:mm)") },
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
                        value = durationText,
                        onValueChange = { durationText = it },
                        label = { Text("Duração (min)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = errors[ActivityLogField.Duration]?.let { { Text(it) } },
                        isError = errors.containsKey(ActivityLogField.Duration),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = rpeText,
                        onValueChange = { rpeText = it },
                        label = { Text("RPE (1 a 10)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = errors[ActivityLogField.Rpe]?.let { { Text(it) } },
                        isError = errors.containsKey(ActivityLogField.Rpe),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = distanceText,
                    onValueChange = { distanceText = it },
                    label = { Text("Distância (m, opcional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = errors[ActivityLogField.Distance]?.let { { Text(it) } },
                    isError = errors.containsKey(ActivityLogField.Distance),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (modality == ActivityModality.SWIM) {
                item { SectionTitle("Piscina") }
                item {
                    ChipGroup(
                        options = validPoolLengths.map { it.toString() to "${it} m" } + ("" to "Sem piscina"),
                        selected = setOf(poolLength?.toString() ?: ""),
                        onToggle = { value -> poolLength = value.toIntOrNull() },
                    )
                }
                item { SectionTitle("Nado (opcional)") }
                item {
                    ChipGroup(
                        options = swimStrokeOptions,
                        selected = setOf(stroke ?: ""),
                        onToggle = { value -> stroke = if (value == stroke) null else value },
                    )
                }
            }

            item { SectionTitle("Notas") }
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Ex.: 8 x 100 m") },
                    supportingText = errors[ActivityLogField.Notes]?.let { { Text(it) } },
                    isError = errors.containsKey(ActivityLogField.Notes),
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Button(
                    onClick = {
                        onSave(
                            ActivityLogDraft(
                                modality = modality,
                                title = title,
                                dateText = dateText,
                                durationText = durationText,
                                distanceText = distanceText,
                                poolLengthMeters = if (modality == ActivityModality.SWIM) poolLength else null,
                                stroke = if (modality == ActivityModality.SWIM) stroke else null,
                                rpeText = rpeText,
                                notes = notes,
                            )
                        )
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Text("Salvando…")
                        } else {
                            Text(if (editingSessionId != null) "Salvar alterações" else "Salvar")
                        }
                    }
                }
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
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Excluir o registro?") },
            text = { Text("O registro avulso sai do histórico e do próximo backup. Não dá para desfazer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                ) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            },
        )
    }
}
