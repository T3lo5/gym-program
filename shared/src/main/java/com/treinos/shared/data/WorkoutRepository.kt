package com.treinos.shared.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class WorkoutRepository(
    private val workoutPlanDao: WorkoutPlanDao,
    private val workoutDayDao: WorkoutDayDao,
    private val exerciseDao: ExerciseDao
) {

    // Plans
    fun getAllPlans(): Flow<List<WorkoutPlan>> = workoutPlanDao.getAllPlans()
    fun getActivePlans(): Flow<List<WorkoutPlan>> = workoutPlanDao.getActivePlans()

    suspend fun getPlanById(id: Long): WorkoutPlan? = workoutPlanDao.getById(id)

    suspend fun getPlanWithDaysAndExercises(id: Long): WorkoutPlanWithDetails? {
        val plan = workoutPlanDao.getById(id) ?: return null
        val daysWithExercises = workoutPlanDao.getDaysWithExercises(id)
        return WorkoutPlanWithDetails(plan, daysWithExercises)
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun createPlan(
        name: String,
        description: String = "",
        goal: String = "hypertrophy",
        daysPerWeek: Int = 4,
        generatedByAi: Boolean = false,
        aiPromptHash: String? = null
    ): Long {
        val plan = WorkoutPlan(
            name = name,
            description = description,
            goal = goal,
            daysPerWeek = daysPerWeek,
            generatedByAi = generatedByAi,
            aiPromptHash = aiPromptHash
        )
        return workoutPlanDao.insert(plan)
    }

    suspend fun updatePlan(plan: WorkoutPlan) {
        workoutPlanDao.update(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deletePlan(id: Long) {
        workoutPlanDao.deleteById(id)
    }

    suspend fun duplicatePlan(id: Long, newName: String): Long {
        val original = getPlanWithDaysAndExercises(id) ?: throw IllegalArgumentException("Plan not found")
        val newPlanId = createPlan(
            name = newName,
            description = original.plan.description,
            goal = original.plan.goal,
            daysPerWeek = original.plan.daysPerWeek
        )
        original.days.forEach { dayWithExercises ->
            val newDayId = workoutDayDao.insert(
                WorkoutDay(
                    planId = newPlanId,
                    name = dayWithExercises.day.name,
                    dayOrder = dayWithExercises.day.dayOrder,
                    notes = dayWithExercises.day.notes,
                    isRestDay = dayWithExercises.day.isRestDay
                )
            )
            dayWithExercises.exercises.forEach { planExWithDetails ->
                workoutDayDao.insertExercise(
                    WorkoutPlanExercise(
                        dayId = newDayId,
                        exerciseId = planExWithDetails.planExercise.exerciseId,
                        exerciseOrder = planExWithDetails.planExercise.exerciseOrder,
                        sets = planExWithDetails.planExercise.sets,
                        targetReps = planExWithDetails.planExercise.targetReps,
                        targetRpe = planExWithDetails.planExercise.targetRpe,
                        restSeconds = planExWithDetails.planExercise.restSeconds,
                        notes = planExWithDetails.planExercise.notes,
                        isSupersetWithPrevious = planExWithDetails.planExercise.isSupersetWithPrevious
                    )
                )
            }
        }
        return newPlanId
    }

    // Days
    suspend fun addDay(planId: Long, name: String, isRestDay: Boolean = false): Long {
        val days = workoutDayDao.getByPlanId(planId).first()
        val maxOrder = if (days.isEmpty()) -1 else days.maxOf { it.dayOrder }
        return workoutDayDao.insert(
            WorkoutDay(
                planId = planId,
                name = name,
                dayOrder = maxOrder + 1,
                isRestDay = isRestDay
            )
        )
    }

    suspend fun updateDay(day: WorkoutDay) {
        workoutDayDao.update(day)
    }

    suspend fun deleteDay(dayId: Long) {
        workoutDayDao.deleteById(dayId)
    }

    // Exercises in plan
    suspend fun addExerciseToDay(
        dayId: Long,
        exerciseId: Long,
        sets: Int = 3,
        targetReps: String = "8-12",
        targetRpe: Float? = null,
        restSeconds: Int = 90,
        isSupersetWithPrevious: Boolean = false
    ): Long {
        val exercises = workoutDayDao.getExercisesByDayId(dayId).first()
        val maxOrder = if (exercises.isEmpty()) -1 else exercises.maxOf { it.exerciseOrder }
        return workoutDayDao.insertExercise(
            WorkoutPlanExercise(
                dayId = dayId,
                exerciseId = exerciseId,
                exerciseOrder = maxOrder + 1,
                sets = sets,
                targetReps = targetReps,
                targetRpe = targetRpe,
                restSeconds = restSeconds,
                isSupersetWithPrevious = isSupersetWithPrevious
            )
        )
    }

    suspend fun updatePlanExercise(planExercise: WorkoutPlanExercise) {
        workoutDayDao.updateExercise(planExercise)
    }

    suspend fun removeExerciseFromDay(planExerciseId: Long) {
        workoutDayDao.deleteExerciseById(planExerciseId)
    }

    // Workout sessions
    fun getSessionsForPlan(planId: Long): Flow<List<WorkoutSession>> = workoutPlanDao.getSessionsForPlan(planId)
    fun getAllSessions(): Flow<List<WorkoutSession>> = workoutPlanDao.getAllSessions()
    fun getActiveSession(): Flow<WorkoutSession?> = workoutPlanDao.getActiveSession()

    suspend fun startSession(planId: Long, dayId: Long): Long {
        val session = WorkoutSession(
            planId = planId,
            dayId = dayId,
            startedAt = System.currentTimeMillis()
        )
        return workoutPlanDao.insertSession(session)
    }

    suspend fun endSession(sessionId: Long, durationMinutes: Int?) {
        val session = workoutPlanDao.getSessionById(sessionId) ?: return
        workoutPlanDao.updateSession(
            session.copy(
                endedAt = System.currentTimeMillis(),
                durationMinutes = durationMinutes,
                completed = true
            )
        )
    }

    suspend fun logSet(
        sessionId: Long,
        exerciseId: Long,
        setNumber: Int,
        weightKg: Float?,
        reps: Int?,
        rpe: Float?,
        completed: Boolean = true,
        notes: String? = null
    ): Long {
        val log = SessionExerciseLog(
            sessionId = sessionId,
            exerciseId = exerciseId,
            startedAt = System.currentTimeMillis()
        )
        val logId = workoutPlanDao.insertExerciseLog(log)
        val setLog = SetLog(
            exerciseLogId = logId,
            setNumber = setNumber,
            weightKg = weightKg,
            reps = reps,
            rpe = rpe,
            completed = completed,
            notes = notes,
            completedAt = if (completed) System.currentTimeMillis() else null
        )
        workoutPlanDao.insertSetLog(setLog)
        return logId
    }

    suspend fun completeSet(setLogId: Long) {
        val setLog = workoutPlanDao.getSetLogById(setLogId) ?: return
        workoutPlanDao.updateSetLog(
            setLog.copy(
                completed = true,
                completedAt = System.currentTimeMillis()
            )
        )
    }
}
