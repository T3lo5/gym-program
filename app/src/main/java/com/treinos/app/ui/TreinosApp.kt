package com.treinos.app.ui

import android.content.Context
import android.content.pm.PackageInfo
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.treinos.app.data.local.WorkoutPlanWithDetails
import com.treinos.app.ui.screens.GenerateScreen
import com.treinos.app.ui.screens.HistoryScreen
import com.treinos.app.ui.screens.HomeScreen
import com.treinos.app.ui.screens.LogActivityScreen
import com.treinos.app.ui.screens.SettingsScreen
import com.treinos.app.ui.screens.WorkoutScreen
import com.treinos.app.ui.components.ExerciseDetailSheet
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

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
    val isRestoring by viewModel.isRestoring.collectAsStateWithLifecycle()
    val restoreDeviceId by viewModel.restoreDeviceId.collectAsStateWithLifecycle()
    val restoreMessage by viewModel.restoreMessage.collectAsStateWithLifecycle()
    val deviceId by viewModel.deviceId.collectAsStateWithLifecycle()
    val lastSyncAt by viewModel.lastSyncAt.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val backupImportState by viewModel.backupImportState.collectAsStateWithLifecycle()
    val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()
    val bodyWeightKg by viewModel.bodyWeightKg.collectAsStateWithLifecycle()
    val heightCm by viewModel.heightCm.collectAsStateWithLifecycle()
    val exerciseMedia by viewModel.exerciseMedia.collectAsStateWithLifecycle()
    val selectedExerciseId by viewModel.selectedExerciseId.collectAsStateWithLifecycle()
    val isUpdatingCatalog by viewModel.isUpdatingCatalog.collectAsStateWithLifecycle()
    val catalogMessage by viewModel.catalogMessage.collectAsStateWithLifecycle()
    val activityPrefill by viewModel.activityPrefill.collectAsStateWithLifecycle()
    val activityEditingId by viewModel.activityEditingId.collectAsStateWithLifecycle()
    val activityErrors by viewModel.activityErrors.collectAsStateWithLifecycle()
    val isSavingActivity by viewModel.isSavingActivity.collectAsStateWithLifecycle()
    val canCopyLastActivity by viewModel.canCopyLastActivity.collectAsStateWithLifecycle()
    val discoveredBackends by viewModel.discoveredBackends.collectAsStateWithLifecycle()
    val isDiscovering by viewModel.isDiscovering.collectAsStateWithLifecycle()
    val isConnectingBackend by viewModel.isConnectingBackend.collectAsStateWithLifecycle()
    val discoveryMessage by viewModel.discoveryMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val historyMuscleByExerciseId = remember(plan) { muscleLabelByExerciseId(plan) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // O JSON é gerado e escrito no cacheDir antes de tocar no arquivo escolhido:
    // se a geração falhar, o backup anterior do usuário continua intacto.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val (versionName, versionCode) = appVersionInfo(context)
                val json = viewModel.exportBackupJson(
                    appVersionName = versionName,
                    appVersionCode = versionCode,
                )
                val cacheFile = File(context.cacheDir, "backup-treinos.json")
                cacheFile.writeText(json)
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error("Não foi possível abrir o arquivo escolhido para escrita.")
                output.use { out -> cacheFile.inputStream().use { it.copyTo(out) } }
                viewModel.setBackupMessage("Backup exportado no arquivo escolhido.")
            } catch (error: Exception) {
                viewModel.setBackupMessage(error.message ?: "Não foi possível exportar o backup.")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val input = context.contentResolver.openInputStream(uri)
            if (input == null) {
                viewModel.setBackupMessage("Não foi possível ler o arquivo escolhido.")
                return@launch
            }
            val json = input.bufferedReader().use { it.readText() }
            viewModel.prepareImport(json)
        }
    }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.clearMessage()
    }

    LaunchedEffect(catalogMessage) {
        val text = catalogMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.clearCatalogMessage()
    }

    // A descoberta é cara para a bateria: ao sair de Ajustes ela é interrompida.
    LaunchedEffect(screen) {
        if (screen != Screen.Settings) viewModel.stopDiscovery()
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
                    onLogActivity = viewModel::openLogActivity,
                )

                Screen.Generate -> GenerateScreen(
                    isGenerating = isGenerating,
                    onBack = viewModel::goHome,
                    savedBodyWeightKg = bodyWeightKg,
                    savedHeightCm = heightCm,
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
                    exerciseMedia = exerciseMedia,
                    onOpenExerciseDetail = { viewModel.openExerciseDetail(it) },
                )

                Screen.History -> HistoryScreen(
                    sessions = sessions,
                    onBack = viewModel::goHome,
                    exerciseMedia = exerciseMedia,
                    onOpenExerciseDetail = { viewModel.openExerciseDetail(it) },
                    onEditActivity = viewModel::editLogActivity,
                    onDeleteActivity = viewModel::deleteLogActivity,
                    muscleByExerciseId = historyMuscleByExerciseId,
                    onGenerate = viewModel::openGenerate,
                )

                Screen.LogActivity -> LogActivityScreen(
                    prefill = activityPrefill,
                    editingSessionId = activityEditingId,
                    isSaving = isSavingActivity,
                    errors = activityErrors,
                    canCopyLast = canCopyLastActivity,
                    onBack = viewModel::goHome,
                    onModalityChanged = viewModel::onActivityModalityChanged,
                    onCopyLast = viewModel::copyLastActivity,
                    onSave = viewModel::saveActivity,
                    onDelete = { activityEditingId?.let(viewModel::deleteLogActivity) },
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
                    deviceId = deviceId,
                    backupImportState = backupImportState,
                    backupMessage = backupMessage,
                    isRestoring = isRestoring,
                    restoreDeviceId = restoreDeviceId,
                    restoreMessage = restoreMessage,
                    onBack = viewModel::goHome,
                    onSaveUrl = viewModel::saveBackendUrl,
                    onTest = viewModel::testConnection,
                    onSync = viewModel::syncNow,
                    onDownload = viewModel::downloadNow,
                    onRestoreDeviceIdChanged = viewModel::onRestoreDeviceIdChanged,
                    onRestoreByDeviceId = viewModel::restoreByDeviceId,
                    onExportBackup = {
                        exportLauncher.launch(backupFileName())
                    },
                    onImportFileSelected = {
                        importLauncher.launch(arrayOf("application/json"))
                    },
                    onPrepareImport = { json -> viewModel.prepareImport(json) },
                    onConfirmImport = { viewModel.confirmImport() },
                    onCancelImport = { viewModel.cancelImport() },
                    onClearBackupMessage = { viewModel.clearBackupMessage() },
                    isUpdatingCatalog = isUpdatingCatalog,
                    catalogMessage = catalogMessage,
                    onUpdateCatalog = viewModel::updateCatalog,
                    onClearCatalogMessage = viewModel::clearCatalogMessage,
                    discoveredBackends = discoveredBackends,
                    isDiscovering = isDiscovering,
                    isConnectingBackend = isConnectingBackend,
                    discoveryMessage = discoveryMessage,
                    onSearchBackends = viewModel::startDiscovery,
                    onStopSearch = viewModel::stopDiscovery,
                    onConnectBackend = viewModel::connectDiscoveredBackend,
                    onClearDiscoveryMessage = viewModel::clearDiscoveryMessage,
                )
            }
        }
    }

    val selectedMedia = selectedExerciseId?.let { exerciseMedia[it] }
    if (selectedExerciseId != null) {
        ExerciseDetailSheet(
            media = selectedMedia,
            onDismiss = { viewModel.closeExerciseDetail() },
        )
    }
}

private fun backupFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
    return "treinos-backup-$stamp.json"
}

/**
 * `exerciseId` -> músculos do plano ativo, separados por vírgula. Exercícios fora do
 * plano são agrupados em "Outros" pela agregação do histórico.
 */
private fun muscleLabelByExerciseId(plan: WorkoutPlanWithDetails?): Map<String, String> =
    plan?.days
        ?.flatMap { it.exercises }
        ?.associate { it.exerciseId to it.primaryMuscles.trim() }
        .orEmpty()

private fun appVersionInfo(context: Context): Pair<String, Long> {
    val info: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    return (info.versionName ?: "desconhecida") to PackageInfoCompat.getLongVersionCode(info)
}
