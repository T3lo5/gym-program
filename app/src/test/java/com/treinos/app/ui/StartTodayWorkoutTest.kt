package com.treinos.app.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.treinos.app.data.WorkoutRepository
import com.treinos.app.data.local.AppDatabase
import com.treinos.app.data.local.WorkoutDao
import com.treinos.app.data.local.WorkoutDayEntity
import com.treinos.app.data.local.WorkoutPlanEntity
import com.treinos.app.data.local.WorkoutPlanExerciseEntity
import com.treinos.app.data.prefs.AppPreferences
import com.treinos.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
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
 * H4-14: `startTodayWorkout()` — o caminho do toque no widget "Iniciar treino
 * de hoje". Robolectric + Room em memória, dispatcher de teste como Main para
 * o `viewModelScope` rodar de forma determinística.
 */
@RunWith(RobolectricTestRunner::class)
class StartTodayWorkoutTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var dao: WorkoutDao
    private lateinit var repository: WorkoutRepository
    private lateinit var preferences: AppPreferences
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
        Dispatchers.setMain(testDispatcher)
        viewModel = AppViewModel.factory(
            context = context,
            repository = repository,
            preferences = preferences,
        ).create(AppViewModel::class.java)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    private fun todayWeekday(): Int = LocalDate.now().get(ChronoField.DAY_OF_WEEK)

    /** Plano principal com um único dia na weekday dada (e um exercício). */
    private suspend fun semearPlano(weekday: Int?, isRestDay: Boolean = false): Long =
        dao.insertPlanWithDays(
            plan = WorkoutPlanEntity(name = "Principal", kind = "main"),
            days = listOf(
                WorkoutDayEntity(
                    planId = 0,
                    name = "Dia Teste",
                    weekday = weekday,
                    isRestDay = isRestDay,
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

    /**
     * Pré-aquece o StateFlow `activePlan`: o valor fica cacheado, então
     * `startTodayWorkout()` não depende do teto de 5 s para o teste.
     */
    private fun aguardarPlanoAtivo() {
        runBlocking { viewModel.activePlan.first { it != null } }
    }

    @Test
    fun inicia_sessao_do_dia_de_hoje() {
        val weekday = todayWeekday()
        val planId = runBlocking { semearPlano(weekday) }
        aguardarPlanoAtivo()

        viewModel.startTodayWorkout()

        // A sessão em andamento do dia aparece no Room (teto real de 5 s).
        val sessoes = runBlocking {
            withTimeoutOrNull(5_000) {
                repository.observeSessions()
                    .first { list -> list.any { it.session.status == "in_progress" } }
            } ?: error("Sessão do dia não foi iniciada")
        }
        val sessao = sessoes.first { it.session.status == "in_progress" }
        assertEquals(planId, sessao.session.planId)
        assertEquals("Dia Teste", sessao.session.dayName)
    }

    @Test
    fun sem_plano_mostra_mensagem() {
        viewModel.startTodayWorkout()

        // O teto de 5 s de `withTimeoutOrNull` é tempo virtual do scheduler de
        // teste: adianta para o timeout disparar e a mensagem ser publicada.
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            context.getString(R.string.msg_no_workout_today),
            viewModel.message.value,
        )
        val sessoes = runBlocking { repository.observeSessions().first() }
        assertTrue(sessoes.none { it.session.status == "in_progress" })
    }

    @Test
    fun dia_de_descanso_nao_inicia() {
        runBlocking { semearPlano(todayWeekday(), isRestDay = true) }
        aguardarPlanoAtivo()

        viewModel.startTodayWorkout()

        assertEquals(
            context.getString(R.string.msg_no_workout_today),
            viewModel.message.value,
        )
        val sessoes = runBlocking { repository.observeSessions().first() }
        assertTrue(sessoes.none { it.session.status == "in_progress" })
    }
}
