package com.treinos.app.ui.workoutlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.treinos.shared.data.WorkoutPlan
import com.treinos.shared.data.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@HiltViewModel
class WorkoutListViewModel @Inject constructor(
    private val repository: WorkoutRepository
) : ViewModel() {

    private val searchQueryFlow = MutableStateFlow("")

    var searchQuery: String
        get() = searchQueryFlow.value
        set(value) { searchQueryFlow.value = value }

    val allPlans: Flow<List<WorkoutPlan>> = repository.getAllPlans()

    val filteredPlans: Flow<List<WorkoutPlan>> = combine(allPlans, searchQueryFlow) { plans, query ->
        if (query.isBlank()) plans else plans.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun deletePlan(planId: Long) {
        viewModelScope.launch {
            repository.deletePlan(planId)
        }
    }

    fun duplicatePlan(planId: Long, newName: String) {
        viewModelScope.launch {
            repository.duplicatePlan(planId, newName)
        }
    }
}
