package com.treinos.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.glance.appwidget.updateAll
import com.treinos.app.data.WorkoutRepository
import com.treinos.app.data.MeasurementPhotoStore
import com.treinos.app.data.MeasurementValidator
import com.treinos.app.data.ProgressionApplyResult
import com.treinos.app.data.SwapResult
import com.treinos.app.data.SwapScope
import com.treinos.app.data.swapCandidates
import com.treinos.app.data.sessionSwapCandidates
import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.BodyMeasurementEntity
import com.treinos.app.data.local.MEASUREMENT_SOURCE_MANUAL
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
import com.treinos.app.data.health.HealthConnectAvailability
import com.treinos.app.data.health.HealthConnectGateway
import com.treinos.app.data.health.HealthConnectPermissionState
import com.treinos.app.data.health.HealthWeightSample
import com.treinos.app.data.health.healthConnectAvailability
import com.treinos.app.data.health.healthPermissionState
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
import com.treinos.app.data.csv.CsvCodec
import com.treinos.app.data.csv.CsvImportPlan
import com.treinos.app.data.csv.CsvImportPreview
import com.treinos.app.data.csv.CsvImportState
import com.treinos.app.data.csv.StrongHevyCsvParser
import com.treinos.app.data.wear.WearSessionMapper
import com.treinos.app.data.wear.WearSyncRepository
import com.treinos.app.ui.ExerciseSessionData
import com.treinos.app.ui.PendingGeneration
import com.treinos.app.ui.PendingGenerationCodec
import com.treinos.app.ui.ProfileValidator
import com.treinos.app.ui.SetRecord
import com.treinos.app.ui.calculateProgress
import com.treinos.app.ui.nextIncompleteExercise
import com.treinos.app.ui.workoutexecution.restAfterSet
import com.treinos.app.ui.progression.ProgressionContext
import com.treinos.app.ui.progression.ProgressionAction
import com.treinos.app.ui.progression.ProgressionSuggestion
import com.treinos.app.ui.progression.PersonalRecord
import com.treinos.app.ui.progression.personalRecord
import com.treinos.app.ui.progression.REVIEW_POSTPONE_WEEKS
import com.treinos.app.ui.progression.completedSessionsSincePlan
import com.treinos.app.ui.progression.cycleWeek
import com.treinos.app.ui.progression.deloadSuggested
import com.treinos.app.ui.progression.progressionHistory
import com.treinos.app.ui.progression.suggestProgression
import com.treinos.app.ui.history.ExerciseHistory
import com.treinos.app.ui.history.buildExerciseHistory
import com.treinos.app.ui.measurements.latestMeasurement
import com.treinos.app.ui.formatKg
import com.treinos.app.ui.progressText
import com.treinos.app.ui.programs.buildProgram
import com.treinos.app.ui.programs.programCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoField
import com.treinos.protocol.WearCommand
import com.treinos.protocol.WearCommandKind
import com.treinos.protocol.WearWorkoutState
import com.treinos.app.updater.ReleaseChecker
import com.treinos.app.updater.ReleaseParser
import com.treinos.app.updater.UpdateInstaller
import com.treinos.app.updater.UpdateState
import com.treinos.app.updater.VersionCompare
import com.treinos.app.R
import com.treinos.app.notify.RestNotifier
import com.treinos.app.notify.RestNotifierPolicy
import com.treinos.app.widget.TodayWidget

enum class Screen {
    Home,
    Generate,
    Workout,
    RunWorkout,
    History,
    Settings,
    LogActivity,
    Mobility,
    Measurements,
}

/**
 * Recurso do motivo do fallback local (H3-21).
 *
 * Os códigos são os valores fechados do contrato (`fallbackReason`):
 * `no_api_key`, `http_error`, `timeout`, `json_parse`, `unresolved_ids`,
 * `schema_validation` e `unknown`. Retorna null quando não há motivo —
 * plano veio da IA (`source == "ai"`) ou o backend é antigo sem o campo.
 *
 * H3-52: a função devolve o recurso; quem chama resolve com `getString`
 * (fora da composição, o texto acompanha o idioma escolhido).
 */
fun fallbackReasonLabelRes(reason: String?): Int? = when (reason) {
    null, "" -> null
    "no_api_key" -> R.string.fallback_no_api_key
    "http_error" -> R.string.fallback_http_error
    "timeout" -> R.string.fallback_timeout
    "json_parse" -> R.string.fallback_json_parse
    "unresolved_ids" -> R.string.fallback_unresolved_ids
    "schema_validation" -> R.string.fallback_schema_validation
    else -> R.string.fallback_unknown
}

/**
 * H4-8: rascunho do formulário de medida — textos como digitados (vírgula
 * decimal aceita). O parse e a validação ficam no [AppViewModel.saveMeasurement].
 */
data class MeasurementDraft(
    val measuredAtMillis: Long,
    val weightKg: String = "",
    val bodyFatPercent: String = "",
    val waistCm: String = "",
    val chestCm: String = "",
    val armCm: String = "",
    val thighCm: String = "",
    val hipCm: String = "",
    val notes: String = "",
)

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
    private val restNotifier: RestNotifier? = null,
    /** H4-9: gateway do Health Connect; `null` quando indisponível. */
    private val healthGateway: HealthConnectGateway? = null,
) : ViewModel() {

    // H3-35: atalho dinâmico "Retomar treino".
    private fun publishResumeShortcut(session: com.treinos.app.data.local.SessionWithExercises?) {
        if (session != null) {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                // H4-14: o widget "Retomar treino" reusa o mesmo extra do atalho.
                putExtra(MainActivity.EXTRA_RESUME_SESSION, true)
            }
            val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID_RESUME)
                .setShortLabel(context.getString(com.treinos.app.R.string.shortcut_resume_short_label))
                .setLongLabel(context.getString(com.treinos.app.R.string.shortcut_resume_long_label))
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
                _message.value = context.getString(R.string.msg_extra_plan_deleted)
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, context, R.string.msg_plan_delete_error)
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

    /**
     * H4-6: exercícios do plano do dia da sessão em andamento, na ordem do
     * dia. O agrupamento de superset é derivado deste plano vivo: plano
     * regenerado ou excluído no meio da sessão esvazia a lista e a UI degrada
     * sem rótulos e com o descanso normal.
     */
    val activeSessionPlanExercises: StateFlow<List<WorkoutPlanExerciseEntity>> = combine(
        activePlans,
        inProgressSession,
    ) { plans, session ->
        if (session == null) {
            emptyList()
        } else {
            plans.firstOrNull { it.plan.id == session.session.planId }
                ?.days
                ?.firstOrNull { it.day.id == session.session.dayId }
                ?.exercises
                .orEmpty()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    /**
     * H4-12: exercícios da sessão em andamento com sugestão aplicável —
     * sugestão com `action != NONE` **e** par de plano válido no dia (mesmo
     * `exerciseOrder` e mesmo `exerciseId`; exercício trocado só nesta
     * sessão fica de fora). O id é o do exercício da SESSÃO.
     */
    val applicableProgressions: StateFlow<Set<Long>> = combine(
        inProgressSession,
        activeSessionPlanExercises,
        progressionSuggestions,
    ) { session, planExercises, suggestions ->
        if (session == null) {
            emptySet()
        } else {
            session.exercises.mapNotNull { exercise ->
                val sugestao = suggestions[exercise.exercise.exerciseId]
                val casado = planExercises.any {
                    it.exerciseOrder == exercise.exercise.exerciseOrder &&
                        it.exerciseId == exercise.exercise.exerciseId
                }
                if (sugestao != null && sugestao.action != ProgressionAction.NONE && casado) {
                    exercise.exercise.id
                } else {
                    null
                }
            }.toSet()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * H4-12: id do exercício da SESSÃO → carga-alvo aplicada ao plano. O par
     * casa por `exerciseOrder` + `exerciseId`, então o alvo do slot do plano
     * não vaza para o substituto de uma troca THIS_SESSION.
     */
    val planTargetWeights: StateFlow<Map<Long, Double>> = combine(
        inProgressSession,
        activeSessionPlanExercises,
    ) { session, planExercises ->
        if (session == null) {
            emptyMap()
        } else {
            session.exercises.mapNotNull { exercise ->
                val alvo = planExercises
                    .firstOrNull {
                        it.exerciseOrder == exercise.exercise.exerciseOrder &&
                            it.exerciseId == exercise.exercise.exerciseId
                    }
                    ?.targetWeightKg
                    ?: return@mapNotNull null
                exercise.exercise.id to alvo
            }.toMap()
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
     * H3-53: resumo do treino recém-finalizado, exibido como card na Home.
     * Efêmero por decisão: vive só em memória, aparece uma vez por treino
     * finalizado e some com "Fechar" (a morte do processo não o restaura).
     */
    private val _postWorkoutSummary = MutableStateFlow<WorkoutSummary?>(null)
    val postWorkoutSummary: StateFlow<WorkoutSummary?> = _postWorkoutSummary.asStateFlow()

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

    /**
     * H4-1: recorde pessoal por exercício, derivado das séries registradas.
     * Só leitura — nada é gravado ao calcular; modo avião não muda o resultado.
     */
    val personalRecords: StateFlow<Map<String, PersonalRecord>> = sessions.map { list ->
        val ids = linkedSetOf<String>()
        list.forEach { session ->
            session.exercises.forEach { exercise ->
                ids.add(exercise.exercise.exerciseId)
            }
        }
        ids.mapNotNull { id -> personalRecord(list, id)?.let { id to it } }.toMap()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** H4-1: recorde do exercício aberto no `ExerciseDetailSheet`. */
    val selectedExerciseRecord: StateFlow<PersonalRecord?> = combine(
        sessions,
        _selectedExerciseId,
    ) { list, exerciseId ->
        exerciseId?.let { personalRecord(list, it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** H4-3: id do exercício aberto no drill-down do Histórico; `null` = folha fechada. */
    private val _exerciseHistoryId = MutableStateFlow<String?>(null)
    val exerciseHistoryId: StateFlow<String?> = _exerciseHistoryId.asStateFlow()

    /**
     * H4-3: histórico do exercício aberto, derivado das sessões já carregadas
     * — a agregação acontece aqui, nunca dentro do composable (sem recalcular
     * por frame). Só leitura: abrir/fechar a folha não grava nada.
     */
    val exerciseHistory: StateFlow<ExerciseHistory?> = combine(
        sessions,
        _exerciseHistoryId,
    ) { list, exerciseId ->
        exerciseId?.let { buildExerciseHistory(list, it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Abre o drill-down do exercício. Regra 16 da H4-3: o `ExerciseDetailSheet`
     * fecha antes — abrir o histórico a partir do detalhe nunca empilha duas
     * folhas.
     */
    fun openExerciseHistory(exerciseId: String?) {
        if (exerciseId.isNullOrBlank()) return
        _selectedExerciseId.value = null
        _selectedPlanExercise.value = null
        _exerciseHistoryId.value = exerciseId
    }

    fun closeExerciseHistory() {
        _exerciseHistoryId.value = null
    }

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

    /**
     * H3-58: app em segundo plano? A notificação do descanso só existe nesse
     * estado; ao voltar, é cancelada. Sinalizado pela MainActivity.
     */
    private var isAppInBackground = false

    /**
     * H3-58: contador de pedidos de permissão de notificação. Incrementa quando
     * um descanso começa; a MainActivity observa e executa o pedido (API 33+).
     */
    private val _notificationPermissionRequests = MutableStateFlow(0)
    val notificationPermissionRequest: StateFlow<Int> = _notificationPermissionRequests.asStateFlow()

    // ---------------- Health Connect (H4-9) ----------------

    /** H4-9: estado da seção de Health Connect em Ajustes. */
    private val _healthConnectState = MutableStateFlow(HealthConnectUiState())
    val healthConnectState: StateFlow<HealthConnectUiState> = _healthConnectState.asStateFlow()

    private val _healthConnectMessage = MutableStateFlow<String?>(null)
    val healthConnectMessage: StateFlow<String?> = _healthConnectMessage.asStateFlow()

    /**
     * H4-9: contador de pedidos de permissão do Health Connect. Incrementa
     * quando o usuário toca "Conectar"; a MainActivity observa e executa o
     * contract do sistema (mesmo padrão do `POST_NOTIFICATIONS` da H3-58).
     */
    private val _healthPermissionRequests = MutableStateFlow(0)
    val healthPermissionRequest: StateFlow<Int> = _healthPermissionRequests.asStateFlow()

    /** H4-9: sessão cuja escrita falhou; o "Tentar de novo" reenvia (regra 8). */
    private var pendingHealthWriteSessionId: Long? = null

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
        restorePersistedMobility()
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
            // H4-16: lotes de séries registradas offline no relógio.
            viewModelScope.launch {
                wearSyncRepository!!.observeSyncBatches().collect { batch ->
                    applyWearSyncBatch(batch)
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
        // H4-14: widget da tela inicial acompanha sessão em andamento e planos
        // (início, fim, abandono, import/geração de plano). Protegido por
        // runCatching: sem host de widget a chamada não pode derrubar o app.
        viewModelScope.launch {
            inProgressSession.collect {
                runCatching { TodayWidget.updateAll(context) }
            }
        }
        viewModelScope.launch {
            activePlans.collect {
                runCatching { TodayWidget.updateAll(context) }
            }
        }
        // H4-9: estado inicial da seção de Health Connect (nunca crasha —
        // indisponível vira UNAVAILABLE e a seção nem aparece).
        refreshHealthConnect()
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
                        // H4-4: pulso do relógio continua registrando série normal,
                        // sem tipo nem RIR (fora de escopo na spec).
                        input = SetInput(
                            reps = command.reps ?: ultimoSet?.reps,
                            weightKg = command.weightKg ?: ultimoSet?.weightKg,
                            rpe = command.rpe,
                        ),
                    )
                }
            }
            WearCommandKind.SKIP_REST -> skipRest()
            WearCommandKind.FINISH_WORKOUT -> finishWorkout()
        }
    }

    /**
     * H4-16: aplica o lote de séries registradas offline no relógio — aditivo
     * e idempotente (dedupe por `clientSetId` no repositório). Lote de sessão
     * diferente da ativa é ignorado e o celular reenvia o estado atual; lote
     * de sessão já finalizada é rejeitado do mesmo jeito (o estado reenviado
     * tem status != in_progress e o relógio limpa as pendentes daquela
     * sessão). Falha de gravação vira mensagem, nunca crash.
     */
    private fun applyWearSyncBatch(batch: com.treinos.protocol.WearSyncBatch) {
        if (batch.protocolVersion != 1) return
        val activeSession = inProgressSession.value
        if (activeSession == null || batch.sessionId != activeSession.session.id) {
            val session = activeSession ?: return
            publishWearState(session, _exerciseIndex.value)
            return
        }
        viewModelScope.launch {
            try {
                for (set in batch.sets) {
                    val exercicio = activeSession.exercises.firstOrNull {
                        it.exercise.id == set.sessionExerciseId
                    } ?: activeSession.exercises.getOrNull(set.exerciseIndex)
                    ?: continue
                    repository.logSet(
                        exerciseLogId = exercicio.exercise.id,
                        reps = set.reps,
                        weightKg = set.weightKg,
                        rpe = set.rpe,
                        // H4-4: série do relógio é sempre normal, sem RIR.
                        setType = com.treinos.app.data.local.SET_TYPE_NORMAL,
                        rir = null,
                    )
                }
                if (batch.finished) {
                    if (_activeSessionId.value == batch.sessionId) {
                        // Sessão aberta na UI: caminho completo (mensagem,
                        // Health Connect best-effort, limpeza do estado do
                        // relógio).
                        finishWorkout()
                    } else {
                        // App recém-aberto (nenhuma sessão ativa na UI): o
                        // fim do treino offline ainda precisa ser gravado.
                        repository.finishSession(batch.sessionId)
                    }
                }
            } catch (e: Exception) {
                // Falha de gravação do lote: nunca crash — o relógio já
                // considerou o lote entregue (MessageClient) e o dedupe por
                // clientSetId impede duplicação em reenvio.
                android.util.Log.w("AppViewModel", "Falha ao aplicar lote do relógio", e)
            }
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
            // H4-16: snapshot da sessão do dia — o relógio guarda no Room
            // próprio e treina sem o celular (modo standalone).
            wearSyncRepository!!.publishSnapshot(com.treinos.app.data.wear.WearSessionMapper.toSnapshot(session))
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
                    _catalogMessage.value = context.getString(R.string.msg_catalog_updated, now)
                }
            } catch (error: Exception) {
                if (forceRefresh) {
                    _catalogMessage.value = uiErrorMessage(error, context, R.string.msg_catalog_update_error)
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
        context = context,
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

    /** H4-8: abrir "Peso e medidas" limpa mensagem antiga — entrar é nova tentativa. */
    fun openMeasurements() {
        _measurementMessage.value = null
        _backTarget.value = _screen.value
        _screen.value = Screen.Measurements
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
                    _message.value = validation.error ?: context.getString(R.string.msg_profile_invalid)
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
                        _message.value = complementary.error ?: context.getString(R.string.msg_complementary_invalid)
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
                _message.value = context.getString(R.string.msg_generation_submitted)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Falha ao submeter geração", error)
                _generateError.value = uiErrorFrom(error, context, R.string.verb_generate_workout)
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
                            val source = if (result.source == "ai") {
                                context.getString(R.string.msg_source_ai)
                            } else {
                                context.getString(R.string.msg_source_local)
                            }
                            val review = formatReviewDate(result.nextReviewAt) ?: result.nextReviewAt
                            _message.value = fallbackReasonLabelRes(fallbackReason)
                                ?.let { motivoRes ->
                                    context.getString(
                                        R.string.msg_plan_generated_reason,
                                        source,
                                        review,
                                        context.getString(motivoRes),
                                    )
                                }
                                ?: context.getString(R.string.msg_plan_generated, source, review)
                        } catch (error: Exception) {
                            // O job está done no backend; falha local ao salvar não
                            // deve manter o envelope travado.
                            Log.w(TAG, "Falha ao salvar plano do job concluído", error)
                            _generateError.value = uiErrorFrom(error, context, R.string.verb_save_workout)
                        }
                        clearPendingEnvelope()
                    } ?: run {
                        _message.value = context.getString(R.string.msg_job_no_plan)
                        clearPendingEnvelope()
                    }
                }
                "failed" -> {
                    // Mantém o envelope para o retry ("Tentar de novo").
                    _generateError.value = uiErrorFrom(
                        Exception(status.error ?: context.getString(R.string.msg_generation_failed)),
                        context,
                        R.string.verb_generate_workout,
                    )
                }
                "cancelled" -> {
                    clearPendingEnvelope()
                    _message.value = context.getString(R.string.msg_generation_cancelled)
                }
                else -> {
                    // pending/running: ainda em andamento, nada a fazer.
                }
            }
        } catch (error: Exception) {
            // 404 (job sumido) ou erro de rede: falha com retry, envelope mantido.
            Log.w(TAG, "Falha ao consultar status do job", error)
            _generateError.value = uiErrorFrom(error, context, R.string.verb_check_generation)
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
        isAppInBackground = false
        syncRestNotification()
        if (_pendingGeneration.value != null) {
            viewModelScope.launch {
                refreshPendingGeneration()
            }
        }
    }

    /**
     * Chamado pelo onStop da MainActivity (H3-58): com descanso ativo, posta a
     * notificação com o restante atual — sem tick por segundo.
     */
    fun onAppBackgrounded() {
        isAppInBackground = true
        syncRestNotification()
    }

    /**
     * H3-58: recalcula a notificação do descanso a partir do estado atual.
     * Chamado só em transições (ir/voltar do background, zerar, pular, pausar,
     * retomar) — nunca a cada tick do descanso.
     */
    private fun syncRestNotification() {
        val notifier = restNotifier ?: return
        notifier.apply(
            RestNotifierPolicy.decide(
                remainingSeconds = _restRemaining.value,
                paused = _restPaused.value,
                inBackground = isAppInBackground,
                notificationsEnabled = notifier.notificationsEnabled(),
            )
        )
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

    // ---------------- Programas prontos (H4-10) ----------------

    /**
     * Aplica um programa pronto do catálogo local — caminho 100% offline:
     * monta o [GeneratePlanResponse] no aparelho, salva como `main` (o main
     * anterior é desativado; extras e histórico continuam), seleciona o plano
     * e emite snackbar. Nunca chama [submitGeneration]: sem backend, sem
     * envelope pendente e sem mexer em `lastFallbackReason`.
     */
    fun applyProgram(templateId: String) {
        val template = programCatalog().firstOrNull { it.id == templateId }
        if (template == null) {
            _message.value = context.getString(R.string.msg_program_unknown)
            return
        }
        viewModelScope.launch {
            try {
                val response = buildProgram(templateId, nextReviewAt(reviewAfterWeeks = 4))
                if (response == null) {
                    _message.value = context.getString(R.string.msg_program_unknown)
                    return@launch
                }
                val planId = repository.saveGeneratedPlan(response, kind = "main")
                // O programa aplicado é o novo main (H3-45/H3-15).
                _selectedPlanId.value = planId
                _screen.value = Screen.Home
                _message.value = context.getString(R.string.msg_program_applied, template.name)
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, context, R.string.msg_program_apply_error)
            }
        }
    }

    // ---------------- Revisão de plano (H2-6) ----------------

    fun reviewPlan() {
        val plan = mainPlan() ?: run {
            _message.value = context.getString(R.string.msg_no_plan_to_review)
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
                        context.getString(R.string.msg_review_no_changes)
                    } else {
                        context.getString(
                            R.string.msg_review_proposed,
                            response.changes.joinToString(" • "),
                        )
                    }
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, context, R.string.msg_review_error)
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
                _message.value = context.getString(R.string.msg_review_applied)
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, context, R.string.msg_review_apply_error)
            }
        }
    }

    fun discardReview() {
        pendingReview = null
        _reviewChanges.value = emptyList()
        _reviewMessage.value = null
        _message.value = context.getString(R.string.msg_review_discarded)
    }

    fun clearReviewMessage() {
        _reviewMessage.value = null
    }

    // ---------------- Progressão de carga e ciclo (H3-5) ----------------

    fun postponeReview(planId: Long, weeks: Int = REVIEW_POSTPONE_WEEKS) {
        viewModelScope.launch {
            try {
                repository.postponeReview(planId, weeks)
                _message.value = context.getString(R.string.msg_review_postponed, weeks)
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, context, R.string.msg_review_postpone_error)
            }
        }
    }

    // ---------------- Execução ----------------

    fun startWorkout(dayId: Long) {
        val plan = activePlan.value ?: run {
            _message.value = context.getString(R.string.msg_no_plan_on_device)
            return
        }
        val day = plan.days.firstOrNull { it.day.id == dayId }
            ?: run {
                _message.value = context.getString(R.string.msg_workout_day_not_found)
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
                _message.value = uiErrorMessage(error, context, R.string.msg_start_workout_error)
            }
        }
    }

    fun leaveWorkout() {
        _screen.value = Screen.Home
        _message.value = context.getString(R.string.msg_workout_left_open)
    }

    /**
     * H4-14: inicia o treino de hoje direto do widget. Espera o plano ativo
     * convergir com teto de 5 s (cold start do processo); sem plano, dia de
     * descanso ou dia flexível mostra mensagem em vez de travar. Sessão já em
     * andamento (rotação reentregando o extra) retoma em vez de iniciar outra.
     */
    fun startTodayWorkout() {
        viewModelScope.launch {
            inProgressSession.value?.let {
                resumeWorkout()
                return@launch
            }
            val plan = activePlan.value
                ?: withTimeoutOrNull(5_000) { activePlan.first { it != null } }
            if (plan == null) {
                _message.value = context.getString(R.string.msg_no_workout_today)
                return@launch
            }
            val todayWeekday = LocalDate.now().get(ChronoField.DAY_OF_WEEK)
            when (val today = todaySessionSelector(plan.days, todayWeekday)) {
                is TodaySession.Today -> startWorkout(today.day.day.id)
                else -> _message.value = context.getString(R.string.msg_no_workout_today)
            }
        }
    }

    fun resumeWorkout() {
        val session = inProgressSession.value ?: run {
            _message.value = context.getString(R.string.msg_no_workout_in_progress)
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
        input: SetInput,
    ) {
        viewModelScope.launch {
            try {
                repository.logSet(
                    exerciseLogId = exercise.id,
                    reps = input.reps,
                    weightKg = input.weightKg,
                    rpe = input.rpe,
                    setType = input.setType,
                    rir = input.rir,
                )
                // H4-6: membro de superset que não é o último do grupo descansa
                // a transição em vez do descanso cheio do plano.
                startRest(restAfterSet(activeSessionPlanExercises.value, exercise.exerciseId, exercise.restSeconds))
                syncWearState()
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, context, R.string.msg_save_set_error)
            }
        }
    }

    fun logSet(
        exercise: ExerciseSessionData,
        input: SetInput,
    ) {
        viewModelScope.launch {
            try {
                repository.logSet(
                    exerciseLogId = exercise.sessionExerciseId,
                    reps = input.reps,
                    weightKg = input.weightKg,
                    rpe = input.rpe,
                    setType = input.setType,
                    rir = input.rir,
                )
                // H4-6: mesma regra do caminho da tela — o LOG_SET do relógio
                // passa por aqui e usa o descanso de transição do superset.
                startRest(restAfterSet(activeSessionPlanExercises.value, exercise.exerciseId, exercise.restSeconds))
                syncWearState()
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, context, R.string.msg_save_set_error)
            }
        }
    }

    /**
     * H4-6: liga/desliga o superset com o exercício anterior. A flag vive no
     * plano (`WorkoutPlanExerciseEntity`): vale para a sessão atual e para os
     * próximos treinos do dia. Alternar para o estado já gravado é no-op.
     */
    fun toggleSupersetWithPrevious(sessionExerciseId: Long, enabled: Boolean) {
        val sessionExercise = inProgressSession.value?.exercises
            ?.firstOrNull { it.exercise.id == sessionExerciseId }
            ?.exercise ?: return
        val planExercise = activeSessionPlanExercises.value
            .firstOrNull { it.exerciseId == sessionExercise.exerciseId } ?: return
        if (planExercise.isSupersetWithPrevious == enabled) return
        viewModelScope.launch {
            try {
                repository.setSupersetWithPrevious(planExercise.id, enabled)
            } catch (error: Exception) {
                Log.w(TAG, "Falha ao alternar superset", error)
            }
        }
    }

    /**
     * H4-11: grava a nota da sessão em andamento (`workout_sessions.notes`).
     * Sem sessão ativa (`_activeSessionId` nulo e sem `in_progress`) emite
     * mensagem de erro e não grava. O relógio não mostra notas, então
     * `syncWearState` não é necessário.
     */
    fun updateSessionNotes(notes: String) {
        val sessionId = _activeSessionId.value
            ?: inProgressSession.value?.session?.id
            ?: run {
                _message.value = context.getString(R.string.msg_no_workout_in_progress)
                return
            }
        viewModelScope.launch {
            try {
                repository.updateSessionNotes(sessionId, normalizeNote(notes))
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, context, R.string.msg_note_error)
            }
        }
    }

    /**
     * H4-11: grava a nota do exercício no slot do PLANO com o mesmo
     * `exerciseOrder` da sessão — a troca THIS_SESSION (H3-41) mantém a ordem,
     * então a nota acompanha o slot do plano e reaparece nas próximas sessões
     * do dia. Dia/exercício não encontrado vira mensagem de erro.
     */
    fun updateExerciseNotes(sessionExercise: SessionExerciseEntity, notes: String) {
        val sessionId = _activeSessionId.value
            ?: inProgressSession.value?.session?.id
            ?: run {
                _message.value = context.getString(R.string.msg_no_workout_in_progress)
                return
            }
        viewModelScope.launch {
            try {
                val session = repository.observeSession(sessionId).first()?.session
                    ?: error("Sessão não encontrada no aparelho.")
                repository.updatePlanExerciseNotes(
                    dayId = session.dayId,
                    exerciseOrder = sessionExercise.exerciseOrder,
                    notes = normalizeNote(notes),
                )
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, context, R.string.msg_note_error)
            }
        }
    }

    /**
     * H4-12: aplica a sugestão do `ProgressionEngine` ao plano com um toque.
     * Só este caminho grava o alvo — abrir a tela, ver a sugestão ou registrar
     * série não muda o plano. Exercício trocado nesta sessão ou plano excluído
     * não gravam (`ExerciseNotInPlan`). Não toca em `nextReviewAt` nem dispara
     * `syncWearState` — o relógio não mostra o alvo do plano.
     */
    fun applyProgression(exercise: SessionExerciseWithSets) {
        val sessionId = _activeSessionId.value
            ?: inProgressSession.value?.session?.id
            ?: run {
                _message.value = context.getString(R.string.msg_no_workout_in_progress)
                return
            }
        val sugestao = progressionSuggestions.value[exercise.exercise.exerciseId] ?: return
        viewModelScope.launch {
            try {
                val session = repository.observeSession(sessionId).first()?.session
                    ?: error("Sessão não encontrada no aparelho.")
                when (val result = repository.applyProgressionToPlanExercise(
                    dayId = session.dayId,
                    exerciseOrder = exercise.exercise.exerciseOrder,
                    exerciseId = exercise.exercise.exerciseId,
                    suggestion = sugestao,
                )) {
                    is ProgressionApplyResult.Applied -> {
                        val alvo = sugestao.suggestedWeightKg?.let { formatKg(it) }
                            ?: context.getString(R.string.workout_reps_range_short, result.targetReps)
                        _message.value = context.getString(
                            R.string.msg_progression_applied,
                            alvo,
                            exercise.exercise.exerciseName.ifBlank { exercise.exercise.exerciseId },
                        )
                    }
                    ProgressionApplyResult.NothingToApply,
                    ProgressionApplyResult.ExerciseNotInPlan,
                    -> _message.value = context.getString(R.string.msg_progression_apply_error)
                }
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, context, R.string.msg_progression_apply_error)
            }
        }
    }

    fun finishWorkout() {
        val sessionId = _activeSessionId.value ?: return
        viewModelScope.launch {
            try {
                repository.finishSession(sessionId)
                // H4-9: escrita no Health Connect é best-effort e roda em
                // coroutine própria — falha nunca bloqueia o fim do treino
                // (regra 8); a sessão continua salva no Room.
                viewModelScope.launch {
                    try {
                        repository.writeCompletedSessionToHealth(sessionId)
                    } catch (error: Exception) {
                        pendingHealthWriteSessionId = sessionId
                        _healthConnectMessage.value = healthConnectErrorMessage(error)
                        Log.w(TAG, "Falha ao enviar sessão ao Health Connect", error)
                    }
                }
                skipRest()
                _activeSessionId.value = null
                _screen.value = Screen.Home
                // H3-53: lê a sessão já finalizada pelo `id` (com a duração
                // gravada por `finishSession`) em vez do fluxo
                // `inProgressSession`, que sai de "in_progress" e viria nulo.
                // Só musculação gera card de séries; corrida e mobilidade ficam
                // fora de escopo.
                _postWorkoutSummary.value = repository.observeSession(sessionId).first()
                    ?.takeIf { SessionKind.fromModality(it.session.modality) == SessionKind.STRENGTH }
                    ?.let { WorkoutSummary.from(it) }
                _message.value = context.getString(R.string.msg_workout_finished)
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, context, R.string.msg_finish_workout_error)
            }
        }
    }

    /** H3-53: "Fechar" do card de resumo pós-treino na Home. */
    fun dismissPostWorkoutSummary() {
        _postWorkoutSummary.value = null
    }

    /**
     * Edita uma série já registrada (H3-27): o mesmo `id` é atualizado com
     * reps/carga/RPE/tipo/RIR novos (H4-4) e o estado do relógio é reenviado.
     */
    fun updateSet(set: SetLogEntity, input: SetInput) {
        viewModelScope.launch {
            try {
                repository.updateSet(
                    set.copy(
                        reps = input.reps,
                        weightKg = input.weightKg,
                        rpe = input.rpe,
                        setType = input.setType,
                        rir = input.rir,
                    )
                )
                syncWearState()
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, context, R.string.msg_update_set_error)
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
                _message.value = uiErrorMessage(error, context, R.string.msg_delete_set_error)
            }
        }
    }

    /**
     * H3-54: edita uma série de sessão concluída direto do Histórico. O
     * repositório recusa sessão em andamento; o relógio não é reenviado
     * porque ele espelha a sessão em andamento, que não mudou.
     */
    fun updateHistorySet(set: SetLogEntity, input: SetInput) {
        viewModelScope.launch {
            try {
                repository.updateHistorySet(
                    set.copy(
                        reps = input.reps,
                        weightKg = input.weightKg,
                        rpe = input.rpe,
                        setType = input.setType,
                        rir = input.rir,
                    )
                )
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, context, R.string.msg_update_set_error)
            }
        }
    }

    /**
     * H3-54: exclui uma série de sessão concluída direto do Histórico. Sem
     * séries, o exercício sai da sessão e as métricas recalculam pelo Room.
     */
    fun deleteHistorySet(set: SetLogEntity) {
        viewModelScope.launch {
            try {
                repository.deleteHistorySet(set.id)
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, context, R.string.msg_delete_set_error)
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
                _message.value = context.getString(R.string.msg_workout_abandoned)
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, context, R.string.msg_abandon_error)
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
                _message.value = context.getString(R.string.msg_run_finished)
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, context, R.string.msg_finish_run_error)
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
                _message.value = context.getString(R.string.msg_mobility_finished)
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, context, R.string.msg_save_mobility_error)
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
            _message.value = context.getString(R.string.msg_only_manual_editable)
            return
        }
        // H3-56: ao entrar em edição o `prefill` vence; o rascunho salvo não
        // deve vazar para a edição de uma sessão antiga.
        preferences.activityDraftV1 = null
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
                    _message.value = context.getString(R.string.msg_activity_updated)
                } else {
                    repository.logActivity(input.toManualSession())
                    _message.value = context.getString(R.string.msg_activity_saved)
                }
                // H3-56: registro concluído — o rascunho some.
                preferences.activityDraftV1 = null
                _activityEditingId.value = null
                _activityPrefill.value = null
                // H3-38: salvar edição retorna à origem (Histórico quando veio dele).
                _screen.value = _backTarget.value ?: Screen.Home
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, context, R.string.msg_save_activity_error)
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
                    // H3-56: confirmar "Excluir" da edição também limpa o rascunho.
                    preferences.activityDraftV1 = null
                    _screen.value = Screen.History
                }
                _message.value = context.getString(R.string.msg_activity_deleted)
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, context, R.string.msg_delete_activity_error)
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
        persistMobilityState(routine, session)
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
        val routine = current.routine ?: return
        val session = current.session ?: return
        val toggled = MobilitySessionEngine.togglePause(session)
        _mobilityState.value = current.copy(session = toggled)
        persistMobilityState(routine, toggled)
    }

    fun mobilityPreviousMovement() {
        val current = _mobilityState.value
        val routine = current.routine ?: return
        val session = current.session ?: return
        if (session.movementIndex == 0) return
        val next = MobilitySessionEngine.goTo(session, routine, session.movementIndex - 1) ?: return
        _mobilityState.value = current.copy(session = next)
        persistMobilityState(routine, next)
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
        persistMobilityState(routine, next)
    }

    /** Limpa a rotina em andamento (finalização ou descarte confirmado). */
    fun clearMobilitySession() {
        mobilityJob?.cancel()
        mobilityJob = null
        clearPersistedMobility()
        _mobilityState.value = MobilityScreenState()
    }

    private fun startMobilityTicker() {
        mobilityJob?.cancel()
        mobilityJob = viewModelScope.launch {
            var lastMovementIndex = -1
            while (true) {
                delay(1_000)
                val current = _mobilityState.value
                val session = current.session ?: break
                if (!session.running) break
                if (session.paused) continue
                val routine = current.routine ?: break
                val ticked = MobilitySessionEngine.tick(session, routine)
                _mobilityState.value = current.copy(session = ticked)
                // Persiste quando o movimento muda (incluindo transição → próximo movimento)
                if (ticked.movementIndex != lastMovementIndex) {
                    persistMobilityState(routine, ticked)
                    lastMovementIndex = ticked.movementIndex
                }
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
                _message.value = context.getString(R.string.msg_mobility_finished)
                _screen.value = Screen.Home
            } catch (error: Exception) {
                _message.value = uiErrorMessage(error, context, R.string.msg_save_mobility_error)
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
        // H3-58: um descanso começou — momento contextual para pedir a permissão.
        _notificationPermissionRequests.value = _notificationPermissionRequests.value + 1
        syncRestNotification()
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
                // H3-58: zerou — em background, a notificação vira "concluído"
                // exatamente uma vez (o loop termina aqui).
                syncRestNotification()
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
        // H3-58: contagem congelada não tem notificação (nunca chegaria a zero).
        syncRestNotification()
    }

    fun resumeRest() {
        if (!_restPaused.value) return
        val remaining = pausedRemainingSeconds
        _restPaused.value = false
        val deadline = RestTimer.resume(remaining, System.currentTimeMillis()) ?: return
        preferences.restDeadlineAtMillis = deadline
        restJob = viewModelScope.launch { tickRest() }
        syncRestNotification()
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
        // H3-58: estender limpa a notificação (mesmos caminhos da H3-26).
        restNotifier?.cancel()
    }

    private fun clearRestState() {
        _restRemaining.value = null
        _restTotal.value = null
        _restPaused.value = false
        pausedRemainingSeconds = 0
        preferences.restDeadlineAtMillis = 0L
        // H3-58: pular/finalizar limpa a notificação, inclusive via Wear.
        syncRestNotification()
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

    /**
     * Processo morto com mobilidade em andamento: restaura o movimento e tempo
     * restante via deadline wall-clock (H3-55). Se a rotina não existir mais no
     * catálogo, descarta o estado salvo.
     */
    private fun restorePersistedMobility() {
        val routineId = preferences.mobilityRoutineId ?: return
        val movementIndex = preferences.mobilityMovementIndex
        val deadline = preferences.mobilityMovementDeadlineAtMillis
        val startedAt = preferences.mobilityStartedAtMillis
        if (deadline <= 0L || startedAt <= 0L) {
            // Resíduo sem deadline (sessão já encerrada): nada para retomar.
            clearPersistedMobility()
            return
        }

        val routine = MobilityRoutineTemplates.getRoutine(routineId) ?: run {
            // Rotina removida do catálogo: descarta estado salvo.
            clearPersistedMobility()
            return
        }

        val saved = MobilitySessionEngine.SavedMobilityState(
            routineId = routineId,
            movementIndex = movementIndex,
            movementDeadlineAtMillis = deadline,
            startedAtMillis = startedAt,
        )
        val now = System.currentTimeMillis()
        val restored = MobilitySessionEngine.resume(routine, saved, now) ?: run {
            clearPersistedMobility()
            return
        }

        _mobilityState.value = MobilityScreenState(routine = routine, session = restored)
        startMobilityTicker()
    }

    /** Limpa as chaves de persistência da mobilidade (H3-55). */
    private fun clearPersistedMobility() {
        preferences.mobilityRoutineId = null
        preferences.mobilityMovementIndex = 0
        preferences.mobilityMovementDeadlineAtMillis = 0L
        preferences.mobilityStartedAtMillis = 0L
    }

    /** Persiste o estado atual da mobilidade (H3-55). */
    private fun persistMobilityState(routine: MobilityRoutine, session: MobilitySessionState) {
        // Na transição (H3-31) nenhum movimento começou: o estado persistido
        // continua sendo o do movimento que acabou de terminar — é o deadline
        // wall-clock dele que reconstrói a transição ao restaurar (H3-55).
        if (session.inTransition) return
        val now = System.currentTimeMillis()
        // Calcula o deadline do movimento atual: now + secondsRemaining
        val deadline = if (session.running && session.secondsRemaining > 0) {
            now + session.secondsRemaining.toLong() * 1000L
        } else {
            0L
        }
        // startedAt = now - elapsedSeconds
        val startedAt = now - session.elapsedSeconds * 1000L

        preferences.mobilityRoutineId = routine.id
        preferences.mobilityMovementIndex = session.movementIndex
        preferences.mobilityMovementDeadlineAtMillis = deadline
        preferences.mobilityStartedAtMillis = startedAt
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
                RestoreOutcome.InvalidDeviceId -> context.getString(R.string.msg_restore_invalid_id)
                RestoreOutcome.Empty ->
                    context.getString(R.string.msg_restore_empty, deviceId.trim())
                is RestoreOutcome.Failed -> outcome.message
                is RestoreOutcome.Success -> {
                    _deviceId.value = preferences.deviceId
                    _lastSyncAt.value = preferences.lastSyncAt
                    _restoreDeviceId.value = ""
                    context.getString(
                        R.string.msg_restore_success,
                        outcome.planCount,
                        outcome.sessionCount,
                        deviceId.trim(),
                    )
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
                    context.getString(
                        R.string.msg_connection_ok,
                        health.model.ifBlank { "—" },
                        health.catalogSize,
                    )
            } catch (error: Exception) {
                _connectionMessage.value = uiErrorMessage(error, context, R.string.msg_connection_error)
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
                    context.getString(R.string.msg_sync_done, result.plans, result.sessions, result.setLogs)
            } catch (error: Exception) {
                Log.w(TAG, "Falha ao enviar ao backend", error)
                _syncError.value = uiErrorFrom(error, context, R.string.verb_send_to_backend)
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
                        context.getString(
                            R.string.msg_download_restored,
                            result.planCount,
                            result.sessionCount,
                        )
                    }
                    SyncDownloadResult.Empty ->
                        context.getString(R.string.msg_download_empty)
                }
            } catch (error: Exception) {
                Log.w(TAG, "Falha ao baixar dados do backend", error)
                _downloadError.value = uiErrorFrom(error, context, R.string.verb_download_from_backend)
            } finally {
                _isDownloading.value = false
            }
        }
    }

    // ---------------- Backup JSON ----------------

    suspend fun exportBackupJson(appVersionName: String, appVersionCode: Long): String =
        repository.exportBackup(appVersionName, appVersionCode)

    /**
     * H3-8: liga [BackupImportState.Reading] ANTES de o seletor abrir o stream.
     * Um provedor remoto (Drive/OneDrive/Dropbox) baixa o arquivo durante
     * openInputStream e isso pode demorar — sem isso a tela parece travada até
     * a validação começar. Nenhum estado novo: só a transição que já existia.
     */
    fun beginBackupReading() {
        if (_backupImportState.value is BackupImportState.Importing) return
        _backupImportState.value = BackupImportState.Reading
    }

    /**
     * H3-8: a leitura do stream falhou antes de chegar à validação (provedor
     * offline, permissão revogada etc.). Só recua se o estado ainda é Reading —
     * nunca apaga Ready/Error/Importing nem o pendingBackupPayload.
     */
    fun abortBackupReading() {
        if (_backupImportState.value is BackupImportState.Reading) {
            _backupImportState.value = BackupImportState.Idle
        }
    }

    fun prepareImport(json: String) {
        if (_backupImportState.value is BackupImportState.Importing) return
        _backupMessage.value = null
        viewModelScope.launch {
            _backupImportState.value = BackupImportState.Reading
            try {
                val file = backupImportDecoder.decodeFromString(BackupFile.serializer(), json)
                val result = BackupValidator.validate(file)
                if (!result.valid) {
                    _backupImportState.value = BackupImportState.Error(result.message ?: context.getString(R.string.msg_backup_invalid))
                    pendingBackupPayload = null
                    return@launch
                }
                val preview = result.preview
                    ?: BackupPreview(0, 0, 0, 0, 0, 0)
                pendingBackupPayload = file
                _backupImportState.value = BackupImportState.Ready(preview = preview, payload = file)
            } catch (e: Exception) {
                _backupImportState.value = BackupImportState.Error(context.getString(R.string.msg_backup_file_invalid))
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
                    context.getString(R.string.msg_backup_imported_with_id, payload.deviceId.trim())
                } else {
                    context.getString(R.string.msg_backup_imported)
                }
                _backupImportState.value = BackupImportState.Idle
                pendingBackupPayload = null
            } catch (error: Exception) {
                _backupImportState.value = BackupImportState.Error(uiErrorMessage(error, context, R.string.msg_import_error))
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

    // ---------------- CSV (H4-7): exportar e importar Strong/Hevy ----------------

    private val _csvImportState = MutableStateFlow<CsvImportState>(CsvImportState.Idle)
    val csvImportState: StateFlow<CsvImportState> = _csvImportState.asStateFlow()

    private val _csvMessage = MutableStateFlow<String?>(null)
    val csvMessage: StateFlow<String?> = _csvMessage.asStateFlow()

    private var pendingCsvPlan: CsvImportPlan? = null

    suspend fun exportSessionsCsv(): String = repository.exportSessionsCsv()

    /**
     * Lê o texto do arquivo, detecta o formato só pelo cabeçalho e monta a
     * pré-visualização com contagens — nada é gravado aqui. Formato
     * desconhecido e arquivo sem nenhuma linha válida viram [CsvImportState.Error];
     * CSV vazio (só cabeçalho) vira `Ready` com zero sessões e o botão de
     * confirmar desabilitado.
     */
    fun prepareCsvImport(text: String) {
        if (_csvImportState.value is CsvImportState.Importing) return
        _csvMessage.value = null
        viewModelScope.launch {
            try {
                val rows = CsvCodec.parseCsv(text)
                val format = StrongHevyCsvParser.detectFormat(rows.firstOrNull().orEmpty())
                if (format == null) {
                    pendingCsvPlan = null
                    _csvImportState.value =
                        CsvImportState.Error(context.getString(R.string.csv_error_unknown_format))
                    return@launch
                }
                val plan = StrongHevyCsvParser.parse(rows)
                if (plan.sessions.isEmpty() && plan.skippedRows > 0) {
                    pendingCsvPlan = null
                    _csvImportState.value =
                        CsvImportState.Error(context.getString(R.string.csv_error_no_valid_rows))
                    return@launch
                }
                val preview = CsvImportPreview(
                    format = plan.format,
                    sessions = plan.sessions.size,
                    exercises = plan.sessions.sumOf { session -> session.exercises.size },
                    sets = plan.sessions.sumOf { session ->
                        session.exercises.sumOf { exercise -> exercise.sets.size }
                    },
                    duplicates = repository.csvDuplicates(plan),
                    skippedRows = plan.skippedRows,
                    warnings = plan.warnings,
                )
                pendingCsvPlan = plan
                _csvImportState.value = CsvImportState.Ready(preview = preview, plan = plan)
            } catch (error: Exception) {
                Log.w(TAG, "Falha ao preparar importação de CSV", error)
                pendingCsvPlan = null
                _csvImportState.value =
                    CsvImportState.Error(context.getString(R.string.csv_error_invalid_file))
            }
        }
    }

    /** Grava o plano pendente; a gravação é transacional e aditiva. */
    fun confirmCsvImport() {
        val plan = pendingCsvPlan ?: return
        if (_csvImportState.value is CsvImportState.Importing) return
        viewModelScope.launch {
            _csvImportState.value = CsvImportState.Importing
            try {
                val result = repository.importSessionsCsv(plan, preferences.deviceId)
                _csvMessage.value = context.getString(
                    R.string.msg_csv_imported,
                    result.sessionsImported,
                    result.setsImported,
                )
                _csvImportState.value = CsvImportState.Idle
                pendingCsvPlan = null
            } catch (error: Exception) {
                Log.w(TAG, "Falha ao importar CSV", error)
                _csvImportState.value =
                    CsvImportState.Error(uiErrorMessage(error, context, R.string.csv_error_import))
            }
        }
    }

    /** Cancelar (ou dispensar o erro) não altera nada. */
    fun cancelCsvImport() {
        _csvImportState.value = CsvImportState.Idle
        pendingCsvPlan = null
        _csvMessage.value = null
    }

    fun clearCsvMessage() {
        _csvMessage.value = null
    }

    fun setCsvMessage(message: String?) {
        _csvMessage.value = message
    }

    // ---------------- Peso e medidas (H4-8) ----------------

    val measurements: StateFlow<List<BodyMeasurementEntity>> = repository.observeMeasurements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _measurementMessage = MutableStateFlow<String?>(null)
    val measurementMessage: StateFlow<String?> = _measurementMessage.asStateFlow()

    /** Foto já anexada no formulário e ainda não gravada junto com a medida. */
    private val _pendingMeasurementPhoto = MutableStateFlow<String?>(null)
    val pendingMeasurementPhoto: StateFlow<String?> = _pendingMeasurementPhoto.asStateFlow()

    fun clearMeasurementMessage() {
        _measurementMessage.value = null
    }

    /**
     * Valida e grava a medida do formulário (regras 1–3). Devolve `true` quando
     * gravada — a tela limpa o formulário; `false` significa que nada foi
     * gravado e a mensagem explica o motivo.
     */
    suspend fun saveMeasurement(draft: MeasurementDraft): Boolean {
        val validation = MeasurementValidator.validate(
            measuredAt = draft.measuredAtMillis,
            weightKg = MeasurementValidator.parse(draft.weightKg),
            bodyFatPercent = MeasurementValidator.parse(draft.bodyFatPercent),
            waistCm = MeasurementValidator.parse(draft.waistCm),
            chestCm = MeasurementValidator.parse(draft.chestCm),
            armCm = MeasurementValidator.parse(draft.armCm),
            thighCm = MeasurementValidator.parse(draft.thighCm),
            hipCm = MeasurementValidator.parse(draft.hipCm),
            nowMillis = System.currentTimeMillis(),
        )
        if (!validation.valid) {
            _measurementMessage.value = validation.message
            return false
        }
        return try {
            repository.addMeasurement(
                BodyMeasurementEntity(
                    measuredAt = draft.measuredAtMillis,
                    weightKg = MeasurementValidator.parse(draft.weightKg),
                    bodyFatPercent = MeasurementValidator.parse(draft.bodyFatPercent),
                    waistCm = MeasurementValidator.parse(draft.waistCm),
                    chestCm = MeasurementValidator.parse(draft.chestCm),
                    armCm = MeasurementValidator.parse(draft.armCm),
                    thighCm = MeasurementValidator.parse(draft.thighCm),
                    hipCm = MeasurementValidator.parse(draft.hipCm),
                    photoPath = _pendingMeasurementPhoto.value,
                    notes = draft.notes.trim(),
                    source = MEASUREMENT_SOURCE_MANUAL,
                ),
            )
            _pendingMeasurementPhoto.value = null
            _measurementMessage.value = context.getString(R.string.msg_measurement_saved)
            true
        } catch (error: Exception) {
            Log.w(TAG, "Falha ao salvar medida corporal", error)
            _measurementMessage.value = uiErrorMessage(error, context, R.string.msg_measurement_save_error)
            false
        }
    }

    /**
     * Exclui a medida e, junto, o arquivo da foto (regra 7) — arquivo ausente
     * não é erro. Registrar/apagar medida nunca altera o perfil (regra 13).
     */
    fun deleteMeasurement(id: Long) {
        viewModelScope.launch {
            try {
                val measurement = measurements.value.firstOrNull { it.id == id } ?: return@launch
                repository.deleteMeasurement(id)
                measurement.photoPath?.let { path ->
                    withContext(Dispatchers.IO) { MeasurementPhotoStore.deletePhoto(context, path) }
                }
                _measurementMessage.value = context.getString(R.string.msg_measurement_deleted)
            } catch (error: Exception) {
                Log.w(TAG, "Falha ao excluir medida corporal", error)
                _measurementMessage.value = uiErrorMessage(error, context, R.string.msg_measurement_delete_error)
            }
        }
    }

    /**
     * Anexa a foto do formulário (regra 8). Da câmera (`file://` gravado pelo
     * TakePicture direto em `progress-photos/`) adota o arquivo; da galeria
     * (`content://`) copia os bytes para um arquivo novo na mesma pasta. A foto
     * nunca sai do aparelho.
     */
    fun attachMeasurementPhoto(uri: Uri) {
        viewModelScope.launch {
            try {
                val path = withContext(Dispatchers.IO) { adoptOrCopyPhoto(uri) }
                _pendingMeasurementPhoto.value = path
            } catch (error: Exception) {
                Log.w(TAG, "Falha ao anexar foto de progresso", error)
                _measurementMessage.value = context.getString(R.string.msg_measurement_photo_error)
            }
        }
    }

    /** Descarta a foto anexada que ainda não foi salva (apaga o arquivo). */
    fun removePendingMeasurementPhoto() {
        val path = _pendingMeasurementPhoto.value ?: return
        _pendingMeasurementPhoto.value = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) { MeasurementPhotoStore.deletePhoto(context, path) }
        }
    }

    private fun adoptOrCopyPhoto(uri: Uri): String {
        if (uri.scheme == "file") {
            val file = File(uri.path ?: error("URI de foto sem caminho"))
            if (file.parentFile?.absolutePath == MeasurementPhotoStore.photosDir(context).absolutePath) {
                return file.absolutePath
            }
        }
        val target = MeasurementPhotoStore.newPhotoFile(context)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Não foi possível abrir a imagem da foto")
        return target.absolutePath
    }

    /**
     * Regra 13: ação explícita do usuário — grava o último peso registrado no
     * `bodyWeightKg` do perfil. Registrar medida NÃO altera o perfil sozinho.
     */
    fun copyLatestWeightToProfile() {
        val weight = latestMeasurement(measurements.value)?.weightKg
        if (weight == null) {
            _measurementMessage.value = context.getString(R.string.msg_measurement_copy_empty)
            return
        }
        preferences.bodyWeightKg = weight
        _bodyWeightKg.value = preferences.bodyWeightKg
        _measurementMessage.value = context.getString(R.string.msg_measurement_copy_done, formatKg(weight))
    }

    fun startDiscovery() {
        if (_isDiscovering.value) return
        val manager = discoveryManager
        if (manager == null) {
            _discoveryMessage.value = context.getString(R.string.msg_discovery_unavailable)
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
                            context.getString(
                                R.string.msg_connected_to_backend,
                                outcome.backend.name,
                                outcome.backend.endpoint,
                                outcome.model.ifBlank { "—" },
                                outcome.catalogSize,
                            )
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

    // ------------------------------------------------------------------
    // H3-51: atualização in-app via GitHub Releases.
    // Checagem sob demanda (uma vez por processo ao abrir Ajustes + botão
    // manual); download e instalação só com toque do usuário, sempre via
    // instalador do sistema.
    // ------------------------------------------------------------------

    private val releaseChecker = ReleaseChecker()
    private val updateInstaller = UpdateInstaller(context)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    /** A checagem automática roda uma única vez por processo — nunca polling. */
    private var updateCheckedThisProcess = false

    /**
     * Consulta o release `latest` e mostra o card só se o `versionCode`
     * remoto for maior que o local. Falha silenciosa no automático; o botão
     * manual mostra o erro discreto.
     */
    fun checkForUpdate(manual: Boolean) {
        val current = _updateState.value
        if (current is UpdateState.Checking || current is UpdateState.Downloading) return
        _updateState.value = UpdateState.Checking
        viewModelScope.launch {
            val release = withContext(Dispatchers.IO) {
                ReleaseParser.parse(releaseChecker.fetchLatest())
            }
            val localCode = try {
                PackageInfoCompat.getLongVersionCode(
                    context.packageManager.getPackageInfo(context.packageName, 0),
                )
            } catch (_: Exception) {
                0L
            }
            _updateState.value = when {
                release == null ->
                    if (manual) {
                        UpdateState.Failed(
                            context.getString(R.string.settings_update_error_check),
                        )
                    } else {
                        UpdateState.Idle
                    }
                VersionCompare.shouldShowCard(release.versionCode, localCode) ->
                    UpdateState.Available(release)
                manual -> UpdateState.UpToDate
                else -> UpdateState.Idle
            }
            updateCheckedThisProcess = true
        }
    }

    /** Gatilho da checagem automática ao abrir Ajustes (uma vez por processo). */
    fun maybeCheckForUpdateOnOpen() {
        if (!updateCheckedThisProcess) checkForUpdate(manual = false)
    }

    /** Baixa o APK verificado e abre o instalador do sistema. */
    fun downloadAndInstallUpdate() {
        val available = _updateState.value as? UpdateState.Available ?: return
        val release = available.release
        _updateState.value = UpdateState.Downloading(0)
        viewModelScope.launch {
            updateInstaller.downloadAndVerify(
                release.apkUrl,
                release.sha256Url,
            ) { progress ->
                _updateState.value = UpdateState.Downloading(progress)
            }.fold(
                onSuccess = { file ->
                    _updateState.value = UpdateState.Available(
                        release,
                        note = context.getString(R.string.settings_update_started),
                    )
                    try {
                        context.startActivity(updateInstaller.installIntent(file))
                    } catch (_: Exception) {
                        _updateState.value = UpdateState.Failed(
                            context.getString(R.string.settings_update_error_installer),
                        )
                    }
                },
                onFailure = { failure ->
                    _updateState.value = UpdateState.Failed(
                        context.getString(
                            R.string.settings_update_error_download,
                            failure.message ?: context.getString(R.string.msg_unknown_error),
                        ),
                    )
                },
            )
        }
    }

    /** Dispensa o erro discreto / o aviso "já está atualizado" do updater. */
    fun clearUpdateMessage() {
        if (_updateState.value is UpdateState.Failed || _updateState.value is UpdateState.UpToDate) {
            _updateState.value = UpdateState.Idle
        }
    }

    // ---------------- Health Connect (H4-9) ----------------

    /**
     * H4-9: recolhe disponibilidade e permissões atuais do Health Connect.
     * Nunca crasha: qualquer falha do SDK vira `UNAVAILABLE` (regra 1).
     */
    fun refreshHealthConnect() {
        viewModelScope.launch {
            val availability = runCatching { healthConnectAvailability(context, healthGateway) }
                .getOrDefault(HealthConnectAvailability.UNAVAILABLE)
            val granted = healthGateway?.grantedPermissions() ?: emptySet()
            _healthConnectState.value = _healthConnectState.value.copy(
                availability = availability,
                permissions = healthPermissionState(granted),
            )
        }
    }

    /**
     * H4-9: pede as permissões de saúde. A permissão em si é executada pela
     * MainActivity com o contract do Health Connect (contexto de Activity).
     */
    fun requestHealthPermissions() {
        _healthPermissionRequests.value = _healthPermissionRequests.value + 1
    }

    /**
     * H4-9: "Sincronizar agora" — reenvia a sessão cuja escrita falhou (regra
     * 8) e lê peso/passos dos últimos 30 dias. Sem permissão nenhuma, nada é
     * tentado e a UI mostra "Conecte para sincronizar" (regra 2).
     */
    fun syncHealthData() {
        val state = _healthConnectState.value
        if (state.availability != HealthConnectAvailability.AVAILABLE) return
        if (!state.permissions.hasAnyPermission) {
            _healthConnectMessage.value = context.getString(R.string.settings_health_not_connected)
            return
        }
        if (state.isSyncing) return
        _healthConnectState.value = state.copy(isSyncing = true)
        viewModelScope.launch {
            var lastWeight: HealthWeightSample? = null
            var stepsTotal: Long? = null
            var failure: String? = null
            try {
                val pending = pendingHealthWriteSessionId
                if (pending != null) {
                    try {
                        repository.writeCompletedSessionToHealth(pending)
                        pendingHealthWriteSessionId = null
                    } catch (error: Exception) {
                        failure = healthConnectErrorMessage(error)
                    }
                }
                val now = System.currentTimeMillis()
                val from = now - HEALTH_SYNC_WINDOW_MILLIS
                if (_healthConnectState.value.permissions.canReadWeight) {
                    try {
                        lastWeight = repository.syncHealthWeight(from, now)
                            .maxByOrNull { it.timeMillis }
                    } catch (error: Exception) {
                        failure = healthConnectErrorMessage(error)
                    }
                }
                if (_healthConnectState.value.permissions.canReadSteps) {
                    try {
                        stepsTotal = repository.syncHealthSteps(from, now)
                    } catch (error: Exception) {
                        failure = healthConnectErrorMessage(error)
                    }
                }
            } finally {
                _healthConnectState.value = _healthConnectState.value.copy(
                    lastWeight = lastWeight,
                    stepsTotal = stepsTotal,
                    writeFailed = pendingHealthWriteSessionId != null,
                    isSyncing = false,
                )
            }
            _healthConnectMessage.value = failure
                ?: context.getString(R.string.settings_health_sync_done)
        }
    }

    /** H4-9: mensagem de falha do Health Connect, distinguindo permissão. */
    private fun healthConnectErrorMessage(error: Exception): String = when (error) {
        is SecurityException -> context.getString(R.string.settings_health_error_permission)
        else -> context.getString(R.string.settings_health_error_write)
    }

    override fun onCleared() {
        super.onCleared()
        discoveryManager?.stop()
    }

    companion object {
        private const val TAG = "AppViewModel"

        // H3-35: atalho dinâmico "Retomar treino".
        private const val SHORTCUT_ID_RESUME = "resume_workout"

        /** "+15 s" padrão da faixa de descanso (H3-26). */
        const val REST_EXTEND_SECONDS = 15

        /** H4-9: janela padrão de sincronização com o Health Connect (30 dias). */
        const val HEALTH_SYNC_WINDOW_MILLIS = 30L * 24L * 60L * 60L * 1000L

        fun factory(
            context: Context,
            repository: WorkoutRepository,
            preferences: AppPreferences,
            exerciseMediaRepository: ExerciseMediaRepository? = null,
            discoveryManager: BackendDiscoveryManager? = null,
            healthChecker: BackendHealthChecker = HttpBackendHealthChecker(),
            wearSyncRepository: WearSyncRepository? = null,
            restFeedback: RestFeedbackPlayer? = null,
            restNotifier: RestNotifier? = null,
            healthGateway: HealthConnectGateway? = null,
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
                    restNotifier,
                    healthGateway,
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

/**
 * H4-9: estado da seção "Health Connect" em Ajustes. `stepsTotal == null`
 * significa "sem dados de passos" no período; `writeFailed` liga o "Tentar de
 * novo" da regra 8.
 */
data class HealthConnectUiState(
    val availability: HealthConnectAvailability = HealthConnectAvailability.UNAVAILABLE,
    val permissions: HealthConnectPermissionState = HealthConnectPermissionState(),
    /** Leitura de peso mais recente dos últimos 30 dias; `null` = nenhuma. */
    val lastWeight: HealthWeightSample? = null,
    /** Total de passos do período; `null` = sem dados de passos. */
    val stepsTotal: Long? = null,
    val isSyncing: Boolean = false,
    val writeFailed: Boolean = false,
)

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
