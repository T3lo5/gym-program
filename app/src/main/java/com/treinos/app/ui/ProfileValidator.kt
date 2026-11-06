package com.treinos.app.ui

private const val MIN_WEIGHT_KG = 30.0
private const val MAX_WEIGHT_KG = 300.0
private const val MIN_HEIGHT_CM = 120
private const val MAX_HEIGHT_CM = 250

/**
 * Resultado puro de validação dos campos de perfil (peso/altura).
 * Não depende de Android, ViewModel nem corrotinas.
 */
object ProfileValidator {

    /** Valores do contrato `POST /v1/generate-workout` (H3-7). */
    val SEX_VALUES = setOf("male", "female", "other", "undisclosed")

    private val SEX_LABELS = mapOf(
        "feminino" to "female",
        "masculino" to "male",
        "outro / prefiro escrever" to "other",
        "outro" to "other",
        "prefiro não informar" to "undisclosed",
        "female" to "female",
        "male" to "male",
        "other" to "other",
        "undisclosed" to "undisclosed",
    )

    data class Result(
        val valid: Boolean,
        val error: String?,
        val bodyWeightKg: Double?,
        val heightCm: Int?,
        val sex: String?,
    )

    fun validate(weightText: String?, heightText: String?, sexText: String? = null): Result {
        val weight = parseWeight(weightText)
        val height = parseHeight(heightText)
        val sex = normalizeSex(sexText)

        if (weightText?.isNotBlank() == true && weight == null) {
            return Result(false, "Peso inválido.", null, null, sex)
        }
        if (weight != null && (weight.isNaN() || weight.isInfinite() || weight < MIN_WEIGHT_KG || weight > MAX_WEIGHT_KG)) {
            return Result(false, "Peso fora da faixa permitida (30–300 kg).", null, null, sex)
        }

        if (heightText?.isNotBlank() == true && height == null) {
            return Result(false, "Altura inválida.", null, null, sex)
        }
        if (height != null && (height < MIN_HEIGHT_CM || height > MAX_HEIGHT_CM)) {
            return Result(false, "Altura fora da faixa permitida (120–250 cm).", null, null, sex)
        }

        return Result(true, null, weight, height, sex)
    }

    /** Rótulo da UI (ou valor já normalizado) → valor de contrato; `null` quando vazio ou não reconhecido. */
    fun normalizeSex(text: String?): String? {
        val key = text?.trim()?.lowercase()
        if (key.isNullOrEmpty()) return null
        return SEX_LABELS[key]
    }

    private fun parseWeight(text: String?): Double? {
        if (text.isNullOrBlank()) return null
        val normalized = text.trim().replace(",", ".")
        return normalized.toDoubleOrNull()
    }

    private fun parseHeight(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val normalized = text.trim().replace(",", ".")
        return normalized.toDoubleOrNull()?.toInt()
    }
}
