package com.treinos.app.di

import android.content.Context
import com.treinos.shared.data.AppDatabase
import com.treinos.shared.data.ExerciseDao
import com.treinos.shared.data.ExerciseRepository
import com.treinos.shared.data.SetLogDao
import com.treinos.shared.data.WorkoutDayDao
import com.treinos.shared.data.WorkoutPlanDao
import com.treinos.shared.data.WorkoutSessionDao
import com.treinos.shared.data.SessionExerciseLogDao
import com.treinos.shared.util.BackendRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@dagger.hilt.android.qualifiers.ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao()

    @Provides
    @Singleton
    fun provideWorkoutPlanDao(db: AppDatabase): WorkoutPlanDao = db.workoutPlanDao()

    @Provides
    @Singleton
    fun provideWorkoutDayDao(db: AppDatabase): WorkoutDayDao = db.workoutDayDao()

    @Provides
    @Singleton
    fun provideWorkoutSessionDao(db: AppDatabase): WorkoutSessionDao = db.workoutSessionDao()

    @Provides
    @Singleton
    fun provideSessionExerciseLogDao(db: AppDatabase): SessionExerciseLogDao = db.sessionExerciseLogDao()

    @Provides
    @Singleton
    fun provideSetLogDao(db: AppDatabase): SetLogDao = db.setLogDao()

    @Provides
    @Singleton
    fun provideExerciseRepository(dao: ExerciseDao, backendRepository: BackendRepository): ExerciseRepository = ExerciseRepository(dao, backendRepository)
}