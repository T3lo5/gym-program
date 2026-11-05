package com.treinos.app.ui.history

import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutSessionEntity
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryMetricsTest {

    private val day = LocalDate.of(2026, 11, 10)
    private val chestOnly = mapOf("ex1" to "chest")

    @Test
    fun conta_apenas_sessoes_concluidas() {
        val sessions = listOf(
            session(
                id = 1,
                exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0))),
            ),
            session(
                id = 2,
                exercises = listOf(exercise("ex1", set(reps = 8, weight = 40.0))),
            ),
            session(
                id = 3,
                status = "in_progress",
                exercises = listOf(exercise("ex1", set(reps = 5, weight = 40.0))),
            ),
        )

        val metrics = buildHistoryMetrics(sessions, chestOnly)

        assertEquals(3, metrics.sessionCount)
        assertEquals(2, metrics.completedCount)
        assertEquals(2, metrics.totalSets)
        assertEquals(720.0, metrics.totalVolumeKg, EPSILON)
    }

    @Test
    fun ignora_series_warmup() {
        val sessions = listOf(
            session(
                id = 1,
                exercises = listOf(
                    exercise(
                        "ex1",
                        set(reps = 12, weight = 20.0, isWarmup = true),
                        set(reps = 10, weight = 40.0),
                    ),
                ),
            ),
        )

        val metrics = buildHistoryMetrics(sessions, chestOnly)

        assertEquals(1, metrics.totalSets)
        assertEquals(10, metrics.totalReps)
        assertEquals(400.0, metrics.totalVolumeKg, EPSILON)
    }

    @Test
    fun soma_volume_apenas_com_reps_e_carga() {
        val sessions = listOf(
            session(
                id = 1,
                exercises = listOf(
                    exercise(
                        "ex1",
                        set(reps = 10, weight = 40.0),
                        set(reps = 8, weight = 40.0),
                        set(reps = 12, weight = 30.0),
                        set(reps = 10, weight = null),
                        set(reps = null, weight = 40.0),
                    ),
                ),
            ),
        )

        val metrics = buildHistoryMetrics(sessions, chestOnly)

        assertEquals(1080.0, metrics.totalVolumeKg, EPSILON)
        assertEquals(5, metrics.totalSets)
        assertEquals(40, metrics.totalReps)
    }

    @Test
    fun conta_series_sem_carga() {
        val sessions = listOf(
            session(
                id = 1,
                exercises = listOf(
                    exercise(
                        "ex1",
                        set(reps = 15, weight = null),
                        set(reps = 8, weight = 30.0, completed = false),
                    ),
                ),
            ),
        )

        val metrics = buildHistoryMetrics(sessions, chestOnly)

        assertEquals(1, metrics.totalSets)
        assertEquals(15, metrics.totalReps)
        assertEquals(0.0, metrics.totalVolumeKg, EPSILON)
        assertTrue(metrics.volumeByMuscle.isEmpty())
    }

    @Test
    fun divide_volume_entre_musculos() {
        val sessions = listOf(
            session(id = 1, exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0)))),
        )

        val metrics = buildHistoryMetrics(sessions, muscleByExerciseId = mapOf("ex1" to "chest,back"))

        assertEquals(400.0, metrics.totalVolumeKg, EPSILON)
        assertEquals(200.0, metrics.volumeByMuscle.getValue("Peito"), EPSILON)
        assertEquals(200.0, metrics.volumeByMuscle.getValue("Costas"), EPSILON)
    }

    @Test
    fun mapeia_exercicio_desconhecido_para_outros() {
        val sessions = listOf(
            session(
                id = 1,
                exercises = listOf(
                    exercise("ausente-no-plano", set(reps = 10, weight = 40.0)),
                    exercise("sem-musculo", set(reps = 5, weight = 10.0)),
                    exercise("ex1", set(reps = 4, weight = 10.0)),
                    exercise("ex2", set(reps = 6, weight = 5.0)),
                ),
            ),
        )

        val metrics = buildHistoryMetrics(
            sessions = sessions,
            muscleByExerciseId = mapOf(
                "sem-musculo" to "",
                "ex1" to "chest",
                "ex2" to "sphinx",
            ),
        )

        assertEquals(3, metrics.volumeByMuscle.size)
        assertEquals(450.0, metrics.volumeByMuscle.getValue(OTHERS_MUSCLE_LABEL), EPSILON)
        assertEquals(40.0, metrics.volumeByMuscle.getValue("Peito"), EPSILON)
        assertEquals(30.0, metrics.volumeByMuscle.getValue("sphinx"), EPSILON)
    }

    @Test
    fun soma_duracao_de_sessoes_concluidas() {
        val sessions = listOf(
            session(id = 1, durationSeconds = 2_700L),
            session(id = 2, durationSeconds = 900L),
            session(id = 3, status = "in_progress", durationSeconds = 60L),
        )

        val metrics = buildHistoryMetrics(sessions, chestOnly)

        assertEquals(3_600L, metrics.totalDurationSeconds)
    }

    @Test
    fun filtra_por_periodo_inclusivo() {
        val outside = session(
            id = 1,
            startedAt = millis(LocalDate.of(2026, 11, 9)),
            exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0))),
        )
        val inside = session(
            id = 2,
            startedAt = millis(LocalDate.of(2026, 11, 10)),
            exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0))),
        )

        val november = buildHistoryMetrics(
            sessions = listOf(outside, inside),
            muscleByExerciseId = chestOnly,
            from = LocalDate.of(2026, 11, 10),
            to = LocalDate.of(2026, 11, 30),
        )

        assertEquals(1, november.sessionCount)
        assertEquals(400.0, november.totalVolumeKg, EPSILON)

        // 23:30 de 31/12 continua sendo 31/12 no fuso local.
        val newYearEveNight = session(
            id = 3,
            startedAt = LocalDate.of(2026, 12, 31)
                .atTime(23, 30)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli(),
            exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0))),
        )

        assertEquals(LocalDate.of(2026, 12, 31), sessionDay(newYearEveNight))
        assertEquals(
            1,
            buildHistoryMetrics(
                sessions = listOf(newYearEveNight),
                muscleByExerciseId = chestOnly,
                from = LocalDate.of(2026, 12, 31),
                to = LocalDate.of(2026, 12, 31),
            ).completedCount,
        )
        assertEquals(
            0,
            buildHistoryMetrics(
                sessions = listOf(newYearEveNight),
                muscleByExerciseId = chestOnly,
                from = LocalDate.of(2026, 12, 1),
                to = LocalDate.of(2026, 12, 30),
            ).completedCount,
        )
    }

    @Test
    fun lida_com_lista_vazia() {
        val metrics = buildHistoryMetrics(emptyList(), chestOnly)

        assertEquals(0, metrics.sessionCount)
        assertEquals(0, metrics.completedCount)
        assertEquals(0, metrics.totalSets)
        assertEquals(0, metrics.totalReps)
        assertEquals(0.0, metrics.totalVolumeKg, EPSILON)
        assertEquals(0L, metrics.totalDurationSeconds)
        assertTrue(metrics.volumeByMuscle.isEmpty())
    }

    private fun session(
        id: Long,
        status: String = "completed",
        startedAt: Long = millis(day),
        durationSeconds: Long = 60L,
        exercises: List<SessionExerciseWithSets> = emptyList(),
    ) = SessionWithExercises(
        session = WorkoutSessionEntity(
            id = id,
            status = status,
            startedAt = startedAt,
            totalDurationSeconds = durationSeconds,
        ),
        exercises = exercises,
    )

    private fun exercise(
        exerciseId: String,
        vararg sets: SetLogEntity,
    ) = SessionExerciseWithSets(
        exercise = SessionExerciseEntity(id = 1, sessionId = 1, exerciseId = exerciseId),
        sets = sets.mapIndexed { index, set -> set.copy(setNumber = index + 1) },
    )

    private fun set(
        reps: Int?,
        weight: Double?,
        completed: Boolean = true,
        isWarmup: Boolean = false,
    ) = SetLogEntity(
        id = 0L,
        exerciseLogId = 1L,
        setNumber = 1,
        reps = reps,
        weightKg = weight,
        isCompleted = completed,
        isWarmup = isWarmup,
    )

    private fun millis(date: LocalDate): Long =
        date.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private companion object {
        const val EPSILON = 0.0001
    }
}
