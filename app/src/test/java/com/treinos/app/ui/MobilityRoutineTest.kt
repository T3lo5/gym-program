package com.treinos.app.ui

import com.treinos.app.ui.MobilityRoutineTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobilityRoutineTest {
    @Test
    fun templates_tem_movimentos_validos() {
        MobilityRoutineTemplates.routines.forEach { routine ->
            assertFalse(routine.movements.isEmpty())
            routine.movements.forEach { movement ->
                assertTrue(movement.durationSeconds >= 20)
                assertTrue(movement.durationSeconds <= 60)
                assertTrue(movement.name.isNotBlank())
                assertTrue(movement.cue.isNotBlank())
            }
        }
    }

    @Test
    fun duracao_soma_movimentos_e_transicoes() {
        val routine = MobilityRoutineTemplates.getRoutine("back_10")!!
        val movementsTotal = routine.movements.sumOf { it.durationSeconds }
        val transitions = (routine.movements.size - 1) * 5
        assertEquals(movementsTotal + transitions, MobilityRoutineTemplates.totalDurationSeconds(routine))
    }

    @Test
    fun nao_ha_movimento_duplicado_na_mesma_rotina() {
        MobilityRoutineTemplates.routines.forEach { routine ->
            val names = routine.movements.map { it.name to it.side }
            assertEquals(names.size, names.distinct().size)
        }
    }

    @Test
    fun foco_back_tem_movimentos_de_costas() {
        val routine = MobilityRoutineTemplates.getRoutine("back_10")!!
        assertEquals("back", routine.focus)
        val hasBack = routine.movements.any { it.name.contains("Gato") || it.name.contains("torácic") || it.name.contains("piriforme") || it.name.contains("Costas") }
        assertTrue(hasBack)
    }

    @Test
    fun rotina_vazia_e_rejeitada() {
        val empty = MobilityRoutine("empty", "Empty", "test", "test", emptyList())
        assertFalse(MobilityRoutineTemplates.isValidTemplate(empty))
    }

    @Test
    fun foco_back_escolhe_back_10() {
        val routine = MobilityRoutineTemplates.routineForFocus("back", 10)
        assertEquals("back_10", routine.id)
    }

    @Test
    fun foco_hips_escolhe_hips_10() {
        val routine = MobilityRoutineTemplates.routineForFocus("hips", 10)
        assertEquals("hips_10", routine.id)
    }

    @Test
    fun foco_post_run_escolhe_post_run_8() {
        val routine = MobilityRoutineTemplates.routineForFocus("post_run", 8)
        assertEquals("post_run_8", routine.id)
    }

    @Test
    fun foco_desconhecido_usa_full_body() {
        val routine = MobilityRoutineTemplates.routineForFocus("qualquer_coisa", 15)
        assertEquals("full_15", routine.id)
    }
}
