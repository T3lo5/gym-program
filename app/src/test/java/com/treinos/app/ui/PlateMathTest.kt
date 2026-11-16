package com.treinos.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** H4-5: quebra de anilhas por lado — gulosa, sempre para baixo, nunca acima do alvo. */
class PlateMathTest {

    @Test
    fun alvo_100_por_lado_20_20() {
        val resultado = plateBreakdown(100.0)!!
        assertEquals(listOf(20.0, 20.0), resultado.perSideKg)
        assertEquals(100.0, resultado.loadedKg, 1e-9)
        assertEquals(0.0, resultado.remainderKg, 1e-9)
    }

    @Test
    fun alvo_exato_sem_resto() {
        val resultado = plateBreakdown(42.5)!!
        assertEquals(listOf(10.0, 1.25), resultado.perSideKg)
        assertEquals(42.5, resultado.loadedKg, 1e-9)
        assertEquals(0.0, resultado.remainderKg, 1e-9)
    }

    @Test
    fun alvo_nao_carregavel_arredonda_para_baixo() {
        // 61 kg com anilhas de 1,25 kg: carrega 60 e reporta 1 kg de resto.
        val resultado = plateBreakdown(61.0)!!
        assertEquals(60.0, resultado.loadedKg, 1e-9)
        assertEquals(1.0, resultado.remainderKg, 1e-9)
        assertTrue("nunca carrega acima do alvo", resultado.loadedKg <= resultado.targetKg)
    }

    @Test
    fun alvo_menor_que_barra_retorna_null() {
        assertNull(plateBreakdown(15.0))
    }

    @Test
    fun alvo_nao_finito_retorna_null() {
        assertNull(plateBreakdown(Double.NaN))
        assertNull(plateBreakdown(Double.POSITIVE_INFINITY))
        assertNull(plateBreakdown(0.0))
        assertNull(plateBreakdown(-5.0))
    }

    @Test
    fun anilhas_vazias_retorna_barra() {
        val resultado = plateBreakdown(100.0, PlateInventory(plateKg = emptyList()))!!
        assertTrue(resultado.perSideKg.isEmpty())
        assertEquals(20.0, resultado.loadedKg, 1e-9)
        assertEquals(80.0, resultado.remainderKg, 1e-9)
    }

    @Test
    fun anilhas_invalidas_sao_ignoradas() {
        val inventory = PlateInventory(plateKg = listOf(0.0, -1.0, Double.NaN, 10.0))
        val resultado = plateBreakdown(40.0, inventory)!!
        assertEquals(listOf(10.0), resultado.perSideKg)
        assertEquals(40.0, resultado.loadedKg, 1e-9)
    }

    @Test
    fun barra_invalida_retorna_null() {
        assertNull(plateBreakdown(100.0, PlateInventory(barKg = 0.0)))
        assertNull(plateBreakdown(100.0, PlateInventory(barKg = Double.NaN)))
        assertNull(plateBreakdown(100.0, PlateInventory(barKg = -20.0)))
    }

    @Test
    fun equipamento_de_barra_reconhecido() {
        assertTrue(isPlateLoadedEquipment("barbell"))
        assertFalse(isPlateLoadedEquipment("dumbbell"))
        assertFalse(isPlateLoadedEquipment("machine"))
        assertFalse(isPlateLoadedEquipment("cable"))
        assertFalse(isPlateLoadedEquipment("body only"))
        assertFalse(isPlateLoadedEquipment(null))
    }
}
