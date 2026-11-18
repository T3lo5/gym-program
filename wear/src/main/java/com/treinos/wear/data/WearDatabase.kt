package com.treinos.wear.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * H4-16: Room no relógio — o pulso guarda o snapshot da sessão do dia e as
 * séries registradas offline. O banco nasce na versão 1: qualquer mudança de
 * schema futura exige migration explícita e teste — `fallbackToDestructive-
 * Migration` NÃO é usado (nunca apagar dados em silêncio).
 */

/** Sessão do dia recebida no snapshot do celular. */
@Entity(tableName = "wear_sessions")
data class WearSessionEntity(
    @PrimaryKey val id: Long,
    val dayName: String,
    val modality: String,
    val startedAt: Long,
    val status: String,
    /** Deadline wall-clock do descanso em curso; `null` = sem descanso. */
    val restEndsAt: Long? = null,
)

/** Exercício do snapshot, na ordem de execução do dia. */
@Entity(tableName = "wear_session_exercises")
data class WearSessionExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    /** Id do exercício NA SESSÃO do celular — referência do lote de sync. */
    val sessionExerciseId: Long,
    val exerciseIndex: Int,
    val exerciseName: String,
    val plannedSets: Int,
    val targetReps: String,
    val restSeconds: Int,
)

/** Série registrada no pulso — offline fica `pending = true` até o sync. */
@Entity(
    tableName = "wear_set_logs",
    indices = [androidx.room.Index(value = ["clientSetId"], unique = true)],
)
data class WearSetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** UUID gerado no relógio no momento do log; chave de dedupe no celular. */
    val clientSetId: String,
    val sessionId: Long,
    val sessionExerciseId: Long,
    val exerciseIndex: Int,
    val setNumber: Int,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val rpe: Double? = null,
    val loggedAt: Long,
    val pending: Boolean,
    val originDeviceId: String = "",
    val revisionId: String = "",
    val deleted: Boolean = false,
)

@Dao
interface WearStandaloneDao {

    @Query("SELECT * FROM wear_sessions ORDER BY startedAt DESC LIMIT 1")
    fun observeLatestSession(): Flow<WearSessionEntity?>

    @Query("SELECT * FROM wear_sessions ORDER BY startedAt DESC LIMIT 1")
    suspend fun latestSession(): WearSessionEntity?

    @Query("SELECT * FROM wear_session_exercises WHERE sessionId = :sessionId ORDER BY exerciseIndex ASC")
    suspend fun exercisesForSession(sessionId: Long): List<WearSessionExerciseEntity>

    @Query("SELECT * FROM wear_set_logs WHERE sessionId = :sessionId ORDER BY loggedAt ASC")
    suspend fun setsForSession(sessionId: Long): List<WearSetLogEntity>

    @Query("SELECT * FROM wear_set_logs WHERE pending = 1 ORDER BY loggedAt ASC")
    suspend fun pendingSets(): List<WearSetLogEntity>

    @Query("SELECT COUNT(*) FROM wear_set_logs WHERE pending = 1")
    suspend fun pendingCount(): Int

    @Insert
    suspend fun insertSession(session: WearSessionEntity)

    @Insert
    suspend fun insertExercises(exercises: List<WearSessionExerciseEntity>)

    @Insert
    suspend fun insertSetLog(setLog: WearSetLogEntity): Long

    @Query("UPDATE wear_set_logs SET pending = 0 WHERE clientSetId IN (:clientSetIds)")
    suspend fun markSynced(clientSetIds: List<String>)

    @Query("UPDATE wear_sessions SET restEndsAt = :restEndsAt WHERE id = :sessionId")
    suspend fun updateRestEndsAt(sessionId: Long, restEndsAt: Long?)

    @Query("UPDATE wear_sessions SET status = :status WHERE id = :sessionId")
    suspend fun updateStatus(sessionId: Long, status: String)

    /** Remove a sessão e os exercícios dela; as séries pendentes de OUTRAS sessões ficam. */
    @Query("DELETE FROM wear_session_exercises WHERE sessionId = :sessionId")
    suspend fun deleteExercisesOfSession(sessionId: Long)

    @Query("DELETE FROM wear_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("DELETE FROM wear_set_logs WHERE sessionId = :sessionId")
    suspend fun deleteSetsOfSession(sessionId: Long)

    @Query("DELETE FROM wear_set_logs WHERE id = :id")
    suspend fun deleteSetLog(id: Long)
}

@Database(
    version = 2,
    entities = [
        WearSessionEntity::class,
        WearSessionExerciseEntity::class,
        WearSetLogEntity::class,
    ],
    exportSchema = true,
)
abstract class WearDatabase : RoomDatabase() {

    abstract fun standaloneDao(): WearStandaloneDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE wear_set_logs ADD COLUMN originDeviceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE wear_set_logs ADD COLUMN revisionId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE wear_set_logs ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun build(context: Context): WearDatabase =
            Room.databaseBuilder(context, WearDatabase::class.java, "wear-standalone.db")
                .addMigrations(MIGRATION_1_2)
                // H4-16: NUNCA fallbackToDestructiveMigration — Room corrompido
                // ou com versão inesperada falha a abrir e a tela mostra erro;
                // nunca apagar dados em silêncio.
                .build()
    }
}
