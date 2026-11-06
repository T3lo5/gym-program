package com.treinos.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionModeTest {

    @Test
    fun modality_run_usa_modo_corrida() {
        assertEquals(SessionKind.RUN, SessionKind.fromModality("run"))
    }

    @Test
    fun modality_mobility_usa_modo_mobilidade() {
        assertEquals(SessionKind.MOBILITY, SessionKind.fromModality("mobility"))
    }

    @Test
    fun modality_strength_usa_modo_series() {
        assertEquals(SessionKind.STRENGTH, SessionKind.fromModality("strength"))
    }

    @Test
    fun modality_desconhecida_assume_strength() {
        assertEquals(SessionKind.STRENGTH, SessionKind.fromModality(null))
        assertEquals(SessionKind.STRENGTH, SessionKind.fromModality("outro"))
        assertEquals(SessionKind.STRENGTH, SessionKind.fromModality(""))
    }
}
