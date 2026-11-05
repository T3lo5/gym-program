package com.treinos.app.ui

import com.treinos.app.data.local.WorkoutDayWithExercises

/** Ordena os dias da semana híbrida por weekday (1=seg … 7=dom) e dayOrder. */
fun sortWeekDays(days: List<WorkoutDayWithExercises>): List<WorkoutDayWithExercises> =
    days.sortedWith(
        compareBy(
            { it.day.weekday ?: Int.MAX_VALUE },
            { it.day.dayOrder },
        )
    )
