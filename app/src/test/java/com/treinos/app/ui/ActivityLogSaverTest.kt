package com.treinos.app.ui

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import com.treinos.app.ui.screens.ActivityLogDraftSaver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `Saver.save` é member-extension de `SaverScope`: o K1 só resolve a chamada
 * com os dois receivers implícitos (o padrão dos próprios testes do androidx) —
 * `saver.save(x)` com receiver explícito dentro de `with` não compila.
 */
private fun <T : Any> Saver<T, Any>.saveForTest(value: T): Any? =
    with(SaverScope { true }) { save(value) }

/**
 * Saver do rascunho do registro avulso (H3-31): girar o aparelho não perde o
 * que foi digitado. Coberto por round-trip puro.
 *
 * H3-56: o mesmo rascunho também é persistido em `AppPreferences` via
 * `ActivityLogDraftCodec` (sobrevive à morte do processo). Os testes de ida e
 * volta do codec vivem aqui, no módulo JVM puro.
 */
class ActivityLogSaverTest {

    @Test
    fun draft_round_trip_saver() {
        val draft = ActivityLogDraft(
            modality = ActivityModality.RUN,
            title = "Corrida parque",
            dateText = "04/11/2026 17:00",
            durationText = "45",
            distanceText = "5,2",
            poolLengthMeters = 25,
            stroke = "crawl",
            rpeText = "7,5",
            notes = "8 x 100 m",
        )

        // Round-trip pelo `Saver` real: o que `rememberSaveable` grava no Bundle
        // é exatamente o que `restore` devolve.
        val saved = ActivityLogDraftSaver.saveForTest(draft)
        val restored = ActivityLogDraftSaver.restore(requireNotNull(saved))

        assertEquals(draft, restored)
    }

    @Test
    fun codec_ida_e_volta_preserva_campos() {
        val draft = ActivityLogDraft(
            modality = ActivityModality.SWIM,
            title = "Natação de 1.000 m",
            dateText = "14/11/2026 08:30",
            durationText = "40",
            distanceText = "1000",
            poolLengthMeters = 50,
            stroke = "crawl",
            rpeText = "6",
            notes = "aquecimento + 10 x 100",
        )

        val encoded = ActivityLogDraftCodec.encode(draft)
        val decoded = ActivityLogDraftCodec.decode(encoded)

        assertEquals(draft, decoded)
    }

    @Test
    fun codec_entrada_vazia_devolve_null() {
        assertNull(ActivityLogDraftCodec.decode(null))
        assertNull(ActivityLogDraftCodec.decode(""))
        assertNull(ActivityLogDraftCodec.decode("   "))
    }

    @Test
    fun codec_entrada_corrompida_devolve_null() {
        assertNull(ActivityLogDraftCodec.decode("not json {"))
        assertNull(ActivityLogDraftCodec.decode("{\"modality\": 42}"))
        assertNull(ActivityLogDraftCodec.decode("{\"modality\": true}"))
        assertNull(ActivityLogDraftCodec.decode("[1, 2, 3]"))
    }

    @Test
    fun codec_campos_novos_tem_default() {
        // Simula uma versão futura com campos inesperados: `ignoreUnknownKeys`
        // descarta-os e o rascunho restaura com defaults.
        val futureJson = """
            {"modality":"run","title":"t","dateText":"01/01/2026 00:00",
             "durationText":"30","distanceText":"5","poolLengthMeters":null,
             "stroke":null,"rpeText":"","notes":"","futureField":42,
             "futureString":"x","futureList":[1,2,3]}
        """.trimIndent()

        val decoded = ActivityLogDraftCodec.decode(futureJson)
        assertNotNull(decoded)
        assertEquals(ActivityModality.RUN, decoded!!.modality)
        assertEquals("t", decoded.title)
        assertEquals("30", decoded.durationText)
        assertEquals("5", decoded.distanceText)
        assertEquals("", decoded.notes)
    }

    @Test
    fun codec_modalidade_desconhecida_cai_em_other() {
        val json = """
            {"modality":"futuro","title":"","dateText":"01/01/2026 00:00",
             "durationText":"","distanceText":"","poolLengthMeters":null,
             "stroke":null,"rpeText":"","notes":""}
        """.trimIndent()

        val decoded = ActivityLogDraftCodec.decode(json)
        assertNotNull(decoded)
        assertEquals(ActivityModality.OTHER, decoded!!.modality)
    }

    @Test
    fun saver_e_codec_estao_em_sincronia() {
        // O que o Saver salva em rotação é o mesmo que o codec persiste no
        // disco: round-trip por ambos os caminhos deve chegar ao mesmo estado.
        val draft = ActivityLogDraft(
            modality = ActivityModality.STRENGTH,
            title = "Treino A",
            dateText = "14/11/2026 19:00",
            durationText = "60",
            distanceText = "",
            poolLengthMeters = null,
            stroke = null,
            rpeText = "8,5",
            notes = "3 séries",
        )
        val viaSaver = ActivityLogDraftSaver.restore(
            requireNotNull(ActivityLogDraftSaver.saveForTest(draft))
        )
        val viaCodec = ActivityLogDraftCodec.decode(ActivityLogDraftCodec.encode(draft))

        assertEquals(viaSaver, viaCodec)
        assertEquals(draft, viaSaver)
        assertEquals(draft, viaCodec)
    }
}
