package com.treinos.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import com.treinos.app.data.remote.SyncDownloadDayDto
import com.treinos.app.data.remote.SyncDownloadDto
import com.treinos.app.data.remote.SyncDownloadPlanDto
import com.treinos.app.data.remote.SyncDownloadPlanExerciseDto
import com.treinos.app.data.remote.SyncDownloadSessionDto
import com.treinos.app.data.remote.SyncDownloadSessionExerciseDto
import com.treinos.app.data.remote.SyncDownloadSetLogDto
import com.treinos.app.data.remote.SyncRequestDto
import com.treinos.app.data.remote.SyncResponseDto
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

@RunWith(RobolectricTestRunner::class)
class RestoreDownloadTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dao: WorkoutDao
    private lateinit var preferences: AppPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.workoutDao()
        preferences = AppPreferences(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun repositoryWith(dao: WorkoutDao, snapshot: SyncDownloadDto) = WorkoutRepository(
        dao = dao,
        backendClient = FakeBackend(snapshot),
        preferences = preferences,
    )

    private suspend fun seedLocalWorkout() {
        dao.replaceActivePlan(
            plan = WorkoutPlanEntity(name = "Plano local"),
            days = listOf(WorkoutDayEntity(planId = 0, name = "Local A")),
            exercisesByDay = listOf(listOf(WorkoutPlanExerciseEntity(dayId = 0, exerciseId = "ex1"))),
        )
        val sessionId = dao.insertSession(
            WorkoutSessionEntity(planId = 1, dayId = 1, planName = "Plano local", status = "completed"),
        )
        val exerciseId = dao.insertSessionExercises(
            listOf(SessionExerciseEntity(sessionId = sessionId, exerciseId = "ex1", plannedSets = 3)),
        ).single()
        dao.insertSetLog(SetLogEntity(exerciseLogId = exerciseId, setNumber = 1, reps = 10, weightKg = 40.0))
    }

    @Test
    fun `snapshot vazio nao apaga dados locais`() = runTest {
        seedLocalWorkout()
        val repository = repositoryWith(dao, SyncDownloadDto(deviceId = "outro"))

        val result = repository.downloadSync("outro")

        assertEquals(SyncDownloadResult.Empty, result)
        assertEquals("Plano local", dao.getAllPlanEntities().single().name)
        assertEquals(1, dao.getAllSessionEntities().size)
        assertEquals(1, dao.getAllSetLogs().size)
    }

    @Test
    fun `snapshot valido substitui dados`() = runTest {
        seedLocalWorkout()
        val snapshot = SyncDownloadDto(
            deviceId = "outro",
            plans = listOf(planDto(localId = 7, name = "Plano remoto")),
            days = listOf(dayDto(id = 21, localId = 21, planId = 7, name = "Remoto A")),
            planExercises = listOf(planExerciseDto(id = 31, dayId = 21)),
            sessions = listOf(sessionDto(id = 41, localId = 41, planLocalId = 7, dayLocalId = 21)),
            sessionExercises = listOf(sessionExerciseDto(id = 51, localId = 51, sessionId = 41)),
            setLogs = listOf(setLogDto(id = 61, localId = 61, sessionExerciseId = 51)),
        )
        val repository = repositoryWith(dao, snapshot)

        val result = repository.downloadSync("outro")

        assertEquals(SyncDownloadResult.Restored(planCount = 1, sessionCount = 1), result)
        assertEquals(listOf(7L), dao.getAllPlanEntities().map { it.id })
        assertEquals(listOf(21L), dao.getAllDayEntities().map { it.id })
        assertEquals(7L, dao.getAllDayEntities().single().planId)
        assertEquals(21L, dao.getAllPlanExercises().single().dayId)
        assertEquals(listOf(41L), dao.getAllSessionEntities().map { it.id })
        assertEquals(7L, dao.getAllSessionEntities().single().planId)
        assertEquals(21L, dao.getAllSessionEntities().single().dayId)
        assertEquals(41L, dao.getAllSessionExercises().single().sessionId)
        assertEquals(51L, dao.getAllSetLogs().single().exerciseLogId)
        assertEquals(1, dao.getAllSetLogs().single().reps ?: 0)
    }

    @Test
    fun `falha no meio faz rollback`() = runTest {
        seedLocalWorkout()

        // Filho com pai inexistente: o SQLite falha depois de já ter apagado e
        // inserido o plano, e a transação do DAO precisa desfazer tudo.
        val failure = runCatching {
            dao.replaceAllData(
                plans = listOf(WorkoutPlanEntity(id = 77, name = "Plano quebrado")),
                days = listOf(WorkoutDayEntity(id = 78, planId = 999, name = "Dia órfão")),
                planExercises = emptyList(),
                sessions = emptyList(),
                sessionExercises = emptyList(),
                setLogs = emptyList(),
            )
        }.exceptionOrNull()

        assertNotNull("esperava falha de chave estrangeira no meio da inserção", failure)
        assertEquals("Plano local", dao.getAllPlanEntities().single().name)
        assertEquals(1, dao.getAllDayEntities().size)
        assertEquals(1, dao.getAllSetLogs().size)
        assertNull(dao.getPlan(77))
    }

    @Test
    fun `erro de escrita mantem dados e propaga erro`() = runTest {
        seedLocalWorkout()
        val snapshot = SyncDownloadDto(
            deviceId = "outro",
            plans = listOf(planDto(localId = 7)),
        )
        val repository = repositoryWith(ThrowingDao(dao), snapshot)

        val failure = runCatching { repository.downloadSync("outro") }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals("Plano local", dao.getAllPlanEntities().single().name)
    }

    @Test
    fun `restauracao deixa um plano ativo`() = runTest {
        val snapshot = SyncDownloadDto(
            deviceId = "outro",
            plans = listOf(
                planDto(localId = 7, name = "Sem ativo", isActive = false),
                planDto(localId = 8, name = "Também sem ativo", isActive = false),
            ),
        )
        val repository = repositoryWith(dao, snapshot)

        repository.downloadSync("outro")

        val activePlans = dao.getAllPlanEntities().filter { it.isActive }
        assertEquals(1, activePlans.size)
        assertEquals(7L, activePlans.single().id)

        // Snapshot com dois ativos fica com exatamente um.
        val duplicated = SyncDownloadDto(
            deviceId = "outro",
            plans = listOf(
                planDto(localId = 9, name = "Ativo 1"),
                planDto(localId = 10, name = "Ativo 2"),
            ),
        )
        repositoryWith(dao, duplicated).downloadSync("outro")
        assertEquals(listOf(9L), dao.getAllPlanEntities().filter { it.isActive }.map { it.id })
    }

    @Test
    fun `sessao avulsa preservada`() = runTest {
        val snapshot = SyncDownloadDto(
            deviceId = "outro",
            sessions = listOf(
                sessionDto(id = 41, localId = 41, planLocalId = 0, dayLocalId = 0)
                    .copy(source = "manual", modality = "swim", title = "Natação"),
            ),
            sessionExercises = emptyList(),
        )
        val repository = repositoryWith(dao, snapshot)

        val result = repository.downloadSync("outro")

        assertEquals(SyncDownloadResult.Restored(planCount = 0, sessionCount = 1), result)
        val session = dao.getAllSessionEntities().single()
        assertEquals(0L, session.planId)
        assertEquals(0L, session.dayId)
        assertEquals("Natação", session.title)
    }

    @Test
    fun `snapshot com referencia orfa e rejeitado`() = runTest {
        seedLocalWorkout()
        val snapshot = SyncDownloadDto(
            deviceId = "outro",
            plans = listOf(planDto(localId = 7)),
            days = listOf(dayDto(id = 21, localId = 21, planId = 999)),
        )
        val repository = repositoryWith(dao, snapshot)

        val failure = runCatching { repository.downloadSync("outro") }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(failure?.message.orEmpty().contains("plano"))
        assertEquals("Plano local", dao.getAllPlanEntities().single().name)
        assertEquals("Local A", dao.getAllDayEntities().single().name)
    }

    @Test
    fun `snapshot com id duplicado e rejeitado`() = runTest {
        seedLocalWorkout()
        val snapshot = SyncDownloadDto(
            deviceId = "outro",
            plans = listOf(planDto(localId = 7, name = "Um"), planDto(localId = 7, name = "Dois")),
        )
        val repository = repositoryWith(dao, snapshot)

        val failure = runCatching { repository.downloadSync("outro") }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(failure?.message.orEmpty().contains("duplicad"))
        assertEquals("Plano local", dao.getAllPlanEntities().single().name)
    }

    private fun planDto(localId: Long, name: String = "Plano", isActive: Boolean = true) =
        SyncDownloadPlanDto(localId = localId, name = name, isActive = isActive)

    private fun dayDto(
        id: Long,
        localId: Long,
        planId: Long,
        name: String = "Dia",
    ) = SyncDownloadDayDto(id = id, planId = planId, localId = localId, name = name, dayOrder = 0)

    private fun planExerciseDto(id: Long, dayId: Long) = SyncDownloadPlanExerciseDto(
        id = id,
        dayId = dayId,
        exerciseId = "ex1",
        exerciseOrder = 0,
        sets = 3,
    )

    private fun sessionDto(id: Long, localId: Long, planLocalId: Long, dayLocalId: Long) =
        SyncDownloadSessionDto(
            id = id,
            deviceId = "outro",
            localId = localId,
            planLocalId = planLocalId,
            dayLocalId = dayLocalId,
            status = "completed",
        )

    private fun sessionExerciseDto(id: Long, localId: Long, sessionId: Long) =
        SyncDownloadSessionExerciseDto(
            id = id,
            sessionId = sessionId,
            localId = localId,
            exerciseId = "ex1",
            exerciseOrder = 0,
        )

    private fun setLogDto(id: Long, localId: Long, sessionExerciseId: Long) = SyncDownloadSetLogDto(
        id = id,
        sessionExerciseId = sessionExerciseId,
        localId = localId,
        setNumber = 1,
        reps = 1,
    )

    private class ThrowingDao(private val delegate: WorkoutDao) : WorkoutDao by delegate {
        override suspend fun replaceAllData(
            plans: List<WorkoutPlanEntity>,
            days: List<WorkoutDayEntity>,
            planExercises: List<WorkoutPlanExerciseEntity>,
            sessions: List<WorkoutSessionEntity>,
            sessionExercises: List<SessionExerciseEntity>,
            setLogs: List<SetLogEntity>,
        ) {
            throw IllegalStateException("falha no meio da substituição")
        }
    }

    private class FakeBackend(
        private val snapshot: SyncDownloadDto,
    ) : BackendApi {
        override suspend fun health(): HealthResponse = error("não usado")

        override suspend fun generatePlan(request: GeneratePlanRequest): GeneratePlanResponse =
            error("não usado")

        override suspend fun sync(request: SyncRequestDto): SyncResponseDto = error("não usado")

        override suspend fun syncDownload(deviceId: String): SyncDownloadDto = snapshot

        override suspend fun catalog(): CatalogResponseDto = error("não usado")
    }
}
