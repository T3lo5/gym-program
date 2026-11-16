package com.treinos.app.data.csv

import java.text.Normalizer

/**
 * Parser e writer CSV puros (H4-7), sem dependência externa.
 *
 * O delimitador é detectado na primeira linha (vírgula ou ponto e vírgula, o
 * que tiver mais ocorrências fora de aspas), o BOM é removido antes da leitura
 * e campos entre aspas preservam delimitador, aspas escapadas (dobradas) e
 * quebra de linha.
 */
object CsvCodec {

    /** BOM UTF-8: o export o inclui (Excel pt-BR) e o import o remove. */
    const val UTF8_BOM = "\uFEFF"

    private const val COMMA = ','
    private const val SEMICOLON = ';'
    private const val QUOTE = '"'

    /**
     * Divide o texto em registros de campos.
     *
     * - remove o BOM antes de tudo;
     * - detecta o delimitador na primeira linha (empate vira vírgula);
     * - aceita CRLF e LF como quebra de registro;
     * - campo entre aspas pode conter delimitador, aspas (escapadas em pares)
     *   e quebra de linha;
     * - ignora registros com número de campos diferente do primeiro (linhas
     *   malformadas não viram dados).
     */
    fun parseCsv(text: String): List<List<String>> {
        val content = text.removePrefix(UTF8_BOM)
        if (content.isBlank()) return emptyList()

        val delimiter = detectDelimiter(content)
        val records = mutableListOf<List<String>>()
        val field = StringBuilder()
        var record = mutableListOf<String>()
        var inQuotes = false
        var index = 0

        fun endField() {
            record.add(field.toString())
            field.setLength(0)
        }

        fun endRecord() {
            endField()
            records.add(record)
            record = mutableListOf()
        }

        while (index < content.length) {
            val char = content[index]
            when {
                inQuotes -> when {
                    char == QUOTE && index + 1 < content.length && content[index + 1] == QUOTE -> {
                        field.append(QUOTE)
                        index++
                    }
                    char == QUOTE -> inQuotes = false
                    else -> field.append(char)
                }
                char == QUOTE && field.isEmpty() -> inQuotes = true
                char == delimiter -> endField()
                char == '\r' -> {
                    if (index + 1 < content.length && content[index + 1] == '\n') index++
                    endRecord()
                }
                char == '\n' -> endRecord()
                else -> field.append(char)
            }
            index++
        }
        if (field.isNotEmpty() || record.isNotEmpty()) endRecord()

        val cleaned = records.filterNot { it.size == 1 && it[0].isEmpty() }
        if (cleaned.isEmpty()) return emptyList()
        val width = cleaned.first().size
        return cleaned.filter { it.size == width }
    }

    /**
     * Delimitador do primeiro registro: vírgula ou ponto e vírgula, o que
     * tiver mais ocorrências fora de aspas. Empate (ou ausência) vira
     * vírgula, o padrão do Strong/Hevy. A varredura para na primeira quebra
     * de linha FORA de aspas — um campo citado multilinha no cabeçalho não
     * corta a amostra no meio.
     */
    fun detectDelimiter(content: String): Char {
        var commas = 0
        var semicolons = 0
        var inQuotes = false
        for (char in content) {
            when {
                char == QUOTE -> inQuotes = !inQuotes
                !inQuotes && (char == '\r' || char == '\n') -> break
                !inQuotes && char == COMMA -> commas++
                !inQuotes && char == SEMICOLON -> semicolons++
            }
        }
        return if (semicolons > commas) SEMICOLON else COMMA
    }

    /** Uma linha CSV completa, sem quebra final — o chamador escolhe o fim de linha. */
    fun writeCsvRow(fields: List<String>, delimiter: Char = SEMICOLON): String =
        fields.joinToString(delimiter.toString()) { escapeCsvField(it, delimiter) }

    /**
     * Campo com delimitador, aspas ou quebra de linha é citado, e a aspa
     * interna vira um par de aspas.
     */
    fun escapeCsvField(field: String, delimiter: Char = SEMICOLON): String {
        val needsQuotes = field.contains(delimiter) ||
            field.contains(QUOTE) ||
            field.contains('\n') ||
            field.contains('\r')
        if (!needsQuotes) return field
        return "\"" + field.replace(QUOTE.toString(), "\"\"") + "\""
    }

    /** Minúsculas, sem acento e com espaços colapsados — normalização de cabeçalho. */
    internal fun normalizeHeaderField(value: String): String =
        stripAccents(value.trim())
            .lowercase()
            .replace(Regex("\\s+"), " ")

    /** Remove acentos (NFD + marca combinante) para comparação e slug. */
    internal fun stripAccents(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
}
