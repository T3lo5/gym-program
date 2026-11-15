package com.treinos.app.ui.history

import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.ui.MAX_SET_REPS
import com.treinos.app.ui.MIN_SET_REPS
import com.treinos.app.ui.SessionKind
import com.treinos.app.ui.progression.OneRepMaxFormula
import com.treinos.app.ui.progression.PersonalRecord
import com.treinos.app.ui.progression.RecordSet
import com.treinos.app.ui.progression.estimateOneRepMax
import com.treinos.app.ui.progression.isPersonalRecord
import com.treinos.app.ui.progression.personalRecord
import com.treinos.app.ui.progression.recordSetsFor

/**
 * H4-3: drill-down do histórico por exercício — agregação pura derivada das
 * [SessionWithExercises] já carregadas pelo Room. Sem I/O, sem rede, sem
 * mudança de schema e sem gravar nada: abrir/fechar a folha não altera dados
 * e modo avião não muda o resultado (regra 13).
 *
 * Reusa os contratos entregues — [PersonalRecord] e [personalRecord] da H4-1,
 * [ExerciseProgressSeries] e [exerciseProgressSeries] da H4-2 — em vez de
 * reimplementar PR ou gráfico.
 */

/** Resumo de uma sessão concluída que contém o exercício. */
data class ExerciseSessionSummary(
    val sessionId: Long,
    val startedAtMillis: Long,
    /** Melhor série da sessão (regra 4); `null` = peso corporal. */
    val bestSetWeightKg: Double?,
    val bestSetReps: Int?,
    /** Σ (reps × carga) das séries válidas; série sem carga soma 0. */
    val volumeKg: Double,
    /** Quantidade de séries válidas da sessão (regra 5). */
    val sets: Int,
    /** 1RM estimado da melhor série na fórmula escolhida; `null` sem carga. */
    val estimated1RmKg: Double?,
    /** Recorde progressivo: superou todas as sessões anteriores (regra 6). */
    val isPersonalRecord: Boolean,
)

/** Visão focada em um exercício: sessões, recorde de todos os tempos e série. */
data class ExerciseHistory(
    val exerciseId: String,
    val exerciseName: String,
    /** Resumos ordenados por `startedAt` decrescente — o mais recente primeiro. */
    val sessions: List<ExerciseSessionSummary>,
    /** Melhor série de todos os tempos (H4-1); `null` sem histórico válido. */
    val allTimeRecord: PersonalRecord?,
    /** Série cronológica do gráfico (H4-2), métrica `BEST_SET_KG`. */
    val series: ExerciseProgressSeries,
)

/**
 * Monta o histórico de um exercício a partir das sessões concluídas de
 * musculação (regra 1). Sessões em andamento ficam de fora — a sessão atual
 * aparece na tela de treino e não entra na lista nem no recorde.
 *
 * - Séries válidas: concluídas, fora do aquecimento, reps em 1..100 (regra 2).
 * - Melhor série: maior carga finita > 0, empate por maior reps; sem carga,
 *   a série com mais reps (regra 4).
 * - `isPersonalRecord` é recorde progressivo em ordem cronológica, comparado
 *   pela regra de `isPersonalRecord` da H4-1; a primeira sessão válida é PR
 *   e empate não supera (regra 6).
 * - Sessão sem séries válidas do exercício não gera linha (regra 11).
 * - Sessões com o mesmo `startedAt` mantêm ordem estável por `sessionId`
 *   decrescente (regra 12).
 * - `exerciseName` vazio cai para o primeiro nome não vazio do próprio
 *   histórico e, sem nenhum, para o `exerciseId`.
 */
fun buildExerciseHistory(
    sessions: List<SessionWithExercises>,
    exerciseId: String,
    exerciseName: String = "",
    formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
): ExerciseHistory {
    val eligible = sessions
        .filter { it.session.status == STATUS_COMPLETED }
        .filter { SessionKind.fromModality(it.session.modality) == SessionKind.STRENGTH }
        .sortedWith(
            compareByDescending<SessionWithExercises> { it.session.startedAt }
                .thenByDescending { it.session.id },
        )

    // Sessão → (séries válidas, melhor série); sem série válida, fora (regra 11).
    val bySession: List<Triple<SessionWithExercises, List<RecordSet>, RecordSet>> = eligible
        .mapNotNull { session ->
            val validSets = recordSetsFor(listOf(session), exerciseId).filter(::isValidSet)
            val best = validSets.maxWithOrNull(bestSetComparator()) ?: return@mapNotNull null
            Triple(session, validSets, best)
        }

    // Recorde progressivo (regra 6): percorre em ordem cronológica — o mais
    // antigo primeiro — marcando quem supera o melhor de todas as anteriores.
    val isRecordBySessionId = HashMap<Long, Boolean>(bySession.size)
    var runningRecord: PersonalRecord? = null
    bySession.reversed().forEach { (session, _, best) ->
        val isRecord = isPersonalRecord(best, runningRecord, formula)
        isRecordBySessionId[session.session.id] = isRecord
        if (isRecord) {
            runningRecord = PersonalRecord(
                exerciseId = exerciseId,
                weightKg = best.validWeightKg(),
                reps = best.reps,
                estimated1RmKg = estimateOneRepMax(best.weightKg, best.reps, formula),
                achievedAtMillis = best.sessionStartedAt,
            )
        }
    }

    val summaries = bySession.map { (session, validSets, best) ->
        ExerciseSessionSummary(
            sessionId = session.session.id,
            startedAtMillis = session.session.startedAt,
            bestSetWeightKg = best.validWeightKg(),
            bestSetReps = best.reps,
            volumeKg = validSets.sumOf { set -> set.volumeKg() },
            sets = validSets.size,
            estimated1RmKg = estimateOneRepMax(best.weightKg, best.reps, formula),
            isPersonalRecord = isRecordBySessionId.getValue(session.session.id),
        )
    }

    return ExerciseHistory(
        exerciseId = exerciseId,
        exerciseName = exerciseName.ifBlank {
            eligible
                .asSequence()
                .flatMap { it.exercises.asSequence() }
                .filter { it.exercise.exerciseId == exerciseId }
                .map { it.exercise.exerciseName }
                .firstOrNull { it.isNotBlank() }
                ?: exerciseId
        },
        sessions = summaries,
        allTimeRecord = personalRecord(eligible, exerciseId, formula),
        series = exerciseProgressSeries(eligible, exerciseId, ExerciseChartMetric.BEST_SET_KG),
    )
}

/** Série elegível a resumo e PR: mesma regra de elegibilidade da H4-1. */
private fun isValidSet(set: RecordSet): Boolean =
    set.isCompleted &&
        !set.isWarmup &&
        set.reps != null &&
        set.reps in MIN_SET_REPS..MAX_SET_REPS

/**
 * Ordem da melhor série da sessão (regra 4): maior carga finita > 0 (peso
 * corporal vale 0, então qualquer carga vence), empate por maior reps.
 */
private fun bestSetComparator(): Comparator<RecordSet> =
    compareBy(
        { it.validWeightKg() ?: 0.0 },
        { it.reps ?: 0 },
    )

/** Carga utilizável: finita e > 0; `null`, 0, NaN e Infinity são peso corporal. */
private fun RecordSet.validWeightKg(): Double? =
    weightKg?.takeIf { it.isFinite() && it > 0.0 }

/** Volume da série: `reps × weightKg`; sem carga (ou não finita) soma 0. */
private fun RecordSet.volumeKg(): Double {
    val repsValue = reps ?: return 0.0
    val weightValue = weightKg?.takeIf { it.isFinite() } ?: return 0.0
    return repsValue * weightValue
}
