package com.treinos.app.ui

import com.treinos.app.data.local.WorkoutPlanEntity
import com.treinos.app.data.local.WorkoutPlanWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lógica pura de seleção de plano na Home (H3-15). */
class PlanSelectionTest {

    private fun plan(id: Long, kind: String = "main", name: String = "Plano $id") =
        WorkoutPlanWithDetails(
            plan = WorkoutPlanEntity(id = id, name = name, kind = kind),
            days = emptyList(),
        )

    @Test
    fun padrao_primeiro_main() {
        val plans = listOf(plan(1, kind = "extra"), plan(2, kind = "main"), plan(3, kind = "main"))

        assertEquals(2L, resolveSelectedPlan(plans, selectedPlanId = null)?.plan?.id)
    }

    @Test
    fun selecao_sobrevive_a_geracao() {
        val extra = plan(1, kind = "extra")
        val mainAntigo = plan(2, kind = "main")
        // Novo main gerado: a lista muda, mas o extra selecionado continua nela.
        val plans = listOf(plan(3, kind = "main"), extra, mainAntigo)

        assertEquals(1L, resolveSelectedPlan(plans, selectedPlanId = 1)?.plan?.id)
    }

    @Test
    fun excluir_selecionado_volta_ao_main() {
        val plans = listOf(plan(2, kind = "main"))

        assertEquals(2L, resolveSelectedPlan(plans, selectedPlanId = 1)?.plan?.id)
    }

    @Test
    fun sem_main_seleciona_o_primeiro_da_lista() {
        val plans = listOf(plan(1, kind = "extra"), plan(2, kind = "extra"))

        assertEquals(1L, resolveSelectedPlan(plans, selectedPlanId = null)?.plan?.id)
    }

    @Test
    fun lista_vazia_nao_seleciona_nada() {
        assertNull(resolveSelectedPlan(emptyList(), selectedPlanId = null))
        assertNull(resolveSelectedPlan(emptyList(), selectedPlanId = 1))
    }
}