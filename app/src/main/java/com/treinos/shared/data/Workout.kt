package com.treinos.shared.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "workout_days",
    indices = [Index("planId"), Index("dayOrder")],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutPlan::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@Serializable
data class WorkoutDay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val name: String,                 // "Dia A - Peito/Tríceps" ou "Segunda"
    val dayOrder: Int,                // 0, 1, 2...
    val notes: String = "",
    val isRestDay: Boolean = false
)

@Entity(
    tableName = "workout_plan_exercises",
    primaryKeys = ["dayId", "exerciseId", "exerciseOrder"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutDay::class,
            parentColumns = ["id"],
            childColumns = ["dayId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
@Serializable
data class WorkoutPlanExercise(
    val dayId: Long,
    val exerciseId: String,
    val exerciseOrder: Int,
    val sets: Int,
    val targetReps: String,           // "8-12" ou "10" ou "AMRAP"
    val targetRpe: Double? = null,    // 6.0 a 10.0
    val restSeconds: Int = 90,
    val notes: String = "",
    val isSupersetWithPrevious: Boolean = false
)

@Entity(
    tableName = "workout_plans",
    indices = [Index("createdAt"), Index("isActive")]
)
@Serializable
data class WorkoutPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val goal: String = "hypertrophy", // hypertrophy | strength | endurance | power | fat_loss
    val daysPerWeek: Int = 4,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val generatedByAi: Boolean = false,
    val aiPromptHash: String? = null,
    val version: Int = 1
)

@Entity(
    tableName = "workout_sessions",
    indices = [Index("planId"), Index("startedAt"), Index("status")]
)
@Serializable
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val dayId: Long,
    val status: String = "in_progress", // in_progress | completed | paused | cancelled
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val totalDurationSeconds: Long = 0,
    val notes: String = ""
)

@Entity(
    tableName = "session_exercise_logs",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
@Serializable
data class SessionExerciseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val exerciseOrder: Int,
    val plannedSets: Int,
    val completedSets: Int = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null
)

@Entity(
    tableName = "set_logs",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseLog::class,
            parentColumns = ["id"],
            childColumns = ["exerciseLogId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@Serializable
data class SetLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseLogId: Long,
    val setNumber: Int,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val rpe: Double? = null,
    val durationSeconds: Long? = null,    // Para exercícios de tempo (plank, etc.)
    val distanceMeters: Double? = null,   // Para cardio
    val isCompleted: Boolean = false,
    val isWarmup: Boolean = false,
    val restTakenSeconds: Int? = null,
    val completedAt: Long? = null
)

// Tipo de treino para enumeração
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