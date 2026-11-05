package com.treinos.app.ui.history

import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.WorkoutSessionEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarMonthTest {

    @Test
    fun gera_grade_de_seis_semanas() {
        val grid = buildMonthGrid(year = 2026, month = 11)

        assertEquals(MONTH_GRID_CELLS, grid.size)
        assertEquals(30, grid.count { it != null })
        assertEquals(LocalDate.of(2026, 11, 1), grid.filterNotNull().first())
        assertEquals(LocalDate.of(2026, 11, 30), grid.filterNotNull().last())
        assertTrue(grid.filterNotNull().all { it.monthValue == 11 && it.year == 2026 })
    }

    @Test
    fun primeiro_dia_da_semana_e_segunda() {
        // 01/02/2027 cai numa segunda: com semana em segunda a célula 0 é o dia 1.
        val mondayFirst = buildMonthGrid(year = 2027, month = 2, firstDayOfWeek = DayOfWeek.MONDAY)
        val sundayFirst = buildMonthGrid(year = 2027, month = 2, firstDayOfWeek = DayOfWeek.SUNDAY)

        assertEquals(LocalDate.of(2027, 2, 1), mondayFirst[0])
        assertNull(sundayFirst[0])
        assertEquals(LocalDate.of(2027, 2, 1), sundayFirst[1])

        assertEquals(7, weekDayLabels().size)
        assertEquals(weekDayLabels(DayOfWeek.MONDAY).last(), weekDayLabels(DayOfWeek.SUNDAY).first())
    }

    @Test
    fun marca_dias_com_sessao() {
        val sessions = listOf(
            sessionAt(LocalDate.of(2026, 11, 10)),
            sessionAt(LocalDate.of(2026, 11, 10)),
            sessionAt(LocalDate.of(2026, 11, 12)),
        )

        val dates = sessionDates(sessions)

        assertEquals(
            setOf(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12)),
            dates,
        )
    }

    @Test
    fun mes_de_fevereiro_nao_estoura() {
        val grid = buildMonthGrid(year = 2028, month = 2)

        assertEquals(MONTH_GRID_CELLS, grid.size)
        assertEquals(29, grid.count { it != null })
        assertEquals(LocalDate.of(2028, 2, 29), grid.filterNotNull().last())
        assertTrue(grid.filterNotNull().all { it.monthValue == 2 })
    }

    @Test
    fun virada_de_ano() {
        assertEquals(LocalDate.of(2026, 12, 1), firstDayOfMonth(2026, 12))
        assertEquals(LocalDate.of(2026, 12, 31), lastDayOfMonth(2026, 12))
        assertEquals(LocalDate.of(2027, 1, 1), firstDayOfMonth(2027, 1))

        val december = buildMonthGrid(year = 2026, month = 12)
        val january = buildMonthGrid(year = 2027, month = 1)

        assertTrue(december.filterNotNull().all { it.year == 2026 && it.monthValue == 12 })
        assertTrue(january.filterNotNull().all { it.year == 2027 && it.monthValue == 1 })
        assertEquals(LocalDate.of(2027, 1, 1), january.filterNotNull().first())
        assertEquals(31, december.count { it != null })
        assertEquals(LocalDate.of(2027, 1, 1), lastDayOfMonth(2026, 12).plusDays(1))
    }

    @Test
    fun mes_futuro_nao_e_bloqueado() {
        val grid = buildMonthGrid(year = 2027, month = 11)

        assertEquals(MONTH_GRID_CELLS, grid.size)
        assertEquals(30, grid.count { it != null })

        val today = LocalDate.of(2026, 11, 4)
        assertTrue(isBeforeCurrentMonth(year = 2026, month = 10, today = today))
        assertFalse(isBeforeCurrentMonth(year = 2027, month = 11, today = today))
        assertFalse(isBeforeCurrentMonth(year = 2026, month = 11, today = today))
    }

    private fun sessionAt(date: LocalDate): SessionWithExercises {
        val startedAt = date.atTime(18, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return SessionWithExercises(
            session = WorkoutSessionEntity(id = date.dayOfYear.toLong(), startedAt = startedAt),
            exercises = emptyList(),
        )
    }
}
