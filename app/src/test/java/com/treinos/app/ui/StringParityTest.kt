package com.treinos.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Garante que o pacote de strings em português (`values`) e em inglês
 * (`values-en`) são espelhos exatos: mesma chave, e mesma assinatura de
 * placeholders (`%1$s`, `%1$d`, ...).
 */
class StringParityTest {

    @Test
    fun chaves_pt_tem_equivalente_en() {
        val pt = chaves(File("src/main/res/values/strings.xml"))
        val en = chaves(File("src/main/res/values-en/strings.xml"))
        assertEquals(
            "Toda chave PT deve ter equivalente EN (e vice-versa)",
            pt, en,
        )
    }

    @Test
    fun placeholders_batem_pt_e_en() {
        val pt = valores(File("src/main/res/values/strings.xml"))
        val en = valores(File("src/main/res/values-en/strings.xml"))
        assertEquals("Chaves PT e EN devem coincidir", pt.keys, en.keys)
        for ((key, ptValue) in pt) {
            val enValue = en[key]!!
            assertEquals(
                "Placeholders da chave '$key' diferem entre PT e EN",
                placeholders(ptValue).sorted(), placeholders(enValue).sorted(),
            )
        }
    }

    private fun chaves(arquivo: File): Set<String> =
        arquivo.readLines()
            .flatMap { linha ->
                Regex("""<string\s+name="([^"]+)"""").findAll(linha)
                    .map { it.groupValues[1] }
            }
            .toSet()

    private fun valores(arquivo: File): Map<String, String> =
        arquivo.readLines()
            .flatMap { linha ->
                Regex("""<string\s+name="([^"]+)"[^>]*>([^<]*)</string>""").findAll(linha)
                    .map { it.groupValues[1] to it.groupValues[2] }
            }
            .toMap()

    private fun placeholders(texto: String): List<String> =
        Regex("%(?:[1-9]\\$)?[sd]").findAll(texto).map { it.value }.toList().sorted()
}
