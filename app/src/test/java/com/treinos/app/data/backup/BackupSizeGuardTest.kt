package com.treinos.app.data.backup

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSizeGuardTest {

    private fun bytes(text: String) = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))

    @Test
    fun `validateSize rejeita acima do limite`() {
        val result = BackupValidator.validateSize(BackupValidator.MAX_FILE_BYTES + 1)

        assertFalse(result.valid)
        assertTrue(result.message?.contains("25 MB") == true)
    }

    @Test
    fun `validateSize aceita abaixo do limite`() {
        assertTrue(BackupValidator.validateSize(1024L).valid)
    }

    @Test
    fun `validateSize aceita o proprio limite`() {
        assertTrue(BackupValidator.validateSize(BackupValidator.MAX_FILE_BYTES).valid)
    }

    @Test
    fun `mensagem de tamanho e unica`() {
        val result = BackupValidator.validateSize(BackupValidator.MAX_FILE_BYTES + 1)

        assertEquals(BackupSizeGuard.tooLargeMessage, result.message)
    }

    @Test
    fun `leitura limitada rejeita quando passa do limite`() {
        val contem = ByteArray(11) { 'a'.code.toByte() }

        val lido = BackupSizeGuard.readLimited(ByteArrayInputStream(contem), maxBytes = 10L)

        assertNull(lido)
    }

    @Test
    fun `leitura limitada retorna o conteudo ate o limite`() {
        val lido = BackupSizeGuard.readLimited(bytes("treinos"), maxBytes = 10L)

        assertEquals("treinos", lido)
    }

    @Test
    fun `leitura limitada preserva acentos`() {
        val lido = BackupSizeGuard.readLimited(bytes("versão não suportada"), maxBytes = 200L)

        assertEquals("versão não suportada", lido)
    }

    @Test
    fun `leitura limitada funciona sem conhecer o tamanho`() {
        val grande = bytes("x".repeat(1_000))

        assertNull(BackupSizeGuard.readLimited(grande, maxBytes = 500L))
    }

    @Test
    fun `leitura limitada aceita stream vazio`() {
        assertEquals("", BackupSizeGuard.readLimited(ByteArrayInputStream(ByteArray(0)), maxBytes = 10L))
    }
}
