package com.treinos.app.ui.workouteditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.treinos.shared.data.WorkoutPlan
import com.treinos.shared.data.WorkoutRepository
import com.treinos.shared.util.BackendRepository
import com.treinos.shared.util.GenerateRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkoutEditorViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val backendRepository: BackendRepository
) : ViewModel() {

    private var _plan by mutableStateOf<WorkoutPlan?>(null)
    val plan: WorkoutPlan?
        get() = _plan

    private var _days by mutableStateOf<List<com.treinos.shared.data.WorkoutDayWithExercises>>(emptyList())
    val days: List<com.treinos.shared.data.WorkoutDayWithExercises>
        get() = _days

    private var _isLoading by mutableStateOf(false)
    val isLoading: Boolean
        get() = _isLoading

    private var _error by mutableStateOf<String?>(null)
    val error: String?
        get() = _error

    // AI Generation state
    private var _aiGenerationState by mutableStateOf(AiGenerationState.Idle)
    val aiGenerationState: AiGenerationState
        get() = _aiGenerationState

    enum class AiGenerationState {
        Idle, Generating, Success, Error
    }

    fun loadPlan(planId: Long) {
        viewModelScope.launch {
            _isLoading = true
            _error = null
            try {
                val planWithDetails = repository.getPlanWithDaysAndExercises(planId)
                _plan = planWithDetails?.plan
                _days = planWithDetails?.days ?: emptyList()
            } catch (e: Exception) {
                _error = "Erro ao carregar treino: ${e.message}"
            } finally {
                _isLoading = false
            }
        }
    }

    fun createNewPlan() {
        _plan = WorkoutPlan(name = "", goal = "hypertrophy", daysPerWeek = 4)
        _days = emptyList()
        _aiGenerationState = AiGenerationState.Idle
    }

    suspend fun savePlan(
        name: String,
        description: String,
        goal: String,
        daysPerWeek: Int,
        generatedByAi: Boolean = false
    ): Long {
        val currentPlan = _plan ?: throw IllegalStateException("No plan")
        val updatedPlan = currentPlan.copy(
            name = name,
            description = description,
            goal = goal,
            daysPerWeek = daysPerWeek,
            generatedByAi = generatedByAi,
            updatedAt = System.currentTimeMillis()
        )
        if (currentPlan.id == 0L) {
            val newId = repository.createPlan(
                name = name,
                description = description,
                goal = goal,
                daysPerWeek = daysPerWeek,
                generatedByAi = generatedByAi
            )
            _plan = updatedPlan.copy(id = newId)
            return newId
        } else {
            repository.updatePlan(updatedPlan)
            _plan = updatedPlan
            return currentPlan.id
        }
    }

    fun generateWithAi() {
        val plan = _plan ?: return
        viewModelScope.launch {
            _aiGenerationState = AiGenerationState.Generating
            _error = null
            try {
                val request = GenerateRequest(
                    goal = plan.goal,
                    daysPerWeek = plan.daysPerWeek,
                    availableEquipment = getAvailableEquipment(),
                    targetMuscles = getTargetMuscles(),
                    injuriesOrLimitations = "",
                    currentLevel = "intermediate",
                    sessionDurationMinutes = 60,
                    preferredSplit = null
                )
                val response = backendRepository.generateWorkout(request)
                applyGeneratedWorkout(response.workout)
                _aiGenerationState = AiGenerationState.Success
            } catch (e: Exception) {
                _error = "Falha ao gerar treino: ${e.message}"
                _aiGenerationState = AiGenerationState.Error
            }
        }
    }

    private fun getAvailableEquipment(): List<String> {
        // TODO: Read from user settings
        return listOf("body only", "dumbbell", "barbell")
    }

    private fun getTargetMuscles(): List<String> {
        // TODO: Read from user settings or plan
        return listOf("chest", "back", "legs", "shoulders", "arms")
    }

    private fun applyGeneratedWorkout(dto: com.treinos.shared.util.WorkoutPlanDTO) {
        _plan = _plan?.copy(
            name = dto.name,
            description = dto.description,
            goal = dto.goal,
            daysPerWeek = dto.daysPerWeek,
            generatedByAi = true,
            updatedAt = System.currentTimeMillis()
        )
        // Clear existing days and rebuild from DTO
        _days = emptyList()
        dto.days.forEachIndexed { dayIndex, dayDto ->
            viewModelScope.launch {
                val dayId = repository.addDay(_plan?.id ?: 0, dayDto.name, dayDto.isRestDay)
                if (!dayDto.isRestDay) {
                    dayDto.exercises.forEach { exDto ->
                        repository.addExerciseToDay(
                            dayId = dayId,
                            exerciseId = exDto.exerciseId,
                            sets = exDto.sets,
                            targetReps = exDto.targetReps,
                            targetRpe = exDto.targetRpe,
                            restSeconds = exDto.restSeconds
                        )
                    }
                }
                refreshDays()
            }
        }
    }

    suspend fun addDay(name: String, isRestDay: Boolean = false): Long {
        val planId = _plan?.id ?: throw IllegalStateException("No plan")
        return repository.addDay(planId, name, isRestDay)
    }

    suspend fun deleteDay(dayId: Long) {
        repository.deleteDay(dayId)
        refreshDays()
    }

    suspend fun reorderDays(dayOrders: List<Pair<Long, Int>>) {
        val planId = _plan?.id ?: throw IllegalStateException("No plan")
        repository.reorderDays(planId, dayOrders)
        refreshDays()
    }

    suspend fun addExerciseToDay(
        dayId: Long,
        exerciseId: String,
        sets: Int,
        targetReps: String,
        targetRpe: Double?,
        restSeconds: Int
    ) {
        repository.addExerciseToDay(dayId, exerciseId, sets, targetReps, targetRpe, restSeconds)
        refreshDays()
    }

    suspend fun removeExerciseFromDay(dayId: Long, exerciseId: String) {
        repository.deleteExerciseFromDay(dayId, exerciseId)
        refreshDays()
    }

    suspend fun updatePlanExercise(planExercise: com.treinos.shared.data.WorkoutPlanExercise) {
        repository.updatePlanExercise(planExercise)
        refreshDays()
    }

    private fun refreshDays() {
        val planId = _plan?.id ?: return
        viewModelScope.launch {
            val planWithDetails = repository.getPlanWithDaysAndExercises(planId)
            _plan = planWithDetails?.plan
            _days = planWithDetails?.days ?: emptyList()
        }
    }

    fun resetAiGenerationState() {
        _aiGenerationState = AiGenerationState.Idle
    }
}