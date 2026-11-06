package com.treinos.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regras do validador do modo complementar (H3-16), espelhando as validações
 * condicionais do `GenerateRequest` no backend.
 */
class ComplementaryValidatorTest {

    @Test
    fun foco_valido() {
        val result = ComplementaryValidator.validate(
            focusMuscles = listOf("abs"),
            daysPerWeek = 2,
            sessionDurationMinutes = 15,
            modalityTypes = listOf("strength"),
        )
        assertTrue(result.valid)
        assertNull(result.error)

        // Até 4 grupos, qualquer grupo do contrato, dias 1–6, duração 15–120.
        assertTrue(
            ComplementaryValidator.validate(
                listOf("abs", "glutes", "chest", "back"),
                6,
                120,
                listOf("strength"),
            ).valid
        )
        assertTrue(
            ComplementaryValidator.validate(listOf("quadriceps"), 1, 45, listOf("strength")).valid
        )
    }

    @Test
    fun foco_invalido() {
        val result = ComplementaryValidator.validate(
            focusMuscles = listOf("nao-existe"),
            daysPerWeek = 2,
            sessionDurationMinutes = 30,
            modalityTypes = listOf("strength"),
        )
        assertFalse(result.valid)
        assertTrue(result.error!!.contains("nao-existe"))

        // Mais de 4 grupos também é rejeitado.
        assertFalse(
            ComplementaryValidator.validate(
                listOf("abs", "chest", "back", "glutes", "shoulders"),
                2,
                30,
            ).valid
        )
    }

    @Test
    fun lista_vazia_rejeitada() {
        val result = ComplementaryValidator.validate(
            focusMuscles = emptyList(),
            daysPerWeek = 2,
            sessionDurationMinutes = 30,
            modalityTypes = listOf("strength"),
        )
        assertFalse(result.valid)
        // Sem foco o campo deve ser omitido (null), não enviado vazio.
        assertTrue(
            ComplementaryValidator.validate(null, 2, 30, listOf("strength", "run")).valid
        )
    }

    @Test
    fun duracao_15_aceita_so_com_foco() {
        assertTrue(
            ComplementaryValidator.validate(listOf("abs"), 2, 15, listOf("strength")).valid
        )
        val semFoco = ComplementaryValidator.validate(null, 4, 15)
        assertFalse(semFoco.valid)
        assertTrue(semFoco.error!!.contains("30"))
    }

    @Test
    fun dias_1_aceito_so_com_foco() {
        assertTrue(
            ComplementaryValidator.validate(listOf("abs"), 1, 30, listOf("strength")).valid
        )
        val semFoco = ComplementaryValidator.validate(null, 1, 60)
        assertFalse(semFoco.valid)
        assertTrue(semFoco.error!!.contains("2"))
    }

    @Test
    fun sem_foco_regras_atuais() {
        // Plano principal: dias 2–6, duração 30–120, modalidades livres.
        assertTrue(ComplementaryValidator.validate(null, 4, 60, listOf("strength", "run", "mobility")).valid)
        assertTrue(ComplementaryValidator.validate(null, 2, 30).valid)
        assertTrue(ComplementaryValidator.validate(null, 6, 120).valid)
        assertFalse(ComplementaryValidator.validate(null, 1, 60).valid)
        assertFalse(ComplementaryValidator.validate(null, 4, 20).valid)

        // Com foco, corrida/mobilidade são rejeitadas (decisão de produto).
        val comRun = ComplementaryValidator.validate(listOf("abs"), 2, 30, listOf("strength", "run"))
        assertFalse(comRun.valid)
        assertTrue(comRun.error!!.contains("força"))
    }
}
