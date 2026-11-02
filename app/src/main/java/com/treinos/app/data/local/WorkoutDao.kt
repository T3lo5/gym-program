package com.treinos.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    // ---------- Planos ----------

    @Query("SELECT * FROM workout_plans WHERE isActive = 1 ORDER BY updatedAt DESC LIMIT 1")
    fun observeActivePlanEntity(): Flow<WorkoutPlanEntity?>

    @Query("SELECT * FROM workout_days ORDER BY dayOrder")
    fun observeAllDays(): Flow<List<WorkoutDayEntity>>

    @Query("SELECT * FROM workout_plan_exercises ORDER BY exerciseOrder")
    fun observeAllPlanExercises(): Flow<List<WorkoutPlanExerciseEntity>>

    @Query("SELECT * FROM workout_plans ORDER BY updatedAt DESC")
    suspend fun getAllPlanEntities(): List<WorkoutPlanEntity>

    @Query("SELECT * FROM workout_days ORDER BY dayOrder")
    suspend fun getAllDayEntities(): List<WorkoutDayEntity>

    @Query("SELECT * FROM workout_plan_exercises ORDER BY exerciseOrder")
    suspend fun getAllPlanExercises(): List<WorkoutPlanExerciseEntity>

    @Query("SELECT * FROM workout_plans WHERE id = :id")
    suspend fun getPlan(id: Long): WorkoutPlanEntity?

    @Query("SELECT * FROM workout_days WHERE id = :id")
    suspend fun getDay(id: Long): WorkoutDayEntity?

    @Query("SELECT * FROM workout_plan_exercises WHERE dayId = :dayId ORDER BY exerciseOrder")
    suspend fun getExercisesForDay(dayId: Long): List<WorkoutPlanExerciseEntity>

    @Query("UPDATE workout_plans SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE workout_plans SET isActive = 1 WHERE id = :planId")
    suspend fun updatePlanActive(planId: Long)

    @Insert
    suspend fun insertPlan(plan: WorkoutPlanEntity): Long

    @Insert
    suspend fun insertDay(day: WorkoutDayEntity): Long

    @Insert
    suspend fun insertPlanExercises(exercises: List<WorkoutPlanExerciseEntity>)

    /**
     * Desativa o plano anterior e grava plano, dias e exercícios em uma única
     * transação para evitar que a tela veja um plano pela metade.
     */
    @Transaction
    suspend fun replaceActivePlan(
        plan: WorkoutPlanEntity,
        days: List<WorkoutDayEntity>,
        exercisesByDay: List<List<WorkoutPlanExerciseEntity>>,
    ): Long {
        deactivateAll()
        val planId = insertPlan(plan)
        days.forEachIndexed { index, day ->
            val dayId = insertDay(day.copy(planId = planId))
            val exercises = exercisesByDay.getOrNull(index)
                .orEmpty()
                .map { it.copy(dayId = dayId) }
            if (exercises.isNotEmpty()) {
                insertPlanExercises(exercises)
            }
        }
        return planId
    }

    // ---------- Sessões ----------

    @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC")
    fun observeSessionEntities(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM session_exercises ORDER BY exerciseOrder")
    fun observeAllSessionExercises(): Flow<List<SessionExerciseEntity>>

    @Query("SELECT * FROM set_logs ORDER BY setNumber")
    fun observeAllSetLogs(): Flow<List<SetLogEntity>>

    @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC")
    suspend fun getAllSessionEntities(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM session_exercises ORDER BY exerciseOrder")
    suspend fun getAllSessionExercises(): List<SessionExerciseEntity>

    @Query("SELECT * FROM set_logs ORDER BY setNumber")
    suspend fun getAllSetLogs(): List<SetLogEntity>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSession(id: Long): WorkoutSessionEntity?

    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    @Insert
    suspend fun insertSessionExercises(exercises: List<SessionExerciseEntity>): List<Long>

    @Query("SELECT * FROM session_exercises WHERE id = :id")
    suspend fun getSessionExercise(id: Long): SessionExerciseEntity?

    @Query("UPDATE session_exercises SET completedSets = completedSets + 1 WHERE id = :id")
    suspend fun incrementCompletedSets(id: Long)

    @Query("UPDATE session_exercises SET endedAt = :endedAt WHERE id = :id")
    suspend fun setExerciseEndedAt(id: Long, endedAt: Long)

    @Query("SELECT COALESCE(MAX(setNumber), 0) FROM set_logs WHERE exerciseLogId = :exerciseLogId")
    suspend fun maxSetNumber(exerciseLogId: Long): Int

    @Insert
    suspend fun insertSetLog(setLog: SetLogEntity): Long

    @Query("SELECT COALESCE(SUM(completedSets), 0) FROM session_exercises WHERE sessionId = :sessionId")
    suspend fun completedSetsForSession(sessionId: Long): Int

    @Query("DELETE FROM set_logs")
    suspend fun deleteSetLogs()

    @Query("DELETE FROM session_exercises")
    suspend fun deleteSessionExercises()

    @Query("DELETE FROM workout_sessions")
    suspend fun deleteSessions()

    @Query("DELETE FROM workout_plan_exercises")
    suspend fun deletePlanExercises()

    @Query("DELETE FROM workout_days")
    suspend fun deleteDays()

    @Query("DELETE FROM workout_plans")
    suspend fun deletePlans()

    @Transaction
    suspend fun deleteAllData() {
        deleteSetLogs()
        deleteSessionExercises()
        deleteSessions()
        deletePlanExercises()
        deleteDays()
        deletePlans()
    }
}
