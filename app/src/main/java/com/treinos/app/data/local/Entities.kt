package com.treinos.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** `source` de uma sessão registrada avulsa, sem plano nem dia (H2-5). */
const val MANUAL_SESSION_SOURCE = "manual"

@Entity(tableName = "workout_plans")
data class WorkoutPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverPlanId: Long? = null,
    val name: String,
    val description: String = "",
    val goal: String = "hypertrophy",
    val daysPerWeek: Int = 4,
    val isActive: Boolean = true,
    val generatedByAi: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Int = 1,
    val nextReviewAt: String? = null,
    val reviewAfterWeeks: Int = 4,
    val source: String = "local",
    @ColumnInfo(defaultValue = "main")
    val kind: String = "main",
)

@Entity(
    tableName = "workout_days",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("planId")],
)
data class WorkoutDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val name: String,
    val dayOrder: Int = 0,
    val notes: String = "",
    val isRestDay: Boolean = false,
    @ColumnInfo(defaultValue = "strength")
    val modality: String = "strength",
    val weekday: Int? = null,
)

@Entity(
    tableName = "workout_plan_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["dayId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("dayId")],
)
data class WorkoutPlanExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val exerciseId: String,
    val exerciseName: String = "",
    val primaryMuscles: String = "",
    val equipment: String = "",
    val exerciseOrder: Int = 0,
    val sets: Int = 3,
    val targetReps: String = "8-12",
    val targetRpe: Double? = null,
    val restSeconds: Int = 90,
    val notes: String = "",
    val isSupersetWithPrevious: Boolean = false,
    @ColumnInfo(defaultValue = "reps_weight")
    val metricType: String = "reps_weight",
    val targetDurationSeconds: Int? = null,
    val targetDistanceMeters: Double? = null,
    val targetPaceSecondsPerKm: Int? = null,
    val targetHrZone: String? = null,
)

@Entity(tableName = "workout_sessions", indices = [Index("startedAt")])
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long = 0,
    val dayId: Long = 0,
    val planName: String = "",
    val dayName: String = "",
    val status: String = "in_progress",
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val totalDurationSeconds: Long = 0,
    val notes: String = "",
    @ColumnInfo(defaultValue = "strength")
    val modality: String = "strength",
    @ColumnInfo(defaultValue = "plan")
    val source: String = "plan",
    @ColumnInfo(defaultValue = "")
    val title: String = "",
    val totalDistanceMeters: Double? = null,
    val poolLengthMeters: Int? = null,
    val stroke: String? = null,
    val sessionRpe: Double? = null,
)

@Entity(
    tableName = "session_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("sessionId")],
)
data class SessionExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val exerciseName: String = "",
    val exerciseOrder: Int = 0,
    val plannedSets: Int = 3,
    val completedSets: Int = 0,
    val restSeconds: Int = 90,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
)

@Entity(
    tableName = "set_logs",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseLogId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("exerciseLogId")],
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseLogId: Long,
    val setNumber: Int,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val rpe: Double? = null,
    val durationSeconds: Long? = null,
    val distanceMeters: Double? = null,
    val isCompleted: Boolean = true,
    val isWarmup: Boolean = false,
    val restTakenSeconds: Int? = null,
    val completedAt: Long? = System.currentTimeMillis(),
)

data class WorkoutPlanWithDetails(
    val plan: WorkoutPlanEntity,
    val days: List<WorkoutDayWithExercises>,
)

data class WorkoutDayWithExercises(
    val day: WorkoutDayEntity,
    val exercises: List<WorkoutPlanExerciseEntity>,
)

data class SessionWithExercises(
    val session: WorkoutSessionEntity,
    val exercises: List<SessionExerciseWithSets>,
)

data class SessionExerciseWithSets(
    val exercise: SessionExerciseEntity,
    val sets: List<SetLogEntity>,
)
