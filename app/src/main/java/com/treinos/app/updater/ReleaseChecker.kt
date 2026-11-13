package com.treinos.app.updater

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Repositório consultado pelo updater (H3-51). Fonte única da checagem de
 * versão — o mesmo repositório cujo workflow publica o release rolling
 * `latest` com assets de nome estável.
 */
const val UPDATER_REPOSITORY = "T3lo5/gym-program"

/**
 * Release `latest` do GitHub, já reduzido ao que o updater usa (H3-51).
 *
 * `versionName` e `versionCode` vêm do corpo do release
 * (`- **Versão:** 1.2.3 (versionCode 42)`), porque a tag é sempre `latest`.
 */
data class ReleaseInfo(
    val tagName: String,
    val versionName: String?,
    val versionCode: Long?,
    val apkUrl: String,
    val sha256Url: String,
)

/**
 * Interpretação pura da resposta da API do GitHub — testável na JVM sem rede.
 */
object ReleaseParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val versionCodeRegex = Regex("""versionCode\s+(\d+)""")
    private val versionNameRegex = Regex("""\*\*Versão:\*\*\s*`([^`]+)`""")

    /**
     * Interpreta o JSON de `releases/latest`. Retorna null quando a resposta é
     * malformada ou não traz o asset `treinos.apk` — o updater trata null como
     * "nada a fazer"; nunca lança.
     */
    fun parse(payload: String?): ReleaseInfo? = try {
        parseStrict(payload)
    } catch (_: Exception) {
        null
    }

    private fun parseStrict(payload: String?): ReleaseInfo? {
        if (payload.isNullOrBlank()) return null
        val root = json.parseToJsonElement(payload).jsonObject
        val tagName = root["tag_name"]?.jsonPrimitive?.content ?: return null
        val assets = root["assets"]?.jsonArray ?: return null
        var apkUrl: String? = null
        var sha256Url: String? = null
        for (asset in assets) {
            val entry = asset.jsonObject
            when (entry["name"]?.jsonPrimitive?.content) {
                "treinos.apk" -> apkUrl = entry["browser_download_url"]?.jsonPrimitive?.content
                "treinos.apk.sha256" -> sha256Url = entry["browser_download_url"]?.jsonPrimitive?.content
            }
        }
        apkUrl ?: return null
        val body = root["body"]?.jsonPrimitive?.content
        return ReleaseInfo(
            tagName = tagName,
            versionName = body?.let { versionNameRegex.find(it)?.groupValues?.get(1) },
            versionCode = body?.let {
                versionCodeRegex.find(it)?.groupValues?.get(1)?.toLongOrNull()
            },
            apkUrl = apkUrl,
            // Sem o .sha256 publicado, deriva do nome estável do APK.
            sha256Url = sha256Url ?: "$apkUrl.sha256",
        )
    }

    /**
     * Primeiro token do arquivo `.sha256` (formato `<hash>  <arquivo>` ou só
     * `<hash>`); null quando vazio ou fora do formato de hash SHA-256.
     */
    fun parseExpectedSha256(text: String?): String? =
        text?.trim()
            ?.split(Regex("\\s+"))
            ?.firstOrNull()
            ?.takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }
}

/**
 * Busca o release `latest` na API pública do GitHub (sem autenticação).
 * A checagem é sob demanda (ao abrir Ajustes + botão manual) — nunca polling.
 */
class ReleaseChecker(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build(),
) {
    /** Corpo bruto da resposta; null em erro de rede, 403 (rate limit) ou 404. */
    fun fetchLatest(): String? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$UPDATER_REPOSITORY/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "treinos-app")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }
}
