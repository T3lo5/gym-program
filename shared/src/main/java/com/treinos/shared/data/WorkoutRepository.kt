package com.treinos.shared.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class WorkoutRepository(
    private val workoutPlanDao: WorkoutPlanDao,
    private val workoutDayDao: WorkoutDayDao,
    private val exerciseDao: ExerciseDao
) {
    fun getAllPlans(): Flow<List<WorkoutPlan>> = workoutPlanDao.getAllPlans()
    fun getActivePlans(): Flow<List<WorkoutPlan>> = workoutPlanDao.getActivePlans()
    suspend fun getPlanById(id: Long): WorkoutPlan? = workoutPlanDao.getById(id)
    suspend fun getPlanWithDaysAndExercises(id: Long): WorkoutPlanWithDetails? {
        val plan = workoutPlanDao.getById(id) ?: return null
        val daysWithExercises = workoutPlanDao.getDaysWithExercises(id)
        return WorkoutPlanWithDetails(plan, daysWithExercises)
    }
    suspend fun createPlan(name: String, description: String = "", goal: String = "hypertrophy", daysPerWeek: Int = 4, generatedByAi: Boolean = false, aiPromptHash: String? = null): Long {
        val plan = WorkoutPlan(name = name, description = description, goal = goal, daysPerWeek = daysPerWeek, generatedByAi = generatedByAi, aiPromptHash = aiPromptHash)
        return workoutPlanDao.insert(plan)
    }
    suspend fun updatePlan(plan: WorkoutPlan) { workoutPlanDao.update(plan.copy(updatedAt = System.currentTimeMillis())) }
    suspend fun deletePlan(id: Long) { workoutPlanDao.deleteById(id) }
    suspend fun duplicatePlan(id: Long, newName: String): Long {
        val original = getPlanWithDaysAndExercises(id) ?: throw IllegalArgumentException("Plan not found")
        val newPlanId = createPlan(newName, original.plan.description, original.plan.goal, original.plan.daysPerWeek)
        return newPlanId
    }
    suspend fun addDay(planId: Long, name: String, isRestDay: Boolean = false): Long {
        val days = workoutPlanDao.getDaysWithExercises(planId)
        val maxOrder = if (days.isEmpty()) -1 else days.maxOf { it.day.dayOrder }
        return workoutDayDao.insert(WorkoutDay(planId = planId, name = name, dayOrder = maxOrder + 1, isRestDay = isRestDay))
    }
    suspend fun updateDay(day: WorkoutDay) { workoutDayDao.update(day) }
    suspend fun deleteDay(dayId: Long) { workoutDayDao.deleteById(dayId) }
    suspend fun addExerciseToDay(dayId: Long, exerciseId: String, sets: Int = 3, targetReps: String = "8-12", targetRpe: Double? = null, restSeconds: Int = 90, isSupersetWithPrevious: Boolean = false): Long {
        val exercises = workoutDayDao.getExercisesByDayId(dayId)
        val maxOrder = if (exercises.isEmpty()) -1 else exercises.maxOf { it.exerciseOrder }
        return workoutDayDao.insertExercise(WorkoutPlanExercise(dayId = dayId, exerciseId = exerciseId, exerciseOrder = maxOrder + 1, sets = sets, targetReps = targetReps, targetRpe = targetRpe, restSeconds = restSeconds, isSupersetWithPrevious = isSupersetWithPrevious))
    }
    suspend fun updatePlanExercise(planExercise: WorkoutPlanExercise) { workoutDayDao.updateExercise(planExercise) }
    suspend fun removeExerciseFromDay(planExerciseId: Long) {}
    fun getSessionsForPlan(planId: Long): Flow<List<WorkoutSession>> = emptyFlow()
    fun getAllSessions(): Flow<List<WorkoutSession>> = emptyFlow()
    fun getActiveSession(): Flow<WorkoutSession?> = emptyFlow()
    suspend fun startSession(planId: Long, dayId: Long): Long = 0
    suspend fun endSession(sessionId: Long) {}
    suspend fun logSet(sessionId: Long, exerciseId: String, setNumber: Int, weightKg: Double?, reps: Int?, rpe: Double?): Long = 0
    suspend fun completeSet(setLogId: Long) {}
}
