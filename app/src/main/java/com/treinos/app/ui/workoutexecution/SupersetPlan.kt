package com.treinos.app.ui.workoutexecution

import com.treinos.app.data.local.WorkoutPlanExerciseEntity

/**
 * H4-6: descanso de transição entre membros de um superset (bi/tri-set).
 * O `min` com o `restSeconds` do plano evita alongar descansos curtos.
 */
const val SUPERSET_TRANSITION_REST_SECONDS = 15

/**
 * Grupo de superset derivado do plano: `exerciseIds` são os membros em ordem
 * de execução e `startIndex` é a posição do primeiro membro no dia ordenado.
 * Derivado, não persistido — a flag `isSupersetWithPrevious` é a fonte.
 */
data class SupersetGroup(
    val exerciseIds: List<String>,
    val startIndex: Int,
)

/**
 * Agrupa os exercícios do dia em supersets (H4-6):
 *
 * - ordenados por `exerciseOrder`;
 * - um exercício com `isSupersetWithPrevious = true` entra no grupo do
 *   anterior;
 * - grupo válido tem >= 2 exercícios;
 * - o primeiro do dia nunca entra em grupo pela própria flag (flag nele é
 *   ignorada) — plano editado à mão vira exercício sozinho, sem crash.
 */
fun supersetGroups(exercises: List<WorkoutPlanExerciseEntity>): List<SupersetGroup> {
    val sorted = exercises.sortedBy { it.exerciseOrder }
    val groups = mutableListOf<SupersetGroup>()
    var index = 0
    while (index < sorted.size) {
        // A flag do âncora nunca é lida: quem entra no grupo é o exercício
        // seguinte com flag ligada, inclusive quando o âncora é o primeiro
        // do dia com a flag marcada à mão.
        var last = index
        while (last + 1 < sorted.size && sorted[last + 1].isSupersetWithPrevious) {
            last++
        }
        if (last > index) {
            groups += SupersetGroup(
                exerciseIds = sorted.slice(index..last).map { it.exerciseId },
                startIndex = index,
            )
        }
        index = last + 1
    }
    return groups
}

/**
 * Descanso após uma série do exercício (H4-6): membro de superset que NÃO é
 * o último do grupo descansa a transição (`min(15, restSeconds)`; descanso
 * <= 0 do plano manda e vira 0). Último membro, exercício sem grupo ou fora
 * do plano usa o `restSeconds` do exercício (comportamento atual).
 */
fun restAfterSet(
    exercises: List<WorkoutPlanExerciseEntity>,
    exerciseId: String,
    restSeconds: Int,
): Int {
    val sorted = exercises.sortedBy { it.exerciseOrder }
    val group = supersetGroups(sorted).firstOrNull { exerciseId in it.exerciseIds }
        ?: return restSeconds
    if (group.exerciseIds.last() == exerciseId) return restSeconds
    return if (restSeconds <= 0) 0 else minOf(SUPERSET_TRANSITION_REST_SECONDS, restSeconds)
}

/**
 * Verdadeiro quando o exercício pode alternar o superset com o anterior
 * (H4-6): precisa existir no plano e não ser o primeiro do dia. O botão de
 * alternância não aparece no primeiro exercício.
 */
fun canJoinSuperset(exercises: List<WorkoutPlanExerciseEntity>, exerciseId: String): Boolean {
    val sorted = exercises.sortedBy { it.exerciseOrder }
    return sorted.indexOfFirst { it.exerciseId == exerciseId } > 0
}
