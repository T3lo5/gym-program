package com.treinos.app.ui.gamification

import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutSessionEntity
import com.treinos.app.ui.history.STATUS_COMPLETED
import com.treinos.app.ui.history.buildStreak
import com.treinos.app.ui.history.sessionDay
import com.treinos.app.ui.history.StreakSummary
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GamificationEngineTest {

    private val today = LocalDate.of(2026, 11, 15)

    @Test
    fun nivel_inicial_sem_xp() {
        val state = computeGamificationState(emptyList(), StreakSummary(0, 0, null), today)

        assertEquals(1, state.level)
        assertEquals(0, state.xp)
        assertEquals(0, state.badges.count { it.unlocked })
    }

    @Test
    fun xp_de_sessao_concluida() {
        val sessions = listOf(session(id = 1, startedAt = millis(today)))
        val streak = buildStreak(completedDates(sessions, today), today)

        val state = computeGamificationState(sessions, streak, today)

        assertEquals(1, state.level)
        assertEquals(50, state.xp)
        assertTrue(state.badges.any { it.id == "primeiro_treino" && it.unlocked })
    }

    @Test
    fun sessao_em_andamento_nao_gera_xp() {
        val sessions = listOf(session(id = 1, status = "in_progress", startedAt = millis(today)))
        val streak = buildStreak(completedDates(sessions, today), today)

        val state = computeGamificationState(sessions, streak, today)

        assertEquals(0, state.totalSessions)
        assertEquals(0, state.xp)
    }

    @Test
    fun subida_de_nivel_apos_limite() {
        // 2 sessões = 100 XP → nível 2 (requer 100).
        val sessions = listOf(session(id = 1, startedAt = millis(today)), session(id = 2, startedAt = millis(today.minusDays(1))))
        val streak = buildStreak(completedDates(sessions, today), today)

        val state = computeGamificationState(sessions, streak, today)

        assertEquals(2, state.level)
        assertEquals(100, state.xp)
    }

    @Test
    fun badge_primeiro_treino_concedido() {
        val sessions = listOf(session(id = 1, startedAt = millis(today)))
        val streak = buildStreak(completedDates(sessions, today), today)

        val state = computeGamificationState(sessions, streak, today)

        val badge = state.badges.find { it.id == "primeiro_treino" }
        assertTrue(badge?.unlocked == true)
    }

    @Test
    fun badge_semana_de_ouro_em_sete() {
        val sessions = (1..7).map { i -> session(id = i.toLong(), startedAt = millis(today.minusDays((7 - i).toLong()))) }
        val streak = buildStreak(completedDates(sessions, today), today)

        val state = computeGamificationState(sessions, streak, today)

        assertEquals(7, state.totalSessions)
        assertEquals(350, state.xp)
        assertTrue(state.badges.any { it.id == "semana_de_ouro" && it.unlocked })
    }

    @Test
    fun badge_marcador_cem_em_cem() {
        val sessions = (1..100).map { i -> session(id = i.toLong(), startedAt = millis(today.minusDays(i.toLong()))) }
        val streak = buildStreak(completedDates(sessions, today), today)

        val state = computeGamificationState(sessions, streak, today)

        assertEquals(100, state.totalSessions)
        assertTrue(state.badges.any { it.id == "marcador_cem" && it.unlocked })
    }

    @Test
    fun badge_estreia_de_streak_em_tres() {
        val dates = setOf(today.minusDays(2), today.minusDays(1), today)
        val sessions = dates.mapIndexed { i, d -> session(id = i.toLong() + 1, startedAt = millis(d)) }
        val streak = buildStreak(dates, today)

        val state = computeGamificationState(sessions, streak, today)

        assertEquals(3, streak.currentDays)
        assertTrue(state.badges.any { it.id == "estreia_de_streak" && it.unlocked })
    }

    @Test
    fun badge_semana_ininterrupta_em_sete() {
        val dates = (0..6).map { today.minusDays(it.toLong()) }.toSet()
        val sessions = dates.mapIndexed { i, d -> session(id = i.toLong() + 1, startedAt = millis(d)) }
        val streak = buildStreak(dates, today)

        val state = computeGamificationState(sessions, streak, today)

        assertEquals(7, streak.currentDays)
        assertTrue(state.badges.any { it.id == "semana_ininterrupta" && it.unlocked })
    }

    @Test
    fun badge_primeiro_recorde() {
        // Sessão com série concluída e carga → conta como PR.
        val sessions = listOf(session(id = 1, startedAt = millis(today), exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0)))))
        val streak = buildStreak(completedDates(sessions, today), today)

        val state = computeGamificationState(sessions, streak, today)

        assertTrue(state.totalPrs >= 1)
        assertTrue(state.badges.any { it.id == "primeiro_recorde" && it.unlocked })
    }

    @Test
    fun badge_colecionador_de_recorde_em_cinco() {
        val exercises = (1..5).map { i -> "ex$i" }
        val sessions = exercises.mapIndexed { i, id ->
            session(id = (i + 1).toLong(), startedAt = millis(today.minusDays((i + 1).toLong())), exercises = listOf(exercise(id, set(reps = 10, weight = 40.0))))
        }
        val streak = buildStreak(completedDates(sessions, today), today)

        val state = computeGamificationState(sessions, streak, today)

        assertEquals(5, state.totalPrs)
        assertTrue(state.badges.any { it.id == "colecionador_de_recordes" && it.unlocked })
    }

    @Test
    fun badge_maratonista_modalidade() {
        val sessions = listOf(
            session(id = 1, modality = "strength", startedAt = millis(today.minusDays(2))),
            session(id = 2, modality = "run", startedAt = millis(today.minusDays(1))),
            session(id = 3, modality = "mobility", startedAt = millis(today)),
        )
        val streak = buildStreak(completedDates(sessions, today), today)

        val state = computeGamificationState(sessions, streak, today)

        assertTrue(state.badges.any { it.id == "maratonista_modalidade" && it.unlocked })
    }

    @Test
    fun badge_so_uma_vez() {
        // Mesmo com muitas sessões, cada badge aparece uma só vez no catálogo.
        val sessions = (1..10).map { i -> session(id = i.toLong(), startedAt = millis(today.minusDays(i.toLong()))) }
        val streak = buildStreak(completedDates(sessions, today), today)

        val state = computeGamificationState(sessions, streak, today)

        assertEquals(1, state.badges.count { it.id == "primeiro_treino" })
    }

    @Test
    fun sem_dados_nao_quebra() {
        val state = computeGamificationState(emptyList(), StreakSummary(0, 0, null), today)

        assertEquals(1, state.level)
        assertEquals(0, state.xp)
        assertEquals(8, state.badges.size)
    }

    @Test
    fun xp_para_proximo_nivel() {
        // 1 sessão = 50 XP, nível 1 requer 0, próximo (nível 2) requer 100.
        val sessions = listOf(session(id = 1, startedAt = millis(today)))
        val streak = buildStreak(completedDates(sessions, today), today)

        val state = computeGamificationState(sessions, streak, today)

        assertEquals(50, state.xpToNext)
    }

    @Test
    fun badges_conquistados_antes_dos_bloqueados() {
        val sessions = listOf(session(id = 1, startedAt = millis(today)))
        val streak = buildStreak(completedDates(sessions, today), today)

        val state = computeGamificationState(sessions, streak, today)

        val firstLocked = state.badges.indexOfFirst { !it.unlocked }
        assertTrue(state.badges.take(firstLocked).all { it.unlocked })
    }

    // --- helpers ---

    private fun completedDates(sessions: List<SessionWithExercises>, today: LocalDate): Set<LocalDate> =
        sessions
            .filter { it.session.status == STATUS_COMPLETED }
            .mapNotNull { sessionDay(it) }
            .filter { !it.isAfter(today) }
            .toSet()

    private fun session(
        id: Long,
        status: String = STATUS_COMPLETED,
        startedAt: Long = millis(today),
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
}
