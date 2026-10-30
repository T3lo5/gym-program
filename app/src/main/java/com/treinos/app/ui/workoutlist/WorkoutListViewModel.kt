package com.treinos.app.ui.workoutlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.treinos.shared.data.WorkoutPlan
import com.treinos.shared.data.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkoutListViewModel @Inject constructor(
    private val repository: WorkoutRepository
) : ViewModel() {

    private var _searchQuery by mutableStateOf("")
    var searchQuery: String
        get() = _searchQuery
        set(value) { _searchQuery = value }

    val allPlans: Flow<List<WorkoutPlan>> = repository.getAllPlans()

    val filteredPlans: Flow<List<WorkoutPlan>> = combine(allPlans, ::searchQuery) { plans, query ->
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