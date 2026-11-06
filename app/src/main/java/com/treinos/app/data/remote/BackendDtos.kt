package com.treinos.app.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class GeneratePlanRequest(
    val deviceId: String,
    val goal: String = "hypertrophy",
    val environment: String? = null,
    val daysPerWeek: Int = 4,
    val availableEquipment: List<String> = emptyList(),
    val targetMuscles: List<String> = emptyList(),
    val injuriesOrLimitations: String = "",
    val currentLevel: String = "intermediate",
    val sessionDurationMinutes: Int = 60,
    val preferredSplit: String? = null,
    val reviewAfterWeeks: Int = 4,
    val bodyWeightKg: Double? = null,
    val heightCm: Int? = null,
    val sex: String? = null,
    val modalities: List<ModalityDto> = emptyList(),
)

@Serializable
data class ModalityDto(
    val type: String = "strength",
    val sessionsPerWeek: Int = 1,
    val goal: String = "hypertrophy",
    val level: String = "intermediate",
    val availableEquipment: List<String> = emptyList(),
    val targetMuscles: List<String> = emptyList(),
    val focus: String? = null,
    val recent5kSeconds: Int? = null,
    val sessionDurationMinutes: Int = 60,
)

@Serializable
data class GeneratePlanResponse(
    val workout: WorkoutPlanDto,
    val generationTimeMs: Int = 0,
    val modelUsed: String = "",
    val planId: Long = 0,
    val nextReviewAt: String,
    val reviewAfterWeeks: Int = 4,
    val source: String = "local",
)

@Serializable
data class WorkoutPlanDto(
    val name: String,
    val description: String = "",
    val goal: String,
    val daysPerWeek: Int,
    val days: List<WorkoutDayDto> = emptyList(),
)

@Serializable
data class WorkoutDayDto(
    val name: String,
    val dayOrder: Int = 0,
    val exercises: List<WorkoutExerciseDto> = emptyList(),
    val isRestDay: Boolean = false,
    val weekday: Int? = null,
    val modality: String = "strength",
)

@Serializable
data class WorkoutExerciseDto(
    val exerciseId: String,
    val exerciseOrder: Int = 0,
    val sets: Int = 3,
    val targetReps: String = "8-12",
    val targetRpe: Double? = null,
    val restSeconds: Int = 90,
    val notes: String = "",
    val isSupersetWithPrevious: Boolean = false,
    val exerciseName: String = "",
    val primaryMuscles: List<String> = emptyList(),
    val equipment: String = "",
    val metricType: String = "reps_weight",
    val targetDurationSeconds: Int? = null,
    val targetDistanceMeters: Double? = null,
    val targetPaceSecondsPerKm: Int? = null,
    val targetHrZone: String? = null,
)

@Serializable
data class HealthResponse(
    val status: String = "",
    val timestamp: String = "",
    val model: String = "",
    val catalogSize: Int = 0,
)

@Serializable
data class SyncRequestDto(
    val deviceId: String,
    val syncedAt: String,
    val plans: List<SyncPlanDto> = emptyList(),
    val sessions: List<SyncSessionDto> = emptyList(),
)

@Serializable
data class SyncPlanDto(
    val localId: Long,
    val name: String,
    val description: String = "",
    val goal: String = "",
    val daysPerWeek: Int = 0,
    val isActive: Boolean = false,
    val generatedByAi: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val version: Int = 1,
    val nextReviewAt: String? = null,
    val reviewAfterWeeks: Int = 4,
    val source: String = "local",
    val days: List<SyncDayDto> = emptyList(),
)

@Serializable
data class SyncDayDto(
    val localId: Long,
    val name: String = "",
    val dayOrder: Int = 0,
    val notes: String = "",
    val isRestDay: Boolean = false,
    val exercises: List<SyncPlanExerciseDto> = emptyList(),
    val modality: String = "strength",
    val weekday: Int? = null,
)

@Serializable
data class SyncPlanExerciseDto(
    val exerciseId: String,
    val exerciseOrder: Int = 0,
    val sets: Int = 0,
    val targetReps: String = "",
    val targetRpe: Double? = null,
    val restSeconds: Int = 90,
    val notes: String = "",
    val isSupersetWithPrevious: Boolean = false,
    val exerciseName: String = "",
    val primaryMuscles: List<String> = emptyList(),
    val equipment: String = "",
    val metricType: String = "reps_weight",
    val targetDurationSeconds: Int? = null,
    val targetDistanceMeters: Double? = null,
    val targetPaceSecondsPerKm: Int? = null,
    val targetHrZone: String? = null,
)

@Serializable
data class SyncSessionDto(
    val localId: Long,
    val planLocalId: Long = 0,
    val dayLocalId: Long = 0,
    val planName: String = "",
    val dayName: String = "",
    val status: String = "",
    val startedAt: Long = 0,
    val endedAt: Long? = null,
    val totalDurationSeconds: Long = 0,
    val notes: String = "",
    val exercises: List<SyncSessionExerciseDto> = emptyList(),
    val modality: String = "strength",
    val source: String = "plan",
    val title: String = "",
    val totalDistanceMeters: Double? = null,
    val poolLengthMeters: Int? = null,
    val stroke: String? = null,
    val sessionRpe: Double? = null,
)

@Serializable
data class SyncSessionExerciseDto(
    val localId: Long,
    val exerciseId: String,
    val exerciseName: String = "",
    val exerciseOrder: Int = 0,
    val plannedSets: Int = 0,
    val completedSets: Int = 0,
    val startedAt: Long = 0,
    val endedAt: Long? = null,
    val sets: List<SyncSetLogDto> = emptyList(),
)

@Serializable
data class SyncSetLogDto(
    val localId: Long,
    val setNumber: Int,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val rpe: Double? = null,
    val durationSeconds: Long? = null,
    val distanceMeters: Double? = null,
    val isCompleted: Boolean = true,
    val isWarmup: Boolean = false,
    val restTakenSeconds: Int? = null,
    val completedAt: Long? = null,
)

@Serializable
data class SyncResponseDto(
    val ok: Boolean = false,
    val plans: Int = 0,
    val sessions: Int = 0,
    val setLogs: Int = 0,
    val syncedAt: String = "",
)

@Serializable
data class SyncDownloadDto(
    val deviceId: String,
    val plans: List<SyncDownloadPlanDto> = emptyList(),
    val days: List<SyncDownloadDayDto> = emptyList(),
    val planExercises: List<SyncDownloadPlanExerciseDto> = emptyList(),
    val sessions: List<SyncDownloadSessionDto> = emptyList(),
    val sessionExercises: List<SyncDownloadSessionExerciseDto> = emptyList(),
    val setLogs: List<SyncDownloadSetLogDto> = emptyList(),
)

@Serializable
data class SyncDownloadPlanDto(
    val localId: Long,
    val name: String,
    val description: String = "",
    val goal: String = "",
    val daysPerWeek: Int = 0,
    val isActive: Boolean = false,
    val generatedByAi: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val version: Int = 1,
    val nextReviewAt: String? = null,
    val reviewAfterWeeks: Int = 4,
    val source: String = "local",
    val syncedAt: String? = null,
)

@Serializable
data class SyncDownloadDayDto(
    val id: Long,
    val planId: Long,
    val localId: Long,
    val name: String,
    val dayOrder: Int,
    val notes: String = "",
    val isRestDay: Boolean = false,
    val modality: String = "strength",
    val weekday: Int? = null,
)

@Serializable
data class SyncDownloadPlanExerciseDto(
    val id: Long,
    val dayId: Long,
    val exerciseId: String,
    val exerciseOrder: Int,
    val sets: Int,
    val targetReps: String = "",
    val targetRpe: Double? = null,
    val restSeconds: Int = 90,
    val notes: String = "",
    val isSupersetWithPrevious: Boolean = false,
    val exerciseName: String = "",
    val primaryMuscles: String = "",
    val equipment: String = "",
    val metricType: String = "reps_weight",
    val targetDurationSeconds: Int? = null,
    val targetDistanceMeters: Double? = null,
    val targetPaceSecondsPerKm: Int? = null,
    val targetHrZone: String? = null,
)

@Serializable
data class SyncDownloadSessionDto(
    val id: Long,
    val deviceId: String,
    val localId: Long,
    val planLocalId: Long,
    val dayLocalId: Long,
    val planName: String = "",
    val dayName: String = "",
    val status: String = "",
    val startedAt: Long = 0,
    val endedAt: Long? = null,
    val totalDurationSeconds: Long = 0,
    val notes: String = "",
    val syncedAt: String? = null,
    val modality: String = "strength",
    val source: String = "plan",
    val title: String = "",
    val totalDistanceMeters: Double? = null,
    val poolLengthMeters: Int? = null,
    val stroke: String? = null,
    val sessionRpe: Double? = null,
)

@Serializable
data class SyncDownloadSessionExerciseDto(
    val id: Long,
    val sessionId: Long,
    val localId: Long,
    val exerciseId: String,
    val exerciseName: String = "",
    val exerciseOrder: Int,
    val plannedSets: Int = 0,
    val completedSets: Int = 0,
    val startedAt: Long = 0,
    val endedAt: Long? = null,
)

@Serializable
data class SyncDownloadSetLogDto(
    val id: Long,
    val sessionExerciseId: Long,
    val localId: Long,
    val setNumber: Int,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val rpe: Double? = null,
    val durationSeconds: Long? = null,
    val distanceMeters: Double? = null,
    val isCompleted: Boolean = false,
    val isWarmup: Boolean = false,
    val restTakenSeconds: Int? = null,
    val completedAt: Long? = null,
)

@Serializable
data class CatalogExerciseDto(
    val id: String,
    val name: String = "",
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val equipment: String = "",
    val level: String = "",
    val instructions: List<String> = emptyList(),
    val images: List<String> = emptyList(),
)

@Serializable
data class CatalogResponseDto(
    val exercises: List<CatalogExerciseDto> = emptyList(),
)

@Serializable
data class ApiErrorDto(val detail: String = "Erro desconhecido")

// ---------------- Revisão de plano (H2-6) ----------------

@Serializable
data class ReviewPlanRequest(
    val deviceId: String,
    val plan: WorkoutPlanDto,
    val history: ReviewPlanHistory = ReviewPlanHistory(),
)

@Serializable
data class ReviewPlanHistory(
    val weeks: Int = 0,
    val adherence: Double = 1.0,
    val sessions: List<ReviewPlanSession> = emptyList(),
)

@Serializable
data class ReviewPlanSession(
    val modality: String = "strength",
    val completed: Boolean = false,
    val durationSeconds: Long = 0,
    val distanceMeters: Double? = null,
    val rpe: Double? = null,
    val startedAt: Long = 0,
)

@Serializable
data class ReviewPlanResponse(
    val workout: WorkoutPlanDto,
    val changes: List<String> = emptyList(),
    val source: String = "local",
)
