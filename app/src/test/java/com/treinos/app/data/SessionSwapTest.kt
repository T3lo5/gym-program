package com.treinos.app.data

import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.media.ExerciseMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

private val catalog = mapOf(
    "bar_bench" to media("bar_bench", "Supino Barra", listOf("chest"), "barbell"),
    "dumb_bench" to media("dumb_bench", "Supino Halter", listOf("chest"), "dumbbell"),
    "leg_press" to media("leg_press", "Leg Press", listOf("legs"), "machine"),
    "row" to media("row", "Remada", listOf("back"), "barbell"),
)

private val equipment = listOf("barbell", "dumbbell", "body only")

private fun sessionExercise(
    id: Long = 1,
    exerciseId: String,
    exerciseName: String = "",
): SessionExerciseEntity =
    SessionExerciseEntity(
        id = id,
        sessionId = 10,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        plannedSets = 3,
    )

class SessionSwapTest {

    @Test
    fun candidatos_filtram_por_musculo_e_equipamento() {
        val original = sessionExercise(exerciseId = "bar_bench", exerciseName = "Supino Barra")
        val taken = listOf("bar_bench")

        val candidates = sessionSwapCandidates(original, taken, catalog, equipment)

        val ids = candidates.map { it.id }.toSet()
        assertTrue("deve conter dumb_bench", ids.contains("dumb_bench"))
        assertFalse("não deve conter leg_press (máquina não informada)", ids.contains("leg_press"))
        assertFalse("não deve conter row (músculo diferente)", ids.contains("row"))
        assertFalse("não deve sugerir o próprio", ids.contains("bar_bench"))
    }

    @Test
    fun candidatos_nao_incluem_exercicios_ja_presentes_no_dia() {
        val original = sessionExercise(exerciseId = "bar_bench")
        val taken = listOf("bar_bench", "dumb_bench")

        val candidates = sessionSwapCandidates(original, taken, catalog, equipment)

        assertFalse("não deve conter dumb_bench já presente", candidates.map { it.id }.contains("dumb_bench"))
    }

    @Test
    fun candidatos_com_equipamento_vazio_nao_filtra_por_equipamento() {
        val original = sessionExercise(exerciseId = "bar_bench")
        val taken = listOf("bar_bench")

        val candidates = sessionSwapCandidates(original, taken, catalog, emptyList())

        val ids = candidates.map { it.id }.toSet()
        // equipamento vazio não filtra por equipamento, mas ainda filtra por músculo (chest)
        assertTrue("dumb_bench aparece (mesmo músculo)", ids.contains("dumb_bench"))
        assertFalse("leg_press não aparece (músculo diferente)", ids.contains("leg_press"))
        assertFalse("row não aparece (músculo diferente)", ids.contains("row"))
        assertFalse("bar_bench excluído", ids.contains("bar_bench"))
    }
}
