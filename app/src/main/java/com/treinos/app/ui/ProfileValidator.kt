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

    data class Result(
        val valid: Boolean,
        val error: String?,
        val bodyWeightKg: Double?,
        val heightCm: Int?,
    )

    fun validate(weightText: String?, heightText: String?): Result {
        val weight = parseWeight(weightText)
        val height = parseHeight(heightText)

        if (weightText?.isNotBlank() == true && weight == null) {
            return Result(false, "Peso inválido.", null, null)
        }
        if (weight != null && (weight.isNaN() || weight.isInfinite() || weight < MIN_WEIGHT_KG || weight > MAX_WEIGHT_KG)) {
            return Result(false, "Peso fora da faixa permitida (30–300 kg).", null, null)
        }

        if (heightText?.isNotBlank() == true && height == null) {
            return Result(false, "Altura inválida.", null, null)
        }
        if (height != null && (height < MIN_HEIGHT_CM || height > MAX_HEIGHT_CM)) {
            return Result(false, "Altura fora da faixa permitida (120–250 cm).", null, null)
        }

        return Result(true, null, weight, height)
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
