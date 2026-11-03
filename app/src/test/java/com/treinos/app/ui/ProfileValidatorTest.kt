package com.treinos.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileValidatorTest {

    @Test
    fun aceitaPesoEAlturaValidos() {
        val result = ProfileValidator.validate("82,5", "178")
        assertTrue(result.valid)
        assertNull(result.error)
        assertEquals(82.5, result.bodyWeightKg!!, 0.001)
        assertEquals(178, result.heightCm!!)
    }

    @Test
    fun rejeitaPesoForaDaFaixa() {
        val muitoBaixo = ProfileValidator.validate("20", "170")
        assertFalse(muitoBaixo.valid)
        assertEquals("Peso fora da faixa permitida (30–300 kg).", muitoBaixo.error)

        val muitoAlto = ProfileValidator.validate("400", "170")
        assertFalse(muitoAlto.valid)
        assertEquals("Peso fora da faixa permitida (30–300 kg).", muitoAlto.error)
    }

    @Test
    fun rejeitaAlturaForaDaFaixa() {
        val muitoBaixo = ProfileValidator.validate("70", "100")
        assertFalse(muitoBaixo.valid)
        assertEquals("Altura fora da faixa permitida (120–250 cm).", muitoBaixo.error)

        val muitoAlto = ProfileValidator.validate("70", "300")
        assertFalse(muitoAlto.valid)
        assertEquals("Altura fora da faixa permitida (120–250 cm).", muitoAlto.error)
    }

    @Test
    fun campoVazioViraNull() {
        val ambosVazios = ProfileValidator.validate("", "")
        assertTrue(ambosVazios.valid)
        assertNull(ambosVazios.bodyWeightKg)
        assertNull(ambosVazios.heightCm)

        val soPeso = ProfileValidator.validate("70", "")
        assertTrue(soPeso.valid)
        assertEquals(70.0, soPeso.bodyWeightKg!!, 0.001)
        assertNull(soPeso.heightCm)

        val soAltura = ProfileValidator.validate("", "170")
        assertTrue(soAltura.valid)
        assertNull(soAltura.bodyWeightKg)
        assertEquals(170, soAltura.heightCm!!)
    }

    @Test
    fun aceitaVirgulaDecimal() {
        val ponto = ProfileValidator.validate("82.5", "178")
        assertTrue(ponto.valid)
        assertEquals(82.5, ponto.bodyWeightKg!!, 0.001)

        val virgula = ProfileValidator.validate("82,5", "178")
        assertTrue(virgula.valid)
        assertEquals(82.5, virgula.bodyWeightKg!!, 0.001)
    }

    @Test
    fun perfilIncompletoEhValido() {
        val soPeso = ProfileValidator.validate("70", "")
        assertTrue(soPeso.valid)

        val soAltura = ProfileValidator.validate("", "170")
        assertTrue(soAltura.valid)
    }

    @Test
    fun pesoInvalidoNaoEhNumero() {
        val result = ProfileValidator.validate("abc", "170")
        assertFalse(result.valid)
        assertEquals("Peso inválido.", result.error)
    }

    @Test
    fun alturaInvalidaNaoEhNumero() {
        val result = ProfileValidator.validate("70", "abc")
        assertFalse(result.valid)
        assertEquals("Altura inválida.", result.error)
    }
}
