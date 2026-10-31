package com.treinos.shared.data

import kotlinx.coroutines.flow.Flow

class WorkoutRepository(
    private val workoutPlanDao: WorkoutPlanDao,
    private val workoutDayDao: WorkoutDayDao,
    private val exerciseDao: ExerciseDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val sessionExerciseLogDao: SessionExerciseLogDao,
    private val setLogDao: SetLogDao
) {

    fun getAllPlans(): Flow<List<WorkoutPlan>> = workoutPlanDao.getAllPlans()
    fun getActivePlans(): Flow<List<WorkoutPlan>> = workoutPlanDao.getActivePlans()

    suspend fun getPlanById(id: Long): WorkoutPlan? = workoutPlanDao.getById(id)

    suspend fun getPlanWithDaysAndExercises(id: Long): WorkoutPlanWithDetails? {
        val plan = workoutPlanDao.getById(id) ?: return null
        val daysWithExercises = workoutPlanDao.getDaysWithExercises(id)
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

    suspend fun addDay(planId: Long, name: String, isRestDay: Boolean = false): Long {
        val days = workoutPlanDao.getDaysWithExercises(planId)
        val maxOrder = if (days.isEmpty()) -1 else days.maxOf { it.day.dayOrder }
        return workoutDayDao.insert(
            WorkoutDay(planId = planId, name = name, dayOrder = maxOrder + 1, isRestDay = isRestDay)
        )
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
        val exercises = workoutDayDao.getExercisesByDayId(dayId)
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
    }

    fun getSessionsForPlan(planId: Long): Flow<List<WorkoutSession>> = workoutSessionDao.getByPlanId(planId)
    fun getAllSessions(): Flow<List<WorkoutSession>> = workoutSessionDao.getRecentSessions(100)
    fun getActiveSession(): Flow<WorkoutSession?> = kotlinx.coroutines.flow.flow { emit(workoutSessionDao.getActiveSession()) }

    suspend fun startSession(planId: Long, dayId: Long): Long {
        return workoutSessionDao.insert(
            WorkoutSession(planId = planId, dayId = dayId, startedAt = System.currentTimeMillis(), status = "in_progress")
        )
    }

    suspend fun endSession(sessionId: Long) {
        val session = workoutSessionDao.getById(sessionId) ?: return
        workoutSessionDao.update(session.copy(endedAt = System.currentTimeMillis(), status = "completed"))
    }

    suspend fun logSet(sessionId: Long, exerciseId: String, setNumber: Int, weightKg: Double?, reps: Int?, rpe: Double?): Long {
        val log = SessionExerciseLog(sessionId = sessionId, exerciseId = exerciseId, exerciseOrder = setNumber, plannedSets = 3)
        val logId = sessionExerciseLogDao.insert(log)
        val setLog = SetLog(exerciseLogId = logId, setNumber = setNumber, weightKg = weightKg, reps = reps, rpe = rpe, isCompleted = true, completedAt = System.currentTimeMillis())
        setLogDao.insert(setLog)
        return logId
    }

    suspend fun completeSet(setLogId: Long) {
        val setLog = setLogDao.getById(setLogId) ?: return
        setLogDao.update(setLog.copy(isCompleted = true, completedAt = System.currentTimeMillis()))
    }
}
