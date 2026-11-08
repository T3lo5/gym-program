package com.treinos.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattersTest {
    @Test
    fun format_decimal_locale_fixo() {
        assertEquals("7,5", formatDecimal(7.5, 1))
        assertEquals("60,0", formatDecimal(60.0, 1))
        assertEquals("7,50", formatDecimal(7.5, 2))
        assertEquals("0,50", formatDecimal(0.5, 2))
    }

    @Test
    fun format_kg_null_traco() {
        assertEquals("—", formatKg(null))
        assertEquals("62,5 kg", formatKg(62.5))
        assertEquals("500,0 kg", formatKg(500.0))
    }

    @Test
    fun pace_e_distancia_canonicos() {
        assertEquals("5:00/km", formatPacePerKm(1_500L, 5_000.0))
        assertEquals("4:18/km", formatPacePerKm(1_290L, 5_000.0))
        assertNull(formatPacePerKm(1_500L, 0.0))
        assertNull(formatPacePerKm(1_500L, null))
        assertEquals("5,0 km", formatRunDistanceKm(5_000.0))
        assertNull(formatRunDistanceKm(null))
    }
}
