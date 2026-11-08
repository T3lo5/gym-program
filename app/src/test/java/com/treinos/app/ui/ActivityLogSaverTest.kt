package com.treinos.app.ui

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import com.treinos.app.ui.screens.ActivityLogDraftSaver
import org.junit.Assert.assertEquals
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
}
