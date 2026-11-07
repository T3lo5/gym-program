package com.treinos.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Rótulos em português do motivo do fallback local (H3-21).
 *
 * Os códigos espelham os valores fechados do contrato (`fallbackReason`),
 * definidos no backend em `FALLBACK_REASONS` e validados dos dois lados
 * pelo fixture `contracts/generate_response.json`.
 */
class FallbackReasonLabelTest {

    @Test
    fun sem_motivo_retorna_null() {
        // Plano veio da IA (campo vazio) ou backend antigo (campo ausente).
        assertNull(fallbackReasonLabel(null))
        assertNull(fallbackReasonLabel(""))
    }

    @Test
    fun codigos_fechados_tem_rotulo() {
        assertEquals("chave da IA não configurada", fallbackReasonLabel("no_api_key"))
        assertEquals("a API de IA respondeu com erro", fallbackReasonLabel("http_error"))
        assertEquals("tempo limite ou erro de rede na IA", fallbackReasonLabel("timeout"))
        assertEquals("resposta da IA sem JSON válido", fallbackReasonLabel("json_parse"))
        assertEquals("resposta da IA sem IDs válidos", fallbackReasonLabel("unresolved_ids"))
        assertEquals(
            "resposta da IA fora do formato esperado",
            fallbackReasonLabel("schema_validation"),
        )
    }

    @Test
    fun codigo_desconhecido_cita_o_codigo() {
        // Código novo vindo de um backend mais novo não quebra a UI.
        assertEquals("motivo desconhecido (xyz)", fallbackReasonLabel("xyz"))
    }
}
