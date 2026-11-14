package com.treinos.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes puros da restauração de mobilidade (H3-55).
 * Cobre: retomar no mesmo movimento, avançar vencidos, terminar quando tudo passou,
 * descartar quando rotina não existe mais.
 */
class MobilityResumeTest {

    private val routine = MobilityRoutineTemplates.getRoutine("back_10")!!

    @Test
    fun retoma_no_mesmo_movimento_com_restante() {
        // 3º movimento (index 2), 20 s restantes, deadline no futuro
        val now = System.currentTimeMillis()
        val movement = routine.movements[2] // Rotação torácica lado direito, 50s
        val deadline = now + 20_000L // 20 s no futuro
        val startedAt = now - 150_000L // começou há 150 s

        val saved = MobilitySessionEngine.SavedMobilityState(
            routineId = routine.id,
            movementIndex = 2,
            movementDeadlineAtMillis = deadline,
            startedAtMillis = startedAt,
        )

        val restored = MobilitySessionEngine.resume(routine, saved, now)

        assertNotNull(restored)
        assertEquals(routine.id, restored!!.routineId)
        assertEquals(2, restored.movementIndex)
        assertEquals(20, restored.secondsRemaining)
        assertTrue(restored.running)
        assertFalse(restored.paused)
        assertEquals(0, restored.transitionSecondsRemaining)
        // elapsed ≈ 150 s
        assertTrue(restored.elapsedSeconds >= 149 && restored.elapsedSeconds <= 151)
    }

    @Test
    fun movimentos_vencidos_avancam() {
        // 2º movimento (index 1) já venceu há 10 s, deve avançar pro 3º com duração cheia
        val now = System.currentTimeMillis()
        val deadline = now - 10_000L // 10 s no passado
        val startedAt = now - 100_000L

        val saved = MobilitySessionEngine.SavedMobilityState(
            routineId = routine.id,
            movementIndex = 1,
            movementDeadlineAtMillis = deadline,
            startedAtMillis = startedAt,
        )

        val restored = MobilitySessionEngine.resume(routine, saved, now)

        assertNotNull(restored)
        // Deve ter avançado para o movimento 2 (index 2) com duração cheia (50s)
        assertEquals(2, restored!!.movementIndex)
        assertEquals(50, restored.secondsRemaining)
        assertTrue(restored.running)
    }

    @Test
    fun tudo_vencido_termina() {
        // Último movimento (index 14) já venceu, sessão deve terminar
        val now = System.currentTimeMillis()
        val lastIndex = routine.movements.size - 1
        val deadline = now - 5_000L // 5 s no passado
        val startedAt = now - 500_000L

        val saved = MobilitySessionEngine.SavedMobilityState(
            routineId = routine.id,
            movementIndex = lastIndex,
            movementDeadlineAtMillis = deadline,
            startedAtMillis = startedAt,
        )

        val restored = MobilitySessionEngine.resume(routine, saved, now)

        assertNotNull(restored)
        assertEquals(lastIndex, restored!!.movementIndex)
        assertEquals(0, restored.secondsRemaining)
        assertFalse(restored.running)
        // elapsed ≈ tempo total decorrido
        assertTrue(restored.elapsedSeconds > 0)
    }

    @Test
    fun rotina_ausente_descarta() {
        // Rotina com ID diferente não deve restaurar
        val now = System.currentTimeMillis()
        val deadline = now + 20_000L
        val startedAt = now - 150_000L

        val saved = MobilitySessionEngine.SavedMobilityState(
            routineId = "routine_inexistente",
            movementIndex = 2,
            movementDeadlineAtMillis = deadline,
            startedAtMillis = startedAt,
        )

        val restored = MobilitySessionEngine.resume(routine, saved, now)

        assertNull(restored)
    }

    @Test
    fun indice_invalido_descarta() {
        // movementIndex fora dos limites
        val now = System.currentTimeMillis()
        val deadline = now + 20_000L
        val startedAt = now - 150_000L

        val saved = MobilitySessionEngine.SavedMobilityState(
            routineId = routine.id,
            movementIndex = 999, // índice inválido
            movementDeadlineAtMillis = deadline,
            startedAtMillis = startedAt,
        )

        val restored = MobilitySessionEngine.resume(routine, saved, now)

        assertNull(restored)
    }

    @Test
    fun deadline_zero_descarta() {
        // deadline <= 0 não restaura
        val now = System.currentTimeMillis()
        val startedAt = now - 150_000L

        val saved = MobilitySessionEngine.SavedMobilityState(
            routineId = routine.id,
            movementIndex = 2,
            movementDeadlineAtMillis = 0L,
            startedAtMillis = startedAt,
        )

        val restored = MobilitySessionEngine.resume(routine, saved, now)

        assertNull(restored)
    }

    @Test
    fun startedAt_zero_descarta() {
        // startedAt <= 0 não restaura
        val now = System.currentTimeMillis()
        val deadline = now + 20_000L

        val saved = MobilitySessionEngine.SavedMobilityState(
            routineId = routine.id,
            movementIndex = 2,
            movementDeadlineAtMillis = deadline,
            startedAtMillis = 0L,
        )

        val restored = MobilitySessionEngine.resume(routine, saved, now)

        assertNull(restored)
    }

    @Test
    fun transicao_nao_persistida_assume_sem_transicao() {
        // Se o movimento salvo estava em transição, não persistimos transitionSecondsRemaining
        // O resume assume que não estava em transição; o tick seguinte cuida de entrar nela
        val now = System.currentTimeMillis()
        val movement = routine.movements[0] // 40s
        // deadline = now + 5s (simulando que estava nos últimos 5s do movimento, prestes a entrar em transição)
        val deadline = now + 5_000L
        val startedAt = now - 35_000L

        val saved = MobilitySessionEngine.SavedMobilityState(
            routineId = routine.id,
            movementIndex = 0,
            movementDeadlineAtMillis = deadline,
            startedAtMillis = startedAt,
        )

        val restored = MobilitySessionEngine.resume(routine, saved, now)

        assertNotNull(restored)
        assertEquals(0, restored!!.movementIndex)
        assertEquals(5, restored.secondsRemaining)
        assertEquals(0, restored.transitionSecondsRemaining)
        assertTrue(restored.running)
    }
}