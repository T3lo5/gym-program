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

    @Test
    fun sessao_manual_com_plan_id_zero() {
        val dto = BackupSession(
            id = 50,
            planId = 0,
            dayId = 0,
            planName = "",
            dayName = "",
            status = "completed",
            startedAt = 1,
            endedAt = 2,
            totalDurationSeconds = 1,
            notes = "",
            modality = "swim",
            source = "manual",
            title = "Natação",
            totalDistanceMeters = 1500.0,
            poolLengthMeters = 25,
            stroke = "crawl",
            sessionRpe = 7.0,
            exercises = emptyList(),
        )

        val entity = BackupMapper.toEntity(dto, planId = dto.planId)

        assertEquals(0L, entity.planId)
        assertEquals(0L, entity.dayId)
        assertEquals("swim", entity.modality)
        assertEquals("manual", entity.source)
        assertEquals("Natação", entity.title)
    }

    @Test
    fun sessao_manual_round_trip() {
        val session = WorkoutSessionEntity(
            id = 50,
            planId = 0,
            dayId = 0,
            planName = "",
            dayName = "Natação",
            status = "completed",
            startedAt = 1_760_000_000_000L,
            endedAt = 1_760_002_700_000L,
            totalDurationSeconds = 2_700L,
            notes = "8 x 100 m",
            modality = "swim",
            source = "manual",
            title = "Natação",
            totalDistanceMeters = 1_500.0,
            poolLengthMeters = 25,
            stroke = "crawl",
            sessionRpe = 7.0,
        )

        val dto = BackupMapper.toDto(session, exercises = emptyList())
        val volta = BackupMapper.toEntity(dto, planId = dto.planId)

        assertEquals(session, volta)
        assertEquals(0L, volta.planId)
        assertEquals(0L, volta.dayId)
        assertEquals("manual", volta.source)
        assertEquals(emptyList<Any>(), dto.exercises)
    }

    @Test
    fun sessao_com_piscina_e_stroke() {
        val dto = BackupSession(
            id = 50,
            planId = 0,
            dayId = 0,
            planName = "",
            dayName = "",
            status = "completed",
            startedAt = 1,
            endedAt = 60,
            totalDurationSeconds = 59,
            notes = "8 x 100 m",
            modality = "swim",
            source = "manual",
            title = "Natação",
            totalDistanceMeters = 1500.0,
            poolLengthMeters = 50,
            stroke = "costas",
            sessionRpe = 6.5,
            exercises = emptyList(),
        )

        val entity = BackupMapper.toEntity(dto, planId = dto.planId)

        assertEquals(1500.0, entity.totalDistanceMeters!!, 0.0)
        assertEquals(50, entity.poolLengthMeters!!)
        assertEquals("costas", entity.stroke)
        assertEquals(6.5, entity.sessionRpe!!, 0.0)
        assertEquals("8 x 100 m", entity.notes)
    }

    @Test
    fun dia_sem_weekday() {
        val dto = BackupDay(
            id = 10,
            planId = 1,
            name = "A",
            dayOrder = 0,
            notes = "",
            isRestDay = false,
            modality = "run",
            weekday = null,
            exercises = emptyList(),
        )

        val entity = BackupMapper.toEntity(dto, planId = dto.planId)

        assertEquals("run", entity.modality)
        assertEquals(null, entity.weekday)
    }

    @Test
    fun exercicio_sem_alvos() {
        val dto = BackupPlanExercise(
            id = 100,
            exerciseId = "e",
            exerciseName = "Ex",
            primaryMuscles = "",
            equipment = "",
            exerciseOrder = 0,
            sets = 3,
            targetReps = "8-12",
            targetRpe = null,
            restSeconds = 90,
            notes = "",
            isSupersetWithPrevious = false,
            dayId = 10,
            metricType = "distance_duration",
            targetDurationSeconds = null,
            targetDistanceMeters = null,
            targetPaceSecondsPerKm = null,
            targetHrZone = null,
        )

        val entity = BackupMapper.toEntity(dto, dayId = dto.dayId)

        assertEquals("distance_duration", entity.metricType)
        assertEquals(null, entity.targetDurationSeconds)
        assertEquals(null, entity.targetDistanceMeters)
        assertEquals(null, entity.targetPaceSecondsPerKm)
        assertEquals(null, entity.targetHrZone)
    }
}
