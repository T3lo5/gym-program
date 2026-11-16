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
import com.treinos.app.data.ProgressionApplyResult
import com.treinos.app.data.media.ExerciseMedia
import com.treinos.app.data.prefs.AppPreferences
import com.treinos.app.data.remote.BackendApi
import com.treinos.app.data.remote.GenerateJobAck
import com.treinos.app.data.remote.GenerateJobStatus
import com.treinos.app.data.remote.GeneratePlanRequest
import com.treinos.app.data.remote.GeneratePlanResponse
import com.treinos.app.ui.progression.ProgressionAction
import com.treinos.app.ui.progression.ProgressionSuggestion
import com.treinos.app.ui.programs.buildProgram
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

    // ---------- H4-4: tipos de série ----------

    /** Sessão de força em andamento com um exercício planejado; devolve o logId. */
    private suspend fun sessaoEmAndamento(): Pair<Long, Long> {
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
        return sessionId to exerciseLogId
    }

    @Test
    fun set_superset_with_previous_persiste() = runTest {
        // H4-6: alternar o superset grava a flag no plano (não só na sessão).
        val planId = dao.insertPlan(
            WorkoutPlanEntity(id = 1, name = "Plano", goal = "hypertrophy", daysPerWeek = 3, isActive = true)
        )
        val dayId = dao.insertDay(
            WorkoutDayEntity(id = 10, planId = planId, name = "Treino A", dayOrder = 0, weekday = 1, modality = "strength")
        )
        dao.insertPlanExercises(
            listOf(WorkoutPlanExerciseEntity(id = 100, dayId = dayId, exerciseId = "ex1", exerciseName = "Ex 1", sets = 3))
        )
        val planExerciseId = dao.getExercisesForDay(dayId).single().id

        repository.setSupersetWithPrevious(planExerciseId, true)
        assertEquals(true, dao.getExercisesForDay(dayId).single().isSupersetWithPrevious)

        repository.setSupersetWithPrevious(planExerciseId, false)
        assertEquals(false, dao.getExercisesForDay(dayId).single().isSupersetWithPrevious)
    }

    @Test
    fun logSet_warmup_nao_incrementa_completed_sets() = runTest {
        val (_, exerciseLogId) = sessaoEmAndamento()

        repository.logSet(exerciseLogId, reps = 10, weightKg = 40.0, rpe = null, setType = "warmup")
        repository.logSet(exerciseLogId, reps = 10, weightKg = 60.0, rpe = null)

        val sets = dao.getAllSetLogs().filter { it.exerciseLogId == exerciseLogId }
        assertEquals(2, sets.size)
        // Aquecimento: isWarmup derivado na escrita e não conta no progresso.
        assertEquals("warmup", sets.first { it.setType == "warmup" }.setType)
        assertEquals(true, sets.first { it.setType == "warmup" }.isWarmup)
        // Série de trabalho incrementa o contador.
        assertEquals(1, dao.getSessionExercise(exerciseLogId)?.completedSets)
    }

    @Test
    fun logSet_drop_e_amrap_contam() = runTest {
        val (_, exerciseLogId) = sessaoEmAndamento()

        repository.logSet(exerciseLogId, reps = 12, weightKg = 60.0, rpe = null, setType = "drop")
        repository.logSet(exerciseLogId, reps = 15, weightKg = 40.0, rpe = null, setType = "amrap", rir = 1)

        val sets = dao.getAllSetLogs().filter { it.exerciseLogId == exerciseLogId }.sortedBy { it.setNumber }
        assertEquals(listOf("drop", "amrap"), sets.map { it.setType })
        // Ambas contam como série de trabalho.
        assertEquals(false, sets.any { it.isWarmup })
        assertEquals(2, dao.getSessionExercise(exerciseLogId)?.completedSets)
        assertEquals(1, sets[1].rir)
    }

    @Test
    fun updateSet_para_warmup_recalcula_completed_sets() = runTest {
        val (_, exerciseLogId) = sessaoEmAndamento()
        repository.logSet(exerciseLogId, reps = 10, weightKg = 60.0, rpe = null)
        val primeira = dao.getAllSetLogs().first { it.exerciseLogId == exerciseLogId }
        repository.logSet(exerciseLogId, reps = 10, weightKg = 60.0, rpe = null)
        assertEquals(2, dao.getSessionExercise(exerciseLogId)?.completedSets)

        // Virar warm-up tira a série do progresso.
        repository.updateSet(primeira.copy(setType = "warmup"))
        assertEquals(1, dao.getSessionExercise(exerciseLogId)?.completedSets)
        assertEquals(true, dao.getSetLog(primeira.id)?.isWarmup)

        // Voltar a ser de trabalho devolve a série ao progresso.
        repository.updateSet(dao.getSetLog(primeira.id)!!.copy(setType = "normal"))
        assertEquals(2, dao.getSessionExercise(exerciseLogId)?.completedSets)
        assertEquals(false, dao.getSetLog(primeira.id)?.isWarmup)
    }

    @Test
    fun delete_warmup_nao_altera_completed_sets() = runTest {
        val (_, exerciseLogId) = sessaoEmAndamento()
        repository.logSet(exerciseLogId, reps = 10, weightKg = 60.0, rpe = null)
        repository.logSet(exerciseLogId, reps = 10, weightKg = 40.0, rpe = null, setType = "warmup")
        assertEquals(1, dao.getSessionExercise(exerciseLogId)?.completedSets)
        val warmup = dao.getAllSetLogs().first { it.setType == "warmup" }

        repository.deleteSet(warmup.id)

        // Excluir aquecimento não muda o contador de séries de trabalho.
        assertEquals(1, dao.getSessionExercise(exerciseLogId)?.completedSets)
        assertEquals(1, dao.getAllSetLogs().count { it.exerciseLogId == exerciseLogId })
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

    // ---------- H4-10: programas prontos ----------

    @Test
    fun aplicar_programa_salva_como_main_e_desativa_anterior() = runTest {
        val mainAntigo = repository.saveGeneratedPlan(generatedResponse("Meu treino"), kind = "main")

        val programId = repository.saveGeneratedPlan(
            requireNotNull(buildProgram("531", "2026-12-13T00:00:00Z")),
            kind = "main",
        )

        // O main anterior é desativado, não excluído.
        assertEquals(false, dao.getPlan(mainAntigo)?.isActive)
        val plan = dao.getPlan(programId)
        assertEquals(true, plan?.isActive)
        assertEquals("main", plan?.kind)
        assertEquals("local", plan?.source)
        assertEquals(false, plan?.generatedByAi)
        assertEquals("5/3/1", plan?.name)
        assertEquals("2026-12-13T00:00:00Z", plan?.nextReviewAt)
        assertEquals(4, plan?.reviewAfterWeeks)
        val days = dao.getAllDayEntities().filter { it.planId == programId }.sortedBy { it.dayOrder }
        assertEquals(4, days.size)
        assertEquals(listOf(0, 1, 2, 3), days.map { it.dayOrder })
        // Conteúdo prescrito: o principal do dia 1 é 3×5 RPE 8 com a nota 5/3/1.
        val exercicios = dao.getExercisesForDay(days.first().id).sortedBy { it.exerciseOrder }
        assertEquals("Barbell_Squat", exercicios.first().exerciseId)
        assertEquals(3, exercicios.first().sets)
        assertEquals("5", exercicios.first().targetReps)
        assertEquals(8.0, exercicios.first().targetRpe ?: 0.0, 0.001)
        assertEquals(180, exercicios.first().restSeconds)
        assertTrue(exercicios.first().notes.startsWith("Principal 5/3/1"))

        // Aplicar o mesmo programa duas vezes regenera o plano sem duplicar dias.
        val segunda = repository.saveGeneratedPlan(
            requireNotNull(buildProgram("531", "2026-12-13T00:00:00Z")),
            kind = "main",
        )
        assertEquals(false, dao.getPlan(programId)?.isActive)
        assertEquals(
            4,
            dao.getAllDayEntities().count { it.planId == segunda },
        )
    }

    @Test
    fun aplicar_programa_preserva_extras_e_historico() = runTest {
        val mainAntigo = repository.saveGeneratedPlan(generatedResponse("Meu treino"), kind = "main")
        val extraId = repository.saveGeneratedPlan(generatedResponse("Abdômen"), kind = "extra")
        val extraDayId = dao.getAllDayEntities().single { it.planId == extraId }.id
        val sessionId = repository.startSession(extraId, extraDayId)
        repository.finishSession(sessionId)

        val programId = repository.saveGeneratedPlan(
            requireNotNull(buildProgram("ppl", "2026-12-13T00:00:00Z")),
            kind = "main",
        )

        // O extra continua ativo e o histórico permanece (sessões não têm FK).
        assertEquals(true, dao.getPlan(extraId)?.isActive)
        assertEquals(false, dao.getPlan(mainAntigo)?.isActive)
        assertEquals("Abdômen", dao.getSession(sessionId)?.planName)
        // PPL: 6 dias no plano novo.
        assertEquals(6, dao.getAllDayEntities().count { it.planId == programId })
    }

    // ---------- H4-11: notas do treino e do exercício ----------

    /** Plano de força com dois exercícios (ordens 0 e 1); devolve o dayId. */
    private suspend fun planoComDoisExercicios(): Pair<Long, Long> {
        val planId = dao.insertPlan(
            WorkoutPlanEntity(id = 1, name = "Plano", goal = "hypertrophy", daysPerWeek = 3, isActive = true)
        )
        val dayId = dao.insertDay(
            WorkoutDayEntity(id = 10, planId = planId, name = "Treino A", dayOrder = 0, weekday = 1, modality = "strength")
        )
        dao.insertPlanExercises(
            listOf(
                WorkoutPlanExerciseEntity(id = 100, dayId = dayId, exerciseId = "ex1", exerciseName = "Ex 1", exerciseOrder = 0, sets = 3),
                WorkoutPlanExerciseEntity(id = 101, dayId = dayId, exerciseId = "ex2", exerciseName = "Ex 2", exerciseOrder = 1, sets = 3),
            )
        )
        return planId to dayId
    }

    @Test
    fun update_session_notes_persiste_e_faz_trim() = runTest {
        val (planId, dayId) = planoComDoisExercicios()
        val sessionId = repository.startSession(planId, dayId)

        repository.updateSessionNotes(sessionId, "  ombro esquerdo incomodou  ")

        assertEquals("ombro esquerdo incomodou", dao.getSession(sessionId)?.notes)
    }

    @Test
    fun update_session_notes_vazia_limpa() = runTest {
        val (planId, dayId) = planoComDoisExercicios()
        val sessionId = repository.startSession(planId, dayId)
        repository.updateSessionNotes(sessionId, "algo anotado")

        // Texto só de espaços normaliza para vazio e limpa a nota.
        repository.updateSessionNotes(sessionId, "   ")

        assertEquals("", dao.getSession(sessionId)?.notes)
    }

    @Test
    fun update_plan_exercise_notes_casa_por_ordem() = runTest {
        val (_, dayId) = planoComDoisExercicios()

        repository.updatePlanExerciseNotes(dayId, 1, "puxar a barra até o peito")

        val exercicios = dao.getExercisesForDay(dayId).sortedBy { it.exerciseOrder }
        // Casou pelo `exerciseOrder`: só o slot 1 recebeu a nota.
        assertEquals("puxar a barra até o peito", exercicios[1].notes)
        assertEquals("", exercicios[0].notes)
    }

    @Test
    fun update_plan_exercise_notes_sem_dia_falha_sem_gravar() = runTest {
        val (_, dayId) = planoComDoisExercicios()

        val falhou = runCatching {
            repository.updatePlanExerciseNotes(dayId = 999L, exerciseOrder = 0, notes = "x")
        }.isFailure

        assertTrue(falhou)
        // Nada foi gravado: os dois slots continuam sem nota.
        assertTrue(dao.getExercisesForDay(dayId).all { it.notes.isEmpty() })
    }

    @Test
    fun update_plan_exercise_notes_apos_troca_this_session_usa_ordem() = runTest {
        // H3-41: a troca THIS_SESSION muda o exercício da sessão, não o plano;
        // a nota vai para o slot do plano de mesmo `exerciseOrder` e vale para
        // as próximas sessões do dia.
        val (_, dayId) = planoComDoisExercicios()
        val sessionId = repository.startSession(1L, dayId)
        val substituido = dao.getAllSessionExercises().single {
            it.sessionId == sessionId && it.exerciseOrder == 0
        }
        val catalogo = mapOf(
            "ex1" to ExerciseMedia(
                id = "ex1", name = "Ex 1", primaryMuscles = listOf("chest"),
                equipment = "barbell", instructions = emptyList(), imageUrl = null,
            ),
            "ex_sub" to ExerciseMedia(
                id = "ex_sub", name = "Substituto", primaryMuscles = listOf("chest"),
                equipment = "dumbbell", instructions = emptyList(), imageUrl = null,
            ),
        )

        val troca = repository.swapSessionExercise(
            sessionExerciseId = substituido.id,
            replacementExerciseId = "ex_sub",
            scope = SwapScope.THIS_SESSION,
            catalog = catalogo,
            availableEquipment = listOf("barbell", "dumbbell"),
        )
        assertTrue(troca is SwapResult.Ok)
        repository.updatePlanExerciseNotes(dayId, 0, "nota do slot do plano")

        val plano = dao.getExercisesForDay(dayId).sortedBy { it.exerciseOrder }
        // O plano não mudou de exercício; a nota ficou no slot de ordem 0.
        assertEquals("ex1", plano[0].exerciseId)
        assertEquals("nota do slot do plano", plano[0].notes)
        assertEquals("", plano[1].notes)
        // A sessão continua com o substituto, na mesma ordem.
        assertEquals("ex_sub", dao.getSessionExercise(substituido.id)?.exerciseId)
        assertEquals(0, dao.getSessionExercise(substituido.id)?.exerciseOrder)
    }

    // ---------- H4-12: aplicar sugestão de progressão ao plano ----------

    private fun sugestao(
        action: ProgressionAction,
        suggestedWeightKg: Double? = null,
        suggestedReps: Int? = null,
    ) = ProgressionSuggestion(
        action = action,
        suggestedWeightKg = suggestedWeightKg,
        suggestedReps = suggestedReps,
        reason = "Duas sessões no topo da faixa com RPE confortável.",
    )

    @Test
    fun aplicar_progressao_grava_target_weight_por_ordem() = runTest {
        val (_, dayId) = planoComDoisExercicios()

        val resultado = repository.applyProgressionToPlanExercise(
            dayId = dayId,
            exerciseOrder = 1,
            exerciseId = "ex2",
            suggestion = sugestao(ProgressionAction.INCREASE, suggestedWeightKg = 42.5),
        )

        assertTrue(resultado is ProgressionApplyResult.Applied)
        val aplicado = resultado as ProgressionApplyResult.Applied
        assertEquals(42.5, aplicado.targetWeightKg ?: 0.0, 0.001)
        val exercicios = dao.getExercisesForDay(dayId).sortedBy { it.exerciseOrder }
        // Casou pelo par ordem+exercício: só o slot 1 recebeu o alvo.
        assertEquals(42.5, exercicios[1].targetWeightKg ?: 0.0, 0.001)
        assertNull(exercicios[0].targetWeightKg)
        // O restante do slot não mudou.
        assertEquals("ex2", exercicios[1].exerciseId)
        assertEquals(3, exercicios[1].sets)
    }

    @Test
    fun aplicar_progressao_nao_casa_exercicio_trocado() = runTest {
        // H3-41: a troca THIS_SESSION muda o exercício da sessão, não o plano;
        // o `exerciseId` da sessão difere do plano na mesma ordem → nada gravado.
        val (_, dayId) = planoComDoisExercicios()

        val resultado = repository.applyProgressionToPlanExercise(
            dayId = dayId,
            exerciseOrder = 0,
            exerciseId = "ex_sub",
            suggestion = sugestao(ProgressionAction.INCREASE, suggestedWeightKg = 42.5),
        )

        assertTrue(resultado is ProgressionApplyResult.ExerciseNotInPlan)
        assertTrue(dao.getExercisesForDay(dayId).all { it.targetWeightKg == null })
    }

    @Test
    fun aplicar_progressao_sem_sugestao_nao_grava() = runTest {
        val (_, dayId) = planoComDoisExercicios()

        val resultado = repository.applyProgressionToPlanExercise(
            dayId = dayId,
            exerciseOrder = 0,
            exerciseId = "ex1",
            suggestion = sugestao(ProgressionAction.NONE),
        )

        assertTrue(resultado is ProgressionApplyResult.NothingToApply)
        assertTrue(dao.getExercisesForDay(dayId).all { it.targetWeightKg == null })
    }

    @Test
    fun aplicar_progressao_sem_dia_falha_sem_gravar() = runTest {
        // Plano `extra` excluído durante a sessão: o dia some → não casa e
        // nada é gravado.
        val (_, dayId) = planoComDoisExercicios()

        val resultado = repository.applyProgressionToPlanExercise(
            dayId = 999L,
            exerciseOrder = 0,
            exerciseId = "ex1",
            suggestion = sugestao(ProgressionAction.INCREASE, suggestedWeightKg = 42.5),
        )

        assertTrue(resultado is ProgressionApplyResult.ExerciseNotInPlan)
        assertTrue(dao.getExercisesForDay(dayId).all { it.targetWeightKg == null })
    }
}
