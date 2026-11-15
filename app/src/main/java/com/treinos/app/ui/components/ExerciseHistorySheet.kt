package com.treinos.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.treinos.app.R
import com.treinos.app.ui.formatDateTime
import com.treinos.app.ui.formatKg
import com.treinos.app.ui.formatVolumeKg
import com.treinos.app.ui.history.ExerciseHistory
import com.treinos.app.ui.history.ExerciseSessionSummary

/**
 * H4-3: folha do histórico por exercício (drill-down) — reúne o recorde de
 * todos os tempos (H4-1), o gráfico de evolução (H4-2) e a lista de sessões
 * da mais recente para a mais antiga, com o selo de recorde progressivo.
 *
 * O conteúdo chega pronto do ViewModel ([ExerciseHistory] derivado das
 * sessões já carregadas): a folha não recalcula nada por frame e não grava
 * nada. Enquanto o primeiro emit não chega ([history] `null`), o corpo fica
 * vazio por um frame — invisível sob a animação da folha; o estado vazio
 * real ("Nenhuma sessão registrada") é o exercício nunca treinado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseHistorySheet(
    history: ExerciseHistory?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        if (history == null) return@ModalBottomSheet
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = history.exerciseName,
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
                }
            }

            // H4-1: melhor de todos os tempos — mesmos rótulos do detalhe.
            Text(
                text = stringResource(R.string.exercise_detail_record_title),
                style = MaterialTheme.typography.titleMedium,
            )
            val record = history.allTimeRecord
            if (record == null) {
                Text(
                    text = stringResource(R.string.exercise_detail_record_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                val setLabel = if (record.weightKg != null) {
                    stringResource(
                        R.string.exercise_detail_record_set_loaded,
                        formatKg(record.weightKg),
                        record.reps ?: 0,
                    )
                } else {
                    stringResource(R.string.exercise_detail_record_set_bodyweight, record.reps ?: 0)
                }
                Text(
                    text = setLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                record.estimated1RmKg?.let { estimated ->
                    Text(
                        text = stringResource(R.string.exercise_detail_record_1rm, formatKg(estimated)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (history.sessions.isEmpty()) {
                // Exercício nunca treinado: estado vazio e nenhum gráfico (regra 9).
                Text(
                    text = stringResource(R.string.exercise_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.chart_exercise_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                val points = history.series.points
                if (points.size >= 2) {
                    // Mesma janela e mesma descrição do gráfico do Histórico (H4-2).
                    val drawn = points.takeLast(MAX_DRAWN_POINTS)
                    val description = stringResource(
                        R.string.chart_line_a11y,
                        history.exerciseName,
                        stringResource(R.string.chart_metric_best),
                        drawn.size,
                        formatKg(drawn.first().value),
                        drawn.first().label,
                        formatKg(drawn.last().value),
                        drawn.last().label,
                    )
                    ProgressLineChart(
                        points = points,
                        valueLabel = { value -> formatKg(value) },
                        contentDescription = description,
                    )
                } else {
                    // Um ponto isolado não é tendência (H4-2, regra 6).
                    Text(
                        text = stringResource(R.string.chart_few_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = stringResource(R.string.exercise_history_sessions_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                history.sessions.forEachIndexed { index, summary ->
                    ExerciseHistorySessionRow(summary)
                    if (index < history.sessions.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

/**
 * Linha de sessão do drill-down: data, melhor série, selo de recorde
 * progressivo e volume. O TalkBack lê a linha inteira como um item único com
 * descrição própria (data + melhor série + volume + recorde) — única por
 * sessão porque a data inclui hora (regra 15).
 */
@Composable
private fun ExerciseHistorySessionRow(summary: ExerciseSessionSummary) {
    val dateText = formatDateTime(summary.startedAtMillis)
    val bestLabel = if (summary.bestSetWeightKg != null) {
        stringResource(
            R.string.exercise_detail_record_set_loaded,
            formatKg(summary.bestSetWeightKg),
            summary.bestSetReps ?: 0,
        )
    } else {
        stringResource(R.string.exercise_detail_record_set_bodyweight, summary.bestSetReps ?: 0)
    }
    val volumeLabel = stringResource(
        R.string.exercise_history_row_volume,
        formatVolumeKg(summary.volumeKg),
    )
    val rowDescription = if (summary.isPersonalRecord) {
        stringResource(R.string.exercise_history_row_a11y_pr, dateText, bestLabel, volumeLabel)
    } else {
        stringResource(R.string.exercise_history_row_a11y, dateText, bestLabel, volumeLabel)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = rowDescription },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = bestLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (summary.isPersonalRecord) {
                Text(
                    text = stringResource(R.string.exercise_history_pr_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = volumeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
