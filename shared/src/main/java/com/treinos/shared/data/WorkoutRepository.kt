package com.treinos.shared.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
        val daysWithExercises = workoutDayDao.getDaysWithExercises(id)
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
        val maxOrder = workoutDayDao.getByPlanId(planId)
            .first()
            .maxByOrNull { it.dayOrder }?.dayOrder ?: -1
        return workoutDayDao.insert(WorkoutDay(
            planId = planId,
            name = name,
            dayOrder = maxOrder + 1,
            isRestDay = isRestDay
        ))
    }

    suspend fun updateDay(day: WorkoutDay) {
        workoutDayDao.update(day)
    }

    suspend fun deleteDay(dayId: Long) {
        workoutDayDao.deleteById(dayId)
    }

    suspend fun reorderDays(planId: Long, dayOrders: List<Pair<Long, Int>>) {
        dayOrders.forEach { (id, order) ->
            val day = workoutDayDao.getById(id) ?: return@forEach
            workoutDayDao.update(day.copy(dayOrder = order))
        }
    }

    // Exercises in day
    suspend fun addExerciseToDay(
        dayId: Long,
        exerciseId: String,
        sets: Int,
        targetReps: String,
        targetRpe: Double?,
        restSeconds: Int,
        notes: String = "",
        isSuperset: Boolean = false
    ) {
        val maxOrder = workoutDayDao.getExercisesByDayId(dayId)
            .maxByOrNull { it.exerciseOrder }?.exerciseOrder ?: -1
        workoutDayDao.insertExercise(WorkoutPlanExercise(
            dayId = dayId,
            exerciseId = exerciseId,
            exerciseOrder = maxOrder + 1,
            sets = sets,
            targetReps = targetReps,
            targetRpe = targetRpe,
            restSeconds = restSeconds,
            notes = notes,
            isSupersetWithPrevious = isSuperset
        ))
    }

    suspend fun updatePlanExercise(planExercise: WorkoutPlanExercise) {
        workoutDayDao.updateExercise(planExercise)
    }

    suspend fun deleteExerciseFromDay(dayId: Long, exerciseId: String) {
        workoutDayDao.deleteExerciseFromDay(dayId, exerciseId)
    }

    suspend fun reorderExercisesInDay(dayId: Long, exerciseOrders: List<Pair<String, Int>>) {
        exerciseOrders.forEach { (exerciseId, order) ->
            val planEx = workoutDayDao.getExerciseInDay(dayId, exerciseId) ?: return@forEach
            workoutDayDao.updateExercise(planEx.copy(exerciseOrder = order))
        }
    }

    // Search exercises for picker
    fun searchExercises(query: String): Flow<List<Exercise>> = exerciseDao.search(query)

    fun getExercisesByMuscle(muscle: String): Flow<List<Exercise>> = exerciseDao.getByPrimaryMuscle(muscle)

    fun getExercisesByEquipment(equipment: String): Flow<List<Exercise>> = exerciseDao.getByEquipment(equipment)

    fun getAllEquipments(): Flow<List<String>> = exerciseDao.getAllEquipments()

    fun getAllMuscleGroups(): Flow<List<String>> = exerciseDao.getAllMuscleGroups()

    suspend fun getExerciseById(id: String): Exercise? = exerciseDao.getById(id)

    suspend fun getExercisesByIds(ids: List<String>): List<Exercise> = exerciseDao.getByIds(ids)
}

data class WorkoutPlanWithDetails(
    val plan: WorkoutPlan,
    val days: List<WorkoutDayWithExercises>
)