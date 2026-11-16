package com.treinos.app.ui.progression

import com.treinos.app.data.local.WorkoutPlanExerciseEntity
import kotlin.math.max

/**
 * H4-12: aplica a sugestão do `ProgressionEngine` ao exercício do plano.
 * Puro, sem Android/Room: decide **o que** muda; quem chama (repositório)
 * decide **onde** grava. Aplicar é opt-in — nenhum caminho chama isto sem
 * o toque explícito do usuário.
 */
fun applyProgressionToPlanExercise(
    exercise: WorkoutPlanExerciseEntity,
    suggestion: ProgressionSuggestion,
): WorkoutPlanExerciseEntity? = when {
    // Nada a aplicar: sem sugestão válida o plano fica como está.
    suggestion.action == ProgressionAction.NONE -> null
    // Carga sugerida vira carga-alvo; a faixa de reps não muda.
    suggestion.suggestedWeightKg != null ->
        exercise.copy(targetWeightKg = suggestion.suggestedWeightKg)
    // Sem carga, a faixa de reps se desloca; a carga-alvo não muda.
    suggestion.suggestedReps != null ->
        exercise.copy(targetReps = applyRepsSuggestion(exercise.targetReps, suggestion.suggestedReps))
    // Ação sem valor concreto (ex.: MAINTAIN sem carga no histórico).
    else -> null
}

/**
 * Desloca o alvo de reps para o valor sugerido:
 * - faixa `"a-b"` → desloca os dois extremos por `delta = suggestedReps - b`,
 *   com piso 1 no início e o fim nunca abaixo do início;
 * - alvo único `"n"` → devolve o valor sugerido;
 * - texto que não casa (`"AMRAP"`, vazio) → devolve o valor atual sem mudar.
 */
fun applyRepsSuggestion(currentTargetReps: String, suggestedReps: Int): String {
    val text = currentTargetReps.trim()
    REPS_RANGE_PATTERN.matchEntire(text)?.let { match ->
        val a = match.groupValues[1].toIntOrNull() ?: return text
        val b = match.groupValues[2].toIntOrNull() ?: return text
        val delta = suggestedReps - b
        val aNovo = max(1, a + delta)
        val bNovo = max(aNovo, b + delta)
        return "$aNovo-$bNovo"
    }
    SINGLE_REPS_PATTERN.matchEntire(text)?.let { match ->
        val n = match.groupValues[1].toIntOrNull() ?: return text
        if (n < 1) return text
        return suggestedReps.toString()
    }
    return text
}

private val REPS_RANGE_PATTERN = Regex("""(\d+)\s*[-–]\s*(\d+)""")
private val SINGLE_REPS_PATTERN = Regex("""(\d+)""")
