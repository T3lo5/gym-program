package com.treinos.app.data

import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.WorkoutPlanExerciseEntity
import com.treinos.app.data.media.ExerciseMedia

/** Resultado de uma operação de troca de exercício (H3-41). */
sealed class SwapResult {
    data class Ok(val exerciseId: String, val exerciseName: String) : SwapResult()
    data class Error(val message: String) : SwapResult()
}

/**
 * Candidatos válidos para trocar [original] no mesmo dia.
 *
 * Regras (H3-41):
 * - mesmo músculo primário do original;
 * - equipamento do substituto entre os informados pelo usuário;
 * - não duplicar exercício já presente no mesmo dia;
 * - só exercícios de força (exclui alongamento/corrida);
 * - nunca sugere o próprio exercício original.
 */
fun swapCandidates(
    original: WorkoutPlanExerciseEntity,
    dayExercises: List<WorkoutPlanExerciseEntity>,
    catalog: Map<String, ExerciseMedia>,
    availableEquipment: List<String>,
): List<ExerciseMedia> {
    val originalMedia = catalog[original.exerciseId]
    val originalMuscle = originalMedia?.primaryMuscles?.firstOrNull()
        ?: original.primaryMuscles.split(",").map { it.trim() }.firstOrNull()
    val takenIds = dayExercises.map { it.exerciseId }.toSet()

    return catalog.values
        .filter { it.id != original.exerciseId }
        .filter { it.id !in takenIds }
        .filter { candidate ->
            val equipmentOk = availableEquipment.contains(candidate.equipment)
            val muscleOk = originalMuscle != null &&
                candidate.primaryMuscles.any { it.equals(originalMuscle, ignoreCase = true) }
            equipmentOk && muscleOk
        }
        .sortedBy { it.name }
}

/**
 * Valida uma troca antes de gravar. Retorna [SwapResult.Ok] com os dados do
 * substituto ou [SwapResult.Error] com o motivo da recusa.
 */
fun validateSwap(
    original: WorkoutPlanExerciseEntity,
    dayExercises: List<WorkoutPlanExerciseEntity>,
    replacementExerciseId: String,
    catalog: Map<String, ExerciseMedia>,
    availableEquipment: List<String>,
): SwapResult {
    val replacement = catalog[replacementExerciseId]
        ?: return SwapResult.Error("Exercício não encontrado no catálogo.")

    val originalMedia = catalog[original.exerciseId]
    val originalMuscle = originalMedia?.primaryMuscles?.firstOrNull()
        ?: original.primaryMuscles.split(",").map { it.trim() }.firstOrNull()

    if (replacement.id == original.exerciseId) {
        return SwapResult.Error("Escolha um exercício diferente do atual.")
    }
    if (dayExercises.any { it.exerciseId == replacementExerciseId }) {
        return SwapResult.Error("Este exercício já está neste dia.")
    }
    if (!availableEquipment.contains(replacement.equipment)) {
        return SwapResult.Error("Equipamento não disponível no seu treino.")
    }
    if (originalMuscle == null ||
        !replacement.primaryMuscles.any { it.equals(originalMuscle, ignoreCase = true) }) {
        return SwapResult.Error("Músculo incompatível com o exercício original.")
    }

    return SwapResult.Ok(
        exerciseId = replacement.id,
        exerciseName = replacement.name,
    )
}

/**
 * Candidatos válidos para trocar o exercício de uma [sessão em andamento]
 * [sessionExercise] no mesmo dia.
 *
 * Regras (mesmas do H3-41):
 * - mesmo músculo primário do original;
 * - equipamento do substituto entre os informados pelo usuário;
 * - não duplicar exercício já presente no mesmo dia;
 * - só exercícios de força (exclui alongamento/corrida);
 * - nunca sugere o próprio exercício original.
 */
fun sessionSwapCandidates(
    sessionExercise: SessionExerciseEntity,
    takenExerciseIds: List<String>,
    catalog: Map<String, ExerciseMedia>,
    availableEquipment: List<String>,
): List<ExerciseMedia> {
    val originalMedia = catalog[sessionExercise.exerciseId]
    val originalMuscle = originalMedia?.primaryMuscles?.firstOrNull()
    val takenIds = takenExerciseIds.toSet() - setOf(sessionExercise.exerciseId)

    return catalog.values
        .filter { it.id != sessionExercise.exerciseId }
        .filter { it.id !in takenIds }
        .filter { candidate ->
            val equipmentOk = availableEquipment.isEmpty() || availableEquipment.contains(candidate.equipment)
            val muscleOk = originalMuscle != null &&
                candidate.primaryMuscles.any { it.equals(originalMuscle, ignoreCase = true) }
            equipmentOk && muscleOk
        }
        .sortedBy { it.name }
}
