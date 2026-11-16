package com.treinos.app.ui.history

import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.ui.progression.OneRepMaxFormula
import com.treinos.app.ui.progression.PersonalRecord
import com.treinos.app.ui.progression.RecordSet
import com.treinos.app.ui.progression.isPersonalRecord
import com.treinos.app.ui.progression.personalRecord
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * H4-13: streak/consistência e resumo mensal/anual, derivados do histórico
 * local — leitura pura, sem I/O, sem rede, sem mudança de Room.
 *
 * Regras da spec (docs/specs/H4-13-streak-resumo-periodico.md):
 * - Só sessões `STATUS_COMPLETED` entram em treinos, volume, dias ativos e
 *   média; `newPrCount` é a exceção e considera também séries de sessões em
 *   andamento (regra 11 da H4-1).
 * - Datas no fuso do sistema via [sessionDay] (mesma regra do calendário).
 * - A comparação de PR reusa o motor canônico da H4-1
 *   (`personalRecord`/`isPersonalRecord`) — nada de reimplementar a regra.
 */

/** Recorte do relatório do Histórico: mês exibido no calendário ou o ano dele. */
enum class ReportPeriod { MONTH, YEAR }

/** Sequência de dias com treino concluído, para o card da Home. */
data class StreakSummary(
    val currentDays: Int,
    val bestDays: Int,
    val lastCompletedDate: LocalDate?,
)

/** Resumo do período exibido no Histórico, com as métricas da H3-28 reusadas. */
data class PeriodReport(
    val period: ReportPeriod,
    val from: LocalDate,
    val to: LocalDate,
    val metrics: HistoryMetrics,
    val activeDays: Int,
    val newPrCount: Int,
    val sessionsPerWeek: Double,
)

/**
 * Sequência de treino a partir das datas com pelo menos uma sessão concluída
 * (duplicatas contam uma vez — o [Set] garante). Datas futuras (`> today`)
 * são descartadas antes de qualquer cálculo.
 *
 * `currentDays`: se `today` está no conjunto, conta para trás a partir de
 * hoje; senão, se `today.minusDays(1)` está, conta para trás a partir de
 * ontem; senão `0`. `bestDays` é a maior sequência consecutiva do conjunto.
 */
fun buildStreak(completedDates: Set<LocalDate>, today: LocalDate): StreakSummary {
    val dates = completedDates.filterTo(mutableSetOf()) { !it.isAfter(today) }
    val current = when {
        today in dates -> runLengthEndingAt(dates, today)
        today.minusDays(1) in dates -> runLengthEndingAt(dates, today.minusDays(1))
        else -> 0
    }
    var best = 0
    var run = 0
    val sorted = dates.sorted()
    sorted.forEachIndexed { index, day ->
        run = if (index > 0 && sorted[index - 1].plusDays(1) == day) run + 1 else 1
        if (run > best) best = run
    }
    return StreakSummary(
        currentDays = current,
        bestDays = best,
        lastCompletedDate = dates.maxOrNull(),
    )
}

/** Quantos dias consecutivos terminam em [anchor], contando para trás. */
private fun runLengthEndingAt(dates: Set<LocalDate>, anchor: LocalDate): Int {
    var length = 0
    var day = anchor
    while (day in dates) {
        length++
        day = day.minusDays(1)
    }
    return length
}

/**
 * Resumo do período: [ReportPeriod.MONTH] usa o mês de [reference] (o mês
 * exibido no calendário, `month.atDay(1)`); [ReportPeriod.YEAR] usa o ano de
 * [reference]. As métricas vêm de [buildHistoryMetrics] (H3-28) — mesma
 * agregação de séries/reps/volume/duração e divisão de volume entre músculos.
 */
fun buildPeriodReport(
    sessions: List<SessionWithExercises>,
    muscleByExerciseId: Map<String, String>,
    period: ReportPeriod,
    reference: LocalDate,
    formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
): PeriodReport {
    val from: LocalDate
    val to: LocalDate
    when (period) {
        ReportPeriod.MONTH -> {
            from = firstDayOfMonth(reference.year, reference.monthValue)
            to = lastDayOfMonth(reference.year, reference.monthValue)
        }
        ReportPeriod.YEAR -> {
            from = LocalDate.of(reference.year, 1, 1)
            to = LocalDate.of(reference.year, 12, 31)
        }
    }
    val metrics = buildHistoryMetrics(sessions, muscleByExerciseId, from, to)
    val activeDays = sessions
        .filter { it.session.status == STATUS_COMPLETED && sessionDay(it) in from..to }
        .mapTo(mutableSetOf()) { sessionDay(it) }
        .size
    val daysInPeriod = ChronoUnit.DAYS.between(from, to) + 1
    val sessionsPerWeek = if (daysInPeriod <= 0) {
        0.0
    } else {
        (metrics.completedCount / (daysInPeriod / 7.0) * 10.0).roundToInt() / 10.0
    }
    return PeriodReport(
        period = period,
        from = from,
        to = to,
        metrics = metrics,
        activeDays = activeDays,
        newPrCount = countNewRecords(sessions, from, to, formula),
        sessionsPerWeek = sessionsPerWeek,
    )
}

/**
 * H4-13 regra 8: número de exercícios cujo melhor `RecordSet` do período vence
 * o recorde de tudo **anterior a [from]**. A seleção do candidato e a
 * comparação são as da H4-1: [personalRecord] já escolhe a melhor série via
 * `recordSetsFor` com o desempate canônico (1RM estimado, carga, reps,
 * `sessionStartedAt`), e [isPersonalRecord] decide — primeiro registro válido
 * conta, empate não conta. Sessões em andamento entram nas duas pontas (regra
 * 11 da H4-1); corrida, natação e mobilidade não têm séries elegíveis.
 */
private fun countNewRecords(
    sessions: List<SessionWithExercises>,
    from: LocalDate,
    to: LocalDate,
    formula: OneRepMaxFormula,
): Int {
    val inPeriod = sessions.filter { sessionDay(it) in from..to }
    if (inPeriod.isEmpty()) return 0
    val before = sessions.filter { sessionDay(it).isBefore(from) }
    val exerciseIds = inPeriod
        .flatMap { it.exercises }
        .map { it.exercise.exerciseId }
        .filter { it.isNotBlank() }
        .distinct()
    return exerciseIds.count { exerciseId ->
        val candidate = personalRecord(inPeriod, exerciseId, formula) ?: return@count false
        val recordBefore = personalRecord(before, exerciseId, formula)
        isPersonalRecord(candidate.asRecordSet(), recordBefore, formula)
    }
}

/**
 * O `PersonalRecord` guarda exatamente o que [isPersonalRecord] consulta do
 * candidato — reps, carga válida e o 1RM estimado da melhor série —, então
 * reconstruir o `RecordSet` evita duplicar a comparação da H4-1.
 */
private fun PersonalRecord.asRecordSet(): RecordSet = RecordSet(
    sessionId = 0L,
    sessionStartedAt = achievedAtMillis ?: 0L,
    reps = reps,
    weightKg = weightKg,
)
