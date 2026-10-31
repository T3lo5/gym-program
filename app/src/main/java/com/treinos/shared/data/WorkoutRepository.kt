package com.treinos.shared.data

class WorkoutRepository(
    private val workoutPlanDao: WorkoutDao,
    private val workoutDayDao: WorkoutDao,
    private val exerciseDao: ExerciseDao
)
