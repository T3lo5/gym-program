package com.treinos.app.ui

/**
 * Validação pura do modo complementar (H3-16), espelhando as regras do
 * `GenerateRequest` no backend (`back/app/main.py`).
 *
 * Não depende de Android, ViewModel nem corrotinas — roda em teste JVM.
 * O modo complementar é identificado por `focusMuscles` presente: plano só de
 * força, 1–6 dias e 15–120 min. Sem foco, valem as regras do plano principal
 * (2–6 dias, 30–120 min).
 */
object ComplementaryValidator {

    /** Grupos aceitos no contrato (`MUSCLE_ALIASES` do backend). */
    val FOCUS_GROUPS = setOf(
        "back",
        "legs",
        "arms",
        "abs",
        "chest",
        "shoulders",
        "biceps",
        "triceps",
        "glutes",
        "calves",
        "quadriceps",
        "hamstrings",
        "abdominals",
    )

    const val MAX_FOCUS_GROUPS = 4
    const val MIN_DAYS_WITH_FOCUS = 1
    const val MIN_DAYS_WITHOUT_FOCUS = 2
    const val MAX_DAYS = 6
    const val MIN_DURATION_WITH_FOCUS = 15
    const val MIN_DURATION_WITHOUT_FOCUS = 30
    const val MAX_DURATION = 120

    data class Result(
        val valid: Boolean,
        val error: String?,
    )

    /**
     * Valida a combinação foco + dias + duração + modalidades.
     *
     * `focusMuscles` null significa plano principal (campo omitido no request);
     * lista vazia é entrada inválida, como no backend.
     */
    fun validate(
        focusMuscles: List<String>?,
        daysPerWeek: Int,
        sessionDurationMinutes: Int,
        modalityTypes: List<String> = emptyList(),
    ): Result {
        if (focusMuscles == null) {
            if (daysPerWeek < MIN_DAYS_WITHOUT_FOCUS || daysPerWeek > MAX_DAYS) {
                return Result(false, "Dias por semana deve ser entre 2 e 6 (com foco exclusivo, 1 a 6).")
            }
            if (sessionDurationMinutes < MIN_DURATION_WITHOUT_FOCUS || sessionDurationMinutes > MAX_DURATION) {
                return Result(false, "Duração deve ser entre 30 e 120 min (com foco exclusivo, 15 a 120).")
            }
            return Result(true, null)
        }

        if (focusMuscles.isEmpty()) {
            return Result(false, "Selecione pelo menos um grupo de foco ou desative o modo complementar.")
        }
        if (focusMuscles.size > MAX_FOCUS_GROUPS) {
            return Result(false, "O foco exclusivo aceita no máximo $MAX_FOCUS_GROUPS grupos.")
        }
        val invalid = focusMuscles
            .filter { it.trim().lowercase() !in FOCUS_GROUPS }
        if (invalid.isNotEmpty()) {
            return Result(false, "Grupo de foco inválido: ${invalid.joinToString(", ")}.")
        }
        val nonStrength = modalityTypes.filter { it != "strength" }
        if (nonStrength.isNotEmpty()) {
            return Result(false, "Plano complementar é apenas de força; desative corrida e mobilidade.")
        }
        if (daysPerWeek < MIN_DAYS_WITH_FOCUS || daysPerWeek > MAX_DAYS) {
            return Result(false, "Dias por semana deve ser entre 1 e 6.")
        }
        if (sessionDurationMinutes < MIN_DURATION_WITH_FOCUS || sessionDurationMinutes > MAX_DURATION) {
            return Result(false, "Duração deve ser entre 15 e 120 min.")
        }
        return Result(true, null)
    }
}
