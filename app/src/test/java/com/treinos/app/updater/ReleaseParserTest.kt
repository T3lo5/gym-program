package com.treinos.app.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * H3-51: o corpo do release é o contrato do updater in-app. O parser lê
 * `**Versão:** \`X\`` e `versionCode N` do texto e o asset `treinos.apk`.
 * Se o template do workflow mudar, estes testes quebram antes do app.
 */
class ReleaseParserTest {

    private val payload = """
        {
          "tag_name": "latest",
          "body": "Build automático do commit `abc1234`.\n\n- **Versão:** `1.5.0` (versionCode 3615278)\n",
          "assets": [
            {"name": "treinos.apk", "browser_download_url": "https://example/treinos.apk"},
            {"name": "treinos.apk.sha256", "browser_download_url": "https://example/treinos.apk.sha256"},
            {"name": "wear.apk", "browser_download_url": "https://example/wear.apk"}
          ]
        }
    """.trimIndent()

    @Test
    fun `parse_extrai_apk_e_sha256`() {
        val info = ReleaseParser.parse(payload)
        assertEquals("latest", info?.tagName)
        assertEquals("1.5.0", info?.versionName)
        assertEquals(3615278L, info?.versionCode)
        assertEquals("https://example/treinos.apk", info?.apkUrl)
        assertEquals("https://example/treinos.apk.sha256", info?.sha256Url)
    }

    @Test
    fun `parse_versao_sem_crases_do_template_real`() {
        // O template do workflow publica `- **Versão:** 1.5.0 (versionCode N)`,
        // sem crases — o parser tem de aceitar as duas formas.
        val semCrases = """
            {"tag_name":"latest","body":"- **Versão:** 1.5.0 (versionCode 3615278)\n",
             "assets":[{"name":"treinos.apk","browser_download_url":"https://example/treinos.apk"}]}
        """.trimIndent()
        val info = ReleaseParser.parse(semCrases)
        assertEquals("1.5.0", info?.versionName)
        assertEquals(3615278L, info?.versionCode)
    }

    @Test
    fun `parse_sem_asset_apk_retorna_vazio`() {
        val semApk = """
            {"tag_name":"latest","body":"","assets":[
              {"name":"wear.apk","browser_download_url":"https://example/wear.apk"}
            ]}
        """.trimIndent()
        assertNull(ReleaseParser.parse(semApk))
    }

    @Test
    fun `parse_resposta_malformada_nao_lanca`() {
        assertNull(ReleaseParser.parse(null))
        assertNull(ReleaseParser.parse(""))
        assertNull(ReleaseParser.parse("não é json"))
        assertNull(ReleaseParser.parse("{}"))
    }
}
