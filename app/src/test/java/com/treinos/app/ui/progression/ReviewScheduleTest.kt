package com.treinos.app.ui.progression

import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutSessionEntity
import com.treinos.app.ui.reviewIsDue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

class ReviewScheduleTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 5)

    @Test
    fun revisao_atrasada_aparece() {
        val current = LocalDate.now()
        assertTrue(reviewIsDue(current.minusDays(1).toString()))
        assertFalse(reviewIsDue(current.toString()))
        assertFalse(reviewIsDue(current.plusWeeks(2).toString()))
        assertFalse(reviewIsDue(null))
    }

    @Test
    fun adiar_duas_semanas_de_data_futura() {
        val next = postponeReviewDate(today.plusWeeks(2).toString(), today)
        assertEquals(today.plusWeeks(4), next)
    }

    @Test
    fun adiar_de_data_passada_conta_de_hoje() {
        val next = postponeReviewDate(today.minusWeeks(5).toString(), today)
        assertEquals(today.plusWeeks(2), next)
    }

    @Test
    fun adiar_sem_data_conta_de_hoje() {
        assertEquals(today.plusWeeks(2), postponeReviewDate(null, today))
        assertEquals(today.plusWeeks(2), postponeReviewDate("", today))
        assertEquals(today.plusWeeks(2), postponeReviewDate("ontem", today))
    }

    @Test
    fun nunca_diminui_data_existente() {
        val future = today.plusWeeks(3)
        assertEquals(future, postponeReviewDate(future.toString(), today, weeks = 0))
        assertTrue(postponeReviewDate(future.toString(), today, weeks = 1).isAfter(future))
        assertTrue(postponeReviewDate(today.minusDays(1).toString(), today).isAfter(today.minusDays(1)))
    }

    @Test
    fun ciclo_comeca_na_semana_1() {
        // toMillis usa UTC; o zoneId precisa casar, senão o fuso do ambiente
        // (ex.: America/Sao_Paulo) desloca a data para o dia anterior.
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        assertEquals(1, cycleWeek(toMillis(monday), toMillis(monday.plusDays(4)), ZoneOffset.UTC))
        assertEquals(2, cycleWeek(toMillis(monday), toMillis(monday.plusDays(7)), ZoneOffset.UTC))
        assertEquals(1, cycleWeek(toMillis(monday.plusDays(30)), toMillis(monday), ZoneOffset.UTC))
    }

    @Test
    fun deload_na_semana_6_com_8_sessoes() {
        val firstMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val createdAt = toMillis(firstMonday)
        val sessions = List(8) { index -> completedSession(id = index + 1L, startedAt = createdAt + index) }
        assertEquals(6, cycleWeek(createdAt, toMillis(firstMonday.plusWeeks(5)), ZoneOffset.UTC))
        assertTrue(
            deloadSuggested(
                planCreatedAtMillis = createdAt,
                nowMillis = toMillis(firstMonday.plusWeeks(5)),
                completedSessionsSincePlan = completedSessionsSincePlan(sessions, createdAt),
                zoneId = ZoneOffset.UTC,
            ),
        )
    }

    @Test
    fun sem_deload_antes_da_semana_6() {
        val firstMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val createdAt = toMillis(firstMonday)
        val sessions = List(8) { index -> completedSession(id = index + 1L, startedAt = createdAt + index) }
        assertFalse(
            deloadSuggested(
                planCreatedAtMillis = createdAt,
                nowMillis = toMillis(firstMonday.plusWeeks(5).minusDays(1)),
                completedSessionsSincePlan = completedSessionsSincePlan(sessions, createdAt),
                zoneId = ZoneOffset.UTC,
            ),
        )
        assertFalse(
            deloadSuggested(
                planCreatedAtMillis = createdAt,
                nowMillis = toMillis(firstMonday.plusWeeks(5)),
                completedSessionsSincePlan = completedSessionsSincePlan(sessions.take(7), createdAt),
                zoneId = ZoneOffset.UTC,
            ),
        )
        assertEquals(7, completedSessionsSincePlan(sessions.take(7) + inProgressSession(99L, createdAt), createdAt))
    }

    private fun toMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun completedSession(id: Long, startedAt: Long): SessionWithExercises =
        session(id = id, status = "completed", startedAt = startedAt)

    private fun inProgressSession(id: Long, startedAt: Long): SessionWithExercises =
        session(id = id, status = "in_progress", startedAt = startedAt)

    private fun session(id: Long, status: String, startedAt: Long): SessionWithExercises =
        SessionWithExercises(
            session = WorkoutSessionEntity(
                id = id,
                planId = 1,
                dayId = 1,
                planName = "Plano",
                dayName = "Dia",
                status = status,
                startedAt = startedAt,
            ),
            exercises = listOf(
                SessionExerciseWithSets(
                    exercise = SessionExerciseEntity(
                        id = id,
                        sessionId = id,
                        exerciseId = "barbell_bench_press",
                        exerciseName = "Supino reto",
                    ),
                    sets = listOf(
                        SetLogEntity(
                            id = id,
                            exerciseLogId = id,
                            setNumber = 1,
                            reps = 12,
                            weightKg = 40.0,
                            rpe = 8.0,
                            completedAt = startedAt,
                        ),
                    ),
                ),
            ),
        )
}
