package com.treinos.wear.ui

import com.treinos.protocol.WearWorkoutState

/** Telas possíveis do pulso no modo conectado. */
enum class WearScreenKind {
    /** Sem estado publicado (primeira abertura, celular longe do app). */
    NoState,

    /** Sessão finalizada. */
    Finished,

    /**
     * Sem sessão em andamento no celular: status `idle` publicado pela
     * correção do canal wear — ou qualquer status desconhecido.
     */
    Idle,

    /** Sessão de força em andamento. */
    Strength,

    /** Corrida/mobilidade em andamento. */
    Modality,
}

/**
 * Decisão pura de qual tela mostrar no modo conectado. Status desconhecido
 * (celular mais novo que o relógio) cai em [WearScreenKind.Idle] — nunca numa
 * tela de treino com campos vazios.
 */
fun wearScreenKind(state: WearWorkoutState?): WearScreenKind = when {
    state == null -> WearScreenKind.NoState
    state.status == "finished" -> WearScreenKind.Finished
    state.status == "in_progress" ->
        if (WearSessionKind.from(state.modality) == WearSessionKind.Strength) {
            WearScreenKind.Strength
        } else {
            WearScreenKind.Modality
        }
    else -> WearScreenKind.Idle
}
