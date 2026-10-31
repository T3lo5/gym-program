package com.treinos.shared.util

import com.treinos.shared.data.Exercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BackendRepository(private val api: BackendApi) {

    suspend fun healthCheck(): HealthResponse = api.health()

    suspend fun getFullCatalog(): List<FreeExerciseDbExercise> = api.getCatalog().exercises

    suspend fun generateWorkout(request: GenerateRequest): GenerateResponse = api.generateWorkout(request)

    // Convert free-exercise-db format to our Exercise entity
    fun toExerciseList(catalog: List<FreeExerciseDbExercise>): List<Exercise> = catalog.map { ex ->
        Exercise(
            id = ex.id,
            name = ex.name,
            force = ex.force,
            level = ex.level,
            mechanic = ex.mechanic,
            equipment = ex.equipment,
            primaryMuscles = ex.primaryMuscles,
            secondaryMuscles = ex.secondaryMuscles,
            instructions = ex.instructions,
            category = ex.category,
            images = ex.images
        )
    }
}