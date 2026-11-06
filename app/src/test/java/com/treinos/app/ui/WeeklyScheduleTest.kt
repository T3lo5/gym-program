package com.treinos.app.ui

import com.treinos.app.data.local.WorkoutDayEntity
import com.treinos.app.data.local.WorkoutDayWithExercises
import com.treinos.app.data.local.WorkoutPlanExerciseEntity
import com.treinos.app.data.local.WorkoutPlanWithDetails
import com.treinos.app.data.local.WorkoutPlanEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WeeklyScheduleTest {

    private fun day(
        weekday: Int?,
        dayOrder: Int,
        modality: String = "strength",
        exercises: Int = 1,
    ): WorkoutDayWithExercises =
        WorkoutDayWithExercises(
            day = WorkoutDayEntity(
                id = 0,
                planId = 0,
                name = "Dia $dayOrder",
                dayOrder = dayOrder,
                weekday = weekday,
                modality = modality,
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
            day(3, 0),
            day(1, 0),
            day(1, 1),
            day(2, 0),
        )
        val ordered = sortWeekDays(days)
        assertEquals(listOf(1, 1, 2, 3), ordered.map { it.day.weekday })
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
        val days = (1..7).map { day(it, 0) }
        assertEquals(7, sortWeekDays(days).size)
        val oito = listOf(day(1, 0, exercises = 2), day(2, 0), day(3, 0), day(4, 0), day(5, 0), day(6, 0), day(7, 0), day(1, 1))
        assertEquals(8, sortWeekDays(oito).size)
    }

    @Test
    fun agrupa_dias_por_modalidade() {
        val days = listOf(
            day(1, 0, "strength", exercises = 5),
            day(2, 0, "run"),
            day(3, 0, "mobility"),
            day(4, 0, "strength", exercises = 5),
        )
        val groups = groupSessionsByModality(days)
        assertEquals(2, groups[ModalityTab.STRENGTH]?.size)
        assertEquals(1, groups[ModalityTab.RUN]?.size)
        assertEquals(1, groups[ModalityTab.MOBILITY]?.size)
        assertEquals(4, groups.values.sumOf { it.size })
    }

    @Test
    fun mesmo_weekday_com_duas_modalidades_nao_vira_uma_sessao() {
        val days = listOf(
            day(1, 0, "strength"),
            day(1, 1, "run"),
        )
        val groups = groupSessionsByModality(days)
        assertEquals(1, groups[ModalityTab.STRENGTH]?.size)
        assertEquals(1, groups[ModalityTab.RUN]?.size)
    }

    @Test
    fun sessoes_de_modalidades_diferentes_ficam_em_grupos_diferentes() {
        val days = listOf(
            day(1, 0, "strength"),
            day(1, 1, "run"),
            day(1, 2, "mobility"),
        )
        val groups = groupSessionsByModality(days)
        assertEquals(1, groups[ModalityTab.STRENGTH]?.size)
        assertEquals(1, groups[ModalityTab.RUN]?.size)
        assertEquals(1, groups[ModalityTab.MOBILITY]?.size)
    }

    @Test
    fun aba_padrao_e_a_proxima_sessao() {
        val todayWeekday = 3 // quarta
        val days = listOf(
            day(4, 0, "run"), // amanhã (quinta) tem corrida
            day(1, 0, "strength"),
        )
        val tab = defaultModalityTab(days, todayWeekday)
        assertEquals(ModalityTab.RUN, tab)
    }

    @Test
    fun aba_padrao_sessao_hoje() {
        val todayWeekday = 1 // segunda
        val days = listOf(
            day(1, 0, "run"),
            day(3, 0, "strength"),
        )
        val tab = defaultModalityTab(days, todayWeekday)
        assertEquals(ModalityTab.RUN, tab)
    }

    @Test
    fun aba_padrao_sem_sessoes() {
        val tab = defaultModalityTab(emptyList(), 1)
        assertNull(tab)
    }

    @Test
    fun rotulo_do_plano_hibrido_conta_sessoes() {
        val plan = WorkoutPlanWithDetails(
            plan = WorkoutPlanEntity(
                id = 1,
                name = "Híbrido",
                goal = "general",
                daysPerWeek = 7,
            ),
            days = listOf(
                day(1, 0, "strength"),
                day(2, 0, "run"),
                day(3, 0, "mobility"),
            ),
        )
        assertEquals("3 sess\u00E3os/semana", planVolumeLabel(plan))
    }

    @Test
    fun sessoes_do_dia_ordenadas_por_day_order() {
        val days = listOf(
            day(1, 2, "run"),
            day(1, 0, "strength"),
            day(1, 1, "mobility"),
        )
        val sameDay = sessionsOnWeekday(days, 1)
        assertEquals(listOf(0, 1, 2), sameDay.map { it.day.dayOrder })
    }
}
