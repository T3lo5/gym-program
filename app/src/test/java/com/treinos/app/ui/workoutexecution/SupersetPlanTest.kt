package com.treinos.app.ui.workoutexecution

import com.treinos.app.data.local.WorkoutPlanExerciseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H4-6: agrupamento de supersets (bi/tri-set) e descanso de transição —
 * funções puras, sem dependência de Android.
 */
class SupersetPlanTest {

    private fun exercicio(
        id: Long,
        exerciseId: String,
        order: Int,
        superset: Boolean = false,
        restSeconds: Int = 90,
    ) = WorkoutPlanExerciseEntity(
        id = id,
        dayId = 10,
        exerciseId = exerciseId,
        exerciseName = exerciseId,
        exerciseOrder = order,
        restSeconds = restSeconds,
        isSupersetWithPrevious = superset,
    )

    @Test
    fun grupo_de_dois() {
        val plano = listOf(
            exercicio(1, "ex1", 0),
            exercicio(2, "ex2", 1, superset = true),
        )

        val grupos = supersetGroups(plano)

        assertEquals(1, grupos.size)
        assertEquals(listOf("ex1", "ex2"), grupos.single().exerciseIds)
        assertEquals(0, grupos.single().startIndex)
    }

    @Test
    fun tri_set_tem_tres_membros() {
        val plano = listOf(
            exercicio(1, "ex1", 0),
            exercicio(2, "ex2", 1, superset = true),
            exercicio(3, "ex3", 2, superset = true),
        )

        val grupos = supersetGroups(plano)

        assertEquals(1, grupos.size)
        assertEquals(listOf("ex1", "ex2", "ex3"), grupos.single().exerciseIds)
    }

    @Test
    fun primeiro_marcado_e_ignorado() {
        // Plano editado à mão: a flag no primeiro do dia não cria grupo e
        // o exercício fica sozinho, sem crash.
        val plano = listOf(
            exercicio(1, "ex1", 0, superset = true),
            exercicio(2, "ex2", 1),
        )

        assertTrue(supersetGroups(plano).isEmpty())
        assertEquals(90, restAfterSet(plano, "ex1", 90))
        assertFalse(canJoinSuperset(plano, "ex1"))
    }

    @Test
    fun sem_flags_nao_ha_grupo() {
        val plano = listOf(
            exercicio(1, "ex1", 0),
            exercicio(2, "ex2", 1),
            exercicio(3, "ex3", 2),
        )

        assertTrue(supersetGroups(plano).isEmpty())
    }

    @Test
    fun transicao_entre_membros() {
        val plano = listOf(
            exercicio(1, "ex1", 0),
            exercicio(2, "ex2", 1, superset = true),
            exercicio(3, "ex3", 2, superset = true),
        )

        // Membro que não é o último do grupo descansa a transição (15 s):
        // vale para o âncora e para os membros do meio.
        assertEquals(SUPERSET_TRANSITION_REST_SECONDS, restAfterSet(plano, "ex1", 90))
        assertEquals(SUPERSET_TRANSITION_REST_SECONDS, restAfterSet(plano, "ex2", 90))
    }

    @Test
    fun ultimo_membro_usa_resto_normal() {
        val plano = listOf(
            exercicio(1, "ex1", 0),
            exercicio(2, "ex2", 1, superset = true),
            exercicio(3, "ex3", 2, superset = true),
        )

        assertEquals(90, restAfterSet(plano, "ex3", 90))
        // Sem grupo, o descanso do plano passa direto.
        assertEquals(120, restAfterSet(plano, "ex3", 120))
    }

    @Test
    fun resto_zero_nao_vira_transicao() {
        val plano = listOf(
            exercicio(1, "ex1", 0, restSeconds = 0),
            exercicio(2, "ex2", 1, superset = true),
        )

        // O plano manda: descanso <= 0 nunca vira a transição de 15 s.
        assertEquals(0, restAfterSet(plano, "ex1", 0))
        assertEquals(0, restAfterSet(plano, "ex1", -5))
    }

    @Test
    fun resto_menor_que_transicao_e_respeitado() {
        val plano = listOf(
            exercicio(1, "ex1", 0, restSeconds = 10),
            exercicio(2, "ex2", 1, superset = true),
        )

        // min(15, restSeconds): descanso curto do plano não é alongado.
        assertEquals(10, restAfterSet(plano, "ex1", 10))
    }

    @Test
    fun exercicio_fora_do_plano_usa_resto_normal() {
        val plano = listOf(
            exercicio(1, "ex1", 0),
            exercicio(2, "ex2", 1, superset = true),
        )

        // Sessão antiga ou plano trocado: usa restSeconds, sem crash.
        assertEquals(90, restAfterSet(plano, "sumido", 90))
        assertFalse(canJoinSuperset(plano, "sumido"))
    }

    @Test
    fun desligar_do_meio_divide_grupo() {
        // A, B(off), C(on): A fica sozinho e o grupo vira [B, C].
        val plano = listOf(
            exercicio(1, "ex1", 0),
            exercicio(2, "ex2", 1),
            exercicio(3, "ex3", 2, superset = true),
        )

        val grupos = supersetGroups(plano)

        assertEquals(1, grupos.size)
        assertEquals(listOf("ex2", "ex3"), grupos.single().exerciseIds)
        assertEquals(1, grupos.single().startIndex)
        // A (sozinho) e C (último do grupo) usam o descanso do plano; B vira
        // âncora não-última do grupo [B, C] e paga a transição fixa de 15 s.
        assertEquals(90, restAfterSet(plano, "ex1", 90))
        assertEquals(15, restAfterSet(plano, "ex2", 90))
        assertEquals(90, restAfterSet(plano, "ex3", 90))
    }

    @Test
    fun can_join_falso_no_primeiro() {
        val plano = listOf(
            exercicio(1, "ex1", 0),
            exercicio(2, "ex2", 1),
        )

        // O botão de alternância não aparece no primeiro exercício do dia.
        assertFalse(canJoinSuperset(plano, "ex1"))
        assertTrue(canJoinSuperset(plano, "ex2"))
    }
}
