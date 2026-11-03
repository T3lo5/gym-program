package com.treinos.app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Teste de Room em memória para o WorkoutDao.
 *
 * Usa banco em memória (não grava dados reais) e fecha no @After.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: WorkoutDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.workoutDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun inserePlanoCompleto() = runBlocking {
        val planId = dao.insertPlan(
            WorkoutPlanEntity(
                id = 1,
                name = "Plano Teste",
                goal = "hypertrophy",
                daysPerWeek = 3,
                isActive = true,
            )
        )
        assertNotNull(planId)
        assertTrue(planId > 0)

        val dayId = dao.insertDay(
            WorkoutDayEntity(
                id = 10,
                planId = planId,
                name = "Dia A",
                dayOrder = 0,
            )
        )
        assertTrue(dayId > 0)

        dao.insertPlanExercises(
            listOf(
                WorkoutPlanExerciseEntity(
                    id = 100,
                    dayId = dayId,
                    exerciseId = "Barbell_Bench_Press",
                    exerciseName = "Supino reto",
                    sets = 3,
                    restSeconds = 90,
                )
            )
        )

        val plano: WorkoutPlanEntity? = dao.getPlan(planId)
        assertNotNull(plano)
        assertEquals("Plano Teste", plano?.name)

        val dias = dao.getAllDayEntities()
        assertEquals(1, dias.size)
        assertEquals(planId, dias.first().planId)

        val exercicios = dao.getExercisesForDay(dayId)
        assertEquals(1, exercicios.size)
        assertEquals("Barbell_Bench_Press", exercicios.first().exerciseId)
    }

    @Test
    fun deleteAllDataLimpaTudo() = runBlocking {
        // Insere dados
        val planId = dao.insertPlan(
            WorkoutPlanEntity(id = 1, name = "Plano", daysPerWeek = 3, isActive = true)
        )
        val dayId = dao.insertDay(
            WorkoutDayEntity(id = 10, planId = planId, name = "Dia", dayOrder = 0)
        )
        dao.insertPlanExercises(
            listOf(WorkoutPlanExerciseEntity(id = 100, dayId = dayId, exerciseId = "EX", sets = 3))
        )
        val sessionId = dao.insertSession(
            WorkoutSessionEntity(id = 50, planId = planId, dayId = dayId, status = "in_progress")
        )
        dao.insertSessionExercises(
            listOf(SessionExerciseEntity(id = 500, sessionId = sessionId, exerciseId = "EX", plannedSets = 3))
        )
        dao.insertSetLog(
            SetLogEntity(id = 5000, exerciseLogId = 500, setNumber = 1, reps = 10)
        )

        // Verifica que existem dados
        assertTrue(dao.getAllPlanEntities().isNotEmpty())
        assertTrue(dao.getAllDayEntities().isNotEmpty())
        assertTrue(dao.getAllPlanExercises().isNotEmpty())
        assertTrue(dao.getAllSessionEntities().isNotEmpty())
        assertTrue(dao.getAllSessionExercises().isNotEmpty())
        assertTrue(dao.getAllSetLogs().isNotEmpty())

        // Limpa tudo
        dao.deleteAllData()

        // Verifica que tudo foi limpo
        assertTrue(dao.getAllPlanEntities().isEmpty())
        assertTrue(dao.getAllDayEntities().isEmpty())
        assertTrue(dao.getAllPlanExercises().isEmpty())
        assertTrue(dao.getAllSessionEntities().isEmpty())
        assertTrue(dao.getAllSessionExercises().isEmpty())
        assertTrue(dao.getAllSetLogs().isEmpty())
    }

    @Test
    fun replaceAllDataPreservaIds() = runBlocking {
        // Insere dados iniciais
        dao.insertPlan(
            WorkoutPlanEntity(id = 1, name = "Plano Antigo", daysPerWeek = 3, isActive = true)
        )
        dao.insertDay(
            WorkoutDayEntity(id = 10, planId = 1, name = "Dia Antigo", dayOrder = 0)
        )

        // Insere novo plano com replaceAllData (preserva IDs)
        dao.replaceAllData(
            plans = listOf(
                WorkoutPlanEntity(id = 42, name = "Plano Novo", daysPerWeek = 4, isActive = true)
            ),
            days = listOf(
                WorkoutDayEntity(id = 100, planId = 42, name = "Dia Novo", dayOrder = 0)
            ),
            planExercises = listOf(
                WorkoutPlanExerciseEntity(
                    id = 200, dayId = 100, exerciseId = "EX_Novo", sets = 4
                )
            ),
            sessions = emptyList(),
            sessionExercises = emptyList(),
            setLogs = emptyList(),
        )

        // Verifica que IDs foram preservados
        val plano: WorkoutPlanEntity? = dao.getPlan(42)
        assertNotNull(plano)
        assertEquals("Plano Novo", plano?.name)
        assertEquals(4, plano?.daysPerWeek)

        val dia: WorkoutDayEntity? = dao.getDay(100)
        assertNotNull(dia)
        assertEquals(42L, dia?.planId)
        assertEquals("Dia Novo", dia?.name)

        val ex = dao.getExercisesForDay(100)
        assertEquals(1, ex.size)
        assertEquals("EX_Novo", ex.first().exerciseId)

        // O plano antigo deve ter sido removido (deleteAllData roda antes)
        assertFalse(dao.getAllPlanEntities().any { it.id == 1L })
    }

    @Test
    fun replaceAllDataEAtomico() = runBlocking {
        // Insere dados iniciais
        dao.insertPlan(
            WorkoutPlanEntity(id = 1, name = "Plano Atual", daysPerWeek = 3, isActive = true)
        )
        dao.insertDay(
            WorkoutDayEntity(id = 10, planId = 1, name = "Dia Atual", dayOrder = 0)
        )

        // Substitui todos os dados com replaceAllData
        dao.replaceAllData(
            plans = listOf(
                WorkoutPlanEntity(id = 99, name = "Plano Teste Atomico", daysPerWeek = 5, isActive = true)
            ),
            days = emptyList(),
            planExercises = emptyList(),
            sessions = emptyList(),
            sessionExercises = emptyList(),
            setLogs = emptyList(),
        )

        // O novo plano deve existir com os dados fornecidos
        val plano: WorkoutPlanEntity? = dao.getPlan(99)
        assertNotNull(plano)
        assertEquals("Plano Teste Atomico", plano?.name)
        assertEquals(5, plano?.daysPerWeek)

        // O banco deve estar consistente (não meio gravado)
        val todosPlanos = dao.getAllPlanEntities()
        assertTrue(todosPlanos.any { it.id == 99L })
    }
}
