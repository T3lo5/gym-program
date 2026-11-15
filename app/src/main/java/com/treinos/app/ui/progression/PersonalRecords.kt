package com.treinos.app.ui.progression

import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.ui.MAX_SET_REPS
import com.treinos.app.ui.MIN_SET_REPS

/**
 * H4-1: recordes pessoais (PR) e 1RM estimado, derivados das séries já
 * registradas em `SetLogEntity`. Tudo é leitura pura — nada é gravado, nenhum
 * dado novo no Room, sem rede e sem backend.
 *
 * Regras da spec (docs/specs/H4-1-recordes-pessoais-1rm.md):
 * - Só contam séries concluídas, fora do aquecimento, com reps em 1..100.
 * - Carga só conta se finita e > 0; sem carga válida a série é de peso corporal.
 * - PR com carga é comparado por 1RM estimado estrito (`>`), nunca por carga
 *   isolada; empate de 1RM não é PR novo.
 * - Peso corporal: PR é a maior contagem de reps; empate não é PR novo.
 * - Série de 1 rep devolve a própria carga nas duas fórmulas: ela É o 1RM real
 *   (remove o artefato de ~3,3% do Epley e alinha as duas fórmulas na origem).
 */

/** Fórmula de estimativa de 1RM. A UI usa Epley por padrão. */
enum class OneRepMaxFormula { EPLEY, BRZYCKI }

/** Série normalizada para cálculo, sem dependência do Room. */
data class RecordSet(
    val sessionId: Long,
    val sessionStartedAt: Long,
    val reps: Int?,
    val weightKg: Double?,
    val rpe: Double? = null,
    val isCompleted: Boolean = true,
    val isWarmup: Boolean = false,
)

/**
 * Melhor registro de um exercício. [weightKg] `null` significa recorde de peso
 * corporal; [estimated1RmKg] `null` quando não há carga válida. Exibido, nunca
 * gravado.
 */
data class PersonalRecord(
    val exerciseId: String,
    val weightKg: Double?,
    val reps: Int?,
    val estimated1RmKg: Double?,
    val achievedAtMillis: Long?,
)

/** Limite superior de reps para o Brzycki (abaixo disso o denominador zera). */
private const val BRZYCKI_MAX_REPS = 36

/** Carga utilizável: finita e > 0; `null`, 0, NaN e Infinity são peso corporal. */
private fun validWeight(weightKg: Double?): Double? =
    weightKg?.takeIf { it.isFinite() && it > 0.0 }

/** Série elegível a PR: concluída, fora do aquecimento, com reps em 1..100. */
private fun isEligible(set: RecordSet): Boolean =
    set.isCompleted &&
        !set.isWarmup &&
        set.reps != null &&
        set.reps in MIN_SET_REPS..MAX_SET_REPS

/**
 * 1RM estimado a partir de uma série. Epley: `w × (1 + reps/30)`.
 * Brzycki: `w × 36/(37 − reps)`, só para reps em 1..36 (fora disso `null`).
 * Com `reps = 1` devolve a própria carga — a série é o 1RM real.
 * Sem carga válida (peso corporal) ou reps inválidas: `null`.
 */
fun estimateOneRepMax(
    weightKg: Double?,
    reps: Int?,
    formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
): Double? {
    val weight = validWeight(weightKg) ?: return null
    val repsValue = reps ?: return null
    if (repsValue !in MIN_SET_REPS..MAX_SET_REPS) return null
    if (repsValue == 1) return weight
    return when (formula) {
        OneRepMaxFormula.EPLEY -> weight * (1.0 + repsValue / 30.0)
        OneRepMaxFormula.BRZYCKI ->
            if (repsValue <= BRZYCKI_MAX_REPS) weight * BRZYCKI_NUMERATOR / (BRZYCKI_DENOMINATOR_BASE - repsValue)
            else null
    }
}

private const val BRZYCKI_NUMERATOR = 36.0
private const val BRZYCKI_DENOMINATOR_BASE = 37.0

/** Traduz o histórico do exercício para o modelo puro, sem filtrar. */
fun recordSetsFor(
    sessions: List<SessionWithExercises>,
    exerciseId: String,
): List<RecordSet> = sessions.flatMap { session ->
    session.exercises
        .filter { it.exercise.exerciseId == exerciseId }
        .flatMap { exercise ->
            exercise.sets.map { set ->
                RecordSet(
                    sessionId = session.session.id,
                    sessionStartedAt = session.session.startedAt,
                    reps = set.reps,
                    weightKg = set.weightKg,
                    rpe = set.rpe,
                    isCompleted = set.isCompleted,
                    isWarmup = set.isWarmup,
                )
            }
        }
}

/**
 * Melhor série de todos os tempos do exercício; `null` sem série válida.
 * Sessões `in_progress` contam: a série registrada é dado real (regra 11).
 */
fun personalRecord(
    sessions: List<SessionWithExercises>,
    exerciseId: String,
    formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
): PersonalRecord? {
    val best = recordSetsFor(sessions, exerciseId)
        .filter(::isEligible)
        .maxWithOrNull(recordComparator(formula)) ?: return null
    val weight = validWeight(best.weightKg)
    return PersonalRecord(
        exerciseId = exerciseId,
        weightKg = weight,
        reps = best.reps,
        estimated1RmKg = estimateOneRepMax(best.weightKg, best.reps, formula),
        achievedAtMillis = best.sessionStartedAt,
    )
}

/**
 * O candidato supera [record]? `record == null` = primeiro registro válido é PR.
 * Com carga: 1RM estimado estrito (`>`). Contra recorde de peso corporal, carga
 * sempre vence; peso corporal contra recorde com carga nunca vence.
 */
fun isPersonalRecord(
    candidate: RecordSet,
    record: PersonalRecord?,
    formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
): Boolean {
    if (!isEligible(candidate)) return false
    if (record == null) return true
    val candidateRm = estimateOneRepMax(candidate.weightKg, candidate.reps, formula)
    return if (record.estimated1RmKg == null) {
        if (candidateRm != null) {
            true
        } else {
            (candidate.reps ?: 0) > (record.reps ?: 0)
        }
    } else {
        candidateRm != null && candidateRm > record.estimated1RmKg
    }
}

/**
 * Ordem do melhor registro: 1RM estimado, depois carga, depois reps e, por
 * último, a sessão mais recente. Serve só para escolher o melhor registro —
 * empate continua não sendo PR novo em [isPersonalRecord].
 */
private fun recordComparator(formula: OneRepMaxFormula): Comparator<RecordSet> =
    Comparator { a, b ->
        val rmA = estimateOneRepMax(a.weightKg, a.reps, formula) ?: Double.NEGATIVE_INFINITY
        val rmB = estimateOneRepMax(b.weightKg, b.reps, formula) ?: Double.NEGATIVE_INFINITY
        if (rmA != rmB) return@Comparator rmA.compareTo(rmB)
        val wA = validWeight(a.weightKg) ?: Double.NEGATIVE_INFINITY
        val wB = validWeight(b.weightKg) ?: Double.NEGATIVE_INFINITY
        if (wA != wB) return@Comparator wA.compareTo(wB)
        val repsA = a.reps ?: 0
        val repsB = b.reps ?: 0
        if (repsA != repsB) return@Comparator repsA.compareTo(repsB)
        a.sessionStartedAt.compareTo(b.sessionStartedAt)
    }
