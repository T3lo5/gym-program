package com.treinos.app.data.backup

data class BackupValidationResult(
    val valid: Boolean,
    val message: String?,
    val preview: BackupPreview?,
)

object BackupValidator {

    private const val EXPECTED_SCHEMA_VERSION = 1
    private const val EXPECTED_PACKAGE_NAME = "com.treinos.app"
    private const val MAX_FILE_BYTES = 25L * 1024 * 1024

    fun validateSize(fileSizeBytes: Long): BackupValidationResult =
        if (fileSizeBytes > MAX_FILE_BYTES) {
            BackupValidationResult(
                valid = false,
                message = "Arquivo muito grande para importar (limite de 25 MB).",
                preview = null,
            )
        } else {
            BackupValidationResult(valid = true, message = null, preview = null)
        }

    fun validate(file: BackupFile): BackupValidationResult {
        if (file.schemaVersion != EXPECTED_SCHEMA_VERSION) {
            return BackupValidationResult(
                valid = false,
                message = "Backup de versão não suportada (encontrada ${file.schemaVersion}; esperada $EXPECTED_SCHEMA_VERSION).",
                preview = null,
            )
        }

        if (file.app.packageName != EXPECTED_PACKAGE_NAME) {
            return BackupValidationResult(
                valid = false,
                message = "Arquivo de backup não pertence a este app (${file.app.packageName}).",
                preview = null,
            )
        }

        val planIds = mutableSetOf<Long>()
        for (plan in file.plans) {
            if (!planIds.add(plan.id)) {
                return BackupValidationResult(
                    valid = false,
                    message = "IDs de plano duplicados no backup.",
                    preview = null,
                )
            }
            if (plan.daysPerWeek < 0 || plan.daysPerWeek > 7) {
                return BackupValidationResult(
                    valid = false,
                    message = "daysPerWeek inválido no plano '${plan.name}'.",
                    preview = null,
                )
            }

                val dayIds = mutableSetOf<Long>()
            for (day in plan.days) {
                if (!dayIds.add(day.id)) {
                    return BackupValidationResult(
                        valid = false,
                        message = "IDs de dia duplicados no plano '${plan.name}'.",
                        preview = null,
                    )
                }
                if (day.planId != plan.id) {
                    return BackupValidationResult(
                        valid = false,
                        message = "Dia '${day.name}' não pertence ao plano '${plan.name}' (planId órfão).",
                        preview = null,
                    )
                }

                val exerciseIds = mutableSetOf<Long>()
                for (exercise in day.exercises) {
                    if (!exerciseIds.add(exercise.id)) {
                        return BackupValidationResult(
                            valid = false,
                            message = "IDs de exercício duplicados no dia '${day.name}'.",
                            preview = null,
                        )
                    }
                    if (exercise.dayId != day.id) {
                        return BackupValidationResult(
                            valid = false,
                            message = "Exercício '${exercise.exerciseName}' não pertence ao dia '${day.name}' (dayId órfão).",
                            preview = null,
                        )
                    }
                    if (exercise.sets < 0) {
                        return BackupValidationResult(
                            valid = false,
                            message = "sets inválido no exercício '${exercise.exerciseName}'.",
                            preview = null,
                        )
                    }
                    if (exercise.targetRpe != null && (exercise.targetRpe < 1.0 || exercise.targetRpe > 10.0)) {
                        return BackupValidationResult(
                            valid = false,
                            message = "targetRpe fora da faixa no exercício '${exercise.exerciseName}'.",
                            preview = null,
                        )
                    }
                }
            }
        }

            val sessionIds = mutableSetOf<Long>()
        for (session in file.sessions) {
            if (!sessionIds.add(session.id)) {
                return BackupValidationResult(
                    valid = false,
                    message = "IDs de sessão duplicados no backup.",
                    preview = null,
                )
            }

            val sessionExerciseIds = mutableSetOf<Long>()
            for (sessionExercise in session.exercises) {
                if (!sessionExerciseIds.add(sessionExercise.id)) {
                    return BackupValidationResult(
                        valid = false,
                        message = "IDs de exercício de sessão duplicados na sessão ${session.id}.",
                        preview = null,
                    )
                }
                if (sessionExercise.sessionId != session.id) {
                    return BackupValidationResult(
                        valid = false,
                        message = "Exercício de sessão '${sessionExercise.exerciseName}' não pertence à sessão ${session.id} (sessionId órfão).",
                        preview = null,
                    )
                }
                if (sessionExercise.plannedSets < 0) {
                    return BackupValidationResult(
                        valid = false,
                        message = "plannedSets inválido no exercício '${sessionExercise.exerciseName}'.",
                        preview = null,
                    )
                }
                if (sessionExercise.completedSets < 0) {
                    return BackupValidationResult(
                        valid = false,
                        message = "completedSets inválido no exercício '${sessionExercise.exerciseName}'.",
                        preview = null,
                    )
                }
                if (sessionExercise.restSeconds < 0) {
                    return BackupValidationResult(
                        valid = false,
                        message = "restSeconds inválido no exercício '${sessionExercise.exerciseName}'.",
                        preview = null,
                    )
                }

                val setLogIds = mutableSetOf<Long>()
                for (setLog in sessionExercise.sets) {
                    if (!setLogIds.add(setLog.id)) {
                        return BackupValidationResult(
                            valid = false,
                            message = "IDs de série duplicados no exercício '${sessionExercise.exerciseName}'.",
                            preview = null,
                        )
                    }
                    if (setLog.exerciseLogId != sessionExercise.id) {
                        return BackupValidationResult(
                            valid = false,
                            message = "Série ${setLog.setNumber} não pertence ao exercício '${sessionExercise.exerciseName}' (exerciseLogId órfão).",
                            preview = null,
                        )
                    }
                    if (setLog.setNumber < 1) {
                        return BackupValidationResult(
                            valid = false,
                            message = "setNumber da série ${setLog.setNumber} inválido no exercício '${sessionExercise.exerciseName}'.",
                            preview = null,
                        )
                    }
                    if (setLog.reps != null && setLog.reps < 0) {
                        return BackupValidationResult(
                            valid = false,
                            message = "reps da série ${setLog.setNumber} inválido no exercício '${sessionExercise.exerciseName}'.",
                            preview = null,
                        )
                    }
                    if (setLog.weightKg != null && setLog.weightKg < 0) {
                        return BackupValidationResult(
                            valid = false,
                            message = "weightKg da série ${setLog.setNumber} inválido no exercício '${sessionExercise.exerciseName}'.",
                            preview = null,
                        )
                    }
                    if (setLog.rpe != null && (setLog.rpe < 1.0 || setLog.rpe > 10.0)) {
                        return BackupValidationResult(
                            valid = false,
                            message = "rpe da série ${setLog.setNumber} fora da faixa no exercício '${sessionExercise.exerciseName}'.",
                            preview = null,
                        )
                    }
                }
            }
        }

        val planCount = file.plans.size
        val dayCount = file.plans.sumOf { it.days.size }
        val planExerciseCount = file.plans.sumOf { it.days.sumOf { it.exercises.size } }
        val sessionCount = file.sessions.size
        val sessionExerciseCount = file.sessions.sumOf { it.exercises.size }
        val setLogCount = file.sessions.sumOf { it.exercises.sumOf { it.sets.size } }

        return BackupValidationResult(
            valid = true,
            message = null,
            preview = BackupPreview(
                plans = planCount,
                days = dayCount,
                planExercises = planExerciseCount,
                sessions = sessionCount,
                sessionExercises = sessionExerciseCount,
                setLogs = setLogCount,
            ),
        )
    }
}
