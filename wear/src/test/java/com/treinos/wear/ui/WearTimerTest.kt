package com.treinos.wear.ui

import com.treinos.protocol.WearWorkoutState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.treinos.wear.ui.WearTimerAction.Skip
import com.treinos.wear.ui.WearTimerAction.SetConnection
import com.treinos.wear.ui.WearTimerAction.Tick
import com.treinos.wear.ui.WearTimerAction.Reset
class WearTimerTest {

    private fun estadoPulso(
        sessionId: Long = 1,
        exerciseIndex: Int = 0,
        setNumber: Int = 1,
        restRemainingSeconds: Int? = 90,
    ): WearWorkoutState =
        WearWorkoutState(
            sessionId = sessionId,
            dayName = "Treino A",
            exerciseIndex = exerciseIndex,
            exerciseCount = 2,
            exerciseName = "Supino",
            setNumber = setNumber,
            plannedSets = 3,
            targetReps = "8-12",
            status = "in_progress",
            updatedAt = 1_000_000_000_000L,
            restRemainingSeconds = restRemainingSeconds,
        )

    @Test
    fun decrementa_a_cada_segundo() {
        var estado = WearTimerState(remainingSeconds = 5, isRunning = true, connected = true)
        estado = WearTimer.reduce(estado, Tick)
        assertEquals(4, estado.remainingSeconds)
        assertTrue(estado.isRunning)
    }

    @Test
    fun para_em_zero() {
        var estado = WearTimerState(remainingSeconds = 1, isRunning = true, connected = true)
        estado = WearTimer.reduce(estado, Tick)
        assertEquals(0, estado.remainingSeconds)
        assertFalse(estado.isRunning)
        // Não fica negativo
        estado = WearTimer.reduce(estado, Tick)
        assertEquals(0, estado.remainingSeconds)
    }

    @Test
    fun estado_novo_reinicia_timer() {
        var estado = WearTimerState(remainingSeconds = 0, isRunning = false, connected = true)
        estado = WearTimer.reduce(estado, Reset(seconds = 30))
        assertEquals(30, estado.remainingSeconds)
        assertTrue(estado.isRunning)
    }

    @Test
    fun pular_descanso_zera_timer() {
        var estado = WearTimerState(remainingSeconds = 30, isRunning = true, connected = true)
        estado = WearTimer.reduce(estado, Skip)
        assertEquals(0, estado.remainingSeconds)
        assertFalse(estado.isRunning)
    }

    @Test
    fun sem_conexao_mantem_timer_local() {
        var estado = WearTimerState(remainingSeconds = 10, isRunning = true, connected = true)
        estado = WearTimer.reduce(estado, SetConnection(false))
        assertFalse(estado.connected)
        assertEquals(10, estado.remainingSeconds)
        assertTrue(estado.isRunning)
        // Tick continua local sem conexão
        estado = WearTimer.reduce(estado, Tick)
        assertEquals(9, estado.remainingSeconds)
    }

    @Test
    fun sincroniza_novo_descanso_pelo_estado() {
        var estado = WearTimerState(remainingSeconds = 0, isRunning = false, connected = true)
        val novo = WearTimer.syncFromState(estado, estadoPulso(restRemainingSeconds = 90))
        assertEquals(90, novo.remainingSeconds)
        assertTrue(novo.isRunning)
        assertEquals("1:0:1:90", novo.restKey)
    }

    @Test
    fun mesmo_descanso_nao_reinicia_timer() {
        var estado = WearTimerState(remainingSeconds = 45, isRunning = true, connected = true, restKey = "1:0:1:90")
        val novo = WearTimer.syncFromState(estado, estadoPulso(restRemainingSeconds = 90))
        // Mesma chave: não reinicia, continua de onde está
        assertEquals(45, novo.remainingSeconds)
        assertTrue(novo.isRunning)
        assertEquals("1:0:1:90", novo.restKey)
    }

    @Test
    fun republicacao_mesma_chave_mantem_restante() {
        var estado = WearTimerState(remainingSeconds = 30, isRunning = false, connected = true, restKey = "1:0:1:90")
        val novo = WearTimer.syncFromState(estado, estadoPulso(restRemainingSeconds = 90))
        // Mesma chave: estado inalterado (mesmo com descanso > 0 no incoming)
        assertEquals(30, novo.remainingSeconds)
        assertFalse(novo.isRunning)
        assertEquals("1:0:1:90", novo.restKey)
    }

    @Test
    fun descanso_nulo_zera_timer_rodando() {
        var estado = WearTimerState(remainingSeconds = 60, isRunning = true, connected = true)
        val novo = WearTimer.syncFromState(estado, estadoPulso(restRemainingSeconds = null))
        assertEquals(0, novo.remainingSeconds)
        assertFalse(novo.isRunning)
        assertNull(novo.restKey)
    }

    @Test
    fun chave_muda_com_set_number() {
        var estado = WearTimerState(remainingSeconds = 0, isRunning = false, connected = true)
        estado = WearTimer.syncFromState(estado, estadoPulso(setNumber = 1, restRemainingSeconds = 90))
        assertEquals(90, estado.remainingSeconds)
        assertEquals("1:0:1:90", estado.restKey)

        // Mesmo descanso de 90 s, mas série nova (setNumber 2): reinicia
        estado = WearTimer.syncFromState(estado, estadoPulso(setNumber = 2, restRemainingSeconds = 90))
        assertEquals(90, estado.remainingSeconds)
        assertEquals("1:0:2:90", estado.restKey)
    }
}
