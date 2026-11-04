package com.treinos.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.treinos.wear.ui.WearTimerAction.Skip
import com.treinos.wear.ui.WearTimerAction.SetConnection
import com.treinos.wear.ui.WearTimerAction.Tick
import com.treinos.wear.ui.WearTimerAction.Reset
class WearTimerTest {

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
}
