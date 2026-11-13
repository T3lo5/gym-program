package com.treinos.app.ui

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import org.junit.Assert.assertEquals
import org.junit.Test

class HapticFeedbackTest {

    @Test
    fun `evento_salvar_serie_mapeia_confirmacao`() {
        assertEquals(HapticFeedbackType.LongPress, hapticFeedbackForEvent(HapticEvent.SetSaved))
        assertEquals(HapticFeedbackType.LongPress, hapticFeedbackForEvent(HapticEvent.SetEdited))
        assertEquals(HapticFeedbackType.LongPress, hapticFeedbackForEvent(HapticEvent.RepeatLastSet))
    }

    @Test
    fun `evento_controle_descanso_mapeia_toque_leve`() {
        assertEquals(HapticFeedbackType.TextHandleMove, hapticFeedbackForEvent(HapticEvent.RestAdjusted))
    }

    @Test
    fun `evento_avanco_e_fim_treino_confirmacao`() {
        assertEquals(HapticFeedbackType.LongPress, hapticFeedbackForEvent(HapticEvent.ExerciseAdvanced))
        assertEquals(HapticFeedbackType.LongPress, hapticFeedbackForEvent(HapticEvent.WorkoutFinished))
        assertEquals(HapticFeedbackType.LongPress, hapticFeedbackForEvent(HapticEvent.WorkoutAbandoned))
        assertEquals(HapticFeedbackType.LongPress, hapticFeedbackForEvent(HapticEvent.RunFinished))
        assertEquals(HapticFeedbackType.LongPress, hapticFeedbackForEvent(HapticEvent.RunDiscarded))
        assertEquals(HapticFeedbackType.LongPress, hapticFeedbackForEvent(HapticEvent.MobilityFinished))
        assertEquals(HapticFeedbackType.LongPress, hapticFeedbackForEvent(HapticEvent.MobilityDiscarded))
    }

    @Test
    fun `evento_mobility_controle_mapeia_toque_leve`() {
        assertEquals(HapticFeedbackType.TextHandleMove, hapticFeedbackForEvent(HapticEvent.MobilityControl))
    }

    @Test
    fun `evento_excluir_serie_mapeia_confirmacao`() {
        assertEquals(HapticFeedbackType.LongPress, hapticFeedbackForEvent(HapticEvent.SetDeleted))
    }

    @Test
    fun `mapa_cobre_eventos_de_treino_sem_nulos`() {
        // Garante que todos os eventos do sealed interface têm mapeamento explícito
        val allEvents = listOf(
            HapticEvent.SetSaved,
            HapticEvent.SetEdited,
            HapticEvent.SetDeleted,
            HapticEvent.RepeatLastSet,
            HapticEvent.RestAdjusted,
            HapticEvent.ExerciseAdvanced,
            HapticEvent.WorkoutFinished,
            HapticEvent.WorkoutAbandoned,
            HapticEvent.RunFinished,
            HapticEvent.RunDiscarded,
            HapticEvent.MobilityControl,
            HapticEvent.MobilityFinished,
            HapticEvent.MobilityDiscarded,
        )

        allEvents.forEach { event ->
            val feedback = hapticFeedbackForEvent(event)
            assertEquals("Evento $event deve ter mapeamento não-nulo", feedback != null, true)
        }
    }
}