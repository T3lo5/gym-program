package com.treinos.app.ui.history

import com.treinos.app.data.local.SessionWithExercises
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Seis semanas cobrem qualquer mês começando em sábado/domingo com a semana completa. */
const val MONTH_GRID_CELLS = 42

private val ptBr = Locale.forLanguageTag("pt-BR")

fun buildMonthGrid(
    year: Int,
    month: Int,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
): List<LocalDate?> {
    val monthStart = YearMonth.of(year, month).atDay(1)
    val leadingDays = (monthStart.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val gridStart = monthStart.minusDays(leadingDays.toLong())
    return (0 until MONTH_GRID_CELLS).map { index ->
        gridStart.plusDays(index.toLong())
            .takeIf { it.year == year && it.monthValue == month }
    }
}

fun sessionDates(sessions: List<SessionWithExercises>): Set<LocalDate> =
    sessions.mapTo(mutableSetOf()) { sessionDay(it) }

fun firstDayOfMonth(year: Int, month: Int): LocalDate = YearMonth.of(year, month).atDay(1)

fun lastDayOfMonth(year: Int, month: Int): LocalDate = YearMonth.of(year, month).atEndOfMonth()

/** O mês atual é o último navegável para a frente. */
fun isBeforeCurrentMonth(year: Int, month: Int, today: LocalDate = LocalDate.now()): Boolean =
    YearMonth.of(year, month) < YearMonth.from(today)

fun weekDayLabels(firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): List<String> =
    (0 until 7).map { offset ->
        val day = DayOfWeek.of((firstDayOfWeek.value - 1 + offset) % 7 + 1)
        day.getDisplayName(TextStyle.SHORT, ptBr).trimEnd('.').replaceFirstChar { it.uppercaseChar() }
    }
