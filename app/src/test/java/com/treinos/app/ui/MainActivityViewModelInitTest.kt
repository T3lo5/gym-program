package com.treinos.app.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.treinos.app.data.WorkoutRepository
import com.treinos.app.data.local.AppDatabase
import com.treinos.app.data.prefs.AppPreferences
import com.treinos.app.data.remote.BackendApi
import com.treinos.app.data.remote.GenerateJobAck
import com.treinos.app.data.remote.GenerateJobStatus
import com.treinos.app.data.remote.GeneratePlanRequest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class FakeBackendApiMain : BackendApi {
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
class MainActivityViewModelInitTest {

    private lateinit var context: Context
    private lateinit var repository: WorkoutRepository
    private lateinit var preferences: AppPreferences

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.workoutDao()
        preferences = AppPreferences(context)
        preferences.deviceId = "device"
        repository = WorkoutRepository(dao, FakeBackendApiMain(), preferences)
    }

    @After
    fun tearDown() = runTest {}

    @Test
    fun viewModel_eh_inicializado_pela_factory_antes_de_onResume() = runTest {
        val factory = AppViewModel.factory(
            context = context,
            repository = repository,
            preferences = preferences,
        )
        val viewModel = factory.create(AppViewModel::class.java)
        assertNotNull(viewModel)
        // onResume da Activity chama onAppResumed(); nao deve lançar excecao
        viewModel.onAppResumed()
    }
}
