package com.treinos.app.data.csv

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * H4-7: parser/writer CSV puros — delimitador detectado, aspas, BOM, CRLF/LF.
 */
class CsvCodecTest {

    @Test
    fun detecta_ponto_e_virgula() {
        val rows = CsvCodec.parseCsv("a;b\r\nc;d")
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), rows)
    }

    @Test
    fun detecta_virgula() {
        val rows = CsvCodec.parseCsv("a,b\nc,d")
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), rows)
    }

    @Test
    fun campo_com_aspas_e_escapado() {
        // `""` interno vira `"` no valor.
        val rows = CsvCodec.parseCsv("\"a\"\"b\";x")
        assertEquals(listOf(listOf("a\"b", "x")), rows)
    }

    @Test
    fun campo_com_quebra_de_linha() {
        val rows = CsvCodec.parseCsv("\"linha1\nlinha2\";x")
        assertEquals(listOf(listOf("linha1\nlinha2", "x")), rows)
    }

    @Test
    fun campo_com_delimitador_interno() {
        val rows = CsvCodec.parseCsv("\"a;b\";c")
        assertEquals(listOf(listOf("a;b", "c")), rows)
    }

    @Test
    fun remove_bom() {
        val rows = CsvCodec.parseCsv(CsvCodec.UTF8_BOM + "a;b")
        assertEquals(listOf(listOf("a", "b")), rows)
    }

    @Test
    fun aceita_crlf_e_lf() {
        val rows = CsvCodec.parseCsv("a;b\r\nc;d\ne;f")
        assertEquals(
            listOf(listOf("a", "b"), listOf("c", "d"), listOf("e", "f")),
            rows,
        )
    }

    @Test
    fun linha_com_numero_de_colunas_errado_e_ignorada() {
        val rows = CsvCodec.parseCsv("a;b\nc\nd;e;f")
        assertEquals(listOf(listOf("a", "b")), rows)
    }

    @Test
    fun writer_cita_quando_necessario() {
        val row = CsvCodec.writeCsvRow(listOf("a;b", "diga \"oi\"", "linha\nquebrada", "simples"))
        assertEquals("\"a;b\";\"diga \"\"oi\"\"\";\"linha\nquebrada\";simples", row)
        assertEquals("simples", CsvCodec.escapeCsvField("simples"))
    }
}
