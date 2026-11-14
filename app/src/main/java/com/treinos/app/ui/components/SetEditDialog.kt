package com.treinos.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.treinos.app.R
import com.treinos.app.ui.HapticEvent
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
 * treino e pela folha "Editar séries" do Histórico.
 */
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
                // Háptico curto ao confirmar o salvamento (H3-46).
                haptics.performHapticFeedback(hapticFeedbackForEvent(HapticEvent.SetSaved))
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
