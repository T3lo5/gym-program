package com.treinos.app.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.treinos.app.data.backup.BackupApp
import com.treinos.app.data.backup.BackupDay
import com.treinos.app.data.backup.BackupFile
import com.treinos.app.data.backup.BackupPlan
import com.treinos.app.data.backup.BackupPlanExercise
import com.treinos.app.data.backup.BackupSession
import com.treinos.app.data.backup.BackupSessionExercise
import com.treinos.app.data.backup.BackupSetLog
import com.treinos.app.data.backup.BackupValidator
import com.treinos.app.ui.screens.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Estados do fluxo de backup em Ajustes: pré-visualização com confirmação, erro de
 * versão e cancelamento. O estado vem de [BackupImportState] falsos — a tela nunca
 * toca no ViewModel nem no banco do usuário.
 */
@RunWith(RobolectricTestRunner::class)
class BackupFlowUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Texto exato que a tela mostra quando o arquivo passou na validação. */
    private val textoPreview =
        "Pré-visualização: 1 plano(s), 1 dia(s), 1 exercício(s) de plano, " +
            "1 sessão(ões), 1 exercício(s) de sessão, 2 série(s)."

    private val plano = BackupPlan(
        id = 1,
        serverPlanId = null,
        name = "Plano do arquivo",
        description = "",
        goal = "hypertrophy",
        daysPerWeek = 3,
        isActive = true,
        generatedByAi = false,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        version = 1,
        nextReviewAt = null,
        reviewAfterWeeks = 4,
        source = "local",
        days = listOf(
            BackupDay(
                id = 10,
                planId = 1,
                name = "Dia A",
                dayOrder = 0,
                notes = "",
                isRestDay = false,
                exercises = listOf(
                    BackupPlanExercise(
                        id = 100,
                        exerciseId = "Barbell_Bench_Press",
                        exerciseName = "Supino reto",
                        primaryMuscles = "chest",
                        equipment = "barbell",
                        exerciseOrder = 0,
                        sets = 3,
                        targetReps = "8-12",
                        targetRpe = 8.0,
                        restSeconds = 90,
                        notes = "",
                        isSupersetWithPrevious = false,
                        dayId = 10,
                    ),
                ),
            ),
        ),
    )

    private val sessao = BackupSession(
        id = 50,
        planId = 1,
        dayId = 10,
        planName = "Plano do arquivo",
        dayName = "Dia A",
        status = "completed",
        startedAt = 1_000L,
        endedAt = 2_000L,
        totalDurationSeconds = 60,
        notes = "",
        exercises = listOf(
            BackupSessionExercise(
                id = 500,
                exerciseId = "Barbell_Bench_Press",
                exerciseName = "Supino reto",
                exerciseOrder = 0,
                plannedSets = 3,
                completedSets = 2,
                restSeconds = 90,
                startedAt = 1_000L,
                endedAt = 2_000L,
                sessionId = 50,
                sets = listOf(
                    setLog(id = 5_000, exerciseLogId = 500, setNumber = 1),
                    setLog(id = 5_001, exerciseLogId = 500, setNumber = 2),
                ),
            ),
        ),
    )

    private fun setLog(id: Long, exerciseLogId: Long, setNumber: Int) = BackupSetLog(
        id = id,
        setNumber = setNumber,
        reps = 10,
        weightKg = 40.0,
        rpe = 8.0,
        durationSeconds = null,
        distanceMeters = null,
        isCompleted = true,
        isWarmup = false,
        restTakenSeconds = 90,
        completedAt = 2_000L,
        exerciseLogId = exerciseLogId,
    )

    private fun arquivo(schemaVersion: Int = 2) = BackupFile(
        schemaVersion = schemaVersion,
        exportedAt = "2026-11-05T18:00:00Z",
        app = BackupApp("com.treinos.app", "1.0.0", 1),
        deviceId = "device-do-arquivo",
        plans = listOf(plano),
        sessions = listOf(sessao),
    )

    private fun render(
        state: MutableState<BackupImportState>,
        message: String? = null,
        onConfirmImport: () -> Unit = { },
        onCancelImport: () -> Unit = { },
        onClearBackupMessage: () -> Unit = { },
    ) {
        composeRule.setContent {
            SettingsScreen(
                backendUrl = "http://localhost:3000",
                isTesting = false,
                isSyncing = false,
                isDownloading = false,
                connectionMessage = null,
                syncMessage = null,
                downloadMessage = null,
                lastSyncAt = null,
                deviceId = "device-1",
                backupImportState = state.value,
                backupMessage = message,
                isRestoring = false,
                restoreDeviceId = "",
                restoreMessage = null,
                onBack = { },
                onSaveUrl = { },
                onTest = { },
                onSync = { },
                onDownload = { },
                onRestoreDeviceIdChanged = { },
                onRestoreByDeviceId = { },
                onExportBackup = { },
                onImportFileSelected = { },
                onPrepareImport = { },
                onConfirmImport = onConfirmImport,
                onCancelImport = onCancelImport,
                onClearBackupMessage = onClearBackupMessage,
            )
        }
    }

    @Test
    fun mostra_confirmacao_de_import() {
        val payload = arquivo()
        val preview = requireNotNull(BackupValidator.validate(payload).preview)
        val state = mutableStateOf<BackupImportState>(BackupImportState.Ready(preview, payload))
        var confirmou = false

        render(state, onConfirmImport = { confirmou = true })

        composeRule.onNodeWithText(textoPreview).assertExists()
        // O card de backup é o último da coluna rolável: sem rolar, o toque cai fora.
        composeRule.onNodeWithText("Substituir tudo").performScrollTo().performClick()
        assertTrue(confirmou)
    }

    @Test
    fun mostra_erro_de_versao() {
        val invalido = arquivo(schemaVersion = 4)
        val resultado = BackupValidator.validate(invalido)
        val mensagem = requireNotNull(resultado.message)
        val state = mutableStateOf<BackupImportState>(BackupImportState.Error(mensagem))
        var dispensou = false

        render(state, onClearBackupMessage = { dispensou = true })

        assertEquals(false, resultado.valid)
        composeRule.onNodeWithText(mensagem).assertExists()
        // Sem pré-visualização e sem botão de confirmar: nada pode ser gravado.
        composeRule.onNodeWithText(textoPreview).assertDoesNotExist()
        composeRule.onNodeWithText("Substituir tudo").assertDoesNotExist()
        composeRule.onNodeWithText("Dispensar").performScrollTo().performClick()
        assertTrue(dispensou)
    }

    @Test
    fun cancela_sem_alterar() {
        val payload = arquivo()
        val preview = requireNotNull(BackupValidator.validate(payload).preview)
        val state = mutableStateOf<BackupImportState>(BackupImportState.Ready(preview, payload))
        var confirmou = false
        var cancelou = false

        render(
            state,
            onConfirmImport = { confirmou = true },
            onCancelImport = {
                cancelou = true
                state.value = BackupImportState.Idle
            },
        )

        composeRule.onNodeWithText(textoPreview).assertExists()
        composeRule.onNodeWithText("Cancelar").performScrollTo().performClick()

        assertTrue(cancelou)
        assertEquals(false, confirmou)
        assertSame(BackupImportState.Idle, state.value)
        // A confirmação sai da tela: sem pré-visualização e sem botão de substituir.
        composeRule.onNodeWithText(textoPreview).assertDoesNotExist()
        composeRule.onNodeWithText("Substituir tudo").assertDoesNotExist()
    }

    @Test
    fun card_de_backup_avisa_que_o_arquivo_contem_o_perfil() {
        render(mutableStateOf<BackupImportState>(BackupImportState.Idle))

        composeRule.onNodeWithText("perfil (peso e altura)", substring = true)
            .performScrollTo()
            .assertExists()
    }
}
