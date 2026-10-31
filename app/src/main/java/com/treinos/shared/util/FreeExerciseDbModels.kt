package com.treinos.shared.util

import kotlinx.serialization.Serializable

@Serializable
data class FreeExerciseDbExercise(
    val id: String,
    val name: String,
    val force: String?,
    val level: String,
    val mechanic: String?,
    val equipment: String?,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val instructions: List<String>,
    val category: String,
    val images: List<String>
)

@Serializable
data class FreeExerciseDbWrapper(
    val exercises: List<FreeExerciseDbExercise>
)