package com.treinos.shared.data

data class WorkoutDay(
    val id: Long = 0,
    val planId: Long = 0,
    val name: String = "",
    val dayOrder: Int = 0,
    val notes: String = "",
    val isRestDay: Boolean = false
)
