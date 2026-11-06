package com.treinos.app.ui.history

import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.ui.SessionKind
import com.treinos.app.ui.muscleLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val STATUS_COMPLETED = "completed"
const val STATUS_IN_PROGRESS = "in_progress"

/** Chave usada quando o exercício não está no plano ativo ou não tem músculo. */
const val OTHERS_MUSCLE_LABEL = "Outros"

data class HistoryMetrics(
    val sessionCount: Int = 0,
    val completedCount: Int = 0,
    val totalSets: Int = 0,
    val totalReps: Int = 0,
    val totalVolumeKg: Double = 0.0,
    val totalDurationSeconds: Long = 0L,
    val volumeByMuscle: Map<String, Double> = emptyMap(),
    /** Distância total (m) das sessões de corrida concluídas no período. */
    val runDistanceMeters: Double = 0.0,
    /** Duração total (s) das sessões de corrida concluídas no período. */
    val runDurationSeconds: Long = 0L,
    /** Duração total (s) das sessões de mobilidade concluídas no período. */
    val mobilityDurationSeconds: Long = 0L,
    /** Séries totais apenas de sessões de musculação. */
    val strengthSets: Int = 0,
    /** Volume (kg) apenas de sessões de musculação. */
    val strengthVolumeKg: Double = 0.0,
)

/**
 * Séries, reps e volume somam apenas sessões de musculação concluídas;
 * corrida soma distância e duração; mobilidade soma duração.
 * [sessionCount] é o total do período (inclui as em andamento) e
 * [completedCount] o que entra nas somas.
 */
fun buildHistoryMetrics(
    sessions: List<SessionWithExercises>,
    muscleByExerciseId: Map<String, String>,
    from: LocalDate? = null,
    to: LocalDate? = null,
): HistoryMetrics {
    val inPeriod = sessions.filter { session ->
        val day = sessionDay(session)
        (from == null || !day.isBefore(from)) && (to == null || !day.isAfter(to))
    }
    val completed = inPeriod.filter { it.session.status == STATUS_COMPLETED }

    var totalSets = 0
    var totalReps = 0
    var totalVolumeKg = 0.0
    var totalDurationSeconds = 0L
    var runDistanceMeters = 0.0
    var runDurationSeconds = 0L
    var mobilityDurationSeconds = 0L
    var strengthSets = 0
    var strengthVolumeKg = 0.0
    val volumeByMuscle = linkedMapOf<String, Double>()

    completed.forEach { session ->
        val kind = SessionKind.fromModality(session.session.modality)
        totalDurationSeconds += session.session.totalDurationSeconds

        when (kind) {
            SessionKind.RUN -> {
                runDistanceMeters += session.session.totalDistanceMeters ?: 0.0
                runDurationSeconds += session.session.totalDurationSeconds
            }
            SessionKind.MOBILITY -> {
                mobilityDurationSeconds += session.session.totalDurationSeconds
            }
            SessionKind.STRENGTH -> {
                session.exercises.forEach { exercise ->
                    exercise.sets.filter { it.countsAsMetric() }.forEach { set ->
                        totalSets += 1
                        strengthSets += 1
                        totalReps += set.reps ?: 0
                        val volume = set.volumeKg()
                        totalVolumeKg += volume
                        strengthVolumeKg += volume
                        if (volume > 0.0) {
                            val muscles = muscleKeysFor(exercise.exercise.exerciseId, muscleByExerciseId)
                            val share = volume / muscles.size
                            muscles.forEach { muscle ->
                                volumeByMuscle[muscle] = (volumeByMuscle[muscle] ?: 0.0) + share
                            }
                        }
                    }
                }
            }
        }
    }

    return HistoryMetrics(
        sessionCount = inPeriod.size,
        completedCount = completed.size,
        totalSets = totalSets,
        totalReps = totalReps,
        totalVolumeKg = totalVolumeKg,
        totalDurationSeconds = totalDurationSeconds,
        volumeByMuscle = volumeByMuscle,
        runDistanceMeters = runDistanceMeters,
        runDurationSeconds = runDurationSeconds,
        mobilityDurationSeconds = mobilityDurationSeconds,
        strengthSets = strengthSets,
        strengthVolumeKg = strengthVolumeKg,
    )
}

fun sessionDay(session: SessionWithExercises): LocalDate =
    Instant.ofEpochMilli(session.session.startedAt)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()

/**
 * [muscleByExerciseId] guarda a lista de músculos do exercício separada por vírgula;
 * o volume é dividido igualmente entre eles para não contar a mesma série duas vezes.
 */
private fun muscleKeysFor(exerciseId: String, muscleByExerciseId: Map<String, String>): List<String> {
    val labels = muscleByExerciseId[exerciseId]
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.map(::muscleLabel)
        ?.distinct()
        .orEmpty()
    return labels.ifEmpty { listOf(OTHERS_MUSCLE_LABEL) }
}

private fun SetLogEntity.countsAsMetric(): Boolean = isCompleted && !isWarmup

private fun SetLogEntity.volumeKg(): Double {
    val repsValue = reps ?: return 0.0
    val weightValue = weightKg ?: return 0.0
    if (!weightValue.isFinite()) return 0.0
    return repsValue * weightValue
}
