package com.treinos.app.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.treinos.app.R
import com.treinos.app.data.WorkoutRepository
import com.treinos.app.data.local.AppDatabase
import com.treinos.app.data.local.WorkoutDao
import com.treinos.app.data.local.WorkoutDayEntity
import com.treinos.app.data.local.WorkoutPlanEntity
import com.treinos.app.data.local.WorkoutPlanExerciseEntity
import com.treinos.app.data.prefs.AppPreferences
import com.treinos.app.data.wear.WearSyncChannel
import com.treinos.protocol.WearCommand
import com.treinos.protocol.WearCommandKind
import com.treinos.protocol.WearSessionSnapshot
import com.treinos.protocol.WearSyncBatch
import com.treinos.protocol.WearWorkoutState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.temporal.ChronoField

/**
 * Regressão do "pedir no relógio e nada acontece": o estado do treino
 * (`/treinos/workout-state`) nunca foi publicado — `publish()` não tinha
 * chamador desde o H2-2 — e o `REQUEST_STATE` do pulso era descartado em
 * silêncio quando não havia sessão em andamento. O gate JVM não exercita o
 * GMS, então o fio só aparece aqui: o fake grava o que o ViewModel publica.
 */
@RunWith(RobolectricTestRunner::class)
class WearPublishTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var dao: WorkoutDao
    private lateinit var repository: WorkoutRepository
    private lateinit var preferences: AppPreferences
    private lateinit var fake: FakeWearSyncChannel
    private lateinit var viewModel: AppViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.workoutDao()
        preferences = AppPreferences(context)
        preferences.deviceId = "device"
        repository = WorkoutRepository(dao, FakeBackendApiMain(), preferences)
        fake = FakeWearSyncChannel()
        Dispatchers.setMain(testDispatcher)
        viewModel = AppViewModel.factory(
            context = context,
            repository = repository,
            preferences = preferences,
            wearSyncRepository = fake,
        ).create(AppViewModel::class.java)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    private fun todayWeekday(): Int = LocalDate.now().get(ChronoField.DAY_OF_WEEK)

    /** Plano principal com um único dia na weekday dada (e um exercício). */
    private suspend fun semearPlano(weekday: Int?): Long =
        dao.insertPlanWithDays(
            plan = WorkoutPlanEntity(name = "Principal", kind = "main"),
            days = listOf(
                WorkoutDayEntity(
                    planId = 0,
                    name = "Dia Teste",
                    weekday = weekday,
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

    /** Pré-aquece `activePlan` (valor cacheado), como em StartTodayWorkoutTest. */
    private fun aguardarPlanoAtivo() {
        runBlocking {
            viewModel.activePlan.first { plan ->
                plan != null && plan.days.isNotEmpty()
            }
        }
    }

    /** Espera limitada: a gravação da sessão roda no executor do Room. */
    private fun aguardarPublicacao(): WearWorkoutState? {
        val inicio = System.currentTimeMillis()
        while (fake.estados.isEmpty() && System.currentTimeMillis() - inicio < 5_000) {
            Thread.sleep(10)
        }
        return fake.estados.firstOrNull()
    }

    @Test
    fun iniciar_sessao_publica_estado_no_data_layer() {
        runBlocking { semearPlano(todayWeekday()) }
        aguardarPlanoAtivo()

        viewModel.startTodayWorkout()

        val publicado = aguardarPublicacao()
        assertTrue("Estado do treino nunca foi publicado ao relógio", publicado != null)
        assertEquals("in_progress", publicado!!.status)
        assertEquals("Dia Teste", publicado.dayName)
    }

    @Test
    fun request_state_sem_sessao_responde_idle_com_o_treino_de_hoje() {
        runBlocking { semearPlano(todayWeekday()) }
        aguardarPlanoAtivo()

        fake.comandos.tryEmit(
            WearCommand(
                commandId = "req-1",
                sessionId = -1,
                kind = WearCommandKind.REQUEST_STATE,
            ),
        )

        val publicado = aguardarPublicacao()
        assertTrue("REQUEST_STATE sem sessão foi descartado em silêncio", publicado != null)
        assertEquals("idle", publicado!!.status)
        assertEquals("Dia Teste", publicado.dayName)
    }

    @Test
    fun request_state_sem_sessao_e_sem_plano_responde_idle_vazio() {
        fake.comandos.tryEmit(
            WearCommand(
                commandId = "req-2",
                sessionId = -1,
                kind = WearCommandKind.REQUEST_STATE,
            ),
        )

        val publicado = aguardarPublicacao()
        assertTrue("REQUEST_STATE sem plano foi descartado em silêncio", publicado != null)
        assertEquals("idle", publicado!!.status)
        assertEquals("", publicado.dayName)
    }

    @Test
    fun sincronizar_agora_sem_sessao_publica_idle() {
        runBlocking { semearPlano(todayWeekday()) }
        aguardarPlanoAtivo()

        viewModel.syncWearNow()

        val publicado = aguardarPublicacao()
        assertTrue("Botão de sync não publicou nada", publicado != null)
        assertEquals("idle", publicado!!.status)
        assertEquals("Dia Teste", publicado.dayName)
    }

    @Test
    fun start_today_sem_sessao_inicia_e_publica_in_progress() {
        runBlocking { semearPlano(todayWeekday()) }
        aguardarPlanoAtivo()

        fake.comandos.tryEmit(
            WearCommand(
                commandId = "start-1",
                sessionId = -1,
                kind = WearCommandKind.START_TODAY,
            ),
        )

        val publicado = aguardarPublicacao()
        assertTrue("START_TODAY não publicou o estado iniciado", publicado != null)
        assertEquals("in_progress", publicado!!.status)
        assertEquals("Dia Teste", publicado.dayName)
        val sessoes = runBlocking { repository.observeSessions().first() }
        assertEquals(1, sessoes.count { it.session.status == "in_progress" })
    }

    @Test
    fun start_today_em_dia_de_descanso_responde_idle() {
        runBlocking {
            dao.insertPlanWithDays(
                plan = WorkoutPlanEntity(name = "Principal", kind = "main"),
                days = listOf(
                    WorkoutDayEntity(
                        planId = 0,
                        name = "Descanso",
                        weekday = todayWeekday(),
                        isRestDay = true,
                    ),
                ),
                exercisesByDay = listOf(emptyList()),
            )
        }
        aguardarPlanoAtivo()

        fake.comandos.tryEmit(
            WearCommand(
                commandId = "start-rest",
                sessionId = -1,
                kind = WearCommandKind.START_TODAY,
            ),
        )

        val publicado = aguardarPublicacao()
        assertTrue("START_TODAY no descanso não respondeu idle", publicado != null)
        assertEquals("idle", publicado!!.status)
        val sessoes = runBlocking { repository.observeSessions().first() }
        assertTrue(sessoes.none { it.session.status == "in_progress" })
    }

    @Test
    fun start_today_com_sessao_ativa_nao_duplica() {
        runBlocking { semearPlano(todayWeekday()) }
        aguardarPlanoAtivo()
        viewModel.startTodayWorkout()
        aguardarPublicacao()

        fake.comandos.tryEmit(
            WearCommand(
                commandId = "start-active",
                sessionId = -1,
                kind = WearCommandKind.START_TODAY,
            ),
        )

        val publicado = aguardarPublicacao()
        assertTrue("START_TODAY com sessão ativa não respondeu estado", publicado != null)
        assertEquals("in_progress", publicado!!.status)
        val sessoes = runBlocking { repository.observeSessions().first() }
        assertEquals(1, sessoes.count { it.session.status == "in_progress" })
    }
}

/** Fake do canal de sync: grava o que o ViewModel publica ao relógio. */
class FakeWearSyncChannel : WearSyncChannel {

    val comandos = MutableSharedFlow<WearCommand>(extraBufferCapacity = 50)

    val estados = mutableListOf<WearWorkoutState>()

    val snapshots = mutableListOf<WearSessionSnapshot>()

    override val commands: Flow<WearCommand> = comandos

    override fun observeSyncBatches(): Flow<WearSyncBatch> = MutableSharedFlow()

    override fun publish(state: WearWorkoutState) {
        estados.add(state)
    }

    override fun publishSnapshot(snapshot: WearSessionSnapshot) {
        snapshots.add(snapshot)
    }
}
