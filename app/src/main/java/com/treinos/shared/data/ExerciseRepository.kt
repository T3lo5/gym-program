package com.treinos.shared.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ExerciseRepository(private val dao: ExerciseDao, private val backendRepository: com.treinos.shared.util.BackendRepository?) {

    suspend fun initializeIfNeeded() {
        val count = dao.getCount()
        if (count == 0L) {
            dao.insertAll(ExerciseCatalog.allExercises)
        }
    }

    fun getAllExercises(): Flow<List<Exercise>> = dao.getAll()

    fun searchExercises(query: String): Flow<List<Exercise>> = dao.search(query)

    fun getByMuscle(muscle: String): Flow<List<Exercise>> = dao.getByPrimaryMuscle(muscle)

    fun getByEquipment(equipment: String): Flow<List<Exercise>> = dao.getByEquipment(equipment)

    fun getByLevel(level: String): Flow<List<Exercise>> = dao.getByLevel(level)

    fun getByCategory(category: String): Flow<List<Exercise>> = dao.getByCategory(category)

    fun getFavorites(): Flow<List<Exercise>> = dao.getFavorites()

    fun getRecentlyUsed(limit: Int = 10): Flow<List<Exercise>> = dao.getRecentlyUsed(limit)

    fun getAllEquipments(): Flow<List<String>> = dao.getAllEquipments()

    fun getAllMuscleGroups(): Flow<List<String>> = dao.getAllMuscleGroups()

    suspend fun getByIds(ids: List<String>): List<Exercise> = dao.getByIds(ids)

    suspend fun toggleFavorite(exercise: Exercise) {
        val updated = exercise.copy(isFavorite = !exercise.isFavorite)
        dao.update(updated)
    }

    suspend fun markUsed(exerciseId: String) {
        dao.updateLastUsed(exerciseId, System.currentTimeMillis())
    }

    @Suppress("UNUSED_PARAMETER")
    fun getCount(): Long {
        return 0L
    }
}