package com.treinos.shared.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<Exercise>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(exercise: Exercise): Long

    @Update
    suspend fun update(exercise: Exercise): Int

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: String): Exercise?

    @Query("SELECT * FROM exercises ORDER BY name ASC")
    fun getAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE primaryMuscles LIKE '%' || :muscle || '%' ORDER BY name ASC")
    fun getByPrimaryMuscle(muscle: String): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE equipment = :equipment ORDER BY name ASC")
    fun getByEquipment(equipment: String): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE level = :level ORDER BY name ASC")
    fun getByLevel(level: String): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE category = :category ORDER BY name ASC")
    fun getByCategory(category: String): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE name LIKE '%' || :query || '%' OR namePt LIKE '%' || :query || '%' ORDER BY name ASC")
    fun search(query: String): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavorites(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises ORDER BY lastUsedTimestamp DESC LIMIT :limit")
    fun getRecentlyUsed(limit: Int): Flow<List<Exercise>>

    @Query("SELECT DISTINCT equipment FROM exercises WHERE equipment IS NOT NULL ORDER BY equipment ASC")
    fun getAllEquipments(): Flow<List<String>>

    @Query("SELECT DISTINCT primaryMuscles FROM exercises ORDER BY primaryMuscles ASC")
    fun getAllMuscleGroups(): Flow<List<String>>

    @Query("SELECT * FROM exercises WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<Exercise>

    @Query("UPDATE exercises SET lastUsedTimestamp = :ts WHERE id = :id")
    suspend fun updateLastUsed(id: String, ts: Long): Int

    @Query("UPDATE exercises SET namePt = :namePt WHERE id = :id")
    suspend fun updateNamePt(id: String, namePt: String): Int

    @Query("DELETE FROM exercises")
    suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun getCount(): Long
}