package com.treinos.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WearSessionKindTest {

    @Test
    fun strength_por_default() {
        assertEquals(WearSessionKind.Strength, WearSessionKind.from("strength"))
    }

    @Test
    fun run_reconhecida() {
        assertEquals(WearSessionKind.Run, WearSessionKind.from("run"))
    }

    @Test
    fun mobility_reconhecida() {
        assertEquals(WearSessionKind.Mobility, WearSessionKind.from("mobility"))
    }

    @Test
    fun desconhecida_vira_strength() {
        assertEquals(WearSessionKind.Strength, WearSessionKind.from("swimming"))
    }

    @Test
    fun vazia_vira_strength() {
        assertEquals(WearSessionKind.Strength, WearSessionKind.from(""))
    }
}