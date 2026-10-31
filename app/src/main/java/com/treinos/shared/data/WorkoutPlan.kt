package com.treinos.shared.data

data class WorkoutPlan(
    val id: Long = 0,
    val name: String = "",
    val description: String = "",
    val goal: String = "hypertrophy",
    val daysPerWeek: Int = 4,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val generatedByAi: Boolean = false,
    val aiPromptHash: String? = null,
    val version: Int = 1
)
