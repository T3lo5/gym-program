package com.treinos.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.treinos.app.data.local.AppDatabase
import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutDayEntity
import com.treinos.app.data.local.WorkoutPlanEntity
import com.treinos.app.data.local.WorkoutPlanExerciseEntity
import com.treinos.app.data.local.WorkoutSessionEntity
import com.treinos.app.data.prefs.AppPreferences
import com.treinos.app.data.remote.BackendApi
import com.treinos.app.data.remote.GeneratePlanRequest
import com.treinos.app.data.remote.GeneratePlanResponse
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class FakeBackendApi : BackendApi {
    override suspend fun health() = throw UnsupportedOperationException()
    override suspend fun generatePlan(request: GeneratePlanRequest): GeneratePlanResponse =
        throw UnsupportedOperationException()
    override suspend fun sync(request: com.treinos.app.data.remote.SyncRequestDto) =
        throw UnsupportedOperationException()
    override suspend fun syncDownload(deviceId: String) =
        throw UnsupportedOperationException()
    override suspend fun catalog() = throw UnsupportedOperationException()
    override suspend fun reviewPlan(request: com.treinos.app.data.remote.ReviewPlanRequest) =
        throw UnsupportedOperationException()
}

@RunWith(RobolectricTestRunner::class)
class WorkoutRepositorySessionTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: com.treinos.app.data.local.WorkoutDao
    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.workoutDao()
        val preferences = AppPreferences(context)
        preferences.deviceId = "device"
        repository = WorkoutRepository(dao, FakeBackendApi(), preferences)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun start_session_copia_modalidade_do_dia() = runTest {
        val planId = dao.insertPlan(
            WorkoutPlanEntity(id = 1, name = "Plano", goal = "general", daysPerWeek = 3, isActive = true)
        )
        val dayId = dao.insertDay(
            WorkoutDayEntity(id = 10, planId = planId, name = "Corrida A", dayOrder = 0, weekday = 1, modality = "run")
        )

        val sessionId = repository.startSession(planId, dayId)

        val session = dao.getSession(sessionId)
        assertNotNull(session)
        assertEquals("run", session?.modality)
        assertEquals("Corrida A", session?.title)
    }

    @Test
    fun start_session_copia_modalidade_strength() = runTest {
        val planId = dao.insertPlan(
            WorkoutPlanEntity(id = 1, name = "Plano", goal = "hypertrophy", daysPerWeek = 3, isActive = true)
        )
        val dayId = dao.insertDay(
            WorkoutDayEntity(id = 10, planId = planId, name = "Treino A", dayOrder = 0, weekday = 1, modality = "strength")
        )
        dao.insertPlanExercises(
            listOf(WorkoutPlanExerciseEntity(id = 100, dayId = dayId, exerciseId = "ex1", exerciseName = "Ex 1", sets = 3))
        )

        val sessionId = repository.startSession(planId, dayId)

        val session = dao.getSession(sessionId)
        assertNotNull(session)
        assertEquals("strength", session?.modality)
        assertEquals("Treino A", session?.title)
    }

    @Test
    fun finish_run_session_grava_duracao_distancia_e_rpe() = runTest {
        val planId = dao.insertPlan(
            WorkoutPlanEntity(id = 1, name = "Plano", goal = "general", daysPerWeek = 3, isActive = true)
        )
        val dayId = dao.insertDay(
            WorkoutDayEntity(id = 10, planId = planId, name = "Corrida A", dayOrder = 0, weekday = 1, modality = "run")
        )
        val sessionId = repository.startSession(planId, dayId)

        repository.finishRunSession(sessionId, durationSeconds = 1800L, distanceMeters = 5000.0, rpe = 8.0)

        val session = dao.getSession(sessionId)
        assertNotNull(session)
        assertEquals("completed", session?.status)
        assertEquals(1800L, session?.totalDurationSeconds ?: 0L)
        assertEquals(5000.0, session?.totalDistanceMeters ?: 0.0, 0.001)
        assertEquals(8.0, session?.sessionRpe ?: 0.0, 0.001)
    }

    @Test
    fun log_planned_mobility_nao_cria_series() = runTest {
        val planId = dao.insertPlan(
            WorkoutPlanEntity(id = 1, name = "Plano", goal = "general", daysPerWeek = 3, isActive = true)
        )
        val dayId = dao.insertDay(
            WorkoutDayEntity(id = 10, planId = planId, name = "Mobilidade", dayOrder = 0, weekday = 1, modality = "mobility")
        )

        val newSessionId = repository.logPlannedMobility(planId, dayId, "back_10", 600L)

        val session = dao.getSession(newSessionId)
        assertNotNull(session)
        assertEquals("mobility", session?.modality)
        assertEquals("plan", session?.source)
        assertEquals("back_10", session?.title)
        assertEquals(600L, session?.totalDurationSeconds)
        assertEquals(planId, session?.planId)
        assertEquals(dayId, session?.dayId)
        assertEquals("completed", session?.status)
        assertTrue(dao.getAllSessionExercises().isEmpty())
    }

    @Test
    fun mobilidade_planejada_fecha_sessao_unica() = runTest {
        // H3-27: finalizar a mobilidade planejada fecha a sessão in_progress
        // criada por startSession — não existe segunda sessão (fantasma).
        val planId = dao.insertPlan(
            WorkoutPlanEntity(id = 1, name = "Plano", goal = "general", daysPerWeek = 3, isActive = true)
        )
        val dayId = dao.insertDay(
            WorkoutDayEntity(id = 10, planId = planId, name = "Mobilidade", dayOrder = 0, weekday = 1, modality = "mobility")
        )
        val sessionId = repository.startSession(planId, dayId)

        repository.finishPlannedMobilitySession(sessionId, "back_10", 600L)

        val sessions = dao.getAllSessionEntities()
        assertEquals(1, sessions.size)
        val session = sessions.single()
        assertEquals(sessionId, session.id)
        assertEquals("completed", session.status)
        assertNotNull(session.endedAt)
        assertEquals(600L, session.totalDurationSeconds)
        assertEquals("back_10", session.title)
        assertEquals("mobility", session.modality)
        assertEquals("plan", session.source)
    }

    @Test
    fun abandonar_remove_sessao() = runTest {
        // H3-27: abandonar apaga a sessão, os exercícios e as séries dela;
        // as demais sessões do histórico ficam intactas.
        val planId = dao.insertPlan(
            WorkoutPlanEntity(id = 1, name = "Plano", goal = "hypertrophy", daysPerWeek = 3, isActive = true)
        )
        val dayId = dao.insertDay(
            WorkoutDayEntity(id = 10, planId = planId, name = "Treino A", dayOrder = 0, weekday = 1, modality = "strength")
        )
        dao.insertPlanExercises(
            listOf(WorkoutPlanExerciseEntity(id = 100, dayId = dayId, exerciseId = "ex1", exerciseName = "Ex 1", sets = 3))
        )
        val sessionId = repository.startSession(planId, dayId)
        val exerciseLogId = dao.getAllSessionExercises().single { it.sessionId == sessionId }.id
        repository.logSet(exerciseLogId, reps = 10, weightKg = 60.0, rpe = null)
        val outraSessao = repository.startSession(planId, dayId)

        repository.deleteSession(sessionId)

        assertNull(dao.getSession(sessionId))
        assertTrue(dao.getAllSessionExercises().none { it.sessionId == sessionId })
        assertTrue(dao.getAllSetLogs().none { it.exerciseLogId == exerciseLogId })
        // A outra sessão não foi tocada: sobrou 1 de 2.
        assertNotNull(dao.getSession(outraSessao))
        assertEquals(1, dao.getAllSessionEntities().size)
    }

    private fun generatedResponse(name: String) = GeneratePlanResponse(
        workout = com.treinos.app.data.remote.WorkoutPlanDto(
            name = name,
            goal = "hypertrophy",
            daysPerWeek = 3,
            days = listOf(com.treinos.app.data.remote.WorkoutDayDto(name = "A", dayOrder = 0)),
        ),
        nextReviewAt = "2026-12-01",
    )

    @Test
    fun gerar_extra_nao_toca_no_main() = runTest {
        val mainId = repository.saveGeneratedPlan(generatedResponse("Principal"), kind = "main")

        val extraId = repository.saveGeneratedPlan(generatedResponse("Abdômen"), kind = "extra")

        assertEquals(true, dao.getPlan(mainId)?.isActive)
        assertEquals("main", dao.getPlan(mainId)?.kind)
        assertEquals(true, dao.getPlan(extraId)?.isActive)
        assertEquals("extra", dao.getPlan(extraId)?.kind)
        assertEquals(2, dao.getAllPlanEntities().size)
    }

    @Test
    fun gerar_main_mantem_extras() = runTest {
        val mainAntigo = repository.saveGeneratedPlan(generatedResponse("Principal antigo"), kind = "main")
        val extraId = repository.saveGeneratedPlan(generatedResponse("Abdômen"), kind = "extra")

        val mainNovo = repository.saveGeneratedPlan(generatedResponse("Principal novo"), kind = "main")

        assertEquals(false, dao.getPlan(mainAntigo)?.isActive)
        assertEquals(true, dao.getPlan(mainNovo)?.isActive)
        assertEquals(true, dao.getPlan(extraId)?.isActive)
    }

    @Test
    fun excluir_plano_main_e_rejeitado() = runTest {
        val mainId = repository.saveGeneratedPlan(generatedResponse("Principal"), kind = "main")

        val falhou = runCatching { repository.deleteExtraPlan(mainId) }.isFailure

        assertTrue(falhou)
        assertNotNull(dao.getPlan(mainId))
    }

    @Test
    fun excluir_extra_preserva_historico() = runTest {
        val extraId = repository.saveGeneratedPlan(generatedResponse("Abdômen"), kind = "extra")
        val dayId = dao.getAllDayEntities().single { it.planId == extraId }.id
        val sessionId = repository.startSession(extraId, dayId)
        repository.finishSession(sessionId)

        repository.deleteExtraPlan(extraId)

        assertNull(dao.getPlan(extraId))
        assertEquals("Abdômen", dao.getSession(sessionId)?.planName)
    }
}
