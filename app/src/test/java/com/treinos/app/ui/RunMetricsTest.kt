package com.treinos.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertEquals(27, estimateRunBlockDurationMinutes(5, 1_000.0, 330))
        assertEquals(30, estimateRunBlockDurationMinutes(1, 5_000.0, 360))
        assertEquals(0, estimateRunBlockDurationMinutes(5, 0.0, 330))
    }

    @Test
    fun sem_benchmark_usa_rpe() {
        assertNull(formatPacePerKm(1_500L, null))
    }

    @Test
    fun pace_calculado_na_finalizacao() {
        val result = validateRunLog(
            RunLogInput(durationSeconds = 1_500L, distanceKm = 5.0, rpe = null),
        )
        assertTrue(result.valid)
        assertEquals(300, result.paceSecondsPerKm)
    }

    @Test
    fun distancia_zero_nao_gera_pace() {
        val result = validateRunLog(
            RunLogInput(durationSeconds = 1_500L, distanceKm = 0.0, rpe = null),
        )
        assertTrue(result.valid)
        assertNull(result.paceSecondsPerKm)
    }

    @Test
    fun duracao_invalida_bloqueia_registro() {
        val result = validateRunLog(
            RunLogInput(durationSeconds = 0L, distanceKm = null, rpe = null),
        )
        assertFalse(result.valid)
        assertNotNull(result.error)
    }

    @Test
    fun rpe_fora_de_faixa_bloqueia_registro() {
        val result = validateRunLog(
            RunLogInput(durationSeconds = 600L, distanceKm = null, rpe = 11.0),
        )
        assertFalse(result.valid)
        assertNotNull(result.error)
    }
}
