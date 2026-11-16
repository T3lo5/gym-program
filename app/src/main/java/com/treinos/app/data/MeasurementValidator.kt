package com.treinos.app.data

/**
 * Resultado puro da validação de uma medida corporal (H4-8).
 * Não depende de Android, ViewModel nem corrotinas.
 */
data class MeasurementValidationResult(val valid: Boolean, val message: String?)

/**
 * H4-8: validação pura do formulário de medidas — faixas de peso, gordura
 * corporal e circunferências, "ao menos um valor" e data (passado ou hoje,
 * com tolerância de relógio). `NaN`/`Infinity` são sempre inválidos.
 *
 * As mensagens seguem a convenção dos validadores puros do app
 * ([com.treinos.app.ui.ProfileValidator]): texto em português, resolvido fora
 * da composição. Os erros inline por campo da tela usam os helpers
 * `isValid*`/`isDateValid` com os recursos localizados.
 */
object MeasurementValidator {

    const val MIN_WEIGHT_KG = 30.0
    const val MAX_WEIGHT_KG = 300.0
    const val MIN_BODY_FAT = 0.0
    const val MAX_BODY_FAT = 100.0
    const val MIN_CIRCUMFERENCE_CM = 0.0
    const val MAX_CIRCUMFERENCE_CM = 300.0

    /** Tolerância de relógio (regra 3): futuro até 5 min ainda é aceito. */
    const val CLOCK_TOLERANCE_MILLIS = 5L * 60 * 1000

    /**
     * Convenção de parse numérico do app (vírgula decimal aceita, como no
     * perfil): texto em branco ou inválido vira `null` — campo opcional.
     */
    fun parse(text: String?): Double? {
        if (text.isNullOrBlank()) return null
        return text.trim().replace(",", ".").toDoubleOrNull()
    }

    /** `null` é campo opcional (válido); valor presente precisa ser finito e na faixa. */
    fun isValidWeight(kg: Double?): Boolean =
        kg == null || (kg.isFinite() && kg >= MIN_WEIGHT_KG && kg <= MAX_WEIGHT_KG)

    fun isValidBodyFat(percent: Double?): Boolean =
        percent == null || (percent.isFinite() && percent >= MIN_BODY_FAT && percent <= MAX_BODY_FAT)

    fun isValidCircumference(cm: Double?): Boolean =
        cm == null || (cm.isFinite() && cm >= MIN_CIRCUMFERENCE_CM && cm <= MAX_CIRCUMFERENCE_CM)

    /** `measuredAt > 0` e no máximo `now + 5 min` (regra 3). */
    fun isDateValid(measuredAt: Long, nowMillis: Long): Boolean =
        measuredAt > 0 && measuredAt <= nowMillis + CLOCK_TOLERANCE_MILLIS

    fun validate(
        measuredAt: Long,
        weightKg: Double?,
        bodyFatPercent: Double?,
        waistCm: Double?,
        chestCm: Double?,
        armCm: Double?,
        thighCm: Double?,
        hipCm: Double?,
        nowMillis: Long,
    ): MeasurementValidationResult {
        if (measuredAt <= 0) {
            return MeasurementValidationResult(false, "Data inválida.")
        }
        if (measuredAt > nowMillis + CLOCK_TOLERANCE_MILLIS) {
            return MeasurementValidationResult(false, "Data no futuro.")
        }
        val values = listOf(weightKg, bodyFatPercent, waistCm, chestCm, armCm, thighCm, hipCm)
        if (values.all { it == null }) {
            // Regra 2: não se cria registro vazio.
            return MeasurementValidationResult(false, "Informe ao menos um valor.")
        }
        if (!isValidWeight(weightKg)) {
            return MeasurementValidationResult(false, "Peso fora da faixa permitida (30–300 kg).")
        }
        if (!isValidBodyFat(bodyFatPercent)) {
            return MeasurementValidationResult(false, "Gordura corporal fora da faixa permitida (0–100%).")
        }
        val circumferences = listOf(waistCm, chestCm, armCm, thighCm, hipCm)
        if (circumferences.any { !isValidCircumference(it) }) {
            return MeasurementValidationResult(false, "Medida fora da faixa permitida (0–300 cm).")
        }
        return MeasurementValidationResult(true, null)
    }
}
