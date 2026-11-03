package com.treinos.app.data.media

import android.content.Context
import com.treinos.app.data.remote.BackendClient
import com.treinos.app.data.remote.CatalogResponseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit

data class ExerciseMedia(
    val id: String,
    val name: String,
    val primaryMuscles: List<String>,
    val equipment: String,
    val instructions: List<String>,
    val imageUrl: String?,
)

@Serializable
private data class CachedCatalog(
    val fetchedAt: Long,
    val exercises: List<CachedExercise>,
)

@Serializable
private data class CachedExercise(
    val id: String,
    val name: String = "",
    val primaryMuscles: List<String> = emptyList(),
    val equipment: String = "",
    val instructions: List<String> = emptyList(),
    val images: List<String> = emptyList(),
)

class ExerciseMediaRepository(
    private val context: Context,
    private val backendClient: BackendClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val cacheFile = File(context.filesDir, "exercise_catalog_v1.json")
    private val ttlMillis = TimeUnit.DAYS.toMillis(7)
    private val maxCacheBytes = 5L * 1024 * 1024

    companion object {
        const val IMAGE_BASE_URL =
            "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"
    }

    suspend fun load(forceRefresh: Boolean = false): Map<String, ExerciseMedia> =
        withContext(Dispatchers.IO) {
            val cached = if (!forceRefresh) readCache() else null
            if (cached != null && !isExpired()) {
                return@withContext cached
            }

            val fetched = runCatching { fetchFromBackend() }.getOrNull()
            if (fetched != null) {
                writeCache(fetched)
                return@withContext fetched
            }

            if (cached != null) {
                return@withContext cached
            }

            return@withContext emptyMap()
        }

    private suspend fun fetchFromBackend(): Map<String, ExerciseMedia>? {
        val response: CatalogResponseDto = backendClient.catalog()
        val exercises = response.exercises
        if (exercises.isEmpty()) return emptyMap()
        return exercises.associate { exercise ->
            val imageUrl = imageUrlFor(exercise.images)
            exercise.id to ExerciseMedia(
                id = exercise.id,
                name = exercise.name,
                primaryMuscles = exercise.primaryMuscles,
                equipment = exercise.equipment,
                instructions = exercise.instructions,
                imageUrl = imageUrl,
            )
        }
    }

    fun imageUrlFor(images: List<String>): String? {
        val first = images.firstOrNull() ?: return null
        if (first.isBlank()) return null
        val encoded = first.replace("\\", "/")
        return IMAGE_BASE_URL + encoded
    }

    private fun readCache(): Map<String, ExerciseMedia>? {
        if (!cacheFile.exists()) return null
        if (cacheFile.length() > maxCacheBytes) {
            return null
        }
        return runCatching {
            val raw = cacheFile.readText()
            val cached = json.decodeFromString<CachedCatalog>(raw)
            cached.exercises.associate { ex ->
                ex.id to ExerciseMedia(
                    id = ex.id,
                    name = ex.name,
                    primaryMuscles = ex.primaryMuscles,
                    equipment = ex.equipment,
                    instructions = ex.instructions,
                    imageUrl = imageUrlFor(ex.images),
                )
            }
        }.getOrNull()
    }

    private fun isExpired(): Boolean {
        if (!cacheFile.exists()) return true
        if (cacheFile.length() > maxCacheBytes) return true
        return runCatching {
            val raw = cacheFile.readText()
            val cached = json.decodeFromString<CachedCatalog>(raw)
            val age = System.currentTimeMillis() - cached.fetchedAt
            age > ttlMillis
        }.getOrElse { true }
    }

    private fun writeCache(map: Map<String, ExerciseMedia>) {
        runCatching {
            val exercises = map.values.map { media ->
                CachedExercise(
                    id = media.id,
                    name = media.name,
                    primaryMuscles = media.primaryMuscles,
                    equipment = media.equipment,
                    instructions = media.instructions,
                    images = imagesFromUrl(media.imageUrl),
                )
            }
            val cached = CachedCatalog(fetchedAt = System.currentTimeMillis(), exercises = exercises)
            cacheFile.writeText(json.encodeToString(CachedCatalog.serializer(), cached))
        }
    }

    private fun imagesFromUrl(url: String?): List<String> {
        if (url.isNullOrBlank()) return emptyList()
        if (!url.startsWith(IMAGE_BASE_URL)) return emptyList()
        val path = url.removePrefix(IMAGE_BASE_URL)
        return listOf(path)
    }
}
