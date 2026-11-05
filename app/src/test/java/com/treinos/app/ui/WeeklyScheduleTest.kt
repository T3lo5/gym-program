package com.treinos.app.ui

import com.treinos.app.data.local.WorkoutDayEntity
import com.treinos.app.data.local.WorkoutDayWithExercises
import com.treinos.app.data.local.WorkoutPlanExerciseEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyScheduleTest {

    private fun day(weekday: Int?, dayOrder: Int, exercises: Int = 1): WorkoutDayWithExercises =
        WorkoutDayWithExercises(
            day = WorkoutDayEntity(
                id = 0,
                planId = 0,
                name = "Dia",
                dayOrder = dayOrder,
                weekday = weekday,
                modality = "strength",
            ),
            exercises = List(exercises) { i ->
                WorkoutPlanExerciseEntity(
                    id = 0.toLong() + i,
                    dayId = 0,
                    exerciseId = "ex$i",
                    exerciseOrder = i,
                    sets = 3,
                )
            },
        )

    @Test
    fun ordena_por_weekday_e_day_order() {
        val days = listOf(
            day(3, 0), // quarta
            day(1, 0), // segunda
            day(1, 1), // segunda, segunda sessão
            day(2, 0), // terça
        )
        val ordered = sortWeekDays(days)
        assertEquals(
            listOf(1, 1, 2, 3),
            ordered.map { it.day.weekday }
        )
        assertEquals(listOf(0, 1, 0, 0), ordered.map { it.day.dayOrder })
    }

    @Test
    fun permite_duas_sessoes_no_mesmo_dia() {
        val days = listOf(day(1, 0, exercises = 2), day(2, 0, exercises = 1))
        val ordered = sortWeekDays(days)
        assertEquals(2, ordered.first { it.day.weekday == 1 }.exercises.size)
        assertEquals(1, ordered.first { it.day.weekday == 2 }.exercises.size)
    }

    @Test
    fun conta_sessoes_e_nao_dias() {
        // 3 musculação + 2 corrida + 2 mobilidade = 7 sessões em 7 dias distintos.
        val days = (1..7).map { day(it, 0) }
        assertEquals(7, sortWeekDays(days).size)
        // 8 sessões com duas no mesmo weekday continuam sendo 8 dias de exibição (uma linha por sessão agrupada).
        val oito = listOf(day(1, 0, exercises = 2), day(2, 0), day(3, 0), day(4, 0), day(5, 0), day(6, 0), day(7, 0), day(1, 1))
        assertEquals(8, sortWeekDays(oito).size)
    }
}
