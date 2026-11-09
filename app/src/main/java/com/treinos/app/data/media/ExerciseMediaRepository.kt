package com.treinos.app.data.media

import android.content.Context
import com.treinos.app.data.prefs.AppPreferences
import com.treinos.app.data.remote.BackendClient
import com.treinos.app.data.remote.CatalogResponseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class ExerciseMedia(
    val id: String,
    val name: String,
    val primaryMuscles: List<String>,
    val equipment: String,
    val instructions: List<String>,
    val imageUrl: String?,
    // H3-36: tradução PT aditiva do catálogo. Null = sem tradução — o app
    // usa o EN canônico (nunca inventar).
    val namePt: String? = null,
    val instructionsPt: List<String>? = null,
)

/** Idiomas de conteúdo (H3-36); a preferência do app defaulta "pt". */
const val LANGUAGE_CONTENT_PT = "pt"

/**
 * H3-36: conteúdo no idioma pedido. "pt" usa `namePt`/`instructionsPt`
 * quando existem; tradução ausente/vazia cai para o EN canônico — campo a
 * campo, porque o fork pode trazer nome sem passos. Pura: testável em JVM.
 */
fun ExerciseMedia.resolvedFor(language: String?): ExerciseMedia {
    if (language != LANGUAGE_CONTENT_PT) return this
    val ptName = namePt?.takeIf { it.isNotBlank() }
    val ptInstructions = instructionsPt?.takeIf { it.isNotEmpty() }
    if (ptName == null && ptInstructions == null) return this
    return copy(
        name = ptName ?: name,
        instructions = ptInstructions ?: instructions,
    )
}

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
    // H3-36: cache guarda EN + PT; cache antigo sem os campos decodifica null.
    val namePt: String? = null,
    val instructionsPt: List<String>? = null,
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

        /**
         * Monta a URL da primeira imagem, codificando cada segmento do path
         * (H3-24: espaço/acento não podem quebrar a URL; segmentos limpos saem
         * iguais). Pura: resolve sem instância e sem Android.
         */
        fun imageUrlFor(images: List<String>): String? {
            val first = images.firstOrNull() ?: return null
            if (first.isBlank()) return null
            val path = first.replace("\\", "/")
            val encoded = path
                .split('/')
                .joinToString("/") { segment -> encodeSegment(segment) }
            return IMAGE_BASE_URL + encoded
        }

        private fun encodeSegment(segment: String): String =
            URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")

        /**
         * Decisão pura de qual catálogo usar (H3-24): dados frescos do backend
         * vencem; resposta vazia ou falha de rede mantém o cache existente; sem
         * nenhuma das duas, o mapa fica vazio e a mídia do plano cobre na UI.
         */
        internal fun mergeCatalog(
            cached: Map<String, ExerciseMedia>?,
            fetched: Map<String, ExerciseMedia>?,
        ): Map<String, ExerciseMedia> =
            fetched?.takeIf { it.isNotEmpty() } ?: cached ?: emptyMap()
    }

    suspend fun load(forceRefresh: Boolean = false): Map<String, ExerciseMedia> =
        withContext(Dispatchers.IO) {
            // H3-36: o cache/merge guarda EN + PT; a resolução por idioma
            // acontece na saída, lendo a preferência do app (default "pt").
            val language = AppPreferences(context).language
            fun localize(map: Map<String, ExerciseMedia>) =
                map.mapValues { (_, media) -> media.resolvedFor(language) }

            val cached = if (!forceRefresh) readCache() else null
            if (cached != null && !isExpired()) {
                return@withContext localize(cached)
            }

            val fetched = runCatching { fetchFromBackend() }.getOrNull()
            // H3-24: resposta vazia do catálogo chega como `null` e nunca vira
            // cache — o mapa anterior permanece e um vazio não é gravado por
            // 7 dias envenenando a resolução de mídia.
            val merged = mergeCatalog(cached, fetched)
            if (merged.isNotEmpty() && merged !== cached) {
                writeCache(merged)
            }
            return@withContext localize(merged)
        }

    private suspend fun fetchFromBackend(): Map<String, ExerciseMedia>? {
        val response: CatalogResponseDto = backendClient.catalog()
        val exercises = response.exercises
        // Vazio não é um catálogo válido: devolve null para o `load` manter o
        // cache anterior em vez de substituí-lo (ou gravá-lo) por vazio.
        if (exercises.isEmpty()) return null
        return exercises.associate { exercise ->
            val imageUrl = imageUrlFor(exercise.images)
            exercise.id to ExerciseMedia(
                id = exercise.id,
                name = exercise.name,
                primaryMuscles = exercise.primaryMuscles,
                equipment = exercise.equipment,
                instructions = exercise.instructions,
                imageUrl = imageUrl,
                namePt = exercise.namePt,
                instructionsPt = exercise.instructionsPt,
            )
        }
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
                    namePt = ex.namePt,
                    instructionsPt = ex.instructionsPt,
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
                    // EN + PT vão juntos para o cache: trocar a preferência
                    // (H3-37) não exige refetch do backend.
                    namePt = media.namePt,
                    instructionsPt = media.instructionsPt,
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
        // O cache guarda o path cru: [imageUrlFor] codifica a cada leitura, e
        // decodificar aqui evita dupla codificação (%20 viraria %2520).
        val raw = path
            .split('/')
            .joinToString("/") { segment -> decodeSegment(segment) }
        return listOf(raw)
    }

    private fun decodeSegment(segment: String): String =
        runCatching { URLDecoder.decode(segment, Charsets.UTF_8.name()) }
            .getOrDefault(segment)
}

/**
 * H3-24: resolução determinística da mídia de um exercício.
 *
 * Catálogo primeiro (fonte preferencial: tem músculos, equipamento e as
 * instruções completas); sem entrada no catálogo, usa a mídia que veio no
 * plano e foi persistida no Room. Sem nenhuma das duas, retorna null — a UI
 * mantém o estado de hoje (letra + "Sem instruções disponíveis.").
 *
 * `planInstructions` chega com passos separados por '\n' e `planImages` com
 * paths relativos separados por ',', exatamente como [com.treinos.app.data.local.WorkoutPlanExerciseEntity] persiste.
 */
fun resolveExerciseMedia(
    catalog: Map<String, ExerciseMedia>,
    exerciseId: String,
    planName: String = "",
    planPrimaryMuscles: List<String> = emptyList(),
    planEquipment: String = "",
    planInstructions: String = "",
    planImages: String = "",
): ExerciseMedia? {
    catalog[exerciseId]?.let { return it }
    val instructions = planInstructions
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val imagePaths = planImages
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (instructions.isEmpty() && imagePaths.isEmpty()) return null
    return ExerciseMedia(
        id = exerciseId,
        name = planName.ifBlank { exerciseId },
        primaryMuscles = planPrimaryMuscles,
        equipment = planEquipment,
        instructions = instructions,
        imageUrl = ExerciseMediaRepository.imageUrlFor(imagePaths),
    )
}
