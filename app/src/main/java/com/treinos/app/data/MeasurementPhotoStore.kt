package com.treinos.app.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * H4-8: guarda as fotos de progresso em `filesDir/progress-photos/` — um lugar
 * só, dentro do armazenamento privado do app.
 *
 * Privacidade (regras 8 e 9): a foto nunca vai para nuvem, backend ou backup
 * JSON. O backup automático do Android exclui a pasta nas três seções
 * (`backup_rules.xml`, `cloud-backup` e `device-transfer` de
 * `data_extraction_rules.xml`), garantia conferida por `BackupRulesTest`.
 *
 * No banco, `photoPath` é o caminho **absoluto** do arquivo; no backup JSON ele
 * viaja **relativo** a `filesDir` (a conversão é do `WorkoutRepository`).
 */
object MeasurementPhotoStore {

    const val PHOTO_DIR_NAME = "progress-photos"

    private const val FILE_EXTENSION = ".jpg"
    private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US)

    /** Pasta das fotos, criada se ainda não existir. */
    fun photosDir(context: Context): File =
        File(context.filesDir, PHOTO_DIR_NAME).apply { mkdirs() }

    /**
     * Arquivo novo e único para a próxima foto, com nome
     * `<yyyy-MM-dd-HHmm>-<random>.jpg` (regra 8). Não cria o arquivo no disco —
     * quem escreve é a câmera (via FileProvider) ou a cópia da galeria.
     */
    fun newPhotoFile(context: Context): File {
        val dir = photosDir(context)
        val stamp = fileNameFormat.format(Date())
        while (true) {
            val candidate = File(dir, "$stamp-${UUID.randomUUID().toString().take(8)}$FILE_EXTENSION")
            if (!candidate.exists()) return candidate
        }
    }

    /**
     * Apaga o arquivo da foto de uma medida. Arquivo já ausente não é erro
     * (regra 7) e caminho em branco é ignorado — exclusão de foto é best-effort:
     * nunca derruba a exclusão da medida.
     */
    fun deletePhoto(context: Context, path: String) {
        if (path.isBlank()) return
        resolve(context, path).delete()
    }

    /** `true` só quando o arquivo da foto existe de fato (base do aviso da UI). */
    fun exists(path: String): Boolean =
        path.isNotBlank() && File(path).isFile

    /** Caminho absoluto do banco; caminho relativo cai contra `filesDir`. */
    private fun resolve(context: Context, path: String): File {
        val file = File(path)
        return if (file.isAbsolute) file else File(context.filesDir, path)
    }
}
