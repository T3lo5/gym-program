package com.treinos.shared.util

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface BackendApi {
    @GET("health")
    suspend fun health(): HealthResponse

    @GET("v1/catalog")
    suspend fun getCatalog(): CatalogResponse

    @POST("v1/generate-workout")
    suspend fun generateWorkout(@Body request: GenerateRequest): GenerateResponse
}

@Serializable
data class HealthResponse(
    val status: String,
    val timestamp: String,
    val model: String,
    val catalogSize: Int
)

@Serializable
data class CatalogResponse(
    val exercises: List<FreeExerciseDbExercise>
)

@Serializable
data class GenerateRequest(
    val goal: String,
    val daysPerWeek: Int,
    val availableEquipment: List<String>,
    val targetMuscles: List<String>,
    val injuriesOrLimitations: String,
    val currentLevel: String,
    val sessionDurationMinutes: Int,
    val preferredSplit: String?
)

@Serializable
data class GenerateResponse(
    val workout: WorkoutPlanDTO,
    val generationTimeMs: Int,
    val modelUsed: String
)

@Serializable
data class WorkoutPlanDTO(
    val name: String,
    val description: String,
    val goal: String,
    val daysPerWeek: Int,
    val days: List<DayDTO>
)

@Serializable
data class DayDTO(
    val name: String,
    val dayOrder: Int,
    val exercises: List<ExerciseDTO>,
    val isRestDay: Boolean = false
)

@Serializable
data class ExerciseDTO(
    val exerciseId: String,
    val exerciseOrder: Int,
    val sets: Int,
    val targetReps: String,
    val targetRpe: Double?,
    val restSeconds: Int,
    val notes: String,
    val isSupersetWithPrevious: Boolean
)