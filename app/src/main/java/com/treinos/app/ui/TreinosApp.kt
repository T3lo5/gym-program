package com.treinos.app.ui

import android.animation.ValueAnimator
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
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
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.health.connect.client.HealthConnectClient
import com.treinos.app.data.backup.BackupSizeGuard
import com.treinos.app.data.backup.BackupValidator
import com.treinos.app.data.MeasurementPhotoStore
import com.treinos.app.data.SwapResult
import com.treinos.app.data.SwapScope
import com.treinos.app.data.local.WorkoutPlanWithDetails
import com.treinos.app.ui.screens.GenerateScreen
import com.treinos.app.ui.screens.HistoryScreen
import com.treinos.app.ui.screens.HomeScreen
import com.treinos.app.ui.screens.LogActivityScreen
import com.treinos.app.ui.screens.MeasurementsScreen
import com.treinos.app.ui.screens.MobilityScreen
import com.treinos.app.ui.screens.RunWorkoutScreen
import com.treinos.app.ui.screens.SettingsScreen
import com.treinos.app.ui.screens.WorkoutScreen
import com.treinos.app.ui.measurements.latestMeasurement
import com.treinos.app.ui.components.BottomNavigationBar
import com.treinos.app.ui.components.ExerciseDetailSheet
import com.treinos.app.ui.components.ExerciseHistorySheet
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import androidx.activity.result.PickVisualMediaRequest

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
    // Troca de exercício: equipamento do treino em andamento (união plano +
    // última geração), não o da última geração sozinho.
    val swapAvailableEquipment by viewModel.swapAvailableEquipment.collectAsStateWithLifecycle()
    val lastFallbackReason by viewModel.lastFallbackReason.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTesting.collectAsStateWithLifecycle()
    val backendUrl by viewModel.backendUrl.collectAsStateWithLifecycle()
    val useRemoteBackend by viewModel.useRemoteBackend.collectAsStateWithLifecycle()
    val remoteBackendUrl by viewModel.remoteBackendUrl.collectAsStateWithLifecycle()
    val apiToken by viewModel.apiToken.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val isLoadingDiagnostics by viewModel.isLoadingDiagnostics.collectAsStateWithLifecycle()
    val diagnosticsMessage by viewModel.diagnosticsMessage.collectAsStateWithLifecycle()
    val syncSpaceId by viewModel.syncSpaceId.collectAsStateWithLifecycle()
    val syncSecret by viewModel.syncSecret.collectAsStateWithLifecycle()
    val syncJoined by viewModel.syncJoined.collectAsStateWithLifecycle()
    val syncSpaceMessage by viewModel.syncSpaceMessage.collectAsStateWithLifecycle()
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
    // H4-7: exportar CSV e importar de Strong/Hevy.
    val csvImportState by viewModel.csvImportState.collectAsStateWithLifecycle()
    val csvMessage by viewModel.csvMessage.collectAsStateWithLifecycle()
    val bodyWeightKg by viewModel.bodyWeightKg.collectAsStateWithLifecycle()
    val heightCm by viewModel.heightCm.collectAsStateWithLifecycle()
    val sex by viewModel.sex.collectAsStateWithLifecycle()
    val exerciseMedia by viewModel.exerciseMedia.collectAsStateWithLifecycle()
    val selectedExerciseId by viewModel.selectedExerciseId.collectAsStateWithLifecycle()
    val isUpdatingCatalog by viewModel.isUpdatingCatalog.collectAsStateWithLifecycle()
    val catalogMessage by viewModel.catalogMessage.collectAsStateWithLifecycle()
    val activityPrefill by viewModel.activityPrefill.collectAsStateWithLifecycle()
    val progressionSuggestions by viewModel.progressionSuggestions.collectAsStateWithLifecycle()
    // H4-12: sugestão aplicável ao plano e carga-alvo já aplicada.
    val applicableProgressions by viewModel.applicableProgressions.collectAsStateWithLifecycle()
    val planTargetWeights by viewModel.planTargetWeights.collectAsStateWithLifecycle()
    val personalRecords by viewModel.personalRecords.collectAsStateWithLifecycle()
    // H4-6: plano do dia da sessão em andamento para os rótulos de superset.
    val activeSessionPlanExercises by viewModel.activeSessionPlanExercises.collectAsStateWithLifecycle()
    // H4-8: peso corporal e medidas ao longo do tempo.
    val measurements by viewModel.measurements.collectAsStateWithLifecycle()
    val measurementMessage by viewModel.measurementMessage.collectAsStateWithLifecycle()
    val pendingMeasurementPhoto by viewModel.pendingMeasurementPhoto.collectAsStateWithLifecycle()
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
    // H4-9: integração com o Health Connect (seção em Ajustes).
    val healthConnectState by viewModel.healthConnectState.collectAsStateWithLifecycle()
    // Permissões em tela (card da Home e seção em Ajustes).
    val permissionsState by viewModel.permissionsState.collectAsStateWithLifecycle()
    val healthConnectMessage by viewModel.healthConnectMessage.collectAsStateWithLifecycle()
    val watchSyncMessage by viewModel.watchSyncMessage.collectAsStateWithLifecycle()
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
            // H3-8: o estado de leitura liga ANTES de abrir o stream — um
            // provedor remoto (Drive/OneDrive/Dropbox) baixa o arquivo durante
            // openInputStream e a leitura pode demorar; sem isso a tela parece
            // travada até a validação começar.
            viewModel.beginBackupReading()
            try {
                val input = context.contentResolver.openInputStream(uri)
                if (input == null) {
                    viewModel.abortBackupReading()
                    viewModel.setBackupMessage(context.getString(com.treinos.app.R.string.backup_file_read_error))
                    return@launch
                }
                val json = input.use { BackupSizeGuard.readLimited(it) }
                if (json == null) {
                    viewModel.abortBackupReading()
                    viewModel.setBackupMessage(BackupSizeGuard.tooLargeMessage)
                    return@launch
                }
                viewModel.prepareImport(json)
            } catch (error: Exception) {
                // Provedor remoto falhando no meio da leitura (modo avião,
                // permissão revogada): mensagem de erro e nada muda nos dados.
                viewModel.abortBackupReading()
                viewModel.setBackupMessage(
                    uiErrorMessage(error, context, com.treinos.app.R.string.backup_file_read_error),
                )
            }
        }
    }

    // H4-7: exportação CSV — o texto é gerado antes de tocar no arquivo
    // escolhido; se a geração falhar, o arquivo do usuário continua intacto.
    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val csv = viewModel.exportSessionsCsv()
                val cacheFile = File(context.cacheDir, "treinos-export.csv")
                cacheFile.writeText(csv)
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error(context.getString(com.treinos.app.R.string.backup_file_open_error))
                output.use { out -> cacheFile.inputStream().use { it.copyTo(out) } }
                // H4-7 (regra 13): sem treino com série registrada, o CSV sai
                // só com o cabeçalho e a mensagem avisa em vez de celebrar.
                val hasExportableSets = sessions.any { details ->
                    details.exercises.any { exercise ->
                        exercise.sets.any { set -> set.isCompleted }
                    }
                }
                viewModel.setCsvMessage(
                    if (hasExportableSets) {
                        context.getString(com.treinos.app.R.string.msg_csv_exported)
                    } else {
                        context.getString(com.treinos.app.R.string.settings_csv_nothing_to_export)
                    },
                )
            } catch (error: Exception) {
                viewModel.setCsvMessage(
                    uiErrorMessage(error, context, com.treinos.app.R.string.csv_error_export),
                )
            }
        }
    }

    // H4-7: importação CSV (Strong/Hevy) — mesmo padrão do import de backup,
    // com o mesmo teto de leitura (BackupSizeGuard.readLimited, 25 MB).
    val csvImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val knownSize = documentSize(context.contentResolver, uri)
            if (knownSize != null) {
                val sizeCheck = BackupValidator.validateSize(knownSize)
                if (!sizeCheck.valid) {
                    viewModel.setCsvMessage(sizeCheck.message ?: BackupSizeGuard.tooLargeMessage)
                    return@launch
                }
            }
            val input = context.contentResolver.openInputStream(uri)
            if (input == null) {
                viewModel.setCsvMessage(context.getString(com.treinos.app.R.string.backup_file_read_error))
                return@launch
            }
            val text = input.use { BackupSizeGuard.readLimited(it) }
            if (text == null) {
                viewModel.setCsvMessage(BackupSizeGuard.tooLargeMessage)
                return@launch
            }
            viewModel.prepareCsvImport(text)
        }
    }

    // H4-8: foto de progresso — a câmera grava direto em
    // filesDir/progress-photos/ via FileProvider (o arquivo é o destino final);
    // a galeria chega como content:// e o ViewModel copia para a mesma pasta.
    val measurementGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri?.let(viewModel::attachMeasurementPhoto)
    }
    var measurementCameraTarget by remember { mutableStateOf<File?>(null) }
    val measurementCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved: Boolean ->
        val target = measurementCameraTarget
        measurementCameraTarget = null
        if (saved && target != null) {
            viewModel.attachMeasurementPhoto(Uri.fromFile(target))
        }
    }
    val launchMeasurementCamera = {
        val file = MeasurementPhotoStore.newPhotoFile(context)
        measurementCameraTarget = file
        measurementCameraLauncher.launch(
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
        )
    }
    val launchMeasurementGallery = {
        measurementGalleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
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

    // H3-57: predictive back — o sistema mostra a prévia animada do gesto e o
    // app só navega quando o gesto é confirmado; soltar no meio (cancelar)
    // lança CancellationException e a navegação não roda. API < 33 mantém o
    // back clássico: no activity-compose 1.8.2 o fluxo do PredictiveBackHandler
    // só completa com eventos de gesto preditivo.
    val onBackFromStack = {
        when (screen) {
            Screen.Workout -> viewModel.leaveWorkout()
            else -> viewModel.voltar()
        }
    }
    if (Build.VERSION.SDK_INT >= 33) {
        PredictiveBackHandler(enabled = screen != Screen.Home) { progress ->
            // O fluxo completa quando o gesto é confirmado; cancelar cancela a
            // coroutine e o bloco abaixo não executa.
            progress.collect { }
            onBackFromStack()
        }
    } else {
        BackHandler(enabled = screen != Screen.Home, onBack = onBackFromStack)
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
                    // H4-8: atalho "Peso e medidas" com o último peso, se houver.
                    onOpenMeasurements = viewModel::openMeasurements,
                    latestWeightKg = latestMeasurement(measurements)?.weightKg,
                    // Permissões em tela: pede na hora, sem adivinhar Configurações.
                    permissions = permissionsState,
                    healthAvailability = healthConnectState.availability,
                    healthPermissionsGranted = healthConnectState.permissions.hasAnyPermission,
                    onRequestNotifications = viewModel::requestNotificationsPermission,
                    onRequestHealthPermissions = viewModel::requestHealthPermissions,
                    onRequestInstallPackages = viewModel::requestInstallPackagesPermission,
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
                    // H4-10: programas prontos — com main ativo, aplicar pede
                    // confirmação dizendo qual plano será substituído.
                    hasMainPlan = plans.any { it.plan.kind == "main" },
                    mainPlanName = plans.firstOrNull { it.plan.kind == "main" }?.plan?.name,
                    onApplyProgram = viewModel::applyProgram,
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
                    onLogSet = { exercise, input ->
                        viewModel.logSet(exercise.exercise, input)
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
                    // H4-12: aplicar a sugestão ao plano com um toque.
                    applicableProgressions = applicableProgressions,
                    planTargetWeights = planTargetWeights,
                    onApplyProgression = viewModel::applyProgression,
                    personalRecords = personalRecords,
                    onOpenExerciseDetail = { viewModel.openExerciseDetail(it) },
                    onUpdateSet = viewModel::updateSet,
                    onDeleteSet = viewModel::deleteSet,
                    onAbandon = viewModel::abandonWorkout,
                    onNextExercise = viewModel::nextExercise,
                    availableEquipment = swapAvailableEquipment,
                    onSwapExercise = { sessionExerciseId, replacementId, swapScope ->
                        scope.launch {
                            viewModel.swapSessionExercise(
                                sessionExerciseId = sessionExerciseId,
                                replacementExerciseId = replacementId,
                                scope = swapScope,
                                availableEquipment = swapAvailableEquipment,
                            )
                        }
                    },
                    onLoadCatalog = { viewModel.loadExerciseMedia(false) },
                    // H4-6: agrupamento de superset e alternância no plano.
                    planExercises = activeSessionPlanExercises,
                    onToggleSuperset = viewModel::toggleSupersetWithPrevious,
                    // H4-11: notas da sessão e do exercício durante o treino.
                    onEditSessionNote = viewModel::updateSessionNotes,
                    onEditExerciseNote = viewModel::updateExerciseNotes,
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
                            routePolyline = input.routePolyline,
                            avgHeartRate = input.avgHeartRate,
                            maxHeartRate = input.maxHeartRate,
                        )
                    },
                )

                Screen.History -> HistoryScreen(
                    sessions = sessions,
                    sessionsState = sessionsState,
                    onBack = viewModel::voltar,
                    exerciseMedia = exerciseMedia,
                    onOpenExerciseDetail = { viewModel.openExerciseDetail(it) },
                    // H4-3: drill-down do histórico por exercício.
                    onOpenExerciseHistory = { viewModel.openExerciseHistory(it) },
                    onEditActivity = viewModel::editLogActivity,
                    onDeleteActivity = viewModel::deleteLogActivity,
                    muscleByExerciseId = historyMuscleByExerciseId,
                    onGenerate = viewModel::openGenerate,
                    // H3-54: corrigir séries de sessão concluída direto do Histórico.
                    onUpdateSet = { set, input -> viewModel.updateHistorySet(set, input) },
                    onDeleteSet = viewModel::deleteHistorySet,
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
                    useRemoteBackend = useRemoteBackend,
                    remoteBackendUrl = remoteBackendUrl,
                    apiToken = apiToken,
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
                    onSaveRemote = viewModel::saveRemoteBackend,
                    diagnostics = diagnostics,
                    isLoadingDiagnostics = isLoadingDiagnostics,
                    diagnosticsMessage = diagnosticsMessage,
                    onRefreshDiagnostics = viewModel::refreshDiagnostics,
                    syncSpaceId = syncSpaceId,
                    syncSecret = syncSecret,
                    syncJoined = syncJoined,
                    syncSpaceMessage = syncSpaceMessage,
                    onCreateSyncSpace = viewModel::createSyncSpace,
                    onJoinSyncSpace = viewModel::joinSyncSpace,
                    onRevokeSyncSpace = viewModel::revokeSyncSpace,
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
                    // H4-7: CSV — exportar "Treinos" e importar de Strong/Hevy.
                    csvImportState = csvImportState,
                    csvMessage = csvMessage,
                    onExportCsv = { csvExportLauncher.launch(csvFileName()) },
                    onImportCsvFileSelected = {
                        csvImportLauncher.launch(
                            arrayOf("text/*", "text/comma-separated-values", "application/csv"),
                        )
                    },
                    onConfirmCsvImport = { viewModel.confirmCsvImport() },
                    onCancelCsvImport = { viewModel.cancelCsvImport() },
                    onClearCsvMessage = { viewModel.clearCsvMessage() },
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
                    // H4-8: card "Peso e medidas" abrindo a tela.
                    onOpenMeasurements = viewModel::openMeasurements,
                    // H4-9: integração com o Health Connect.
                    healthConnectState = healthConnectState,
                    // Permissões em tela: status e pedido direto em Ajustes.
                    permissions = permissionsState,
                    onRequestNotifications = viewModel::requestNotificationsPermission,
                    onRequestInstallPackages = viewModel::requestInstallPackagesPermission,
                    // Correção do canal wear: sincronização manual com o relógio.
                    watchSyncMessage = watchSyncMessage,
                    onSyncWatch = viewModel::syncWearNow,
                    onClearWatchSyncMessage = viewModel::clearWatchSyncMessage,
                    healthConnectMessage = healthConnectMessage,
                    onRequestHealthPermissions = viewModel::requestHealthPermissions,
                    onSyncHealth = viewModel::syncHealthData,
                    onOpenHealthConnectSettings = {
                        runCatching {
                            context.startActivity(
                                Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS),
                            )
                        }
                    },
                    onOpenHealthConnectStore = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW).setData(Uri.parse(HEALTH_CONNECT_STORE_URI)),
                            )
                        }
                    },
                )

                // H4-8: peso corporal e medidas ao longo do tempo — gráfico da
                // H4-2, lista, formulário validado e foto que nunca sai do
                // aparelho.
                Screen.Measurements -> MeasurementsScreen(
                    measurements = measurements,
                    message = measurementMessage,
                    pendingPhotoPath = pendingMeasurementPhoto,
                    onBack = viewModel::voltar,
                    onSave = viewModel::saveMeasurement,
                    onDelete = viewModel::deleteMeasurement,
                    onRemovePhoto = viewModel::removePendingMeasurementPhoto,
                    onCopyLatestToProfile = viewModel::copyLatestWeightToProfile,
                    onOpenCamera = launchMeasurementCamera,
                    onOpenGallery = launchMeasurementGallery,
                    onClearMessage = viewModel::clearMeasurementMessage,
                )
            }
            }
        }
    }

    // H3-24: mídia resolvida no ViewModel — catálogo primeiro, mídia do plano
    // (persistida no Room) como fallback quando o catálogo não tem o ID.
    val selectedMedia by viewModel.selectedMedia.collectAsStateWithLifecycle()
    // H4-1: recorde do exercício aberto no detalhe.
    val selectedExerciseRecord by viewModel.selectedExerciseRecord.collectAsStateWithLifecycle()
    if (selectedExerciseId != null) {
        ExerciseDetailSheet(
            media = selectedMedia,
            record = selectedExerciseRecord,
            onDismiss = { viewModel.closeExerciseDetail() },
            // H4-3: "Ver histórico completo" fecha o detalhe e abre o drill-down.
            onOpenHistory = { viewModel.openExerciseHistory(it) },
        )
    }

    // H4-3: drill-down do histórico por exercício. `openExerciseHistory` fecha
    // o detalhe antes, então as duas folhas nunca ficam empilhadas.
    val exerciseHistoryId by viewModel.exerciseHistoryId.collectAsStateWithLifecycle()
    val exerciseHistory by viewModel.exerciseHistory.collectAsStateWithLifecycle()
    if (exerciseHistoryId != null) {
        ExerciseHistorySheet(
            history = exerciseHistory,
            onDismiss = { viewModel.closeExerciseHistory() },
        )
    }
}

private fun backupFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
    return "treinos-backup-$stamp.json"
}

/** H4-7: nome sugerido do CSV de treinos (`treinos-*.csv`). */
private fun csvFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
    return "treinos-$stamp.csv"
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

/**
 * H4-9: URI da loja para instalar/atualizar o app do Health Connect quando o
 * SDK reporta `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED` (regra 1).
 */
private const val HEALTH_CONNECT_STORE_URI =
    "market://details?id=com.google.android.apps.healthdata" +
        "&url=utm_source%3D%26utm_medium%3Dapp_link"
