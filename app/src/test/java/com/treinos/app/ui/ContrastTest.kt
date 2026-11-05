package com.treinos.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    @Test
    fun texto_principal_contraste_claro() {
        assertTrue(
            contrastRatio(ThemeColors.LightOnSurface, ThemeColors.LightSurface) >= 4.5,
        )
    }

    @Test
    fun texto_principal_contraste_escuro() {
        assertTrue(
            contrastRatio(ThemeColors.DarkOnSurface, ThemeColors.DarkSurface) >= 4.5,
        )
    }

    @Test
    fun texto_sobre_primary_container() {
        assertTrue(
            contrastRatio(ThemeColors.LightOnPrimaryContainer, ThemeColors.LightPrimaryContainer) >= 4.5,
        )
        assertTrue(
            contrastRatio(ThemeColors.DarkOnPrimaryContainer, ThemeColors.DarkPrimaryContainer) >= 4.5,
        )
    }

    @Test
    fun preto_e_branco_tem_contraste_maximo() {
        val ratio = contrastRatio(0xFFFFFFFF, 0xFF000000)
        assertTrue(ratio >= 21.0 - 0.001)
    }
}
