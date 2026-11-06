package com.treinos.app.data.backup

data class BackupValidationResult(
    val valid: Boolean,
    val message: String?,
    val preview: BackupPreview?,
)

object BackupValidator {

    private const val EXPECTED_PACKAGE_NAME = "com.treinos.app"

    /** Limite do arquivo de backup; usado antes de ler o conteúdo (H3-6). */
    const val MAX_FILE_BYTES = 25L * 1024 * 1024
    private const val MIN_WEIGHT_KG = 30.0
    private const val MAX_WEIGHT_KG = 300.0
    private const val MIN_HEIGHT_CM = 120
    private const val MAX_HEIGHT_CM = 250
    private val SUPPORTED_VERSIONS = setOf(1, 2, 3)
    private val MODALITIES = setOf("strength", "run", "swim", "mobility", "other")
    private val SOURCES = setOf("plan", "manual")
    private val METRIC_TYPES = setOf("reps_weight", "duration", "distance_duration")
    private val POOL_LENGTHS = setOf(25, 50)
    private val STROKES = setOf("crawl", "costas", "peito", "borboleta", "misto", "outro")
    private val SEX_VALUES = setOf("male", "female", "other", "undisclosed")
    private val PLAN_KINDS = setOf("main", "extra")

    fun validateSize(fileSizeBytes: Long): BackupValidationResult =
        if (fileSizeBytes > MAX_FILE_BYTES) {
            BackupValidationResult(
                valid = false,
                message = BackupSizeGuard.tooLargeMessage,
                preview = null,
            )
        } else {
            BackupValidationResult(valid = true, message = null, preview = null)
        }

    fun validate(file: BackupFile): BackupValidationResult {
        if (file.schemaVersion !in SUPPORTED_VERSIONS) {
            return BackupValidationResult(
                valid = false,
                message = "Backup de versão não suportada (encontrada ${file.schemaVersion}; esperada 1, 2 ou 3).",
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

        val profile = file.profile
        if (profile != null) {
            val weight = profile.bodyWeightKg
            if (weight != null &&
                (weight.isNaN() || weight.isInfinite() || weight < MIN_WEIGHT_KG || weight > MAX_WEIGHT_KG)
            ) {
                return BackupValidationResult(
                    valid = false,
                    message = "Peso do perfil fora da faixa permitida (30–300 kg).",
                    preview = null,
                )
            }
            val height = profile.heightCm
            if (height != null && (height < MIN_HEIGHT_CM || height > MAX_HEIGHT_CM)) {
                return BackupValidationResult(
                    valid = false,
                    message = "Altura do perfil fora da faixa permitida (120–250 cm).",
                    preview = null,
                )
            }
            val sex = profile.sex
            if (sex != null && sex !in SEX_VALUES) {
                return BackupValidationResult(
                    valid = false,
                    message = "Sexo do perfil com valor não reconhecido.",
                    preview = null,
                )
            }
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
            if (plan.daysPerWeek < 0 || plan.daysPerWeek > 21) {
                return BackupValidationResult(
                    valid = false,
                    message = "daysPerWeek inválido no plano '${plan.name}'.",
                    preview = null,
                )
            }
            if (plan.kind !in PLAN_KINDS) {
                return BackupValidationResult(
                    valid = false,
                    message = "kind inválido no plano '${plan.name}' (esperado main ou extra).",
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
                if (day.modality !in MODALITIES) {
                    return BackupValidationResult(
                        valid = false,
                        message = "modality inválida no dia '${day.name}'.",
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
                    if (exercise.metricType !in METRIC_TYPES) {
                        return BackupValidationResult(
                            valid = false,
                            message = "metricType inválido no exercício '${exercise.exerciseName}'.",
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
            if (session.modality !in MODALITIES) {
                return BackupValidationResult(
                    valid = false,
                    message = "modality inválida na sessão ${session.id}.",
                    preview = null,
                )
            }
            if (session.source !in SOURCES) {
                return BackupValidationResult(
                    valid = false,
                    message = "source inválido na sessão ${session.id}.",
                    preview = null,
                )
            }
            if (session.poolLengthMeters != null && session.poolLengthMeters !in POOL_LENGTHS) {
                return BackupValidationResult(
                    valid = false,
                    message = "poolLengthMeters inválido na sessão ${session.id}.",
                    preview = null,
                )
            }
            if (session.stroke != null && session.stroke !in STROKES) {
                return BackupValidationResult(
                    valid = false,
                    message = "stroke inválido na sessão ${session.id}.",
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
