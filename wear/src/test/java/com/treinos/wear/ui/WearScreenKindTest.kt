package com.treinos.wear.ui

import com.treinos.protocol.WearWorkoutState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Correção do "pedir no relógio e nada acontece": o celular passa a publicar
 * status `idle` quando não há sessão em andamento — e qualquer status
 * desconhecido (celular mais novo que o relógio) tem de cair na tela idle,
 * nunca numa tela de treino com campos vazios.
 */
class WearScreenKindTest {

    private fun estado(status: String, modality: String = "strength") = WearWorkoutState(
        sessionId = 1L,
        dayName = "Treino A — Empurrar",
        modality = modality,
        exerciseIndex = 0,
        exerciseCount = 4,
        exerciseName = "Agachamento",
        setNumber = 1,
        plannedSets = 3,
        targetReps = "8-10",
        status = status,
        updatedAt = 0L,
    )

    @Test
    fun estado_nulo_mapeia_sem_estado() {
        assertEquals(WearScreenKind.NoState, wearScreenKind(null))
    }

    @Test
    fun idle_mapeia_tela_idle() {
        assertEquals(WearScreenKind.Idle, wearScreenKind(estado("idle")))
    }

    @Test
    fun status_desconhecido_mapeia_idle() {
        assertEquals(WearScreenKind.Idle, wearScreenKind(estado("completed")))
        assertEquals(WearScreenKind.Idle, wearScreenKind(estado("qualquer-coisa")))
    }

    @Test
    fun in_progress_forca_mapeia_strength() {
        assertEquals(WearScreenKind.Strength, wearScreenKind(estado("in_progress")))
    }

    @Test
    fun in_progress_corrida_mapeia_modalidade() {
        assertEquals(WearScreenKind.Modality, wearScreenKind(estado("in_progress", "run")))
        assertEquals(WearScreenKind.Modality, wearScreenKind(estado("in_progress", "mobility")))
    }

    @Test
    fun finished_mapeia_fim() {
        assertEquals(WearScreenKind.Finished, wearScreenKind(estado("finished")))
    }
}
