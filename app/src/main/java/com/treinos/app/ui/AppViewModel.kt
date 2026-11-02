package com.treinos.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.treinos.app.data.WorkoutRepository
import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.WorkoutPlanWithDetails
import com.treinos.app.data.prefs.AppPreferences
import com.treinos.app.data.remote.GeneratePlanRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _backendUrl = MutableStateFlow(preferences.backendUrl)
    val backendUrl: StateFlow<String> = _backendUrl.asStateFlow()

    private val _connectionMessage = MutableStateFlow<String?>(null)
    val connectionMessage: StateFlow<String?> = _connectionMessage.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _lastSyncAt = MutableStateFlow(preferences.lastSyncAt)
    val lastSyncAt: StateFlow<String?> = _lastSyncAt.asStateFlow()

    private val _restRemaining = MutableStateFlow<Int?>(null)
    val restRemaining: StateFlow<Int?> = _restRemaining.asStateFlow()

    private val _restTotal = MutableStateFlow<Int?>(null)
    val restTotal: StateFlow<Int?> = _restTotal.asStateFlow()

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
    ) {
        if (_isGenerating.value) return
        viewModelScope.launch {
            _isGenerating.value = true
            try {
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
                    ?: "Não foi possível sincronizar com o backend."
            } finally {
                _isSyncing.value = false
            }
        }
    }

    companion object {
        fun factory(
            repository: WorkoutRepository,
            preferences: AppPreferences,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppViewModel(repository, preferences) as T
            }
        }
    }
}
