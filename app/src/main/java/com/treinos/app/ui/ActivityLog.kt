package com.treinos.app.ui

import com.treinos.app.data.local.MANUAL_SESSION_SOURCE
import com.treinos.app.data.local.WorkoutSessionEntity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class ActivityModality(val storageValue: String, val label: String) {
    STRENGTH("strength", "Musculação"),
    RUN("run", "Corrida"),
    SWIM("swim", "Natação"),
    MOBILITY("mobility", "Mobilidade"),
    OTHER("other", "Outra"),
    ;

    companion object {
        fun fromStorageValue(value: String?): ActivityModality =
            entries.firstOrNull { it.storageValue == value } ?: OTHER
    }
}

data class ActivityLogInput(
    val modality: ActivityModality = ActivityModality.SWIM,
    val title: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    val durationMinutes: Int = 45,
    val distanceMeters: Double? = null,
    val poolLengthMeters: Int? = null,
    val stroke: String? = null,
    val rpe: Double? = null,
    val notes: String = "",
)

/** O mesmo formulário, ainda como texto editável. */
data class ActivityLogDraft(
    val modality: ActivityModality = ActivityModality.SWIM,
    val title: String = "",
    val dateText: String = "",
    val durationText: String = "",
    val distanceText: String = "",
    val poolLengthMeters: Int? = null,
    val stroke: String? = null,
    val rpeText: String = "",
    val notes: String = "",
)

enum class ActivityLogField {
    Title,
    Date,
    Duration,
    Distance,
    PoolLength,
    Stroke,
    Rpe,
    Notes,
}

data class ActivityLogValidation(
    val valid: Boolean,
    val errors: Map<ActivityLogField, String>,
    val input: ActivityLogInput?,
)

val swimStrokeOptions: List<Pair<String, String>> = listOf(
    "crawl" to "Crawl",
    "costas" to "Costas",
    "peito" to "Peito",
    "borboleta" to "Borboleta",
    "misto" to "Misto",
    "outro" to "Outro",
)

val validPoolLengths: List<Int> = listOf(25, 50)

private const val MIN_DURATION_MINUTES = 1
private const val MAX_DURATION_MINUTES = 600
private const val MAX_DISTANCE_METERS = 100_000.0
private const val MIN_RPE = 1.0
private const val MAX_RPE = 10.0
private const val MAX_NOTES_CHARS = 2000
private const val FUTURE_TOLERANCE_MILLIS = 5 * 60_000L

private val draftDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

fun WorkoutSessionEntity.isManualActivity(): Boolean = source == MANUAL_SESSION_SOURCE

/** Título efetivo: o informado ou o default da modalidade (regra 2). */
fun activityTitle(modality: ActivityModality, title: String): String {
    val trimmed = title.trim()
    if (trimmed.isNotEmpty()) return trimmed
    return if (modality == ActivityModality.OTHER) "Atividade" else modality.label
}

fun validateActivityLog(
    input: ActivityLogInput,
    nowMillis: Long = System.currentTimeMillis(),
): ActivityLogValidation {
    val errors = linkedMapOf<ActivityLogField, String>()

    if (input.durationMinutes < MIN_DURATION_MINUTES || input.durationMinutes > MAX_DURATION_MINUTES) {
        errors[ActivityLogField.Duration] =
            "Informe uma duração entre $MIN_DURATION_MINUTES e $MAX_DURATION_MINUTES minutos."
    }

    input.distanceMeters?.let { distance ->
        when {
            !distance.isFinite() -> errors[ActivityLogField.Distance] = "Distância inválida."
            distance < 0.0 -> errors[ActivityLogField.Distance] = "A distância não pode ser negativa."
            distance > MAX_DISTANCE_METERS ->
                errors[ActivityLogField.Distance] = "A distância máxima registrada é 100.000 m."
        }
    }

    if (input.startedAt > nowMillis + FUTURE_TOLERANCE_MILLIS) {
        errors[ActivityLogField.Date] = "A data não pode estar no futuro."
    }

    if (input.notes.length > MAX_NOTES_CHARS) {
        errors[ActivityLogField.Notes] = "As notas têm no máximo $MAX_NOTES_CHARS caracteres."
    }

    if (input.modality == ActivityModality.SWIM) {
        input.poolLengthMeters?.let { pool ->
            if (pool !in validPoolLengths) {
                errors[ActivityLogField.PoolLength] = "A piscina deve ter 25 ou 50 m."
            }
        }
        input.stroke?.let { stroke ->
            if (swimStrokeOptions.none { it.first == stroke }) {
                errors[ActivityLogField.Stroke] = "Estilo de nado inválido."
            }
        }
    }

    input.rpe?.let { rpe ->
        when {
            !rpe.isFinite() || rpe < MIN_RPE || rpe > MAX_RPE ->
                errors[ActivityLogField.Rpe] = "O RPE deve ficar entre 1 e 10."
            abs(rpe * 2 - (rpe * 2).roundToInt()) > 1e-9 ->
                errors[ActivityLogField.Rpe] = "Use passos de 0,5 no RPE."
        }
    }

    if (errors.isNotEmpty()) {
        return ActivityLogValidation(valid = false, errors = errors, input = null)
    }
    return ActivityLogValidation(valid = true, errors = emptyMap(), input = normalize(input))
}

private fun normalize(input: ActivityLogInput): ActivityLogInput {
    val isSwim = input.modality == ActivityModality.SWIM
    return input.copy(
        title = activityTitle(input.modality, input.title),
        distanceMeters = input.distanceMeters?.takeIf { it > 0.0 },
        poolLengthMeters = if (isSwim) input.poolLengthMeters else null,
        stroke = if (isSwim) input.stroke else null,
        notes = input.notes.trim(),
    )
}

/**
 * Converte o texto do formulário e valida. Erros de digitação devolvem o campo
 * inválido sem rodar as regras de faixa — a mensagem não sai duplicada.
 */
fun validateActivityDraft(
    draft: ActivityLogDraft,
    nowMillis: Long = System.currentTimeMillis(),
): ActivityLogValidation {
    val parseErrors = linkedMapOf<ActivityLogField, String>()
    val startedAt = if (draft.dateText.isBlank()) {
        nowMillis
    } else {
        parseDraftDate(draft.dateText) ?: run {
            parseErrors[ActivityLogField.Date] = "Use o formato dd/mm/aaaa hh:mm."
            nowMillis
        }
    }
    val durationMinutes = parseDraftInt(draft.durationText) ?: run {
        parseErrors[ActivityLogField.Duration] = "Informe a duração em minutos."
        0
    }
    val distance = parseDraftNumber(draft.distanceText) ?: run {
        if (draft.distanceText.isNotBlank()) {
            parseErrors[ActivityLogField.Distance] = "Distância inválida."
        }
        null
    }
    val rpe = parseDraftNumber(draft.rpeText) ?: run {
        if (draft.rpeText.isNotBlank()) {
            parseErrors[ActivityLogField.Rpe] = "RPE inválido."
        }
        null
    }
    if (parseErrors.isNotEmpty()) {
        return ActivityLogValidation(valid = false, errors = parseErrors, input = null)
    }

    return validateActivityLog(
        ActivityLogInput(
            modality = draft.modality,
            title = draft.title,
            startedAt = startedAt,
            durationMinutes = durationMinutes,
            distanceMeters = distance,
            poolLengthMeters = draft.poolLengthMeters,
            stroke = draft.stroke,
            rpe = rpe,
            notes = draft.notes,
        ),
        nowMillis,
    )
}

private fun parseDraftDate(text: String): Long? {
    if (text.isBlank()) return null
    return try {
        LocalDateTime.parse(text.trim(), draftDateFormatter)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}

private fun parseDraftInt(text: String): Int? {
    if (text.isBlank()) return null
    return parseDraftNumber(text)?.let { if (it.isFinite()) it.roundToInt() else null }
}

/**
 * Aceita o texto como o usuário o escreve: `1,5` ou `1.5` para RPE e `1.500`
 * (ou `1500`) para metros. Com vírgula, a vírgula é o decimal e o ponto é
 * milhar; sem vírgula, um único grupo de 3 dígitos depois do ponto é milhar.
 */
fun parseDraftNumber(text: String): Double? {
    val raw = text.trim().replace(" ", "")
    if (raw.isEmpty()) return null
    val normalized = if (raw.contains(',')) {
        raw.replace(".", "").replace(',', '.')
    } else {
        val dot = raw.lastIndexOf('.')
        val isThousands = dot > 0 && raw.length - dot - 1 == 3 && raw.indexOf('.') == dot
        if (isThousands) raw.replace(".", "") else raw
    }
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
}

fun paceSecondsPer100m(durationSeconds: Long, distanceMeters: Double?): Long? {
    if (durationSeconds <= 0L) return null
    val distance = distanceMeters ?: return null
    if (distance <= 0.0) return null
    return (durationSeconds / (distance / 100.0)).roundToLong()
}

fun paceSecondsPerKm(durationSeconds: Long, distanceMeters: Double?): Long? {
    if (durationSeconds <= 0L) return null
    val distance = distanceMeters ?: return null
    if (distance <= 0.0) return null
    return (durationSeconds / (distance / 1000.0)).roundToLong()
}

/** O registro avulso nasce concluído e sem exercícios (regras 13 e 14). */
fun ActivityLogInput.toManualSession(id: Long = 0L): WorkoutSessionEntity {
    val effectiveTitle = activityTitle(modality, title)
    val durationSeconds = durationMinutes.toLong() * 60L
    val isSwim = modality == ActivityModality.SWIM
    return WorkoutSessionEntity(
        id = id,
        planId = 0L,
        dayId = 0L,
        planName = "",
        dayName = effectiveTitle,
        status = "completed",
        startedAt = startedAt,
        endedAt = startedAt + durationSeconds * 1000L,
        totalDurationSeconds = durationSeconds,
        notes = notes,
        modality = modality.storageValue,
        source = MANUAL_SESSION_SOURCE,
        title = effectiveTitle,
        totalDistanceMeters = distanceMeters?.takeIf { it > 0.0 },
        poolLengthMeters = if (isSwim) poolLengthMeters else null,
        stroke = if (isSwim) stroke else null,
        sessionRpe = rpe,
    )
}

/** Reabre um registro salvo no formulário (edição e "copiar último"). */
fun WorkoutSessionEntity.toActivityLogInput(): ActivityLogInput = ActivityLogInput(
    modality = ActivityModality.fromStorageValue(modality),
    title = title.ifBlank { dayName },
    startedAt = startedAt,
    durationMinutes = (totalDurationSeconds / 60L).toInt().coerceAtLeast(1),
    distanceMeters = totalDistanceMeters,
    poolLengthMeters = poolLengthMeters,
    stroke = stroke,
    rpe = sessionRpe,
    notes = notes,
)
