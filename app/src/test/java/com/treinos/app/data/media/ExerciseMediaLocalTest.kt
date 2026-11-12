package com.treinos.app.data.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.treinos.app.data.remote.BackendClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * H3-44: o catálogo local tem de existir antes de qualquer rede. Estes testes
 * rodam com Robolectric porque exercitam o cache em `filesDir` e o asset
 * embarcado de verdade.
 */
@RunWith(RobolectricTestRunner::class)
class ExerciseMediaLocalTest {

    private lateinit var context: Context
    private lateinit var repository: ExerciseMediaRepository
    private lateinit var cacheFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cacheFile = File(context.filesDir, "exercise_catalog_v1.json")
        cacheFile.delete()
        repository = ExerciseMediaRepository(
            context = context,
            backendClient = BackendClient({ "http://127.0.0.1:1" }),
        )
    }

    private fun writeCache(fetchedAt: Long, exercises: String) {
        cacheFile.writeText(
            """{"fetchedAt":$fetchedAt,"exercises":$exercises}""",
        )
    }

    private fun cacheExercise(id: String, name: String): String =
        """{"id":"$id","name":"$name","primaryMuscles":["chest"],"equipment":"barbell","instructions":[],"images":[]}"""

    @Test
    fun load_local_usa_cache_mesmo_expirado() = runTest {
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        writeCache(thirtyDaysAgo, "[${cacheExercise("Cache_Exerc", "Exerc do cache")}]")

        val local = repository.loadLocal()

        assertTrue("cache expirado deve ser usado offline", local.containsKey("Cache_Exerc"))
        assertFalse("asset não deve vencer o cache", local.containsKey("Dumbbell_Bench_Press"))
    }

    @Test
    fun load_local_cai_para_asset_quando_sem_cache() = runTest {
        val local = repository.loadLocal()

        assertTrue(
            "primeiro uso offline deve usar o asset embarcado",
            local.containsKey("Dumbbell_Bench_Press"),
        )
    }

    @Test
    fun cache_corrompido_cai_para_asset() = runTest {
        cacheFile.writeText("nao e json")

        val local = repository.loadLocal()

        assertTrue(local.containsKey("Dumbbell_Bench_Press"))
    }

    @Test
    fun cache_vazio_cai_para_asset() = runTest {
        writeCache(System.currentTimeMillis(), "[]")

        val local = repository.loadLocal()

        assertTrue(local.containsKey("Dumbbell_Bench_Press"))
    }
}