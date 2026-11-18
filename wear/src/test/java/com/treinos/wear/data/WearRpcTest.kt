package com.treinos.wear.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * O crash "abre e fecha na sequência" do relógio era `Tasks.await` do GMS na
 * main thread (`IllegalStateException: Must not be called on the main
 * application thread`). Toda chamada GMS do pulso passa por [WearRpc], então
 * o contrato dele é o que trava a classe do bug:
 *
 * 1. o bloco roda FORA da thread do chamador (a main, nos callbacks reais);
 * 2. exceção do bloco nunca propaga — vira valor neutro.
 */
class WearRpcTest {

    @Test
    fun offMain_executa_fora_da_thread_do_chamador() = runBlocking {
        val chamador = Thread.currentThread()
        var executouEm: Thread? = null
        val resultado = WearRpc().offMain {
            executouEm = Thread.currentThread()
            "ok"
        }
        assertEquals("ok", resultado)
        assertNotEquals("await GMS na main derruba o processo", chamador, executouEm)
    }

    @Test
    fun offMain_nunca_propaga_excecao_do_bloco() = runBlocking {
        val resultado = WearRpc().offMain<String> { error("GMS explodiu") }
        assertNull("falha do GMS vira valor neutro, não crash", resultado)
    }
}
