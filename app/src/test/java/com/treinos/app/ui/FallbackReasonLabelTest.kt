package com.treinos.app.ui

import com.treinos.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Recursos do motivo do fallback local (H3-21).
 *
 * Os códigos espelham os valores fechados do contrato (`fallbackReason`),
 * definidos no backend em `FALLBACK_REASONS` e validados dos dois lados
 * pelo fixture `contracts/generate_response.json`.
 *
 * H3-52: a função devolve o recurso — quem chama resolve com `getString`
 * no idioma escolhido; o teste afirma o mapeamento código → recurso.
 */
class FallbackReasonLabelTest {

    @Test
    fun sem_motivo_retorna_null() {
        // Plano veio da IA (campo vazio) ou backend antigo (campo ausente).
        assertNull(fallbackReasonLabelRes(null))
        assertNull(fallbackReasonLabelRes(""))
    }

    @Test
    fun codigos_fechados_tem_rotulo() {
        assertEquals(R.string.fallback_no_api_key, fallbackReasonLabelRes("no_api_key"))
        assertEquals(R.string.fallback_http_error, fallbackReasonLabelRes("http_error"))
        assertEquals(R.string.fallback_timeout, fallbackReasonLabelRes("timeout"))
        assertEquals(R.string.fallback_json_parse, fallbackReasonLabelRes("json_parse"))
        assertEquals(R.string.fallback_unresolved_ids, fallbackReasonLabelRes("unresolved_ids"))
        assertEquals(
            R.string.fallback_schema_validation,
            fallbackReasonLabelRes("schema_validation"),
        )
    }

    @Test
    fun codigo_desconhecido_cita_o_codigo() {
        // Código novo vindo de um backend mais novo não quebra a UI: o recurso
        // tem o placeholder do código.
        assertEquals(R.string.fallback_unknown, fallbackReasonLabelRes("xyz"))
    }
}
