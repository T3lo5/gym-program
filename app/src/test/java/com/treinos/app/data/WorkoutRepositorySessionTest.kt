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
import com.treinos.app.data.remote.GenerateJobAck
import com.treinos.app.data.remote.GenerateJobStatus
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
    override suspend fun submitGeneration(request: GeneratePlanRequest): GenerateJobAck =
        throw UnsupportedOperationException()
    override suspend fun generationStatus(jobId: String): GenerateJobStatus =
        throw UnsupportedOperationException()
    override suspend fun cancelGeneration(jobId: String): GenerateJobStatus =
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

    /** H3-54: sessão concluída com séries gravadas; devolve ids dos exercícios. */
    private suspend fun criarSessaoConcluida(
        exercicios: List<Triple<String, String, Int>>,
    ): Pair<Long, Map<String, Long>> {
        val planId = dao.insertPlan(
            WorkoutPlanEntity(id = 1, name = "Plano", goal = "hypertrophy", daysPerWeek = 3, isActive = true)
        )
        val dayId = dao.insertDay(
            WorkoutDayEntity(id = 10, planId = planId, name = "Treino A", dayOrder = 0, weekday = 1, modality = "strength")
        )
        dao.insertPlanExercises(
            exercicios.mapIndexed { index, (exerciseId, nome, sets) ->
                WorkoutPlanExerciseEntity(
                    id = 100L + index,
                    dayId = dayId,
                    exerciseId = exerciseId,
                    exerciseName = nome,
                    sets = sets,
                )
            }
        )
        val sessionId = repository.startSession(planId, dayId)
        val logIds = dao.getAllSessionExercises()
            .filter { it.sessionId == sessionId }
            .associate { it.exerciseId to it.id }
        logIds.forEach { (exerciseId, logId) ->
            val sets = exercicios.first { it.first == exerciseId }.third
            repeat(sets) { index ->
                repository.logSet(logId, reps = 10, weightKg = 40.0 + index, rpe = null)
            }
        }
        repository.finishSession(sessionId)
        return sessionId to logIds
    }

    @Test
    fun update_set_em_sessao_concluida_persiste() = runTest {
        val (sessionId, logIds) = criarSessaoConcluida(listOf(Triple("ex1", "Supino", 2)))
        val exerciseLogId = logIds.getValue("ex1")
        val antes = dao.getSession(sessionId)
        val set = dao.getAllSetLogs().first { it.exerciseLogId == exerciseLogId }

        repository.updateHistorySet(set.copy(reps = 8, weightKg = 42.5, rpe = 9.0))

        val depois = dao.getSetLog(set.id)
        assertEquals(8, depois?.reps)
        assertEquals(42.5, depois?.weightKg ?: 0.0, 0.001)
        assertEquals(9.0, depois?.rpe ?: 0.0, 0.001)
        // Vínculo com o exercício e número da série preservados (H3-27).
        assertEquals(set.exerciseLogId, depois?.exerciseLogId)
        assertEquals(set.setNumber, depois?.setNumber)
        // startedAt/endedAt/duração não mudam na edição do Histórico.
        val sessaoDepois = dao.getSession(sessionId)
        assertEquals(antes?.startedAt, sessaoDepois?.startedAt)
        assertEquals(antes?.endedAt, sessaoDepois?.endedAt)
        assertEquals(antes?.totalDurationSeconds, sessaoDepois?.totalDurationSeconds)
    }

    @Test
    fun delete_set_realinia_completed_sets() = runTest {
        val (sessionId, logIds) = criarSessaoConcluida(listOf(Triple("ex1", "Supino", 3)))
        val exerciseLogId = logIds.getValue("ex1")
        val sets = dao.getAllSetLogs()
            .filter { it.exerciseLogId == exerciseLogId }
            .sortedBy { it.setNumber }
        assertEquals(3, dao.getSessionExercise(exerciseLogId)?.completedSets)

        repository.deleteHistorySet(sets[1].id)

        assertEquals(2, dao.getAllSetLogs().count { it.exerciseLogId == exerciseLogId })
        assertEquals(2, dao.getSessionExercise(exerciseLogId)?.completedSets)
        assertEquals(2, dao.completedSetsForSession(sessionId))
        // O exercício ainda tem séries: a entrada permanece na sessão.
        assertNotNull(dao.getSessionExercise(exerciseLogId))
    }

    @Test
    fun caminho_historico_rejeita_sessao_em_andamento() = runTest {
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
        val set = dao.getAllSetLogs().single { it.exerciseLogId == exerciseLogId }

        // Sessão em andamento não é editável pelo caminho do Histórico (H3-54).
        val updateFalhou = runCatching {
            repository.updateHistorySet(set.copy(reps = 5, weightKg = 20.0))
        }.isFailure
        val deleteFalhou = runCatching { repository.deleteHistorySet(set.id) }.isFailure

        assertTrue(updateFalhou)
        assertTrue(deleteFalhou)
        // Nada mudou: a edição em andamento continua pela tela de treino.
        assertEquals(10, dao.getSetLog(set.id)?.reps)
        assertEquals(60.0, dao.getSetLog(set.id)?.weightKg ?: 0.0, 0.001)
        assertEquals(1, dao.getAllSetLogs().count { it.exerciseLogId == exerciseLogId })
    }

    @Test
    fun excluir_ultima_serie_vazia_exercicio() = runTest {
        val (sessionId, logIds) = criarSessaoConcluida(
            listOf(Triple("ex1", "Supino", 2), Triple("ex2", "Remada", 1))
        )
        val ex1 = logIds.getValue("ex1")
        val ex2 = logIds.getValue("ex2")
        val ultimaEx2 = dao.getAllSetLogs().filter { it.exerciseLogId == ex2 }.single()

        repository.deleteHistorySet(ultimaEx2.id)

        // Exercício sem séries sai da sessão; o outro permanece intacto.
        assertNull(dao.getSessionExercise(ex2))
        assertEquals(2, dao.getAllSetLogs().count { it.exerciseLogId == ex1 })
        assertNotNull(dao.getSessionExercise(ex1))
        // Esvaziar o último exercício deixa a sessão sem séries — permitido.
        dao.getAllSetLogs().filter { it.exerciseLogId == ex1 }.forEach { set ->
            repository.deleteHistorySet(set.id)
        }
        assertNull(dao.getSessionExercise(ex1))
        assertTrue(dao.getAllSessionExercises().none { it.sessionId == sessionId })
        assertNotNull(dao.getSession(sessionId))
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
