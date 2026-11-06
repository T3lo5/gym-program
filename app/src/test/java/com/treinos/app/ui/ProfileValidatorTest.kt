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

    @Test
    fun normaliza_rotulos_de_sexo() {
        assertEquals("female", ProfileValidator.normalizeSex("Feminino"))
        assertEquals("male", ProfileValidator.normalizeSex("Masculino"))
        assertEquals("other", ProfileValidator.normalizeSex("Outro"))
        assertEquals("other", ProfileValidator.normalizeSex("Outro / prefiro escrever"))
        assertEquals("undisclosed", ProfileValidator.normalizeSex("Prefiro não informar"))
        // A UI guarda o valor de contrato no estado do campo.
        assertEquals("female", ProfileValidator.normalizeSex("female"))
        assertTrue(ProfileValidator.SEX_VALUES.containsAll(listOf("male", "female", "other", "undisclosed")))
    }

    @Test
    fun sexo_vazio_vira_null() {
        assertNull(ProfileValidator.normalizeSex(null))
        assertNull(ProfileValidator.normalizeSex(""))
        assertNull(ProfileValidator.normalizeSex("   "))

        val result = ProfileValidator.validate("70", "170", null)
        assertTrue(result.valid)
        assertNull(result.sex)
    }

    @Test
    fun rotulo_desconhecido_vira_null() {
        assertNull(ProfileValidator.normalizeSex("qualquer-coisa"))
        assertNull(ProfileValidator.normalizeSex("sexo"))
        // O recorte é só do rótulo, não de um prefixo de outro valor.
        assertNull(ProfileValidator.normalizeSex("femininoX"))
        // Nunca vira erro de peso/altura (regra 2).
        val result = ProfileValidator.validate("70", "170", "qualquer-coisa")
        assertTrue(result.valid)
        assertNull(result.error)
        assertNull(result.sex)
    }

    @Test
    fun sexo_nao_invalida_peso_ou_altura() {
        val invalido = ProfileValidator.validate("abc", "170", "female")
        assertFalse(invalido.valid)
        assertEquals("Peso inválido.", invalido.error)
        assertEquals("female", invalido.sex)

        val faixa = ProfileValidator.validate("400", "170", "male")
        assertFalse(faixa.valid)
        assertEquals("Peso fora da faixa permitida (30–300 kg).", faixa.error)

        // Sexo nunca produz erro dele mesmo, nem limpa os outros campos (regra 4).
        val valido = ProfileValidator.validate("82,5", "178", "undisclosed")
        assertTrue(valido.valid)
        assertEquals(82.5, valido.bodyWeightKg!!, 0.001)
        assertEquals(178, valido.heightCm)
        assertEquals("undisclosed", valido.sex)
    }

    @Test
    fun perfil_sem_sexo_continua_valido() {
        val semPerfil = ProfileValidator.validate("", "")
        assertTrue(semPerfil.valid)
        assertNull(semPerfil.bodyWeightKg)
        assertNull(semPerfil.heightCm)
        assertNull(semPerfil.sex)

        val soPesoEAltura = ProfileValidator.validate("82,5", "178")
        assertTrue(soPesoEAltura.valid)
        assertEquals(82.5, soPesoEAltura.bodyWeightKg!!, 0.001)
        assertEquals(178, soPesoEAltura.heightCm)
        assertNull(soPesoEAltura.sex)
    }
}
