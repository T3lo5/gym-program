package com.treinos.app.data.media

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * H3-44: o asset embarcado é a fonte de substitutos no primeiro uso offline.
 * Ele é gerado a partir do catálogo do backend; este teste impede que os dois
 * fiquem dessincronizados sem aviso.
 */
class SwapCatalogAssetTest {

    private fun findFirst(vararg candidates: String): File =
        candidates.map(::File).firstOrNull { it.exists() }
            ?: error("Arquivo não encontrado: ${candidates.joinToString()}")

    private fun asset(): File =
        findFirst(
            "src/main/assets/exercise_swap_catalog.json",
            "app/src/main/assets/exercise_swap_catalog.json",
            "../app/src/main/assets/exercise_swap_catalog.json",
        )

    private fun backendCatalog(): File =
        findFirst(
            "../back/app/exercise_catalog.json",
            "back/app/exercise_catalog.json",
        )

    private fun assetJson() = Json.parseToJsonElement(asset().readText()).jsonObject

    private fun assetExercises() = assetJson()["exercises"]!!.jsonArray

    @Test
    fun asset_catalogo_e_parseavel() {
        val catalog = ExerciseMediaRepository.parseSwapCatalog(asset().readText())

        assertTrue("asset não pode ser vazio", catalog.isNotEmpty())
        assertTrue(catalog.containsKey("Dumbbell_Bench_Press"))
    }

    @Test
    fun asset_ids_iguais_ao_backend() {
        val backend = Json.parseToJsonElement(backendCatalog().readText()).jsonArray
        val backendIds = backend.map { it.jsonObject["id"]!!.jsonPrimitive.content }.toSet()
        val assetIds = assetExercises()
            .map { it.jsonObject["id"]!!.jsonPrimitive.content }
            .toSet()

        assertEquals(
            "todo ID do catálogo do backend deve estar no asset",
            emptySet<String>(),
            backendIds - assetIds,
        )
        assertEquals(
            "o asset não pode ter IDs que o backend não tem",
            emptySet<String>(),
            assetIds - backendIds,
        )
    }

    @Test
    fun asset_sem_ids_duplicados() {
        val ids = assetExercises().map { it.jsonObject["id"]!!.jsonPrimitive.content }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun asset_campos_obrigatorios() {
        assetExercises().forEach { element ->
            val exercise = element.jsonObject
            val id = exercise["id"]!!.jsonPrimitive.content
            val name = exercise["name"]!!.jsonPrimitive.content
            val muscles = exercise["primaryMuscles"]!!.jsonArray
            assertTrue("equipment deve ser string para $id", exercise["equipment"]!!.jsonPrimitive.isString)
            assertTrue("id vazio", id.isNotBlank())
            assertTrue("name vazio para $id", name.isNotBlank())
            assertTrue("primaryMuscles vazio para $id", muscles.isNotEmpty())
        }
    }

    @Test
    fun asset_ordem_deterministica() {
        val ids = assetExercises().map { it.jsonObject["id"]!!.jsonPrimitive.content }

        assertEquals(ids.sorted(), ids)
    }
}