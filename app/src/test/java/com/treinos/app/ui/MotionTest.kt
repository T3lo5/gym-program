package com.treinos.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    @Test
    fun home_para_history_e_forward() {
        assertEquals(ScreenMotion.FORWARD, motionFor(Screen.Home, Screen.History))
    }

    @Test
    fun history_para_home_e_backward() {
        assertEquals(ScreenMotion.BACKWARD, motionFor(Screen.History, Screen.Home))
    }

    @Test
    fun generate_para_home_e_backward() {
        assertEquals(ScreenMotion.BACKWARD, motionFor(Screen.Generate, Screen.Home))
    }

    @Test
    fun mesma_tela_nao_anima() {
        assertEquals(ScreenMotion.NONE, motionFor(Screen.Home, Screen.Home))
    }

    @Test
    fun escala_zero_desliga_animacao() {
        assertEquals(0, motionDurationMillis(0f))
    }

    @Test
    fun loading_so_aparece_apos_150ms() {
        assertFalse(shouldShowLoading(100))
        assertTrue(shouldShowLoading(150))
    }
}
