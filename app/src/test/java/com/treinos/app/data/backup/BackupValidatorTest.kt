package com.treinos.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupValidatorTest {

    private fun validPlan(id: Long = 1, name: String = "Plano", days: List<BackupDay> = emptyList()): BackupPlan =
        BackupPlan(
            id = id,
            serverPlanId = null,
            name = name,
            description = "",
            goal = "hypertrophy",
            daysPerWeek = 3,
            isActive = true,
            generatedByAi = false,
            createdAt = 1000L,
            updatedAt = 1000L,
            version = 1,
            nextReviewAt = null,
            reviewAfterWeeks = 4,
            source = "local",
            days = days,
        )

    private fun validDay(id: Long = 10, planId: Long = 1, exercises: List<BackupPlanExercise> = emptyList()): BackupDay =
        BackupDay(
            id = id,
            planId = planId,
            name = "Dia",
            dayOrder = 0,
            notes = "",
            isRestDay = false,
            exercises = exercises,
        )

    private fun validSession(id: Long = 50, planId: Long = 1, exercises: List<BackupSessionExercise> = emptyList()): BackupSession =
        BackupSession(
            id = id,
            planId = planId,
            dayId = 10,
            planName = "Plano",
            dayName = "Dia",
            status = "completed",
            startedAt = 1000L,
            endedAt = null,
            totalDurationSeconds = 100,
            notes = "",
            exercises = exercises,
        )

    private fun validFile(
        plans: List<BackupPlan> = emptyList(),
        sessions: List<BackupSession> = emptyList(),
        schemaVersion: Int = 1,
    ): BackupFile =
        BackupFile(
            schemaVersion = schemaVersion,
            exportedAt = "2026-11-03T18:00:00Z",
            app = BackupApp("com.treinos.app", "1.0.0", 1),
            deviceId = "device-1",
            plans = plans,
            sessions = sessions,
        )

    @Test
    fun backupVazioEValido() {
        val result = BackupValidator.validate(validFile())
        assertTrue(result.valid)
        assertEquals(0, result.preview?.plans)
        assertEquals(0, result.preview?.sessions)
    }

    @Test
    fun rejeitaIdDuplicado() {
        val planoDuplicado = validPlan(id = 1)
        val planoDuplicado2 = validPlan(id = 1)
        val result = BackupValidator.validate(validFile(plans = listOf(planoDuplicado, planoDuplicado2)))
        assertFalse(result.valid)
        assertEquals("IDs de plano duplicados no backup.", result.message)
    }

    @Test
    fun rejeitaDiaOrfao() {
        val diaOrfao = validDay(id = 10, planId = 999)
        val plano = validPlan(id = 1, days = listOf(diaOrfao))
        val result = BackupValidator.validate(validFile(plans = listOf(plano)))
        assertFalse(result.valid)
        assertEquals("Dia 'Dia' não pertence ao plano 'Plano' (planId órfão).", result.message)
    }

    @Test
    fun rejeitaExercicioOrfao() {
        val exercicioOrfao = BackupPlanExercise(
            id = 100,
            exerciseId = "EX",
            exerciseName = "Ex",
            primaryMuscles = "chest",
            equipment = "barbell",
            exerciseOrder = 0,
            sets = 3,
            targetReps = "8-12",
            targetRpe = 8.0,
            restSeconds = 90,
            notes = "",
            isSupersetWithPrevious = false,
            dayId = 999,
        )
        val dia = validDay(id = 10, planId = 1, exercises = listOf(exercicioOrfao))
        val plano = validPlan(id = 1, days = listOf(dia))
        val result = BackupValidator.validate(validFile(plans = listOf(plano)))
        assertFalse(result.valid)
        assertEquals("Exercício 'Ex' não pertence ao dia 'Dia' (dayId órfão).", result.message)
    }

    @Test
    fun rejeitaSetLogOrfao() {
        val setLogOrfao = BackupSetLog(
            id = 5000,
            setNumber = 1,
            reps = 10,
            weightKg = 40.0,
            rpe = 8.0,
            durationSeconds = null,
            distanceMeters = null,
            isCompleted = true,
            isWarmup = false,
            restTakenSeconds = 90,
            completedAt = 1000L,
            exerciseLogId = 999,
        )
        val exercicioSessao = BackupSessionExercise(
            id = 500,
            exerciseId = "EX",
            exerciseName = "Ex",
            exerciseOrder = 0,
            plannedSets = 3,
            completedSets = 1,
            restSeconds = 90,
            startedAt = 1000L,
            endedAt = null,
            sessionId = 50,
            sets = listOf(setLogOrfao),
        )
        val sessao = validSession(id = 50, planId = 1, exercises = listOf(exercicioSessao))
        val result = BackupValidator.validate(validFile(sessions = listOf(sessao)))
        assertFalse(result.valid)
        assertEquals("Série 1 não pertence ao exercício 'Ex' (exerciseLogId órfão).", result.message)
    }

    @Test
    fun rejeitaSetNumberInvalido() {
        val setLogInvalido = BackupSetLog(
            id = 5000,
            setNumber = 0,
            reps = 10,
            weightKg = 40.0,
            rpe = 8.0,
            durationSeconds = null,
            distanceMeters = null,
            isCompleted = true,
            isWarmup = false,
            restTakenSeconds = 90,
            completedAt = 1000L,
            exerciseLogId = 500,
        )
        val exercicioSessao = BackupSessionExercise(
            id = 500,
            exerciseId = "EX",
            exerciseName = "Ex",
            exerciseOrder = 0,
            plannedSets = 3,
            completedSets = 1,
            restSeconds = 90,
            startedAt = 1000L,
            endedAt = null,
            sessionId = 50,
            sets = listOf(setLogInvalido),
        )
        val sessao = validSession(id = 50, planId = 1, exercises = listOf(exercicioSessao))
        val result = BackupValidator.validate(validFile(sessions = listOf(sessao)))
        assertFalse(result.valid)
        assertEquals("setNumber da série 0 inválido no exercício 'Ex'.", result.message)
    }

    @Test
    fun rejeitaRpeForaDaFaixa() {
        val setLogRpeInvalido = BackupSetLog(
            id = 5000,
            setNumber = 1,
            reps = 10,
            weightKg = 40.0,
            rpe = 11.0,
            durationSeconds = null,
            distanceMeters = null,
            isCompleted = true,
            isWarmup = false,
            restTakenSeconds = 90,
            completedAt = 1000L,
            exerciseLogId = 500,
        )
        val exercicioSessao = BackupSessionExercise(
            id = 500,
            exerciseId = "EX",
            exerciseName = "Ex",
            exerciseOrder = 0,
            plannedSets = 3,
            completedSets = 1,
            restSeconds = 90,
            startedAt = 1000L,
            endedAt = null,
            sessionId = 50,
            sets = listOf(setLogRpeInvalido),
        )
        val sessao = validSession(id = 50, planId = 1, exercises = listOf(exercicioSessao))
        val result = BackupValidator.validate(validFile(sessions = listOf(sessao)))
        assertFalse(result.valid)
        val msg = result.message
        assertTrue(msg != null && msg.contains("rpe da série 1 fora da faixa"))
    }

    @Test
    fun aceitaSessaoSemPlano() {
        val sessaoSemPlano = validSession(id = 50, planId = 999)
        val result = BackupValidator.validate(validFile(sessions = listOf(sessaoSemPlano)))
        assertTrue(result.valid)
    }
}
