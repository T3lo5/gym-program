package com.treinos.app.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H3-51: o card de atualização só aparece quando o versionCode do release é
 * estritamente maior que o local. Com a numeração única (scripts/version.sh),
 * o release e o build local compartilham a mesma sequência.
 */
class VersionCompareTest {

    @Test
    fun `remoto_maior_mostra_card`() {
        assertTrue(VersionCompare.shouldShowCard(3615279L, 3615278L))
    }

    @Test
    fun `igual_nao_mostra`() {
        assertFalse(VersionCompare.shouldShowCard(3615278L, 3615278L))
    }

    @Test
    fun `remoto_menor_nao_mostra`() {
        assertFalse(VersionCompare.shouldShowCard(100L, 3615278L))
    }

    @Test
    fun `local_invalido_tratado_como_zero`() {
        assertEquals(0L, VersionCompare.localVersionCode(null))
        assertEquals(0L, VersionCompare.localVersionCode("abc"))
        assertEquals(0L, VersionCompare.localVersionCode("  "))
        assertEquals(42L, VersionCompare.localVersionCode(" 42 "))
        // Local ilegível não esconde um update real.
        assertTrue(VersionCompare.shouldShowCard(1L, VersionCompare.localVersionCode("abc")))
    }
}
