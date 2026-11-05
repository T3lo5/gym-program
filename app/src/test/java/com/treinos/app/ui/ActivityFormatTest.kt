package com.treinos.app.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActivityFormatTest {

    @Test
    fun formata_metros() {
        assertEquals("1.500 m", formatDistanceMeters(1_500.0))
        assertEquals("250 m", formatDistanceMeters(250.0))
        assertEquals("12.500 m", formatDistanceMeters(12_500.0))
        assertNull(formatDistanceMeters(0.0))
        assertNull(formatDistanceMeters(null))
    }

    @Test
    fun formata_pace_100m() {
        assertEquals("3:00/100m", formatPacePer100m(2_700L, 1_500.0))
        assertEquals("0:30/100m", formatPacePer100m(2_700L, 9_000.0))
        // 176,7 s por 100 m arredonda para o segundo mais próximo
        assertEquals("2:57/100m", formatPacePer100m(2_650L, 1_500.0))
        assertNull(formatPacePer100m(2_700L, null))
    }

    @Test
    fun formata_pace_km() {
        assertEquals("5:00/km", formatPacePerKm(1_500L, 5_000.0))
        assertEquals("4:18/km", formatPacePerKm(1_290L, 5_000.0))
        assertEquals("4:30/km", formatPacePerKm(2_700L, 10_000.0))
        assertNull(formatPacePerKm(1_500L, 0.0))
    }

    @Test
    fun formata_duracao_minutos() {
        assertEquals("45 min", formatDuration(2_700L))
        assertEquals("1 min", formatDuration(60L))
        assertEquals("30s", formatDuration(30L))
        assertEquals("1h 00min", formatDuration(3_600L))
        assertEquals("—", formatDuration(0L))
    }

    @Test
    fun formata_volume() {
        assertEquals("0 kg", formatVolumeKg(0.0))
        assertEquals("850 kg", formatVolumeKg(850.0))
        assertEquals("999 kg", formatVolumeKg(999.4))
        assertEquals("1,0 t", formatVolumeKg(999.6))
        assertEquals("1,1 t", formatVolumeKg(1_080.0))
        assertEquals("12,3 t", formatVolumeKg(12_345.0))
    }

    @Test
    fun formata_mes_ano_e_dia() {
        assertEquals("Novembro de 2026", formatMonthYear(2026, 11))
        assertEquals("Março de 2027", formatMonthYear(2027, 3))
        assertEquals("04/11", formatDateShort(LocalDate.of(2026, 11, 4)))
    }
}
