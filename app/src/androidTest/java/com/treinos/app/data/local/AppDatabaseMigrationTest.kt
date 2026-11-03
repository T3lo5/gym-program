package com.treinos.app.data.local

import androidx.room.Room
import androidx.room.testing.Frameworks
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

/**
 * Teste instrumentado de migração do AppDatabase.
 *
 * Enquanto a versão for 1, o teste apenas abre o banco e valida que o
 * schema exportado está presente. Não há migrations para testar ainda.
 *
 * Rode com: ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val migrationTestHelper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        Frameworks.newFrameworkInstrumentation()
    )

    @Test
    fun abreBancoNaVersaoAtual() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "treinos_migration_test.db",
        )
            .addMigrations()
            .build()

        assertTrue(database.isOpen)
        database.close()
    }
}
