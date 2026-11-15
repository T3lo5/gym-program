package com.treinos.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.treinos.app.R
import com.treinos.app.data.local.SET_TYPE_AMRAP
import com.treinos.app.data.local.SET_TYPE_DROP
import com.treinos.app.data.local.SET_TYPE_NORMAL
import com.treinos.app.data.local.SET_TYPE_WARMUP
import com.treinos.app.ui.HapticEvent
import com.treinos.app.ui.SetInput
import com.treinos.app.ui.SetInputErrors
import com.treinos.app.ui.SetInputValidation
import com.treinos.app.ui.formatKg
import com.treinos.app.ui.hapticFeedbackForEvent
import com.treinos.app.ui.progression.ProgressionAction
import com.treinos.app.ui.progression.ProgressionSuggestion
import com.treinos.app.ui.validateSetInput

/**
 * Diálogo de série (H3-27), extraído para reuso (H3-54): registra uma série
 * nova ou edita uma existente com as mesmas validações (`SetValidation`),
 * sugestão de progressão e botão "Excluir série" opcional. Usado pela tela de
 * treino e pela folha "Editar séries" do Histórico. H4-4: escolhe o tipo
 * (Normal/Aquecimento/Drop set/AMRAP) e o RIR, entregues no `SetInput`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetEditDialog(
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
    onConfirm: (SetInput) -> Unit,
    confirmHaptic: (SetInput) -> HapticEvent = { _ -> HapticEvent.SetSaved },
    initialSetType: String = SET_TYPE_NORMAL,
    initialRir: String = "",
) {
    // H3-31: o que foi digitado sobrevive à rotação (e ao teclado que recria
    // a Activity). `key` evita herdar rascunho de outro diálogo na mesma posição.
    var reps by rememberSaveable(key = key) { mutableStateOf(initialReps) }
    var weight by rememberSaveable(key = key) { mutableStateOf(initialWeight) }
    var rpe by rememberSaveable(key = key) { mutableStateOf(initialRpe) }
    var rir by rememberSaveable(key = key) { mutableStateOf(initialRir) }
    var setType by rememberSaveable(key = key) { mutableStateOf(initialSetType) }
    var errors by remember { mutableStateOf(SetInputErrors()) }
    val haptics = LocalHapticFeedback.current
    val suggestionText = suggestionLabel(suggestion)
    val chipSelectedDesc = stringResource(R.string.chip_selected)
    val chipNotSelectedDesc = stringResource(R.string.chip_not_selected)

    // H3-27: valor preenchido e inválido bloqueia o salvamento — nada é
    // gravado com carga/reps/RPE `null` por falha de parse.
    fun submit() {
        when (
            val result = validateSetInput(
                repsText = reps,
                weightText = weight,
                rpeText = rpe,
                rirText = rir,
                setType = setType,
            )
        ) {
            is SetInputValidation.Invalid -> errors = result.errors
            is SetInputValidation.Valid -> {
                // H3-46/H4-1: o evento é decidido por quem chama — o PR substitui
                // o SetSaved na mesma confirmação (nunca dois hápticos).
                val event = confirmHaptic(result.input)
                haptics.performHapticFeedback(hapticFeedbackForEvent(event))
                onConfirm(result.input)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
                // H4-4: tipo da série — aquecimento não conta como série de
                // trabalho; drop set e AMRAP contam.
                Text(
                    text = stringResource(R.string.workout_set_dialog_type),
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        SET_TYPE_NORMAL to R.string.workout_set_type_normal,
                        SET_TYPE_WARMUP to R.string.workout_set_type_warmup,
                        SET_TYPE_DROP to R.string.workout_set_type_drop,
                        SET_TYPE_AMRAP to R.string.workout_set_type_amrap,
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = setType == value,
                            onClick = { setType = value },
                            label = { Text(stringResource(label)) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .semantics {
                                    stateDescription =
                                        if (setType == value) chipSelectedDesc else chipNotSelectedDesc
                                },
                        )
                    }
                }
                OutlinedTextField(
                    value = reps,
                    onValueChange = {
                        reps = it.filter(Char::isDigit)
                        errors = errors.copy(reps = null)
                    },
                    label = { Text(stringResource(R.string.workout_set_dialog_reps)) },
                    supportingText = {
                        Text(
                            errors.reps ?: if (reps.isBlank()) {
                                stringResource(R.string.common_optional)
                            } else {
                                stringResource(R.string.workout_set_dialog_support_reps)
                            }
                        )
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
                    label = { Text(stringResource(R.string.workout_set_dialog_weight)) },
                    supportingText = { Text(errors.weight ?: stringResource(R.string.workout_set_dialog_support_weight)) },
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
                    label = { Text(stringResource(R.string.workout_set_dialog_rpe)) },
                    supportingText = { Text(errors.rpe ?: stringResource(R.string.workout_set_dialog_support_rpe)) },
                    isError = errors.rpe != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = rir,
                    onValueChange = { value ->
                        rir = value.filter(Char::isDigit)
                        errors = errors.copy(rir = null)
                    },
                    label = { Text(stringResource(R.string.workout_set_dialog_rir)) },
                    supportingText = { Text(errors.rir ?: stringResource(R.string.workout_set_dialog_support_rir)) },
                    isError = errors.rir != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
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
                        onClick = {
                            haptics.performHapticFeedback(hapticFeedbackForEvent(HapticEvent.SetDeleted))
                            onDelete()
                        },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(stringResource(R.string.workout_set_dialog_delete_set))
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        },
    )
}

/** Texto de apoio do motor de progressão; `null` quando não há o que sugerir. */
@Composable
private fun suggestionLabel(suggestion: ProgressionSuggestion?): String? {
    if (suggestion == null || suggestion.action == ProgressionAction.NONE) return null
    val weight = suggestion.suggestedWeightKg?.let { formatKg(it) }
    val reps = if (weight == null) suggestion.suggestedReps?.let { stringResource(R.string.workout_reps, it) } else null
    val target = weight ?: reps ?: return null
    return stringResource(R.string.workout_suggestion, target, suggestion.reason)
}
