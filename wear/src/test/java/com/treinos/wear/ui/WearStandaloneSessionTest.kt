package com.treinos.wear.ui

import com.treinos.protocol.WearSessionExercise
import com.treinos.protocol.WearSessionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** H4-16: reducer puro da sessão offline do relógio. */
class WearStandaloneSessionTest {

    private fun snapshot(
        vararg exercicios: Triple<Long, Int, String>,
        sessionId: Long = 42,
        modality: String = "strength",
    ): WearSessionSnapshot =
        WearSessionSnapshot(
            sessionId = sessionId,
            dayName = "Treino A",
            modality = modality,
            startedAt = 1_700_000_000_000,
            exercises = exercicios.mapIndexed { index, (id, planned, nome) ->
                WearSessionExercise(
                    exerciseIndex = index,
                    sessionExerciseId = id,
                    exerciseName = nome,
                    plannedSets = planned,
                    targetReps = "8-10",
                    restSeconds = 90,
                )
            },
        )

    @Test
    fun inicia_no_primeiro_exercicio() {
        val estado = WearStandaloneSession.reduce(
            WearStandaloneState(),
            WearStandaloneAction.Start(
                snapshot = snapshot(Triple(42L, 3, "Supino")),
                loggedSets = 0,
                pendingCount = 0,
            ),
        )
        assertTrue(estado.hasSnapshot)
        assertEquals(0, estado.exerciseIndex)
        assertEquals(1, estado.setNumber)
        assertEquals(3, estado.plannedSets)
        assertEquals("Supino", estado.exercise?.exerciseName)
        assertEquals(1, estado.exerciseCount)
    }

    @Test
    fun registra_serie_e_avanca() {
        val inicio = WearStandaloneSession.reduce(
            WearStandaloneState(),
            WearStandaloneAction.Start(snapshot(Triple(42L, 3, "Supino")), 0, 0),
        )
        val now = 1_700_000_000_000
        val depois = WearStandaloneSession.reduce(
            inicio,
            WearStandaloneAction.LogSet(reps = 10, weightKg = 60.0, now = now),
        )
        // Ainda há séries: avança a série e abre o descanso do exercício.
        assertEquals(2, depois.setNumber)
        assertEquals(now + 90_000L, depois.restEndsAt)
        assertEquals(1, depois.pendingCount)
        assertEquals("in_progress", depois.status)
    }

    @Test
    fun ultima_serie_avanca_exercicio() {
        val inicio = WearStandaloneSession.reduce(
            WearStandaloneState(),
            WearStandaloneAction.Start(snapshot(Triple(42L, 2, "Supino"), Triple(42L, 3, "Remada")), 1, 1),
        )
        assertEquals(2, inicio.setNumber)
        val now = 1_700_000_000_000
        val depois = WearStandaloneSession.reduce(
            inicio,
            WearStandaloneAction.LogSet(reps = 8, weightKg = 60.0, now = now),
        )
        // Última série do exercício: avança para o próximo, série 1, sem descanso.
        assertEquals(1, depois.exerciseIndex)
        assertEquals(1, depois.setNumber)
        assertNull(depois.restEndsAt)
        assertEquals(2, depois.pendingCount)
    }

    @Test
    fun finaliza_ultimo_exercicio() {
        val inicio = WearStandaloneSession.reduce(
            WearStandaloneState(),
            WearStandaloneAction.Start(snapshot(Triple(42L, 1, "Supino")), 0, 0),
        )
        val depois = WearStandaloneSession.reduce(
            inicio,
            WearStandaloneAction.LogSet(reps = 8, weightKg = 60.0, now = 1_700_000_000_000),
        )
        assertEquals("finished", depois.status)
    }

    @Test
    fun rest_ends_at_calculado() {
        val inicio = WearStandaloneSession.reduce(
            WearStandaloneState(),
            WearStandaloneAction.Start(snapshot(Triple(42L, 3, "Supino")), 0, 0),
        )
        val now = 1_700_000_000_000
        val depois = WearStandaloneSession.reduce(
            inicio,
            WearStandaloneAction.LogSet(reps = 10, weightKg = 60.0, now = now),
        )
        // restSeconds = 90 s do snapshot.
        assertEquals(now + 90_000L, depois.restEndsAt)
        assertEquals(90, WearStandaloneSession.restante(depois.restEndsAt, now))
        assertEquals(89, WearStandaloneSession.restante(depois.restEndsAt, now + 1_000L))
        assertEquals(0, WearStandaloneSession.restante(depois.restEndsAt, now + 91_000L))
    }

    @Test
    fun rest_no_passado_zera() {
        // restEndsAt no passado (relógio reiniciado depois do prazo) → 0, nunca negativo.
        assertEquals(0, WearStandaloneSession.restante(1_000L, 2_000L))
        assertEquals(0, WearStandaloneSession.restante(null, 2_000L))
    }

    @Test
    fun sem_snapshot_nao_quebra() {
        val estado = WearStandaloneSession.reduce(
            WearStandaloneState(),
            WearStandaloneAction.Start(snapshot = null, loggedSets = 0, pendingCount = 3),
        )
        assertFalse(estado.hasSnapshot)
        assertEquals(3, estado.pendingCount)
        // LogSet sem snapshot é no-op.
        val depois = WearStandaloneSession.reduce(
            estado,
            WearStandaloneAction.LogSet(reps = 10, weightKg = 60.0, now = 1_700_000_000_000),
        )
        assertFalse(depois.hasSnapshot)
    }

    @Test
    fun snapshot_novo_mantem_pendentes() {
        // O Start com loggedSets > 0 restaura a série atual do exercício — o
        // snapshot novo NÃO zera o que foi registrado offline (a persistência
        // das pendentes é o Room; o reducer só deriva a posição).
        val estado = WearStandaloneSession.reduce(
            WearStandaloneState(),
            WearStandaloneAction.Start(snapshot(Triple(42L, 3, "Supino")), loggedSets = 2, pendingCount = 2),
        )
        assertEquals(3, estado.setNumber)
        assertEquals(2, estado.pendingCount)
    }

    @Test
    fun corrida_sem_exercicios_finaliza() {
        // Snapshot de corrida/mobilidade: 0 exercícios — não avança; o
        // "Finalizar" encerra offline sem séries.
        val estado = WearStandaloneSession.reduce(
            WearStandaloneState(),
            WearStandaloneAction.Start(
                snapshot = WearSessionSnapshot(
                    sessionId = 42,
                    dayName = "Caminhada",
                    modality = "run",
                    startedAt = 1_700_000_000_000,
                    exercises = emptyList(),
                ),
                loggedSets = 0,
                pendingCount = 0,
            ),
        )
        assertTrue(estado.hasSnapshot)
        assertEquals(0, estado.exerciseCount)
        val depois = WearStandaloneSession.reduce(
            estado,
            WearStandaloneAction.Finish(now = 1_700_000_000_000),
        )
        assertEquals("finished", depois.status)
    }
}
