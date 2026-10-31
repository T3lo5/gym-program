package com.treinos.shared.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class WorkoutRepository(
    private val workoutPlanDao: WorkoutPlanDao,
    private val workoutDayDao: WorkoutDayDao,
    private val exerciseDao: ExerciseDao,
    private val workoutSessionDao: WorkoutSessionDao? = null,
    private val sessionExerciseLogDao: SessionExerciseLogDao? = null,
    private val setLogDao: SetLogDao? = null
) {
    fun getAllPlans(): Flow<List<WorkoutPlan>> = workoutPlanDao.getAllPlans()
    fun getActivePlans(): Flow<List<WorkoutPlan>> = workoutPlanDao.getActivePlans()

    suspend fun getPlanById(id: Long): WorkoutPlan? = workoutPlanDao.getById(id)

    suspend fun getPlanWithDaysAndExercises(id: Long): WorkoutPlanWithDetails? {
        val plan = workoutPlanDao.getById(id) ?: return null
        val daysWithExercises = try {
            workoutPlanDao.getDaysWithExercises(id)
        } catch (e: Exception) {
            emptyList()
        }
        return WorkoutPlanWithDetails(plan, daysWithExercises)
    }

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
        val newPlanId = createPlan(name = newName, description = original.plan.description, goal = original.plan.goal, daysPerWeek = original.plan.daysPerWeek)
        return newPlanId
    }

    suspend fun addDay(planId: Long, name: String, isRestDay: Boolean = false): Long {
        return workoutDayDao.insert(WorkoutDay(planId = planId, name = name, dayOrder = 0, isRestDay = isRestDay))
    }

    suspend fun updateDay(day: WorkoutDay) {
        workoutDayDao.update(day)
    }

    suspend fun deleteDay(dayId: Long) {
        workoutDayDao.deleteById(dayId)
    }

    suspend fun addExerciseToDay(
        dayId: Long,
        exerciseId: String,
        sets: Int = 3,
        targetReps: String = "8-12",
        targetRpe: Double? = null,
        restSeconds: Int = 90,
        isSupersetWithPrevious: Boolean = false
    ): Long {
        return workoutDayDao.insertExercise(
            WorkoutPlanExercise(dayId = dayId, exerciseId = exerciseId, exerciseOrder = 0, sets = sets, targetReps = targetReps, targetRpe = targetRpe, restSeconds = restSeconds, isSupersetWithPrevious = isSupersetWithPrevious)
        )
    }

    suspend fun updatePlanExercise(planExercise: WorkoutPlanExercise) {
        workoutDayDao.updateExercise(planExercise)
    }

    suspend fun removeExerciseFromDay(planExerciseId: Long) {}
    fun getSessionsForPlan(planId: Long): Flow<List<WorkoutSession>> = flowOf(emptyList())
    fun getAllSessions(): Flow<List<WorkoutSession>> = flowOf(emptyList())
    fun getActiveSession(): Flow<WorkoutSession?> = flowOf(null)
    suspend fun startSession(planId: Long, dayId: Long): Long = 0
    suspend fun endSession(sessionId: Long) {}
    suspend fun logSet(sessionId: Long, exerciseId: String, setNumber: Int, weightKg: Double?, reps: Int?, rpe: Double?): Long = 0
    suspend fun completeSet(setLogId: Long) {}
}
