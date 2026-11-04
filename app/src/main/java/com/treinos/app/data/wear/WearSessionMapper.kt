package com.treinos.app.data.wear

import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.ui.ExerciseSessionData
import com.treinos.app.ui.SetRecord
import com.treinos.app.ui.calculateProgress
import com.treinos.protocol.WearWorkoutState

object WearSessionMapper {

    fun toWearState(
        session: SessionWithExercises,
        exerciseIndex: Int,
        restRemainingSeconds: Int?,
    ): WearWorkoutState {
        val exercicios = session.exercises
        val idx = exerciseIndex.coerceIn(0, (exercicios.size - 1).coerceAtLeast(0))
        val atual = exercicios.getOrNull(idx)
        val ex = atual?.exercise

        val proximo = if (idx + 1 < exercicios.size) {
            exercicios.subList(idx + 1, exercicios.size).firstOrNull { ses ->
                val progress = calculateProgress(
                    plannedSets = ses.exercise.plannedSets,
                    completedSets = ses.exercise.completedSets,
                    sets = ses.sets.map { SetRecord(it.setNumber, it.reps, it.weightKg) },
                )
                !progress.isCompleted
            }
        } else {
            null
        }

        val ultimoSet = atual?.sets?.lastOrNull()
        val plannedSets = ex?.plannedSets ?: 0
        val setCount = atual?.sets?.size ?: 0
        var setNumber = (setCount + 1).coerceAtLeast(1)
        if (plannedSets > 0) setNumber = setNumber.coerceAtMost(plannedSets)

        return WearWorkoutState(
            sessionId = session.session.id,
            dayName = session.session.dayName,
            exerciseIndex = idx,
            exerciseCount = exercicios.size,
            exerciseName = ex?.let { e ->
                val nome = e.exerciseName
                if (nome.isNotEmpty()) nome else e.exerciseId
            } ?: "",
            setNumber = setNumber,
            plannedSets = plannedSets,
            targetReps = "",
            lastWeightKg = ultimoSet?.weightKg,
            lastReps = ultimoSet?.reps,
            restSeconds = ex?.restSeconds ?: 90,
            restRemainingSeconds = restRemainingSeconds,
            nextExerciseName = proximo?.let { e ->
                val nome = e.exercise.exerciseName
                if (nome.isNotEmpty()) nome else e.exercise.exerciseId
            },
            status = session.session.status,
            updatedAt = System.currentTimeMillis(),
        )
    }
}
