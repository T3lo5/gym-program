package com.treinos.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.treinos.app.R
import com.treinos.app.data.MeasurementPhotoStore
import com.treinos.app.data.MeasurementValidator
import com.treinos.app.data.local.BodyMeasurementEntity
import com.treinos.app.ui.TreinosSpacing
import com.treinos.app.ui.MeasurementDraft
import com.treinos.app.ui.components.ConfirmationDialog
import com.treinos.app.ui.components.ProgressLineChart
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.components.StatusText
import com.treinos.app.ui.components.statusIsSuccess
import com.treinos.app.ui.formatDateTime
import com.treinos.app.ui.formatDecimal
import com.treinos.app.ui.formatKg
import com.treinos.app.ui.measurements.latestMeasurement
import com.treinos.app.ui.measurements.trendDeltaKg
import com.treinos.app.ui.measurements.weightTrendPoints
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.launch

/**
 * H4-8: "Peso e medidas" — tendência do peso no gráfico da H4-2
 * ([ProgressLineChart], nenhum gráfico novo), lista por `measuredAt` desc,
 * formulário validado com foto de progresso e exclusão que apaga o arquivo.
 *
 * Tudo é offline: as fotos ficam em `filesDir/progress-photos/` e nunca saem do
 * aparelho (o aviso de privacidade aparece no formulário).
 */

private const val MILLIS_PER_DAY = 86_400_000L

/** Saver do dia selecionado (epochDay), para sobreviver a recriação da activity. */
private val measuredDateSaver = Saver<LocalDate, Long>(
    save = { it.toEpochDay() },
    restore = { LocalDate.ofEpochDay(it) },
)

@Composable
fun MeasurementsScreen(
    measurements: List<BodyMeasurementEntity>,
    message: String?,
    pendingPhotoPath: String?,
    onBack: () -> Unit,
    onSave: suspend (MeasurementDraft) -> Boolean,
    onDelete: (Long) -> Unit,
    onRemovePhoto: () -> Unit,
    onCopyLatestToProfile: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onClearMessage: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = { ScreenTopBar(title = stringResource(R.string.screen_measurements), onBack = onBack) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(TreinosSpacing.spaceM),
            verticalArrangement = Arrangement.spacedBy(TreinosSpacing.spaceM),
        ) {
            item {
                MeasurementFormCard(
                    pendingPhotoPath = pendingPhotoPath,
                    message = message,
                    onSave = onSave,
                    onRemovePhoto = onRemovePhoto,
                    onOpenCamera = onOpenCamera,
                    onOpenGallery = onOpenGallery,
                    onClearMessage = onClearMessage,
                )
            }

            if (measurements.isNotEmpty()) {
                item { MeasurementTrendCard(measurements, onCopyLatestToProfile) }
            } else {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(TreinosSpacing.spaceXL),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(TreinosSpacing.spaceS),
                        ) {
                            Text(
                                text = stringResource(R.string.measurements_empty_title),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = stringResource(R.string.measurements_empty_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Text(stringResource(R.string.measurements_empty_cta))
                            }
                        }
                    }
                }
            }

            items(measurements.size) { index ->
                val measurement = measurements[index]
                MeasurementRow(
                    measurement = measurement,
                    onDelete = { pendingDeleteId = measurement.id },
                )
            }
        }
    }

    ConfirmationDialog(
        visible = pendingDeleteId != null,
        onDismiss = { pendingDeleteId = null },
        onConfirm = {
            pendingDeleteId?.let(onDelete)
            pendingDeleteId = null
        },
        title = stringResource(R.string.measurements_delete_title),
        message = stringResource(R.string.measurements_delete_body),
        confirmLabel = stringResource(R.string.common_delete),
        isDestructive = true,
    )
}

/**
 * Formulário de entrada (regras 1–3 e 8): validação inline por campo com os
 * helpers puros de [MeasurementValidator], botão de salvar desabilitado quando
 * algo está fora da faixa e foto anexada antes de gravar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeasurementFormCard(
    pendingPhotoPath: String?,
    message: String?,
    onSave: suspend (MeasurementDraft) -> Boolean,
    onRemovePhoto: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onClearMessage: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var measuredDate by rememberSaveable(stateSaver = measuredDateSaver) {
        mutableStateOf(LocalDate.now())
    }
    var weightText by remember { mutableStateOf("") }
    var bodyFatText by remember { mutableStateOf("") }
    var waistText by remember { mutableStateOf("") }
    var chestText by remember { mutableStateOf("") }
    var armText by remember { mutableStateOf("") }
    var thighText by remember { mutableStateOf("") }
    var hipText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    val parsedWeight = MeasurementValidator.parse(weightText)
    val parsedBodyFat = MeasurementValidator.parse(bodyFatText)
    val parsedWaist = MeasurementValidator.parse(waistText)
    val parsedChest = MeasurementValidator.parse(chestText)
    val parsedArm = MeasurementValidator.parse(armText)
    val parsedThigh = MeasurementValidator.parse(thighText)
    val parsedHip = MeasurementValidator.parse(hipText)

    // `measuredAt` = dia escolhido + hora atual: hoje é ~agora, ontem é passado
    // e um dia futuro cai na regra 3 ("Data no futuro").
    val zone = ZoneId.systemDefault()
    val draftMillis = measuredDate
        .atTime(LocalTime.now())
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
    val dateValid = MeasurementValidator.isDateValid(draftMillis, System.currentTimeMillis())
    val hasValue = listOf(parsedWeight, parsedBodyFat, parsedWaist, parsedChest, parsedArm, parsedThigh, parsedHip)
        .any { it != null }
    val formValid = hasValue && dateValid &&
        weightErrorRes(weightText, parsedWeight) == null &&
        bodyFatErrorRes(bodyFatText, parsedBodyFat) == null &&
        circumferenceErrorRes(waistText, parsedWaist) == null &&
        circumferenceErrorRes(chestText, parsedChest) == null &&
        circumferenceErrorRes(armText, parsedArm) == null &&
        circumferenceErrorRes(thighText, parsedThigh) == null &&
        circumferenceErrorRes(hipText, parsedHip) == null

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(TreinosSpacing.spaceM),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.measurements_form_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )

            // Campo somente leitura: o toque abre o seletor de datas (o overlay
            // cobre o campo, então o teclado nunca aparece).
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = measuredDate.format(inputDateFormatter),
                    onValueChange = { },
                    readOnly = true,
                    label = { Text(stringResource(R.string.measurements_field_date)) },
                    isError = !dateValid,
                    supportingText = {
                        if (!dateValid) Text(stringResource(R.string.measurements_error_future_date))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showDatePicker = true },
                )
            }

            MeasurementNumberField(
                label = stringResource(R.string.measurements_field_weight),
                value = weightText,
                onValueChange = { weightText = it },
                errorRes = weightErrorRes(weightText, parsedWeight),
            )
            MeasurementNumberField(
                label = stringResource(R.string.measurements_field_body_fat),
                value = bodyFatText,
                onValueChange = { bodyFatText = it },
                errorRes = bodyFatErrorRes(bodyFatText, parsedBodyFat),
            )
            MeasurementNumberField(
                label = stringResource(R.string.measurements_field_waist),
                value = waistText,
                onValueChange = { waistText = it },
                errorRes = circumferenceErrorRes(waistText, parsedWaist),
            )
            MeasurementNumberField(
                label = stringResource(R.string.measurements_field_chest),
                value = chestText,
                onValueChange = { chestText = it },
                errorRes = circumferenceErrorRes(chestText, parsedChest),
            )
            MeasurementNumberField(
                label = stringResource(R.string.measurements_field_arm),
                value = armText,
                onValueChange = { armText = it },
                errorRes = circumferenceErrorRes(armText, parsedArm),
            )
            MeasurementNumberField(
                label = stringResource(R.string.measurements_field_thigh),
                value = thighText,
                onValueChange = { thighText = it },
                errorRes = circumferenceErrorRes(thighText, parsedThigh),
            )
            MeasurementNumberField(
                label = stringResource(R.string.measurements_field_hip),
                value = hipText,
                onValueChange = { hipText = it },
                errorRes = circumferenceErrorRes(hipText, parsedHip),
            )
            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                label = { Text(stringResource(R.string.measurements_field_notes)) },
                modifier = Modifier.fillMaxWidth(),
            )

            if (!hasValue) {
                Text(
                    text = stringResource(R.string.measurements_error_no_value),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            MeasurementPhotoSection(
                pendingPhotoPath = pendingPhotoPath,
                onRemovePhoto = onRemovePhoto,
                onOpenCamera = onOpenCamera,
                onOpenGallery = onOpenGallery,
            )

            message?.let {
                StatusText(text = it, isSuccess = statusIsSuccess(it), announce = true)
                TextButton(onClick = onClearMessage) {
                    Text(stringResource(R.string.settings_btn_clear))
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        val saved = onSave(
                            MeasurementDraft(
                                measuredAtMillis = draftMillis,
                                weightKg = weightText,
                                bodyFatPercent = bodyFatText,
                                waistCm = waistText,
                                chestCm = chestText,
                                armCm = armText,
                                thighCm = thighText,
                                hipCm = hipText,
                                notes = notesText,
                            ),
                        )
                        // O dia escolhido fica (facilita registrar medidas
                        // antigas em sequência); os valores limpam.
                        if (saved) {
                            weightText = ""
                            bodyFatText = ""
                            waistText = ""
                            chestText = ""
                            armText = ""
                            thighText = ""
                            hipText = ""
                            notesText = ""
                        }
                    }
                },
                enabled = formValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.common_save))
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = measuredDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        measuredDate = LocalDate.ofEpochDay(millis / MILLIS_PER_DAY)
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun MeasurementNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    errorRes: Int?,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = errorRes != null,
        supportingText = {
            errorRes?.let { Text(stringResource(it)) }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MeasurementPhotoSection(
    pendingPhotoPath: String?,
    onRemovePhoto: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TreinosSpacing.spaceXs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(TreinosSpacing.spaceXs)) {
            Button(onClick = onOpenCamera, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.measurements_photo_camera))
            }
            OutlinedButton(onClick = onOpenGallery, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.measurements_photo_gallery))
            }
        }
        if (pendingPhotoPath != null && MeasurementPhotoStore.exists(pendingPhotoPath)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TreinosSpacing.spaceXs),
            ) {
                AsyncImage(
                    model = File(pendingPhotoPath),
                    contentDescription = stringResource(R.string.measurements_photo_thumbnail),
                    modifier = Modifier.size(96.dp),
                )
                TextButton(onClick = onRemovePhoto) {
                    Text(stringResource(R.string.measurements_photo_remove))
                }
            }
        }
        Text(
            text = stringResource(R.string.measurements_photo_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Gráfico da H4-2 + delta do período + cópia explícita do último peso (regra 13). */
@Composable
private fun MeasurementTrendCard(
    measurements: List<BodyMeasurementEntity>,
    onCopyLatestToProfile: () -> Unit,
) {
    val points = remember(measurements) { weightTrendPoints(measurements) }
    val delta = remember(measurements) { trendDeltaKg(measurements) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(TreinosSpacing.spaceM),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.measurements_chart_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            if (points.isNotEmpty()) {
                ProgressLineChart(
                    points = points,
                    valueLabel = { formatKg(it) },
                    contentDescription = stringResource(
                        R.string.measurements_chart_desc,
                        formatKg(points.first().value),
                        formatKg(points.last().value),
                        points.size,
                    ),
                )
                delta?.let {
                    Text(
                        text = stringResource(R.string.measurements_delta, formatKg(it)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            val latestWeight = remember(measurements) { latestMeasurement(measurements)?.weightKg }
            if (latestWeight != null) {
                TextButton(onClick = onCopyLatestToProfile) {
                    Text(stringResource(R.string.measurements_copy_profile))
                }
            }
        }
    }
}

@Composable
private fun MeasurementRow(
    measurement: BodyMeasurementEntity,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(TreinosSpacing.spaceS),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(TreinosSpacing.spaceXs),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(TreinosSpacing.space2xs),
            ) {
                Text(
                    text = formatDateTime(measurement.measuredAt),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (measurement.weightKg != null) {
                    Text(
                        text = formatKg(measurement.weightKg),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                val details = measurementDetailsLine(measurement)
                if (details.isNotBlank()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (measurement.notes.isNotBlank()) {
                    Text(
                        text = measurement.notes,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (measurement.photoPath != null) {
                    if (MeasurementPhotoStore.exists(measurement.photoPath)) {
                        AsyncImage(
                            model = File(measurement.photoPath),
                            contentDescription = stringResource(R.string.measurements_photo_thumbnail),
                            modifier = Modifier.size(72.dp),
                        )
                    } else {
                        // Regra 12: medida importada sem o arquivo continua válida.
                        Text(
                            text = stringResource(R.string.measurements_photo_missing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Linha resumo "Gordura 18,5% · Cintura 84,0 cm" com os campos presentes. */
@Composable
private fun measurementDetailsLine(measurement: BodyMeasurementEntity): String = listOfNotNull(
    measurement.bodyFatPercent?.let {
        stringResource(R.string.measurements_short_body_fat) + " " + formatDecimal(it, 1) + "%"
    },
    measurement.waistCm?.let { shortCm(R.string.measurements_short_waist, it) },
    measurement.chestCm?.let { shortCm(R.string.measurements_short_chest, it) },
    measurement.armCm?.let { shortCm(R.string.measurements_short_arm, it) },
    measurement.thighCm?.let { shortCm(R.string.measurements_short_thigh, it) },
    measurement.hipCm?.let { shortCm(R.string.measurements_short_hip, it) },
).joinToString(" · ")

@Composable
private fun shortCm(labelRes: Int, value: Double): String =
    stringResource(labelRes) + " " + formatDecimal(value, 1) + " cm"

private fun weightErrorRes(text: String, parsed: Double?): Int? = when {
    text.isBlank() -> null
    parsed == null -> R.string.measurements_error_invalid_number
    !MeasurementValidator.isValidWeight(parsed) -> R.string.measurements_error_weight
    else -> null
}

private fun bodyFatErrorRes(text: String, parsed: Double?): Int? = when {
    text.isBlank() -> null
    parsed == null -> R.string.measurements_error_invalid_number
    !MeasurementValidator.isValidBodyFat(parsed) -> R.string.measurements_error_body_fat
    else -> null
}

private fun circumferenceErrorRes(text: String, parsed: Double?): Int? = when {
    text.isBlank() -> null
    parsed == null -> R.string.measurements_error_invalid_number
    !MeasurementValidator.isValidCircumference(parsed) -> R.string.measurements_error_circumference
    else -> null
}

private val inputDateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
