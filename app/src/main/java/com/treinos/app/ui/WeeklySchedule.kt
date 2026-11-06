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

enum class ModalityTab { STRENGTH, RUN, MOBILITY }

/** Agrupa as sessões do plano por modalidade, preservando o dayOrder dentro de cada grupo. */
fun groupSessionsByModality(
    days: List<WorkoutDayWithExercises>,
): Map<ModalityTab, List<WorkoutDayWithExercises>> {
    return days.groupBy { day ->
        when (day.day.modality) {
            "run" -> ModalityTab.RUN
            "mobility" -> ModalityTab.MOBILITY
            else -> ModalityTab.STRENGTH
        }
    }.mapValues { (_, sessions) ->
        sessions.sortedWith(
            compareBy(
                { it.day.dayOrder },
                { it.day.weekday ?: Int.MAX_VALUE },
            )
        )
    }
}

/** Retorna a aba da próxima sessão (hoje ou o próximo weekday); sem sessões, null. */
fun defaultModalityTab(
    days: List<WorkoutDayWithExercises>,
    todayWeekday: Int,
): ModalityTab? {
    val tomorrow = todayWeekday % 7 + 1
    val todaySessions = sessionsOnWeekday(days, todayWeekday)
    val nextSessions = sessionsOnWeekday(days, tomorrow)
    val candidates = if (todaySessions.isNotEmpty()) todaySessions else nextSessions
    return candidates.firstOrNull()?.let { day ->
        when (day.day.modality) {
            "run" -> ModalityTab.RUN
            "mobility" -> ModalityTab.MOBILITY
            else -> ModalityTab.STRENGTH
        }
    }
}

/** Todas as sessões de um weekday, ordenadas por dayOrder. */
fun sessionsOnWeekday(
    days: List<WorkoutDayWithExercises>,
    weekday: Int,
): List<WorkoutDayWithExercises> =
    days.filter { it.day.weekday == weekday }
        .sortedBy { it.day.dayOrder }

/** Rótulo do plano contando sessões (dias podem repetir por weekday). */
fun planVolumeLabel(plan: com.treinos.app.data.local.WorkoutPlanWithDetails): String {
    val sessions = plan.days.size
    return "$sessions sessão${if (sessions != 1) "s" else ""}/semana"
}
