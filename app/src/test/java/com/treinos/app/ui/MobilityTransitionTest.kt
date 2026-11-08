package com.treinos.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Máquina de estados do timer de mobilidade (H3-31): um segundo por tick,
 * transição de TRANSITION_SECONDS entre movimentos com toque para pular.
 */
class MobilityTransitionTest {

    private val routine = MobilityRoutineTemplates.getRoutine("back_10")!!

    @Test
    fun transicao_5s_entre_movimentos() {
        // Primeiro movimento: 40 s.
        val state = MobilitySessionEngine.start(routine) ?: return
        assertEquals(0, state.movementIndex)
        assertEquals(40, state.secondsRemaining)
        assertTrue(state.running)

        // Consome os 40 s do primeiro movimento; ao zerar, entra em transição.
        var ticked = state
        repeat(40) { ticked = MobilitySessionEngine.tick(ticked, routine) }
        assertEquals(0, ticked.secondsRemaining)
        assertEquals(5, ticked.transitionSecondsRemaining)
        assertTrue(ticked.inTransition)

        // Os 5 s de transição passam e o segundo movimento começa (60 s).
        repeat(5) { ticked = MobilitySessionEngine.tick(ticked, routine) }
        assertEquals(1, ticked.movementIndex)
        assertEquals(60, ticked.secondsRemaining)
        assertEquals(0, ticked.transitionSecondsRemaining)
        assertFalse(ticked.inTransition)
        assertTrue(ticked.running)
    }

    @Test
    fun pular_transicao_avanca() {
        var state = MobilitySessionEngine.start(routine) ?: return
        repeat(40) { state = MobilitySessionEngine.tick(state, routine) }
        assertTrue(state.inTransition)

        // Toque para pular a transição: vai direto ao próximo movimento.
        state = MobilitySessionEngine.skipTransition(state, routine)
        assertEquals(1, state.movementIndex)
        assertEquals(60, state.secondsRemaining)
        assertFalse(state.inTransition)
    }
}
