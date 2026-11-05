package com.treinos.app.data.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Leitura do backup com limite de tamanho.
 *
 * Um `OpenDocument` pode apontar para um provedor de nuvem que não informa o
 * tamanho do arquivo, então o limite é aplicado durante a leitura e não só na
 * consulta de metadados.
 */
object BackupSizeGuard {

    private const val CHUNK_BYTES = 8192

    val tooLargeMessage: String =
        "Arquivo muito grande para importar (limite de 25 MB)."

    /** `null` quando o conteúdo passa de [maxBytes]; nada além do limite é retido. */
    fun readLimited(
        input: InputStream,
        maxBytes: Long = BackupValidator.MAX_FILE_BYTES,
    ): String? {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(CHUNK_BYTES)
        var total = 0L

        while (true) {
            val read = input.read(chunk)
            if (read == -1) break
            total += read
            if (total > maxBytes) return null
            out.write(chunk, 0, read)
        }

        return out.toString(Charsets.UTF_8.name())
    }
}
