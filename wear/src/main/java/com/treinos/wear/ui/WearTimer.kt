package com.treinos.wear.ui

import com.treinos.protocol.WearWorkoutState

data class WearTimerState(
    val remainingSeconds: Int,
    val isRunning: Boolean,
    val connected: Boolean,
    /**
     * Chave do descanso atualmente sincronizado: "sessionId:exerciseIndex:setNumber:restRemainingSeconds".
     * Nula quando o timer não está sincronizado a um descanso (início, após Skip, sessão finalizada).
     */
    val restKey: String? = null,
)

sealed interface WearTimerAction {
    object Tick : WearTimerAction
    data class Reset(val seconds: Int) : WearTimerAction
    object Skip : WearTimerAction
    data class SetConnection(val connected: Boolean) : WearTimerAction
}

object WearTimer {
    fun reduce(state: WearTimerState, action: WearTimerAction): WearTimerState {
        return when (action) {
            is WearTimerAction.Tick -> {
                if (!state.isRunning) state
                else {
                    val novo = state.remainingSeconds - 1
                    if (novo <= 0) state.copy(remainingSeconds = 0, isRunning = false)
                    else state.copy(remainingSeconds = novo)
                }
            }
            is WearTimerAction.Reset -> state.copy(
                remainingSeconds = action.seconds.coerceAtLeast(0),
                isRunning = action.seconds > 0,
            )
            WearTimerAction.Skip -> state.copy(remainingSeconds = 0, isRunning = false)
            is WearTimerAction.SetConnection -> state.copy(connected = action.connected)
        }
    }

    /**
     * Decide Reset/Skip a partir de um `WearWorkoutState` novo, usando a regra de
     * sincronização do spec H3-18. Devolve o novo estado do timer e a nova
     * `restKey` sincronizada.
     *
     * - restRemainingSeconds > 0 e restKey != lastKey → Reset(restRemainingSeconds), lastKey = restKey
     * - restRemainingSeconds > 0 e restKey == lastKey → nada (republicação do mesmo descanso;
     *   o timer local continua de onde está)
     * - restRemainingSeconds nulo/0 e timer rodando → Skip (descanso acabou ou foi pulado no celular)
     * - caso contrário → estado do timer inalterado
     */
    fun syncFromState(
        state: WearTimerState,
        incoming: WearWorkoutState?,
    ): WearTimerState {
        if (incoming == null) return state
        val rest = incoming.restRemainingSeconds ?: 0
        val newKey = if (rest > 0) restKeyFor(incoming) else null
        return when {
            rest > 0 && newKey != state.restKey -> {
                reduce(state, WearTimerAction.Reset(rest))
                    .copy(restKey = newKey)
            }
            rest > 0 -> state
            state.isRunning -> reduce(state, WearTimerAction.Skip).copy(restKey = null)
            else -> state
        }
    }

    /**
     * Chave do descanso: "sessionId:exerciseIndex:setNumber:restRemainingSeconds".
     * `setNumber` muda a cada série logada, então dois descansos consecutivos com a
     * mesma duração reiniciam corretamente.
     */
    fun restKeyFor(state: WearWorkoutState): String =
        "${state.sessionId}:${state.exerciseIndex}:${state.setNumber}:${state.restRemainingSeconds}"
}