package com.treinos.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    // ---------- H5-1 sync outbox ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncOutbox(items: List<SyncOutboxEntity>)

    @Query("SELECT * FROM sync_outbox ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getSyncOutbox(limit: Int = 500): List<SyncOutboxEntity>

    @Query("DELETE FROM sync_outbox WHERE mutationId IN (:mutationIds)")
    suspend fun deleteSyncOutbox(mutationIds: List<String>)

    @Query("DELETE FROM sync_outbox")
    suspend fun clearSyncOutbox()

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

    @Query("SELECT * FROM workout_sessions WHERE dayId = :dayId AND status = 'in_progress'")
    suspend fun getInProgressSessionForDay(dayId: Long): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_plan_exercises WHERE dayId = :dayId ORDER BY exerciseOrder")
    suspend fun getExercisesForDay(dayId: Long): List<WorkoutPlanExerciseEntity>

    /** Atualiza uma linha de exercício de plano existente (H3-41: troca de exercício). */
    @Update
    suspend fun updatePlanExercise(exercise: WorkoutPlanExerciseEntity): Int

    /** H4-6: liga/desliga o superset com o exercício anterior no plano. */
    @Query("UPDATE workout_plan_exercises SET isSupersetWithPrevious = :enabled WHERE id = :id")
    suspend fun updateSupersetFlag(id: Long, enabled: Boolean)

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

    /** H4-7: chave de deduplicação do import de CSV (`importRef` único). */
    @Query("SELECT * FROM workout_sessions WHERE importRef = :ref LIMIT 1")
    suspend fun getSessionByImportRef(ref: String): WorkoutSessionEntity?

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

    /** Atualiza o exercício de uma sessão em andamento (troca durante o treino). */
    @Update
    suspend fun updateSessionExercise(exercise: SessionExerciseEntity): Int

    @Query("SELECT COALESCE(MAX(setNumber), 0) FROM set_logs WHERE exerciseLogId = :exerciseLogId")
    suspend fun maxSetNumber(exerciseLogId: Long): Int

    @Insert
    suspend fun insertSetLog(setLog: SetLogEntity): Long

    @Query("SELECT * FROM set_logs WHERE clientSetId = :clientSetId LIMIT 1")
    suspend fun getSetLogByClientSetId(clientSetId: String): SetLogEntity?

    @Query("DELETE FROM set_logs WHERE clientSetId = :clientSetId")
    suspend fun deleteSetLogByClientSetId(clientSetId: String)

    @Insert
    suspend fun insertSetLogs(setLogs: List<SetLogEntity>): List<Long>

    /**
     * H4-7: grava as sessões importadas de CSV em uma única transação —
     * falha no meio (ex.: `importRef` duplicado batendo no índice UNIQUE)
     * faz rollback completo, sem sessão parcial e sem `importRef` órfão.
     * Aditivo: nada existente é apagado nem alterado.
     */
    @Transaction
    suspend fun insertImportedCsvSessions(
        sessions: List<WorkoutSessionEntity>,
        exercisesBySession: List<List<SessionExerciseEntity>>,
        setsByExercise: List<List<List<SetLogEntity>>>,
    ): Int {
        var importedSessions = 0
        sessions.forEachIndexed { sessionIndex, session ->
            val sessionId = insertSession(session)
            importedSessions++
            val exercises = exercisesBySession.getOrNull(sessionIndex).orEmpty()
            val setsOfSession = setsByExercise.getOrNull(sessionIndex).orEmpty()
            exercises.forEachIndexed { exerciseIndex, exercise ->
                val exerciseLogId = insertSessionExercises(
                    listOf(exercise.copy(sessionId = sessionId)),
                ).single()
                val sets = setsOfSession.getOrNull(exerciseIndex).orEmpty()
                if (sets.isNotEmpty()) {
                    insertSetLogs(sets.map { it.copy(exerciseLogId = exerciseLogId) })
                }
            }
        }
        return importedSessions
    }

    @Query("SELECT * FROM set_logs WHERE id = :id")
    suspend fun getSetLog(id: Long): SetLogEntity?

    /** Edita a série pelo id (H3-27); `exerciseLogId` e `setNumber` vêm da entidade. */
    @Update
    suspend fun updateSetLog(setLog: SetLogEntity)

    /** Exclui a série pelo id (H3-27); o resto da sessão permanece. */
    @Query("DELETE FROM set_logs WHERE id = :setId")
    suspend fun deleteSetLog(setId: Long)

    /**
     * Realinha `completedSets` ao número real de séries gravadas (H3-27):
     * após excluir uma série, o contador persistido volta a ser derivado.
     * H4-4: aquecimento não conta como série de trabalho.
     */
    @Query(
        "UPDATE session_exercises SET completedSets = " +
            "(SELECT COUNT(*) FROM set_logs WHERE exerciseLogId = :exerciseLogId AND isWarmup = 0) " +
            "WHERE id = :exerciseLogId"
    )
    suspend fun recalculateCompletedSets(exerciseLogId: Long)

    /** H3-54: exclui a entrada de um exercício que ficou sem séries na sessão. */
    @Query("DELETE FROM session_exercises WHERE id = :id")
    suspend fun deleteSessionExercise(id: Long)

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
     *
     * H4-8: `measurements` decide o destino das medidas corporais, com a mesma
     * semântica do `profile` no backup — `null` significa "o arquivo não fala
     * disso" (backup v1/v2/v3 e sync do backend): as medidas locais ficam
     * intactas. Lista não-nula (inclusive vazia) substitui todas: limpa e
     * reinsere dentro da mesma transação, então uma falha no meio faz rollback
     * completo de dados e medidas.
     */
    @Transaction
    suspend fun replaceAllData(
        plans: List<WorkoutPlanEntity>,
        days: List<WorkoutDayEntity>,
        planExercises: List<WorkoutPlanExerciseEntity>,
        sessions: List<WorkoutSessionEntity>,
        sessionExercises: List<SessionExerciseEntity>,
        setLogs: List<SetLogEntity>,
        measurements: List<BodyMeasurementEntity>? = null,
    ) {
        deleteAllData()
        if (plans.isNotEmpty()) insertPlans(plans)
        if (days.isNotEmpty()) insertDays(days)
        if (planExercises.isNotEmpty()) insertPlanExercisesBatch(planExercises)
        if (sessions.isNotEmpty()) insertSessions(sessions)
        if (sessionExercises.isNotEmpty()) insertSessionExercises(sessionExercises)
        if (setLogs.isNotEmpty()) insertSetLogs(setLogs)
        if (measurements != null) {
            deleteAllMeasurements()
            if (measurements.isNotEmpty()) insertMeasurements(measurements)
        }
    }

    // ---------- Medidas corporais (H4-8) ----------

    /** Lista exibida por `measuredAt` desc (regra 4); o gráfico reordena asc. */
    @Query("SELECT * FROM body_measurements ORDER BY measuredAt DESC, id DESC")
    fun observeMeasurements(): Flow<List<BodyMeasurementEntity>>

    @Query("SELECT * FROM body_measurements ORDER BY measuredAt DESC, id DESC")
    suspend fun getAllMeasurements(): List<BodyMeasurementEntity>

    @Insert
    suspend fun insertMeasurement(measurement: BodyMeasurementEntity): Long

    @Insert
    suspend fun insertMeasurements(measurements: List<BodyMeasurementEntity>)

    @Query("DELETE FROM body_measurements WHERE id = :id")
    suspend fun deleteMeasurement(id: Long)

    @Query("DELETE FROM body_measurements")
    suspend fun deleteAllMeasurements()

    // ---------- Uso de exercício para calibrar precisão (H3-42) ----------

    @Insert
    suspend fun insertUsageEvent(event: ExerciseUsageEvent): Long

    @Query(
        "SELECT * FROM exercise_usage_events WHERE occurredAt >= :since " +
            "ORDER BY occurredAt DESC"
    )
    suspend fun getUsageEventsSince(since: Long): List<ExerciseUsageEvent>
}
