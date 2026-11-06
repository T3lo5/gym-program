package com.treinos.app.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupFile(
    val schemaVersion: Int,
    val exportedAt: String,
    val app: BackupApp,
    val deviceId: String,
    val plans: List<BackupPlan>,
    val sessions: List<BackupSession>,
    val profile: BackupProfile? = null,
)

/** Perfil local (H1-5). Ausente em backups v1/v2. `sex` entrou no bloco v3 sem novo bump (H3-7). */
@Serializable
data class BackupProfile(
    val bodyWeightKg: Double? = null,
    val heightCm: Int? = null,
    val sex: String? = null,
)

@Serializable
data class BackupApp(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
)

@Serializable
data class BackupPlan(
    val id: Long,
    val serverPlanId: Long?,
    val name: String,
    val description: String,
    val goal: String,
    val daysPerWeek: Int,
    val isActive: Boolean,
    val generatedByAi: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Int,
    val nextReviewAt: String?,
    val reviewAfterWeeks: Int,
    val source: String,
    val days: List<BackupDay>,
)

@Serializable
data class BackupDay(
    val id: Long,
    val planId: Long,
    val name: String,
    val dayOrder: Int,
    val notes: String,
    val isRestDay: Boolean,
    val modality: String = "strength",
    val weekday: Int? = null,
    val exercises: List<BackupPlanExercise>,
)

@Serializable
data class BackupPlanExercise(
    val id: Long,
    val exerciseId: String,
    val exerciseName: String,
    val primaryMuscles: String,
    val equipment: String,
    val exerciseOrder: Int,
    val sets: Int,
    val targetReps: String,
    val targetRpe: Double?,
    val restSeconds: Int,
    val notes: String,
    val isSupersetWithPrevious: Boolean,
    val dayId: Long,
    val metricType: String = "reps_weight",
    val targetDurationSeconds: Int? = null,
    val targetDistanceMeters: Double? = null,
    val targetPaceSecondsPerKm: Int? = null,
    val targetHrZone: String? = null,
)

@Serializable
data class BackupSession(
    val id: Long,
    val planId: Long,
    val dayId: Long,
    val planName: String,
    val dayName: String,
    val status: String,
    val startedAt: Long,
    val endedAt: Long?,
    val totalDurationSeconds: Long,
    val notes: String,
    val modality: String = "strength",
    val source: String = "plan",
    val title: String = "",
    val totalDistanceMeters: Double? = null,
    val poolLengthMeters: Int? = null,
    val stroke: String? = null,
    val sessionRpe: Double? = null,
    val exercises: List<BackupSessionExercise>,
)

@Serializable
data class BackupSessionExercise(
    val id: Long,
    val exerciseId: String,
    val exerciseName: String,
    val exerciseOrder: Int,
    val plannedSets: Int,
    val completedSets: Int,
    val restSeconds: Int,
    val startedAt: Long,
    val endedAt: Long?,
    val sessionId: Long,
    val sets: List<BackupSetLog>,
)

@Serializable
data class BackupSetLog(
    val id: Long,
    val setNumber: Int,
    val reps: Int?,
    val weightKg: Double?,
    val rpe: Double?,
    val durationSeconds: Long?,
    val distanceMeters: Double?,
    val isCompleted: Boolean,
    val isWarmup: Boolean,
    val restTakenSeconds: Int?,
    val completedAt: Long?,
    val exerciseLogId: Long,
)

data class BackupPreview(
    val plans: Int,
    val days: Int,
    val planExercises: Int,
    val sessions: Int,
    val sessionExercises: Int,
    val setLogs: Int,
)
