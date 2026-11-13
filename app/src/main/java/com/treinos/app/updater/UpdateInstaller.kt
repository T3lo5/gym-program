package com.treinos.app.updater

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Download verificado do APK do release e disparo da instalação pelo fluxo do
 * sistema (H3-51). O arquivo fica no cache interno; hash divergente descarta
 * o arquivo e nada instala. A instalação sempre passa pela confirmação do
 * sistema — o app nunca instala nem desinstala por conta própria.
 */
class UpdateInstaller(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.MINUTES)
        .build(),
) {
    /**
     * Baixa o `.sha256`, depois o APK, conferindo o SHA-256 durante o stream.
     * Em erro ou divergência de hash, apaga o arquivo parcial e falha —
     * nada é deixado pela metade no cache.
     */
    suspend fun downloadAndVerify(
        apkUrl: String,
        sha256Url: String,
        onProgress: (Int) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val expected = fetchExpectedSha256(sha256Url)
                ?: error("checksum do release indisponível")
            Result.success(downloadApk(apkUrl, expected, onProgress))
        } catch (cancellation: CancellationException) {
            apkFile().delete()
            throw cancellation
        } catch (failure: Exception) {
            apkFile().delete()
            Result.failure(failure)
        }
    }

    /** Intent que abre o instalador do sistema para o APK baixado. */
    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK,
            )
    }

    private fun apkFile(): File = File(File(context.cacheDir, "updater"), "treinos.apk")

    private fun fetchExpectedSha256(url: String): String? = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                null
            } else {
                ReleaseParser.parseExpectedSha256(response.body?.string())
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun downloadApk(
        url: String,
        expectedSha256: String,
        onProgress: (Int) -> Unit,
    ): File {
        val file = apkFile()
        file.parentFile?.mkdirs()
        file.delete()
        val digest = MessageDigest.getInstance("SHA-256")
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("download do APK falhou (HTTP ${response.code})")
            }
            val body = response.body ?: error("download do APK vazio")
            val total = body.contentLength()
            var read = 0L
            file.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        read += count
                        if (total > 0) {
                            onProgress(((read * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
        }
        val actual = digest.digest().joinToString(separator = "") { "%02x".format(it) }
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            file.delete()
            error("checksum divergente — arquivo descartado")
        }
        return file
    }
}
