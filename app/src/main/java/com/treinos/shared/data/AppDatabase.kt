package com.treinos.shared.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Exercise::class,
        WorkoutPlan::class,
        WorkoutDay::class,
        WorkoutPlanExercise::class,
        WorkoutSession::class,
        SessionExerciseLog::class,
        SetLog::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutPlanDao(): WorkoutPlanDao
    abstract fun workoutDayDao(): WorkoutDayDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun sessionExerciseLogDao(): SessionExerciseLogDao
    abstract fun setLogDao(): SetLogDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "treinos_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class Converters {
    @androidx.room.TypeConverter
    fun fromList(list: List<String>?): String? {
        return list?.joinToString(",")
    }

    @androidx.room.TypeConverter
    fun toList(data: String?): List<String> {
        return data?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }
}