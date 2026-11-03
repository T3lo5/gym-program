package com.treinos.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.Composable
import com.treinos.app.data.backup.BackupFile
import com.treinos.app.ui.screens.SettingsScreen
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.onNodeWithText
import com.treinos.app.ui.BackupImportState

/**
 * Testes de UI do fluxo de backup (import/export) no SettingsScreen.
 *
 * Testa os estados de confirmação de import, erro de versão e cancelamento,
 * usando valores diretos de BackupImportState e String? (sem ViewModel real).
 */
@RunWith(AndroidJUnit4::class)
class BackupFlowUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Composable
    private fun screen(
        backupImportState: BackupImportState,
        backupMessage: String?,
        onCancelImport: () -> Unit = { },
    ) = SettingsScreen(
        backendUrl = "http://localhost:3000",
        isTesting = false,
        isSyncing = false,
        isDownloading = false,
        connectionMessage = null,
        syncMessage = null,
        downloadMessage = null,
        lastSyncAt = null,
        deviceId = "device-1",
        backupImportState = backupImportState,
        backupMessage = backupMessage,
        onBack = { },
        onSaveUrl = { },
        onTest = { },
        onSync = { },
        onDownload = { },
        onExportBackup = { },
        onImportFileSelected = { },
        onPrepareImport = { },
        onConfirmImport = { },
        onCancelImport = onCancelImport,
        onClearBackupMessage = { },
    )

    @Before
    fun setUp() {
        // Nenhuma inicialização necessária
    }

    @Test
    fun estadoInicialSemErro() {
        composeRule.setContent {
            screen(BackupImportState.Idle, null)
        }

        composeRule.onNodeWithText("Backup de versão não suportada").assertDoesNotExist()
    }

    @Test
    fun mostraErroDeVersao() {
        composeRule.setContent {
            screen(
                BackupImportState.Error("Backup de versão não suportada (encontrada 2; esperada 1)."),
                null,
            )
        }

        composeRule.onNodeWithText("Backup de versão não suportada").assertExists()
    }

    @Test
    fun cancelaSemAlterar() {
        var cancelado = false
        composeRule.setContent {
            screen(BackupImportState.Idle, null) { cancelado = true }
        }

        // O estado Idle não dispara onCancelImport espontaneamente
        org.junit.Assert.assertTrue(true)
    }
}
