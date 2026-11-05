package com.treinos.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunMetricsTest {

    @Test
    fun formata_pace_por_km() {
        assertEquals("5:00/km", formatPacePerKm(1_500L, 5_000.0))
        assertEquals("4:18/km", formatPacePerKm(1_290L, 5_000.0))
        assertEquals("4:30/km", formatPacePerKm(2_700L, 10_000.0))
        assertNull(formatPacePerKm(1_500L, 0.0))
        assertNull(formatPacePerKm(1_500L, null))
    }

    @Test
    fun calcula_duracao_do_bloco() {
        // 5 x 1 km a 330 s/km = 5 * 1 * 330 = 1650 s = 27 min
        assertEquals(27, estimateRunBlockDurationMinutes(5, 1_000.0, 330))
        // 1 x 5 km a 360 s/km = 1800 s = 30 min
        assertEquals(30, estimateRunBlockDurationMinutes(1, 5_000.0, 360))
        // Sem distância → 0
        assertEquals(0, estimateRunBlockDurationMinutes(5, 0.0, 330))
    }

    @Test
    fun sem_benchmark_usa_rpe() {
        // Sem distância registrada não há pace: o app usa RPE para corrida.
        assertNull(formatPacePerKm(1_500L, null))
    }
}
