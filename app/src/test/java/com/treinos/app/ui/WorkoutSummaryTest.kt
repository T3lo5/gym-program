package com.treinos.app.ui

import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * H3-53: regras do resumo pós-treino — volume, séries realizadas/planejadas,
 * duração e contagem de exercícios (função pura, JVM).
 */
class WorkoutSummaryTest {

    @Test
    fun volume_soma_carga_x_reps() {
        val summary = WorkoutSummary.from(
            session(
                durationSeconds = 3_600L,
                exercises = listOf(
                    exercise(
                        plannedSets = 3,
                        set(reps = 10, weight = 40.0),
                        set(reps = 8, weight = 50.0),
                    ),
                ),
            ),
        )

        assertEquals(2, summary.setsDone)
        assertEquals(800.0, summary.volumeKg, EPSILON)
    }

    @Test
    fun sem_carga_volume_zero() {
        val summary = WorkoutSummary.from(
            session(
                durationSeconds = 600L,
                exercises = listOf(
                    exercise(
                        plannedSets = 2,
                        set(reps = 10, weight = null),
                        set(reps = 12, weight = 0.0),
                    ),
                ),
            ),
        )

        // Séries sem carga contam como feitas e somam zero — nunca NaN.
        assertEquals(2, summary.setsDone)
        assertEquals(0.0, summary.volumeKg, EPSILON)
    }

    @Test
    fun sem_alvo_mostra_so_realizadas() {
        val summary = WorkoutSummary.from(
            session(
                durationSeconds = 600L,
                exercises = listOf(
                    exercise(
                        plannedSets = 0,
                        set(reps = 10, weight = 40.0),
                        set(reps = 10, weight = 40.0),
                    ),
                ),
            ),
        )

        assertNull(summary.setsPlanned)
        assertEquals(2, summary.setsDone)
    }

    @Test
    fun duracao_menor_que_um_minuto() {
        val summary = WorkoutSummary.from(
            session(
                durationSeconds = 45L,
                exercises = listOf(exercise(plannedSets = 1, set(reps = 10, weight = 20.0))),
            ),
        )

        // A UI mostra "menos de 1 min" quando a duração fica abaixo de 60 s.
        assertEquals(45L, summary.durationSeconds)
    }

    @Test
    fun exercicios_concluidos_contam_uma_vez() {
        val summary = WorkoutSummary.from(
            session(
                durationSeconds = 1_200L,
                exercises = listOf(
                    exercise(
                        plannedSets = 3,
                        set(reps = 10, weight = 40.0),
                        set(reps = 10, weight = 40.0),
                        set(reps = 10, weight = 40.0),
                    ),
                    exercise(
                        plannedSets = 3,
                        set(reps = 10, weight = 40.0),
                        set(reps = 10, weight = 40.0),
                    ),
                ),
            ),
        )

        // O exercício completo conta uma vez, mesmo com várias séries; o
        // parcial (2 de 3) não entra.
        assertEquals(1, summary.exercisesDone)
        assertEquals(2, summary.exercisesTotal)
        assertEquals(5, summary.setsDone)
    }

    @Test
    fun series_de_aquecimento_nao_contam() {
        val summary = WorkoutSummary.from(
            session(
                durationSeconds = 600L,
                exercises = listOf(
                    exercise(
                        plannedSets = 2,
                        set(reps = 12, weight = 20.0, isWarmup = true),
                        set(reps = 10, weight = 40.0),
                    ),
                ),
            ),
        )

        assertEquals(1, summary.setsDone)
        assertEquals(400.0, summary.volumeKg, EPSILON)
    }

    @Test
    fun volume_ignora_carga_nao_finita() {
        val summary = WorkoutSummary.from(
            session(
                durationSeconds = 600L,
                exercises = listOf(
                    exercise(
                        plannedSets = 2,
                        set(reps = 10, weight = Double.NaN),
                        set(reps = 10, weight = Double.POSITIVE_INFINITY),
                    ),
                ),
            ),
        )

        assertEquals(2, summary.setsDone)
        assertEquals(0.0, summary.volumeKg, EPSILON)
    }

    @Test
    fun resumo_oito_de_dez_series() {
        // Critério de aceite 1: treino com 8 de 10 séries planejadas.
        val summary = WorkoutSummary.from(
            session(
                durationSeconds = 2_700L,
                exercises = listOf(
                    exercise(
                        plannedSets = 3,
                        set(reps = 10, weight = 40.0),
                        set(reps = 10, weight = 40.0),
                        set(reps = 10, weight = 40.0),
                    ),
                    exercise(
                        plannedSets = 3,
                        set(reps = 8, weight = 60.0),
                        set(reps = 8, weight = 60.0),
                        set(reps = 8, weight = 60.0),
                    ),
                    exercise(
                        plannedSets = 4,
                        set(reps = 12, weight = 20.0),
                        set(reps = 12, weight = 20.0),
                    ),
                ),
            ),
        )

        assertEquals(8, summary.setsDone)
        assertEquals(10, summary.setsPlanned)
        assertEquals(2_700L, summary.durationSeconds)
        assertEquals(2, summary.exercisesDone)
        assertEquals(3, summary.exercisesTotal)
        assertEquals(3_120.0, summary.volumeKg, EPSILON)
    }

    private fun session(
        durationSeconds: Long,
        exercises: List<SessionExerciseWithSets>,
    ) = SessionWithExercises(
        session = WorkoutSessionEntity(
            id = 1L,
            status = "completed",
            totalDurationSeconds = durationSeconds,
            modality = "strength",
        ),
        exercises = exercises,
    )

    private fun exercise(
        plannedSets: Int,
        vararg sets: SetLogEntity,
    ) = SessionExerciseWithSets(
        exercise = SessionExerciseEntity(
            id = 1L,
            sessionId = 1L,
            exerciseId = "ex1",
            plannedSets = plannedSets,
        ),
        sets = sets.mapIndexed { index, set -> set.copy(setNumber = index + 1) },
    )

    private fun set(
        reps: Int?,
        weight: Double?,
        isWarmup: Boolean = false,
    ) = SetLogEntity(
        id = 0L,
        exerciseLogId = 1L,
        setNumber = 1,
        reps = reps,
        weightKg = weight,
        isWarmup = isWarmup,
    )

    private companion object {
        const val EPSILON = 0.0001
    }
}
