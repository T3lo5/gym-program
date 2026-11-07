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
    ],
    version = 4,
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

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
