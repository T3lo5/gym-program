package com.treinos.shared.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutPlanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: WorkoutPlan): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plans: List<WorkoutPlan>): List<Long>

    @Update
    suspend fun update(plan: WorkoutPlan): Int

    @Query("SELECT * FROM workout_plans WHERE id = :id")
    suspend fun getById(id: Long): WorkoutPlan?

    @Query("SELECT * FROM workout_plans WHERE isActive = 1 ORDER BY updatedAt DESC")
    fun getActivePlans(): Flow<List<WorkoutPlan>>

    @Query("SELECT * FROM workout_plans ORDER BY updatedAt DESC")
    fun getAllPlans(): Flow<List<WorkoutPlan>>

    @Query("SELECT * FROM workout_plans WHERE generatedByAi = 1 ORDER BY createdAt DESC LIMIT :limit")
    fun getAiGeneratedPlans(limit: Int): Flow<List<WorkoutPlan>>

    @Query("DELETE FROM workout_plans WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Transaction
    @Query("SELECT * FROM workout_days WHERE planId = :planId ORDER BY dayOrder ASC")
    suspend fun getDaysWithExercises(planId: Long): List<WorkoutDayWithExercises>

    @Transaction
    @Query("SELECT * FROM workout_days WHERE planId = :planId ORDER BY dayOrder ASC")
    fun getDaysWithExercisesFlow(planId: Long): Flow<List<WorkoutDayWithExercises>>
}

@Dao
interface WorkoutDayDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(day: WorkoutDay): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(days: List<WorkoutDay>): List<Long>

    @Update
    suspend fun update(day: WorkoutDay): Int

    @Query("SELECT * FROM workout_days WHERE id = :id")
    suspend fun getById(id: Long): WorkoutDay?

    @Query("SELECT * FROM workout_days WHERE planId = :planId ORDER BY dayOrder ASC")
    fun getByPlanId(planId: Long): Flow<List<WorkoutDay>>

    @Query("DELETE FROM workout_days WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM workout_days WHERE planId = :planId")
    suspend fun deleteAllByPlanId(planId: Long): Int

    @Query("SELECT * FROM workout_plan_exercises WHERE dayId = :dayId ORDER BY exerciseOrder ASC")
    suspend fun getExercisesByDayId(dayId: Long): List<WorkoutPlanExercise>

    @Query("SELECT * FROM workout_plan_exercises WHERE dayId = :dayId AND exerciseId = :exerciseId")
    suspend fun getExerciseInDay(dayId: Long, exerciseId: String): WorkoutPlanExercise?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: WorkoutPlanExercise): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<WorkoutPlanExercise>): List<Long>

    @Update
    suspend fun updateExercise(exercise: WorkoutPlanExercise): Int

    @Query("DELETE FROM workout_plan_exercises WHERE dayId = :dayId AND exerciseId = :exerciseId")
    suspend fun deleteExerciseFromDay(dayId: Long, exerciseId: String): Int

    @Query("DELETE FROM workout_plan_exercises WHERE dayId = :dayId")
    suspend fun deleteAllExercisesFromDay(dayId: Long): Int
}

data class WorkoutDayWithExercises(
    @Embedded val day: WorkoutDay,
    @Relation(
        parentColumn = "id",
        entityColumn = "dayId",
        entity = WorkoutPlanExercise::class
    )
    val exercises: List<WorkoutPlanExerciseWithDetails>
)

data class WorkoutPlanExerciseWithDetails(
    @Embedded val planExercise: WorkoutPlanExercise,
    @Relation(
        parentColumn = "exerciseId",
        entityColumn = "id",
        entity = Exercise::class
    )
    val exercise: Exercise?
)

@Dao
interface WorkoutSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: WorkoutSession): Long

    @Update
    suspend fun update(session: WorkoutSession): Int

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getById(id: Long): WorkoutSession?

    @Query("SELECT * FROM workout_sessions WHERE planId = :planId ORDER BY startedAt DESC")
    fun getByPlanId(planId: Long): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workout_sessions WHERE status = 'in_progress' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveSession(): WorkoutSession?

    @Query("SELECT * FROM workout_sessions WHERE dayId = :dayId AND status = 'in_progress' LIMIT 1")
    suspend fun getActiveSessionForDay(dayId: Long): WorkoutSession?

    @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun getRecentSessions(limit: Int): Flow<List<WorkoutSession>>

    @Transaction
    @Query("SELECT * FROM session_exercise_logs WHERE sessionId = :sessionId ORDER BY exerciseOrder ASC")
    suspend fun getExercisesWithLogs(sessionId: Long): List<SessionExerciseLogWithSets>

    @Transaction
    @Query("SELECT * FROM session_exercise_logs WHERE sessionId = :sessionId ORDER BY exerciseOrder ASC")
    fun getExercisesWithLogsFlow(sessionId: Long): Flow<List<SessionExerciseLogWithSets>>
}

@Dao
interface SessionExerciseLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SessionExerciseLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<SessionExerciseLog>): List<Long>

    @Update
    suspend fun update(log: SessionExerciseLog): Int

    @Query("SELECT * FROM session_exercise_logs WHERE id = :id")
    suspend fun getById(id: Long): SessionExerciseLog?

    @Query("SELECT * FROM session_exercise_logs WHERE sessionId = :sessionId ORDER BY exerciseOrder ASC")
    suspend fun getBySessionId(sessionId: Long): List<SessionExerciseLog>

    @Query("SELECT * FROM session_exercise_logs WHERE sessionId = :sessionId AND exerciseId = :exerciseId")
    suspend fun getBySessionAndExercise(sessionId: Long, exerciseId: String): SessionExerciseLog?

    @Query("DELETE FROM session_exercise_logs WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}

@Dao
interface SetLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SetLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<SetLog>): List<Long>

    @Update
    suspend fun update(log: SetLog): Int

    @Query("SELECT * FROM set_logs WHERE id = :id")
    suspend fun getById(id: Long): SetLog?

    @Query("SELECT * FROM set_logs WHERE exerciseLogId = :exerciseLogId ORDER BY setNumber ASC")
    suspend fun getByExerciseLogId(exerciseLogId: Long): List<SetLog>

    @Query("SELECT * FROM set_logs WHERE exerciseLogId = :exerciseLogId AND setNumber = :setNumber")
    suspend fun getByExerciseLogAndSet(exerciseLogId: Long, setNumber: Int): SetLog?

    @Query("DELETE FROM set_logs WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}

data class SessionExerciseLogWithSets(
    @Embedded val log: SessionExerciseLog,
    @Relation(
        parentColumn = "id",
        entityColumn = "exerciseLogId",
        entity = SetLog::class
    )
    val sets: List<SetLog>
)