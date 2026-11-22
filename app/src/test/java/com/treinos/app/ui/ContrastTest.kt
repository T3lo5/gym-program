package com.treinos.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    @Test
    fun texto_principal_contraste_escuro() {
        assertTrue(
            contrastRatio(ThemeColors.DarkOnSurface, ThemeColors.DarkSurface) >= 4.5,
        )
    }

    @Test
    fun texto_sobre_primary_container_escuro() {
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
            contrastRatio(ThemeColors.DarkOnSurfaceVariant, ThemeColors.DarkSurface) >= 4.5,
        )
    }

    @Test
    fun pares_error_e_onerror_cobertos() {
        // error / onError (StatusText de erro)
        assertTrue(contrastRatio(ThemeColors.DarkOnError, ThemeColors.DarkError) >= 4.5)
        // errorContainer
        assertTrue(
            contrastRatio(
                ThemeColors.DarkOnErrorContainer,
                ThemeColors.DarkErrorContainer,
            ) >= 4.5,
        )
    }

    @Test
    fun alpha_blend_calculado() {
        // Cor com alpha sobre fundo escuro deve blender (contraste menor que a opaca).
        val opaco = contrastRatio(0xFF9CA3AF, ThemeColors.DarkSurface)
        val comAlpha = contrastRatio(0xBF9CA3AF, ThemeColors.DarkSurface)
        assertTrue(comAlpha < opaco)
        assertTrue(comAlpha > 0.0)
    }

    @Test
    fun numero_destaque_contraste_fundo_escuro() {
        // Número de destaque (display, texto grande) sobre fundo escuro ≥ 3:1.
        assertTrue(contrastRatio(0xFFFFFFFF, ThemeColors.DarkSurface) >= 3.0)
    }
}
