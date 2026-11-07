package com.treinos.app.ui

/**
 * Regras puras de validação do formulário de série (H3-27).
 *
 * Fim do `null` silencioso: valor preenchido e inválido **bloqueia** o
 * salvamento com mensagem no diálogo — nada é gravado com carga/reps/RPE
 * `null` por falha de parse. Campo vazio continua sendo escolha explícita
 * (`null`) para carga, reps e RPE.
 */
data class SetInput(
    val reps: Int? = null,
    val weightKg: Double? = null,
    val rpe: Double? = null,
)

/** Erro por campo; campo sem erro fica `null`. */
data class SetInputErrors(
    val reps: String? = null,
    val weight: String? = null,
    val rpe: String? = null,
) {
    val hasErrors: Boolean
        get() = reps != null || weight != null || rpe != null
}

sealed interface SetInputValidation {
    data class Valid(val input: SetInput) : SetInputValidation
    data class Invalid(val errors: SetInputErrors) : SetInputValidation
}

/** Teto da carga em kg; acima disso é erro de digitação (H3-27). */
const val MAX_SET_WEIGHT_KG = 500.0

/** Reps aceitas: inteiro em 1..100. */
const val MIN_SET_REPS = 1
const val MAX_SET_REPS = 100

/** RPE aceito: 1..10, com no máximo uma casa decimal. */
const val MIN_SET_RPE = 1.0
const val MAX_SET_RPE = 10.0

/**
 * Valida o texto bruto dos três campos do diálogo de série.
 *
 * - Carga: não vazia deve parsear (vírgula ou ponto) e satisfazer
 *   `0 < peso <= 500`.
 * - Reps: não vazia deve ser inteiro em `1..100`.
 * - RPE: vazio = `null`; preenchido deve estar em `1..10` com no máximo
 *   uma casa decimal.
 */
fun validateSetInput(repsText: String, weightText: String, rpeText: String): SetInputValidation {
    val errors = SetInputErrors(
        reps = validateRepsText(repsText),
        weight = validateWeightText(weightText),
        rpe = validateRpeText(rpeText),
    )
    return if (errors.hasErrors) {
        SetInputValidation.Invalid(errors)
    } else {
        SetInputValidation.Valid(
            SetInput(
                reps = repsText.trim().toIntOrNull(),
                weightKg = parseDecimalInput(weightText),
                rpe = parseDecimalInput(rpeText),
            )
        )
    }
}

/** Converte vírgula em ponto e parseia; `null` quando não é número. */
fun parseDecimalInput(value: String): Double? =
    value.trim().replace(',', '.').toDoubleOrNull()

private fun validateRepsText(repsText: String): String? {
    if (repsText.isBlank()) return null
    val reps = repsText.trim().toIntOrNull() ?: return "Informe as repetições como número inteiro."
    return if (reps in MIN_SET_REPS..MAX_SET_REPS) {
        null
    } else {
        "As repetições devem estar entre $MIN_SET_REPS e $MAX_SET_REPS."
    }
}

private fun validateWeightText(weightText: String): String? {
    if (weightText.isBlank()) return null
    val weight = parseDecimalInput(weightText)
        ?: return "Informe a carga como número, usando vírgula ou ponto."
    return if (weight > 0.0 && weight <= MAX_SET_WEIGHT_KG) {
        null
    } else {
        "A carga deve ser maior que 0 e até ${formatKg(MAX_SET_WEIGHT_KG)} kg."
    }
}

private fun validateRpeText(rpeText: String): String? {
    if (rpeText.isBlank()) return null
    val rpe = parseDecimalInput(rpeText)
        ?: return "Informe o RPE como número, usando vírgula ou ponto."
    return when {
        rpe < MIN_SET_RPE || rpe > MAX_SET_RPE -> "RPE deve ser 1–10."
        hasMoreThanOneDecimal(rpeText) -> "RPE aceita até uma casa decimal."
        else -> null
    }
}

/** Verdadeiro quando o texto tem mais de um dígito depois do separador. */
private fun hasMoreThanOneDecimal(text: String): Boolean =
    text.trim().replace(',', '.').substringAfter('.', "").length > 1

private fun formatKg(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format("%.1f", value)
