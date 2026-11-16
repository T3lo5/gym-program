package com.treinos.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.treinos.app.R
import com.treinos.app.ui.MAX_WORKOUT_NOTE_CHARS
import com.treinos.app.ui.noteLengthError
import com.treinos.app.ui.normalizeNote

/**
 * Diálogo de nota (H4-11): campo multilinha com contador de caracteres e ações
 * Cancelar/Salvar/Limpar. Acima do teto ([MAX_WORKOUT_NOTE_CHARS]) o Salvar
 * fica desabilitado e o contador avisa — nada é gravado. O rascunho vive em
 * `rememberSaveable(key)` (padrão H3-31): girar não perde o texto nem herda o
 * rascunho de outro diálogo na mesma posição.
 */
@Composable
fun NoteEditDialog(
    key: String,
    title: String,
    initialNote: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable(key = key) { mutableStateOf(initialNote) }
    val tooLong = noteLengthError(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.workout_note_field_label)) },
                supportingText = {
                    Text(
                        text = if (tooLong) {
                            stringResource(R.string.workout_note_too_long, MAX_WORKOUT_NOTE_CHARS)
                        } else {
                            stringResource(R.string.workout_note_counter, text.length, MAX_WORKOUT_NOTE_CHARS)
                        },
                        color = if (tooLong) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                isError = tooLong,
                minLines = 3,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_edit_field"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(normalizeNote(text)) },
                enabled = !tooLong,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { text = "" }, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Text(stringResource(R.string.workout_note_clear))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        },
    )
}
