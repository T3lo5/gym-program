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

/** Aba correspondente a uma modalidade armazenada. */
fun tabOfModality(modality: String?): ModalityTab = when (modality) {
    "run" -> ModalityTab.RUN
    "mobility" -> ModalityTab.MOBILITY
    else -> ModalityTab.STRENGTH
}

/** Agrupa as sessões do plano por modalidade, preservando o dayOrder dentro de cada grupo. */
fun groupSessionsByModality(
    days: List<WorkoutDayWithExercises>,
): Map<ModalityTab, List<WorkoutDayWithExercises>> {
    return days.groupBy { day -> tabOfModality(day.day.modality) }
        .mapValues { (_, sessions) ->
            sessions.sortedWith(
                compareBy(
                    { it.day.dayOrder },
                    { it.day.weekday ?: Int.MAX_VALUE },
                )
            )
        }
}

/** O que a Home mostra como "treino de hoje", derivado do plano e do weekday atual. */
sealed interface TodaySession {
    /** Hoje tem sessão: herói com "Iniciar" direto. */
    data class Today(val day: WorkoutDayWithExercises) : TodaySession

    /** Hoje é descanso; a próxima sessão do plano cai em [day]. */
    data class Next(val day: WorkoutDayWithExercises) : TodaySession

    /** Plano sem sessão em dia fixo da semana (dias flexíveis): nada marca "hoje". */
    data object Rest : TodaySession
}

/**
 * Seleção pura do treino de hoje (H3-28): sessão de hoje, próxima sessão futura
 * — procurando dia a dia na semana, com volta ao domingo — ou descanso. Dias sem
 * weekday fixo nunca são "hoje". Consumido pela Home (herói) e pela aba inicial.
 */
fun todaySessionSelector(
    days: List<WorkoutDayWithExercises>,
    todayWeekday: Int,
): TodaySession {
    sessionsOnWeekday(days, todayWeekday)
        .firstOrNull { !it.day.isRestDay }
        ?.let { return TodaySession.Today(it) }
    for (offset in 1..7) {
        val weekday = (todayWeekday + offset - 1) % 7 + 1
        sessionsOnWeekday(days, weekday)
            .firstOrNull { !it.day.isRestDay }
            ?.let { return TodaySession.Next(it) }
    }
    return TodaySession.Rest
}

/** Aba inicial: a de hoje quando hoje tem sessão; senão a da próxima sessão futura. */
fun defaultModalityTab(
    days: List<WorkoutDayWithExercises>,
    todayWeekday: Int,
): ModalityTab? =
    when (val today = todaySessionSelector(days, todayWeekday)) {
        is TodaySession.Today -> tabOfModality(today.day.day.modality)
        is TodaySession.Next -> tabOfModality(today.day.day.modality)
        TodaySession.Rest -> null
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
