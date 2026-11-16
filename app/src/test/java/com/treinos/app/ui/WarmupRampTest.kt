package com.treinos.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** H4-5: rampa de aquecimento — degraus 40/60/80%, arredondados para baixo. */
class WarmupRampTest {

    @Test
    fun rampa_60kg_descarta_40_por_cento() {
        // 40% de 60 = 24 kg carrega 22,5 kg — só micro anilha na barra, na
        // prática a barra — e é descartado; sobram 60% e 80%.
        assertEquals(
            listOf(
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
        // Inventário sintético (barra 2 kg, anilha única de 1,25 kg): 60% e 80%
        // de 11,7 kg carregam os mesmos 7 kg — mantém o primeiro (60%).
        val inventory = PlateInventory(barKg = 2.0, plateKg = listOf(1.25))
        assertEquals(
            listOf(WarmupStep(percent = 60, weightKg = 7.0, reps = 5)),
            warmupRamp(11.7, inventory),
        )
    }

    @Test
    fun rampa_arredonda_para_baixo() {
        val rampa = warmupRamp(61.0)
        assertEquals(2, rampa.size)
        // 60% de 61 = 36,6 carrega 35; 80% = 48,8 carrega 47,5 — nunca acima.
        assertEquals(35.0, rampa[0].weightKg, 1e-9)
        assertEquals(47.5, rampa[1].weightKg, 1e-9)
        assertTrue(rampa.all { it.weightKg <= 61.0 })
    }

    @Test
    fun rampa_nao_finita_vazia() {
        assertTrue(warmupRamp(Double.NaN).isEmpty())
        assertTrue(warmupRamp(Double.POSITIVE_INFINITY).isEmpty())
    }
}
