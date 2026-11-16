package com.treinos.app.ui.programs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * H4-10: todo `exerciseId` dos templates existe no asset
 * `exercise_swap_catalog.json` (versão 1) e nome/músculos/equipamento batem.
 * Um id removido do asset quebraria o template silenciosamente — este teste
 * falha no gate antes do release.
 */
class ProgramCatalogAssetTest {

    private fun findFirst(vararg candidates: String): File =
        candidates.map(::File).firstOrNull { it.exists() }
            ?: error("Arquivo não encontrado: ${candidates.joinToString()}")

    private fun asset(): File =
        findFirst(
            "src/main/assets/exercise_swap_catalog.json",
            "app/src/main/assets/exercise_swap_catalog.json",
            "../app/src/main/assets/exercise_swap_catalog.json",
        )

    private fun assetById(): Map<String, JsonObject> =
        Json.parseToJsonElement(asset().readText())
            .jsonObject["exercises"]!!.jsonArray
            .associate { element ->
                val exercise = element.jsonObject
                exercise["id"]!!.jsonPrimitive.content to exercise
            }

    /** Todos os exercícios dos 4 templates, na saída real de [buildProgram]. */
    private fun templateExercises() =
        listOf("ppl", "531", "gzclp", "phul")
            .flatMap { id -> buildProgram(id, "2026-12-13T00:00:00Z")!!.workout.days }
            .flatMap { it.exercises }

    @Test
    fun todos_exercise_ids_existem_no_asset() {
        val assetIds = assetById().keys
        val faltando = templateExercises().map { it.exerciseId }.distinct() - assetIds
        assertTrue("exerciseIds fora do asset: $faltando", faltando.isEmpty())
    }

    @Test
    fun nome_musculos_equipamento_batem_com_asset() {
        val asset = assetById()
        templateExercises().forEach { exercise ->
            val entry = asset.getValue(exercise.exerciseId)
            assertEquals(
                "nome divergente em ${exercise.exerciseId}",
                entry["name"]!!.jsonPrimitive.content,
                exercise.exerciseName,
            )
            assertEquals(
                "músculos divergentes em ${exercise.exerciseId}",
                entry["primaryMuscles"]!!.jsonArray.map { it.jsonPrimitive.content }.joinToString(", "),
                exercise.primaryMuscles.joinToString(", "),
            )
            assertEquals(
                "equipamento divergente em ${exercise.exerciseId}",
                entry["equipment"]!!.jsonPrimitive.content,
                exercise.equipment,
            )
        }
    }
}
