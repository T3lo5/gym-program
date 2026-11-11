package com.treinos.app.data

import com.treinos.app.data.local.ExerciseUsageEvent
import org.junit.Assert.assertEquals
import org.junit.Test

private fun event(
    exerciseId: String,
    outcome: String,
    daysAgo: Long,
): ExerciseUsageEvent =
    ExerciseUsageEvent(
        exerciseId = exerciseId,
        outcome = outcome,
        occurredAt = nowMillis - daysAgo * 24 * 60 * 60 * 1000L,
        planId = null,
        dayId = null,
        source = "generation",
    )

private const val nowMillis = 1_700_000_000_000L

class ExerciseUsageTest {

    @Test
    fun `completed confere peso positivo`() {
        val events = listOf(event("ex1", "completed", 5))
        assertEquals(1, usageWeightFor("ex1", events, nowMillis))
    }

    @Test
    fun `skipped e trocado devolvem peso zero (piso)`() {
        val events = listOf(event("ex1", "skipped", 5), event("ex1", "swapped", 5))
        assertEquals(0, usageWeightFor("ex1", events, nowMillis))
    }

    @Test
    fun `peso nunca excede o teto 5`() {
        val events = List(10) { event("ex1", "completed", 1) }
        assertEquals(5, usageWeightFor("ex1", events, nowMillis))
    }

    @Test
    fun `eventos fora da janela de 90 dias nao contam`() {
        val events = listOf(event("ex1", "completed", 91))
        assertEquals(0, usageWeightFor("ex1", events, nowMillis))
    }

    @Test
    fun `decaimento reduz peso de eventos antigos`() {
        val recent = usageWeightFor("ex1", listOf(event("ex1", "completed", 10)), nowMillis)
        val old = usageWeightFor("ex1", listOf(event("ex1", "completed", 70)), nowMillis)
        assertEquals(1, recent)
        assertEquals(0, old) // 1 * 0.33 = 0.33 -> toInt() = 0
    }

    @Test
    fun `outcome desconhecido nao altera o score`() {
        val events = listOf(event("ex1", "unknown", 5))
        assertEquals(0, usageWeightFor("ex1", events, nowMillis))
    }

    @Test
    fun `usageWeightsForCatalog mapeia todos os ids`() {
        val events = listOf(event("ex1", "completed", 5), event("ex2", "skipped", 5))
        val weights = usageWeightsForCatalog(listOf("ex1", "ex2", "ex3"), events, nowMillis)
        assertEquals(mapOf("ex1" to 1, "ex2" to 0, "ex3" to 0), weights)
    }
}
