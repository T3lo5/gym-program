package com.treinos.wear.data

import com.treinos.protocol.WearCommand
import com.treinos.protocol.WearCommandKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearCommandQueueTest {

    private fun comando(id: String, sessionId: Long = 1): WearCommand =
        WearCommand(
            commandId = id,
            sessionId = sessionId,
            kind = WearCommandKind.LOG_SET,
        )

    @Test
    fun fifo_preserva_ordem() {
        val fila = WearCommandQueue()
        fila.add(comando("a"))
        fila.add(comando("b"))
        fila.add(comando("c"))

        assertEquals(listOf("a", "b", "c"), fila.all().map { it.commandId })
    }

    @Test
    fun cap_vinte_descarta_mais_antigo() {
        val fila = WearCommandQueue()
        repeat(20) { i -> fila.add(comando("c$i")) }
        fila.add(comando("novo"))

        assertEquals(20, fila.size())
        assertEquals("c1", fila.all().first().commandId)
        assertEquals("novo", fila.all().last().commandId)
        assertFalse(fila.all().any { it.commandId == "c0" })
    }

    @Test
    fun remove_por_command_id() {
        val fila = WearCommandQueue()
        fila.add(comando("a"))
        fila.add(comando("b"))

        assertTrue(fila.removeById("a"))
        assertEquals(listOf("b"), fila.all().map { it.commandId })
        assertFalse(fila.removeById("a"))
    }

    @Test
    fun decode_invalido_vira_fila_vazia() {
        assertEquals(0, WearCommandQueue.decode("não é json").size())
        assertEquals(0, WearCommandQueue.decode(null).size())
        assertEquals(0, WearCommandQueue.decode("").size())
    }

    @Test
    fun round_trip_json() {
        val fila = WearCommandQueue()
        fila.add(comando("a", sessionId = 7))
        fila.add(
            WearCommand(
                commandId = "b",
                sessionId = 7,
                kind = WearCommandKind.FINISH_WORKOUT,
            ),
        )

        val restaurada = WearCommandQueue.decode(fila.encode())

        assertEquals(
            listOf("a", "b"),
            restaurada.all().map { it.commandId },
        )
        assertEquals(WearCommandKind.FINISH_WORKOUT, restaurada.all().last().kind)
    }
}