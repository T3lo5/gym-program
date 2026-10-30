package com.treinos.app.ui.exercisepicker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.treinos.shared.data.Exercise
import com.treinos.shared.data.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExercisePickerViewModel @Inject constructor(
    private val repository: WorkoutRepository
) : ViewModel() {

    private var _searchQuery by mutableStateOf("")
    var searchQuery: String
        get() = _searchQuery
        set(value) { _searchQuery = value }

    private var _selectedMuscle by mutableStateOf<String?>(null)
    var selectedMuscle: String?
        get() = _selectedMuscle
        set(value) { _selectedMuscle = value }

    private var _selectedEquipment by mutableStateOf<String?>(null)
    var selectedEquipment: String?
        get() = _selectedEquipment
        set(value) { _selectedEquipment = value }

    val allExercises: Flow<List<Exercise>> = repository.getAllExercises()

    val allMuscleGroups: Flow<List<String>> = repository.getAllMuscleGroups()
    val allEquipments: Flow<List<String>> = repository.getAllEquipments()

    val filteredExercises: Flow<List<Exercise>> = combine(
        allExercises,
        ::searchQuery,
        ::selectedMuscle,
        ::selectedEquipment
    ) { exercises, query, muscle, equipment ->
        exercises.filter { ex ->
            val matchesQuery = query.isBlank() || ex.name.contains(query, ignoreCase = true) || ex.namePt?.contains(query, ignoreCase = true) == true
            val matchesMuscle = muscle == null || ex.primaryMuscles.contains(muscle) || ex.secondaryMuscles.contains(muscle)
            val matchesEquipment = equipment == null || ex.equipment == equipment
            matchesQuery && matchesMuscle && matchesEquipment
        }
    }

    fun clearFilters() {
        _searchQuery = ""
        _selectedMuscle = null
        _selectedEquipment = null
    }
}