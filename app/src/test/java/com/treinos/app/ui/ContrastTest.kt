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

    @Test
    fun par_selo_gerado_com_ia_passa() {
        // Selo "Gerado com IA" / "Gerado localmente": onSurfaceVariant sobre surface.
        assertTrue(
            contrastRatio(ThemeColors.LightOnSurfaceVariant, ThemeColors.LightSurface) >= 4.5,
        )
        assertTrue(
            contrastRatio(ThemeColors.DarkOnSurfaceVariant, ThemeColors.DarkSurface) >= 4.5,
        )
    }

    @Test
    fun pares_error_e_tertiary_cobertos() {
        // error / onError (StatusText de erro)
        assertTrue(contrastRatio(ThemeColors.LightOnError, ThemeColors.LightError) >= 4.5)
        assertTrue(contrastRatio(ThemeColors.DarkOnError, ThemeColors.DarkError) >= 4.5)
        // tertiary (ReviewPlanCard / chips de ambiente)
        assertTrue(
            contrastRatio(
                ThemeColors.LightOnTertiaryContainer,
                ThemeColors.LightTertiaryContainer,
            ) >= 4.5,
        )
        assertTrue(
            contrastRatio(
                ThemeColors.DarkOnTertiaryContainer,
                ThemeColors.DarkTertiaryContainer,
            ) >= 4.5,
        )
        // errorContainer (ReviewPlanCard)
        assertTrue(
            contrastRatio(
                ThemeColors.LightOnErrorContainer,
                ThemeColors.LightErrorContainer,
            ) >= 4.5,
        )
        assertTrue(
            contrastRatio(
                ThemeColors.DarkOnErrorContainer,
                ThemeColors.DarkErrorContainer,
            ) >= 4.5,
        )
    }

    @Test
    fun alpha_blend_calculado() {
        // Cor com alpha sobre fundo claro deve blendar (contraste menor que a opaca).
        val opaco = contrastRatio(0xFF49494F, ThemeColors.LightSurface)
        val comAlpha = contrastRatio(0xBF49494F, ThemeColors.LightSurface)
        assertTrue(comAlpha < opaco)
        assertTrue(comAlpha > 0.0)
    }
}
