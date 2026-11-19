package com.treinos.wear.ui

import com.treinos.protocol.WearSessionExercise
import com.treinos.protocol.WearSessionSnapshot

/**
 * H4-16: reducer puro da sessão offline — o pulso avança exercício/série,
 * registra série, calcula o descanso e finaliza sem o celular. Sem Android e
 * sem Room: a persistência é o `WearStandaloneStore` (wrapper fino).
 */
data class WearStandaloneState(
    val sessionId: Long = 0L,
    val dayName: String = "",
    val modality: String = "strength",
    val exerciseIndex: Int = 0,
    val setNumber: Int = 1,
    val plannedSets: Int = 0,
    val restEndsAt: Long? = null,
    val status: String = "in_progress",
    val pendingCount: Int = 0,
    /** Exercício do snapshot na posição atual (null quando não há snapshot). */
    val exercise: WearSessionExercise? = null,
    val exerciseCount: Int = 0,
) {
    val hasSnapshot: Boolean get() = sessionId != 0L
}

/** Ações do reducer offline. */
sealed interface WearStandaloneAction {

    /** Snapshot recebido (ou relido do Room) — restaura o estado da sessão. */
    data class Start(
        val snapshot: WearSessionSnapshot?,
        /**
         * H6-1: séries registradas por `sessionExerciseId` (Room do relógio) —
         * a posição corrente é derivada delas em `resumePosition`.
         */
        val loggedByExercise: Map<Long, Int>,
        val pendingCount: Int,
        /** Deadline persistido do descanso (`null` = sem descanso). */
        val restEndsAt: Long? = null,
        val now: Long = 0L,
    ) : WearStandaloneAction

    /** Série registrada offline; `clientSetId` é gerado pelo store. */
    data class LogSet(val reps: Int?, val weightKg: Double?, val now: Long) : WearStandaloneAction

    data class SkipRest(val now: Long) : WearStandaloneAction

    data class Finish(val now: Long) : WearStandaloneAction

    /** Tick de 1 s — o descanso conta por wall-clock no próprio estado. */
    data class Tick(val now: Long) : WearStandaloneAction
}

/** Posição restaurada da sessão (H6-1): exercício corrente e próxima série. */
data class WearStandaloneResume(
    val exerciseIndex: Int,
    val setNumber: Int,
)

object WearStandaloneSession {

    /**
     * Restante do descanso = `max(0, restEndsAt - now)` — wall-clock, igual ao
     * celular (H3-26): sobrevive a reinício do relógio; `restEndsAt` no
     * passado zera o descanso.
     */
    fun restante(restEndsAt: Long?, now: Long): Int =
        restEndsAt?.let { end -> ((end - now) / 1000L).toInt().coerceAtLeast(0) } ?: 0

    /**
     * H6-1: posição corrente derivada das séries registradas no relógio —
     * o primeiro exercício com séries abaixo do planejado é o corrente
     * (`setNumber = registradas + 1`); todos completos → último exercício com
     * série extra. Sem exercícios, não há posição (tela de "sem treino").
     *
     * Limitação documentada na spec: séries registradas no CELULAR antes de
     * ficar offline não estão no Room do relógio — a derivação usa só o que o
     * pulso registrou (fallback: exercício 1, como antes da correção).
     */
    fun resumePosition(
        snapshot: WearSessionSnapshot,
        loggedByExercise: Map<Long, Int>,
    ): WearStandaloneResume? {
        val exercicios = snapshot.exercises.sortedBy { it.exerciseIndex }
        val primeiroIncompleto = exercicios.firstOrNull { ex ->
            val registradas = loggedByExercise[ex.sessionExerciseId] ?: 0
            registradas < ex.plannedSets.coerceAtLeast(1)
        }
        val alvo = primeiroIncompleto ?: exercicios.lastOrNull() ?: return null
        val registradas = loggedByExercise[alvo.sessionExerciseId] ?: 0
        return WearStandaloneResume(
            exerciseIndex = alvo.exerciseIndex,
            setNumber = (registradas + 1).coerceAtLeast(1),
        )
    }

    fun reduce(state: WearStandaloneState, action: WearStandaloneAction): WearStandaloneState =
        when (action) {
            is WearStandaloneAction.Start -> start(action)
            is WearStandaloneAction.LogSet -> logSet(state, action)
            is WearStandaloneAction.SkipRest -> state.copy(restEndsAt = null)
            is WearStandaloneAction.Finish -> state.copy(status = "finished", restEndsAt = null)
            is WearStandaloneAction.Tick -> state
        }

    private fun start(action: WearStandaloneAction.Start): WearStandaloneState {
        val snapshot = action.snapshot ?: return WearStandaloneState(
            pendingCount = action.pendingCount,
        )
        val exercicios = snapshot.exercises
        // H6-1: exercício/série correntes derivados das séries registradas —
        // reiniciar o relógio no meio da sessão não volta mais ao exercício 1.
        val resume = resumePosition(snapshot, action.loggedByExercise)
        val exercicio = exercicios.firstOrNull { it.exerciseIndex == resume?.exerciseIndex }
            ?: exercicios.firstOrNull()
        val setNumber = resume?.setNumber
            ?: ((action.loggedByExercise[exercicio?.sessionExerciseId] ?: 0) + 1)
                .coerceIn(1, exercicio?.plannedSets?.coerceAtLeast(1) ?: 1)
        return WearStandaloneState(
            sessionId = snapshot.sessionId,
            dayName = snapshot.dayName,
            modality = snapshot.modality,
            exerciseIndex = resume?.exerciseIndex ?: 0,
            setNumber = setNumber,
            plannedSets = exercicio?.plannedSets ?: 0,
            // H6-1: o descanso persistido (`restEndsAt` futuro) sobrevive ao
            // reinício; no passado zera (sem card fantasma).
            restEndsAt = action.restEndsAt?.takeIf { it > action.now },
            status = "in_progress",
            pendingCount = action.pendingCount,
            exercise = exercicio,
            exerciseCount = exercicios.size,
        )
    }

    private fun logSet(state: WearStandaloneState, action: WearStandaloneAction.LogSet): WearStandaloneState {
        if (!state.hasSnapshot || state.status != "in_progress") return state
        val atual = state.exercise ?: return state
        val proximoSet = state.setNumber + 1
        return if (state.plannedSets > 0 && proximoSet <= state.plannedSets) {
            // Ainda há séries no exercício: avança a série e abre o descanso
            // do exercício (wall-clock).
            state.copy(
                setNumber = proximoSet,
                restEndsAt = action.now + atual.restSeconds * 1000L,
                pendingCount = state.pendingCount + 1,
            )
        } else {
            avancarExercicio(state)
        }
    }

    private fun avancarExercicio(state: WearStandaloneState): WearStandaloneState {
        val proximoIndice = state.exerciseIndex + 1
        // Snapshot com 0 exercícios (corrida/mobilidade) não avança — o
        // "Finalizar" encerra offline sem séries.
        if (proximoIndice >= state.exerciseCount) {
            return state.copy(status = "finished", restEndsAt = null)
        }
        return state.copy(
            exerciseIndex = proximoIndice,
            setNumber = 1,
            restEndsAt = null,
            pendingCount = state.pendingCount + 1,
        )
    }
}
