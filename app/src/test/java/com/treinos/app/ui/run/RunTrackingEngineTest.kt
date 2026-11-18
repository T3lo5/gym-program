package com.treinos.app.ui.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunTrackingEngineTest {
    @Test
    fun ignora_localizacao_invalida() {
        val state = addRunTrackingSample(
            RunTrackingState(),
            RunTrackingSample(91.0, 0.0, 1_000L),
        )
        assertTrue(state.route.isEmpty())
        assertEquals(0.0, state.distanceMeters, 0.0)
    }

    @Test
    fun soma_distancia_e_calcula_pace() {
        var state = RunTrackingState()
        state = addRunTrackingSample(
            state,
            RunTrackingSample(-23.5505, -46.6333, 0L),
        )
        state = addRunTrackingSample(
            state,
            RunTrackingSample(-23.5415, -46.6333, 360_000L),
        )
        assertTrue(state.distanceMeters >= 900.0)
        assertNotNull(state.paceSecondsPerKm)
    }

    @Test
    fun avanca_intervalado_por_tempo() {
        assertEquals("warmup", intervalPhaseAt(299L).first)
        assertEquals("work", intervalPhaseAt(300L).first)
        assertEquals("recover", intervalPhaseAt(360L).first)
        assertEquals("work", intervalPhaseAt(420L).first)
    }

    @Test
    fun sem_gps_mantem_estado_manual() {
        val state = RunTrackingState()
        assertEquals(0.0, state.distanceMeters, 0.0)
        assertEquals(null, state.paceSecondsPerKm)
    }
}
