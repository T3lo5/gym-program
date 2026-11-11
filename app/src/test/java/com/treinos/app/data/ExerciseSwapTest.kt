package com.treinos.app.data

import com.treinos.app.data.local.WorkoutPlanExerciseEntity
import com.treinos.app.data.media.ExerciseMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun media(
    id: String,
    name: String,
    muscles: List<String>,
    equipment: String,
): ExerciseMedia =
    ExerciseMedia(
        id = id,
        name = name,
        primaryMuscles = muscles,
        equipment = equipment,
        instructions = emptyList(),
        imageUrl = null,
    )

private fun planExercise(
    id: String,
    name: String = "",
    muscles: String = "",
    equipment: String = "",
    order: Int = 0,
): WorkoutPlanExerciseEntity =
    WorkoutPlanExerciseEntity(
        id = 0,
        dayId = 1,
        exerciseId = id,
        exerciseName = name,
        primaryMuscles = muscles,
        equipment = equipment,
        exerciseOrder = order,
    )

class ExerciseSwapTest {

    private val chestBar = media("bar_bench", "Supino Barra", listOf("chest"), "barbell")
    private val chestDumb = media("dumb_bench", "Supino Halter", listOf("chest"), "dumbbell")
    private val legPress = media("leg_press", "Leg Press", listOf("legs"), "machine")
    private val squat = media("squat", "Agachamento", listOf("legs"), "body only")
    private val row = media("row", "Remada", listOf("back"), "barbell")

    private val equipment = listOf("barbell", "dumbbell", "body only")

    private fun dayExercises(vararg exercises: WorkoutPlanExerciseEntity): List<WorkoutPlanExerciseEntity> =
        exercises.toList()

    private fun catalog(): Map<String, ExerciseMedia> =
        mapOf(
            chestBar.id to chestBar,
            chestDumb.id to chestDumb,
            legPress.id to legPress,
            squat.id to squat,
            row.id to row,
        )

    @Test
    fun troca_valida_retorna_ok_com_id_e_nome() {
        val original = planExercise("bar_bench", "Supino Barra", "chest", "barbell")
        val day = dayExercises(original)

        val result = validateSwap(original, day, "dumb_bench", catalog(), equipment)

        assertNotNull("esperava Ok", (result as? SwapResult.Ok))
        val ok = result as SwapResult.Ok
        assertEquals("dumb_bench", ok.exerciseId)
        assertEquals("Supino Halter", ok.exerciseName)
    }

    @Test
    fun troca_mesmo_exercicio_e_rejeitada() {
        val original = planExercise("bar_bench", "Supino Barra", "chest", "barbell")
        val day = dayExercises(original)

        val result = validateSwap(original, day, "bar_bench", catalog(), equipment)

        assertTrue("esperava Error", result is SwapResult.Error)
    }

    @Test
    fun troca_musculo_diferente_e_rejeitada() {
        val original = planExercise("bar_bench", "Supino Barra", "chest", "barbell")
        val day = dayExercises(original)

        // "row" (back) tem equipamento barbell (permitido), mas músculo diferente.
        val result = validateSwap(original, day, "row", catalog(), equipment)

        assertTrue("esperava Error", result is SwapResult.Error)
        assertTrue("mensagem de músculo", (result as SwapResult.Error).message.contains("Músculo"))
    }

    @Test
    fun troca_equipamento_nao_informado_e_rejeitada() {
        val original = planExercise("bar_bench", "Supino Barra", "chest", "barbell")
        val day = dayExercises(original)

        val result = validateSwap(original, day, "leg_press", catalog(), listOf("barbell"))

        assertTrue("esperava Error", result is SwapResult.Error)
        assertTrue("mensagem de equipamento", (result as SwapResult.Error).message.contains("Equipamento"))
    }

    @Test
    fun troca_duplicada_no_dia_e_rejeitada() {
        val original = planExercise("bar_bench", "Supino Barra", "chest", "barbell")
        val already = planExercise("dumb_bench", "Supino Halter", "chest", "dumbbell", order = 1)
        val day = dayExercises(original, already)

        val result = validateSwap(original, day, "dumb_bench", catalog(), equipment)

        assertTrue("esperava Error", result is SwapResult.Error)
        assertTrue("mensagem de duplicado", (result as SwapResult.Error).message.contains("já está neste dia"))
    }

    @Test
    fun troca_id_inexistente_no_catalogo_e_rejeitada() {
        val original = planExercise("bar_bench", "Supino Barra", "chest", "barbell")
        val day = dayExercises(original)

        val result = validateSwap(original, day, "nao_existe", catalog(), equipment)

        assertTrue("esperava Error", result is SwapResult.Error)
        assertTrue("mensagem de catálogo", (result as SwapResult.Error).message.contains("não encontrado no catálogo"))
    }

    @Test
    fun candidatos_filtram_por_musculo_e_equipamento() {
        val original = planExercise("bar_bench", "Supino Barra", "chest", "barbell")
        val day = dayExercises(original)

        val candidates = swapCandidates(original, day, catalog(), equipment)

        val ids = candidates.map { it.id }.toSet()
        assertTrue("deve conter dumb_bench", ids.contains("dumb_bench"))
        assertFalse("não deve conter leg_press (máquina não informada)", ids.contains("leg_press"))
        assertFalse("não deve conter row (músculo diferente)", ids.contains("row"))
        assertFalse("não deve sugerir o próprio", ids.contains("bar_bench"))
    }

    @Test
    fun candidatos_nao_incluem_exercicios_ja_presentes_no_dia() {
        val original = planExercise("bar_bench", "Supino Barra", "chest", "barbell")
        val present = planExercise("dumb_bench", "Supino Halter", "chest", "dumbbell", order = 1)
        val day = dayExercises(original, present)

        val candidates = swapCandidates(original, day, catalog(), equipment)

        assertFalse("não deve conter dumb_bench já presente", candidates.map { it.id }.contains("dumb_bench"))
    }
}
