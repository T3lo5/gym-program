package com.treinos.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Equipamento disponível para a troca de exercício (bug relatado por usuário):
 * a troca durante o treino usava só o `equipment` da ÚLTIMA geração
 * (`generateConfig`). Quem gerou um treino de peso corporal e depois treina um
 * plano de academia completa via a lista de substitutos travada em "body only".
 * O filtro passa a ser a união do equipamento dos exercícios do plano ativo
 * com o da configuração — nunca mais estreito que o treino em andamento.
 */
class SwapEquipmentTest {

    @Test
    fun plano_de_academia_libera_equipamentos_apesar_de_config_peso_corporal() {
        // Última geração foi peso corporal; o plano ativo é de academia.
        val disponivel = availableEquipmentForSwap(
            planEquipment = listOf("barbell", "dumbbell", "machine", "body only"),
            configuredEquipment = listOf("body only"),
        )

        assertTrue("barbell deve estar disponível", disponivel.contains("barbell"))
        assertTrue("dumbbell deve estar disponível", disponivel.contains("dumbbell"))
        assertTrue("machine deve estar disponível", disponivel.contains("machine"))
    }

    @Test
    fun uni_config_e_plano_sem_duplicar() {
        val disponivel = availableEquipmentForSwap(
            planEquipment = listOf("barbell", "dumbbell"),
            configuredEquipment = listOf("dumbbell", "cable"),
        )

        assertEquals(listOf("barbell", "dumbbell", "cable"), disponivel)
    }

    @Test
    fun equipamento_vazio_do_plano_e_ignorado() {
        val disponivel = availableEquipmentForSwap(
            planEquipment = listOf("", "barbell", "   "),
            configuredEquipment = listOf(""),
        )

        assertEquals(listOf("barbell"), disponivel)
    }

    @Test
    fun nada_informado_devolve_vazio_sem_filtrar() {
        // Vazio significa "não filtra por equipamento" no `sessionSwapCandidates`:
        // plano sem metadados e sem config não pode travar a troca.
        val disponivel = availableEquipmentForSwap(
            planEquipment = emptyList(),
            configuredEquipment = emptyList(),
        )

        assertTrue(disponivel.isEmpty())
    }

    @Test
    fun config_so_nao_restringe_o_plano() {
        // A configuração da última geração nunca estreita o filtro: só soma.
        val disponivel = availableEquipmentForSwap(
            planEquipment = listOf("barbell"),
            configuredEquipment = listOf("body only"),
        )

        assertTrue(disponivel.contains("barbell"))
        assertTrue(disponivel.contains("body only"))
        assertFalse(disponivel.contains("machine"))
    }
}
