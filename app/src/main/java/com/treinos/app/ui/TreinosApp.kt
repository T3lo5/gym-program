package com.treinos.app.ui

import android.animation.ValueAnimator
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.treinos.app.data.backup.BackupSizeGuard
import com.treinos.app.data.backup.BackupValidator
import com.treinos.app.data.SwapResult
import com.treinos.app.data.SwapScope
import com.treinos.app.data.local.WorkoutPlanWithDetails
import com.treinos.app.ui.screens.GenerateScreen
import com.treinos.app.ui.screens.HistoryScreen
import com.treinos.app.ui.screens.HomeScreen
import com.treinos.app.ui.screens.LogActivityScreen
import com.treinos.app.ui.screens.MobilityScreen
import com.treinos.app.ui.screens.RunWorkoutScreen
import com.treinos.app.ui.screens.SettingsScreen
import com.treinos.app.ui.screens.WorkoutScreen
import com.treinos.app.ui.components.BottomNavigationBar
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
    val plans by viewModel.activePlans.collectAsStateWithLifecycle()
    val plansState by viewModel.plansState.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()
    val inProgressSession by viewModel.inProgressSession.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val activeSessionState by viewModel.activeSessionState.collectAsStateWithLifecycle()
    val restRemaining by viewModel.restRemaining.collectAsStateWithLifecycle()
    val restTotal by viewModel.restTotal.collectAsStateWithLifecycle()
    val restPaused by viewModel.restPaused.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    // H3-30: config restaurada na tela de gerar e motivo do fallback no selo.
    val generateConfig by viewModel.generateConfig.collectAsStateWithLifecycle()
    val lastFallbackReason by viewModel.lastFallbackReason.collectAsStateWithLifecycle()
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
    // H3-51: estado do updater in-app (checagem sob demanda em Ajustes).
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    // H3-40: estado da geração pendente (envelope + status do job).
    val pendingGeneration by viewModel.pendingGeneration.collectAsStateWithLifecycle()
    // H3-53: resumo do treino recém-finalizado, exibido uma vez na Home.
    val postWorkoutSummary by viewModel.postWorkoutSummary.collectAsStateWithLifecycle()
    val pendingGenerationStatus by viewModel.pendingGenerationStatus.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val generateError by viewModel.generateError.collectAsStateWithLifecycle()
    val syncError by viewModel.syncError.collectAsStateWithLifecycle()
    val downloadError by viewModel.downloadError.collectAsStateWithLifecycle()
    val backupImportState by viewModel.backupImportState.collectAsStateWithLifecycle()
    val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()
    val bodyWeightKg by viewModel.bodyWeightKg.collectAsStateWithLifecycle()
    val heightCm by viewModel.heightCm.collectAsStateWithLifecycle()
    val sex by viewModel.sex.collectAsStateWithLifecycle()
    val exerciseMedia by viewModel.exerciseMedia.collectAsStateWithLifecycle()
    val selectedExerciseId by viewModel.selectedExerciseId.collectAsStateWithLifecycle()
    val isUpdatingCatalog by viewModel.isUpdatingCatalog.collectAsStateWithLifecycle()
    val catalogMessage by viewModel.catalogMessage.collectAsStateWithLifecycle()
    val activityPrefill by viewModel.activityPrefill.collectAsStateWithLifecycle()
    val progressionSuggestions by viewModel.progressionSuggestions.collectAsStateWithLifecycle()
    val planCycle by viewModel.planCycle.collectAsStateWithLifecycle()
    val activityEditingId by viewModel.activityEditingId.collectAsStateWithLifecycle()
    val activityErrors by viewModel.activityErrors.collectAsStateWithLifecycle()
    val isSavingActivity by viewModel.isSavingActivity.collectAsStateWithLifecycle()
    val canCopyLastActivity by viewModel.canCopyLastActivity.collectAsStateWithLifecycle()
    // H3-31: alvo do plano na corrida e estado da mobilidade fora da composição.
    val runPlanTarget by viewModel.runPlanTarget.collectAsStateWithLifecycle()
    val mobilityState by viewModel.mobilityState.collectAsStateWithLifecycle()
    val discoveredBackends by viewModel.discoveredBackends.collectAsStateWithLifecycle()
    val isDiscovering by viewModel.isDiscovering.collectAsStateWithLifecycle()
    val isConnectingBackend by viewModel.isConnectingBackend.collectAsStateWithLifecycle()
    val discoveryMessage by viewModel.discoveryMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Dispensar o aviso de deload é só visual: nada aqui muda o plano gravado.
    var isDeloadNoticeDismissed by remember { mutableStateOf(false) }
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
                    ?: error(context.getString(com.treinos.app.R.string.backup_file_open_error))
                output.use { out -> cacheFile.inputStream().use { it.copyTo(out) } }
                viewModel.setBackupMessage(context.getString(com.treinos.app.R.string.backup_exported_message))
            } catch (error: Exception) {
                viewModel.setBackupMessage(
                    uiErrorMessage(error, context, com.treinos.app.R.string.backup_export_error),
                )
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val knownSize = documentSize(context.contentResolver, uri)
            if (knownSize != null) {
                val sizeCheck = BackupValidator.validateSize(knownSize)
                if (!sizeCheck.valid) {
                    viewModel.setBackupMessage(sizeCheck.message ?: BackupSizeGuard.tooLargeMessage)
                    return@launch
                }
            }
            val input = context.contentResolver.openInputStream(uri)
            if (input == null) {
                viewModel.setBackupMessage(context.getString(com.treinos.app.R.string.backup_file_read_error))
                return@launch
            }
            val json = input.use { BackupSizeGuard.readLimited(it) }
            if (json == null) {
                viewModel.setBackupMessage(BackupSizeGuard.tooLargeMessage)
                return@launch
            }
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
            else -> viewModel.voltar()
        }
    }

    val isHub = screen == Screen.Home || screen == Screen.History || screen == Screen.Settings
    val bottomBarSelected = when (screen) {
        Screen.Home -> 0
        Screen.History -> 1
        Screen.Settings -> 2
        else -> -1
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { if (isHub) BottomNavigationBar(bottomBarSelected, viewModel) else null },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AnimatedContent(
                targetState = screen,
                label = "screen",
                transitionSpec = {
                    val animatorScale = if (Build.VERSION.SDK_INT >= 33) {
                        ValueAnimator.getDurationScale()
                    } else {
                        1f
                    }
                    val duration = motionDurationMillis(animatorScale)
                    val motion = if (initialState == null) {
                        ScreenMotion.NONE
                    } else {
                        motionFor(initialState, targetState)
                    }
                    val enter = when (motion) {
                        ScreenMotion.FORWARD -> slideIntoContainer(
                            towards = SlideDirection.Right,
                            animationSpec = tween(durationMillis = duration),
                        )
                        ScreenMotion.BACKWARD -> slideIntoContainer(
                            towards = SlideDirection.Left,
                            animationSpec = tween(durationMillis = duration),
                        )
                        ScreenMotion.NONE -> fadeIn(animationSpec = tween(durationMillis = duration))
                    }
                    val exit = when (motion) {
                        ScreenMotion.FORWARD -> slideOutOfContainer(
                            towards = SlideDirection.Left,
                            animationSpec = tween(durationMillis = duration),
                        )
                        ScreenMotion.BACKWARD -> slideOutOfContainer(
                            towards = SlideDirection.Right,
                            animationSpec = tween(durationMillis = duration),
                        )
                        ScreenMotion.NONE -> fadeOut(animationSpec = tween(durationMillis = duration))
                    }
                    enter.togetherWith(exit)
                },
            ) { targetScreen ->
                when (targetScreen) {
                Screen.Home -> HomeScreen(
                    plan = plan,
                    inProgressSession = inProgressSession,
                    sessions = sessions,
                    planCycle = planCycle,
                    showDeloadNotice = planCycle?.isDeloadSuggested == true && !isDeloadNoticeDismissed,
                    exerciseMedia = exerciseMedia,
                    onDismissDeloadNotice = { isDeloadNoticeDismissed = true },
                    onGenerate = viewModel::openGenerate,
                    onHistory = viewModel::openHistory,
                    onSettings = viewModel::openSettings,
                    onStartDay = viewModel::startWorkout,
                    onResume = viewModel::resumeWorkout,
                    onAbandonWorkout = viewModel::abandonWorkout,
                    onLogActivity = viewModel::openLogActivity,
                    onOpenMobility = viewModel::openMobility,
                    onPostponeReview = { planId -> viewModel.postponeReview(planId) },
                    plans = plans,
                    plansState = plansState,
                    onSelectPlan = viewModel::selectPlan,
                    onDeletePlan = viewModel::deleteExtraPlan,
                    // H3-30: o selo do plano local cita o motivo persistido do
                    // fallback até a próxima geração da IA.
                    fallbackReasonLabel = fallbackReasonLabelRes(lastFallbackReason)
                        ?.let { context.getString(it) },
                    // H3-40: card de geração pendente (em andamento / falhou).
                    pendingGeneration = pendingGeneration,
                    pendingGenerationStatus = pendingGenerationStatus,
                    onRefreshPending = { scope.launch { viewModel.refreshPendingGeneration() } },
                    onCancelPending = { scope.launch { viewModel.cancelGeneration() } },
                    onRetryPending = { viewModel.retryFromEnvelope() },
                    onDismissPending = { viewModel.dismissPendingGeneration() },
                    // H3-53: card de conclusão do treino na Home.
                    postWorkoutSummary = postWorkoutSummary,
                    onDismissPostWorkoutSummary = viewModel::dismissPostWorkoutSummary,
                )

                Screen.Generate -> GenerateScreen(
                    isGenerating = isGenerating,
                    onBack = viewModel::voltar,
                    savedBodyWeightKg = bodyWeightKg,
                    savedHeightCm = heightCm,
                    savedSex = sex,
                    savedConfig = generateConfig,
                    error = generateError,
                    onRetry = viewModel::retryGenerate,
                    onGenerate = { modalities, goal, daysPerWeek, level, equipment, muscles, sessionDuration, reviewWeeks, bodyWeightKgText, heightCmText, sexChoice, environment, kind, config ->
                        viewModel.submitGeneration(
                            modalities = modalities,
                            goal = goal,
                            daysPerWeek = daysPerWeek,
                            level = level,
                            equipment = equipment,
                            muscles = muscles,
                            sessionDuration = sessionDuration,
                            reviewWeeks = reviewWeeks,
                            bodyWeightKgText = bodyWeightKgText,
                            heightCmText = heightCmText,
                            sexChoice = sexChoice,
                            environment = environment,
                            kind = kind,
                            config = config,
                        )
                    },
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
                    restPaused = restPaused,
                    onPauseRest = viewModel::pauseRest,
                    onResumeRest = viewModel::resumeRest,
                    onExtendRest = { viewModel.extendRest() },
                    exerciseMedia = exerciseMedia,
                    progressionSuggestions = progressionSuggestions,
                    onOpenExerciseDetail = { viewModel.openExerciseDetail(it) },
                    onUpdateSet = viewModel::updateSet,
                    onDeleteSet = viewModel::deleteSet,
                    onAbandon = viewModel::abandonWorkout,
                    onNextExercise = viewModel::nextExercise,
                    availableEquipment = generateConfig?.equipment.orEmpty(),
                    onSwapExercise = { sessionExerciseId, replacementId, swapScope ->
                        scope.launch {
                            viewModel.swapSessionExercise(
                                sessionExerciseId = sessionExerciseId,
                                replacementExerciseId = replacementId,
                                scope = swapScope,
                                availableEquipment = generateConfig?.equipment.orEmpty(),
                            )
                        }
                    },
                    onLoadCatalog = { viewModel.loadExerciseMedia(false) },
                )

                Screen.RunWorkout -> RunWorkoutScreen(
                    state = activeSessionState,
                    // H3-31: o alvo do dia planejado aparece no topo da corrida.
                    planTarget = runPlanTarget,
                    onLeave = viewModel::leaveWorkout,
                    // H3-29: o sessionId vem da sessão renderizada — "Finalizar"
                    // sem sessão não existe mais como no-op silencioso.
                    onFinish = { sessionId, input ->
                        viewModel.finishRunWorkout(
                            sessionId = sessionId,
                            durationSeconds = input.durationSeconds,
                            // H3-31: o usuário digita km; a sessão grava metros.
                            distanceMeters = input.distanceKm?.let(::runDistanceKmToMeters),
                            rpe = input.rpe,
                        )
                    },
                )

                Screen.History -> HistoryScreen(
                    sessions = sessions,
                    sessionsState = sessionsState,
                    onBack = viewModel::voltar,
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
                    onBack = viewModel::voltar,
                    onModalityChanged = viewModel::onActivityModalityChanged,
                    onCopyLast = viewModel::copyLastActivity,
                    onSave = viewModel::saveActivity,
                    onDelete = { activityEditingId?.let(viewModel::deleteLogActivity) },
                )

                // H3-27: o planId/dayId da sessão ativa alimentam a mobilidade
                // planejada — finalizar fecha a sessão aberta em vez de criar
                // uma segunda sessão (fantasma eterno na Home).
                Screen.Mobility -> {
                    val mobilitySession = inProgressSession
                        ?.takeIf { it.session.modality == "mobility" }
                    MobilityScreen(
                        // H3-31: estado da rotina no ViewModel — rotação não
                        // devolve a tela para a lista nem perde o movimento.
                        state = mobilityState,
                        exerciseMedia = exerciseMedia,
                        onBack = viewModel::goHome,
                        onFinishMobility = viewModel::finishPlannedMobility,
                        onStartRoutine = viewModel::startMobilityRoutine,
                        onStartPreset = viewModel::startMobilityFromPreset,
                        onTogglePause = viewModel::toggleMobilityPause,
                        onPreviousMovement = viewModel::mobilityPreviousMovement,
                        onNextMovement = viewModel::mobilityNextMovement,
                        onClearSession = viewModel::clearMobilitySession,
                        planId = mobilitySession?.session?.planId ?: 0L,
                        dayId = mobilitySession?.session?.dayId ?: 0L,
                    )
                }

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
                    onBack = viewModel::voltar,
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
                    syncError = syncError,
                    onRetrySync = viewModel::syncNow,
                    downloadError = downloadError,
                    onRetryDownload = viewModel::downloadNow,
                    // H3-51: atualização in-app via GitHub Releases.
                    updateState = updateState,
                    onCheckUpdateOnOpen = viewModel::maybeCheckForUpdateOnOpen,
                    onCheckForUpdate = { viewModel.checkForUpdate(manual = true) },
                    onDownloadUpdate = viewModel::downloadAndInstallUpdate,
                    onClearUpdateMessage = viewModel::clearUpdateMessage,
                )
            }
            }
        }
    }

    // H3-24: mídia resolvida no ViewModel — catálogo primeiro, mídia do plano
    // (persistida no Room) como fallback quando o catálogo não tem o ID.
    val selectedMedia by viewModel.selectedMedia.collectAsStateWithLifecycle()
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
 * Tamanho informado pelo provedor do SAF. `null` também quando ele não sabe —
 * provedores de nuvem costumam não reportar, então o limite é aplicado pela
 * leitura limitada ([BackupSizeGuard.readLimited]).
 */
private fun documentSize(resolver: ContentResolver, uri: Uri): Long? =
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()

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
