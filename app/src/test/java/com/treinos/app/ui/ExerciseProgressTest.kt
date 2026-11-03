package com.treinos.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseProgressTest {

    private fun set(number: Int) = SetRecord(setNumber = number, reps = 10, weightKg = 20.0)

    @Test
    fun `sem series o proximo numero e 1`() {
        val progress = calculateProgress(plannedSets = 4, completedSets = 0, sets = emptyList())

        assertEquals(1, progress.currentSet)
        assertEquals(1, progress.nextSetNumber)
        assertFalse(progress.isCompleted)
    }

    @Test
    fun `proximo numero e o maior setNumber registrado mais um`() {
        val progress = calculateProgress(
            plannedSets = 4,
            completedSets = 2,
            sets = listOf(set(1), set(2)),
        )

        assertEquals(3, progress.nextSetNumber)
        assertEquals(3, progress.currentSet)
        assertFalse(progress.isCompleted)
    }

    @Test
    fun `series registradas fora de ordem nao quebram o proximo numero`() {
        val progress = calculateProgress(
            plannedSets = 4,
            completedSets = 3,
            sets = listOf(set(3), set(1), set(2)),
        )

        assertEquals(4, progress.nextSetNumber)
    }

    @Test
    fun `progresso respeita o limite de series planejadas`() {
        val progress = calculateProgress(
            plannedSets = 3,
            completedSets = 10,
            sets = listOf(set(1), set(2), set(3), set(4)),
        )

        assertEquals(3, progress.completedSets)
        assertEquals(3, progress.currentSet)
        assertTrue(progress.isCompleted)
    }

    @Test
    fun `zero series planejadas e normalizado para pelo menos uma`() {
        val progress = calculateProgress(plannedSets = 0, completedSets = 0, sets = emptyList())

        assertEquals(1, progress.plannedSets)
        assertEquals(1, progress.currentSet)
    }

    @Test
    fun `proximo exercicio incompleto ignora os concluidos`() {
        val concluido = ExerciseSessionData(
            sessionExerciseId = 1,
            exerciseId = "a",
            exerciseName = "A",
            plannedSets = 2,
            completedSets = 2,
            sets = listOf(set(1), set(2)),
            restSeconds = 60,
        )
        val pendente = concluido.copy(
            sessionExerciseId = 2,
            exerciseId = "b",
            exerciseName = "B",
            completedSets = 1,
            sets = listOf(set(1)),
        )

        assertEquals(pendente, nextIncompleteExercise(listOf(concluido, pendente)))
        assertNull(nextIncompleteExercise(listOf(concluido)))
    }

    @Test
    fun `texto de progresso usa a serie atual e o total planejado`() {
        val progress = calculateProgress(plannedSets = 4, completedSets = 1, sets = listOf(set(1)))

        assertEquals("2/4 series", progressText(progress))
    }
}
