package com.treinos.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Conversão de distância da corrida (H3-31): o corredor digita km, a sessão
 * grava metros. Funções puras — sem Android.
 */
class RunLogTest {

    @Test
    fun km_uma_casa_converte_metros() {
        assertEquals(5200.0, runDistanceKmToMeters(5.2), 0.001)
        assertEquals(0.0, runDistanceKmToMeters(0.0), 0.001)
        assertEquals(10000.0, runDistanceKmToMeters(10.0), 0.001)
        assertEquals(500.0, runDistanceKmToMeters(0.5), 0.001)
    }

    @Test
    fun km_invalido_rejeitado() {
        // Duas casas decimais não passam (o campo aceita no máximo uma).
        assertNull(parseDistanceKm("5,25"))
        assertNull(parseDistanceKm("5.25"))
        // Texto não numérico.
        assertNull(parseDistanceKm("abc"))
        assertNull(parseDistanceKm(""))
        assertNull(parseDistanceKm("   "))
        // Negativo rejeitado.
        assertNull(parseDistanceKm("-1,0"))
        // Uma casa decimal com vírgula ou ponto é aceita.
        assertEquals(5.2, parseDistanceKm("5,2")!!, 0.0001)
        assertEquals(5.2, parseDistanceKm("5.2")!!, 0.0001)
        assertEquals(0.0, parseDistanceKm("0")!!, 0.0001)
        // Vazio = sem distância (campo opcional).
        assertNull(parseDistanceKm(""))
    }
}
