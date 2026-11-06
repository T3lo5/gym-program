package com.treinos.app.ui

import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class RunLogInput(
    val durationSeconds: Long,
    val distanceMeters: Double?,
    val rpe: Double?,
)

data class RunLogValidation(
    val valid: Boolean,
    val error: String?,
    val paceSecondsPerKm: Int?,
)

private const val MIN_DURATION_MINUTES = 1
private const val MAX_DURATION_MINUTES = 600
private const val MAX_DISTANCE_METERS = 100_000.0
private const val MIN_RPE = 1.0
private const val MAX_RPE = 10.0

fun validateRunLog(input: RunLogInput): RunLogValidation {
    if (input.durationSeconds <= 0) {
        return RunLogValidation(valid = false, error = "Informe uma duração de pelo menos 1 minuto.", paceSecondsPerKm = null)
    }
    val durationMinutes = input.durationSeconds / 60
    if (durationMinutes < MIN_DURATION_MINUTES || durationMinutes > MAX_DURATION_MINUTES) {
        return RunLogValidation(valid = false, error = "Duração entre $MIN_DURATION_MINUTES e $MAX_DURATION_MINUTES minutos.", paceSecondsPerKm = null)
    }
    input.distanceMeters?.let { distance ->
        if (!distance.isFinite() || distance < 0.0 || distance > MAX_DISTANCE_METERS) {
            return RunLogValidation(valid = false, error = "Distância inválida.", paceSecondsPerKm = null)
        }
    }
    input.rpe?.let { rpe ->
        if (!rpe.isFinite() || rpe < MIN_RPE || rpe > MAX_RPE) {
            return RunLogValidation(valid = false, error = "O RPE deve ficar entre 1 e 10.", paceSecondsPerKm = null)
        }
    }
    val pace = input.distanceMeters?.takeIf { it > 0.0 }?.let { distance ->
        val secondsPerKm = (input.durationSeconds / (distance / 1000.0)).roundToInt()
        secondsPerKm
    }
    return RunLogValidation(valid = true, error = null, paceSecondsPerKm = pace)
}

fun formatRunPace(paceSecondsPerKm: Int?): String? = paceSecondsPerKm?.let {
    "${it / 60}:${(it % 60).toString().padStart(2, '0')}/km"
}

fun formatRunDistance(meters: Double?): String? = meters?.takeIf { it > 0.0 }?.let {
    "${it.roundToLong().toString().replace(Regex("\\B(?=(\\d{3})+(?!\\d))"), ".")} m"
}
