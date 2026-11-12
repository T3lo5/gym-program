package com.treinos.app.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.treinos.app.data.WorkoutRepository
import com.treinos.app.data.SwapResult
import com.treinos.app.data.SwapScope
import com.treinos.app.data.swapCandidates
import com.treinos.app.data.sessionSwapCandidates
import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutPlanExerciseEntity
import com.treinos.app.data.local.WorkoutPlanWithDetails
import com.treinos.app.data.discovery.BackendConnector
import com.treinos.app.data.discovery.BackendDiscoveryManager
import com.treinos.app.data.discovery.BackendHealthChecker
import com.treinos.app.data.discovery.ConnectOutcome
import com.treinos.app.data.discovery.DiscoveredBackend
import com.treinos.app.data.discovery.HttpBackendHealthChecker
import com.treinos.app.data.discovery.dedupeBackends
import com.treinos.app.data.media.ExerciseMedia
import com.treinos.app.data.media.ExerciseMediaRepository
import com.treinos.app.data.media.resolveExerciseMedia
import com.treinos.app.data.prefs.AppPreferences
import com.treinos.app.data.SyncDownloadResult
import com.treinos.app.data.remote.GenerateJobAck
import com.treinos.app.data.remote.GenerateJobStatus
import com.treinos.app.data.remote.GeneratePlanRequest
import com.treinos.app.data.remote.GeneratePlanResponse
import com.treinos.app.data.remote.ModalityDto
import com.treinos.app.data.remote.ReviewPlanRequest
import com.treinos.app.data.remote.ReviewPlanResponse
import com.treinos.app.data.remote.ReviewPlanHistory
import com.treinos.app.data.remote.ReviewPlanSession
import com.treinos.app.data.remote.WorkoutDayDto
import com.treinos.app.data.remote.WorkoutExerciseDto
import com.treinos.app.data.remote.WorkoutPlanDto
import com.treinos.app.data.backup.BackupFile
import com.treinos.app.data.backup.BackupPreview
import com.treinos.app.data.backup.BackupValidator
import com.treinos.app.data.backup.backupImportDecoder
import com.treinos.app.data.wear.WearSessionMapper
import com.treinos.app.data.wear.WearSyncRepository
import com.treinos.app.ui.ExerciseSessionData
import com.treinos.app.ui.PendingGeneration
import com.treinos.app.ui.PendingGenerationCodec
import com.treinos.app.ui.ProfileValidator
import com.treinos.app.ui.SetRecord
import com.treinos.app.ui.calculateProgress
import com.treinos.app.ui.nextIncompleteExercise
import com.treinos.app.ui.progression.ProgressionContext
import com.treinos.app.ui.progression.ProgressionSuggestion
import com.treinos.app.ui.progression.REVIEW_POSTPONE_WEEKS
import com.treinos.app.ui.progression.completedSessionsSincePlan
import com.treinos.app.ui.progression.cycleWeek
import com.treinos.app.ui.progression.deloadSuggested
import com.treinos.app.ui.progression.progressionHistory
import com.treinos.app.ui.progression.suggestProgression
import com.treinos.app.ui.progressText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import java.time.Instant
import java.time.temporal.ChronoUnit
import com.treinos.protocol.WearCommand
import com.treinos.protocol.WearCommandKind
import com.treinos.protocol.WearWorkoutState
import com.treinos.app.R

enum class Screen {
    Home,
    Generate,
    Workout,
    RunWorkout,
    History,
    Settings,
    LogActivity,
    Mobility,
}

/**
 * Rótulo em português do motivo do fallback local (H3-21).
 *
 * Os códigos são os valores fechados do contrato (`fallbackReason`):
 * `no_api_key`, `http_error`, `timeout`, `json_parse`, `unresolved_ids`,
 * `schema_validation` e `unknown`. Retorna null quando não há motivo —
 * plano veio da IA (`source == "ai"`) ou o backend é antigo sem o campo.
 */
fun fallbackReasonLabel(reason: String?): String? = when (reason) {
    null, "" -> null
    "no_api_key" -> "chave da IA não configurada"
    "http_error" -> "a API de IA respondeu com erro"
    "timeout" -> "tempo limite ou erro de rede na IA"
    "json_parse" -> "resposta da IA sem JSON válido"
    "unresolved_ids" -> "resposta da IA sem IDs válidos"
    "schema_validation" -> "resposta da IA fora do formato esperado"
    else -> "motivo desconhecido ($reason)"
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(
    private val context: Context,
    private val repository: WorkoutRepository,
    private val preferences: AppPreferences,
    private val exerciseMediaRepository: ExerciseMediaRepository? = null,
    private val discoveryManager: BackendDiscoveryManager? = null,
    private val healthChecker: BackendHealthChecker = HttpBackendHealthChecker(),
    private val wearSyncRepository: WearSyncRepository? = null,
    private val restFeedback: RestFeedbackPlayer? = null,
) : ViewModel() {

    // H3-35: atalho dinâmico "Retomar treino".
    private fun publishResumeShortcut(session: com.treinos.app.data.local.SessionWithExercises?) {
        if (session != null) {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(EXTRA_RESUME_SESSION, true)
            }
            val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID_RESUME)
                .setShortLabel("Retomar treino")
                .setLongLabel("Retomar o treino em andamento")
                .setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(
                    context, com.treinos.app.R.mipmap.ic_launcher))
                .setIntent(intent)
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        } else {
            ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(SHORTCUT_ID_RESUME))
        }
    }

    private val backendConnector = BackendConnector(healthChecker, preferences)

    private val _screen = MutableStateFlow(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _backTarget = MutableStateFlow<Screen?>(null)
    val backTarget: StateFlow<Screen?> = _backTarget.asStateFlow()

    private val _discoveredBackends = MutableStateFlow<List<DiscoveredBackend>>(emptyList())
    val discoveredBackends: StateFlow<List<DiscoveredBackend>> = _discoveredBackends.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _isConnectingBackend = MutableStateFlow(false)
    val isConnectingBackend: StateFlow<Boolean> = _isConnectingBackend.asStateFlow()

    private val _discoveryMessage = MutableStateFlow<String?>(null)
    val discoveryMessage: StateFlow<String?> = _discoveryMessage.asStateFlow()

    val activePlans: StateFlow<List<WorkoutPlanWithDetails>> = repository.observeActivePlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * H3-29: estado de tela dos planos. `Loading` até o **primeiro** emit do
     * Room — a Home só mostra o empty state quando o banco realmente está
     * vazio, não nos primeiros frames ("vazio falso").
     */
    val plansState: StateFlow<UiState<List<WorkoutPlanWithDetails>>> = repository.observeActivePlans()
        .map<List<WorkoutPlanWithDetails>, UiState<List<WorkoutPlanWithDetails>>> { UiState.Content(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private val _selectedPlanId = MutableStateFlow<Long?>(null)
    val selectedPlanId: StateFlow<Long?> = _selectedPlanId.asStateFlow()

    /** Plano selecionado na Home; consumidores existentes seguem lendo `activePlan`. */
    val activePlan: StateFlow<WorkoutPlanWithDetails?> = combine(
        activePlans,
        _selectedPlanId,
    ) { plans, selectedId ->
        resolveSelectedPlan(plans, selectedId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectPlan(planId: Long) {
        _selectedPlanId.value = planId
    }

    fun deleteExtraPlan(planId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteExtraPlan(planId)
                if (_selectedPlanId.value == planId) {
                    _selectedPlanId.value = null
                }
                _message.value = "Plano complementar excluído. O histórico foi preservado."
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, "Não foi possível excluir o plano.")
            }
        }
    }

    /** A revisão por IA continua apontando para o plano principal (H3-15). */
    private fun mainPlan(): WorkoutPlanWithDetails? =
        activePlans.value.firstOrNull { it.plan.kind == "main" }

    val sessions: StateFlow<List<SessionWithExercises>> = repository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** H3-29: mesmo padrão de [plansState] — Loading até o primeiro emit do Room. */
    val sessionsState: StateFlow<UiState<List<SessionWithExercises>>> = repository.observeSessions()
        .map<List<SessionWithExercises>, UiState<List<SessionWithExercises>>> { UiState.Content(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    val inProgressSession: StateFlow<SessionWithExercises?> = repository.observeSessions()
        .map { list -> list.firstOrNull { it.session.status == "in_progress" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _activeSessionId = MutableStateFlow<Long?>(null)

    val activeSession: StateFlow<SessionWithExercises?> = _activeSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else repository.observeSession(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * H3-29: sessão ativa como estado de tela. A cada troca de `_activeSessionId`
     * o estado volta a `Loading` até o Room emitir — a corrida nunca mostra
     * "Sessão não encontrada" nos frames antes da emissão.
     */
    val activeSessionState: StateFlow<UiState<SessionWithExercises?>> = _activeSessionId
        .flatMapLatest { id ->
            val source = if (id == null) flowOf<SessionWithExercises?>(null) else repository.observeSession(id)
            source.map<SessionWithExercises?, UiState<SessionWithExercises?>> { UiState.Content(it) }
                .onStart { emit(UiState.Loading) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    /**
     * H3-31: alvo do dia planejado para a corrida em andamento (nome do bloco,
     * duração e distância quando existirem no plano). `null` sem plano casado —
     * a tela mostra o cartão genérico.
     */
    val runPlanTarget: StateFlow<RunPlanTarget?> = combine(
        activeSession,
        activePlan,
    ) { session, plan ->
        session?.let { runPlanTargetFor(it, plan) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _exerciseIndex = MutableStateFlow(0)
    val exerciseIndex: StateFlow<Int> = _exerciseIndex.asStateFlow()

    /**
     * Sugestão de carga por `exerciseId` do plano ativo, derivada apenas do
     * histórico local. O usuário sempre pode digitar outro valor.
     */
    val progressionSuggestions: StateFlow<Map<String, ProgressionSuggestion>> = combine(
        activePlan,
        sessions,
    ) { plan, sessionList ->
        val exercises = plan?.days?.flatMap { it.exercises }.orEmpty()
        val nowMillis = System.currentTimeMillis()
        exercises.associate { exercise ->
            exercise.exerciseId to suggestProgression(
                ProgressionContext(
                    exerciseId = exercise.exerciseId,
                    targetReps = exercise.targetReps,
                    equipment = exercise.equipment,
                    primaryMuscles = exercise.primaryMuscles,
                ),
                progressionHistory(sessionList, exercise.exerciseId),
                nowMillis,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val planCycle: StateFlow<PlanCycle?> = combine(activePlan, sessions) { plan, sessionList ->
        val entity = plan?.plan
        if (entity == null) {
            null
        } else {
            val nowMillis = System.currentTimeMillis()
            PlanCycle(
                week = cycleWeek(entity.createdAt, nowMillis),
                isDeloadSuggested = deloadSuggested(
                    planCreatedAtMillis = entity.createdAt,
                    nowMillis = nowMillis,
                    completedSessionsSincePlan = completedSessionsSincePlan(sessionList, entity.createdAt),
                ),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _exercises: StateFlow<List<ExerciseSessionData>> = repository.observeSessions()
        .map { sessions ->
            val inProgress = sessions.firstOrNull { it.session.status == "in_progress" }
            inProgress?.exercises?.map { ex ->
                ExerciseSessionData(
                    sessionExerciseId = ex.exercise.id,
                    exerciseId = ex.exercise.exerciseId,
                    exerciseName = ex.exercise.exerciseName.ifBlank { ex.exercise.exerciseId },
                    plannedSets = ex.exercise.plannedSets,
                    completedSets = ex.exercise.completedSets,
                    sets = ex.sets.map { SetRecord(it.setNumber, it.reps ?: 0, it.weightKg) },
                    restSeconds = ex.exercise.restSeconds,
                )
            } ?: emptyList()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentExercise: StateFlow<ExerciseSessionData?> = combine(
        _exerciseIndex,
        _exercises,
    ) { index, exercises ->
        exercises.getOrNull(index)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val totalExercises: StateFlow<Int> = _exercises
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * H3-29: erro persistente da geração — vive na GenerateScreen com
     * "Tentar de novo" até o usuário repetir a ação ou voltar.
     */
    private val _generateError = MutableStateFlow<UiError?>(null)
    val generateError: StateFlow<UiError?> = _generateError.asStateFlow()

    /** Parâmetros da última geração, para o "Tentar de novo" reexecutá-la igual. */
    private var lastGenerateArgs: GeneratePlanArgs? = null

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    /** Job da geração em curso (H3-30, mantido para compatibilidade). */
    private var generateJob: Job? = null

    /**
     * H3-30: motivo do último fallback local, persistido em
     * `last_fallback_reason`. Vazio = o último plano veio da IA. O selo do
     * plano na Home cita este motivo até a próxima geração da IA.
     */
    private val _lastFallbackReason = MutableStateFlow(preferences.lastFallbackReason)
    val lastFallbackReason: StateFlow<String> = _lastFallbackReason.asStateFlow()

    /**
     * H3-40: envelope da geração em andamento (jobId, kind, request, config).
     * Sobrevive a processo morto; restaurado no init. Nulo quando não há job.
     */
    private val _pendingGeneration = MutableStateFlow<PendingGeneration?>(null)
    val pendingGeneration: StateFlow<PendingGeneration?> = _pendingGeneration.asStateFlow()

    /** Dedupe de polls sobrepostos (H3-40): só um refresh de cada vez. */
    private val _isPolling = MutableStateFlow(false)
    val isPolling: StateFlow<Boolean> = _isPolling.asStateFlow()

    /** Último status conhecido do job do envelope (H3-40): pending/running/done/failed/cancelled. */
    private val _pendingGenerationStatus = MutableStateFlow<String?>(null)
    val pendingGenerationStatus: StateFlow<String?> = _pendingGenerationStatus.asStateFlow()

    /**
     * H3-30: última configuração da tela "Gerar", restaurada na abertura.
     * null = nenhuma config salva (a tela usa os defaults).
     */
    private val _generateConfig = MutableStateFlow(
        GenerateConfigCodec.decode(preferences.generateConfigJson)
    )
    val generateConfig: StateFlow<GenerateConfig?> = _generateConfig.asStateFlow()

    private val _isReviewing = MutableStateFlow(false)
    val isReviewing: StateFlow<Boolean> = _isReviewing.asStateFlow()

    private val _reviewMessage = MutableStateFlow<String?>(null)
    val reviewMessage: StateFlow<String?> = _reviewMessage.asStateFlow()

    private val _reviewChanges = MutableStateFlow<List<String>>(emptyList())
    val reviewChanges: StateFlow<List<String>> = _reviewChanges.asStateFlow()

    /** Proposta de revisão aguardando confirmação do usuário. */
    private var pendingReview: ReviewPlanResponse? = null

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    private val _restoreMessage = MutableStateFlow<String?>(null)
    val restoreMessage: StateFlow<String?> = _restoreMessage.asStateFlow()

    private val _restoreDeviceId = MutableStateFlow("")
    val restoreDeviceId: StateFlow<String> = _restoreDeviceId.asStateFlow()

    private val _downloadMessage = MutableStateFlow<String?>(null)
    val downloadMessage: StateFlow<String?> = _downloadMessage.asStateFlow()

    /** H3-29: erro persistente do download do backend, com "Tentar de novo" em Ajustes. */
    private val _downloadError = MutableStateFlow<UiError?>(null)
    val downloadError: StateFlow<UiError?> = _downloadError.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _backendUrl = MutableStateFlow(preferences.backendUrl)
    val backendUrl: StateFlow<String> = _backendUrl.asStateFlow()

    private val _connectionMessage = MutableStateFlow<String?>(null)
    val connectionMessage: StateFlow<String?> = _connectionMessage.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    /** H3-29: erro persistente do envio ao backend, com "Tentar de novo" em Ajustes. */
    private val _syncError = MutableStateFlow<UiError?>(null)
    val syncError: StateFlow<UiError?> = _syncError.asStateFlow()

    private val _exerciseMedia = MutableStateFlow<Map<String, ExerciseMedia>>(emptyMap())
    val exerciseMedia: StateFlow<Map<String, ExerciseMedia>> = _exerciseMedia.asStateFlow()

    private val _selectedExerciseId = MutableStateFlow<String?>(null)
    val selectedExerciseId: StateFlow<String?> = _selectedExerciseId.asStateFlow()

    /**
     * H3-24: mídia embutida no plano para o exercício aberto no detalhe.
     * Capturada na abertura a partir dos planos ativos; é o fallback quando o
     * catálogo não tem o ID (ou não foi baixado).
     */
    private val _selectedPlanExercise = MutableStateFlow<WorkoutPlanExerciseEntity?>(null)

    /**
     * Mídia resolvida do detalhe aberto: catálogo primeiro, mídia do plano
     * como fallback, null quando nenhuma das duas tem algo a mostrar.
     */
    val selectedMedia: StateFlow<ExerciseMedia?> = combine(
        _exerciseMedia,
        _selectedExerciseId,
        _selectedPlanExercise,
    ) { catalog, exerciseId, planExercise ->
        if (exerciseId == null) return@combine null
        resolveExerciseMedia(
            catalog = catalog,
            exerciseId = exerciseId,
            planName = planExercise?.exerciseName.orEmpty(),
            planPrimaryMuscles = planExercise?.primaryMuscles
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty(),
            planEquipment = planExercise?.equipment.orEmpty(),
            planInstructions = planExercise?.instructions.orEmpty(),
            planImages = planExercise?.images.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _isUpdatingCatalog = MutableStateFlow(false)
    val isUpdatingCatalog: StateFlow<Boolean> = _isUpdatingCatalog.asStateFlow()

    private val _catalogMessage = MutableStateFlow<String?>(null)
    val catalogMessage: StateFlow<String?> = _catalogMessage.asStateFlow()

    private val _restRemaining = MutableStateFlow<Int?>(null)
    val restRemaining: StateFlow<Int?> = _restRemaining.asStateFlow()

    private val _restTotal = MutableStateFlow<Int?>(null)
    val restTotal: StateFlow<Int?> = _restTotal.asStateFlow()

    /** `true` enquanto o descanso está pausado (H3-26); a contagem fica congelada. */
    private val _restPaused = MutableStateFlow(false)
    val restPaused: StateFlow<Boolean> = _restPaused.asStateFlow()

    /** Segundos congelados pela pausa; usado pela retomada para recalcular o deadline. */
    private var pausedRemainingSeconds = 0

    /**
     * Job do tick de UI do descanso. Declarado junto do estado do descanso e
     * **antes** do `init`: a restauração do deadline persistido roda no init e
     * preenche este handle — se fosse declarado depois, o inicializador
     * `= null` sobrescreveria o job restaurado.
     */
    private var restJob: Job? = null

    private val _lastSyncAt = MutableStateFlow(preferences.lastSyncAt)
    val lastSyncAt: StateFlow<String?> = _lastSyncAt.asStateFlow()

    private val _backupImportState = MutableStateFlow<BackupImportState>(BackupImportState.Idle)
    val backupImportState: StateFlow<BackupImportState> = _backupImportState.asStateFlow()

    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage.asStateFlow()

    private val _bodyWeightKg = MutableStateFlow(preferences.bodyWeightKg)
    val bodyWeightKg: StateFlow<Double?> = _bodyWeightKg.asStateFlow()

    private val _heightCm = MutableStateFlow(preferences.heightCm)
    val heightCm: StateFlow<Int?> = _heightCm.asStateFlow()

    private val _sex = MutableStateFlow(preferences.sex)
    val sex: StateFlow<String?> = _sex.asStateFlow()

    private val _wearState = MutableStateFlow<WearWorkoutState?>(null)
    val wearState: StateFlow<WearWorkoutState?> = _wearState.asStateFlow()

    private val _activityPrefill = MutableStateFlow<ActivityLogInput?>(null)
    val activityPrefill: StateFlow<ActivityLogInput?> = _activityPrefill.asStateFlow()

    private val _activityEditingId = MutableStateFlow<Long?>(null)
    val activityEditingId: StateFlow<Long?> = _activityEditingId.asStateFlow()

    private val _activityErrors = MutableStateFlow<Map<ActivityLogField, String>>(emptyMap())
    val activityErrors: StateFlow<Map<ActivityLogField, String>> = _activityErrors.asStateFlow()

    private val _isSavingActivity = MutableStateFlow(false)
    val isSavingActivity: StateFlow<Boolean> = _isSavingActivity.asStateFlow()

    private val _canCopyLastActivity = MutableStateFlow(false)
    val canCopyLastActivity: StateFlow<Boolean> = _canCopyLastActivity.asStateFlow()

    private var pendingBackupPayload: BackupFile? = null

    init {
        viewModelScope.launch {
            // H3-44: publica o catálogo local (cache ou asset embarcado) antes
            // de qualquer rede. O diálogo de troca nunca espera o backend.
            exerciseMediaRepository?.let { repo ->
                _exerciseMedia.value = repo.loadLocal()
            }
            loadExerciseMedia(false)
        }
        restorePersistedRest()
        // H3-40: restaura o envelope persistido (sobrevive a processo morto).
        preferences.pendingGenerationV1?.let { raw ->
            PendingGenerationCodec.decode(raw)?.let { envelope ->
                _pendingGeneration.value = envelope
            }
        }
        if (wearSyncRepository != null) {
            viewModelScope.launch {
                wearSyncRepository!!.commands.collect { command ->
                    handleWearCommand(command)
                }
            }
            viewModelScope.launch {
                combine(inProgressSession, _exerciseIndex) { session, index ->
                    session to index
                }.collect { (session, index) ->
                    if (session != null) {
                        publishWearState(session, index)
                    } else {
                        _wearState.value = null
                    }
                }
            }
        }
        // H3-35: atalho dinâmico "Retomar treino" quando há sessão em andamento.
        viewModelScope.launch {
            inProgressSession.collect { session ->
                publishResumeShortcut(session)
            }
        }
    }

    private fun handleWearCommand(command: WearCommand) {
        if (command.protocolVersion != 1) return
        val activeSession = inProgressSession.value
        if (activeSession == null) return
        if (command.sessionId != activeSession.session.id && command.kind != WearCommandKind.REQUEST_STATE) {
            // Comando de sessão diferente: reenviar estado atual
            publishWearState(activeSession, _exerciseIndex.value)
            return
        }
        when (command.kind) {
            WearCommandKind.REQUEST_STATE -> publishWearState(activeSession, _exerciseIndex.value)
            WearCommandKind.LOG_SET -> {
                val exercicio = activeSession.exercises
                    .firstOrNull { it.exercise.id == _exercises.value.getOrNull(_exerciseIndex.value)?.sessionExerciseId }
                    ?: activeSession.exercises.firstOrNull()
                if (exercicio != null) {
                    val ultimoSet = exercicio.sets.lastOrNull()
                    logSet(
                        exercise = exercicio.exercise,
                        reps = command.reps ?: ultimoSet?.reps,
                        weightKg = command.weightKg ?: ultimoSet?.weightKg,
                        rpe = command.rpe,
                    )
                }
            }
            WearCommandKind.SKIP_REST -> skipRest()
            WearCommandKind.FINISH_WORKOUT -> finishWorkout()
        }
    }

    private fun publishWearState(session: com.treinos.app.data.local.SessionWithExercises, exerciseIndex: Int) {
        val rest = _restRemaining.value
        _wearState.value = WearSessionMapper.toWearState(session, exerciseIndex, rest)
    }

    private fun syncWearState() {
        if (wearSyncRepository == null) return
        val session = inProgressSession.value
        if (session != null) {
            publishWearState(session, _exerciseIndex.value)
        } else {
            _wearState.value = null
        }
    }

    fun loadExerciseMedia(forceRefresh: Boolean) {
        val repo = exerciseMediaRepository ?: return
        if (forceRefresh && _isUpdatingCatalog.value) return
        viewModelScope.launch {
            if (forceRefresh) {
                _isUpdatingCatalog.value = true
            }
            try {
                val map = repo.load(forceRefresh)
                _exerciseMedia.value = map
                if (forceRefresh) {
                    val now = java.text.SimpleDateFormat(
                        "dd/MM/yyyy HH:mm",
                        java.util.Locale.getDefault(),
                    ).format(java.util.Date())
                    _catalogMessage.value = "Catálogo atualizado em $now"
                }
            } catch (error: Exception) {
                if (forceRefresh) {
                    _catalogMessage.value = uiErrorMessage(error, "Não foi possível atualizar o catálogo.")
                }
            } finally {
                if (forceRefresh) {
                    _isUpdatingCatalog.value = false
                }
            }
        }
    }

    fun updateCatalog() {
        loadExerciseMedia(true)
    }

    fun clearCatalogMessage() {
        _catalogMessage.value = null
    }

    fun openExerciseDetail(exerciseId: String?) {
        if (exerciseId.isNullOrBlank()) return
        // H3-24: captura a mídia embutida no plano (fallback do catálogo) no
        // momento da abertura; sem catálogo, o detalhe ainda tem imagem e passos.
        _selectedPlanExercise.value = planExerciseById(exerciseId)
        _selectedExerciseId.value = exerciseId
    }

    fun closeExerciseDetail() {
        _selectedExerciseId.value = null
        _selectedPlanExercise.value = null
    }

    /**
     * Troca um exercício de um plano salvo (H3-41). Usa o catálogo já carregado
     * em [`exerciseMedia`]; `availableEquipment` vem do caller (config do
     * usuário) para validar equipamento permitido.
     */
    suspend fun swapExercise(
        dayId: Long,
        position: Int,
        replacementExerciseId: String,
        availableEquipment: List<String>,
    ): SwapResult = repository.swapExercise(
        dayId = dayId,
        position = position,
        replacementExerciseId = replacementExerciseId,
        catalog = _exerciseMedia.value,
        availableEquipment = availableEquipment,
    )

    /** Candidatos válidos para trocar o exercício [original] no mesmo dia. */
    fun swapCandidates(
        original: WorkoutPlanExerciseEntity,
        dayExercises: List<WorkoutPlanExerciseEntity>,
        availableEquipment: List<String>,
    ): List<ExerciseMedia> =
        swapCandidates(
            original = original,
            dayExercises = dayExercises,
            catalog = _exerciseMedia.value,
            availableEquipment = availableEquipment,
        )

    /**
     * Troca o exercício de uma sessão em andamento. `scope` define se vale
     * só este treino (`THIS_SESSION`) ou todos os treinos futuros
     * (`ALL_FUTURE`, atualiza o plano salvo). `availableEquipment` vem do
     * caller (config do usuário) para validar equipamento permitido.
     */
    suspend fun swapSessionExercise(
        sessionExerciseId: Long,
        replacementExerciseId: String,
        scope: SwapScope,
        availableEquipment: List<String>,
    ): SwapResult = repository.swapSessionExercise(
        sessionExerciseId = sessionExerciseId,
        replacementExerciseId = replacementExerciseId,
        scope = scope,
        catalog = _exerciseMedia.value,
        availableEquipment = availableEquipment,
    )

    /** Candidatos válidos para trocar o exercício de uma sessão em andamento. */
    fun sessionSwapCandidates(
        sessionExercise: SessionExerciseEntity,
        takenExerciseIds: List<String>,
        availableEquipment: List<String>,
    ): List<ExerciseMedia> =
        sessionSwapCandidates(
            sessionExercise = sessionExercise,
            takenExerciseIds = takenExerciseIds,
            catalog = _exerciseMedia.value,
            availableEquipment = availableEquipment,
        )

    /** Registra uso de um exercício (concluído/pulado/trocado) para calibrar a próxima geração (H3-42). */
    suspend fun recordExerciseUsage(
        exerciseId: String,
        outcome: String,
        planId: Long? = null,
        dayId: Long? = null,
        source: String = "generation",
    ) = repository.recordExerciseUsage(
        exerciseId = exerciseId,
        outcome = outcome,
        planId = planId,
        dayId = dayId,
        source = source,
    )

    /** Peso de uso de cada exercício do catálogo (últimos 90 dias), para ordenar o prompt da próxima geração. */
    suspend fun usageWeightsForCatalog(
        exerciseIds: List<String>,
    ): Map<String, Int> = repository.usageWeightsForCatalog(exerciseIds)

    private fun planExerciseById(exerciseId: String): WorkoutPlanExerciseEntity? =
        activePlans.value
            .flatMap { it.days }
            .flatMap { it.exercises }
            .firstOrNull { it.exerciseId == exerciseId }

    private val _deviceId = MutableStateFlow(preferences.deviceId)
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    private val deviceRestore = RestoreByDeviceId(
        downloader = repository,
        devices = preferences,
    )

    // ---------------- Navegação ----------------

    fun openGenerate() {
        // H3-29: entrar na tela é uma nova tentativa — erro antigo não volta.
        _generateError.value = null
        _backTarget.value = _screen.value
        _screen.value = Screen.Generate
    }

    fun openHistory() {
        _backTarget.value = _screen.value
        _screen.value = Screen.History
    }

    fun openSettings() {
        _backTarget.value = _screen.value
        _screen.value = Screen.Settings
    }

    fun openMobility() {
        _backTarget.value = _screen.value
        _screen.value = Screen.Mobility
    }

    fun goHome() {
        _screen.value = Screen.Home
    }

    /** H3-38: volta para a origem da navegação (ou Home como raiz padrão). */
    fun voltar() {
        val destino = _backTarget.value ?: Screen.Home
        _backTarget.value = null
        _screen.value = destino
    }

    fun clearMessage() {
        _message.value = null
    }

    // ---------------- Geração assíncrona (H3-40) ----------------

    /**
     * Submete a geração ao backend (202 imediato), persiste o envelope e vai
     * para a Home. Não salva o plano — ele só é salvo quando o job conclui
     * (`refreshPendingGeneration`). O submit é rápido; nenhum caminho de rede
     * espera a IA.
     */
    fun submitGeneration(
        modalities: List<ModalityDto>,
        goal: String,
        daysPerWeek: Int,
        level: String,
        equipment: List<String>,
        muscles: List<String>,
        sessionDuration: Int,
        reviewWeeks: Int,
        bodyWeightKgText: String,
        heightCmText: String,
        sexChoice: String?,
        environment: String? = null,
        kind: String = "main",
        config: GenerateConfig? = null,
    ) {
        if (_isGenerating.value) return
        _generateError.value = null

        val isComplementary = kind == "extra"
        val focusMuscles = if (isComplementary && muscles.isNotEmpty()) muscles else null
        val effectiveModalities = if (isComplementary) {
            listOf(
                ModalityDto(
                    type = "strength",
                    sessionsPerWeek = daysPerWeek.coerceIn(1, 6),
                    goal = goal,
                    level = level,
                    availableEquipment = equipment,
                    targetMuscles = emptyList(),
                    sessionDurationMinutes = sessionDuration,
                )
            )
        } else {
            modalities
        }

        viewModelScope.launch {
            _isGenerating.value = true
            try {
                val validation = ProfileValidator.validate(bodyWeightKgText, heightCmText, sexChoice)
                if (!validation.valid) {
                    _message.value = validation.error ?: "Perfil inválido."
                    return@launch
                }
                // Persist profile quando pelo menos um campo estiver preenchido.
                if (validation.bodyWeightKg != null) {
                    preferences.bodyWeightKg = validation.bodyWeightKg
                    _bodyWeightKg.value = preferences.bodyWeightKg
                }
                if (validation.heightCm != null) {
                    preferences.heightCm = validation.heightCm
                    _heightCm.value = preferences.heightCm
                }
                preferences.sex = validation.sex
                _sex.value = preferences.sex

                if (isComplementary) {
                    val complementary = ComplementaryValidator.validate(
                        focusMuscles = focusMuscles,
                        daysPerWeek = daysPerWeek,
                        sessionDurationMinutes = sessionDuration,
                        modalityTypes = effectiveModalities.map { it.type },
                    )
                    if (!complementary.valid) {
                        _message.value = complementary.error ?: "Plano complementar inválido."
                        return@launch
                    }
                }

                val request = GeneratePlanRequest(
                    deviceId = preferences.deviceId,
                    goal = goal,
                    daysPerWeek = daysPerWeek,
                    availableEquipment = equipment,
                    targetMuscles = if (isComplementary) emptyList() else muscles,
                    currentLevel = level,
                    sessionDurationMinutes = sessionDuration,
                    reviewAfterWeeks = reviewWeeks,
                    bodyWeightKg = validation.bodyWeightKg,
                    heightCm = validation.heightCm,
                    sex = validation.sex,
                    modalities = effectiveModalities,
                    environment = environment,
                    focusMuscles = focusMuscles,
                    language = preferences.language,
                )

                // Persistir config ANTES do submit: se o processo morrer, o
                // retry reenvia o request do envelope (com a config do form).
                config?.let { cfg ->
                    preferences.generateConfigJson = GenerateConfigCodec.encode(cfg)
                    _generateConfig.value = cfg
                }

                val ack = repository.submitGeneration(request)

                // Envelope: sobrevive a processo morto.
                val envelope = PendingGeneration(
                    jobId = ack.jobId,
                    kind = kind,
                    request = request,
                    config = config,
                )
                preferences.pendingGenerationV1 = PendingGenerationCodec.encode(envelope)
                _pendingGeneration.value = envelope

                _screen.value = Screen.Home
                _message.value = "Treino sendo preparado. Toque em Atualizar quando voltar."
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Falha ao submeter geração", error)
                _generateError.value = uiErrorFrom(error, "gerar o treino")
            } finally {
                _isGenerating.value = false
            }
        }
    }

    /**
     * Consulta o status do job em andamento e, quando concluído, salva o plano,
     * limpa o envelope e mostra a mensagem. Quando falhou, mantém o envelope
     * para o retry. Só um poll de cada vez (dedupe).
     */
    suspend fun refreshPendingGeneration() {
        if (_isPolling.value) return
        val envelope = _pendingGeneration.value ?: return
        _isPolling.value = true
        try {
            val status = repository.generationStatus(envelope.jobId)
            _pendingGenerationStatus.value = status.status
            when (status.status) {
                "done" -> {
                    status.result?.let { result ->
                        try {
                            val planId = repository.completeGeneration(status, envelope.kind)
                            // H3-45: um main recém-gerado passa a ser o plano
                            // exibido; um extra preserva a seleção atual (H3-15).
                            if (envelope.kind == "main") {
                                _selectedPlanId.value = planId
                            }
                            // Persistir config + motivo do fallback (regras H3-30).
                            envelope.config?.let { cfg ->
                                preferences.generateConfigJson = GenerateConfigCodec.encode(cfg)
                                _generateConfig.value = cfg
                            }
                            val fallbackReason = if (result.source == "ai") {
                                ""
                            } else {
                                result.fallbackReason?.takeIf { it.isNotBlank() } ?: "unknown"
                            }
                            preferences.lastFallbackReason = fallbackReason
                            _lastFallbackReason.value = fallbackReason
                            // Não navega se o usuário estiver em outra tela — só mensagem.
                            val source = if (result.source == "ai") "com IA" else "local"
                            val review = formatReviewDate(result.nextReviewAt) ?: result.nextReviewAt
                            val base = "Treino gerado $source e salvo no aparelho. Revisar em $review."
                            _message.value = fallbackReasonLabel(fallbackReason)
                                ?.let { motivo -> "$base Motivo: $motivo." } ?: base
                        } catch (error: Exception) {
                            // O job está done no backend; falha local ao salvar não
                            // deve manter o envelope travado.
                            Log.w(TAG, "Falha ao salvar plano do job concluído", error)
                            _generateError.value = uiErrorFrom(error, "salvar o treino")
                        }
                        clearPendingEnvelope()
                    } ?: run {
                        _message.value = "Job concluído sem plano."
                        clearPendingEnvelope()
                    }
                }
                "failed" -> {
                    // Mantém o envelope para o retry ("Tentar de novo").
                    _generateError.value = uiErrorFrom(
                        Exception(status.error ?: "Falha na geração"), "gerar o treino"
                    )
                }
                "cancelled" -> {
                    clearPendingEnvelope()
                    _message.value = "Geração cancelada."
                }
                else -> {
                    // pending/running: ainda em andamento, nada a fazer.
                }
            }
        } catch (error: Exception) {
            // 404 (job sumido) ou erro de rede: falha com retry, envelope mantido.
            Log.w(TAG, "Falha ao consultar status do job", error)
            _generateError.value = uiErrorFrom(error, "consultar a geração")
        } finally {
            _isPolling.value = false
        }
    }

    /** Cancela o job no backend e limpa o envelope local (sem salvar nada). */
    suspend fun cancelGeneration() {
        val envelope = _pendingGeneration.value ?: return
        try {
            repository.cancelGeneration(envelope.jobId)
        } catch (error: Exception) {
            Log.w(TAG, "Falha ao cancelar job", error)
        } finally {
            clearPendingEnvelope()
        }
    }

    /** Chamado pelo onResume da MainActivity: consulta o status se houver envelope. */
    fun onAppResumed() {
        if (_pendingGeneration.value != null) {
            viewModelScope.launch {
                refreshPendingGeneration()
            }
        }
    }

    /** Retry de job failed: reenvia o request persistido no envelope. */
    fun retryFromEnvelope() {
        val envelope = _pendingGeneration.value ?: return
        submitGeneration(
            modalities = envelope.request.modalities,
            goal = envelope.request.goal,
            daysPerWeek = envelope.request.daysPerWeek,
            level = envelope.request.currentLevel,
            equipment = envelope.request.availableEquipment,
            muscles = envelope.request.targetMuscles,
            sessionDuration = envelope.request.sessionDurationMinutes,
            reviewWeeks = envelope.request.reviewAfterWeeks,
            bodyWeightKgText = envelope.request.bodyWeightKg?.toString() ?: "",
            heightCmText = envelope.request.heightCm?.toString() ?: "",
            sexChoice = envelope.request.sex,
            environment = envelope.request.environment,
            kind = envelope.kind,
            config = envelope.config,
        )
    }

    /**
     * H3-29: "Tentar de novo" da GenerateScreen — reexecuta a última geração
     * com exatamente os mesmos parâmetros (mantido para compatibilidade).
     */
    fun retryGenerate() {
        val args = lastGenerateArgs ?: return
        submitGeneration(
            modalities = args.modalities,
            goal = args.goal,
            daysPerWeek = args.daysPerWeek,
            level = args.level,
            equipment = args.equipment,
            muscles = args.muscles,
            sessionDuration = args.sessionDuration,
            reviewWeeks = args.reviewWeeks,
            bodyWeightKgText = args.bodyWeightKgText,
            heightCmText = args.heightCmText,
            sexChoice = args.sexChoice,
            environment = args.environment,
            kind = args.kind,
            config = args.config,
        )
    }

    private fun clearPendingEnvelope() {
        preferences.pendingGenerationV1 = null
        _pendingGeneration.value = null
    }

    /** Descarta o envelope local (usuário descartou o job falho). */
    fun dismissPendingGeneration() {
        clearPendingEnvelope()
    }

    private fun nextReviewAt(reviewAfterWeeks: Int): String =
        Instant.now().plus(reviewAfterWeeks.toLong(), ChronoUnit.WEEKS).toString()

    // ---------------- Revisão de plano (H2-6) ----------------

    fun reviewPlan() {
        val plan = mainPlan() ?: run {
            _message.value = "Nenhum plano ativo para revisar."
            return
        }
        if (_isReviewing.value) return
        viewModelScope.launch {
            _isReviewing.value = true
            try {
                val cutoff = Instant.now().minus(4L, ChronoUnit.WEEKS).toEpochMilli()
                val sessionList = sessions.value.filter { it.session.startedAt >= cutoff }
                val history = ReviewPlanHistory(
                    weeks = 4,
                    adherence = if (sessionList.isEmpty()) 1.0 else {
                        sessionList.count { it.session.status == "completed" } / sessionList.size.toDouble()
                    },
                    sessions = sessionList.map { s ->
                        ReviewPlanSession(
                            modality = s.session.modality,
                            completed = s.session.status == "completed",
                            durationSeconds = s.session.totalDurationSeconds,
                            distanceMeters = s.session.totalDistanceMeters,
                            rpe = s.session.sessionRpe,
                            startedAt = s.session.startedAt,
                        )
                    },
                )
                val response = repository.reviewPlan(
                    ReviewPlanRequest(
                        deviceId = preferences.deviceId,
                        plan = plan.toReviewPlanDto(),
                        history = history,
                    )
                )
                pendingReview = response
                _reviewChanges.value = response.changes
                _reviewMessage.value =
                    if (response.changes.isEmpty()) {
                        "Sem mudanças propostas. O plano permanece como está."
                    } else {
                        "Revisão proposta: ${response.changes.joinToString(" • ")}. Confirme para aplicar."
                    }
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, "Não foi possível revisar o plano.")
            } finally {
                _isReviewing.value = false
            }
        }
    }

    fun acceptReview() {
        val response = pendingReview ?: return
        viewModelScope.launch {
            try {
                val main = mainPlan()?.plan
                val planId = repository.saveGeneratedPlan(
                    GeneratePlanResponse(
                        workout = response.workout,
                        source = response.source,
                        nextReviewAt = main?.nextReviewAt
                            ?: nextReviewAt(main?.reviewAfterWeeks ?: 4),
                        reviewAfterWeeks = main?.reviewAfterWeeks ?: 4,
                    ),
                    kind = "main",
                )
                // H3-45: a revisão aprovada é o novo main; seleciona-o.
                _selectedPlanId.value = planId
                pendingReview = null
                _reviewChanges.value = emptyList()
                _reviewMessage.value = null
                _message.value = "Plano atualizado com a revisão."
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, "Não foi possível aplicar a revisão.")
            }
        }
    }

    fun discardReview() {
        pendingReview = null
        _reviewChanges.value = emptyList()
        _reviewMessage.value = null
        _message.value = "Revisão descartada. O plano permanece como está."
    }

    fun clearReviewMessage() {
        _reviewMessage.value = null
    }

    // ---------------- Progressão de carga e ciclo (H3-5) ----------------

    fun postponeReview(planId: Long, weeks: Int = REVIEW_POSTPONE_WEEKS) {
        viewModelScope.launch {
            try {
                repository.postponeReview(planId, weeks)
                _message.value = "Revisão adiada em $weeks semanas."
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, "Não foi possível adiar a revisão.")
            }
        }
    }

    // ---------------- Execução ----------------

    fun startWorkout(dayId: Long) {
        val plan = activePlan.value ?: run {
            _message.value = "Nenhum plano ativo no aparelho."
            return
        }
        val day = plan.days.firstOrNull { it.day.id == dayId }
            ?: run {
                _message.value = "Dia de treino não encontrado."
                return
            }
        viewModelScope.launch {
            try {
                val sessionId = repository.startSession(plan.plan.id, dayId)
                _activeSessionId.value = sessionId
                _exerciseIndex.value = 0
                syncWearState()
                when (SessionKind.fromModality(day.day.modality)) {
                    SessionKind.RUN -> _screen.value = Screen.RunWorkout
                    SessionKind.MOBILITY -> _screen.value = Screen.Mobility
                    SessionKind.STRENGTH -> _screen.value = Screen.Workout
                }
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, "Não foi possível iniciar o treino.")
            }
        }
    }

    fun leaveWorkout() {
        _screen.value = Screen.Home
        _message.value = "Treino continua salvo no aparelho. Toque em Retomar treino para voltar."
    }

    fun resumeWorkout() {
        val session = inProgressSession.value ?: run {
            _message.value = "Nenhum treino em andamento."
            return
        }
        _activeSessionId.value = session.session.id
        when (SessionKind.fromModality(session.session.modality)) {
            SessionKind.RUN -> _screen.value = Screen.RunWorkout
            SessionKind.MOBILITY -> _screen.value = Screen.Mobility
            SessionKind.STRENGTH -> _screen.value = Screen.Workout
        }
    }

    fun logSet(
        exercise: SessionExerciseEntity,
        reps: Int?,
        weightKg: Double?,
        rpe: Double?,
    ) {
        viewModelScope.launch {
            try {
                repository.logSet(
                    exerciseLogId = exercise.id,
                    reps = reps,
                    weightKg = weightKg,
                    rpe = rpe,
                )
                startRest(exercise.restSeconds)
                syncWearState()
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, "Não foi possível salvar a série.")
            }
        }
    }

    fun logSet(
        exercise: ExerciseSessionData,
        reps: Int?,
        weightKg: Double?,
        rpe: Double?,
    ) {
        viewModelScope.launch {
            try {
                repository.logSet(
                    exerciseLogId = exercise.sessionExerciseId,
                    reps = reps,
                    weightKg = weightKg,
                    rpe = rpe,
                )
                startRest(exercise.restSeconds)
                syncWearState()
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, "Não foi possível salvar a série.")
            }
        }
    }

    fun finishWorkout() {
        val sessionId = _activeSessionId.value ?: return
        viewModelScope.launch {
            try {
                repository.finishSession(sessionId)
                skipRest()
                _activeSessionId.value = null
                _screen.value = Screen.Home
                _message.value = "Treino finalizado e salvo no aparelho."
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, "Não foi possível finalizar o treino.")
            }
        }
    }

    /**
     * Edita uma série já registrada (H3-27): o mesmo `id` é atualizado com
     * reps/carga/RPE novos e o estado do relógio é reenviado.
     */
    fun updateSet(set: SetLogEntity, reps: Int?, weightKg: Double?, rpe: Double?) {
        viewModelScope.launch {
            try {
                repository.updateSet(set.copy(reps = reps, weightKg = weightKg, rpe = rpe))
                syncWearState()
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, "Não foi possível atualizar a série.")
            }
        }
    }

    /**
     * Exclui uma série registrada (H3-27); o progresso do exercício é
     * recalculado no repositório e o relógio recebe o estado novo.
     */
    fun deleteSet(set: SetLogEntity) {
        viewModelScope.launch {
            try {
                repository.deleteSet(set.id)
                syncWearState()
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, "Não foi possível excluir a série.")
            }
        }
    }

    /**
     * "Abandonar treino" (H3-27): apaga a sessão em andamento — exercícios e
     * séries caem por CASCADE — e limpa descanso e estado do relógio. A UI
     * pede confirmação antes de chamar este método.
     */
    fun abandonWorkout() {
        val sessionId = inProgressSession.value?.session?.id
            ?: _activeSessionId.value
            ?: return
        viewModelScope.launch {
            try {
                repository.deleteSession(sessionId)
                _activeSessionId.value = null
                // Estado do relógio limpo na hora: o `inProgressSession` ainda
                // pode estar obsoleto aqui, então `syncWearState()` republicaria
                // a sessão recém-apagada; o coletor do init confirma o `null`
                // quando o flow do Room emitir.
                _wearState.value = null
                skipRest()
                _screen.value = Screen.Home
                _message.value = "Treino abandonado."
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, "Não foi possível abandonar o treino.")
            }
        }
    }

    /** Finaliza uma sessão de corrida planejada. */
    fun finishRunWorkout(sessionId: Long, durationSeconds: Long, distanceMeters: Double?, rpe: Double?) {
        viewModelScope.launch {
            try {
                repository.finishRunSession(sessionId, durationSeconds, distanceMeters, rpe)
                _activeSessionId.value = null
                _screen.value = Screen.Home
                _message.value = "Corrida registrada e salva no aparelho."
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, "Não foi possível finalizar a corrida.")
            }
        }
    }

    /**
     * Finaliza uma sessão de mobilidade planejada (H3-27). Quando a sessão
     * veio de um dia do plano (`planId != 0`), a sessão `in_progress` criada
     * por [startWorkout] é **fechada** — nunca é inserida uma segunda sessão
     * `completed`, que ficaria como fantasma eterno na Home. O atalho
     * "Mobilidade" sem plano continua gravando a sessão avulsa de antes.
     */
    fun finishPlannedMobility(planId: Long, dayId: Long, routineId: String, elapsedSeconds: Long) {
        viewModelScope.launch {
            try {
                val openSession = inProgressSession.value
                    ?.takeIf { it.session.modality == "mobility" }
                if (planId != 0L && openSession != null) {
                    repository.finishPlannedMobilitySession(
                        sessionId = openSession.session.id,
                        routineId = routineId,
                        elapsedSeconds = elapsedSeconds,
                    )
                } else {
                    repository.logPlannedMobility(planId, dayId, routineId, elapsedSeconds)
                }
                _screen.value = Screen.Home
                _message.value = "Mobilidade finalizada e salva no histórico."
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, "Não foi possível salvar a mobilidade.")
            }
        }
    }

    // ---------------- Registro avulso ----------------

    fun openLogActivity() {
        _activityEditingId.value = null
        _activityPrefill.value = null
        _activityErrors.value = emptyMap()
        _screen.value = Screen.LogActivity
        refreshCanCopyLastActivity(ActivityModality.SWIM)
    }

    fun editLogActivity(sessionId: Long) {
        val session = sessions.value.firstOrNull { it.session.id == sessionId } ?: return
        if (!session.session.isManualActivity()) {
            _message.value = "Só um registro manual pode ser editado."
            return
        }
        _activityEditingId.value = sessionId
        _activityPrefill.value = session.session.toActivityLogInput()
        _activityErrors.value = emptyMap()
        _screen.value = Screen.LogActivity
        refreshCanCopyLastActivity(_activityPrefill.value?.modality ?: ActivityModality.SWIM)
    }

    fun onActivityModalityChanged(modality: ActivityModality) {
        refreshCanCopyLastActivity(modality)
    }

    /** Preenche o formulário com a última sessão manual da mesma modalidade (regra 16). */
    fun copyLastActivity(modality: ActivityModality) {
        viewModelScope.launch {
            val last = repository.lastManualSession(modality.storageValue) ?: return@launch
            _activityPrefill.value = last.toActivityLogInput().copy(startedAt = System.currentTimeMillis())
        }
    }

    private fun refreshCanCopyLastActivity(modality: ActivityModality) {
        viewModelScope.launch {
            _canCopyLastActivity.value = repository.lastManualSession(modality.storageValue) != null
        }
    }

    fun saveActivity(draft: ActivityLogDraft) {
        if (_isSavingActivity.value) return
        val validation = validateActivityDraft(draft)
        _activityErrors.value = validation.errors
        val input = validation.input ?: return
        val editingId = _activityEditingId.value
        viewModelScope.launch {
            _isSavingActivity.value = true
            try {
                if (editingId != null) {
                    repository.updateActivity(input.toManualSession(id = editingId))
                    _message.value = "Registro atualizado no aparelho."
                } else {
                    repository.logActivity(input.toManualSession())
                    _message.value = "Atividade registrada e salva no aparelho."
                }
                    _activityEditingId.value = null
                    _activityPrefill.value = null
                    // H3-38: salvar edição retorna à origem (Histórico quando veio dele).
                    _screen.value = _backTarget.value ?: Screen.Home
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, "Não foi possível salvar a atividade.")
            } finally {
                _isSavingActivity.value = false
            }
        }
    }

    fun deleteLogActivity(sessionId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteActivity(sessionId)
                if (_activityEditingId.value == sessionId) {
                    _activityEditingId.value = null
                    _activityPrefill.value = null
                    _screen.value = Screen.History
                }
                _message.value = "Registro manual excluído."
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, "Não foi possível excluir o registro.")
            }
        }
    }

    // ---------------- Mobilidade ----------------

    /**
     * H3-31: estado da rotina de mobilidade vive aqui (rotina, movimento,
     * tempo restante e decorrido) — sobrevive à rotação. O tick roda em job
     * próprio, como o descanso; morte de processo reinicia a rotina (aceito).
     */
    private val _mobilityState = MutableStateFlow(MobilityScreenState())
    val mobilityState: StateFlow<MobilityScreenState> = _mobilityState.asStateFlow()

    private var mobilityJob: Job? = null

    fun startMobilityRoutine(routine: MobilityRoutine) {
        val session = MobilitySessionEngine.start(routine) ?: return
        _mobilityState.value = MobilityScreenState(routine = routine, session = session)
        startMobilityTicker()
    }

    /** Rotina planejada (preset): não sobrescreve uma rotina em andamento. */
    fun startMobilityFromPreset(routineId: String) {
        if (_mobilityState.value.session != null) return
        val routine = MobilityRoutineTemplates.getRoutine(routineId) ?: return
        startMobilityRoutine(routine)
    }

    fun toggleMobilityPause() {
        val current = _mobilityState.value
        val session = current.session ?: return
        _mobilityState.value = current.copy(session = MobilitySessionEngine.togglePause(session))
    }

    fun mobilityPreviousMovement() {
        val current = _mobilityState.value
        val routine = current.routine ?: return
        val session = current.session ?: return
        if (session.movementIndex == 0) return
        _mobilityState.value = current.copy(
            session = MobilitySessionEngine.goTo(session, routine, session.movementIndex - 1) ?: session,
        )
    }

    /** "Avançar" manual ou toque na transição (H3-31): pula direto ao próximo. */
    fun mobilityNextMovement() {
        val current = _mobilityState.value
        val routine = current.routine ?: return
        val session = current.session ?: return
        val next = if (session.inTransition) {
            MobilitySessionEngine.skipTransition(session, routine)
        } else {
            MobilitySessionEngine.goTo(session, routine, session.movementIndex + 1) ?: return
        }
        _mobilityState.value = current.copy(session = next)
    }

    /** Limpa a rotina em andamento (finalização ou descarte confirmado). */
    fun clearMobilitySession() {
        mobilityJob?.cancel()
        mobilityJob = null
        _mobilityState.value = MobilityScreenState()
    }

    private fun startMobilityTicker() {
        mobilityJob?.cancel()
        mobilityJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                val current = _mobilityState.value
                val session = current.session ?: break
                if (!session.running) break
                if (session.paused) continue
                val routine = current.routine ?: break
                val ticked = MobilitySessionEngine.tick(session, routine)
                _mobilityState.value = current.copy(session = ticked)
                if (!ticked.running) break
            }
        }
    }

    fun finishMobility(routineId: String, elapsedSeconds: Long) {
        viewModelScope.launch {
            try {
                val routine = com.treinos.app.ui.MobilityRoutineTemplates.getRoutine(routineId)
                val duration = if (elapsedSeconds > 0) elapsedSeconds else {
                    routine?.let { com.treinos.app.ui.MobilityRoutineTemplates.totalDurationSeconds(it) } ?: 0
                }
                val session = com.treinos.app.data.local.WorkoutSessionEntity(
                    planId = 0,
                    dayId = 0,
                    planName = "",
                    dayName = "Mobilidade",
                    status = "completed",
                    startedAt = System.currentTimeMillis() - (duration.toLong() * 1000L),
                    endedAt = System.currentTimeMillis(),
                    totalDurationSeconds = duration.toLong(),
                    modality = "mobility",
                    source = "manual",
                    title = routine?.name ?: "Mobilidade",
                    notes = routine?.focus ?: "",
                )
                repository.logActivity(session)
                _message.value = "Mobilidade finalizada e salva no histórico."
                _screen.value = Screen.Home
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, "Não foi possível salvar a mobilidade.")
                _screen.value = Screen.Home
            }
        }
    }

    // ---------------- Descanso ----------------

    /**
     * Descanso por deadline em wall-clock (H3-26): o deadline é persistido em
     * preferências e `remaining` é derivado de `deadline - now` a cada tick —
     * o tick de 1 s só atualiza a UI, então Doze/tela apagada não atrasam o
     * fim. `seconds <= 0` não inicia descanso: não grava estado, não mostra card.
     */
    private fun startRest(seconds: Int) {
        restJob?.cancel()
        restJob = null
        val duration = seconds.coerceAtLeast(0)
        val deadline = RestTimer.start(duration, System.currentTimeMillis())
        if (deadline == null) {
            clearRestState()
            return
        }
        _restTotal.value = duration
        _restPaused.value = false
        pausedRemainingSeconds = 0
        preferences.restDeadlineAtMillis = deadline
        preferences.restTotalSeconds = duration
        _restRemaining.value = duration
        restJob = viewModelScope.launch { tickRest() }
    }

    /**
     * Loop de UI do descanso: recalcula `remaining` do deadline persistido a
     * cada iteração (assim "+15 s" feito por outro caminho é pego no próximo
     * tick) e, ao chegar a 0, limpa o deadline em disco e toca o feedback.
     */
    private suspend fun tickRest() {
        while (true) {
            val deadline = preferences.restDeadlineAtMillis
            if (deadline <= 0L) return
            val now = System.currentTimeMillis()
            val remaining = RestTimer.remainingSeconds(deadline, now)
            _restRemaining.value = remaining
            if (remaining <= 0) {
                preferences.restDeadlineAtMillis = 0L
                restFeedback?.onRestFinished()
                return
            }
            val millisToZero = deadline - now
            delay(minOf(1_000L, maxOf(50L, millisToZero + 50L)))
        }
    }

    fun skipRest() {
        restJob?.cancel()
        restJob = null
        clearRestState()
        syncWearState()
    }

    /** Congela a contagem; a retomada recalcula o deadline a partir do restante. */
    fun pauseRest() {
        if (_restPaused.value) return
        val remaining = _restRemaining.value ?: return
        if (remaining <= 0) return
        restJob?.cancel()
        restJob = null
        pausedRemainingSeconds = remaining
        _restPaused.value = true
        // Pausa não sobrevive a processo morto: sem deadline em disco, o app
        // reaberto não restaura um descanso que o usuário congelou.
        preferences.restDeadlineAtMillis = 0L
    }

    fun resumeRest() {
        if (!_restPaused.value) return
        val remaining = pausedRemainingSeconds
        _restPaused.value = false
        val deadline = RestTimer.resume(remaining, System.currentTimeMillis()) ?: return
        preferences.restDeadlineAtMillis = deadline
        restJob = viewModelScope.launch { tickRest() }
    }

    /** "+15 s" sem teto: empurra o deadline (ou o restante congelado, se pausado). */
    fun extendRest(seconds: Int = REST_EXTEND_SECONDS) {
        if (seconds <= 0) return
        if (_restPaused.value) {
            pausedRemainingSeconds += seconds
        } else {
            val deadline = preferences.restDeadlineAtMillis
            if (deadline <= 0L) return
            preferences.restDeadlineAtMillis = RestTimer.extend(deadline, seconds)
        }
        val total = (preferences.restTotalSeconds + seconds).coerceAtLeast(0)
        preferences.restTotalSeconds = total
        _restTotal.value = total
    }

    private fun clearRestState() {
        _restRemaining.value = null
        _restTotal.value = null
        _restPaused.value = false
        pausedRemainingSeconds = 0
        preferences.restDeadlineAtMillis = 0L
    }

    /**
     * Processo morto com descanso em curso: deadline no futuro restaura o
     * descanso restante; no passado, é limpo (sem card fantasma).
     */
    private fun restorePersistedRest() {
        val deadline = preferences.restDeadlineAtMillis
        if (deadline <= 0L) return
        val now = System.currentTimeMillis()
        val total = preferences.restTotalSeconds
        if (deadline <= now || total <= 0) {
            preferences.restDeadlineAtMillis = 0L
            return
        }
        _restTotal.value = total
        _restRemaining.value = RestTimer.remainingSeconds(deadline, now)
        restJob = viewModelScope.launch { tickRest() }
    }

    // ---------------- Navegacao por exercicio (fluxo nao-circuito) ----------------

    fun nextExercise() {
        val exercises = _exercises.value
        val next = nextIncompleteExercise(exercises)
        if (next == null) return
        _exerciseIndex.value = exercises.indexOf(next)
        skipRest()
    }

    fun isCurrentExerciseCompleted(): Boolean {
        val current = currentExercise.value ?: return true
        val progress = calculateProgress(
            plannedSets = current.plannedSets,
            completedSets = current.completedSets,
            sets = current.sets,
        )
        return progress.isCompleted
    }

    fun currentExerciseProgressText(): String {
        val current = currentExercise.value ?: return ""
        val progress = calculateProgress(
            plannedSets = current.plannedSets,
            completedSets = current.completedSets,
            sets = current.sets,
        )
        return progressText(progress)
    }

    // ---------------- Restauracao por deviceId ----------------

    fun restoreByDeviceId(deviceId: String) {
        if (_isRestoring.value) return
        viewModelScope.launch {
            _isRestoring.value = true
            val outcome = deviceRestore(deviceId)
            _restoreMessage.value = when (outcome) {
                RestoreOutcome.InvalidDeviceId -> "Informe um ID de aparelho válido."
                RestoreOutcome.Empty ->
                    "Nenhum dado encontrado para o dispositivo ${deviceId.trim()}; " +
                        "seus dados locais e o ID atual foram mantidos."
                is RestoreOutcome.Failed -> outcome.message
                is RestoreOutcome.Success -> {
                    _deviceId.value = preferences.deviceId
                    _lastSyncAt.value = preferences.lastSyncAt
                    _restoreDeviceId.value = ""
                    "Restaurado: ${outcome.planCount} plano(s) e ${outcome.sessionCount} " +
                        "sessão(ões) do aparelho ${deviceId.trim()}. O ID deste aparelho foi atualizado."
                }
            }
            _isRestoring.value = false
        }
    }

    fun onRestoreDeviceIdChanged(text: String) {
        _restoreDeviceId.value = text
        _restoreMessage.value = null
    }

    // ---------------- Configurações / sincronização ----------------

    fun saveBackendUrl(url: String) {
        preferences.backendUrl = url
        _backendUrl.value = preferences.backendUrl
        _connectionMessage.value = null
        _syncMessage.value = null
        // H3-24: URL salva, catálogo recarrega em fundo; se o backend não
        // responder, o cache atual permanece (resposta vazia não envenena).
        loadExerciseMedia(false)
    }

    fun testConnection() {
        if (_isTesting.value) return
        viewModelScope.launch {
            _isTesting.value = true
            try {
                val health = repository.testConnection()
                _connectionMessage.value =
                    "Conectado. Modelo: ${health.model.ifBlank { "—" }} • " +
                    "Catálogo: ${health.catalogSize} exercícios."
            } catch (error: Exception) {
                _connectionMessage.value = uiErrorMessage(error, "Falha ao conectar.")
            } finally {
                _isTesting.value = false
            }
        }
    }

    fun syncNow() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            _syncMessage.value = null
            try {
                val result = repository.sync()
                _lastSyncAt.value = preferences.lastSyncAt
                _syncMessage.value =
                    "Enviado: ${result.plans} plano(s), ${result.sessions} treino(s) " +
                    "e ${result.setLogs} série(s)."
            } catch (error: Exception) {
                Log.w(TAG, "Falha ao enviar ao backend", error)
                _syncError.value = uiErrorFrom(error, "enviar ao backend")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun downloadNow() {
        if (_isDownloading.value) return
        viewModelScope.launch {
            _isDownloading.value = true
            _downloadError.value = null
            _downloadMessage.value = null
            try {
                _downloadMessage.value = when (
                    val result = repository.downloadSync(preferences.deviceId)
                ) {
                    is SyncDownloadResult.Restored -> {
                        _lastSyncAt.value = preferences.lastSyncAt
                        "Restaurado: ${result.planCount} plano(s) e ${result.sessionCount} " +
                            "sessão(ões) do backend."
                    }
                    SyncDownloadResult.Empty ->
                        "Nenhum dado encontrado; seus dados locais foram mantidos."
                }
            } catch (error: Exception) {
                Log.w(TAG, "Falha ao baixar dados do backend", error)
                _downloadError.value = uiErrorFrom(error, "baixar dados do backend")
            } finally {
                _isDownloading.value = false
            }
        }
    }

    // ---------------- Backup JSON ----------------

    suspend fun exportBackupJson(appVersionName: String, appVersionCode: Long): String =
        repository.exportBackup(appVersionName, appVersionCode)

    fun prepareImport(json: String) {
        if (_backupImportState.value is BackupImportState.Importing) return
        _backupMessage.value = null
        viewModelScope.launch {
            _backupImportState.value = BackupImportState.Reading
            try {
                val file = backupImportDecoder.decodeFromString(BackupFile.serializer(), json)
                val result = BackupValidator.validate(file)
                if (!result.valid) {
                    _backupImportState.value = BackupImportState.Error(result.message ?: "Backup inválido")
                    pendingBackupPayload = null
                    return@launch
                }
                val preview = result.preview
                    ?: BackupPreview(0, 0, 0, 0, 0, 0)
                pendingBackupPayload = file
                _backupImportState.value = BackupImportState.Ready(preview = preview, payload = file)
            } catch (e: Exception) {
                _backupImportState.value = BackupImportState.Error("Arquivo de backup inválido")
                pendingBackupPayload = null
            }
        }
    }

    fun confirmImport() {
        val payload = pendingBackupPayload ?: return
        if (_backupImportState.value is BackupImportState.Importing) return
        viewModelScope.launch {
            _backupImportState.value = BackupImportState.Importing
            try {
                repository.importBackup(payload, preferences)
                // H3-45: a seleção anterior pode apontar para um plano que não
                // existe mais (ou para um extra); volta ao main restaurado.
                _selectedPlanId.value = null
                _deviceId.value = preferences.deviceId
                _backupMessage.value = if (payload.deviceId.isNotBlank()) {
                    "Backup importado. O ID deste aparelho passou a ser ${payload.deviceId.trim()}."
                } else {
                    "Backup importado com sucesso."
                }
                _backupImportState.value = BackupImportState.Idle
                pendingBackupPayload = null
            } catch (error: Exception) {
                _backupImportState.value = BackupImportState.Error(uiErrorMessage(error, "Não foi possível importar o backup."))
            }
        }
    }

    fun cancelImport() {
        _backupImportState.value = BackupImportState.Idle
        pendingBackupPayload = null
        _backupMessage.value = null
    }

    fun clearBackupMessage() {
        _backupMessage.value = null
    }

    fun setBackupMessage(message: String?) {
        _backupMessage.value = message
    }

    fun startDiscovery() {
        if (_isDiscovering.value) return
        val manager = discoveryManager
        if (manager == null) {
            _discoveryMessage.value = "Descoberta de rede indisponível neste aparelho. Use o endereço manual."
            return
        }
        _discoveredBackends.value = emptyList()
        _discoveryMessage.value = null
        _isDiscovering.value = true
        manager.start(
            onFound = { backend ->
                _discoveredBackends.value = dedupeBackends(_discoveredBackends.value + backend)
            },
            onError = { message ->
                _isDiscovering.value = false
                _discoveryMessage.value = message
            },
        )
    }

    fun stopDiscovery() {
        discoveryManager?.stop()
        _isDiscovering.value = false
    }

    fun clearDiscoveryMessage() {
        _discoveryMessage.value = null
    }

    /**
     * Conectar é a única forma de trocar a URL sem digitar: nada é salvo antes
     * de o backend responder `GET /health`.
     */
    fun connectDiscoveredBackend(backend: DiscoveredBackend) {
        if (_isConnectingBackend.value) return
        viewModelScope.launch {
            _isConnectingBackend.value = true
            _discoveryMessage.value = null
            try {
                when (val outcome = backendConnector(backend)) {
                    is ConnectOutcome.Connected -> {
                        _backendUrl.value = preferences.backendUrl
                        _connectionMessage.value = null
                        _syncMessage.value = null
                        _discoveryMessage.value =
                            "Conectado a ${outcome.backend.name} (${outcome.backend.endpoint}). " +
                            "Modelo: ${outcome.model.ifBlank { "—" }} • " +
                            "Catálogo: ${outcome.catalogSize} exercícios."
                        // H3-24: backend novo conectado, catálogo recarrega em
                        // fundo (sem bloquear a UI; sem forçar refresh).
                        loadExerciseMedia(false)
                        stopDiscovery()
                    }

                    is ConnectOutcome.Refused -> {
                        _discoveryMessage.value = outcome.message
                    }
                }
            } finally {
                _isConnectingBackend.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        discoveryManager?.stop()
    }

    companion object {
        private const val TAG = "AppViewModel"

        // H3-35: atalho dinâmico "Retomar treino".
        private const val SHORTCUT_ID_RESUME = "resume_workout"
        private const val EXTRA_RESUME_SESSION = "extra_resume_session"

        /** "+15 s" padrão da faixa de descanso (H3-26). */
        const val REST_EXTEND_SECONDS = 15

        fun factory(
            context: Context,
            repository: WorkoutRepository,
            preferences: AppPreferences,
            exerciseMediaRepository: ExerciseMediaRepository? = null,
            discoveryManager: BackendDiscoveryManager? = null,
            healthChecker: BackendHealthChecker = HttpBackendHealthChecker(),
            wearSyncRepository: WearSyncRepository? = null,
            restFeedback: RestFeedbackPlayer? = null,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppViewModel(
                    context,
                    repository,
                    preferences,
                    exerciseMediaRepository,
                    discoveryManager,
                    healthChecker,
                    wearSyncRepository,
                    restFeedback,
                ) as T
            }
        }
    }
}

/** Posição do plano ativo no ciclo; o aviso de deload não altera nada sozinho. */
data class PlanCycle(val week: Int, val isDeloadSuggested: Boolean)

/**
 * Parâmetros da última chamada de geração (H3-29): guardados para o
 * "Tentar de novo" reexecutar a mesma ação sem depender do formulário.
 */
private data class GeneratePlanArgs(
    val modalities: List<ModalityDto>,
    val goal: String,
    val daysPerWeek: Int,
    val level: String,
    val equipment: List<String>,
    val muscles: List<String>,
    val sessionDuration: Int,
    val reviewWeeks: Int,
    val bodyWeightKgText: String,
    val heightCmText: String,
    val sexChoice: String?,
    val environment: String?,
    val kind: String,
    /** H3-30: configuração do formulário, persistida quando a geração funciona. */
    val config: GenerateConfig? = null,
)

sealed interface BackupImportState {
    object Idle : BackupImportState
    object Reading : BackupImportState
    data class Ready(val preview: BackupPreview, val payload: BackupFile) : BackupImportState
    object Importing : BackupImportState
    data class Error(val message: String) : BackupImportState
}

/** Converte o plano ativo em DTO de revisão para enviar ao backend. */
private fun WorkoutPlanWithDetails.toReviewPlanDto(): WorkoutPlanDto =
    WorkoutPlanDto(
        name = plan.name,
        description = plan.description,
        goal = plan.goal,
        daysPerWeek = plan.daysPerWeek,
        days = days.map { day ->
            WorkoutDayDto(
                name = day.day.name,
                dayOrder = day.day.dayOrder,
                exercises = day.exercises
                    .sortedBy { it.exerciseOrder }
                    .map { ex ->
                        WorkoutExerciseDto(
                            exerciseId = ex.exerciseId,
                            exerciseOrder = ex.exerciseOrder,
                            sets = ex.sets,
                            targetReps = ex.targetReps,
                            targetRpe = ex.targetRpe,
                            restSeconds = ex.restSeconds,
                            notes = ex.notes,
                            isSupersetWithPrevious = ex.isSupersetWithPrevious,
                            exerciseName = ex.exerciseName,
                            primaryMuscles = ex.primaryMuscles
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() },
                            equipment = ex.equipment,
                            metricType = ex.metricType,
                            targetDurationSeconds = ex.targetDurationSeconds,
                            targetDistanceMeters = ex.targetDistanceMeters,
                            targetPaceSecondsPerKm = ex.targetPaceSecondsPerKm,
                            targetHrZone = ex.targetHrZone,
                        )
                    },
                isRestDay = day.day.isRestDay,
                weekday = day.day.weekday,
                modality = day.day.modality,
            )
        },
    )
