package com.treinos.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
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
    fun multiplos_planos_ativos() = runTest {
        dao.insertPlan(plano(id = 1, name = "Principal"))
        dao.insertPlan(plano(id = 2, name = "Extra").copy(kind = "extra", updatedAt = System.currentTimeMillis() + 10_000))

        val ativos = dao.observeActivePlanEntities().first()

        // Main primeiro, mesmo com o extra mais recente.
        assertEquals(listOf(1L, 2L), ativos.map { it.id })
        assertEquals(listOf("main", "extra"), ativos.map { it.kind })
    }

    @Test
    fun replace_desativa_so_mesmo_kind() = runTest {
        dao.insertPlan(plano(id = 1, name = "Main antigo"))
        dao.insertPlan(plano(id = 2, name = "Extra").copy(kind = "extra"))

        dao.replaceActivePlan(
            plan = plano(id = 3, name = "Main novo"),
            days = listOf(dia(id = 30, planId = 0, name = "A")),
            exercisesByDay = listOf(emptyList()),
        )

        assertEquals(false, dao.getPlan(1)?.isActive)
        assertEquals(true, dao.getPlan(2)?.isActive)
        assertEquals(true, dao.getPlan(3)?.isActive)
        assertEquals(listOf(3L, 2L), dao.observeActivePlanEntities().first().map { it.id })
    }

    @Test
    fun insert_extra_nao_desativa() = runTest {
        dao.insertPlan(plano(id = 1, name = "Principal"))

        val extraId = dao.insertPlanWithDays(
            plan = plano(id = 0, name = "Abdômen").copy(kind = "extra"),
            days = listOf(dia(id = 0, planId = 0, name = "Core")),
            exercisesByDay = listOf(listOf(exercicioPlano(id = 0, dayId = 0, exerciseId = "Crunch"))),
        )

        assertEquals(true, dao.getPlan(1)?.isActive)
        assertEquals(true, dao.getPlan(extraId)?.isActive)
        assertEquals("extra", dao.getPlan(extraId)?.kind)
        assertEquals(listOf("Crunch"), dao.getExercisesForDay(dao.getAllDayEntities().last().id).map { it.exerciseId })
    }

    @Test
    fun delete_extra_preserva_sessoes() = runTest {
        dao.insertPlan(plano(id = 1, name = "Principal"))
        dao.insertPlan(plano(id = 2, name = "Extra").copy(kind = "extra"))
        dao.insertDay(dia(id = 20, planId = 2, name = "Core"))
        dao.insertPlanExercises(listOf(exercicioPlano(id = 200, dayId = 20, exerciseId = "Crunch")))
        dao.insertSession(
            WorkoutSessionEntity(id = 50, planId = 2, dayId = 20, planName = "Extra", dayName = "Core", status = "completed")
        )
        dao.insertSessionExercises(
            listOf(SessionExerciseEntity(id = 500, sessionId = 50, exerciseId = "Crunch", plannedSets = 3))
        )
        dao.insertSetLog(SetLogEntity(id = 5000, exerciseLogId = 500, setNumber = 1, reps = 15))

        dao.deletePlan(2)

        assertNull(dao.getPlan(2))
        assertNull(dao.getDay(20))
        assertTrue(dao.getExercisesForDay(20).isEmpty())
        assertEquals(true, dao.getPlan(1)?.isActive)
        // Histórico sem FK: sessão, exercício e série continuam com o nome do plano.
        assertEquals("Extra", dao.getSession(50)?.planName)
        assertEquals(1, dao.getAllSessionExercises().size)
        assertEquals(1, dao.getAllSetLogs().size)
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
    fun exercicio_do_plano_persiste_instrucoes_e_imagens() = runTest {
        // H3-24: mídia embutida no plano sobrevive ao reinício do app.
        val planId = dao.insertPlan(plano())
        val dayId = dao.insertDay(dia(planId = planId))
        dao.insertPlanExercises(
            listOf(
                exercicioPlano(dayId = dayId).copy(
                    instructions = "Deite no banco.\nSegure a barra.",
                    images = "Barbell_Bench_Press/0.jpg,Barbell_Bench_Press/1.jpg",
                ),
            ),
        )

        val salvo = dao.getExercisesForDay(dayId).single()
        assertEquals("Deite no banco.\nSegure a barra.", salvo.instructions)
        assertEquals("Barbell_Bench_Press/0.jpg,Barbell_Bench_Press/1.jpg", salvo.images)

        // Round-trip: os separadores voltam exatamente como entraram.
        assertEquals(
            listOf("Deite no banco.", "Segure a barra."),
            salvo.instructions.split('\n'),
        )
        assertEquals(
            listOf("Barbell_Bench_Press/0.jpg", "Barbell_Bench_Press/1.jpg"),
            salvo.images.split(','),
        )

        // Exercício antigo (antes da migração) lê vazio, sem crash.
        dao.insertPlanExercises(
            listOf(exercicioPlano(id = 0, dayId = dayId, exerciseId = "Antigo")),
        )
        val antigo = dao.getExercisesForDay(dayId).last()
        assertEquals("", antigo.instructions)
        assertEquals("", antigo.images)
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
