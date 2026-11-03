package com.treinos.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.treinos.app.data.WorkoutRepository
import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.WorkoutPlanWithDetails
import com.treinos.app.data.media.ExerciseMedia
import com.treinos.app.data.media.ExerciseMediaRepository
import com.treinos.app.data.prefs.AppPreferences
import com.treinos.app.data.remote.GeneratePlanRequest
import com.treinos.app.data.backup.BackupFile
import com.treinos.app.data.backup.BackupPreview
import com.treinos.app.data.backup.BackupValidator
import com.treinos.app.ui.ExerciseSessionData
import com.treinos.app.ui.ProfileValidator
import com.treinos.app.ui.SetRecord
import com.treinos.app.ui.calculateProgress
import com.treinos.app.ui.nextIncompleteExercise
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
import kotlinx.serialization.json.Json

enum class Screen {
    Home,
    Generate,
    Workout,
    History,
    Settings,
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(
    private val repository: WorkoutRepository,
    private val preferences: AppPreferences,
    private val exerciseMediaRepository: ExerciseMediaRepository? = null,
) : ViewModel() {

    private val _screen = MutableStateFlow(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    val activePlan: StateFlow<WorkoutPlanWithDetails?> = repository.observeActivePlan()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    private var pendingBackupPayload: BackupFile? = null

    init {
        viewModelScope.launch {
            loadExerciseMedia(false)
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

    val deviceId: String = preferences.deviceId

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

    fun goHome() {
        _screen.value = Screen.Home
    }

    fun clearMessage() {
        _message.value = null
    }

    // ---------------- Geração ----------------

    fun generatePlan(
        goal: String,
        daysPerWeek: Int,
        level: String,
        equipment: List<String>,
        muscles: List<String>,
        sessionDuration: Int,
        reviewWeeks: Int,
        bodyWeightKgText: String,
        heightCmText: String,
    ) {
        if (_isGenerating.value) return
        viewModelScope.launch {
            _isGenerating.value = true
            try {
                val validation = ProfileValidator.validate(bodyWeightKgText, heightCmText)
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
                    )
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

    // ---------------- Execução ----------------

    fun startWorkout(dayId: Long) {
        val plan = activePlan.value ?: run {
            _message.value = "Nenhum plano ativo no aparelho."
            return
        }
        val hasDay = plan.days.any { it.day.id == dayId }
        if (!hasDay) {
            _message.value = "Dia de treino não encontrado."
            return
        }
        viewModelScope.launch {
            try {
                val sessionId = repository.startSession(plan.plan.id, dayId)
                _activeSessionId.value = sessionId
                _exerciseIndex.value = 0
                _screen.value = Screen.Workout
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
        _screen.value = Screen.Workout
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
        if (deviceId.isBlank()) {
            _restoreMessage.value = "Informe um ID de aparelho válido."
            return
        }
        viewModelScope.launch {
            _isRestoring.value = true
            try {
                preferences.deviceId = deviceId
                val data = repository.downloadSync()
                _lastSyncAt.value = preferences.lastSyncAt
                val planCount = data.plans.size
                _restoreMessage.value =
                    if (planCount > 0) "Restaurado: ${planCount} plano(s) do backup do dispositivo $deviceId."
                    else "Nenhum dado encontrado para o dispositivo $deviceId."
                _restoreDeviceId.value = ""
            } catch (error: Exception) {
                _restoreMessage.value = error.message
                    ?: "Não foi possível restaurar o backup do dispositivo $deviceId."
            } finally {
                _isRestoring.value = false
            }
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
                val data = repository.downloadSync()
                _lastSyncAt.value = preferences.lastSyncAt
                val planCount = data.plans.size
                _downloadMessage.value =
                    if (planCount > 0) "Restaurado: ${planCount} plano(s) do backend."
                    else "Nenhum dado encontrado para este dispositivo (deviceId: ${preferences.deviceId}). Verifique se está usando o mesmo aparelho que gerou o treino."
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
                val file = Json.decodeFromString(BackupFile.serializer(), json)
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
                repository.importBackup(payload)
                _backupMessage.value = "Backup importado com sucesso."
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

    companion object {
        fun factory(
            repository: WorkoutRepository,
            preferences: AppPreferences,
            exerciseMediaRepository: ExerciseMediaRepository? = null,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppViewModel(repository, preferences, exerciseMediaRepository) as T
            }
        }
    }
}

sealed interface BackupImportState {
    object Idle : BackupImportState
    object Reading : BackupImportState
    data class Ready(val preview: BackupPreview, val payload: BackupFile) : BackupImportState
    object Importing : BackupImportState
    data class Error(val message: String) : BackupImportState
}
