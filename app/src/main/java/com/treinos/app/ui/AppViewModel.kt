package com.treinos.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.treinos.app.data.WorkoutRepository
import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionWithExercises
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
import com.treinos.app.data.prefs.AppPreferences
import com.treinos.app.data.SyncDownloadResult
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import java.time.Instant
import java.time.temporal.ChronoUnit
import com.treinos.protocol.WearCommand
import com.treinos.protocol.WearCommandKind
import com.treinos.protocol.WearWorkoutState

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

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(
    private val repository: WorkoutRepository,
    private val preferences: AppPreferences,
    private val exerciseMediaRepository: ExerciseMediaRepository? = null,
    private val discoveryManager: BackendDiscoveryManager? = null,
    private val healthChecker: BackendHealthChecker = HttpBackendHealthChecker(),
    private val wearSyncRepository: WearSyncRepository? = null,
) : ViewModel() {

    private val backendConnector = BackendConnector(healthChecker, preferences)

    private val _screen = MutableStateFlow(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

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
                _message.value = error.message ?: "Não foi possível excluir o plano."
            }
        }
    }

    /** A revisão por IA continua apontando para o plano principal (H3-15). */
    private fun mainPlan(): WorkoutPlanWithDetails? =
        activePlans.value.firstOrNull { it.plan.kind == "main" }

    val sessions: StateFlow<List<SessionWithExercises>> = repository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val inProgressSession: StateFlow<SessionWithExercises?> = repository.observeSessions()
        .map { list -> list.firstOrNull { it.session.status == "in_progress" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _activeSessionId = MutableStateFlow<Long?>(null)

    val activeSession: StateFlow<SessionWithExercises?> = _activeSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else repository.observeSession(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

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

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _backendUrl = MutableStateFlow(preferences.backendUrl)
    val backendUrl: StateFlow<String> = _backendUrl.asStateFlow()

    private val _connectionMessage = MutableStateFlow<String?>(null)
    val connectionMessage: StateFlow<String?> = _connectionMessage.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _exerciseMedia = MutableStateFlow<Map<String, ExerciseMedia>>(emptyMap())
    val exerciseMedia: StateFlow<Map<String, ExerciseMedia>> = _exerciseMedia.asStateFlow()

    private val _selectedExerciseId = MutableStateFlow<String?>(null)
    val selectedExerciseId: StateFlow<String?> = _selectedExerciseId.asStateFlow()

    private val _isUpdatingCatalog = MutableStateFlow(false)
    val isUpdatingCatalog: StateFlow<Boolean> = _isUpdatingCatalog.asStateFlow()

    private val _catalogMessage = MutableStateFlow<String?>(null)
    val catalogMessage: StateFlow<String?> = _catalogMessage.asStateFlow()

    private val _restRemaining = MutableStateFlow<Int?>(null)
    val restRemaining: StateFlow<Int?> = _restRemaining.asStateFlow()

    private val _restTotal = MutableStateFlow<Int?>(null)
    val restTotal: StateFlow<Int?> = _restTotal.asStateFlow()

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
            loadExerciseMedia(false)
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
                    _catalogMessage.value = error.message ?: "Não foi possível atualizar o catálogo."
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
        _selectedExerciseId.value = exerciseId
    }

    fun closeExerciseDetail() {
        _selectedExerciseId.value = null
    }

    private var restJob: Job? = null

    private val _deviceId = MutableStateFlow(preferences.deviceId)
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    private val deviceRestore = RestoreByDeviceId(
        downloader = repository,
        devices = preferences,
    )

    // ---------------- Navegação ----------------

    fun openGenerate() {
        _screen.value = Screen.Generate
    }

    fun openHistory() {
        _screen.value = Screen.History
    }

    fun openSettings() {
        _screen.value = Screen.Settings
    }

    fun openMobility() {
        _screen.value = Screen.Mobility
    }

    fun goHome() {
        _screen.value = Screen.Home
    }

    fun clearMessage() {
        _message.value = null
    }

    // ---------------- Geração ----------------

    fun generatePlan(
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
    ) {
        if (_isGenerating.value) return
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
                // O campo nasce com a seleção salva, então null aqui é escolha explícita.
                preferences.sex = validation.sex
                _sex.value = preferences.sex
                val response = repository.generatePlan(
                    GeneratePlanRequest(
                        deviceId = preferences.deviceId,
                        goal = goal,
                        daysPerWeek = daysPerWeek,
                        availableEquipment = equipment,
                        targetMuscles = muscles,
                        currentLevel = level,
                        sessionDurationMinutes = sessionDuration,
                        reviewAfterWeeks = reviewWeeks,
                        bodyWeightKg = validation.bodyWeightKg,
                        heightCm = validation.heightCm,
                        sex = validation.sex,
                        modalities = modalities,
                        environment = environment,
                    ),
                    kind = kind,
                )
                _screen.value = Screen.Home
                val source = if (response.source == "ai") "com IA" else "local"
                val review = formatReviewDate(response.nextReviewAt) ?: response.nextReviewAt
                _message.value =
                    "Treino gerado $source e salvo no aparelho. Revisar em $review."
            } catch (error: Exception) {
                _message.value = error.message ?: "Não foi possível gerar o treino."
            } finally {
                _isGenerating.value = false
            }
        }
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
                _message.value = error.message ?: "Não foi possível revisar o plano."
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
                repository.saveGeneratedPlan(
                    GeneratePlanResponse(
                        workout = response.workout,
                        source = response.source,
                        nextReviewAt = main?.nextReviewAt
                            ?: nextReviewAt(main?.reviewAfterWeeks ?: 4),
                        reviewAfterWeeks = main?.reviewAfterWeeks ?: 4,
                    ),
                    kind = "main",
                )
                pendingReview = null
                _reviewChanges.value = emptyList()
                _reviewMessage.value = null
                _message.value = "Plano atualizado com a revisão."
            } catch (error: Exception) {
                _message.value = error.message ?: "Não foi possível aplicar a revisão."
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
                _message.value = error.message ?: "Não foi possível adiar a revisão."
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
                _message.value = error.message ?: "Não foi possível iniciar o treino."
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
                _message.value = error.message ?: "Não foi possível salvar a série."
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
                _message.value = error.message ?: "Não foi possível salvar a série."
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
                _message.value = error.message ?: "Não foi possível finalizar o treino."
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
                _message.value = error.message ?: "Não foi possível finalizar a corrida."
            }
        }
    }

    /** Finaliza uma sessão de mobilidade planejada. */
    fun finishPlannedMobility(planId: Long, dayId: Long, routineId: String, elapsedSeconds: Long) {
        viewModelScope.launch {
            try {
                repository.logPlannedMobility(planId, dayId, routineId, elapsedSeconds)
                _screen.value = Screen.Home
                _message.value = "Mobilidade finalizada e salva no histórico."
            } catch (error: Exception) {
                _message.value = error.message ?: "Não foi possível salvar a mobilidade."
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
                _screen.value = Screen.Home
            } catch (error: Exception) {
                _message.value = error.message ?: "Não foi possível salvar a atividade."
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
                _message.value = error.message ?: "Não foi possível excluir o registro."
            }
        }
    }

    // ---------------- Mobilidade ----------------

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
                _message.value = error.message ?: "Não foi possível salvar a mobilidade."
                _screen.value = Screen.Home
            }
        }
    }

    // ---------------- Descanso ----------------

    private fun startRest(seconds: Int) {
        restJob?.cancel()
        val duration = seconds.coerceAtLeast(0)
        _restTotal.value = duration
        restJob = viewModelScope.launch {
            var remaining = duration
            while (remaining > 0) {
                _restRemaining.value = remaining
                delay(1_000)
                remaining--
            }
            _restRemaining.value = 0
        }
    }

    fun skipRest() {
        restJob?.cancel()
        restJob = null
        _restRemaining.value = null
        _restTotal.value = null
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
                _connectionMessage.value = error.message ?: "Falha ao conectar."
            } finally {
                _isTesting.value = false
            }
        }
    }

    fun syncNow() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val result = repository.sync()
                _lastSyncAt.value = preferences.lastSyncAt
                _syncMessage.value =
                    "Enviado: ${result.plans} plano(s), ${result.sessions} treino(s) " +
                    "e ${result.setLogs} série(s)."
            } catch (error: Exception) {
                _syncMessage.value = error.message
                    ?: "Não foi possível enviar ao backend."
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun downloadNow() {
        if (_isDownloading.value) return
        viewModelScope.launch {
            _isDownloading.value = true
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
                _downloadMessage.value = error.message
                    ?: "Não foi possível baixar dados do backend."
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
                _deviceId.value = preferences.deviceId
                _backupMessage.value = if (payload.deviceId.isNotBlank()) {
                    "Backup importado. O ID deste aparelho passou a ser ${payload.deviceId.trim()}."
                } else {
                    "Backup importado com sucesso."
                }
                _backupImportState.value = BackupImportState.Idle
                pendingBackupPayload = null
            } catch (error: Exception) {
                _backupImportState.value = BackupImportState.Error(error.message ?: "Não foi possível importar o backup.")
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
        fun factory(
            repository: WorkoutRepository,
            preferences: AppPreferences,
            exerciseMediaRepository: ExerciseMediaRepository? = null,
            discoveryManager: BackendDiscoveryManager? = null,
            healthChecker: BackendHealthChecker = HttpBackendHealthChecker(),
            wearSyncRepository: WearSyncRepository? = null,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppViewModel(
                    repository,
                    preferences,
                    exerciseMediaRepository,
                    discoveryManager,
                    healthChecker,
                    wearSyncRepository,
                ) as T
            }
        }
    }
}

/** Posição do plano ativo no ciclo; o aviso de deload não altera nada sozinho. */
data class PlanCycle(val week: Int, val isDeloadSuggested: Boolean)

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
