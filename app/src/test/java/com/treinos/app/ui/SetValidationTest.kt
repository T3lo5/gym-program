package com.treinos.app.ui

import com.treinos.app.data.local.SET_TYPE_AMRAP
import com.treinos.app.data.local.SET_TYPE_NORMAL
import com.treinos.app.data.local.SET_TYPE_WARMUP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regras puras do diálogo de série (H3-27): valor preenchido e inválido
 * bloqueia o salvamento — nada vira `null` em silêncio. H4-4: RIR em 0..10
 * (vazio = `null`) e tipo desconhecido normaliza para `normal`.
 */
class SetValidationTest {

    private fun valido(
        reps: String = "10",
        weight: String = "60",
        rpe: String = "",
        rir: String = "",
        setType: String = SET_TYPE_NORMAL,
    ) = validateSetInput(
        repsText = reps,
        weightText = weight,
        rpeText = rpe,
        rirText = rir,
        setType = setType,
    )

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
        assertEquals("A carga deve ser maior que 0 e até 500,0 kg.", errors.weight)
        // Teto exato aceito.
        assertTrue(valido(weight = "500") is SetInputValidation.Valid)
    }

    @Test
    fun carga_zero_rejeitada() {
        val result = valido(weight = "0")

        assertTrue(result is SetInputValidation.Invalid)
        assertEquals(
            "A carga deve ser maior que 0 e até 500,0 kg.",
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

    // ---------- H4-4: RIR ----------

    @Test
    fun rir_vazio_e_null() {
        // Vazio é "não informado", nunca 0.
        val input = (valido(rir = "") as SetInputValidation.Valid).input
        assertNull(input.rir)
    }

    @Test
    fun rir_0_a_10_valido() {
        val zero = (valido(rir = "0") as SetInputValidation.Valid).input
        val dez = (valido(rir = "10") as SetInputValidation.Valid).input
        val dois = (valido(rir = "2") as SetInputValidation.Valid).input

        assertEquals(0, zero.rir)
        assertEquals(10, dez.rir)
        assertEquals(2, dois.rir)
    }

    @Test
    fun rir_fora_da_faixa_bloqueia() {
        val acima = valido(rir = "11")
        val abaixo = valido(rir = "-1")

        assertEquals(
            "RIR deve estar entre 0 e 10.",
            (acima as SetInputValidation.Invalid).errors.rir,
        )
        assertEquals(
            "RIR deve estar entre 0 e 10.",
            (abaixo as SetInputValidation.Invalid).errors.rir,
        )
    }

    @Test
    fun rir_nao_inteiro_bloqueia() {
        val quebrado = valido(rir = "7,5")
        val texto = valido(rir = "muito")

        assertEquals(
            "Informe o RIR como número inteiro.",
            (quebrado as SetInputValidation.Invalid).errors.rir,
        )
        assertEquals(
            "Informe o RIR como número inteiro.",
            (texto as SetInputValidation.Invalid).errors.rir,
        )
    }

    // ---------- H4-4: tipo da série ----------

    @Test
    fun set_type_desconhecido_vira_normal() {
        val desconhecido = (valido(setType = "gigante") as SetInputValidation.Valid).input
        val conhecido = (valido(setType = SET_TYPE_WARMUP) as SetInputValidation.Valid).input
        val amrap = (valido(setType = SET_TYPE_AMRAP) as SetInputValidation.Valid).input

        assertEquals(SET_TYPE_NORMAL, desconhecido.setType)
        assertEquals(SET_TYPE_WARMUP, conhecido.setType)
        assertEquals(SET_TYPE_AMRAP, amrap.setType)
    }
}
