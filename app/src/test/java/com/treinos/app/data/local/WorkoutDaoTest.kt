package com.treinos.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Room em memória no gate: cobre o caminho de escrita que a UI usa (plano completo,
 * limpeza e substituição preservando IDs) sem tocar no banco do usuário.
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: WorkoutDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.workoutDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun plano(id: Long = 1, name: String = "Plano Teste", daysPerWeek: Int = 3) =
        WorkoutPlanEntity(id = id, name = name, goal = "hypertrophy", daysPerWeek = daysPerWeek, isActive = true)

    private fun dia(id: Long = 10, planId: Long = 1, name: String = "Dia A") =
        WorkoutDayEntity(id = id, planId = planId, name = name, dayOrder = 0)

    private fun exercicioPlano(id: Long = 100, dayId: Long = 10, exerciseId: String = "Barbell_Bench_Press") =
        WorkoutPlanExerciseEntity(
            id = id,
            dayId = dayId,
            exerciseId = exerciseId,
            exerciseName = "Supino reto",
            sets = 3,
            restSeconds = 90,
        )

    @Test
    fun insere_plano_completo() = runTest {
        val planId = dao.insertPlan(plano())
        assertTrue(planId > 0)

        val dayId = dao.insertDay(dia(planId = planId))
        assertTrue(dayId > 0)

        dao.insertPlanExercises(listOf(exercicioPlano(dayId = dayId)))

        assertEquals("Plano Teste", dao.getPlan(planId)?.name)
        assertEquals(listOf(planId), dao.getAllDayEntities().map { it.planId })
        assertEquals(listOf("Barbell_Bench_Press"), dao.getExercisesForDay(dayId).map { it.exerciseId })
    }

    @Test
    fun delete_all_data_limpa_tudo() = runTest {
        dao.insertPlan(plano())
        dao.insertDay(dia())
        dao.insertPlanExercises(listOf(exercicioPlano()))
        dao.insertSession(WorkoutSessionEntity(id = 50, planId = 1, dayId = 10, status = "in_progress"))
        dao.insertSessionExercises(
            listOf(SessionExerciseEntity(id = 500, sessionId = 50, exerciseId = "Barbell_Bench_Press", plannedSets = 3))
        )
        dao.insertSetLog(SetLogEntity(id = 5000, exerciseLogId = 500, setNumber = 1, reps = 10))

        assertTrue(dao.getAllPlanEntities().isNotEmpty())
        assertTrue(dao.getAllDayEntities().isNotEmpty())
        assertTrue(dao.getAllPlanExercises().isNotEmpty())
        assertTrue(dao.getAllSessionEntities().isNotEmpty())
        assertTrue(dao.getAllSessionExercises().isNotEmpty())
        assertTrue(dao.getAllSetLogs().isNotEmpty())

        dao.deleteAllData()

        assertTrue(dao.getAllPlanEntities().isEmpty())
        assertTrue(dao.getAllDayEntities().isEmpty())
        assertTrue(dao.getAllPlanExercises().isEmpty())
        assertTrue(dao.getAllSessionEntities().isEmpty())
        assertTrue(dao.getAllSessionExercises().isEmpty())
        assertTrue(dao.getAllSetLogs().isEmpty())
    }

    @Test
    fun replace_all_data_preserva_ids() = runTest {
        dao.insertPlan(plano(id = 1, name = "Plano Antigo"))
        dao.insertDay(dia(id = 10, planId = 1, name = "Dia Antigo"))

        dao.replaceAllData(
            plans = listOf(plano(id = 42, name = "Plano Novo", daysPerWeek = 4)),
            days = listOf(dia(id = 100, planId = 42, name = "Dia Novo")),
            planExercises = listOf(exercicioPlano(id = 200, dayId = 100, exerciseId = "EX_Novo")),
            sessions = emptyList(),
            sessionExercises = emptyList(),
            setLogs = emptyList(),
        )

        assertEquals(42L, dao.getPlan(42)?.id)
        assertEquals("Plano Novo", dao.getPlan(42)?.name)
        assertEquals(4, dao.getPlan(42)?.daysPerWeek)
        assertEquals(42L, dao.getDay(100)?.planId)
        assertEquals(listOf("EX_Novo"), dao.getExercisesForDay(100).map { it.exerciseId })
        // O payload novo substitui o anterior, não é somado a ele.
        assertEquals(listOf(42L), dao.getAllPlanEntities().map { it.id })
    }

    @Test
    fun replace_all_data_e_atomico() = runTest {
        dao.insertPlan(plano(id = 1, name = "Plano Atual"))
        dao.insertDay(dia(id = 10, planId = 1, name = "Dia Atual"))

        // O segundo plano repete a chave primária: a transação inteira tem de falhar,
        // inclusive a limpeza que roda antes de inserir.
        val falhou = runCatching {
            dao.replaceAllData(
                plans = listOf(plano(id = 99, name = "Plano Bom"), plano(id = 99, name = "Plano Duplicado")),
                days = listOf(dia(id = 200, planId = 99, name = "Dia Novo")),
                planExercises = emptyList(),
                sessions = emptyList(),
                sessionExercises = emptyList(),
                setLogs = emptyList(),
            )
        }.isFailure

        assertTrue(falhou)
        assertNull(dao.getPlan(99))
        assertNull(dao.getDay(200))
        // Dados anteriores intactos: nada do payload parcial ficou gravado.
        assertEquals(listOf(1L), dao.getAllPlanEntities().map { it.id })
        assertEquals(listOf(10L), dao.getAllDayEntities().map { it.id })
    }
}
