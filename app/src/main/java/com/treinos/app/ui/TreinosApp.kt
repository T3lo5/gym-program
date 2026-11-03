package com.treinos.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.treinos.app.ui.screens.GenerateScreen
import com.treinos.app.ui.screens.HistoryScreen
import com.treinos.app.ui.screens.HomeScreen
import com.treinos.app.ui.screens.SettingsScreen
import com.treinos.app.ui.screens.WorkoutScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@Composable
fun TreinosApp(viewModel: AppViewModel) {
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val plan by viewModel.activePlan.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val inProgressSession by viewModel.inProgressSession.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val restRemaining by viewModel.restRemaining.collectAsStateWithLifecycle()
    val restTotal by viewModel.restTotal.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTesting.collectAsStateWithLifecycle()
    val backendUrl by viewModel.backendUrl.collectAsStateWithLifecycle()
    val connectionMessage by viewModel.connectionMessage.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val downloadMessage by viewModel.downloadMessage.collectAsStateWithLifecycle()
    val lastSyncAt by viewModel.lastSyncAt.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val backupImportState by viewModel.backupImportState.collectAsStateWithLifecycle()
    val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.clearMessage()
    }

    LaunchedEffect(backupMessage) {
        val text = backupMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.clearBackupMessage()
    }

    BackHandler(enabled = screen != Screen.Home) {
        when (screen) {
            Screen.Workout -> viewModel.leaveWorkout()
            else -> viewModel.goHome()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (screen) {
                Screen.Home -> HomeScreen(
                    plan = plan,
                    inProgressSession = inProgressSession,
                    onGenerate = viewModel::openGenerate,
                    onHistory = viewModel::openHistory,
                    onSettings = viewModel::openSettings,
                    onStartDay = viewModel::startWorkout,
                    onResume = viewModel::resumeWorkout,
                )

                Screen.Generate -> GenerateScreen(
                    isGenerating = isGenerating,
                    onBack = viewModel::goHome,
                    onGenerate = viewModel::generatePlan,
                )

                Screen.Workout -> WorkoutScreen(
                    session = activeSession,
                    restRemaining = restRemaining,
                    restTotal = restTotal,
                    onLeave = viewModel::leaveWorkout,
                    onLogSet = { exercise, reps, weight, rpe ->
                        viewModel.logSet(exercise.exercise, reps, weight, rpe)
                    },
                    onSkipRest = viewModel::skipRest,
                    onFinish = viewModel::finishWorkout,
                    hasInProgressSession = inProgressSession != null,
                )

                Screen.History -> HistoryScreen(
                    sessions = sessions,
                    onBack = viewModel::goHome,
                )

                Screen.Settings -> SettingsScreen(
                    backendUrl = backendUrl,
                    isTesting = isTesting,
                    isSyncing = isSyncing,
                    isDownloading = isDownloading,
                    connectionMessage = connectionMessage,
                    syncMessage = syncMessage,
                    downloadMessage = downloadMessage,
                    lastSyncAt = lastSyncAt,
                    deviceId = viewModel.deviceId,
                    backupImportState = backupImportState,
                    backupMessage = backupMessage,
                    onBack = viewModel::goHome,
                    onSaveUrl = viewModel::saveBackendUrl,
                    onTest = viewModel::testConnection,
                    onSync = viewModel::syncNow,
                    onDownload = viewModel::downloadNow,
                    onExportBackup = {
                        scope.launch {
                            try {
                                val json = viewModel.exportBackupJson(
                                    appVersionName = "1.0.0",
                                    appVersionCode = 1L,
                                )
                                viewModel.setBackupMessage(
                                    "Backup gerado (${
                                        json.lines().count { it.trim().isNotEmpty() }
                                    } linhas). Copie o conteúdo do log para salvar."
                                )
                            } catch (e: Exception) {
                                viewModel.setBackupMessage(
                                    e.message ?: "Não foi possível exportar o backup."
                                )
                            }
                        }
                    },
                    onImportFileSelected = { /* seletor de arquivo a ser conectado */ },
                    onPrepareImport = { json ->
                        viewModel.prepareImport(json)
                    },
                    onConfirmImport = { viewModel.confirmImport() },
                    onCancelImport = { viewModel.cancelImport() },
                    onClearBackupMessage = { viewModel.clearBackupMessage() },
                )
            }
        }
    }
}
