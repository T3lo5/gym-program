package com.treinos.app.ui.exercisepicker

import androidx.lifecycle.ViewModel
import com.treinos.shared.data.Exercise
import com.treinos.shared.data.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

@HiltViewModel
class ExercisePickerViewModel @Inject constructor(
    private val repository: WorkoutRepository
) : ViewModel() {

    private val searchQueryFlow = MutableStateFlow("")
    private val selectedMuscleFlow = MutableStateFlow<String?>(null)
    private val selectedEquipmentFlow = MutableStateFlow<String?>(null)

    var searchQuery: String
        get() = searchQueryFlow.value
        set(value) { searchQueryFlow.value = value }

    var selectedMuscle: String?
        get() = selectedMuscleFlow.value
        set(value) { selectedMuscleFlow.value = value }

    var selectedEquipment: String?
        get() = selectedEquipmentFlow.value
        set(value) { selectedEquipmentFlow.value = value }

    val allExercises: Flow<List<Exercise>> = repository.getAllExercises()

    val allMuscleGroups: Flow<List<String>> = repository.getAllMuscleGroups()
    val allEquipments: Flow<List<String>> = repository.getAllEquipments()

    val filteredExercises: Flow<List<Exercise>> = combine(
        allExercises,
        searchQueryFlow,
        selectedMuscleFlow,
        selectedEquipmentFlow
    ) { exercises, query, muscle, equipment ->
        exercises.filter { ex ->
            val matchesQuery = query.isBlank() ||
                ex.name.contains(query, ignoreCase = true) ||
                ex.namePt?.contains(query, ignoreCase = true) == true
            val matchesMuscle = muscle == null ||
                ex.primaryMuscles.contains(muscle) ||
                ex.secondaryMuscles.contains(muscle)
            val matchesEquipment = equipment == null || ex.equipment == equipment
            matchesQuery && matchesMuscle && matchesEquipment
        }
    }

    fun clearFilters() {
        searchQueryFlow.value = ""
        selectedMuscleFlow.value = null
        selectedEquipmentFlow.value = null
    }
}
