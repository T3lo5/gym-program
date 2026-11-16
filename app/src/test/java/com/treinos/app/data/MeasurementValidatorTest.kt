package com.treinos.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H4-8: validação pura do formulário de medidas — faixas (regra 1), "ao menos
 * um valor" (regra 2) e data com tolerância de relógio (regra 3).
 */
class MeasurementValidatorTest {

    private val now = 1_760_000_000_000L

    private fun validate(
        measuredAt: Long = now,
        weightKg: Double? = 80.0,
        bodyFatPercent: Double? = null,
        waistCm: Double? = null,
        chestCm: Double? = null,
        armCm: Double? = null,
        thighCm: Double? = null,
        hipCm: Double? = null,
    ): MeasurementValidationResult = MeasurementValidator.validate(
        measuredAt = measuredAt,
        weightKg = weightKg,
        bodyFatPercent = bodyFatPercent,
        waistCm = waistCm,
        chestCm = chestCm,
        armCm = armCm,
        thighCm = thighCm,
        hipCm = hipCm,
        nowMillis = now,
    )

    @Test
    fun aceita_peso_minimo_e_maximo() {
        assertTrue(validate(weightKg = MeasurementValidator.MIN_WEIGHT_KG).valid)
        assertTrue(validate(weightKg = MeasurementValidator.MAX_WEIGHT_KG).valid)
    }

    @Test
    fun rejeita_peso_fora_da_faixa() {
        assertFalse(validate(weightKg = MeasurementValidator.MIN_WEIGHT_KG - 0.1).valid)
        assertFalse(validate(weightKg = 500.0).valid)
        assertFalse(validate(weightKg = MeasurementValidator.MAX_WEIGHT_KG + 0.1).valid)
    }

    @Test
    fun rejeita_nan_e_infinito() {
        assertFalse(validate(weightKg = Double.NaN).valid)
        assertFalse(validate(weightKg = Double.POSITIVE_INFINITY).valid)
        assertFalse(validate(weightKg = Double.NEGATIVE_INFINITY).valid)
        assertFalse(validate(waistCm = Double.NaN).valid)
        assertFalse(validate(bodyFatPercent = Double.POSITIVE_INFINITY).valid)
    }

    @Test
    fun aceita_gordura_0_e_100() {
        assertTrue(validate(weightKg = null, bodyFatPercent = MeasurementValidator.MIN_BODY_FAT).valid)
        assertTrue(validate(weightKg = null, bodyFatPercent = MeasurementValidator.MAX_BODY_FAT).valid)
    }

    @Test
    fun rejeita_gordura_acima_de_100() {
        assertFalse(validate(bodyFatPercent = 100.1).valid)
        assertFalse(validate(bodyFatPercent = -0.1).valid)
    }

    @Test
    fun aceita_circunferencia_0_e_300() {
        assertTrue(validate(weightKg = null, waistCm = MeasurementValidator.MIN_CIRCUMFERENCE_CM).valid)
        assertTrue(
            validate(
                weightKg = null,
                waistCm = MeasurementValidator.MAX_CIRCUMFERENCE_CM,
                chestCm = MeasurementValidator.MAX_CIRCUMFERENCE_CM,
                armCm = MeasurementValidator.MAX_CIRCUMFERENCE_CM,
                thighCm = MeasurementValidator.MAX_CIRCUMFERENCE_CM,
                hipCm = MeasurementValidator.MAX_CIRCUMFERENCE_CM,
            ).valid,
        )
    }

    @Test
    fun rejeita_circunferencia_acima_de_300() {
        assertFalse(validate(waistCm = 300.1).valid)
        assertFalse(validate(hipCm = MeasurementValidator.MAX_CIRCUMFERENCE_CM + 0.1).valid)
        assertFalse(validate(armCm = -0.1).valid)
    }

    @Test
    fun rejeita_medida_sem_nenhum_valor() {
        val result = validate(
            weightKg = null,
            bodyFatPercent = null,
            waistCm = null,
            chestCm = null,
            armCm = null,
            thighCm = null,
            hipCm = null,
        )
        assertFalse(result.valid)
        assertEquals("Informe ao menos um valor.", result.message)
    }

    @Test
    fun rejeita_data_zero_ou_negativa() {
        assertFalse(validate(measuredAt = 0).valid)
        assertFalse(validate(measuredAt = -1).valid)
    }

    @Test
    fun rejeita_data_no_futuro() {
        // Regra 3: futuro além da tolerância de 5 min é rejeitado.
        assertFalse(validate(measuredAt = now + MeasurementValidator.CLOCK_TOLERANCE_MILLIS + 1).valid)
        assertFalse(validate(measuredAt = now + 60 * 60 * 1000).valid)
    }

    @Test
    fun aceita_data_de_hoje() {
        assertTrue(validate(measuredAt = now).valid)
        // Dentro da tolerância de relógio ainda é aceito.
        assertTrue(validate(measuredAt = now + MeasurementValidator.CLOCK_TOLERANCE_MILLIS).valid)
    }
}
