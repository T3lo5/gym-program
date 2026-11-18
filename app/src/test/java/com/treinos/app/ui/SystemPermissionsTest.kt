package com.treinos.app.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Estado das permissões pedidas em tela. No Robolectric (API 31) nenhuma
 * permissão runtime está concedida e `POST_NOTIFICATIONS` nem existe — os
 * dois caminhos de versão ficam cobertos sem depender de aparelho.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SystemPermissionsTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun api_31_notificacao_vale_concedida_e_instalacao_falta() {
        val estado = SystemPermissions.snapshot(context)
        // API < 33: POST_NOTIFICATIONS nem existe — não pode bloquear o card.
        assertTrue(estado.notificationsGranted)
        // API 26+: apps desconhecidos começa negada — o card precisa mostrar.
        assertFalse(estado.canInstallPackages)
        assertTrue(estado.installMissing)
        assertFalse(estado.notificationsMissing)
    }

    @Test
    fun flags_de_falta_sao_coerentes_com_o_estado_concedido() {
        val concedido = AppViewModel.PermissionsUiState(
            notificationsGranted = true,
            canInstallPackages = true,
        )
        assertEquals(false, concedido.notificationsMissing)
        assertEquals(false, concedido.installMissing)

        val faltando = AppViewModel.PermissionsUiState(
            notificationsGranted = false,
            canInstallPackages = false,
        )
        assertTrue(faltando.notificationsMissing)
        assertTrue(faltando.installMissing)
    }
}
