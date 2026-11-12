package com.treinos.app.data.wear

import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearSessionMapperTest {

    private fun setLog(setNumber: Int, reps: Int? = null, weightKg: Double? = null): SetLogEntity =
        SetLogEntity(
            id = 0,
            exerciseLogId = 0,
            setNumber = setNumber,
            reps = reps,
            weightKg = weightKg,
            rpe = null,
            durationSeconds = null,
            distanceMeters = null,
            isCompleted = true,
            isWarmup = false,
            restTakenSeconds = null,
            completedAt = null,
        )

    private fun sessionExercise(
        id: Long,
        name: String,
        plannedSets: Int,
        completedSets: Int = 0,
        sets: List<SetLogEntity> = emptyList(),
        targetReps: String = "",
    ): SessionExerciseWithSets =
        SessionExerciseWithSets(
            exercise = SessionExerciseEntity(
                id = id,
                sessionId = 1,
                exerciseId = "ex-$id",
                exerciseName = name,
                exerciseOrder = 0,
                plannedSets = plannedSets,
                completedSets = completedSets,
                restSeconds = 90,
                targetReps = targetReps,
            ),
            sets = sets,
        )

    private fun session(
        vararg exercises: SessionExerciseWithSets,
        modality: String = "strength",
    ): SessionWithExercises =
        SessionWithExercises(
            session = WorkoutSessionEntity(
                id = 10,
                planId = 1,
                dayId = 1,
                planName = "Plano",
                dayName = "Treino A",
                status = "in_progress",
                modality = modality,
            ),
            exercises = exercises.toList(),
        )

    @Test
    fun mapeia_primeiro_exercicio() {
        val sessao = session(
            sessionExercise(1, "Supino reto", 3, sets = listOf(setLog(1, 10, 60.0)), targetReps = "8-12"),
            sessionExercise(2, "Agachamento", 4, targetReps = "10"),
        )
        val estado = WearSessionMapper.toWearState(sessao, 0, restRemainingSeconds = 45)

        assertEquals(10L, estado.sessionId)
        assertEquals("Treino A", estado.dayName)
        assertEquals("strength", estado.modality)
        assertEquals(0, estado.exerciseIndex)
        assertEquals(2, estado.exerciseCount)
        assertEquals("Supino reto", estado.exerciseName)
        assertEquals(2, estado.setNumber)
        assertEquals(3, estado.plannedSets)
        assertEquals("8-12", estado.targetReps)
        assertEquals(60.0, estado.lastWeightKg!!, 0.001)
        assertEquals(10, estado.lastReps)
        assertEquals(90, estado.restSeconds)
        assertEquals(45, estado.restRemainingSeconds)
        assertEquals("Agachamento", estado.nextExerciseName)
        assertEquals("in_progress", estado.status)
    }

    @Test
    fun alvo_vazio_quando_sessao_sem_alvo() {
        val sessao = session(sessionExercise(1, "Supino reto", 3))
        val estado = WearSessionMapper.toWearState(sessao, 0, restRemainingSeconds = null)

        assertEquals("", estado.targetReps)
    }

    @Test
    fun mapeia_ultimo_exercicio_sem_proximo() {
        val sessao = session(
            sessionExercise(1, "Supino reto", 3),
            sessionExercise(2, "Agachamento", 4),
        )
        val estado = WearSessionMapper.toWearState(sessao, 1, restRemainingSeconds = null)

        assertEquals(1, estado.exerciseIndex)
        assertEquals("Agachamento", estado.exerciseName)
        assertEquals(4, estado.plannedSets)
        assertNull(estado.nextExerciseName)
        assertNull(estado.restRemainingSeconds)
    }

    @Test
    fun inclui_ultima_carga_e_reps() {
        val sessao = session(
            sessionExercise(
                1,
                "Supino reto",
                3,
                sets = listOf(setLog(1, 8, 50.0), setLog(2, 10, 55.0)),
            ),
        )
        val estado = WearSessionMapper.toWearState(sessao, 0, restRemainingSeconds = 0)

        assertEquals(55.0, estado.lastWeightKg!!, 0.001)
        assertEquals(10, estado.lastReps)
        assertEquals(3, estado.setNumber)
    }

    @Test
    fun sessao_finalizada_vira_status_finished() {
        val base = session(sessionExercise(1, "Supino reto", 3))
        val sessao = base.copy(session = base.session.copy(status = "finished"))
        val estado = WearSessionMapper.toWearState(sessao, 0, restRemainingSeconds = null)

        assertEquals("finished", estado.status)
    }

    @Test
    fun modality_da_sessao_vai_no_estado() {
        val sessao = session(
            sessionExercise(1, "Supino reto", 3),
            modality = "run",
        )
        val estado = WearSessionMapper.toWearState(sessao, 0, restRemainingSeconds = null)

        assertEquals("run", estado.modality)
    }

    @Test
    fun sessao_de_corrida_sem_exercicios_mapeia_coerente() {
        val sessao = session(modality = "mobility")
        val estado = WearSessionMapper.toWearState(sessao, 0, restRemainingSeconds = null)

        assertEquals("mobility", estado.modality)
        assertEquals("Treino A", estado.dayName)
        assertEquals(0, estado.exerciseCount)
        assertEquals("", estado.exerciseName)
        assertEquals("in_progress", estado.status)
    }

    @Test
    fun ignora_sessao_sem_exercicios() {
        val sessao = session()
        val estado = WearSessionMapper.toWearState(sessao, 0, restRemainingSeconds = null)

        assertEquals(0, estado.exerciseCount)
        assertEquals("", estado.exerciseName)
        assertEquals(0, estado.plannedSets)
        assertEquals("in_progress", estado.status)
    }
}
