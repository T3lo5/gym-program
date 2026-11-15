package com.treinos.app.ui

/**
 * Estado da rotina de mobilidade em andamento (H3-31). Vive no
 * `AppViewModel`, então sobrevive à rotação; a morte de processo retoma do
 * estado persistido em `AppPreferences` (H3-55), pelo mesmo padrão do
 * descanso (H3-26): deadline em wall-clock.
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

    /**
     * Restaura a sessão de mobilidade a partir do estado persistido (H3-55),
     * pelo mesmo padrão do descanso (H3-26): deadline em wall-clock sobrevive
     * à morte do processo.
     *
     * @param routine Rotina atual do catálogo (pode ter mudado desde a gravação).
     * @param saved Estado salvo: routineId, movementIndex, movementDeadlineAtMillis e startedAtMillis.
     * @param nowMillis Agora em epoch millis (wall-clock).
     * @return Estado restaurado — no mesmo movimento, encadeando os vencidos
     *   ou concluído — ou `null` se a rotina não corresponder ou o estado for
     *   inválido.
     */
    fun resume(
        routine: MobilityRoutine,
        saved: SavedMobilityState,
        nowMillis: Long,
    ): MobilitySessionState? {
        if (saved.routineId != routine.id) return null
        if (saved.movementIndex !in routine.movements.indices) return null
        if (saved.movementDeadlineAtMillis <= 0L || saved.startedAtMillis <= 0L) return null

        val elapsed = ((nowMillis - saved.startedAtMillis) / 1000L).coerceAtLeast(0L)

        // Movimento salvo ainda com prazo: retoma nele com o restante real
        // (deadline − now, mínimo 0).
        if (saved.movementDeadlineAtMillis > nowMillis) {
            val movement = routine.movements[saved.movementIndex]
            return MobilitySessionState(
                routineId = routine.id,
                movementIndex = saved.movementIndex,
                secondsRemaining = remainingSeconds(saved.movementDeadlineAtMillis, nowMillis)
                    .coerceAtMost(movement.durationSeconds),
                elapsedSeconds = elapsed,
                running = true,
            )
        }

        // O movimento salvo venceu com o app morto: o relógio não parou.
        // Avança encadeando os vencidos — transição de 5 s (H3-31) + duração
        // de cada movimento — até o primeiro com tempo restante; se todos
        // passaram, a sessão terminou (estado final, tempo real decorrido).
        var cursor = saved.movementDeadlineAtMillis
        var index = saved.movementIndex
        while (true) {
            val transitionEnd = cursor + MobilityRoutineTemplates.TRANSITION_SECONDS * 1000L
            if (nowMillis < transitionEnd) {
                return MobilitySessionState(
                    routineId = routine.id,
                    movementIndex = index,
                    secondsRemaining = 0,
                    transitionSecondsRemaining = remainingSeconds(transitionEnd, nowMillis),
                    elapsedSeconds = elapsed,
                    running = true,
                )
            }
            val next = index + 1
            val movement = routine.movements.getOrNull(next)
                ?: return MobilitySessionState(
                    routineId = routine.id,
                    movementIndex = routine.movements.size - 1,
                    secondsRemaining = 0,
                    elapsedSeconds = elapsed,
                    running = false,
                )
            val movementEnd = transitionEnd + movement.durationSeconds * 1000L
            if (nowMillis < movementEnd) {
                return MobilitySessionState(
                    routineId = routine.id,
                    movementIndex = next,
                    secondsRemaining = remainingSeconds(movementEnd, nowMillis),
                    elapsedSeconds = elapsed,
                    running = true,
                )
            }
            cursor = movementEnd
            index = next
        }
    }

    /** Segundos até o deadline, arredondados para cima (mínimo 0). */
    private fun remainingSeconds(deadlineMillis: Long, nowMillis: Long): Int =
        ((deadlineMillis - nowMillis + 999) / 1000L).toInt().coerceAtLeast(0)

    /** Estado persistido da mobilidade (H3-55). Puro para testes. */
    data class SavedMobilityState(
        val routineId: String,
        val movementIndex: Int,
        val movementDeadlineAtMillis: Long,
        val startedAtMillis: Long,
    )

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
