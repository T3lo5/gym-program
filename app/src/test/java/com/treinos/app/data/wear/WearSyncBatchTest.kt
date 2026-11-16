package com.treinos.app.data.wear

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.treinos.app.data.WorkoutRepository
import com.treinos.app.data.local.AppDatabase
import com.treinos.app.data.local.WorkoutDayEntity
import com.treinos.app.data.local.WorkoutPlanEntity
import com.treinos.app.data.local.WorkoutPlanExerciseEntity
import com.treinos.app.data.prefs.AppPreferences
import com.treinos.app.ui.AppViewModel
import com.treinos.app.ui.FakeBackendApiMain
import com.treinos.protocol.WearLoggedSet
import com.treinos.protocol.WearSyncBatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * H4-16: dedupe e aplicação do lote de séries do relógio. Robolectric + Room
 * em memória, dispatcher de teste como Main para o `viewModelScope` rodar de
 * forma determinística (mesmo padrão de `StartTodayWorkoutTest`).
 */
@RunWith(RobolectricTestRunner::class)
class WearSyncBatchTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: WorkoutRepository
    private lateinit var preferences: AppPreferences
    private lateinit var wearRepo: WearSyncRepository
    private lateinit var viewModel: AppViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = AppPreferences(context)
        preferences.deviceId = "device"
        repository = WorkoutRepository(database.workoutDao(), FakeBackendApiMain(), preferences)
        wearRepo = WearSyncRepository(context, preferences)
        Dispatchers.setMain(testDispatcher)
        viewModel = AppViewModel.factory(
            context = context,
            repository = repository,
            preferences = preferences,
            wearSyncRepository = wearRepo,
        ).create(AppViewModel::class.java)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    /**
     * Plano principal com um dia e um exercício; inicia a sessão e devolve
     * (sessionId, sessionExerciseId) do exercício do dia.
     */
    private suspend fun iniciarSessao(): Pair<Long, Long> {
        val dao = database.workoutDao()
        val planId = dao.insertPlanWithDays(
            plan = WorkoutPlanEntity(name = "Principal", kind = "main"),
            days = listOf(
                WorkoutDayEntity(
                    planId = 0,
                    name = "Dia Teste",
                    weekday = null,
                    isRestDay = false,
                ),
            ),
            exercisesByDay = listOf(
                listOf(
                    WorkoutPlanExerciseEntity(
                        dayId = 0,
                        exerciseId = "squat",
                        exerciseName = "Agachamento",
                        sets = 3,
                    ),
                ),
            ),
        )
        val dayId = dao.observeAllDays().first().first { it.planId == planId }.id
        val sessionId = repository.startSession(planId = planId, dayId = dayId)
        val sessao = repository.observeSessions()
            .first { list -> list.any { it.session.id == sessionId } }
            .first { it.session.id == sessionId }
        return sessionId to sessao.exercises.first().exercise.id
    }

    private fun lote(
        sessionId: Long,
        sessionExerciseId: Long,
        finished: Boolean = false,
        protocolVersion: Int = 1,
        clientSetId: String = "set-1",
    ): WearSyncBatch =
        WearSyncBatch(
            protocolVersion = protocolVersion,
            sessionId = sessionId,
            batchId = "batch-1",
            finished = finished,
            sets = listOf(
                WearLoggedSet(
                    clientSetId = clientSetId,
                    sessionExerciseId = sessionExerciseId,
                    exerciseIndex = 0,
                    setNumber = 1,
                    reps = 10,
                    weightKg = 60.0,
                    loggedAt = 1_700_000_000_000,
                ),
            ),
        )

    @Test
    fun aplica_lote_uma_vez() {
        val (sessionId, sessionExerciseId) = runBlocking { iniciarSessao() }

        wearRepo.onSyncBatchReceived(lote(sessionId, sessionExerciseId))

        // A série do relógio aparece no Room exatamente uma vez.
        val aplicado = runBlocking {
            withTimeoutOrNull(5_000) {
                repository.observeSessions().first { list ->
                    list.firstOrNull()?.exercises?.firstOrNull()?.sets?.isNotEmpty() == true
                }
            } ?: error("Lote não foi aplicado")
        }
        val serie = aplicado.first().exercises.first().sets.single()
        assertEquals(10, serie.reps)
        assertEquals(60.0, serie.weightKg!!, 0.0)
    }

    @Test
    fun ignora_client_set_id_repetido() = runTest {
        val recebidos = mutableListOf<WearSyncBatch>()
        val job = launch { wearRepo.observeSyncBatches().collect { recebidos.add(it) } }
        runCurrent()

        // Reenvio do mesmo lote (mesmo clientSetId): dedupe por clientSetId.
        wearRepo.onSyncBatchReceived(lote(42, 100))
        wearRepo.onSyncBatchReceived(lote(42, 100))
        runCurrent()
        job.cancel()

        assertEquals(1, recebidos.size)
        assertEquals(1, recebidos[0].sets.size)
    }

    @Test
    fun ignora_sessao_diferente() {
        val (_, sessionExerciseId) = runBlocking { iniciarSessao() }

        // Lote de outra sessão: o celular ignora e reenvia o estado atual.
        wearRepo.onSyncBatchReceived(lote(sessionId = 999, sessionExerciseId = sessionExerciseId))
        runBlocking { delay(300) }

        val sessoes = runBlocking { repository.observeSessions().first() }
        assertTrue(sessoes.first().exercises.all { it.sets.isEmpty() })
    }

    @Test
    fun ignora_versao_incompativel() = runTest {
        val recebidos = mutableListOf<WearSyncBatch>()
        val job = launch { wearRepo.observeSyncBatches().collect { recebidos.add(it) } }
        runCurrent()

        wearRepo.onSyncBatchReceived(lote(42, 100, protocolVersion = 2))
        runCurrent()
        job.cancel()

        // protocolVersion != 1: o celular ignora e o relógio mantém pendente.
        assertTrue(recebidos.isEmpty())
    }

    @Test
    fun lote_finished_finaliza_sessao() {
        val (sessionId, sessionExerciseId) = runBlocking { iniciarSessao() }

        wearRepo.onSyncBatchReceived(lote(sessionId, sessionExerciseId, finished = true))

        // O status da sessão no Room é "completed" (o "finished" é do protocolo).
        val finalizada = runBlocking {
            withTimeoutOrNull(5_000) {
                repository.observeSessions().first { list ->
                    list.firstOrNull()?.session?.status == "completed"
                }
            } ?: error("Sessão não foi finalizada pelo lote")
        }
        assertEquals("completed", finalizada.first().session.status)
    }
}
