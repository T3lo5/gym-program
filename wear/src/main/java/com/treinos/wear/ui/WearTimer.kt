package com.treinos.wear.ui

data class WearTimerState(
    val remainingSeconds: Int,
    val isRunning: Boolean,
    val connected: Boolean,
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
}
