package com.treinos.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H3-30: codec puro da configuração persistida da tela "Gerar"
 * (`generate_config_v1`). Sem Android — roda na JVM.
 */
class GenerateConfigTest {

    @Test
    fun round_trip_config() {
        val config = GenerateConfig(
            planKind = "extra",
            goal = "strength",
            daysPerWeek = 3,
            level = "advanced",
            equipment = listOf("barbell", "cable"),
            muscles = listOf("chest", "back"),
            sessionDurationMinutes = 45,
            reviewWeeks = 6,
            environment = "home",
            bodyWeightKgText = "82.5",
            heightCmText = "178",
            sexChoice = "male",
            modalities = listOf(
                ModalityDraft(type = "strength", included = true, sessionsPerWeek = 4),
                ModalityDraft(type = "run", included = true, sessionsPerWeek = 2, recent5kSeconds = 1500),
                ModalityDraft(type = "mobility", focus = "quadril"),
            ),
        )

        val encoded = GenerateConfigCodec.encode(config)
        val decoded = GenerateConfigCodec.decode(encoded)

        assertEquals(config, decoded)
        assertTrue(encoded.contains("\"goal\":\"strength\""))
        assertEquals(1500, decoded?.modalities?.first { it.type == "run" }?.recent5kSeconds)
        assertEquals("quadril", decoded?.modalities?.first { it.type == "mobility" }?.focus)
    }

    @Test
    fun campo_opcional_nulo_nao_viaja() {
        val encoded = GenerateConfigCodec.encode(GenerateConfig(sexChoice = null))

        assertFalse(encoded.contains("sexChoice"))
        assertFalse(encoded.contains("\"focus\""))
    }

    @Test
    fun campo_desconhecido_e_ignorado() {
        // ignoreUnknownKeys: uma versão futura pode acrescentar campos sem
        // quebrar a leitura da config salva por uma versão antiga.
        val decoded = GenerateConfigCodec.decode("""{"planKind":"main","campoNovo":123}""")

        assertEquals("main", decoded?.planKind)
    }

    @Test
    fun config_corrompida_cai_no_default() {
        assertNull(GenerateConfigCodec.decode(null))
        assertNull(GenerateConfigCodec.decode(""))
        assertNull(GenerateConfigCodec.decode("   "))
        assertNull(GenerateConfigCodec.decode("não é json {"))
        // JSON válido com tipo errado também é rejeitado.
        assertNull(GenerateConfigCodec.decode("""{"daysPerWeek":"muito"}"""))
    }

    @Test
    fun musculacao_default_incluida() {
        val config = GenerateConfig.DEFAULT

        val strength = config.draftsByType.getValue("strength")
        assertTrue("Primeira abertura deve vir com musculação inclusa", strength.included)
        assertEquals(4, strength.sessionsPerWeek)
        // Corrida e mobilidade nascem desligadas.
        assertFalse(config.draftsByType.getValue("run").included)
        assertFalse(config.draftsByType.getValue("mobility").included)
        // O CTA nasce habilitado: há modalidade inclusa.
        assertTrue(config.modalities.any { it.included })
    }

    @Test
    fun drafts_por_tipo_cai_no_padrao_quando_falta_tipo() {
        // Config antiga/parcial sem mobilidade: o draft ausente vira o padrão.
        val config = GenerateConfig(
            modalities = listOf(ModalityDraft(type = "strength", included = true)),
        )

        assertFalse(config.draftsByType.getValue("run").included)
        assertFalse(config.draftsByType.getValue("mobility").included)
    }
}
