package com.treinos.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Leitura do estado real das permissões do sistema que o app pede em tela
 * (card da Home e seção em Ajustes). Isolado do ViewModel para ser testável
 * em Robolectric sem montar o `AppViewModel` inteiro.
 */
object SystemPermissions {

    fun snapshot(context: Context): AppViewModel.PermissionsUiState {
        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            // Antes do Android 13 a permissão nem existe: vale como concedida.
            true
        }
        val install = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            // Antes do Android 8 não existe instalador por app: vale como ok.
            true
        }
        return AppViewModel.PermissionsUiState(
            notificationsGranted = notifications,
            canInstallPackages = install,
        )
    }
}
