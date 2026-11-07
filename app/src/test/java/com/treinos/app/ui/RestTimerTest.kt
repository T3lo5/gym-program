package com.treinos.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestTimerTest {

    @Test
    fun `remaining_derivado_de_deadline`() {
        val now = 1_000_000L
        val deadline = RestTimer.start(totalSeconds = 90, nowMillis = now)!!

        // No início, o descanso de 90 s mostra 90 (arredonda para cima).
        assertEquals(90, RestTimer.remainingSeconds(deadline, now))
        // Um tick depois do início, ainda 90 — a contagem não acumula atraso.
        assertEquals(90, RestTimer.remainingSeconds(deadline, now + 500))
        // Faltando meio segundo, mostra 1.
        assertEquals(1, RestTimer.remainingSeconds(deadline, deadline - 500))
        // No deadline e depois dele, 0 — nunca negativo.
        assertEquals(0, RestTimer.remainingSeconds(deadline, deadline))
        assertEquals(0, RestTimer.remainingSeconds(deadline, deadline + 60_000))
    }

    @Test
    fun `extend_soma_sem_teto`() {
        val now = 1_000_000L
        val deadline = RestTimer.start(totalSeconds = 90, nowMillis = now)!!

        val estendido = RestTimer.extend(deadline, extraSeconds = 15)
        assertEquals(deadline + 15_000L, estendido)
        assertEquals(105, RestTimer.remainingSeconds(estendido, now))

        // Sem teto: estender de novo soma por cima.
        val duasVezes = RestTimer.extend(estendido, extraSeconds = 15)
        assertEquals(estendido + 15_000L, duasVezes)
        assertEquals(120, RestTimer.remainingSeconds(duasVezes, now))
    }

    @Test
    fun `pause_resume_recalcula_deadline`() {
        val now = 1_000_000L
        val deadline = RestTimer.start(totalSeconds = 90, nowMillis = now)!!

        // Pausa 30 s depois: restam 60 congelados.
        val pausadoEm = now + 30_000L
        val restante = RestTimer.pausedRemainingSeconds(deadline, pausadoEm)
        assertEquals(60, restante)

        // Retomada 5 minutos depois: o deadline é recalculado a partir do
        // restante congelado, não do deadline antigo.
        val retomadoEm = pausadoEm + 300_000L
        val novoDeadline = RestTimer.resume(remainingSeconds = restante, nowMillis = retomadoEm)!!
        assertEquals(retomadoEm + 60_000L, novoDeadline)
        assertEquals(60, RestTimer.remainingSeconds(novoDeadline, retomadoEm))
    }

    @Test
    fun `start_zero_nao_inicia`() {
        val now = 1_000_000L

        // Descanso de 0 s (ou negativo) não inicia: sem deadline, sem estado.
        assertNull(RestTimer.start(totalSeconds = 0, nowMillis = now))
        assertNull(RestTimer.start(totalSeconds = -5, nowMillis = now))
        // Retomada sem o que retomar também é nula.
        assertNull(RestTimer.resume(remainingSeconds = 0, nowMillis = now))
    }
}
