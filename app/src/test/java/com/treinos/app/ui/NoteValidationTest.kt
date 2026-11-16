package com.treinos.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regras puras da nota do treino/exercício (H4-11). */
class NoteValidationTest {

    @Test
    fun trim_antes_de_gravar() {
        assertEquals("ombro esquerdo incomodou", normalizeNote("  ombro esquerdo incomodou  "))
        assertEquals("texto", normalizeNote("\ntexto\n"))
    }

    @Test
    fun texto_so_espacos_vira_vazio() {
        // Nota só de espaços/quebras é limpa: gravar vazio apaga a nota.
        assertEquals("", normalizeNote("   \n\t  "))
    }

    @Test
    fun limite_de_2000_aceito() {
        assertEquals(2000, MAX_WORKOUT_NOTE_CHARS)
        assertFalse(noteLengthError("a".repeat(MAX_WORKOUT_NOTE_CHARS)))
    }

    @Test
    fun acima_de_2000_bloqueia() {
        assertTrue(noteLengthError("a".repeat(MAX_WORKOUT_NOTE_CHARS + 1)))
    }
}
