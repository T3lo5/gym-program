package com.treinos.wear.ui

import com.treinos.protocol.WearCommand
import com.treinos.protocol.WearCommandKind

/**
 * H4-15: estado puro do editor de carga/reps no pulso.
 *
 * `weightKg = 0.0` significa "sem carga" (peso corporal) e vira `null` no
 * comando — nunca `0.0` gravado no celular. Carga anda em passos de 2,5 kg e
 * reps em passos de 1, com travas em 0,0 kg e 1..999 reps.
 */
data class WearSetEditorState(
    val weightKg: Double,
    val reps: Int,
) {
    /**
     * Comando `LOG_SET` com os valores editados: carga 0,0 → `weightKg = null`
     * (peso corporal); o celular deixa de cair no fallback da última série.
     */
    fun toLogSetCommand(sessionId: Long, commandId: String): WearCommand = WearCommand(
        commandId = commandId,
        sessionId = sessionId,
        kind = WearCommandKind.LOG_SET,
        reps = reps,
        weightKg = if (weightKg == 0.0) null else weightKg,
        rpe = null,
    )
}

sealed interface WearSetEditorAction {
    object IncrementWeight : WearSetEditorAction
    object DecrementWeight : WearSetEditorAction
    object IncrementReps : WearSetEditorAction
    object DecrementReps : WearSetEditorAction

    /**
     * Volta ao default do exercício: chamado ao abrir a tela e ao trocar de
     * exercício (`exerciseIndex` novo no `WearWorkoutState`).
     */
    data class Reset(
        val lastWeightKg: Double?,
        val lastReps: Int?,
        val targetReps: String,
    ) : WearSetEditorAction
}

object WearSetEditor {
    const val WEIGHT_STEP_KG = 2.5
    const val MIN_WEIGHT_KG = 0.0
    const val MIN_REPS = 1
    const val MAX_REPS = 999
    const val DEFAULT_REPS = 8

    fun reduce(state: WearSetEditorState, action: WearSetEditorAction): WearSetEditorState =
        when (action) {
            WearSetEditorAction.IncrementWeight -> state.copy(
                weightKg = normalizaCarga(state.weightKg + WEIGHT_STEP_KG),
            )
            WearSetEditorAction.DecrementWeight -> state.copy(
                weightKg = normalizaCarga((state.weightKg - WEIGHT_STEP_KG).coerceAtLeast(MIN_WEIGHT_KG)),
            )
            WearSetEditorAction.IncrementReps -> state.copy(reps = (state.reps + 1).coerceAtMost(MAX_REPS))
            WearSetEditorAction.DecrementReps -> state.copy(reps = (state.reps - 1).coerceAtLeast(MIN_REPS))
            is WearSetEditorAction.Reset -> default(action.lastWeightKg, action.lastReps, action.targetReps)
        }

    /**
     * Default do editor: última série do exercício; sem última, carga `0,0`
     * (sem carga) e o primeiro inteiro de `targetReps`; sem inteiro no alvo,
     * `8`. Valor não finito que chegar por qualquer motivo é ignorado e vale
     * o default.
     */
    private fun default(lastWeightKg: Double?, lastReps: Int?, targetReps: String): WearSetEditorState =
        WearSetEditorState(
            weightKg = normalizaCarga(lastWeightKg ?: MIN_WEIGHT_KG),
            reps = (lastReps ?: primeiroInteiro(targetReps) ?: DEFAULT_REPS).coerceIn(MIN_REPS, MAX_REPS),
        )

    /** Arredonda para 1 casa decimal (2,5 kg), trava em 0,0 e descarta NaN/Infinity. */
    private fun normalizaCarga(weightKg: Double): Double {
        if (!weightKg.isFinite()) return MIN_WEIGHT_KG
        val arredondada = Math.round(weightKg * 10.0) / 10.0
        return if (arredondada < MIN_WEIGHT_KG) MIN_WEIGHT_KG else arredondada
    }

    /** Primeiro inteiro dentro do texto do alvo ("8-12" → 8; "AMRAP" → null). */
    private fun primeiroInteiro(targetReps: String): Int? =
        Regex("""\d+""").find(targetReps)?.value?.toIntOrNull()
}
