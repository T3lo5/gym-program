package com.treinos.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migração no device: o schema exportado (`app/schemas`) e a `MIGRATION_1_2` são
 * validados contra o SQLite real do Android. O gate compila este teste
 * (`:app:assembleDebugAndroidTest`) mas não o executa — o Docker não tem emulador.
 *
 * Rode com: ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    @Throws(IOException::class)
    fun abre_banco_na_versao_atual() {
        // Migra de 1 para 2 no SQLite do aparelho e valida o resultado contra o schema
        // exportado de 2.json; o Robolectric usa SQLite embutido do host, não este.
        val version = 2
        helper.createDatabase(TEST_DB, 1).close()
        val migrated =
            helper.runMigrationsAndValidate(TEST_DB, version, true, AppDatabase.MIGRATION_1_2)
        migrated.query("PRAGMA user_version").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(version, cursor.getInt(0))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DB = "migration-test-device"
    }
}
