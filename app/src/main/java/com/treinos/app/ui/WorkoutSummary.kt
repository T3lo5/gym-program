package com.treinos.app.ui

import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SetLogEntity

/**
 * H3-53: resumo do treino recém-finalizado, exibido como card efêmero na Home.
 * Função pura sobre a sessão já gravada — sem Room e sem Android, testável em JVM.
 *
 * Convenções alinhadas com `buildHistoryMetrics` (H3-28): só contam séries
 * concluídas e que não são aquecimento; o volume soma carga × reps das séries
 * com carga > 0 e nunca devolve NaN/Infinity.
 */
data class WorkoutSummary(
    val setsDone: Int,
    /** Alvo do plano do dia; `null` quando a sessão não tem contagem (alvo 0). */
    val setsPlanned: Int?,
    val volumeKg: Double,
    val durationSeconds: Long,
    val exercisesDone: Int,
    val exercisesTotal: Int,
) {
    companion object {
        /**
         * Espera a sessão já finalizada (`totalDurationSeconds` gravado por
         * `WorkoutRepository.finishSession`).
         */
        fun from(session: SessionWithExercises): WorkoutSummary {
            var setsDone = 0
            var volumeKg = 0.0
            var setsPlanned = 0
            var exercisesDone = 0
            session.exercises.forEach { exercise ->
                val planned = exercise.exercise.plannedSets.coerceAtLeast(0)
                setsPlanned += planned
                val counting = exercise.sets.count { it.countsAsSummarySet() }
                setsDone += counting
                // Exercício concluído conta uma vez, independente das séries:
                // feito quando as séries contáveis alcançam o alvo do plano
                // (sem alvo, basta uma série registrada).
                if (counting >= planned.coerceAtLeast(1)) exercisesDone += 1
                exercise.sets.filter { it.countsAsSummarySet() }.forEach { set ->
                    volumeKg += set.summaryVolumeKg()
                }
            }
            return WorkoutSummary(
                setsDone = setsDone,
                setsPlanned = setsPlanned.takeIf { it > 0 },
                volumeKg = if (volumeKg.isFinite()) volumeKg else 0.0,
                durationSeconds = session.session.totalDurationSeconds.coerceAtLeast(0L),
                exercisesDone = exercisesDone,
                exercisesTotal = session.exercises.size,
            )
        }

        /** Mesmo critério de série métrica do histórico (H3-28). */
        private fun SetLogEntity.countsAsSummarySet(): Boolean = isCompleted && !isWarmup

        /**
         * Séries sem carga contam como feitas e somam zero; carga <= 0, reps
         * ausentes e valores não finitos não geram volume.
         */
        private fun SetLogEntity.summaryVolumeKg(): Double {
            val repsValue = reps ?: return 0.0
            val weightValue = weightKg ?: return 0.0
            if (!weightValue.isFinite() || weightValue <= 0.0) return 0.0
            if (repsValue <= 0) return 0.0
            val volume = repsValue * weightValue
            return if (volume.isFinite()) volume else 0.0
        }
    }
}
