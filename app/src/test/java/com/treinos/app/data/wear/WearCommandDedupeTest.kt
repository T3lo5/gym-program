package com.treinos.app.data.wear

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearCommandDedupeTest {

    @Test
    fun ignora_command_id_repetido() {
        val ids = CommandDedupe.emptyIds()
        val id = "cmd-1"
        assertFalse(CommandDedupe.isRecent(ids, id))
        CommandDedupe.addRecent(ids, id)
        assertTrue(CommandDedupe.isRecent(ids, id))
        assertTrue(CommandDedupe.isRecent(ids, id))
    }

    @Test
    fun aceita_command_ids_diferentes() {
        val ids = CommandDedupe.emptyIds()
        assertFalse(CommandDedupe.isRecent(ids, "cmd-1"))
        assertFalse(CommandDedupe.isRecent(ids, "cmd-2"))
        CommandDedupe.addRecent(ids, "cmd-1")
        assertTrue(CommandDedupe.isRecent(ids, "cmd-1"))
        assertFalse(CommandDedupe.isRecent(ids, "cmd-2"))
    }

    @Test
    fun limita_historico_a_50() {
        val ids = CommandDedupe.emptyIds()
        for (i in 1..60) {
            CommandDedupe.addRecent(ids, "cmd-$i")
        }
        assertTrue(CommandDedupe.isRecent(ids, "cmd-60"))
        assertTrue(CommandDedupe.isRecent(ids, "cmd-59"))
        assertFalse(CommandDedupe.isRecent(ids, "cmd-1"))
    }

    @Test
    fun ignora_sessao_diferente() {
        // O dedupe é por commandId, independente da sessão.
        val ids = CommandDedupe.emptyIds()
        CommandDedupe.addRecent(ids, "cmd-sessao-a")
        assertTrue(CommandDedupe.isRecent(ids, "cmd-sessao-a"))
    }
}
