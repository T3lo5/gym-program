package com.treinos.wear.tile

import com.treinos.protocol.WearWorkoutState

/**
 * Estado puro do tile do relógio (H4-14). Sem Android: quem renderiza resolve
 * os recursos de string; aqui só se decide O QUE mostrar.
 */
sealed interface WearTileModel {
    /** Treino em andamento no celular, com estado recente: dia, exercício e série. */
    data class Active(
        val dayName: String,
        val exerciseName: String,
        val setNumber: Int,
        val plannedSets: Int,
    ) : WearTileModel

    /** Sem estado, estado terminal ou estado mais velho que a janela: só abre o app. */
    data object Idle : WearTileModel
}

/**
 * Janela de validade do estado do treino (H4-14, regra 9): depois de 12 h sem
 * atualização o tile mostra o estado ocioso — o celular não publica estado
 * terminal ao finalizar, então a janela limita o tile obsoleto.
 */
const val WEAR_TILE_STALE_AFTER_MS = 12L * 60L * 60L * 1000L

/**
 * Decisão pura do conteúdo do tile (H4-14), a partir do último estado
 * publicado pelo celular e do instante atual.
 *
 * Regras: sem estado, estado fora de `in_progress` ou estado mais velho que
 * a janela de 12 h → `Idle`; caso contrário → `Active`. `updatedAt` no futuro
 * (relógio adiantado) não é stale: a diferença negativa não supera o limite
 * (regra 11). `setNumber`/`plannedSets` inválidos são normalizados sem quebrar
 * (regra 12).
 */
fun wearTileModel(state: WearWorkoutState?, nowMillis: Long): WearTileModel {
    if (state == null) return WearTileModel.Idle
    if (state.status != "in_progress") return WearTileModel.Idle
    if (nowMillis - state.updatedAt > WEAR_TILE_STALE_AFTER_MS) return WearTileModel.Idle
    return WearTileModel.Active(
        dayName = state.dayName,
        exerciseName = state.exerciseName,
        setNumber = state.setNumber.coerceAtLeast(1),
        plannedSets = state.plannedSets.coerceAtLeast(0),
    )
}
