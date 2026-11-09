package com.treinos.app.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.treinos.app.data.WorkoutRepository
import com.treinos.app.data.local.AppDatabase
import com.treinos.app.data.local.MANUAL_SESSION_SOURCE
import com.treinos.app.data.local.WorkoutSessionEntity
import com.treinos.app.data.prefs.AppPreferences
import com.treinos.app.data.remote.BackendApi
import com.treinos.app.data.remote.GeneratePlanRequest
import com.treinos.app.data.remote.GeneratePlanResponse
import com.treinos.app.ui.ActivityLogDraft
import com.treinos.app.ui.ActivityModality
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class FakeBackendApiNav : BackendApi {
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
class NavigationBackTest {

    private lateinit var context: Context
    private lateinit var repository: WorkoutRepository
    private lateinit var viewModel: AppViewModel

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.workoutDao()
        val preferences = AppPreferences(context)
        preferences.deviceId = "device"
        repository = WorkoutRepository(dao, FakeBackendApiNav(), preferences)
        viewModel = AppViewModel(
            context = context,
            repository = repository,
            preferences = preferences,
        )
    }

    @After
    fun tearDown() = runTest {}

    @Test
    fun back_retorna_a_origem() = runTest {
        // Home -> Histórico
        viewModel.openHistory()
        assertEquals(Screen.History, viewModel.screen.value)
        assertEquals(Screen.Home, viewModel.backTarget.value)

        // Voltar() retorna à origem
        viewModel.voltar()
        assertEquals(Screen.Home, viewModel.screen.value)
    }

    @Test
    fun back_target_default_home() = runTest {
        // Sem navegação prévia, backTarget é nulo -> padrão Home
        assertEquals(null, viewModel.backTarget.value)
        viewModel.voltar()
        assertEquals(Screen.Home, viewModel.screen.value)
    }

    @Test
    fun pilha_multipla_home_historico_ajustes_volta_na_ordem() = runTest {
        // Home -> Histórico
        viewModel.openHistory()
        assertEquals(Screen.History, viewModel.screen.value)
        assertEquals(Screen.Home, viewModel.backTarget.value)

        // Histórico -> Ajustes
        viewModel.openSettings()
        assertEquals(Screen.Settings, viewModel.screen.value)
        assertEquals(Screen.History, viewModel.backTarget.value)

        // back x3: Ajustes -> Histórico -> Home -> (Home permanece)
        viewModel.voltar()
        assertEquals(Screen.History, viewModel.screen.value)

        viewModel.voltar()
        assertEquals(Screen.Home, viewModel.screen.value)
        assertEquals(null, viewModel.backTarget.value)

        viewModel.voltar()
        assertEquals(Screen.Home, viewModel.screen.value)
    }
}
