package com.treinos.app.ui

import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.WorkoutPlanWithDetails
import kotlin.math.roundToInt

/**
 * H3-31: a distância informada pelo corredor é em **km** (uma casa decimal);
 * a conversão para metros acontece uma única vez, em [runDistanceKmToMeters],
 * antes de gravar na sessão.
 */
data class RunLogInput(
    val durationSeconds: Long,
    val distanceKm: Double?,
    val rpe: Double?,
)

data class RunLogValidation(
    val valid: Boolean,
    val error: String?,
    val paceSecondsPerKm: Int?,
)

private const val MIN_DURATION_MINUTES = 1
private const val MAX_DURATION_MINUTES = 600
private const val MAX_DISTANCE_KM = 100.0
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
    input.distanceKm?.let { distance ->
        if (!distance.isFinite() || distance < 0.0 || distance > MAX_DISTANCE_KM) {
            return RunLogValidation(valid = false, error = "Distância inválida.", paceSecondsPerKm = null)
        }
    }
    input.rpe?.let { rpe ->
        if (!rpe.isFinite() || rpe < MIN_RPE || rpe > MAX_RPE) {
            return RunLogValidation(valid = false, error = "O RPE deve ficar entre 1 e 10.", paceSecondsPerKm = null)
        }
    }
    val pace = input.distanceKm?.takeIf { it > 0.0 }?.let { distance ->
        (input.durationSeconds / distance).roundToInt()
    }
    return RunLogValidation(valid = true, error = null, paceSecondsPerKm = pace)
}

/**
 * H3-31: o campo "Distância (km)" aceita vírgula ou ponto e no máximo uma
 * casa decimal. Vazio = sem distância (campo opcional); texto inválido,
 * negativo ou com duas casas devolve `null` para a tela mostrar o erro.
 */
fun parseDistanceKm(text: String): Double? {
    val normalized = text.trim().replace(',', '.')
    if (normalized.isEmpty()) return null
    val dot = normalized.indexOf('.')
    if (dot >= 0 && normalized.length - dot - 1 > 1) return null
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
}

/** km → m (×1000), com o resultado em metros arredondado a uma casa decimal. */
fun runDistanceKmToMeters(km: Double): Double =
    Math.round(km * 1000.0 * 10.0) / 10.0

/**
 * H3-35: benchmark 5 km aceita "mm:ss" (ex.: "25:30" → 1530 s) ou segundos crus
 * ("1530" → 1530 s). Rejeita segundos > 59 na parte de minutos (ex.: "25:70"),
 * vazio e negativo. Sem mudança de rede/banco.
 */
fun parse5k(value: String): Int? {
    val normalized = value.trim()
    if (normalized.isEmpty()) return null
    if (normalized.contains(':')) {
        val partes = normalized.split(':')
        if (partes.size != 2) return null
        val minutos = partes[0].toIntOrNull() ?: return null
        val segundos = partes[1].toIntOrNull() ?: return null
        if (minutos < 0 || segundos < 0 || segundos > 59) return null
        return (minutos * 60 + segundos).takeIf { it >= 0 }
    }
    return normalized.toIntOrNull()?.takeIf { it >= 0 }
}

/** Alvo do dia planejado exibido no topo da corrida (H3-31). */
data class RunPlanTarget(
    val name: String,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
)

/** "5,2 km • 30 min"; `null` quando o plano não traz nem duração nem distância. */
fun formatRunPlanTarget(target: RunPlanTarget): String? {
    val parts = buildList {
        target.distanceMeters
            ?.takeIf { it > 0.0 }
            ?.let { formatRunDistanceKm(it) }
            ?.let(::add)
        target.durationSeconds?.takeIf { it > 0 }?.let { "${it / 60} min" }?.let(::add)
    }
    return parts.joinToString(" • ").ifEmpty { null }
}

/**
 * Resolve o alvo do plano para a sessão de corrida aberta: o primeiro
 * exercício da sessão corresponde ao bloco planejado do dia. Sem sessão
 * planejada correspondente, devolve `null` (a tela mostra o cartão genérico).
 */
fun runPlanTargetFor(
    session: SessionWithExercises,
    plan: WorkoutPlanWithDetails?,
): RunPlanTarget? {
    val exerciseId = session.exercises.firstOrNull()?.exercise?.exerciseId ?: return null
    val planExercise = plan?.days
        ?.flatMap { it.exercises }
        ?.firstOrNull { it.exerciseId == exerciseId }
        ?: return null
    return RunPlanTarget(
        name = planExercise.exerciseName.ifBlank { session.session.dayName },
        durationSeconds = planExercise.targetDurationSeconds,
        distanceMeters = planExercise.targetDistanceMeters,
    )
}
