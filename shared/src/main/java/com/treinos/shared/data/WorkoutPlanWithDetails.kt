package com.treinos.shared.data

data class WorkoutPlanWithDetails(
    val plan: WorkoutPlan,
    val days: List<WorkoutDayWithExercises>
)
