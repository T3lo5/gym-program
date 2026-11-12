package com.treinos.wear.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearDebounceTest {

    @Test
    fun primeiro_toque_passa() {
        val debounce = WearDebounce()
        assertTrue(debounce.canSend(1_000L))
    }

    @Test
    fun segundo_toque_na_janela_bloqueia() {
        val debounce = WearDebounce()
        debounce.markSent(1_000L)
        assertFalse(debounce.canSend(2_999L))
    }

    @Test
    fun janela_expirada_passa() {
        val debounce = WearDebounce()
        debounce.markSent(1_000L)
        assertTrue(debounce.canSend(3_000L))
    }

    @Test
    fun janela_unica_para_todos_os_botoes() {
        val debounce = WearDebounce()
        debounce.markSent(1_000L)
        // Outro botão compartilha a mesma janela.
        assertFalse(debounce.canSend(1_500L))
    }
}