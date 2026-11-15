package com.treinos.app.ui.history

import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.ui.SessionKind
import com.treinos.app.ui.formatDateShort
import java.time.LocalDate

/**
 * H4-2: agregações puras para os gráficos de progresso do Histórico.
 *
 * Tudo é derivado de [SessionWithExercises] já carregadas pelo Room — sem
 * I/O, sem rede e sem mudança de schema. Só sessões de musculação concluídas
 * entram nas agregações; corrida e mobilidade são ignoradas.
 */

/** Métrica do gráfico de linha: melhor série da sessão ou volume total. */
enum class ExerciseChartMetric { BEST_SET_KG, VOLUME_KG }

/** Um ponto do gráfico de linha: uma sessão concluída com o exercício. */
data class ProgressPoint(
    val sessionId: Long,
    val startedAtMillis: Long,
    /** Data curta do treino, ex.: "10/11". */
    val label: String,
    val value: Double,
)

/** Série cronológica de um exercício, pronta para o gráfico de linha. */
data class ExerciseProgressSeries(
    val exerciseId: String,
    val metric: ExerciseChartMetric,
    /** Ordem cronológica crescente por [ProgressPoint.startedAtMillis]. */
    val points: List<ProgressPoint>,
)

/** Barra do gráfico de volume por músculo. */
data class MuscleBar(
    /** Rótulo já traduzido por [muscleLabel] (ou "Outros"). */
    val muscle: String,
    val volumeKg: Double,
    val sets: Int,
)

/** Exercício com histórico no período, para o seletor do gráfico. */
data class ExerciseOption(
    val exerciseId: String,
    val name: String,
    val sessionCount: Int,
)

/**
 * Série de um exercício ao longo das sessões concluídas de musculação no
 * período, ordenada por `startedAt` crescente.
 *
 * - `BEST_SET_KG`: maior carga finita e > 0 entre as séries válidas da sessão;
 *   sessão só com séries sem carga não gera ponto.
 * - `VOLUME_KG`: soma de `reps × weightKg` das séries válidas; séries sem
 *   carga somam 0 (a sessão gera ponto com valor 0).
 *
 * Período inclusivo e opcional; `null` significa sem limite.
 */
fun exerciseProgressSeries(
    sessions: List<SessionWithExercises>,
    exerciseId: String,
    metric: ExerciseChartMetric,
    from: LocalDate? = null,
    to: LocalDate? = null,
): ExerciseProgressSeries {
    val points = sessions
        .filter { it.isChartEligible() && it.isInPeriod(from, to) }
        .sortedWith(compareBy({ it.session.startedAt }, { it.session.id }))
        .mapNotNull { session ->
            val validSets = session.exercises
                .filter { it.exercise.exerciseId == exerciseId }
                .flatMap { it.sets }
                .filter { it.countsForChart() }
            val value: Double? = when (metric) {
                ExerciseChartMetric.BEST_SET_KG -> validSets
                    .mapNotNull { it.weightKg?.takeIf { weight -> weight.isFinite() && weight > 0.0 } }
                    .maxOrNull()
                ExerciseChartMetric.VOLUME_KG -> validSets.sumOf { it.chartVolumeKg() }
            }
            value?.let {
                ProgressPoint(
                    sessionId = session.session.id,
                    startedAtMillis = session.session.startedAt,
                    label = formatDateShort(sessionDay(session)),
                    value = it,
                )
            }
        }
    return ExerciseProgressSeries(
        exerciseId = exerciseId,
        metric = metric,
        points = points,
    )
}

/**
 * Volume por músculo no período, com a mesma divisão de volume entre os
 * músculos do exercício usada por [buildHistoryMetrics] (via
 * [muscleKeysFor]); exercício sem músculo conhecido cai em
 * [OTHERS_MUSCLE_LABEL]. `sets` conta a série inteira em **cada** músculo.
 *
 * Barras ordenadas por volume decrescente; empate por rótulo crescente.
 */
fun muscleVolumeBars(
    sessions: List<SessionWithExercises>,
    muscleByExerciseId: Map<String, String>,
    from: LocalDate? = null,
    to: LocalDate? = null,
): List<MuscleBar> {
    class BarAccumulator {
        var volumeKg = 0.0
        var sets = 0
    }

    val byMuscle = linkedMapOf<String, BarAccumulator>()
    sessions
        .filter { it.isChartEligible() && it.isInPeriod(from, to) }
        .forEach { session ->
            session.exercises.forEach { exercise ->
                val muscles = muscleKeysFor(exercise.exercise.exerciseId, muscleByExerciseId)
                exercise.sets.filter { it.countsForChart() }.forEach { set ->
                    val volume = set.chartVolumeKg()
                    val share = volume / muscles.size
                    muscles.forEach { muscle ->
                        val accumulator = byMuscle.getOrPut(muscle) { BarAccumulator() }
                        accumulator.volumeKg += share
                        accumulator.sets += 1
                    }
                }
            }
        }
    return byMuscle
        .map { (muscle, accumulator) -> MuscleBar(muscle, accumulator.volumeKg, accumulator.sets) }
        .filter { !(it.volumeKg == 0.0 && it.sets == 0) }
        .sortedWith(compareByDescending<MuscleBar> { it.volumeKg }.thenBy { it.muscle })
}

/**
 * Exercícios presentes no período, com quantas sessões concluídas contêm
 * cada um. Ordena por `sessionCount` decrescente, depois nome crescente
 * (case-insensitive), depois `exerciseId`; nome vazio cai para o ID.
 */
fun exercisesWithHistory(
    sessions: List<SessionWithExercises>,
    from: LocalDate? = null,
    to: LocalDate? = null,
): List<ExerciseOption> {
    class OptionAccumulator(val name: String) {
        var sessionCount = 0
    }

    val byExerciseId = linkedMapOf<String, OptionAccumulator>()
    sessions
        .filter { it.isChartEligible() && it.isInPeriod(from, to) }
        .forEach { session ->
            session.exercises
                .map { it.exercise.exerciseId }
                .distinct()
                .filter { it.isNotBlank() }
                .forEach { exerciseId ->
                    val entry = session.exercises.first { it.exercise.exerciseId == exerciseId }
                    val name = entry.exercise.exerciseName
                        .takeIf { it.isNotBlank() }
                        ?: exerciseId
                    byExerciseId.getOrPut(exerciseId) { OptionAccumulator(name) }.sessionCount += 1
                }
        }
    return byExerciseId
        .map { (exerciseId, accumulator) ->
            ExerciseOption(exerciseId, accumulator.name, accumulator.sessionCount)
        }
        .sortedWith(
            compareByDescending<ExerciseOption> { it.sessionCount }
                .thenBy { it.name.lowercase() }
                .thenBy { it.exerciseId },
        )
}

/** Só sessões concluídas de musculação entram nos gráficos (H4-2, regra 1). */
private fun SessionWithExercises.isChartEligible(): Boolean =
    session.status == STATUS_COMPLETED &&
        SessionKind.fromModality(session.modality) == SessionKind.STRENGTH

/** Período inclusivo e opcional; `null` significa sem limite (regra 10). */
private fun SessionWithExercises.isInPeriod(from: LocalDate?, to: LocalDate?): Boolean {
    val day = sessionDay(this)
    return (from == null || !day.isBefore(from)) && (to == null || !day.isAfter(to))
}

/** Séries concluídas e fora do aquecimento (regra 2). */
private fun SetLogEntity.countsForChart(): Boolean = isCompleted && !isWarmup

/**
 * Volume da série: `reps × weightKg` quando os dois existem e a carga é
 * finita; sem carga (ou carga não finita) soma 0 (regras 4 e 12).
 */
private fun SetLogEntity.chartVolumeKg(): Double {
    val repsValue = reps ?: return 0.0
    val weightValue = weightKg?.takeIf { it.isFinite() } ?: return 0.0
    return repsValue * weightValue
}
