package com.treinos.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** H4-5: rampa de aquecimento — degraus 40/60/80%, arredondados para baixo. */
class WarmupRampTest {

    @Test
    fun rampa_60kg_tres_degraus() {
        // 40% de 60 = 24 kg carrega 22,5 kg (1,25 kg por lado) — sai da barra
        // e fica; 60% = 35 kg e 80% = 47,5 kg completam os três degraus.
        assertEquals(
            listOf(
                WarmupStep(percent = 40, weightKg = 22.5, reps = 8),
                WarmupStep(percent = 60, weightKg = 35.0, reps = 5),
                WarmupStep(percent = 80, weightKg = 47.5, reps = 3),
            ),
            warmupRamp(60.0),
        )
    }

    @Test
    fun rampa_100kg_tres_degraus() {
        assertEquals(
            listOf(
                WarmupStep(percent = 40, weightKg = 40.0, reps = 8),
                WarmupStep(percent = 60, weightKg = 60.0, reps = 5),
                WarmupStep(percent = 80, weightKg = 80.0, reps = 3),
            ),
            warmupRamp(100.0),
        )
    }

    @Test
    fun rampa_abaixo_da_barra_vazia() {
        assertTrue(warmupRamp(15.0).isEmpty())
        assertTrue(warmupRamp(20.0).isEmpty())
    }

    @Test
    fun rampa_deduplica_pesos_iguais() {
        // Inventário sintético (barra 2 kg, anilha única de 1,25 kg): 80% de
        // 11,7 kg carrega os mesmos 7,0 kg do 60% — mantém o primeiro (60%).
        // O 40% (4,5 kg) sai da barra e fica.
        val inventory = PlateInventory(barKg = 2.0, plateKg = listOf(1.25))
        assertEquals(
            listOf(
                WarmupStep(percent = 40, weightKg = 4.5, reps = 8),
                WarmupStep(percent = 60, weightKg = 7.0, reps = 5),
            ),
            warmupRamp(11.7, inventory),
        )
    }

    @Test
    fun rampa_arredonda_para_baixo() {
        val rampa = warmupRamp(61.0)
        assertEquals(3, rampa.size)
        // 40% de 61 = 24,4 carrega 22,5; 60% = 36,6 carrega 35; 80% = 48,8
        // carrega 47,5 — nunca acima.
        assertEquals(22.5, rampa[0].weightKg, 1e-9)
        assertEquals(35.0, rampa[1].weightKg, 1e-9)
        assertEquals(47.5, rampa[2].weightKg, 1e-9)
        assertTrue(rampa.all { it.weightKg <= 61.0 })
    }

    @Test
    fun rampa_nao_finita_vazia() {
        assertTrue(warmupRamp(Double.NaN).isEmpty())
        assertTrue(warmupRamp(Double.POSITIVE_INFINITY).isEmpty())
    }
}
