package com.treinos.app.ui.history

import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutSessionEntity
import com.treinos.app.ui.progression.OneRepMaxFormula
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H4-13: regras puras de streak e resumo mensal/anual
 * (docs/specs/H4-13-streak-resumo-periodico.md). Cada teste cobre uma regra
 * da spec; sem Robolectric, sem Room, sem Android. O PR reusa o motor da H4-1
 * (`personalRecord`/`isPersonalRecord`) — aqui se testa o consumo dele.
 */
class PeriodReportTest {

    private val today = LocalDate.of(2026, 11, 15)

    @Test
    fun streak_tres_dias_consecutivos() {
        // Critério 1: três treinos em dias consecutivos terminando hoje.
        val dates = setOf(today.minusDays(2), today.minusDays(1), today)

        val streak = buildStreak(dates, today)

        assertEquals(3, streak.currentDays)
        assertEquals(3, streak.bestDays)
        assertEquals(today, streak.lastCompletedDate)
    }

    @Test
    fun streak_sem_treino_hoje_mantem_ate_ontem() {
        // Regra 3: sem treino hoje, a sequência atual conta para trás a partir
        // de ontem — o dia de descanso de hoje não zera o que veio antes.
        val dates = setOf(today.minusDays(3), today.minusDays(2), today.minusDays(1))

        val streak = buildStreak(dates, today)

        assertEquals(3, streak.currentDays)
        assertEquals(3, streak.bestDays)
        assertEquals(today.minusDays(1), streak.lastCompletedDate)
    }

    @Test
    fun streak_quebrada_zera_atual_mantem_melhor() {
        // Critério 2: cinco dias consecutivos na semana passada e nenhum nos
        // últimos dois dias → atual 0, melhor 5.
        val dates = (5 downTo 1).map { today.minusDays(it.toLong() + 2) }.toSet()

        val streak = buildStreak(dates, today)

        assertEquals(0, streak.currentDays)
        assertEquals(5, streak.bestDays)
        assertEquals(today.minusDays(3), streak.lastCompletedDate)
    }

    @Test
    fun streak_datas_duplicadas_contam_uma_vez() {
        // Regra 3: duas sessões no mesmo dia viram uma data só — via
        // `sessionDates`, o mesmo caminho que a UI usa.
        val sessions = listOf(
            session(id = 1, startedAt = millis(today, hour = 8)),
            session(id = 2, startedAt = millis(today, hour = 19)),
            session(id = 3, startedAt = millis(today.minusDays(1))),
        )
        val dates = sessionDates(sessions.filter { it.session.status == STATUS_COMPLETED })

        val streak = buildStreak(dates, today)

        assertEquals(setOf(today.minusDays(1), today), dates)
        assertEquals(2, streak.currentDays)
        assertEquals(2, streak.bestDays)
    }

    @Test
    fun streak_conjunto_vazio() {
        val streak = buildStreak(emptySet(), today)

        assertEquals(0, streak.currentDays)
        assertEquals(0, streak.bestDays)
        assertNull(streak.lastCompletedDate)
    }

    @Test
    fun streak_ignora_datas_futuras() {
        // Regra 3: datas futuras são descartadas antes de qualquer cálculo —
        // não entram na sequência, no recorde nem na última data.
        val dates = setOf(
            today.minusDays(1),
            today,
            today.plusDays(1),
            today.plusDays(2),
        )

        val streak = buildStreak(dates, today)

        assertEquals(2, streak.currentDays)
        assertEquals(2, streak.bestDays)
        assertEquals(today, streak.lastCompletedDate)
    }

    @Test
    fun relatorio_mes_limites() {
        // Regra 5: período inclusivo do primeiro ao último dia do mês de
        // `reference`; dia vizinho antes e depois ficam fora.
        val sessions = listOf(
            session(id = 1, startedAt = millis(LocalDate.of(2026, 1, 31))),
            session(id = 2, startedAt = millis(LocalDate.of(2026, 2, 1))),
            session(id = 3, startedAt = millis(LocalDate.of(2026, 2, 28))),
            session(id = 4, startedAt = millis(LocalDate.of(2026, 3, 1))),
        )

        val report = buildPeriodReport(
            sessions = sessions,
            muscleByExerciseId = emptyMap(),
            period = ReportPeriod.MONTH,
            reference = LocalDate.of(2026, 2, 15),
        )

        assertEquals(LocalDate.of(2026, 2, 1), report.from)
        assertEquals(LocalDate.of(2026, 2, 28), report.to)
        assertEquals(ReportPeriod.MONTH, report.period)
        assertEquals(2, report.metrics.completedCount)
        assertEquals(2, report.activeDays)
    }

    @Test
    fun relatorio_ano_limites() {
        // Critério 4: o ano agrega de 1º de janeiro a 31 de dezembro do ano do
        // mês exibido.
        val sessions = listOf(
            session(id = 1, startedAt = millis(LocalDate.of(2025, 12, 31))),
            session(id = 2, startedAt = millis(LocalDate.of(2026, 1, 1))),
            session(id = 3, startedAt = millis(LocalDate.of(2026, 12, 31))),
        )

        val report = buildPeriodReport(
            sessions = sessions,
            muscleByExerciseId = emptyMap(),
            period = ReportPeriod.YEAR,
            reference = LocalDate.of(2026, 7, 10),
        )

        assertEquals(LocalDate.of(2026, 1, 1), report.from)
        assertEquals(LocalDate.of(2026, 12, 31), report.to)
        assertEquals(ReportPeriod.YEAR, report.period)
        assertEquals(2, report.metrics.completedCount)
        assertEquals(2, report.activeDays)
    }

    @Test
    fun relatorio_conta_so_concluidas() {
        // Regra 1: em andamento não entra em treinos, volume nem dias ativos —
        // mas `sessionCount` segue contando o total do período (H3-28).
        val sessions = listOf(
            session(
                id = 1,
                startedAt = millis(LocalDate.of(2026, 3, 10)),
                exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0))),
            ),
            session(
                id = 2,
                status = STATUS_IN_PROGRESS,
                startedAt = millis(LocalDate.of(2026, 3, 12)),
                exercises = listOf(exercise("ex1", set(reps = 10, weight = 100.0))),
            ),
        )

        val report = buildPeriodReport(
            sessions = sessions,
            muscleByExerciseId = emptyMap(),
            period = ReportPeriod.MONTH,
            reference = LocalDate.of(2026, 3, 15),
        )

        assertEquals(2, report.metrics.sessionCount)
        assertEquals(1, report.metrics.completedCount)
        assertEquals(400.0, report.metrics.totalVolumeKg, EPSILON)
        assertEquals(1, report.activeDays)
    }

    @Test
    fun relatorio_dias_ativos() {
        // Regra 7: datas distintas com sessão concluída — duas sessões no
        // mesmo dia contam um dia ativo.
        val sessions = listOf(
            session(id = 1, startedAt = millis(LocalDate.of(2026, 3, 5), hour = 8)),
            session(id = 2, startedAt = millis(LocalDate.of(2026, 3, 5), hour = 19)),
            session(id = 3, startedAt = millis(LocalDate.of(2026, 3, 20))),
        )

        val report = buildPeriodReport(
            sessions = sessions,
            muscleByExerciseId = emptyMap(),
            period = ReportPeriod.MONTH,
            reference = LocalDate.of(2026, 3, 15),
        )

        assertEquals(3, report.metrics.completedCount)
        assertEquals(2, report.activeDays)
    }

    @Test
    fun relatorio_media_por_semana() {
        // Regra 9: concluídos / (dias do período / 7), arredondado a 1 casa.
        // Fevereiro de 2026 tem 28 dias: 8 treinos = 2,0 por semana.
        val fevereiro = (1..8).map { session(id = it.toLong(), startedAt = millis(LocalDate.of(2026, 2, it))) }
        val reportFevereiro = buildPeriodReport(
            sessions = fevereiro,
            muscleByExerciseId = emptyMap(),
            period = ReportPeriod.MONTH,
            reference = LocalDate.of(2026, 2, 15),
        )
        assertEquals(2.0, reportFevereiro.sessionsPerWeek, EPSILON)

        // Janeiro tem 31 dias: 10 treinos = 70/31 = 2,258 → 2,3.
        val janeiro = (1..10).map { session(id = it.toLong(), startedAt = millis(LocalDate.of(2026, 1, it))) }
        val reportJaneiro = buildPeriodReport(
            sessions = janeiro,
            muscleByExerciseId = emptyMap(),
            period = ReportPeriod.MONTH,
            reference = LocalDate.of(2026, 1, 15),
        )
        assertEquals(2.3, reportJaneiro.sessionsPerWeek, EPSILON)
    }

    @Test
    fun relatorio_vazio_nao_quebra() {
        // Regra 10: lista vazia → relatório zerado, sem exceção, com os
        // limites do período corretos.
        val mes = buildPeriodReport(
            sessions = emptyList(),
            muscleByExerciseId = emptyMap(),
            period = ReportPeriod.MONTH,
            reference = today,
        )
        assertEquals(LocalDate.of(2026, 11, 1), mes.from)
        assertEquals(LocalDate.of(2026, 11, 30), mes.to)
        assertEquals(0, mes.metrics.completedCount)
        assertEquals(0.0, mes.metrics.totalVolumeKg, EPSILON)
        assertTrue(mes.metrics.volumeByMuscle.isEmpty())
        assertEquals(0, mes.activeDays)
        assertEquals(0, mes.newPrCount)
        assertEquals(0.0, mes.sessionsPerWeek, EPSILON)

        val ano = buildPeriodReport(
            sessions = emptyList(),
            muscleByExerciseId = emptyMap(),
            period = ReportPeriod.YEAR,
            reference = today,
        )
        assertEquals(LocalDate.of(2026, 1, 1), ano.from)
        assertEquals(LocalDate.of(2026, 12, 31), ano.to)
        assertEquals(0, ano.metrics.completedCount)
        assertEquals(0, ano.newPrCount)
    }

    @Test
    fun pr_primeiro_registro_conta() {
        // Critério 5 + regra 8 da H4-1: exercício sem histórico antes do mês e
        // uma série válida no mês → o primeiro registro válido é PR.
        val sessions = listOf(
            session(
                id = 1,
                startedAt = millis(LocalDate.of(2026, 3, 10)),
                exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0))),
            ),
        )

        val report = buildPeriodReport(
            sessions = sessions,
            muscleByExerciseId = emptyMap(),
            period = ReportPeriod.MONTH,
            reference = LocalDate.of(2026, 3, 15),
        )

        assertEquals(1, report.newPrCount)
    }

    @Test
    fun pr_empate_nao_conta() {
        // Regra 4 da H4-1: repetir a mesma carga × reps no mês não conta outro
        // PR (1RM estimado empatado não é recorde novo).
        val sessions = listOf(
            session(
                id = 1,
                startedAt = millis(LocalDate.of(2026, 2, 10)),
                exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0))),
            ),
            session(
                id = 2,
                startedAt = millis(LocalDate.of(2026, 3, 10)),
                exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0))),
            ),
        )

        val report = buildPeriodReport(
            sessions = sessions,
            muscleByExerciseId = emptyMap(),
            period = ReportPeriod.MONTH,
            reference = LocalDate.of(2026, 3, 15),
        )

        assertEquals(0, report.newPrCount)
    }

    @Test
    fun pr_melhora_1rm_conta() {
        // Antes: 40 × 10 (1RM Epley 53,33). No mês: 45 × 8 (57,0) — vence pelo
        // 1RM estimado, não pela carga isolada.
        val sessions = listOf(
            session(
                id = 1,
                startedAt = millis(LocalDate.of(2026, 2, 10)),
                exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0))),
            ),
            session(
                id = 2,
                startedAt = millis(LocalDate.of(2026, 3, 10)),
                exercises = listOf(exercise("ex1", set(reps = 8, weight = 45.0))),
            ),
        )

        val report = buildPeriodReport(
            sessions = sessions,
            muscleByExerciseId = emptyMap(),
            period = ReportPeriod.MONTH,
            reference = LocalDate.of(2026, 3, 15),
        )

        assertEquals(1, report.newPrCount)
    }

    @Test
    fun pr_considera_sessao_em_andamento() {
        // Regra 1, exceção: a série registrada em sessão in_progress é dado
        // real (regra 11 da H4-1) e conta para o PR — mas a sessão não entra
        // nas métricas nem nos dias ativos.
        val sessions = listOf(
            session(
                id = 1,
                startedAt = millis(LocalDate.of(2026, 2, 10)),
                exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0))),
            ),
            session(
                id = 2,
                status = STATUS_IN_PROGRESS,
                startedAt = millis(LocalDate.of(2026, 3, 10)),
                exercises = listOf(exercise("ex1", set(reps = 10, weight = 50.0))),
            ),
        )

        val report = buildPeriodReport(
            sessions = sessions,
            muscleByExerciseId = emptyMap(),
            period = ReportPeriod.MONTH,
            reference = LocalDate.of(2026, 3, 15),
        )

        assertEquals(1, report.newPrCount)
        assertEquals(0, report.metrics.completedCount)
        assertEquals(0, report.activeDays)
        assertEquals(0.0, report.metrics.totalVolumeKg, EPSILON)
    }

    @Test
    fun pr_formula_brzycki_muda_resultado() {
        // Regra 13: Brzycki fica disponível pelo parâmetro `formula` e muda o
        // resultado. Antes: 60 × 15 (Epley 90,0 / Brzycki 98,18). No mês:
        // 80 × 5 (Epley 93,33 → PR; Brzycki 90,0 → não é PR).
        val sessions = listOf(
            session(
                id = 1,
                startedAt = millis(LocalDate.of(2026, 2, 10)),
                exercises = listOf(exercise("ex1", set(reps = 15, weight = 60.0))),
            ),
            session(
                id = 2,
                startedAt = millis(LocalDate.of(2026, 3, 10)),
                exercises = listOf(exercise("ex1", set(reps = 5, weight = 80.0))),
            ),
        )

        val epley = buildPeriodReport(
            sessions = sessions,
            muscleByExerciseId = emptyMap(),
            period = ReportPeriod.MONTH,
            reference = LocalDate.of(2026, 3, 15),
            formula = OneRepMaxFormula.EPLEY,
        )
        val brzycki = buildPeriodReport(
            sessions = sessions,
            muscleByExerciseId = emptyMap(),
            period = ReportPeriod.MONTH,
            reference = LocalDate.of(2026, 3, 15),
            formula = OneRepMaxFormula.BRZYCKI,
        )

        assertEquals(1, epley.newPrCount)
        assertEquals(0, brzycki.newPrCount)
    }

    @Test
    fun relatorio_volume_por_musculo_reaproveitado() {
        // Regra 6: a divisão do volume entre os músculos do exercício vem de
        // `buildHistoryMetrics`/`muscleKeysFor` — nada reimplementado aqui.
        val sessions = listOf(
            session(
                id = 1,
                startedAt = millis(LocalDate.of(2026, 3, 10)),
                exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0))),
            ),
        )

        val report = buildPeriodReport(
            sessions = sessions,
            muscleByExerciseId = mapOf("ex1" to "chest,triceps"),
            period = ReportPeriod.MONTH,
            reference = LocalDate.of(2026, 3, 15),
        )

        assertEquals(400.0, report.metrics.totalVolumeKg, EPSILON)
        assertEquals(200.0, report.metrics.volumeByMuscle["Peito"]!!, EPSILON)
        assertEquals(200.0, report.metrics.volumeByMuscle["Tríceps"]!!, EPSILON)
    }

    @Test
    fun relatorio_virada_de_ano_local() {
        // Regra 2: datas no fuso do sistema — 23:30 de 31/12 continua sendo
        // 31/12, então o treino pertence ao ano que está acabando.
        val vespera = session(
            id = 1,
            startedAt = millis(LocalDate.of(2025, 12, 31), hour = 23, minute = 30),
            exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0))),
        )

        val ano2025 = buildPeriodReport(
            sessions = listOf(vespera),
            muscleByExerciseId = emptyMap(),
            period = ReportPeriod.YEAR,
            reference = LocalDate.of(2025, 12, 15),
        )
        assertEquals(LocalDate.of(2025, 1, 1), ano2025.from)
        assertEquals(LocalDate.of(2025, 12, 31), ano2025.to)
        assertEquals(1, ano2025.metrics.completedCount)

        // O mesmo treino não entra no ano seguinte nem no mês de janeiro.
        val ano2026 = buildPeriodReport(
            sessions = listOf(vespera),
            muscleByExerciseId = emptyMap(),
            period = ReportPeriod.YEAR,
            reference = LocalDate.of(2026, 6, 15),
        )
        assertEquals(0, ano2026.metrics.completedCount)
        val janeiro2026 = buildPeriodReport(
            sessions = listOf(vespera),
            muscleByExerciseId = emptyMap(),
            period = ReportPeriod.MONTH,
            reference = LocalDate.of(2026, 1, 15),
        )
        assertEquals(0, janeiro2026.metrics.completedCount)
    }

    private fun session(
        id: Long,
        status: String = STATUS_COMPLETED,
        startedAt: Long = millis(LocalDate.of(2026, 3, 15)),
        modality: String = "strength",
        exercises: List<SessionExerciseWithSets> = emptyList(),
    ) = SessionWithExercises(
        session = WorkoutSessionEntity(
            id = id,
            status = status,
            startedAt = startedAt,
            totalDurationSeconds = 60L,
            modality = modality,
        ),
        exercises = exercises,
    )

    private fun exercise(
        exerciseId: String,
        vararg sets: SetLogEntity,
    ) = SessionExerciseWithSets(
        exercise = SessionExerciseEntity(
            id = 1,
            sessionId = 1,
            exerciseId = exerciseId,
            exerciseName = exerciseId,
        ),
        sets = sets.mapIndexed { index, set -> set.copy(setNumber = index + 1) },
    )

    private fun set(
        reps: Int?,
        weight: Double?,
        completed: Boolean = true,
        isWarmup: Boolean = false,
    ) = SetLogEntity(
        id = 0L,
        exerciseLogId = 1L,
        setNumber = 1,
        reps = reps,
        weightKg = weight,
        isCompleted = completed,
        isWarmup = isWarmup,
    )

    private fun millis(date: LocalDate, hour: Int = 10, minute: Int = 0): Long =
        date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private companion object {
        const val EPSILON = 0.0001
    }
}
