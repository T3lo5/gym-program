package com.treinos.app.ui.workoutexecution

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.treinos.shared.data.Exercise
import com.treinos.shared.data.ExerciseDao
import com.treinos.shared.data.SessionExerciseLog
import com.treinos.shared.data.SessionExerciseLogDao
import com.treinos.shared.data.SetLog
import com.treinos.shared.data.SetLogDao
import com.treinos.shared.data.WorkoutDayDao
import com.treinos.shared.data.WorkoutPlanExercise
import com.treinos.shared.data.WorkoutRepository
import com.treinos.shared.data.WorkoutSession
import com.treinos.shared.data.WorkoutSessionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.launch

@HiltViewModel
class WorkoutExecutionViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val workoutSessionDao: WorkoutSessionDao,
    private val sessionExerciseLogDao: SessionExerciseLogDao,
    private val setLogDao: SetLogDao,
    private val exerciseDao: ExerciseDao,
    private val workoutDayDao: WorkoutDayDao
) : ViewModel() {

    // Session state
    private var _session by mutableStateOf<WorkoutSession?>(null)
    val session: WorkoutSession? get() = _session

    private var _exerciseLogs by mutableStateOf<List<SessionExerciseLogWithExercise>>(emptyList())
    val exerciseLogs: List<SessionExerciseLogWithExercise> get() = _exerciseLogs

    private var _currentExerciseIndex by mutableStateOf(0)
    var currentExerciseIndex: Int
        get() = _currentExerciseIndex
        set(value) {
            _currentExerciseIndex = if (exerciseLogs.isEmpty()) {
                0
            } else {
                value.coerceIn(0, exerciseLogs.size - 1)
            }
        }

    // Timer state
    private var _restTimerSeconds by mutableStateOf(0)
    var restTimerSeconds: Int
        get() = _restTimerSeconds
        set(value) { _restTimerSeconds = value.coerceAtLeast(0) }

    private var _isRestTimerRunning by mutableStateOf(false)
    var isRestTimerRunning: Boolean
        get() = _isRestTimerRunning
        set(value) { _isRestTimerRunning = value }

    private var _elapsedSessionSeconds by mutableStateOf(0L)
    var elapsedSessionSeconds: Long
        get() = _elapsedSessionSeconds
        set(value) { _elapsedSessionSeconds = value }

    // Rest timer job reference
    private var restTimerJob: kotlinx.coroutines.Job? = null

    val currentExerciseLog: SessionExerciseLogWithExercise?
        get() = exerciseLogs.getOrNull(currentExerciseIndex)

    val progress: Float
        get() = if (exerciseLogs.isEmpty()) 0f else (currentExerciseIndex + 1).toFloat() / exerciseLogs.size

    fun startSession(planId: Long, dayId: Long) {
        viewModelScope.launch {
            val sessionId = workoutSessionDao.insert(
                WorkoutSession(
                    planId = planId,
                    dayId = dayId,
                    status = "in_progress",
                    startedAt = System.currentTimeMillis()
                )
            )
            val session = workoutSessionDao.getById(sessionId)!!
            _session = session

            // Create exercise logs from plan
            val planExercises = workoutDayDao.getExercisesByDayId(dayId)
            val logs = planExercises.mapIndexed { _, planEx ->
                val logId = sessionExerciseLogDao.insert(
                    SessionExerciseLog(
                        sessionId = sessionId,
                        exerciseId = planEx.exerciseId,
                        exerciseOrder = planEx.exerciseOrder,
                        plannedSets = planEx.sets
                    )
                )
                // Create empty set logs
                (1..planEx.sets).forEach { setNum ->
                    setLogDao.insert(
                        SetLog(
                            exerciseLogId = logId,
                            setNumber = setNum,
                            isCompleted = false
                        )
                    )
                }
                SessionExerciseLogWithExercise(
                    log = SessionExerciseLog(
                        id = logId,
                        sessionId = sessionId,
                        exerciseId = planEx.exerciseId,
                        exerciseOrder = planEx.exerciseOrder,
                        plannedSets = planEx.sets
                    ),
                    exercise = exerciseDao.getById(planEx.exerciseId),
                    planExercise = planEx,
                    sets = setLogDao.getByExerciseLogId(logId)
                )
            }
            _exerciseLogs = logs
            _currentExerciseIndex = 0

            // Start session timer
            startSessionTimer()
        }
    }

    private fun startSessionTimer() {
        viewModelScope.launch {
            while (_session?.status == "in_progress") {
                kotlinx.coroutines.delay(1000)
                _elapsedSessionSeconds++
            }
        }
    }

    fun completeSet(
        exerciseLogId: Long,
        setNumber: Int,
        reps: Int?,
        weightKg: Double?,
        rpe: Double?,
        durationSeconds: Long?
    ) {
        viewModelScope.launch {
            val existing = setLogDao.getByExerciseLogAndSet(exerciseLogId, setNumber)
            setLogDao.update(
                (existing ?: SetLog(exerciseLogId = exerciseLogId, setNumber = setNumber)).copy(
                    reps = reps,
                    weightKg = weightKg,
                    rpe = rpe,
                    durationSeconds = durationSeconds,
                    isCompleted = true,
                    completedAt = System.currentTimeMillis()
                )
            )
            // Update exercise log completed count
            val log = sessionExerciseLogDao.getById(exerciseLogId)!!
            sessionExerciseLogDao.update(
                log.copy(completedSets = log.completedSets + 1)
            )
            refreshExerciseLogs()
        }
    }

    fun uncompleteSet(exerciseLogId: Long, setNumber: Int) {
        viewModelScope.launch {
            val existing = setLogDao.getByExerciseLogAndSet(exerciseLogId, setNumber)
            if (existing != null) {
                setLogDao.update(
                    existing.copy(
                        isCompleted = false,
                        completedAt = null
                    )
                )
            }
            val log = sessionExerciseLogDao.getById(exerciseLogId)!!
            sessionExerciseLogDao.update(
                log.copy(completedSets = max(0, log.completedSets - 1))
            )
            refreshExerciseLogs()
        }
    }

    private fun refreshExerciseLogs() {
        val sessionId = _session?.id ?: return
        viewModelScope.launch {
            val logsWithSets = workoutSessionDao.getExercisesWithLogs(sessionId)
            val session = workoutSessionDao.getById(sessionId) ?: return@launch
            _exerciseLogs = logsWithSets.map { logWithSets ->
                val planEx = workoutDayDao.getExerciseInDay(
                    session.dayId,
                    logWithSets.log.exerciseId
                )
                SessionExerciseLogWithExercise(
                    log = logWithSets.log,
                    exercise = exerciseDao.getById(logWithSets.log.exerciseId),
                    planExercise = planEx,
                    sets = logWithSets.sets
                )
            }
        }
    }

    fun startRestTimer(seconds: Int) {
        _restTimerSeconds = seconds
        _isRestTimerRunning = true
        restTimerJob?.cancel()
        restTimerJob = viewModelScope.launch {
            while (_restTimerSeconds > 0 && _isRestTimerRunning) {
                kotlinx.coroutines.delay(1000)
                _restTimerSeconds--
            }
            _isRestTimerRunning = false
            // Vibrate/notify when done
            // TODO: Trigger vibration
        }
    }

    fun pauseRestTimer() {
        _isRestTimerRunning = false
        restTimerJob?.cancel()
    }

    fun skipRestTimer() {
        _restTimerSeconds = 0
        _isRestTimerRunning = false
        restTimerJob?.cancel()
    }

    fun nextExercise() {
        if (currentExerciseIndex < exerciseLogs.size - 1) {
            currentExerciseIndex++
            // Auto-start rest timer for next exercise
            currentExerciseLog?.planExercise?.restSeconds?.let { startRestTimer(it) }
        } else {
            finishSession()
        }
    }

    fun previousExercise() {
        if (currentExerciseIndex > 0) {
            currentExerciseIndex--
        }
    }

    private fun finishSession() {
        _session?.let { session ->
            viewModelScope.launch {
                workoutSessionDao.update(
                    session.copy(
                        status = "completed",
                        endedAt = System.currentTimeMillis(),
                        totalDurationSeconds = _elapsedSessionSeconds
                    )
                )
                _session = session.copy(
                    status = "completed",
                    endedAt = System.currentTimeMillis(),
                    totalDurationSeconds = _elapsedSessionSeconds
                )
            }
        }
    }

    fun cancelSession() {
        _session?.let { session ->
            viewModelScope.launch {
                workoutSessionDao.update(
                    session.copy(status = "cancelled", endedAt = System.currentTimeMillis())
                )
            }
        }
    }

    override fun onCleared() {
        restTimerJob?.cancel()
        super.onCleared()
    }
}

data class SessionExerciseLogWithExercise(
    val log: SessionExerciseLog,
    val exercise: Exercise?,
    val planExercise: WorkoutPlanExercise? = null,
    val sets: List<SetLog> = emptyList()
)
