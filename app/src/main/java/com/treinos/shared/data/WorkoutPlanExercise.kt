package com.treinos.shared.data

data class WorkoutPlanExercise(
    val dayId: Long = 0,
    val exerciseId: String = "",
    val exerciseOrder: Int = 0,
    val sets: Int = 3,
    val targetReps: String = "8-12",
    val targetRpe: Double? = null,
    val restSeconds: Int = 90,
    val notes: String = "",
    val isSupersetWithPrevious: Boolean = false
)
