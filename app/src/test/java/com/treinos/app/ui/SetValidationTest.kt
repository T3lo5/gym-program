package com.treinos.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regras puras do diálogo de série (H3-27): valor preenchido e inválido
 * bloqueia o salvamento — nada vira `null` em silêncio.
 */
class SetValidationTest {

    private fun valido(reps: String = "10", weight: String = "60", rpe: String = "") =
        validateSetInput(repsText = reps, weightText = weight, rpeText = rpe)

    @Test
    fun carga_invalida_rejeitada() {
        val result = valido(weight = "6.0.5")

        val errors = (result as SetInputValidation.Invalid).errors
        assertEquals("Informe a carga como número, usando vírgula ou ponto.", errors.weight)
        assertNull(errors.reps)
        assertNull(errors.rpe)
    }

    @Test
    fun carga_virgula_aceita() {
        val result = valido(weight = "62,5")

        val input = (result as SetInputValidation.Valid).input
        assertEquals(62.5, input.weightKg!!, 0.0001)
    }

    @Test
    fun reps_fora_de_faixa() {
        val zero = valido(reps = "0")
        val acima = valido(reps = "101")

        assertTrue(zero is SetInputValidation.Invalid)
        assertTrue(acima is SetInputValidation.Invalid)
        assertEquals(
            "As repetições devem estar entre 1 e 100.",
            (zero as SetInputValidation.Invalid).errors.reps,
        )
        // Limite superior aceito.
        assertTrue(valido(reps = "100") is SetInputValidation.Valid)
    }

    @Test
    fun rpe_vazio_null() {
        val result = valido(rpe = "")

        val input = (result as SetInputValidation.Valid).input
        assertNull(input.rpe)
    }

    @Test
    fun rpe_fora_de_faixa() {
        val acima = valido(rpe = "12")
        val abaixo = valido(rpe = "0,5")

        assertEquals("RPE deve ser 1–10.", (acima as SetInputValidation.Invalid).errors.rpe)
        assertEquals("RPE deve ser 1–10.", (abaixo as SetInputValidation.Invalid).errors.rpe)
        // Uma casa decimal no limite continua válida.
        assertTrue(valido(rpe = "7,5") is SetInputValidation.Valid)
        assertTrue(valido(rpe = "10") is SetInputValidation.Valid)
    }

    @Test
    fun peso_acima_de_500() {
        val result = valido(weight = "500,5")

        val errors = (result as SetInputValidation.Invalid).errors
        assertEquals("A carga deve ser maior que 0 e até 500 kg.", errors.weight)
        // Teto exato aceito.
        assertTrue(valido(weight = "500") is SetInputValidation.Valid)
    }

    @Test
    fun carga_zero_rejeitada() {
        val result = valido(weight = "0")

        assertTrue(result is SetInputValidation.Invalid)
        assertEquals(
            "A carga deve ser maior que 0 e até 500 kg.",
            (result as SetInputValidation.Invalid).errors.weight,
        )
    }

    @Test
    fun rpe_com_duas_casas_rejeitado() {
        val result = valido(rpe = "7,55")

        assertTrue(result is SetInputValidation.Invalid)
        assertEquals("RPE aceita até uma casa decimal.", (result as SetInputValidation.Invalid).errors.rpe)
    }

    @Test
    fun campos_vazios_sao_opcionais_e_validos() {
        val result = valido(reps = "", weight = "", rpe = "")

        val input = (result as SetInputValidation.Valid).input
        assertNull(input.reps)
        assertNull(input.weightKg)
        assertNull(input.rpe)
    }
}
