package com.treinos.app.data.backup

import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutDayEntity
import com.treinos.app.data.local.WorkoutPlanEntity
import com.treinos.app.data.local.WorkoutPlanExerciseEntity
import com.treinos.app.data.local.WorkoutSessionEntity

object BackupMapper {

    fun toDto(plan: WorkoutPlanEntity, days: List<BackupDay>): BackupPlan =
        BackupPlan(
            id = plan.id,
            serverPlanId = plan.serverPlanId,
            name = plan.name,
            description = plan.description,
            goal = plan.goal,
            daysPerWeek = plan.daysPerWeek,
            isActive = plan.isActive,
            generatedByAi = plan.generatedByAi,
            createdAt = plan.createdAt,
            updatedAt = plan.updatedAt,
            version = plan.version,
            nextReviewAt = plan.nextReviewAt,
            reviewAfterWeeks = plan.reviewAfterWeeks,
            source = plan.source,
            days = days,
        )

    fun toDto(day: WorkoutDayEntity, exercises: List<BackupPlanExercise>): BackupDay =
        BackupDay(
            id = day.id,
            planId = day.planId,
            name = day.name,
            dayOrder = day.dayOrder,
            notes = day.notes,
            isRestDay = day.isRestDay,
            modality = day.modality,
            weekday = day.weekday,
            exercises = exercises,
        )

    fun toDto(exercise: WorkoutPlanExerciseEntity, dayId: Long): BackupPlanExercise =
        BackupPlanExercise(
            id = exercise.id,
            exerciseId = exercise.exerciseId,
            exerciseName = exercise.exerciseName,
            primaryMuscles = exercise.primaryMuscles,
            equipment = exercise.equipment,
            exerciseOrder = exercise.exerciseOrder,
            sets = exercise.sets,
            targetReps = exercise.targetReps,
            targetRpe = exercise.targetRpe,
            restSeconds = exercise.restSeconds,
            notes = exercise.notes,
            isSupersetWithPrevious = exercise.isSupersetWithPrevious,
            dayId = dayId,
            metricType = exercise.metricType,
            targetDurationSeconds = exercise.targetDurationSeconds,
            targetDistanceMeters = exercise.targetDistanceMeters,
            targetPaceSecondsPerKm = exercise.targetPaceSecondsPerKm,
            targetHrZone = exercise.targetHrZone,
        )

    fun toDto(session: WorkoutSessionEntity, exercises: List<BackupSessionExercise>): BackupSession =
        BackupSession(
            id = session.id,
            planId = session.planId,
            dayId = session.dayId,
            planName = session.planName,
            dayName = session.dayName,
            status = session.status,
            startedAt = session.startedAt,
            endedAt = session.endedAt,
            totalDurationSeconds = session.totalDurationSeconds,
            notes = session.notes,
            modality = session.modality,
            source = session.source,
            title = session.title,
            totalDistanceMeters = session.totalDistanceMeters,
            poolLengthMeters = session.poolLengthMeters,
            stroke = session.stroke,
            sessionRpe = session.sessionRpe,
            exercises = exercises,
        )

    fun toDto(
        exercise: SessionExerciseEntity,
        sessionId: Long,
        sets: List<BackupSetLog>,
    ): BackupSessionExercise =
        BackupSessionExercise(
            id = exercise.id,
            exerciseId = exercise.exerciseId,
            exerciseName = exercise.exerciseName,
            exerciseOrder = exercise.exerciseOrder,
            plannedSets = exercise.plannedSets,
            completedSets = exercise.completedSets,
            restSeconds = exercise.restSeconds,
            startedAt = exercise.startedAt,
            endedAt = exercise.endedAt,
            sessionId = sessionId,
            sets = sets,
        )

    fun toDto(setLog: SetLogEntity, exerciseLogId: Long): BackupSetLog =
        BackupSetLog(
            id = setLog.id,
            setNumber = setLog.setNumber,
            reps = setLog.reps,
            weightKg = setLog.weightKg,
            rpe = setLog.rpe,
            durationSeconds = setLog.durationSeconds,
            distanceMeters = setLog.distanceMeters,
            isCompleted = setLog.isCompleted,
            isWarmup = setLog.isWarmup,
            restTakenSeconds = setLog.restTakenSeconds,
            completedAt = setLog.completedAt,
            exerciseLogId = exerciseLogId,
        )

    fun toEntity(dto: BackupPlan): WorkoutPlanEntity =
        WorkoutPlanEntity(
            id = dto.id,
            serverPlanId = dto.serverPlanId,
            name = dto.name,
            description = dto.description,
            goal = dto.goal,
            daysPerWeek = dto.daysPerWeek,
            isActive = dto.isActive,
            generatedByAi = dto.generatedByAi,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            version = dto.version,
            nextReviewAt = dto.nextReviewAt,
            reviewAfterWeeks = dto.reviewAfterWeeks,
            source = dto.source,
        )

    fun toEntity(dto: BackupDay, planId: Long): WorkoutDayEntity =
        WorkoutDayEntity(
            id = dto.id,
            planId = planId,
            name = dto.name,
            dayOrder = dto.dayOrder,
            notes = dto.notes,
            isRestDay = dto.isRestDay,
            modality = dto.modality,
            weekday = dto.weekday,
        )

    fun toEntity(dto: BackupPlanExercise, dayId: Long): WorkoutPlanExerciseEntity =
        WorkoutPlanExerciseEntity(
            id = dto.id,
            dayId = dayId,
            exerciseId = dto.exerciseId,
            exerciseName = dto.exerciseName,
            primaryMuscles = dto.primaryMuscles,
            equipment = dto.equipment,
            exerciseOrder = dto.exerciseOrder,
            sets = dto.sets,
            targetReps = dto.targetReps,
            targetRpe = dto.targetRpe,
            restSeconds = dto.restSeconds,
            notes = dto.notes,
            isSupersetWithPrevious = dto.isSupersetWithPrevious,
            metricType = dto.metricType,
            targetDurationSeconds = dto.targetDurationSeconds,
            targetDistanceMeters = dto.targetDistanceMeters,
            targetPaceSecondsPerKm = dto.targetPaceSecondsPerKm,
            targetHrZone = dto.targetHrZone,
        )

    fun toEntity(dto: BackupSession, planId: Long): WorkoutSessionEntity =
        WorkoutSessionEntity(
            id = dto.id,
            planId = planId,
            dayId = dto.dayId,
            planName = dto.planName,
            dayName = dto.dayName,
            status = dto.status,
            startedAt = dto.startedAt,
            endedAt = dto.endedAt,
            totalDurationSeconds = dto.totalDurationSeconds,
            notes = dto.notes,
            modality = dto.modality,
            source = dto.source,
            title = dto.title,
            totalDistanceMeters = dto.totalDistanceMeters,
            poolLengthMeters = dto.poolLengthMeters,
            stroke = dto.stroke,
            sessionRpe = dto.sessionRpe,
        )

    fun toEntity(dto: BackupSessionExercise, sessionId: Long): SessionExerciseEntity =
        SessionExerciseEntity(
            id = dto.id,
            sessionId = sessionId,
            exerciseId = dto.exerciseId,
            exerciseName = dto.exerciseName,
            exerciseOrder = dto.exerciseOrder,
            plannedSets = dto.plannedSets,
            completedSets = dto.completedSets,
            restSeconds = dto.restSeconds,
            startedAt = dto.startedAt,
            endedAt = dto.endedAt,
        )

    fun toEntity(dto: BackupSetLog, exerciseLogId: Long): SetLogEntity =
        SetLogEntity(
            id = dto.id,
            exerciseLogId = exerciseLogId,
            setNumber = dto.setNumber,
            reps = dto.reps,
            weightKg = dto.weightKg,
            rpe = dto.rpe,
            durationSeconds = dto.durationSeconds,
            distanceMeters = dto.distanceMeters,
            isCompleted = dto.isCompleted,
            isWarmup = dto.isWarmup,
            restTakenSeconds = dto.restTakenSeconds,
            completedAt = dto.completedAt,
        )
}
