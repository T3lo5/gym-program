package com.treinos.wear.tile

import com.treinos.protocol.WearWorkoutState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testes JVM puros do modelo do tile (H4-14): janela de 12 h, status e
 * normalização de séries, sem Android.
 */
class WearTileModelTest {

    private fun estado(
        status: String = "in_progress",
        updatedAt: Long = 1_000L,
        dayName: String = "Push",
        exerciseName: String = "Supino",
        setNumber: Int = 2,
        plannedSets: Int = 4,
    ) = WearWorkoutState(
        sessionId = 7L,
        dayName = dayName,
        exerciseIndex = 0,
        exerciseCount = 5,
        exerciseName = exerciseName,
        setNumber = setNumber,
        plannedSets = plannedSets,
        targetReps = "8-12",
        status = status,
        updatedAt = updatedAt,
    )

    @Test
    fun sem_estado_e_idle() {
        assertEquals(WearTileModel.Idle, wearTileModel(state = null, nowMillis = 10_000L))
    }

    @Test
    fun estado_in_progress_recente_e_ativo() {
        val resultado = wearTileModel(estado(updatedAt = 9_000L), nowMillis = 10_000L)
        assertEquals(
            WearTileModel.Active(
                dayName = "Push",
                exerciseName = "Supino",
                setNumber = 2,
                plannedSets = 4,
            ),
            resultado,
        )
    }

    @Test
    fun estado_antigo_e_idle() {
        val agora = 100_000_000_000L
        // Um milissegundo além da janela de 12 h já é stale.
        val stale = wearTileModel(
            estado(updatedAt = agora - WEAR_TILE_STALE_AFTER_MS - 1),
            nowMillis = agora,
        )
        assertEquals(WearTileModel.Idle, stale)
        // Exatamente na janela (12 h) ainda é ativo: o limite é estrito.
        val noLimite = wearTileModel(
            estado(updatedAt = agora - WEAR_TILE_STALE_AFTER_MS),
            nowMillis = agora,
        )
        assertEquals(
            WearTileModel.Active("Push", "Supino", 2, 4),
            noLimite,
        )
    }

    @Test
    fun status_finished_e_idle() {
        val resultado = wearTileModel(estado(status = "finished"), nowMillis = 2_000L)
        assertEquals(WearTileModel.Idle, resultado)
    }

    @Test
    fun updated_at_futuro_nao_e_stale() {
        // Relógio adiantado: updatedAt no futuro dá diferença negativa, que
        // não supera o limite (regra 11).
        val resultado = wearTileModel(estado(updatedAt = 11_000L), nowMillis = 10_000L)
        assertEquals(
            WearTileModel.Active("Push", "Supino", 2, 4),
            resultado,
        )
    }

    @Test
    fun set_number_e_planned_sets_zerados_nao_quebram() {
        val resultado = wearTileModel(
            estado(setNumber = 0, plannedSets = 0),
            nowMillis = 2_000L,
        )
        assertEquals(
            WearTileModel.Active("Push", "Supino", setNumber = 1, plannedSets = 0),
            resultado,
        )
    }
}
