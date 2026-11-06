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

    @Query(
        "SELECT * FROM workout_plans WHERE isActive = 1 " +
            "ORDER BY CASE kind WHEN 'main' THEN 0 ELSE 1 END, updatedAt DESC"
    )
    fun observeActivePlanEntities(): Flow<List<WorkoutPlanEntity>>

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

    @Query("UPDATE workout_plans SET isActive = 0 WHERE kind = :kind")
    suspend fun deactivateByKind(kind: String)

    @Query("UPDATE workout_plans SET isActive = 1 WHERE id = :planId")
    suspend fun updatePlanActive(planId: Long)

    @Query("UPDATE workout_plans SET nextReviewAt = :nextReviewAt WHERE id = :planId")
    suspend fun updatePlanReviewDate(planId: Long, nextReviewAt: String)

    @Insert
    suspend fun insertPlan(plan: WorkoutPlanEntity): Long

    @Insert
    suspend fun insertPlans(plans: List<WorkoutPlanEntity>): List<Long>

    @Insert
    suspend fun insertDay(day: WorkoutDayEntity): Long

    @Insert
    suspend fun insertDays(days: List<WorkoutDayEntity>): List<Long>

    @Insert
    suspend fun insertPlanExercises(exercises: List<WorkoutPlanExerciseEntity>)

    @Insert
    suspend fun insertPlanExercisesBatch(exercises: List<WorkoutPlanExerciseEntity>)

    /**
     * Desativa os planos anteriores do mesmo `kind` e grava plano, dias e
     * exercícios em uma única transação para evitar que a tela veja um plano
     * pela metade. Extras não desativam nada: use [insertPlanWithDays].
     */
    @Transaction
    suspend fun replaceActivePlan(
        plan: WorkoutPlanEntity,
        days: List<WorkoutDayEntity>,
        exercisesByDay: List<List<WorkoutPlanExerciseEntity>>,
    ): Long {
        deactivateByKind(plan.kind)
        return insertPlanWithDays(plan, days, exercisesByDay)
    }

    /** Grava plano, dias e exercícios sem desativar nenhum plano existente. */
    @Transaction
    suspend fun insertPlanWithDays(
        plan: WorkoutPlanEntity,
        days: List<WorkoutDayEntity>,
        exercisesByDay: List<List<WorkoutPlanExerciseEntity>>,
    ): Long {
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

    /** Apaga o plano; dias e exercícios caem em CASCADE. Sessões não têm FK e ficam. */
    @Query("DELETE FROM workout_plans WHERE id = :planId")
    suspend fun deletePlan(planId: Long)

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

    @Insert
    suspend fun insertSessions(sessions: List<WorkoutSessionEntity>): List<Long>

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

    @Insert
    suspend fun insertSetLogs(setLogs: List<SetLogEntity>): List<Long>

    @Query("SELECT COALESCE(SUM(completedSets), 0) FROM session_exercises WHERE sessionId = :sessionId")
    suspend fun completedSetsForSession(sessionId: Long): Int

    @Query("DELETE FROM set_logs")
    suspend fun deleteSetLogs()

    @Query("DELETE FROM session_exercises")
    suspend fun deleteSessionExercises()

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

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

    /**
     * Limpa todos os dados e reinsere o payload do backup em uma única transação,
     * preservando os IDs originais (Room grava id = 0 como auto-incremento e
     * id > 0 como valor explícito). Ordem de pai para filho:
     * plans → days → plan_exercises → sessions → session_exercises → set_logs.
     */
    @Transaction
    suspend fun replaceAllData(
        plans: List<WorkoutPlanEntity>,
        days: List<WorkoutDayEntity>,
        planExercises: List<WorkoutPlanExerciseEntity>,
        sessions: List<WorkoutSessionEntity>,
        sessionExercises: List<SessionExerciseEntity>,
        setLogs: List<SetLogEntity>,
    ) {
        deleteAllData()
        if (plans.isNotEmpty()) insertPlans(plans)
        if (days.isNotEmpty()) insertDays(days)
        if (planExercises.isNotEmpty()) insertPlanExercisesBatch(planExercises)
        if (sessions.isNotEmpty()) insertSessions(sessions)
        if (sessionExercises.isNotEmpty()) insertSessionExercises(sessionExercises)
        if (setLogs.isNotEmpty()) insertSetLogs(setLogs)
    }
}
