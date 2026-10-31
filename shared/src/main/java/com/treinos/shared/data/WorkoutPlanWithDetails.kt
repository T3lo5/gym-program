package com.treinos.shared.data

data class WorkoutPlanWithDetails(
    val plan: WorkoutPlan,
    val days: List<WorkoutDayWithExercises>
)

data class WorkoutDayWithExercises(
    val day: WorkoutDay,
    val exercises: List<WorkoutPlanExerciseWithDetails>
)

data class WorkoutPlanExerciseWithDetails(
    val planExercise: WorkoutPlanExercise,
    val exercise: Exercise?
)
