package com.treinos.app.ui

/**
 * Registro simplificado de uma serie, usado para calcular progresso sem depender de entidades Room.
 */
data class SetRecord(
    val setNumber: Int,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val rpe: Double? = null,
)

/**
 * Visao simplificada de um exercicio dentro de uma sessao, sem dependencia do Android/Room.
 * contem o id do banco para gravacao de series.
 */
data class ExerciseSessionData(
    val sessionExerciseId: Long,
    val exerciseId: String,
    val exerciseName: String,
    val plannedSets: Int,
    val completedSets: Int,
    val sets: List<SetRecord>,
    val restSeconds: Int,
)

/**
 * Resultado do calculo de progresso de um exercicio dentro de uma sessao.
 */
data class ExerciseProgress(
    val currentSet: Int,
    val plannedSets: Int,
    val completedSets: Int,
    val isCompleted: Boolean,
    val nextSetNumber: Int,
)

/**
 * Calcula o progresso de um exercicio com base nas series ja registradas.
 * Funcao pura: sem dependencia do Android, testavel em JVM puro.
 */
fun calculateProgress(
    plannedSets: Int,
    completedSets: Int,
    sets: List<SetRecord>,
): ExerciseProgress {
    val planned = plannedSets.coerceAtLeast(1)
    val completed = completedSets.coerceIn(0, planned)
    val currentSet = (sets.size + 1).coerceAtLeast(1).coerceAtMost(planned)
    val isCompleted = completed >= planned
    val nextSetNumber = (sets.maxOfOrNull { it.setNumber } ?: 0) + 1
    return ExerciseProgress(
        currentSet = currentSet,
        plannedSets = planned,
        completedSets = completed,
        isCompleted = isCompleted,
        nextSetNumber = nextSetNumber,
    )
}

/**
 * Texto de progresso exibido na UI, ex: "1/4 series".
 */
fun progressText(progress: ExerciseProgress): String {
    return "${progress.currentSet}/${progress.plannedSets} series"
}

/**
 * Verifica se todas as series planejadas de um exercicio foram concluidas.
 */
fun areAllSetsCompleted(progress: ExerciseProgress): Boolean {
    return progress.isCompleted
}

/**
 * Retorna o primeiro exercicio da lista que ainda nao teve todas as series concluidas,
 * ou null se todos estiverem completos. Usado para navegar de um exercicio para o seguinte
 * somente apos encerrar as series do atual (fluxo nao-circuito).
 */
fun nextIncompleteExercise(
    exercises: List<ExerciseSessionData>,
): ExerciseSessionData? {
    return exercises.firstOrNull { exercise ->
        val progress = calculateProgress(
            plannedSets = exercise.plannedSets,
            completedSets = exercise.completedSets,
            sets = exercise.sets,
        )
        !progress.isCompleted
    }
}
