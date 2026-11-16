package com.treinos.app.ui.progression

import com.treinos.app.data.local.WorkoutPlanExerciseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * H4-12: regras puras de aplicação da sugestão ao exercício do plano.
 * Sem Android/Room — a decisão de **o que** muda é testada aqui; o
 * repositório testa **onde** grava.
 */
class ProgressionApplicationTest {

    private fun exercise(
        targetReps: String = "8-12",
        targetWeightKg: Double? = null,
    ) = WorkoutPlanExerciseEntity(
        id = 100,
        dayId = 10,
        exerciseId = "Barbell_Bench_Press",
        exerciseName = "Supino reto",
        exerciseOrder = 0,
        sets = 3,
        targetReps = targetReps,
        targetWeightKg = targetWeightKg,
    )

    @Test
    fun none_nao_aplica() {
        val sugestao = ProgressionSuggestion(
            action = ProgressionAction.NONE,
            suggestedWeightKg = 42.5,
            suggestedReps = 13,
            reason = "Histórico insuficiente.",
        )

        assertNull(applyProgressionToPlanExercise(exercise(), sugestao))
    }

    @Test
    fun aplica_carga_sugerida_sem_mexer_em_reps() {
        val sugestao = ProgressionSuggestion(
            action = ProgressionAction.INCREASE,
            suggestedWeightKg = 42.5,
            suggestedReps = null,
            reason = "Duas sessões no topo da faixa com RPE confortável.",
        )

        val aplicado = applyProgressionToPlanExercise(exercise(), sugestao)

        assertEquals(42.5, aplicado?.targetWeightKg)
        // A faixa de reps não muda quando a sugestão é de carga.
        assertEquals("8-12", aplicado?.targetReps)
        // Os demais campos do plano ficam intactos.
        assertEquals(3, aplicado?.sets)
        assertEquals("Barbell_Bench_Press", aplicado?.exerciseId)
    }

    @Test
    fun aplica_reps_em_faixa_deslocando_os_extremos() {
        val sugestao = ProgressionSuggestion(
            action = ProgressionAction.INCREASE,
            suggestedWeightKg = null,
            suggestedReps = 13,
            reason = "Duas sessões no topo da faixa com RPE confortável.",
        )

        val aplicado = applyProgressionToPlanExercise(exercise(targetReps = "8-12"), sugestao)

        // delta = 13 - 12 = 1 → "9-13".
        assertEquals("9-13", aplicado?.targetReps)
        // A carga-alvo não muda quando a sugestão é de reps.
        assertNull(aplicado?.targetWeightKg)
    }

    @Test
    fun aplica_reps_em_alvo_unico() {
        val sugestao = ProgressionSuggestion(
            action = ProgressionAction.INCREASE,
            suggestedWeightKg = null,
            suggestedReps = 9,
            reason = "Duas sessões no topo da faixa com RPE confortável.",
        )

        val aplicado = applyProgressionToPlanExercise(exercise(targetReps = "8"), sugestao)

        assertEquals("9", aplicado?.targetReps)
    }

    @Test
    fun faixa_desconhecida_nao_muda_reps() {
        val sugestao = ProgressionSuggestion(
            action = ProgressionAction.INCREASE,
            suggestedWeightKg = null,
            suggestedReps = 13,
            reason = "Duas sessões no topo da faixa com RPE confortável.",
        )

        // "AMRAP" e vazio não casam com faixa nem alvo único: ficam como estão.
        assertEquals("AMRAP", applyProgressionToPlanExercise(exercise(targetReps = "AMRAP"), sugestao)?.targetReps)
        assertEquals("", applyProgressionToPlanExercise(exercise(targetReps = ""), sugestao)?.targetReps)
    }

    @Test
    fun decrease_aplica_peso_menor() {
        val sugestao = ProgressionSuggestion(
            action = ProgressionAction.DECREASE,
            suggestedWeightKg = 57.0,
            suggestedReps = null,
            reason = "RPE alto ou repetições abaixo da faixa; reduza a carga.",
        )

        val aplicado = applyProgressionToPlanExercise(exercise(targetWeightKg = 60.0), sugestao)

        assertEquals(57.0, aplicado?.targetWeightKg)
        assertEquals("8-12", aplicado?.targetReps)
    }

    @Test
    fun ambos_nulos_nao_aplica() {
        val sugestao = ProgressionSuggestion(
            action = ProgressionAction.INCREASE,
            suggestedWeightKg = null,
            suggestedReps = null,
            reason = "Duas sessões no topo da faixa com RPE confortável.",
        )

        assertNull(applyProgressionToPlanExercise(exercise(), sugestao))
    }
}
