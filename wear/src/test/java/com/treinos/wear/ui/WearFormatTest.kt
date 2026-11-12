package com.treinos.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearFormatTest {

    @Test
    fun ultima_serie_completa() {
        assertEquals("55 kg × 10 reps", formatLastSet(55.0, 10))
    }

    @Test
    fun ultima_serie_somente_reps() {
        assertEquals("× 10 reps", formatLastSet(null, 10))
    }

    @Test
    fun ultima_serie_somente_carga() {
        assertEquals("55 kg ×", formatLastSet(55.0, null))
    }

    @Test
    fun carga_inteira_sem_decimais() {
        assertEquals("55 kg × 10 reps", formatLastSet(55.0, 10))
    }

    @Test
    fun carga_fracionaria_com_virgula() {
        assertEquals("52,5 kg × 8 reps", formatLastSet(52.5, 8))
    }

    @Test
    fun sem_nenhuma_ultima_serie_retorna_nulo() {
        assertNull(formatLastSet(null, null))
    }
}
