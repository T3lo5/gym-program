package com.treinos.app.data.local

import com.treinos.app.data.prefs.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Prova de que a armadilha do `treinos_database` (spec H3-10) não volta: os XMLs
 * de backup da plataforma precisam citar o nome real do banco
 * ([AppDatabase.DATABASE_NAME]) e do arquivo de prefs ([AppPreferences.PREFS_NAME]).
 *
 * Um `<include>` com caminho errado não inclui nada em silêncio; com a política
 * de `<exclude>` da H3-10, um nome errado deixaria de excluir — o teste pega
 * dos dois jeitos, comparando contra as constantes, não contra strings soltas.
 */
class BackupRulesTest {

    /** (seção, arquivo): `null` é o root de backup_rules.xml (Android 11−). */
    private val sections = listOf(
        null to "backup_rules.xml",
        "cloud-backup" to "data_extraction_rules.xml",
        "device-transfer" to "data_extraction_rules.xml",
    )

    /** Par (domínio, caminho) de cada <exclude>/<include> dentro de [section]. */
    private fun rules(section: String?, fileName: String): List<Pair<String, String>> {
        val file = File("src/main/res/xml/$fileName")
        assertTrue("Arquivo não encontrado em ${file.absolutePath}", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val scope = if (section == null) {
            listOf(doc.documentElement)
        } else {
            val nodes = doc.documentElement.getElementsByTagName(section)
            assertEquals("Seção <$section> ausente em $fileName", 1, nodes.length)
            listOf(nodes.item(0))
        }
        return scope.flatMap { scopeNode ->
            val children = scopeNode.childNodes
            (0 until children.length).map { children.item(it) }
        }
            .filter { it.nodeName == "exclude" || it.nodeName == "include" }
            .map { node ->
                node.attributes.getNamedItem("domain").nodeValue to
                    node.attributes.getNamedItem("path").nodeValue
            }
    }

    @Test
    fun nome_do_banco_bate_com_as_rules() {
        sections.forEach { (section, fileName) ->
            val databaseRules = rules(section, fileName).filter { (domain, _) -> domain == "database" }
            assertTrue("$fileName ($section) precisa citar o banco", databaseRules.isNotEmpty())
            val paths = databaseRules.map { (_, path) -> path }
            assertTrue(
                "$fileName ($section) precisa citar o arquivo principal ${AppDatabase.DATABASE_NAME}; veio $paths",
                AppDatabase.DATABASE_NAME in paths,
            )
            paths.forEach { path ->
                assertTrue(
                    "$fileName ($section) cita caminho de banco fora do padrão do AppDatabase: $path",
                    path == AppDatabase.DATABASE_NAME || path.startsWith("${AppDatabase.DATABASE_NAME}-"),
                )
            }
            assertFalse(
                "O nome antigo e errado 'treinos_database' não pode voltar",
                paths.any { it.contains("treinos_database") },
            )
        }
    }

    @Test
    fun prefs_nao_vao_para_a_nuvem() {
        val prefsFile = "${AppPreferences.PREFS_NAME}.xml"
        val fullBackup = rules(null, "backup_rules.xml")
        assertTrue(
            "backup_rules.xml precisa excluir $prefsFile",
            "sharedpref" to prefsFile in fullBackup,
        )
        val cloud = rules("cloud-backup", "data_extraction_rules.xml")
        assertTrue(
            "cloud-backup precisa excluir $prefsFile",
            "sharedpref" to prefsFile in cloud,
        )
        val transfer = rules("device-transfer", "data_extraction_rules.xml")
        assertFalse(
            "device-transfer é migração iniciada pelo usuário: prefs devem ir (sem exclude de $prefsFile)",
            transfer.any { (domain, path) -> domain == "sharedpref" && path == prefsFile },
        )
    }

    @Test
    fun wal_e_shm_excluidos() {
        sections.forEach { (section, fileName) ->
            val rules = rules(section, fileName)
            assertTrue(
                "$fileName ($section) precisa excluir ${AppDatabase.DATABASE_NAME}-wal (WAL em backup automático corrompe restore)",
                "database" to "${AppDatabase.DATABASE_NAME}-wal" in rules,
            )
            assertTrue(
                "$fileName ($section) precisa excluir ${AppDatabase.DATABASE_NAME}-shm",
                "database" to "${AppDatabase.DATABASE_NAME}-shm" in rules,
            )
        }
    }

    @Test
    fun manifest_referencia_as_duas_rules() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("Manifest não encontrado em ${manifest.absolutePath}", manifest.exists())
        val text = manifest.readText()
        assertTrue(
            "Manifest precisa apontar android:fullBackupContent para @xml/backup_rules",
            text.contains("""android:fullBackupContent="@xml/backup_rules""""),
        )
        assertTrue(
            "Manifest precisa apontar android:dataExtractionRules para @xml/data_extraction_rules",
            text.contains("""android:dataExtractionRules="@xml/data_extraction_rules""""),
        )
    }
}
