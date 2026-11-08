package com.treinos.app.ui

/**
 * Estado da rotina de mobilidade em andamento (H3-31). Vive no
 * `AppViewModel`, então sobrevive à rotação; morte de processo reinicia a
 * rotina — perda aceitável por decisão da spec.
 */
data class MobilitySessionState(
    val routineId: String,
    val movementIndex: Int,
    val secondsRemaining: Int,
    val elapsedSeconds: Long = 0L,
    val running: Boolean = false,
    val paused: Boolean = false,
    /** > 0 = contagem regressiva da transição antes do próximo movimento. */
    val transitionSecondsRemaining: Int = 0,
) {
    val inTransition: Boolean get() = transitionSecondsRemaining > 0
    val finished: Boolean get() = !running && !inTransition && secondsRemaining <= 0
}

/** O que a tela de mobilidade renderiza: a rotina escolhida e seu timer. */
data class MobilityScreenState(
    val routine: MobilityRoutine? = null,
    val session: MobilitySessionState? = null,
)

/**
 * Máquina de estados pura do timer de mobilidade (H3-31): um segundo por
 * [tick], transição de `TRANSITION_SECONDS` entre movimentos com toque para
 * pular. Sem Android — coberta por `MobilityTransitionTest`.
 */
object MobilitySessionEngine {

    fun start(routine: MobilityRoutine, index: Int = 0): MobilitySessionState? {
        val movement = routine.movements.getOrNull(index) ?: return null
        return MobilitySessionState(
            routineId = routine.id,
            movementIndex = index,
            secondsRemaining = movement.durationSeconds,
            running = true,
        )
    }

    /** Um segundo de relógio; congelado em pausa e após o fim da rotina. */
    fun tick(state: MobilitySessionState, routine: MobilityRoutine): MobilitySessionState {
        if (!state.running || state.paused) return state
        val elapsed = state.elapsedSeconds + 1
        if (state.inTransition) {
            val next = state.transitionSecondsRemaining - 1
            return if (next == 0) {
                beginMovement(state, routine, state.movementIndex + 1, elapsed)
            } else {
                state.copy(transitionSecondsRemaining = next, elapsedSeconds = elapsed)
            }
        }
        if (state.secondsRemaining <= 0) return state
        val remaining = state.secondsRemaining - 1
        return when {
            remaining > 0 -> state.copy(secondsRemaining = remaining, elapsedSeconds = elapsed)
            state.movementIndex < routine.movements.size - 1 ->
                // H3-31: `TRANSITION_SECONDS` passa a valer — pausa curta entre
                // movimentos antes do próximo começar.
                state.copy(
                    secondsRemaining = 0,
                    transitionSecondsRemaining = MobilityRoutineTemplates.TRANSITION_SECONDS,
                    elapsedSeconds = elapsed,
                )
            else -> state.copy(secondsRemaining = 0, running = false, elapsedSeconds = elapsed)
        }
    }

    fun togglePause(state: MobilitySessionState): MobilitySessionState =
        if (!state.running) state else state.copy(paused = !state.paused)

    /** Toque na transição: entra no próximo movimento imediatamente. */
    fun skipTransition(state: MobilitySessionState, routine: MobilityRoutine): MobilitySessionState =
        if (!state.inTransition) {
            state
        } else {
            beginMovement(state, routine, state.movementIndex + 1, state.elapsedSeconds)
        }

    /** "Voltar"/"Avançar" manuais: reinicia o movimento escolhido; `null` fora da rotina. */
    fun goTo(state: MobilitySessionState, routine: MobilityRoutine, index: Int): MobilitySessionState? {
        if (index !in routine.movements.indices) return null
        return beginMovement(state, routine, index, state.elapsedSeconds)
    }

    private fun beginMovement(
        state: MobilitySessionState,
        routine: MobilityRoutine,
        index: Int,
        elapsedSeconds: Long,
    ): MobilitySessionState {
        val movement = routine.movements.getOrNull(index)
            ?: return state.copy(
                transitionSecondsRemaining = 0,
                running = false,
                elapsedSeconds = elapsedSeconds,
            )
        return state.copy(
            movementIndex = index,
            secondsRemaining = movement.durationSeconds,
            transitionSecondsRemaining = 0,
            paused = false,
            elapsedSeconds = elapsedSeconds,
        )
    }
}
