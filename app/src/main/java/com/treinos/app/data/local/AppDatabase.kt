package com.treinos.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WorkoutPlanEntity::class,
        WorkoutDayEntity::class,
        WorkoutPlanExerciseEntity::class,
        WorkoutSessionEntity::class,
        SessionExerciseEntity::class,
        SetLogEntity::class,
        ExerciseUsageEvent::class,
        BodyMeasurementEntity::class,
        SyncOutboxEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        /** Nome do arquivo SQLite; backup_rules.xml/data_extraction_rules.xml dependem dele. */
        const val DATABASE_NAME = "treinos.db"

        @Volatile
        private var instance: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_days ADD COLUMN modality TEXT NOT NULL DEFAULT 'strength'")
                db.execSQL("ALTER TABLE workout_days ADD COLUMN weekday INTEGER")

                db.execSQL("ALTER TABLE workout_plan_exercises ADD COLUMN metricType TEXT NOT NULL DEFAULT 'reps_weight'")
                db.execSQL("ALTER TABLE workout_plan_exercises ADD COLUMN targetDurationSeconds INTEGER")
                db.execSQL("ALTER TABLE workout_plan_exercises ADD COLUMN targetDistanceMeters REAL")
                db.execSQL("ALTER TABLE workout_plan_exercises ADD COLUMN targetPaceSecondsPerKm INTEGER")
                db.execSQL("ALTER TABLE workout_plan_exercises ADD COLUMN targetHrZone TEXT")

                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN modality TEXT NOT NULL DEFAULT 'strength'")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN source TEXT NOT NULL DEFAULT 'plan'")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN title TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN totalDistanceMeters REAL")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN poolLengthMeters INTEGER")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN stroke TEXT")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN sessionRpe REAL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_plans ADD COLUMN kind TEXT NOT NULL DEFAULT 'main'")
            }
        }

        // H3-24: mídia do exercício do plano (passos e paths de imagem) passa a
        // ser persistida. Colunas aditivas com default vazio, sem destruição —
        // mesmo padrão das migrações 1→2 e 2→3.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_plan_exercises ADD COLUMN instructions TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE workout_plan_exercises ADD COLUMN images TEXT NOT NULL DEFAULT ''")
            }
        }

        // H3-42: eventos de uso do exercício (concluído/pulado/trocado) para
        // calibrar a precisão da próxima geração. Tabela nova, aditiva.
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS exercise_usage_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        exerciseId TEXT NOT NULL,
                        outcome TEXT NOT NULL,
                        occurredAt INTEGER NOT NULL,
                        planId INTEGER,
                        dayId INTEGER,
                        source TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        // H3-17: alvo de reps do plano passa a ser persistido na sessão para o
        // relógio mostrar "Alvo" sem rederivar do plano. Coluna aditiva, sem
        // destruição.
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE session_exercises ADD COLUMN targetReps TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        // H4-4: tipo da série ('normal' | 'warmup' | 'drop' | 'amrap') e RIR.
        // Colunas aditivas com default; backfill marca como 'warmup' quem já
        // tinha `isWarmup = 1`. Sem destruição — mesmo padrão de 3→4 e 5→6.
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE set_logs ADD COLUMN setType TEXT NOT NULL DEFAULT 'normal'")
                db.execSQL("ALTER TABLE set_logs ADD COLUMN rir INTEGER")
                db.execSQL("UPDATE set_logs SET setType = 'warmup' WHERE isWarmup = 1")
            }
        }

        // H4-7: chave de deduplicação de importação de CSV (Strong/Hevy).
        // Coluna aditiva e índice único — NULLs são distintos no SQLite, então
        // as sessões criadas no app (todas `null`) não colidem. Nenhuma tabela
        // é recriada e nenhum dado existente é tocado.
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN importRef TEXT")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_sessions_importRef` " +
                        "ON `workout_sessions` (`importRef`)"
                )
            }
        }

        // H4-8: medidas corporais ao longo do tempo (peso, gordura,
        // circunferências, foto de progresso). Tabela e índice aditivos —
        // nenhuma tabela existente é tocada e nenhum dado é perdido.
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS body_measurements (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        measuredAt INTEGER NOT NULL,
                        weightKg REAL, bodyFatPercent REAL,
                        waistCm REAL, chestCm REAL, armCm REAL, thighCm REAL, hipCm REAL,
                        photoPath TEXT, notes TEXT NOT NULL DEFAULT '', source TEXT NOT NULL DEFAULT 'manual'
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_body_measurements_measuredAt` " +
                        "ON `body_measurements` (`measuredAt`)"
                )
            }
        }

        // H4-12: carga-alvo aplicada pelo usuário ao exercício do plano a
        // partir da sugestão de progressão. Coluna aditiva e opcional —
        // `NULL` = sem alvo definido (comportamento atual); nenhuma tabela
        // é recriada e nenhum dado é perdido.
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_plan_exercises ADD COLUMN targetWeightKg REAL")
            }
        }

        // H5-1: outbox persistente para sincronização incremental. Nenhum dado
        // existente é alterado; mutations reenviadas ficam após crash/reinício.
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_outbox (
                        mutationId TEXT PRIMARY KEY NOT NULL,
                        entity TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        operation TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        payloadJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sync_outbox_createdAt` " +
                        "ON `sync_outbox` (`createdAt`)"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                    )
                    .build()
                    .also { instance = it }
            }
    }
}
