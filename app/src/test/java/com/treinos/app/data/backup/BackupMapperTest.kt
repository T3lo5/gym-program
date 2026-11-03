package com.treinos.app.data.backup

import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutDayEntity
import com.treinos.app.data.local.WorkoutPlanEntity
import com.treinos.app.data.local.WorkoutPlanExerciseEntity
import com.treinos.app.data.local.WorkoutSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupMapperTest {

    @Test
    fun entityParaDtoEVolta() {
        val plan = WorkoutPlanEntity(
            id = 1,
            serverPlanId = null,
            name = "Full Body A",
            description = "",
            goal = "hypertrophy",
            daysPerWeek = 4,
            isActive = true,
            generatedByAi = false,
            createdAt = 1760000000000L,
            updatedAt = 1760000000000L,
            version = 1,
            nextReviewAt = "2026-12-01",
            reviewAfterWeeks = 4,
            source = "local",
        )
        val day = WorkoutDayEntity(
            id = 10,
            planId = 1,
            name = "Treino A",
            dayOrder = 0,
            notes = "",
            isRestDay = false,
        )
        val exercise = WorkoutPlanExerciseEntity(
            id = 100,
            dayId = 10,
            exerciseId = "Barbell_Bench_Press",
            exerciseName = "Supino reto",
            primaryMuscles = "chest",
            equipment = "barbell",
            exerciseOrder = 0,
            sets = 3,
            targetReps = "8-12",
            targetRpe = 8.0,
            restSeconds = 90,
            notes = "",
            isSupersetWithPrevious = false,
        )

        val dtoPlan = BackupMapper.toDto(plan, listOf(BackupMapper.toDto(day, listOf(BackupMapper.toDto(exercise, day.id)))))
        val voltaPlan = BackupMapper.toEntity(dtoPlan)
        assertEquals(plan.id, voltaPlan.id)
        assertEquals(plan.name, voltaPlan.name)
        assertEquals(plan.goal, voltaPlan.goal)
        assertEquals(plan.daysPerWeek, voltaPlan.daysPerWeek)

        val dtoDay = BackupMapper.toDto(day, listOf(BackupMapper.toDto(exercise, day.id)))
        val voltaDay = BackupMapper.toEntity(dtoDay, planId = plan.id)
        assertEquals(day.id, voltaDay.id)
        assertEquals(day.planId, voltaDay.planId)
        assertEquals(day.name, voltaDay.name)

        val dtoEx = BackupMapper.toDto(exercise, day.id)
        val voltaEx = BackupMapper.toEntity(dtoEx, dayId = day.id)
        assertEquals(exercise.id, voltaEx.id)
        assertEquals(exercise.dayId, voltaEx.dayId)
        assertEquals(exercise.exerciseId, voltaEx.exerciseId)
        assertEquals(exercise.targetRpe, voltaEx.targetRpe)
    }

    @Test
    fun listasPlanasPreservamRelacoes() {
        val plan = WorkoutPlanEntity(
            id = 1,
            name = "Plano X",
            daysPerWeek = 3,
            isActive = true,
        )
        val day = WorkoutDayEntity(
            id = 10,
            planId = 1,
            name = "Dia 1",
            dayOrder = 0,
        )
        val ex = WorkoutPlanExerciseEntity(
            id = 100,
            dayId = 10,
            exerciseId = "EX_1",
            exerciseName = "Exercício 1",
            sets = 3,
        )
        val session = WorkoutSessionEntity(
            id = 50,
            planId = 1,
            dayId = 10,
            planName = "Plano X",
            dayName = "Dia 1",
            status = "completed",
            startedAt = 1000L,
        )
        val sessionEx = SessionExerciseEntity(
            id = 500,
            sessionId = 50,
            exerciseId = "EX_1",
            exerciseName = "Exercício 1",
            exerciseOrder = 0,
            plannedSets = 3,
            completedSets = 1,
        )
        val setLog = SetLogEntity(
            id = 5000,
            exerciseLogId = 500,
            setNumber = 1,
            reps = 10,
            weightKg = 40.0,
            rpe = 8.0,
            isCompleted = true,
        )

        val backupFile = BackupFile(
            schemaVersion = 1,
            exportedAt = "2026-11-03T18:00:00Z",
            app = BackupApp("com.treinos.app", "1.0.0", 1),
            deviceId = "device-1",
            plans = listOf(
                BackupMapper.toDto(plan, listOf(
                    BackupMapper.toDto(day, listOf(BackupMapper.toDto(ex, day.id)))
                ))
            ),
            sessions = listOf(
                BackupMapper.toDto(session, listOf(
                    BackupMapper.toDto(sessionEx, session.id, listOf(BackupMapper.toDto(setLog, sessionEx.id)))
                ))
            ),
        )

        val plans = backupFile.plans.map { BackupMapper.toEntity(it) }
        val days = backupFile.plans.flatMap { p ->
            p.days.map { BackupMapper.toEntity(it, planId = p.id) }
        }
        val planExercises = backupFile.plans.flatMap { p ->
            p.days.flatMap { d ->
                d.exercises.map { BackupMapper.toEntity(it, dayId = d.id) }
            }
        }
        val sessions = backupFile.sessions.map { BackupMapper.toEntity(it, planId = it.planId) }
        val sessionExercises = backupFile.sessions.flatMap { s ->
            s.exercises.map { BackupMapper.toEntity(it, sessionId = s.id) }
        }
        val setLogs = backupFile.sessions.flatMap { s ->
            s.exercises.flatMap { e ->
                e.sets.map { BackupMapper.toEntity(it, exerciseLogId = e.id) }
            }
        }

        assertEquals(1, plans.size)
        assertEquals(1, days.size)
        assertEquals(1L, days.first().planId)
        assertEquals(1, planExercises.size)
        assertEquals(10L, planExercises.first().dayId)
        assertEquals(1, sessions.size)
        assertEquals(1L, sessions.first().planId)
        assertEquals(10L, sessions.first().dayId)
        assertEquals(1, sessionExercises.size)
        assertEquals(50L, sessionExercises.first().sessionId)
        assertEquals(1, setLogs.size)
        assertEquals(500L, setLogs.first().exerciseLogId)
    }
}
