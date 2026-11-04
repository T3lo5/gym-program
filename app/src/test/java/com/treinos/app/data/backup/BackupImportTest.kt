package com.treinos.app.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.treinos.app.data.DeviceIdStore
import com.treinos.app.data.WorkoutRepository
import com.treinos.app.data.local.AppDatabase
import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutDao
import com.treinos.app.data.local.WorkoutDayEntity
import com.treinos.app.data.local.WorkoutPlanEntity
import com.treinos.app.data.local.WorkoutPlanExerciseEntity
import com.treinos.app.data.local.WorkoutSessionEntity
import com.treinos.app.data.prefs.AppPreferences
import com.treinos.app.data.remote.BackendApi
import com.treinos.app.data.remote.CatalogResponseDto
import com.treinos.app.data.remote.GeneratePlanRequest
import com.treinos.app.data.remote.GeneratePlanResponse
import com.treinos.app.data.remote.HealthResponse
import com.treinos.app.data.remote.SyncDownloadDto
import com.treinos.app.data.remote.SyncRequestDto
import com.treinos.app.data.remote.SyncResponseDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupImportTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: WorkoutDao
    private lateinit var preferences: AppPreferences
    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.workoutDao()
        preferences = AppPreferences(context)
        preferences.deviceId = "id-deste-app"
        repository = WorkoutRepository(dao = dao, backendClient = UnusedBackend(), preferences = preferences)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedWorkout() {
        dao.replaceActivePlan(
            plan = WorkoutPlanEntity(id = 1, name = "Plano do backup"),
            days = listOf(WorkoutDayEntity(id = 10, planId = 1, name = "Treino A")),
            exercisesByDay = listOf(
                listOf(WorkoutPlanExerciseEntity(id = 100, dayId = 10, exerciseId = "ex1", exerciseName = "Supino")),
            ),
        )
        dao.insertSession(WorkoutSessionEntity(id = 50, planId = 1, dayId = 10, status = "completed"))
        dao.insertSessionExercises(
            listOf(SessionExerciseEntity(id = 500, sessionId = 50, exerciseId = "ex1", exerciseName = "Supino")),
        )
        dao.insertSetLogs(
            listOf(SetLogEntity(id = 5000, exerciseLogId = 500, setNumber = 1, reps = 10, weightKg = 40.0)),
        )
    }

    private suspend fun backupFromCurrentData(deviceId: String): BackupFile {
        val json = repository.exportBackup(appVersionName = "1.0", appVersionCode = 1L)
        return Json.decodeFromString(BackupFile.serializer(), json).copy(deviceId = deviceId)
    }

    @Test
    fun `importa backup aplica device id`() = runTest {
        seedWorkout()
        val payload = backupFromCurrentData(deviceId = "celular-antigo")
        val store = FakeDeviceIdStore(deviceId = "id-deste-app")

        repository.importBackup(payload, store)

        assertEquals("celular-antigo", store.deviceId)
        assertEquals("Plano do backup", dao.getAllPlanEntities().single().name)
        assertEquals(5000L, dao.getAllSetLogs().single().id)
    }

    @Test
    fun `backup sem device id mantem device id atual`() = runTest {
        seedWorkout()
        val payload = backupFromCurrentData(deviceId = "")
        val store = FakeDeviceIdStore(deviceId = "id-deste-app")

        repository.importBackup(payload, store)

        assertEquals("id-deste-app", store.deviceId)
        assertEquals(1, dao.getAllPlanEntities().size)
    }

    @Test
    fun `importacao invalida nao altera dados nem device id`() = runTest {
        seedWorkout()
        val payload = backupFromCurrentData(deviceId = "celular-antigo")
            .copy(app = BackupApp(packageName = "com.outro.app", versionName = "1.0", versionCode = 1L))
        val store = FakeDeviceIdStore(deviceId = "id-deste-app")

        val failure = runCatching { repository.importBackup(payload, store) }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals("id-deste-app", store.deviceId)
        assertEquals("Plano do backup", dao.getAllPlanEntities().single().name)
        assertEquals(1, dao.getAllSessionEntities().size)
    }

    private class FakeDeviceIdStore(override var deviceId: String) : DeviceIdStore

    private class UnusedBackend : BackendApi {
        override suspend fun health(): HealthResponse = error("não usado")

        override suspend fun generatePlan(request: GeneratePlanRequest): GeneratePlanResponse =
            error("não usado")

        override suspend fun sync(request: SyncRequestDto): SyncResponseDto = error("não usado")

        override suspend fun syncDownload(deviceId: String): SyncDownloadDto = error("não usado")

        override suspend fun catalog(): CatalogResponseDto = error("não usado")
    }
}
