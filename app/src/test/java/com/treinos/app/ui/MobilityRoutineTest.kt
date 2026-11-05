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
}
