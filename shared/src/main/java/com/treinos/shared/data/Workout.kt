package com.treinos.shared.data

import kotlinx.serialization.Serializable

@Serializable
data class WorkoutSession(
    val id: Long = 0,
    val planId: Long = 0,
    val dayId: Long = 0,
    val status: String = "in_progress",
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val totalDurationSeconds: Long = 0,
    val notes: String = ""
)

@Serializable
data class SessionExerciseLog(
    val id: Long = 0,
    val sessionId: Long = 0,
    val exerciseId: String = "",
    val exerciseOrder: Int = 0,
    val plannedSets: Int = 3,
    val completedSets: Int = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null
)

@Serializable
data class SetLog(
    val id: Long = 0,
    val exerciseLogId: Long = 0,
    val setNumber: Int = 0,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val rpe: Double? = null,
    val durationSeconds: Long? = null,
    val distanceMeters: Double? = null,
    val isCompleted: Boolean = false,
    val isWarmup: Boolean = false,
    val restTakenSeconds: Int? = null,
    val completedAt: Long? = null
)

enum class WorkoutGoal(val value: String, val labelPt: String) {
    HYPERTROPHY("hypertrophy", "Hipertrofia"),
    STRENGTH("strength", "Força Máxima"),
    ENDURANCE("endurance", "Resistência"),
    POWER("power", "Potência"),
    FAT_LOSS("fat_loss", "Perda de Gordura"),
    GENERAL("general", "Condicionamento Geral")
}

enum class SessionStatus(val value: String) {
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    PAUSED("paused"),
    CANCELLED("cancelled")
}
